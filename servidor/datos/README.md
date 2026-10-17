# Datos

- `ciudades/<id>/ciudad.toml`: centro, radio, país y zona horaria de la ciudad.
- `ciudades/<id>/semilla.toml`: población sintética, mezcla de construcción, barrios, equipos,
  zonas sin población y evento de prueba. En el paso 6, `preparacion/` reemplaza la población y la
  vulnerabilidad sintéticas por datos reales (OSM, censo) y descarga el mapa local de la ciudad.
- `semilla.py`: carga la ciudad activa en la base de datos (`--reiniciar` la borra antes).
- `demo_paso1.py`: recorre el flujo completo contra el servidor en marcha.
- `demo_paso2.py`: rutas y bloqueos (ruta recalculada).
- `ensayo_demo.py`: puesta a punto de la demostración en vivo (`preparar`, `verificar`, `fondo`). Ver `GUION_DEMO.md` en la raíz.

Para usar otra ciudad: crear `ciudades/<id>/` con los dos archivos y cambiar `[ciudad] activa` en
`config/enlace.toml`. Las coordenadas de los barrios de Managua son aproximadas y solo sirven para
dar nombre a las celdas.
