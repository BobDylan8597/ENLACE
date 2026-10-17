import math
from typing import Any
from datetime import UTC, datetime, timedelta

import pytest

from app.config import ConfigFusion, obtener_config
from app.modelos.enums import EstadoCheckIn, NivelDano, OrigenReporte, TipoReporte
from app.motor.fusion import Evidencia, estimar_celda, informacion_nueva, preparar_evidencia
from app.motor.tipos import DatosCheckIn, DatosReporte

H3 = "88283082bffffff"
HORA = datetime(2026, 10, 16, 14, 0, tzinfo=UTC)
OCUPANTES = 1000.0
FRACCION_MODELO = 0.1


@pytest.fixture
def config() -> ConfigFusion:
    return obtener_config().fusion


def _reporte(
    id_mensaje: str,
    id_anonimo: str = "a",
    tipo: TipoReporte = TipoReporte.ayuda,
    origen: OrigenReporte = OrigenReporte.app,
    minutos: float = 0,
    **campos: object,
) -> DatosReporte:
    return DatosReporte(
        id_mensaje=id_mensaje,
        id_anonimo=id_anonimo,
        id_h3=H3,
        origen=origen,
        tipo=tipo,
        hora=HORA + timedelta(minutes=minutos),
        **campos,  # type: ignore[arg-type]
    )


def _estimar(config: ConfigFusion, evidencia: list[Evidencia], checkins=()):  # type: ignore[no-untyped-def]
    return estimar_celda(H3, OCUPANTES, FRACCION_MODELO, evidencia, list(checkins), config)


def test_sin_evidencia_se_usa_el_modelo(config: ConfigFusion) -> None:
    estado = _estimar(config, [])
    assert estado.certeza == 0.0
    assert estado.impacto_estimado == estado.impacto_inicial == pytest.approx(100.0)


def test_brigada_que_ve_colapso_sube_estimacion_y_certeza(config: ConfigFusion) -> None:
    reporte = _reporte(
        "b1",
        tipo=TipoReporte.evaluacion,
        origen=OrigenReporte.brigada,
        nivel_dano=NivelDano.colapso,
    )
    estado = _estimar(config, preparar_evidencia([reporte], config).principales)
    certeza = 1 - math.exp(-1.0)
    esperado = (1 - certeza) * FRACCION_MODELO + certeza * 0.6
    assert estado.certeza == pytest.approx(certeza)
    assert estado.fraccion_final == pytest.approx(esperado)
    assert estado.impacto_estimado > estado.impacto_inicial


def test_reportes_de_bien_bajan_la_estimacion(config: ConfigFusion) -> None:
    reportes = [_reporte(f"r{i}", id_anonimo=f"p{i}", tipo=TipoReporte.bien) for i in range(5)]
    estado = _estimar(config, preparar_evidencia(reportes, config).principales)
    assert estado.impacto_estimado < estado.impacto_inicial
    assert estado.certeza > 0.5


def test_misma_persona_en_la_ventana_se_fusiona_como_duplicado(config: ConfigFusion) -> None:
    preparada = preparar_evidencia(
        [_reporte("m1"), _reporte("m2", minutos=5), _reporte("m3", minutos=120)], config
    )
    assert preparada.duplicados == {"m2": "m1"}
    assert [e.reporte.id_mensaje for e in preparada.principales] == ["m1", "m3"]


def test_reportes_coincidentes_de_distintas_personas_pesan_mas(config: ConfigFusion) -> None:
    solo = preparar_evidencia([_reporte("m1")], config).principales
    coincidentes = preparar_evidencia(
        [_reporte("m1", id_anonimo="a"), _reporte("m2", id_anonimo="b", minutos=10)], config
    ).principales
    assert solo[0].peso == config.pesos_origen[OrigenReporte.app]
    assert {e.peso for e in coincidentes} == {config.peso_coincidentes}


def test_radio_sin_verificar_pesa_menos_que_la_app(config: ConfigFusion) -> None:
    radio = preparar_evidencia([_reporte("r", origen=OrigenReporte.radio)], config).principales
    app = preparar_evidencia([_reporte("a")], config).principales
    assert radio[0].peso < app[0].peso


def test_impacto_nunca_es_menor_que_lo_confirmado(config: ConfigFusion) -> None:
    reporte = _reporte("m1", atrapados=3, heridos=2)
    estado = estimar_celda(H3, 10.0, 0.0, [Evidencia(reporte, 0.5)], [], config)
    assert estado.conteo_confirmado == 5
    assert estado.impacto_estimado >= 5


def test_pocos_checkins_no_cuentan_y_muchos_si(config: ConfigFusion) -> None:
    def checkins(n: int) -> list[DatosCheckIn]:
        return [DatosCheckIn(f"c{i}", H3, EstadoCheckIn.ayuda) for i in range(n)]

    pocos = _estimar(config, [], checkins(config.minimo_checkins - 1))
    muchos = _estimar(config, [], checkins(config.minimo_checkins * 4))
    assert pocos.certeza == 0.0
    assert muchos.certeza > 0.0
    assert muchos.impacto_estimado > muchos.impacto_inicial


def test_checkins_de_bien_bajan_la_estimacion(config: ConfigFusion) -> None:
    checkins = [DatosCheckIn(f"c{i}", H3, EstadoCheckIn.bien) for i in range(40)]
    estado = _estimar(config, [], checkins)
    assert estado.impacto_estimado < estado.impacto_inicial


def _campos(**valores: Any) -> dict[str, Any]:
    base = {"personas": 1, "atrapados": 0, "heridos": 0, "vulnerables": 0, "necesidades": []}
    return {**base, **valores}


def test_reenvio_identico_no_cambia_nada() -> None:
    original = _campos(atrapados=2, necesidades=["rescate"])
    assert informacion_nueva(original, [dict(original)]) == {}


def test_reenvio_con_informacion_nueva_actualiza_el_original() -> None:
    original = _campos(personas=3, atrapados=2, necesidades=["rescate"])
    reenvio = _campos(personas=3, atrapados=1, heridos=1, necesidades=["medico"])
    # El mayor de cada conteo (no se pierden los 2 atrapados) y todas las necesidades
    assert informacion_nueva(original, [reenvio]) == {
        "heridos": 1,
        "necesidades": ["rescate", "medico"],
    }
