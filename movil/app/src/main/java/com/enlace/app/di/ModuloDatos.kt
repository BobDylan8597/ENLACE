package com.enlace.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.enlace.app.datos.ConexionRepositorioImpl
import com.enlace.app.datos.local.AjustesRepositorioImpl
import com.enlace.app.datos.remoto.MonitorConexionImpl
import com.enlace.app.dominio.AjustesRepositorio
import com.enlace.app.dominio.ConexionRepositorio
import com.enlace.app.dominio.MonitorConexion
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** Tiempo máximo para conectar con el servidor de la red local antes de probar otro canal. */
private const val ESPERA_CONEXION_SEGUNDOS = 5L
private const val ESPERA_LECTURA_SEGUNDOS = 15L

private val Context.preferencias: DataStore<Preferences> by preferencesDataStore(name = "enlace")

@Module
@InstallIn(SingletonComponent::class)
object ModuloDatos {
    @Provides
    @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun http(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(ESPERA_CONEXION_SEGUNDOS, TimeUnit.SECONDS)
        .readTimeout(ESPERA_LECTURA_SEGUNDOS, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun preferencias(@ApplicationContext contexto: Context): DataStore<Preferences> = contexto.preferencias
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ModuloRepositorios {
    @Binds
    abstract fun conexion(implementacion: ConexionRepositorioImpl): ConexionRepositorio

    @Binds
    abstract fun ajustes(implementacion: AjustesRepositorioImpl): AjustesRepositorio

    @Binds
    abstract fun monitor(implementacion: MonitorConexionImpl): MonitorConexion
}
