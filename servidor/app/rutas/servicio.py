"""Bloqueos y rutas sobre la base de datos: registro de reportes de bloqueo, penalizaciones
vigentes, ruta de cada asignación y recálculo de las rutas activas afectadas."""

from __future__ import annotations

from collections import OrderedDict
from dataclasses import dataclass
from datetime import datetime
from enum import StrEnum
from functools import lru_cache

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.ciudad import directorio_ciudad
from app.config import Configuracion, obtener_config
from app.modelos.enums import (
    AlcanceBloqueo,
    DecisionAsignacion,
    EstadoBloqueo,
    TipoBloqueo,
    TipoReporte,
)
from app.modelos.tablas import Asignacion, Bloqueo, Equipo, Identidad, Reporte
from app.motor.asignacion import EstimadorLineaRecta, EstimadorTiempo
from app.motor.geo import centro_celda, distancia_km
from app.rutas.bloqueos import BloqueoEnRed, Modo, Penalizaciones, estado_segun_reportes
from app.rutas.calculo import EstimadorRutas, calcular_ruta, tramos_de_geojson
from app.rutas.grafo import GrafoCalles, Tramo

# Caché de tiempos por origen compartida entre peticiones; se invalida sola al cambiar bloqueos
_CACHE_TIEMPOS: OrderedDict[tuple[int, int], dict[int, float]] = OrderedDict()


@lru_cache
def obtener_red() -> GrafoCalles | None:
    config = obtener_config()
    return GrafoCalles.cargar(directorio_ciudad(config) / config.rutas.archivo_calles)


# ── Bloqueos ───────────────────────────────────────────────


class AccionBloqueo(StrEnum):
    reportar = "reportar"
    confirmar = "confirmar"
    descartar = "descartar"


class BloqueoNoEncontrado(LookupError):
    pass


@dataclass(frozen=True)
class CambioBloqueo:
    bloqueo: Bloqueo
    # El estado cambió (y con él, el costo de la calle)
    cambio_estado: bool


def _tramos(bloqueo: Bloqueo, red: GrafoCalles | None) -> tuple[Tramo, ...]:
    if red is None or bloqueo.nodo_u is None or bloqueo.nodo_v is None:
        return ()
    return tuple(red.tramos_de_la_calle((bloqueo.nodo_u, bloqueo.nodo_v, bloqueo.clave_tramo or 0)))


def bloqueos_activos(sesion: Session) -> list[Bloqueo]:
    return list(
        sesion.scalars(
            select(Bloqueo).where(Bloqueo.estado != EstadoBloqueo.descartado).order_by(Bloqueo.hora)
        )
    )


def penalizaciones(
    sesion: Session, red: GrafoCalles | None, modo: Modo, config: Configuracion
) -> Penalizaciones:
    return Penalizaciones(
        (
            BloqueoEnRed(_tramos(b, red), b.estado, b.alcance)
            for b in bloqueos_activos(sesion)
            if _tramos(b, red)
        ),
        modo,
        config.rutas,
    )


def _cercano(sesion: Session, lat: float, lon: float, config: Configuracion) -> Bloqueo | None:
    radio_km = config.rutas.radio_fusion_bloqueo_m / 1000
    candidatos = [
        (distancia_km(lat, lon, b.lat, b.lon), b)
        for b in bloqueos_activos(sesion)
        if distancia_km(lat, lon, b.lat, b.lon) <= radio_km
    ]
    return min(candidatos, key=lambda par: par[0])[1] if candidatos else None


def _sumar_reportante(bloqueo: Bloqueo, reportante: str, es_brigada: bool) -> None:
    if reportante not in bloqueo.reportantes:
        bloqueo.reportantes = [*bloqueo.reportantes, reportante]
    if es_brigada:
        bloqueo.confirmado_por_brigada = True


def registrar_bloqueo(
    sesion: Session,
    red: GrafoCalles | None,
    accion: AccionBloqueo,
    reportante: str,
    es_brigada: bool,
    ahora: datetime,
    config: Configuracion,
    id_bloqueo: int | None = None,
    lat: float | None = None,
    lon: float | None = None,
    tipo: TipoBloqueo = TipoBloqueo.otro,
    alcance: AlcanceBloqueo = AlcanceBloqueo.total,
) -> CambioBloqueo:
    """Reportar crea un bloqueo o confirma el que ya existe a menos de `radio_fusion_bloqueo_m`.
    Una misma persona no puede confirmar dos veces. Descartar restaura la calle."""
    if accion is AccionBloqueo.reportar:
        if lat is None or lon is None:
            raise ValueError("Reportar un bloqueo requiere lat y lon")
        bloqueo = _cercano(sesion, lat, lon, config)
        if bloqueo is None:
            bloqueo = Bloqueo(
                lat=lat,
                lon=lon,
                tipo=tipo,
                alcance=alcance,
                reportantes=[],
                hora=ahora,
                estado=EstadoBloqueo.sin_confirmar,
            )
            cercano = red.tramo_cercano(lat, lon) if red is not None else None
            if cercano is not None and cercano.distancia_m <= config.rutas.distancia_maxima_calle_m:
                bloqueo.nodo_u, bloqueo.nodo_v, bloqueo.clave_tramo = cercano.tramo
            sesion.add(bloqueo)
    else:
        encontrado = sesion.get(Bloqueo, id_bloqueo) if id_bloqueo is not None else None
        if encontrado is None:
            raise BloqueoNoEncontrado(f"Bloqueo {id_bloqueo} no encontrado")
        bloqueo = encontrado

    anterior = bloqueo.estado if bloqueo.id is not None else None
    if accion is AccionBloqueo.descartar:
        bloqueo.estado = EstadoBloqueo.descartado
    else:
        _sumar_reportante(bloqueo, reportante, es_brigada)
        bloqueo.estado = estado_segun_reportes(
            len(bloqueo.reportantes), bloqueo.confirmado_por_brigada, config.rutas
        )
    bloqueo.confirmaciones = len(bloqueo.reportantes)
    bloqueo.actualizado = ahora
    sesion.flush()
    return CambioBloqueo(bloqueo, cambio_estado=anterior != bloqueo.estado)


# ── Rutas de las asignaciones ──────────────────────────────


def estimador(sesion: Session, red: GrafoCalles | None, config: Configuracion) -> EstimadorTiempo:
    if red is None:
        return EstimadorLineaRecta(config.asignacion)
    return EstimadorRutas(
        red,
        penalizaciones(sesion, red, Modo.vehiculo, config),
        config.rutas,
        config.asignacion,
        _CACHE_TIEMPOS,
    )


def destino_de(sesion: Session, asignacion: Asignacion) -> tuple[float, float]:
    """Punto exacto de quien pidió ayuda o, si no lo hay, el centro de la celda."""
    if asignacion.id_reporte is not None:
        reporte = sesion.get(Reporte, asignacion.id_reporte)
        if reporte is not None and reporte.tipo is TipoReporte.ayuda:
            identidad = sesion.scalars(
                select(Identidad).where(
                    Identidad.id_anonimo == reporte.id_anonimo,
                    (
                        Identidad.id_evento.is_(None)
                        if reporte.id_evento is None
                        else Identidad.id_evento == reporte.id_evento
                    ),
                )
            ).first()
            if identidad is not None:
                return identidad.lat, identidad.lon
    return centro_celda(asignacion.id_h3)


def trazar_ruta(
    sesion: Session, red: GrafoCalles | None, asignacion: Asignacion, config: Configuracion
) -> bool:
    """Calcula la ruta del equipo a su destino y la guarda en la asignación.
    Devuelve False si hoy no hay forma de llegar (o no hay grafo de calles)."""
    if red is None:
        return False
    equipo = sesion.get_one(Equipo, asignacion.id_equipo)
    destino = destino_de(sesion, asignacion)
    ruta = calcular_ruta(
        red,
        penalizaciones(sesion, red, Modo.vehiculo, config),
        (equipo.lat, equipo.lon),
        destino,
        config.rutas,
    )
    if ruta is None:
        asignacion.ruta = None
        return False
    asignacion.ruta = ruta.como_geojson(destino=list(destino))
    asignacion.tiempo_estimado_min = ruta.tiempo_min
    return True


def asignaciones_activas(sesion: Session) -> list[Asignacion]:
    return list(
        sesion.scalars(
            select(Asignacion).where(
                Asignacion.decision.in_(
                    (DecisionAsignacion.aceptada, DecisionAsignacion.modificada)
                ),
                Asignacion.hora_fin.is_(None),
            )
        )
    )


def recalcular_rutas_afectadas(
    sesion: Session, red: GrafoCalles | None, bloqueo: Bloqueo, config: Configuracion
) -> list[Asignacion]:
    """Solo las rutas activas que pasan por la calle del bloqueo, desde donde está el equipo."""
    calles = {(u, v) for u, v, _ in _tramos(bloqueo, red)}
    if not calles:
        return []
    afectadas = [a for a in asignaciones_activas(sesion) if tramos_de_geojson(a.ruta) & calles]
    for asignacion in afectadas:
        trazar_ruta(sesion, red, asignacion, config)
    sesion.flush()
    return afectadas
