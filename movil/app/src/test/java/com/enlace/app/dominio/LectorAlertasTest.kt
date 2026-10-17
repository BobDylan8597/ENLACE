package com.enlace.app.dominio

import com.enlace.app.datos.seguridad.VerificadorEd25519
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** La alerta de prueba la firmó el servidor real (app/activacion/firma.py): prueba que Python y Kotlin se entienden. */
class LectorAlertasTest {
    private val clave = "ey8ciBDrRDi/bwcbckBgYFR78/o3Hc66O+09pb5IIZw="
    private val contenido =
        "{\"tipo_mensaje\":\"alerta\",\"id_evento\":7,\"tipo\":\"sismo\",\"fuente\":\"manual\",\"magnitud\":6.2," +
            "\"lat\":12.145,\"lon\":-86.268,\"hora\":\"2026-10-16T14:00:00+00:00\",\"zona_afectada\":[\"88283082bffffff\"]}"
    private val firma =
        "YbffGblWolLEHJ9x8hHRxgZVNHtRM4NOlMz26++8B4zOJ9VqM1pPM5+8P8exRfeSv0E/JpGC8MQwyI73bgEICQ=="
    private val lector = LectorAlertas(VerificadorEd25519(clave), Json { ignoreUnknownKeys = true })

    @Test
    fun `alerta firmada por el servidor se acepta y se interpreta`() {
        val alerta = lector.leer(AlertaFirmada(contenido, firma, "Ed25519"))
        assertEquals(7, alerta?.idEvento)
        assertEquals("sismo", alerta?.tipo)
        assertEquals(6.2, alerta?.magnitud)
        assertEquals(listOf("88283082bffffff"), alerta?.zonaAfectada)
    }

    @Test
    fun `contenido alterado se descarta`() {
        assertNull(lector.leer(AlertaFirmada(contenido.replace("6.2", "8.2"), firma, "Ed25519")))
    }

    @Test
    fun `firma de otra clave, invalida o de otro algoritmo se descarta`() {
        val otraClave = LectorAlertas(
            VerificadorEd25519("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="),
            Json { ignoreUnknownKeys = true },
        )
        assertNull(otraClave.leer(AlertaFirmada(contenido, firma, "Ed25519")))
        assertNull(lector.leer(AlertaFirmada(contenido, "esto-no-es-base64!", "Ed25519")))
        assertNull(lector.leer(AlertaFirmada(contenido, firma, "RSA")))
    }
}
