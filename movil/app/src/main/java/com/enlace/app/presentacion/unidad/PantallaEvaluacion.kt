package com.enlace.app.presentacion.unidad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.enlace.app.dominio.DatosEvaluacion
import com.enlace.app.dominio.Necesidad
import com.enlace.app.dominio.NivelDano
import com.enlace.app.dominio.ResultadoEnvio
import com.enlace.app.dominio.UnidadRepositorio
import com.enlace.app.presentacion.componentes.BotonPrincipal
import com.enlace.app.presentacion.componentes.Contador
import com.enlace.app.presentacion.componentes.OpcionElegible
import com.enlace.app.presentacion.tema.ColoresEnlace
import com.enlace.app.presentacion.usuario.ConfirmacionEnvio
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

val TEXTO_NIVEL_DANO = mapOf(
    NivelDano.NINGUNO to (R.string.dano_ninguno to R.string.dano_ninguno_detalle),
    NivelDano.LEVE to (R.string.dano_leve to R.string.dano_leve_detalle),
    NivelDano.MODERADO to (R.string.dano_moderado to R.string.dano_moderado_detalle),
    NivelDano.GRAVE to (R.string.dano_grave to R.string.dano_grave_detalle),
    NivelDano.COLAPSO to (R.string.dano_colapso to R.string.dano_colapso_detalle),
)

data class UiEvaluacion(
    val datos: DatosEvaluacion = DatosEvaluacion(),
    val enviando: Boolean = false,
    val resultado: ResultadoEnvio? = null,
)

@HiltViewModel
class EvaluacionViewModel @Inject constructor(private val unidad: UnidadRepositorio) : ViewModel() {
    private val _ui = MutableStateFlow(UiEvaluacion())
    val ui: StateFlow<UiEvaluacion> = _ui.asStateFlow()

    fun cambiar(transformar: (DatosEvaluacion) -> DatosEvaluacion) = _ui.update { it.copy(datos = transformar(it.datos)) }

    fun enviar() {
        if (_ui.value.datos.nivel == null || _ui.value.enviando) return
        _ui.update { it.copy(enviando = true) }
        viewModelScope.launch {
            val resultado = unidad.evaluar(_ui.value.datos)
            _ui.update { it.copy(enviando = false, resultado = resultado) }
        }
    }

    fun reintentar() = _ui.update { it.copy(resultado = null) }
}

/** B8: evaluación de zona. Es el reporte de mayor confianza para el motor (brigada = 1.0). */
@Composable
fun PantallaEvaluacion(alVolver: () -> Unit, viewModel: EvaluacionViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    ui.resultado?.let { resultado ->
        ConfirmacionEnvio(
            resultado = resultado,
            textoExito = R.string.evaluacion_ok,
            detalleExito = R.string.evaluacion_ok_detalle,
            alReintentar = viewModel::reintentar,
            alVolver = alVolver,
        )
        return
    }
    val datos = ui.datos

    PantallaConBarra(stringResource(R.string.evaluacion_titulo), alVolver) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.evaluacion_nivel), style = MaterialTheme.typography.titleMedium)
            NivelDano.entries.forEach { nivel ->
                val (titulo, detalle) = TEXTO_NIVEL_DANO.getValue(nivel)
                OpcionElegible(
                    titulo = stringResource(titulo),
                    detalle = stringResource(detalle),
                    elegida = datos.nivel == nivel,
                    alElegir = { viewModel.cambiar { it.copy(nivel = nivel) } },
                )
            }
            Contador(stringResource(R.string.evaluacion_personas), datos.personas, { n -> viewModel.cambiar { it.copy(personas = n) } })
            Contador(stringResource(R.string.ayuda_atrapados), datos.atrapados, { n -> viewModel.cambiar { it.copy(atrapados = n) } })
            Contador(stringResource(R.string.ayuda_heridos), datos.heridos, { n -> viewModel.cambiar { it.copy(heridos = n) } })
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
            if (datos.nivel == null) {
                Text(stringResource(R.string.evaluacion_falta_nivel), color = ColoresEnlace.explorar)
            }
            BotonPrincipal(
                texto = stringResource(if (ui.enviando) R.string.ayuda_enviando else R.string.evaluacion_enviar),
                alTocar = viewModel::enviar,
                habilitado = datos.nivel != null && !ui.enviando,
                color = ColoresEnlace.accion,
            )
        }
    }
}
