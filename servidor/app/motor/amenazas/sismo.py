"""Modelo de sismo: intensidad por atenuación y daño por curvas de fragilidad."""

from __future__ import annotations

import math

from app.config import ConfigSismo
from app.modelos.enums import TipoEvento
from app.motor.geo import centro_celda, distancia_km
from app.motor.tipos import DatosCelda, DatosEvento


def _normal_acumulada(x: float) -> float:
    return 0.5 * (1.0 + math.erf(x / math.sqrt(2.0)))


class ModeloSismo:
    tipo = TipoEvento.sismo

    def __init__(self, config: ConfigSismo) -> None:
        self._config = config

    def intensidad(self, evento: DatosEvento, lat: float, lon: float) -> float:
        """Intensidad de Mercalli modificada (MMI) estimada en un punto."""
        if evento.magnitud is None:
            raise ValueError("Un sismo necesita magnitud para estimar su intensidad")
        c = self._config
        profundidad = (
            evento.profundidad_km
            if evento.profundidad_km is not None
            else c.profundidad_por_defecto_km
        )
        epicentral = distancia_km(evento.lat, evento.lon, lat, lon)
        hipocentral = max(math.hypot(epicentral, profundidad), c.distancia_minima_km)
        return c.a + c.b * evento.magnitud - c.c * math.log10(hipocentral)

    def probabilidad_dano(self, intensidad: float, tipo_construccion: str) -> float:
        curva = self._config.fragilidad.get(tipo_construccion)
        if curva is None:
            raise KeyError(f"Tipo de construcción sin curva de fragilidad: {tipo_construccion}")
        return _normal_acumulada((intensidad - curva.mediana) / curva.dispersion)

    def fraccion_dano(self, evento: DatosEvento, celda: DatosCelda) -> float:
        lat, lon = centro_celda(celda.id_h3)
        intensidad = self.intensidad(evento, lat, lon)
        total = sum(celda.tipos_construccion.values())
        if total <= 0:
            return 0.0
        return sum(
            fraccion / total * self.probabilidad_dano(intensidad, tipo)
            for tipo, fraccion in celda.tipos_construccion.items()
        )
