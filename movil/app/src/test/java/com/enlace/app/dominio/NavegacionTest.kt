package com.enlace.app.dominio

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavegacionTest {
    private val json = Json { ignoreUnknownKeys = true }

    // Ruta en "L": ~556 m hacia el norte y ~556 m hacia el este (0,005° en Managua)
    private val geojson = """
        {"type": "Feature",
         "geometry": {"type": "LineString", "coordinates": [[-86.25, 12.13], [-86.25, 12.135], [-86.24489, 12.135]]},
         "properties": {"tiempo_min": 3.2, "distancia_m": 1112, "instrucciones": [
            {"texto": "Sal por Calle Norte y sigue 550 m", "distancia_m": 556},
            {"texto": "Gira a la derecha en Calle Este y sigue 550 m", "distancia_m": 556},
            {"texto": "Llegas al destino", "distancia_m": 0}],
          "tramos": [], "destino": [12.135, -86.24489]}}
    """.trimIndent()

    private fun ruta() = requireNotNull(leerRuta(geojson, json))

    @Test
    fun `lee la ruta del servidor`() {
        val ruta = ruta()
        assertEquals(3, ruta.puntos.size)
        assertEquals(Punto(12.13, -86.25), ruta.puntos.first())
        assertEquals(3, ruta.instrucciones.size)
        assertEquals(1112.0, ruta.distanciaM, 0.1)
        assertNull(leerRuta(null, json))
        assertNull(leerRuta("{no es json", json))
    }

    @Test
    fun `al salir toca la primera instruccion`() {
        val p = progreso(ruta(), Punto(12.13, -86.25))
        assertEquals("Sal por Calle Norte y sigue 550 m", p.actual?.texto)
        assertEquals("Gira a la derecha en Calle Este y sigue 550 m", p.siguiente?.texto)
        assertEquals(556.0, p.metrosHastaSiguiente, 5.0)
        assertEquals(1112.0, p.metrosRestantes, 5.0)
        assertFalse(p.fueraDeRuta)
        assertFalse(p.llegando)
    }

    @Test
    fun `pasado el giro toca la segunda instruccion`() {
        val p = progreso(ruta(), Punto(12.135, -86.2475))
        assertEquals("Gira a la derecha en Calle Este y sigue 550 m", p.actual?.texto)
        assertTrue(p.metrosRestantes in 250.0..300.0)
    }

    @Test
    fun `cerca del destino avisa que llega`() {
        val p = progreso(ruta(), Punto(12.135, -86.2449))
        assertTrue(p.llegando)
    }

    @Test
    fun `lejos de la ruta no cuenta como llegada aunque se proyecte al final`() {
        // Más allá del destino, a 500 m: la proyección cae en el final de la ruta
        val p = progreso(ruta(), Punto(12.135, -86.2403))
        assertFalse(p.llegando)
        assertTrue(p.fueraDeRuta)
    }

    @Test
    fun `lejos de la ruta lo marca fuera de ruta`() {
        val p = progreso(ruta(), Punto(12.132, -86.2470))
        assertTrue(p.fueraDeRuta)
    }

    @Test
    fun `fuera de ruta se recalcula pero no mas seguido que la espera`() {
        val fuera = progreso(ruta(), Punto(12.132, -86.2470))
        val dentro = progreso(ruta(), Punto(12.13, -86.25))
        assertTrue(debeRecalcular(fuera, ESPERA_ENTRE_RECALCULOS_MS))
        assertFalse(debeRecalcular(fuera, ESPERA_ENTRE_RECALCULOS_MS - 1))
        assertFalse(debeRecalcular(dentro, ESPERA_ENTRE_RECALCULOS_MS))
        assertFalse(debeRecalcular(null, ESPERA_ENTRE_RECALCULOS_MS))
    }

    @Test
    fun `la posicion se informa solo al moverse lo suficiente`() {
        val a = Punto(12.13, -86.25)
        assertTrue(debeInformarPosicion(null, a))
        assertFalse(debeInformarPosicion(a, Punto(12.1302, -86.25)))
        assertTrue(debeInformarPosicion(a, Punto(12.131, -86.25)))
    }
}
