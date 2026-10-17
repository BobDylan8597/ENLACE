"""Utilidades geográficas del motor."""

from __future__ import annotations

import math

import h3

RADIO_TIERRA_KM = 6371.0088


def distancia_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Distancia sobre la superficie terrestre (haversine)."""
    fi1, fi2 = math.radians(lat1), math.radians(lat2)
    d_fi = fi2 - fi1
    d_lambda = math.radians(lon2 - lon1)
    a = math.sin(d_fi / 2) ** 2 + math.cos(fi1) * math.cos(fi2) * math.sin(d_lambda / 2) ** 2
    return 2 * RADIO_TIERRA_KM * math.asin(math.sqrt(a))


def centro_celda(id_h3: str) -> tuple[float, float]:
    return h3.cell_to_latlng(id_h3)


def celdas_en_radio(lat: float, lon: float, radio_km: float, resolucion: int) -> list[str]:
    """Celdas h3 cuyo centro está a `radio_km` o menos del punto."""
    # Distancia entre centros vecinos: √3 × arista. Un anillo k cubre al menos k·paso·√3/2
    # (en la dirección de las esquinas del hexágono): hay que llegar a radio_km por ese lado
    paso_km = h3.average_hexagon_edge_length(resolucion, unit="km") * math.sqrt(3)
    anillos = math.ceil(radio_km / (paso_km * math.sqrt(3) / 2)) + 1
    centro = h3.latlng_to_cell(lat, lon, resolucion)
    return sorted(
        c
        for c in h3.grid_disk(centro, anillos)
        if distancia_km(lat, lon, *h3.cell_to_latlng(c)) <= radio_km
    )
