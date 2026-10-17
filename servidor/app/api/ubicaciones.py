"""Datos del mapa de la brigada (solo con clave de brigada o de administrador):

- `/ubicaciones`: puntos de quienes eligieron compartirlos durante la emergencia.
- `/pedidos`: pedidos de ayuda pendientes, con su punto y qué necesitan, sin datos personales.
"""

from __future__ import annotations

from fastapi import APIRouter
from sqlalchemy import select

from app.api.dependencias import Brigada, SesionDep
from app.api.vistas import identidades_de_ayuda
from app.modelos.enums import EstadoReporte, TipoReporte
from app.modelos.esquemas import PedidoMapa, UbicacionSalida
from app.modelos.tablas import Celda, Reporte
from app.motor.pipeline import evento_activo
from app.privacidad.ubicaciones import ubicaciones_del_evento

router = APIRouter(tags=["mapa de la brigada"])


@router.get("/ubicaciones", response_model=list[UbicacionSalida], dependencies=[Brigada])
def listar(sesion: SesionDep) -> list[UbicacionSalida]:
    """Del evento activo. Sin evento activo no hay puntos: se borran al cerrarlo."""
    evento = evento_activo(sesion)
    return [] if evento is None else ubicaciones_del_evento(sesion, evento)


@router.get("/pedidos", response_model=list[PedidoMapa], dependencies=[Brigada])
def pedidos(sesion: SesionDep) -> list[PedidoMapa]:
    evento = evento_activo(sesion)
    if evento is None:
        return []
    reportes = list(
        sesion.scalars(
            select(Reporte).where(
                Reporte.id_evento == evento.id,
                Reporte.tipo == TipoReporte.ayuda,
                Reporte.id_reporte_padre.is_(None),
                Reporte.estado.in_([EstadoReporte.sin_asignar, EstadoReporte.asignado]),
            )
        )
    )
    identidades = identidades_de_ayuda(sesion, reportes)
    salida = []
    for r in reportes:
        identidad = identidades.get((r.id_anonimo, r.id_evento))
        if identidad is None:  # sin punto exacto: ya se ve en la zona
            continue
        celda = sesion.get(Celda, r.id_h3)
        salida.append(
            PedidoMapa(
                id_mensaje=r.id_mensaje,
                lat=identidad.lat,
                lon=identidad.lon,
                id_h3=r.id_h3,
                nombre_zona=celda.nombre_zona if celda else "",
                estado=r.estado,
                personas=r.personas,
                atrapados=r.atrapados,
                heridos=r.heridos,
                vulnerables=r.vulnerables,
                necesidades=r.necesidades,
                hora=r.hora,
            )
        )
    return salida
