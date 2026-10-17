"""Puntos exactos compartidos al llegar una alerta (sección 13, opción de consentimiento
"compartir mi ubicación exacta durante emergencias").

- Se guardan solo si el celular los envía: la persona eligió esa opción.
- Uno por persona y evento: un check-in nuevo lo reemplaza; uno sin punto lo borra
  (la persona cambió de opinión).
- Se borran al cerrar el evento (borrado.py).
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import delete, select
from sqlalchemy.orm import Session

from app.modelos.esquemas import CheckInEntrada, UbicacionSalida
from app.modelos.tablas import CheckIn, Evento, UbicacionAlerta


def guardar_ubicacion(
    sesion: Session, entrada: CheckInEntrada, evento: Evento, hora: datetime
) -> None:
    filtro = (
        UbicacionAlerta.id_anonimo == entrada.id_anonimo,
        UbicacionAlerta.id_evento == evento.id,
    )
    if entrada.ubicacion is None:
        sesion.execute(delete(UbicacionAlerta).where(*filtro))
        return
    punto = sesion.scalars(select(UbicacionAlerta).where(*filtro)).first()
    if punto is None:
        punto = UbicacionAlerta(id_anonimo=entrada.id_anonimo, id_evento=evento.id)
        sesion.add(punto)
    punto.lat = entrada.ubicacion.lat
    punto.lon = entrada.ubicacion.lon
    punto.hora = hora


def ubicaciones_del_evento(sesion: Session, evento: Evento) -> list[UbicacionSalida]:
    """Puntos con el estado del check-in de esa persona; sin identificadores."""
    filas = sesion.execute(
        select(UbicacionAlerta, CheckIn)
        .join(
            CheckIn,
            (CheckIn.id_anonimo == UbicacionAlerta.id_anonimo)
            & (CheckIn.id_evento == UbicacionAlerta.id_evento),
        )
        .where(UbicacionAlerta.id_evento == evento.id)
        .order_by(UbicacionAlerta.id)
    ).all()
    return [
        UbicacionSalida(
            id=punto.id,
            lat=punto.lat,
            lon=punto.lon,
            id_h3=checkin.id_h3,
            estado=checkin.estado,
            personas=checkin.personas,
            vulnerables=checkin.vulnerables,
            hora=punto.hora,
        )
        for punto, checkin in filas
    ]
