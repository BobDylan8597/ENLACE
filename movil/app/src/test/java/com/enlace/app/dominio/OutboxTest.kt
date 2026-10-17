package com.enlace.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    fun `el check-in de una persona en un evento tiene un id fijo y propio`() {
        assertEquals(idCheckin(7, "a"), idCheckin(7, "a"))
        assertEquals("checkin-7-a", idCheckin(7, "a"))
        assertNotEquals(idCheckin(7, "a"), idCheckin(7, "b"))
    }

    @Test
    fun `estado de atencion desde el servidor`() {
        assertEquals(EstadoAtencion.ASIGNADO, estadoAtencion("asignado"))
        assertEquals(null, estadoAtencion("descartado"))
    }
}
