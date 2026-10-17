"""Carga la ciudad activa con datos sintéticos de demostración: celdas y equipos.

Uso (desde servidor/):
    python datos/semilla.py              # agrega lo que falte
    python datos/semilla.py --reiniciar  # borra la base y la vuelve a crear

Lee `ciudad.toml` y `semilla.toml` de la carpeta de la ciudad activa. No contiene nada propio de
ninguna ciudad: para otra ciudad basta con crear su carpeta con esos dos archivos.
"""

from __future__ import annotations

import argparse
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
from app.motor.geo import distancia_km

# Distancia entre centros de celdas vecinas en resolución 8 (~0,8 km), para elegir el anillo h3
_PASO_KM_RES8 = 0.8


def _celdas_excluidas(zonas: list[dict[str, Any]], resolucion: int) -> set[str]:
    excluidas: set[str] = set()
    for zona in zonas:
        poligono = h3.LatLngPoly([(lat, lon) for lat, lon in zona["contorno"]])
        excluidas.update(h3.h3shape_to_cells(poligono, resolucion))
    return excluidas


def _celdas_del_area(ciudad: Ciudad, semilla: dict[str, Any], resolucion: int) -> list[str]:
    centro = h3.latlng_to_cell(ciudad.centro.lat, ciudad.centro.lon, resolucion)
    paso = _PASO_KM_RES8 * math.sqrt(7) ** (8 - resolucion)
    anillos = math.ceil(ciudad.radio_km / paso) + 1
    excluidas = _celdas_excluidas(semilla.get("zonas_sin_poblacion", []), resolucion)
    return sorted(
        c
        for c in h3.grid_disk(centro, anillos)
        if c not in excluidas
        and distancia_km(ciudad.centro.lat, ciudad.centro.lon, *h3.cell_to_latlng(c))
        <= ciudad.radio_km
    )


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


def generar_celdas(ciudad: Ciudad, semilla: dict[str, Any], resolucion: int) -> list[Celda]:
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
        infraestructura = {
            nombre: 1
            for nombre, probabilidad in semilla["infraestructura"].items()
            if azar.random() < probabilidad
        }
        celdas.append(
            Celda(
                id_h3=id_h3,
                nombre_zona=_nombre_zona(lat, lon, semilla.get("barrios", []), indice),
                poblacion_por_hora=_poblacion_por_hora(noche, tipo, poblacion),
                vulnerabilidad=round(azar.uniform(minimo, maximo), 2),
                infraestructura_critica=infraestructura,
                tipos_construccion=_construccion(zonas["construccion"][tipo], azar),
            )
        )
    return celdas


def cargar_semilla(
    sesion: Session, ciudad: Ciudad, semilla: dict[str, Any], resolucion: int
) -> tuple[int, int]:
    existentes = set(sesion.scalars(select(Celda.id_h3)))
    nuevas = [c for c in generar_celdas(ciudad, semilla, resolucion) if c.id_h3 not in existentes]
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
    with fabrica_sesiones()() as sesion:
        celdas, equipos = cargar_semilla(sesion, ciudad, semilla, config.h3.resolucion)
        total = len(list(sesion.scalars(select(Celda.id_h3))))
    print(f"{ciudad.nombre} ({ciudad.pais}): {celdas} celdas y {equipos} equipos nuevos")
    print(f"Base de datos: {resolver_ruta(config.servidor.base_datos)} ({total} celdas)")


if __name__ == "__main__":
    main()
