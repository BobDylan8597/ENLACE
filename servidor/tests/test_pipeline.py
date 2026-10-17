from datetime import UTC, datetime, timedelta
from zoneinfo import ZoneInfo

import h3
import pytest
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import obtener_config
from app.modelos.enums import (
    Capacidad,
    CategoriaCelda,
    DecisionAsignacion,
    EstadoCheckIn,
    EstadoReporte,
    FuenteEvento,
    OrigenReporte,
    TipoEvento,
    TipoReporte,
)
from app.modelos.tablas import Asignacion, Celda, CheckIn, Equipo, Evento, Reporte
from app.motor import pipeline

CONFIG = obtener_config()
ZONA = ZoneInfo("America/Managua")
CENTRO = (12.1364, -86.2514)
HORA = datetime(2026, 10, 16, 14, 0, tzinfo=UTC)
EPICENTRO = h3.latlng_to_cell(*CENTRO, 8)
LEJANA = h3.latlng_to_cell(CENTRO[0] + 0.6, CENTRO[1], 8)  # ~65 km


@pytest.fixture
def escenario(sesion: Session) -> Evento:
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
    evento = Evento(
        tipo=TipoEvento.sismo,
        magnitud=6.2,
        profundidad_km=10,
        lat=CENTRO[0],
        lon=CENTRO[1],
        fuente=FuenteEvento.manual,
        hora=HORA,
    )
    sesion.add(evento)
    sesion.flush()
    pipeline.recalcular_todas(sesion, evento, HORA, CONFIG, ZONA)
    return evento


def _reporte(
    id_mensaje: str, id_anonimo: str = "a", minutos: float = 5, **campos: object
) -> Reporte:
    valores: dict[str, object] = {
        "id_h3": EPICENTRO,
        "origen": OrigenReporte.app,
        "tipo": TipoReporte.ayuda,
        "necesidades": [],
    }
    valores.update(campos)
    return Reporte(
        id_mensaje=id_mensaje,
        id_anonimo=id_anonimo,
        hora=HORA + timedelta(minutes=minutos),
        **valores,
    )


def _ingresar(sesion: Session, *reportes: Reporte) -> pipeline.ResultadoIngreso:
    return pipeline.ingresar_reportes(sesion, list(reportes), HORA, CONFIG, ZONA)


def test_al_emitir_el_epicentro_queda_para_explorar(sesion: Session, escenario: Evento) -> None:
    centro = sesion.get_one(Celda, EPICENTRO)
    lejos = sesion.get_one(Celda, LEJANA)
    assert centro.impacto_inicial > lejos.impacto_inicial
    assert centro.categoria is CategoriaCelda.explorar
    assert centro.certeza == 0.0


def test_reporte_repetido_se_ignora_y_duplicado_se_fusiona(
    sesion: Session, escenario: Evento
) -> None:
    primero = _ingresar(sesion, _reporte("m1"), _reporte("m2", minutos=10))
    otra_vez = _ingresar(sesion, _reporte("m1"))
    assert [r.id_mensaje for r in primero.nuevos] == ["m1", "m2"]
    assert otra_vez.repetidos == ["m1"]
    assert sesion.get_one(Reporte, "m2").id_reporte_padre == "m1"
    assert sesion.get_one(Reporte, "m1").id_evento == escenario.id


def test_reportes_suben_la_certeza_de_la_celda(sesion: Session, escenario: Evento) -> None:
    _ingresar(sesion, *[_reporte(f"m{i}", id_anonimo=f"p{i}", atrapados=1) for i in range(4)])
    celda = sesion.get_one(Celda, EPICENTRO)
    assert celda.certeza > CONFIG.clasificacion.umbral_certeza
    assert celda.categoria is CategoriaCelda.actuar


def test_reporte_fuera_del_area_crea_una_celda_vacia(sesion: Session, escenario: Evento) -> None:
    fuera = h3.latlng_to_cell(-12.0, -77.0, 8)
    _ingresar(sesion, _reporte("m1", id_h3=fuera, atrapados=2))
    celda = sesion.get_one(Celda, fuera)
    assert celda.nombre_zona == CONFIG.celdas.nombre_fuera_de_area
    assert celda.impacto_estimado == 2.0


def test_recomienda_rescate_para_atrapados_y_reconocimiento_para_explorar(
    sesion: Session, escenario: Evento
) -> None:
    _ingresar(sesion, _reporte("m1", id_h3=LEJANA, atrapados=2))
    recomendaciones = pipeline.actualizar_recomendaciones(sesion, escenario, HORA, CONFIG)
    por_destino = {(a.id_reporte, a.id_h3): a for a in recomendaciones}
    rescate = por_destino[("m1", LEJANA)]
    reconocimiento = por_destino[(None, EPICENTRO)]
    equipos = {e.id: e for e in sesion.scalars(select(Equipo))}
    assert Capacidad.rescate in equipos[rescate.id_equipo].capacidades
    assert Capacidad.reconocimiento in equipos[reconocimiento.id_equipo].capacidades
    assert "2 atrapados" in rescate.motivo


def test_recomendaciones_estables_entre_recalculos(sesion: Session, escenario: Evento) -> None:
    _ingresar(sesion, _reporte("m1", atrapados=1))
    antes = {a.id for a in pipeline.actualizar_recomendaciones(sesion, escenario, HORA, CONFIG)}
    despues = {a.id for a in pipeline.actualizar_recomendaciones(sesion, escenario, HORA, CONFIG)}
    assert antes == despues


def test_par_rechazado_no_se_vuelve_a_proponer(sesion: Session, escenario: Evento) -> None:
    _ingresar(sesion, _reporte("m1", atrapados=1))
    (propuesta,) = [
        a
        for a in pipeline.actualizar_recomendaciones(sesion, escenario, HORA, CONFIG)
        if a.id_reporte == "m1"
    ]
    propuesta.decision = DecisionAsignacion.rechazada
    sesion.flush()
    nuevas = pipeline.actualizar_recomendaciones(sesion, escenario, HORA, CONFIG)
    assert not [a for a in nuevas if a.id_reporte == "m1"]


def test_personas_atendidas_ya_no_cuentan_como_confirmadas(
    sesion: Session, escenario: Evento
) -> None:
    fuera = h3.latlng_to_cell(-12.0, -77.0, 8)
    _ingresar(sesion, _reporte("m1", id_h3=fuera, atrapados=3))
    sesion.get_one(Reporte, "m1").estado = EstadoReporte.atendido
    celda = sesion.get_one(Celda, fuera)
    estado = pipeline.recalcular_celda(sesion, celda, escenario, HORA, CONFIG, ZONA)
    assert estado.conteo_confirmado == 0


def test_checkin_se_actualiza_y_recalcula_ambas_celdas(sesion: Session, escenario: Evento) -> None:
    def checkin(id_h3: str, estado: EstadoCheckIn) -> list[str]:
        nuevo = CheckIn(id_anonimo="p1", id_h3=id_h3, estado=estado, hora=HORA)
        return pipeline.ingresar_checkin(sesion, nuevo, escenario, HORA, CONFIG, ZONA)

    checkin(EPICENTRO, EstadoCheckIn.sin_respuesta)
    celdas = checkin(LEJANA, EstadoCheckIn.bien)
    guardados = list(sesion.scalars(select(CheckIn)))
    assert len(guardados) == 1 and guardados[0].id_h3 == LEJANA
    assert celdas == sorted([EPICENTRO, LEJANA])
