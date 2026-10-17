package com.enlace.app.presentacion.consentimiento

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enlace.app.R
import com.enlace.app.dominio.AjustesRepositorio
import com.enlace.app.dominio.Consentimiento
import com.enlace.app.presentacion.componentes.BotonPrincipal
import com.enlace.app.presentacion.componentes.OpcionElegible
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConsentimientoViewModel @Inject constructor(private val ajustes: AjustesRepositorio) : ViewModel() {
    suspend fun actual(): Consentimiento? = ajustes.consentimiento.first()

    fun guardar(consentimiento: Consentimiento, alGuardar: () -> Unit) {
        viewModelScope.launch {
            ajustes.guardarConsentimiento(consentimiento)
            alGuardar()
        }
    }
}

private val OPCIONES = listOf(
    Triple(Consentimiento.AUTOMATICO, R.string.consentimiento_automatico, R.string.consentimiento_automatico_detalle),
    Triple(
        Consentimiento.CON_CONFIRMACION,
        R.string.consentimiento_confirmacion,
        R.string.consentimiento_confirmacion_detalle,
    ),
    Triple(
        Consentimiento.NO_COMPARTIR,
        R.string.consentimiento_no_compartir,
        R.string.consentimiento_no_compartir_detalle,
    ),
)

/** U2: consentimiento en pantalla propia, no escondido en términos y condiciones (sección 13). */
@Composable
fun PantallaConsentimiento(alGuardar: () -> Unit, viewModel: ConsentimientoViewModel = hiltViewModel()) {
    // Sin opción marcada de antemano: la persona elige de forma explícita
    var elegido by rememberSaveable { mutableStateOf<Consentimiento?>(null) }
    LaunchedEffect(Unit) { viewModel.actual()?.let { elegido = it } }
    // Se guarda la elección aunque se niegue el permiso: igual se podrá pedir ayuda a mano
    val pedirUbicacion = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        elegido?.let { viewModel.guardar(it, alGuardar) }
    }
    fun continuar() {
        val opcion = elegido ?: return
        if (opcion == Consentimiento.NO_COMPARTIR) {
            viewModel.guardar(opcion, alGuardar)
        } else {
            pedirUbicacion.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }

    Scaffold { relleno ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(relleno)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(painterResource(R.drawable.ic_candado), contentDescription = null)
            Text(stringResource(R.string.consentimiento_titulo), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.consentimiento_explicacion), style = MaterialTheme.typography.bodyLarge)
            OPCIONES.forEach { (opcion, titulo, detalle) ->
                OpcionElegible(
                    titulo = stringResource(titulo),
                    detalle = stringResource(detalle),
                    elegida = elegido == opcion,
                    alElegir = { elegido = opcion },
                )
            }
            Text(stringResource(R.string.consentimiento_nota), style = MaterialTheme.typography.bodyMedium)
            BotonPrincipal(
                texto = stringResource(R.string.continuar),
                alTocar = ::continuar,
                habilitado = elegido != null,
            )
        }
    }
}
