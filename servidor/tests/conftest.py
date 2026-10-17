from collections.abc import Iterator
from dataclasses import dataclass
from datetime import datetime, timedelta
from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.orm import Session, sessionmaker

from app.activacion.firma import Firmador
from app.api.dependencias import obtener_firmador, reloj
from app.db import crear_motor, crear_tablas, obtener_sesion
from app.main import app
from app.rutas.grafo import GrafoCalles
from app.rutas.servicio import obtener_red
from tests.ciudad_prueba import HORA, poblar_ciudad


@pytest.fixture
def sesion() -> Iterator[Session]:
    """Base de datos SQLite en memoria, nueva para cada prueba."""
    motor = crear_motor("sqlite://")
    crear_tablas(motor)
    with sessionmaker(bind=motor, expire_on_commit=False)() as s:
        yield s
    motor.dispose()


@dataclass
class Reloj:
    ahora: datetime

    def avanzar(self, **delta: float) -> None:
        self.ahora += timedelta(**delta)


@dataclass
class Entorno:
    cliente: TestClient
    reloj: Reloj
    firmador: Firmador
    fabrica: sessionmaker[Session]

    def usar_red(self, red: GrafoCalles | None) -> None:
        """Por defecto no hay grafo de calles (línea recta); las pruebas de rutas ponen uno."""
        app.dependency_overrides[obtener_red] = lambda: red


@pytest.fixture
def entorno(tmp_path: Path) -> Iterator[Entorno]:
    """La API completa sobre una base en memoria con la ciudad de prueba y un reloj controlado."""
    motor = crear_motor("sqlite://", compartir_conexion=True)
    crear_tablas(motor)
    fabrica = sessionmaker(bind=motor, expire_on_commit=False)
    with fabrica() as s:
        poblar_ciudad(s)
        s.commit()

    def sesion_de_prueba() -> Iterator[Session]:
        with fabrica() as s:
            yield s

    controlado = Reloj(HORA)
    firmador = Firmador.cargar_o_crear(tmp_path / "claves")
    app.dependency_overrides[obtener_sesion] = sesion_de_prueba
    app.dependency_overrides[reloj] = lambda: controlado.ahora
    app.dependency_overrides[obtener_firmador] = lambda: firmador
    app.dependency_overrides[obtener_red] = lambda: None
    try:
        yield Entorno(TestClient(app), controlado, firmador, fabrica)
    finally:
        app.dependency_overrides.clear()
        motor.dispose()
