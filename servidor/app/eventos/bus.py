"""Pub/Sub interno: los módulos se comunican publicando temas, sin conocerse entre sí.

Los manejadores pueden ser funciones normales o corrutinas. `publicar` se puede llamar
desde rutas síncronas (que FastAPI ejecuta en otro hilo): las corrutinas se programan
en el bucle principal registrado con `vincular_bucle`.
"""

from __future__ import annotations

import asyncio
import inspect
import logging
from collections import defaultdict
from collections.abc import Awaitable, Callable
from enum import StrEnum
from typing import Any

log = logging.getLogger(__name__)

Datos = dict[str, Any]
Manejador = Callable[[Datos], Awaitable[None] | None]


class Tema(StrEnum):
    evento_emitido = "evento_emitido"
    evento_cerrado = "evento_cerrado"
    reporte_recibido = "reporte_recibido"
    reporte_actualizado = "reporte_actualizado"
    checkin_recibido = "checkin_recibido"
    celda_actualizada = "celda_actualizada"
    recomendaciones_actualizadas = "recomendaciones_actualizadas"
    asignacion_cambiada = "asignacion_cambiada"
    bloqueo_cambiado = "bloqueo_cambiado"
    ruta_recalculada = "ruta_recalculada"
    propuesta_comunitaria = "propuesta_comunitaria"


class BusEventos:
    def __init__(self) -> None:
        self._suscriptores: dict[Tema, list[Manejador]] = defaultdict(list)
        self._bucle: asyncio.AbstractEventLoop | None = None
        self._tareas: set[asyncio.Task[None]] = set()

    def vincular_bucle(self, bucle: asyncio.AbstractEventLoop) -> None:
        self._bucle = bucle

    def suscribir(self, tema: Tema, manejador: Manejador) -> None:
        self._suscriptores[tema].append(manejador)

    def publicar(self, tema: Tema, datos: Datos) -> None:
        for manejador in list(self._suscriptores[tema]):
            try:
                resultado = manejador(datos)
            except Exception:
                log.exception("Falló un manejador de %s", tema)
                continue
            if inspect.isawaitable(resultado):
                self._programar(resultado)

    def _programar(self, corrutina: Awaitable[None]) -> None:
        try:
            bucle_actual = asyncio.get_running_loop()
        except RuntimeError:
            bucle_actual = None
        if bucle_actual is not None:
            tarea = bucle_actual.create_task(corrutina)  # type: ignore[arg-type]
            self._tareas.add(tarea)
            tarea.add_done_callback(self._tareas.discard)
        elif self._bucle is not None and self._bucle.is_running():
            asyncio.run_coroutine_threadsafe(corrutina, self._bucle)  # type: ignore[arg-type]
        else:
            log.warning("Sin bucle de eventos: se descarta un manejador asíncrono")
            corrutina.close()  # type: ignore[attr-defined]


bus = BusEventos()
