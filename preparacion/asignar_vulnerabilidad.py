"""Vulnerabilidad e infraestructura crítica por celda. Requiere internet una sola vez.

Uso (desde la raíz del repositorio, con el entorno del servidor, después de calcular_poblacion.py):
    servidor/.venv/Scripts/python preparacion/asignar_vulnerabilidad.py [--ciudad <id>] [--indice-oficial archivo.csv]

Fuente: OpenStreetMap (ODbL, vía Overpass).
    - Infraestructura crítica por celda: hospitales, clínicas, escuelas, bomberos, policía y
      albergues (se ve en el detalle de la zona y sirve para priorizar).
    - Índice de vulnerabilidad 0–1 aproximado (pesos en config/enlace.toml):
      densidad de población, distancia al hospital o clínica más cercano y asentamientos
      marcados como informales.

Si el país tiene un índice oficial (por ejemplo necesidades básicas insatisfechas del censo),
--indice-oficial acepta un CSV con columnas `id_h3,vulnerabilidad` (0–1) que reemplaza el
aproximado en esas celdas.

Genera servidor/datos/ciudades/<id>/vulnerabilidad.json. datos/semilla.py lo usa en lugar de la
vulnerabilidad sintética.
"""

from __future__ import annotations

import argparse
import csv
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
    distancia_mas_cercana_km,
    indice_vulnerabilidad,
    limites,
)

from app.ciudad import cargar_ciudad, directorio_ciudad  # noqa: E402
from app.config import obtener_config, resolver_ruta  # noqa: E402

# Categoría de ENLACE ← etiquetas de OSM
INFRAESTRUCTURA = {
    "hospital": '["amenity"="hospital"]',
    "clinica": '["amenity"~"^(clinic|doctors)$"]',
    "escuela": '["amenity"~"^(school|kindergarten|college|university)$"]',
    "bomberos": '["amenity"="fire_station"]',
    "policia": '["amenity"="police"]',
    "albergue": '["social_facility"="shelter"]',
}
# Asentamientos informales (precariedad), como se etiquetan en OSM
INFORMALES = ['["residential"="informal"]', '["informal"="yes"]', '["place"="informal_settlement"]']
SALUD = ("hospital", "clinica")


def _consulta(caja: str) -> str:
    partes = [f'nwr{filtro}({caja});' for filtro in INFRAESTRUCTURA.values()]
    partes += [f"nwr{filtro}({caja});" for filtro in INFORMALES]
    return "[out:json][timeout:180];\n(\n  " + "\n  ".join(partes) + "\n);\nout center tags qt;\n"


def _categoria(etiquetas: dict[str, str]) -> str | None:
    amenidad = etiquetas.get("amenity", "")
    if amenidad == "hospital":
        return "hospital"
    if amenidad in ("clinic", "doctors"):
        return "clinica"
    if amenidad in ("school", "kindergarten", "college", "university"):
        return "escuela"
    if amenidad == "fire_station":
        return "bomberos"
    if amenidad == "police":
        return "policia"
    if etiquetas.get("social_facility") == "shelter":
        return "albergue"
    if (
        etiquetas.get("residential") == "informal"
        or etiquetas.get("informal") == "yes"
        or etiquetas.get("place") == "informal_settlement"
    ):
        return "informal"
    return None


def _indice_oficial(ruta: Path | None) -> dict[str, float]:
    if ruta is None:
        return {}
    with ruta.open(encoding="utf-8") as archivo:
        return {fila["id_h3"]: float(fila["vulnerabilidad"]) for fila in csv.DictReader(archivo)}


def main() -> None:
    argumentos = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    argumentos.add_argument("--ciudad", help="id de la carpeta en datos/ciudades (por defecto, la activa)")
    argumentos.add_argument("--indice-oficial", type=Path, help="CSV id_h3,vulnerabilidad (0–1)")
    opciones = argumentos.parse_args()

    config = obtener_config()
    directorio = (
        resolver_ruta(config.ciudad.directorio) / opciones.ciudad
        if opciones.ciudad
        else directorio_ciudad(config)
    )
    ciudad = cargar_ciudad(directorio)
    resolucion = config.h3.resolucion
    celdas = celdas_de_la_ciudad(ciudad, resolucion)
    archivo_poblacion = directorio / config.preparacion.archivo_poblacion
    if not archivo_poblacion.exists():
        raise SystemExit(f"Falta {archivo_poblacion.name}: correr antes calcular_poblacion.py")
    poblacion = json.loads(archivo_poblacion.read_text(encoding="utf-8"))["celdas"]

    # Con margen: el hospital más cercano puede estar fuera del área de la demo
    caja = ",".join(f"{v:.5f}" for v in limites(ciudad, config.preparacion.margen_km))
    elementos = consultar_overpass(_consulta(caja), config, directorio / ".cache_datos")

    infraestructura: dict[str, dict[str, list[str]]] = {}
    salud: list[tuple[float, float]] = []
    informales: set[str] = set()
    for elemento in elementos:
        punto = centro_elemento(elemento)
        etiquetas = elemento.get("tags", {})
        categoria = _categoria(etiquetas)
        if punto is None or categoria is None:
            continue
        celda = h3.latlng_to_cell(punto[0], punto[1], resolucion)
        if categoria == "informal":
            informales.add(celda)
            continue
        if categoria in SALUD:
            salud.append(punto)
        nombre = etiquetas.get("name", "")
        infraestructura.setdefault(celda, {}).setdefault(categoria, []).append(nombre)

    oficial = _indice_oficial(opciones.indice_oficial)
    vulnerabilidad = config.preparacion.vulnerabilidad
    resultado = {}
    for id_h3 in celdas:
        valor, componentes = indice_vulnerabilidad(
            poblacion.get(id_h3, {}).get("noche", 0.0),
            distancia_mas_cercana_km(h3.cell_to_latlng(id_h3), salud),
            1.0 if id_h3 in informales else 0.0,
            vulnerabilidad,
        )
        if id_h3 in oficial:
            valor, componentes = round(oficial[id_h3], 3), {"indice_oficial": oficial[id_h3]}
        # En la celda: cuántos hay de cada tipo y sus nombres (para el detalle de la zona)
        lugares = infraestructura.get(id_h3, {})
        resultado[id_h3] = {
            "vulnerabilidad": valor,
            "componentes": componentes,
            "infraestructura": {
                categoria: {"cantidad": len(nombres), "nombres": sorted({n for n in nombres if n})[:5]}
                for categoria, nombres in sorted(lugares.items())
            },
        }

    salida = directorio / config.preparacion.archivo_vulnerabilidad
    salida.write_text(
        json.dumps(
            {
                "fuentes": ["© colaboradores de OpenStreetMap (ODbL) vía Overpass"]
                + ([f"Índice oficial: {opciones.indice_oficial.name}"] if oficial else []),
                "generado": datetime.now(UTC).isoformat(timespec="seconds"),
                "celdas": resultado,
            },
            ensure_ascii=False,
            indent=1,
        ),
        encoding="utf-8",
    )
    totales = {
        categoria: sum(c["infraestructura"].get(categoria, {}).get("cantidad", 0) for c in resultado.values())
        for categoria in INFRAESTRUCTURA
    }
    valores = sorted(c["vulnerabilidad"] for c in resultado.values())
    print(f"Infraestructura crítica en el área: {totales}")
    print(f"Centros de salud considerados (con margen): {len(salud)}; celdas con asentamientos informales: {len(informales & set(celdas))}")
    print(
        f"Vulnerabilidad: mínima {valores[0]}, mediana {valores[len(valores) // 2]}, máxima {valores[-1]}"
        + (f"; {len(oficial)} celdas con índice oficial" if oficial else "")
    )
    print(f"→ {salida}")


if __name__ == "__main__":
    main()
