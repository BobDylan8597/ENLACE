"""Grafo de calles de la ciudad activa, cargado una sola vez en memoria.

Lo genera `preparacion/descargar_osm.py`. Si el archivo no existe el servidor sigue funcionando:
los tiempos de llegada se estiman en línea recta (ver `app/motor/asignacion.py`).
"""

from __future__ import annotations

import logging
import math
from dataclasses import dataclass
from pathlib import Path

import networkx as nx
import numpy as np
import shapely
from shapely import LineString, STRtree

log = logging.getLogger(__name__)

METROS_POR_GRADO_LAT = 110_540.0
METROS_POR_GRADO_LON_ECUADOR = 111_320.0

# Un tramo es la arista dirigida (u, v, key) del grafo de OSMnx
Tramo = tuple[int, int, int]


@dataclass(frozen=True)
class PuntoEnRed:
    nodo: int
    distancia_m: float


@dataclass(frozen=True)
class TramoCercano:
    tramo: Tramo
    distancia_m: float


class GrafoCalles:
    def __init__(self, grafo: nx.MultiDiGraph) -> None:
        self.grafo = grafo
        self._nodos = np.array(list(grafo.nodes), dtype=np.int64)
        latitudes = np.array([grafo.nodes[n]["y"] for n in self._nodos])
        longitudes = np.array([grafo.nodes[n]["x"] for n in self._nodos])
        # Proyección local equirectangular: suficiente para distancias de una ciudad
        self._lat_ref = float(latitudes.mean()) if len(latitudes) else 0.0
        self._m_por_grado_lon = METROS_POR_GRADO_LON_ECUADOR * math.cos(math.radians(self._lat_ref))
        self._xy = np.column_stack(
            (longitudes * self._m_por_grado_lon, latitudes * METROS_POR_GRADO_LAT)
        )
        self._tramos: list[Tramo] = []
        geometrias = []
        for u, v, k, datos in grafo.edges(keys=True, data=True):
            self._tramos.append((u, v, k))
            geometrias.append(self._proyectar(self.geometria(u, v, k, datos)))
        self._indice = STRtree(geometrias) if geometrias else None
        self._geometrias = geometrias

    @classmethod
    def cargar(cls, ruta: Path) -> GrafoCalles | None:
        if not ruta.exists():
            log.warning("No hay grafo de calles en %s: se usará la línea recta", ruta)
            return None
        import osmnx as ox

        return cls(ox.load_graphml(ruta))

    def _proyectar(self, linea: LineString) -> LineString:
        return shapely.transform(
            linea, lambda c: c * np.array([self._m_por_grado_lon, METROS_POR_GRADO_LAT])
        )

    def a_metros(self, lat: float, lon: float) -> tuple[float, float]:
        return lon * self._m_por_grado_lon, lat * METROS_POR_GRADO_LAT

    def geometria(self, u: int, v: int, k: int, datos: dict | None = None) -> LineString:
        """Forma del tramo en (lon, lat), orientada de u a v."""
        datos = datos if datos is not None else self.grafo.edges[u, v, k]
        geometria = datos.get("geometry")
        if geometria is not None:
            return geometria
        origen, destino = self.grafo.nodes[u], self.grafo.nodes[v]
        return LineString([(origen["x"], origen["y"]), (destino["x"], destino["y"])])

    def nodo_cercano(self, lat: float, lon: float) -> PuntoEnRed:
        x, y = self.a_metros(lat, lon)
        distancias = np.hypot(self._xy[:, 0] - x, self._xy[:, 1] - y)
        indice = int(np.argmin(distancias))
        return PuntoEnRed(int(self._nodos[indice]), float(distancias[indice]))

    def tramo_cercano(self, lat: float, lon: float) -> TramoCercano | None:
        if self._indice is None:
            return None
        punto = shapely.Point(self.a_metros(lat, lon))
        indice = int(self._indice.nearest(punto))
        return TramoCercano(self._tramos[indice], float(self._geometrias[indice].distance(punto)))

    def tramos_de_la_calle(self, tramo: Tramo) -> list[Tramo]:
        """El tramo y su sentido contrario, si existe: un bloqueo corta la calle en ambos sentidos."""
        u, v, _ = tramo
        inversos = [(v, u, k) for k in self.grafo[v].get(u, {})]
        return [tramo, *inversos]
