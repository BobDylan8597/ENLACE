package com.enlace.app.datos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.enlace.app.dominio.EquipoUnidad
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clave de brigada y equipo de este celular en modo unidad. Separada de la del administrador:
 * si se pierde el celular de una unidad, no expone el panel del centro de coordinación.
 */
@Singleton
class CredencialesBrigada @Inject constructor(
    private val preferencias: DataStore<Preferences>,
    private val json: Json,
) {
    val equipo: Flow<EquipoUnidad?> = preferencias.data.map { p ->
        p[EQUIPO]?.let { runCatching { json.decodeFromString<EquipoUnidad>(it) }.getOrNull() }
    }

    suspend fun clave(): String? = preferencias.data.first()[CLAVE]

    suspend fun guardar(clave: String, equipo: EquipoUnidad) {
        preferencias.edit {
            it[CLAVE] = clave
            it[EQUIPO] = json.encodeToString(EquipoUnidad.serializer(), equipo)
        }
    }

    suspend fun borrar() {
        preferencias.edit {
            it.remove(CLAVE)
            it.remove(EQUIPO)
        }
    }

    private companion object {
        val CLAVE = stringPreferencesKey("clave_brigada")
        val EQUIPO = stringPreferencesKey("equipo_unidad")
    }
}
