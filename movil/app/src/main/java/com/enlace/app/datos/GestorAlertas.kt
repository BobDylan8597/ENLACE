package com.enlace.app.datos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.enlace.app.datos.remoto.CheckInDto
import com.enlace.app.datos.seguridad.VerificadorEd25519
import com.enlace.app.dominio.AjustesRepositorio
import com.enlace.app.dominio.AlertaFirmada
import com.enlace.app.dominio.AlertaVigente
import com.enlace.app.dominio.ConexionRepositorio
import com.enlace.app.dominio.EnvioZona
import com.enlace.app.dominio.EstadoEnvio
import com.enlace.app.dominio.TipoMensaje
import com.enlace.app.dominio.idCheckin
import com.enlace.app.dominio.LectorAlertas
import com.enlace.app.dominio.LocalizadorCelda
import com.enlace.app.dominio.envioInicial
import com.enlace.app.ubicacion.ProveedorUbicacion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Estado del check-in que se informa al servidor (servidor: EstadoCheckIn). */
private const val ESTADO_SIN_RESPUESTA = "sin_respuesta"
private const val ESTADO_BIEN = "bien"

/**
 * Recibe las alertas, descarta las que no tienen firma válida, guarda la vigente y, según el
 * consentimiento, envía la zona del celular (solo la celda h3, nunca coordenadas).
 */
@Singleton
class GestorAlertas @Inject constructor(
    private val preferencias: DataStore<Preferences>,
    private val conexion: ConexionRepositorio,
    private val ajustes: AjustesRepositorio,
    private val outbox: Outbox,
    private val ubicacion: ProveedorUbicacion,
    private val notificador: NotificadorAlertas,
    private val json: Json,
) {
    private val cerrojo = Mutex()

    val vigente: Flow<AlertaVigente?> = preferencias.data.map { p ->
        p[CLAVE_VIGENTE]?.let { runCatching { json.decodeFromString<AlertaVigente>(it) }.getOrNull() }
    }

    /** Devuelve true si era una alerta nueva y válida. */
    suspend fun recibir(firmada: AlertaFirmada): Boolean = cerrojo.withLock {
        val clave = conexion.clavePublica() ?: return false
        val alerta = LectorAlertas(VerificadorEd25519(clave), json).leer(firmada) ?: return false
        if (vigente.first()?.alerta?.idEvento == alerta.idEvento) return false

        val pendiente = envioInicial(ajustes.consentimiento.first())
        guardar(AlertaVigente(alerta, firmada, pendiente ?: EnvioZona.ENVIANDO))
        notificador.mostrarAlerta(alerta)
        if (pendiente == null) enviarZonaSinCerrojo(ESTADO_SIN_RESPUESTA)
        true
    }

    /** La persona aceptó enviar su zona (consentimiento "con confirmación"). */
    suspend fun enviarZona(): EnvioZona = cerrojo.withLock { enviarZonaSinCerrojo(ESTADO_SIN_RESPUESTA) }

    /** La persona prefirió no enviar su zona en esta alerta. */
    suspend fun noEnviarZona(): Unit = cerrojo.withLock {
        vigente.first()?.let { guardar(it.copy(envioZona = EnvioZona.NO_COMPARTIDA)) }
    }

    /** "Estoy bien": se informa a la zona solo si la persona permite compartirla. */
    suspend fun estoyBien(): Unit = cerrojo.withLock {
        val actual = vigente.first() ?: return
        guardar(actual.copy(estoyBien = true))
        if (actual.envioZona != EnvioZona.NO_COMPARTIDA) enviarZonaSinCerrojo(ESTADO_BIEN)
    }

    suspend fun cerrar(idEvento: Int): Unit = cerrojo.withLock {
        if (vigente.first()?.alerta?.idEvento == idEvento) {
            preferencias.edit { it.remove(CLAVE_VIGENTE) }
            notificador.quitarAlerta()
        }
    }

    private suspend fun enviarZonaSinCerrojo(estado: String): EnvioZona {
        val actual = vigente.first() ?: return EnvioZona.SIN_UBICACION
        guardar(actual.copy(envioZona = EnvioZona.ENVIANDO))
        val resultado = enviar(actual, estado)
        guardar(actual.copy(envioZona = resultado))
        return resultado
    }

    private suspend fun enviar(actual: AlertaVigente, estado: String): EnvioZona {
        val (lat, lon) = ubicacion.unaVez() ?: return EnvioZona.SIN_UBICACION
        val celda = LocalizadorCelda(conexion.celdas()).celdaDe(lat, lon) ?: return EnvioZona.FUERA_DE_ZONA
        val checkin = CheckInDto(conexion.idAnonimo(), actual.alerta.idEvento, celda, estado)
        val id = idCheckin(actual.alerta.idEvento)
        outbox.guardar(id, TipoMensaje.CHECKIN, json.encodeToString(CheckInDto.serializer(), checkin))
        outbox.enviarPendientes()
        return if (outbox.observar(id).first()?.estado == EstadoEnvio.ENVIADO) EnvioZona.ENVIADA
        else EnvioZona.SIN_CONEXION
    }

    private suspend fun guardar(vigente: AlertaVigente) {
        preferencias.edit { it[CLAVE_VIGENTE] = json.encodeToString(AlertaVigente.serializer(), vigente) }
    }

    private companion object {
        val CLAVE_VIGENTE = stringPreferencesKey("alerta_vigente")
    }
}
