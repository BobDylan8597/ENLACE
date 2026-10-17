package com.enlace.app.datos

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.enlace.app.datos.remoto.FabricaApi
import com.enlace.app.datos.remoto.GeometriaDto
import com.enlace.app.dominio.CeldaH3
import com.enlace.app.dominio.ConexionRepositorio
import com.enlace.app.dominio.InfoServidor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val ARCHIVO_GEOMETRIA = "geometria_celdas.json"

@Singleton
class ConexionRepositorioImpl @Inject constructor(
    @param:ApplicationContext private val contexto: Context,
    private val preferencias: DataStore<Preferences>,
    private val fabricaApi: FabricaApi,
    private val json: Json,
) : ConexionRepositorio {

    override val direccion: Flow<String?> = preferencias.data.map { it[CLAVE_DIRECCION] }

    private var celdasEnMemoria: List<CeldaH3>? = null

    override suspend fun idAnonimo(): String {
        preferencias.data.first()[CLAVE_ID_ANONIMO]?.let { return it }
        val nuevo = UUID.randomUUID().toString()
        preferencias.edit { it[CLAVE_ID_ANONIMO] = nuevo }
        return nuevo
    }

    override suspend fun guardarDireccion(direccion: String) {
        preferencias.edit { it[CLAVE_DIRECCION] = direccion }
    }

    override suspend fun probar(direccion: String): Result<InfoServidor> = runCatching {
        val salud = fabricaApi.para(direccion).salud()
        InfoServidor(ciudad = salud.ciudad, pais = salud.pais)
    }

    override suspend fun prepararDatosLocales(direccion: String): Result<Unit> = runCatching {
        val api = fabricaApi.para(direccion)
        val clave = api.clavePublica().clavePublica
        val geometria = api.geometria()
        val numeroSms = api.ciudad().numeroSms
        withContext(Dispatchers.IO) {
            archivoGeometria().writeText(json.encodeToString(GeometriaDto.serializer(), geometria))
        }
        preferencias.edit {
            it[CLAVE_PUBLICA] = clave
            if (numeroSms != null) it[CLAVE_NUMERO_SMS] = numeroSms else it.remove(CLAVE_NUMERO_SMS)
        }
        celdasEnMemoria = null
    }

    override suspend fun clavePublica(): String? = preferencias.data.first()[CLAVE_PUBLICA]

    override suspend fun numeroSms(): String? = preferencias.data.first()[CLAVE_NUMERO_SMS]

    override suspend fun celdas(): List<CeldaH3> {
        celdasEnMemoria?.let { return it }
        val archivo = archivoGeometria()
        if (!archivo.exists()) return emptyList()
        val geometria = withContext(Dispatchers.IO) {
            json.decodeFromString(GeometriaDto.serializer(), archivo.readText())
        }
        return geometria.celdas
            .map { celda -> CeldaH3(celda.idH3, celda.contorno.map { (lat, lon) -> lat to lon }) }
            .also { celdasEnMemoria = it }
    }

    private fun archivoGeometria() = File(contexto.filesDir, ARCHIVO_GEOMETRIA)

    private companion object {
        val CLAVE_DIRECCION = stringPreferencesKey("direccion_servidor")
        val CLAVE_ID_ANONIMO = stringPreferencesKey("id_anonimo")
        val CLAVE_PUBLICA = stringPreferencesKey("clave_publica_alertas")
        val CLAVE_NUMERO_SMS = stringPreferencesKey("numero_sms")
    }
}
