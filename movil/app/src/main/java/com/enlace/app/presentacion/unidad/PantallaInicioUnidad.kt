package com.enlace.app.presentacion.unidad

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.enlace.app.R
import com.enlace.app.datos.GestorAlertas
import com.enlace.app.datos.NotificadorAlertas
import com.enlace.app.datos.nearby.RedCercana
import com.enlace.app.datos.textoAlerta
import com.enlace.app.dominio.AlertaVigente
import com.enlace.app.dominio.AsignacionUnidad
import com.enlace.app.dominio.ESTADO_AYUDA
import com.enlace.app.dominio.EquipoUnidad
import com.enlace.app.dominio.EstadoConexion
import com.enlace.app.dominio.MonitorConexion
import com.enlace.app.dominio.PanoramaUnidad
import com.enlace.app.dominio.Punto
import com.enlace.app.dominio.UnidadRepositorio
import com.enlace.app.dominio.minutosRedondeados
import com.enlace.app.dominio.ordenarPanorama
import com.enlace.app.presentacion.componentes.AvisoInicioAutomatico
import com.enlace.app.presentacion.componentes.IndicadorConexion
import com.enlace.app.presentacion.tema.ColoresEnlace
import com.enlace.app.servicio.ServicioAlertas
import com.enlace.app.ubicacion.ProveedorUbicacion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val DEJAR_DE_OBSERVAR_MS = 5_000L
private const val MAXIMO_POR_LISTA = 5

data class UiPanorama(val cargando: Boolean = false, val datos: PanoramaUnidad? = null, val error: Boolean = false)

@HiltViewModel
class InicioUnidadViewModel @Inject constructor(
    private val unidad: UnidadRepositorio,
    private val ubicacion: ProveedorUbicacion,
    private val notificador: NotificadorAlertas,
    monitor: MonitorConexion,
    gestor: GestorAlertas,
    red: RedCercana,
) : ViewModel() {
    val permisos: Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        addAll(red.permisosNecesarios())
    }.distinct().toTypedArray()

    private fun <T> observar(flujo: kotlinx.coroutines.flow.Flow<T>, inicial: T) =
        flujo.stateIn(viewModelScope, SharingStarted.WhileSubscribed(DEJAR_DE_OBSERVAR_MS), inicial)

    val conexion: StateFlow<EstadoConexion> = observar(monitor.estado, EstadoConexion.SinConexion)
    val equipo: StateFlow<EquipoUnidad?> = observar(unidad.equipo, null)
    val asignacion: StateFlow<AsignacionUnidad?> = observar(unidad.asignacion, null)
    val alerta: StateFlow<AlertaVigente?> = observar(gestor.vigente, null)
    val mensajes: StateFlow<Int> = observar(unidad.mensajesQueLleva, 0)

    private val _pantallaCompleta = MutableStateFlow(true)

    /** Falta el permiso para abrir la asignación sobre la pantalla bloqueada (Android 14+). */
    val pantallaCompleta: StateFlow<Boolean> = _pantallaCompleta.asStateFlow()

    fun revisarPantallaCompleta() {
        _pantallaCompleta.value = notificador.puedePantallaCompleta()
    }

    private val _panorama = MutableStateFlow(UiPanorama())
    val panorama: StateFlow<UiPanorama> = _panorama.asStateFlow()

    /** Lo más urgente cerca de la unidad. La ubicación de la brigada sí se consulta (no es ciudadana). */
    fun actualizar() {
        _panorama.value = _panorama.value.copy(cargando = true)
        viewModelScope.launch {
            val aqui = ubicacion.unaVez()?.let { (lat, lon) -> Punto(lat, lon) }
            unidad.panorama()
                .onSuccess { _panorama.value = UiPanorama(datos = ordenarPanorama(it, aqui, MAXIMO_POR_LISTA)) }
                .onFailure { _panorama.value = _panorama.value.copy(cargando = false, error = true) }
        }
    }

    fun salir(alSalir: () -> Unit) {
        viewModelScope.launch {
            unidad.salir()
            alSalir()
        }
    }
}

/** B2: inicio de la unidad. "Ver mapa" muestra estas mismas capas sobre el mapa de la ciudad. */
@Composable
fun PantallaInicioUnidad(
    alAbrirAsignacion: () -> Unit,
    alEvaluar: () -> Unit,
    alVerMapa: () -> Unit,
    alVerBloqueos: () -> Unit,
    alVerMula: () -> Unit,
    alAbrirConexion: () -> Unit,
    alSalir: () -> Unit,
    viewModel: InicioUnidadViewModel = hiltViewModel(),
) {
    val conexion by viewModel.conexion.collectAsStateWithLifecycle()
    val equipo by viewModel.equipo.collectAsStateWithLifecycle()
    val asignacion by viewModel.asignacion.collectAsStateWithLifecycle()
    val alerta by viewModel.alerta.collectAsStateWithLifecycle()
    val mensajes by viewModel.mensajes.collectAsStateWithLifecycle()
    val panorama by viewModel.panorama.collectAsStateWithLifecycle()
    val pantallaCompleta by viewModel.pantallaCompleta.collectAsStateWithLifecycle()
    // Se revisa al volver de los ajustes del sistema
    LifecycleResumeEffect(Unit) {
        viewModel.revisarPantallaCompleta()
        onPauseOrDispose { }
    }
    val contexto = LocalContext.current
    val pedirPermisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        ServicioAlertas.iniciar(contexto)
        viewModel.actualizar()
    }
    LaunchedEffect(Unit) { pedirPermisos.launch(viewModel.permisos) }

    Scaffold { relleno ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(relleno),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.app_nombre),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = ColoresEnlace.accion,
                        )
                        Text(stringResource(R.string.unidad_modo), style = MaterialTheme.typography.labelLarge)
                    }
                    IndicadorConexion(conexion)
                }
            }
            item { TarjetaEquipo(equipo, asignacion) }
            item { AvisoInicioAutomatico() }
            if (!pantallaCompleta && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                item {
                    Tarjeta(
                        color = ColoresEnlace.explorar,
                        icono = R.drawable.ic_alerta,
                        titulo = stringResource(R.string.pantalla_completa_titulo),
                        detalle = stringResource(R.string.pantalla_completa_detalle),
                        alTocar = {
                            contexto.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                    Uri.parse("package:${contexto.packageName}"),
                                ),
                            )
                        },
                    )
                }
            }
            alerta?.let { activa ->
                item {
                    Tarjeta(ColoresEnlace.actuar, R.drawable.ic_alerta, textoAlerta(contexto, activa.alerta), null)
                }
            }
            asignacion?.let { a ->
                item {
                    Tarjeta(
                        color = ColoresEnlace.accion,
                        icono = R.drawable.ic_equipo,
                        titulo = stringResource(R.string.asignacion_en_curso),
                        detalle = stringResource(R.string.asignacion_resumen, a.nombreZona.ifEmpty { a.idH3 }, minutosRedondeados(a.minutos)),
                        alTocar = alAbrirAsignacion,
                    )
                }
            }
            item { FilaMula(mensajes, alVerMula) }
            item {
                OutlinedButton(onClick = alVerMapa, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(painterResource(R.drawable.ic_ubicacion), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        stringResource(R.string.mapa_ver),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                OutlinedButton(onClick = alEvaluar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.evaluacion_boton), style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(onClick = alVerBloqueos, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.bloqueos_cercanos_titulo), style = MaterialTheme.typography.titleMedium)
                }
            }
            seccionesPanorama(panorama, alActualizar = viewModel::actualizar)
            item {
                TextButton(onClick = alAbrirConexion, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.inicio_servidor))
                }
                TextButton(
                    onClick = {
                        viewModel.salir {
                            ServicioAlertas.reiniciar(contexto)
                            alSalir()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.unidad_salir))
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.seccionesPanorama(
    ui: UiPanorama,
    alActualizar: () -> Unit,
) {
    item {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.panorama_titulo),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = alActualizar, enabled = !ui.cargando) {
                Text(stringResource(if (ui.cargando) R.string.panorama_cargando else R.string.panorama_actualizar))
            }
        }
        if (ui.error) Text(stringResource(R.string.panorama_error), color = ColoresEnlace.actuar)
    }
    val datos = ui.datos ?: return
    item { Subtitulo(R.string.panorama_zonas) }
    if (datos.zonas.isEmpty()) item { Vacio() }
    items(datos.zonas, key = { "z" + it.idH3 }) { zona ->
        Fila(
            simbolo = "⚠ ${zona.criticos}",
            color = ColoresEnlace.actuar,
            titulo = zona.nombre,
            detalle = pluralStringResource(R.plurals.panorama_criticos, zona.criticos, zona.criticos),
            distancia = zona.distanciaM,
        )
    }
    item { Subtitulo(R.string.panorama_personas) }
    if (datos.personas.isEmpty()) item { Vacio() }
    items(datos.personas) { persona ->
        val ayuda = persona.estado == ESTADO_AYUDA
        Fila(
            simbolo = if (ayuda) "!" else "?",
            color = if (ayuda) ColoresEnlace.actuar else ColoresEnlace.sinDatos,
            titulo = stringResource(if (ayuda) R.string.persona_ayuda else R.string.persona_sin_respuesta),
            detalle = buildString {
                append(pluralStringResource(R.plurals.panorama_personas_hogar, persona.personas, persona.personas))
                if (persona.vulnerables > 0) {
                    append(" · ")
                    append(pluralStringResource(R.plurals.panorama_vulnerables, persona.vulnerables, persona.vulnerables))
                }
            },
            distancia = persona.distanciaM,
        )
    }
    item { Subtitulo(R.string.panorama_pedidos) }
    if (datos.pedidos.isEmpty()) item { Vacio() }
    items(datos.pedidos, key = { "p" + it.id }) { pedido ->
        Fila(
            simbolo = "!",
            color = ColoresEnlace.actuar,
            titulo = pedido.zona,
            detalle = textoAtender(pedido.atender),
            distancia = pedido.distanciaM,
        )
    }
}

@Composable
private fun TarjetaEquipo(equipo: EquipoUnidad?, asignacion: AsignacionUnidad?) {
    val enRuta = asignacion != null
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painterResource(if (enRuta) R.drawable.ic_equipo else R.drawable.ic_bien),
                contentDescription = null,
                tint = if (enRuta) ColoresEnlace.accion else ColoresEnlace.estable,
                modifier = Modifier.size(32.dp),
            )
            Column {
                Text(equipo?.nombre.orEmpty(), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(if (enRuta) R.string.unidad_en_ruta else R.string.unidad_disponible),
                    color = if (enRuta) ColoresEnlace.accion else ColoresEnlace.estable,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
private fun Tarjeta(color: Color, icono: Int, titulo: String, detalle: String?, alTocar: (() -> Unit)? = null) {
    val contenido: @Composable () -> Unit = {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(painterResource(icono), contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            Column {
                Text(titulo, color = Color.White, style = MaterialTheme.typography.titleLarge)
                detalle?.let { Text(it, color = Color.White) }
            }
        }
    }
    val colores = CardDefaults.cardColors(containerColor = color)
    if (alTocar != null) {
        Card(onClick = alTocar, colors = colores, modifier = Modifier.fillMaxWidth()) { contenido() }
    } else {
        Card(colors = colores, modifier = Modifier.fillMaxWidth()) { contenido() }
    }
}

@Composable
private fun FilaMula(mensajes: Int, alTocar: () -> Unit) {
    // B10: tocar abre la mula de datos (qué lleva y entregar ahora)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = alTocar),
    ) {
        Icon(
            painterResource(R.drawable.ic_red_cercana),
            contentDescription = null,
            tint = ColoresEnlace.accion,
            modifier = Modifier.size(20.dp),
        )
        Text(
            if (mensajes == 0) stringResource(R.string.mula_vacia)
            else pluralStringResource(R.plurals.mula_mensajes, mensajes, mensajes),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun Subtitulo(texto: Int) {
    Text(stringResource(texto), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Vacio() {
    Text(stringResource(R.string.panorama_vacio), style = MaterialTheme.typography.bodyMedium, color = ColoresEnlace.sinDatos)
}

@Composable
private fun Fila(simbolo: String, color: Color, titulo: String, detalle: String, distancia: Double?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(simbolo, color = color, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
        Column(Modifier.weight(1f)) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Text(detalle, style = MaterialTheme.typography.bodyMedium)
        }
        distancia?.let { Text(textoDistancia(it), style = MaterialTheme.typography.labelLarge) }
    }
}
