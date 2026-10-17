package com.enlace.app.presentacion.componentes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.enlace.app.R
import com.enlace.app.dominio.EstadoConexion
import com.enlace.app.presentacion.tema.MarcaEnlace

/**
 * Encabezado de las pantallas de inicio: azul noche con el logo y el estado de la conexión,
 * siempre visible (sección 16). `contenido` va debajo, dentro del mismo fondo (p. ej. el equipo).
 */
@Composable
fun EncabezadoMarca(
    conexion: EstadoConexion,
    subtitulo: String? = null,
    contenido: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(MarcaEnlace.nocheOscuro, MarcaEnlace.noche)),
                RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            )
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Image(
                    painterResource(R.drawable.logo_enlace_blanco),
                    contentDescription = stringResource(R.string.app_nombre),
                    modifier = Modifier.height(30.dp),
                )
                subtitulo?.let {
                    Text(it, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge)
                }
            }
            IndicadorConexion(conexion, sobreOscuro = true)
        }
        contenido?.invoke()
    }
}
