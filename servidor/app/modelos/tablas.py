"""Tablas SQLite (sección 7 de CLAUDE.md).

Privacidad (sección 13): `CheckIn` y `Reporte` solo guardan la celda h3, nunca coordenadas.
Las coordenadas exactas y los datos personales viven únicamente en `Identidad`,
que solo existe si la persona pidió ayuda y se borra tras cerrar el evento.
"""

from __future__ import annotations

from datetime import datetime
from enum import StrEnum
from typing import Any

from sqlalchemy import (
    JSON,
    BigInteger,
    Boolean,
    Enum,
    Float,
    ForeignKey,
    Integer,
    String,
    Text,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base, FechaUTC, ahora
from app.modelos.enums import (
    AlcanceBloqueo,
    CategoriaCelda,
    DecisionAsignacion,
    EstadoBloqueo,
    EstadoCheckIn,
    EstadoEquipo,
    EstadoEvento,
    EstadoPropuesta,
    EstadoReporte,
    FuenteEvento,
    NivelAdmin,
    NivelDano,
    OrigenReporte,
    TipoBloqueo,
    TipoEvento,
    TipoReporte,
)


def _enum(clase: type[StrEnum]) -> Enum:
    return Enum(
        clase,
        native_enum=False,
        validate_strings=True,
        values_callable=lambda miembros: [m.value for m in miembros],
    )


class Evento(Base):
    __tablename__ = "eventos"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    tipo: Mapped[TipoEvento] = mapped_column(_enum(TipoEvento))
    magnitud: Mapped[float | None] = mapped_column(Float)
    # Extensión al modelo: necesaria para la distancia hipocentral en sismos
    profundidad_km: Mapped[float | None] = mapped_column(Float)
    lat: Mapped[float] = mapped_column(Float)
    lon: Mapped[float] = mapped_column(Float)
    zona_afectada: Mapped[list[str]] = mapped_column(JSON, default=list)
    fuente: Mapped[FuenteEvento] = mapped_column(_enum(FuenteEvento))
    hora: Mapped[datetime] = mapped_column(FechaUTC, default=ahora)
    estado: Mapped[EstadoEvento] = mapped_column(_enum(EstadoEvento), default=EstadoEvento.activo)
    fecha_cierre: Mapped[datetime | None] = mapped_column(FechaUTC)


class Celda(Base):
    __tablename__ = "celdas"

    id_h3: Mapped[str] = mapped_column(String(16), primary_key=True)
    nombre_zona: Mapped[str] = mapped_column(String(120))
    poblacion_por_hora: Mapped[list[float]] = mapped_column(JSON)
    vulnerabilidad: Mapped[float] = mapped_column(Float)
    infraestructura_critica: Mapped[dict[str, Any]] = mapped_column(JSON, default=dict)
    # Extensión al modelo: fracción de edificaciones por tipo, para P(daño | intensidad, tipo)
    tipos_construccion: Mapped[dict[str, float]] = mapped_column(JSON, default=dict)
    # Estimación del modelo de amenaza, antes de cualquier evidencia
    impacto_inicial: Mapped[float] = mapped_column(Float, default=0.0)
    impacto_estimado: Mapped[float] = mapped_column(Float, default=0.0)
    certeza: Mapped[float] = mapped_column(Float, default=0.0)
    # Σ pesos de la evidencia recibida; certeza = 1 − exp(−suma_pesos)
    suma_pesos: Mapped[float] = mapped_column(Float, default=0.0)
    prioridad: Mapped[float] = mapped_column(Float, default=0.0)
    categoria: Mapped[CategoriaCelda] = mapped_column(
        _enum(CategoriaCelda), default=CategoriaCelda.explorar_despues
    )
    ultima_atencion: Mapped[datetime | None] = mapped_column(FechaUTC)


class Reporte(Base):
    __tablename__ = "reportes"

    id_mensaje: Mapped[str] = mapped_column(String(36), primary_key=True)
    id_anonimo: Mapped[str] = mapped_column(String(36), index=True)
    id_evento: Mapped[int | None] = mapped_column(ForeignKey("eventos.id"))
    id_h3: Mapped[str] = mapped_column(String(16), index=True)
    origen: Mapped[OrigenReporte] = mapped_column(_enum(OrigenReporte))
    tipo: Mapped[TipoReporte] = mapped_column(_enum(TipoReporte))
    personas: Mapped[int] = mapped_column(Integer, default=0)
    atrapados: Mapped[int] = mapped_column(Integer, default=0)
    heridos: Mapped[int] = mapped_column(Integer, default=0)
    vulnerables: Mapped[int] = mapped_column(Integer, default=0)
    necesidades: Mapped[list[str]] = mapped_column(JSON, default=list)
    nivel_dano: Mapped[NivelDano | None] = mapped_column(_enum(NivelDano))
    confiabilidad: Mapped[float] = mapped_column(Float)
    saltos_nearby: Mapped[int] = mapped_column(Integer, default=0)
    hora: Mapped[datetime] = mapped_column(FechaUTC, default=ahora)
    estado: Mapped[EstadoReporte] = mapped_column(
        _enum(EstadoReporte), default=EstadoReporte.sin_asignar
    )
    id_reporte_padre: Mapped[str | None] = mapped_column(ForeignKey("reportes.id_mensaje"))


class CheckIn(Base):
    __tablename__ = "checkins"
    __table_args__ = (UniqueConstraint("id_anonimo", "id_evento"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    id_anonimo: Mapped[str] = mapped_column(String(36))
    id_h3: Mapped[str] = mapped_column(String(16), index=True)
    id_evento: Mapped[int] = mapped_column(ForeignKey("eventos.id"))
    hora: Mapped[datetime] = mapped_column(FechaUTC, default=ahora)
    estado: Mapped[EstadoCheckIn] = mapped_column(
        _enum(EstadoCheckIn), default=EstadoCheckIn.sin_respuesta
    )


class Identidad(Base):
    __tablename__ = "identidades"
    __table_args__ = (UniqueConstraint("id_anonimo", "id_evento"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    id_anonimo: Mapped[str] = mapped_column(String(36), index=True)
    nombre: Mapped[str | None] = mapped_column(String(120))
    telefono: Mapped[str | None] = mapped_column(String(32))
    lat: Mapped[float] = mapped_column(Float)
    lon: Mapped[float] = mapped_column(Float)
    condiciones: Mapped[str | None] = mapped_column(Text)
    id_evento: Mapped[int | None] = mapped_column(ForeignKey("eventos.id"))
    fecha_borrado: Mapped[datetime | None] = mapped_column(FechaUTC)


class Equipo(Base):
    __tablename__ = "equipos"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    nombre: Mapped[str] = mapped_column(String(120))
    organismo: Mapped[str] = mapped_column(String(120))
    capacidades: Mapped[list[str]] = mapped_column(JSON, default=list)
    lat: Mapped[float] = mapped_column(Float)
    lon: Mapped[float] = mapped_column(Float)
    estado: Mapped[EstadoEquipo] = mapped_column(
        _enum(EstadoEquipo), default=EstadoEquipo.disponible
    )


class Asignacion(Base):
    __tablename__ = "asignaciones"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    id_equipo: Mapped[int] = mapped_column(ForeignKey("equipos.id"))
    id_reporte: Mapped[str | None] = mapped_column(ForeignKey("reportes.id_mensaje"))
    id_h3: Mapped[str] = mapped_column(String(16))
    motivo: Mapped[str] = mapped_column(Text)
    tiempo_estimado_min: Mapped[float] = mapped_column(Float)
    ruta: Mapped[dict[str, Any] | None] = mapped_column(JSON)
    decision: Mapped[DecisionAsignacion] = mapped_column(
        _enum(DecisionAsignacion), default=DecisionAsignacion.pendiente
    )
    decidido_por: Mapped[str | None] = mapped_column(String(120))
    hora: Mapped[datetime] = mapped_column(FechaUTC, default=ahora)
    # Extensión al modelo: cuándo la unidad marcó la zona atendida (la ruta deja de estar activa)
    hora_fin: Mapped[datetime | None] = mapped_column(FechaUTC)


class Bloqueo(Base):
    __tablename__ = "bloqueos"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    lat: Mapped[float] = mapped_column(Float)
    lon: Mapped[float] = mapped_column(Float)
    tipo: Mapped[TipoBloqueo] = mapped_column(_enum(TipoBloqueo))
    alcance: Mapped[AlcanceBloqueo] = mapped_column(_enum(AlcanceBloqueo))
    confirmaciones: Mapped[int] = mapped_column(Integer, default=1)
    estado: Mapped[EstadoBloqueo] = mapped_column(
        _enum(EstadoBloqueo), default=EstadoBloqueo.sin_confirmar
    )
    # Extensiones al modelo:
    # identificadores anónimos de quienes lo reportaron (cuentan personas distintas)
    reportantes: Mapped[list[str]] = mapped_column(JSON, default=list)
    confirmado_por_brigada: Mapped[bool] = mapped_column(Boolean, default=False)
    # Calle afectada (tramo u → v del grafo); None si el punto está lejos de toda calle
    nodo_u: Mapped[int | None] = mapped_column(BigInteger)
    nodo_v: Mapped[int | None] = mapped_column(BigInteger)
    clave_tramo: Mapped[int | None] = mapped_column(Integer)
    hora: Mapped[datetime] = mapped_column(FechaUTC, default=ahora)
    actualizado: Mapped[datetime] = mapped_column(FechaUTC, default=ahora)


class UsuarioAdmin(Base):
    __tablename__ = "usuarios_admin"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    nombre: Mapped[str] = mapped_column(String(120))
    organismo: Mapped[str] = mapped_column(String(120))
    nivel: Mapped[NivelAdmin] = mapped_column(_enum(NivelAdmin))


class PropuestaComunitaria(Base):
    """Extensión al modelo: muchos pedidos de ayuda juntos sin alerta activa.

    El operador la aprueba (y entonces se emite la alerta) o la descarta.
    """

    __tablename__ = "propuestas_comunitarias"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    id_h3: Mapped[str] = mapped_column(String(16))
    pedidos: Mapped[int] = mapped_column(Integer)
    hora: Mapped[datetime] = mapped_column(FechaUTC, default=ahora)
    estado: Mapped[EstadoPropuesta] = mapped_column(
        _enum(EstadoPropuesta), default=EstadoPropuesta.pendiente
    )
    id_evento: Mapped[int | None] = mapped_column(ForeignKey("eventos.id"))
