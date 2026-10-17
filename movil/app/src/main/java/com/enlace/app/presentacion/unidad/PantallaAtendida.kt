package com.enlace.app.presentacion.unidad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.enlace.app.R
import com.enlace.app.dominio.AsignacionUnidad
import com.enlace.app.dominio.ResultadoAtendida
import com.enlace.app.dominio.UnidadRepositorio
import com.enlace.app.presentacion.componentes.BotonPrincipal
import com.enlace.app.presentacion.tema.ColoresEnlace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UiAtendida(val enviando: Boolean = false, val resultado: ResultadoAtendida? = null)

@HiltViewModel
class AtendidaViewModel @Inject constructor(private val unidad: UnidadRepositorio) : ViewModel() {
    val asignacion: StateFlow<AsignacionUnidad?> =
        unidad.asignacion.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _ui = MutableStateFlow(UiAtendida())
    val ui: StateFlow<UiAtendida> = _ui.asStateFlow()

    fun confirmar() {
        if (_ui.value.enviando) return
        _ui.value = UiAtendida(enviando = true)
        viewModelScope.launch { _ui.value = UiAtendida(resultado = unidad.marcarAtendida()) }
    }
}

/** B9: la unidad terminó en la zona. El equipo queda disponible y las prioridades se recalculan. */
@Composable
fun PantallaAtendida(alVolver: () -> Unit, alTerminar: () -> Unit, viewModel: AtendidaViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val asignacion by viewModel.asignacion.collectAsStateWithLifecycle()

    Scaffold { relleno ->
        Column(
            modifier = Modifier.fillMaxSize().padding(relleno).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (ui.resultado) {
                ResultadoAtendida.ATENDIDA -> {
                    Icon(
                        painterResource(R.drawable.ic_bien),
                        contentDescription = null,
                        tint = ColoresEnlace.estable,
                        modifier = Modifier.size(64.dp),
                    )
                    Text(stringResource(R.string.atendida_ok), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.atendida_ok_detalle), style = MaterialTheme.typography.bodyLarge)
                    BotonPrincipal(stringResource(R.string.envio_volver_inicio), alTerminar)
                }
                else -> {
                    Text(stringResource(R.string.atendida_pregunta), style = MaterialTheme.typography.headlineSmall)
                    asignacion?.let {
                        Text(it.nombreZona.ifEmpty { it.idH3 }, style = MaterialTheme.typography.titleLarge)
                    }
                    Text(stringResource(R.string.atendida_explicacion), style = MaterialTheme.typography.bodyLarge)
                    if (ui.resultado == ResultadoAtendida.SIN_CONEXION) {
                        Text(stringResource(R.string.atendida_sin_conexion), color = ColoresEnlace.actuar)
                    }
                    BotonPrincipal(
                        texto = stringResource(if (ui.enviando) R.string.ayuda_enviando else R.string.atendida_confirmar),
                        alTocar = viewModel::confirmar,
                        habilitado = !ui.enviando,
                        color = ColoresEnlace.estable,
                    )
                    TextButton(onClick = alVolver, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.volver))
                    }
                }
            }
        }
    }
}
