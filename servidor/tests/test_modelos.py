from datetime import UTC, datetime, timedelta, timezone

import pytest
from sqlalchemy import inspect
from sqlalchemy.exc import IntegrityError, StatementError
from sqlalchemy.orm import Session

from app.modelos.enums import (
    FuenteEvento,
    OrigenReporte,
    TipoEvento,
    TipoReporte,
)
from app.modelos.tablas import CheckIn, Evento, Reporte

TABLAS_ESPERADAS = {
    "eventos",
    "celdas",
    "reportes",
    "checkins",
    "ubicaciones_alerta",
    "identidades",
    "equipos",
    "asignaciones",
    "bloqueos",
    "usuarios_admin",
    "propuestas_comunitarias",
}


def _evento(sesion: Session) -> Evento:
    evento = Evento(
        tipo=TipoEvento.sismo, magnitud=6.2, lat=0.0, lon=0.0, fuente=FuenteEvento.manual
    )
    sesion.add(evento)
    sesion.commit()
    return evento


def _reporte(id_mensaje: str) -> Reporte:
    return Reporte(
        id_mensaje=id_mensaje,
        id_anonimo="anon-1",
        id_h3="88283082bffffff",
        origen=OrigenReporte.app,
        tipo=TipoReporte.ayuda,
        confiabilidad=0.5,
    )


def test_se_crean_todas_las_tablas(sesion: Session) -> None:
    assert set(inspect(sesion.get_bind()).get_table_names()) == TABLAS_ESPERADAS


def test_checkin_no_tiene_columnas_de_coordenadas(sesion: Session) -> None:
    columnas = {c["name"] for c in inspect(sesion.get_bind()).get_columns("checkins")}
    assert not columnas & {"lat", "lon", "latitud", "longitud"}


def test_id_mensaje_repetido_se_rechaza(sesion: Session) -> None:
    sesion.add(_reporte("m-1"))
    sesion.commit()
    sesion.add(_reporte("m-1"))
    with pytest.raises(IntegrityError):
        sesion.commit()


def test_un_checkin_por_persona_y_evento(sesion: Session) -> None:
    evento = _evento(sesion)
    for _ in range(2):
        sesion.add(CheckIn(id_anonimo="anon-1", id_h3="x", id_evento=evento.id))
    with pytest.raises(IntegrityError):
        sesion.commit()


def test_fechas_se_guardan_y_leen_en_utc(sesion: Session) -> None:
    hora_local = datetime(2026, 10, 16, 8, 0, tzinfo=timezone(timedelta(hours=-6)))
    evento = Evento(
        tipo=TipoEvento.sismo, lat=0.0, lon=0.0, fuente=FuenteEvento.manual, hora=hora_local
    )
    sesion.add(evento)
    sesion.commit()
    sesion.expire_all()
    leida = sesion.get(Evento, evento.id)
    assert leida is not None
    assert leida.hora == hora_local
    assert leida.hora.tzinfo == UTC


def test_fecha_sin_zona_horaria_se_rechaza(sesion: Session) -> None:
    sesion.add(
        Evento(
            tipo=TipoEvento.sismo,
            lat=0.0,
            lon=0.0,
            fuente=FuenteEvento.manual,
            hora=datetime(2026, 10, 16, 8, 0),
        )
    )
    with pytest.raises(StatementError):
        sesion.commit()


def test_valor_fuera_del_enum_se_rechaza(sesion: Session) -> None:
    evento = _evento(sesion)
    sesion.add(CheckIn(id_anonimo="a", id_h3="x", id_evento=evento.id, estado="perdido"))
    with pytest.raises(StatementError):
        sesion.commit()
