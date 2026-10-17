package com.enlace.app.dominio

import kotlinx.coroutines.flow.Flow

/** Ciudad que atiende el servidor ENLACE al que está conectado el celular. */
data class InfoServidor(val ciudad: String, val pais: String)

/** Dirección del servidor y prueba de conexión (la capa de datos decide cómo). */
interface ConexionRepositorio {
    /** Dirección base guardada, o null si todavía no se configuró. */
    val direccion: Flow<String?>

    suspend fun guardarDireccion(direccion: String)

    suspend fun probar(direccion: String): Result<InfoServidor>
}
