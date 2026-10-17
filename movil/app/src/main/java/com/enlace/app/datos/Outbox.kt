package com.enlace.app.datos

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.enlace.app.datos.local.MensajeSaliente
import com.enlace.app.datos.local.OutboxDao
import com.enlace.app.datos.remoto.BloqueoDto
import com.enlace.app.datos.remoto.CheckInDto
import com.enlace.app.datos.remoto.FabricaApi
import com.enlace.app.datos.remoto.ReporteDto
import com.enlace.app.dominio.ConexionRepositorio
import com.enlace.app.dominio.EstadoEnvio
import com.enlace.app.dominio.TipoMensaje
import com.enlace.app.dominio.estadosDelLote
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TRABAJO = "envio_outbox"
private const val ESPERA_REINTENTO_SEGUNDOS = 15L

/**
 * Outbox: todo reporte se guarda primero en el celular y luego se envía (CLAUDE.md, sección 5).
 * Se vacía con WorkManager (con reintentos) y cada vez que el servicio recupera la conexión.
 */
@Singleton
class Outbox @Inject constructor(
    @param:ApplicationContext private val contexto: Context,
    private val dao: OutboxDao,
    private val conexion: ConexionRepositorio,
    private val fabricaApi: FabricaApi,
    private val credenciales: CredencialesBrigada,
    private val json: Json,
) {
    private val cerrojo = Mutex()

    suspend fun guardar(id: String, tipo: TipoMensaje, cuerpo: String) {
        dao.guardar(MensajeSaliente(id, tipo, cuerpo, creado = System.currentTimeMillis()))
        programarEnvio()
    }

    fun observar(id: String) = dao.observar(id)

    /** Cuántos mensajes esperan ser entregados (para la unidad: los que lleva como mula). */
    fun pendientes() = dao.contarPendientes()

    /** Envía todo lo pendiente. Devuelve true si no quedó nada por enviar. */
    suspend fun enviarPendientes(): Boolean = cerrojo.withLock {
        val pendientes = dao.pendientes()
        if (pendientes.isEmpty()) return true
        val direccion = conexion.direccion.first() ?: return false
        val api = fabricaApi.para(direccion)
        var sinConexion = false

        // Reportes en un solo lote, como una mula de datos (POST /reportes acepta listas)
        val reportes = pendientes.filter { it.tipo == TipoMensaje.REPORTE }
        if (reportes.isNotEmpty()) {
            runCatching {
                // En modo unidad, con la clave: las evaluaciones de la brigada se aceptan como tales
                api.reportes(reportes.map { json.decodeFromString<ReporteDto>(it.cuerpo) }, credenciales.clave())
            }.onSuccess { r ->
                val estados = estadosDelLote(
                    reportes.map { it.idMensaje },
                    r.aceptados,
                    r.repetidos,
                    r.rechazados.associate { it.idMensaje to it.error },
                )
                estados.forEach { (id, estado) -> dao.marcar(id, estado.first, estado.second) }
            }.onFailure {
                sinConexion = true
                dao.sumarIntento(reportes.map { it.idMensaje })
            }
        }
        for (mensaje in pendientes.filter { it.tipo != TipoMensaje.REPORTE }) {
            val resultado = runCatching {
                when (mensaje.tipo) {
                    TipoMensaje.BLOQUEO -> api.bloqueo(json.decodeFromString<BloqueoDto>(mensaje.cuerpo))
                    TipoMensaje.CHECKIN -> api.checkin(json.decodeFromString<CheckInDto>(mensaje.cuerpo))
                    TipoMensaje.REPORTE -> Unit
                }
            }
            val error = resultado.exceptionOrNull()
            when {
                error == null -> dao.marcar(mensaje.idMensaje, EstadoEnvio.ENVIADO, null)
                // El servidor respondió con un error de datos: reintentar no lo arregla
                error is HttpException && error.code() in 400..499 ->
                    dao.marcar(mensaje.idMensaje, EstadoEnvio.RECHAZADO, error.message())
                else -> {
                    sinConexion = true
                    dao.sumarIntento(listOf(mensaje.idMensaje))
                }
            }
        }
        !sinConexion && dao.pendientes().isEmpty()
    }

    /** WorkManager reintenta solo, con espera creciente, aunque se cierre la app. */
    fun programarEnvio() {
        val trabajo = OneTimeWorkRequestBuilder<TrabajoEnvioOutbox>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, ESPERA_REINTENTO_SEGUNDOS, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(contexto).enqueueUniqueWork(TRABAJO, ExistingWorkPolicy.REPLACE, trabajo)
    }
}
