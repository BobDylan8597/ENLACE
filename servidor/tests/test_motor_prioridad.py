from datetime import UTC, datetime, timedelta

import pytest

from app.config import obtener_config
from app.modelos.enums import CategoriaCelda, OrigenReporte, TipoReporte
from app.motor.prioridad import clasificar, prioridad_celda, prioridad_reporte
from app.motor.tipos import DatosReporte

HORA = datetime(2026, 10, 16, 14, 0, tzinfo=UTC)
CONFIG = obtener_config()
ALTO = CONFIG.clasificacion.umbral_impacto_personas * 2
BAJO = CONFIG.clasificacion.umbral_impacto_personas / 2


@pytest.mark.parametrize(
    ("impacto", "certeza", "esperada"),
    [
        (ALTO, 0.9, CategoriaCelda.actuar),
        (ALTO, 0.1, CategoriaCelda.explorar),
        (BAJO, 0.9, CategoriaCelda.estable),
        (BAJO, 0.1, CategoriaCelda.explorar_despues),
    ],
)
def test_clasificacion_en_cuatro_categorias(
    impacto: float, certeza: float, esperada: CategoriaCelda
) -> None:
    assert clasificar(impacto, certeza, CONFIG.clasificacion) is esperada


def test_prioridad_de_celda_crece_con_el_impacto() -> None:
    def valor(impacto: float) -> float:
        return prioridad_celda(impacto, 0.3, HORA, None, HORA, CONFIG.prioridad.celda).valor

    assert 0 <= valor(0) < valor(20) < valor(200) <= 100


def test_prioridad_de_celda_llega_a_100_con_todo_al_maximo() -> None:
    c = CONFIG.prioridad.celda
    tarde = HORA + timedelta(hours=c.horas_urgencia_max)
    assert prioridad_celda(1e9, 1.0, HORA, None, tarde, c).valor == pytest.approx(100, abs=0.1)


def test_atender_una_celda_reinicia_su_espera() -> None:
    c = CONFIG.prioridad.celda
    ahora = HORA + timedelta(hours=5)
    sin_atender = prioridad_celda(50, 0.3, HORA, None, ahora, c)
    atendida = prioridad_celda(50, 0.3, HORA, ahora, ahora, c)
    assert atendida.valor < sin_atender.valor
    assert atendida.componentes["espera"] == 0.0


def _reporte(**campos: object) -> DatosReporte:
    return DatosReporte(
        "m", "a", "x", OrigenReporte.app, TipoReporte.ayuda, HORA, **campos  # type: ignore[arg-type]
    )


def test_reporte_con_atrapados_va_antes_que_uno_sin_lesionados() -> None:
    c = CONFIG.prioridad.reporte
    grave = prioridad_reporte(_reporte(atrapados=2, heridos=1), 50, 0.3, HORA, c)
    leve = prioridad_reporte(_reporte(personas=1), 50, 0.3, HORA, c)
    assert grave.valor > leve.valor


def test_personas_vulnerables_suben_la_prioridad() -> None:
    c = CONFIG.prioridad.reporte
    con = prioridad_reporte(_reporte(personas=2, vulnerables=2), 50, 0.1, HORA, c)
    sin = prioridad_reporte(_reporte(personas=2), 50, 0.1, HORA, c)
    assert con.valor > sin.valor


def test_prioridad_distingue_impactos_del_rango_realista() -> None:
    # Una celda de resolución 8 puede tener cientos de afectados: el componente no debe saturarse
    c = CONFIG.prioridad.celda
    componentes = [
        prioridad_celda(impacto, 0.3, HORA, None, HORA, c).componentes["impacto"]
        for impacto in (100, 300, 500, 850)
    ]
    assert componentes == sorted(componentes)
    assert componentes[-1] - componentes[-2] > 0.05
