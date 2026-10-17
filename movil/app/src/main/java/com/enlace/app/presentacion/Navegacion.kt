package com.enlace.app.presentacion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.enlace.app.dominio.AjustesRepositorio
import com.enlace.app.dominio.ConexionRepositorio
import com.enlace.app.dominio.Destino
import com.enlace.app.dominio.destinoInicial
import com.enlace.app.presentacion.configuracion.PantallaConfiguracion
import com.enlace.app.presentacion.consentimiento.PantallaConsentimiento
import com.enlace.app.presentacion.modo.PantallaModo
import com.enlace.app.presentacion.usuario.PantallaInicio
import com.enlace.app.presentacion.usuario.PantallaPerfil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable private data object RutaConfiguracion
@Serializable private data object RutaModo
@Serializable private data object RutaConsentimiento
@Serializable private data object RutaInicio
@Serializable private data object RutaPerfil

@HiltViewModel
class ArranqueViewModel @Inject constructor(
    conexion: ConexionRepositorio,
    ajustes: AjustesRepositorio,
) : ViewModel() {
    private val _destino = MutableStateFlow<Destino?>(null)
    val destino: StateFlow<Destino?> = _destino.asStateFlow()

    init {
        viewModelScope.launch {
            _destino.value = destinoInicial(
                conexion.direccion.first(),
                ajustes.modo.first(),
                ajustes.consentimiento.first(),
            )
        }
    }
}

private fun ruta(destino: Destino): Any = when (destino) {
    Destino.CONFIGURACION -> RutaConfiguracion
    Destino.MODO -> RutaModo
    Destino.CONSENTIMIENTO -> RutaConsentimiento
    Destino.INICIO_USUARIO -> RutaInicio
}

/** Avanza y borra el historial: no se vuelve atrás a un paso de configuración ya hecho. */
private fun NavHostController.avanzarA(destino: Any) = navigate(destino) {
    popUpTo(graph.id) { inclusive = true }
}

@Composable
fun NavegacionEnlace(arranque: ArranqueViewModel = hiltViewModel()) {
    val destino by arranque.destino.collectAsStateWithLifecycle()
    val inicial = destino ?: run {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = ruta(inicial)) {
        composable<RutaConfiguracion> {
            // Desde el inicio (Conexión) se vuelve atrás; en la primera configuración, se sigue al modo
            PantallaConfiguracion(alContinuar = { if (!nav.popBackStack()) nav.avanzarA(RutaModo) })
        }
        composable<RutaModo> {
            PantallaModo(alElegir = { nav.avanzarA(RutaConsentimiento) })
        }
        composable<RutaConsentimiento> {
            PantallaConsentimiento(alGuardar = {
                // Desde el inicio (Privacidad) se vuelve atrás; en la primera configuración, al inicio
                if (!nav.popBackStack()) nav.avanzarA(RutaInicio)
            })
        }
        composable<RutaInicio> {
            PantallaInicio(
                alAbrirPerfil = { nav.navigate(RutaPerfil) },
                alAbrirPrivacidad = { nav.navigate(RutaConsentimiento) },
                alAbrirConexion = { nav.navigate(RutaConfiguracion) },
            )
        }
        composable<RutaPerfil> {
            PantallaPerfil(alVolver = { nav.popBackStack() })
        }
    }
}
