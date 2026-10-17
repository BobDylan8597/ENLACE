package com.enlace.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.enlace.app.datos.CredencialesBrigada
import com.enlace.app.datos.EXTRA_ASIGNACION
import com.enlace.app.dominio.AjustesRepositorio
import com.enlace.app.dominio.ConexionRepositorio
import com.enlace.app.presentacion.NavegacionEnlace
import com.enlace.app.servicio.ServicioAlertas
import com.enlace.app.servicio.appConfigurada
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var conexion: ConexionRepositorio
    @Inject lateinit var ajustes: AjustesRepositorio
    @Inject lateinit var credenciales: CredencialesBrigada

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mostrarSobreBloqueo(intent)
        enableEdgeToEdge()
        setContent {
            NavegacionEnlace() // aplica el tema: oscuro durante una alerta
        }
    }

    /**
     * El servicio (alertas, Outbox y red cercana) arranca cada vez que la app pasa a primer plano
     * con la configuración hecha, sin depender de qué pantalla se abra primero (p. ej. la alerta).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        mostrarSobreBloqueo(intent)
    }

    /** El aviso de asignación (B3) se ve aunque el celular de la unidad esté bloqueado. */
    private fun mostrarSobreBloqueo(intent: Intent?) {
        if (intent?.hasExtra(EXTRA_ASIGNACION) != true) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            if (appConfigurada(conexion, ajustes, credenciales)) ServicioAlertas.iniciar(this@MainActivity)
        }
    }
}
