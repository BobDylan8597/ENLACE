package com.enlace.app.dominio

import java.net.URI

/**
 * Convierte lo que escribe la persona ("192.168.1.10:8000", "http://laptop:8000/") en la
 * dirección base del servidor ENLACE, o null si no es válida.
 */
object DireccionServidor {
    /** Puerto con el que arranca el servidor ENLACE (servidor/config/enlace.toml). */
    const val PUERTO_POR_DEFECTO = 8000

    fun normalizar(texto: String): String? {
        val limpio = texto.trim().trimEnd('/')
        if (limpio.isEmpty() || limpio.any { it.isWhitespace() }) return null
        val conEsquema =
            if (limpio.startsWith("http://") || limpio.startsWith("https://")) limpio else "http://$limpio"
        val uri = runCatching { URI(conEsquema) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        if (!uri.path.isNullOrEmpty() || uri.query != null) return null
        val puerto = if (uri.port == -1) PUERTO_POR_DEFECTO else uri.port
        return "${uri.scheme}://$host:$puerto/"
    }
}
