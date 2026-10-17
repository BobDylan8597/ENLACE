"""Formato SMS de reporte (sección 14):

    ENL <lat>,<lon> P<personas> A<atrapados> H<heridos> <necesidades>

Necesidades: R=rescate, M=médico, W=agua, F=refugio. Ejemplo: `ENL 13.10,-85.20 P4 A1 H1 RM`.
Se tolera mayúsculas/minúsculas y espacios extra; P, A, H y las necesidades son opcionales.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field

from app.modelos.enums import Necesidad

PREFIJO = "ENL"
LETRAS_NECESIDAD = {
    "R": Necesidad.rescate,
    "M": Necesidad.medico,
    "W": Necesidad.agua,
    "F": Necesidad.refugio,
}
_COORDENADAS = re.compile(r"^(-?\d{1,2}(?:\.\d+)?),\s*(-?\d{1,3}(?:\.\d+)?)$")
_CONTEO = re.compile(r"^([PAH])(\d{1,4})$")
_NECESIDADES = re.compile(r"^[RMWF]+$")


class SmsInvalido(ValueError):
    pass


@dataclass(frozen=True)
class SmsReporte:
    lat: float
    lon: float
    personas: int = 0
    atrapados: int = 0
    heridos: int = 0
    necesidades: list[Necesidad] = field(default_factory=list)


def interpretar_sms(texto: str) -> SmsReporte:
    partes = texto.strip().upper().split()
    if not partes or partes[0] != PREFIJO:
        raise SmsInvalido(f"El mensaje debe empezar con {PREFIJO}")
    if len(partes) < 2:
        raise SmsInvalido("Falta la ubicación")

    # Las coordenadas pueden venir como "13.1,-85.2" o "13.1, -85.2"
    resto = partes[1:]
    if resto[0].endswith(",") and len(resto) > 1:
        resto = [resto[0] + resto[1], *resto[2:]]
    coordenadas = _COORDENADAS.match(resto[0])
    if coordenadas is None:
        raise SmsInvalido(f"Ubicación no reconocida: {resto[0]}")
    lat, lon = float(coordenadas.group(1)), float(coordenadas.group(2))
    if not (-90 <= lat <= 90 and -180 <= lon <= 180):
        raise SmsInvalido("Ubicación fuera de rango")

    conteos = {"P": 0, "A": 0, "H": 0}
    necesidades: list[Necesidad] = []
    for parte in resto[1:]:
        conteo = _CONTEO.match(parte)
        if conteo:
            conteos[conteo.group(1)] = int(conteo.group(2))
        elif _NECESIDADES.match(parte):
            for letra in parte:
                if LETRAS_NECESIDAD[letra] not in necesidades:
                    necesidades.append(LETRAS_NECESIDAD[letra])
        else:
            raise SmsInvalido(f"Parte no reconocida: {parte}")
    return SmsReporte(lat, lon, conteos["P"], conteos["A"], conteos["H"], necesidades)
