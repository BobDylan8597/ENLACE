"""Red de calles mínima para las pruebas: cuadrícula de cruces (3 × 3 por defecto).

    fila 2   20 ── 21 ── 22      "Calle 2"   (las filas van de oeste a este)
              │     │     │
    fila 1   10 ── 11 ── 12      "Calle 1"
              │     │     │
    fila 0   00 ── 01 ── 02      "Calle 0"
           Av. 0  Av. 1  Av. 2   (las columnas van de sur a norte)

Cada cuadra se recorre en 30 s; todas las calles son de doble sentido.
"""

import networkx as nx

from app.motor.geo import distancia_km
from app.rutas.grafo import GrafoCalles

LAT0, LON0, PASO = 12.0, -86.0, 0.002
SEGUNDOS_POR_CUADRA = 30.0


def nodo(fila: int, columna: int) -> int:
    return fila * 10 + columna


def punto(
    fila: int, columna: int, lat0: float = LAT0, lon0: float = LON0, paso: float = PASO
) -> tuple[float, float]:
    """(lat, lon) del cruce."""
    return lat0 + fila * paso, lon0 + columna * paso


def red_cuadricula(
    filas: int = 3,
    columnas: int = 3,
    lat0: float = LAT0,
    lon0: float = LON0,
    paso: float = PASO,
) -> GrafoCalles:
    grafo = nx.MultiDiGraph(crs="epsg:4326")
    for fila in range(filas):
        for columna in range(columnas):
            lat, lon = punto(fila, columna, lat0, lon0, paso)
            grafo.add_node(nodo(fila, columna), y=lat, x=lon)

    def calle(a: tuple[int, int], b: tuple[int, int], nombre: str) -> None:
        metros = distancia_km(*punto(*a, lat0, lon0, paso), *punto(*b, lat0, lon0, paso)) * 1000
        for u, v in ((a, b), (b, a)):
            grafo.add_edge(
                nodo(*u), nodo(*v), length=metros, travel_time=SEGUNDOS_POR_CUADRA, name=nombre
            )

    for fila in range(filas):
        for columna in range(columnas - 1):
            calle((fila, columna), (fila, columna + 1), f"Calle {fila}")
    for columna in range(columnas):
        for fila in range(filas - 1):
            calle((fila, columna), (fila + 1, columna), f"Avenida {columna}")
    return GrafoCalles(grafo)
