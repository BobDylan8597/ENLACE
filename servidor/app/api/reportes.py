"""Rutas de entrada desde el terreno: reportes (incluidos lotes de mulas de datos),
check-ins anónimos y SMS reenviados por el receptor; y la bandeja de reportes del admin."""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from datetime import datetime
from typing import Annotated, Any

import h3
from fastapi import APIRouter, Body, HTTPException, Query, status
from pydantic import ValidationError
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.activacion.fuentes.fuente_comunitaria import detectar_propuestas
from app.api.dependencias import (
    Admin,
    AhoraDep,
    CiudadDep,
    ConfigDep,
    PrivilegiadoDep,
    SesionDep,
)
from app.api.formato_sms import SmsInvalido, interpretar_sms
from app.api.vistas import (
    asignacion_salida,
    identidades_de_ayuda,
    reporte_salida,
    resumen_celdas,
)
from app.ciudad import Ciudad
from app.config import Configuracion
from app.eventos.bus import Tema, bus
from app.modelos.enums import EstadoEvento, EstadoReporte, OrigenReporte, TipoReporte
from app.modelos.esquemas import (
    CheckInEntrada,
    IdentidadEntrada,
    IdentidadSalida,
    PropuestaSalida,
    Rechazo,
    ReporteDetalle,
    ReporteEntrada,
    ReporteSalida,
    ResultadoReportes,
    SmsEntrante,
)
from app.modelos.tablas import Asignacion, Celda, CheckIn, Evento, Identidad, Reporte
from app.motor.pipeline import (
    a_datos_reporte,
    es_celda_valida,
    evento_activo,
    ingresar_checkin,
    ingresar_reportes,
    reportes_por_prioridad,
)
from app.motor.prioridad import prioridad_reporte
from app.privacidad.borrado import plazo_borrado

router = APIRouter(tags=["reportes"])

# Espacio de nombres para derivar identificadores estables de los SMS
ESPACIO_SMS = uuid.UUID("6f1c7a52-5d0e-4c43-9a51-2f7d1e0b8e11")


@dataclass
class _Preparado:
    reporte: Reporte
    punto: tuple[float, float] | None
    identidad: IdentidadEntrada | None


def _preparar(
    entrada: ReporteEntrada, ahora: datetime, config: Configuracion, privilegiado: bool
) -> _Preparado:
    if entrada.origen is OrigenReporte.brigada and not privilegiado:
        # Pesa 1.0 en el motor: solo una brigada (con su clave) puede enviarlo
        raise ValueError("Un reporte de brigada requiere la clave de brigada")
    if entrada.saltos_nearby > config.nearby.saltos_max:
        raise ValueError(f"Superó el límite de {config.nearby.saltos_max} saltos")
    punto = (entrada.lat, entrada.lon) if entrada.lat is not None else None
    if punto is not None:
        id_h3 = h3.latlng_to_cell(punto[0], punto[1], config.h3.resolucion)
    else:
        id_h3 = entrada.id_h3 or ""
    if not es_celda_valida(id_h3, config):
        raise ValueError(f"Celda h3 inválida (se espera resolución {config.h3.resolucion})")
    reporte = Reporte(
        id_mensaje=entrada.id_mensaje,
        id_anonimo=entrada.id_anonimo,
        id_evento=entrada.id_evento,
        id_h3=id_h3,
        origen=entrada.origen,
        tipo=entrada.tipo,
        personas=entrada.personas,
        atrapados=entrada.atrapados,
        heridos=entrada.heridos,
        vulnerables=entrada.vulnerables,
        necesidades=[n.value for n in entrada.necesidades],
        nivel_dano=entrada.nivel_dano,
        saltos_nearby=entrada.saltos_nearby,
        hora=entrada.hora or ahora,
        confiabilidad=0.0,
    )
    # Solo un pedido de ayuda conserva el punto exacto (sección 13)
    if entrada.tipo is not TipoReporte.ayuda:
        punto = None
    return _Preparado(reporte, punto, entrada.identidad)


def _guardar_identidad(
    sesion: Session,
    preparado: _Preparado,
    ahora: datetime,
    config: Configuracion,
) -> None:
    if preparado.punto is None:
        return
    reporte = preparado.reporte
    existente = sesion.scalars(
        select(Identidad).where(
            Identidad.id_anonimo == reporte.id_anonimo,
            (
                Identidad.id_evento.is_(None)
                if reporte.id_evento is None
                else Identidad.id_evento == reporte.id_evento
            ),
        )
    ).first()
    identidad = existente or Identidad(id_anonimo=reporte.id_anonimo, id_evento=reporte.id_evento)
    identidad.lat, identidad.lon = preparado.punto
    datos = preparado.identidad
    if datos is not None:
        identidad.nombre = datos.nombre or identidad.nombre
        identidad.telefono = datos.telefono or identidad.telefono
        identidad.condiciones = datos.condiciones or identidad.condiciones
    if reporte.id_evento is None:
        # Sin alerta activa: plazo provisional por si nunca llega a haber un evento
        identidad.fecha_borrado = plazo_borrado(ahora, config.privacidad)
    if existente is None:
        sesion.add(identidad)


def procesar_reportes(
    sesion: Session,
    items: list[dict[str, Any] | ReporteEntrada],
    ahora: datetime,
    config: Configuracion,
    ciudad: Ciudad,
    privilegiado: bool = False,
) -> ResultadoReportes:
    preparados: list[_Preparado] = []
    rechazados: list[Rechazo] = []
    for item in items:
        id_mensaje = item.id_mensaje if isinstance(item, ReporteEntrada) else item.get("id_mensaje")
        try:
            entrada = (
                item if isinstance(item, ReporteEntrada) else ReporteEntrada.model_validate(item)
            )
            preparados.append(_preparar(entrada, ahora, config, privilegiado))
        except (ValidationError, ValueError) as error:
            detalle = (
                "; ".join(e["msg"] for e in error.errors())
                if isinstance(error, ValidationError)
                else str(error)
            )
            rechazados.append(
                Rechazo(id_mensaje=str(id_mensaje) if id_mensaje else None, error=detalle)
            )

    resultado = ingresar_reportes(
        sesion, [p.reporte for p in preparados], ahora, config, ciudad.zona
    )
    nuevos = {r.id_mensaje for r in resultado.nuevos}
    for preparado in preparados:
        if preparado.reporte.id_mensaje in nuevos:
            _guardar_identidad(sesion, preparado, ahora, config)
    propuestas = detectar_propuestas(sesion, ahora, config.activacion)
    sesion.commit()

    if resultado.nuevos:
        evento = evento_activo(sesion)
        bus.publicar(
            Tema.reporte_recibido,
            {"ids": sorted(nuevos), "celdas": resultado.celdas_actualizadas},
        )
        bus.publicar(
            Tema.celda_actualizada,
            {
                "celdas": [
                    c.model_dump(mode="json")
                    for c in resumen_celdas(sesion, evento, resultado.celdas_actualizadas)
                ]
            },
        )
    if resultado.reportes_actualizados:
        bus.publicar(Tema.reporte_actualizado, {"ids": resultado.reportes_actualizados})
    for propuesta in propuestas:
        bus.publicar(
            Tema.propuesta_comunitaria,
            PropuestaSalida.model_validate(propuesta).model_dump(mode="json"),
        )
    return ResultadoReportes(
        aceptados=sorted(nuevos), repetidos=resultado.repetidos, rechazados=rechazados
    )


@router.post("/reportes", response_model=ResultadoReportes)
def recibir_reportes(
    cuerpo: Annotated[dict[str, Any] | list[dict[str, Any]], Body()],
    sesion: SesionDep,
    ahora: AhoraDep,
    config: ConfigDep,
    ciudad: CiudadDep,
    privilegiado: PrivilegiadoDep,
) -> ResultadoReportes:
    """Uno o varios reportes. Cada uno se valida por separado: un mensaje dañado en el lote de
    una mula de datos no impide recibir los demás."""
    items: list[dict[str, Any] | ReporteEntrada] = list(
        cuerpo if isinstance(cuerpo, list) else [cuerpo]
    )
    return procesar_reportes(sesion, items, ahora, config, ciudad, privilegiado)


def _evento_consultado(sesion: Session, id_evento: int | None) -> Evento | None:
    return sesion.get(Evento, id_evento) if id_evento is not None else evento_activo(sesion)


def _duplicados_por_padre(sesion: Session) -> dict[str, int]:
    filas = sesion.execute(
        select(Reporte.id_reporte_padre, func.count())
        .where(Reporte.id_reporte_padre.is_not(None))
        .group_by(Reporte.id_reporte_padre)
    ).all()
    return {padre: cantidad for padre, cantidad in filas}


@router.get("/reportes", dependencies=[Admin], response_model=list[ReporteSalida])
def bandeja(
    sesion: SesionDep,
    ahora: AhoraDep,
    config: ConfigDep,
    estado: EstadoReporte | None = None,
    tipo: TipoReporte | None = None,
    origen: OrigenReporte | None = None,
    id_h3: str | None = None,
    id_evento: int | None = None,
    limite: Annotated[int, Query(ge=1, le=1000)] = 200,
) -> list[ReporteSalida]:
    """Bandeja del centro de reportes, ordenada por prioridad (duplicados agrupados)."""
    evento = _evento_consultado(sesion, id_evento)
    if evento is None:
        return []
    items = [
        i
        for i in reportes_por_prioridad(sesion, evento, ahora, config)
        if (estado is None or i.reporte.estado is estado)
        and (tipo is None or i.reporte.tipo is tipo)
        and (origen is None or i.reporte.origen is origen)
        and (id_h3 is None or i.reporte.id_h3 == id_h3)
    ][:limite]
    reportes = [i.reporte for i in items]
    celdas = {c.id_h3: c for c in sesion.scalars(select(Celda))}
    identidades = identidades_de_ayuda(sesion, reportes)
    duplicados = _duplicados_por_padre(sesion)
    return [
        reporte_salida(
            i.reporte,
            i.prioridad,
            celdas,
            identidades,
            duplicados.get(i.reporte.id_mensaje, 0),
        )
        for i in items
    ]


@router.get("/reportes/{id_mensaje}", dependencies=[Admin], response_model=ReporteDetalle)
def detalle_reporte(
    id_mensaje: str, sesion: SesionDep, ahora: AhoraDep, config: ConfigDep
) -> ReporteDetalle:
    reporte = sesion.get(Reporte, id_mensaje)
    if reporte is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Reporte no encontrado")
    celdas = {c.id_h3: c for c in sesion.scalars(select(Celda).where(Celda.id_h3 == reporte.id_h3))}
    celda = celdas.get(reporte.id_h3)
    prioridad = prioridad_reporte(
        a_datos_reporte(reporte),
        celda.prioridad if celda else 0.0,
        celda.vulnerabilidad if celda else config.celdas.vulnerabilidad_por_defecto,
        ahora,
        config.prioridad.reporte,
    )
    identidades = identidades_de_ayuda(sesion, [reporte])
    salida = reporte_salida(
        reporte,
        prioridad,
        celdas,
        identidades,
        _duplicados_por_padre(sesion).get(reporte.id_mensaje, 0),
    )
    identidad = identidades.get((reporte.id_anonimo, reporte.id_evento))
    asignaciones = sesion.scalars(
        select(Asignacion).where(Asignacion.id_reporte == id_mensaje).order_by(Asignacion.hora)
    )
    return ReporteDetalle(
        **salida.model_dump(),
        identidad=IdentidadSalida.model_validate(identidad) if identidad else None,
        asignaciones=[asignacion_salida(sesion, a) for a in asignaciones],
    )


@router.post("/checkins", status_code=status.HTTP_202_ACCEPTED)
def recibir_checkin(
    entrada: CheckInEntrada,
    sesion: SesionDep,
    ahora: AhoraDep,
    config: ConfigDep,
    ciudad: CiudadDep,
) -> dict[str, list[str]]:
    """Check-in anónimo: solo la celda h3, nunca coordenadas (el esquema no las admite)."""
    evento = sesion.get(Evento, entrada.id_evento)
    if evento is None or evento.estado is not EstadoEvento.activo:
        raise HTTPException(status.HTTP_409_CONFLICT, "El evento no está activo")
    if not es_celda_valida(entrada.id_h3, config):
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_CONTENT, "Celda h3 inválida")
    checkin = CheckIn(
        id_anonimo=entrada.id_anonimo,
        id_h3=entrada.id_h3,
        estado=entrada.estado,
        hora=entrada.hora or ahora,
    )
    celdas = ingresar_checkin(sesion, checkin, evento, ahora, config, ciudad.zona)
    sesion.commit()
    bus.publicar(Tema.checkin_recibido, {"id_evento": evento.id, "celdas": celdas})
    bus.publicar(
        Tema.celda_actualizada,
        {"celdas": [c.model_dump(mode="json") for c in resumen_celdas(sesion, evento, celdas)]},
    )
    return {"celdas_actualizadas": celdas}


@router.post("/sms/entrante", response_model=ResultadoReportes)
def recibir_sms(
    entrada: SmsEntrante,
    sesion: SesionDep,
    ahora: AhoraDep,
    config: ConfigDep,
    ciudad: CiudadDep,
) -> ResultadoReportes:
    """SMS reenviado por el receptor. Un SMS siempre es un pedido de ayuda."""
    try:
        sms = interpretar_sms(entrada.texto)
    except SmsInvalido as error:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_CONTENT, str(error)) from error
    hora = entrada.hora or ahora
    # El mismo SMS reenviado dos veces genera el mismo id_mensaje y se descarta
    clave = f"{entrada.remitente}|{entrada.texto.strip()}|{hora.replace(second=0, microsecond=0).isoformat()}"
    reporte = ReporteEntrada(
        id_mensaje=str(uuid.uuid5(ESPACIO_SMS, clave)),
        id_anonimo=str(uuid.uuid5(ESPACIO_SMS, entrada.remitente)),
        origen=OrigenReporte.sms,
        tipo=TipoReporte.ayuda,
        lat=sms.lat,
        lon=sms.lon,
        personas=sms.personas,
        atrapados=sms.atrapados,
        heridos=sms.heridos,
        necesidades=sms.necesidades,
        hora=hora,
        identidad=IdentidadEntrada(telefono=entrada.remitente),
    )
    return procesar_reportes(sesion, [reporte], ahora, config, ciudad)
