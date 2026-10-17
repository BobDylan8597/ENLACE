# Preparación de ciudades

Scripts que se ejecutan **una vez, con internet**, antes de la demo. Lo que generan queda en
`servidor/datos/ciudades/<id>/` y no se sube al repositorio (se regenera en minutos).

## descargar_osm.py

```bash
servidor/.venv/Scripts/python -m pip install -e "servidor[preparacion]"
servidor/.venv/Scripts/python preparacion/descargar_osm.py                 # ciudad activa
servidor/.venv/Scripts/python preparacion/descargar_osm.py --ciudad tegucigalpa
```

Lee el centro y el radio de `ciudad.toml` (más un margen) y genera:

- `calles.graphml`: grafo de calles para vehículos con tiempo de viaje por tramo, descargado de
  OpenStreetMap con OSMnx. Si un servidor Overpass no responde, prueba los siguientes de la lista
  en `config/enlace.toml`.
- `mapa.pmtiles`: mapa base vectorial solo de la zona, extraído por rangos HTTP de la compilación
  diaria de Protomaps. Sirve para la web (Leaflet) y la app Android (MapLibre) sin internet.
  No se descargan imágenes de `tile.openstreetmap.org`, cuya política prohíbe la descarga masiva.

| Ciudad | Calles | Mapa base | Tiempo |
|---|---|---|---|
| Managua (6,5 km) | 13.921 cruces, 19,5 MB | 228 teselas, 3,1 MB | ~4 min |
| Tegucigalpa (3,5 km) | 6.223 cruces, 8,5 MB | 97 teselas, 2,9 MB | ~2 min |

Atribución obligatoria en las interfaces que muestren el mapa: © OpenStreetMap · Protomaps.

## calcular_poblacion.py

```bash
servidor/.venv/Scripts/python preparacion/calcular_poblacion.py [--ciudad <id>]
```

- **Población de noche por celda:** [Kontur Population](https://data.humdata.org/dataset/kontur-population-dataset)
  (CC BY 4.0), publicado por país en HDX ya en hexágonos H3 de resolución 8, la misma de ENLACE.
  El país sale de `codigo_pais` en `ciudad.toml`, así que sirve para cualquier país de la región.
- **Tipo de zona (día/noche):** comercios, oficinas y servicios de OSM por celda. Es relativo a la
  ciudad (OSM está más o menos completo según el lugar): el 20 % con más comercio es comercial y el
  30 % siguiente mixto. Los factores de ocupación de día y de noche están en `semilla.toml`.
- **Nombres de zona:** el barrio o colonia de OSM más cercano al centro de cada celda.

Genera `poblacion.json`. Managua: 552.000 personas en 95 celdas habitadas, 5.287 comercios, 600
barrios con nombre.

## asignar_vulnerabilidad.py

```bash
servidor/.venv/Scripts/python preparacion/asignar_vulnerabilidad.py [--ciudad <id>] [--indice-oficial indice.csv]
```

- **Infraestructura crítica por celda (OSM):** hospitales, clínicas, escuelas, bomberos, policía y
  albergues, con sus nombres (se ve en el detalle de la zona).
- **Vulnerabilidad 0–1 aproximada:** densidad de población, distancia al hospital o clínica más
  cercano y asentamientos marcados como informales en OSM (pesos en `config/enlace.toml`).
  Es una aproximación con datos abiertos: si el país tiene un índice oficial por zona (por ejemplo
  necesidades básicas insatisfechas), `--indice-oficial` acepta un CSV `id_h3,vulnerabilidad`.

Genera `vulnerabilidad.json`. Después: `python datos/semilla.py --reiniciar` (desde `servidor/`)
usa los datos reales; sin estos archivos, la semilla sigue siendo sintética.

Las respuestas de Overpass y el archivo de Kontur quedan en `.cache_datos/`: volver a correr los
scripts no repite descargas (los servidores públicos de Overpass limitan las consultas seguidas).

## validar_evento.py — validación retrospectiva

```bash
servidor/.venv/Scripts/python preparacion/validar_evento.py                       # sismos por defecto
servidor/.venv/Scripts/python preparacion/validar_evento.py --evento us2000ar20 --evento usd0003cqx:NI
```

Compara la intensidad que ENLACE estima desde el primer minuto (el mismo modelo del motor, solo
con magnitud, epicentro y profundidad) con lo observado en sismos reales del catálogo del USGS:
reportes de la gente («¿Lo sintió?», DYFI) y estaciones sísmicas del ShakeMap. Con `id:PAIS`
compara también la población expuesta (Kontur) con PAGER del USGS. Propone coeficientes
regionales, pero no cambia la configuración.

Resultado en [`resultados/validacion_sismos.md`](resultados/validacion_sismos.md). Con los sismos
por defecto (Costa Rica 2012 M7.6, México 2017 M7.1 y Managua 2014 M6.1):

- 210 observaciones: sesgo +0,22 grados, error típico 0,87, **72 % dentro de ±1 grado**.
- Managua 2014: el modelo sobrestima cerca del epicentro (pone la ciudad en VI; el USGS, en V).
  La estimación inicial es conservadora y por eso arranca con certeza baja.
