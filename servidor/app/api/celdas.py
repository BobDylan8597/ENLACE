"""Rutas de celdas: totales anónimos por celda, nunca puntos exactos."""

from __future__ import annotations

from typing import Any

import h3
from fastapi import APIRouter, HTTPException, status
from sqlalchemy import func, select

from app.api.dependencias import AhoraDep, CiudadDep, ConfigDep, SesionDep
from app.api.vistas import resumen_celdas, totales_checkin
from app.modelos.esquemas import CeldaDetalle, CeldaResumen, TotalesCheckIn
from app.modelos.tablas import Celda, Reporte
from app.motor.geo import celdas_en_radio
from app.motor.pipeline import evento_activo, poblacion_critica
from app.motor.prioridad import prioridad_celda, vulnerabilidad_efectiva

router = APIRouter(tags=["celdas"])


@router.get("/celdas", response_model=list[CeldaResumen])
def listar(sesion: SesionDep) -> list[CeldaResumen]:
    return resumen_celdas(sesion, evento_activo(sesion))


@router.get("/celdas/geometria")
def geometria(ciudad: CiudadDep, config: ConfigDep) -> dict[str, Any]:
    """Forma de las celdas de la zona (ciudad y margen). Con esto el celular calcula en qué celda
    está sin enviar sus coordenadas: los check-ins viajan por celulares ajenos (red Nearby)."""
    radio = ciudad.radio_km + config.preparacion.margen_km
    return {
        "resolucion": config.h3.resolucion,
        "celdas": [
            {"id_h3": id_h3, "contorno": [list(v) for v in h3.cell_to_boundary(id_h3)]}
            for id_h3 in celdas_en_radio(
                ciudad.centro.lat, ciudad.centro.lon, radio, config.h3.resolucion
            )
        ],
    }


@router.get("/celdas/{id_h3}", response_model=CeldaDetalle)
def detalle(id_h3: str, sesion: SesionDep, ahora: AhoraDep, config: ConfigDep) -> CeldaDetalle:
    celda = sesion.get(Celda, id_h3)
    if celda is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Celda no encontrada")
    evento = evento_activo(sesion)
    componentes: dict[str, float] = {}
    reportes = 0
    if evento is not None:
        componentes = prioridad_celda(
            celda.impacto_estimado,
            vulnerabilidad_efectiva(
                celda.vulnerabilidad,
                poblacion_critica(sesion, id_h3, evento.id),
                config.prioridad.celda,
            ),
            evento.hora,
            celda.ultima_atencion,
            ahora,
            config.prioridad.celda,
        ).componentes
        reportes = (
            sesion.scalar(
                select(func.count())
                .select_from(Reporte)
                .where(
                    Reporte.id_h3 == id_h3,
                    Reporte.id_evento == evento.id,
                    Reporte.id_reporte_padre.is_(None),
                )
            )
            or 0
        )
    return CeldaDetalle.model_validate(celda).model_copy(
        update={
            "checkins": totales_checkin(sesion, evento).get(id_h3, TotalesCheckIn()),
            "reportes": reportes,
            "componentes_prioridad": componentes,
        }
    )
