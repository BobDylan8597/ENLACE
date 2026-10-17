package com.enlace.app.presentacion.configuracion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enlace.app.dominio.ConexionRepositorio
import com.enlace.app.dominio.DireccionServidor
import com.enlace.app.dominio.InfoServidor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface EstadoPrueba {
    data object SinProbar : EstadoPrueba
    data object Probando : EstadoPrueba
    data object DireccionInvalida : EstadoPrueba
    data object SinRespuesta : EstadoPrueba
    data class Conectado(val info: InfoServidor) : EstadoPrueba
}

data class UiConfiguracion(
    val direccion: String = "",
    val prueba: EstadoPrueba = EstadoPrueba.SinProbar,
)

@HiltViewModel
class ConfiguracionViewModel @Inject constructor(
    private val conexion: ConexionRepositorio,
) : ViewModel() {

    private val _ui = MutableStateFlow(UiConfiguracion())
    val ui: StateFlow<UiConfiguracion> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            conexion.direccion.first()?.let { guardada ->
                _ui.update { it.copy(direccion = guardada.removePrefix("http://").trimEnd('/')) }
            }
        }
    }

    fun cambiarDireccion(texto: String) {
        _ui.update { UiConfiguracion(direccion = texto) }
    }

    /** Prueba la conexión y, si responde, guarda la dirección para el resto de la app. */
    fun probar() {
        val direccion = DireccionServidor.normalizar(_ui.value.direccion)
        if (direccion == null) {
            _ui.update { it.copy(prueba = EstadoPrueba.DireccionInvalida) }
            return
        }
        _ui.update { it.copy(prueba = EstadoPrueba.Probando) }
        viewModelScope.launch {
            val resultado = conexion.probar(direccion)
            val info = resultado.getOrNull()
            if (info != null) conexion.guardarDireccion(direccion)
            _ui.update {
                it.copy(prueba = info?.let(EstadoPrueba::Conectado) ?: EstadoPrueba.SinRespuesta)
            }
        }
    }
}
