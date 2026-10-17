package com.enlace.app.dominio

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Equipo de respuesta con el que trabaja este celular en modo unidad. */
@Serializable
data class EquipoUnidad(
    val id: Int,
    val nombre: String,
    val organismo: String,
    val capacidades: List<String> = emptyList(),
)

/** Qué hay que atender en un pedido de ayuda. */
@Serializable
data class Atender(
    val personas: Int = 0,
    val atrapados: Int = 0,
    val heridos: Int = 0,
    val vulnerables: Int = 0,
    val necesidades: List<String> = emptyList(),
)

/** Contacto de quien pidió ayuda: solo lo recibe la unidad asignada. */
@Serializable
data class Contacto(val nombre: String? = null, val telefono: String? = null, val condiciones: String? = null)

/** Asignación que el centro de coordinación aceptó para este equipo (B3). */
@Serializable
data class AsignacionUnidad(
    val id: Int,
    val idReporte: String? = null,
    val idH3: String,
    val nombreZona: String = "",
    val motivo: String,
    val minutos: Double,
    val destinoLat: Double,
    val destinoLon: Double,
    val pedido: Atender? = null,
    val contacto: Contacto? = null,
    /** GeoJSON de la ruta con instrucciones (para la navegación del paso 4.8). */
    val ruta: String? = null,
    /** La unidad ya abrió el aviso: no se vuelve a abrir solo. */
    val vista: Boolean = false,
    /** B6: por qué cambió la ruta (p. ej. un bloqueo confirmado), hasta que la unidad lo lee. */
    val aviso: String? = null,
)

/** Niveles de daño de la evaluación de una brigada (servidor: NivelDano). */
enum class NivelDano(val codigo: String) {
    NINGUNO("ninguno"),
    LEVE("leve"),
    MODERADO("moderado"),
    GRAVE("grave"),
    COLAPSO("colapso"),
}

/** Lo que la brigada informa en la evaluación de zona (B8). */
data class DatosEvaluacion(
    val nivel: NivelDano? = null,
    val personas: Int = 0,
    val atrapados: Int = 0,
    val heridos: Int = 0,
    val necesidades: Set<Necesidad> = emptySet(),
)

// ── Panorama de la zona para la unidad (B2) ────────────────

data class Punto(val lat: Double, val lon: Double)

data class ZonaCritica(
    val idH3: String,
    val nombre: String,
    val criticos: Int,
    val personas: Int,
    val centro: Punto?,
    val distanciaM: Double? = null,
)

/** Persona que eligió compartir su ubicación exacta (estado: sin_respuesta | ayuda | bien). */
data class PersonaUbicada(
    val punto: Punto,
    val estado: String,
    val personas: Int,
    val vulnerables: Int,
    val distanciaM: Double? = null,
)

data class PedidoPendiente(
    val id: String,
    val punto: Punto,
    val zona: String,
    val estado: String,
    val atender: Atender,
    val distanciaM: Double? = null,
)

data class PanoramaUnidad(
    val zonas: List<ZonaCritica>,
    val personas: List<PersonaUbicada>,
    val pedidos: List<PedidoPendiente>,
)

private const val RADIO_TIERRA_M = 6_371_000.0

/** Distancia en línea recta (haversine), en metros. */
fun distanciaM(a: Punto, b: Punto): Double {
    val lat1 = Math.toRadians(a.lat)
    val lat2 = Math.toRadians(b.lat)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    return 2 * RADIO_TIERRA_M * asin(sqrt(h))
}

/**
 * Ordena lo que la unidad ve en su inicio:
 * - zonas: más población crítica primero (a igual cantidad, la más cercana);
 * - personas: solo las que no avisaron que están bien; pidieron ayuda primero, luego hogares con
 *   personas vulnerables, y dentro de cada grupo la más cercana;
 * - pedidos: el más cercano primero.
 * Sin ubicación de la unidad (`aqui` null) se ordena igual, sin distancias.
 */
fun ordenarPanorama(panorama: PanoramaUnidad, aqui: Punto?, maximo: Int): PanoramaUnidad {
    fun distancia(p: Punto?): Double? = if (aqui == null || p == null) null else distanciaM(aqui, p)
    val lejos = Double.MAX_VALUE
    val zonas = panorama.zonas
        .filter { it.criticos > 0 }
        .map { it.copy(distanciaM = distancia(it.centro)) }
        .sortedWith(compareByDescending<ZonaCritica> { it.criticos }.thenBy { it.distanciaM ?: lejos })
        .take(maximo)
    val personas = panorama.personas
        .filter { it.estado != ESTADO_BIEN }
        .map { it.copy(distanciaM = distancia(it.punto)) }
        .sortedWith(
            compareBy<PersonaUbicada> { if (it.estado == ESTADO_AYUDA) 0 else 1 }
                .thenBy { if (it.vulnerables > 0) 0 else 1 }
                .thenBy { it.distanciaM ?: lejos },
        )
        .take(maximo)
    val pedidos = panorama.pedidos
        .map { it.copy(distanciaM = distancia(it.punto)) }
        .sortedBy { it.distanciaM ?: lejos }
        .take(maximo)
    return PanoramaUnidad(zonas, personas, pedidos)
}

/** Centro aproximado de una celda: el promedio de sus vértices. */
fun centroDe(celda: CeldaH3): Punto? = celda.contorno.takeIf { it.isNotEmpty() }?.let { vertices ->
    Punto(vertices.sumOf { it.first } / vertices.size, vertices.sumOf { it.second } / vertices.size)
}

/** Una asignación sigue en curso si fue aceptada (o con otro equipo) y no terminó. */
fun enCurso(decision: String, horaFin: String?): Boolean =
    horaFin == null && decision in setOf("aceptada", "modificada")

/** Minutos para mostrar, redondeados como en el motivo del servidor ("llega en unos 4 min"). */
fun minutosRedondeados(minutos: Double): Int = kotlin.math.round(minutos).toInt()

const val ESTADO_BIEN = "bien"
const val ESTADO_AYUDA = "ayuda"

sealed interface ResultadoClave {
    data class Valida(val equipos: List<EquipoUnidad>) : ResultadoClave
    data object Invalida : ResultadoClave
    data object SinConexion : ResultadoClave
}

enum class ResultadoAtendida { ATENDIDA, SIN_CONEXION }

interface UnidadRepositorio {
    /** Equipo activo en este celular, o null si no está en modo unidad. */
    val equipo: Flow<EquipoUnidad?>
    val asignacion: Flow<AsignacionUnidad?>

    /** Mensajes de otros celulares que esta unidad lleva para entregar (mula de datos). */
    val mensajesQueLleva: Flow<Int>

    suspend fun claveBrigada(): String?

    suspend fun comprobarClave(clave: String): ResultadoClave

    suspend fun activar(clave: String, equipo: EquipoUnidad)

    suspend fun salir()

    suspend fun marcarVista(id: Int)

    suspend fun marcarAtendida(): ResultadoAtendida

    suspend fun evaluar(datos: DatosEvaluacion): ResultadoEnvio

    suspend fun panorama(): Result<PanoramaUnidad>

    /** Mientras navega, la unidad informa dónde está: el servidor recalcula rutas desde ahí. */
    suspend fun informarPosicion(punto: Punto): Boolean

    suspend fun descartarAviso()

    /** La unidad salió de la ruta: se pide una nueva desde donde está (evitando los bloqueos). */
    suspend fun recalcularRuta(desde: Punto): Boolean
}
