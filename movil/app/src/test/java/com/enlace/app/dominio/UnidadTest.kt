package com.enlace.app.dominio

import com.enlace.app.datos.remoto.AvisoAsignacionDto
import com.enlace.app.presentacion.unidad.textoDistancia
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class UnidadTest {
    private val aqui = Punto(12.1364, -86.2514)

    @Test
    fun `la distancia entre dos puntos cercanos es la esperada`() {
        // 0,01° de latitud son unos 1112 m
        assertEquals(1112.0, distanciaM(aqui, Punto(aqui.lat + 0.01, aqui.lon)), 5.0)
        assertEquals(0.0, distanciaM(aqui, aqui), 0.001)
    }

    @Test
    fun `zonas con mas poblacion critica primero y sin las que no tienen`() {
        val panorama = PanoramaUnidad(
            zonas = listOf(
                ZonaCritica("a", "Lejos", 5, 20, Punto(aqui.lat + 0.05, aqui.lon)),
                ZonaCritica("b", "Cerca", 5, 20, Punto(aqui.lat + 0.001, aqui.lon)),
                ZonaCritica("c", "Más críticos", 9, 30, Punto(aqui.lat + 0.1, aqui.lon)),
                ZonaCritica("d", "Sin críticos", 0, 40, aqui),
            ),
            personas = emptyList(),
            pedidos = emptyList(),
        )
        val zonas = ordenarPanorama(panorama, aqui, 10).zonas
        assertEquals(listOf("c", "b", "a"), zonas.map { it.idH3 })
    }

    @Test
    fun `personas que pidieron ayuda primero luego vulnerables y sin las que estan bien`() {
        val cerca = Punto(aqui.lat + 0.001, aqui.lon)
        val lejos = Punto(aqui.lat + 0.02, aqui.lon)
        val panorama = PanoramaUnidad(
            zonas = emptyList(),
            personas = listOf(
                PersonaUbicada(cerca, "sin_respuesta", 2, 0),
                PersonaUbicada(lejos, "sin_respuesta", 3, 1),
                PersonaUbicada(lejos, "ayuda", 1, 0),
                PersonaUbicada(cerca, "bien", 4, 2),
            ),
            pedidos = emptyList(),
        )
        val personas = ordenarPanorama(panorama, aqui, 10).personas
        assertEquals(listOf("ayuda" to 0, "sin_respuesta" to 1, "sin_respuesta" to 0), personas.map { it.estado to it.vulnerables })
    }

    @Test
    fun `sin ubicacion de la unidad se ordena igual pero sin distancias y respeta el maximo`() {
        val pedidos = (1..8).map { PedidoPendiente("p$it", aqui, "Zona", "sin_asignar", Atender()) }
        val ordenado = ordenarPanorama(PanoramaUnidad(emptyList(), emptyList(), pedidos), null, 5)
        assertEquals(5, ordenado.pedidos.size)
        assertNull(ordenado.pedidos.first().distanciaM)
    }

    @Test
    fun `centro de una celda es el promedio de sus vertices`() {
        val celda = CeldaH3("x", listOf(0.0 to 0.0, 0.0 to 2.0, 2.0 to 2.0, 2.0 to 0.0))
        assertEquals(Punto(1.0, 1.0), centroDe(celda))
        assertNull(centroDe(CeldaH3("vacia", emptyList())))
    }

    @Test
    fun `una asignacion terminada o rechazada ya no esta en curso`() {
        assertEquals(true, enCurso("aceptada", null))
        assertEquals(true, enCurso("modificada", null))
        assertEquals(false, enCurso("aceptada", "2026-10-17T10:30:00Z"))
        assertEquals(false, enCurso("rechazada", null))
        assertEquals(4, minutosRedondeados(3.6))
    }

    @Test
    fun `distancias legibles`() {
        assertEquals("350 m", textoDistancia(350.4))
        assertEquals("1,2 km", textoDistancia(1234.0, Locale.forLanguageTag("es")))
    }

    @Test
    fun `el aviso de asignacion del servidor se interpreta`() {
        val texto = """
            {"asignacion": {"id": 7, "id_equipo": 2, "nombre_equipo": "Cruz Roja 2", "id_reporte": "r-1",
              "id_h3": "886d6172b5fffff", "motivo": "2 atrapados", "tiempo_estimado_min": 6.4,
              "decision": "aceptada", "decidido_por": "Ana", "hora": "2026-10-17T10:00:00Z",
              "hora_fin": null, "ruta": {"type": "FeatureCollection", "features": []}},
             "destino": {"lat": 12.15, "lon": -86.26}, "nombre_zona": "Villa Fontana",
             "pedido": {"tipo": "ayuda", "personas": 3, "atrapados": 2, "heridos": 0, "vulnerables": 1,
              "necesidades": ["rescate"]},
             "contacto": {"nombre": "Ana", "telefono": "+50588880000", "condiciones": null}}
        """.trimIndent()
        val aviso = Json { ignoreUnknownKeys = true }.decodeFromString<AvisoAsignacionDto>(texto)
        assertEquals(7, aviso.asignacion.id)
        assertEquals("Villa Fontana", aviso.nombreZona)
        assertEquals(2, aviso.pedido?.atrapados)
        assertEquals("+50588880000", aviso.contacto?.telefono)
    }
}
