"""Reglas obligatorias de la sección 13."""

import uuid
from datetime import timedelta

from sqlalchemy import select

from app.config import obtener_config
from app.modelos.tablas import Celda, CheckIn, Identidad, Reporte, UbicacionAlerta
from app.privacidad.borrado import borrar_vencidos
from tests.ciudad_prueba import CENTRO, EPICENTRO
from tests.test_api import ADMIN, SISMO, Entorno

DIAS = obtener_config().privacidad.dias_borrado


def _pedido_con_identidad(entorno: Entorno) -> str:
    id_mensaje = str(uuid.uuid4())
    entorno.cliente.post(
        "/reportes",
        json={
            "id_mensaje": id_mensaje,
            "id_anonimo": str(uuid.uuid4()),
            "origen": "app",
            "tipo": "ayuda",
            "lat": CENTRO[0],
            "lon": CENTRO[1],
            "atrapados": 1,
            "identidad": {"nombre": "Ana", "telefono": "+50588880000"},
        },
    )
    return id_mensaje


def test_cerrar_evento_programa_y_ejecuta_el_borrado(entorno: Entorno) -> None:
    evento = entorno.cliente.post("/eventos", json=SISMO, headers=ADMIN).json()["evento"]
    id_mensaje = _pedido_con_identidad(entorno)

    cerrado = entorno.cliente.post(f"/eventos/{evento['id']}/cerrar", headers=ADMIN)
    assert cerrado.status_code == 200 and cerrado.json()["estado"] == "cerrado"
    assert entorno.cliente.post(f"/eventos/{evento['id']}/cerrar", headers=ADMIN).status_code == 409

    with entorno.fabrica() as s:
        (identidad,) = s.scalars(select(Identidad))
        assert identidad.fecha_borrado == entorno.reloj.ahora + timedelta(days=DIAS)
        # Antes del plazo no se borra nada
        assert borrar_vencidos(s, entorno.reloj.ahora + timedelta(days=DIAS - 1)) == 0
        assert borrar_vencidos(s, entorno.reloj.ahora + timedelta(days=DIAS)) == 1
        s.commit()

    with entorno.fabrica() as s:
        # Los datos personales desaparecen; el reporte anónimo y la celda se conservan
        assert list(s.scalars(select(Identidad))) == []
        reporte = s.get_one(Reporte, id_mensaje)
        assert reporte.atrapados == 1 and s.get(Celda, EPICENTRO) is not None


def test_identidad_sin_evento_tiene_plazo_provisional(entorno: Entorno) -> None:
    _pedido_con_identidad(entorno)
    with entorno.fabrica() as s:
        (identidad,) = s.scalars(select(Identidad))
        assert identidad.id_evento is None
        assert identidad.fecha_borrado == entorno.reloj.ahora + timedelta(days=DIAS)


def test_identidad_previa_pasa_al_evento_al_emitir(entorno: Entorno) -> None:
    _pedido_con_identidad(entorno)
    evento = entorno.cliente.post("/eventos", json=SISMO, headers=ADMIN).json()["evento"]
    with entorno.fabrica() as s:
        (identidad,) = s.scalars(select(Identidad))
        assert identidad.id_evento == evento["id"] and identidad.fecha_borrado is None


def test_celdas_no_exponen_puntos_exactos(entorno: Entorno) -> None:
    entorno.cliente.post("/eventos", json=SISMO, headers=ADMIN)
    _pedido_con_identidad(entorno)
    for celda in entorno.cliente.get("/celdas").json():
        assert not {"lat", "lon", "ubicacion"} & set(celda)
    detalle = entorno.cliente.get(f"/celdas/{EPICENTRO}").json()
    assert not {"lat", "lon", "ubicacion"} & set(detalle)


# ── Ubicación al llegar la alerta ──────────────────────────

BRIGADA = {"X-Clave-Brigada": obtener_config().servidor.clave_brigada}


def _checkin(
    entorno: Entorno, id_evento: int, id_anonimo: str, ubicacion: bool = True, **campos: object
) -> None:
    cuerpo: dict[str, object] = {
        "id_anonimo": id_anonimo,
        "id_evento": id_evento,
        "id_h3": EPICENTRO,
        **campos,
    }
    if ubicacion:
        cuerpo["ubicacion"] = {"lat": CENTRO[0], "lon": CENTRO[1]}
    respuesta = entorno.cliente.post("/checkins", json=cuerpo)
    assert respuesta.status_code == 202, respuesta.text


def test_conteos_del_hogar_forman_la_poblacion_critica(entorno: Entorno) -> None:
    evento = entorno.cliente.post("/eventos", json=SISMO, headers=ADMIN).json()["evento"]
    _checkin(entorno, evento["id"], str(uuid.uuid4()), False, personas=4, vulnerables=2)
    _checkin(
        entorno, evento["id"], str(uuid.uuid4()), False, personas=3, vulnerables=1, estado="bien"
    )
    totales = entorno.cliente.get(f"/celdas/{EPICENTRO}").json()["checkins"]
    # Quien avisó que está bien no cuenta como población crítica
    assert (totales["personas"], totales["vulnerables"], totales["criticos"]) == (7, 3, 2)


def test_poblacion_critica_sube_la_prioridad_de_la_celda(entorno: Entorno) -> None:
    evento = entorno.cliente.post("/eventos", json=SISMO, headers=ADMIN).json()["evento"]
    antes = entorno.cliente.get(f"/celdas/{EPICENTRO}").json()
    for _ in range(5):
        _checkin(entorno, evento["id"], str(uuid.uuid4()), False, personas=3, vulnerables=2)
    despues = entorno.cliente.get(f"/celdas/{EPICENTRO}").json()
    assert (
        despues["componentes_prioridad"]["vulnerabilidad"]
        > antes["componentes_prioridad"]["vulnerabilidad"]
    )
    assert despues["prioridad"] > antes["prioridad"]


def test_vulnerables_no_superan_a_las_personas(entorno: Entorno) -> None:
    evento = entorno.cliente.post("/eventos", json=SISMO, headers=ADMIN).json()["evento"]
    respuesta = entorno.cliente.post(
        "/checkins",
        json={
            "id_anonimo": str(uuid.uuid4()),
            "id_evento": evento["id"],
            "id_h3": EPICENTRO,
            "personas": 1,
            "vulnerables": 2,
        },
    )
    assert respuesta.status_code == 422


def test_ubicaciones_exactas_solo_para_brigada_y_admin(entorno: Entorno) -> None:
    evento = entorno.cliente.post("/eventos", json=SISMO, headers=ADMIN).json()["evento"]
    _checkin(entorno, evento["id"], str(uuid.uuid4()), personas=2, vulnerables=1)
    _checkin(entorno, evento["id"], str(uuid.uuid4()), ubicacion=False)
    assert entorno.cliente.get("/ubicaciones").status_code == 401
    (punto,) = entorno.cliente.get("/ubicaciones", headers=BRIGADA).json()
    assert entorno.cliente.get("/ubicaciones", headers=ADMIN).json() == [punto]
    assert (punto["lat"], punto["lon"]) == CENTRO
    assert (punto["estado"], punto["personas"], punto["vulnerables"]) == ("sin_respuesta", 2, 1)
    # Sin nada que identifique a la persona
    assert "id_anonimo" not in punto
    # Los totales por celda siguen sin coordenadas
    assert not {"lat", "lon", "ubicacion"} & set(entorno.cliente.get(f"/celdas/{EPICENTRO}").json())


def test_estoy_bien_actualiza_el_punto_y_sin_punto_se_borra(entorno: Entorno) -> None:
    evento = entorno.cliente.post("/eventos", json=SISMO, headers=ADMIN).json()["evento"]
    persona = str(uuid.uuid4())
    _checkin(entorno, evento["id"], persona)
    _checkin(entorno, evento["id"], persona, estado="bien")
    (punto,) = entorno.cliente.get("/ubicaciones", headers=BRIGADA).json()
    assert punto["estado"] == "bien"
    # La persona dejó de compartir su ubicación exacta: el punto desaparece
    _checkin(entorno, evento["id"], persona, ubicacion=False, estado="bien")
    assert entorno.cliente.get("/ubicaciones", headers=BRIGADA).json() == []


def test_cerrar_evento_borra_los_puntos_exactos(entorno: Entorno) -> None:
    evento = entorno.cliente.post("/eventos", json=SISMO, headers=ADMIN).json()["evento"]
    _checkin(entorno, evento["id"], str(uuid.uuid4()), personas=2, vulnerables=1)
    entorno.cliente.post(f"/eventos/{evento['id']}/cerrar", headers=ADMIN)
    with entorno.fabrica() as s:
        assert list(s.scalars(select(UbicacionAlerta))) == []
        # El agregado anónimo se conserva
        (checkin,) = s.scalars(select(CheckIn))
        assert (checkin.id_h3, checkin.vulnerables) == (EPICENTRO, 1)
