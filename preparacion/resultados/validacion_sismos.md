# Validación retrospectiva del modelo de intensidad

Generado el 2026-10-17 con `preparacion/validar_evento.py`.

ENLACE estima la intensidad de un sismo **desde el primer minuto**, solo con magnitud, epicentro y profundidad (atenuación de Bakun y Wentworth, 1997: MMI = 3.67 + 1.17·M − 3.19·log10(R)). Aquí se compara esa estimación con lo que se observó en sismos reales de la región:

- **DYFI** («¿Lo sintió?», USGS): intensidad reportada por la gente, en celdas de 10 km con al menos 3 respuestas.
- **Estaciones**: intensidad del ShakeMap del USGS calculada con la aceleración medida.

## 11 km ENE of Hojancha, Costa Rica — M7.6, 2012-09-05, profundidad 35 km

Evento del USGS: `usp000jrsw`.

| | Observaciones | Sesgo (estimada − observada) | Error típico (RMSE) | Dentro de ±1 grado |
|---|---|---|---|---|
| Todas | 99 | +0.29 | 0.72 | 81% |
| DYFI | 38 | +0.53 | 0.90 | 66% |
| Estaciones | 61 | +0.15 | 0.59 | 90% |
| 0–50 km | 4 | +0.24 | 0.48 | 100% |
| 50–150 km | 49 | +0.30 | 0.66 | 88% |
| 150–300 km | 37 | +0.26 | 0.82 | 70% |
| más de 300 km | 9 | +0.38 | 0.74 | 78% |

## 1 km S of Matzaco, Mexico — M7.1, 2017-09-19, profundidad 48 km

Evento del USGS: `us2000ar20`.

| | Observaciones | Sesgo (estimada − observada) | Error típico (RMSE) | Dentro de ±1 grado |
|---|---|---|---|---|
| Todas | 111 | +0.15 | 0.98 | 64% |
| DYFI | 82 | -0.03 | 1.03 | 61% |
| Estaciones | 29 | +0.68 | 0.84 | 72% |
| 50–150 km | 39 | -0.67 | 1.00 | 56% |
| 150–300 km | 32 | +0.34 | 0.81 | 72% |
| más de 300 km | 40 | +0.80 | 1.08 | 65% |

## 16 km SW of Valle San Francisco, Nicaragua — M6.1, 2014-04-10, profundidad 13 km

Evento del USGS: `usd0003cqx`.

Sin observaciones suficientes (ni DYFI con varias respuestas ni estaciones).

**Población expuesta** (Kontur Population de NI con la intensidad de ENLACE, frente a PAGER del USGS, que usa el ShakeMap y su propia capa de población):

| Intensidad | ENLACE | PAGER |
|---|---|---|
| IV | 1,999,228 | 4,196,164 |
| V | 2,298,812 | 1,676,947 |
| VI | 1,625,827 | 162,184 |
| VII | 7,996 | 5,675 |

En intensidad VI o más (daños leves en adelante): ENLACE 1,633,823 personas, PAGER 167,859 (9.7 veces).

## Conjunto

| | Observaciones | Sesgo (estimada − observada) | Error típico (RMSE) | Dentro de ±1 grado |
|---|---|---|---|---|
| Todos los sismos | 210 | +0.22 | 0.87 | 72% |
| 0–50 km | 4 | +0.24 | 0.48 | 100% |
| 50–150 km | 88 | -0.13 | 0.83 | 74% |
| 150–300 km | 69 | +0.30 | 0.81 | 71% |
| más de 300 km | 49 | +0.73 | 1.03 | 67% |

### Calibración regional propuesta

Ajustando por mínimos cuadrados `a` y `c` (con b = 1.17 fijo) a estas observaciones: **a = 5.70, c = 4.19** (hoy 3.67 y 3.19).

| | Observaciones | Sesgo (estimada − observada) | Error típico (RMSE) | Dentro de ±1 grado |
|---|---|---|---|---|
| Con los coeficientes actuales | 210 | +0.22 | 0.87 | 72% |
| Con los propuestos | 210 | +0.00 | 0.79 | 77% |

Los coeficientes están en `servidor/config/enlace.toml` (`[amenazas.sismo]`). Cambiarlos es una decisión del equipo: conviene validar con más sismos antes de adoptarlos.

## Limitaciones

- DYFI depende de que la gente responda: en Centroamérica hay pocas respuestas lejos de las ciudades, y la intensidad reportada tiende a ser más baja en zonas poco pobladas.
- El modelo usa la distancia al hipocentro y no considera el suelo ni el tipo de falla; ENLACE lo corrige después con los reportes que llegan (fusión de evidencia).
- Cerca del epicentro (menos de 50 km) hay muy pocas observaciones en estos sismos: el ajuste propuesto mejora el promedio pero no está comprobado ahí, donde está el daño. En Managua 2014 (sismo poco profundo a ~30 km de la ciudad) el modelo pone a la ciudad en intensidad VI y el USGS en V: la estimación inicial de ENLACE es conservadora (prefiere sobrestimar), y por eso la certeza inicial es baja y las zonas quedan para «explorar».
- La población de Kontur es residencial (de noche) y la de PAGER es LandScan (promedio del día); las diferencias en exposición también vienen de ahí.
