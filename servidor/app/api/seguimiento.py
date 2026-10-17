"""Seguimiento de un pedido de ayuda (U12) para el celular que lo envió.

Complementa el aviso "ayuda en camino" por WebSocket, que se pierde si el celular estaba
desconectado justo en ese momento. Sin datos personales: solo estado, equipo y tiempo. El id
es el UUID que generó el propio celular, imposible de adivinar.
"""

from __future__ import annotations

from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel
from sqlalchemy import select

from app.api.dependencias import SesionDep
from app.modelos.enums import EstadoReporte
from app.modelos.tablas import Asignacion, Equipo, Reporte
from app.motor.pipeline import DECISIONES_EN_CURSO

router = APIRouter(tags=["seguimiento"])


class Seguimiento(BaseModel):
    estado: EstadoReporte
    equipo: str | None = None
    tiempo_estimado_min: float | None = None


@router.get("/seguimiento/{id_mensaje}", response_model=Seguimiento)
def seguimiento(id_mensaje: str, sesion: SesionDep) -> Seguimiento:
    reporte = sesion.get(Reporte, id_mensaje)
    if reporte is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Pedido no recibido todavía")
    # Un reenvío fusionado sigue el estado del pedido original
    original = (
        sesion.get(Reporte, reporte.id_reporte_padre) if reporte.id_reporte_padre else reporte
    )
    asignacion = sesion.scalars(
        select(Asignacion)
        .where(
            Asignacion.id_reporte == original.id_mensaje,
            Asignacion.decision.in_(DECISIONES_EN_CURSO),
        )
        .order_by(Asignacion.hora.desc())
    ).first()
    equipo = sesion.get(Equipo, asignacion.id_equipo) if asignacion else None
    return Seguimiento(
        estado=original.estado,
        equipo=equipo.nombre if equipo else None,
        tiempo_estimado_min=asignacion.tiempo_estimado_min if asignacion else None,
    )
