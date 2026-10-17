package com.enlace.app.dominio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Tipos de mensaje que viajan por la red cercana (sección 12). */
@Serializable
enum class TipoMensajeRed {
    @SerialName("reporte") REPORTE,
    @SerialName("checkin") CHECKIN,
    @SerialName("bloqueo") BLOQUEO,
    @SerialName("alerta") ALERTA,
}

/**
 * Mensaje compacto de la red Nearby: `payload` es el JSON tal como se enviará al servidor
 * (o la alerta firmada, que cada celular vuelve a verificar).
 */
@Serializable
data class MensajeRed(
    @SerialName("id_mensaje") val idMensaje: String,
    val saltos: Int,
    val hora: String,
    val tipo: TipoMensajeRed,
    val payload: String,
)

sealed interface DecisionGossip {
    /** Ya se había visto (por otra ruta de la red): no se hace nada. */
    data object Ignorar : DecisionGossip

    /** Nuevo: se guarda; si `reenviar` no es null, se pasa a los demás celulares. */
    data class Guardar(val reenviar: MensajeRed?) : DecisionGossip
}

/**
 * Propagación epidémica (gossip): cada celular guarda lo nuevo y lo reenvía con un salto más,
 * hasta un máximo, para que el mensaje se extienda sin dar vueltas para siempre.
 */
class Gossip(private val saltosMax: Int) {
    fun alRecibir(mensaje: MensajeRed, yaVisto: Boolean): DecisionGossip {
        if (yaVisto) return DecisionGossip.Ignorar
        val siguiente = mensaje.saltos + 1
        return DecisionGossip.Guardar(if (siguiente <= saltosMax) mensaje.copy(saltos = siguiente) else null)
    }
}

/** Límite de saltos de la red (igual al del servidor: [nearby] saltos_max). */
const val SALTOS_MAXIMOS = 10
