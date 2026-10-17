package com.enlace.app.presentacion.unidad

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.enlace.app.R
import com.enlace.app.TEXTO_NECESIDAD
import com.enlace.app.dominio.Atender
import com.enlace.app.dominio.Necesidad
import java.util.Locale

private const val METROS_POR_KM = 1000.0

/** "350 m", "1,2 km" */
fun textoDistancia(metros: Double, idioma: Locale = Locale.getDefault()): String =
    if (metros < METROS_POR_KM) "${metros.toInt()} m"
    else String.format(idioma, "%.1f km", metros / METROS_POR_KM)

/** "2 atrapadas · 1 herida · 1 con atención especial · Rescate, Agua" */
@Composable
fun textoAtender(atender: Atender): String {
    val partes = buildList {
        if (atender.atrapados > 0) add(pluralStringResource(R.plurals.atender_atrapados, atender.atrapados, atender.atrapados))
        if (atender.heridos > 0) add(pluralStringResource(R.plurals.atender_heridos, atender.heridos, atender.heridos))
        if (atender.vulnerables > 0) {
            add(pluralStringResource(R.plurals.panorama_vulnerables, atender.vulnerables, atender.vulnerables))
        }
        val necesidades = atender.necesidades.mapNotNull { codigo ->
            Necesidad.entries.firstOrNull { it.codigo == codigo }?.let { TEXTO_NECESIDAD[it] }
        }
        if (necesidades.isNotEmpty()) add(necesidades.map { stringResource(it) }.joinToString(", "))
        if (isEmpty()) add(pluralStringResource(R.plurals.panorama_personas_hogar, atender.personas, atender.personas))
    }
    return partes.joinToString(" · ")
}
