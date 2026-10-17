"""Emisión de la Alerta ENLACE: crea el evento, estima el impacto inicial y firma la alerta."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.activacion.firma import AlertaFirmada, Firmador
from app.activacion.fuentes.base import NuevoEvento
from app.config import Configuracion
from app.modelos.tablas import Evento, Reporte
from app.motor.pipeline import evento_activo, recalcular_todas
from app.privacidad.borrado import adoptar_identidades


class EventoYaActivo(Exception):
    """Ya hay un evento activo: hay que cerrarlo antes de emitir otro."""


@dataclass(frozen=True)
class ResultadoEmision:
    evento: Evento
    alerta: AlertaFirmada


def contenido_alerta(evento: Evento) -> dict[str, object]:
    return {
        "tipo_mensaje": "alerta",
        "id_evento": evento.id,
        "tipo": evento.tipo.value,
        "fuente": evento.fuente.value,
        "magnitud": evento.magnitud,
        "lat": evento.lat,
        "lon": evento.lon,
        "hora": evento.hora.isoformat(),
        "zona_afectada": evento.zona_afectada,
    }


def emitir(
    sesion: Session,
    nuevo: NuevoEvento,
    firmador: Firmador,
    ahora: datetime,
    config: Configuracion,
    zona: ZoneInfo,
) -> ResultadoEmision:
    if evento_activo(sesion) is not None:
        raise EventoYaActivo("Ya hay un evento activo")
    evento = Evento(
        tipo=nuevo.tipo,
        fuente=nuevo.fuente,
        magnitud=nuevo.magnitud,
        profundidad_km=nuevo.profundidad_km,
        lat=nuevo.lat,
        lon=nuevo.lon,
        hora=nuevo.hora,
        zona_afectada=[],
    )
    sesion.add(evento)
    sesion.flush()

    # Los pedidos que llegaron justo antes de la alerta pasan a ser parte del evento
    desde = nuevo.hora - timedelta(minutes=config.activacion.ventana_comunitaria_min)
    previos = list(
        sesion.scalars(select(Reporte).where(Reporte.id_evento.is_(None), Reporte.hora >= desde))
    )
    for reporte in previos:
        reporte.id_evento = evento.id
    adoptar_identidades(sesion, evento, {r.id_anonimo for r in previos})
    sesion.flush()

    estados = recalcular_todas(sesion, evento, ahora, config, zona)
    if nuevo.zona_afectada is not None:
        evento.zona_afectada = list(nuevo.zona_afectada)
    else:
        evento.zona_afectada = sorted(
            e.id_h3 for e in estados if e.fraccion_modelo >= config.activacion.umbral_zona_afectada
        )
    sesion.flush()
    return ResultadoEmision(evento, firmador.firmar(contenido_alerta(evento)))
