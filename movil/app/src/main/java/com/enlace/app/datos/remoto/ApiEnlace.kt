package com.enlace.app.datos.remoto

import kotlinx.serialization.Serializable
import retrofit2.http.GET

/** Rutas del servidor ENLACE que usa la app (servidor/README.md). */
interface ApiEnlace {
    @GET("salud")
    suspend fun salud(): SaludDto
}

@Serializable
data class SaludDto(val estado: String, val ciudad: String, val pais: String)
