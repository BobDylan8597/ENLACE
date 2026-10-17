import json
from datetime import timedelta
from pathlib import Path

import pytest
from sqlalchemy.orm import Session

from app.activacion.emisor import EventoYaActivo, emitir
from app.activacion.firma import Firmador, verificar
from app.activacion.fuentes.base import EntradaInvalida, NuevoEvento
from app.activacion.fuentes.fuente_cap import FuenteCap
from app.activacion.fuentes.fuente_comunitaria import FuenteComunitaria, detectar_propuestas
from app.activacion.fuentes.fuente_sismologica import FuenteSismologica
from app.config import obtener_config
from app.modelos.enums import FuenteEvento, OrigenReporte, TipoEvento, TipoReporte
from app.modelos.tablas import Reporte
from tests.ciudad_prueba import CENTRO, EPICENTRO, HORA, LEJANA, ZONA, poblar_ciudad

CONFIG = obtener_config()


# ── Firma ──────────────────────────────────────────────────


@pytest.fixture
def firmador(tmp_path: Path) -> Firmador:
    return Firmador.cargar_o_crear(tmp_path)


def test_alerta_firmada_se_verifica(firmador: Firmador) -> None:
    alerta = firmador.firmar({"id_evento": 1, "tipo": "sismo"}).como_dict()
    assert verificar(alerta, firmador.clave_publica_base64())


def test_alerta_alterada_se_rechaza(firmador: Firmador) -> None:
    alerta = firmador.firmar({"id_evento": 1, "tipo": "sismo"}).como_dict()
    alerta["contenido"] = alerta["contenido"].replace("sismo", "otro")
    assert not verificar(alerta, firmador.clave_publica_base64())


def test_alerta_de_otra_clave_o_sin_firma_se_rechaza(firmador: Firmador, tmp_path: Path) -> None:
    impostor = Firmador.cargar_o_crear(tmp_path / "impostor")
    alerta = impostor.firmar({"id_evento": 1}).como_dict()
    assert not verificar(alerta, firmador.clave_publica_base64())
    assert not verificar({"contenido": "{}"}, firmador.clave_publica_base64())


def test_la_clave_se_conserva_entre_arranques(tmp_path: Path) -> None:
    primero = Firmador.cargar_o_crear(tmp_path)
    segundo = Firmador.cargar_o_crear(tmp_path)
    assert primero.clave_publica_base64() == segundo.clave_publica_base64()
    assert (tmp_path / "alertas_publica.pem").exists()


# ── Fuentes ────────────────────────────────────────────────


def _cap(
    status: str = "Actual", evento: str = "Terremoto", enviado: str = "2026-10-16T08:00:00-06:00"
) -> str:
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
  <identifier>INETER-2026-001</identifier>
  <sender>ineter.gob.ni</sender>
  <sent>{enviado}</sent>
  <status>{status}</status>
  <msgType>Alert</msgType>
  <scope>Public</scope>
  <info>
    <category>Geo</category>
    <event>{evento}</event>
    <parameter><valueName>Magnitud</valueName><value>6.2</value></parameter>
    <parameter><valueName>Profundidad</valueName><value>12</value></parameter>
    <area><areaDesc>Managua</areaDesc><circle>12.13,-86.25 30</circle></area>
  </info>
</alert>"""


def test_cap_oficial_se_convierte_en_evento() -> None:
    evento = FuenteCap().convertir(_cap())
    assert evento.tipo is TipoEvento.sismo and evento.fuente is FuenteEvento.cap
    assert (evento.lat, evento.lon, evento.magnitud, evento.profundidad_km) == (
        12.13,
        -86.25,
        6.2,
        12.0,
    )
    assert evento.hora.utcoffset() == timedelta(hours=-6)


def test_cap_reconoce_inundacion_con_tildes() -> None:
    assert FuenteCap().convertir(_cap(evento="Inundación")).tipo is TipoEvento.inundacion


@pytest.mark.parametrize(
    "xml",
    [_cap(status="Exercise"), _cap(enviado="2026-10-16T08:00:00"), "<no-es-cap"],
)
def test_cap_de_prueba_sin_zona_horaria_o_mal_formado_se_rechaza(xml: str) -> None:
    with pytest.raises(EntradaInvalida):
        FuenteCap().convertir(xml)


def test_sismo_geojson_se_convierte_en_evento() -> None:
    feature = {
        "type": "Feature",
        "properties": {"mag": 6.4, "time": 1792080000000},
        "geometry": {"type": "Point", "coordinates": [-86.3, 12.1, 15.0]},
    }
    evento = FuenteSismologica().convertir(feature)
    assert (evento.lat, evento.lon, evento.magnitud, evento.profundidad_km) == (
        12.1,
        -86.3,
        6.4,
        15.0,
    )
    with pytest.raises(EntradaInvalida):
        FuenteSismologica().convertir({"properties": {}})


def _pedidos(sesion: Session, personas: int, id_h3: str = LEJANA) -> None:
    for i in range(personas):
        sesion.add(
            Reporte(
                id_mensaje=f"m{id_h3}{i}",
                id_anonimo=f"p{i}",
                id_h3=id_h3,
                origen=OrigenReporte.app,
                tipo=TipoReporte.ayuda,
                confiabilidad=0.5,
                hora=HORA - timedelta(minutes=2),
            )
        )
    sesion.flush()


def test_cuatro_personas_pidiendo_ayuda_generan_una_propuesta(sesion: Session) -> None:
    _pedidos(sesion, CONFIG.activacion.pedidos_minimos_comunitaria)
    (propuesta,) = detectar_propuestas(sesion, HORA, CONFIG.activacion)
    assert propuesta.id_h3 == LEJANA
    # Volver a detectar sin cambios no duplica la propuesta
    assert detectar_propuestas(sesion, HORA, CONFIG.activacion) == []
    evento = FuenteComunitaria().convertir(propuesta)
    assert evento.fuente is FuenteEvento.comunitaria and LEJANA in (evento.zona_afectada or [])


def test_pocos_pedidos_no_generan_propuesta(sesion: Session) -> None:
    _pedidos(sesion, CONFIG.activacion.pedidos_minimos_comunitaria - 1)
    assert detectar_propuestas(sesion, HORA, CONFIG.activacion) == []


# ── Emisor ─────────────────────────────────────────────────


def _sismo() -> NuevoEvento:
    return NuevoEvento(
        tipo=TipoEvento.sismo,
        fuente=FuenteEvento.manual,
        lat=CENTRO[0],
        lon=CENTRO[1],
        hora=HORA,
        magnitud=6.2,
        profundidad_km=10,
    )


def test_emitir_crea_evento_zona_afectada_y_alerta_valida(
    sesion: Session, firmador: Firmador
) -> None:
    poblar_ciudad(sesion)
    resultado = emitir(sesion, _sismo(), firmador, HORA, CONFIG, ZONA)
    assert resultado.evento.zona_afectada == [EPICENTRO]
    assert verificar(resultado.alerta.como_dict(), firmador.clave_publica_base64())
    datos = json.loads(resultado.alerta.contenido)
    assert datos["id_evento"] == resultado.evento.id and datos["zona_afectada"] == [EPICENTRO]


def test_no_se_emite_con_un_evento_activo(sesion: Session, firmador: Firmador) -> None:
    poblar_ciudad(sesion)
    emitir(sesion, _sismo(), firmador, HORA, CONFIG, ZONA)
    with pytest.raises(EventoYaActivo):
        emitir(sesion, _sismo(), firmador, HORA, CONFIG, ZONA)


def test_pedidos_previos_a_la_alerta_se_suman_al_evento(
    sesion: Session, firmador: Firmador
) -> None:
    poblar_ciudad(sesion)
    _pedidos(sesion, 2, id_h3=EPICENTRO)
    resultado = emitir(sesion, _sismo(), firmador, HORA, CONFIG, ZONA)
    reporte = sesion.get_one(Reporte, f"m{EPICENTRO}0")
    assert reporte.id_evento == resultado.evento.id
