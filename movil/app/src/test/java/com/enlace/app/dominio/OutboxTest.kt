package com.enlace.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Test

class OutboxTest {
    @Test
    fun `aceptados y repetidos quedan enviados, rechazados con su motivo`() {
        val estados = estadosDelLote(
            ids = listOf("a", "b", "c", "d"),
            aceptados = listOf("a"),
            repetidos = listOf("b"),
            rechazados = mapOf("c" to "Celda h3 inválida"),
        )
        assertEquals(EstadoEnvio.ENVIADO to null, estados["a"])
        assertEquals(EstadoEnvio.ENVIADO to null, estados["b"])
        assertEquals(EstadoEnvio.RECHAZADO to "Celda h3 inválida", estados["c"])
        // Lo que el servidor no menciona no se da por enviado
        assertEquals(EstadoEnvio.PENDIENTE to null, estados["d"])
    }

    @Test
    fun `el check-in de un evento tiene un id fijo para reemplazar el anterior`() {
        assertEquals(idCheckin(7), idCheckin(7))
        assertEquals("checkin-7", idCheckin(7))
    }

    @Test
    fun `estado de atencion desde el servidor`() {
        assertEquals(EstadoAtencion.ASIGNADO, estadoAtencion("asignado"))
        assertEquals(null, estadoAtencion("descartado"))
    }
}
