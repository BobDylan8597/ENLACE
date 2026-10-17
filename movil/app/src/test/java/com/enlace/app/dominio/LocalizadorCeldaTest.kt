package com.enlace.app.dominio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Celdas reales de Managua (resolución 8), con los contornos que calcula h3 en el servidor. */
class LocalizadorCeldaTest {
    private val centro = CeldaH3(
        "886d617767fffff",
        listOf(
            12.141978639324112 to -86.24822213690076,
            12.13939798029136 to -86.25235735967836,
            12.134896010417254 to -86.2520205574766,
            12.132974701857332 to -86.24754840042903,
            12.135555513089278 to -86.24341310055135,
            12.14005748066784 to -86.24375003481799,
        ),
    )
    private val vecina = CeldaH3(
        "886d617763fffff",
        listOf(
            12.142638006842413 to -86.23961484064446,
            12.14005748066784 to -86.24375003481799,
            12.135555513089278 to -86.24341310055135,
            12.133634074051376 to -86.23894084005593,
            12.136214752456484 to -86.23480556888825,
            12.140716717654854 to -86.23514263520683,
        ),
    )
    private val localizador = LocalizadorCelda(listOf(centro, vecina))

    @Test
    fun `ubica el punto en la misma celda que h3`() {
        assertEquals("886d617767fffff", localizador.celdaDe(12.1370, -86.2510))
        assertEquals("886d617763fffff", localizador.celdaDe(12.1364, -86.2414))
    }

    @Test
    fun `fuera de la zona preparada no hay celda`() {
        assertNull(localizador.celdaDe(13.0, -86.0))
    }
}
