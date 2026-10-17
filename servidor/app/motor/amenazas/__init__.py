"""Selección del modelo de amenaza según el tipo de evento."""

from __future__ import annotations

from app.config import ConfigAmenazas
from app.modelos.enums import TipoEvento
from app.motor.amenazas.base import ModeloAmenaza
from app.motor.amenazas.inundacion import ModeloInundacion
from app.motor.amenazas.sismo import ModeloSismo


def obtener_modelo(tipo: TipoEvento, config: ConfigAmenazas) -> ModeloAmenaza | None:
    """Devuelve el modelo del tipo de evento, o None si no hay modelo para estimar sin reportes.

    Sin modelo, el impacto inicial es 0 y la celda se estima solo con la evidencia que llega.
    """
    if tipo is TipoEvento.sismo:
        return ModeloSismo(config.sismo)
    if tipo is TipoEvento.inundacion:
        return ModeloInundacion()
    return None
