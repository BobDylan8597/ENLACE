"""Funciones comunes de los scripts de preparación: descargas (Overpass, HDX) y reglas puras.

Las reglas puras (tipo de zona, índice de vulnerabilidad, conteos por celda) no tocan la red y se
prueban en servidor/tests/test_preparacion.py.
"""

from __future__ import annotations

import gzip
import hashlib
import json
import math
import shutil
import sqlite3
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from collections.abc import Iterable
from pathlib import Path
from typing import Any

import h3

from app.ciudad import Ciudad
from app.config import ConfigVulnerabilidadPrep, Configuracion
from app.motor.geo import celdas_en_radio, distancia_km

AGENTE = "ENLACE-preparacion/0.1 (Rally Latinoamericano de Innovación)"
ESPERA_OVERPASS_S = 180
RONDAS_OVERPASS = 3
ESPERA_ENTRE_RONDAS_S = 60

Punto = tuple[float, float]


# ── Reglas puras ───────────────────────────────────────────


def tipos_de_zona(
    comercios: dict[str, int], fraccion_comercial: float, fraccion_mixta: float
) -> dict[str, str]:
    """Las celdas con más comercios, oficinas y servicios se llenan de día y se vacían de noche.
    Es relativo a la ciudad (OSM está más o menos completo según el lugar); sin comercios, siempre
    residencial."""
    orden = sorted(comercios, key=lambda c: comercios[c], reverse=True)
    n_comercial = round(len(orden) * fraccion_comercial)
    n_mixta = round(len(orden) * fraccion_mixta)
    tipos = {}
    for posicion, celda in enumerate(orden):
        if comercios[celda] == 0:
            tipos[celda] = "residencial"
        elif posicion < n_comercial:
            tipos[celda] = "comercial"
        elif posicion < n_comercial + n_mixta:
            tipos[celda] = "mixta"
        else:
            tipos[celda] = "residencial"
    return tipos


def contar_por_celda(puntos: Iterable[Punto], resolucion: int) -> Counter[str]:
    return Counter(h3.latlng_to_cell(lat, lon, resolucion) for lat, lon in puntos)


def distancia_mas_cercana_km(origen: Punto, destinos: list[Punto]) -> float | None:
    if not destinos:
        return None
    return min(distancia_km(origen[0], origen[1], lat, lon) for lat, lon in destinos)


def indice_vulnerabilidad(
    poblacion_noche: float,
    distancia_salud_km: float | None,
    precariedad: float,
    config: ConfigVulnerabilidadPrep,
) -> tuple[float, dict[str, float]]:
    """Índice 0–1 y sus componentes (para explicar el valor):
    - densidad: más gente en la celda, más difícil evacuar y atender (crece y se satura);
    - acceso a salud: lejos del hospital o clínica más cercano (sin ninguno en la zona, 1);
    - precariedad: fracción de la celda en asentamientos marcados como informales en OSM.
    """
    densidad = 1 - math.exp(-max(poblacion_noche, 0.0) / config.escala_densidad)
    acceso = (
        1.0
        if distancia_salud_km is None
        else min(distancia_salud_km / config.distancia_salud_max_km, 1.0)
    )
    componentes = {
        "densidad": round(densidad, 3),
        "acceso_salud": round(acceso, 3),
        "precariedad": round(min(max(precariedad, 0.0), 1.0), 3),
    }
    valor = (
        config.peso_densidad * componentes["densidad"]
        + config.peso_acceso_salud * componentes["acceso_salud"]
        + config.peso_precariedad * componentes["precariedad"]
    )
    return round(valor, 3), componentes


def nombre_mas_cercano(punto: Punto, lugares: list[tuple[str, Punto]], maximo_km: float) -> str | None:
    """Barrio o colonia de OSM más cercano al centro de la celda, si está a menos de maximo_km."""
    candidatos = [
        (distancia_km(punto[0], punto[1], lat, lon), nombre) for nombre, (lat, lon) in lugares
    ]
    cercano = min(candidatos, default=None)
    return cercano[1] if cercano and cercano[0] <= maximo_km else None


# ── Área de la ciudad ──────────────────────────────────────


def limites(ciudad: Ciudad, margen_km: float) -> tuple[float, float, float, float]:
    """(sur, oeste, norte, este): el orden que usa Overpass."""
    radio = ciudad.radio_km + margen_km
    d_lat = radio / 111.32
    d_lon = radio / (111.32 * math.cos(math.radians(ciudad.centro.lat)))
    return (
        ciudad.centro.lat - d_lat,
        ciudad.centro.lon - d_lon,
        ciudad.centro.lat + d_lat,
        ciudad.centro.lon + d_lon,
    )


def celdas_de_la_ciudad(ciudad: Ciudad, resolucion: int) -> list[str]:
    return celdas_en_radio(ciudad.centro.lat, ciudad.centro.lon, ciudad.radio_km, resolucion)


# ── Descargas ──────────────────────────────────────────────


def _leer_url(url: str, datos: bytes | None = None, espera_s: int = 60) -> bytes:
    pedido = urllib.request.Request(url, data=datos, headers={"User-Agent": AGENTE})
    with urllib.request.urlopen(pedido, timeout=espera_s) as respuesta:
        return respuesta.read()


def consultar_overpass(consulta: str, config: Configuracion, cache: Path) -> list[dict[str, Any]]:
    """Ejecuta una consulta Overpass QL. La respuesta se guarda en cache: volver a correr el
    script no repite consultas (los servidores públicos limitan cuántas se hacen seguidas).
    Si un servidor falla o está saturado, prueba el siguiente; tras una ronda, espera y reintenta."""
    cache.mkdir(parents=True, exist_ok=True)
    guardada = cache / f"overpass_{hashlib.sha256(consulta.encode()).hexdigest()[:16]}.json"
    if guardada.exists():
        return list(json.loads(guardada.read_text(encoding="utf-8")))
    cuerpo = urllib.parse.urlencode({"data": consulta}).encode()
    for ronda in range(RONDAS_OVERPASS):
        if ronda:
            print(f"Overpass: servidores ocupados, esperando {ESPERA_ENTRE_RONDAS_S} s…")
            time.sleep(ESPERA_ENTRE_RONDAS_S)
        for servidor in config.preparacion.servidores_overpass:
            try:
                respuesta = _leer_url(f"{servidor}/interpreter", cuerpo, ESPERA_OVERPASS_S + 30)
                elementos = list(json.loads(respuesta)["elements"])
            except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, KeyError) as error:
                print(f"Overpass: {servidor} no respondió ({error}), probando otro…")
                continue
            guardada.write_text(json.dumps(elementos), encoding="utf-8")
            return elementos
    raise SystemExit("Ningún servidor Overpass respondió; reintentar más tarde")


def centro_elemento(elemento: dict[str, Any]) -> Punto | None:
    """Nodos traen lat/lon; vías y relaciones, 'center' (con `out center`)."""
    if "lat" in elemento:
        return float(elemento["lat"]), float(elemento["lon"])
    centro = elemento.get("center")
    return (float(centro["lat"]), float(centro["lon"])) if centro else None


def descargar_poblacion(codigo_pais: str, config: Configuracion, cache: Path) -> Path:
    """GeoPackage de Kontur Population del país (el más reciente), descomprimido en cache."""
    cache.mkdir(parents=True, exist_ok=True)
    destino = cache / f"kontur_{codigo_pais}.gpkg"
    if destino.exists():
        return destino
    busqueda = config.preparacion.url_busqueda_poblacion.format(pais=codigo_pais.upper())
    recursos = json.loads(_leer_url(busqueda))["result"]["results"]
    gpkg = sorted(
        (r for r in recursos if r["name"].endswith(".gpkg")), key=lambda r: str(r["name"])
    )
    if not gpkg:
        raise SystemExit(f"No hay datos de población de Kontur para el país {codigo_pais}")
    ultimo = gpkg[-1]
    print(f"Población: descargando {ultimo['name']}…")
    comprimido = cache / f"kontur_{codigo_pais}.gpkg.gz"
    comprimido.write_bytes(_leer_url(ultimo["url"], espera_s=300))
    with gzip.open(comprimido) as entrada, destino.open("wb") as salida:
        shutil.copyfileobj(entrada, salida)
    comprimido.unlink()
    return destino


def leer_poblacion(gpkg: Path, celdas: set[str]) -> dict[str, float]:
    """Personas por celda H3 (res. 8, la misma de ENLACE) solo para las celdas pedidas."""
    with sqlite3.connect(gpkg) as conexion:
        tabla = conexion.execute("SELECT table_name FROM gpkg_contents").fetchone()[0]
        filas = conexion.execute(f'SELECT h3, population FROM "{tabla}"').fetchall()
    return {id_h3: float(personas) for id_h3, personas in filas if id_h3 in celdas}
