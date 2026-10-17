"""Fuente comunitaria: muchos pedidos de ayuda juntos sin alerta activa.

No emite nada por sí sola: crea una propuesta que un operador debe aprobar.
"""

from __future__ import annotations

from datetime import datetime, timedelta

import h3
from sqlalchemy import distinct, func, select
from sqlalchemy.orm import Session

from app.activacion.fuentes.base import NuevoEvento
from app.config import ConfigActivacion
from app.modelos.enums import EstadoPropuesta, FuenteEvento, TipoEvento, TipoReporte
from app.modelos.tablas import PropuestaComunitaria, Reporte
from app.motor.geo import centro_celda
from app.motor.pipeline import evento_activo


class FuenteComunitaria:
    fuente = FuenteEvento.comunitaria

    def __init__(self, tipo: TipoEvento = TipoEvento.otro) -> None:
        # El operador puede indicar el tipo al aprobar (p. ej. si sabe que fue un sismo)
        self._tipo = tipo

    def convertir(self, entrada: PropuestaComunitaria) -> NuevoEvento:
        lat, lon = centro_celda(entrada.id_h3)
        return NuevoEvento(
            tipo=self._tipo,
            fuente=self.fuente,
            lat=lat,
            lon=lon,
            hora=entrada.hora,
            zona_afectada=sorted(h3.grid_disk(entrada.id_h3, 1)),
        )


def detectar_propuestas(
    sesion: Session, ahora: datetime, config: ConfigActivacion
) -> list[PropuestaComunitaria]:
    """Crea o actualiza propuestas para celdas con muchos pedidos de personas distintas."""
    if evento_activo(sesion) is not None:
        return []
    desde = ahora - timedelta(minutes=config.ventana_comunitaria_min)
    conteos = sesion.execute(
        select(Reporte.id_h3, func.count(distinct(Reporte.id_anonimo)))
        .where(
            Reporte.id_evento.is_(None),
            Reporte.tipo == TipoReporte.ayuda,
            Reporte.hora >= desde,
        )
        .group_by(Reporte.id_h3)
    ).all()
    pendientes = {
        p.id_h3: p
        for p in sesion.scalars(
            select(PropuestaComunitaria).where(
                PropuestaComunitaria.estado == EstadoPropuesta.pendiente
            )
        )
    }
    cambiadas: list[PropuestaComunitaria] = []
    for id_h3, pedidos in conteos:
        if pedidos < config.pedidos_minimos_comunitaria:
            continue
        propuesta = pendientes.get(id_h3)
        if propuesta is None:
            propuesta = PropuestaComunitaria(id_h3=id_h3, pedidos=pedidos, hora=ahora)
            sesion.add(propuesta)
        elif propuesta.pedidos == pedidos:
            continue
        else:
            propuesta.pedidos = pedidos
        cambiadas.append(propuesta)
    sesion.flush()
    return cambiadas
