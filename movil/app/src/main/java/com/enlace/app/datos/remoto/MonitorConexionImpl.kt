package com.enlace.app.datos.remoto

import com.enlace.app.datos.nearby.RedCercana
import com.enlace.app.dominio.ConexionRepositorio
import com.enlace.app.dominio.EstadoConexion
import com.enlace.app.dominio.MonitorConexion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** Cada cuánto se comprueba si el servidor responde, mientras alguien mira el indicador. */
private const val INTERVALO_MS = 10_000L

@Singleton
class MonitorConexionImpl @Inject constructor(
    private val conexion: ConexionRepositorio,
    private val red: RedCercana,
) : MonitorConexion {

    @OptIn(ExperimentalCoroutinesApi::class)
    override val estado: Flow<EstadoConexion> = conexion.direccion
        .flatMapLatest { direccion ->
            flow {
                while (true) {
                    emit(direccion != null && conexion.probar(direccion).isSuccess)
                    delay(INTERVALO_MS)
                }
            }
        }
        .combine(red.celulares) { servidor, celulares ->
            when {
                servidor -> EstadoConexion.EnLinea
                celulares > 0 -> EstadoConexion.RedCercana(celulares)
                else -> EstadoConexion.SinConexion
            }
        }
        .distinctUntilChanged()
}
