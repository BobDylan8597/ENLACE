package com.enlace.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AjustesTest {
    private val servidor = "http://192.168.1.10:8000/"

    @Test
    fun `la primera vez abre la bienvenida`() {
        assertEquals(Destino.BIENVENIDA, destinoInicial(null, Modo.USUARIO, Consentimiento.AUTOMATICO))
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
    fun `el modo unidad pide la clave hasta activarse y no pide consentimiento`() {
        assertEquals(Destino.ACTIVAR_UNIDAD, destinoInicial(servidor, Modo.UNIDAD, null))
        assertEquals(Destino.INICIO_UNIDAD, destinoInicial(servidor, Modo.UNIDAD, null, unidadActiva = true))
    }

    @Test
    fun `el aviso de inicio automatico solo aparece en marcas que lo bloquean`() {
        assertTrue(necesitaInicioAutomatico("Xiaomi"))
        assertTrue(necesitaInicioAutomatico("HONOR"))
        assertFalse(necesitaInicioAutomatico("samsung"))
        assertFalse(necesitaInicioAutomatico("Google"))
    }

    @Test
    fun `el perfil por defecto no tiene datos personales`() {
        val perfil = Perfil()
        assertEquals("", perfil.nombre)
        assertEquals("", perfil.telefono)
        assertEquals(emptySet<Condicion>(), perfil.condiciones)
    }
}
