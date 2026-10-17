"""Pruebas de la API de punta a punta, siguiendo el flujo de la demostración (sección 17)."""

import uuid
from typing import Any

import h3
from sqlalchemy import select

from app.activacion.firma import verificar
from app.ciudad import obtener_ciudad
from app.config import obtener_config
from app.modelos.tablas import Identidad
from tests.ciudad_prueba import CENTRO, EPICENTRO, HORA, LEJANA
from tests.conftest import Entorno

ADMIN = {"X-Clave-Admin": obtener_config().servidor.clave_admin}
SISMO = {
    "fuente": "manual",
    "tipo": "sismo",
    "lat": CENTRO[0],
    "lon": CENTRO[1],
    "magnitud": 6.2,
    "profundidad_km": 10,
}


def _id() -> str:
    return str(uuid.uuid4())


def _emitir(entorno: Entorno) -> dict[str, Any]:
    respuesta = entorno.cliente.post("/eventos", json=SISMO, headers=ADMIN)
    assert respuesta.status_code == 201, respuesta.text
    return respuesta.json()


def _ayuda(**campos: Any) -> dict[str, Any]:
    reporte = {
        "id_mensaje": _id(),
        "id_anonimo": _id(),
        "origen": "app",
        "tipo": "ayuda",
        "id_h3": EPICENTRO,
        "atrapados": 1,
        "necesidades": ["rescate"],
    }
    reporte.update(campos)
    return reporte


# ── Seguridad y alertas ────────────────────────────────────


def test_rutas_de_admin_exigen_clave(entorno: Entorno) -> None:
    c = entorno.cliente
    assert c.post("/eventos", json=SISMO).status_code == 401
    assert c.get("/reportes", headers={"X-Clave-Admin": "otra"}).status_code == 401
    assert c.get("/equipos", headers=ADMIN).status_code == 200


def test_emitir_alerta_firmada_y_no_duplicar(entorno: Entorno) -> None:
    emision = _emitir(entorno)
    clave = entorno.cliente.get("/alertas/clave-publica").json()["clave_publica"]
    assert verificar(emision["alerta"], clave)
    assert emision["evento"]["zona_afectada"] == [EPICENTRO]
    assert entorno.cliente.post("/eventos", json=SISMO, headers=ADMIN).status_code == 409
    activa = entorno.cliente.get("/alertas/activa").json()
    assert verificar(activa, clave)


def test_cap_de_ejercicio_no_emite(entorno: Entorno) -> None:
    xml = "<alert xmlns='urn:oasis:names:tc:emergency:cap:1.2'><status>Exercise</status></alert>"
    respuesta = entorno.cliente.post("/eventos", json={"fuente": "cap", "xml": xml}, headers=ADMIN)
    assert respuesta.status_code == 422


# ── Check-ins (privacidad) ─────────────────────────────────


def test_checkin_con_coordenadas_se_rechaza(entorno: Entorno) -> None:
    evento = _emitir(entorno)["evento"]
    respuesta = entorno.cliente.post(
        "/checkins",
        json={
            "id_anonimo": _id(),
            "id_evento": evento["id"],
            "id_h3": EPICENTRO,
            "lat": 1.0,
            "lon": 2.0,
        },
    )
    assert respuesta.status_code == 422


def test_checkins_llenan_los_totales_por_celda(entorno: Entorno) -> None:
    evento = _emitir(entorno)["evento"]
    for estado in ("bien", "bien", "ayuda"):
        respuesta = entorno.cliente.post(
            "/checkins",
            json={
                "id_anonimo": _id(),
                "id_evento": evento["id"],
                "id_h3": EPICENTRO,
                "estado": estado,
            },
        )
        assert respuesta.status_code == 202
    celdas = {c["id_h3"]: c for c in entorno.cliente.get("/celdas").json()}
    assert celdas[EPICENTRO]["checkins"] == {"sin_respuesta": 0, "bien": 2, "ayuda": 1}


# ── Reportes ───────────────────────────────────────────────


def test_lote_de_mula_acepta_buenos_rechaza_malos_e_ignora_repetidos(entorno: Entorno) -> None:
    _emitir(entorno)
    bueno = _ayuda()
    lote = [bueno, {"id_mensaje": "roto-123", "tipo": "ayuda"}, _ayuda(saltos_nearby=99)]
    resultado = entorno.cliente.post("/reportes", json=lote).json()
    assert resultado["aceptados"] == [bueno["id_mensaje"]]
    assert {r["id_mensaje"] for r in resultado["rechazados"]} == {"roto-123", lote[2]["id_mensaje"]}
    otra_vez = entorno.cliente.post("/reportes", json=bueno).json()
    assert otra_vez["repetidos"] == [bueno["id_mensaje"]]


def test_punto_exacto_solo_para_pedidos_de_ayuda(entorno: Entorno) -> None:
    _emitir(entorno)
    lat, lon = CENTRO[0] + 0.001, CENTRO[1] + 0.001
    ayuda = _ayuda(
        id_h3=None, lat=lat, lon=lon, identidad={"nombre": "Ana", "telefono": "+50588880000"}
    )
    bien = {
        "id_mensaje": _id(),
        "id_anonimo": _id(),
        "origen": "app",
        "tipo": "bien",
        "lat": lat,
        "lon": lon,
    }
    entorno.cliente.post("/reportes", json=[ayuda, bien])

    bandeja = {r["id_mensaje"]: r for r in entorno.cliente.get("/reportes", headers=ADMIN).json()}
    assert bandeja[ayuda["id_mensaje"]]["ubicacion"] == {"lat": lat, "lon": lon}
    assert bandeja[bien["id_mensaje"]]["ubicacion"] is None
    assert bandeja[bien["id_mensaje"]]["id_h3"] == h3.latlng_to_cell(lat, lon, 8)
    with entorno.fabrica() as s:
        assert [i.id_anonimo for i in s.scalars(select(Identidad))] == [ayuda["id_anonimo"]]

    detalle = entorno.cliente.get(f"/reportes/{ayuda['id_mensaje']}", headers=ADMIN).json()
    assert detalle["identidad"]["nombre"] == "Ana"


def test_datos_personales_solo_al_pedir_ayuda(entorno: Entorno) -> None:
    reporte = {
        "id_mensaje": _id(),
        "id_anonimo": _id(),
        "origen": "app",
        "tipo": "bien",
        "id_h3": EPICENTRO,
        "identidad": {"nombre": "Ana"},
    }
    resultado = entorno.cliente.post("/reportes", json=reporte).json()
    assert resultado["aceptados"] == [] and len(resultado["rechazados"]) == 1


def test_bandeja_ordenada_por_prioridad(entorno: Entorno) -> None:
    _emitir(entorno)
    leve = _ayuda(atrapados=0, personas=1, necesidades=[])
    grave = _ayuda(atrapados=3, heridos=2)
    entorno.cliente.post("/reportes", json=[leve, grave])
    bandeja = entorno.cliente.get("/reportes", headers=ADMIN).json()
    assert [r["id_mensaje"] for r in bandeja] == [grave["id_mensaje"], leve["id_mensaje"]]
    assert bandeja[0]["prioridad"] > bandeja[1]["prioridad"]


# ── SMS ────────────────────────────────────────────────────


def test_sms_crea_pedido_de_ayuda_y_no_se_duplica(entorno: Entorno) -> None:
    _emitir(entorno)
    sms = {
        "remitente": "+50588881234",
        "texto": f"ENL {CENTRO[0]},{CENTRO[1]} P4 A1 H1 RM",
        "hora": HORA.isoformat(),
    }
    primero = entorno.cliente.post("/sms/entrante", json=sms).json()
    segundo = entorno.cliente.post("/sms/entrante", json=sms).json()
    assert len(primero["aceptados"]) == 1 and segundo["repetidos"] == primero["aceptados"]
    detalle = entorno.cliente.get(f"/reportes/{primero['aceptados'][0]}", headers=ADMIN).json()
    assert (detalle["origen"], detalle["atrapados"], detalle["necesidades"]) == (
        "sms",
        1,
        ["rescate", "medico"],
    )
    assert detalle["identidad"]["telefono"] == "+50588881234"


def test_sms_mal_formado_se_rechaza(entorno: Entorno) -> None:
    respuesta = entorno.cliente.post("/sms/entrante", json={"remitente": "+505", "texto": "hola"})
    assert respuesta.status_code == 422


# ── Recomendaciones y decisiones ───────────────────────────


def _recomendacion_para(entorno: Entorno, id_mensaje: str) -> dict[str, Any]:
    recomendaciones = entorno.cliente.get("/recomendaciones", headers=ADMIN).json()
    return next(r for r in recomendaciones if r["id_reporte"] == id_mensaje)


def test_aceptar_y_atender_una_recomendacion(entorno: Entorno) -> None:
    _emitir(entorno)
    pedido = _ayuda(id_h3=LEJANA, atrapados=2)
    entorno.cliente.post("/reportes", json=pedido)
    recomendacion = _recomendacion_para(entorno, pedido["id_mensaje"])
    assert (
        recomendacion["nombre_equipo"] == "Rescate 1" and "2 atrapados" in recomendacion["motivo"]
    )

    decision = entorno.cliente.post(
        f"/asignaciones/{recomendacion['id']}/decision",
        json={"decision": "aceptada", "decidido_por": "Coordinadora"},
        headers=ADMIN,
    )
    assert decision.json()["decision"] == "aceptada"
    equipos = {e["nombre"]: e for e in entorno.cliente.get("/equipos", headers=ADMIN).json()}
    assert equipos["Rescate 1"]["estado"] == "en_ruta"
    # Decidir dos veces no se permite
    repetida = entorno.cliente.post(
        f"/asignaciones/{recomendacion['id']}/decision",
        json={"decision": "rechazada", "decidido_por": "Otra"},
        headers=ADMIN,
    )
    assert repetida.status_code == 409

    entorno.reloj.avanzar(minutes=40)
    atendida = entorno.cliente.post(f"/asignaciones/{recomendacion['id']}/atendida", headers=ADMIN)
    assert atendida.status_code == 200
    detalle = entorno.cliente.get(f"/reportes/{pedido['id_mensaje']}", headers=ADMIN).json()
    assert detalle["estado"] == "atendido"
    equipos = {e["nombre"]: e for e in entorno.cliente.get("/equipos", headers=ADMIN).json()}
    assert equipos["Rescate 1"]["estado"] == "disponible"


def test_modificar_exige_equipo_y_lo_cambia(entorno: Entorno) -> None:
    _emitir(entorno)
    nuevo = entorno.cliente.post(
        "/equipos",
        json={
            "nombre": "Rescate 2",
            "organismo": "Cruz Roja",
            "capacidades": ["rescate"],
            "lat": CENTRO[0],
            "lon": CENTRO[1],
        },
        headers=ADMIN,
    ).json()
    pedido = _ayuda(id_h3=LEJANA, atrapados=1)
    entorno.cliente.post("/reportes", json=pedido)
    recomendacion = _recomendacion_para(entorno, pedido["id_mensaje"])
    ruta = f"/asignaciones/{recomendacion['id']}/decision"
    sin_equipo = entorno.cliente.post(
        ruta, json={"decision": "modificada", "decidido_por": "X"}, headers=ADMIN
    )
    assert sin_equipo.status_code == 422
    otro = nuevo if recomendacion["id_equipo"] != nuevo["id"] else None
    assert otro is not None
    cambiada = entorno.cliente.post(
        ruta,
        json={"decision": "modificada", "decidido_por": "X", "id_equipo": otro["id"]},
        headers=ADMIN,
    ).json()
    assert cambiada["decision"] == "modificada" and cambiada["id_equipo"] == otro["id"]
    assert "Cambiado por X" in cambiada["motivo"]


# ── Propuesta comunitaria ──────────────────────────────────


def test_pedidos_sin_alerta_generan_propuesta_que_se_aprueba(entorno: Entorno) -> None:
    for _ in range(obtener_config().activacion.pedidos_minimos_comunitaria):
        entorno.cliente.post("/reportes", json=_ayuda(id_h3=LEJANA, hora=HORA.isoformat()))
    (propuesta,) = entorno.cliente.get("/propuestas", headers=ADMIN).json()
    assert propuesta["id_h3"] == LEJANA
    emision = entorno.cliente.post(
        "/eventos", json={"fuente": "comunitaria", "id_propuesta": propuesta["id"]}, headers=ADMIN
    )
    assert emision.status_code == 201
    assert LEJANA in emision.json()["evento"]["zona_afectada"]
    # Los pedidos previos ya forman parte del evento
    assert len(entorno.cliente.get("/reportes", headers=ADMIN).json()) == 4
    assert entorno.cliente.get("/propuestas", headers=ADMIN).json() == []


# ── Ciudad y mapa ──────────────────────────────────────────


def test_ciudad_activa_para_centrar_el_mapa(entorno: Entorno) -> None:
    ciudad = entorno.cliente.get("/ciudad").json()
    activa = obtener_ciudad()
    assert ciudad["nombre"] == activa.nombre
    assert ciudad["centro"] == {"lat": activa.centro.lat, "lon": activa.centro.lon}
    assert ciudad["resolucion_h3"] == obtener_config().h3.resolucion
    assert "OpenStreetMap" in ciudad["atribucion_mapa"]


def test_mapa_base_se_sirve_por_rangos(entorno: Entorno) -> None:
    if not entorno.cliente.get("/ciudad").json()["mapa_disponible"]:
        assert entorno.cliente.get("/mapa/base.pmtiles").status_code == 404
        return
    parcial = entorno.cliente.get("/mapa/base.pmtiles", headers={"Range": "bytes=0-6"})
    assert parcial.status_code == 206 and parcial.content == b"PMTiles"


# ── Clave de brigada ───────────────────────────────────────

BRIGADA = {"X-Clave-Brigada": obtener_config().servidor.clave_brigada}


def _evaluacion() -> dict[str, Any]:
    return {
        "id_mensaje": _id(),
        "id_anonimo": "equipo-1",
        "origen": "brigada",
        "tipo": "evaluacion",
        "id_h3": EPICENTRO,
        "nivel_dano": "grave",
    }


def test_reporte_de_brigada_exige_su_clave(entorno: Entorno) -> None:
    _emitir(entorno)
    sin_clave = entorno.cliente.post("/reportes", json=_evaluacion()).json()
    assert sin_clave["aceptados"] == [] and "brigada" in sin_clave["rechazados"][0]["error"]
    con_clave = entorno.cliente.post("/reportes", json=_evaluacion(), headers=BRIGADA).json()
    assert len(con_clave["aceptados"]) == 1


def test_clave_de_brigada_no_abre_el_panel_admin(entorno: Entorno) -> None:
    assert entorno.cliente.get("/reportes", headers=BRIGADA).status_code == 401
    assert entorno.cliente.get("/recomendaciones", headers=BRIGADA).status_code == 401
    respuesta = entorno.cliente.post(
        "/equipos/1/ubicacion", json={"lat": 12.1, "lon": -86.2}, headers=BRIGADA
    )
    assert respuesta.status_code == 200


def test_geometria_permite_ubicar_la_celda_sin_enviar_coordenadas(entorno: Entorno) -> None:
    from shapely.geometry import Point, Polygon

    geometria = entorno.cliente.get("/celdas/geometria").json()
    assert geometria["resolucion"] == obtener_config().h3.resolucion
    lat, lon = CENTRO[0] + 0.003, CENTRO[1] - 0.002
    # Lo que hará el celular: buscar el contorno que contiene su punto
    dentro = [
        c["id_h3"]
        for c in geometria["celdas"]
        if Polygon([(lo, la) for la, lo in c["contorno"]]).contains(Point(lon, lat))
    ]
    assert dentro == [h3.latlng_to_cell(lat, lon, geometria["resolucion"])]


# ── Seguimiento del pedido (U12) ───────────────────────────


def test_seguimiento_del_pedido_sin_datos_personales(entorno: Entorno) -> None:
    _emitir(entorno)
    pedido = _ayuda(id_h3=LEJANA, atrapados=2, identidad=None)
    assert entorno.cliente.get(f"/seguimiento/{_id()}").status_code == 404
    entorno.cliente.post("/reportes", json=pedido)
    antes = entorno.cliente.get(f"/seguimiento/{pedido['id_mensaje']}").json()
    assert antes == {"estado": "sin_asignar", "equipo": None, "tiempo_estimado_min": None}

    recomendacion = _recomendacion_para(entorno, pedido["id_mensaje"])
    entorno.cliente.post(
        f"/asignaciones/{recomendacion['id']}/decision",
        json={"decision": "aceptada", "decidido_por": "Coordinadora"},
        headers=ADMIN,
    )
    despues = entorno.cliente.get(f"/seguimiento/{pedido['id_mensaje']}").json()
    assert despues["estado"] == "asignado" and despues["equipo"] == recomendacion["nombre_equipo"]
    assert despues["tiempo_estimado_min"] > 0
    assert set(despues) == {"estado", "equipo", "tiempo_estimado_min"}
