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
