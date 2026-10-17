# Rutas y bloqueos

Ruta más rápida por **tiempo de viaje** (no distancia) sobre el grafo de calles de OSM de la
ciudad activa, evitando bloqueos. Funciona sin internet: el grafo se prepara una vez con
`preparacion/descargar_osm.py` y se carga al arrancar el servidor (unos 3 s para Managua).
Si no hay grafo, el servidor sigue funcionando y estima los tiempos en línea recta.

| Archivo | Qué hace |
|---|---|
| `grafo.py` | Carga el grafo; cruce y calle más cercanos a un punto (numpy y STRtree de shapely) |
| `bloqueos.py` | Estado de cada bloqueo según quién lo reporta y su efecto en el costo de la calle |
| `calculo.py` | Ruta en GeoJSON con instrucciones paso a paso, y estimador de tiempos para el motor |
| `servicio.py` | Registro de bloqueos, ruta de cada asignación y recálculo de las rutas afectadas |

## Bloqueos

| Estado | Cuándo | Efecto en la calle |
|---|---|---|
| sin_confirmar | 1 persona lo reportó | tiempo × 3 |
| probable | 2 personas distintas | tiempo × 10 |
| confirmado | 3 personas distintas, o una brigada | la calle se elimina |
| descartado | una brigada u operador lo descarta | la calle se restaura |

- `solo_a_pie` elimina la calle solo para vehículos.
- Un reporte a menos de 50 m de un bloqueo activo lo confirma; la misma persona no cuenta dos veces.
- El bloqueo corta la calle en ambos sentidos.
- Un punto a más de 150 m de toda calle queda registrado pero no cambia ninguna ruta.
- Las velocidades de OSM se multiplican por 0,7 (escombros, tráfico tras el desastre).

Todos estos valores están en `[rutas]` de `config/enlace.toml`.

## Rutas activas

Al aceptar una asignación se calcula la ruta del equipo a su destino (el punto exacto de quien
pidió ayuda o el centro de la celda) y se guarda con la lista de tramos que recorre. Cuando un
bloqueo cambia de estado, se recalculan **solo** las rutas activas que pasan por esa calle, desde
la última posición informada del equipo (`POST /equipos/{id}/ubicacion`), y la unidad recibe
`ruta_recalculada` por `/ws/movil/equipo-<n>`. Una ruta deja de estar activa al marcarse atendida.

## Rendimiento (Managua, 13.921 cruces y 38.324 tramos)

- Una ruta: ~30 ms.
- Estimador del motor: un Dijkstra por equipo hacia toda la red (~60 ms), guardado en caché
  hasta que cambie algún bloqueo; después, cada destino cuesta menos de 1 ms.
