package com.enlace.app.presentacion.mapa

import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.enlace.app.R
import com.enlace.app.dominio.Punto
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.fillOutlineColor
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

private const val ZOOM_CIUDAD = 13.0
private const val ZOOM_SEGUIR = 16.0
private const val MARGEN_ENCUADRE_PX = 120
private val FUENTE = arrayOf("Noto Sans Medium")

/** Capas propias, en orden de dibujo (las últimas quedan encima). */
private enum class Capa(val id: String) {
    ZONA_AFECTADA("enlace-zona-afectada"),
    ZONAS_CRITICAS("enlace-zonas-criticas"),
    RUTA("enlace-ruta"),
    BLOQUEOS("enlace-bloqueos"),
    PERSONAS("enlace-personas"),
    PEDIDOS("enlace-pedidos"),
    DESTINO("enlace-destino"),
    YO("enlace-yo"),
}

private fun geojson(capa: Capa, capas: CapasMapa): String = when (capa) {
    Capa.ZONA_AFECTADA -> GeoJsonCapas.zonaAfectada(capas.zonaAfectada)
    Capa.ZONAS_CRITICAS -> GeoJsonCapas.zonasCriticas(capas.zonasCriticas)
    Capa.RUTA -> GeoJsonCapas.ruta(capas.ruta)
    Capa.BLOQUEOS -> GeoJsonCapas.bloqueos(capas.bloqueos)
    Capa.PERSONAS -> GeoJsonCapas.personas(capas.personas)
    Capa.PEDIDOS -> GeoJsonCapas.pedidos(capas.pedidos)
    Capa.DESTINO -> GeoJsonCapas.destino(capas.destino)
    Capa.YO -> GeoJsonCapas.yo(capas.yo)
}

/** Las capas de la app sobre el mapa base: colores y textos salen de las propiedades del GeoJSON. */
private fun agregarCapas(estilo: Style) {
    for (capa in Capa.entries) {
        estilo.addSource(GeoJsonSource(capa.id, "{\"type\":\"FeatureCollection\",\"features\":[]}"))
        val capasDeEsta: List<Layer> = when (capa) {
            Capa.ZONA_AFECTADA, Capa.ZONAS_CRITICAS -> listOf(
                FillLayer(capa.id, capa.id).withProperties(
                    fillColor(get("color")),
                    fillOpacity(get("opacidad")),
                    fillOutlineColor(get("color")),
                ),
                texto("${capa.id}-texto", capa.id, tamano = 15f),
            )
            Capa.RUTA -> listOf(
                LineLayer(capa.id, capa.id).withProperties(
                    lineColor(get("color")),
                    lineWidth(7f),
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
            else -> listOf(
                CircleLayer(capa.id, capa.id).withProperties(
                    circleColor(get("color")),
                    circleRadius(get("radio")),
                    circleStrokeColor(get("borde")),
                    circleStrokeWidth(3f),
                ),
                texto("${capa.id}-texto", capa.id, tamano = 13f, blanco = true),
            )
        }
        capasDeEsta.forEach(estilo::addLayer)
    }
}

private fun texto(id: String, fuente: String, tamano: Float, blanco: Boolean = false) =
    SymbolLayer(id, fuente).withProperties(
        textField(get("texto")),
        textFont(FUENTE),
        textSize(tamano),
        textColor(if (blanco) "#FFFFFF" else ColorMapa.ACTUAR),
        textHaloColor(if (blanco) "#00000000" else "#FFFFFF"),
        textHaloWidth(if (blanco) 0f else 2f),
        textAllowOverlap(true),
        textIgnorePlacement(true),
    )

/**
 * Mapa de la ciudad guardado en el celular (sin internet) con las capas de ENLACE.
 * `encuadre`: puntos que deben verse al abrir; `seguir`: la cámara acompaña a "yo" (navegación).
 */
@Composable
fun MapaEnlace(
    estilo: String?,
    capas: CapasMapa,
    centro: Punto?,
    modifier: Modifier = Modifier,
    encuadre: List<Punto> = emptyList(),
    seguir: Boolean = false,
) {
    if (estilo == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.mapa_no_disponible),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }
    val contexto = LocalContext.current
    val vista = remember {
        MapLibre.getInstance(contexto)
        MapView(contexto).apply { onCreate(Bundle()) }
    }
    var mapa by remember { mutableStateOf<MapLibreMap?>(null) }
    var listo by remember { mutableStateOf(false) }
    var encuadrado by remember { mutableStateOf(false) }

    val ciclo = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(ciclo) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_START -> vista.onStart()
                Lifecycle.Event.ON_RESUME -> vista.onResume()
                Lifecycle.Event.ON_PAUSE -> vista.onPause()
                Lifecycle.Event.ON_STOP -> vista.onStop()
                else -> Unit
            }
        }
        ciclo.addObserver(observador)
        onDispose {
            ciclo.removeObserver(observador)
            vista.onDestroy()
        }
    }

    LaunchedEffect(estilo) {
        vista.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = false
            m.setStyle(Style.Builder().fromJson(estilo)) { s ->
                agregarCapas(s)
                mapa = m
                listo = true
            }
        }
    }

    LaunchedEffect(listo, capas) {
        val m = mapa ?: return@LaunchedEffect
        val s = m.style ?: return@LaunchedEffect
        Capa.entries.forEach { capa -> s.getSourceAs<GeoJsonSource>(capa.id)?.setGeoJson(geojson(capa, capas)) }
        when {
            seguir && capas.yo != null ->
                m.animateCamera(CameraUpdateFactory.newLatLngZoom(capas.yo.latLng(), ZOOM_SEGUIR))
            !encuadrado && encuadre.size >= 2 -> {
                val limites = LatLngBounds.Builder().includes(encuadre.map { it.latLng() }).build()
                m.moveCamera(CameraUpdateFactory.newLatLngBounds(limites, MARGEN_ENCUADRE_PX))
                encuadrado = true
            }
            !encuadrado && (encuadre.firstOrNull() ?: centro) != null -> {
                val punto = encuadre.firstOrNull() ?: centro!!
                m.moveCamera(CameraUpdateFactory.newLatLngZoom(punto.latLng(), ZOOM_CIUDAD))
                encuadrado = true
            }
        }
    }

    AndroidView(factory = { vista }, modifier = modifier)
}

private fun Punto.latLng() = LatLng(lat, lon)
