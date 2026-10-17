package com.enlace.app.datos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.enlace.app.datos.remoto.FabricaApi
import com.enlace.app.dominio.ConexionRepositorio
import com.enlace.app.dominio.InfoServidor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConexionRepositorioImpl @Inject constructor(
    private val preferencias: DataStore<Preferences>,
    private val fabricaApi: FabricaApi,
) : ConexionRepositorio {

    override val direccion: Flow<String?> = preferencias.data.map { it[CLAVE_DIRECCION] }

    override suspend fun guardarDireccion(direccion: String) {
        preferencias.edit { it[CLAVE_DIRECCION] = direccion }
    }

    override suspend fun probar(direccion: String): Result<InfoServidor> = runCatching {
        val salud = fabricaApi.para(direccion).salud()
        InfoServidor(ciudad = salud.ciudad, pais = salud.pais)
    }

    private companion object {
        val CLAVE_DIRECCION = stringPreferencesKey("direccion_servidor")
    }
}
