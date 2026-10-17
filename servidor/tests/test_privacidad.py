"""Reglas obligatorias de la sección 13."""

import uuid
from datetime import timedelta

from sqlalchemy import select

from app.config import obtener_config
from app.modelos.tablas import Celda, Identidad, Reporte
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
