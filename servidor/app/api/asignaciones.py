"""Recomendaciones del motor y decisiones humanas sobre ellas (sección 9.7)."""

from __future__ import annotations

from fastapi import APIRouter, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.api.dependencias import Admin, AhoraDep, CiudadDep, ConfigDep, RedDep, SesionDep
from app.api.vistas import asignacion_salida, resumen_celdas
from app.eventos.bus import Tema, bus
from app.modelos.enums import DecisionAsignacion, EstadoEquipo, EstadoReporte
from app.modelos.esquemas import AsignacionSalida, DecisionEntrada
from app.modelos.tablas import Asignacion, Celda, Equipo, Evento, Reporte
from app.motor.pipeline import (
    DECISIONES_EN_CURSO,
    actualizar_recomendaciones,
    evento_activo,
    recalcular_celda,
)
from app.rutas.servicio import asignaciones_activas, destino_de, estimador, trazar_ruta

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


def _publicar_asignacion(sesion: Session, asignacion: Asignacion) -> AsignacionSalida:
    """Para la unidad: destino (el punto exacto si es un pedido de ayuda) y su ruta.
    Para el ciudadano (por su id_anonimo): el aviso de ayuda en camino."""
    salida = asignacion_salida(sesion, asignacion)
    lat, lon = destino_de(sesion, asignacion)
    reporte = sesion.get(Reporte, asignacion.id_reporte) if asignacion.id_reporte else None
    bus.publicar(
        Tema.asignacion_cambiada,
        {
            "asignacion": salida.model_dump(mode="json"),
            "destino": {"lat": lat, "lon": lon},
            "id_anonimo": reporte.id_anonimo if reporte else None,
        },
    )
    return salida


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
    sesion: SesionDep, ahora: AhoraDep, config: ConfigDep, red: RedDep
) -> list[AsignacionSalida]:
    """Próximas decisiones sugeridas, de mayor a menor prioridad, cada una con su motivo."""
    evento = evento_activo(sesion)
    if evento is None:
        return []
    vigentes = actualizar_recomendaciones(
        sesion, evento, ahora, config, estimador(sesion, red, config)
    )
    sesion.commit()
    return [asignacion_salida(sesion, a) for a in vigentes]


@router.get("/asignaciones", response_model=list[AsignacionSalida])
def listar_asignaciones(sesion: SesionDep, activas: bool = True) -> list[AsignacionSalida]:
    """Asignaciones en curso (con su ruta), para el tablero. `activas=false`: todas las decididas."""
    if activas:
        elegidas = asignaciones_activas(sesion)
    else:
        elegidas = list(
            sesion.scalars(
                select(Asignacion)
                .where(Asignacion.decision != DecisionAsignacion.pendiente)
                .order_by(Asignacion.hora.desc())
            )
        )
    return [asignacion_salida(sesion, a) for a in elegidas]


@router.post("/asignaciones/{id_asignacion}/decision", response_model=AsignacionSalida)
def decidir(
    id_asignacion: int,
    entrada: DecisionEntrada,
    sesion: SesionDep,
    ahora: AhoraDep,
    config: ConfigDep,
    red: RedDep,
) -> AsignacionSalida:
    """La decisión final siempre es humana: aceptar, cambiar el equipo o rechazar.
    Al aceptar se traza la ruta del equipo, que la unidad recibe con la asignación."""
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
                estimador(sesion, red, config).minutos(
                    (equipo.lat, equipo.lon), destino_de(sesion, asignacion)
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
        trazar_ruta(sesion, red, asignacion, config)

    sesion.flush()
    vigentes = actualizar_recomendaciones(
        sesion, evento, ahora, config, estimador(sesion, red, config)
    )
    sesion.commit()
    salida = _publicar_asignacion(sesion, asignacion)
    _publicar_recomendaciones(sesion, vigentes)
    return salida


@router.post("/asignaciones/{id_asignacion}/atendida", response_model=AsignacionSalida)
def marcar_atendida(
    id_asignacion: int,
    sesion: SesionDep,
    ahora: AhoraDep,
    config: ConfigDep,
    ciudad: CiudadDep,
    red: RedDep,
) -> AsignacionSalida:
    """La unidad terminó (pantalla B9): el equipo queda libre y la celda se recalcula."""
    evento = _evento_o_409(sesion)
    asignacion = _asignacion_o_404(sesion, id_asignacion)
    if asignacion.decision not in DECISIONES_EN_CURSO or asignacion.hora_fin is not None:
        raise HTTPException(status.HTTP_409_CONFLICT, "La asignación no está en curso")
    asignacion.hora_fin = ahora
    if asignacion.id_reporte is not None:
        sesion.get_one(Reporte, asignacion.id_reporte).estado = EstadoReporte.atendido
    equipo = sesion.get_one(Equipo, asignacion.id_equipo)
    equipo.estado = EstadoEquipo.disponible
    # El equipo queda donde terminó el trabajo
    equipo.lat, equipo.lon = destino_de(sesion, asignacion)
    celda = sesion.get_one(Celda, asignacion.id_h3)
    celda.ultima_atencion = ahora
    sesion.flush()
    recalcular_celda(sesion, celda, evento, ahora, config, ciudad.zona)
    vigentes = actualizar_recomendaciones(
        sesion, evento, ahora, config, estimador(sesion, red, config)
    )
    sesion.commit()

    salida = _publicar_asignacion(sesion, asignacion)
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
