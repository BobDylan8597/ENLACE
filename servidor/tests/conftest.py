from collections.abc import Iterator

import pytest
from sqlalchemy.orm import Session, sessionmaker

from app.db import crear_motor, crear_tablas


@pytest.fixture
def sesion() -> Iterator[Session]:
    """Base de datos SQLite en memoria, nueva para cada prueba."""
    motor = crear_motor("sqlite://")
    crear_tablas(motor)
    with sessionmaker(bind=motor, expire_on_commit=False)() as s:
        yield s
    motor.dispose()
