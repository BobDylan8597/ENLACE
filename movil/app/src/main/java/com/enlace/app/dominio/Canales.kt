package com.enlace.app.dominio

import java.util.Locale

/** Pedido de ayuda ya guardado en el Outbox, listo para salir por el canal que funcione. */
data class PedidoParaEnviar(val idMensaje: String, val lat: Double, val lon: Double, val datos: DatosPedido)

sealed interface ResultadoCanal {
    /** El canal lo entregó (o lo dejó en camino hacia el servidor). */
    data object Entregado : ResultadoCanal

    /** Este canal no puede ahora: se prueba el siguiente. */
    data object NoDisponible : ResultadoCanal

    /** No hay envío automático posible: se ofrece a la persona enviar este SMS. */
    data class OfrecerSms(val numero: String, val texto: String) : ResultadoCanal
}

/** Un canal de envío (patrón Strategy): servidor, red cercana (Nearby) o SMS. */
fun interface CanalEnvio {
    suspend fun enviar(pedido: PedidoParaEnviar): ResultadoCanal
}

/**
 * Elige el canal (Chain of Responsibility): prueba en orden y se queda con el primero que
 * entrega u ofrece una salida. Orden: servidor → red cercana → ofrecer SMS.
 */
class CadenaCanales(private val canales: List<CanalEnvio>) {
    suspend fun enviar(pedido: PedidoParaEnviar): ResultadoCanal {
        for (canal in canales) {
            val resultado = canal.enviar(pedido)
            if (resultado != ResultadoCanal.NoDisponible) return resultado
        }
        return ResultadoCanal.NoDisponible
    }
}

private val LETRA_NECESIDAD = mapOf(
    Necesidad.RESCATE to 'R',
    Necesidad.MEDICO to 'M',
    Necesidad.AGUA to 'W',
    Necesidad.REFUGIO to 'F',
)

/**
 * Formato SMS (CLAUDE.md, sección 14): `ENL <lat>,<lon> P<personas> A<atrapados> H<heridos> <necesidades>`.
 * Ejemplo: `ENL 13.10000,-85.20000 P4 A1 H1 RM`. Cinco decimales: ~1 m, suficiente para encontrar a alguien.
 */
fun textoSms(lat: Double, lon: Double, datos: DatosPedido): String {
    val coordenadas = String.format(Locale.ROOT, "%.5f,%.5f", lat, lon)
    val necesidades = Necesidad.entries.filter { it in datos.necesidades }.mapNotNull { LETRA_NECESIDAD[it] }
    return buildString {
        append("ENL ").append(coordenadas)
        append(" P").append(datos.personas)
        append(" A").append(datos.atrapados)
        append(" H").append(datos.heridos)
        if (necesidades.isNotEmpty()) append(' ').append(necesidades.joinToString(""))
    }
}

/** Último eslabón: ofrecer el SMS si se conoce el número del receptor. */
class CanalSms(private val numeroReceptor: suspend () -> String?) : CanalEnvio {
    override suspend fun enviar(pedido: PedidoParaEnviar): ResultadoCanal {
        val numero = numeroReceptor() ?: return ResultadoCanal.NoDisponible
        return ResultadoCanal.OfrecerSms(numero, textoSms(pedido.lat, pedido.lon, pedido.datos))
    }
}
