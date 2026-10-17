package com.enlace.app.ubicacion

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val ESPERA_LECTURA_MS = 15_000L

/**
 * Nunca rastreo continuo (sección 13): una sola consulta, solo al recibir una alerta válida o al
 * pedir ayuda. Primero la última ubicación conocida; si no hay, una sola lectura actual.
 */
@Singleton
class ProveedorUbicacion @Inject constructor(@param:ApplicationContext private val contexto: Context) {
    private val cliente = LocationServices.getFusedLocationProviderClient(contexto)

    fun tienePermiso(): Boolean = ContextCompat.checkSelfPermission(
        contexto,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    /** (lat, lon) o null si no hay permiso o el celular no sabe dónde está. */
    @SuppressLint("MissingPermission") // se comprueba con tienePermiso()
    suspend fun unaVez(): Pair<Double, Double>? {
        if (!tienePermiso()) return null
        val ultima = cliente.lastLocation.esperar()
        val ubicacion = ultima ?: CancellationTokenSource().let { token ->
            // Con el GPS apagado la lectura no termina nunca: se abandona a los pocos segundos
            withTimeoutOrNull(ESPERA_LECTURA_MS) {
                cliente.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token).esperar()
            }.also { if (it == null) token.cancel() }
        }
        return ubicacion?.let { it.latitude to it.longitude }
    }
}

private suspend fun <T> Task<T>.esperar(): T? = suspendCancellableCoroutine { continuacion ->
    addOnSuccessListener { continuacion.resume(it) }
    addOnFailureListener { continuacion.resume(null) }
    addOnCanceledListener { continuacion.resume(null) }
}
