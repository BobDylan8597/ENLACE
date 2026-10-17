"""Bloqueos y su efecto en el tiempo de viaje (sección 10).

| Estado        | Cuándo                                         | Efecto en la calle      |
|---------------|------------------------------------------------|-------------------------|
| sin_confirmar | 1 persona lo reportó                           | tiempo × factor (3)     |
| probable      | `reportes_para_probable` personas distintas    | tiempo × factor (10)    |
| confirmado    | `reportes_para_confirmado` personas o brigada  | la calle se elimina     |
| descartado    | brigada u operador lo descarta                 | la calle se restaura    |

`solo_a_pie` elimina la calle solo para vehículos. Los pesos se aplican al calcular cada ruta,
sin copiar el grafo, así que un cambio de bloqueo tiene efecto inmediato.
"""

from __future__ import annotations

from collections.abc import Callable, Iterable
from dataclasses import dataclass
from enum import StrEnum
from typing import Any

from app.config import ConfigRutas
from app.modelos.enums import AlcanceBloqueo, EstadoBloqueo
from app.rutas.grafo import Tramo


class Modo(StrEnum):
    vehiculo = "vehiculo"
    a_pie = "a_pie"


# None = la calle no se puede usar
Factor = float | None


def estado_segun_reportes(
    personas: int, confirmado_por_brigada: bool, config: ConfigRutas
) -> EstadoBloqueo:
    if confirmado_por_brigada or personas >= config.reportes_para_confirmado:
        return EstadoBloqueo.confirmado
    if personas >= config.reportes_para_probable:
        return EstadoBloqueo.probable
    return EstadoBloqueo.sin_confirmar


def factor_bloqueo(
    estado: EstadoBloqueo, alcance: AlcanceBloqueo, modo: Modo, config: ConfigRutas
) -> Factor:
    if estado is EstadoBloqueo.descartado:
        return 1.0
    if alcance is AlcanceBloqueo.solo_a_pie:
        # Pasa una persona caminando, no un vehículo
        return None if modo is Modo.vehiculo else 1.0
    if estado is EstadoBloqueo.confirmado:
        return None
    if estado is EstadoBloqueo.probable:
        return config.factor_probable
    return config.factor_sin_confirmar


@dataclass(frozen=True)
class BloqueoEnRed:
    tramos: tuple[Tramo, ...]
    estado: EstadoBloqueo
    alcance: AlcanceBloqueo


class Penalizaciones:
    """Factor por tramo dirigido. Si varios bloqueos caen en el mismo tramo, gana el peor."""

    def __init__(self, bloqueos: Iterable[BloqueoEnRed], modo: Modo, config: ConfigRutas) -> None:
        self.factores: dict[tuple[int, int], Factor] = {}
        for bloqueo in bloqueos:
            factor = factor_bloqueo(bloqueo.estado, bloqueo.alcance, modo, config)
            if factor == 1.0:
                continue
            for u, v, _ in bloqueo.tramos:
                actual = self.factores.get((u, v), 1.0)
                self.factores[(u, v)] = (
                    None if factor is None or actual is None else max(actual, factor)
                )
        # Identifica el estado de la red: cambia cuando cambia algún bloqueo
        self.version = hash(frozenset(self.factores.items()))

    def funcion_peso(
        self, factor_velocidad: float
    ) -> Callable[[int, int, dict[int, dict[str, Any]]], float | None]:
        """Peso para networkx: segundos de viaje en emergencia, o None si la calle está cortada."""

        def peso(u: int, v: int, aristas: dict[int, dict[str, Any]]) -> float | None:
            factor = self.factores.get((u, v), 1.0)
            if factor is None:
                return None
            mejor = min(float(d["travel_time"]) for d in aristas.values())
            return mejor / factor_velocidad * factor

        return peso
