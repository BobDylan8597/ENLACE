from fastapi.testclient import TestClient

from app.ciudad import obtener_ciudad
from app.config import obtener_config
from app.main import app


def test_config_carga_secciones_obligatorias() -> None:
    config = obtener_config()
    assert config.h3.resolucion == 8
    assert config.ciudad.activa


def test_ciudad_activa_tiene_zona_horaria_valida() -> None:
    ciudad = obtener_ciudad()
    assert ciudad.zona.key == ciudad.zona_horaria


def test_salud_responde_con_ciudad_activa() -> None:
    respuesta = TestClient(app).get("/salud")
    assert respuesta.status_code == 200
    assert respuesta.json()["ciudad"] == obtener_ciudad().nombre
