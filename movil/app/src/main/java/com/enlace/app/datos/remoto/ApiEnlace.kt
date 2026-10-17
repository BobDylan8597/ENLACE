package com.enlace.app.datos.remoto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** Rutas del servidor ENLACE que usa la app (servidor/README.md). */
interface ApiEnlace {
    @GET("salud")
    suspend fun salud(): SaludDto

    @GET("ciudad")
    suspend fun ciudad(): CiudadDto

    /** Mapa vectorial de la ciudad (PMTiles), para guardarlo en el celular. */
    @Streaming
    @GET("mapa/base.pmtiles")
    suspend fun mapa(): ResponseBody

    @GET("bloqueos")
    suspend fun bloqueos(): List<BloqueoMapaDto>

    /** Sin clave (persona de la comunidad) solo se puede confirmar; la brigada también descarta. */
    @POST("bloqueos")
    suspend fun decidirBloqueo(@Body decision: BloqueoDecisionDto, @Header("X-Clave-Brigada") clave: String?)

    /** Ruta más rápida evitando bloqueos (GeoJSON con instrucciones). origen/destino: "lat,lon". */
    @GET("rutas")
    suspend fun ruta(@Query("origen") origen: String, @Query("destino") destino: String): JsonObject

    @POST("equipos/{id}/ubicacion")
    suspend fun ubicacionEquipo(
        @Path("id") idEquipo: Int,
        @Body punto: PuntoDto,
        @Header("X-Clave-Brigada") clave: String,
    )

    @GET("alertas/clave-publica")
    suspend fun clavePublica(): ClavePublicaDto

    @GET("celdas/geometria")
    suspend fun geometria(): GeometriaDto

    @POST("checkins")
    suspend fun checkin(@Body checkin: CheckInDto)

    /** La clave de brigada (o null) habilita los reportes con origen "brigada". */
    @POST("reportes")
    suspend fun reportes(
        @Body reportes: List<ReporteDto>,
        @Header("X-Clave-Brigada") claveBrigada: String?,
    ): ResultadoReportesDto

    // ── Modo unidad (clave de brigada) ──

    @GET("equipos")
    suspend fun equipos(@Header("X-Clave-Brigada") clave: String): List<EquipoDto>

    @POST("asignaciones/{id}/atendida")
    suspend fun atendida(@Path("id") idAsignacion: Int, @Header("X-Clave-Brigada") clave: String)

    @GET("celdas")
    suspend fun celdas(): List<CeldaResumenDto>

    @GET("ubicaciones")
    suspend fun ubicaciones(@Header("X-Clave-Brigada") clave: String): List<UbicacionDto>

    @GET("pedidos")
    suspend fun pedidos(@Header("X-Clave-Brigada") clave: String): List<PedidoMapaDto>

    @POST("bloqueos")
    suspend fun bloqueo(@Body bloqueo: BloqueoDto): CambioBloqueoDto

    @GET("seguimiento/{id}")
    suspend fun seguimiento(@Path("id") idMensaje: String): SeguimientoDto
}

@Serializable
data class SeguimientoDto(
    val estado: String,
    val equipo: String? = null,
    @SerialName("tiempo_estimado_min") val tiempoEstimadoMin: Double? = null,
)

@Serializable
data class SaludDto(val estado: String, val ciudad: String, val pais: String)

@Serializable
data class CiudadDto(@SerialName("numero_sms") val numeroSms: String? = null, val idioma: String? = null)

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
    val personas: Int,
    val vulnerables: Int,
    /** Solo con el consentimiento "ubicación exacta"; si es null no se envía. */
    val ubicacion: PuntoDto? = null,
)

@Serializable
data class PuntoDto(val lat: Double, val lon: Double)

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
    /** Solo en la evaluación de una brigada (B8). */
    @SerialName("nivel_dano") val nivelDano: String? = null,
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

@Serializable
data class BloqueoMapaDto(
    val id: Int = 0,
    val lat: Double,
    val lon: Double,
    val estado: String,
    val alcance: String = "total",
    val tipo: String = "otro",
    val confirmaciones: Int = 1,
)

/** Confirmar o descartar un bloqueo existente (servidor: BloqueoEntrada sin lat/lon). */
@Serializable
data class BloqueoDecisionDto(
    val accion: String,
    @SerialName("id_bloqueo") val idBloqueo: Int,
    val reportante: String,
    val origen: String,
)

// ── Modo unidad ──

@Serializable
data class EquipoDto(
    val id: Int,
    val nombre: String,
    val organismo: String,
    val capacidades: List<String> = emptyList(),
    val estado: String = "",
)

@Serializable
data class TotalesCheckInDto(
    val personas: Int = 0,
    val vulnerables: Int = 0,
    val criticos: Int = 0,
)

@Serializable
data class CeldaResumenDto(
    @SerialName("id_h3") val idH3: String,
    @SerialName("nombre_zona") val nombreZona: String,
    val checkins: TotalesCheckInDto = TotalesCheckInDto(),
)

@Serializable
data class UbicacionDto(
    val lat: Double,
    val lon: Double,
    val estado: String,
    val personas: Int,
    val vulnerables: Int,
)

@Serializable
data class PedidoMapaDto(
    @SerialName("id_mensaje") val idMensaje: String,
    val lat: Double,
    val lon: Double,
    @SerialName("nombre_zona") val nombreZona: String = "",
    val estado: String,
    val personas: Int = 0,
    val atrapados: Int = 0,
    val heridos: Int = 0,
    val vulnerables: Int = 0,
    val necesidades: List<String> = emptyList(),
)

/** Aviso de asignación por WebSocket (servidor: vistas.aviso_asignacion). */
@Serializable
data class AvisoAsignacionDto(
    val asignacion: AsignacionDto,
    /** En "ruta_recalculada" no viene: el destino no cambia. */
    val destino: PuntoDto? = null,
    @SerialName("nombre_zona") val nombreZona: String = "",
    val pedido: AtenderDto? = null,
    val contacto: IdentidadDto? = null,
    /** Solo en "ruta_recalculada": por qué cambió y si ya no hay forma de llegar. */
    val motivo: String? = null,
    @SerialName("sin_ruta") val sinRuta: Boolean = false,
)

@Serializable
data class AsignacionDto(
    val id: Int,
    @SerialName("id_equipo") val idEquipo: Int,
    @SerialName("id_reporte") val idReporte: String? = null,
    @SerialName("id_h3") val idH3: String,
    val motivo: String,
    @SerialName("tiempo_estimado_min") val tiempoEstimadoMin: Double,
    val decision: String,
    @SerialName("hora_fin") val horaFin: String? = null,
    val ruta: JsonObject? = null,
)

@Serializable
data class AtenderDto(
    val personas: Int = 0,
    val atrapados: Int = 0,
    val heridos: Int = 0,
    val vulnerables: Int = 0,
    val necesidades: List<String> = emptyList(),
)
