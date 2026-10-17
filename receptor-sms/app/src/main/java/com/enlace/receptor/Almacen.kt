package com.enlace.receptor

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.datos: DataStore<Preferences> by preferencesDataStore(name = "receptor")

/** Cuántos SMS ya procesados se conservan para mostrar en pantalla (los pendientes, todos). */
private const val HISTORIAL = 30

/**
 * Lo que vive en el celular receptor: la dirección del servidor, la clave de brigada y la cola de
 * SMS. Primero se guarda y después se envía (Outbox): si el servidor no responde, nada se pierde.
 */
class Almacen(contexto: Context) {
    private val datos = contexto.applicationContext.datos
    private val json = Json { ignoreUnknownKeys = true }
    private val cerrojo = Mutex()

    val direccion: Flow<String?> = datos.data.map { it[DIRECCION] }
    val clave: Flow<String?> = datos.data.map { it[CLAVE] }
    val cola: Flow<List<SmsRecibido>> = datos.data.map { leer(it) }

    suspend fun guardarConexion(direccion: String, clave: String) {
        datos.edit {
            it[DIRECCION] = direccion
            it[CLAVE] = clave
        }
    }

    suspend fun agregar(remitente: String, texto: String, hora: String) = cambiar { cola ->
        listOf(SmsRecibido(UUID.randomUUID().toString(), remitente, texto, hora)) + cola
    }

    suspend fun pendientes(): List<SmsRecibido> = cola.first().filter { it.estado == EstadoSms.PENDIENTE }

    suspend fun actualizar(id: String, estado: EstadoSms, error: String?) = cambiar { cola ->
        cola.map { if (it.id == id) it.copy(estado = estado, error = error, intentos = it.intentos + 1) else it }
    }

    private suspend fun cambiar(transformar: (List<SmsRecibido>) -> List<SmsRecibido>) = cerrojo.withLock {
        datos.edit { p ->
            val nueva = transformar(leer(p))
            // Los pendientes nunca se descartan; de los procesados queda solo lo reciente
            val (pendientes, procesados) = nueva.partition { it.estado == EstadoSms.PENDIENTE }
            val conservados = nueva.filter { it in pendientes || it in procesados.take(HISTORIAL) }
            p[COLA] = json.encodeToString(conservados)
        }
    }

    private fun leer(p: Preferences): List<SmsRecibido> =
        p[COLA]?.let { runCatching { json.decodeFromString<List<SmsRecibido>>(it) }.getOrNull() }.orEmpty()

    private companion object {
        val DIRECCION = stringPreferencesKey("direccion_servidor")
        val CLAVE = stringPreferencesKey("clave_brigada")
        val COLA = stringPreferencesKey("cola_sms")
    }
}
