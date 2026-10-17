package com.enlace.app.dominio

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

enum class Modo { USUARIO, UNIDAD }

/**
 * Qué hace la app con la ubicación cuando llega una alerta oficial (sección 13).
 * Se elige en una pantalla propia, no en términos y condiciones.
 */
enum class Consentimiento {
    /** Envía la zona aproximada (celda h3) sin preguntar. */
    AUTOMATICO,

    /** Pregunta antes de enviar la zona. */
    CON_CONFIRMACION,

    /** Nunca envía la zona; igual se puede pedir ayuda a mano. */
    NO_COMPARTIR,
}

/** Situaciones que ayudan a priorizar a quien pide ayuda. */
enum class Condicion { MOVILIDAD_REDUCIDA, ADULTO_MAYOR, EMBARAZO, NINOS, MEDICACION, DISCAPACIDAD }

/** Perfil guardado solo en el celular. Sale del teléfono únicamente al pedir ayuda. */
@Serializable
data class Perfil(
    val nombre: String = "",
    val telefono: String = "",
    val condiciones: Set<Condicion> = emptySet(),
    val personasEnHogar: Int = 1,
)

interface AjustesRepositorio {
    val modo: Flow<Modo?>
    val consentimiento: Flow<Consentimiento?>
    val perfil: Flow<Perfil>

    suspend fun guardarModo(modo: Modo)

    suspend fun guardarConsentimiento(consentimiento: Consentimiento)

    suspend fun guardarPerfil(perfil: Perfil)
}

/** Pantalla con la que abre la app según lo que ya está configurado. */
enum class Destino { CONFIGURACION, MODO, CONSENTIMIENTO, INICIO_USUARIO }

fun destinoInicial(direccion: String?, modo: Modo?, consentimiento: Consentimiento?): Destino = when {
    direccion == null -> Destino.CONFIGURACION
    modo == null -> Destino.MODO
    // El modo unidad se habilita en el paso 4.7; mientras tanto se elige de nuevo
    modo == Modo.UNIDAD -> Destino.MODO
    consentimiento == null -> Destino.CONSENTIMIENTO
    else -> Destino.INICIO_USUARIO
}
