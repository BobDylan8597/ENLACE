"""Demo del paso 2 (rutas y bloqueos), contra el servidor en marcha y sin internet.

Uso (desde servidor/, con la semilla recién cargada, el mapa preparado y el servidor corriendo):
    python datos/demo_paso2.py [--url http://127.0.0.1:8000]

1. Alerta y un pedido de ayuda con 2 personas atrapadas.
2. La coordinación acepta el equipo sugerido: la unidad recibe su ruta paso a paso.
3. Un vecino reporta un derrumbe sobre esa ruta, otro lo confirma y una brigada lo confirma:
   en cada cambio de estado se recalcula solo esa ruta (pantalla B6).
"""

from __future__ import annotations

import argparse
import tomllib
import uuid
from typing import Any

import h3
from demo_paso1 import CONFIG, Cliente, _titulo

from app.ciudad import directorio_ciudad


def _id() -> str:
    return str(uuid.uuid4())


def _resumen(ruta: dict[str, Any] | None, instrucciones: int = 4) -> None:
    if ruta is None:
        print("  sin ruta posible con los bloqueos actuales")
        return
    propiedades = ruta["properties"]
    print(f"  {propiedades['tiempo_min']} min · {propiedades['distancia_m'] / 1000:.1f} km")
    for paso in propiedades["instrucciones"][:instrucciones]:
        print(f"   · {paso['texto']}")
    if len(propiedades["instrucciones"]) > instrucciones:
        print(f"   · … ({len(propiedades['instrucciones']) - instrucciones} pasos más)")


def main() -> None:
    argumentos = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    argumentos.add_argument("--url", default=f"http://127.0.0.1:{CONFIG.servidor.puerto}")
    cliente = Cliente(argumentos.parse_args().url)
    with (directorio_ciudad(CONFIG) / "semilla.toml").open("rb") as archivo:
        semilla = tomllib.load(archivo)

    salud = cliente.get("/salud")
    print(f"Servidor ENLACE · {salud['ciudad']} ({salud['pais']})")
    if cliente.get("/eventos/activo") is not None:
        raise SystemExit("Ya hay un evento activo: recargar la semilla con --reiniciar")

    _titulo("1. Alerta y pedido de ayuda")
    cliente.post("/eventos", {"fuente": "manual", **semilla["evento_prueba"]}, admin=True)
    celdas = cliente.get("/celdas")
    destino = max(celdas, key=lambda c: c["impacto_estimado"])
    lat, lon = h3.cell_to_latlng(destino["id_h3"])
    pedido = {
        "id_mensaje": _id(),
        "id_anonimo": _id(),
        "origen": "app",
        "tipo": "ayuda",
        "lat": round(lat, 5),
        "lon": round(lon, 5),
        "atrapados": 2,
        "necesidades": ["rescate"],
    }
    cliente.post("/reportes", pedido)
    print(f"2 personas atrapadas en {destino['nombre_zona']}")

    _titulo("2. Asignación aceptada: ruta de la unidad")
    recomendacion = next(
        r
        for r in cliente.get("/recomendaciones", admin=True)
        if r["id_reporte"] == pedido["id_mensaje"]
    )
    asignacion = cliente.post(
        f"/asignaciones/{recomendacion['id']}/decision",
        {"decision": "aceptada", "decidido_por": "Coordinación demo"},
        admin=True,
    )
    print(f"{asignacion['nombre_equipo']} va en camino:")
    _resumen(asignacion["ruta"])
    if asignacion["ruta"] is None:
        raise SystemExit("Sin ruta: ¿se ejecutó preparacion/descargar_osm.py?")

    _titulo("3. Derrumbe sobre la ruta")
    coordenadas = asignacion["ruta"]["geometry"]["coordinates"]
    blon, blat = coordenadas[len(coordenadas) // 2]
    pasos = [
        ("Un vecino reporta un derrumbe", {"reportante": _id()}),
        ("Otro vecino lo confirma", {"reportante": _id()}),
        (
            "Una brigada lo confirma",
            {"reportante": f"equipo-{asignacion['id_equipo']}", "origen": "brigada"},
        ),
    ]
    for texto, campos in pasos:
        cambio = cliente.post(
            "/bloqueos",
            {"lat": blat, "lon": blon, "tipo": "derrumbe", **campos},
            admin=campos.get("origen") == "brigada",
        )
        bloqueo = cambio["bloqueo"]
        print(
            f"{texto}: {bloqueo['estado']} ({bloqueo['confirmaciones']} "
            f"{'persona' if bloqueo['confirmaciones'] == 1 else 'personas'}) · "
            f"rutas recalculadas: {cambio['rutas_recalculadas']}"
        )
    detalle = cliente.get(f"/reportes/{pedido['id_mensaje']}", admin=True)
    print("Ruta nueva de la unidad (evita la calle cortada):")
    _resumen(detalle["asignaciones"][-1]["ruta"])


if __name__ == "__main__":
    main()
