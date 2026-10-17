"""Consulta y registro de equipos de respuesta."""

from __future__ import annotations

from fastapi import APIRouter, HTTPException, status
from sqlalchemy import select

from app.api.dependencias import Admin, Brigada, SesionDep
from app.modelos.esquemas import EquipoEntrada, EquipoSalida, UbicacionEquipoEntrada
from app.modelos.tablas import Equipo

router = APIRouter(tags=["equipos"])


# La brigada elige con cuál equipo trabaja al activar el modo unidad
@router.get("/equipos", dependencies=[Brigada], response_model=list[EquipoSalida])
def listar(sesion: SesionDep) -> list[Equipo]:
    return list(sesion.scalars(select(Equipo).order_by(Equipo.nombre)))


@router.post(
    "/equipos",
    status_code=status.HTTP_201_CREATED,
    dependencies=[Admin],
    response_model=EquipoSalida,
)
def registrar(entrada: EquipoEntrada, sesion: SesionDep) -> Equipo:
    equipo = Equipo(
        nombre=entrada.nombre,
        organismo=entrada.organismo,
        capacidades=[c.value for c in entrada.capacidades],
        lat=entrada.lat,
        lon=entrada.lon,
        estado=entrada.estado,
    )
    sesion.add(equipo)
    sesion.commit()
    return equipo


@router.post("/equipos/{id_equipo}/ubicacion", dependencies=[Brigada], response_model=EquipoSalida)
def actualizar_ubicacion(
    id_equipo: int, entrada: UbicacionEquipoEntrada, sesion: SesionDep
) -> Equipo:
    """La unidad informa dónde está mientras navega (B5), para recalcular desde ahí.
    Solo equipos de respuesta: a los ciudadanos nunca se los rastrea (sección 13)."""
    equipo = sesion.get(Equipo, id_equipo)
    if equipo is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Equipo no encontrado")
    equipo.lat, equipo.lon = entrada.lat, entrada.lon
    sesion.commit()
    return equipo
