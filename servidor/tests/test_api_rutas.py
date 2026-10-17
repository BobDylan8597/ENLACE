"""Bloqueos y rutas por la API, siguiendo la demo (sección 17, punto 4):
la unidad recibe su ruta → alguien reporta un bloqueo en ella → se recalcula y se avisa."""

import uuid
from typing import Any

import pytest

from app.config import obtener_config
from tests.ciudad_prueba import CENTRO
from tests.conftest import Entorno
from tests.red_prueba import nodo, punto, red_cuadricula
from tests.test_api import ADMIN, SISMO, _ayuda
from tests.test_tiempo_real import CLAVE_BRIGADA, _esperar

RUTAS = obtener_config().rutas
# Cuadrícula 5 × 5 centrada en la ciudad de prueba: el cruce (2, 2) es el centro,
# "Rescate 1" (CENTRO + 0,02° de latitud) está en el cruce (4, 2)
LAT0, LON0, PASO = CENTRO[0] - 0.02, CENTRO[1] - 0.02, 0.01


def _punto(fila: float, columna: float) -> tuple[float, float]:
    return punto(fila, columna, LAT0, LON0, PASO)  # type: ignore[arg-type]


@pytest.fixture
def con_red(entorno: Entorno) -> Entorno:
    entorno.usar_red(red_cuadricula(5, 5, LAT0, LON0, PASO))
    return entorno


def _bloqueo(entorno: Entorno, lat: float, lon: float, **campos: Any) -> dict[str, Any]:
    cuerpo = {"lat": lat, "lon": lon, "tipo": "derrumbe", "reportante": str(uuid.uuid4())}
    cuerpo.update(campos)
    cabeceras = (
        ADMIN if campos.get("origen", "app") != "app" or campos.get("accion") == "descartar" else {}
    )
    respuesta = entorno.cliente.post("/bloqueos", json=cuerpo, headers=cabeceras)
    assert respuesta.status_code == 200, respuesta.text
    return respuesta.json()


def _aceptar_rescate(entorno: Entorno) -> dict[str, Any]:
    """Un pedido de ayuda en el centro, aceptado para el equipo de rescate."""
    entorno.cliente.post("/eventos", json=SISMO, headers=ADMIN)
    lat, lon = _punto(2, 2)
    pedido = _ayuda(id_h3=None, lat=lat, lon=lon, atrapados=2)
    entorno.cliente.post("/reportes", json=pedido)
    recomendacion = next(
        r
        for r in entorno.cliente.get("/recomendaciones", headers=ADMIN).json()
        if r["id_reporte"] == pedido["id_mensaje"]
    )
    respuesta = entorno.cliente.post(
        f"/asignaciones/{recomendacion['id']}/decision",
        json={"decision": "aceptada", "decidido_por": "Coordinadora"},
        headers=ADMIN,
    )
    return respuesta.json()


def _nodos(ruta: dict[str, Any]) -> list[int]:
    tramos = ruta["properties"]["tramos"]
    return [tramos[0][0], *(v for _, v, _ in tramos)]


# ── Estados del bloqueo ────────────────────────────────────


def test_misma_persona_no_confirma_dos_veces(con_red: Entorno) -> None:
    lat, lon = _punto(3.5, 2)
    primero = _bloqueo(con_red, lat, lon, reportante="persona-1")["bloqueo"]
    otra_vez = _bloqueo(con_red, lat, lon, reportante="persona-1")["bloqueo"]
    assert primero["id"] == otra_vez["id"]
    assert otra_vez["confirmaciones"] == 1 and otra_vez["estado"] == "sin_confirmar"


def test_estados_segun_personas_distintas(con_red: Entorno) -> None:
    lat, lon = _punto(3.5, 2)
    estados = [
        _bloqueo(con_red, lat + 0.0001 * i, lon, reportante=f"persona-{i}")["bloqueo"]["estado"]
        for i in range(RUTAS.reportes_para_confirmado)
    ]
    assert estados[0] == "sin_confirmar"
    assert estados[RUTAS.reportes_para_probable - 1] == "probable"
    assert estados[-1] == "confirmado"
    assert len(con_red.cliente.get("/bloqueos").json()) == 1


def test_brigada_confirma_de_inmediato_y_solo_con_clave(con_red: Entorno) -> None:
    lat, lon = _punto(3.5, 2)
    sin_clave = con_red.cliente.post(
        "/bloqueos", json={"lat": lat, "lon": lon, "reportante": "equipo-1", "origen": "brigada"}
    )
    assert sin_clave.status_code == 401
    confirmado = _bloqueo(con_red, lat, lon, reportante="equipo-1", origen="brigada")["bloqueo"]
    assert confirmado["estado"] == "confirmado" and confirmado["confirmado_por_brigada"]


def test_descartar_exige_clave_y_saca_el_bloqueo_de_los_activos(con_red: Entorno) -> None:
    lat, lon = _punto(3.5, 2)
    creado = _bloqueo(con_red, lat, lon)["bloqueo"]
    ciudadano = con_red.cliente.post(
        "/bloqueos", json={"accion": "descartar", "id_bloqueo": creado["id"], "reportante": "x-123"}
    )
    assert ciudadano.status_code == 401
    descartado = _bloqueo(con_red, lat, lon, accion="descartar", id_bloqueo=creado["id"])
    assert descartado["bloqueo"]["estado"] == "descartado"
    assert con_red.cliente.get("/bloqueos").json() == []


def test_bloqueo_lejos_de_toda_calle_no_afecta_rutas(con_red: Entorno) -> None:
    # A mitad de una manzana de ~1 km: a más de 150 m de cualquier calle
    lat, lon = _punto(2.5, 2.5)
    assert _bloqueo(con_red, lat, lon)["bloqueo"]["afecta_rutas"] is False


# ── Rutas ──────────────────────────────────────────────────


def test_ruta_por_api_evita_bloqueo_confirmado(con_red: Entorno) -> None:
    origen, destino = _punto(4, 2), _punto(2, 2)
    consulta = f"/rutas?origen={origen[0]},{origen[1]}&destino={destino[0]},{destino[1]}"
    directa = con_red.cliente.get(consulta).json()
    assert _nodos(directa) == [nodo(4, 2), nodo(3, 2), nodo(2, 2)]
    assert directa["properties"]["instrucciones"][-1]["texto"] == "Llegas al destino"

    lat, lon = _punto(3.5, 2)
    _bloqueo(con_red, lat, lon, reportante="equipo-1", origen="brigada")
    desvio = con_red.cliente.get(consulta).json()
    assert (nodo(4, 2), nodo(3, 2)) not in {(u, v) for u, v, _ in desvio["properties"]["tramos"]}
    assert desvio["properties"]["tiempo_min"] > directa["properties"]["tiempo_min"]


def test_rutas_sin_mapa_responde_503(entorno: Entorno) -> None:
    respuesta = entorno.cliente.get("/rutas?origen=12.1,-86.2&destino=12.2,-86.3")
    assert respuesta.status_code == 503


def test_coordenadas_invalidas_responde_422(con_red: Entorno) -> None:
    assert con_red.cliente.get("/rutas?origen=norte&destino=12.2,-86.3").status_code == 422


# ── Asignaciones con ruta y recálculo ──────────────────────


def test_asignacion_aceptada_lleva_su_ruta(con_red: Entorno) -> None:
    asignacion = _aceptar_rescate(con_red)
    assert asignacion["ruta"] is not None
    assert _nodos(asignacion["ruta"]) == [nodo(4, 2), nodo(3, 2), nodo(2, 2)]
    assert asignacion["tiempo_estimado_min"] == asignacion["ruta"]["properties"]["tiempo_min"]


def test_bloqueo_en_la_ruta_la_recalcula_y_avisa_a_la_unidad(con_red: Entorno) -> None:
    asignacion = _aceptar_rescate(con_red)
    lat, lon = _punto(3.5, 2)
    with con_red.cliente.websocket_connect(
        f"/ws/movil/equipo-{asignacion['id_equipo']}?clave={CLAVE_BRIGADA}"
    ) as ws:
        cambio = _bloqueo(con_red, lat, lon, reportante="equipo-9", origen="brigada")
        assert cambio["rutas_recalculadas"] == 1
        aviso = _esperar(ws, "ruta_recalculada")
    nueva = aviso["asignacion"]["ruta"]
    assert (nodo(4, 2), nodo(3, 2)) not in {(u, v) for u, v, _ in nueva["properties"]["tramos"]}
    assert "confirmado" in aviso["motivo"] and aviso["sin_ruta"] is False


def test_bloqueo_fuera_de_la_ruta_no_recalcula_nada(con_red: Entorno) -> None:
    _aceptar_rescate(con_red)
    lat, lon = _punto(0.5, 0)  # Avenida 0, lejos de la ruta
    assert (
        _bloqueo(con_red, lat, lon, reportante="equipo-9", origen="brigada")["rutas_recalculadas"]
        == 0
    )


def test_la_unidad_informa_su_posicion_y_la_ruta_sale_desde_ahi(con_red: Entorno) -> None:
    asignacion = _aceptar_rescate(con_red)
    lat, lon = _punto(4, 3)
    movida = con_red.cliente.post(
        f"/equipos/{asignacion['id_equipo']}/ubicacion",
        json={"lat": lat, "lon": lon},
        headers=ADMIN,
    )
    assert movida.status_code == 200
    # Un bloqueo en la ruta original obliga a recalcular desde la nueva posición
    bloqueo = _punto(3.5, 2)
    _bloqueo(con_red, *bloqueo, reportante="equipo-9", origen="brigada")
    with con_red.fabrica() as s:
        from app.modelos.tablas import Asignacion

        ruta = s.get_one(Asignacion, asignacion["id"]).ruta
    assert ruta is not None and _nodos(ruta)[0] == nodo(4, 3)


def test_marcar_atendida_termina_la_ruta(con_red: Entorno) -> None:
    asignacion = _aceptar_rescate(con_red)
    atendida = con_red.cliente.post(f"/asignaciones/{asignacion['id']}/atendida", headers=ADMIN)
    assert atendida.json()["hora_fin"] is not None
    lat, lon = _punto(3.5, 2)
    # Ya no hay rutas activas que recalcular
    assert (
        _bloqueo(con_red, lat, lon, reportante="equipo-9", origen="brigada")["rutas_recalculadas"]
        == 0
    )


def test_tablero_lista_las_asignaciones_activas_con_su_ruta(con_red: Entorno) -> None:
    asignacion = _aceptar_rescate(con_red)
    (activa,) = con_red.cliente.get("/asignaciones", headers=ADMIN).json()
    assert activa["id"] == asignacion["id"] and activa["ruta"] is not None
    con_red.cliente.post(f"/asignaciones/{asignacion['id']}/atendida", headers=ADMIN)
    assert con_red.cliente.get("/asignaciones", headers=ADMIN).json() == []
    todas = con_red.cliente.get("/asignaciones?activas=false", headers=ADMIN).json()
    assert [a["id"] for a in todas] == [asignacion["id"]]
