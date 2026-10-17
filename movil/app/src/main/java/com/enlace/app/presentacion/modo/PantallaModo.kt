package com.enlace.app.presentacion.modo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enlace.app.R
import com.enlace.app.dominio.AjustesRepositorio
import com.enlace.app.dominio.Modo
import com.enlace.app.presentacion.componentes.BotonPrincipal
import com.enlace.app.presentacion.componentes.OpcionElegible
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModoViewModel @Inject constructor(private val ajustes: AjustesRepositorio) : ViewModel() {
    fun elegir(modo: Modo, alGuardar: () -> Unit) {
        viewModelScope.launch {
            ajustes.guardarModo(modo)
            alGuardar()
        }
    }
}

@Composable
fun PantallaModo(alElegir: (Modo) -> Unit, viewModel: ModoViewModel = hiltViewModel()) {
    var modo by rememberSaveable { mutableStateOf(Modo.USUARIO) }
    Scaffold { relleno ->
        Column(
            modifier = Modifier.fillMaxSize().padding(relleno).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.modo_titulo), style = MaterialTheme.typography.headlineSmall)
            OpcionElegible(
                titulo = stringResource(R.string.modo_usuario),
                detalle = stringResource(R.string.modo_usuario_detalle),
                elegida = modo == Modo.USUARIO,
                alElegir = { modo = Modo.USUARIO },
            )
            // El modo unidad se habilita en el paso 4.7 (asignaciones, rutas, mula de datos)
            OpcionElegible(
                titulo = stringResource(R.string.modo_unidad),
                detalle = stringResource(R.string.modo_unidad_detalle),
                elegida = modo == Modo.UNIDAD,
                alElegir = { modo = Modo.UNIDAD },
                habilitada = false,
            )
            BotonPrincipal(stringResource(R.string.continuar), { viewModel.elegir(modo) { alElegir(modo) } })
        }
    }
}
