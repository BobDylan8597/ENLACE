"""Ruta más rápida evitando bloqueos, con instrucciones paso a paso, y estimador de tiempos."""

from __future__ import annotations

import math
from collections import OrderedDict
from dataclasses import dataclass, field
from typing import Any

import networkx as nx

from app.config import ConfigAsignacion, ConfigRutas
from app.motor.asignacion import EstimadorLineaRecta
from app.rutas.bloqueos import Penalizaciones
from app.rutas.grafo import GrafoCalles, Tramo

SIN_NOMBRE = "una calle sin nombre"
# Cambio de rumbo (grados) a partir del cual se indica un giro
GIRO_LEVE = 30.0
GIRO_EN_U = 150.0
TAMANO_CACHE_ORIGENES = 64


@dataclass(frozen=True)
class Instruccion:
    texto: str
    distancia_m: float


@dataclass
class Ruta:
    coordenadas: list[tuple[float, float]]  # (lon, lat)
    tramos: list[Tramo]
    tiempo_s: float
    distancia_m: float
    instrucciones: list[Instruccion] = field(default_factory=list)

    @property
    def tiempo_min(self) -> float:
        return round(self.tiempo_s / 60, 1)

    def como_geojson(self, **propiedades: Any) -> dict[str, Any]:
        return {
            "type": "Feature",
            "geometry": {"type": "LineString", "coordinates": [list(c) for c in self.coordenadas]},
            "properties": {
                "tiempo_min": self.tiempo_min,
                "distancia_m": round(self.distancia_m),
                "instrucciones": [
                    {"texto": i.texto, "distancia_m": round(i.distancia_m)}
                    for i in self.instrucciones
                ],
                "tramos": [list(t) for t in self.tramos],
                **propiedades,
            },
        }


def tramos_de_geojson(ruta: dict[str, Any] | None) -> set[tuple[int, int]]:
    """Calles (u, v) que recorre una ruta guardada."""
    if not ruta:
        return set()
    return {(int(u), int(v)) for u, v, *_ in ruta.get("properties", {}).get("tramos", [])}


# ── Instrucciones ──────────────────────────────────────────


def _nombre(datos: dict[str, Any]) -> str:
    nombre = datos.get("name")
    if isinstance(nombre, list):
        nombre = nombre[0] if nombre else None
    return str(nombre) if nombre else SIN_NOMBRE


def _rumbo(desde: tuple[float, float], hasta: tuple[float, float]) -> float:
    """Rumbo en grados (0 = norte, 90 = este) entre dos puntos (lon, lat)."""
    lat1, lat2 = math.radians(desde[1]), math.radians(hasta[1])
    d_lon = math.radians(hasta[0] - desde[0])
    x = math.sin(d_lon) * math.cos(lat2)
    y = math.cos(lat1) * math.sin(lat2) - math.sin(lat1) * math.cos(lat2) * math.cos(d_lon)
    return math.degrees(math.atan2(x, y)) % 360


def _distancia_legible(metros: float) -> str:
    if metros < 100:
        return f"{max(10, round(metros / 10) * 10):.0f} m"
    if metros < 1000:
        return f"{round(metros / 50) * 50:.0f} m"
    return f"{metros / 1000:.1f} km".replace(".", ",")


def _giro(anterior: float, nuevo: float) -> str:
    cambio = (nuevo - anterior + 540) % 360 - 180  # −180..180, positivo = derecha
    if abs(cambio) < GIRO_LEVE:
        return "Continúa por"
    if abs(cambio) > GIRO_EN_U:
        return "Da la vuelta y toma"
    return "Gira a la derecha en" if cambio > 0 else "Gira a la izquierda en"


def instrucciones(segmentos: list[tuple[str, float, float, float]]) -> list[Instruccion]:
    """segmentos: (nombre de calle, metros, rumbo al entrar, rumbo al salir) de cada tramo."""
    grupos: list[list[Any]] = []
    for nombre, metros, entrada, salida in segmentos:
        if grupos and grupos[-1][0] == nombre:
            grupos[-1][1] += metros
            grupos[-1][3] = salida
        else:
            grupos.append([nombre, metros, entrada, salida])
    resultado: list[Instruccion] = []
    for indice, (nombre, metros, entrada, _) in enumerate(grupos):
        verbo = "Sal por" if indice == 0 else _giro(grupos[indice - 1][3], entrada)
        resultado.append(
            Instruccion(f"{verbo} {nombre} y sigue {_distancia_legible(metros)}", metros)
        )
    resultado.append(Instruccion("Llegas al destino", 0.0))
    return resultado


# ── Cálculo ────────────────────────────────────────────────


def calcular_ruta(
    red: GrafoCalles,
    penalizaciones: Penalizaciones,
    desde: tuple[float, float],
    hasta: tuple[float, float],
    config: ConfigRutas,
) -> Ruta | None:
    """desde / hasta: (lat, lon). None si no hay forma de llegar con los bloqueos actuales."""
    origen = red.nodo_cercano(*desde)
    destino = red.nodo_cercano(*hasta)
    if max(origen.distancia_m, destino.distancia_m) > config.distancia_maxima_a_la_red_m:
        return None
    peso = penalizaciones.funcion_peso(config.factor_velocidad_emergencia)
    try:
        nodos = nx.shortest_path(red.grafo, origen.nodo, destino.nodo, weight=peso)
    except nx.NetworkXNoPath:
        return None

    coordenadas: list[tuple[float, float]] = []
    tramos: list[Tramo] = []
    segmentos: list[tuple[str, float, float, float]] = []
    tiempo = distancia = 0.0
    for u, v in zip(nodos, nodos[1:]):
        aristas = red.grafo[u][v]
        k = min(aristas, key=lambda clave: float(aristas[clave]["travel_time"]))
        datos = aristas[k]
        tiempo += peso(u, v, {k: datos}) or 0.0
        metros = float(datos.get("length", 0.0))
        distancia += metros
        tramos.append((u, v, k))
        puntos = list(red.geometria(u, v, k, datos).coords)
        coordenadas.extend(puntos if not coordenadas else puntos[1:])
        segmentos.append(
            (_nombre(datos), metros, _rumbo(puntos[0], puntos[1]), _rumbo(puntos[-2], puntos[-1]))
        )
    if not coordenadas:  # origen y destino en el mismo cruce
        nodo = red.grafo.nodes[origen.nodo]
        coordenadas = [(nodo["x"], nodo["y"])]
    return Ruta(coordenadas, tramos, tiempo, distancia, instrucciones(segmentos))


class EstimadorRutas:
    """Tiempo de llegada por la red de calles con los bloqueos actuales.

    Calcula un Dijkstra desde cada origen hacia todos los cruces y lo guarda: con 8 equipos y
    decenas de pendientes son 8 cálculos, no cientos. La caché se invalida sola cuando cambian
    los bloqueos (cambia `penalizaciones.version`). Fuera de la red, usa la línea recta.
    """

    def __init__(
        self,
        red: GrafoCalles,
        penalizaciones: Penalizaciones,
        config: ConfigRutas,
        config_asignacion: ConfigAsignacion,
        cache: OrderedDict[tuple[int, int], dict[int, float]] | None = None,
    ) -> None:
        self._red = red
        self._penalizaciones = penalizaciones
        self._distancia_maxima = config.distancia_maxima_a_la_red_m
        self._peso = penalizaciones.funcion_peso(config.factor_velocidad_emergencia)
        self._linea_recta = EstimadorLineaRecta(config_asignacion)
        self._cache = cache if cache is not None else OrderedDict()

    def _tiempos_desde(self, nodo: int) -> dict[int, float]:
        clave = (self._penalizaciones.version, nodo)
        if clave in self._cache:
            self._cache.move_to_end(clave)
            return self._cache[clave]
        tiempos = nx.single_source_dijkstra_path_length(self._red.grafo, nodo, weight=self._peso)
        self._cache[clave] = tiempos
        while len(self._cache) > TAMANO_CACHE_ORIGENES:
            self._cache.popitem(last=False)
        return tiempos

    def minutos(self, desde: tuple[float, float], hasta: tuple[float, float]) -> float:
        origen = self._red.nodo_cercano(*desde)
        destino = self._red.nodo_cercano(*hasta)
        if max(origen.distancia_m, destino.distancia_m) > self._distancia_maxima:
            return self._linea_recta.minutos(desde, hasta)
        segundos = self._tiempos_desde(origen.nodo).get(destino.nodo)
        return math.inf if segundos is None else segundos / 60
