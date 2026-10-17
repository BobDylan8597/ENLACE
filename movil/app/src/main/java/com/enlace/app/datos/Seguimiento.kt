package com.enlace.app.datos

import com.enlace.app.datos.local.OutboxDao
import com.enlace.app.datos.local.PedidoPropio
import com.enlace.app.datos.local.PedidosDao
import com.enlace.app.datos.remoto.FabricaApi
import com.enlace.app.dominio.ConexionRepositorio
import com.enlace.app.dominio.EstadoAtencion
import com.enlace.app.dominio.EstadoEnvio
import com.enlace.app.dominio.estadoAtencion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Pedido propio con su estado de envío, para U12. */
data class PedidoConEstado(val pedido: PedidoPropio, val envio: EstadoEnvio)

/** Seguimiento de mis pedidos de ayuda: por WebSocket ("ayuda en camino") y consultando al servidor. */
@Singleton
class Seguimiento @Inject constructor(
    private val pedidos: PedidosDao,
    private val outbox: OutboxDao,
    private val conexion: ConexionRepositorio,
    private val fabricaApi: FabricaApi,
    private val notificador: NotificadorAlertas,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val mios: Flow<List<PedidoConEstado>> = pedidos.todos().flatMapLatest { lista ->
        if (lista.isEmpty()) {
            flowOf(emptyList())
        } else {
            outbox.observarVarios(lista.map { it.idMensaje }).map { mensajes ->
                val envio = mensajes.associate { it.idMensaje to it.estado }
                lista.map { PedidoConEstado(it, envio[it.idMensaje] ?: EstadoEnvio.PENDIENTE) }
            }
        }
    }

    suspend fun guardar(pedido: PedidoPropio) = pedidos.guardar(pedido)

    /** Aviso por WebSocket: el centro asignó un equipo a mi pedido. */
    suspend fun ayudaEnCamino(idReporte: String, equipo: String, minutos: Double) {
        val propio = pedidos.lista().firstOrNull { it.idMensaje == idReporte } ?: return
        pedidos.actualizar(propio.idMensaje, EstadoAtencion.ASIGNADO, equipo, minutos)
        notificador.mostrarAyudaEnCamino(equipo, minutos)
    }

    /** Consulta al servidor los pedidos ya enviados que todavía no fueron atendidos. */
    suspend fun actualizar() {
        val direccion = conexion.direccion.first() ?: return
        val api = fabricaApi.para(direccion)
        for (pedido in pedidos.lista().filter { it.atencion != EstadoAtencion.ATENDIDO }) {
            val estado = runCatching { api.seguimiento(pedido.idMensaje) }.getOrNull() ?: continue
            val atencion = estadoAtencion(estado.estado)
            val nuevoEquipo = atencion == EstadoAtencion.ASIGNADO && pedido.atencion != EstadoAtencion.ASIGNADO
            pedidos.actualizar(pedido.idMensaje, atencion, estado.equipo, estado.tiempoEstimadoMin)
            if (nuevoEquipo && estado.equipo != null) {
                notificador.mostrarAyudaEnCamino(estado.equipo, estado.tiempoEstimadoMin ?: 0.0)
            }
        }
    }
}
