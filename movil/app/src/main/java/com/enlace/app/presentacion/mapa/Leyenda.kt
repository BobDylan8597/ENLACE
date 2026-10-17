package com.enlace.app.presentacion.mapa

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Elemento de la leyenda: el mismo círculo con símbolo que se ve en el mapa, y su texto. */
data class ElementoLeyenda(val color: String, val simbolo: String, val texto: Int, val borde: String = "#FFFFFF")

@Composable
fun Leyenda(elementos: List<ElementoLeyenda>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        elementos.forEach { e ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(color(e.color), CircleShape)
                        .border(2.dp, color(e.borde), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(e.simbolo, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
                Text(stringResource(e.texto), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun color(hex: String) = Color(android.graphics.Color.parseColor(hex))
