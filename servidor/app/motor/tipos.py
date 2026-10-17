"""Datos de entrada y salida del motor, independientes de la base de datos."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime

from app.modelos.enums import (
    CategoriaCelda,
    EstadoCheckIn,
    NivelDano,
    OrigenReporte,
    TipoEvento,
    TipoReporte,
)


@dataclass(frozen=True)
class DatosEvento:
    tipo: TipoEvento
    lat: float
    lon: float
    hora: datetime
    magnitud: float | None = None
    profundidad_km: float | None = None


@dataclass(frozen=True)
class DatosCelda:
    id_h3: str
    poblacion_por_hora: list[float]
    vulnerabilidad: float
    tipos_construccion: dict[str, float]
    nombre_zona: str = ""


@dataclass(frozen=True)
class DatosReporte:
    id_mensaje: str
    id_anonimo: str
    id_h3: str
    origen: OrigenReporte
    tipo: TipoReporte
    hora: datetime
    personas: int = 0
    atrapados: int = 0
    heridos: int = 0
    vulnerables: int = 0
    necesidades: list[str] = field(default_factory=list)
    nivel_dano: NivelDano | None = None


@dataclass(frozen=True)
class DatosCheckIn:
    id_anonimo: str
    id_h3: str
    estado: EstadoCheckIn


@dataclass(frozen=True)
class EstadoCelda:
    """Resultado de estimar una celda: lo que el motor cree y con cuánta certeza."""

    id_h3: str
    ocupantes: float
    fraccion_modelo: float
    fraccion_final: float
    impacto_inicial: float
    impacto_estimado: float
    certeza: float
    suma_pesos: float
    conteo_confirmado: int
    categoria: CategoriaCelda = CategoriaCelda.explorar_despues
    # Pedidos originales que un reenvío actualizó con información nueva
    actualizados: tuple[str, ...] = ()
