"""Conexión a SQLite y sesión por petición."""

from __future__ import annotations

from collections.abc import Iterator
from datetime import UTC, datetime
from functools import lru_cache

from sqlalchemy import DateTime, Engine, create_engine, event
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker
from sqlalchemy.types import TypeDecorator

from app.config import obtener_config, resolver_ruta


class Base(DeclarativeBase):
    pass


class FechaUTC(TypeDecorator[datetime]):
    """Guarda fechas en UTC. SQLite no conserva la zona horaria, así que se restaura al leer."""

    impl = DateTime
    cache_ok = True

    def process_bind_param(self, valor: datetime | None, dialect: object) -> datetime | None:
        if valor is None:
            return None
        if valor.tzinfo is None:
            raise ValueError("Las fechas deben tener zona horaria")
        return valor.astimezone(UTC).replace(tzinfo=None)

    def process_result_value(self, valor: datetime | None, dialect: object) -> datetime | None:
        return None if valor is None else valor.replace(tzinfo=UTC)


def ahora() -> datetime:
    return datetime.now(UTC)


def crear_motor(url: str) -> Engine:
    motor = create_engine(url, connect_args={"check_same_thread": False})

    @event.listens_for(motor, "connect")
    def _activar_claves_foraneas(conexion, _registro) -> None:  # type: ignore[no-untyped-def]
        cursor = conexion.cursor()
        cursor.execute("PRAGMA foreign_keys=ON")
        cursor.close()

    return motor


@lru_cache
def obtener_motor() -> Engine:
    ruta = resolver_ruta(obtener_config().servidor.base_datos)
    return crear_motor(f"sqlite:///{ruta}")


def crear_tablas(motor: Engine) -> None:
    import app.modelos.tablas  # noqa: F401  (registra las tablas en Base.metadata)

    Base.metadata.create_all(motor)


@lru_cache
def _fabrica_sesiones() -> sessionmaker[Session]:
    return sessionmaker(bind=obtener_motor(), expire_on_commit=False)


def obtener_sesion() -> Iterator[Session]:
    """Dependencia de FastAPI: una sesión por petición."""
    with _fabrica_sesiones()() as sesion:
        yield sesion
