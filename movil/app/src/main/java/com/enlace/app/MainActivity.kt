package com.enlace.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.enlace.app.dominio.AjustesRepositorio
import com.enlace.app.dominio.ConexionRepositorio
import com.enlace.app.dominio.Modo
import com.enlace.app.presentacion.NavegacionEnlace
import com.enlace.app.servicio.ServicioAlertas
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var conexion: ConexionRepositorio
    @Inject lateinit var ajustes: AjustesRepositorio

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NavegacionEnlace() // aplica el tema: oscuro durante una alerta
        }
    }

    /**
     * El servicio (alertas, Outbox y red cercana) arranca cada vez que la app pasa a primer plano
     * con la configuración hecha, sin depender de qué pantalla se abra primero (p. ej. la alerta).
     */
    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            val configurada = conexion.direccion.first() != null && ajustes.modo.first() == Modo.USUARIO
            if (configurada) ServicioAlertas.iniciar(this@MainActivity)
        }
    }
}
