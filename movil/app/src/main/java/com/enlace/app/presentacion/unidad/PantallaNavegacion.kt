package com.enlace.app.presentacion.unidad

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.enlace.app.R
import com.enlace.app.datos.DatosMapa
import com.enlace.app.dominio.AsignacionUnidad
import com.enlace.app.dominio.Progreso
import com.enlace.app.dominio.Punto
import com.enlace.app.dominio.RutaNavegable
import com.enlace.app.dominio.UnidadRepositorio
import com.enlace.app.dominio.debeInformarPosicion
import com.enlace.app.dominio.debeRecalcular
import com.enlace.app.dominio.distanciaM
import com.enlace.app.dominio.leerRuta
import com.enlace.app.dominio.minutosRedondeados
import com.enlace.app.dominio.progreso
import com.enlace.app.presentacion.componentes.BotonPrincipal
import com.enlace.app.presentacion.mapa.BloqueoMapa
import com.enlace.app.presentacion.mapa.CapasMapa
import com.enlace.app.presentacion.mapa.MapaEnlace
import com.enlace.app.presentacion.tema.ColoresEnlace
import com.enlace.app.ubicacion.UbicacionContinua
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

private const val SEGUIR_MIENTRAS_MS = 5_000L

data class UiNavegacion(
    val asignacion: AsignacionUnidad? = null,
    val ruta: RutaNavegable? = null,
    val yo: Punto? = null,
    val progreso: Progreso? = null,
)

@HiltViewModel
class NavegacionViewModel @Inject constructor(
    private val unidad: UnidadRepositorio,
    private val datos: DatosMapa,
    ubicacion: UbicacionContinua,
    json: Json,
) : ViewModel() {
    private val _estilo = MutableStateFlow<String?>(null)
    val estilo: StateFlow<String?> = _estilo.asStateFlow()
    private val _bloqueos = MutableStateFlow<List<BloqueoMapa>>(emptyList())
    val bloqueos: StateFlow<List<BloqueoMapa>> = _bloqueos.asStateFlow()
    private var ultimaInformada: Punto? = null
    private var ultimoRecalculo = 0L

    /** La posición se sigue solo mientras la pantalla está visible (y solo en modo unidad). */
    private val posiciones = ubicacion.posiciones().onEach { punto ->
        if (debeInformarPosicion(ultimaInformada, punto) && unidad.informarPosicion(punto)) ultimaInformada = punto
    }

    val ui: StateFlow<UiNavegacion> = combine(unidad.asignacion, posiciones) { asignacion, yo ->
        val ruta = leerRuta(asignacion?.ruta, json)
        val avance = ruta?.let { progreso(it, yo) }
        val ahora = System.currentTimeMillis()
        if (debeRecalcular(avance, ahora - ultimoRecalculo)) {
            ultimoRecalculo = ahora
            viewModelScope.launch { unidad.recalcularRuta(yo) }
        }
        UiNavegacion(asignacion, ruta, yo, avance)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SEGUIR_MIENTRAS_MS), UiNavegacion())

    // Antes de la primera posición también se muestra la asignación y su ruta
    val asignacion: StateFlow<AsignacionUnidad?> =
        unidad.asignacion.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            _estilo.value = datos.estilo()
            _bloqueos.value = datos.bloqueos()
        }
    }

    fun entendido() = viewModelScope.launch {
        unidad.descartarAviso()
        // Un aviso de ruta recalculada viene de un bloqueo: se muestra en el mapa
        _bloqueos.value = datos.bloqueos()
    }
}

/** B5: navegación guiada a la asignación. B6: aviso cuando un bloqueo cambia la ruta. */
@Composable
fun PantallaNavegacion(
    alEvaluar: () -> Unit,
    alMarcarAtendida: () -> Unit,
    alVolver: () -> Unit,
    viewModel: NavegacionViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val estilo by viewModel.estilo.collectAsStateWithLifecycle()
    val bloqueos by viewModel.bloqueos.collectAsStateWithLifecycle()
    val asignacionInicial by viewModel.asignacion.collectAsStateWithLifecycle()
    val asignacion = ui.asignacion ?: asignacionInicial
    val ruta = ui.ruta ?: leerRuta(asignacion?.ruta, Json { ignoreUnknownKeys = true })
    val destino = asignacion?.let { Punto(it.destinoLat, it.destinoLon) }

    Scaffold { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno)) {
            CabeceraNavegacion(asignacion, ruta, ui.progreso, ui.yo)
            asignacion?.aviso?.let { aviso ->
                // B6: ámbar = sin confirmar / cambio; el texto explica por qué cambió
                Row(
                    modifier = Modifier.fillMaxWidth().background(ColoresEnlace.explorar).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_alerta), contentDescription = null, tint = Color.White)
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(stringResource(R.string.ruta_recalculada), color = Color.White, fontWeight = FontWeight.Bold)
                        Text(aviso, color = Color.White)
                    }
                    TextButton(onClick = { viewModel.entendido() }) { Text(stringResource(R.string.entendido), color = Color.White) }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                MapaEnlace(
                    estilo = estilo,
                    capas = CapasMapa(ruta = ruta?.puntos.orEmpty(), destino = destino, bloqueos = bloqueos, yo = ui.yo),
                    centro = destino,
                    encuadre = ruta?.puntos?.let { listOf(it.first(), it.last()) } ?: listOfNotNull(destino),
                    seguir = ui.yo != null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ui.progreso?.llegando == true) {
                    BotonPrincipal(stringResource(R.string.evaluacion_boton), alEvaluar, color = ColoresEnlace.accion)
                    BotonPrincipal(stringResource(R.string.atendida_boton), alMarcarAtendida, color = ColoresEnlace.estable)
                }
                TextButton(onClick = alVolver, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.navegacion_ver_asignacion))
                }
            }
        }
    }
}

@Composable
private fun CabeceraNavegacion(asignacion: AsignacionUnidad?, ruta: RutaNavegable?, progreso: Progreso?, yo: Punto?) {
    val (color, titulo, detalle) = when {
        progreso?.llegando == true -> Triple(ColoresEnlace.estable, stringResource(R.string.navegacion_llegaste), asignacion?.nombreZona)
        ruta == null -> Triple(ColoresEnlace.explorar, stringResource(R.string.navegacion_sin_ruta), stringResource(R.string.navegacion_sin_ruta_detalle))
        progreso == null -> Triple(ColoresEnlace.accion, ruta.instrucciones.firstOrNull()?.texto.orEmpty(), stringResource(R.string.navegacion_buscando_posicion))
        // Fuera de la ruta lo útil es cuánto falta en línea recta (la ruta se recalcula con la posición)
        progreso.fueraDeRuta -> Triple(
            ColoresEnlace.explorar,
            stringResource(R.string.navegacion_fuera_de_ruta),
            yo?.let { stringResource(R.string.navegacion_linea_recta, textoDistancia(distanciaM(it, ruta.puntos.last()))) },
        )
        else -> Triple(
            ColoresEnlace.accion,
            progreso.actual?.texto.orEmpty(),
            progreso.siguiente?.let { stringResource(R.string.navegacion_despues, textoDistancia(progreso.metrosHastaSiguiente), it.texto) },
        )
    }
    Column(Modifier.fillMaxWidth().background(color).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_ubicacion), contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            Text(titulo, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        detalle?.let { Text(it, color = Color.White, style = MaterialTheme.typography.bodyLarge) }
        if (ruta != null && progreso != null && !progreso.llegando && !progreso.fueraDeRuta) {
            val minutos = if (ruta.distanciaM > 0) ruta.minutos * progreso.metrosRestantes / ruta.distanciaM else 0.0
            Text(
                stringResource(R.string.navegacion_restante, textoDistancia(progreso.metrosRestantes), minutosRedondeados(minutos)),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
