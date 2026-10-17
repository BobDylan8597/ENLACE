"""Validación retrospectiva del modelo de intensidad de ENLACE con sismos reales.

Uso (desde la raíz del repositorio, con el entorno del servidor; requiere internet):
    servidor/.venv/Scripts/python preparacion/validar_evento.py
    servidor/.venv/Scripts/python preparacion/validar_evento.py --evento us2000ar20 --evento usd0003cqx:NI

Para cada sismo (id del catálogo del USGS) compara la intensidad que ENLACE estima desde el primer
minuto (solo con magnitud, epicentro y profundidad, el mismo modelo del motor) con lo observado:
    - "¿Lo sintió?" (DYFI, USGS): intensidad reportada por la gente, en celdas de 10 km;
    - estaciones sísmicas del ShakeMap (intensidad convertida de la aceleración medida).
Con `id:PAIS`, además compara la población expuesta a cada intensidad (Kontur Population del país)
con la estimación PAGER del USGS.

Escribe preparacion/resultados/validacion_sismos.md (para la presentación) y un .json con los
números. No cambia la configuración: si propone otros coeficientes, se revisan a mano.
"""

from __future__ import annotations

import argparse
import math
import json
import re
import sqlite3
import sys
import urllib.request
from dataclasses import asdict
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import h3

RAIZ = Path(__file__).resolve().parent
sys.path.insert(0, str(RAIZ))

from datos_ciudad import AGENTE, descargar_poblacion  # noqa: E402
from validacion import (  # noqa: E402
    ROMANOS,
    Metricas,
    Observacion,
    ajustar_a_y_c,
    exposicion,
    por_banda,
)

from app.config import obtener_config  # noqa: E402
from app.modelos.enums import TipoEvento  # noqa: E402
from app.motor.amenazas.sismo import ModeloSismo  # noqa: E402
from app.motor.geo import distancia_km  # noqa: E402
from app.motor.tipos import DatosEvento  # noqa: E402

URL_EVENTO = "https://earthquake.usgs.gov/fdsnws/event/1/query?eventid={id}&format=geojson"
CACHE = RAIZ / ".cache_validacion"
RESULTADOS = RAIZ / "resultados"
# Sismos por defecto: Centroamérica y México con muchas observaciones, y Managua (ciudad demo)
EVENTOS_POR_DEFECTO = ["usp000jrsw", "us2000ar20", "usd0003cqx:NI"]
# Una celda DYFI con menos respuestas que esto es poco confiable
RESPUESTAS_MINIMAS_DYFI = 3
# Desde esta intensidad empiezan los daños (VI: daños leves en construcciones vulnerables)
GRADO_FUERTE = 6


def _descargar(url: str, nombre: str) -> bytes:
    """Con caché: volver a correr la validación no repite descargas."""
    CACHE.mkdir(exist_ok=True)
    archivo = CACHE / nombre
    if not archivo.exists():
        pedido = urllib.request.Request(url, headers={"User-Agent": AGENTE})
        with urllib.request.urlopen(pedido, timeout=120) as respuesta:
            archivo.write_bytes(respuesta.read())
    return archivo.read_bytes()


def _json(url: str, nombre: str) -> Any:
    return json.loads(_descargar(url, nombre))


def _centro_poligono(geometria: dict[str, Any]) -> tuple[float, float]:
    anillo = geometria["coordinates"][0]
    return (
        sum(p[1] for p in anillo) / len(anillo),
        sum(p[0] for p in anillo) / len(anillo),
    )


def observaciones_del_evento(
    id_evento: str, modelo: ModeloSismo
) -> tuple[dict[str, Any], DatosEvento, list[Observacion]]:
    detalle = _json(URL_EVENTO.format(id=id_evento), f"{id_evento}.json")
    lon, lat, profundidad = detalle["geometry"]["coordinates"]
    propiedades = detalle["properties"]
    evento = DatosEvento(
        tipo=TipoEvento.sismo,
        lat=lat,
        lon=lon,
        hora=datetime.fromtimestamp(propiedades["time"] / 1000, UTC),
        magnitud=float(propiedades["mag"]),
        profundidad_km=float(profundidad),
    )
    productos = propiedades["products"]

    def hipocentral(p_lat: float, p_lon: float) -> float:
        return (distancia_km(lat, lon, p_lat, p_lon) ** 2 + profundidad**2) ** 0.5

    observaciones: list[Observacion] = []
    if "dyfi" in productos:
        contenido = productos["dyfi"][0]["contents"].get("dyfi_geo_10km.geojson")
        if contenido:
            for celda in _json(contenido["url"], f"{id_evento}_dyfi10.json")["features"]:
                p = celda["properties"]
                if p.get("nresp", 0) < RESPUESTAS_MINIMAS_DYFI or p.get("cdi") is None:
                    continue
                c_lat, c_lon = _centro_poligono(celda["geometry"])
                observaciones.append(
                    Observacion(
                        "dyfi",
                        hipocentral(c_lat, c_lon),
                        evento.magnitud or 0.0,
                        float(p["cdi"]),
                        modelo.intensidad(evento, c_lat, c_lon),
                    )
                )
    if "shakemap" in productos:
        contenido = productos["shakemap"][0]["contents"].get("download/stationlist.json")
        if contenido:
            for estacion in _json(contenido["url"], f"{id_evento}_estaciones.json")["features"]:
                p = estacion["properties"]
                intensidad = p.get("intensity")
                if p.get("station_type") != "seismic" or intensidad in (None, "null") or p.get("intensity_flag"):
                    continue
                e_lon, e_lat = estacion["geometry"]["coordinates"][:2]
                observaciones.append(
                    Observacion(
                        "estacion",
                        hipocentral(e_lat, e_lon),
                        evento.magnitud or 0.0,
                        float(intensidad),
                        modelo.intensidad(evento, e_lat, e_lon),
                    )
                )
    return propiedades, evento, observaciones


def exposicion_pager(id_evento: str, productos: dict[str, Any]) -> dict[int, float]:
    contenido = productos.get("losspager", [{}])[0].get("contents", {}).get("exposure.xml")
    if not contenido:
        return {}
    xml = _descargar(contenido["url"], f"{id_evento}_pager.xml").decode("utf-8")
    patron = re.compile(r'<bin label="[IVX]+" value="(\d+)"[^>]*>\s*<measure type="population" value="(\d+)"')
    return {int(grado): float(personas) for grado, personas in patron.findall(xml)}


def exposicion_enlace(evento: DatosEvento, modelo: ModeloSismo, pais: str) -> dict[int, float]:
    """Población del país (Kontur, H3 res. 8) expuesta a la intensidad que estima ENLACE."""
    gpkg = descargar_poblacion(pais, obtener_config(), CACHE)
    with sqlite3.connect(gpkg) as conexion:
        tabla = conexion.execute("SELECT table_name FROM gpkg_contents").fetchone()[0]
        filas = conexion.execute(f'SELECT h3, population FROM "{tabla}"').fetchall()
    return exposicion(
        (float(personas), modelo.intensidad(evento, *h3.cell_to_latlng(celda)))
        for celda, personas in filas
    )


def _fila(nombre: str, m: Metricas) -> str:
    return f"| {nombre} | {m.n} | {m.sesgo:+.2f} | {m.rmse:.2f} | {m.dentro_de_1:.0%} |"


ENCABEZADO = "| | Observaciones | Sesgo (estimada − observada) | Error típico (RMSE) | Dentro de ±1 grado |\n|---|---|---|---|---|"


def main() -> None:
    argumentos = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    argumentos.add_argument("--evento", action="append", help="id del USGS, o id:PAIS para comparar la exposición")
    pedidos = argumentos.parse_args().evento or EVENTOS_POR_DEFECTO

    config = obtener_config()
    sismo = config.amenazas.sismo
    modelo = ModeloSismo(sismo)
    lineas = [
        "# Validación retrospectiva del modelo de intensidad",
        "",
        f"Generado el {datetime.now(UTC):%Y-%m-%d} con `preparacion/validar_evento.py`.",
        "",
        "ENLACE estima la intensidad de un sismo **desde el primer minuto**, solo con magnitud, "
        "epicentro y profundidad (atenuación de Bakun y Wentworth, 1997: "
        f"MMI = {sismo.a} + {sismo.b}·M − {sismo.c}·log10(R)). "
        "Aquí se compara esa estimación con lo que se observó en sismos reales de la región:",
        "",
        "- **DYFI** («¿Lo sintió?», USGS): intensidad reportada por la gente, en celdas de 10 km "
        f"con al menos {RESPUESTAS_MINIMAS_DYFI} respuestas.",
        "- **Estaciones**: intensidad del ShakeMap del USGS calculada con la aceleración medida.",
        "",
    ]
    todas: list[Observacion] = []
    resumen: dict[str, Any] = {"eventos": []}
    for pedido in pedidos:
        id_evento, _, pais = pedido.partition(":")
        propiedades, evento, observaciones = observaciones_del_evento(id_evento, modelo)
        todas += observaciones
        titulo = f"{propiedades['place']} — M{evento.magnitud}, {evento.hora:%Y-%m-%d}, profundidad {evento.profundidad_km:.0f} km"
        print(f"{id_evento}: {titulo}: {len(observaciones)} observaciones")
        lineas += [f"## {titulo}", "", f"Evento del USGS: `{id_evento}`.", ""]
        global_ = Metricas.de(observaciones)
        registro: dict[str, Any] = {"id": id_evento, "titulo": titulo}
        if global_:
            lineas += [ENCABEZADO, _fila("Todas", global_)]
            for fuente, nombre in (("dyfi", "DYFI"), ("estacion", "Estaciones")):
                m = Metricas.de(o for o in observaciones if o.fuente == fuente)
                if m:
                    lineas.append(_fila(nombre, m))
            for banda, m in por_banda(observaciones).items():
                lineas.append(_fila(banda, m))
            lineas.append("")
            registro["metricas"] = asdict(global_)
        else:
            lineas += ["Sin observaciones suficientes (ni DYFI con varias respuestas ni estaciones).", ""]

        if pais:
            pager = exposicion_pager(id_evento, propiedades["products"])
            propia = exposicion_enlace(evento, modelo, pais)
            lineas += [
                f"**Población expuesta** (Kontur Population de {pais} con la intensidad de ENLACE, "
                "frente a PAGER del USGS, que usa el ShakeMap y su propia capa de población):",
                "",
                "| Intensidad | ENLACE | PAGER |",
                "|---|---|---|",
            ]
            for grado in range(4, 11):
                if propia.get(grado) or pager.get(grado):
                    lineas.append(
                        f"| {ROMANOS[grado]} | {propia.get(grado, 0):,.0f} | {pager.get(grado, 0):,.0f} |"
                    )
            fuerte_propia = sum(v for g, v in propia.items() if g >= GRADO_FUERTE)
            fuerte_pager = sum(v for g, v in pager.items() if g >= GRADO_FUERTE)
            lineas += [
                "",
                f"En intensidad {ROMANOS[GRADO_FUERTE]} o más (daños leves en adelante): ENLACE "
                f"{fuerte_propia:,.0f} personas, PAGER {fuerte_pager:,.0f}"
                + (f" ({fuerte_propia / fuerte_pager:.1f} veces)." if fuerte_pager else "."),
                "",
            ]
            registro["exposicion"] = {"enlace": propia, "pager": pager}
        resumen["eventos"].append(registro)

    conjunto = Metricas.de(todas)
    ajuste = ajustar_a_y_c(todas, sismo.b)
    lineas += ["## Conjunto", ""]
    if conjunto:
        lineas += [ENCABEZADO, _fila("Todos los sismos", conjunto)]
        for banda, m in por_banda(todas).items():
            lineas.append(_fila(banda, m))
        lineas.append("")
        resumen["conjunto"] = asdict(conjunto)
    if ajuste:
        a, c = ajuste
        ajustadas = [
            Observacion(o.fuente, o.distancia_km, o.magnitud, o.observada, o.estimada - (sismo.a - a) - (c - sismo.c) * math.log10(max(o.distancia_km, 1.0)))
            for o in todas
        ]
        m = Metricas.de(ajustadas)
        resumen["ajuste_propuesto"] = {"a": round(a, 2), "c": round(c, 2), "metricas": asdict(m) if m else None}
        lineas += [
            "### Calibración regional propuesta",
            "",
            f"Ajustando por mínimos cuadrados `a` y `c` (con b = {sismo.b} fijo) a estas observaciones: "
            f"**a = {a:.2f}, c = {c:.2f}** (hoy {sismo.a} y {sismo.c}).",
            "",
            ENCABEZADO,
            _fila("Con los coeficientes actuales", conjunto) if conjunto else "",
            _fila("Con los propuestos", m) if m else "",
            "",
            "Los coeficientes están en `servidor/config/enlace.toml` (`[amenazas.sismo]`). Cambiarlos "
            "es una decisión del equipo: conviene validar con más sismos antes de adoptarlos.",
            "",
        ]
    lineas += [
        "## Limitaciones",
        "",
        "- DYFI depende de que la gente responda: en Centroamérica hay pocas respuestas lejos de las "
        "ciudades, y la intensidad reportada tiende a ser más baja en zonas poco pobladas.",
        "- El modelo usa la distancia al hipocentro y no considera el suelo ni el tipo de falla; "
        "ENLACE lo corrige después con los reportes que llegan (fusión de evidencia).",
        "- Cerca del epicentro (menos de 50 km) hay muy pocas observaciones en estos sismos: el "
        "ajuste propuesto mejora el promedio pero no está comprobado ahí, donde está el daño. En "
        "Managua 2014 (sismo poco profundo a ~30 km de la ciudad) el modelo pone a la ciudad en "
        "intensidad VI y el USGS en V: la estimación inicial de ENLACE es conservadora (prefiere "
        "sobrestimar), y por eso la certeza inicial es baja y las zonas quedan para «explorar».",
        "- La población de Kontur es residencial (de noche) y la de PAGER es LandScan (promedio "
        "del día); las diferencias en exposición también vienen de ahí.",
    ]
    RESULTADOS.mkdir(exist_ok=True)
    (RESULTADOS / "validacion_sismos.md").write_text("\n".join(lineas) + "\n", encoding="utf-8")
    (RESULTADOS / "validacion_sismos.json").write_text(
        json.dumps(resumen, ensure_ascii=False, indent=1, default=str), encoding="utf-8"
    )
    if conjunto:
        print(
            f"Conjunto: {conjunto.n} observaciones, sesgo {conjunto.sesgo:+.2f}, "
            f"RMSE {conjunto.rmse:.2f}, {conjunto.dentro_de_1:.0%} dentro de ±1 grado"
        )
    if ajuste:
        print(f"Ajuste propuesto: a = {ajuste[0]:.2f}, c = {ajuste[1]:.2f} → {resumen['ajuste_propuesto']['metricas']}")
    print(f"→ {RESULTADOS / 'validacion_sismos.md'}")


if __name__ == "__main__":
    main()
