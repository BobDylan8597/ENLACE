package com.enlace.app.presentacion.usuario

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.enlace.app.R
import com.enlace.app.datos.PedidoConEstado
import com.enlace.app.datos.Seguimiento
import com.enlace.app.dominio.EstadoAtencion
import com.enlace.app.dominio.EstadoEnvio
import com.enlace.app.presentacion.tema.ColoresEnlace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Mientras la pantalla está abierta, se consulta al servidor cada 30 s (además del WebSocket). */
private const val CONSULTA_MS = 30_000L
private const val MS_POR_MINUTO = 60_000L

@HiltViewModel
class SeguimientoViewModel @Inject constructor(private val seguimiento: Seguimiento) : ViewModel() {
    val pedidos: StateFlow<List<PedidoConEstado>> =
        seguimiento.mios.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    suspend fun consultar() = seguimiento.actualizar()
}

/** U12: seguimiento de mis pedidos de ayuda. */
@Composable
fun PantallaSeguimiento(alVolver: () -> Unit, viewModel: SeguimientoViewModel = hiltViewModel()) {
    val pedidos by viewModel.pedidos.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.consultar()
            delay(CONSULTA_MS)
        }
    }

    Scaffold { relleno ->
        Column(
            modifier = Modifier.fillMaxSize().padding(relleno).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.seguimiento_titulo), style = MaterialTheme.typography.headlineSmall)
            if (pedidos.isEmpty()) {
                Text(stringResource(R.string.seguimiento_vacio), style = MaterialTheme.typography.bodyLarge)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                items(pedidos, key = { it.pedido.idMensaje }) { FilaPedido(it) }
            }
            TextButton(onClick = alVolver) { Text(stringResource(R.string.envio_volver_inicio)) }
        }
    }
}

@Composable
private fun FilaPedido(item: PedidoConEstado) {
    val pedido = item.pedido
    val (icono, color, titulo) = when {
        pedido.atencion == EstadoAtencion.ATENDIDO ->
            Triple(R.drawable.ic_bien, ColoresEnlace.estable, stringResource(R.string.seguimiento_atendido))
        pedido.atencion == EstadoAtencion.ASIGNADO ->
            Triple(R.drawable.ic_equipo, ColoresEnlace.accion, stringResource(R.string.seguimiento_en_camino))
        item.envio == EstadoEnvio.RECHAZADO ->
            Triple(R.drawable.ic_alerta, ColoresEnlace.actuar, stringResource(R.string.seguimiento_rechazado))
        item.envio == EstadoEnvio.ENVIADO ->
            Triple(R.drawable.ic_bien, ColoresEnlace.estable, stringResource(R.string.seguimiento_enviado))
        else -> Triple(R.drawable.ic_sin_senal, ColoresEnlace.explorar, stringResource(R.string.seguimiento_guardado))
    }
    val minutosDesde = ((System.currentTimeMillis() - pedido.creado) / MS_POR_MINUTO).toInt()

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(icono), contentDescription = null, tint = color, modifier = Modifier.size(32.dp))
            Column {
                Text(titulo, color = color, style = MaterialTheme.typography.titleMedium)
                if (pedido.atencion == EstadoAtencion.ASIGNADO && pedido.equipo != null) {
                    Text(
                        stringResource(R.string.seguimiento_equipo, pedido.equipo, (pedido.minutos ?: 0.0).toInt()),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Text(pedido.resumen, style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.seguimiento_hace, minutosDesde),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
        }
    }
}
