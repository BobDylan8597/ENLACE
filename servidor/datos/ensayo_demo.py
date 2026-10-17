"""Ensayo y puesta a punto de la demostración (CLAUDE.md, sección 17). Ver GUION_DEMO.md.

Uso (desde servidor/):
    python datos/ensayo_demo.py preparar     # con el servidor DETENIDO: base limpia con la ciudad real
    python datos/ensayo_demo.py verificar    # con el servidor corriendo: lista de comprobación
    python datos/ensayo_demo.py fondo        # durante la demo: simula al resto de la ciudad

"fondo" espera a que la coordinación emita la alerta en vivo (A6) y entonces, poco a poco
(para que el tablero se vea llenarse en tiempo real), envía check-ins anónimos y pedidos de ayuda
de personas ficticias, como llegarían de cientos de celulares. Los celulares reales de la demo
se suman a eso.
"""

from __future__ import annotations

import argparse
import random
import shutil
import socket
import subprocess
import sys
import time
import tomllib
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

from demo_paso1 import CONFIG, Cliente, _id, _punto_en

from app.ciudad import directorio_ciudad
from app.config import resolver_ruta

RAIZ = Path(__file__).resolve().parent
NUMERO_SMS_DE_EJEMPLO = "+50588880000"
ESPERA_ALERTA_S = 2


def _semilla() -> dict[str, Any]:
    with (directorio_ciudad(CONFIG) / "semilla.toml").open("rb") as archivo:
        return tomllib.load(archivo)


def _ips_locales() -> list[str]:
    """Direcciones de la laptop en la red local (las que se escriben en los celulares)."""
    nombre = socket.gethostname()
    try:
        _, _, ips = socket.gethostbyname_ex(nombre)
    except OSError:
        ips = []
    return sorted({ip for ip in ips if not ip.startswith("127.")})


def _servidor_responde(url: str) -> bool:
    try:
        with urllib.request.urlopen(f"{url}/salud", timeout=3) as respuesta:
            return respuesta.status == 200
    except (urllib.error.URLError, OSError):
        return False


# ── preparar ───────────────────────────────────────────────


def preparar(url: str) -> None:
    if _servidor_responde(url):
        raise SystemExit(
            "El servidor está corriendo: detenerlo (Ctrl+C) antes de preparar, porque se borra la base."
        )
    directorio = directorio_ciudad(CONFIG)
    faltan = [
        nombre
        for nombre in (
            CONFIG.rutas.archivo_calles,
            CONFIG.preparacion.archivo_mapa,
            CONFIG.preparacion.archivo_poblacion,
            CONFIG.preparacion.archivo_vulnerabilidad,
        )
        if not (directorio / nombre).exists()
    ]
    if faltan:
        print(f"⚠ Faltan datos de la ciudad: {', '.join(faltan)} (ver preparacion/README.md).")
    subprocess.run([sys.executable, str(RAIZ / "semilla.py"), "--reiniciar"], check=True)
    print("\nBase lista, sin alerta activa (la alerta se emite en vivo durante la demo).")
    print(
        f"Iniciar el servidor:  uvicorn app.main:app --host 0.0.0.0 --port {CONFIG.servidor.puerto}"
    )
    for ip in _ips_locales():
        print(f"En los celulares, dirección del servidor:  {ip}:{CONFIG.servidor.puerto}")
    print("Después:  python datos/ensayo_demo.py verificar")


# ── verificar ──────────────────────────────────────────────


def verificar(url: str) -> None:
    resultados: list[tuple[bool | None, str]] = []

    def anotar(ok: bool | None, texto: str) -> None:
        resultados.append((ok, texto))
        marca = {True: "✓", False: "✗", None: "⚠"}[ok]
        print(f"  {marca} {texto}")

    print("Servidor")
    if not _servidor_responde(url):
        anotar(False, f"No responde en {url}: iniciar uvicorn")
        raise SystemExit(1)
    cliente = Cliente(url)
    ciudad = cliente.get("/ciudad")
    anotar(True, f"Responde · ciudad {ciudad['nombre']}, {ciudad['pais']}")
    anotar(ciudad["mapa_disponible"], "Mapa base de la ciudad (sin internet)")
    centro = ciudad["centro"]
    try:
        ruta = cliente.get(
            f"/rutas?origen={centro['lat']},{centro['lon']}&destino={centro['lat'] + 0.01},{centro['lon']}"
        )
        anotar(bool(ruta), "Grafo de calles para las rutas")
    except SystemExit:
        anotar(False, "Grafo de calles para las rutas (correr preparacion/descargar_osm.py)")
    equipos = cliente.get("/equipos", admin=True)
    disponibles = sum(1 for e in equipos if e["estado"] == "disponible")
    anotar(disponibles == len(equipos) > 0, f"Equipos disponibles: {disponibles}/{len(equipos)}")
    activo = cliente.get("/eventos/activo")
    anotar(
        activo is None,
        (
            "Sin alerta activa (se emite en vivo)"
            if activo is None
            else "Hay una alerta activa: correr «preparar»"
        ),
    )
    clave = cliente.get("/alertas/clave-publica")
    anotar(bool(clave.get("clave_publica")), "Clave pública para verificar las alertas firmadas")
    numero = ciudad.get("numero_sms")
    anotar(
        None if numero == NUMERO_SMS_DE_EJEMPLO else True,
        f"Número del receptor de SMS: {numero}"
        + (
            " (es el de ejemplo: cambiarlo en config/enlace.toml)"
            if numero == NUMERO_SMS_DE_EJEMPLO
            else ""
        ),
    )
    web = resolver_ruta(CONFIG.web.directorio) / "index.html"
    anotar(web.exists(), "Web del centro de coordinación compilada (npm run build en web/)")

    print("Red local")
    ips = _ips_locales()
    if not ips:
        anotar(False, "La laptop no tiene dirección en una red local")
    for ip in ips:
        anotar(
            _servidor_responde(f"http://{ip}:{CONFIG.servidor.puerto}"),
            f"Accesible en {ip}:{CONFIG.servidor.puerto} (la dirección que se escribe en los celulares)",
        )

    adb = shutil.which("adb") or str(
        Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe"
    )
    if Path(adb).exists():
        print("Celulares conectados por adb (opcional)")
        salida = subprocess.run(
            [adb, "devices"], capture_output=True, text=True
        ).stdout.splitlines()[1:]
        conectados = [linea.split()[0] for linea in salida if linea.strip().endswith("device")]
        anotar(None if not conectados else True, f"{len(conectados)} celular(es) por adb")

    errores = sum(1 for ok, _ in resultados if ok is False)
    avisos = sum(1 for ok, _ in resultados if ok is None)
    print(
        f"\n{'Todo listo' if not errores else f'{errores} problema(s)'}"
        + (f", {avisos} aviso(s)" if avisos else "")
    )
    if errores:
        raise SystemExit(1)


# ── fondo ──────────────────────────────────────────────────


def fondo(url: str, checkins: int, pedidos: int, duracion_s: float, espera_max_min: float) -> None:
    cliente = Cliente(url)
    semilla = _semilla()
    azar = random.Random()
    print("Esperando a que la coordinación emita la alerta…")
    limite = time.monotonic() + espera_max_min * 60
    evento = cliente.get("/eventos/activo")
    while evento is None:
        if time.monotonic() > limite:
            raise SystemExit("No se emitió ninguna alerta a tiempo")
        time.sleep(ESPERA_ALERTA_S)
        evento = cliente.get("/eventos/activo")
    print(f"Alerta {evento['id']}: simulando la ciudad durante {duracion_s:.0f} s")

    celdas = cliente.get("/celdas")
    pesos = [c["impacto_estimado"] + 5 for c in celdas]
    criticas = [c["id_h3"] for c in sorted(celdas, key=lambda c: -c["impacto_estimado"])[:10]]
    nombres = semilla["demo"]["nombres"]
    fraccion_punto = semilla["demo"]["fraccion_ubicacion_exacta"]

    acciones: list[tuple[str, Any, bool]] = []
    for _ in range(checkins):
        celda = azar.choices(celdas, weights=pesos)[0]
        riesgo = celda["impacto_estimado"] / (celda["impacto_estimado"] + 150)
        personas = azar.choice([1, 2, 3, 4, 5])
        checkin: dict[str, Any] = {
            "id_anonimo": _id(),
            "id_evento": evento["id"],
            "id_h3": celda["id_h3"],
            "estado": azar.choices(
                ["ayuda", "sin_respuesta", "bien"], weights=[riesgo, 0.25, 1 - riesgo]
            )[0],
            "personas": personas,
            "vulnerables": min(personas, azar.choices([0, 1, 2], weights=[0.55, 0.3, 0.15])[0]),
        }
        if azar.random() < fraccion_punto:
            lat, lon = _punto_en(celda["id_h3"], azar)
            checkin["ubicacion"] = {"lat": lat, "lon": lon}
        acciones.append(("/checkins", checkin, False))
    for i in range(pedidos):
        lat, lon = _punto_en(azar.choice(criticas), azar)
        origen = azar.choices(["app", "nearby", "sms"], weights=[0.6, 0.25, 0.15])[0]
        if origen == "sms":
            texto = f"ENL {lat},{lon} P{azar.randint(1, 5)} A{azar.randint(0, 2)} H{azar.randint(0, 2)} RM"
            remitente = f"+505 8{azar.randint(1000000, 9999999)}"
            acciones.append(("/sms/entrante", {"remitente": remitente, "texto": texto}, True))
            continue
        acciones.append(
            (
                "/reportes",
                {
                    "id_mensaje": _id(),
                    "id_anonimo": _id(),
                    "id_evento": evento["id"],
                    "origen": origen,
                    "tipo": "ayuda",
                    "lat": lat,
                    "lon": lon,
                    "personas": azar.randint(1, 5),
                    "atrapados": azar.choices([0, 1, 2, 3], weights=[0.4, 0.3, 0.2, 0.1])[0],
                    "heridos": azar.randint(0, 2),
                    "vulnerables": azar.randint(0, 1),
                    "necesidades": azar.sample(
                        ["rescate", "medico", "agua", "refugio"], azar.randint(1, 2)
                    ),
                    "saltos_nearby": azar.randint(1, 4) if origen == "nearby" else 0,
                    "identidad": {
                        "nombre": nombres[i % len(nombres)],
                        "telefono": f"+505 8{azar.randint(1000000, 9999999)}",
                    },
                },
                False,
            )
        )
    azar.shuffle(acciones)
    pausa = duracion_s / max(len(acciones), 1)
    for numero, (ruta, cuerpo, brigada) in enumerate(acciones, 1):
        cliente.post(ruta, cuerpo, brigada=brigada)
        if numero % 50 == 0 or numero == len(acciones):
            print(f"  {numero}/{len(acciones)} mensajes de la ciudad")
        time.sleep(pausa)
    print("Listo: el tablero y el centro de reportes ya muestran la ciudad.")


def main() -> None:
    argumentos = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    argumentos.add_argument("comando", choices=["preparar", "verificar", "fondo"])
    argumentos.add_argument("--url", default=f"http://127.0.0.1:{CONFIG.servidor.puerto}")
    argumentos.add_argument("--checkins", type=int, default=300)
    argumentos.add_argument("--pedidos", type=int, default=25)
    argumentos.add_argument(
        "--duracion", type=float, default=60.0, help="segundos para enviar todo"
    )
    argumentos.add_argument(
        "--espera", type=float, default=30.0, help="minutos esperando la alerta"
    )
    opciones = argumentos.parse_args()
    if opciones.comando == "preparar":
        preparar(opciones.url)
    elif opciones.comando == "verificar":
        verificar(opciones.url)
    else:
        fondo(opciones.url, opciones.checkins, opciones.pedidos, opciones.duracion, opciones.espera)


if __name__ == "__main__":
    main()
