package com.enlace.app.servicio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.enlace.app.datos.CredencialesBrigada
import com.enlace.app.dominio.AjustesRepositorio
import com.enlace.app.dominio.ConexionRepositorio
import com.enlace.app.dominio.Modo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** La app ya se configuró (servidor y modo; en modo unidad, también su equipo). */
suspend fun appConfigurada(
    conexion: ConexionRepositorio,
    ajustes: AjustesRepositorio,
    credenciales: CredencialesBrigada,
): Boolean = conexion.direccion.first() != null && when (ajustes.modo.first()) {
    Modo.USUARIO -> true
    Modo.UNIDAD -> credenciales.equipo.first() != null
    null -> false
}

/**
 * Al encender el celular (o al actualizar la app) vuelve a quedar "atento a alertas" sin que la
 * persona tenga que abrir ENLACE: una alerta puede llegar en cualquier momento.
 */
@AndroidEntryPoint
class ArranqueSistema : BroadcastReceiver() {
    @Inject lateinit var conexion: ConexionRepositorio
    @Inject lateinit var ajustes: AjustesRepositorio
    @Inject lateinit var credenciales: CredencialesBrigada

    override fun onReceive(contexto: Context, intent: Intent) {
        if (intent.action !in ACCIONES) return
        val pendiente = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (appConfigurada(conexion, ajustes, credenciales)) {
                    ServicioAlertas.iniciar(contexto, desdeArranque = true)
                }
            } finally {
                pendiente.finish()
            }
        }
    }

    private companion object {
        val ACCIONES = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)
    }
}
