package com.enlace.app.dominio

/** Qué se guarda en el Outbox: todo se guarda en el celular primero y se envía después. */
enum class TipoMensaje { REPORTE, BLOQUEO, CHECKIN }

enum class EstadoEnvio {
    /** Guardado en el celular, esperando conexión. */
    PENDIENTE,

    /** El servidor lo tiene (aceptado, o ya lo había recibido por otra vía). */
    ENVIADO,

    /** El servidor lo rechazó: reintentar no sirve. */
    RECHAZADO,
}

/**
 * Resultado de un lote de reportes enviado a POST /reportes. `repetidos` también cuenta como
 * enviado: otra vía (otra mula de datos, un SMS) ya lo había entregado. Lo que el servidor no
 * menciona queda pendiente.
 */
fun estadosDelLote(
    ids: List<String>,
    aceptados: Collection<String>,
    repetidos: Collection<String>,
    rechazados: Map<String?, String>,
): Map<String, Pair<EstadoEnvio, String?>> = ids.associateWith { id ->
    when (id) {
        in aceptados, in repetidos -> EstadoEnvio.ENVIADO to null
        in rechazados -> EstadoEnvio.RECHAZADO to rechazados[id]
        else -> EstadoEnvio.PENDIENTE to null
    }
}

/** Estado de atención de un pedido propio, para el seguimiento (U12). */
enum class EstadoAtencion { SIN_ASIGNAR, ASIGNADO, ATENDIDO }

fun estadoAtencion(codigo: String?): EstadoAtencion? = when (codigo) {
    "sin_asignar" -> EstadoAtencion.SIN_ASIGNAR
    "asignado" -> EstadoAtencion.ASIGNADO
    "atendido" -> EstadoAtencion.ATENDIDO
    else -> null
}

/** Id fijo del check-in de un evento: el nuevo estado reemplaza al anterior en el Outbox. */
fun idCheckin(idEvento: Int): String = "checkin-$idEvento"
