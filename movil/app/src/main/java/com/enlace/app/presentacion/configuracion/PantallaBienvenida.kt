package com.enlace.app.presentacion.configuracion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enlace.app.R
import com.enlace.app.presentacion.componentes.BotonPrincipal
import com.enlace.app.presentacion.tema.ColoresEnlace

private data class Paso(val icono: Int, val color: Color, val titulo: Int, val detalle: Int)

private val PASOS = listOf(
    Paso(R.drawable.ic_alerta, ColoresEnlace.actuar, R.string.bienvenida_alertas, R.string.bienvenida_alertas_detalle),
    Paso(R.drawable.ic_red_cercana, ColoresEnlace.accion, R.string.bienvenida_sin_internet, R.string.bienvenida_sin_internet_detalle),
    Paso(R.drawable.ic_candado, ColoresEnlace.estable, R.string.bienvenida_privacidad, R.string.bienvenida_privacidad_detalle),
)

/** U1: bienvenida, la primera vez que se abre la app. Qué es ENLACE en tres ideas. */
@Composable
fun PantallaBienvenida(alEmpezar: () -> Unit) {
    Scaffold { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                stringResource(R.string.app_nombre),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = ColoresEnlace.accion,
            )
            Text(stringResource(R.string.bienvenida_lema), style = MaterialTheme.typography.titleLarge)
            PASOS.forEach { paso ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(painterResource(paso.icono), contentDescription = null, tint = paso.color, modifier = Modifier.size(36.dp))
                    Column {
                        Text(stringResource(paso.titulo), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(paso.detalle), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            BotonPrincipal(stringResource(R.string.bienvenida_empezar), alEmpezar)
        }
    }
}
