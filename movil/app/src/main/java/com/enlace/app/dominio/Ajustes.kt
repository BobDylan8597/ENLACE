package com.enlace.app.dominio

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

enum class Modo { USUARIO, UNIDAD }

/**
 * Qué hace la app con la ubicación cuando llega una alerta oficial (sección 13).
 * Se elige en una pantalla propia, no en términos y condiciones.
 */
enum class Consentimiento {
    /**
     * Envía sin preguntar la zona y también el punto exacto, una vez por alerta: brigadas y
     * coordinación ven dónde está cada persona. El punto se borra al cerrar la emergencia.
     */
    UBICACION_EXACTA,

    /** Envía la zona aproximada (celda h3) sin preguntar. */
    AUTOMATICO,

    /** Pregunta antes de enviar la zona. */
    CON_CONFIRMACION,

    /** Nunca envía la zona; igual se puede pedir ayuda a mano. */
    NO_COMPARTIR,
}

/** Situaciones que ayudan a priorizar a quien pide ayuda. */
enum class Condicion { MOVILIDAD_REDUCIDA, ADULTO_MAYOR, EMBARAZO, NINOS, MEDICACION, DISCAPACIDAD }

/**
 * Perfil guardado solo en el celular. Nombre, teléfono y condiciones salen del teléfono
 * únicamente al pedir ayuda; con la alerta solo viajan los conteos (anónimos).
 */
@Serializable
data class Perfil(
    val nombre: String = "",
    val telefono: String = "",
    val condiciones: Set<Condicion> = emptySet(),
    val personasEnHogar: Int = 1,
    /** Cuántas de las personas del hogar tienen alguna de las condiciones. */
    val personasVulnerables: Int = 1,
)

/** Conteos anónimos del hogar que acompañan a la zona: forman la "población crítica". */
data class ConteoHogar(val personas: Int, val vulnerables: Int)

fun Perfil.conteoHogar(): ConteoHogar {
    val personas = personasEnHogar.coerceAtLeast(1)
    val vulnerables = if (condiciones.isEmpty()) 0 else personasVulnerables.coerceIn(1, personas)
    return ConteoHogar(personas, vulnerables)
}

interface AjustesRepositorio {
    val modo: Flow<Modo?>
    val consentimiento: Flow<Consentimiento?>
    val perfil: Flow<Perfil>

    suspend fun guardarModo(modo: Modo)

    suspend fun guardarConsentimiento(consentimiento: Consentimiento)

    suspend fun guardarPerfil(perfil: Perfil)

    /** La persona ya vio (y cerró) el aviso de permitir el inicio automático. */
    val avisoInicioAutomaticoVisto: Flow<Boolean>

    suspend fun marcarAvisoInicioAutomatico()
}

/** Marcas que bloquean el arranque automático de las apps salvo permiso de la persona. */
private val MARCAS_CON_INICIO_RESTRINGIDO = setOf("xiaomi", "redmi", "poco", "honor", "huawei", "oppo", "realme", "oneplus", "vivo")

fun necesitaInicioAutomatico(fabricante: String): Boolean = fabricante.lowercase() in MARCAS_CON_INICIO_RESTRINGIDO

/** Pantalla con la que abre la app según lo que ya está configurado. */
enum class Destino { BIENVENIDA, CONFIGURACION, MODO, CONSENTIMIENTO, INICIO_USUARIO, ACTIVAR_UNIDAD, INICIO_UNIDAD }

fun destinoInicial(
    direccion: String?,
    modo: Modo?,
    consentimiento: Consentimiento?,
    unidadActiva: Boolean = false,
): Destino = when {
    direccion == null -> Destino.BIENVENIDA
    modo == null -> Destino.MODO
    // La unidad necesita la clave de brigada y su equipo; el consentimiento es del modo usuario
    modo == Modo.UNIDAD -> if (unidadActiva) Destino.INICIO_UNIDAD else Destino.ACTIVAR_UNIDAD
    consentimiento == null -> Destino.CONSENTIMIENTO
    else -> Destino.INICIO_USUARIO
}
