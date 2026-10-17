package com.enlace.app.presentacion.componentes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Botón de ancho completo y al menos 48 dp de alto (sección 16). */
@Composable
fun BotonPrincipal(
    texto: String,
    alTocar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Button(
        onClick = alTocar,
        enabled = habilitado,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(texto, style = MaterialTheme.typography.titleMedium)
    }
}
