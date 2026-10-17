"""Fuente manual: un operador emite la alerta desde la web (pantalla A6)."""

from __future__ import annotations

from dataclasses import replace

from app.activacion.fuentes.base import NuevoEvento
from app.modelos.enums import FuenteEvento


class FuenteManual:
    fuente = FuenteEvento.manual

    def convertir(self, entrada: NuevoEvento) -> NuevoEvento:
        return replace(entrada, fuente=self.fuente)
