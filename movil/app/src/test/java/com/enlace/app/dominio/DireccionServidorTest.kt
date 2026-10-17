package com.enlace.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DireccionServidorTest {

    @Test
    fun `ip sola usa http y el puerto por defecto`() {
        assertEquals("http://192.168.1.10:8000/", DireccionServidor.normalizar("192.168.1.10"))
    }

    @Test
    fun `respeta puerto, esquema y barra final`() {
        assertEquals("http://192.168.1.10:9000/", DireccionServidor.normalizar(" 192.168.1.10:9000/ "))
        assertEquals("https://enlace.gob.ni:443/", DireccionServidor.normalizar("https://enlace.gob.ni:443"))
    }

    @Test
    fun `rechaza direcciones vacias o con espacios, rutas o consultas`() {
        assertNull(DireccionServidor.normalizar(""))
        assertNull(DireccionServidor.normalizar("192.168 .1.10"))
        assertNull(DireccionServidor.normalizar("192.168.1.10:8000/api"))
        assertNull(DireccionServidor.normalizar("192.168.1.10?x=1"))
    }
}
