"""Reglas puras de la validación retrospectiva (validar_evento.py): métricas, bandas y ajuste.

Se prueban en servidor/tests/test_preparacion.py.
"""

from __future__ import annotations

import math
from collections.abc import Iterable
from dataclasses import dataclass

# Bandas de distancia al hipocentro para ver dónde acierta o falla el modelo
BANDAS_KM = ((0, 50), (50, 150), (150, 300), (300, 10_000))


@dataclass(frozen=True)
class Observacion:
    """Intensidad observada en un lugar y la que estima ENLACE para ese lugar."""

    fuente: str  # "dyfi" (reportes de la gente) o "estacion" (acelerógrafo)
    distancia_km: float  # hipocentral
    magnitud: float
    observada: float
    estimada: float

    @property
    def error(self) -> float:
        return self.estimada - self.observada


@dataclass(frozen=True)
class Metricas:
    n: int
    sesgo: float  # promedio de (estimada − observada): > 0, el modelo sobreestima
    rmse: float
    dentro_de_1: float  # fracción con error de a lo sumo 1 grado

    @staticmethod
    def de(observaciones: Iterable[Observacion]) -> Metricas | None:
        errores = [o.error for o in observaciones]
        if not errores:
            return None
        return Metricas(
            n=len(errores),
            sesgo=sum(errores) / len(errores),
            rmse=math.sqrt(sum(e * e for e in errores) / len(errores)),
            dentro_de_1=sum(1 for e in errores if abs(e) <= 1.0) / len(errores),
        )


def por_banda(observaciones: list[Observacion]) -> dict[str, Metricas]:
    resultado = {}
    for desde, hasta in BANDAS_KM:
        metricas = Metricas.de(o for o in observaciones if desde <= o.distancia_km < hasta)
        if metricas is not None:
            etiqueta = f"{desde}–{hasta} km" if hasta < BANDAS_KM[-1][1] else f"más de {desde} km"
            resultado[etiqueta] = metricas
    return resultado


def ajustar_a_y_c(observaciones: list[Observacion], b: float) -> tuple[float, float] | None:
    """Mínimos cuadrados de MMI = a + b·M − c·log10(R) con b fijo: devuelve (a, c).
    Con pocos sismos, b (la escala con la magnitud) queda mal determinado; por eso se fija."""
    if len(observaciones) < 3:
        return None
    # y = MMI − b·M = a − c·x, con x = log10(R)
    xs = [math.log10(max(o.distancia_km, 1.0)) for o in observaciones]
    ys = [o.observada - b * o.magnitud for o in observaciones]
    n = len(xs)
    media_x, media_y = sum(xs) / n, sum(ys) / n
    varianza = sum((x - media_x) ** 2 for x in xs)
    if varianza == 0:
        return None
    pendiente = sum((x - media_x) * (y - media_y) for x, y in zip(xs, ys, strict=True)) / varianza
    return media_y - pendiente * media_x, -pendiente


def grado_mmi(intensidad: float) -> int:
    """Grado entero de Mercalli (I–X), como las bandas de exposición de PAGER."""
    return min(max(round(intensidad), 1), 10)


def exposicion(poblacion_e_intensidad: Iterable[tuple[float, float]]) -> dict[int, float]:
    """Personas expuestas a cada grado de intensidad."""
    total: dict[int, float] = {}
    for personas, intensidad in poblacion_e_intensidad:
        grado = grado_mmi(intensidad)
        total[grado] = total.get(grado, 0.0) + personas
    return total


ROMANOS = ["", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"]
