"""Recomendación de equipos: el motor propone, la persona coordinadora decide.

Se recorren los pendientes de mayor a menor prioridad y a cada uno se le propone el equipo
disponible, con la capacidad necesaria, que llega antes. Cada propuesta lleva un motivo legible.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol

from app.config import ConfigAsignacion
from app.modelos.enums import Capacidad, Necesidad
from app.motor.geo import distancia_km
from app.motor.tipos import DatosReporte


class EstimadorTiempo(Protocol):
    def minutos(self, desde: tuple[float, float], hasta: tuple[float, float]) -> float: ...


class EstimadorLineaRecta:
    """Paso 1: distancia en línea recta corregida por un factor de desvío. El paso 2 usa rutas."""

    def __init__(self, config: ConfigAsignacion) -> None:
        self._config = config

    def minutos(self, desde: tuple[float, float], hasta: tuple[float, float]) -> float:
        km = distancia_km(*desde, *hasta) * self._config.factor_desvio
        return km / self._config.velocidad_promedio_kmh * 60


@dataclass(frozen=True)
class Pendiente:
    """Algo que necesita un equipo: un reporte concreto o una celda a explorar."""

    id_h3: str
    nombre_zona: str
    prioridad: float
    capacidad: Capacidad
    descripcion: str
    destino: tuple[float, float]
    id_reporte: str | None = None

    @property
    def clave(self) -> str:
        return self.id_reporte or self.id_h3


@dataclass(frozen=True)
class EquipoDisponible:
    id: int
    nombre: str
    capacidades: frozenset[Capacidad]
    ubicacion: tuple[float, float]


@dataclass(frozen=True)
class Recomendacion:
    id_equipo: int
    id_reporte: str | None
    id_h3: str
    prioridad: float
    tiempo_estimado_min: float
    motivo: str


@dataclass(frozen=True)
class ResultadoAsignacion:
    recomendaciones: list[Recomendacion]
    # Pendientes para los que no quedó ningún equipo con la capacidad necesaria
    sin_cubrir: list[Pendiente]


def capacidad_requerida(reporte: DatosReporte, config: ConfigAsignacion) -> Capacidad:
    if reporte.atrapados > 0:
        return Capacidad.rescate
    if reporte.heridos > 0:
        return Capacidad.medico
    for necesidad in reporte.necesidades:
        try:
            return config.capacidad_por_necesidad[Necesidad(necesidad)]
        except ValueError:
            continue
    return Capacidad.reconocimiento


def describir_reporte(reporte: DatosReporte) -> str:
    partes = []
    if reporte.atrapados:
        partes.append(
            f"{reporte.atrapados} {'atrapado' if reporte.atrapados == 1 else 'atrapados'}"
        )
    if reporte.heridos:
        partes.append(f"{reporte.heridos} {'herido' if reporte.heridos == 1 else 'heridos'}")
    if reporte.vulnerables:
        partes.append(f"{reporte.vulnerables} en situación vulnerable")
    if not partes and reporte.personas:
        partes.append(f"{reporte.personas} {'persona' if reporte.personas == 1 else 'personas'}")
    if reporte.necesidades:
        partes.append("necesita " + ", ".join(reporte.necesidades))
    return "; ".join(partes) or "pedido de ayuda sin detalles"


def recomendar(
    pendientes: list[Pendiente],
    equipos: list[EquipoDisponible],
    estimador: EstimadorTiempo,
    config: ConfigAsignacion,
    vetados: frozenset[tuple[str, int]] = frozenset(),
) -> ResultadoAsignacion:
    """`vetados`: pares (clave del pendiente, id del equipo) que una persona ya rechazó."""
    libres = list(equipos)
    recomendaciones: list[Recomendacion] = []
    sin_cubrir: list[Pendiente] = []

    for pendiente in sorted(pendientes, key=lambda p: p.prioridad, reverse=True):
        if len(recomendaciones) >= config.maximo_recomendaciones:
            break
        aptos = [
            e
            for e in libres
            if pendiente.capacidad in e.capacidades and (pendiente.clave, e.id) not in vetados
        ]
        if not aptos:
            sin_cubrir.append(pendiente)
            continue
        tiempos = {e.id: estimador.minutos(e.ubicacion, pendiente.destino) for e in aptos}
        elegido = min(aptos, key=lambda e: (tiempos[e.id], e.id))
        libres.remove(elegido)
        minutos = tiempos[elegido.id]
        motivo = (
            f"Prioridad {pendiente.prioridad:.0f}/100 en {pendiente.nombre_zona}: "
            f"{pendiente.descripcion}. {elegido.nombre} ({pendiente.capacidad}) es el equipo "
            f"disponible más cercano: llega en unos {minutos:.0f} min."
        )
        recomendaciones.append(
            Recomendacion(
                id_equipo=elegido.id,
                id_reporte=pendiente.id_reporte,
                id_h3=pendiente.id_h3,
                prioridad=pendiente.prioridad,
                tiempo_estimado_min=round(minutos, 1),
                motivo=motivo,
            )
        )
    return ResultadoAsignacion(recomendaciones, sin_cubrir)
