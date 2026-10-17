"""Fusión de evidencia: cada dato que llega corrige la estimación de su celda.

Cada evidencia aporta una *señal* (fracción de ocupantes gravemente afectados) y un *peso*:

    certeza          = 1 − exp(−Σ pesos)
    fraccion_final   = (1 − certeza) · fraccion_modelo + certeza · promedio_ponderado(señales)
    impacto_estimado = max(ocupantes · fraccion_final, atrapados + heridos confirmados)
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from datetime import timedelta

from app.config import ConfigFusion
from app.modelos.enums import EstadoCheckIn, OrigenReporte, TipoReporte
from app.motor.tipos import DatosCheckIn, DatosReporte, EstadoCelda


@dataclass(frozen=True)
class Evidencia:
    reporte: DatosReporte
    peso: float


@dataclass(frozen=True)
class EvidenciaPreparada:
    principales: list[Evidencia]
    # id_mensaje del duplicado → id_mensaje del reporte con el que se fusionó
    duplicados: dict[str, str]


def preparar_evidencia(reportes: list[DatosReporte], config: ConfigFusion) -> EvidenciaPreparada:
    """Deduplica y pondera los reportes de una celda."""
    ventana_duplicado = timedelta(minutes=config.ventana_duplicado_min)
    ventana_coincidencia = timedelta(minutes=config.ventana_coincidencia_min)

    principales: list[DatosReporte] = []
    duplicados: dict[str, str] = {}
    for reporte in sorted(reportes, key=lambda r: (r.hora, r.id_mensaje)):
        padre = next(
            (
                p
                for p in principales
                if p.id_anonimo == reporte.id_anonimo
                and p.tipo == reporte.tipo
                and reporte.hora - p.hora <= ventana_duplicado
            ),
            None,
        )
        if padre is None:
            principales.append(reporte)
        else:
            duplicados[reporte.id_mensaje] = padre.id_mensaje

    def peso(reporte: DatosReporte) -> float:
        base = config.pesos_origen[reporte.origen]
        if reporte.origen is OrigenReporte.brigada:
            return base
        coincide = any(
            otro.id_anonimo != reporte.id_anonimo
            and otro.tipo == reporte.tipo
            and abs(otro.hora - reporte.hora) <= ventana_coincidencia
            for otro in principales
        )
        return max(base, config.peso_coincidentes) if coincide else base

    return EvidenciaPreparada([Evidencia(r, peso(r)) for r in principales], duplicados)


def senal_reporte(
    reporte: DatosReporte, fraccion_modelo: float, config: ConfigFusion
) -> float | None:
    """Fracción de afectados que sugiere un reporte, o None si no informa sobre el daño."""
    if reporte.tipo is TipoReporte.evaluacion:
        if reporte.nivel_dano is None:
            return None
        return config.fraccion_por_nivel_dano[reporte.nivel_dano]
    if reporte.tipo is TipoReporte.ayuda:
        return min(1.0, max(fraccion_modelo * config.factor_ayuda, config.fraccion_minima_ayuda))
    return fraccion_modelo * config.factor_bien


def senal_checkins(
    checkins: list[DatosCheckIn], config: ConfigFusion
) -> tuple[float, float] | None:
    """(señal, peso) del conjunto de check-ins de la celda, o None si son muy pocos."""
    if len(checkins) < config.minimo_checkins:
        return None
    ayuda = sum(1 for c in checkins if c.estado is EstadoCheckIn.ayuda)
    sin_respuesta = sum(1 for c in checkins if c.estado is EstadoCheckIn.sin_respuesta)
    senal = (ayuda + config.peso_sin_respuesta * sin_respuesta) / len(checkins)
    peso = min(len(checkins) * config.peso_por_checkin, config.peso_maximo_checkins)
    return senal, peso


def estimar_celda(
    id_h3: str,
    ocupantes: float,
    fraccion_modelo: float,
    evidencia: list[Evidencia],
    checkins: list[DatosCheckIn],
    config: ConfigFusion,
) -> EstadoCelda:
    senales: list[tuple[float, float]] = []
    for item in evidencia:
        senal = senal_reporte(item.reporte, fraccion_modelo, config)
        if senal is not None:
            senales.append((senal, item.peso))
    aporte_checkins = senal_checkins(checkins, config)
    if aporte_checkins is not None:
        senales.append(aporte_checkins)

    suma_pesos = sum(peso for _, peso in senales)
    certeza = 1.0 - math.exp(-suma_pesos)
    if suma_pesos > 0:
        fraccion_evidencia = sum(s * p for s, p in senales) / suma_pesos
        fraccion_final = (1.0 - certeza) * fraccion_modelo + certeza * fraccion_evidencia
    else:
        fraccion_final = fraccion_modelo

    conteo_confirmado = sum(
        item.reporte.atrapados + item.reporte.heridos
        for item in evidencia
        if item.reporte.tipo is not TipoReporte.bien
    )
    return EstadoCelda(
        id_h3=id_h3,
        ocupantes=ocupantes,
        fraccion_modelo=fraccion_modelo,
        fraccion_final=fraccion_final,
        impacto_inicial=ocupantes * fraccion_modelo,
        impacto_estimado=max(ocupantes * fraccion_final, float(conteo_confirmado)),
        certeza=certeza,
        suma_pesos=suma_pesos,
        conteo_confirmado=conteo_confirmado,
    )
