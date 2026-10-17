"""Rutas sobre el grafo real de la ciudad activa. Se salta si no se ejecutó
preparacion/descargar_osm.py (el archivo no se guarda en el repositorio)."""

import pytest

from app.ciudad import directorio_ciudad, obtener_ciudad
from app.config import obtener_config
from app.modelos.enums import AlcanceBloqueo, EstadoBloqueo
from app.rutas.bloqueos import BloqueoEnRed, Modo, Penalizaciones
from app.rutas.calculo import calcular_ruta
from app.rutas.grafo import GrafoCalles

CONFIG = obtener_config()
ARCHIVO = directorio_ciudad(CONFIG) / CONFIG.rutas.archivo_calles

pytestmark = pytest.mark.skipif(not ARCHIVO.exists(), reason="sin grafo de calles preparado")


@pytest.fixture(scope="module")
def red() -> GrafoCalles:
    cargada = GrafoCalles.cargar(ARCHIVO)
    assert cargada is not None
    return cargada


def test_ruta_real_cruzando_la_ciudad_y_desvio_por_bloqueo(red: GrafoCalles) -> None:
    ciudad = obtener_ciudad()
    d = ciudad.radio_km / 2 / 111
    desde = (ciudad.centro.lat - d, ciudad.centro.lon - d)
    hasta = (ciudad.centro.lat + d, ciudad.centro.lon + d)
    libre = Penalizaciones([], Modo.vehiculo, CONFIG.rutas)
    ruta = calcular_ruta(red, libre, desde, hasta, CONFIG.rutas)
    assert ruta is not None and ruta.distancia_m > 1000
    assert ruta.instrucciones[-1].texto == "Llegas al destino"

    # Cortar el tramo del medio de la ruta obliga a buscar otro camino
    medio = ruta.tramos[len(ruta.tramos) // 2]
    cortes = Penalizaciones(
        [
            BloqueoEnRed(
                tuple(red.tramos_de_la_calle(medio)), EstadoBloqueo.confirmado, AlcanceBloqueo.total
            )
        ],
        Modo.vehiculo,
        CONFIG.rutas,
    )
    desvio = calcular_ruta(red, cortes, desde, hasta, CONFIG.rutas)
    assert desvio is not None
    assert (medio[0], medio[1]) not in {(u, v) for u, v, _ in desvio.tramos}
    assert desvio.tiempo_s >= ruta.tiempo_s
