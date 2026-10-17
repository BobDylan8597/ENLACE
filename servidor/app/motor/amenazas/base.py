"""Interfaz común de los modelos de amenaza (patrón Strategy)."""

from __future__ import annotations

from typing import Protocol

from app.modelos.enums import TipoEvento
from app.motor.tipos import DatosCelda, DatosEvento


class ModeloAmenaza(Protocol):
    tipo: TipoEvento

    def fraccion_dano(self, evento: DatosEvento, celda: DatosCelda) -> float:
        """Fracción (0–1) de ocupantes de la celda con daño grave."""
        ...
