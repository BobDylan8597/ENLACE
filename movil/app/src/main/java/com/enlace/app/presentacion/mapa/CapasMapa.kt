package com.enlace.app.presentacion.mapa

import com.enlace.app.dominio.CeldaH3
import com.enlace.app.dominio.ESTADO_AYUDA
import com.enlace.app.dominio.ESTADO_BIEN
import com.enlace.app.dominio.PedidoPendiente
import com.enlace.app.dominio.PersonaUbicada
import com.enlace.app.dominio.Punto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.math.exp

/** Colores con significado fijo (sección 16), los mismos de la web. */
object ColorMapa {
    const val ACTUAR = "#C62828"
    const val EXPLORAR = "#B45309"
    const val ESTABLE = "#2E7D32"
    const val SIN_DATOS = "#6B7280"
    const val ACCION = "#1D4ED8"
}

data class BloqueoMapa(val punto: Punto, val estado: String)

/** Todo lo que puede dibujarse sobre el mapa de la ciudad. */
data class CapasMapa(
    /** Celdas con su población crítica (vulnerables sin responder o pidiendo ayuda). */
    val zonasCriticas: List<Pair<CeldaH3, Int>> = emptyList(),
    /** Zona afectada de la alerta (mapa ciudadano). */
    val zonaAfectada: List<CeldaH3> = emptyList(),
    val personas: List<PersonaUbicada> = emptyList(),
    val pedidos: List<PedidoPendiente> = emptyList(),
    val bloqueos: List<BloqueoMapa> = emptyList(),
    val ruta: List<Punto> = emptyList(),
    val destino: Punto? = null,
    val yo: Punto? = null,
)

/**
 * GeoJSON de cada capa. El estilo de cada punto (color, texto) va en sus propiedades: el mapa
 * solo las lee, así las reglas quedan aquí y se prueban sin Android. El color nunca va solo:
 * cada punto lleva además un símbolo ("!", "?", "×") o un número (daltonismo).
 */
object GeoJsonCapas {
    private const val ESCALA_CRITICOS = 10.0

    fun zonasCriticas(zonas: List<Pair<CeldaH3, Int>>): String = coleccion(
        zonas.filter { it.second > 0 }.map { (celda, criticos) ->
            poligono(celda.contorno) {
                put("color", ColorMapa.ACTUAR)
                put("opacidad", 0.15 + 0.5 * (1 - exp(-criticos / ESCALA_CRITICOS)))
                put("texto", criticos.toString())
            }
        },
    )

    fun zonaAfectada(celdas: List<CeldaH3>): String = coleccion(
        celdas.map { celda ->
            poligono(celda.contorno) {
                put("color", ColorMapa.EXPLORAR)
                put("opacidad", 0.18)
                put("texto", "")
            }
        },
    )

    fun personas(personas: List<PersonaUbicada>): String = coleccion(
        personas.map { p ->
            punto(p.punto) {
                put("color", colorEstado(p.estado))
                put("texto", simboloEstado(p.estado))
                // Anillo oscuro: hogar con personas que necesitan atención especial
                put("borde", if (p.vulnerables > 0) "#111827" else "#FFFFFF")
                put("radio", 8)
            }
        },
    )

    fun pedidos(pedidos: List<PedidoPendiente>): String = coleccion(
        pedidos.map { p ->
            punto(p.punto) {
                put("color", ColorMapa.ACTUAR)
                put("texto", "!")
                put("borde", "#FFFFFF")
                put("radio", 11)
            }
        },
    )

    fun bloqueos(bloqueos: List<BloqueoMapa>): String = coleccion(
        bloqueos.filter { it.estado != "descartado" }.map { b ->
            punto(b.punto) {
                put("color", if (b.estado == "confirmado") ColorMapa.ACTUAR else ColorMapa.EXPLORAR)
                put("texto", "×")
                put("borde", "#FFFFFF")
                put("radio", 10)
            }
        },
    )

    fun ruta(puntos: List<Punto>): String = coleccion(
        if (puntos.size < 2) emptyList()
        else listOf(
            buildJsonObject {
                put("type", "Feature")
                putJsonObject("geometry") {
                    put("type", "LineString")
                    put("coordinates", coordenadas(puntos))
                }
                putJsonObject("properties") { put("color", ColorMapa.ACCION) }
            },
        ),
    )

    fun destino(destino: Punto?): String = coleccion(
        listOfNotNull(
            destino?.let {
                punto(it) {
                    put("color", ColorMapa.ACTUAR)
                    put("texto", "!")
                    put("borde", "#FFFFFF")
                    put("radio", 13)
                }
            },
        ),
    )

    fun yo(yo: Punto?): String = coleccion(
        listOfNotNull(
            yo?.let {
                punto(it) {
                    put("color", ColorMapa.ACCION)
                    put("texto", "")
                    put("borde", "#FFFFFF")
                    put("radio", 9)
                }
            },
        ),
    )

    fun colorEstado(estado: String): String = when (estado) {
        ESTADO_AYUDA -> ColorMapa.ACTUAR
        ESTADO_BIEN -> ColorMapa.ESTABLE
        else -> ColorMapa.SIN_DATOS
    }

    private fun simboloEstado(estado: String): String = when (estado) {
        ESTADO_AYUDA -> "!"
        ESTADO_BIEN -> ""
        else -> "?"
    }

    private fun coleccion(elementos: List<JsonObject>): String = buildJsonObject {
        put("type", "FeatureCollection")
        put("features", JsonArray(elementos))
    }.toString()

    private fun punto(p: Punto, propiedades: JsonObjectBuilder.() -> Unit) = buildJsonObject {
        put("type", "Feature")
        putJsonObject("geometry") {
            put("type", "Point")
            putJsonArray("coordinates") {
                add(JsonPrimitive(p.lon))
                add(JsonPrimitive(p.lat))
            }
        }
        putJsonObject("properties", propiedades)
    }

    private fun poligono(
        contorno: List<Pair<Double, Double>>,
        propiedades: JsonObjectBuilder.() -> Unit,
    ) = buildJsonObject {
        put("type", "Feature")
        putJsonObject("geometry") {
            put("type", "Polygon")
            // GeoJSON: anillo cerrado, en (lon, lat)
            val anillo = contorno.map { (lat, lon) -> Punto(lat, lon) }.let { it + it.take(1) }
            putJsonArray("coordinates") { add(coordenadas(anillo)) }
        }
        putJsonObject("properties", propiedades)
    }

    private fun coordenadas(puntos: List<Punto>): JsonArray = buildJsonArray {
        puntos.forEach { p ->
            addJsonArray {
                add(JsonPrimitive(p.lon))
                add(JsonPrimitive(p.lat))
            }
        }
    }
}
