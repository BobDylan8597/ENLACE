"""Valores cerrados del modelo de datos (sección 7 de CLAUDE.md)."""

from __future__ import annotations

from enum import StrEnum


class TipoEvento(StrEnum):
    sismo = "sismo"
    inundacion = "inundacion"
    huracan = "huracan"
    otro = "otro"


class FuenteEvento(StrEnum):
    cap = "cap"
    sismologica = "sismologica"
    manual = "manual"
    comunitaria = "comunitaria"


class EstadoEvento(StrEnum):
    activo = "activo"
    cerrado = "cerrado"


class CategoriaCelda(StrEnum):
    actuar = "actuar"
    explorar = "explorar"
    estable = "estable"
    explorar_despues = "explorar_despues"


class OrigenReporte(StrEnum):
    app = "app"
    sms = "sms"
    nearby = "nearby"
    radio = "radio"
    brigada = "brigada"


class TipoReporte(StrEnum):
    bien = "bien"
    ayuda = "ayuda"
    evaluacion = "evaluacion"


class Necesidad(StrEnum):
    rescate = "rescate"
    medico = "medico"
    agua = "agua"
    refugio = "refugio"


class NivelDano(StrEnum):
    """Evaluación de daño hecha por una brigada en terreno."""

    ninguno = "ninguno"
    leve = "leve"
    moderado = "moderado"
    grave = "grave"
    colapso = "colapso"


class EstadoReporte(StrEnum):
    sin_asignar = "sin_asignar"
    asignado = "asignado"
    atendido = "atendido"
    descartado = "descartado"


class EstadoCheckIn(StrEnum):
    sin_respuesta = "sin_respuesta"
    bien = "bien"
    ayuda = "ayuda"


class Capacidad(StrEnum):
    rescate = "rescate"
    medico = "medico"
    agua = "agua"
    reconocimiento = "reconocimiento"
    maquinaria = "maquinaria"


class EstadoEquipo(StrEnum):
    disponible = "disponible"
    en_ruta = "en_ruta"
    ocupado = "ocupado"


class DecisionAsignacion(StrEnum):
    pendiente = "pendiente"
    aceptada = "aceptada"
    modificada = "modificada"
    rechazada = "rechazada"


class TipoBloqueo(StrEnum):
    derrumbe = "derrumbe"
    agua = "agua"
    escombros = "escombros"
    cables = "cables"
    via_rota = "via_rota"
    otro = "otro"


class AlcanceBloqueo(StrEnum):
    total = "total"
    solo_a_pie = "solo_a_pie"


class EstadoBloqueo(StrEnum):
    sin_confirmar = "sin_confirmar"
    probable = "probable"
    confirmado = "confirmado"
    descartado = "descartado"


class NivelAdmin(StrEnum):
    coordinador = "coordinador"
    operador = "operador"
    brigada = "brigada"
    admin_organismo = "admin_organismo"
