package com.enlace.app.presentacion.componentes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.enlace.app.R
import com.enlace.app.dominio.EstadoConexion
import com.enlace.app.presentacion.tema.ColoresEnlace

/** Siempre visible (sección 16): el color va siempre con ícono y texto. */
@Composable
fun IndicadorConexion(estado: EstadoConexion, modifier: Modifier = Modifier, sobreOscuro: Boolean = false) {
    val (icono, texto, color) = when (estado) {
        EstadoConexion.EnLinea ->
            Triple(R.drawable.ic_senal, stringResource(R.string.conexion_en_linea), ColoresEnlace.estable)
        is EstadoConexion.RedCercana -> Triple(
            R.drawable.ic_red_cercana,
            pluralStringResource(R.plurals.conexion_red_cercana, estado.celulares, estado.celulares),
            ColoresEnlace.explorar,
        )
        EstadoConexion.SinConexion ->
            Triple(R.drawable.ic_sin_senal, stringResource(R.string.conexion_sin_conexion), ColoresEnlace.actuar)
    }
    // Sobre el encabezado azul noche: texto blanco y el color del estado en el ícono
    val colorTexto = if (sobreOscuro) Color.White else color
    val colorIcono = if (sobreOscuro) estadoClaro(color) else color
    Row(
        modifier = modifier
            .background(if (sobreOscuro) Color.White.copy(alpha = 0.12f) else color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(icono), contentDescription = null, tint = colorIcono, modifier = Modifier.size(18.dp))
        Text(texto, color = colorTexto, style = MaterialTheme.typography.labelLarge)
    }
}

/** Versión más clara del color del estado, legible sobre azul noche. */
private fun estadoClaro(color: Color): Color = when (color) {
    ColoresEnlace.estable -> Color(0xFF7BD88F)
    ColoresEnlace.explorar -> Color(0xFFFFC266)
    ColoresEnlace.actuar -> Color(0xFFFF8A80)
    else -> color
}
