"""Modelo de inundación (pendiente).

Idea para implementarlo: fracción de daño según la profundidad de agua estimada en la celda
(cota del terreno vs. nivel de crecida) con curvas profundidad–daño por tipo de construcción.
"""

from __future__ import annotations

from app.modelos.enums import TipoEvento
from app.motor.tipos import DatosCelda, DatosEvento


class ModeloInundacion:
    tipo = TipoEvento.inundacion

    def fraccion_dano(self, evento: DatosEvento, celda: DatosCelda) -> float:
        raise NotImplementedError("El modelo de inundación aún no está implementado")
