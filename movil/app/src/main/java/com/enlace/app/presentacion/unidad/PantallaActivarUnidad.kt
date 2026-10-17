package com.enlace.app.presentacion.unidad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.enlace.app.R
import com.enlace.app.dominio.AjustesRepositorio
import com.enlace.app.dominio.EquipoUnidad
import com.enlace.app.dominio.Modo
import com.enlace.app.dominio.ResultadoClave
import com.enlace.app.dominio.UnidadRepositorio
import com.enlace.app.presentacion.componentes.BotonPrincipal
import com.enlace.app.presentacion.componentes.OpcionElegible
import com.enlace.app.presentacion.tema.ColoresEnlace
import com.enlace.app.servicio.ServicioAlertas
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UiActivar(
    val clave: String = "",
    val comprobando: Boolean = false,
    val error: Int? = null,
    val equipos: List<EquipoUnidad>? = null,
    val elegido: EquipoUnidad? = null,
)

@HiltViewModel
class ActivarUnidadViewModel @Inject constructor(
    private val unidad: UnidadRepositorio,
    private val ajustes: AjustesRepositorio,
) : ViewModel() {
    private val _ui = MutableStateFlow(UiActivar())
    val ui: StateFlow<UiActivar> = _ui.asStateFlow()

    fun cambiarClave(clave: String) = _ui.update { UiActivar(clave = clave) }

    fun elegir(equipo: EquipoUnidad) = _ui.update { it.copy(elegido = equipo) }

    fun comprobar() {
        val clave = _ui.value.clave.trim()
        if (clave.isEmpty()) return
        _ui.update { it.copy(comprobando = true, error = null) }
        viewModelScope.launch {
            val resultado = unidad.comprobarClave(clave)
            _ui.update {
                when (resultado) {
                    is ResultadoClave.Valida -> it.copy(comprobando = false, equipos = resultado.equipos)
                    ResultadoClave.Invalida -> it.copy(comprobando = false, error = R.string.unidad_clave_invalida)
                    ResultadoClave.SinConexion -> it.copy(comprobando = false, error = R.string.configuracion_sin_respuesta)
                }
            }
        }
    }

    fun activar(alActivar: () -> Unit) {
        val equipo = _ui.value.elegido ?: return
        viewModelScope.launch {
            ajustes.guardarModo(Modo.UNIDAD)
            unidad.activar(_ui.value.clave.trim(), equipo)
            alActivar()
        }
    }
}

/** Activar el modo unidad: clave de brigada (no la del administrador) y equipo de este celular. */
@Composable
fun PantallaActivarUnidad(
    alActivar: () -> Unit,
    alVolver: () -> Unit,
    viewModel: ActivarUnidadViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val contexto = LocalContext.current

    Scaffold { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.unidad_activar_titulo), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.unidad_activar_explicacion), style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = ui.clave,
                onValueChange = viewModel::cambiarClave,
                label = { Text(stringResource(R.string.unidad_clave)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            ui.error?.let { Text(stringResource(it), color = ColoresEnlace.actuar) }
            if (ui.equipos == null) {
                BotonPrincipal(
                    texto = stringResource(if (ui.comprobando) R.string.configuracion_probando else R.string.unidad_comprobar),
                    alTocar = viewModel::comprobar,
                    habilitado = ui.clave.isNotBlank() && !ui.comprobando,
                )
            } else {
                Text(stringResource(R.string.unidad_elegir_equipo), style = MaterialTheme.typography.titleMedium)
                ui.equipos.orEmpty().forEach { equipo ->
                    OpcionElegible(
                        titulo = equipo.nombre,
                        detalle = equipo.organismo,
                        elegida = ui.elegido?.id == equipo.id,
                        alElegir = { viewModel.elegir(equipo) },
                    )
                }
                BotonPrincipal(
                    texto = stringResource(R.string.unidad_activar),
                    alTocar = {
                        viewModel.activar {
                            // La conexión se rehace como unidad: recibe sus asignaciones
                            ServicioAlertas.reiniciar(contexto)
                            alActivar()
                        }
                    },
                    habilitado = ui.elegido != null,
                )
            }
            TextButton(onClick = alVolver, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.volver))
            }
        }
    }
}
