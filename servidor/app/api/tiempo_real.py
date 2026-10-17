"""Tiempo real por WebSocket (patrón Observer): el bus interno se reenvía a la web y a la app.

- /ws/admin?clave=...   todos los temas, para el centro de reportes y el tablero.
- /ws/movil/{id}        alertas firmadas para todos; `id` = "equipo-<n>" recibe además sus
                        asignaciones (con su ruta) y las rutas recalculadas por bloqueos;
                        `id` = id_anonimo recibe el aviso de ayuda en camino.

Mensajes: {"tipo": "<tema>", "datos": {...}}
"""

from __future__ import annotations

import asyncio
import logging
import secrets
from collections import defaultdict
from typing import Any

from fastapi import APIRouter, WebSocket, WebSocketDisconnect, status

from app.activacion.emisor import contenido_alerta
from app.api.dependencias import FirmadorDep, SesionDep
from app.config import obtener_config
from app.eventos.bus import BusEventos, Datos, Tema, bus
from app.modelos.enums import DecisionAsignacion
from app.motor.pipeline import evento_activo

log = logging.getLogger(__name__)
router = APIRouter(tags=["tiempo real"])

PREFIJO_EQUIPO = "equipo-"
DECISIONES_NOTIFICADAS = {DecisionAsignacion.aceptada.value, DecisionAsignacion.modificada.value}


class GestorConexiones:
    def __init__(self) -> None:
        self.admins: set[WebSocket] = set()
        self.moviles: dict[str, set[WebSocket]] = defaultdict(set)

    async def _enviar(self, destinos: set[WebSocket], mensaje: dict[str, Any]) -> None:
        for ws in list(destinos):
            try:
                await ws.send_json(mensaje)
            except Exception:  # conexión caída: se limpia al desconectar
                log.debug("No se pudo enviar a un WebSocket", exc_info=True)

    async def a_admins(self, tema: Tema, datos: Datos) -> None:
        await self._enviar(self.admins, {"tipo": tema.value, "datos": datos})

    async def a_moviles(self, tipo: str, datos: Datos) -> None:
        todos = set().union(*self.moviles.values()) if self.moviles else set()
        await self._enviar(todos, {"tipo": tipo, "datos": datos})

    async def a_movil(self, id_movil: str, tipo: str, datos: Datos) -> None:
        await self._enviar(self.moviles.get(id_movil, set()), {"tipo": tipo, "datos": datos})


gestor = GestorConexiones()


def suscribir(bus: BusEventos) -> None:
    """Conecta el bus interno con las conexiones abiertas."""
    for tema in Tema:

        async def reenviar_a_admins(datos: Datos, tema: Tema = tema) -> None:
            await gestor.a_admins(tema, datos)

        bus.suscribir(tema, reenviar_a_admins)

    async def alerta_a_moviles(datos: Datos) -> None:
        await gestor.a_moviles("alerta", datos["alerta"])

    async def cierre_a_moviles(datos: Datos) -> None:
        await gestor.a_moviles("evento_cerrado", {"id_evento": datos["id"]})

    async def asignacion_a_moviles(datos: Datos) -> None:
        asignacion = datos["asignacion"]
        if asignacion["decision"] not in DECISIONES_NOTIFICADAS:
            return
        await gestor.a_movil(f"{PREFIJO_EQUIPO}{asignacion['id_equipo']}", "asignacion", datos)
        if datos.get("id_anonimo"):
            await gestor.a_movil(
                datos["id_anonimo"],
                "ayuda_en_camino",
                {
                    "id_reporte": asignacion["id_reporte"],
                    "equipo": asignacion["nombre_equipo"],
                    "tiempo_estimado_min": asignacion["tiempo_estimado_min"],
                },
            )

    async def ruta_a_unidad(datos: Datos) -> None:
        id_equipo = datos["asignacion"]["id_equipo"]
        await gestor.a_movil(f"{PREFIJO_EQUIPO}{id_equipo}", "ruta_recalculada", datos)

    bus.suscribir(Tema.evento_emitido, alerta_a_moviles)
    bus.suscribir(Tema.ruta_recalculada, ruta_a_unidad)
    bus.suscribir(Tema.evento_cerrado, cierre_a_moviles)
    bus.suscribir(Tema.asignacion_cambiada, asignacion_a_moviles)


async def _mantener_abierta(ws: WebSocket) -> None:
    """Las conexiones solo reciben; lo que envíe el cliente (p. ej. pings) se ignora."""
    while True:
        await ws.receive_text()


@router.websocket("/ws/admin")
async def ws_admin(ws: WebSocket, clave: str = "") -> None:
    if not secrets.compare_digest(clave, obtener_config().servidor.clave_admin):
        await ws.close(code=status.WS_1008_POLICY_VIOLATION)
        return
    _vincular()
    await ws.accept()
    gestor.admins.add(ws)
    try:
        await ws.send_json({"tipo": "conectado", "datos": {}})
        await _mantener_abierta(ws)
    except WebSocketDisconnect:
        pass
    finally:
        gestor.admins.discard(ws)


@router.websocket("/ws/movil/{id_movil}")
async def ws_movil(ws: WebSocket, id_movil: str, sesion: SesionDep, firmador: FirmadorDep) -> None:
    # La app que se conecta tarde recibe igual la alerta firmada vigente
    evento = evento_activo(sesion)
    alerta = firmador.firmar(contenido_alerta(evento)).como_dict() if evento else None
    sesion.close()  # no retener la base mientras la conexión siga abierta

    _vincular()
    await ws.accept()
    gestor.moviles[id_movil].add(ws)
    try:
        if alerta is not None:
            await ws.send_json({"tipo": "alerta", "datos": alerta})
        await _mantener_abierta(ws)
    except WebSocketDisconnect:
        pass
    finally:
        gestor.moviles[id_movil].discard(ws)
        if not gestor.moviles[id_movil]:
            del gestor.moviles[id_movil]


def _vincular() -> None:
    """El bucle que atiende los WebSocket es al que el bus debe entregar los mensajes."""
    bus.vincular_bucle(asyncio.get_running_loop())
