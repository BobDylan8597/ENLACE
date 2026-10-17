"""Ciudad mínima para las pruebas: dos celdas (epicentro y lejana) y dos equipos."""

from datetime import UTC, datetime
from zoneinfo import ZoneInfo

import h3
from sqlalchemy.orm import Session

from app.modelos.tablas import Celda, Equipo

ZONA = ZoneInfo("America/Managua")
CENTRO = (12.1364, -86.2514)
HORA = datetime(2026, 10, 16, 14, 0, tzinfo=UTC)
EPICENTRO = h3.latlng_to_cell(*CENTRO, 8)
LEJANA = h3.latlng_to_cell(CENTRO[0] + 0.6, CENTRO[1], 8)  # ~65 km


def poblar_ciudad(sesion: Session) -> None:
    for id_h3, nombre in ((EPICENTRO, "Centro"), (LEJANA, "Lejos")):
        sesion.add(
            Celda(
                id_h3=id_h3,
                nombre_zona=nombre,
                poblacion_por_hora=[3000.0] * 24,
                vulnerabilidad=0.4,
                infraestructura_critica={},
                tipos_construccion={"adobe_taquezal": 0.5, "mamposteria_confinada": 0.5},
            )
        )
    sesion.add_all(
        [
            Equipo(
                nombre="Rescate 1",
                organismo="Bomberos",
                capacidades=["rescate"],
                lat=CENTRO[0] + 0.02,
                lon=CENTRO[1],
            ),
            Equipo(
                nombre="Reconocimiento 1",
                organismo="Defensa Civil",
                capacidades=["reconocimiento"],
                lat=CENTRO[0],
                lon=CENTRO[1] + 0.02,
            ),
        ]
    )
    sesion.flush()
