package com.enlace.app.dominio

/** Necesidades que entiende el servidor (y el formato SMS: R, M, W, F). */
enum class Necesidad(val codigo: String) {
    RESCATE("rescate"),
    MEDICO("medico"),
    AGUA("agua"),
    REFUGIO("refugio"),
}

/** Lo que la persona marca en el reporte rápido (U10). */
data class DatosPedido(
    val personas: Int = 1,
    val atrapados: Int = 0,
    val heridos: Int = 0,
    val vulnerables: Int = 0,
    val necesidades: Set<Necesidad> = emptySet(),
)

/** Datos personales que salen del celular: solo al pedir ayuda y solo si la persona los cargó. */
data class IdentidadPedido(val nombre: String?, val telefono: String?, val condiciones: String?)

/** El perfil acompaña al pedido solo si tiene algún dato; si está vacío, no se envía nada. */
fun identidadDesde(perfil: Perfil, nombreCondicion: (Condicion) -> String): IdentidadPedido? {
    val nombre = perfil.nombre.trim().ifEmpty { null }
    val telefono = perfil.telefono.trim().ifEmpty { null }
    val partes = buildList {
        if (perfil.condiciones.isNotEmpty()) {
            add(perfil.condiciones.sortedBy { it.ordinal }.joinToString(", ") { nombreCondicion(it) })
        }
        if (perfil.personasEnHogar > 1) add("${perfil.personasEnHogar} personas en el hogar")
    }
    val condiciones = partes.joinToString(". ").ifEmpty { null }
    return if (nombre == null && telefono == null && condiciones == null) null
    else IdentidadPedido(nombre, telefono, condiciones)
}

/** Vulnerables del pedido: lo que marca la persona, o al menos 1 si su perfil indica una condición. */
fun vulnerablesSugeridos(perfil: Perfil): Int = if (perfil.condiciones.isEmpty()) 0 else 1

enum class TipoBloqueo(val codigo: String) {
    DERRUMBE("derrumbe"),
    AGUA("agua"),
    ESCOMBROS("escombros"),
    CABLES("cables"),
    VIA_ROTA("via_rota"),
    OTRO("otro"),
}

/** Si pasa una persona a pie, el bloqueo corta solo vehículos (servidor: alcance "solo_a_pie"). */
fun alcanceBloqueo(pasaAPie: Boolean): String = if (pasaAPie) "solo_a_pie" else "total"

/** Resultado de enviar algo al servidor, para la pantalla de confirmación (U11). */
sealed interface ResultadoEnvio {
    data object Enviado : ResultadoEnvio

    /** Sin conexión: quedó guardado en el celular y se enviará solo (Outbox). */
    data object Guardado : ResultadoEnvio

    /** Guardado, y además se puede enviar ya mismo por SMS (la persona lo envía). */
    data class GuardadoConSms(val numero: String, val texto: String) : ResultadoEnvio
    data class Rechazado(val motivo: String) : ResultadoEnvio
    data object SinUbicacion : ResultadoEnvio
}

interface ReportesRepositorio {
    suspend fun pedirAyuda(datos: DatosPedido): ResultadoEnvio

    suspend fun reportarBloqueo(tipo: TipoBloqueo, pasaAPie: Boolean): ResultadoEnvio
}
