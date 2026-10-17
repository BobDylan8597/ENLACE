"""WebSocket: la web y la app reciben los cambios en tiempo real."""

from typing import Any

import pytest
from starlette.testclient import WebSocketTestSession
from starlette.websockets import WebSocketDisconnect

from app.activacion.firma import verificar
from app.config import obtener_config
from tests.conftest import Entorno
from tests.test_api import ADMIN, SISMO, _ayuda

CLAVE = ADMIN["X-Clave-Admin"]
CLAVE_BRIGADA = obtener_config().servidor.clave_brigada


def _esperar(ws: WebSocketTestSession, tipo: str, intentos: int = 20) -> dict[str, Any]:
    for _ in range(intentos):
        mensaje = ws.receive_json()
        if mensaje["tipo"] == tipo:
            return mensaje["datos"]
    raise AssertionError(f"No llegó un mensaje {tipo}")


def test_admin_sin_clave_no_se_conecta(entorno: Entorno) -> None:
    with pytest.raises(WebSocketDisconnect):
        with entorno.cliente.websocket_connect("/ws/admin?clave=otra") as ws:
            ws.receive_json()


def test_admin_recibe_alerta_y_celdas(entorno: Entorno) -> None:
    with entorno.cliente.websocket_connect(f"/ws/admin?clave={CLAVE}") as ws:
        assert ws.receive_json()["tipo"] == "conectado"
        entorno.cliente.post("/eventos", json=SISMO, headers=ADMIN)
        assert _esperar(ws, "evento_emitido")["evento"]["tipo"] == "sismo"
        assert _esperar(ws, "celda_actualizada")["celdas"]


def test_movil_recibe_alerta_firmada_al_emitir_y_al_conectarse_tarde(entorno: Entorno) -> None:
    clave = entorno.firmador.clave_publica_base64()
    with entorno.cliente.websocket_connect("/ws/movil/telefono-1") as ws:
        entorno.cliente.post("/eventos", json=SISMO, headers=ADMIN)
        assert verificar(_esperar(ws, "alerta"), clave)
    with entorno.cliente.websocket_connect("/ws/movil/telefono-2") as tarde:
        assert verificar(_esperar(tarde, "alerta"), clave)


def test_movil_sin_alerta_activa_recibe_el_aviso(entorno: Entorno) -> None:
    """Quien estuvo desconectado al cerrarse el evento deja de mostrar la alerta vieja."""
    with entorno.cliente.websocket_connect("/ws/movil/telefono-1") as ws:
        assert ws.receive_json() == {"tipo": "sin_alerta_activa", "datos": {}}


def test_unidad_y_ciudadano_reciben_la_asignacion(entorno: Entorno) -> None:
    entorno.cliente.post("/eventos", json=SISMO, headers=ADMIN)
    pedido = _ayuda(id_h3=None, lat=12.74, lon=-86.25, atrapados=2)
    entorno.cliente.post("/reportes", json=pedido)
    recomendacion = next(
        r
        for r in entorno.cliente.get("/recomendaciones", headers=ADMIN).json()
        if r["id_reporte"] == pedido["id_mensaje"]
    )
    unidad = f"/ws/movil/equipo-{recomendacion['id_equipo']}?clave={CLAVE_BRIGADA}"
    ciudadano = f"/ws/movil/{pedido['id_anonimo']}"
    with entorno.cliente.websocket_connect(unidad) as ws_unidad:
        with entorno.cliente.websocket_connect(ciudadano) as ws_ciudadano:
            entorno.cliente.post(
                f"/asignaciones/{recomendacion['id']}/decision",
                json={"decision": "aceptada", "decidido_por": "Coordinadora"},
                headers=ADMIN,
            )
            asignacion = _esperar(ws_unidad, "asignacion")
            # El destino de la unidad es el punto exacto de quien pidió ayuda
            assert asignacion["destino"] == {"lat": 12.74, "lon": -86.25}
            aviso = _esperar(ws_ciudadano, "ayuda_en_camino")
            assert aviso["equipo"] == recomendacion["nombre_equipo"]


def test_websocket_de_unidad_exige_clave_de_brigada(entorno: Entorno) -> None:
    with pytest.raises(WebSocketDisconnect):
        with entorno.cliente.websocket_connect("/ws/movil/equipo-1") as ws:
            ws.receive_json()
    with entorno.cliente.websocket_connect(f"/ws/movil/equipo-1?clave={CLAVE_BRIGADA}"):
        pass


def _pedido_asignado(entorno: Entorno) -> tuple[dict[str, Any], dict[str, Any]]:
    """Pedido con contacto, y la recomendación del motor para él (todavía sin decidir)."""
    entorno.cliente.post("/eventos", json=SISMO, headers=ADMIN)
    pedido = _ayuda(
        id_h3=None,
        lat=12.74,
        lon=-86.25,
        atrapados=2,
        vulnerables=1,
        necesidades=["rescate", "medico"],
        identidad={"nombre": "Ana", "telefono": "+50588880000", "condiciones": "Adulto mayor"},
    )
    entorno.cliente.post("/reportes", json=pedido)
    recomendacion = next(
        r
        for r in entorno.cliente.get("/recomendaciones", headers=ADMIN).json()
        if r["id_reporte"] == pedido["id_mensaje"]
    )
    return pedido, recomendacion


def _aceptar(entorno: Entorno, recomendacion: dict[str, Any]) -> None:
    entorno.cliente.post(
        f"/asignaciones/{recomendacion['id']}/decision",
        json={"decision": "aceptada", "decidido_por": "Coordinadora"},
        headers=ADMIN,
    )


def test_la_unidad_recibe_que_atender_y_el_contacto(entorno: Entorno) -> None:
    pedido, recomendacion = _pedido_asignado(entorno)
    unidad = f"/ws/movil/equipo-{recomendacion['id_equipo']}?clave={CLAVE_BRIGADA}"
    with entorno.cliente.websocket_connect(unidad) as ws:
        _aceptar(entorno, recomendacion)
        aviso = _esperar(ws, "asignacion")
    assert aviso["pedido"]["atrapados"] == 2 and aviso["pedido"]["vulnerables"] == 1
    assert aviso["pedido"]["necesidades"] == ["rescate", "medico"]
    assert aviso["contacto"] == {
        "nombre": "Ana",
        "telefono": "+50588880000",
        "condiciones": "Adulto mayor",
    }
    assert aviso["nombre_zona"]
    # El identificador de la persona no le hace falta a la unidad
    assert "id_anonimo" not in aviso


def test_unidad_que_se_reconecta_recibe_su_asignacion_en_curso(entorno: Entorno) -> None:
    pedido, recomendacion = _pedido_asignado(entorno)
    _aceptar(entorno, recomendacion)
    unidad = f"/ws/movil/equipo-{recomendacion['id_equipo']}?clave={CLAVE_BRIGADA}"
    with entorno.cliente.websocket_connect(unidad) as ws:
        aviso = _esperar(ws, "asignacion")
    assert aviso["asignacion"]["id"] == recomendacion["id"]
    assert aviso["asignacion"]["id_reporte"] == pedido["id_mensaje"]
    # Terminada la asignación, al reconectarse ya no la recibe
    entorno.cliente.post(
        f"/asignaciones/{recomendacion['id']}/atendida",
        headers={"X-Clave-Brigada": CLAVE_BRIGADA},
    )
    with entorno.cliente.websocket_connect(unidad) as ws:
        assert ws.receive_json()["tipo"] == "alerta"
        assert ws.receive_json() == {"tipo": "sin_asignacion", "datos": {}}


def test_mapa_de_la_brigada_sin_datos_personales(entorno: Entorno) -> None:
    pedido, _ = _pedido_asignado(entorno)
    brigada = {"X-Clave-Brigada": CLAVE_BRIGADA}
    assert entorno.cliente.get("/pedidos").status_code == 401
    (punto,) = entorno.cliente.get("/pedidos", headers=brigada).json()
    assert punto["id_mensaje"] == pedido["id_mensaje"]
    assert (punto["lat"], punto["lon"], punto["atrapados"]) == (12.74, -86.25, 2)
    assert not {"nombre", "telefono", "condiciones", "id_anonimo"} & set(punto)
    # La brigada puede elegir su equipo
    assert entorno.cliente.get("/equipos", headers=brigada).status_code == 200
    assert entorno.cliente.get("/equipos").status_code == 401
