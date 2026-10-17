package com.enlace.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnvioZonaTest {
    @Test
    fun `automatico envia la zona sin preguntar`() {
        assertNull(envioInicial(Consentimiento.AUTOMATICO))
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
