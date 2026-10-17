"""Rutas de eventos, alertas y propuestas comunitarias."""

from __future__ import annotations

from fastapi import APIRouter, HTTPException, status
from sqlalchemy import select

from app.activacion.emisor import EventoYaActivo, contenido_alerta, emitir
from app.activacion.fuentes.base import EntradaInvalida, NuevoEvento
from app.activacion.fuentes.fuente_cap import FuenteCap
from app.activacion.fuentes.fuente_comunitaria import FuenteComunitaria
from app.activacion.fuentes.fuente_manual import FuenteManual
from app.activacion.fuentes.fuente_sismologica import FuenteSismologica
from app.api.dependencias import Admin, AhoraDep, CiudadDep, ConfigDep, FirmadorDep, SesionDep
from app.api.vistas import resumen_celdas
from app.eventos.bus import Tema, bus
from app.modelos.enums import EstadoPropuesta, FuenteEvento
from app.modelos.esquemas import (
    AlertaSalida,
    Emision,
    EmisionCap,
    EmisionComunitaria,
    EmisionManual,
    EmisionSismologica,
    EmisionSalida,
    EventoSalida,
    PropuestaSalida,
)
from app.modelos.tablas import Evento, PropuestaComunitaria
from app.motor.pipeline import evento_activo
from app.privacidad.borrado import cerrar_evento

router = APIRouter(tags=["eventos"])


def _a_nuevo_evento(
    entrada: Emision, sesion: SesionDep, ahora: AhoraDep
) -> tuple[NuevoEvento, PropuestaComunitaria | None]:
    try:
        match entrada:
            case EmisionManual():
                manual = NuevoEvento(
                    tipo=entrada.tipo,
                    fuente=FuenteEvento.manual,
                    lat=entrada.lat,
                    lon=entrada.lon,
                    hora=entrada.hora or ahora,
                    magnitud=entrada.magnitud,
                    profundidad_km=entrada.profundidad_km,
                    zona_afectada=entrada.zona_afectada,
                )
                return FuenteManual().convertir(manual), None
            case EmisionCap():
                return FuenteCap().convertir(entrada.xml), None
            case EmisionSismologica():
                return FuenteSismologica().convertir(entrada.geojson), None
            case EmisionComunitaria():
                propuesta = sesion.get(PropuestaComunitaria, entrada.id_propuesta)
                if propuesta is None or propuesta.estado is not EstadoPropuesta.pendiente:
                    raise HTTPException(status.HTTP_404_NOT_FOUND, "Propuesta no pendiente")
                return FuenteComunitaria(entrada.tipo).convertir(propuesta), propuesta
    except EntradaInvalida as error:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_CONTENT, str(error)) from error
    raise HTTPException(status.HTTP_422_UNPROCESSABLE_CONTENT, "Fuente desconocida")


@router.post(
    "/eventos",
    status_code=status.HTTP_201_CREATED,
    dependencies=[Admin],
    response_model=EmisionSalida,
)
def emitir_alerta(
    entrada: Emision,
    sesion: SesionDep,
    ahora: AhoraDep,
    config: ConfigDep,
    ciudad: CiudadDep,
    firmador: FirmadorDep,
) -> EmisionSalida:
    nuevo, propuesta = _a_nuevo_evento(entrada, sesion, ahora)
    try:
        resultado = emitir(sesion, nuevo, firmador, ahora, config, ciudad.zona)
    except EventoYaActivo as error:
        raise HTTPException(status.HTTP_409_CONFLICT, str(error)) from error
    if propuesta is not None:
        propuesta.estado = EstadoPropuesta.aprobada
        propuesta.id_evento = resultado.evento.id
    sesion.commit()

    salida = EmisionSalida(
        evento=EventoSalida.model_validate(resultado.evento),
        alerta=AlertaSalida(**resultado.alerta.como_dict()),
    )
    bus.publicar(Tema.evento_emitido, salida.model_dump(mode="json"))
    bus.publicar(
        Tema.celda_actualizada,
        {"celdas": [c.model_dump(mode="json") for c in resumen_celdas(sesion, resultado.evento)]},
    )
    return salida


@router.post("/eventos/{id_evento}/cerrar", dependencies=[Admin], response_model=EventoSalida)
def cerrar(id_evento: int, sesion: SesionDep, ahora: AhoraDep, config: ConfigDep) -> Evento:
    evento = sesion.get(Evento, id_evento)
    if evento is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Evento no encontrado")
    if evento.fecha_cierre is not None:
        raise HTTPException(status.HTTP_409_CONFLICT, "El evento ya está cerrado")
    cerrar_evento(sesion, evento, ahora, config.privacidad)
    sesion.commit()
    bus.publicar(Tema.evento_cerrado, EventoSalida.model_validate(evento).model_dump(mode="json"))
    return evento


@router.get("/eventos/activo", response_model=EventoSalida | None)
def activo(sesion: SesionDep) -> Evento | None:
    return evento_activo(sesion)


@router.get("/alertas/activa", response_model=AlertaSalida)
def alerta_activa(sesion: SesionDep, firmador: FirmadorDep) -> AlertaSalida:
    """La alerta firmada vigente, para la app que se conecta después de la emisión."""
    evento = evento_activo(sesion)
    if evento is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "No hay alerta activa")
    return AlertaSalida(**firmador.firmar(contenido_alerta(evento)).como_dict())


@router.get("/alertas/clave-publica")
def clave_publica(firmador: FirmadorDep) -> dict[str, str]:
    return {"algoritmo": "Ed25519", "clave_publica": firmador.clave_publica_base64()}


@router.get("/propuestas", dependencies=[Admin], response_model=list[PropuestaSalida])
def propuestas(sesion: SesionDep) -> list[PropuestaComunitaria]:
    return list(
        sesion.scalars(
            select(PropuestaComunitaria)
            .where(PropuestaComunitaria.estado == EstadoPropuesta.pendiente)
            .order_by(PropuestaComunitaria.hora)
        )
    )


@router.post(
    "/propuestas/{id_propuesta}/descartar", dependencies=[Admin], response_model=PropuestaSalida
)
def descartar_propuesta(id_propuesta: int, sesion: SesionDep) -> PropuestaComunitaria:
    propuesta = sesion.get(PropuestaComunitaria, id_propuesta)
    if propuesta is None or propuesta.estado is not EstadoPropuesta.pendiente:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Propuesta no pendiente")
    propuesta.estado = EstadoPropuesta.descartada
    sesion.commit()
    return propuesta
