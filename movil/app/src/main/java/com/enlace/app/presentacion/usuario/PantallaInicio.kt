package com.enlace.app.presentacion.usuario

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.enlace.app.R
import com.enlace.app.dominio.EstadoConexion
import com.enlace.app.dominio.MonitorConexion
import com.enlace.app.presentacion.componentes.IndicadorConexion
import com.enlace.app.presentacion.tema.ColoresEnlace
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Deja de consultar al servidor 5 s después de que nadie mira la pantalla. */
private const val DEJAR_DE_OBSERVAR_MS = 5_000L

@HiltViewModel
class InicioViewModel @Inject constructor(monitor: MonitorConexion) : ViewModel() {
    val conexion: StateFlow<EstadoConexion> = monitor.estado.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(DEJAR_DE_OBSERVAR_MS),
        EstadoConexion.SinConexion,
    )
}

/** U5: inicio del modo usuario. La alerta (U8) y el pedido de ayuda (U10) llegan en 4.2 y 4.3. */
@Composable
fun PantallaInicio(
    alAbrirPerfil: () -> Unit,
    alAbrirPrivacidad: () -> Unit,
    alAbrirConexion: () -> Unit,
    viewModel: InicioViewModel = hiltViewModel(),
) {
    val conexion by viewModel.conexion.collectAsStateWithLifecycle()

    Scaffold { relleno ->
        Column(
            modifier = Modifier.fillMaxSize().padding(relleno).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(R.string.app_nombre),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = ColoresEnlace.accion,
                    modifier = Modifier.weight(1f),
                )
                IndicadorConexion(conexion)
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_bien),
                        contentDescription = null,
                        tint = ColoresEnlace.estable,
                        modifier = Modifier.size(32.dp),
                    )
                    Column {
                        Text(stringResource(R.string.inicio_sin_alerta), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.inicio_sin_alerta_detalle), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            BotonSecundario(R.drawable.ic_persona, stringResource(R.string.inicio_perfil), alAbrirPerfil)
            BotonSecundario(R.drawable.ic_candado, stringResource(R.string.inicio_privacidad), alAbrirPrivacidad)
            BotonSecundario(R.drawable.ic_senal, stringResource(R.string.inicio_servidor), alAbrirConexion)
        }
    }
}

@Composable
private fun BotonSecundario(icono: Int, texto: String, alTocar: () -> Unit) {
    OutlinedButton(onClick = alTocar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(painterResource(icono), contentDescription = null, modifier = Modifier.size(20.dp))
        Text(texto, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
    }
}
