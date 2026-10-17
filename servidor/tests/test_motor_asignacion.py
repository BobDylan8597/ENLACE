from datetime import UTC, datetime

import pytest

from app.config import obtener_config
from app.modelos.enums import Capacidad, OrigenReporte, TipoReporte
from app.motor.asignacion import (
    EquipoDisponible,
    EstimadorLineaRecta,
    Pendiente,
    capacidad_requerida,
    recomendar,
)
from app.motor.tipos import DatosReporte

CONFIG = obtener_config().asignacion
DESTINO = (12.1364, -86.2514)


def _reporte(**campos: object) -> DatosReporte:
    return DatosReporte(
        "m", "a", "x", OrigenReporte.app, TipoReporte.ayuda, datetime.now(UTC), **campos  # type: ignore[arg-type]
    )


@pytest.mark.parametrize(
    ("campos", "esperada"),
    [
        ({"atrapados": 1, "heridos": 2}, Capacidad.rescate),
        ({"heridos": 1}, Capacidad.medico),
        ({"necesidades": ["agua"]}, Capacidad.agua),
        ({"necesidades": ["refugio"]}, Capacidad.reconocimiento),
        ({}, Capacidad.reconocimiento),
    ],
)
def test_capacidad_segun_el_reporte(campos: dict[str, object], esperada: Capacidad) -> None:
    assert capacidad_requerida(_reporte(**campos), CONFIG) is esperada


def _pendiente(prioridad: float, capacidad: Capacidad = Capacidad.rescate) -> Pendiente:
    return Pendiente(
        "x", "Zona Centro", prioridad, capacidad, "2 atrapados", DESTINO, f"r{prioridad}"
    )


def _equipo(id: int, km_al_norte: float, *capacidades: Capacidad) -> EquipoDisponible:
    return EquipoDisponible(
        id, f"Equipo {id}", frozenset(capacidades), (DESTINO[0] + km_al_norte / 111, DESTINO[1])
    )


def test_elige_el_equipo_capaz_mas_cercano() -> None:
    equipos = [
        _equipo(1, 1, Capacidad.medico),
        _equipo(2, 5, Capacidad.rescate),
        _equipo(3, 2, Capacidad.rescate),
    ]
    resultado = recomendar([_pendiente(90)], equipos, EstimadorLineaRecta(CONFIG), CONFIG)
    assert [r.id_equipo for r in resultado.recomendaciones] == [3]


def test_un_equipo_no_se_recomienda_dos_veces_y_gana_la_mayor_prioridad() -> None:
    equipos = [_equipo(1, 1, Capacidad.rescate)]
    pendientes = [_pendiente(40), _pendiente(90)]
    resultado = recomendar(pendientes, equipos, EstimadorLineaRecta(CONFIG), CONFIG)
    assert [r.prioridad for r in resultado.recomendaciones] == [90]
    assert [p.prioridad for p in resultado.sin_cubrir] == [40]


def test_motivo_explica_la_decision() -> None:
    resultado = recomendar(
        [_pendiente(87)], [_equipo(7, 2, Capacidad.rescate)], EstimadorLineaRecta(CONFIG), CONFIG
    )
    motivo = resultado.recomendaciones[0].motivo
    assert "Prioridad 87/100" in motivo and "Equipo 7" in motivo and "min" in motivo


def test_tiempo_en_linea_recta() -> None:
    # 10 km × factor de desvío / velocidad
    minutos = EstimadorLineaRecta(CONFIG).minutos(DESTINO, (DESTINO[0] + 10 / 111.195, DESTINO[1]))
    esperado = 10 * CONFIG.factor_desvio / CONFIG.velocidad_promedio_kmh * 60
    assert minutos == pytest.approx(esperado, rel=1e-3)


def test_no_repite_un_par_rechazado() -> None:
    equipos = [_equipo(1, 1, Capacidad.rescate), _equipo(2, 3, Capacidad.rescate)]
    pendiente = _pendiente(90)
    resultado = recomendar(
        [pendiente],
        equipos,
        EstimadorLineaRecta(CONFIG),
        CONFIG,
        vetados=frozenset({(pendiente.clave, 1)}),
    )
    assert [r.id_equipo for r in resultado.recomendaciones] == [2]
