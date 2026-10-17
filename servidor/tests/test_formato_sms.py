import pytest

from app.api.formato_sms import SmsInvalido, SmsReporte, interpretar_sms
from app.modelos.enums import Necesidad


def test_ejemplo_del_documento() -> None:
    assert interpretar_sms("ENL 13.10,-85.20 P4 A1 H1 RM") == SmsReporte(
        13.10, -85.20, 4, 1, 1, [Necesidad.rescate, Necesidad.medico]
    )


def test_tolera_minusculas_espacios_y_partes_opcionales() -> None:
    assert interpretar_sms("  enl 12.1, -86.25   wf ") == SmsReporte(
        12.1, -86.25, 0, 0, 0, [Necesidad.agua, Necesidad.refugio]
    )


@pytest.mark.parametrize(
    "texto",
    ["", "HOLA 13.1,-85.2", "ENL", "ENL norte", "ENL 13.1,-85.2 X9", "ENL 95.0,-85.2"],
)
def test_mensajes_invalidos(texto: str) -> None:
    with pytest.raises(SmsInvalido):
        interpretar_sms(texto)
