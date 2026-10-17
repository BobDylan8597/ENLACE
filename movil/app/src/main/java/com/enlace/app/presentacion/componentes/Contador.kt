package com.enlace.app.presentacion.componentes

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.enlace.app.R

/** Número con botones − y + grandes (48 dp): fácil de usar con prisa o con las manos temblando. */
@Composable
fun Contador(etiqueta: String, valor: Int, alCambiar: (Int) -> Unit, minimo: Int = 0, maximo: Int = 99) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(etiqueta, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        val restar = stringResource(R.string.restar)
        val sumar = stringResource(R.string.sumar)
        FilledTonalButton(
            onClick = { alCambiar(valor - 1) },
            enabled = valor > minimo,
            modifier = Modifier.size(48.dp).semantics { contentDescription = "$restar $etiqueta" },
        ) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(
            valor.toString(),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp),
        )
        FilledTonalButton(
            onClick = { alCambiar(valor + 1) },
            enabled = valor < maximo,
            modifier = Modifier.size(48.dp).semantics { contentDescription = "$sumar $etiqueta" },
        ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}
