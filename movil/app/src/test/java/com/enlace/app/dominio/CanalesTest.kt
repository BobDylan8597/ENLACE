package com.enlace.app.dominio

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CanalesTest {
    private val pedido = PedidoParaEnviar(
        idMensaje = "m-1",
        lat = 13.1,
        lon = -85.2,
        datos = DatosPedido(personas = 4, atrapados = 1, heridos = 1, necesidades = setOf(Necesidad.MEDICO, Necesidad.RESCATE)),
    )

    @Test
    fun `texto sms con el formato que interpreta el servidor`() {
        // Mismo ejemplo que CLAUDE.md, sección 14 (necesidades en orden fijo: R, M, W, F)
        assertEquals("ENL 13.10000,-85.20000 P4 A1 H1 RM", textoSms(13.1, -85.2, pedido.datos))
    }

    @Test
    fun `sin necesidades el sms termina en los conteos`() {
        assertEquals("ENL 12.13640,-86.25140 P1 A0 H0", textoSms(12.1364, -86.2514, DatosPedido()))
    }

    @Test
    fun `la cadena se queda con el primer canal que entrega`() = runTest {
        val usados = mutableListOf<String>()
        val servidor = CanalEnvio { usados += "servidor"; ResultadoCanal.Entregado }
        val sms = CanalEnvio { usados += "sms"; ResultadoCanal.OfrecerSms("1", "x") }
        assertEquals(ResultadoCanal.Entregado, CadenaCanales(listOf(servidor, sms)).enviar(pedido))
        assertEquals(listOf("servidor"), usados)
    }

    @Test
    fun `sin servidor ni red cercana se ofrece el sms`() = runTest {
        val sinServidor = CanalEnvio { ResultadoCanal.NoDisponible }
        val sinRedCercana = CanalEnvio { ResultadoCanal.NoDisponible }
        val resultado = CadenaCanales(listOf(sinServidor, sinRedCercana, CanalSms { "+50588880000" })).enviar(pedido)
        assertEquals(ResultadoCanal.OfrecerSms("+50588880000", "ENL 13.10000,-85.20000 P4 A1 H1 RM"), resultado)
    }

    @Test
    fun `sin numero de receptor no hay sms que ofrecer`() = runTest {
        assertEquals(ResultadoCanal.NoDisponible, CadenaCanales(listOf(CanalSms { null })).enviar(pedido))
    }
}
