"""Reglas puras de los scripts de preparación de ciudades (preparacion/datos_ciudad.py)."""

import math
import sys
from pathlib import Path

import h3
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "preparacion"))

from datos_ciudad import (  # noqa: E402
    centro_elemento,
    contar_por_celda,
    distancia_mas_cercana_km,
    indice_vulnerabilidad,
    nombre_mas_cercano,
    tipos_de_zona,
)

from validacion import (  # noqa: E402
    Metricas,
    Observacion,
    ajustar_a_y_c,
    exposicion,
    grado_mmi,
    por_banda,
)

from app.config import obtener_config  # noqa: E402

VULNERABILIDAD = obtener_config().preparacion.vulnerabilidad


def test_tipos_de_zona_relativos_a_la_ciudad() -> None:
    comercios = {f"c{i}": i for i in range(10)}  # c9 es la que más comercio tiene
    tipos = tipos_de_zona(comercios, fraccion_comercial=0.2, fraccion_mixta=0.3)
    assert [tipos[f"c{i}"] for i in (9, 8)] == ["comercial", "comercial"]
    assert [tipos[f"c{i}"] for i in (7, 6, 5)] == ["mixta"] * 3
    # Sin comercios: siempre residencial
    assert tipos["c0"] == "residencial"


def test_sin_comercio_en_ninguna_celda_todo_es_residencial() -> None:
    tipos = tipos_de_zona({"a": 0, "b": 0}, 0.5, 0.5)
    assert set(tipos.values()) == {"residencial"}


def test_conteo_por_celda_h3() -> None:
    punto = (12.1364, -86.2514)
    celda = h3.latlng_to_cell(*punto, 8)
    assert contar_por_celda([punto, punto, (12.3, -86.0)], 8)[celda] == 2


def test_vulnerabilidad_crece_con_densidad_lejania_y_precariedad() -> None:
    base, _ = indice_vulnerabilidad(1000, 0.5, 0.0, VULNERABILIDAD)
    densa, _ = indice_vulnerabilidad(8000, 0.5, 0.0, VULNERABILIDAD)
    lejos, _ = indice_vulnerabilidad(1000, 3.5, 0.0, VULNERABILIDAD)
    precaria, componentes = indice_vulnerabilidad(1000, 0.5, 1.0, VULNERABILIDAD)
    assert base < densa and base < lejos and base < precaria
    assert set(componentes) == {"densidad", "acceso_salud", "precariedad"}
    # Todo al máximo: 1; sin hospitales en la zona, el acceso cuenta como el peor
    tope, _ = indice_vulnerabilidad(1e9, None, 1.0, VULNERABILIDAD)
    assert tope == pytest.approx(1.0)


def test_distancias_y_nombres_cercanos() -> None:
    assert distancia_mas_cercana_km((12.13, -86.25), []) is None
    assert distancia_mas_cercana_km((12.13, -86.25), [(12.14, -86.25)]) == pytest.approx(
        1.11, abs=0.02
    )
    barrios = [("Cerca", (12.131, -86.25)), ("Lejos", (12.2, -86.25))]
    assert nombre_mas_cercano((12.13, -86.25), barrios, 1.0) == "Cerca"
    assert nombre_mas_cercano((12.5, -86.25), barrios, 1.0) is None


def test_centro_de_nodos_y_vias_de_overpass() -> None:
    assert centro_elemento({"type": "node", "lat": 1, "lon": 2}) == (1.0, 2.0)
    assert centro_elemento({"type": "way", "center": {"lat": 3, "lon": 4}}) == (3.0, 4.0)
    assert centro_elemento({"type": "relation"}) is None


def test_la_semilla_usa_los_datos_reales_si_existen() -> None:
    import tomllib

    sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "datos"))
    from semilla import generar_celdas

    from app.ciudad import cargar_ciudad

    # La ciudad de la demo (sus archivos de configuración están en el repositorio)
    directorio = Path(__file__).resolve().parents[1] / "datos" / "ciudades" / "managua"
    ciudad = cargar_ciudad(directorio)
    semilla = tomllib.loads((directorio / "semilla.toml").read_text(encoding="utf-8"))
    sinteticas = generar_celdas(ciudad, semilla, 8)
    una = sinteticas[0].id_h3
    reales = generar_celdas(
        ciudad,
        semilla,
        8,
        poblacion_real={una: {"noche": 1234.0, "tipo": "residencial", "nombre": "Barrio OSM"}},
        vulnerabilidad_real={
            una: {"vulnerabilidad": 0.42, "infraestructura": {"hospital": {"cantidad": 1}}}
        },
    )
    celda = next(c for c in reales if c.id_h3 == una)
    assert celda.nombre_zona == "Barrio OSM" and celda.vulnerabilidad == 0.42
    assert celda.poblacion_por_hora[0] == 1234.0  # de noche, residencial: factor 1
    assert celda.infraestructura_critica == {"hospital": {"cantidad": 1}}
    # Con población real, una celda sin datos de Kontur no tiene habitantes
    otra = next(c for c in reales if c.id_h3 != una)
    assert max(otra.poblacion_por_hora) == 0.0


def _obs(distancia: float, observada: float, estimada: float) -> Observacion:
    return Observacion("dyfi", distancia, 7.0, observada, estimada)


def test_metricas_de_la_validacion() -> None:
    m = Metricas.de([_obs(10, 5, 6), _obs(10, 5, 4), _obs(10, 5, 7.5)])
    assert m is not None
    assert m.n == 3
    assert m.sesgo == pytest.approx(2.5 / 3)  # (+1 − 1 + 2,5) / 3
    assert m.rmse == pytest.approx(((1 + 1 + 6.25) / 3) ** 0.5)
    assert m.dentro_de_1 == pytest.approx(2 / 3)
    assert Metricas.de([]) is None


def test_metricas_por_banda_de_distancia() -> None:
    bandas = por_banda([_obs(20, 5, 5), _obs(100, 5, 6), _obs(500, 3, 3)])
    assert list(bandas) == ["0–50 km", "50–150 km", "más de 300 km"]


def test_el_ajuste_recupera_los_coeficientes_de_datos_sinteticos() -> None:
    a, b, c = 4.0, 1.17, 3.5
    datos = [
        Observacion("estacion", r, m, a + b * m - c * math.log10(r), 0.0)
        for r in (20, 60, 150, 400)
        for m in (6.0, 7.0)
    ]
    ajuste = ajustar_a_y_c(datos, b)
    assert ajuste is not None
    assert ajuste[0] == pytest.approx(a) and ajuste[1] == pytest.approx(c)
    assert ajustar_a_y_c(datos[:2], b) is None


def test_exposicion_por_grado_de_intensidad() -> None:
    assert (
        grado_mmi(5.4) == 5 and grado_mmi(5.6) == 6 and grado_mmi(12) == 10 and grado_mmi(0.2) == 1
    )
    assert exposicion([(100, 5.4), (50, 4.6), (10, 6.2)]) == {5: 150, 6: 10}
