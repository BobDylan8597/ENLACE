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


def _coincide(clave: str | None, esperada: str) -> bool:
    return clave is not None and secrets.compare_digest(clave, esperada)


def es_admin(config: Configuracion, clave: str | None) -> bool:
    return _coincide(clave, config.servidor.clave_admin)


def es_brigada(config: Configuracion, clave: str | None) -> bool:
    """Una brigada, o el administrador (que puede hacer todo lo que hace una brigada)."""
    return _coincide(clave, config.servidor.clave_brigada) or es_admin(config, clave)


def exigir_admin(
    config: Annotated[Configuracion, Depends(obtener_config)],
    x_clave_admin: Annotated[str | None, Header()] = None,
) -> None:
    """Demo: clave fija en la configuración. En producción, autenticación por usuario y nivel."""
    if not es_admin(config, x_clave_admin):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Clave de administrador inválida")


def nivel_privilegiado(
    config: Annotated[Configuracion, Depends(obtener_config)],
    x_clave_admin: Annotated[str | None, Header()] = None,
    x_clave_brigada: Annotated[str | None, Header()] = None,
) -> bool:
    """True si la petición viene de una brigada o del administrador."""
    return es_brigada(config, x_clave_brigada) or es_admin(config, x_clave_admin)


def exigir_brigada(privilegiado: Annotated[bool, Depends(nivel_privilegiado)]) -> None:
    if not privilegiado:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Clave de brigada inválida")


SesionDep = Annotated[Session, Depends(obtener_sesion)]
AhoraDep = Annotated[datetime, Depends(reloj)]
ConfigDep = Annotated[Configuracion, Depends(obtener_config)]
CiudadDep = Annotated[Ciudad, Depends(obtener_ciudad)]
FirmadorDep = Annotated[Firmador, Depends(obtener_firmador)]
# Grafo de calles de la ciudad activa; None si no se preparó (se usa la línea recta)
RedDep = Annotated[GrafoCalles | None, Depends(obtener_red)]
Admin = Depends(exigir_admin)
Brigada = Depends(exigir_brigada)
PrivilegiadoDep = Annotated[bool, Depends(nivel_privilegiado)]
