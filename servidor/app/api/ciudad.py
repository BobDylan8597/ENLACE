"""Datos públicos de la ciudad activa y su mapa base local (sin internet)."""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter, HTTPException, status
from fastapi.responses import FileResponse

from app.api.dependencias import CiudadDep, ConfigDep
from app.ciudad import directorio_ciudad

router = APIRouter(tags=["ciudad"])

ATRIBUCION_MAPA = "© OpenStreetMap · Protomaps"


@router.get("/ciudad")
def ciudad_activa(ciudad: CiudadDep, config: ConfigDep) -> dict[str, Any]:
    """Para que la web y la app centren el mapa sin coordenadas escritas en su código."""
    mapa = directorio_ciudad(config) / config.preparacion.archivo_mapa
    return {
        "id": ciudad.id,
        "nombre": ciudad.nombre,
        "pais": ciudad.pais,
        "zona_horaria": ciudad.zona_horaria,
        "idioma": ciudad.idioma,
        "centro": {"lat": ciudad.centro.lat, "lon": ciudad.centro.lon},
        "radio_km": ciudad.radio_km,
        "resolucion_h3": config.h3.resolucion,
        "mapa_disponible": mapa.exists(),
        "atribucion_mapa": ATRIBUCION_MAPA,
        # Para ofrecer el envío por SMS cuando no hay otra conexión
        "numero_sms": config.sms.numero_receptor,
    }


@router.get("/mapa/base.pmtiles")
def mapa_base(config: ConfigDep) -> FileResponse:
    """Mapa vectorial de la ciudad. Se lee por rangos HTTP (Range), como un servidor de tiles."""
    mapa = directorio_ciudad(config) / config.preparacion.archivo_mapa
    if not mapa.exists():
        raise HTTPException(
            status.HTTP_404_NOT_FOUND, "No hay mapa base: ejecutar preparacion/descargar_osm.py"
        )
    return FileResponse(mapa, media_type="application/octet-stream")
