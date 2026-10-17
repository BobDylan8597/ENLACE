package com.enlace.app.presentacion.usuario

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
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
import com.enlace.app.TEXTO_NECESIDAD
import com.enlace.app.dominio.AjustesRepositorio
import com.enlace.app.dominio.DatosPedido
import com.enlace.app.dominio.Necesidad
import com.enlace.app.dominio.ReportesRepositorio
import com.enlace.app.dominio.ResultadoEnvio
import com.enlace.app.dominio.vulnerablesSugeridos
import com.enlace.app.presentacion.componentes.BotonPrincipal
import com.enlace.app.presentacion.componentes.Contador
import com.enlace.app.presentacion.tema.ColoresEnlace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UiPedido(
    val datos: DatosPedido = DatosPedido(),
    val enviando: Boolean = false,
    val resultado: ResultadoEnvio? = null,
)

@HiltViewModel
class PedirAyudaViewModel @Inject constructor(
    private val reportes: ReportesRepositorio,
    ajustes: AjustesRepositorio,
) : ViewModel() {
    private val _ui = MutableStateFlow(UiPedido())
    val ui: StateFlow<UiPedido> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val perfil = ajustes.perfil.first()
            _ui.update { it.copy(datos = it.datos.copy(vulnerables = vulnerablesSugeridos(perfil))) }
        }
    }

    fun cambiar(transformar: (DatosPedido) -> DatosPedido) = _ui.update { it.copy(datos = transformar(it.datos)) }

    fun enviar() {
        if (_ui.value.enviando) return
        _ui.update { it.copy(enviando = true) }
        viewModelScope.launch {
            val resultado = reportes.pedirAyuda(_ui.value.datos)
            _ui.update { it.copy(enviando = false, resultado = resultado) }
        }
    }

    fun reintentar() = _ui.update { it.copy(resultado = null) }
}

/** U10: reporte rápido de ayuda. Pocas preguntas, botones grandes, un solo envío. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaPedirAyuda(
    alVolver: () -> Unit,
    alVerSeguimiento: () -> Unit,
    viewModel: PedirAyudaViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    PantallaConBarra(stringResource(R.string.ayuda_titulo), alVolver) { relleno ->
        val resultado = ui.resultado
        if (resultado != null) {
            Column(Modifier.padding(relleno)) {
                ConfirmacionEnvio(
                    resultado = resultado,
                    textoExito = R.string.envio_ayuda_ok,
                    detalleExito = R.string.envio_ayuda_ok_detalle,
                    alReintentar = viewModel::reintentar,
                    alVolver = alVolver,
                    alVerSeguimiento = alVerSeguimiento,
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.ayuda_explicacion), style = MaterialTheme.typography.bodyMedium)
            val datos = ui.datos
            Contador(stringResource(R.string.ayuda_personas), datos.personas, { n -> viewModel.cambiar { it.copy(personas = n) } }, minimo = 1)
            Contador(stringResource(R.string.ayuda_atrapados), datos.atrapados, { n -> viewModel.cambiar { it.copy(atrapados = n) } })
            Contador(stringResource(R.string.ayuda_heridos), datos.heridos, { n -> viewModel.cambiar { it.copy(heridos = n) } })
            Contador(stringResource(R.string.ayuda_vulnerables), datos.vulnerables, { n -> viewModel.cambiar { it.copy(vulnerables = n) } })
            Text(stringResource(R.string.ayuda_necesidades), style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Necesidad.entries.forEach { necesidad ->
                    val marcada = necesidad in datos.necesidades
                    FilterChip(
                        selected = marcada,
                        onClick = {
                            viewModel.cambiar {
                                it.copy(necesidades = if (marcada) it.necesidades - necesidad else it.necesidades + necesidad)
                            }
                        },
                        label = { Text(stringResource(TEXTO_NECESIDAD.getValue(necesidad))) },
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
            BotonPrincipal(
                texto = stringResource(if (ui.enviando) R.string.ayuda_enviando else R.string.ayuda_enviar),
                alTocar = viewModel::enviar,
                habilitado = !ui.enviando,
                color = ColoresEnlace.actuar,
            )
        }
    }
}
