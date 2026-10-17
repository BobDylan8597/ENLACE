package com.enlace.app.presentacion.usuario

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.enlace.app.datos.nearby.RedCercana
import com.enlace.app.datos.textoAlerta
import com.enlace.app.dominio.AlertaVigente
import com.enlace.app.dominio.EstadoConexion
import com.enlace.app.dominio.MonitorConexion
import com.enlace.app.presentacion.componentes.BotonPrincipal
import com.enlace.app.presentacion.componentes.IndicadorConexion
import com.enlace.app.presentacion.tema.ColoresEnlace
import com.enlace.app.servicio.ServicioAlertas
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Deja de consultar al servidor 5 s después de que nadie mira la pantalla. */
private const val DEJAR_DE_OBSERVAR_MS = 5_000L

@HiltViewModel
class InicioViewModel @Inject constructor(
    monitor: MonitorConexion,
    gestor: GestorAlertas,
    red: RedCercana,
) : ViewModel() {
    /** Notificaciones (Android 13+) y red cercana: se piden juntos al llegar al inicio. */
    val permisos: Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        addAll(red.permisosNecesarios())
    }.distinct().toTypedArray()

    val conexion: StateFlow<EstadoConexion> = monitor.estado.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(DEJAR_DE_OBSERVAR_MS),
        EstadoConexion.SinConexion,
    )
    val alerta: StateFlow<AlertaVigente?> =
        gestor.vigente.stateIn(viewModelScope, SharingStarted.WhileSubscribed(DEJAR_DE_OBSERVAR_MS), null)
}

/** U5: inicio del modo usuario. La alerta (U8) y el pedido de ayuda (U10) llegan en 4.2 y 4.3. */
@Composable
fun PantallaInicio(
    alAbrirPerfil: () -> Unit,
    alAbrirPrivacidad: () -> Unit,
    alAbrirConexion: () -> Unit,
    alAbrirAlerta: () -> Unit,
    alPedirAyuda: () -> Unit,
    alReportarBloqueo: () -> Unit,
    alVerSeguimiento: () -> Unit,
    alModoUnidad: () -> Unit,
    alVerMapa: () -> Unit,
    viewModel: InicioViewModel = hiltViewModel(),
) {
    val conexion by viewModel.conexion.collectAsStateWithLifecycle()
    val alerta by viewModel.alerta.collectAsStateWithLifecycle()
    val contexto = LocalContext.current
    val pedirPermisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Con o sin permisos, el servicio arranca: sin ellos, la alerta se ve al abrir la app
        ServicioAlertas.iniciar(contexto)
    }
    LaunchedEffect(Unit) { pedirPermisos.launch(viewModel.permisos) }

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
            alerta?.let { activa ->
                Card(
                    onClick = alAbrirAlerta,
                    colors = CardDefaults.cardColors(containerColor = ColoresEnlace.actuar),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_alerta),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                        Column {
                            Text(textoAlerta(contexto, activa.alerta), color = Color.White, style = MaterialTheme.typography.titleLarge)
                            Text(stringResource(R.string.inicio_ver_alerta), color = Color.White)
                        }
                    }
                }
            } ?: Card(modifier = Modifier.fillMaxWidth()) {
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
            // Sin alerta activa también se puede pedir ayuda: alimenta la propuesta comunitaria
            BotonPrincipal(stringResource(R.string.ayuda_boton), alPedirAyuda, color = ColoresEnlace.actuar)
            BotonSecundario(R.drawable.ic_alerta, stringResource(R.string.bloqueo_boton), alReportarBloqueo)
            BotonSecundario(R.drawable.ic_ubicacion, stringResource(R.string.mapa_ver), alVerMapa)
            BotonSecundario(R.drawable.ic_equipo, stringResource(R.string.seguimiento_titulo), alVerSeguimiento)
            BotonSecundario(R.drawable.ic_persona, stringResource(R.string.inicio_perfil), alAbrirPerfil)
            BotonSecundario(R.drawable.ic_candado, stringResource(R.string.inicio_privacidad), alAbrirPrivacidad)
            BotonSecundario(R.drawable.ic_senal, stringResource(R.string.inicio_servidor), alAbrirConexion)
            BotonSecundario(R.drawable.ic_equipo, stringResource(R.string.inicio_modo_unidad), alModoUnidad)
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
