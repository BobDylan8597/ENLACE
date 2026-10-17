"""Impacto inicial: personas afectadas estimadas desde el primer minuto, sin reportes.

personas_afectadas = ocupantes(hora local del evento) × fracción con daño grave
"""

from __future__ import annotations

from datetime import datetime
from zoneinfo import ZoneInfo

from app.motor.amenazas.base import ModeloAmenaza
from app.motor.tipos import DatosCelda, DatosEvento


def ocupantes(celda: DatosCelda, hora: datetime, zona: ZoneInfo) -> float:
    """Personas en la celda a esa hora (la población cambia entre el día y la noche)."""
    if len(celda.poblacion_por_hora) != 24:
        raise ValueError(f"Celda {celda.id_h3}: poblacion_por_hora debe tener 24 valores")
    return celda.poblacion_por_hora[hora.astimezone(zona).hour]


def fraccion_inicial(evento: DatosEvento, celda: DatosCelda, modelo: ModeloAmenaza | None) -> float:
    if modelo is None:
        return 0.0
    return min(max(modelo.fraccion_dano(evento, celda), 0.0), 1.0)
