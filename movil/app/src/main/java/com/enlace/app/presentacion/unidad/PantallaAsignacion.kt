package com.enlace.app.presentacion.unidad

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.enlace.app.R
import com.enlace.app.datos.NotificadorAlertas
import com.enlace.app.dominio.AsignacionUnidad
import com.enlace.app.dominio.UnidadRepositorio
import com.enlace.app.dominio.minutosRedondeados
import com.enlace.app.presentacion.componentes.BotonPrincipal
import com.enlace.app.presentacion.tema.ColoresEnlace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AsignacionViewModel @Inject constructor(
    private val unidad: UnidadRepositorio,
    private val notificador: NotificadorAlertas,
) : ViewModel() {
    val asignacion: StateFlow<AsignacionUnidad?> =
        unidad.asignacion.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun vista(id: Int) {
        notificador.quitarAsignacion()
        viewModelScope.launch { unidad.marcarVista(id) }
    }
}

/** B3: asignación para la unidad, a pantalla completa: adónde ir, qué atender y a quién llamar. */
@Composable
fun PantallaAsignacion(
    alIrAhora: () -> Unit,
    alEvaluar: () -> Unit,
    alMarcarAtendida: () -> Unit,
    alVolver: () -> Unit,
    viewModel: AsignacionViewModel = hiltViewModel(),
) {
    val asignacion by viewModel.asignacion.collectAsStateWithLifecycle()
    val a = asignacion ?: run {
        Scaffold { relleno ->
            Column(Modifier.fillMaxSize().padding(relleno).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.asignacion_ninguna), style = MaterialTheme.typography.titleLarge)
                BotonPrincipal(stringResource(R.string.envio_volver_inicio), alVolver)
            }
        }
        return
    }
    LaunchedEffect(a.id) { viewModel.vista(a.id) }
    val contexto = LocalContext.current

    Scaffold { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState()),
        ) {
            // Encabezado azul: acción de la brigada (sección 16)
            Column(
                modifier = Modifier.fillMaxWidth().background(ColoresEnlace.accion).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_equipo), contentDescription = null, tint = Color.White)
                    Text(stringResource(R.string.asignacion_nueva), color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    a.nombreZona.ifEmpty { a.idH3 },
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(stringResource(R.string.asignacion_llegada, minutosRedondeados(a.minutos)), color = Color.White)
            }
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                a.pedido?.let { pedido ->
                    Text(stringResource(R.string.asignacion_atender), style = MaterialTheme.typography.titleMedium)
                    Text(textoAtender(pedido), style = MaterialTheme.typography.titleLarge, color = ColoresEnlace.actuar)
                }
                a.contacto?.let { contacto ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.asignacion_contacto), style = MaterialTheme.typography.titleMedium)
                            contacto.nombre?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
                            contacto.condiciones?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                            contacto.telefono?.let { telefono ->
                                // ACTION_DIAL abre el marcador con el número: no hace falta permiso de llamadas
                                OutlinedButton(
                                    onClick = { contexto.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$telefono"))) },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                ) {
                                    Icon(painterResource(R.drawable.ic_persona), contentDescription = null, modifier = Modifier.size(20.dp))
                                    Text(
                                        stringResource(R.string.asignacion_llamar, telefono),
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                Text(stringResource(R.string.asignacion_motivo), style = MaterialTheme.typography.titleMedium)
                Text(a.motivo, style = MaterialTheme.typography.bodyLarge)
                BotonPrincipal(stringResource(R.string.asignacion_ir_ahora), alIrAhora, color = ColoresEnlace.accion)
                OutlinedButton(onClick = alEvaluar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.evaluacion_boton), style = MaterialTheme.typography.titleMedium)
                }
                BotonPrincipal(stringResource(R.string.atendida_boton), alMarcarAtendida, color = ColoresEnlace.estable)
                TextButton(onClick = alVolver, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.envio_volver_inicio))
                }
            }
        }
    }
}
