package com.enlace.app.dominio

import kotlinx.coroutines.flow.Flow

/** Indicador siempre visible (sección 16): internet / red cercana (n celulares) / sin conexión. */
sealed interface EstadoConexion {
    /** El servidor ENLACE responde. */
    data object EnLinea : EstadoConexion

    /** Sin servidor, pero con celulares cercanos por la red Nearby (paso 4.6). */
    data class RedCercana(val celulares: Int) : EstadoConexion

    data object SinConexion : EstadoConexion
}

interface MonitorConexion {
    val estado: Flow<EstadoConexion>
}
