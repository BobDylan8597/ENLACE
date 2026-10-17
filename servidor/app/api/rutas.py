"""Rutas de bloqueos (U13–U14, confirmación de brigadas) y cálculo de rutas."""

from __future__ import annotations

from typing import Annotated, Any

from fastapi import APIRouter, HTTPException, Query, status

from app.api.dependencias import AhoraDep, ConfigDep, PrivilegiadoDep, RedDep, SesionDep
from app.api.vistas import asignacion_salida
from app.eventos.bus import Tema, bus
from app.modelos.esquemas import BloqueoEntrada, BloqueoSalida, CambioBloqueoSalida
from app.modelos.tablas import Bloqueo
from app.rutas.bloqueos import Modo
from app.rutas.calculo import calcular_ruta
from app.rutas.grafo import GrafoCalles
from app.rutas.servicio import (
    AccionBloqueo,
    BloqueoNoEncontrado,
    bloqueos_activos,
    penalizaciones,
    recalcular_rutas_afectadas,
    registrar_bloqueo,
)

router = APIRouter(tags=["rutas"])


def _salida(bloqueo: Bloqueo) -> BloqueoSalida:
    return BloqueoSalida.model_validate(bloqueo).model_copy(
        update={"afecta_rutas": bloqueo.nodo_u is not None}
    )


def _coordenadas(texto: str) -> tuple[float, float]:
    try:
        lat, lon = (float(v) for v in texto.split(","))
    except ValueError as error:
        raise HTTPException(
            status.HTTP_422_UNPROCESSABLE_CONTENT, f"Coordenadas inválidas: {texto}"
        ) from error
    if not (-90 <= lat <= 90 and -180 <= lon <= 180):
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_CONTENT, "Coordenadas fuera de rango")
    return lat, lon


@router.post("/bloqueos", response_model=CambioBloqueoSalida)
def bloqueo(
    entrada: BloqueoEntrada,
    sesion: SesionDep,
    ahora: AhoraDep,
    config: ConfigDep,
    red: RedDep,
    privilegiado: PrivilegiadoDep,
) -> CambioBloqueoSalida:
    """Cualquier persona puede reportar o confirmar. Una confirmación de brigada (o de un
    operador) confirma el bloqueo de inmediato; descartarlo exige la clave."""
    requiere_clave = entrada.origen != "app" or entrada.accion == "descartar"
    if requiere_clave and not privilegiado:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Hace falta la clave de brigada")
    try:
        cambio = registrar_bloqueo(
            sesion,
            red,
            AccionBloqueo(entrada.accion),
            entrada.reportante,
            es_brigada=entrada.origen != "app",
            ahora=ahora,
            config=config,
            id_bloqueo=entrada.id_bloqueo,
            lat=entrada.lat,
            lon=entrada.lon,
            tipo=entrada.tipo,
            alcance=entrada.alcance,
        )
    except BloqueoNoEncontrado as error:
        raise HTTPException(status.HTTP_404_NOT_FOUND, str(error)) from error
    recalculadas = (
        recalcular_rutas_afectadas(sesion, red, cambio.bloqueo, config)
        if cambio.cambio_estado
        else []
    )
    sesion.commit()

    salida = _salida(cambio.bloqueo)
    bus.publicar(Tema.bloqueo_cambiado, salida.model_dump(mode="json"))
    for asignacion in recalculadas:
        bus.publicar(
            Tema.ruta_recalculada,
            {
                "asignacion": asignacion_salida(sesion, asignacion).model_dump(mode="json"),
                "motivo": f"Bloqueo {salida.estado.value.replace('_', ' ')} en tu ruta",
                "sin_ruta": asignacion.ruta is None,
            },
        )
    return CambioBloqueoSalida(bloqueo=salida, rutas_recalculadas=len(recalculadas))


@router.get("/bloqueos", response_model=list[BloqueoSalida])
def listar(sesion: SesionDep) -> list[BloqueoSalida]:
    return [_salida(b) for b in bloqueos_activos(sesion)]


def _red_o_503(red: GrafoCalles | None) -> GrafoCalles:
    if red is None:
        raise HTTPException(
            status.HTTP_503_SERVICE_UNAVAILABLE,
            "No hay mapa de calles: ejecutar preparacion/descargar_osm.py",
        )
    return red


@router.get("/rutas")
def ruta(
    sesion: SesionDep,
    config: ConfigDep,
    red: RedDep,
    origen: Annotated[str, Query(description="lat,lon")],
    destino: Annotated[str, Query(description="lat,lon")],
    modo: Modo = Modo.vehiculo,
) -> dict[str, Any]:
    """Ruta más rápida evitando bloqueos: GeoJSON con instrucciones paso a paso."""
    calculada = calcular_ruta(
        _red_o_503(red),
        penalizaciones(sesion, red, modo, config),
        _coordenadas(origen),
        _coordenadas(destino),
        config.rutas,
    )
    if calculada is None:
        raise HTTPException(
            status.HTTP_404_NOT_FOUND, "No hay forma de llegar con los bloqueos actuales"
        )
    return calculada.como_geojson(modo=modo.value)
