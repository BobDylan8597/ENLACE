from datetime import UTC, datetime
from zoneinfo import ZoneInfo

import h3
import pytest

from app.config import obtener_config
from app.modelos.enums import TipoEvento
from app.motor.amenazas import obtener_modelo
from app.motor.amenazas.sismo import ModeloSismo
from app.motor.impacto import fraccion_inicial, ocupantes
from app.motor.tipos import DatosCelda, DatosEvento

LAT, LON = 12.1364, -86.2514
HORA = datetime(2026, 10, 16, 14, 0, tzinfo=UTC)


@pytest.fixture
def modelo() -> ModeloSismo:
    return ModeloSismo(obtener_config().amenazas.sismo)


def _sismo(magnitud: float = 6.2, profundidad: float | None = 10.0) -> DatosEvento:
    return DatosEvento(
        TipoEvento.sismo, LAT, LON, HORA, magnitud=magnitud, profundidad_km=profundidad
    )


def _celda(lat: float = LAT, lon: float = LON, tipos: dict[str, float] | None = None) -> DatosCelda:
    return DatosCelda(
        id_h3=h3.latlng_to_cell(lat, lon, 8),
        poblacion_por_hora=[float(h) for h in range(24)],
        vulnerabilidad=0.5,
        tipos_construccion=tipos or {"adobe_taquezal": 1.0},
    )


def test_intensidad_coincide_con_la_formula(modelo: ModeloSismo) -> None:
    # Sobre el epicentro con 10 km de profundidad: R = 10 → MMI = a + b·M − c
    c = obtener_config().amenazas.sismo
    assert modelo.intensidad(_sismo(6.0), LAT, LON) == pytest.approx(c.a + c.b * 6.0 - c.c)


def test_intensidad_baja_con_la_distancia_y_sube_con_la_magnitud(modelo: ModeloSismo) -> None:
    cerca = modelo.intensidad(_sismo(), LAT, LON)
    lejos = modelo.intensidad(_sismo(), LAT + 0.5, LON)
    mayor = modelo.intensidad(_sismo(7.0), LAT, LON)
    assert lejos < cerca < mayor


def test_sismo_sin_magnitud_se_rechaza(modelo: ModeloSismo) -> None:
    with pytest.raises(ValueError):
        modelo.intensidad(_sismo(magnitud=None), LAT, LON)  # type: ignore[arg-type]


def test_probabilidad_de_dano_es_media_en_la_mediana(modelo: ModeloSismo) -> None:
    mediana = obtener_config().amenazas.sismo.fragilidad["adobe_taquezal"].mediana
    assert modelo.probabilidad_dano(mediana, "adobe_taquezal") == pytest.approx(0.5)


def test_construccion_debil_sufre_mas_que_la_resistente(modelo: ModeloSismo) -> None:
    debil = modelo.fraccion_dano(_sismo(), _celda(tipos={"adobe_taquezal": 1.0}))
    fuerte = modelo.fraccion_dano(_sismo(), _celda(tipos={"concreto_reforzado": 1.0}))
    mixta = modelo.fraccion_dano(
        _sismo(), _celda(tipos={"adobe_taquezal": 0.5, "concreto_reforzado": 0.5})
    )
    assert fuerte < mixta < debil
    assert mixta == pytest.approx((debil + fuerte) / 2)


def test_tipo_de_construccion_desconocido_se_rechaza(modelo: ModeloSismo) -> None:
    with pytest.raises(KeyError):
        modelo.fraccion_dano(_sismo(), _celda(tipos={"carton": 1.0}))


def test_ocupantes_usa_la_hora_local_de_la_ciudad() -> None:
    # 14:00 UTC = 08:00 en Managua (UTC−6)
    assert ocupantes(_celda(), HORA, ZoneInfo("America/Managua")) == 8.0


def test_evento_sin_modelo_parte_de_impacto_cero() -> None:
    otro = DatosEvento(TipoEvento.otro, LAT, LON, HORA)
    modelo = obtener_modelo(TipoEvento.otro, obtener_config().amenazas)
    assert fraccion_inicial(otro, _celda(), modelo) == 0.0
