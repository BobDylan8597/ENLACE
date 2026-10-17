"""Clasificación de celdas y prioridad (0–100) de celdas y reportes."""

from __future__ import annotations

import math
from dataclasses import dataclass
from datetime import datetime

from app.config import ConfigClasificacion, ConfigPrioridadCelda, ConfigPrioridadReporte
from app.modelos.enums import CategoriaCelda
from app.motor.tipos import DatosReporte


@dataclass(frozen=True)
class Prioridad:
    valor: float
    # Cada componente en 0–1, para explicar en la interfaz por qué tiene esa prioridad
    componentes: dict[str, float]


def _saturar(valor: float, escala: float) -> float:
    """Lleva un valor no negativo a 0–1, creciendo rápido al principio y luego más lento."""
    return 1.0 - math.exp(-max(valor, 0.0) / escala)


def _proporcion(valor: float, maximo: float) -> float:
    return min(max(valor, 0.0) / maximo, 1.0)


def _horas(desde: datetime, hasta: datetime) -> float:
    return (hasta - desde).total_seconds() / 3600


def clasificar(impacto: float, certeza: float, config: ConfigClasificacion) -> CategoriaCelda:
    impacto_alto = impacto >= config.umbral_impacto_personas
    certeza_alta = certeza >= config.umbral_certeza
    if impacto_alto:
        return CategoriaCelda.actuar if certeza_alta else CategoriaCelda.explorar
    return CategoriaCelda.estable if certeza_alta else CategoriaCelda.explorar_despues


def vulnerabilidad_efectiva(
    vulnerabilidad: float, poblacion_critica: int, config: ConfigPrioridadCelda
) -> float:
    """La vulnerabilidad de la celda (datos previos) sube con las personas vulnerables que los
    check-ins muestran sin responder o pidiendo ayuda: lo que falta hasta 1 se reduce."""
    base = min(max(vulnerabilidad, 0.0), 1.0)
    return base + (1 - base) * _saturar(poblacion_critica, config.escala_poblacion_critica)


def prioridad_celda(
    impacto: float,
    vulnerabilidad: float,
    hora_evento: datetime,
    ultima_atencion: datetime | None,
    ahora: datetime,
    config: ConfigPrioridadCelda,
) -> Prioridad:
    componentes = {
        "impacto": _saturar(impacto, config.escala_impacto_personas),
        "urgencia": _proporcion(_horas(hora_evento, ahora), config.horas_urgencia_max),
        "vulnerabilidad": min(max(vulnerabilidad, 0.0), 1.0),
        "espera": _proporcion(
            _horas(ultima_atencion or hora_evento, ahora), config.horas_espera_max
        ),
    }
    valor = 100 * (
        config.peso_impacto * componentes["impacto"]
        + config.peso_urgencia * componentes["urgencia"]
        + config.peso_vulnerabilidad * componentes["vulnerabilidad"]
        + config.peso_espera * componentes["espera"]
    )
    return Prioridad(round(valor, 1), componentes)


def prioridad_reporte(
    reporte: DatosReporte,
    prioridad_de_celda: float,
    vulnerabilidad_celda: float,
    ahora: datetime,
    config: ConfigPrioridadReporte,
) -> Prioridad:
    gravedad = (
        config.peso_atrapado * reporte.atrapados
        + config.peso_herido * reporte.heridos
        + config.peso_persona * reporte.personas
    )
    componentes = {
        "gravedad": _saturar(gravedad, config.escala_gravedad),
        "vulnerabilidad": max(
            _saturar(reporte.vulnerables, config.escala_vulnerables),
            min(max(vulnerabilidad_celda, 0.0), 1.0),
        ),
        "espera": _proporcion(_horas(reporte.hora, ahora), config.horas_espera_max),
        "celda": min(max(prioridad_de_celda / 100, 0.0), 1.0),
    }
    valor = 100 * (
        config.peso_gravedad * componentes["gravedad"]
        + config.peso_vulnerabilidad * componentes["vulnerabilidad"]
        + config.peso_espera * componentes["espera"]
        + config.peso_celda * componentes["celda"]
    )
    return Prioridad(round(valor, 1), componentes)
