package com.enlace.app.presentacion.configuracion

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.enlace.app.R
import com.enlace.app.datos.nearby.RedCercana
import com.enlace.app.presentacion.componentes.BotonPrincipal
import com.enlace.app.presentacion.tema.ColoresEnlace
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PermisosViewModel @Inject constructor(red: RedCercana) : ViewModel() {
    val permisos: Array<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        addAll(red.permisosNecesarios())
    }.distinct().toTypedArray()
}

private val EXPLICACIONES = listOf(
    Triple(R.drawable.ic_alerta, R.string.permiso_notificaciones, R.string.permiso_notificaciones_detalle),
    Triple(R.drawable.ic_ubicacion, R.string.permiso_ubicacion, R.string.permiso_ubicacion_detalle),
    Triple(R.drawable.ic_red_cercana, R.string.permiso_cercanos, R.string.permiso_cercanos_detalle),
)

/** U4: para qué es cada permiso, antes de que Android los pida (nada escondido). */
@Composable
fun PantallaPermisos(alContinuar: () -> Unit, viewModel: PermisosViewModel = hiltViewModel()) {
    // Con o sin permisos se sigue: la app funciona igual, con menos ayudas
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { alContinuar() }
    Scaffold { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(stringResource(R.string.permisos_titulo), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.permisos_explicacion), style = MaterialTheme.typography.bodyLarge)
            EXPLICACIONES.forEach { (icono, titulo, detalle) ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(painterResource(icono), contentDescription = null, tint = ColoresEnlace.accion, modifier = Modifier.size(32.dp))
                    Column {
                        Text(stringResource(titulo), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(detalle), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            BotonPrincipal(stringResource(R.string.permisos_dar), { pedir.launch(viewModel.permisos) })
            TextButton(onClick = alContinuar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.permisos_ahora_no))
            }
        }
    }
}
