package com.enlace.app.presentacion.usuario

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import com.enlace.app.presentacion.componentes.EncabezadoMarca
import com.enlace.app.presentacion.componentes.FilaAjuste
import com.enlace.app.presentacion.componentes.TarjetaAccion
import com.enlace.app.presentacion.tema.EstiloSeccion
import com.enlace.app.presentacion.tema.MarcaEnlace
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.enlace.app.presentacion.componentes.AvisoInicioAutomatico
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
    alVerBloqueos: () -> Unit,
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

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = relleno.calculateBottomPadding())
                .verticalScroll(rememberScrollState()),
        ) {
            EncabezadoMarca(conexion) {
                // Estado de la emergencia, dentro del encabezado
                alerta?.let { activa ->
                    EstadoAlerta(
                        icono = R.drawable.ic_alerta,
                        titulo = textoAlerta(contexto, activa.alerta),
                        detalle = stringResource(R.string.inicio_ver_alerta),
                        fondo = ColoresEnlace.actuar,
                        alTocar = alAbrirAlerta,
                    )
                } ?: EstadoAlerta(
                    icono = R.drawable.ic_bien,
                    titulo = stringResource(R.string.inicio_sin_alerta),
                    detalle = stringResource(R.string.inicio_sin_alerta_detalle),
                    fondo = Color.White.copy(alpha = 0.1f),
                    alTocar = null,
                )
            }
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AvisoInicioAutomatico()
                // Sin alerta activa también se puede pedir ayuda: alimenta la propuesta comunitaria
                BotonAyuda(alPedirAyuda)
                Text(stringResource(R.string.inicio_seccion_acciones).uppercase(), style = EstiloSeccion, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TarjetaAccion(R.drawable.ic_alerta, stringResource(R.string.bloqueo_boton), ColoresEnlace.explorar, alReportarBloqueo, Modifier.weight(1f))
                    TarjetaAccion(R.drawable.ic_ubicacion, stringResource(R.string.mapa_ver), ColoresEnlace.accion, alVerMapa, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TarjetaAccion(R.drawable.ic_red_cercana, stringResource(R.string.bloqueos_cercanos_titulo), MaterialTheme.colorScheme.secondary, alVerBloqueos, Modifier.weight(1f))
                    TarjetaAccion(R.drawable.ic_equipo, stringResource(R.string.seguimiento_titulo), ColoresEnlace.estable, alVerSeguimiento, Modifier.weight(1f))
                }
                Text(
                    stringResource(R.string.inicio_seccion_ajustes).uppercase(),
                    style = EstiloSeccion,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                FilaAjuste(R.drawable.ic_persona, stringResource(R.string.inicio_perfil), alAbrirPerfil)
                FilaAjuste(R.drawable.ic_candado, stringResource(R.string.inicio_privacidad), alAbrirPrivacidad)
                FilaAjuste(R.drawable.ic_senal, stringResource(R.string.inicio_servidor), alAbrirConexion)
                FilaAjuste(R.drawable.ic_equipo, stringResource(R.string.inicio_modo_unidad), alModoUnidad)
            }
        }
    }
}

/** Estado de la emergencia dentro del encabezado azul noche. */
@Composable
private fun EstadoAlerta(icono: Int, titulo: String, detalle: String, fondo: Color, alTocar: (() -> Unit)?) {
    val contenido: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(44.dp).background(Color.White.copy(alpha = 0.16f), MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icono), contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(titulo, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(detalle, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium)
            }
            if (alTocar != null) Text("›", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
    }
    val colores = CardDefaults.cardColors(containerColor = fondo)
    if (alTocar != null) {
        Card(onClick = alTocar, colors = colores, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) { contenido() }
    } else {
        Card(colors = colores, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) { contenido() }
    }
}

/** "Necesito ayuda": el botón más importante de la app, grande y rojo (sección 16). */
@Composable
private fun BotonAyuda(alTocar: () -> Unit) {
    Card(
        onClick = alTocar,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = ColoresEnlace.actuar),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.size(56.dp).background(Color.White.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_alerta), contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Column {
                Text(stringResource(R.string.ayuda_boton), color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.inicio_ayuda_detalle), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
