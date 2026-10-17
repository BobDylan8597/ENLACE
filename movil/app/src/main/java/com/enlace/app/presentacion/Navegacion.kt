package com.enlace.app.presentacion

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.enlace.app.datos.GestorAlertas
import com.enlace.app.dominio.AjustesRepositorio
import com.enlace.app.dominio.ConexionRepositorio
import com.enlace.app.dominio.Destino
import com.enlace.app.dominio.destinoInicial
import com.enlace.app.presentacion.configuracion.PantallaConfiguracion
import com.enlace.app.presentacion.consentimiento.PantallaConsentimiento
import com.enlace.app.presentacion.modo.PantallaModo
import com.enlace.app.presentacion.tema.TemaEnlace
import com.enlace.app.presentacion.usuario.PantallaAlerta
import com.enlace.app.presentacion.usuario.PantallaInicio
import com.enlace.app.presentacion.usuario.PantallaPerfil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable private data object RutaConfiguracion
@Serializable private data object RutaModo
@Serializable private data object RutaConsentimiento
@Serializable private data object RutaInicio
@Serializable private data object RutaPerfil
@Serializable private data object RutaAlerta

@HiltViewModel
class ArranqueViewModel @Inject constructor(
    conexion: ConexionRepositorio,
    ajustes: AjustesRepositorio,
    gestor: GestorAlertas,
) : ViewModel() {
    private val _destino = MutableStateFlow<Destino?>(null)
    val destino: StateFlow<Destino?> = _destino.asStateFlow()

    /** Evento de la alerta activa, o null: abre U8 al llegar y oscurece el tema. */
    val eventoActivo: StateFlow<Int?> =
        gestor.vigente.map { it?.alerta?.idEvento }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
    val eventoActivo by arranque.eventoActivo.collectAsStateWithLifecycle()

    // Modo oscuro por defecto durante una alerta (sección 16)
    TemaEnlace(oscuro = eventoActivo != null || isSystemInDarkTheme()) {
        val inicial = destino ?: run {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@TemaEnlace
        }
        val nav = rememberNavController()

        // Una alerta nueva abre U8 si la persona ya terminó de configurar la app
        LaunchedEffect(eventoActivo) {
            val enInicio = nav.currentBackStackEntry?.destination?.hasRoute<RutaInicio>() == true
            if (eventoActivo != null && enInicio) nav.navigate(RutaAlerta)
        }

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
                    alAbrirAlerta = { nav.navigate(RutaAlerta) },
                )
            }
            composable<RutaPerfil> {
                PantallaPerfil(alVolver = { nav.popBackStack() })
            }
            composable<RutaAlerta> {
                PantallaAlerta(alVolver = { nav.popBackStack(RutaInicio, inclusive = false) })
            }
        }
    }
}
