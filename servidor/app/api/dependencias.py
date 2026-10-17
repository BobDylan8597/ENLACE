"""Dependencias compartidas por las rutas de la API."""

from __future__ import annotations

import secrets
from datetime import UTC, datetime
from functools import lru_cache
from typing import Annotated

from fastapi import Depends, Header, HTTPException, status
from sqlalchemy.orm import Session

from app.activacion.firma import Firmador
from app.ciudad import Ciudad, obtener_ciudad
from app.config import Configuracion, obtener_config, resolver_ruta
from app.db import obtener_sesion
from app.rutas.grafo import GrafoCalles
from app.rutas.servicio import obtener_red


def reloj() -> datetime:
    """Hora actual; las pruebas la reemplazan para controlar el tiempo."""
    return datetime.now(UTC)


@lru_cache
def obtener_firmador() -> Firmador:
    return Firmador.cargar_o_crear(resolver_ruta(obtener_config().activacion.directorio_claves))


def exigir_admin(
    config: Annotated[Configuracion, Depends(obtener_config)],
    x_clave_admin: Annotated[str | None, Header()] = None,
) -> None:
    """Demo: clave fija en la configuración. En producción, autenticación por usuario y nivel."""
    if x_clave_admin is None or not secrets.compare_digest(
        x_clave_admin, config.servidor.clave_admin
    ):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Clave de administrador inválida")


SesionDep = Annotated[Session, Depends(obtener_sesion)]
AhoraDep = Annotated[datetime, Depends(reloj)]
ConfigDep = Annotated[Configuracion, Depends(obtener_config)]
CiudadDep = Annotated[Ciudad, Depends(obtener_ciudad)]
FirmadorDep = Annotated[Firmador, Depends(obtener_firmador)]
# Grafo de calles de la ciudad activa; None si no se preparó (se usa la línea recta)
RedDep = Annotated[GrafoCalles | None, Depends(obtener_red)]
Admin = Depends(exigir_admin)
