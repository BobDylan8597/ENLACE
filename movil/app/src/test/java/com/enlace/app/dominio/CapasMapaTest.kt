package com.enlace.app.dominio

import com.enlace.app.presentacion.mapa.BloqueoMapa
import com.enlace.app.presentacion.mapa.ColorMapa
import com.enlace.app.presentacion.mapa.GeoJsonCapas
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapasMapaTest {
    private fun features(geojson: String) =
        Json.parseToJsonElement(geojson).jsonObject.getValue("features").jsonArray.map { it.jsonObject }

    private fun propiedad(feature: kotlinx.serialization.json.JsonObject, nombre: String) =
        feature.getValue("properties").jsonObject.getValue(nombre).jsonPrimitive.content

    @Test
    fun `los puntos van en lon lat y cada estado tiene color y simbolo`() {
        val personas = listOf(
            PersonaUbicada(Punto(12.1, -86.2), "ayuda", 2, 1),
            PersonaUbicada(Punto(12.2, -86.3), "sin_respuesta", 1, 0),
        )
        val (ayuda, sinRespuesta) = features(GeoJsonCapas.personas(personas))
        val coordenadas = ayuda.getValue("geometry").jsonObject.getValue("coordinates").jsonArray
        assertEquals(-86.2, coordenadas[0].jsonPrimitive.double, 0.0)
        assertEquals(12.1, coordenadas[1].jsonPrimitive.double, 0.0)
        assertEquals(ColorMapa.ACTUAR, propiedad(ayuda, "color"))
        assertEquals("!", propiedad(ayuda, "texto"))
        // Hogar con personas vulnerables: anillo oscuro
        assertEquals("#111827", propiedad(ayuda, "borde"))
        assertEquals("?", propiedad(sinRespuesta, "texto"))
        assertEquals(ColorMapa.SIN_DATOS, propiedad(sinRespuesta, "color"))
    }

    @Test
    fun `zonas criticas cerradas y solo las que tienen poblacion critica`() {
        val celda = CeldaH3("a", listOf(0.0 to 0.0, 0.0 to 1.0, 1.0 to 1.0))
        val zonas = features(GeoJsonCapas.zonasCriticas(listOf(celda to 3, celda.copy(id = "b") to 0)))
        assertEquals(1, zonas.size)
        val anillo = zonas[0].getValue("geometry").jsonObject.getValue("coordinates").jsonArray[0].jsonArray
        assertEquals(anillo.first(), anillo.last())
        assertEquals("3", propiedad(zonas[0], "texto"))
    }

    @Test
    fun `bloqueos descartados no se dibujan y los confirmados son rojos`() {
        val bloqueos = listOf(
            BloqueoMapa(Punto(1.0, 1.0), "confirmado"),
            BloqueoMapa(Punto(1.0, 1.0), "sin_confirmar"),
            BloqueoMapa(Punto(1.0, 1.0), "descartado"),
        )
        val dibujados = features(GeoJsonCapas.bloqueos(bloqueos))
        assertEquals(listOf(ColorMapa.ACTUAR, ColorMapa.EXPLORAR), dibujados.map { propiedad(it, "color") })
    }

    @Test
    fun `sin datos las capas quedan vacias`() {
        assertTrue(features(GeoJsonCapas.ruta(listOf(Punto(1.0, 1.0)))).isEmpty())
        assertTrue(features(GeoJsonCapas.destino(null)).isEmpty())
        assertTrue(features(GeoJsonCapas.yo(null)).isEmpty())
    }
}
