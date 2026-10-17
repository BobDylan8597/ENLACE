package com.enlace.app.presentacion.componentes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.enlace.app.R
import com.enlace.app.TEXTO_BLOQUEO
import com.enlace.app.datos.BloqueoCercano
import com.enlace.app.datos.BloqueosCercanos
import com.enlace.app.datos.DecisionBloqueo
import com.enlace.app.dominio.TipoBloqueo
import com.enlace.app.presentacion.tema.ColoresEnlace
import com.enlace.app.presentacion.unidad.textoDistancia
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MAXIMO = 15

data class UiBloqueosCercanos(
    val cargando: Boolean = true,
    val bloqueos: List<BloqueoCercano> = emptyList(),
    val error: Boolean = false,
    /** Ids ya respondidos en esta pantalla (para no repetir y agradecer). */
    val respondidos: Set<Int> = emptySet(),
)

@HiltViewModel
class BloqueosCercanosViewModel @Inject constructor(private val bloqueos: BloqueosCercanos) : ViewModel() {
    private val _ui = MutableStateFlow(UiBloqueosCercanos())
    val ui: StateFlow<UiBloqueosCercanos> = _ui.asStateFlow()

    init {
        cargar()
    }

    fun cargar() {
        _ui.value = _ui.value.copy(cargando = true)
        viewModelScope.launch {
            bloqueos.listar()
                .onSuccess { _ui.value = _ui.value.copy(cargando = false, error = false, bloqueos = it.take(MAXIMO)) }
                .onFailure { _ui.value = _ui.value.copy(cargando = false, error = true) }
        }
    }

    fun decidir(id: Int, decision: DecisionBloqueo, comoBrigada: Boolean) {
        viewModelScope.launch {
            if (bloqueos.decidir(id, decision, comoBrigada)) {
                _ui.value = _ui.value.copy(respondidos = _ui.value.respondidos + id)
                cargar()
            } else {
                _ui.value = _ui.value.copy(error = true)
            }
        }
    }
}

/**
 * U15 (persona de la comunidad): "¿sigue bloqueado?" — confirmar ayuda a que las rutas lo eviten.
 * B11 (brigada): confirmar o descartar; su palabra vale de inmediato.
 */
@Composable
fun PantallaBloqueosCercanos(
    comoBrigada: Boolean,
    alVolver: () -> Unit,
    viewModel: BloqueosCercanosViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Scaffold { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno).padding(horizontal = 24.dp)) {
            Text(
                stringResource(R.string.bloqueos_cercanos_titulo),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                stringResource(if (comoBrigada) R.string.bloqueos_cercanos_brigada else R.string.bloqueos_cercanos_comunidad),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (ui.error) Text(stringResource(R.string.panorama_error), color = ColoresEnlace.actuar)
            if (!ui.cargando && ui.bloqueos.isEmpty() && !ui.error) {
                Text(stringResource(R.string.bloqueos_cercanos_vacio), modifier = Modifier.padding(top = 16.dp))
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(ui.bloqueos, key = { it.id }) { bloqueo ->
                    FilaBloqueo(bloqueo, comoBrigada, bloqueo.id in ui.respondidos) { decision ->
                        viewModel.decidir(bloqueo.id, decision, comoBrigada)
                    }
                }
            }
            TextButton(onClick = alVolver, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.volver))
            }
        }
    }
}

@Composable
private fun FilaBloqueo(
    bloqueo: BloqueoCercano,
    comoBrigada: Boolean,
    respondido: Boolean,
    alDecidir: (DecisionBloqueo) -> Unit,
) {
    val confirmado = bloqueo.estado == "confirmado"
    val color = if (confirmado) ColoresEnlace.actuar else ColoresEnlace.explorar
    val tipo = TipoBloqueo.entries.firstOrNull { it.codigo == bloqueo.tipo } ?: TipoBloqueo.OTRO
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_alerta), contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
                Text(stringResource(TEXTO_BLOQUEO.getValue(tipo)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                bloqueo.distanciaM?.let { Text(textoDistancia(it), style = MaterialTheme.typography.labelLarge) }
            }
            Text(
                stringResource(if (confirmado) R.string.bloqueo_estado_confirmado else R.string.bloqueo_estado_sin_confirmar) +
                    " · " + pluralStringResource(R.plurals.bloqueo_reportes, bloqueo.confirmaciones, bloqueo.confirmaciones) +
                    if (bloqueo.soloAPie) " · " + stringResource(R.string.bloqueo_a_pie_si) else "",
                color = color,
                style = MaterialTheme.typography.bodyMedium,
            )
            when {
                respondido -> Text(stringResource(R.string.bloqueos_cercanos_gracias), color = ColoresEnlace.estable)
                comoBrigada -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!confirmado) {
                        BotonPrincipal(
                            stringResource(R.string.bloqueos_confirmar),
                            { alDecidir(DecisionBloqueo.CONFIRMAR) },
                            modifier = Modifier.weight(1f),
                            color = ColoresEnlace.accion,
                        )
                    }
                    OutlinedButton(
                        onClick = { alDecidir(DecisionBloqueo.DESCARTAR) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.bloqueos_descartar)) }
                }
                !confirmado -> OutlinedButton(
                    onClick = { alDecidir(DecisionBloqueo.CONFIRMAR) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.bloqueos_sigue)) }
            }
        }
    }
}
