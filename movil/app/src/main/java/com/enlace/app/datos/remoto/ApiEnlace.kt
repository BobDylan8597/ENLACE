package com.enlace.app.datos.remoto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/** Rutas del servidor ENLACE que usa la app (servidor/README.md). */
interface ApiEnlace {
    @GET("salud")
    suspend fun salud(): SaludDto

    @GET("alertas/clave-publica")
    suspend fun clavePublica(): ClavePublicaDto

    @GET("celdas/geometria")
    suspend fun geometria(): GeometriaDto

    @POST("checkins")
    suspend fun checkin(@Body checkin: CheckInDto)
}

@Serializable
data class SaludDto(val estado: String, val ciudad: String, val pais: String)

@Serializable
data class ClavePublicaDto(val algoritmo: String, @SerialName("clave_publica") val clavePublica: String)

@Serializable
data class GeometriaDto(val resolucion: Int, val celdas: List<CeldaDto>)

@Serializable
data class CeldaDto(@SerialName("id_h3") val idH3: String, val contorno: List<List<Double>>)

/** Check-in anónimo: solo la celda, nunca coordenadas (el servidor rechaza cualquier otro campo). */
@Serializable
data class CheckInDto(
    @SerialName("id_anonimo") val idAnonimo: String,
    @SerialName("id_evento") val idEvento: Int,
    @SerialName("id_h3") val idH3: String,
    val estado: String,
)
