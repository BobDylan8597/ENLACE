"""Conexión a SQLite y sesión por petición."""

from __future__ import annotations

from collections.abc import Iterator
from datetime import UTC, datetime
from functools import lru_cache
from typing import Any

from sqlalchemy import DateTime, Engine, create_engine, event, inspect, text
from sqlalchemy.orm import DeclarativeBase, Session, sessionmaker
from sqlalchemy.pool import StaticPool
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


def crear_motor(url: str, compartir_conexion: bool = False) -> Engine:
    """`compartir_conexion`: una sola conexión para todos los hilos (SQLite en memoria)."""
    opciones: dict[str, Any] = {"poolclass": StaticPool} if compartir_conexion else {}
    motor = create_engine(url, connect_args={"check_same_thread": False}, **opciones)

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
    _agregar_columnas_nuevas(motor)


def _agregar_columnas_nuevas(motor: Engine) -> None:
    """create_all no modifica tablas existentes: agrega las columnas que se sumaron al modelo
    para no perder la base de la demo. En producción esto lo hace una migración (Alembic)."""
    revisor = inspect(motor)
    with motor.begin() as conexion:
        for tabla in Base.metadata.sorted_tables:
            existentes = {c["name"] for c in revisor.get_columns(tabla.name)}
            for columna in tabla.columns:
                if columna.name in existentes:
                    continue
                tipo = columna.type.compile(motor.dialect)
                por_defecto = (
                    f" DEFAULT {columna.server_default.arg}"
                    if columna.server_default is not None
                    else ""
                )
                conexion.execute(
                    text(
                        f'ALTER TABLE "{tabla.name}" ADD COLUMN "{columna.name}" {tipo}{por_defecto}'
                    )
                )


@lru_cache
def fabrica_sesiones() -> sessionmaker[Session]:
    return sessionmaker(bind=obtener_motor(), expire_on_commit=False)


def obtener_sesion() -> Iterator[Session]:
    """Dependencia de FastAPI: una sesión por petición."""
    with fabrica_sesiones()() as sesion:
        yield sesion
