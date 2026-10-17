package com.enlace.app.dominio

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.cos
import kotlin.math.sqrt

/** Paso de la ruta tal como lo arma el servidor ("Gira a la derecha en … y sigue 300 m"). */
data class Instruccion(val texto: String, val distanciaM: Double)

/** Ruta del servidor (GeoJSON con LineString en lon, lat) lista para guiar a la unidad. */
data class RutaNavegable(
    val puntos: List<Punto>,
    val instrucciones: List<Instruccion>,
    val distanciaM: Double,
    val minutos: Double,
)

/** Dónde va la unidad dentro de su ruta (B5). */
data class Progreso(
    val actual: Instruccion?,
    val siguiente: Instruccion?,
    val metrosHastaSiguiente: Double,
    val metrosRestantes: Double,
    /** Más lejos de la ruta que el margen: la unidad tomó otro camino. */
    val fueraDeRuta: Boolean,
    val llegando: Boolean,
)

// El GPS dentro de edificios o entre casas puede errar decenas de metros
const val MARGEN_FUERA_DE_RUTA_M = 100.0
const val DISTANCIA_LLEGADA_M = 40.0
private const val METROS_POR_GRADO = 111_320.0

/** Lee la ruta guardada en la asignación; null si no hay o no tiene la forma esperada. */
fun leerRuta(geojson: String?, json: Json): RutaNavegable? = runCatching {
    val ruta = json.parseToJsonElement(geojson ?: return null).jsonObject
    val propiedades = ruta["properties"]?.jsonObject ?: JsonObject(emptyMap())
    val puntos = ruta.getValue("geometry").jsonObject.getValue("coordinates").jsonArray.map {
        val (lon, lat) = it.jsonArray.map { c -> c.jsonPrimitive.double }
        Punto(lat, lon)
    }
    val instrucciones = (propiedades["instrucciones"] as? JsonArray).orEmpty().map {
        val paso = it.jsonObject
        Instruccion(paso.getValue("texto").jsonPrimitive.content, paso["distancia_m"]?.jsonPrimitive?.doubleOrNull ?: 0.0)
    }
    RutaNavegable(
        puntos = puntos,
        instrucciones = instrucciones,
        distanciaM = propiedades["distancia_m"]?.jsonPrimitive?.doubleOrNull ?: longitud(puntos),
        minutos = propiedades["tiempo_min"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
    ).takeIf { puntos.isNotEmpty() }
}.getOrNull()

private fun longitud(puntos: List<Punto>): Double = puntos.zipWithNext { a, b -> distanciaM(a, b) }.sum()

/**
 * Proyecta la posición sobre la ruta (plano local: en una ciudad el error es despreciable) y
 * devuelve cuánto se avanzó; con eso, la instrucción que toca y lo que falta.
 */
fun progreso(ruta: RutaNavegable, aqui: Punto): Progreso {
    var mejorDistancia = Double.MAX_VALUE
    var recorrido = 0.0
    var acumulado = 0.0
    for ((a, b) in ruta.puntos.zipWithNext()) {
        val largo = distanciaM(a, b)
        val (t, distancia) = proyectar(a, b, aqui)
        if (distancia < mejorDistancia) {
            mejorDistancia = distancia
            recorrido = acumulado + t * largo
        }
        acumulado += largo
    }
    if (ruta.puntos.size == 1) mejorDistancia = distanciaM(ruta.puntos.first(), aqui)
    // Las distancias de las instrucciones suman casi lo mismo que la línea: se usan en su escala
    val total = ruta.instrucciones.sumOf { it.distanciaM }.takeIf { it > 0 } ?: acumulado
    val avance = if (acumulado > 0) recorrido / acumulado * total else 0.0
    var inicio = 0.0
    var indice = ruta.instrucciones.lastIndex
    for ((i, paso) in ruta.instrucciones.withIndex()) {
        if (avance < inicio + paso.distanciaM) {
            indice = i
            break
        }
        inicio += paso.distanciaM
    }
    val actual = ruta.instrucciones.getOrNull(indice)
    val restantes = (total - avance).coerceAtLeast(0.0)
    return Progreso(
        actual = actual,
        siguiente = ruta.instrucciones.getOrNull(indice + 1),
        metrosHastaSiguiente = ((actual?.let { inicio + it.distanciaM } ?: total) - avance).coerceAtLeast(0.0),
        metrosRestantes = restantes,
        fueraDeRuta = mejorDistancia > MARGEN_FUERA_DE_RUTA_M,
        // Por la distancia real al destino: lejos de la ruta, la proyección puede caer al final
        llegando = distanciaM(aqui, ruta.puntos.last()) <= DISTANCIA_LLEGADA_M,
    )
}

/** (fracción del segmento 0–1, distancia en metros) del punto más cercano del segmento a `p`. */
private fun proyectar(a: Punto, b: Punto, p: Punto): Pair<Double, Double> {
    val escalaLon = cos(Math.toRadians(a.lat)) * METROS_POR_GRADO
    val bx = (b.lon - a.lon) * escalaLon
    val by = (b.lat - a.lat) * METROS_POR_GRADO
    val px = (p.lon - a.lon) * escalaLon
    val py = (p.lat - a.lat) * METROS_POR_GRADO
    val largo2 = bx * bx + by * by
    val t = if (largo2 == 0.0) 0.0 else ((px * bx + py * by) / largo2).coerceIn(0.0, 1.0)
    val dx = px - t * bx
    val dy = py - t * by
    return t to sqrt(dx * dx + dy * dy)
}

/** Fuera de la ruta se pide una nueva desde donde está la unidad, sin insistir más que esto. */
const val ESPERA_ENTRE_RECALCULOS_MS = 30_000L

fun debeRecalcular(progreso: Progreso?, msDesdeUltimo: Long): Boolean =
    progreso?.fueraDeRuta == true && msDesdeUltimo >= ESPERA_ENTRE_RECALCULOS_MS

/** Cada cuánto la unidad informa su posición mientras navega (para recalcular desde ahí). */
const val INFORMAR_POSICION_CADA_M = 75.0

fun debeInformarPosicion(ultima: Punto?, actual: Punto): Boolean =
    ultima == null || distanciaM(ultima, actual) >= INFORMAR_POSICION_CADA_M
