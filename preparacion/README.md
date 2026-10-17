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

Pendientes del paso 6: `calcular_poblacion.py`, `asignar_vulnerabilidad.py`, `validar_evento.py`.
