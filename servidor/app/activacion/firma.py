"""Firma Ed25519 de las alertas: la app descarta cualquier alerta sin firma válida.

Se firma el texto JSON exacto del contenido (no un objeto re-serializado), así la app solo
necesita verificar los bytes recibidos antes de interpretarlos, sin reproducir ningún formato.

Formato de una alerta firmada:
    {"contenido": "<texto JSON>", "firma": "<base64>", "algoritmo": "Ed25519"}
"""

from __future__ import annotations

import base64
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import (
    Ed25519PrivateKey,
    Ed25519PublicKey,
)

ALGORITMO = "Ed25519"
ARCHIVO_PRIVADA = "alertas_privada.pem"
ARCHIVO_PUBLICA = "alertas_publica.pem"


@dataclass(frozen=True)
class AlertaFirmada:
    contenido: str
    firma: str
    algoritmo: str = ALGORITMO

    def como_dict(self) -> dict[str, str]:
        return {"contenido": self.contenido, "firma": self.firma, "algoritmo": self.algoritmo}

    def datos(self) -> dict[str, Any]:
        return json.loads(self.contenido)


class Firmador:
    def __init__(self, clave_privada: Ed25519PrivateKey) -> None:
        self._privada = clave_privada

    @classmethod
    def cargar_o_crear(cls, directorio: Path) -> Firmador:
        """Usa el par de claves guardado; si no existe, lo genera (primer arranque)."""
        ruta_privada = directorio / ARCHIVO_PRIVADA
        if ruta_privada.exists():
            privada = serialization.load_pem_private_key(ruta_privada.read_bytes(), password=None)
            if not isinstance(privada, Ed25519PrivateKey):
                raise ValueError(f"{ruta_privada} no es una clave Ed25519")
            return cls(privada)
        directorio.mkdir(parents=True, exist_ok=True)
        privada = Ed25519PrivateKey.generate()
        ruta_privada.write_bytes(
            privada.private_bytes(
                serialization.Encoding.PEM,
                serialization.PrivateFormat.PKCS8,
                serialization.NoEncryption(),
            )
        )
        firmador = cls(privada)
        (directorio / ARCHIVO_PUBLICA).write_text(firmador.clave_publica_pem(), encoding="utf-8")
        return firmador

    def clave_publica_pem(self) -> str:
        return (
            self._privada.public_key()
            .public_bytes(
                serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo
            )
            .decode("ascii")
        )

    def clave_publica_base64(self) -> str:
        """Los 32 bytes de la clave pública, para incluirla en la app."""
        crudos = self._privada.public_key().public_bytes(
            serialization.Encoding.Raw, serialization.PublicFormat.Raw
        )
        return base64.b64encode(crudos).decode("ascii")

    def firmar(self, datos: dict[str, Any]) -> AlertaFirmada:
        contenido = json.dumps(datos, ensure_ascii=False, separators=(",", ":"))
        firma = self._privada.sign(contenido.encode("utf-8"))
        return AlertaFirmada(contenido, base64.b64encode(firma).decode("ascii"))


def verificar(alerta: dict[str, Any], clave_publica_base64: str) -> bool:
    try:
        if alerta.get("algoritmo") != ALGORITMO:
            return False
        publica = Ed25519PublicKey.from_public_bytes(base64.b64decode(clave_publica_base64))
        publica.verify(
            base64.b64decode(alerta["firma"], validate=True),
            str(alerta["contenido"]).encode("utf-8"),
        )
        return True
    except (InvalidSignature, KeyError, ValueError, TypeError):
        return False
