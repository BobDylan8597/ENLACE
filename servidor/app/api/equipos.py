"""Consulta y registro de equipos de respuesta."""

from __future__ import annotations

from fastapi import APIRouter, status
from sqlalchemy import select

from app.api.dependencias import Admin, SesionDep
from app.modelos.esquemas import EquipoEntrada, EquipoSalida
from app.modelos.tablas import Equipo

router = APIRouter(tags=["equipos"], dependencies=[Admin])


@router.get("/equipos", response_model=list[EquipoSalida])
def listar(sesion: SesionDep) -> list[Equipo]:
    return list(sesion.scalars(select(Equipo).order_by(Equipo.nombre)))


@router.post("/equipos", status_code=status.HTTP_201_CREATED, response_model=EquipoSalida)
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
