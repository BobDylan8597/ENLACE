"""Borrado programado de datos personales (sección 13).

- Al cerrar un evento, cada Identidad del evento recibe fecha_borrado = cierre + plazo.
- Una Identidad creada sin evento activo recibe un plazo provisional desde su creación;
  si luego se emite una alerta, pasa al evento y se borra al cerrarlo.
- Los agregados anónimos (celdas, reportes sin identidad, check-ins por celda) se conservan.
"""

from __future__ import annotations

import logging
from collections.abc import Callable
from datetime import datetime, timedelta

from apscheduler.schedulers.background import BackgroundScheduler
from sqlalchemy import delete, select, update
from sqlalchemy.orm import Session

from app.config import ConfigPrivacidad
from app.modelos.enums import EstadoEvento
from app.modelos.tablas import Evento, Identidad

log = logging.getLogger(__name__)


def plazo_borrado(desde: datetime, config: ConfigPrivacidad) -> datetime:
    return desde + timedelta(days=config.dias_borrado)


def cerrar_evento(
    sesion: Session, evento: Evento, ahora: datetime, config: ConfigPrivacidad
) -> int:
    """Cierra el evento y programa el borrado de sus identidades; devuelve cuántas son."""
    evento.estado = EstadoEvento.cerrado
    evento.fecha_cierre = ahora
    resultado = sesion.execute(
        update(Identidad)
        .where(Identidad.id_evento == evento.id)
        .values(fecha_borrado=plazo_borrado(ahora, config))
    )
    sesion.flush()
    return resultado.rowcount or 0


def borrar_vencidos(sesion: Session, ahora: datetime) -> int:
    resultado = sesion.execute(
        delete(Identidad).where(
            Identidad.fecha_borrado.is_not(None), Identidad.fecha_borrado <= ahora
        )
    )
    sesion.flush()
    return resultado.rowcount or 0


def adoptar_identidades(sesion: Session, evento: Evento, anonimos: set[str]) -> None:
    """Identidades sin evento de personas cuyos pedidos pasaron a ser parte del evento."""
    if not anonimos:
        return
    for identidad in sesion.scalars(
        select(Identidad).where(Identidad.id_evento.is_(None), Identidad.id_anonimo.in_(anonimos))
    ):
        identidad.id_evento = evento.id
        identidad.fecha_borrado = None


def iniciar_borrado_programado(
    fabrica_sesiones: Callable[[], Session],
    reloj: Callable[[], datetime],
    config: ConfigPrivacidad,
) -> BackgroundScheduler:
    def revisar() -> None:
        with fabrica_sesiones() as sesion:
            borradas = borrar_vencidos(sesion, reloj())
            sesion.commit()
        if borradas:
            log.info("Borradas %d identidades vencidas", borradas)

    planificador = BackgroundScheduler()
    planificador.add_job(
        revisar,
        "interval",
        minutes=config.intervalo_revision_min,
        next_run_time=reloj(),
        id="borrado_identidades",
    )
    planificador.start()
    return planificador
