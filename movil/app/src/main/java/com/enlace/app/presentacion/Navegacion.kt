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
import com.enlace.app.dominio.Modo
import com.enlace.app.dominio.UnidadRepositorio
import com.enlace.app.dominio.destinoInicial
import com.enlace.app.presentacion.componentes.PantallaBloqueosCercanos
import com.enlace.app.presentacion.configuracion.PantallaBienvenida
import com.enlace.app.presentacion.configuracion.PantallaConfiguracion
import com.enlace.app.presentacion.configuracion.PantallaPermisos
import com.enlace.app.presentacion.consentimiento.PantallaConsentimiento
import com.enlace.app.presentacion.modo.PantallaModo
import com.enlace.app.presentacion.tema.TemaEnlace
import com.enlace.app.presentacion.unidad.PantallaActivarUnidad
import com.enlace.app.presentacion.unidad.PantallaAsignacion
import com.enlace.app.presentacion.unidad.PantallaAtendida
import com.enlace.app.presentacion.unidad.PantallaEvaluacion
import com.enlace.app.presentacion.unidad.PantallaInicioUnidad
import com.enlace.app.presentacion.unidad.PantallaMapaUnidad
import com.enlace.app.presentacion.unidad.PantallaMula
import com.enlace.app.presentacion.unidad.PantallaNavegacion
import com.enlace.app.presentacion.usuario.PantallaAlerta
import com.enlace.app.presentacion.usuario.PantallaBloqueo
import com.enlace.app.presentacion.usuario.PantallaInicio
import com.enlace.app.presentacion.usuario.PantallaMapaUsuario
import com.enlace.app.presentacion.usuario.PantallaPedirAyuda
import com.enlace.app.presentacion.usuario.PantallaPerfil
import com.enlace.app.presentacion.usuario.PantallaQueHacer
import com.enlace.app.presentacion.usuario.PantallaSeguimiento
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

@Serializable private data object RutaBienvenida
@Serializable private data object RutaConfiguracion
@Serializable private data object RutaPerfilInicial
@Serializable private data object RutaPermisos
@Serializable private data object RutaQueHacer
@Serializable private data object RutaBloqueosCercanos
@Serializable private data object RutaBloqueosUnidad
@Serializable private data object RutaMula
@Serializable private data object RutaModo
@Serializable private data object RutaConsentimiento
@Serializable private data object RutaInicio
@Serializable private data object RutaPerfil
@Serializable private data object RutaAlerta
@Serializable private data object RutaPedirAyuda
@Serializable private data object RutaBloqueo
@Serializable private data object RutaSeguimiento
@Serializable private data object RutaActivarUnidad
@Serializable private data object RutaInicioUnidad
@Serializable private data object RutaAsignacion
@Serializable private data object RutaEvaluacion
@Serializable private data object RutaAtendida
@Serializable private data object RutaMapaUnidad
@Serializable private data object RutaNavegacion
@Serializable private data object RutaMapaUsuario

@HiltViewModel
class ArranqueViewModel @Inject constructor(
    conexion: ConexionRepositorio,
    ajustes: AjustesRepositorio,
    gestor: GestorAlertas,
    unidad: UnidadRepositorio,
) : ViewModel() {
    private val _destino = MutableStateFlow<Destino?>(null)
    val destino: StateFlow<Destino?> = _destino.asStateFlow()

    /** Evento de la alerta activa, o null: abre U8 al llegar y oscurece el tema. */
    val eventoActivo: StateFlow<Int?> =
        gestor.vigente.map { it?.alerta?.idEvento }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Asignación de la unidad que todavía no se abrió: abre B3 al llegar. */
    val asignacionNueva: StateFlow<Int?> = unidad.asignacion
        .map { a -> a?.takeIf { !it.vista }?.id }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            _destino.value = destinoInicial(
                conexion.direccion.first(),
                ajustes.modo.first(),
                ajustes.consentimiento.first(),
                unidadActiva = unidad.equipo.first() != null,
            )
        }
    }
}

private fun ruta(destino: Destino): Any = when (destino) {
    Destino.BIENVENIDA -> RutaBienvenida
    Destino.CONFIGURACION -> RutaConfiguracion
    Destino.MODO -> RutaModo
    Destino.CONSENTIMIENTO -> RutaConsentimiento
    Destino.INICIO_USUARIO -> RutaInicio
    Destino.ACTIVAR_UNIDAD -> RutaActivarUnidad
    Destino.INICIO_UNIDAD -> RutaInicioUnidad
}

/** Avanza y borra el historial: no se vuelve atrás a un paso de configuración ya hecho. */
private fun NavHostController.avanzarA(destino: Any) = navigate(destino) {
    popUpTo(graph.id) { inclusive = true }
}

@Composable
fun NavegacionEnlace(arranque: ArranqueViewModel = hiltViewModel()) {
    val destino by arranque.destino.collectAsStateWithLifecycle()
    val eventoActivo by arranque.eventoActivo.collectAsStateWithLifecycle()
    val asignacionNueva by arranque.asignacionNueva.collectAsStateWithLifecycle()

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
        // Una asignación nueva abre B3 en el celular de la unidad
        LaunchedEffect(asignacionNueva) {
            val enInicioUnidad = nav.currentBackStackEntry?.destination?.hasRoute<RutaInicioUnidad>() == true
            if (asignacionNueva != null && enInicioUnidad) nav.navigate(RutaAsignacion)
        }

        NavHost(navController = nav, startDestination = ruta(inicial)) {
            composable<RutaBienvenida> {
                PantallaBienvenida(alEmpezar = { nav.avanzarA(RutaConfiguracion) })
            }
            composable<RutaConfiguracion> {
                // Desde el inicio (Conexión) se vuelve atrás; en la primera configuración, se sigue al modo
                PantallaConfiguracion(alContinuar = { if (!nav.popBackStack()) nav.avanzarA(RutaModo) })
            }
            composable<RutaModo> {
                PantallaModo(alElegir = { modo, yaConfigurado ->
                    nav.avanzarA(
                        when {
                            modo == Modo.UNIDAD -> RutaActivarUnidad
                            yaConfigurado -> RutaInicio
                            else -> RutaConsentimiento
                        },
                    )
                })
            }
            composable<RutaConsentimiento> {
                PantallaConsentimiento(alGuardar = {
                    // Desde el inicio (Privacidad) se vuelve atrás; en la primera configuración, U3 y U4
                    if (!nav.popBackStack()) nav.avanzarA(RutaPerfilInicial)
                })
            }
            composable<RutaInicio> {
                PantallaInicio(
                    alAbrirPerfil = { nav.navigate(RutaPerfil) },
                    alAbrirPrivacidad = { nav.navigate(RutaConsentimiento) },
                    alAbrirConexion = { nav.navigate(RutaConfiguracion) },
                    alAbrirAlerta = { nav.navigate(RutaAlerta) },
                    alPedirAyuda = { nav.navigate(RutaPedirAyuda) },
                    alReportarBloqueo = { nav.navigate(RutaBloqueo) },
                    alVerSeguimiento = { nav.navigate(RutaSeguimiento) },
                    alModoUnidad = { nav.navigate(RutaActivarUnidad) },
                    alVerMapa = { nav.navigate(RutaMapaUsuario) },
                    alVerBloqueos = { nav.navigate(RutaBloqueosCercanos) },
                )
            }
            composable<RutaPerfilInicial> {
                PantallaPerfil(alVolver = { nav.avanzarA(RutaPermisos) }, inicial = true)
            }
            composable<RutaPermisos> {
                PantallaPermisos(alContinuar = { nav.avanzarA(RutaInicio) })
            }
            composable<RutaQueHacer> {
                PantallaQueHacer(alPedirAyuda = { nav.navigate(RutaPedirAyuda) }, alVolver = { nav.popBackStack() })
            }
            composable<RutaBloqueosCercanos> {
                PantallaBloqueosCercanos(comoBrigada = false, alVolver = { nav.popBackStack() })
            }
            composable<RutaBloqueosUnidad> {
                PantallaBloqueosCercanos(comoBrigada = true, alVolver = { nav.popBackStack() })
            }
            composable<RutaMula> {
                PantallaMula(alVolver = { nav.popBackStack() })
            }
            composable<RutaPerfil> {
                PantallaPerfil(alVolver = { nav.popBackStack() })
            }
            composable<RutaAlerta> {
                PantallaAlerta(
                    alVolver = { nav.popBackStack(RutaInicio, inclusive = false) },
                    alPedirAyuda = { nav.navigate(RutaPedirAyuda) },
                    alQueHacer = { nav.navigate(RutaQueHacer) },
                )
            }
            composable<RutaPedirAyuda> {
                PantallaPedirAyuda(
                    alVolver = { nav.popBackStack(RutaInicio, inclusive = false) },
                    alVerSeguimiento = {
                        nav.navigate(RutaSeguimiento) { popUpTo(RutaInicio) }
                    },
                )
            }
            composable<RutaSeguimiento> {
                PantallaSeguimiento(alVolver = { nav.popBackStack(RutaInicio, inclusive = false) })
            }
            composable<RutaBloqueo> {
                PantallaBloqueo(alVolver = { nav.popBackStack(RutaInicio, inclusive = false) })
            }

            // ── Modo unidad ──
            composable<RutaActivarUnidad> {
                PantallaActivarUnidad(
                    alActivar = { nav.avanzarA(RutaInicioUnidad) },
                    // Desde el inicio ciudadano se vuelve atrás; en la primera configuración, al modo
                    alVolver = { if (!nav.popBackStack()) nav.avanzarA(RutaModo) },
                )
            }
            composable<RutaInicioUnidad> {
                PantallaInicioUnidad(
                    alAbrirAsignacion = { nav.navigate(RutaAsignacion) },
                    alEvaluar = { nav.navigate(RutaEvaluacion) },
                    alVerMapa = { nav.navigate(RutaMapaUnidad) },
                    alVerBloqueos = { nav.navigate(RutaBloqueosUnidad) },
                    alVerMula = { nav.navigate(RutaMula) },
                    alAbrirConexion = { nav.navigate(RutaConfiguracion) },
                    alSalir = { nav.avanzarA(RutaModo) },
                )
            }
            composable<RutaAsignacion> {
                PantallaAsignacion(
                    alIrAhora = { nav.navigate(RutaNavegacion) },
                    alEvaluar = { nav.navigate(RutaEvaluacion) },
                    alMarcarAtendida = { nav.navigate(RutaAtendida) },
                    alVolver = { nav.popBackStack(RutaInicioUnidad, inclusive = false) },
                )
            }
            composable<RutaNavegacion> {
                PantallaNavegacion(
                    alEvaluar = { nav.navigate(RutaEvaluacion) },
                    alMarcarAtendida = { nav.navigate(RutaAtendida) },
                    alVolver = { nav.popBackStack() },
                )
            }
            composable<RutaMapaUnidad> {
                PantallaMapaUnidad(alVolver = { nav.popBackStack() })
            }
            composable<RutaMapaUsuario> {
                PantallaMapaUsuario(alVolver = { nav.popBackStack() })
            }
            composable<RutaEvaluacion> {
                PantallaEvaluacion(alVolver = { nav.popBackStack() })
            }
            composable<RutaAtendida> {
                PantallaAtendida(
                    alVolver = { nav.popBackStack() },
                    alTerminar = { nav.popBackStack(RutaInicioUnidad, inclusive = false) },
                )
            }
        }
    }
}
