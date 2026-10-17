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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.enlace.app.R
import com.enlace.app.dominio.EstadoConexion
import com.enlace.app.presentacion.tema.ColoresEnlace

/** Siempre visible (sección 16): el color va siempre con ícono y texto. */
@Composable
fun IndicadorConexion(estado: EstadoConexion, modifier: Modifier = Modifier) {
    val (icono, texto, color) = when (estado) {
        EstadoConexion.EnLinea ->
            Triple(R.drawable.ic_senal, stringResource(R.string.conexion_en_linea), ColoresEnlace.estable)
        is EstadoConexion.RedCercana -> Triple(
            R.drawable.ic_red_cercana,
            stringResource(R.string.conexion_red_cercana, estado.celulares),
            ColoresEnlace.explorar,
        )
        EstadoConexion.SinConexion ->
            Triple(R.drawable.ic_sin_senal, stringResource(R.string.conexion_sin_conexion), ColoresEnlace.actuar)
    }
    Row(
        modifier = modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(icono), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(texto, color = color, style = MaterialTheme.typography.labelLarge)
    }
}
