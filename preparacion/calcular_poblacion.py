"""Población real por celda y tipo de zona (día/noche). Requiere internet una sola vez.

Uso (desde la raíz del repositorio, con el entorno del servidor):
    servidor/.venv/Scripts/python preparacion/calcular_poblacion.py [--ciudad <id>]

Fuentes:
    Kontur Population (CC BY 4.0, HDX): personas por hexágono H3 de resolución 8, la misma de
    ENLACE, sin ninguna conversión. Se busca el archivo del país por `codigo_pais` (ciudad.toml).
    OpenStreetMap (ODbL, Overpass): comercios, oficinas y servicios por celda (tipo de zona, que
    define cuánta gente hay de día y de noche) y nombres de barrios y colonias.

Genera servidor/datos/ciudades/<id>/poblacion.json. datos/semilla.py lo usa en lugar de la
población sintética.
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import UTC, datetime
from pathlib import Path

import h3

sys.path.insert(0, str(Path(__file__).resolve().parent))

from datos_ciudad import (  # noqa: E402
    celdas_de_la_ciudad,
    centro_elemento,
    consultar_overpass,
    contar_por_celda,
    descargar_poblacion,
    leer_poblacion,
    limites,
    nombre_mas_cercano,
    tipos_de_zona,
)

from app.ciudad import cargar_ciudad, directorio_ciudad  # noqa: E402
from app.config import obtener_config, resolver_ruta  # noqa: E402

# Un barrio de OSM nombra la celda si su punto está a menos de esto del centro de la celda
DISTANCIA_NOMBRE_KM = 1.2

CONSULTA_COMERCIOS = """
[out:json][timeout:180];
(
  nwr["shop"]({caja});
  nwr["office"]({caja});
  nwr["amenity"~"^(restaurant|cafe|fast_food|bank|marketplace|pharmacy|bar|fuel|cinema|theatre|bus_station)$"]({caja});
);
out center qt;
"""

CONSULTA_BARRIOS = """
[out:json][timeout:120];
nwr["place"~"^(suburb|neighbourhood|quarter)$"]["name"]({caja});
out center qt;
"""


def main() -> None:
    argumentos = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    argumentos.add_argument("--ciudad", help="id de la carpeta en datos/ciudades (por defecto, la activa)")
    elegida = argumentos.parse_args().ciudad

    config = obtener_config()
    directorio = (
        resolver_ruta(config.ciudad.directorio) / elegida if elegida else directorio_ciudad(config)
    )
    ciudad = cargar_ciudad(directorio)
    resolucion = config.h3.resolucion
    celdas = celdas_de_la_ciudad(ciudad, resolucion)
    print(f"{ciudad.nombre} ({ciudad.pais}): {len(celdas)} celdas de resolución {resolucion}")

    cache = directorio / ".cache_datos"
    gpkg = descargar_poblacion(ciudad.codigo_pais, config, cache)
    poblacion = leer_poblacion(gpkg, set(celdas))
    print(f"Población: {sum(poblacion.values()):,.0f} personas en {len(poblacion)} celdas habitadas")

    caja = ",".join(f"{v:.5f}" for v in limites(ciudad, 0.0))
    comercios_osm = consultar_overpass(CONSULTA_COMERCIOS.format(caja=caja), config, cache)
    puntos = [p for p in map(centro_elemento, comercios_osm) if p is not None]
    comercios = contar_por_celda(puntos, resolucion)
    print(f"Comercios, oficinas y servicios (OSM): {len(puntos)}")

    barrios_osm = consultar_overpass(CONSULTA_BARRIOS.format(caja=caja), config, cache)
    barrios = [
        (e["tags"]["name"], punto)
        for e in barrios_osm
        if (punto := centro_elemento(e)) is not None
    ]
    print(f"Barrios y colonias con nombre (OSM): {len(barrios)}")

    p = config.preparacion
    por_celda = {id_h3: comercios.get(id_h3, 0) for id_h3 in celdas}
    tipos = tipos_de_zona(por_celda, p.fraccion_zona_comercial, p.fraccion_zona_mixta)
    resultado = {}
    for id_h3 in celdas:
        centro = h3.cell_to_latlng(id_h3)
        resultado[id_h3] = {
            "noche": poblacion.get(id_h3, 0.0),
            "comercios": por_celda[id_h3],
            "tipo": tipos[id_h3],
            "nombre": nombre_mas_cercano(centro, barrios, DISTANCIA_NOMBRE_KM),
        }

    salida = directorio / p.archivo_poblacion
    salida.write_text(
        json.dumps(
            {
                "fuentes": [
                    "Kontur Population (CC BY 4.0) vía HDX: " + gpkg.name,
                    "© colaboradores de OpenStreetMap (ODbL) vía Overpass",
                ],
                "generado": datetime.now(UTC).isoformat(timespec="seconds"),
                "celdas": resultado,
            },
            ensure_ascii=False,
            indent=1,
        ),
        encoding="utf-8",
    )
    conteo = {t: list(tipos.values()).count(t) for t in ("comercial", "mixta", "residencial")}
    nombradas = sum(1 for c in resultado.values() if c["nombre"])
    print(f"Tipos de zona: {conteo}; celdas con nombre de barrio de OSM: {nombradas}/{len(resultado)}")
    print(f"→ {salida}")


if __name__ == "__main__":
    main()
