package com.enlace.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportesTest {
    private val nombres = mapOf(
        Condicion.ADULTO_MAYOR to "Adulto mayor",
        Condicion.MEDICACION to "Medicación diaria",
    )

    @Test
    fun `perfil vacio no envia datos personales`() {
        assertNull(identidadDesde(Perfil()) { nombres.getValue(it) })
    }

    @Test
    fun `perfil con datos arma la identidad del pedido`() {
        val perfil = Perfil(
            nombre = " Ana Lopez ",
            telefono = "+505 8888 0000",
            condiciones = setOf(Condicion.MEDICACION, Condicion.ADULTO_MAYOR),
            personasEnHogar = 3,
        )
        val identidad = identidadDesde(perfil) { nombres.getValue(it) }
        assertEquals("Ana Lopez", identidad?.nombre)
        assertEquals("+505 8888 0000", identidad?.telefono)
        assertEquals("Adulto mayor, Medicación diaria. 3 personas en el hogar", identidad?.condiciones)
    }

    @Test
    fun `solo condiciones sin nombre tambien se envian`() {
        val identidad = identidadDesde(Perfil(condiciones = setOf(Condicion.ADULTO_MAYOR))) { nombres.getValue(it) }
        assertNull(identidad?.nombre)
        assertEquals("Adulto mayor", identidad?.condiciones)
    }

    @Test
    fun `vulnerables sugeridos segun el perfil`() {
        assertEquals(0, vulnerablesSugeridos(Perfil()))
        assertEquals(1, vulnerablesSugeridos(Perfil(condiciones = setOf(Condicion.EMBARAZO))))
    }

    @Test
    fun `alcance del bloqueo segun si pasa una persona a pie`() {
        assertEquals("solo_a_pie", alcanceBloqueo(pasaAPie = true))
        assertEquals("total", alcanceBloqueo(pasaAPie = false))
    }
}
