package com.enlace.app.datos

import android.content.Context
import com.enlace.app.TEXTO_CONDICION
import com.enlace.app.datos.remoto.ApiEnlace
import com.enlace.app.datos.remoto.BloqueoDto
import com.enlace.app.datos.remoto.FabricaApi
import com.enlace.app.datos.remoto.IdentidadDto
import com.enlace.app.datos.remoto.ReporteDto
import com.enlace.app.dominio.AjustesRepositorio
import com.enlace.app.dominio.ConexionRepositorio
import com.enlace.app.dominio.DatosPedido
import com.enlace.app.dominio.ReportesRepositorio
import com.enlace.app.dominio.ResultadoEnvio
import com.enlace.app.dominio.TipoBloqueo
import com.enlace.app.dominio.alcanceBloqueo
import com.enlace.app.dominio.identidadDesde
import com.enlace.app.ubicacion.ProveedorUbicacion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val ORIGEN_APP = "app"

/**
 * Envía pedidos de ayuda y bloqueos al servidor. En el paso 4.4 pasa a guardar primero en el
 * celular (Outbox) y enviar después; las pantallas no cambian.
 */
@Singleton
class ReportesRepositorioImpl @Inject constructor(
    @param:ApplicationContext private val contexto: Context,
    private val conexion: ConexionRepositorio,
    private val ajustes: AjustesRepositorio,
    private val alertas: GestorAlertas,
    private val ubicacion: ProveedorUbicacion,
    private val fabricaApi: FabricaApi,
) : ReportesRepositorio {

    override suspend fun pedirAyuda(datos: DatosPedido): ResultadoEnvio {
        // Pedir ayuda es el único caso en que salen el punto exacto y el perfil (sección 13)
        val (lat, lon) = ubicacion.unaVez() ?: return ResultadoEnvio.SinUbicacion
        val perfil = ajustes.perfil.first()
        val identidad = identidadDesde(perfil) { contexto.getString(TEXTO_CONDICION.getValue(it)) }
        val reporte = ReporteDto(
            idMensaje = UUID.randomUUID().toString(),
            idAnonimo = conexion.idAnonimo(),
            idEvento = alertas.vigente.first()?.alerta?.idEvento,
            origen = ORIGEN_APP,
            tipo = "ayuda",
            lat = lat,
            lon = lon,
            personas = datos.personas,
            atrapados = datos.atrapados,
            heridos = datos.heridos,
            vulnerables = datos.vulnerables,
            necesidades = datos.necesidades.map { it.codigo },
            hora = OffsetDateTime.now().toString(),
            identidad = identidad?.let { IdentidadDto(it.nombre, it.telefono, it.condiciones) },
        )
        return enviar { api ->
            val resultado = api.reportes(listOf(reporte))
            when {
                reporte.idMensaje in resultado.aceptados -> ResultadoEnvio.Enviado
                reporte.idMensaje in resultado.repetidos -> ResultadoEnvio.YaRecibido
                else -> ResultadoEnvio.Rechazado(resultado.rechazados.joinToString { it.error })
            }
        }
    }

    override suspend fun reportarBloqueo(tipo: TipoBloqueo, pasaAPie: Boolean): ResultadoEnvio {
        val (lat, lon) = ubicacion.unaVez() ?: return ResultadoEnvio.SinUbicacion
        val bloqueo = BloqueoDto(
            accion = "reportar",
            lat = lat,
            lon = lon,
            tipo = tipo.codigo,
            alcance = alcanceBloqueo(pasaAPie),
            reportante = conexion.idAnonimo(),
            origen = ORIGEN_APP,
        )
        return enviar { api ->
            api.bloqueo(bloqueo)
            ResultadoEnvio.Enviado
        }
    }

    private suspend fun enviar(
        accion: suspend (ApiEnlace) -> ResultadoEnvio,
    ): ResultadoEnvio {
        val direccion = conexion.direccion.first() ?: return ResultadoEnvio.SinConexion
        return runCatching { accion(fabricaApi.para(direccion)) }.getOrElse { ResultadoEnvio.SinConexion }
    }
}
