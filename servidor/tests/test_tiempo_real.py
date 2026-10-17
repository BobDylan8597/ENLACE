"""WebSocket: la web y la app reciben los cambios en tiempo real."""

from typing import Any

import pytest
from starlette.testclient import WebSocketTestSession
from starlette.websockets import WebSocketDisconnect

from app.activacion.firma import verificar
from tests.conftest import Entorno
from tests.test_api import ADMIN, SISMO, _ayuda

CLAVE = ADMIN["X-Clave-Admin"]


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


def test_unidad_y_ciudadano_reciben_la_asignacion(entorno: Entorno) -> None:
    entorno.cliente.post("/eventos", json=SISMO, headers=ADMIN)
    pedido = _ayuda(id_h3=None, lat=12.74, lon=-86.25, atrapados=2)
    entorno.cliente.post("/reportes", json=pedido)
    recomendacion = next(
        r
        for r in entorno.cliente.get("/recomendaciones", headers=ADMIN).json()
        if r["id_reporte"] == pedido["id_mensaje"]
    )
    unidad = f"/ws/movil/equipo-{recomendacion['id_equipo']}"
    ciudadano = f"/ws/movil/{pedido['id_anonimo']}"
    with entorno.cliente.websocket_connect(unidad) as ws_unidad:
        with entorno.cliente.websocket_connect(ciudadano) as ws_ciudadano:
            entorno.cliente.post(
                f"/asignaciones/{recomendacion['id']}/decision",
                json={"decision": "aceptada", "decidido_por": "Coordinadora"},
                headers=ADMIN,
            )
            asignacion = _esperar(ws_unidad, "asignacion")
            assert asignacion["ubicacion"] == {"lat": 12.74, "lon": -86.25}
            assert asignacion["destino"] == asignacion["ubicacion"]
            aviso = _esperar(ws_ciudadano, "ayuda_en_camino")
            assert aviso["equipo"] == recomendacion["nombre_equipo"]
