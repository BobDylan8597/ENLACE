"""Carga la ciudad activa: celdas (población, vulnerabilidad, infraestructura) y equipos.

Si existen poblacion.json y vulnerabilidad.json (preparacion/calcular_poblacion.py y
asignar_vulnerabilidad.py, con datos reales de Kontur y OpenStreetMap), se usan. Si no, la
población y la vulnerabilidad se generan sintéticas con los parámetros de semilla.toml.

Uso (desde servidor/):
    python datos/semilla.py              # agrega lo que falte
    python datos/semilla.py --reiniciar  # borra la base y la vuelve a crear

Lee `ciudad.toml` y `semilla.toml` de la carpeta de la ciudad activa. No contiene nada propio de
ninguna ciudad: para otra ciudad basta con crear su carpeta con esos dos archivos.
"""

from __future__ import annotations

import argparse
import json
import math
import random
import tomllib
from pathlib import Path
from typing import Any

import h3
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.ciudad import Ciudad, cargar_ciudad, directorio_ciudad
from app.config import obtener_config, resolver_ruta
from app.db import Base, crear_tablas, fabrica_sesiones, obtener_motor
from app.modelos.tablas import Celda, Equipo
from app.motor.geo import celdas_en_radio, distancia_km


def _celdas_excluidas(zonas: list[dict[str, Any]], resolucion: int) -> set[str]:
    excluidas: set[str] = set()
    for zona in zonas:
        poligono = h3.LatLngPoly([(lat, lon) for lat, lon in zona["contorno"]])
        excluidas.update(h3.h3shape_to_cells(poligono, resolucion))
    return excluidas


def _celdas_del_area(ciudad: Ciudad, semilla: dict[str, Any], resolucion: int) -> list[str]:
    excluidas = _celdas_excluidas(semilla.get("zonas_sin_poblacion", []), resolucion)
    return [
        c
        for c in celdas_en_radio(ciudad.centro.lat, ciudad.centro.lon, ciudad.radio_km, resolucion)
        if c not in excluidas
    ]


def _tipo_zona(proporcion: float, zonas: dict[str, Any], azar: random.Random) -> str:
    if (
        proporcion <= zonas["radio_centro"]
        and azar.random() < zonas["probabilidad_comercial_centro"]
    ):
        return "comercial"
    return "mixta" if azar.random() < zonas["probabilidad_mixta"] else "residencial"


def _poblacion_por_hora(noche: float, tipo: str, parametros: dict[str, Any]) -> list[float]:
    dia = parametros["factor_dia"][tipo]
    de_noche = parametros["factor_noche"][tipo]
    inicio, fin = parametros["hora_inicio_dia"], parametros["hora_fin_dia"]
    return [round(noche * (dia if inicio <= hora < fin else de_noche), 1) for hora in range(24)]


def _construccion(base: dict[str, float], azar: random.Random) -> dict[str, float]:
    variada = {tipo: peso * azar.uniform(0.6, 1.4) for tipo, peso in base.items()}
    total = sum(variada.values())
    return {tipo: round(peso / total, 3) for tipo, peso in variada.items()}


def _nombre_zona(lat: float, lon: float, barrios: list[dict[str, Any]], indice: int) -> str:
    if not barrios:
        return f"Sector {indice + 1}"
    return min(barrios, key=lambda b: distancia_km(lat, lon, b["lat"], b["lon"]))["nombre"]


def leer_datos_reales(directorio: Path, archivo: str) -> dict[str, dict[str, Any]] | None:
    """Celdas de un archivo de preparacion/, o None si todavía no se preparó la ciudad."""
    ruta = directorio / archivo
    if not ruta.exists():
        return None
    return dict(json.loads(ruta.read_text(encoding="utf-8"))["celdas"])


def generar_celdas(
    ciudad: Ciudad,
    semilla: dict[str, Any],
    resolucion: int,
    poblacion_real: dict[str, dict[str, Any]] | None = None,
    vulnerabilidad_real: dict[str, dict[str, Any]] | None = None,
) -> list[Celda]:
    azar = random.Random(semilla["semilla_aleatoria"])
    poblacion = semilla["poblacion"]
    zonas = semilla["zonas"]
    celdas = []
    for indice, id_h3 in enumerate(_celdas_del_area(ciudad, semilla, resolucion)):
        lat, lon = h3.cell_to_latlng(id_h3)
        proporcion = distancia_km(ciudad.centro.lat, ciudad.centro.lon, lat, lon) / ciudad.radio_km
        tipo = _tipo_zona(proporcion, zonas, azar)
        noche = (
            poblacion["noche_max"]
            * (1 - poblacion["caida_al_borde"] * proporcion)
            * azar.uniform(1 - poblacion["variacion"], 1 + poblacion["variacion"])
        )
        minimo, maximo = zonas["vulnerabilidad"][tipo]
        vulnerabilidad = round(azar.uniform(minimo, maximo), 2)
        infraestructura: dict[str, Any] = {
            nombre: 1
            for nombre, probabilidad in semilla["infraestructura"].items()
            if azar.random() < probabilidad
        }
        nombre = _nombre_zona(lat, lon, semilla.get("barrios", []), indice)
        # Datos reales (preparacion/): reemplazan a los sintéticos de esta celda
        if poblacion_real is not None:
            real = poblacion_real.get(id_h3, {"noche": 0.0, "tipo": "residencial", "nombre": None})
            noche, tipo = real["noche"], real["tipo"]
            nombre = real.get("nombre") or nombre
        if vulnerabilidad_real is not None and id_h3 in vulnerabilidad_real:
            vulnerabilidad = vulnerabilidad_real[id_h3]["vulnerabilidad"]
            infraestructura = vulnerabilidad_real[id_h3]["infraestructura"]
        celdas.append(
            Celda(
                id_h3=id_h3,
                nombre_zona=nombre,
                poblacion_por_hora=_poblacion_por_hora(noche, tipo, poblacion),
                vulnerabilidad=vulnerabilidad,
                infraestructura_critica=infraestructura,
                tipos_construccion=_construccion(zonas["construccion"][tipo], azar),
            )
        )
    return celdas


def cargar_semilla(
    sesion: Session,
    ciudad: Ciudad,
    semilla: dict[str, Any],
    resolucion: int,
    poblacion_real: dict[str, dict[str, Any]] | None = None,
    vulnerabilidad_real: dict[str, dict[str, Any]] | None = None,
) -> tuple[int, int]:
    existentes = set(sesion.scalars(select(Celda.id_h3)))
    generadas = generar_celdas(ciudad, semilla, resolucion, poblacion_real, vulnerabilidad_real)
    nuevas = [c for c in generadas if c.id_h3 not in existentes]
    sesion.add_all(nuevas)
    nombres = set(sesion.scalars(select(Equipo.nombre)))
    equipos = [
        Equipo(
            nombre=e["nombre"],
            organismo=e["organismo"],
            capacidades=list(e["capacidades"]),
            lat=e["lat"],
            lon=e["lon"],
        )
        for e in semilla.get("equipos", [])
        if e["nombre"] not in nombres
    ]
    sesion.add_all(equipos)
    sesion.commit()
    return len(nuevas), len(equipos)


def main() -> None:
    argumentos = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    argumentos.add_argument("--reiniciar", action="store_true", help="borra la base de datos")
    reiniciar = argumentos.parse_args().reiniciar

    config = obtener_config()
    directorio = directorio_ciudad(config)
    ciudad = cargar_ciudad(directorio)
    with Path(directorio / "semilla.toml").open("rb") as archivo:
        semilla = tomllib.load(archivo)

    motor = obtener_motor()
    if reiniciar:
        import app.modelos.tablas  # noqa: F401

        Base.metadata.drop_all(motor)
    crear_tablas(motor)
    poblacion_real = leer_datos_reales(directorio, config.preparacion.archivo_poblacion)
    vulnerabilidad_real = leer_datos_reales(directorio, config.preparacion.archivo_vulnerabilidad)
    print(
        "Población: "
        + ("real (Kontur + OSM)" if poblacion_real else "sintética (semilla.toml)")
        + " · vulnerabilidad: "
        + ("real (OSM)" if vulnerabilidad_real else "sintética (semilla.toml)")
    )
    with fabrica_sesiones()() as sesion:
        celdas, equipos = cargar_semilla(
            sesion, ciudad, semilla, config.h3.resolucion, poblacion_real, vulnerabilidad_real
        )
        total = len(list(sesion.scalars(select(Celda.id_h3))))
    print(f"{ciudad.nombre} ({ciudad.pais}): {celdas} celdas y {equipos} equipos nuevos")
    print(f"Base de datos: {resolver_ruta(config.servidor.base_datos)} ({total} celdas)")


if __name__ == "__main__":
    main()
