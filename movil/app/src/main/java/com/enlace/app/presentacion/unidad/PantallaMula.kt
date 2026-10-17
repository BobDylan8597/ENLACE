package com.enlace.app.presentacion.unidad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.enlace.app.R
import com.enlace.app.presentacion.componentes.PantallaConBarra
import com.enlace.app.datos.Outbox
import com.enlace.app.datos.local.MensajeSaliente
import com.enlace.app.dominio.TipoMensaje
import com.enlace.app.presentacion.componentes.BotonPrincipal
import com.enlace.app.presentacion.tema.ColoresEnlace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MS_POR_MINUTO = 60_000L

@HiltViewModel
class MulaViewModel @Inject constructor(private val outbox: Outbox) : ViewModel() {
    val pendientes: StateFlow<List<MensajeSaliente>> =
        outbox.observarPendientes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    /** Devuelve si se entregó todo. */
    fun entregar(alTerminar: (Boolean) -> Unit) = viewModelScope.launch { alTerminar(outbox.enviarPendientes()) }
}

/**
 * B10: mula de datos. Lo que llegó por la red cercana (de celulares sin conexión) y todavía no se
 * entregó al centro. Se entrega solo apenas hay conexión; aquí también se puede forzar.
 * Solo se ve el tipo y la hora: el contenido (datos de quien pidió ayuda) no se muestra.
 */
@Composable
fun PantallaMula(alVolver: () -> Unit, viewModel: MulaViewModel = hiltViewModel()) {
    val pendientes by viewModel.pendientes.collectAsStateWithLifecycle()
    var resultado by remember { mutableStateOf<Boolean?>(null) }
    PantallaConBarra(stringResource(R.string.mula_titulo), alVolver) { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno).padding(horizontal = 24.dp)) {
            Text(stringResource(R.string.mula_explicacion), style = MaterialTheme.typography.bodyLarge)
            Text(
                if (pendientes.isEmpty()) stringResource(R.string.mula_vacia)
                else pluralStringResource(R.plurals.mula_mensajes, pendientes.size, pendientes.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(pendientes, key = { it.idMensaje }) { mensaje ->
                    val minutos = ((System.currentTimeMillis() - mensaje.creado) / MS_POR_MINUTO).toInt()
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(stringResource(textoTipo(mensaje.tipo)), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.seguimiento_hace, minutos) +
                                    " · " + pluralStringResource(R.plurals.mula_intentos, mensaje.intentos, mensaje.intentos),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            resultado?.let {
                Text(
                    stringResource(if (it) R.string.mula_entregado else R.string.mula_sin_conexion),
                    color = if (it) ColoresEnlace.estable else ColoresEnlace.explorar,
                )
            }
            if (pendientes.isNotEmpty()) {
                BotonPrincipal(stringResource(R.string.mula_entregar), { viewModel.entregar { resultado = it } }, color = ColoresEnlace.accion)
            }
        }
    }
}

private fun textoTipo(tipo: TipoMensaje): Int = when (tipo) {
    TipoMensaje.REPORTE -> R.string.mula_tipo_reporte
    TipoMensaje.CHECKIN -> R.string.mula_tipo_checkin
    TipoMensaje.BLOQUEO -> R.string.mula_tipo_bloqueo
}
