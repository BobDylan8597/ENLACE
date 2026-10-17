"""Esquemas Pydantic de entrada y salida de la API.

Privacidad (sección 13) aplicada en los esquemas:
- `CheckInEntrada` prohíbe campos extra: no hay forma de enviar coordenadas en un check-in.
- `ReporteEntrada` acepta lat/lon, pero el servidor solo las conserva (en Identidad) si es un
  pedido de ayuda; en cualquier otro caso se reducen a la celda h3 y se descartan.
"""

from __future__ import annotations

from datetime import datetime
from typing import Annotated, Any, Literal

from pydantic import AwareDatetime, BaseModel, ConfigDict, Field, model_validator

from app.modelos.enums import (
    AlcanceBloqueo,
    Capacidad,
    CategoriaCelda,
    DecisionAsignacion,
    EstadoBloqueo,
    EstadoCheckIn,
    EstadoEquipo,
    EstadoEvento,
    EstadoPropuesta,
    EstadoReporte,
    FuenteEvento,
    Necesidad,
    NivelDano,
    OrigenReporte,
    TipoBloqueo,
    TipoEvento,
    TipoReporte,
)

Latitud = Annotated[float, Field(ge=-90, le=90)]
Longitud = Annotated[float, Field(ge=-180, le=180)]
Conteo = Annotated[int, Field(ge=0)]


class _Entrada(BaseModel):
    model_config = ConfigDict(extra="forbid")


class _Salida(BaseModel):
    model_config = ConfigDict(from_attributes=True)


# ── Eventos ────────────────────────────────────────────────


class EmisionManual(_Entrada):
    fuente: Literal["manual"] = "manual"
    tipo: TipoEvento
    lat: Latitud
    lon: Longitud
    magnitud: float | None = Field(default=None, ge=0, le=10)
    profundidad_km: float | None = Field(default=None, ge=0)
    hora: AwareDatetime | None = None
    zona_afectada: list[str] | None = None


class EmisionCap(_Entrada):
    fuente: Literal["cap"]
    xml: str


class EmisionSismologica(_Entrada):
    fuente: Literal["sismologica"]
    geojson: dict[str, Any]


class EmisionComunitaria(_Entrada):
    """Aprobación humana de una propuesta comunitaria."""

    fuente: Literal["comunitaria"]
    id_propuesta: int
    tipo: TipoEvento = TipoEvento.otro


Emision = Annotated[
    EmisionManual | EmisionCap | EmisionSismologica | EmisionComunitaria,
    Field(discriminator="fuente"),
]


class EventoSalida(_Salida):
    id: int
    tipo: TipoEvento
    fuente: FuenteEvento
    magnitud: float | None
    profundidad_km: float | None
    lat: float
    lon: float
    zona_afectada: list[str]
    hora: datetime
    estado: EstadoEvento
    fecha_cierre: datetime | None


class AlertaSalida(BaseModel):
    contenido: str
    firma: str
    algoritmo: str


class EmisionSalida(BaseModel):
    evento: EventoSalida
    alerta: AlertaSalida


class PropuestaSalida(_Salida):
    id: int
    id_h3: str
    pedidos: int
    hora: datetime
    estado: EstadoPropuesta


# ── Celdas ─────────────────────────────────────────────────


class TotalesCheckIn(BaseModel):
    sin_respuesta: int = 0
    bien: int = 0
    ayuda: int = 0


class CeldaResumen(_Salida):
    id_h3: str
    nombre_zona: str
    impacto_estimado: float
    certeza: float
    prioridad: float
    categoria: CategoriaCelda
    checkins: TotalesCheckIn = TotalesCheckIn()


class CeldaDetalle(CeldaResumen):
    vulnerabilidad: float
    infraestructura_critica: dict[str, Any]
    impacto_inicial: float
    ultima_atencion: datetime | None
    reportes: int = 0
    # Por qué tiene esa prioridad: cada componente en 0–1
    componentes_prioridad: dict[str, float] = {}


# ── Reportes ───────────────────────────────────────────────


class IdentidadEntrada(_Entrada):
    nombre: str | None = Field(default=None, max_length=120)
    telefono: str | None = Field(default=None, max_length=32)
    condiciones: str | None = Field(default=None, max_length=1000)


class ReporteEntrada(_Entrada):
    id_mensaje: str = Field(min_length=8, max_length=36)
    id_anonimo: str = Field(min_length=8, max_length=36)
    id_evento: int | None = None
    origen: OrigenReporte
    tipo: TipoReporte
    # Ubicación: la celda, o el punto (que se reduce a celda salvo en pedidos de ayuda)
    id_h3: str | None = None
    lat: Latitud | None = None
    lon: Longitud | None = None
    personas: Conteo = 0
    atrapados: Conteo = 0
    heridos: Conteo = 0
    vulnerables: Conteo = 0
    necesidades: list[Necesidad] = []
    nivel_dano: NivelDano | None = None
    saltos_nearby: Conteo = 0
    hora: AwareDatetime | None = None
    identidad: IdentidadEntrada | None = None

    @model_validator(mode="after")
    def _coherente(self) -> ReporteEntrada:
        tiene_punto = self.lat is not None and self.lon is not None
        if (self.lat is None) != (self.lon is None):
            raise ValueError("lat y lon van juntas")
        if self.id_h3 is None and not tiene_punto:
            raise ValueError("Falta la ubicación: id_h3 o lat/lon")
        if self.nivel_dano is not None and not (
            self.origen is OrigenReporte.brigada and self.tipo is TipoReporte.evaluacion
        ):
            raise ValueError("nivel_dano solo lo informa una brigada en una evaluación")
        if self.identidad is not None and self.tipo is not TipoReporte.ayuda:
            raise ValueError("Los datos personales solo se envían al pedir ayuda")
        return self


class Rechazo(BaseModel):
    id_mensaje: str | None
    error: str


class ResultadoReportes(BaseModel):
    aceptados: list[str]
    # Ya recibidos antes (por otra vía o por otra mula de datos): no es un error
    repetidos: list[str]
    rechazados: list[Rechazo] = []


class Ubicacion(BaseModel):
    lat: float
    lon: float


class ReporteSalida(_Salida):
    id_mensaje: str
    id_h3: str
    nombre_zona: str = ""
    origen: OrigenReporte
    tipo: TipoReporte
    personas: int
    atrapados: int
    heridos: int
    vulnerables: int
    necesidades: list[str]
    nivel_dano: NivelDano | None
    confiabilidad: float
    saltos_nearby: int
    hora: datetime
    estado: EstadoReporte
    prioridad: float = 0.0
    componentes_prioridad: dict[str, float] = {}
    duplicados: int = 0
    # Un reenvío agregó información nueva (más personas o necesidades nuevas)
    actualizado_en: datetime | None = None
    # Punto exacto: solo existe para pedidos de ayuda
    ubicacion: Ubicacion | None = None


class IdentidadSalida(_Salida):
    nombre: str | None
    telefono: str | None
    condiciones: str | None


class ReporteDetalle(ReporteSalida):
    identidad: IdentidadSalida | None = None
    asignaciones: list[AsignacionSalida] = []


# ── Check-ins y SMS ────────────────────────────────────────


class CheckInEntrada(_Entrada):
    id_anonimo: str = Field(min_length=8, max_length=36)
    id_evento: int
    id_h3: str
    estado: EstadoCheckIn = EstadoCheckIn.sin_respuesta
    hora: AwareDatetime | None = None


class SmsEntrante(_Entrada):
    remitente: str = Field(min_length=3, max_length=32)
    texto: str = Field(max_length=480)
    hora: AwareDatetime | None = None


# ── Equipos y asignaciones ─────────────────────────────────


class EquipoEntrada(_Entrada):
    nombre: str = Field(min_length=1, max_length=120)
    organismo: str = Field(min_length=1, max_length=120)
    capacidades: list[Capacidad] = Field(min_length=1)
    lat: Latitud
    lon: Longitud
    estado: EstadoEquipo = EstadoEquipo.disponible


class EquipoSalida(_Salida):
    id: int
    nombre: str
    organismo: str
    capacidades: list[Capacidad]
    lat: float
    lon: float
    estado: EstadoEquipo


class AsignacionSalida(_Salida):
    id: int
    id_equipo: int
    nombre_equipo: str = ""
    id_reporte: str | None
    id_h3: str
    motivo: str
    tiempo_estimado_min: float
    decision: DecisionAsignacion
    decidido_por: str | None
    hora: datetime
    hora_fin: datetime | None = None
    # GeoJSON de la ruta del equipo, con instrucciones paso a paso (cuando ya fue aceptada)
    ruta: dict[str, Any] | None = None


class DecisionEntrada(_Entrada):
    decision: Literal["aceptada", "modificada", "rechazada"]
    decidido_por: str = Field(min_length=1, max_length=120)
    # Obligatorio al modificar: el equipo que eligió la persona en lugar del sugerido
    id_equipo: int | None = None

    @model_validator(mode="after")
    def _equipo_si_modifica(self) -> DecisionEntrada:
        if self.decision == "modificada" and self.id_equipo is None:
            raise ValueError("Al modificar hay que indicar id_equipo")
        return self


ReporteDetalle.model_rebuild()


# ── Bloqueos y rutas ───────────────────────────────────────


class BloqueoEntrada(_Entrada):
    """Reportar crea un bloqueo o confirma uno cercano; confirmar y descartar usan id_bloqueo."""

    accion: Literal["reportar", "confirmar", "descartar"] = "reportar"
    id_bloqueo: int | None = None
    lat: Latitud | None = None
    lon: Longitud | None = None
    tipo: TipoBloqueo = TipoBloqueo.otro
    alcance: AlcanceBloqueo = AlcanceBloqueo.total
    # Quién reporta: id_anonimo del celular, o "equipo-<n>" si es una brigada
    reportante: str = Field(min_length=3, max_length=36)
    origen: Literal["app", "brigada", "operador"] = "app"

    @model_validator(mode="after")
    def _coherente(self) -> BloqueoEntrada:
        if self.accion == "reportar" and (self.lat is None or self.lon is None):
            raise ValueError("Reportar un bloqueo requiere lat y lon")
        if self.accion != "reportar" and self.id_bloqueo is None:
            raise ValueError("Confirmar o descartar requiere id_bloqueo")
        return self


class BloqueoSalida(_Salida):
    id: int
    lat: float
    lon: float
    tipo: TipoBloqueo
    alcance: AlcanceBloqueo
    confirmaciones: int
    estado: EstadoBloqueo
    confirmado_por_brigada: bool
    hora: datetime
    actualizado: datetime
    # False si el punto está lejos de toda calle: no cambia ninguna ruta
    afecta_rutas: bool = False


class CambioBloqueoSalida(BaseModel):
    bloqueo: BloqueoSalida
    rutas_recalculadas: int


class UbicacionEquipoEntrada(_Entrada):
    lat: Latitud
    lon: Longitud
