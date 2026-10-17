"""Prepara el mapa local de una ciudad. Requiere internet una sola vez; la demo corre sin él.

Uso (desde la raíz del repositorio, con el entorno del servidor):
    servidor/.venv/Scripts/python preparacion/descargar_osm.py [--ciudad <id>] [--solo-calles | --solo-mapa]

Genera en servidor/datos/ciudades/<id>/:
    calles.graphml   grafo de calles con tiempo de viaje por tramo (OSM, vía Overpass)
    mapa.pmtiles     mapa base vectorial de la zona, para la web y la app sin internet
                     (extracto de la compilación diaria de Protomaps)

Funciona para cualquier ciudad con carpeta en datos/ciudades/: lee su centro y radio de
ciudad.toml. Sin --ciudad usa la ciudad activa de config/enlace.toml.
"""

from __future__ import annotations

import argparse
import math
import time
import urllib.error
import urllib.request
from datetime import UTC, datetime, timedelta
from pathlib import Path

from app.ciudad import Ciudad, cargar_ciudad
from app.config import Configuracion, obtener_config, resolver_ruta

AGENTE = "ENLACE-preparacion/0.1 (Rally Latinoamericano de Innovación)"
REINTENTOS = 3


def _limites(ciudad: Ciudad, margen_km: float) -> tuple[float, float, float, float]:
    """(oeste, sur, este, norte) del área de la ciudad más el margen."""
    radio = ciudad.radio_km + margen_km
    d_lat = radio / 111.32
    d_lon = radio / (111.32 * math.cos(math.radians(ciudad.centro.lat)))
    return (
        ciudad.centro.lon - d_lon,
        ciudad.centro.lat - d_lat,
        ciudad.centro.lon + d_lon,
        ciudad.centro.lat + d_lat,
    )


# ── Calles ─────────────────────────────────────────────────


def descargar_calles(ciudad: Ciudad, config: Configuracion, destino: Path) -> None:
    import osmnx as ox

    ox.settings.cache_folder = str(destino.parent / ".cache_osm")
    ox.settings.http_user_agent = AGENTE
    distancia = (ciudad.radio_km + config.preparacion.margen_km) * 1000
    print(f"Calles: descargando {distancia / 1000:.1f} km alrededor de {ciudad.nombre}…")
    inicio = time.monotonic()
    for servidor in config.preparacion.servidores_overpass:
        ox.settings.overpass_url = servidor
        try:
            grafo = ox.graph_from_point(
                (ciudad.centro.lat, ciudad.centro.lon),
                dist=distancia,
                network_type=config.preparacion.tipo_red,
            )
            break
        except Exception as error:  # servidor caído o saturado: probar el siguiente
            print(f"Calles: {servidor} no respondió ({type(error).__name__}), probando otro…")
    else:
        raise SystemExit("Ningún servidor Overpass respondió; reintentar más tarde")
    grafo = ox.add_edge_speeds(grafo)
    grafo = ox.add_edge_travel_times(grafo)
    ox.save_graphml(grafo, destino)
    print(
        f"Calles: {len(grafo.nodes)} cruces, {len(grafo.edges)} tramos "
        f"({time.monotonic() - inicio:.0f} s) → {destino} ({destino.stat().st_size / 1e6:.1f} MB)"
    )


# ── Mapa base ──────────────────────────────────────────────


class FuenteHttp:
    """Lectura por rangos de un .pmtiles remoto, con caché (los directorios se repiten)."""

    def __init__(self, url: str) -> None:
        self.url = url
        self._cache: dict[tuple[int, int], bytes] = {}
        self.pedidos = 0

    def leer(self, desde: int, largo: int) -> bytes:
        clave = (desde, largo)
        if clave not in self._cache:
            self._cache[clave] = self._pedir(desde, largo)
        return self._cache[clave]

    def _pedir(self, desde: int, largo: int) -> bytes:
        pedido = urllib.request.Request(
            self.url,
            headers={
                "Range": f"bytes={desde}-{desde + largo - 1}",
                "User-Agent": AGENTE,
            },
        )
        for intento in range(REINTENTOS):
            try:
                with urllib.request.urlopen(pedido, timeout=60) as respuesta:
                    self.pedidos += 1
                    return respuesta.read()
            except (urllib.error.URLError, TimeoutError):
                if intento == REINTENTOS - 1:
                    raise
                time.sleep(2**intento)
        raise RuntimeError("inalcanzable")


def buscar_compilacion(config: Configuracion) -> str:
    """La compilación diaria de Protomaps más reciente que siga publicada."""
    hoy = datetime.now(UTC).date()
    for dias in range(config.preparacion.dias_busqueda_mapa):
        fecha = (hoy - timedelta(days=dias)).strftime("%Y%m%d")
        url = config.preparacion.url_mapa_base.format(fecha=fecha)
        pedido = urllib.request.Request(url, headers={"Range": "bytes=0-126", "User-Agent": AGENTE})
        try:
            with urllib.request.urlopen(pedido, timeout=30) as respuesta:
                if respuesta.status == 206:
                    return url
        except urllib.error.HTTPError:
            continue
    raise SystemExit("No se encontró una compilación reciente de Protomaps")


def _tesela(lon: float, lat: float, z: int) -> tuple[int, int]:
    n = 2**z
    x = int((lon + 180) / 360 * n)
    y = int((1 - math.asinh(math.tan(math.radians(lat))) / math.pi) / 2 * n)
    return min(max(x, 0), n - 1), min(max(y, 0), n - 1)


def descargar_mapa(ciudad: Ciudad, config: Configuracion, destino: Path) -> None:
    from pmtiles.reader import Reader
    from pmtiles.tile import zxy_to_tileid
    from pmtiles.writer import Writer

    url = buscar_compilacion(config)
    print(f"Mapa: extrayendo {ciudad.nombre} de {url}")
    fuente = FuenteHttp(url)
    lector = Reader(fuente.leer)
    cabecera = lector.header()
    metadatos = lector.metadata()
    oeste, sur, este, norte = _limites(ciudad, config.preparacion.margen_km)
    zoom_maximo = min(config.preparacion.zoom_maximo_mapa, cabecera["max_zoom"])

    teselas = []
    for z in range(cabecera["min_zoom"], zoom_maximo + 1):
        x0, y0 = _tesela(oeste, norte, z)
        x1, y1 = _tesela(este, sur, z)
        teselas += [
            (zxy_to_tileid(z, x, y), z, x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)
        ]
    teselas.sort()

    inicio = time.monotonic()
    temporal = destino.with_suffix(".parcial")
    escritas = 0
    with temporal.open("wb") as archivo:
        escritor = Writer(archivo)
        for indice, (id_tesela, z, x, y) in enumerate(teselas, 1):
            datos = lector.get(z, x, y)
            if datos:
                escritor.write_tile(id_tesela, datos)
                escritas += 1
            if indice % 50 == 0:
                print(f"  {indice}/{len(teselas)} teselas")
        salida = dict(cabecera)
        salida.update(
            min_lon_e7=int(oeste * 1e7),
            min_lat_e7=int(sur * 1e7),
            max_lon_e7=int(este * 1e7),
            max_lat_e7=int(norte * 1e7),
            center_lon_e7=int(ciudad.centro.lon * 1e7),
            center_lat_e7=int(ciudad.centro.lat * 1e7),
            center_zoom=min(13, zoom_maximo),
        )
        metadatos = dict(metadatos)
        metadatos["enlace"] = {"ciudad": ciudad.id, "origen": url}
        escritor.finalize(salida, metadatos)
    temporal.replace(destino)
    print(
        f"Mapa: {escritas} teselas, zoom {cabecera['min_zoom']}–{zoom_maximo} "
        f"({fuente.pedidos} pedidos, {time.monotonic() - inicio:.0f} s) → {destino} "
        f"({destino.stat().st_size / 1e6:.1f} MB)"
    )
    print("Atribución obligatoria en la interfaz: © OpenStreetMap · Protomaps")


def main() -> None:
    argumentos = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    argumentos.add_argument("--ciudad", help="id de la carpeta en datos/ciudades/")
    solo = argumentos.add_mutually_exclusive_group()
    solo.add_argument("--solo-calles", action="store_true")
    solo.add_argument("--solo-mapa", action="store_true")
    opciones = argumentos.parse_args()

    config = obtener_config()
    directorio = resolver_ruta(config.ciudad.directorio) / (opciones.ciudad or config.ciudad.activa)
    if not (directorio / "ciudad.toml").exists():
        raise SystemExit(f"No existe {directorio / 'ciudad.toml'}")
    ciudad = cargar_ciudad(directorio)
    print(f"Preparando {ciudad.nombre} ({ciudad.pais}) en {directorio}")
    if not opciones.solo_mapa:
        descargar_calles(ciudad, config, directorio / config.rutas.archivo_calles)
    if not opciones.solo_calles:
        descargar_mapa(ciudad, config, directorio / config.preparacion.archivo_mapa)


if __name__ == "__main__":
    main()
