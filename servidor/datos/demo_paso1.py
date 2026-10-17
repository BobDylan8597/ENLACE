"""Demo de punta a punta del paso 1, contra el servidor en marcha (sin internet).

Uso (desde servidor/, con el servidor corriendo y la semilla cargada):
    python datos/demo_paso1.py [--url http://127.0.0.1:8000]

1. Emite la alerta del evento de prueba y verifica su firma.
2. Llegan 300 check-ins anónimos.
3. Llegan 35 reportes: app, SMS, lote de una mula Nearby (con repetidos), duplicados,
   radio sin verificar, evaluaciones de brigada y reportes de "estoy bien".
4. Muestra celdas, bandeja y recomendaciones; acepta la primera y la marca atendida.
"""

from __future__ import annotations

import argparse
import json
import random
import tomllib
import urllib.error
import urllib.request
import uuid
from typing import Any

import h3

from app.activacion.firma import verificar
from app.ciudad import directorio_ciudad
from app.config import obtener_config

CONFIG = obtener_config()
ADMIN = {"X-Clave-Admin": CONFIG.servidor.clave_admin}


class Cliente:
    def __init__(self, url: str) -> None:
        self.url = url.rstrip("/")

    def pedir(self, metodo: str, ruta: str, cuerpo: Any = None, admin: bool = False) -> Any:
        datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
        cabeceras = {"Content-Type": "application/json", **(ADMIN if admin else {})}
        pedido = urllib.request.Request(self.url + ruta, datos, cabeceras, method=metodo)
        try:
            with urllib.request.urlopen(pedido) as respuesta:
                return json.loads(respuesta.read() or b"null")
        except urllib.error.HTTPError as error:
            raise SystemExit(f"{metodo} {ruta} → {error.code}: {error.read().decode()}") from error

    def get(self, ruta: str, admin: bool = False) -> Any:
        return self.pedir("GET", ruta, admin=admin)

    def post(self, ruta: str, cuerpo: Any = None, admin: bool = False) -> Any:
        return self.pedir("POST", ruta, cuerpo, admin)


def _id() -> str:
    return str(uuid.uuid4())


def _titulo(texto: str) -> None:
    print(f"\n── {texto} " + "─" * max(0, 60 - len(texto)))


def _punto_en(id_h3: str, azar: random.Random) -> tuple[float, float]:
    lat, lon = h3.cell_to_latlng(id_h3)
    return round(lat + azar.uniform(-0.002, 0.002), 5), round(lon + azar.uniform(-0.002, 0.002), 5)


def main() -> None:
    argumentos = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    argumentos.add_argument("--url", default=f"http://127.0.0.1:{CONFIG.servidor.puerto}")
    cliente = Cliente(argumentos.parse_args().url)
    with (directorio_ciudad(CONFIG) / "semilla.toml").open("rb") as archivo:
        semilla = tomllib.load(archivo)
    azar = random.Random(semilla["semilla_aleatoria"])
    nombres = semilla["demo"]["nombres"]

    salud = cliente.get("/salud")
    print(f"Servidor ENLACE · {salud['ciudad']} ({salud['pais']})")
    if cliente.get("/eventos/activo") is not None:
        raise SystemExit("Ya hay un evento activo: recargar la semilla con --reiniciar")

    # 1. Alerta ─────────────────────────────────────────────
    _titulo("1. Emitir alerta")
    emision = cliente.post("/eventos", {"fuente": "manual", **semilla["evento_prueba"]}, admin=True)
    evento = emision["evento"]
    clave = cliente.get("/alertas/clave-publica")["clave_publica"]
    print(
        f"Evento {evento['id']}: {evento['tipo']} M{evento['magnitud']} · "
        f"{len(evento['zona_afectada'])} celdas en zona afectada · "
        f"firma {'válida' if verificar(emision['alerta'], clave) else 'INVÁLIDA'}"
    )

    celdas = cliente.get("/celdas")
    por_impacto = sorted(celdas, key=lambda c: c["impacto_estimado"], reverse=True)
    criticas = [c["id_h3"] for c in por_impacto[:8]]

    # 2. Check-ins ──────────────────────────────────────────
    _titulo("2. Check-ins anónimos")
    pesos = [c["impacto_estimado"] + 5 for c in celdas]
    for _ in range(semilla["demo"]["checkins"]):
        celda = azar.choices(celdas, weights=pesos)[0]
        riesgo = celda["impacto_estimado"] / (celda["impacto_estimado"] + 150)
        estado = azar.choices(
            ["ayuda", "sin_respuesta", "bien"], weights=[riesgo, 0.2, 1 - riesgo]
        )[0]
        cliente.post(
            "/checkins",
            {
                "id_anonimo": _id(),
                "id_evento": evento["id"],
                "id_h3": celda["id_h3"],
                "estado": estado,
            },
        )
    print(f"{semilla['demo']['checkins']} check-ins enviados (solo la celda h3, sin coordenadas)")

    # 3. Reportes ───────────────────────────────────────────
    _titulo("3. Reportes desde el terreno")
    enviados: list[dict[str, Any]] = []

    def ayuda(origen: str, **campos: Any) -> dict[str, Any]:
        celda = azar.choice(criticas)
        reporte = {
            "id_mensaje": _id(),
            "id_anonimo": _id(),
            "origen": origen,
            "tipo": "ayuda",
            "id_h3": celda,
            "personas": azar.randint(1, 6),
            "atrapados": azar.choice([0, 0, 1, 2, 3]),
            "heridos": azar.choice([0, 1, 1, 2]),
            "vulnerables": azar.choice([0, 0, 1]),
            "necesidades": azar.sample(
                ["rescate", "medico", "agua", "refugio"], azar.randint(1, 2)
            ),
        }
        reporte.update(campos)
        return reporte

    # 12 pedidos por la app; la mitad con punto exacto y datos de contacto
    for i in range(12):
        reporte = ayuda("app")
        if i % 2 == 0:
            lat, lon = _punto_en(reporte.pop("id_h3"), azar)
            reporte.update(
                lat=lat,
                lon=lon,
                identidad={
                    "nombre": nombres[i % len(nombres)],
                    "telefono": f"+505 8{azar.randint(1000000, 9999999)}",
                },
            )
        enviados.append(reporte)
        cliente.post("/reportes", reporte)
    print("12 pedidos de ayuda por la app")

    # 4 SMS reenviados por el receptor
    for _ in range(4):
        lat, lon = _punto_en(azar.choice(criticas), azar)
        texto = (
            f"ENL {lat},{lon} P{azar.randint(1, 5)} A{azar.randint(0, 2)} H{azar.randint(0, 2)} RM"
        )
        cliente.post(
            "/sms/entrante",
            {"remitente": f"+505 8{azar.randint(1000000, 9999999)}", "texto": texto},
        )
    print("4 SMS con formato ENL")

    # Una mula de datos entrega 8 reportes Nearby + 2 que ya habían llegado por la app
    lote = [ayuda("nearby", saltos_nearby=azar.randint(1, 4)) for _ in range(8)]
    lote += enviados[:2]
    resultado = cliente.post("/reportes", lote)
    print(
        f"Mula de datos: {len(resultado['aceptados'])} nuevos, {len(resultado['repetidos'])} repetidos ignorados"
    )

    # 3 personas reenvían su pedido (duplicados que se fusionan)
    for original in enviados[2:5]:
        copia = dict(original, id_mensaje=_id())
        cliente.post("/reportes", copia)
    print("3 pedidos reenviados por la misma persona (se fusionan como duplicados)")

    # 3 por radio sin verificar, 3 evaluaciones de brigada, 2 "estoy bien"
    for _ in range(3):
        cliente.post("/reportes", ayuda("radio"))
    for nivel in ("colapso", "grave", "leve"):
        cliente.post(
            "/reportes",
            {
                "id_mensaje": _id(),
                "id_anonimo": _id(),
                "origen": "brigada",
                "tipo": "evaluacion",
                "id_h3": azar.choice(criticas),
                "nivel_dano": nivel,
            },
        )
    for _ in range(2):
        cliente.post(
            "/reportes",
            {
                "id_mensaje": _id(),
                "id_anonimo": _id(),
                "origen": "app",
                "tipo": "bien",
                "id_h3": azar.choice(criticas),
            },
        )
    print("3 por radio, 3 evaluaciones de brigada y 2 «estoy bien»")

    # 4. Resultados ─────────────────────────────────────────
    _titulo("4. Celdas más prioritarias")
    for c in cliente.get("/celdas")[:6]:
        total = sum(c["checkins"].values())
        print(
            f"{c['prioridad']:5.1f}  {c['categoria']:<16} {c['nombre_zona']:<22} "
            f"impacto≈{c['impacto_estimado']:5.0f}  certeza {c['certeza']:4.0%}  check-ins {total}"
        )

    _titulo("Bandeja del centro de reportes")
    bandeja = cliente.get("/reportes", admin=True)
    print(f"{len(bandeja)} reportes (duplicados agrupados)")
    for r in bandeja[:6]:
        extra = f" +{r['duplicados']} dup." if r["duplicados"] else ""
        punto = " · punto exacto" if r["ubicacion"] else ""
        print(
            f"{r['prioridad']:5.1f}  {r['origen']:<8} {r['tipo']:<10} {r['nombre_zona']:<22} A{r['atrapados']} H{r['heridos']}{extra}{punto}"
        )

    _titulo("Recomendaciones del motor")
    recomendaciones = cliente.get("/recomendaciones", admin=True)
    for r in recomendaciones[:5]:
        print(f"• {r['motivo']}")

    if recomendaciones:
        _titulo("Decisión humana y atención")
        primera = recomendaciones[0]
        cliente.post(
            f"/asignaciones/{primera['id']}/decision",
            {"decision": "aceptada", "decidido_por": "Coordinación demo"},
            admin=True,
        )
        antes = cliente.get(f"/celdas/{primera['id_h3']}")
        print(f"Aceptada: {primera['nombre_equipo']} va a {antes['nombre_zona']}")
        # B8: la unidad evalúa la zona y encuentra menos daño del estimado
        cliente.post(
            "/reportes",
            {
                "id_mensaje": _id(),
                "id_anonimo": _id(),
                "origen": "brigada",
                "tipo": "evaluacion",
                "id_h3": primera["id_h3"],
                "nivel_dano": "leve",
            },
        )
        # B9: zona atendida
        cliente.post(f"/asignaciones/{primera['id']}/atendida", admin=True)
        despues = cliente.get(f"/celdas/{primera['id_h3']}")
        print("La brigada evalúa daño leve y marca la zona atendida:")
        print(
            f"  impacto ≈{antes['impacto_estimado']:.0f} → ≈{despues['impacto_estimado']:.0f} · "
            f"certeza {antes['certeza']:.0%} → {despues['certeza']:.0%} · "
            f"prioridad {antes['prioridad']:.1f} → {despues['prioridad']:.1f} · "
            f"{antes['categoria']} → {despues['categoria']}"
        )


if __name__ == "__main__":
    main()
