"""Construcción de las respuestas de la API (y de los mensajes en tiempo real)."""

from __future__ import annotations

from collections.abc import Iterable
from typing import Any

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
        select(
            CheckIn.id_h3,
            CheckIn.estado,
            func.count(),
            func.sum(CheckIn.personas),
            func.sum(CheckIn.vulnerables),
        )
        .where(CheckIn.id_evento == evento.id)
        .group_by(CheckIn.id_h3, CheckIn.estado)
    ).all()
    totales: dict[str, TotalesCheckIn] = {}
    for id_h3, estado, cantidad, personas, vulnerables in filas:
        actual = totales.setdefault(id_h3, TotalesCheckIn())
        estado = EstadoCheckIn(estado)
        setattr(actual, estado.value, cantidad)
        actual.personas += personas or 0
        actual.vulnerables += vulnerables or 0
        if estado is not EstadoCheckIn.bien:
            actual.criticos += vulnerables or 0
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


def aviso_asignacion(
    sesion: Session, asignacion: Asignacion, destino: tuple[float, float]
) -> dict[str, Any]:
    """Lo que recibe la unidad asignada: destino, qué hay que atender y, si es un pedido de
    ayuda, el contacto de la persona (solo para esa unidad: para encontrarla y llamarla).
    `id_anonimo` sirve para avisar al ciudadano y no se reenvía a la unidad."""
    reporte = sesion.get(Reporte, asignacion.id_reporte) if asignacion.id_reporte else None
    celda = sesion.get(Celda, asignacion.id_h3)
    pedido = contacto = None
    if reporte is not None:
        pedido = {
            "tipo": reporte.tipo.value,
            "personas": reporte.personas,
            "atrapados": reporte.atrapados,
            "heridos": reporte.heridos,
            "vulnerables": reporte.vulnerables,
            "necesidades": list(reporte.necesidades),
        }
        identidad = identidades_de_ayuda(sesion, [reporte]).get(
            (reporte.id_anonimo, reporte.id_evento)
        )
        if identidad is not None:
            contacto = {
                "nombre": identidad.nombre,
                "telefono": identidad.telefono,
                "condiciones": identidad.condiciones,
            }
    return {
        "asignacion": asignacion_salida(sesion, asignacion).model_dump(mode="json"),
        "destino": {"lat": destino[0], "lon": destino[1]},
        "nombre_zona": celda.nombre_zona if celda else "",
        "pedido": pedido,
        "contacto": contacto,
        "id_anonimo": reporte.id_anonimo if reporte else None,
    }


def asignacion_salida(sesion: Session, asignacion: Asignacion) -> AsignacionSalida:
    equipo = sesion.get(Equipo, asignacion.id_equipo)
    return AsignacionSalida.model_validate(asignacion).model_copy(
        update={"nombre_equipo": equipo.nombre if equipo else ""}
    )
