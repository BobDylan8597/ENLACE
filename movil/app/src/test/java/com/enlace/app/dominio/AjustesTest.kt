package com.enlace.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Test

class AjustesTest {
    private val servidor = "http://192.168.1.10:8000/"

    @Test
    fun `sin servidor configurado abre la configuracion`() {
        assertEquals(Destino.CONFIGURACION, destinoInicial(null, Modo.USUARIO, Consentimiento.AUTOMATICO))
    }

    @Test
    fun `con servidor pero sin modo pide elegirlo`() {
        assertEquals(Destino.MODO, destinoInicial(servidor, null, null))
    }

    @Test
    fun `el consentimiento se pide antes del inicio y en pantalla propia`() {
        assertEquals(Destino.CONSENTIMIENTO, destinoInicial(servidor, Modo.USUARIO, null))
        assertEquals(
            Destino.INICIO_USUARIO,
            destinoInicial(servidor, Modo.USUARIO, Consentimiento.NO_COMPARTIR),
        )
    }

    @Test
    fun `el perfil por defecto no tiene datos personales`() {
        val perfil = Perfil()
        assertEquals("", perfil.nombre)
        assertEquals("", perfil.telefono)
        assertEquals(emptySet<Condicion>(), perfil.condiciones)
    }
}
