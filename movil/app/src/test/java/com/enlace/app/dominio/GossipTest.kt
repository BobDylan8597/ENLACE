package com.enlace.app.dominio

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GossipTest {
    private val gossip = Gossip(saltosMax = 10)
    private val mensaje = MensajeRed("m-1", saltos = 0, hora = "2026-10-17T10:00:00Z", tipo = TipoMensajeRed.REPORTE, payload = "{}")

    @Test
    fun `un mensaje nuevo se guarda y se reenvia con un salto mas`() {
        val decision = gossip.alRecibir(mensaje, yaVisto = false)
        assertEquals(DecisionGossip.Guardar(mensaje.copy(saltos = 1)), decision)
    }

    @Test
    fun `un mensaje ya visto se ignora (no da vueltas por la red)`() {
        assertEquals(DecisionGossip.Ignorar, gossip.alRecibir(mensaje, yaVisto = true))
    }

    @Test
    fun `al llegar al limite de saltos se guarda pero ya no se reenvia`() {
        assertEquals(DecisionGossip.Guardar(mensaje.copy(saltos = 10)), gossip.alRecibir(mensaje.copy(saltos = 9), false))
        assertEquals(DecisionGossip.Guardar(null), gossip.alRecibir(mensaje.copy(saltos = 10), false))
    }

    @Test
    fun `formato compacto con los nombres de la seccion 12`() {
        val texto = Json.encodeToString(MensajeRed.serializer(), mensaje)
        assertTrue(texto.contains("\"id_mensaje\":\"m-1\""))
        assertTrue(texto.contains("\"tipo\":\"reporte\""))
        assertEquals(mensaje, Json.decodeFromString(MensajeRed.serializer(), texto))
    }

    @Test
    fun `el limite de saltos coincide con el del servidor`() {
        assertEquals(10, SALTOS_MAXIMOS)
    }
}
