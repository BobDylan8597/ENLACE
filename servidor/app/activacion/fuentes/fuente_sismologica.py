"""Fuente sismológica: un sismo en formato GeoJSON (el que usan USGS y muchas redes nacionales).

{"type": "Feature",
 "properties": {"mag": 6.2, "time": <milisegundos desde 1970>},
 "geometry": {"type": "Point", "coordinates": [lon, lat, profundidad_km]}}
"""

from __future__ import annotations

from datetime import UTC, datetime
from typing import Any

from app.activacion.fuentes.base import EntradaInvalida, NuevoEvento
from app.modelos.enums import FuenteEvento, TipoEvento


class FuenteSismologica:
    fuente = FuenteEvento.sismologica

    def convertir(self, entrada: dict[str, Any]) -> NuevoEvento:
        try:
            propiedades = entrada["properties"]
            coordenadas = entrada["geometry"]["coordinates"]
            lon, lat = float(coordenadas[0]), float(coordenadas[1])
            profundidad = float(coordenadas[2]) if len(coordenadas) > 2 else None
            magnitud = float(propiedades["mag"])
            hora = datetime.fromtimestamp(propiedades["time"] / 1000, tz=UTC)
        except (KeyError, IndexError, TypeError, ValueError) as error:
            raise EntradaInvalida(f"GeoJSON sismológico inválido: {error}") from error
        return NuevoEvento(
            tipo=TipoEvento.sismo,
            fuente=self.fuente,
            lat=lat,
            lon=lon,
            hora=hora,
            magnitud=magnitud,
            profundidad_km=profundidad,
        )
