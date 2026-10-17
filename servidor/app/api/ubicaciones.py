"""Puntos exactos de quienes eligieron compartirlos durante emergencias (solo brigada y admin)."""

from __future__ import annotations

from fastapi import APIRouter

from app.api.dependencias import Brigada, SesionDep
from app.modelos.esquemas import UbicacionSalida
from app.motor.pipeline import evento_activo
from app.privacidad.ubicaciones import ubicaciones_del_evento

router = APIRouter(tags=["ubicaciones"])


@router.get("/ubicaciones", response_model=list[UbicacionSalida], dependencies=[Brigada])
def listar(sesion: SesionDep) -> list[UbicacionSalida]:
    """Del evento activo. Sin evento activo no hay puntos: se borran al cerrarlo."""
    evento = evento_activo(sesion)
    return [] if evento is None else ubicaciones_del_evento(sesion, evento)
