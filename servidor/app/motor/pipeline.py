"""Pipeline del motor sobre la base de datos.

validar → deduplicar → ponderar → actualizar celda → recalcular prioridades → recomendar

Las funciones puras del motor (impacto, fusion, prioridad, asignacion) no conocen la base de
datos; este módulo las alimenta con lo guardado y escribe de vuelta sus resultados.
"""

from __future__ import annotations

import dataclasses
from dataclasses import dataclass
from datetime import datetime
from zoneinfo import ZoneInfo

import h3
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import Configuracion
from app.modelos.enums import (
    Capacidad,
    CategoriaCelda,
    DecisionAsignacion,
    EstadoEquipo,
    EstadoEvento,
    EstadoReporte,
    TipoReporte,
)
from app.modelos.tablas import Asignacion, Celda, CheckIn, Equipo, Evento, Reporte
from app.motor.amenazas import obtener_modelo
from app.motor.asignacion import (
    EquipoDisponible,
    EstimadorLineaRecta,
    EstimadorTiempo,
    Pendiente,
    capacidad_requerida,
    describir_reporte,
    recomendar,
)
from app.motor.fusion import estimar_celda, preparar_evidencia
from app.motor.geo import centro_celda
from app.motor.impacto import fraccion_inicial, ocupantes
from app.motor.prioridad import Prioridad, clasificar, prioridad_celda, prioridad_reporte
from app.motor.tipos import DatosCelda, DatosCheckIn, DatosEvento, DatosReporte, EstadoCelda

DECISIONES_EN_CURSO = (DecisionAsignacion.aceptada, DecisionAsignacion.modificada)


# ── Conversión entre tablas y datos del motor ──────────────


def a_datos_evento(evento: Evento) -> DatosEvento:
    return DatosEvento(
        tipo=evento.tipo,
        lat=evento.lat,
        lon=evento.lon,
        hora=evento.hora,
        magnitud=evento.magnitud,
        profundidad_km=evento.profundidad_km,
    )


def a_datos_celda(celda: Celda) -> DatosCelda:
    return DatosCelda(
        id_h3=celda.id_h3,
        poblacion_por_hora=celda.poblacion_por_hora,
        vulnerabilidad=celda.vulnerabilidad,
        tipos_construccion=celda.tipos_construccion,
        nombre_zona=celda.nombre_zona,
    )


def a_datos_reporte(reporte: Reporte) -> DatosReporte:
    datos = DatosReporte(
        id_mensaje=reporte.id_mensaje,
        id_anonimo=reporte.id_anonimo,
        id_h3=reporte.id_h3,
        origen=reporte.origen,
        tipo=reporte.tipo,
        hora=reporte.hora,
        personas=reporte.personas,
        atrapados=reporte.atrapados,
        heridos=reporte.heridos,
        vulnerables=reporte.vulnerables,
        necesidades=list(reporte.necesidades),
        nivel_dano=reporte.nivel_dano,
    )
    if reporte.estado is EstadoReporte.atendido:
        # Sigue siendo evidencia del daño, pero sus personas ya no están pendientes
        datos = dataclasses.replace(datos, atrapados=0, heridos=0)
    return datos


# ── Consultas ──────────────────────────────────────────────


def evento_activo(sesion: Session) -> Evento | None:
    consulta = (
        select(Evento).where(Evento.estado == EstadoEvento.activo).order_by(Evento.hora.desc())
    )
    return sesion.scalars(consulta).first()


def obtener_o_crear_celda(sesion: Session, id_h3: str, config: Configuracion) -> Celda:
    """Las celdas vienen de la ciudad preparada; un reporte de fuera del área crea una vacía."""
    celda = sesion.get(Celda, id_h3)
    if celda is None:
        celda = Celda(
            id_h3=id_h3,
            nombre_zona=config.celdas.nombre_fuera_de_area,
            poblacion_por_hora=[0.0] * 24,
            vulnerabilidad=config.celdas.vulnerabilidad_por_defecto,
            infraestructura_critica={},
            tipos_construccion={},
        )
        sesion.add(celda)
        sesion.flush()
    return celda


def _reportes_de_celda(sesion: Session, id_h3: str, id_evento: int) -> list[Reporte]:
    consulta = select(Reporte).where(
        Reporte.id_h3 == id_h3,
        Reporte.id_evento == id_evento,
        Reporte.estado != EstadoReporte.descartado,
    )
    return list(sesion.scalars(consulta))


def _checkins_de_celda(sesion: Session, id_h3: str, id_evento: int) -> list[DatosCheckIn]:
    consulta = select(CheckIn).where(CheckIn.id_h3 == id_h3, CheckIn.id_evento == id_evento)
    return [DatosCheckIn(c.id_anonimo, c.id_h3, c.estado) for c in sesion.scalars(consulta)]


# ── Actualizar celdas ──────────────────────────────────────


def recalcular_celda(
    sesion: Session,
    celda: Celda,
    evento: Evento,
    ahora: datetime,
    config: Configuracion,
    zona: ZoneInfo,
) -> EstadoCelda:
    datos_celda = a_datos_celda(celda)
    modelo = obtener_modelo(evento.tipo, config.amenazas)
    fraccion_modelo = fraccion_inicial(a_datos_evento(evento), datos_celda, modelo)
    personas = ocupantes(datos_celda, evento.hora, zona)

    reportes = _reportes_de_celda(sesion, celda.id_h3, evento.id)
    preparada = preparar_evidencia([a_datos_reporte(r) for r in reportes], config.fusion)
    pesos = {e.reporte.id_mensaje: e.peso for e in preparada.principales}
    for reporte in reportes:
        reporte.id_reporte_padre = preparada.duplicados.get(reporte.id_mensaje)
        if reporte.id_mensaje in pesos:
            reporte.confiabilidad = pesos[reporte.id_mensaje]

    estado = estimar_celda(
        celda.id_h3,
        personas,
        fraccion_modelo,
        preparada.principales,
        _checkins_de_celda(sesion, celda.id_h3, evento.id),
        config.fusion,
    )
    categoria = clasificar(estado.impacto_estimado, estado.certeza, config.clasificacion)
    prioridad = prioridad_celda(
        estado.impacto_estimado,
        celda.vulnerabilidad,
        evento.hora,
        celda.ultima_atencion,
        ahora,
        config.prioridad.celda,
    )
    celda.impacto_inicial = estado.impacto_inicial
    celda.impacto_estimado = estado.impacto_estimado
    celda.certeza = estado.certeza
    celda.suma_pesos = estado.suma_pesos
    celda.categoria = categoria
    celda.prioridad = prioridad.valor
    return dataclasses.replace(estado, categoria=categoria)


def recalcular_todas(
    sesion: Session, evento: Evento, ahora: datetime, config: Configuracion, zona: ZoneInfo
) -> list[EstadoCelda]:
    return [
        recalcular_celda(sesion, celda, evento, ahora, config, zona)
        for celda in sesion.scalars(select(Celda))
    ]


# ── Prioridad de reportes (depende de la hora: se calcula al consultar) ──


@dataclass(frozen=True)
class ReporteConPrioridad:
    reporte: Reporte
    prioridad: Prioridad


def reportes_por_prioridad(
    sesion: Session, evento: Evento, ahora: datetime, config: Configuracion
) -> list[ReporteConPrioridad]:
    consulta = select(Reporte).where(
        Reporte.id_evento == evento.id, Reporte.id_reporte_padre.is_(None)
    )
    reportes = list(sesion.scalars(consulta))
    celdas = {c.id_h3: c for c in sesion.scalars(select(Celda))}
    resultado = []
    for reporte in reportes:
        celda = celdas.get(reporte.id_h3)
        prioridad = prioridad_reporte(
            a_datos_reporte(reporte),
            celda.prioridad if celda else 0.0,
            celda.vulnerabilidad if celda else config.celdas.vulnerabilidad_por_defecto,
            ahora,
            config.prioridad.reporte,
        )
        resultado.append(ReporteConPrioridad(reporte, prioridad))
    return sorted(resultado, key=lambda r: r.prioridad.valor, reverse=True)


# ── Recomendaciones ────────────────────────────────────────


def _pendientes(
    sesion: Session, evento: Evento, ahora: datetime, config: Configuracion
) -> list[Pendiente]:
    pendientes: list[Pendiente] = []
    celdas = {c.id_h3: c for c in sesion.scalars(select(Celda))}
    celdas_con_pedidos: set[str] = set()

    for item in reportes_por_prioridad(sesion, evento, ahora, config):
        reporte = item.reporte
        if reporte.tipo is not TipoReporte.ayuda or reporte.estado is not EstadoReporte.sin_asignar:
            continue
        celdas_con_pedidos.add(reporte.id_h3)
        celda = celdas.get(reporte.id_h3)
        pendientes.append(
            Pendiente(
                id_h3=reporte.id_h3,
                nombre_zona=celda.nombre_zona if celda else config.celdas.nombre_fuera_de_area,
                prioridad=item.prioridad.valor,
                capacidad=capacidad_requerida(a_datos_reporte(reporte), config.asignacion),
                descripcion=describir_reporte(a_datos_reporte(reporte)),
                destino=centro_celda(reporte.id_h3),
                id_reporte=reporte.id_mensaje,
            )
        )

    en_curso = {
        a.id_h3: a
        for a in sesion.scalars(
            select(Asignacion).where(
                Asignacion.id_reporte.is_(None), Asignacion.decision.in_(DECISIONES_EN_CURSO)
            )
        )
    }
    for celda in celdas.values():
        if celda.categoria is not CategoriaCelda.explorar or celda.id_h3 in celdas_con_pedidos:
            continue
        asignada = en_curso.get(celda.id_h3)
        if asignada and (celda.ultima_atencion is None or celda.ultima_atencion < asignada.hora):
            continue
        pendientes.append(
            Pendiente(
                id_h3=celda.id_h3,
                nombre_zona=celda.nombre_zona,
                prioridad=celda.prioridad,
                capacidad=Capacidad.reconocimiento,
                descripcion=(
                    f"impacto estimado alto (unas {celda.impacto_estimado:.0f} personas) "
                    f"con poca información (certeza {celda.certeza:.0%})"
                ),
                destino=centro_celda(celda.id_h3),
            )
        )
    return pendientes


def actualizar_recomendaciones(
    sesion: Session,
    evento: Evento,
    ahora: datetime,
    config: Configuracion,
    estimador: EstimadorTiempo | None = None,
) -> list[Asignacion]:
    """Regenera las asignaciones pendientes, conservando las que siguen siendo válidas."""
    estimador = estimador or EstimadorLineaRecta(config.asignacion)
    equipos = [
        EquipoDisponible(
            e.id, e.nombre, frozenset(Capacidad(c) for c in e.capacidades), (e.lat, e.lon)
        )
        for e in sesion.scalars(select(Equipo).where(Equipo.estado == EstadoEquipo.disponible))
    ]
    vetados = frozenset(
        (a.id_reporte or a.id_h3, a.id_equipo)
        for a in sesion.scalars(
            select(Asignacion).where(Asignacion.decision == DecisionAsignacion.rechazada)
        )
    )
    resultado = recomendar(
        _pendientes(sesion, evento, ahora, config), equipos, estimador, config.asignacion, vetados
    )

    existentes = {
        (a.id_reporte, a.id_h3, a.id_equipo): a
        for a in sesion.scalars(
            select(Asignacion).where(Asignacion.decision == DecisionAsignacion.pendiente)
        )
    }
    vigentes: list[Asignacion] = []
    for recomendacion in resultado.recomendaciones:
        clave = (recomendacion.id_reporte, recomendacion.id_h3, recomendacion.id_equipo)
        asignacion = existentes.pop(clave, None)
        if asignacion is None:
            asignacion = Asignacion(
                id_equipo=recomendacion.id_equipo,
                id_reporte=recomendacion.id_reporte,
                id_h3=recomendacion.id_h3,
                hora=ahora,
            )
            sesion.add(asignacion)
        asignacion.motivo = recomendacion.motivo
        asignacion.tiempo_estimado_min = recomendacion.tiempo_estimado_min
        vigentes.append(asignacion)
    for obsoleta in existentes.values():
        sesion.delete(obsoleta)
    sesion.flush()
    return vigentes


def es_celda_valida(id_h3: str, config: Configuracion) -> bool:
    return h3.is_valid_cell(id_h3) and h3.get_resolution(id_h3) == config.h3.resolucion


# ── Entrada de datos ───────────────────────────────────────


@dataclass(frozen=True)
class ResultadoIngreso:
    nuevos: list[Reporte]
    # id_mensaje ya recibidos antes (por otra vía o por otra mula de datos): se ignoran
    repetidos: list[str]
    celdas_actualizadas: list[str]


def ingresar_reportes(
    sesion: Session,
    reportes: list[Reporte],
    ahora: datetime,
    config: Configuracion,
    zona: ZoneInfo,
) -> ResultadoIngreso:
    """Guarda reportes nuevos y recalcula sus celdas. Los reportes ya deben venir validados."""
    evento = evento_activo(sesion)
    ids = [r.id_mensaje for r in reportes]
    vistos = set(sesion.scalars(select(Reporte.id_mensaje).where(Reporte.id_mensaje.in_(ids))))
    nuevos: list[Reporte] = []
    repetidos: list[str] = []
    for reporte in reportes:
        if reporte.id_mensaje in vistos:
            repetidos.append(reporte.id_mensaje)
            continue
        vistos.add(reporte.id_mensaje)
        if reporte.id_evento is None and evento is not None:
            reporte.id_evento = evento.id
        reporte.confiabilidad = config.fusion.pesos_origen[reporte.origen]
        obtener_o_crear_celda(sesion, reporte.id_h3, config)
        sesion.add(reporte)
        nuevos.append(reporte)
    sesion.flush()

    celdas = sorted({r.id_h3 for r in nuevos})
    if evento is not None:
        for id_h3 in celdas:
            recalcular_celda(sesion, sesion.get_one(Celda, id_h3), evento, ahora, config, zona)
    return ResultadoIngreso(nuevos, repetidos, celdas)


def ingresar_checkin(
    sesion: Session,
    checkin: CheckIn,
    evento: Evento,
    ahora: datetime,
    config: Configuracion,
    zona: ZoneInfo,
) -> list[str]:
    """Guarda o actualiza el check-in de una persona en el evento; devuelve celdas recalculadas."""
    existente = sesion.scalars(
        select(CheckIn).where(
            CheckIn.id_anonimo == checkin.id_anonimo, CheckIn.id_evento == evento.id
        )
    ).first()
    celdas = {checkin.id_h3}
    if existente is None:
        checkin.id_evento = evento.id
        sesion.add(checkin)
    else:
        celdas.add(existente.id_h3)
        existente.id_h3 = checkin.id_h3
        existente.estado = checkin.estado
        existente.hora = checkin.hora
    obtener_o_crear_celda(sesion, checkin.id_h3, config)
    sesion.flush()
    for id_h3 in sorted(celdas):
        recalcular_celda(sesion, sesion.get_one(Celda, id_h3), evento, ahora, config, zona)
    return sorted(celdas)
