package com.enlace.app.datos

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.enlace.app.datos.remoto.AvisoAsignacionDto
import com.enlace.app.datos.remoto.FabricaApi
import com.enlace.app.datos.remoto.PuntoDto
import com.enlace.app.datos.remoto.ReporteDto
import com.enlace.app.dominio.AsignacionUnidad
import com.enlace.app.dominio.Atender
import com.enlace.app.dominio.ConexionRepositorio
import com.enlace.app.dominio.Contacto
import com.enlace.app.dominio.DatosEvaluacion
import com.enlace.app.dominio.EquipoUnidad
import com.enlace.app.dominio.EstadoEnvio
import com.enlace.app.dominio.LocalizadorCelda
import com.enlace.app.dominio.PanoramaUnidad
import com.enlace.app.dominio.PedidoPendiente
import com.enlace.app.dominio.PersonaUbicada
import com.enlace.app.dominio.Punto
import com.enlace.app.dominio.ResultadoAtendida
import com.enlace.app.dominio.ResultadoClave
import com.enlace.app.dominio.ResultadoEnvio
import com.enlace.app.dominio.TipoMensaje
import com.enlace.app.dominio.UnidadRepositorio
import com.enlace.app.dominio.ZonaCritica
import com.enlace.app.dominio.centroDe
import com.enlace.app.dominio.enCurso
import com.enlace.app.ubicacion.ProveedorUbicacion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val ORIGEN_BRIGADA = "brigada"
private const val TIPO_EVALUACION = "evaluacion"
private const val NO_AUTORIZADO = 401
private const val CONFLICTO = 409

@Singleton
class UnidadRepositorioImpl @Inject constructor(
    private val preferencias: DataStore<Preferences>,
    private val credenciales: CredencialesBrigada,
    private val conexion: ConexionRepositorio,
    private val fabricaApi: FabricaApi,
    private val outbox: Outbox,
    private val alertas: GestorAlertas,
    private val ubicacion: ProveedorUbicacion,
    private val json: Json,
) : UnidadRepositorio {

    override val equipo: Flow<EquipoUnidad?> = credenciales.equipo

    override val asignacion: Flow<AsignacionUnidad?> = preferencias.data.map { p ->
        p[ASIGNACION]?.let { runCatching { json.decodeFromString<AsignacionUnidad>(it) }.getOrNull() }
    }

    override val mensajesQueLleva: Flow<Int> = outbox.pendientes()

    override suspend fun claveBrigada(): String? = credenciales.clave()

    override suspend fun comprobarClave(clave: String): ResultadoClave {
        val direccion = conexion.direccion.first() ?: return ResultadoClave.SinConexion
        return runCatching { fabricaApi.para(direccion).equipos(clave) }.fold(
            onSuccess = { equipos ->
                ResultadoClave.Valida(equipos.map { EquipoUnidad(it.id, it.nombre, it.organismo, it.capacidades) })
            },
            onFailure = { error ->
                if (error is HttpException && error.code() == NO_AUTORIZADO) ResultadoClave.Invalida
                else ResultadoClave.SinConexion
            },
        )
    }

    override suspend fun activar(clave: String, equipo: EquipoUnidad) {
        credenciales.guardar(clave, equipo)
        preferencias.edit { it.remove(ASIGNACION) }
    }

    override suspend fun salir() {
        credenciales.borrar()
        preferencias.edit { it.remove(ASIGNACION) }
    }

    /** Llega por el WebSocket de la unidad. Devuelve la asignación si es nueva (para avisar). */
    suspend fun recibirAviso(aviso: AvisoAsignacionDto): AsignacionUnidad? {
        val actual = asignacion.first()
        val a = aviso.asignacion
        val destino = aviso.destino ?: return null
        // Terminada (hora_fin) o rechazada: si era la de este equipo, ya no está en curso
        if (!enCurso(a.decision, a.horaFin)) {
            if (actual?.id == a.id) preferencias.edit { it.remove(ASIGNACION) }
            return null
        }
        val nueva = AsignacionUnidad(
            id = a.id,
            idReporte = a.idReporte,
            idH3 = a.idH3,
            nombreZona = aviso.nombreZona,
            motivo = a.motivo,
            minutos = a.tiempoEstimadoMin,
            destinoLat = destino.lat,
            destinoLon = destino.lon,
            pedido = aviso.pedido?.let {
                Atender(it.personas, it.atrapados, it.heridos, it.vulnerables, it.necesidades)
            },
            contacto = aviso.contacto?.let { Contacto(it.nombre, it.telefono, it.condiciones) },
            ruta = a.ruta?.toString(),
            // La misma asignación reenviada al reconectarse no vuelve a abrir el aviso
            vista = actual?.id == a.id && actual.vista,
        )
        guardar(nueva)
        return nueva.takeIf { actual?.id != a.id }
    }

    /** Al reconectarse, el servidor avisa que el equipo no tiene ninguna asignación en curso. */
    suspend fun sinAsignacion() {
        preferencias.edit { it.remove(ASIGNACION) }
    }

    /** Un bloqueo cambió la ruta de la asignación en curso. */
    suspend fun actualizarRuta(aviso: AvisoAsignacionDto) {
        val actual = asignacion.first() ?: return
        if (actual.id != aviso.asignacion.id) return
        guardar(
            actual.copy(
                ruta = aviso.asignacion.ruta?.toString(),
                minutos = aviso.asignacion.tiempoEstimadoMin,
                aviso = aviso.motivo,
            ),
        )
    }

    override suspend fun informarPosicion(punto: Punto): Boolean {
        val equipo = credenciales.equipo.first() ?: return false
        val clave = credenciales.clave() ?: return false
        val direccion = conexion.direccion.first() ?: return false
        return runCatching {
            fabricaApi.para(direccion).ubicacionEquipo(equipo.id, PuntoDto(punto.lat, punto.lon), clave)
        }.isSuccess
    }

    override suspend fun recalcularRuta(desde: Punto): Boolean {
        val actual = asignacion.first() ?: return false
        val direccion = conexion.direccion.first() ?: return false
        val ruta = runCatching {
            fabricaApi.para(direccion).ruta("${desde.lat},${desde.lon}", "${actual.destinoLat},${actual.destinoLon}")
        }.getOrNull() ?: return false
        // Solo si sigue siendo la misma asignación (pudo cerrarse mientras tanto)
        val vigente = asignacion.first()?.takeIf { it.id == actual.id } ?: return false
        guardar(vigente.copy(ruta = ruta.toString()))
        return true
    }

    override suspend fun descartarAviso() {
        asignacion.first()?.let { guardar(it.copy(aviso = null)) }
    }

    override suspend fun marcarVista(id: Int) {
        val actual = asignacion.first() ?: return
        if (actual.id == id) guardar(actual.copy(vista = true))
    }

    override suspend fun marcarAtendida(): ResultadoAtendida {
        val actual = asignacion.first() ?: return ResultadoAtendida.ATENDIDA
        val clave = credenciales.clave() ?: return ResultadoAtendida.SIN_CONEXION
        val direccion = conexion.direccion.first() ?: return ResultadoAtendida.SIN_CONEXION
        val resultado = runCatching { fabricaApi.para(direccion).atendida(actual.id, clave) }
        val error = resultado.exceptionOrNull()
        // 409: el centro ya la cerró o la reasignó; para esta unidad igual terminó
        if (error == null || (error is HttpException && error.code() == CONFLICTO)) {
            preferencias.edit { it.remove(ASIGNACION) }
            return ResultadoAtendida.ATENDIDA
        }
        return ResultadoAtendida.SIN_CONEXION
    }

    override suspend fun evaluar(datos: DatosEvaluacion): ResultadoEnvio {
        // Con asignación se evalúa su zona; sin asignación, la zona donde está la unidad
        val idH3 = asignacion.first()?.idH3 ?: run {
            val (lat, lon) = ubicacion.unaVez() ?: return ResultadoEnvio.SinUbicacion
            LocalizadorCelda(conexion.celdas()).celdaDe(lat, lon) ?: return ResultadoEnvio.SinUbicacion
        }
        val reporte = ReporteDto(
            idMensaje = UUID.randomUUID().toString(),
            idAnonimo = conexion.idAnonimo(),
            idEvento = alertas.vigente.first()?.alerta?.idEvento,
            origen = ORIGEN_BRIGADA,
            tipo = TIPO_EVALUACION,
            idH3 = idH3,
            personas = datos.personas,
            atrapados = datos.atrapados,
            heridos = datos.heridos,
            necesidades = datos.necesidades.map { it.codigo },
            nivelDano = datos.nivel?.codigo,
            hora = OffsetDateTime.now().toString(),
        )
        outbox.guardar(reporte.idMensaje, TipoMensaje.REPORTE, json.encodeToString(ReporteDto.serializer(), reporte))
        outbox.enviarPendientes()
        val mensaje = outbox.observar(reporte.idMensaje).first()
        return when (mensaje?.estado) {
            EstadoEnvio.ENVIADO -> ResultadoEnvio.Enviado
            EstadoEnvio.RECHAZADO -> ResultadoEnvio.Rechazado(mensaje.error.orEmpty())
            EstadoEnvio.PENDIENTE, null -> ResultadoEnvio.Guardado
        }
    }

    override suspend fun panorama(): Result<PanoramaUnidad> = runCatching {
        val direccion = checkNotNull(conexion.direccion.first())
        val clave = checkNotNull(credenciales.clave())
        val api = fabricaApi.para(direccion)
        val centros = conexion.celdas().associate { it.id to centroDe(it) }
        PanoramaUnidad(
            zonas = api.celdas().map {
                ZonaCritica(it.idH3, it.nombreZona, it.checkins.criticos, it.checkins.personas, centros[it.idH3])
            },
            personas = api.ubicaciones(clave).map {
                PersonaUbicada(Punto(it.lat, it.lon), it.estado, it.personas, it.vulnerables)
            },
            pedidos = api.pedidos(clave).map {
                PedidoPendiente(
                    it.idMensaje,
                    Punto(it.lat, it.lon),
                    it.nombreZona,
                    it.estado,
                    Atender(it.personas, it.atrapados, it.heridos, it.vulnerables, it.necesidades),
                )
            },
        )
    }

    private suspend fun guardar(asignacion: AsignacionUnidad) {
        preferencias.edit { it[ASIGNACION] = json.encodeToString(AsignacionUnidad.serializer(), asignacion) }
    }

    private companion object {
        val ASIGNACION = stringPreferencesKey("asignacion_unidad")
    }
}
