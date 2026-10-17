"""Rutas de celdas: totales anónimos por celda, nunca puntos exactos."""

from __future__ import annotations

from fastapi import APIRouter, HTTPException, status
from sqlalchemy import func, select

from app.api.dependencias import AhoraDep, ConfigDep, SesionDep
from app.api.vistas import resumen_celdas, totales_checkin
from app.modelos.esquemas import CeldaDetalle, CeldaResumen, TotalesCheckIn
from app.modelos.tablas import Celda, Reporte
from app.motor.pipeline import evento_activo
from app.motor.prioridad import prioridad_celda

router = APIRouter(tags=["celdas"])


@router.get("/celdas", response_model=list[CeldaResumen])
def listar(sesion: SesionDep) -> list[CeldaResumen]:
    return resumen_celdas(sesion, evento_activo(sesion))


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
            celda.vulnerabilidad,
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
