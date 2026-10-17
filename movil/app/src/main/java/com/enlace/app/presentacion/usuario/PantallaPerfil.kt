package com.enlace.app.presentacion.usuario

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.enlace.app.R
import com.enlace.app.dominio.AjustesRepositorio
import com.enlace.app.dominio.Condicion
import com.enlace.app.dominio.Perfil
import com.enlace.app.presentacion.componentes.BotonPrincipal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val MAXIMO_PERSONAS = 30

@HiltViewModel
class PerfilViewModel @Inject constructor(private val ajustes: AjustesRepositorio) : ViewModel() {
    private val _perfil = MutableStateFlow(Perfil())
    val perfil: StateFlow<Perfil> = _perfil.asStateFlow()

    init {
        viewModelScope.launch { _perfil.value = ajustes.perfil.first() }
    }

    fun cambiar(transformar: (Perfil) -> Perfil) = _perfil.update(transformar)

    fun guardar(alGuardar: () -> Unit) {
        viewModelScope.launch {
            ajustes.guardarPerfil(_perfil.value.copy(nombre = _perfil.value.nombre.trim()))
            alGuardar()
        }
    }
}

private val TEXTO_CONDICION = mapOf(
    Condicion.MOVILIDAD_REDUCIDA to R.string.condicion_movilidad_reducida,
    Condicion.ADULTO_MAYOR to R.string.condicion_adulto_mayor,
    Condicion.EMBARAZO to R.string.condicion_embarazo,
    Condicion.NINOS to R.string.condicion_ninos,
    Condicion.MEDICACION to R.string.condicion_medicacion,
    Condicion.DISCAPACIDAD to R.string.condicion_discapacidad,
)

/** U18: perfil guardado solo en el celular; se envía únicamente al pedir ayuda (sección 13). */
@Composable
fun PantallaPerfil(alVolver: () -> Unit, viewModel: PerfilViewModel = hiltViewModel()) {
    val perfil by viewModel.perfil.collectAsStateWithLifecycle()

    Scaffold { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.perfil_titulo), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.perfil_explicacion), style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = perfil.nombre,
                onValueChange = { texto -> viewModel.cambiar { it.copy(nombre = texto) } },
                label = { Text(stringResource(R.string.perfil_nombre)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = perfil.telefono,
                onValueChange = { texto -> viewModel.cambiar { it.copy(telefono = texto.filter { c -> c.isDigit() || c in "+ " }) } },
                label = { Text(stringResource(R.string.perfil_telefono)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = perfil.personasEnHogar.toString(),
                onValueChange = { texto ->
                    texto.toIntOrNull()?.takeIf { it in 1..MAXIMO_PERSONAS }?.let { n ->
                        viewModel.cambiar { it.copy(personasEnHogar = n) }
                    }
                },
                label = { Text(stringResource(R.string.perfil_personas)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.perfil_condiciones), style = MaterialTheme.typography.titleMedium)
            TEXTO_CONDICION.forEach { (condicion, texto) ->
                val marcada = condicion in perfil.condiciones
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = marcada, role = Role.Checkbox) {
                            viewModel.cambiar { p ->
                                p.copy(condiciones = if (marcada) p.condiciones - condicion else p.condiciones + condicion)
                            }
                        },
                ) {
                    Checkbox(checked = marcada, onCheckedChange = null)
                    Text(stringResource(texto), style = MaterialTheme.typography.bodyLarge)
                }
            }
            BotonPrincipal(stringResource(R.string.guardar), { viewModel.guardar(alVolver) })
            TextButton(onClick = alVolver, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.volver))
            }
        }
    }
}
