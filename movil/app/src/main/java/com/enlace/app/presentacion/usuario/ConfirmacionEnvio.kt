package com.enlace.app.presentacion.usuario

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.enlace.app.R
import com.enlace.app.dominio.ResultadoEnvio
import com.enlace.app.presentacion.componentes.BotonPrincipal
import com.enlace.app.presentacion.tema.ColoresEnlace

/** U11: resultado del envío, con un siguiente paso claro. */
@Composable
fun ConfirmacionEnvio(
    resultado: ResultadoEnvio,
    textoExito: Int,
    detalleExito: Int,
    alReintentar: () -> Unit,
    alVolver: () -> Unit,
) {
    data class Vista(val icono: Int, val color: Color, val titulo: String, val detalle: String?, val exito: Boolean)

    val vista = when (resultado) {
        ResultadoEnvio.Enviado ->
            Vista(R.drawable.ic_bien, ColoresEnlace.estable, stringResource(textoExito), stringResource(detalleExito), true)
        ResultadoEnvio.YaRecibido ->
            Vista(R.drawable.ic_bien, ColoresEnlace.estable, stringResource(R.string.envio_repetido), null, true)
        is ResultadoEnvio.Rechazado -> Vista(
            R.drawable.ic_alerta,
            ColoresEnlace.actuar,
            stringResource(R.string.envio_rechazado, resultado.motivo),
            null,
            false,
        )
        ResultadoEnvio.SinConexion -> Vista(
            R.drawable.ic_sin_senal,
            ColoresEnlace.explorar,
            stringResource(R.string.envio_sin_conexion),
            stringResource(R.string.envio_sin_conexion_detalle),
            false,
        )
        ResultadoEnvio.SinUbicacion -> Vista(
            R.drawable.ic_ubicacion,
            ColoresEnlace.explorar,
            stringResource(R.string.envio_sin_ubicacion),
            stringResource(R.string.envio_sin_ubicacion_detalle),
            false,
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(vista.icono), contentDescription = null, tint = vista.color, modifier = Modifier.size(56.dp))
        Text(vista.titulo, style = MaterialTheme.typography.headlineSmall, color = vista.color)
        vista.detalle?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        if (!vista.exito) BotonPrincipal(stringResource(R.string.envio_reintentar), alReintentar)
        TextButton(onClick = alVolver) { Text(stringResource(R.string.envio_volver_inicio)) }
    }
}
