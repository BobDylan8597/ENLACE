"""Ciudad activa: el servidor no depende de ninguna ciudad en particular.

Cada ciudad es una carpeta en `datos/ciudades/<id>/` con un `ciudad.toml`.
Ahí mismo se guardan sus datos preparados (mapa local, población, vulnerabilidad).
"""

from __future__ import annotations

import tomllib
from functools import lru_cache
from pathlib import Path
from zoneinfo import ZoneInfo

from pydantic import BaseModel, ConfigDict, Field

from app.config import Configuracion, obtener_config, resolver_ruta


class Coordenada(BaseModel):
    model_config = ConfigDict(frozen=True, extra="forbid")

    lat: float = Field(ge=-90, le=90)
    lon: float = Field(ge=-180, le=180)


class Ciudad(BaseModel):
    model_config = ConfigDict(frozen=True, extra="forbid")

    id: str
    nombre: str
    pais: str
    codigo_pais: str
    zona_horaria: str
    idioma: str
    radio_km: float = Field(gt=0)
    centro: Coordenada

    @property
    def zona(self) -> ZoneInfo:
        return ZoneInfo(self.zona_horaria)


def directorio_ciudad(config: Configuracion) -> Path:
    return resolver_ruta(config.ciudad.directorio) / config.ciudad.activa


def cargar_ciudad(directorio: Path) -> Ciudad:
    with (directorio / "ciudad.toml").open("rb") as archivo:
        return Ciudad.model_validate(tomllib.load(archivo))


@lru_cache
def obtener_ciudad() -> Ciudad:
    return cargar_ciudad(directorio_ciudad(obtener_config()))
