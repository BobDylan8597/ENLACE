package com.enlace.app.presentacion.unidad

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import com.enlace.app.datos.DatosMapa
import com.enlace.app.dominio.Punto
import com.enlace.app.dominio.UnidadRepositorio
import com.enlace.app.presentacion.mapa.CapasMapa
import com.enlace.app.presentacion.mapa.ColorMapa
import com.enlace.app.presentacion.mapa.ElementoLeyenda
import com.enlace.app.presentacion.mapa.Leyenda
import com.enlace.app.presentacion.mapa.MapaEnlace
import com.enlace.app.presentacion.tema.ColoresEnlace
import com.enlace.app.ubicacion.ProveedorUbicacion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UiMapaUnidad(
    val cargando: Boolean = true,
    val estilo: String? = null,
    val capas: CapasMapa = CapasMapa(),
    val centro: Punto? = null,
    val encuadre: List<Punto> = emptyList(),
    val sinDatos: Boolean = false,
)

@HiltViewModel
class MapaUnidadViewModel @Inject constructor(
    private val unidad: UnidadRepositorio,
    private val datos: DatosMapa,
    private val ubicacion: ProveedorUbicacion,
) : ViewModel() {
    private val _ui = MutableStateFlow(UiMapaUnidad())
    val ui: StateFlow<UiMapaUnidad> = _ui.asStateFlow()

    init {
        actualizar()
    }

    fun actualizar() {
        _ui.value = _ui.value.copy(cargando = true)
        viewModelScope.launch {
            val estilo = datos.estilo()
            val celdas = datos.celdas()
            val yo = ubicacion.unaVez()?.let { (lat, lon) -> Punto(lat, lon) }
            val asignacion = unidad.asignacion.first()
            val panorama = unidad.panorama().getOrNull()
            val destino = asignacion?.let { Punto(it.destinoLat, it.destinoLon) }
            val capas = CapasMapa(
                zonasCriticas = panorama?.zonas.orEmpty().mapNotNull { z -> celdas[z.idH3]?.let { it to z.criticos } },
                personas = panorama?.personas.orEmpty().filter { it.estado != "bien" },
                pedidos = panorama?.pedidos.orEmpty(),
                bloqueos = datos.bloqueos(),
                destino = destino,
                yo = yo,
            )
            _ui.value = UiMapaUnidad(
                cargando = false,
                estilo = estilo,
                capas = capas,
                centro = yo ?: datos.centroCiudad(),
                // Al abrir se ve dónde está la unidad y lo más urgente (pedidos y su destino)
                encuadre = listOfNotNull(yo, destino) + capas.pedidos.map { it.punto },
                sinDatos = panorama == null,
            )
        }
    }
}

/** B2 en mapa: población crítica, personas sin confirmar, pedidos, bloqueos y su asignación. */
@Composable
fun PantallaMapaUnidad(alVolver: () -> Unit, viewModel: MapaUnidadViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    Scaffold { relleno ->
        Column(Modifier.fillMaxSize().padding(relleno)) {
            MapaEnlace(
                estilo = ui.estilo,
                capas = ui.capas,
                centro = ui.centro,
                encuadre = ui.encuadre,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Column(Modifier.padding(16.dp)) {
                if (ui.sinDatos && !ui.cargando) {
                    Text(stringResource(R.string.panorama_error), color = ColoresEnlace.actuar)
                }
                Leyenda(LEYENDA_UNIDAD)
                TextButton(onClick = viewModel::actualizar, enabled = !ui.cargando, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(
                        stringResource(if (ui.cargando) R.string.panorama_cargando else R.string.panorama_actualizar),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                TextButton(onClick = alVolver, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.volver))
                }
            }
        }
    }
}

val LEYENDA_UNIDAD = listOf(
    ElementoLeyenda(ColorMapa.ACTUAR, "!", R.string.leyenda_ayuda),
    ElementoLeyenda(ColorMapa.SIN_DATOS, "?", R.string.leyenda_sin_respuesta),
    ElementoLeyenda(ColorMapa.SIN_DATOS, "", R.string.leyenda_vulnerables, borde = "#111827"),
    ElementoLeyenda(ColorMapa.ACTUAR, "3", R.string.leyenda_criticos),
    ElementoLeyenda(ColorMapa.ACTUAR, "×", R.string.leyenda_bloqueo),
    ElementoLeyenda(ColorMapa.EXPLORAR, "×", R.string.leyenda_bloqueo_sin_confirmar),
    ElementoLeyenda(ColorMapa.ACCION, "", R.string.leyenda_tu_equipo),
)
