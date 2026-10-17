"""Carga de la configuración de ENLACE desde archivos TOML.

La ruta del archivo puede cambiarse con la variable de entorno ENLACE_CONFIG.
Todas las secciones son obligatorias: si falta un valor, el servidor no arranca.
"""

from __future__ import annotations

import math
import os
import tomllib
from enum import StrEnum
from functools import lru_cache
from pathlib import Path
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, model_validator

from app.modelos.enums import Capacidad, Necesidad, NivelDano, OrigenReporte

RAIZ_SERVIDOR = Path(__file__).resolve().parent.parent
RUTA_CONFIG_POR_DEFECTO = RAIZ_SERVIDOR / "config" / "enlace.toml"


class _Seccion(BaseModel):
    model_config = ConfigDict(frozen=True, extra="forbid")


class ConfigServidor(_Seccion):
    host: str
    puerto: int
    base_datos: str
    clave_admin: str


class ConfigCiudad(_Seccion):
    activa: str
    directorio: str


class ConfigH3(_Seccion):
    resolucion: int


class CurvaFragilidad(_Seccion):
    mediana: float
    dispersion: float = Field(gt=0)


class ConfigSismo(_Seccion):
    a: float
    b: float
    c: float
    profundidad_por_defecto_km: float = Field(ge=0)
    distancia_minima_km: float = Field(gt=0)
    fragilidad: dict[str, CurvaFragilidad]


class ConfigAmenazas(_Seccion):
    sismo: ConfigSismo


class ConfigFusion(_Seccion):
    peso_coincidentes: float = Field(ge=0)
    ventana_coincidencia_min: float = Field(gt=0)
    ventana_duplicado_min: float = Field(gt=0)
    factor_ayuda: float = Field(gt=0)
    factor_bien: float = Field(ge=0)
    fraccion_minima_ayuda: float = Field(ge=0, le=1)
    peso_por_checkin: float = Field(ge=0)
    peso_maximo_checkins: float = Field(ge=0)
    peso_sin_respuesta: float = Field(ge=0, le=1)
    minimo_checkins: int = Field(ge=1)
    pesos_origen: dict[OrigenReporte, float]
    fraccion_por_nivel_dano: dict[NivelDano, float]

    @model_validator(mode="after")
    def _completos(self) -> ConfigFusion:
        _exigir_claves("fusion.pesos_origen", self.pesos_origen, OrigenReporte)
        _exigir_claves("fusion.fraccion_por_nivel_dano", self.fraccion_por_nivel_dano, NivelDano)
        return self


class ConfigClasificacion(_Seccion):
    umbral_impacto_personas: float = Field(ge=0)
    umbral_certeza: float = Field(ge=0, le=1)


class ConfigPrioridadCelda(_Seccion):
    peso_impacto: float = Field(ge=0)
    peso_urgencia: float = Field(ge=0)
    peso_vulnerabilidad: float = Field(ge=0)
    peso_espera: float = Field(ge=0)
    escala_impacto_personas: float = Field(gt=0)
    horas_urgencia_max: float = Field(gt=0)
    horas_espera_max: float = Field(gt=0)

    @model_validator(mode="after")
    def _pesos_suman_uno(self) -> ConfigPrioridadCelda:
        _exigir_suma_uno(
            "prioridad.celda",
            self.peso_impacto,
            self.peso_urgencia,
            self.peso_vulnerabilidad,
            self.peso_espera,
        )
        return self


class ConfigPrioridadReporte(_Seccion):
    peso_gravedad: float = Field(ge=0)
    peso_vulnerabilidad: float = Field(ge=0)
    peso_espera: float = Field(ge=0)
    peso_celda: float = Field(ge=0)
    peso_atrapado: float = Field(ge=0)
    peso_herido: float = Field(ge=0)
    peso_persona: float = Field(ge=0)
    escala_gravedad: float = Field(gt=0)
    escala_vulnerables: float = Field(gt=0)
    horas_espera_max: float = Field(gt=0)

    @model_validator(mode="after")
    def _pesos_suman_uno(self) -> ConfigPrioridadReporte:
        _exigir_suma_uno(
            "prioridad.reporte",
            self.peso_gravedad,
            self.peso_vulnerabilidad,
            self.peso_espera,
            self.peso_celda,
        )
        return self


class ConfigPrioridad(_Seccion):
    celda: ConfigPrioridadCelda
    reporte: ConfigPrioridadReporte


class ConfigAsignacion(_Seccion):
    velocidad_promedio_kmh: float = Field(gt=0)
    factor_desvio: float = Field(ge=1)
    maximo_recomendaciones: int = Field(ge=1)
    capacidad_por_necesidad: dict[Necesidad, Capacidad]

    @model_validator(mode="after")
    def _completa(self) -> ConfigAsignacion:
        _exigir_claves(
            "asignacion.capacidad_por_necesidad", self.capacidad_por_necesidad, Necesidad
        )
        return self


class ConfigCeldas(_Seccion):
    vulnerabilidad_por_defecto: float = Field(ge=0, le=1)
    nombre_fuera_de_area: str


class ConfigActivacion(_Seccion):
    umbral_zona_afectada: float = Field(ge=0, le=1)
    pedidos_minimos_comunitaria: int = Field(ge=1)
    ventana_comunitaria_min: float = Field(gt=0)
    directorio_claves: str


class ConfigNearby(_Seccion):
    saltos_max: int = Field(ge=0)


class ConfigPrivacidad(_Seccion):
    dias_borrado: float = Field(gt=0)
    intervalo_revision_min: float = Field(gt=0)


class ConfigRutas(_Seccion):
    archivo_calles: str
    factor_velocidad_emergencia: float = Field(gt=0, le=1)
    factor_sin_confirmar: float = Field(ge=1)
    factor_probable: float = Field(ge=1)
    reportes_para_probable: int = Field(ge=1)
    reportes_para_confirmado: int = Field(ge=1)
    radio_fusion_bloqueo_m: float = Field(gt=0)
    distancia_maxima_calle_m: float = Field(gt=0)
    distancia_maxima_a_la_red_m: float = Field(gt=0)

    @model_validator(mode="after")
    def _escalonado(self) -> ConfigRutas:
        if self.reportes_para_confirmado <= self.reportes_para_probable:
            raise ValueError(
                "rutas: reportes_para_confirmado debe superar a reportes_para_probable"
            )
        return self


class ConfigPreparacion(_Seccion):
    margen_km: float = Field(ge=0)
    tipo_red: str
    servidores_overpass: list[str] = Field(min_length=1)
    archivo_mapa: str
    url_mapa_base: str
    dias_busqueda_mapa: int = Field(ge=1)
    zoom_maximo_mapa: int = Field(ge=0, le=15)


class ConfigWeb(_Seccion):
    directorio: str


class Configuracion(_Seccion):
    servidor: ConfigServidor
    ciudad: ConfigCiudad
    h3: ConfigH3
    amenazas: ConfigAmenazas
    fusion: ConfigFusion
    clasificacion: ConfigClasificacion
    prioridad: ConfigPrioridad
    asignacion: ConfigAsignacion
    celdas: ConfigCeldas
    activacion: ConfigActivacion
    nearby: ConfigNearby
    privacidad: ConfigPrivacidad
    rutas: ConfigRutas
    preparacion: ConfigPreparacion
    web: ConfigWeb


def _exigir_claves(nombre: str, valores: dict[Any, Any], enum: type[StrEnum]) -> None:
    faltan = set(enum) - set(valores)
    if faltan:
        raise ValueError(f"{nombre}: faltan {sorted(faltan)}")


def _exigir_suma_uno(nombre: str, *pesos: float) -> None:
    if not math.isclose(sum(pesos), 1.0, abs_tol=1e-6):
        raise ValueError(f"{nombre}: los pesos deben sumar 1 (suman {sum(pesos)})")


def resolver_ruta(relativa: str) -> Path:
    """Convierte una ruta del archivo de configuración en absoluta (base: servidor/)."""
    ruta = Path(relativa)
    return ruta if ruta.is_absolute() else RAIZ_SERVIDOR / ruta


def cargar_config(ruta: Path) -> Configuracion:
    with ruta.open("rb") as archivo:
        return Configuracion.model_validate(tomllib.load(archivo))


@lru_cache
def obtener_config() -> Configuracion:
    return cargar_config(Path(os.environ.get("ENLACE_CONFIG", RUTA_CONFIG_POR_DEFECTO)))
