package com.enlace.app.presentacion.usuario

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
import com.enlace.app.datos.GestorAlertas
import com.enlace.app.dominio.Punto
import com.enlace.app.dominio.centroDe
import com.enlace.app.presentacion.mapa.CapasMapa
import com.enlace.app.presentacion.mapa.ColorMapa
import com.enlace.app.presentacion.mapa.ElementoLeyenda
import com.enlace.app.presentacion.mapa.Leyenda
import com.enlace.app.presentacion.mapa.MapaEnlace
import com.enlace.app.ubicacion.ProveedorUbicacion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UiMapaUsuario(
    val estilo: String? = null,
    val capas: CapasMapa = CapasMapa(),
    val centro: Punto? = null,
    val encuadre: List<Punto> = emptyList(),
)

@HiltViewModel
class MapaUsuarioViewModel @Inject constructor(
    private val datos: DatosMapa,
    private val alertas: GestorAlertas,
    private val ubicacion: ProveedorUbicacion,
) : ViewModel() {
    private val _ui = MutableStateFlow(UiMapaUsuario())
    val ui: StateFlow<UiMapaUsuario> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val celdas = datos.celdas()
            // La zona afectada viene en la alerta firmada: se dibuja aunque no haya conexión
            val afectada = alertas.vigente.first()?.alerta?.zonaAfectada.orEmpty().mapNotNull { celdas[it] }
            // Tu posición solo se muestra en tu celular: no se envía a nadie
            val yo = ubicacion.unaVez()?.let { (lat, lon) -> Punto(lat, lon) }
            _ui.value = UiMapaUsuario(
                estilo = datos.estilo(),
                capas = CapasMapa(zonaAfectada = afectada, bloqueos = datos.bloqueos(), yo = yo),
                centro = yo ?: datos.centroCiudad(),
                encuadre = listOfNotNull(yo) + afectada.mapNotNull(::centroDe),
            )
        }
    }
}

/** U6: mapa de la ciudad sin internet, con la zona afectada y las calles bloqueadas. */
@Composable
fun PantallaMapaUsuario(alVolver: () -> Unit, viewModel: MapaUsuarioViewModel = hiltViewModel()) {
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
                Leyenda(
                    listOf(
                        ElementoLeyenda(ColorMapa.EXPLORAR, "", R.string.leyenda_zona_afectada),
                        ElementoLeyenda(ColorMapa.ACTUAR, "×", R.string.leyenda_bloqueo),
                        ElementoLeyenda(ColorMapa.EXPLORAR, "×", R.string.leyenda_bloqueo_sin_confirmar),
                        ElementoLeyenda(ColorMapa.ACCION, "", R.string.leyenda_tu_posicion),
                    ),
                )
                Text(stringResource(R.string.mapa_privacidad), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                TextButton(onClick = alVolver, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.volver))
                }
            }
        }
    }
}
