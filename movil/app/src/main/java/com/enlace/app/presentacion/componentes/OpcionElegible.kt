package com.enlace.app.presentacion.componentes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Opción grande (mínimo 48 dp) con título y explicación corta; toda la tarjeta se puede tocar. */
@Composable
fun OpcionElegible(
    titulo: String,
    detalle: String = "",
    elegida: Boolean,
    alElegir: () -> Unit,
    habilitada: Boolean = true,
) {
    OutlinedCard(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = if (elegida) 2.dp else 1.dp,
            color = if (elegida) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = elegida, enabled = habilitada, role = Role.RadioButton, onClick = alElegir),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RadioButton(selected = elegida, onClick = null, enabled = habilitada)
            Column {
                Text(titulo, style = MaterialTheme.typography.titleMedium)
                if (detalle.isNotEmpty()) Text(detalle, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
