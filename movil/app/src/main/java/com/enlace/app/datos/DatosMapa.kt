package com.enlace.app.datos

import com.enlace.app.datos.remoto.FabricaApi
import com.enlace.app.dominio.CeldaH3
import com.enlace.app.dominio.ConexionRepositorio
import com.enlace.app.dominio.Punto
import com.enlace.app.dominio.centroDe
import com.enlace.app.presentacion.mapa.BloqueoMapa
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Lo que necesitan los mapas de la app, con lo guardado en el celular primero. */
@Singleton
class DatosMapa @Inject constructor(
    private val mapa: MapaLocal,
    private val conexion: ConexionRepositorio,
    private val fabricaApi: FabricaApi,
) {
    /** Estilo con el mapa local; si todavía no se descargó, se intenta ahora. */
    suspend fun estilo(): String? = mapa.estilo() ?: if (mapa.asegurar()) mapa.estilo() else null

    suspend fun celdas(): Map<String, CeldaH3> = conexion.celdas().associateBy { it.id }

    /** Centro de la zona preparada: el promedio de los centros de sus celdas. */
    suspend fun centroCiudad(): Punto? {
        val centros = conexion.celdas().mapNotNull(::centroDe)
        if (centros.isEmpty()) return null
        return Punto(centros.sumOf { it.lat } / centros.size, centros.sumOf { it.lon } / centros.size)
    }

    /** Bloqueos activos; sin conexión, ninguno (el mapa se ve igual). */
    suspend fun bloqueos(): List<BloqueoMapa> {
        val direccion = conexion.direccion.first() ?: return emptyList()
        return runCatching { fabricaApi.para(direccion).bloqueos() }
            .getOrDefault(emptyList())
            .map { BloqueoMapa(Punto(it.lat, it.lon), it.estado) }
    }
}
