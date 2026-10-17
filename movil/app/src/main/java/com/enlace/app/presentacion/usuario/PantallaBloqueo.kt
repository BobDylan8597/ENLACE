package com.enlace.app.presentacion.usuario

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.enlace.app.R
import com.enlace.app.presentacion.componentes.PantallaConBarra
import com.enlace.app.TEXTO_BLOQUEO
import com.enlace.app.dominio.ReportesRepositorio
import com.enlace.app.dominio.ResultadoEnvio
import com.enlace.app.dominio.TipoBloqueo
import com.enlace.app.presentacion.componentes.BotonPrincipal
import com.enlace.app.presentacion.componentes.OpcionElegible
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UiBloqueo(
    val tipo: TipoBloqueo? = null,
    val pasaAPie: Boolean? = null,
    val enviando: Boolean = false,
    val resultado: ResultadoEnvio? = null,
)

@HiltViewModel
class BloqueoViewModel @Inject constructor(private val reportes: ReportesRepositorio) : ViewModel() {
    private val _ui = MutableStateFlow(UiBloqueo())
    val ui: StateFlow<UiBloqueo> = _ui.asStateFlow()

    fun elegirTipo(tipo: TipoBloqueo) = _ui.update { it.copy(tipo = tipo) }

    fun elegirPasaAPie(pasa: Boolean) = _ui.update { it.copy(pasaAPie = pasa) }

    fun enviar() {
        val actual = _ui.value
        val tipo = actual.tipo ?: return
        val pasaAPie = actual.pasaAPie ?: return
        _ui.update { it.copy(enviando = true) }
        viewModelScope.launch {
            val resultado = reportes.reportarBloqueo(tipo, pasaAPie)
            _ui.update { it.copy(enviando = false, resultado = resultado) }
        }
    }

    fun reintentar() = _ui.update { it.copy(resultado = null) }
}

/** U13–U14: dónde (aquí donde estoy) y qué tipo de bloqueo. Marcarlo en el mapa llega en 4.8. */
@Composable
fun PantallaBloqueo(alVolver: () -> Unit, viewModel: BloqueoViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    PantallaConBarra(stringResource(R.string.bloqueo_titulo), alVolver) { relleno ->
        val resultado = ui.resultado
        if (resultado != null) {
            Column(Modifier.padding(relleno)) {
                ConfirmacionEnvio(
                    resultado = resultado,
                    textoExito = R.string.envio_bloqueo_ok,
                    detalleExito = R.string.envio_bloqueo_ok_detalle,
                    alReintentar = viewModel::reintentar,
                    alVolver = alVolver,
                )
            }
            return@PantallaConBarra
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.bloqueo_donde), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.bloqueo_tipo), style = MaterialTheme.typography.titleMedium)
            TipoBloqueo.entries.forEach { tipo ->
                OpcionElegible(
                    titulo = stringResource(TEXTO_BLOQUEO.getValue(tipo)),
                    elegida = ui.tipo == tipo,
                    alElegir = { viewModel.elegirTipo(tipo) },
                )
            }
            Text(stringResource(R.string.bloqueo_a_pie), style = MaterialTheme.typography.titleMedium)
            OpcionElegible(
                titulo = stringResource(R.string.bloqueo_a_pie_si),
                elegida = ui.pasaAPie == true,
                alElegir = { viewModel.elegirPasaAPie(true) },
            )
            OpcionElegible(
                titulo = stringResource(R.string.bloqueo_a_pie_no),
                elegida = ui.pasaAPie == false,
                alElegir = { viewModel.elegirPasaAPie(false) },
            )
            BotonPrincipal(
                texto = stringResource(R.string.bloqueo_enviar),
                alTocar = viewModel::enviar,
                habilitado = ui.tipo != null && ui.pasaAPie != null && !ui.enviando,
            )
        }
    }
}
