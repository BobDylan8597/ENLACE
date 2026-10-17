"""Fuente CAP: alertas oficiales en Common Alerting Protocol 1.2 (XML).

Solo se aceptan mensajes reales (`status` Actual) de tipo alerta (`msgType` Alert).
El área se toma del primer `<circle>` ("lat,lon radio_km").
"""

from __future__ import annotations

import unicodedata
import xml.etree.ElementTree as ET
from datetime import datetime

from app.activacion.fuentes.base import EntradaInvalida, NuevoEvento
from app.modelos.enums import FuenteEvento, TipoEvento

NS = {"cap": "urn:oasis:names:tc:emergency:cap:1.2"}

PALABRAS_POR_TIPO = {
    TipoEvento.sismo: ("sismo", "terremoto", "earthquake", "tremor"),
    TipoEvento.inundacion: ("inundacion", "crecida", "flood"),
    TipoEvento.huracan: ("huracan", "hurricane", "ciclon", "tormenta tropical"),
}
PARAMETROS_MAGNITUD = ("magnitud", "magnitude")
PARAMETROS_PROFUNDIDAD = ("profundidad", "depth")


def _sin_tildes(texto: str) -> str:
    return "".join(
        c for c in unicodedata.normalize("NFD", texto.lower()) if unicodedata.category(c) != "Mn"
    )


def _tipo(evento: str) -> TipoEvento:
    texto = _sin_tildes(evento)
    for tipo, palabras in PALABRAS_POR_TIPO.items():
        if any(p in texto for p in palabras):
            return tipo
    return TipoEvento.otro


def _texto(nodo: ET.Element, ruta: str) -> str | None:
    hallado = nodo.find(ruta, NS)
    return hallado.text.strip() if hallado is not None and hallado.text else None


class FuenteCap:
    fuente = FuenteEvento.cap

    def convertir(self, entrada: str | bytes) -> NuevoEvento:
        try:
            raiz = ET.fromstring(entrada)
        except ET.ParseError as error:
            raise EntradaInvalida(f"CAP mal formado: {error}") from error
        if _texto(raiz, "cap:status") != "Actual" or _texto(raiz, "cap:msgType") != "Alert":
            raise EntradaInvalida("Solo se aceptan alertas CAP reales (Actual / Alert)")
        info = raiz.find("cap:info", NS)
        circulo = _texto(raiz, "cap:info/cap:area/cap:circle")
        enviado = _texto(raiz, "cap:sent")
        if info is None or circulo is None or enviado is None:
            raise EntradaInvalida("CAP sin info, área circular u hora de envío")
        try:
            centro, _radio = circulo.split()
            lat, lon = (float(v) for v in centro.split(","))
            hora = datetime.fromisoformat(enviado)
        except ValueError as error:
            raise EntradaInvalida(f"CAP con valores inválidos: {error}") from error
        if hora.tzinfo is None:
            raise EntradaInvalida("La hora de envío CAP debe incluir zona horaria")

        parametros: dict[str, str] = {}
        for parametro in info.findall("cap:parameter", NS):
            nombre = _texto(parametro, "cap:valueName")
            valor = _texto(parametro, "cap:value")
            if nombre and valor:
                parametros[_sin_tildes(nombre)] = valor

        def numero(nombres: tuple[str, ...]) -> float | None:
            for nombre in nombres:
                if nombre in parametros:
                    try:
                        return float(parametros[nombre])
                    except ValueError as error:
                        raise EntradaInvalida(f"Parámetro {nombre} no numérico") from error
            return None

        return NuevoEvento(
            tipo=_tipo(_texto(info, "cap:event") or ""),
            fuente=self.fuente,
            lat=lat,
            lon=lon,
            hora=hora,
            magnitud=numero(PARAMETROS_MAGNITUD),
            profundidad_km=numero(PARAMETROS_PROFUNDIDAD),
        )
