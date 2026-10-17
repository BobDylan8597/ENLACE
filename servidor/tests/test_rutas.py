"""Rutas y bloqueos (sección 10) sobre la cuadrícula de prueba."""

import math

import pytest

from app.config import obtener_config
from app.modelos.enums import AlcanceBloqueo, Capacidad, EstadoBloqueo
from app.motor.asignacion import EquipoDisponible, Pendiente, recomendar
from app.rutas.bloqueos import (
    BloqueoEnRed,
    Modo,
    Penalizaciones,
    estado_segun_reportes,
    factor_bloqueo,
)
from app.rutas.calculo import SIN_NOMBRE, EstimadorRutas, calcular_ruta, instrucciones
from app.rutas.grafo import GrafoCalles
from tests.red_prueba import SEGUNDOS_POR_CUADRA, nodo, punto, red_cuadricula

CONFIG = obtener_config()
RUTAS = CONFIG.rutas
VELOCIDAD = RUTAS.factor_velocidad_emergencia


@pytest.fixture(scope="module")
def red() -> GrafoCalles:
    return red_cuadricula()


def _bloqueo(
    red: GrafoCalles,
    desde: tuple[int, int],
    hasta: tuple[int, int],
    estado: EstadoBloqueo,
    alcance: AlcanceBloqueo = AlcanceBloqueo.total,
) -> BloqueoEnRed:
    tramo = (nodo(*desde), nodo(*hasta), 0)
    return BloqueoEnRed(tuple(red.tramos_de_la_calle(tramo)), estado, alcance)


def _ruta(red: GrafoCalles, *bloqueos: BloqueoEnRed, modo: Modo = Modo.vehiculo):
    penalizaciones = Penalizaciones(bloqueos, modo, RUTAS)
    return calcular_ruta(red, penalizaciones, punto(0, 0), punto(0, 2), RUTAS)


def _nodos(ruta) -> list[int]:  # type: ignore[no-untyped-def]
    return [ruta.tramos[0][0], *(v for _, v, _ in ruta.tramos)]


# ── Estados y factores ─────────────────────────────────────


def test_estado_segun_personas_que_reportan() -> None:
    assert estado_segun_reportes(1, False, RUTAS) is EstadoBloqueo.sin_confirmar
    assert (
        estado_segun_reportes(RUTAS.reportes_para_probable, False, RUTAS) is EstadoBloqueo.probable
    )
    confirmado = RUTAS.reportes_para_confirmado
    assert estado_segun_reportes(confirmado, False, RUTAS) is EstadoBloqueo.confirmado
    assert estado_segun_reportes(1, True, RUTAS) is EstadoBloqueo.confirmado


@pytest.mark.parametrize(
    ("estado", "alcance", "modo", "esperado"),
    [
        (
            EstadoBloqueo.sin_confirmar,
            AlcanceBloqueo.total,
            Modo.vehiculo,
            RUTAS.factor_sin_confirmar,
        ),
        (EstadoBloqueo.probable, AlcanceBloqueo.total, Modo.vehiculo, RUTAS.factor_probable),
        (EstadoBloqueo.confirmado, AlcanceBloqueo.total, Modo.vehiculo, None),
        (EstadoBloqueo.confirmado, AlcanceBloqueo.total, Modo.a_pie, None),
        (EstadoBloqueo.sin_confirmar, AlcanceBloqueo.solo_a_pie, Modo.vehiculo, None),
        (EstadoBloqueo.confirmado, AlcanceBloqueo.solo_a_pie, Modo.a_pie, 1.0),
        (EstadoBloqueo.descartado, AlcanceBloqueo.total, Modo.vehiculo, 1.0),
    ],
)
def test_factor_de_cada_bloqueo(
    estado: EstadoBloqueo, alcance: AlcanceBloqueo, modo: Modo, esperado: float | None
) -> None:
    assert factor_bloqueo(estado, alcance, modo, RUTAS) == esperado


def test_varios_bloqueos_en_la_misma_calle_gana_el_peor(red: GrafoCalles) -> None:
    leve = _bloqueo(red, (0, 0), (0, 1), EstadoBloqueo.sin_confirmar)
    fuerte = _bloqueo(red, (0, 0), (0, 1), EstadoBloqueo.probable)
    penalizaciones = Penalizaciones([leve, fuerte], Modo.vehiculo, RUTAS)
    assert penalizaciones.factores[(nodo(0, 0), nodo(0, 1))] == RUTAS.factor_probable
    # El bloqueo corta la calle en ambos sentidos
    assert penalizaciones.factores[(nodo(0, 1), nodo(0, 0))] == RUTAS.factor_probable


# ── Rutas ──────────────────────────────────────────────────


def test_sin_bloqueos_va_derecho_y_con_velocidad_de_emergencia(red: GrafoCalles) -> None:
    ruta = _ruta(red)
    assert ruta is not None
    assert _nodos(ruta) == [nodo(0, 0), nodo(0, 1), nodo(0, 2)]
    assert ruta.tiempo_s == pytest.approx(2 * SEGUNDOS_POR_CUADRA / VELOCIDAD)
    assert ruta.distancia_m == pytest.approx(2 * 217, rel=0.02)


def test_sin_confirmar_encarece_la_calle(red: GrafoCalles) -> None:
    sin_bloqueo = _ruta(red)
    con_bloqueo = _ruta(red, _bloqueo(red, (0, 0), (0, 1), EstadoBloqueo.sin_confirmar))
    assert sin_bloqueo is not None and con_bloqueo is not None
    assert con_bloqueo.tiempo_s > sin_bloqueo.tiempo_s


@pytest.mark.parametrize("estado", [EstadoBloqueo.probable, EstadoBloqueo.confirmado])
def test_probable_o_confirmado_hace_desviar(red: GrafoCalles, estado: EstadoBloqueo) -> None:
    ruta = _ruta(red, _bloqueo(red, (0, 0), (0, 1), estado))
    assert ruta is not None
    assert (nodo(0, 0), nodo(0, 1)) not in {(u, v) for u, v, _ in ruta.tramos}
    assert _nodos(ruta)[0] == nodo(0, 0) and _nodos(ruta)[-1] == nodo(0, 2)


def test_descartado_restaura_la_calle(red: GrafoCalles) -> None:
    ruta = _ruta(red, _bloqueo(red, (0, 0), (0, 1), EstadoBloqueo.descartado))
    assert ruta is not None and _nodos(ruta) == [nodo(0, 0), nodo(0, 1), nodo(0, 2)]


def test_solo_a_pie_corta_vehiculos_pero_no_peatones(red: GrafoCalles) -> None:
    bloqueo = _bloqueo(red, (0, 0), (0, 1), EstadoBloqueo.confirmado, AlcanceBloqueo.solo_a_pie)
    en_vehiculo = _ruta(red, bloqueo)
    a_pie = _ruta(red, bloqueo, modo=Modo.a_pie)
    assert en_vehiculo is not None and a_pie is not None
    assert len(en_vehiculo.tramos) > len(a_pie.tramos) == 2


def test_sin_camino_posible_devuelve_none(red: GrafoCalles) -> None:
    # Se cortan las dos salidas del cruce de origen
    cortes = [
        _bloqueo(red, (0, 0), (0, 1), EstadoBloqueo.confirmado),
        _bloqueo(red, (0, 0), (1, 0), EstadoBloqueo.confirmado),
    ]
    assert _ruta(red, *cortes) is None


def test_punto_lejos_de_la_red_no_tiene_ruta(red: GrafoCalles) -> None:
    penalizaciones = Penalizaciones([], Modo.vehiculo, RUTAS)
    assert calcular_ruta(red, penalizaciones, punto(0, 0), (13.0, -86.0), RUTAS) is None


def test_geojson_con_forma_tiempo_e_instrucciones(red: GrafoCalles) -> None:
    ruta = _ruta(red)
    assert ruta is not None
    geojson = ruta.como_geojson(destino="prueba")
    assert geojson["geometry"]["type"] == "LineString"
    assert len(geojson["geometry"]["coordinates"]) == 3
    assert geojson["properties"]["tramos"][0] == [nodo(0, 0), nodo(0, 1), 0]
    assert geojson["properties"]["destino"] == "prueba"


# ── Instrucciones ──────────────────────────────────────────


def test_instrucciones_con_giros(red: GrafoCalles) -> None:
    # Hacia el este por Calle 0 y luego al norte por Avenida 2: giro a la izquierda
    penalizaciones = Penalizaciones([], Modo.vehiculo, RUTAS)
    ruta = calcular_ruta(red, penalizaciones, punto(0, 0), punto(2, 2), RUTAS)
    assert ruta is not None
    textos = [i.texto for i in ruta.instrucciones]
    assert textos[-1] == "Llegas al destino"
    assert textos[0].startswith("Sal por ")
    assert any(
        t.startswith("Gira a la izquierda en") or t.startswith("Gira a la derecha en")
        for t in textos
    )


def test_calles_seguidas_con_el_mismo_nombre_se_agrupan() -> None:
    segmentos = [("Calle 0", 200, 90, 90), ("Calle 0", 220, 90, 90), (SIN_NOMBRE, 80, 0, 0)]
    textos = [i.texto for i in instrucciones(segmentos)]
    assert textos == [
        "Sal por Calle 0 y sigue 400 m",
        "Gira a la izquierda en una calle sin nombre y sigue 80 m",
        "Llegas al destino",
    ]


# ── Estimador para el motor ────────────────────────────────


def test_estimador_coincide_con_la_ruta_y_detecta_sin_camino(red: GrafoCalles) -> None:
    libre = Penalizaciones([], Modo.vehiculo, RUTAS)
    estimador = EstimadorRutas(red, libre, RUTAS, CONFIG.asignacion)
    ruta = calcular_ruta(red, libre, punto(0, 0), punto(2, 2), RUTAS)
    assert ruta is not None
    assert estimador.minutos(punto(0, 0), punto(2, 2)) == pytest.approx(ruta.tiempo_s / 60)

    cortes = Penalizaciones(
        [
            _bloqueo(red, (0, 0), (0, 1), EstadoBloqueo.confirmado),
            _bloqueo(red, (0, 0), (1, 0), EstadoBloqueo.confirmado),
        ],
        Modo.vehiculo,
        RUTAS,
    )
    cortado = EstimadorRutas(red, cortes, RUTAS, CONFIG.asignacion)
    assert math.isinf(cortado.minutos(punto(0, 0), punto(2, 2)))


def test_la_cache_se_renueva_al_cambiar_los_bloqueos(red: GrafoCalles) -> None:
    from collections import OrderedDict

    cache: OrderedDict = OrderedDict()
    libre = Penalizaciones([], Modo.vehiculo, RUTAS)
    antes = EstimadorRutas(red, libre, RUTAS, CONFIG.asignacion, cache).minutos(
        punto(0, 0), punto(0, 2)
    )
    bloqueada = Penalizaciones(
        [_bloqueo(red, (0, 0), (0, 1), EstadoBloqueo.confirmado)], Modo.vehiculo, RUTAS
    )
    despues = EstimadorRutas(red, bloqueada, RUTAS, CONFIG.asignacion, cache).minutos(
        punto(0, 0), punto(0, 2)
    )
    assert despues > antes
    assert len(cache) == 2


def test_motor_no_propone_un_equipo_que_no_puede_llegar(red: GrafoCalles) -> None:
    cortes = Penalizaciones(
        [
            _bloqueo(red, (0, 0), (0, 1), EstadoBloqueo.confirmado),
            _bloqueo(red, (0, 0), (1, 0), EstadoBloqueo.confirmado),
        ],
        Modo.vehiculo,
        RUTAS,
    )
    atrapado = EquipoDisponible(1, "Equipo encerrado", frozenset({Capacidad.rescate}), punto(0, 0))
    libre = EquipoDisponible(2, "Equipo libre", frozenset({Capacidad.rescate}), punto(2, 0))
    pendiente = Pendiente("x", "Zona", 90, Capacidad.rescate, "2 atrapados", punto(2, 2), "r1")
    estimador = EstimadorRutas(red, cortes, RUTAS, CONFIG.asignacion)
    resultado = recomendar([pendiente], [atrapado, libre], estimador, CONFIG.asignacion)
    assert [r.id_equipo for r in resultado.recomendaciones] == [2]
    solo_atrapado = recomendar([pendiente], [atrapado], estimador, CONFIG.asignacion)
    assert solo_atrapado.recomendaciones == [] and solo_atrapado.sin_cubrir == [pendiente]


def test_nodo_y_tramo_mas_cercanos(red: GrafoCalles) -> None:
    lat, lon = punto(1, 1)
    assert red.nodo_cercano(lat + 0.0001, lon).nodo == nodo(1, 1)
    # A mitad de la cuadra entre 00 y 01, un poco al sur: el tramo más cercano es Calle 0
    cercano = red.tramo_cercano(punto(0, 0)[0] - 0.0002, punto(0, 0)[1] + 0.001)
    assert cercano is not None
    assert {cercano.tramo[0], cercano.tramo[1]} == {nodo(0, 0), nodo(0, 1)}
    assert cercano.distancia_m == pytest.approx(22, abs=3)
