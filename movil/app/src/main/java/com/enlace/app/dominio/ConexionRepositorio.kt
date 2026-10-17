package com.enlace.app.dominio

import kotlinx.coroutines.flow.Flow

/** Ciudad que atiende el servidor ENLACE al que está conectado el celular. */
data class InfoServidor(val ciudad: String, val pais: String)

/** Dirección del servidor y prueba de conexión (la capa de datos decide cómo). */
interface ConexionRepositorio {
    /** Dirección base guardada, o null si todavía no se configuró. */
    val direccion: Flow<String?>

    /** Identificador anónimo de este celular (aleatorio, no se deriva de ningún dato personal). */
    suspend fun idAnonimo(): String

    suspend fun guardarDireccion(direccion: String)

    suspend fun probar(direccion: String): Result<InfoServidor>

    /**
     * Descarga lo que la app necesita para funcionar sin conexión durante una emergencia:
     * la clave que verifica las alertas y la forma de las celdas de la zona.
     */
    suspend fun prepararDatosLocales(direccion: String): Result<Unit>

    /** Clave pública de las alertas descargada al configurar, o null si aún no hay. */
    suspend fun clavePublica(): String?

    suspend fun celdas(): List<CeldaH3>

    /** Número del receptor de SMS (descargado al configurar), para ofrecerlo sin conexión. */
    suspend fun numeroSms(): String?
}
