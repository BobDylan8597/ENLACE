package com.enlace.app.dominio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Alerta tal como llega: el texto exacto del contenido y su firma (servidor: app/activacion/firma.py). */
@Serializable
data class AlertaFirmada(val contenido: String, val firma: String, val algoritmo: String)

/** Contenido de una alerta oficial ya verificada. */
@Serializable
data class Alerta(
    @SerialName("tipo_mensaje") val tipoMensaje: String,
    @SerialName("id_evento") val idEvento: Int,
    val tipo: String,
    val fuente: String,
    val magnitud: Double? = null,
    val lat: Double,
    val lon: Double,
    val hora: String,
    @SerialName("zona_afectada") val zonaAfectada: List<String> = emptyList(),
)

/** Qué pasó con la zona (check-in) de este celular para la alerta vigente. */
@Serializable
enum class EnvioZona {
    /** Consentimiento "con confirmación": la pantalla de alerta pregunta antes de enviar. */
    PENDIENTE_CONFIRMACION,
    ENVIANDO,
    ENVIADA,

    /** Se envió la zona y el punto exacto (consentimiento "ubicación exacta"). */
    UBICACION_ENVIADA,
    NO_COMPARTIDA,
    SIN_UBICACION,
    FUERA_DE_ZONA,
    SIN_CONEXION,
}

/** Alerta activa en este celular y lo que la persona ya respondió. */
@Serializable
data class AlertaVigente(
    val alerta: Alerta,
    val firmada: AlertaFirmada,
    val envioZona: EnvioZona,
    val estoyBien: Boolean = false,
)

/** Qué hacer con la zona al recibir una alerta válida, según el consentimiento (sección 13). */
fun envioInicial(consentimiento: Consentimiento?): EnvioZona? = when (consentimiento) {
    Consentimiento.UBICACION_EXACTA, Consentimiento.AUTOMATICO -> null // se envía ya mismo
    Consentimiento.CON_CONFIRMACION -> EnvioZona.PENDIENTE_CONFIRMACION
    Consentimiento.NO_COMPARTIR, null -> EnvioZona.NO_COMPARTIDA
}

/** Solo con esta opción el punto exacto sale del celular junto con la zona. */
fun compartePuntoExacto(consentimiento: Consentimiento?): Boolean =
    consentimiento == Consentimiento.UBICACION_EXACTA

fun interface VerificadorFirma {
    fun esValida(contenido: String, firmaBase64: String): Boolean
}

/**
 * La app descarta toda alerta sin firma válida (sección 11). Primero se verifican los bytes
 * exactos del contenido y solo después se interpreta el JSON.
 */
class LectorAlertas(private val verificador: VerificadorFirma, private val json: Json) {
    fun leer(firmada: AlertaFirmada): Alerta? {
        if (firmada.algoritmo != ALGORITMO) return null
        if (!verificador.esValida(firmada.contenido, firmada.firma)) return null
        val alerta = runCatching { json.decodeFromString<Alerta>(firmada.contenido) }.getOrNull()
        return alerta?.takeIf { it.tipoMensaje == TIPO_ALERTA }
    }

    private companion object {
        const val ALGORITMO = "Ed25519"
        const val TIPO_ALERTA = "alerta"
    }
}
