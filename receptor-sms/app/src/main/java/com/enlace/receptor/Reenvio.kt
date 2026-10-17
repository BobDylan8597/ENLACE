package com.enlace.receptor

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

private const val TRABAJO = "reenvio_sms"
private const val ESPERA_REINTENTO_SEGUNDOS = 15L
private const val ESPERA_CONEXION_SEGUNDOS = 10L
private const val NO_AUTORIZADO = 401

/** Lo que espera el servidor en POST /sms/entrante (servidor: SmsEntrante). */
@Serializable
private data class SmsEntrante(val remitente: String, val texto: String, val hora: String)

/** "192.168.1.10" o "192.168.1.10:8000" → "http://192.168.1.10:8000/" (puerto 8000 por defecto). */
fun normalizarDireccion(texto: String): String? {
    val limpio = texto.trim().removeSuffix("/")
    if (limpio.isEmpty() || limpio.any { it.isWhitespace() }) return null
    val conEsquema = if ("://" in limpio) limpio else "http://$limpio"
    val anfitrion = conEsquema.substringAfter("://")
    return if (":" in anfitrion) "$conEsquema/" else "$conEsquema:8000/"
}

class Reenvio(private val contexto: Context) {
    private val almacen = Almacen(contexto)
    private val json = Json
    private val http = OkHttpClient.Builder()
        .connectTimeout(ESPERA_CONEXION_SEGUNDOS, TimeUnit.SECONDS)
        .readTimeout(ESPERA_CONEXION_SEGUNDOS, TimeUnit.SECONDS)
        .build()

    /** Envía todo lo pendiente. Devuelve true si no quedó nada por enviar. */
    suspend fun enviarPendientes(): Boolean = withContext(Dispatchers.IO) {
        val direccion = almacen.direccion.first() ?: return@withContext false
        val clave = almacen.clave.first().orEmpty()
        var completo = true
        for (sms in almacen.pendientes()) {
            val cuerpo = json.encodeToString(SmsEntrante.serializer(), SmsEntrante(sms.remitente, sms.texto, sms.hora))
            val pedido = Request.Builder()
                .url("${direccion}sms/entrante")
                .header("X-Clave-Brigada", clave)
                .post(cuerpo.toRequestBody("application/json".toMediaType()))
                .build()
            val codigo = runCatching { http.newCall(pedido).execute().use { it.code } }.getOrNull()
            val estado = codigo?.let(::estadoTrasRespuesta) ?: EstadoSms.PENDIENTE
            val error = when {
                codigo == null -> contexto.getString(R.string.error_sin_servidor)
                codigo == NO_AUTORIZADO -> contexto.getString(R.string.error_clave)
                estado == EstadoSms.RECHAZADO -> contexto.getString(R.string.error_formato)
                else -> null
            }
            almacen.actualizar(sms.id, estado, error)
            if (estado == EstadoSms.PENDIENTE) completo = false
        }
        completo
    }

    /** Comprueba la dirección y la clave con una ruta de la brigada que no cambia nada. */
    suspend fun probar(direccion: String, clave: String): Int? = withContext(Dispatchers.IO) {
        val pedido = Request.Builder().url("${direccion}equipos").header("X-Clave-Brigada", clave).build()
        runCatching { http.newCall(pedido).execute().use { it.code } }.getOrNull()
    }

    /** WorkManager reintenta solo, con espera creciente, aunque la app esté cerrada. */
    fun programar() {
        val trabajo = OneTimeWorkRequestBuilder<TrabajoReenvio>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, ESPERA_REINTENTO_SEGUNDOS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(contexto).enqueueUniqueWork(TRABAJO, ExistingWorkPolicy.REPLACE, trabajo)
    }
}

class TrabajoReenvio(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result =
        if (Reenvio(applicationContext).enviarPendientes()) Result.success() else Result.retry()
}
