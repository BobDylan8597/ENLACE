"""Recomendaciones del motor y decisiones humanas sobre ellas (sección 9.7)."""

from __future__ import annotations

from fastapi import APIRouter, HTTPException, status
from sqlalchemy.orm import Session

from app.api.dependencias import Admin, AhoraDep, CiudadDep, ConfigDep, SesionDep
from app.api.vistas import asignacion_salida, resumen_celdas
from app.eventos.bus import Tema, bus
from app.modelos.enums import DecisionAsignacion, EstadoEquipo, EstadoReporte
from app.modelos.esquemas import AsignacionSalida, DecisionEntrada
from app.modelos.tablas import Asignacion, Celda, Equipo, Evento, Reporte
from app.motor.asignacion import EstimadorLineaRecta
from app.motor.geo import centro_celda
from app.motor.pipeline import (
    DECISIONES_EN_CURSO,
    actualizar_recomendaciones,
    evento_activo,
    recalcular_celda,
)

router = APIRouter(tags=["asignaciones"], dependencies=[Admin])


def _evento_o_409(sesion: Session) -> Evento:
    evento = evento_activo(sesion)
    if evento is None:
        raise HTTPException(status.HTTP_409_CONFLICT, "No hay un evento activo")
    return evento


def _asignacion_o_404(sesion: Session, id_asignacion: int) -> Asignacion:
    asignacion = sesion.get(Asignacion, id_asignacion)
    if asignacion is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Asignación no encontrada")
    return asignacion


def _publicar_recomendaciones(sesion: Session, recomendaciones: list[Asignacion]) -> None:
    bus.publicar(
        Tema.recomendaciones_actualizadas,
        {
            "recomendaciones": [
                asignacion_salida(sesion, a).model_dump(mode="json") for a in recomendaciones
            ]
        },
    )


@router.get("/recomendaciones", response_model=list[AsignacionSalida])
def recomendaciones(
    sesion: SesionDep, ahora: AhoraDep, config: ConfigDep
) -> list[AsignacionSalida]:
    """Próximas decisiones sugeridas, de mayor a menor prioridad, cada una con su motivo."""
    evento = evento_activo(sesion)
    if evento is None:
        return []
    vigentes = actualizar_recomendaciones(sesion, evento, ahora, config)
    sesion.commit()
    return [asignacion_salida(sesion, a) for a in vigentes]


@router.post("/asignaciones/{id_asignacion}/decision", response_model=AsignacionSalida)
def decidir(
    id_asignacion: int,
    entrada: DecisionEntrada,
    sesion: SesionDep,
    ahora: AhoraDep,
    config: ConfigDep,
) -> AsignacionSalida:
    """La decisión final siempre es humana: aceptar, cambiar el equipo o rechazar."""
    evento = _evento_o_409(sesion)
    asignacion = _asignacion_o_404(sesion, id_asignacion)
    if asignacion.decision is not DecisionAsignacion.pendiente:
        raise HTTPException(status.HTTP_409_CONFLICT, "Esta asignación ya fue decidida")

    asignacion.decidido_por = entrada.decidido_por
    asignacion.hora = ahora
    if entrada.decision == "rechazada":
        asignacion.decision = DecisionAsignacion.rechazada
    else:
        sugerido = sesion.get_one(Equipo, asignacion.id_equipo)
        equipo = sugerido
        if entrada.decision == "modificada":
            elegido = sesion.get(Equipo, entrada.id_equipo)
            if elegido is None:
                raise HTTPException(status.HTTP_404_NOT_FOUND, "Equipo no encontrado")
            equipo = elegido
        if equipo.estado is not EstadoEquipo.disponible:
            raise HTTPException(status.HTTP_409_CONFLICT, f"{equipo.nombre} no está disponible")
        if equipo is not sugerido:
            asignacion.id_equipo = equipo.id
            asignacion.tiempo_estimado_min = round(
                EstimadorLineaRecta(config.asignacion).minutos(
                    (equipo.lat, equipo.lon), centro_celda(asignacion.id_h3)
                ),
                1,
            )
            asignacion.motivo += (
                f" Cambiado por {entrada.decidido_por}: {equipo.nombre} en lugar de "
                f"{sugerido.nombre}."
            )
            asignacion.decision = DecisionAsignacion.modificada
        else:
            asignacion.decision = DecisionAsignacion.aceptada
        equipo.estado = EstadoEquipo.en_ruta
        if asignacion.id_reporte is not None:
            sesion.get_one(Reporte, asignacion.id_reporte).estado = EstadoReporte.asignado

    sesion.flush()
    vigentes = actualizar_recomendaciones(sesion, evento, ahora, config)
    sesion.commit()
    salida = asignacion_salida(sesion, asignacion)
    bus.publicar(Tema.asignacion_cambiada, salida.model_dump(mode="json"))
    _publicar_recomendaciones(sesion, vigentes)
    return salida


@router.post("/asignaciones/{id_asignacion}/atendida", response_model=AsignacionSalida)
def marcar_atendida(
    id_asignacion: int,
    sesion: SesionDep,
    ahora: AhoraDep,
    config: ConfigDep,
    ciudad: CiudadDep,
) -> AsignacionSalida:
    """La unidad terminó (pantalla B9): el equipo queda libre y la celda se recalcula."""
    evento = _evento_o_409(sesion)
    asignacion = _asignacion_o_404(sesion, id_asignacion)
    if asignacion.decision not in DECISIONES_EN_CURSO:
        raise HTTPException(status.HTTP_409_CONFLICT, "La asignación no está en curso")
    if asignacion.id_reporte is not None:
        sesion.get_one(Reporte, asignacion.id_reporte).estado = EstadoReporte.atendido
    equipo = sesion.get_one(Equipo, asignacion.id_equipo)
    equipo.estado = EstadoEquipo.disponible
    # El equipo queda donde terminó el trabajo
    equipo.lat, equipo.lon = centro_celda(asignacion.id_h3)
    celda = sesion.get_one(Celda, asignacion.id_h3)
    celda.ultima_atencion = ahora
    sesion.flush()
    recalcular_celda(sesion, celda, evento, ahora, config, ciudad.zona)
    vigentes = actualizar_recomendaciones(sesion, evento, ahora, config)
    sesion.commit()

    salida = asignacion_salida(sesion, asignacion)
    bus.publicar(Tema.asignacion_cambiada, salida.model_dump(mode="json"))
    bus.publicar(
        Tema.celda_actualizada,
        {
            "celdas": [
                c.model_dump(mode="json")
                for c in resumen_celdas(sesion, evento, [asignacion.id_h3])
            ]
        },
    )
    _publicar_recomendaciones(sesion, vigentes)
    return salida
