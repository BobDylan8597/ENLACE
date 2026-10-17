package com.enlace.app.datos

import com.enlace.app.datos.remoto.BloqueoDecisionDto
import com.enlace.app.datos.remoto.FabricaApi
import com.enlace.app.dominio.ConexionRepositorio
import com.enlace.app.dominio.Punto
import com.enlace.app.dominio.distanciaM
import com.enlace.app.ubicacion.ProveedorUbicacion
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Bloqueo activo con la distancia a quien lo mira (null si no se sabe dónde está). */
data class BloqueoCercano(
    val id: Int,
    val tipo: String,
    val estado: String,
    val soloAPie: Boolean,
    val confirmaciones: Int,
    val distanciaM: Double?,
)

enum class DecisionBloqueo(val accion: String) { CONFIRMAR("confirmar"), DESCARTAR("descartar") }

/**
 * Bloqueos activos ordenados por cercanía, para confirmarlos (U15, cualquier persona) o
 * confirmarlos y descartarlos (B11, brigada con su clave: su confirmación vale de inmediato).
 */
@Singleton
class BloqueosCercanos @Inject constructor(
    private val conexion: ConexionRepositorio,
    private val fabricaApi: FabricaApi,
    private val credenciales: CredencialesBrigada,
    private val ubicacion: ProveedorUbicacion,
) {
    suspend fun listar(): Result<List<BloqueoCercano>> = runCatching {
        val direccion = checkNotNull(conexion.direccion.first())
        // La posición solo se usa aquí, para ordenar: no se envía
        val aqui = ubicacion.unaVez()?.let { (lat, lon) -> Punto(lat, lon) }
        fabricaApi.para(direccion).bloqueos()
            .filter { it.estado != "descartado" }
            .map {
                BloqueoCercano(
                    id = it.id,
                    tipo = it.tipo,
                    estado = it.estado,
                    soloAPie = it.alcance == "solo_a_pie",
                    confirmaciones = it.confirmaciones,
                    distanciaM = aqui?.let { p -> distanciaM(p, Punto(it.lat, it.lon)) },
                )
            }
            .sortedBy { it.distanciaM ?: Double.MAX_VALUE }
    }

    /** Devuelve true si el servidor lo registró. Como brigada, va con la clave y su equipo. */
    suspend fun decidir(id: Int, decision: DecisionBloqueo, comoBrigada: Boolean): Boolean {
        val direccion = conexion.direccion.first() ?: return false
        val clave = if (comoBrigada) credenciales.clave() else null
        val equipo = if (comoBrigada) credenciales.equipo.first() else null
        val dto = BloqueoDecisionDto(
            accion = decision.accion,
            idBloqueo = id,
            reportante = equipo?.let { "equipo-${it.id}" } ?: conexion.idAnonimo(),
            origen = if (comoBrigada) "brigada" else "app",
        )
        return runCatching { fabricaApi.para(direccion).decidirBloqueo(dto, clave) }.isSuccess
    }
}
