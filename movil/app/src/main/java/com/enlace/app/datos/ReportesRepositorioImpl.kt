package com.enlace.app.datos

import android.content.Context
import com.enlace.app.R
import com.enlace.app.TEXTO_CONDICION
import com.enlace.app.TEXTO_NECESIDAD
import com.enlace.app.datos.local.PedidoPropio
import com.enlace.app.datos.nearby.RedCercana
import com.enlace.app.datos.remoto.BloqueoDto
import com.enlace.app.datos.remoto.IdentidadDto
import com.enlace.app.datos.remoto.ReporteDto
import com.enlace.app.dominio.AjustesRepositorio
import com.enlace.app.dominio.CadenaCanales
import com.enlace.app.dominio.CanalEnvio
import com.enlace.app.dominio.CanalSms
import com.enlace.app.dominio.PedidoParaEnviar
import com.enlace.app.dominio.ResultadoCanal
import com.enlace.app.dominio.ConexionRepositorio
import com.enlace.app.dominio.DatosPedido
import com.enlace.app.dominio.EstadoEnvio
import com.enlace.app.dominio.ReportesRepositorio
import com.enlace.app.dominio.ResultadoEnvio
import com.enlace.app.dominio.TipoBloqueo
import com.enlace.app.dominio.TipoMensaje
import com.enlace.app.dominio.TipoMensajeRed
import com.enlace.app.dominio.alcanceBloqueo
import com.enlace.app.dominio.identidadDesde
import com.enlace.app.ubicacion.ProveedorUbicacion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val ORIGEN_APP = "app"

/** Pedidos de ayuda y bloqueos: se guardan primero en el Outbox y se envían apenas se pueda. */
@Singleton
class ReportesRepositorioImpl @Inject constructor(
    @param:ApplicationContext private val contexto: Context,
    private val conexion: ConexionRepositorio,
    private val ajustes: AjustesRepositorio,
    private val alertas: GestorAlertas,
    private val ubicacion: ProveedorUbicacion,
    private val outbox: Outbox,
    private val seguimiento: Seguimiento,
    private val red: RedCercana,
    private val json: Json,
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
        seguimiento.guardar(PedidoPropio(reporte.idMensaje, System.currentTimeMillis(), resumen(datos)))
        outbox.guardar(reporte.idMensaje, TipoMensaje.REPORTE, json.encodeToString(ReporteDto.serializer(), reporte))

        // Servidor → red cercana → ofrecer SMS
        val cuerpo = json.encodeToString(ReporteDto.serializer(), reporte)
        val cadena = CadenaCanales(
            listOf(canalServidor(), canalRedCercana(TipoMensajeRed.REPORTE, cuerpo), CanalSms { conexion.numeroSms() }),
        )
        val salida = cadena.enviar(PedidoParaEnviar(reporte.idMensaje, lat, lon, datos))
        return when (salida) {
            ResultadoCanal.Entregado -> intentarAhora(reporte.idMensaje)
            is ResultadoCanal.EnRedCercana -> ResultadoEnvio.EnRedCercana(salida.celulares)
            is ResultadoCanal.OfrecerSms -> ResultadoEnvio.GuardadoConSms(salida.numero, salida.texto)
            ResultadoCanal.NoDisponible -> ResultadoEnvio.Guardado
        }
    }

    /** Canal red cercana: si hay celulares conectados, el mensaje empieza a propagarse por gossip. */
    private fun canalRedCercana(tipo: TipoMensajeRed, cuerpo: String) = CanalEnvio { pedido ->
        val celulares = red.celulares.value
        if (celulares == 0) {
            ResultadoCanal.NoDisponible
        } else {
            red.publicar(tipo, pedido.idMensaje, cuerpo)
            ResultadoCanal.EnRedCercana(celulares)
        }
    }

    /** Canal servidor: vacía el Outbox. Si el servidor lo procesó (aceptado o rechazado), terminó. */
    private fun canalServidor() = CanalEnvio { pedido ->
        outbox.enviarPendientes()
        when (outbox.observar(pedido.idMensaje).first()?.estado) {
            EstadoEnvio.ENVIADO, EstadoEnvio.RECHAZADO -> ResultadoCanal.Entregado
            EstadoEnvio.PENDIENTE, null -> ResultadoCanal.NoDisponible
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
        val id = UUID.randomUUID().toString()
        val cuerpo = json.encodeToString(BloqueoDto.serializer(), bloqueo)
        outbox.guardar(id, TipoMensaje.BLOQUEO, cuerpo)
        val resultado = intentarAhora(id)
        if (resultado == ResultadoEnvio.Guardado && red.celulares.value > 0) {
            red.publicar(TipoMensajeRed.BLOQUEO, id, cuerpo)
            return ResultadoEnvio.EnRedCercana(red.celulares.value)
        }
        return resultado
    }

    /** Intenta enviar ya; si no hay conexión, queda guardado y se envía después. */
    private suspend fun intentarAhora(id: String): ResultadoEnvio {
        outbox.enviarPendientes()
        val mensaje = outbox.observar(id).first()
        return when (mensaje?.estado) {
            EstadoEnvio.ENVIADO -> ResultadoEnvio.Enviado
            EstadoEnvio.RECHAZADO -> ResultadoEnvio.Rechazado(mensaje.error.orEmpty())
            EstadoEnvio.PENDIENTE, null -> ResultadoEnvio.Guardado
        }
    }

    /** "Atrapadas: 1 · Heridas: 2 · Rescate, Agua" */
    private fun resumen(datos: DatosPedido): String = buildList {
        if (datos.atrapados > 0) add("${contexto.getString(R.string.ayuda_atrapados)}: ${datos.atrapados}")
        if (datos.heridos > 0) add("${contexto.getString(R.string.ayuda_heridos)}: ${datos.heridos}")
        if (datos.necesidades.isNotEmpty()) {
            add(datos.necesidades.joinToString(", ") { contexto.getString(TEXTO_NECESIDAD.getValue(it)) })
        }
        if (isEmpty()) add("${contexto.getString(R.string.ayuda_personas)}: ${datos.personas}")
    }.joinToString(" · ")
}
