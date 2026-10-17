"""Interfaz común de las fuentes de alerta (patrón Adapter).

Cada fuente traduce su propio formato (CAP, sismológica, formulario manual, propuesta
comunitaria) a un `NuevoEvento`. Solo el servidor emite, y solo con fuente oficial o
confirmación humana.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Protocol, TypeVar

from app.modelos.enums import FuenteEvento, TipoEvento

E = TypeVar("E", contravariant=True)


@dataclass(frozen=True)
class NuevoEvento:
    tipo: TipoEvento
    fuente: FuenteEvento
    lat: float
    lon: float
    hora: datetime
    magnitud: float | None = None
    profundidad_km: float | None = None
    # Si no se indica, la zona afectada se deduce del modelo de amenaza
    zona_afectada: list[str] | None = None


class FuenteAlerta(Protocol[E]):
    fuente: FuenteEvento

    def convertir(self, entrada: E) -> NuevoEvento: ...


class EntradaInvalida(ValueError):
    """La entrada de la fuente no se puede convertir en un evento."""
