"""Punto de entrada del servidor ENLACE (monolito modular, API como fachada)."""

from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api import asignaciones, celdas, equipos, eventos, reportes
from app.api.dependencias import reloj
from app.ciudad import obtener_ciudad
from app.config import obtener_config
from app.db import crear_tablas, fabrica_sesiones, obtener_motor
from app.eventos.bus import bus
from app.privacidad.borrado import iniciar_borrado_programado


@asynccontextmanager
async def ciclo_de_vida(_app: FastAPI) -> AsyncIterator[None]:
    crear_tablas(obtener_motor())
    bus.vincular_bucle(asyncio.get_running_loop())
    planificador = iniciar_borrado_programado(
        fabrica_sesiones(), reloj, obtener_config().privacidad
    )
    try:
        yield
    finally:
        planificador.shutdown(wait=False)


app = FastAPI(title="ENLACE", version="0.1.0", lifespan=ciclo_de_vida)
for modulo in (eventos, celdas, reportes, asignaciones, equipos):
    app.include_router(modulo.router)


@app.get("/salud", tags=["sistema"])
def salud() -> dict[str, str]:
    ciudad = obtener_ciudad()
    return {"estado": "ok", "ciudad": ciudad.nombre, "pais": ciudad.pais}
