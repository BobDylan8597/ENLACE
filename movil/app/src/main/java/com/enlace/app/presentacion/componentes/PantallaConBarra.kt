package com.enlace.app.presentacion.componentes

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.enlace.app.R

/**
 * Pantalla secundaria: barra superior con la flecha para volver y el título, igual en toda la app.
 * Sin `alVolver` (primera configuración, por ejemplo) la barra no muestra la flecha.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaConBarra(titulo: String, alVolver: (() -> Unit)?, contenido: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(titulo, style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    if (alVolver != null) {
                        IconButton(onClick = alVolver) {
                            Icon(painterResource(R.drawable.ic_atras), contentDescription = stringResource(R.string.volver))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        content = contenido,
    )
}
