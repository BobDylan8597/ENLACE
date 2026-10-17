package com.enlace.app.datos

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.enlace.app.datos.remoto.FabricaApi
import com.enlace.app.dominio.ConexionRepositorio
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Nombre del mapa de la ciudad en el celular (también lo borra ConexionRepositorioImpl). */
const val ARCHIVO_MAPA = "mapa.pmtiles"
private const val IDIOMA_POR_DEFECTO = "es"
private val IDIOMAS_DEL_ESTILO = setOf("es", "pt")

/**
 * Mapa de la ciudad guardado en el celular: se descarga del servidor ENLACE (no de internet) apenas
 * hay conexión, para poder usarlo después sin ninguna red. Sirve para cualquier ciudad: el estilo
 * va en la app (assets/mapa) y el servidor entrega el mapa de la ciudad que atiende.
 */
@Singleton
class MapaLocal @Inject constructor(
    @param:ApplicationContext private val contexto: Context,
    private val preferencias: DataStore<Preferences>,
    private val conexion: ConexionRepositorio,
    private val fabricaApi: FabricaApi,
) {
    private val cerrojo = Mutex()
    private val archivo get() = File(contexto.filesDir, ARCHIVO_MAPA)

    fun disponible(): Boolean = archivo.exists()

    /** Descarga el mapa si falta. Devuelve true si quedó en el celular. */
    suspend fun asegurar(): Boolean = cerrojo.withLock {
        if (archivo.exists()) return true
        val direccion = conexion.direccion.first() ?: return false
        runCatching {
            val api = fabricaApi.para(direccion)
            val idioma = api.ciudad().idioma
            withContext(Dispatchers.IO) {
                val temporal = File(contexto.filesDir, "$ARCHIVO_MAPA.descarga")
                api.mapa().byteStream().use { entrada -> temporal.outputStream().use { entrada.copyTo(it) } }
                check(temporal.renameTo(archivo))
            }
            preferencias.edit { it[IDIOMA] = idioma ?: IDIOMA_POR_DEFECTO }
        }.isSuccess
    }

    /** Estilo de MapLibre apuntando al mapa local, o null si todavía no se descargó. */
    suspend fun estilo(): String? {
        if (!archivo.exists()) return null
        val idioma = preferencias.data.first()[IDIOMA]?.takeIf { it in IDIOMAS_DEL_ESTILO } ?: IDIOMA_POR_DEFECTO
        return withContext(Dispatchers.IO) {
            contexto.assets.open("mapa/estilo_$idioma.json").bufferedReader().use { it.readText() }
                .replace("{MAPA}", "file://${archivo.absolutePath}")
        }
    }

    private companion object {
        val IDIOMA = stringPreferencesKey("idioma_mapa")
    }
}
