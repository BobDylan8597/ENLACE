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

    @POST("reportes")
    suspend fun reportes(@Body reportes: List<ReporteDto>): ResultadoReportesDto

    @POST("bloqueos")
    suspend fun bloqueo(@Body bloqueo: BloqueoDto): CambioBloqueoDto
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

@Serializable
data class IdentidadDto(val nombre: String?, val telefono: String?, val condiciones: String?)

/** Reporte (servidor: ReporteEntrada). lat/lon solo en pedidos de ayuda. */
@Serializable
data class ReporteDto(
    @SerialName("id_mensaje") val idMensaje: String,
    @SerialName("id_anonimo") val idAnonimo: String,
    @SerialName("id_evento") val idEvento: Int?,
    val origen: String,
    val tipo: String,
    val lat: Double? = null,
    val lon: Double? = null,
    @SerialName("id_h3") val idH3: String? = null,
    val personas: Int = 0,
    val atrapados: Int = 0,
    val heridos: Int = 0,
    val vulnerables: Int = 0,
    val necesidades: List<String> = emptyList(),
    @SerialName("saltos_nearby") val saltosNearby: Int = 0,
    val hora: String,
    val identidad: IdentidadDto? = null,
)

@Serializable
data class RechazoDto(@SerialName("id_mensaje") val idMensaje: String? = null, val error: String)

@Serializable
data class ResultadoReportesDto(
    val aceptados: List<String>,
    val repetidos: List<String>,
    val rechazados: List<RechazoDto> = emptyList(),
)

@Serializable
data class BloqueoDto(
    val accion: String,
    val lat: Double,
    val lon: Double,
    val tipo: String,
    val alcance: String,
    val reportante: String,
    val origen: String,
)

@Serializable
data class EstadoBloqueoDto(val id: Int, val estado: String, val confirmaciones: Int)

@Serializable
data class CambioBloqueoDto(val bloqueo: EstadoBloqueoDto, @SerialName("rutas_recalculadas") val rutasRecalculadas: Int)
