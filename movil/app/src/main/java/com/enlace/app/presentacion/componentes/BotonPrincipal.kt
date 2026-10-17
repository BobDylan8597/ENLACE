package com.enlace.app.presentacion.componentes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Botón de ancho completo, 56 dp de alto (mínimo 48, sección 16), con ícono opcional. */
@Composable
fun BotonPrincipal(
    texto: String,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    icono: Int? = null,
) {
    Button(
        onClick = alTocar,
        enabled = habilitado,
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp, pressedElevation = 0.dp),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        icono?.let { Icon(painterResource(it), contentDescription = null, modifier = Modifier.size(22.dp).padding(end = 2.dp)) }
        Text(texto, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = if (icono != null) 8.dp else 0.dp))
    }
}
