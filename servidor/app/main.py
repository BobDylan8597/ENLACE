"""Punto de entrada del servidor ENLACE."""

from __future__ import annotations

from fastapi import FastAPI

from app.ciudad import obtener_ciudad

app = FastAPI(title="ENLACE", version="0.1.0")


@app.get("/salud")
def salud() -> dict[str, str]:
    ciudad = obtener_ciudad()
    return {"estado": "ok", "ciudad": ciudad.nombre, "pais": ciudad.pais}
