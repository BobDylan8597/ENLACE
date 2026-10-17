package com.enlace.app.presentacion.configuracion

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import com.enlace.app.presentacion.componentes.BotonPrincipal
import com.enlace.app.presentacion.tema.MarcaEnlace

private data class Paso(val icono: Int, val titulo: Int, val detalle: Int)

private val PASOS = listOf(
    Paso(R.drawable.ic_alerta, R.string.bienvenida_alertas, R.string.bienvenida_alertas_detalle),
    Paso(R.drawable.ic_red_cercana, R.string.bienvenida_sin_internet, R.string.bienvenida_sin_internet_detalle),
    Paso(R.drawable.ic_candado, R.string.bienvenida_privacidad, R.string.bienvenida_privacidad_detalle),
)

/** U1: bienvenida, la primera vez que se abre la app. Qué es ENLACE en tres ideas, con su marca. */
@Composable
fun PantallaBienvenida(alEmpezar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MarcaEnlace.nocheOscuro, MarcaEnlace.noche)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Image(
            painterResource(R.drawable.logo_enlace_blanco),
            contentDescription = stringResource(R.string.app_nombre),
            modifier = Modifier.height(56.dp),
        )
        Text(stringResource(R.string.bienvenida_lema), style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Spacer(Modifier.height(8.dp))
        PASOS.forEach { paso ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier.size(48.dp).background(MarcaEnlace.turquesa.copy(alpha = 0.18f), MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(paso.icono), contentDescription = null, tint = MarcaEnlace.turquesa, modifier = Modifier.size(26.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(paso.titulo), style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Text(stringResource(paso.detalle), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        BotonPrincipal(stringResource(R.string.bienvenida_empezar), alEmpezar, color = MarcaEnlace.turquesaOscuro)
    }
}
