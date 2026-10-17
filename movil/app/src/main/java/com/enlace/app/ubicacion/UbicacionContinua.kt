package com.enlace.app.ubicacion

import android.annotation.SuppressLint
import android.content.Context
import android.os.Looper
import com.enlace.app.dominio.Punto
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val INTERVALO_MS = 3_000L

/**
 * Posición continua SOLO para la unidad mientras navega hacia su asignación (B5): un equipo de
 * respuesta sí informa dónde está. A los ciudadanos nunca se los sigue (sección 13): para ellos
 * está ProveedorUbicacion, que consulta una sola vez.
 */
@Singleton
class UbicacionContinua @Inject constructor(
    @param:ApplicationContext private val contexto: Context,
    private val permiso: ProveedorUbicacion,
) {
    private val cliente = LocationServices.getFusedLocationProviderClient(contexto)

    /** Emite mientras alguien escucha; al dejar de escuchar (salir de la pantalla) se detiene. */
    @SuppressLint("MissingPermission") // se comprueba con tienePermiso()
    fun posiciones(): Flow<Punto> {
        if (!permiso.tienePermiso()) return emptyFlow()
        return callbackFlow {
            val callback = object : LocationCallback() {
                override fun onLocationResult(resultado: LocationResult) {
                    resultado.lastLocation?.let { trySend(Punto(it.latitude, it.longitude)) }
                }
            }
            val pedido = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVALO_MS).build()
            cliente.requestLocationUpdates(pedido, callback, Looper.getMainLooper())
            awaitClose { cliente.removeLocationUpdates(callback) }
        }
    }
}
