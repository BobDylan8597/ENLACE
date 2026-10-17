"""Construcción de las respuestas de la API (y de los mensajes en tiempo real)."""

from __future__ import annotations

from collections.abc import Iterable

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.modelos.enums import EstadoCheckIn, TipoReporte
from app.modelos.esquemas import (
    AsignacionSalida,
    CeldaResumen,
    ReporteSalida,
    TotalesCheckIn,
    Ubicacion,
)
from app.modelos.tablas import Asignacion, Celda, CheckIn, Equipo, Evento, Identidad, Reporte
from app.motor.prioridad import Prioridad


def totales_checkin(sesion: Session, evento: Evento | None) -> dict[str, TotalesCheckIn]:
    if evento is None:
        return {}
    filas = sesion.execute(
        select(CheckIn.id_h3, CheckIn.estado, func.count())
        .where(CheckIn.id_evento == evento.id)
        .group_by(CheckIn.id_h3, CheckIn.estado)
    ).all()
    totales: dict[str, TotalesCheckIn] = {}
    for id_h3, estado, cantidad in filas:
        actual = totales.setdefault(id_h3, TotalesCheckIn())
        setattr(actual, EstadoCheckIn(estado).value, cantidad)
    return totales


def resumen_celdas(
    sesion: Session, evento: Evento | None, ids: Iterable[str] | None = None
) -> list[CeldaResumen]:
    consulta = select(Celda)
    if ids is not None:
        consulta = consulta.where(Celda.id_h3.in_(list(ids)))
    totales = totales_checkin(sesion, evento)
    return [
        CeldaResumen.model_validate(celda).model_copy(
            update={"checkins": totales.get(celda.id_h3, TotalesCheckIn())}
        )
        for celda in sesion.scalars(consulta.order_by(Celda.prioridad.desc()))
    ]


ClaveIdentidad = tuple[str, int | None]


def identidades_de_ayuda(
    sesion: Session, reportes: list[Reporte]
) -> dict[ClaveIdentidad, Identidad]:
    """Identidad por (id_anonimo, id_evento), solo para quien pidió ayuda (sección 13)."""
    claves = {(r.id_anonimo, r.id_evento) for r in reportes if r.tipo is TipoReporte.ayuda}
    if not claves:
        return {}
    candidatas = sesion.scalars(
        select(Identidad).where(Identidad.id_anonimo.in_({a for a, _ in claves}))
    )
    return {
        (i.id_anonimo, i.id_evento): i for i in candidatas if (i.id_anonimo, i.id_evento) in claves
    }


def reporte_salida(
    reporte: Reporte,
    prioridad: Prioridad | None,
    celdas: dict[str, Celda],
    identidades: dict[ClaveIdentidad, Identidad],
    duplicados: int = 0,
) -> ReporteSalida:
    celda = celdas.get(reporte.id_h3)
    identidad = (
        identidades.get((reporte.id_anonimo, reporte.id_evento))
        if reporte.tipo is TipoReporte.ayuda
        else None
    )
    return ReporteSalida.model_validate(reporte).model_copy(
        update={
            "nombre_zona": celda.nombre_zona if celda else "",
            "prioridad": prioridad.valor if prioridad else 0.0,
            "componentes_prioridad": prioridad.componentes if prioridad else {},
            "duplicados": duplicados,
            "ubicacion": Ubicacion(lat=identidad.lat, lon=identidad.lon) if identidad else None,
        }
    )


def asignacion_salida(sesion: Session, asignacion: Asignacion) -> AsignacionSalida:
    equipo = sesion.get(Equipo, asignacion.id_equipo)
    return AsignacionSalida.model_validate(asignacion).model_copy(
        update={"nombre_equipo": equipo.nombre if equipo else ""}
    )
