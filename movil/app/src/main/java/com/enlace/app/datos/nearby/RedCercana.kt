package com.enlace.app.datos.nearby

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.enlace.app.datos.GestorAlertas
import com.enlace.app.datos.Outbox
import com.enlace.app.datos.local.MensajeVisto
import com.enlace.app.datos.local.OutboxDao
import com.enlace.app.datos.local.VistosDao
import com.enlace.app.datos.remoto.ReporteDto
import com.enlace.app.dominio.AlertaFirmada
import com.enlace.app.dominio.DecisionGossip
import com.enlace.app.dominio.Gossip
import com.enlace.app.dominio.MensajeRed
import com.enlace.app.dominio.SALTOS_MAXIMOS
import com.enlace.app.dominio.TipoMensaje
import com.enlace.app.dominio.TipoMensajeRed
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

private const val ID_SERVICIO = "com.enlace.app.red"
private const val ETIQUETA = "RedCercana"

/**
 * Red cercana entre celulares con Nearby Connections (P2P_CLUSTER): funciona sin internet ni
 * router, por Bluetooth y Wi-Fi directo. Los mensajes se propagan por gossip (sección 12).
 */
@Singleton
class RedCercana @Inject constructor(
    @param:ApplicationContext private val contexto: Context,
    private val outbox: Outbox,
    private val outboxDao: OutboxDao,
    private val vistos: VistosDao,
    private val alertas: Provider<GestorAlertas>,
    private val json: Json,
) {
    private val cliente = Nearby.getConnectionsClient(contexto)
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gossip = Gossip(SALTOS_MAXIMOS)

    /** Nombre visible en la red: aleatorio por sesión, no identifica a la persona. */
    private val miNombre = UUID.randomUUID().toString().take(8)
    private val conectados = mutableSetOf<String>()
    private val _celulares = MutableStateFlow(0)

    /** Celulares conectados ahora (para el indicador "Red cercana (n celulares)"). */
    val celulares: StateFlow<Int> = _celulares.asStateFlow()
    private var iniciada = false

    fun permisosNecesarios(): List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }

    fun tienePermisos(): Boolean = permisosNecesarios().all {
        ContextCompat.checkSelfPermission(contexto, it) == PackageManager.PERMISSION_GRANTED
    }

    @Synchronized
    fun iniciar() {
        if (iniciada || !tienePermisos()) return
        iniciada = true
        cliente.startAdvertising(
            miNombre,
            ID_SERVICIO,
            ciclo,
            AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build(),
        ).addOnFailureListener { Log.w(ETIQUETA, "No se pudo anunciar", it) }
        cliente.startDiscovery(
            ID_SERVICIO,
            descubrimiento,
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build(),
        ).addOnFailureListener { Log.w(ETIQUETA, "No se pudo buscar", it) }
    }

    /** Publica un mensaje propio (saltos 0): lo creó este celular. */
    suspend fun publicar(tipo: TipoMensajeRed, idMensaje: String, payload: String) {
        val mensaje = MensajeRed(idMensaje, saltos = 0, hora = OffsetDateTime.now().toString(), tipo, payload)
        vistos.guardar(MensajeVisto(idMensaje, 0, System.currentTimeMillis()))
        difundir(mensaje, excepto = null)
    }

    private fun difundir(mensaje: MensajeRed, excepto: String?) {
        val destinos = synchronized(conectados) { conectados.filter { it != excepto } }
        if (destinos.isEmpty()) return
        val bytes = json.encodeToString(MensajeRed.serializer(), mensaje).toByteArray(Charsets.UTF_8)
        cliente.sendPayload(destinos, Payload.fromBytes(bytes))
    }

    private suspend fun recibir(texto: String, desde: String) {
        val mensaje = runCatching { json.decodeFromString(MensajeRed.serializer(), texto) }.getOrNull() ?: return
        val yaVisto = vistos.saltos(mensaje.idMensaje) != null || outboxDao.existe(mensaje.idMensaje) > 0
        val decision = gossip.alRecibir(mensaje, yaVisto) as? DecisionGossip.Guardar ?: return
        vistos.guardar(MensajeVisto(mensaje.idMensaje, mensaje.saltos, System.currentTimeMillis()))

        val reenviar = when (mensaje.tipo) {
            TipoMensajeRed.ALERTA -> {
                // Solo se propaga una alerta con firma válida (la verifica el gestor)
                val firmada = runCatching { json.decodeFromString<AlertaFirmada>(mensaje.payload) }.getOrNull()
                firmada != null && alertas.get().recibir(firmada, desdeRedCercana = true)
            }
            TipoMensajeRed.REPORTE -> {
                val reporte = runCatching { json.decodeFromString<ReporteDto>(mensaje.payload) }.getOrNull()
                if (reporte != null) {
                    // Quien lo entregue al servidor no es quien lo creó: llegó por la red cercana
                    val reenviado = reporte.copy(
                        origen = if (reporte.origen == "app") "nearby" else reporte.origen,
                        saltosNearby = mensaje.saltos + 1,
                    )
                    outbox.guardar(reporte.idMensaje, TipoMensaje.REPORTE, json.encodeToString(ReporteDto.serializer(), reenviado))
                }
                reporte != null
            }
            TipoMensajeRed.CHECKIN -> true.also { outbox.guardar(mensaje.idMensaje, TipoMensaje.CHECKIN, mensaje.payload) }
            TipoMensajeRed.BLOQUEO -> true.also { outbox.guardar(mensaje.idMensaje, TipoMensaje.BLOQUEO, mensaje.payload) }
        }
        if (reenviar) decision.reenviar?.let { difundir(it, excepto = desde) }
    }

    /** Al conectarse con alguien nuevo se le pasa lo pendiente y la alerta vigente. */
    private suspend fun ponerAlDia(endpoint: String) {
        alertas.get().vigente.first()?.let { vigente ->
            enviarA(endpoint, MensajeRed(idAlerta(vigente.alerta.idEvento), 0, OffsetDateTime.now().toString(), TipoMensajeRed.ALERTA, json.encodeToString(AlertaFirmada.serializer(), vigente.firmada)))
        }
        for (pendiente in outboxDao.pendientes()) {
            val tipo = when (pendiente.tipo) {
                TipoMensaje.REPORTE -> TipoMensajeRed.REPORTE
                TipoMensaje.CHECKIN -> TipoMensajeRed.CHECKIN
                TipoMensaje.BLOQUEO -> TipoMensajeRed.BLOQUEO
            }
            val saltos = vistos.saltos(pendiente.idMensaje) ?: 0
            enviarA(endpoint, MensajeRed(pendiente.idMensaje, saltos, OffsetDateTime.now().toString(), tipo, pendiente.cuerpo))
        }
    }

    private fun enviarA(endpoint: String, mensaje: MensajeRed) {
        val bytes = json.encodeToString(MensajeRed.serializer(), mensaje).toByteArray(Charsets.UTF_8)
        cliente.sendPayload(endpoint, Payload.fromBytes(bytes))
    }

    private val datos = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            alcance.launch { recibir(String(bytes, Charsets.UTF_8), endpointId) }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val ciclo = object : ConnectionLifecycleCallback() {
        // Demo: se acepta a cualquier celular con ENLACE. En producción, autenticar el token.
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            cliente.acceptConnection(endpointId, datos)
        }

        override fun onConnectionResult(endpointId: String, resolucion: ConnectionResolution) {
            if (!resolucion.status.isSuccess) return
            synchronized(conectados) { conectados += endpointId }
            _celulares.value = conectados.size
            alcance.launch { ponerAlDia(endpointId) }
        }

        override fun onDisconnected(endpointId: String) {
            synchronized(conectados) { conectados -= endpointId }
            _celulares.value = conectados.size
        }
    }

    private val descubrimiento = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // Solo pide la conexión uno de los dos (el de nombre menor): evita choques
            if (miNombre < info.endpointName) {
                cliente.requestConnection(miNombre, endpointId, ciclo)
                    .addOnFailureListener { Log.w(ETIQUETA, "No se pudo conectar", it) }
            }
        }

        override fun onEndpointLost(endpointId: String) = Unit
    }
}

/** Id fijo de la alerta de un evento en la red: llega por muchas rutas, se procesa una vez. */
fun idAlerta(idEvento: Int): String = "alerta-$idEvento"
