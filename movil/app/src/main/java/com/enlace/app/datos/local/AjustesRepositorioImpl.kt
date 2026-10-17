package com.enlace.app.datos.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.enlace.app.dominio.AjustesRepositorio
import com.enlace.app.dominio.Consentimiento
import com.enlace.app.dominio.Modo
import com.enlace.app.dominio.Perfil
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Ajustes y perfil: viven solo en este celular (no se sincronizan con nadie). */
@Singleton
class AjustesRepositorioImpl @Inject constructor(
    private val preferencias: DataStore<Preferences>,
    private val json: Json,
) : AjustesRepositorio {

    override val modo: Flow<Modo?> =
        preferencias.data.map { p -> p[MODO]?.let { runCatching { Modo.valueOf(it) }.getOrNull() } }

    override val consentimiento: Flow<Consentimiento?> = preferencias.data.map { p ->
        p[CONSENTIMIENTO]?.let { runCatching { Consentimiento.valueOf(it) }.getOrNull() }
    }

    override val perfil: Flow<Perfil> = preferencias.data.map { p ->
        p[PERFIL]?.let { runCatching { json.decodeFromString<Perfil>(it) }.getOrNull() } ?: Perfil()
    }

    override suspend fun guardarModo(modo: Modo) {
        preferencias.edit { it[MODO] = modo.name }
    }

    override suspend fun guardarConsentimiento(consentimiento: Consentimiento) {
        preferencias.edit { it[CONSENTIMIENTO] = consentimiento.name }
    }

    override suspend fun guardarPerfil(perfil: Perfil) {
        preferencias.edit { it[PERFIL] = json.encodeToString(Perfil.serializer(), perfil) }
    }

    private companion object {
        val MODO = stringPreferencesKey("modo")
        val CONSENTIMIENTO = stringPreferencesKey("consentimiento")
        val PERFIL = stringPreferencesKey("perfil")
    }
}
