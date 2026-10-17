package com.enlace.receptor

import kotlinx.serialization.Serializable
import java.util.Locale

/** Prefijo del formato de reporte (CLAUDE.md, sección 14): `ENL <lat>,<lon> P4 A1 H1 RM`. */
private const val PREFIJO = "ENL"
private const val DIGITOS_VISIBLES = 4

enum class EstadoSms { PENDIENTE, ENTREGADO, RECHAZADO }

/** SMS de reporte recibido, en la cola del celular hasta que el servidor lo confirma. */
@Serializable
data class SmsRecibido(
    val id: String,
    val remitente: String,
    val texto: String,
    /** Hora en que el celular recibió el SMS (ISO 8601 con zona). */
    val hora: String,
    val estado: EstadoSms = EstadoSms.PENDIENTE,
    val intentos: Int = 0,
    val error: String? = null,
)

/**
 * Solo se reenvían los SMS con el formato de ENLACE: el resto (mensajes personales, promociones)
 * nunca sale del celular. El servidor valida el contenido; aquí basta el prefijo.
 */
fun esReporteEnlace(texto: String): Boolean {
    val limpio = texto.trim()
    if (limpio.length <= PREFIJO.length) return false
    return limpio.substring(0, PREFIJO.length).uppercase(Locale.ROOT) == PREFIJO && limpio[PREFIJO.length].isWhitespace()
}

/** Un SMS largo llega en partes: se juntan las de cada remitente en el orden en que llegaron. */
fun unirPartes(partes: List<Pair<String, String>>): List<Pair<String, String>> =
    partes.groupBy({ it.first }, { it.second }).map { (remitente, cuerpos) -> remitente to cuerpos.joinToString("") }

/** En pantalla solo los últimos dígitos: el número completo lo ve únicamente el centro. */
fun ocultarNumero(numero: String): String {
    val digitos = numero.filter { it.isDigit() }
    return if (digitos.length <= DIGITOS_VISIBLES) "•••" else "•••" + digitos.takeLast(DIGITOS_VISIBLES)
}

/** Qué hacer con un SMS según la respuesta del servidor. */
fun estadoTrasRespuesta(codigo: Int): EstadoSms = when (codigo) {
    in 200..299 -> EstadoSms.ENTREGADO
    // 401: la clave de brigada está mal: se corrige la clave y se reintenta, no se descarta
    401 -> EstadoSms.PENDIENTE
    // 422 u otro error de datos: reintentar no lo arregla
    in 400..499 -> EstadoSms.RECHAZADO
    else -> EstadoSms.PENDIENTE
}
