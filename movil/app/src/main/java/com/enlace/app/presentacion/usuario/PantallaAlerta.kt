package com.enlace.app.presentacion.usuario

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.enlace.app.datos.GestorAlertas
import com.enlace.app.datos.textoAlerta
import com.enlace.app.dominio.AlertaVigente
import com.enlace.app.dominio.EnvioZona
import com.enlace.app.presentacion.componentes.BotonPrincipal
import com.enlace.app.presentacion.tema.ColoresEnlace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** `null` mientras se lee lo guardado; `Leida(null)` si no hay alerta activa. */
data class Leida(val vigente: AlertaVigente?)

@HiltViewModel
class AlertaViewModel @Inject constructor(private val gestor: GestorAlertas) : ViewModel() {
    val alerta: StateFlow<Leida?> =
        gestor.vigente.map(::Leida).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun enviarZona() = viewModelScope.launch { gestor.enviarZona() }

    fun noEnviarZona() = viewModelScope.launch { gestor.noEnviarZona() }

    fun estoyBien() = viewModelScope.launch { gestor.estoyBien() }
}

private val TEXTO_ZONA = mapOf(
    EnvioZona.ENVIANDO to R.string.zona_enviando,
    EnvioZona.ENVIADA to R.string.zona_enviada,
    EnvioZona.UBICACION_ENVIADA to R.string.zona_ubicacion_enviada,
    EnvioZona.NO_COMPARTIDA to R.string.zona_no_compartida,
    EnvioZona.SIN_UBICACION to R.string.zona_sin_ubicacion,
    EnvioZona.FUERA_DE_ZONA to R.string.zona_fuera,
    EnvioZona.SIN_CONEXION to R.string.zona_sin_conexion,
)

/** U8: alerta activa. Modo oscuro y botones de ancho completo (sección 16). */
@Composable
fun PantallaAlerta(
    alVolver: () -> Unit,
    alPedirAyuda: () -> Unit,
    alQueHacer: () -> Unit,
    viewModel: AlertaViewModel = hiltViewModel(),
) {
    val leida by viewModel.alerta.collectAsStateWithLifecycle()
    val cargada = leida ?: return // todavía leyendo lo guardado
    val actual = cargada.vigente
    if (actual == null) {
        // El evento se cerró (o no hay alerta): vuelve al inicio
        LaunchedEffect(Unit) { alVolver() }
        return
    }
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
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    painterResource(R.drawable.ic_alerta),
                    contentDescription = null,
                    tint = ColoresEnlace.actuar,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    textoAlerta(contexto, actual.alerta),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    painterResource(R.drawable.ic_bien),
                    contentDescription = null,
                    tint = ColoresEnlace.estable,
                    modifier = Modifier.size(18.dp),
                )
                Text(stringResource(R.string.alerta_verificada), color = ColoresEnlace.estable)
            }
            Text(stringResource(R.string.alerta_consejo), style = MaterialTheme.typography.bodyLarge)

            if (actual.envioZona == EnvioZona.PENDIENTE_CONFIRMACION) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.alerta_preguntar_zona), style = MaterialTheme.typography.titleMedium)
                        BotonPrincipal(stringResource(R.string.alerta_enviar_zona), { viewModel.enviarZona() })
                        TextButton(onClick = { viewModel.noEnviarZona() }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.alerta_no_enviar_zona))
                        }
                    }
                }
            } else {
                TEXTO_ZONA[actual.envioZona]?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
            }

            if (actual.estoyBien) {
                Text(
                    stringResource(R.string.alerta_gracias),
                    color = ColoresEnlace.estable,
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                BotonPrincipal(
                    texto = stringResource(R.string.alerta_estoy_bien),
                    alTocar = { viewModel.estoyBien() },
                    color = ColoresEnlace.estable,
                )
            }
            BotonPrincipal(
                texto = stringResource(R.string.ayuda_boton),
                alTocar = alPedirAyuda,
                color = ColoresEnlace.actuar,
            )
            TextButton(onClick = alQueHacer, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.que_hacer_titulo), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
