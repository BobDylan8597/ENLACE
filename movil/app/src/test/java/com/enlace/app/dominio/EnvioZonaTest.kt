package com.enlace.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvioZonaTest {
    @Test
    fun `automatico envia la zona sin preguntar`() {
        assertNull(envioInicial(Consentimiento.AUTOMATICO))
    }

    @Test
    fun `ubicacion exacta tambien envia sin preguntar y es la unica que comparte el punto`() {
        assertNull(envioInicial(Consentimiento.UBICACION_EXACTA))
        assertTrue(compartePuntoExacto(Consentimiento.UBICACION_EXACTA))
        Consentimiento.entries.filter { it != Consentimiento.UBICACION_EXACTA }.forEach {
            assertFalse(compartePuntoExacto(it))
        }
        assertFalse(compartePuntoExacto(null))
    }

    @Test
    fun `conteo del hogar sin condiciones no tiene vulnerables`() {
        assertEquals(ConteoHogar(4, 0), Perfil(personasEnHogar = 4, personasVulnerables = 3).conteoHogar())
    }

    @Test
    fun `conteo del hogar acota los vulnerables a las personas`() {
        val conCondicion = Perfil(condiciones = setOf(Condicion.ADULTO_MAYOR), personasEnHogar = 2)
        assertEquals(ConteoHogar(2, 2), conCondicion.copy(personasVulnerables = 5).conteoHogar())
        assertEquals(ConteoHogar(2, 1), conCondicion.copy(personasVulnerables = 0).conteoHogar())
    }

    @Test
    fun `con confirmacion espera a que la persona acepte`() {
        assertEquals(EnvioZona.PENDIENTE_CONFIRMACION, envioInicial(Consentimiento.CON_CONFIRMACION))
    }

    @Test
    fun `sin consentimiento o no compartir nunca envia la zona`() {
        assertEquals(EnvioZona.NO_COMPARTIDA, envioInicial(Consentimiento.NO_COMPARTIR))
        assertEquals(EnvioZona.NO_COMPARTIDA, envioInicial(null))
    }
}
