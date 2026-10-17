package com.enlace.app.servicio

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.enlace.app.datos.GestorAlertas
import com.enlace.app.datos.ID_NOTIFICACION_SERVICIO
import com.enlace.app.datos.NotificadorAlertas
import com.enlace.app.datos.Outbox
import com.enlace.app.datos.Seguimiento
import com.enlace.app.datos.nearby.RedCercana
import com.enlace.app.dominio.AlertaFirmada
import com.enlace.app.dominio.ConexionRepositorio
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val ESPERA_INICIAL_MS = 1_000L
private const val ESPERA_MAXIMA_MS = 30_000L
private const val PING_SEGUNDOS = 20L

@Serializable
private data class Mensaje(val tipo: String, val datos: JsonObject)

/**
 * Servicio en primer plano ("ENLACE atento a alertas") que mantiene el WebSocket con el
 * servidor y se reconecta solo. En producción las alertas llegarían por push (FCM/APNs).
 */
@AndroidEntryPoint
class ServicioAlertas : Service() {
    @Inject lateinit var conexion: ConexionRepositorio
    @Inject lateinit var gestor: GestorAlertas
    @Inject lateinit var notificador: NotificadorAlertas
    @Inject lateinit var outbox: Outbox
    @Inject lateinit var seguimiento: Seguimiento
    @Inject lateinit var red: RedCercana
    @Inject lateinit var http: OkHttpClient
    @Inject lateinit var json: Json

    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var iniciado = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(this, ID_NOTIFICACION_SERVICIO, notificador.servicio(), tipos())
        red.iniciar()
        if (!iniciado) {
            iniciado = true
            alcance.launch { escuchar() }
        }
        return START_STICKY
    }

    /** "location" solo si hay permiso: permite consultar la ubicación al llegar una alerta. */
    private fun tipos(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0
        val ubicacion = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        var tipos = if (ubicacion) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            tipos = tipos or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            if (red.tienePermisos()) tipos = tipos or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        }
        return tipos
    }

    private suspend fun escuchar() {
        val cliente = http.newBuilder().pingInterval(PING_SEGUNDOS, TimeUnit.SECONDS).build()
        var espera = ESPERA_INICIAL_MS
        while (true) {
            val direccion = conexion.direccion.first()
            // Sin la clave, toda alerta se descartaría: se descarga si falta (p. ej. tras actualizar)
            if (direccion != null && conexion.clavePublica() == null) {
                conexion.prepararDatosLocales(direccion)
            }
            if (direccion != null && conexion.clavePublica() != null) {
                val url = "${direccion}ws/movil/${conexion.idAnonimo()}"
                val conecto = conectar(cliente, url)
                if (conecto) espera = ESPERA_INICIAL_MS
            }
            delay(espera)
            espera = (espera * 2).coerceAtMost(ESPERA_MAXIMA_MS)
        }
    }

    /** Escucha hasta que la conexión se corta; devuelve true si llegó a abrirse. */
    private suspend fun conectar(cliente: OkHttpClient, url: String): Boolean {
        var abierta = false
        callbackFlow {
            val socket = cliente.newWebSocket(
                Request.Builder().url(url).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        abierta = true
                        // Volvió la conexión: se vacía el Outbox y se pone al día el seguimiento
                        alcance.launch {
                            outbox.enviarPendientes()
                            seguimiento.actualizar()
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        trySend(text)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        close()
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        close()
                    }
                },
            )
            awaitClose { socket.cancel() }
        }.collect { texto -> procesar(texto) }
        return abierta
    }

    private suspend fun procesar(texto: String) {
        val mensaje = runCatching { json.decodeFromString<Mensaje>(texto) }.getOrNull() ?: return
        when (mensaje.tipo) {
            "alerta" -> runCatching { json.decodeFromJsonElement<AlertaFirmada>(mensaje.datos) }
                .getOrNull()?.let { gestor.recibir(it) }
            "evento_cerrado" -> mensaje.datos["id_evento"]?.jsonPrimitive?.int?.let { gestor.cerrar(it) }
            "sin_alerta_activa" -> gestor.cerrarVigente()
            "ayuda_en_camino" -> {
                val idReporte = mensaje.datos["id_reporte"]?.jsonPrimitive?.contentOrNull ?: return
                val equipo = mensaje.datos["equipo"]?.jsonPrimitive?.contentOrNull ?: return
                val minutos = mensaje.datos["tiempo_estimado_min"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                seguimiento.ayudaEnCamino(idReporte, equipo, minutos)
            }
        }
    }

    override fun onDestroy() {
        alcance.cancel()
        super.onDestroy()
    }

    companion object {
        fun iniciar(contexto: Context) {
            ContextCompat.startForegroundService(contexto, Intent(contexto, ServicioAlertas::class.java))
        }
    }
}
