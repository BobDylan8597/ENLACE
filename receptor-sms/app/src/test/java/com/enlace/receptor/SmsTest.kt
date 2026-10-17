package com.enlace.receptor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsTest {
    @Test
    fun `solo los sms con formato ENL se reenvian`() {
        assertTrue(esReporteEnlace("ENL 12.13,-86.25 P4 A1 H1 RM"))
        assertTrue(esReporteEnlace("  enl 12.13,-86.25 P1"))
        assertFalse(esReporteEnlace("Hola, ¿cómo estás?"))
        assertFalse(esReporteEnlace("ENLACE promoción"))
        assertFalse(esReporteEnlace("ENL"))
        assertFalse(esReporteEnlace(""))
    }

    @Test
    fun `las partes de un sms largo se unen por remitente`() {
        val partes = listOf("+505111" to "ENL 12.13,", "+505222" to "Hola", "+505111" to "-86.25 P4")
        assertEquals(listOf("+505111" to "ENL 12.13,-86.25 P4", "+505222" to "Hola"), unirPartes(partes))
    }

    @Test
    fun `en pantalla el numero se ve oculto`() {
        assertEquals("•••1234", ocultarNumero("+505 8888 1234"))
        assertEquals("•••", ocultarNumero("123"))
    }

    @Test
    fun `la respuesta del servidor decide si se reintenta`() {
        assertEquals(EstadoSms.ENTREGADO, estadoTrasRespuesta(200))
        assertEquals(EstadoSms.RECHAZADO, estadoTrasRespuesta(422))
        assertEquals(EstadoSms.PENDIENTE, estadoTrasRespuesta(401))
        assertEquals(EstadoSms.PENDIENTE, estadoTrasRespuesta(503))
    }

    @Test
    fun `la direccion se completa con http y el puerto 8000`() {
        assertEquals("http://192.168.1.10:8000/", normalizarDireccion("192.168.1.10"))
        assertEquals("http://192.168.1.10:9000/", normalizarDireccion(" 192.168.1.10:9000/ "))
        assertEquals(null, normalizarDireccion("192.168. 1.10"))
        assertEquals(null, normalizarDireccion(""))
    }
}
