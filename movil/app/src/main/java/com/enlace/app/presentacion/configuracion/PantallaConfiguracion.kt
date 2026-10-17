package com.enlace.app.presentacion.configuracion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enlace.app.R
import com.enlace.app.presentacion.tema.ColoresEnlace

/** Altura mínima de los botones críticos (sección 16). */
private val ALTO_BOTON = 48.dp

@Composable
fun PantallaConfiguracion(viewModel: ConfiguracionViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.configuracion_titulo), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.configuracion_ayuda), style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = ui.direccion,
                onValueChange = viewModel::cambiarDireccion,
                label = { Text(stringResource(R.string.configuracion_direccion)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = viewModel::probar,
                enabled = ui.prueba != EstadoPrueba.Probando,
                modifier = Modifier.fillMaxWidth().heightIn(min = ALTO_BOTON),
            ) {
                Text(
                    stringResource(
                        if (ui.prueba == EstadoPrueba.Probando) R.string.configuracion_probando
                        else R.string.configuracion_probar,
                    ),
                )
            }
            ResultadoPrueba(ui.prueba)
        }
    }
}

@Composable
private fun ResultadoPrueba(prueba: EstadoPrueba) {
    val (texto, color) = when (prueba) {
        is EstadoPrueba.Conectado ->
            stringResource(R.string.configuracion_conectado, prueba.info.ciudad, prueba.info.pais) to
                ColoresEnlace.estable
        EstadoPrueba.DireccionInvalida ->
            stringResource(R.string.configuracion_direccion_invalida) to ColoresEnlace.actuar
        EstadoPrueba.SinRespuesta ->
            stringResource(R.string.configuracion_sin_respuesta) to ColoresEnlace.actuar
        EstadoPrueba.SinProbar, EstadoPrueba.Probando -> return
    }
    Text(texto, color = color, style = MaterialTheme.typography.bodyLarge)
}
