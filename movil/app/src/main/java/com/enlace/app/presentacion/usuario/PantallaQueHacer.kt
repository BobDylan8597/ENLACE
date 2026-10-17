package com.enlace.app.presentacion.usuario

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.enlace.app.R
import com.enlace.app.presentacion.componentes.PantallaConBarra
import com.enlace.app.datos.GestorAlertas
import com.enlace.app.presentacion.componentes.BotonPrincipal
import com.enlace.app.presentacion.tema.ColoresEnlace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class QueHacerViewModel @Inject constructor(gestor: GestorAlertas) : ViewModel() {
    /** Tipo de la emergencia activa ("sismo", "inundacion"…), o null sin alerta. */
    val tipo: StateFlow<String?> =
        gestor.vigente.map { it?.alerta?.tipo }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

/**
 * U7: qué hacer ahora, según el tipo de emergencia. Los consejos van en la app: se leen sin
 * conexión. Los albergues y puntos de encuentro oficiales los publicaría cada organismo.
 */
@Composable
fun PantallaQueHacer(alPedirAyuda: () -> Unit, alVolver: () -> Unit, viewModel: QueHacerViewModel = hiltViewModel()) {
    val tipo by viewModel.tipo.collectAsStateWithLifecycle()
    val consejos = stringArrayResource(
        when (tipo) {
            "sismo" -> R.array.consejos_sismo
            "inundacion" -> R.array.consejos_inundacion
            "huracan" -> R.array.consejos_huracan
            else -> R.array.consejos_general
        },
    )
    PantallaConBarra(stringResource(R.string.que_hacer_titulo), alVolver) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            consejos.forEachIndexed { i, consejo ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${i + 1}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = ColoresEnlace.accion)
                    Text(consejo, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text(stringResource(R.string.que_hacer_albergues), style = MaterialTheme.typography.bodyMedium)
            BotonPrincipal(stringResource(R.string.ayuda_boton), alPedirAyuda, color = ColoresEnlace.actuar)
        }
    }
}
