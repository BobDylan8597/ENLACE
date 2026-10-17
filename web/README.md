# Web del administrador

Centro de coordinación de ENLACE: React + Vite, Leaflet, h3-js, Tailwind CSS y WebSocket nativo.
**Funciona sin internet**: el mapa base es el `.pmtiles` local de la ciudad (lo sirve el servidor
y lo dibuja `protomaps-leaflet`).

## Uso en la demo (sin Node)

```bash
cd web
npm install        # una vez, con internet
npm run build      # genera web/dist
```

El servidor ENLACE entrega `web/dist` en su raíz: con el servidor corriendo, abrir
`http://<ip-de-la-laptop>:8000/` desde cualquier equipo de la red local. Se ingresa con un nombre
(queda registrado en cada decisión) y la clave de `servidor/config/enlace.toml`.

## Desarrollo

```bash
npm run dev        # http://localhost:5173, reenvía la API y el WebSocket a 127.0.0.1:8000
npm test           # pruebas (vitest)
```

Otra dirección de servidor: variable `ENLACE_SERVIDOR`.

## Pantallas

| Ruta | Pantalla |
|---|---|
| `#/reportes` | A2 centro de reportes (vista inicial): bandeja por prioridad, filtros |
| `#/reportes/<id>` | A3 detalle: persona, equipo sugerido con "Asignar", ruta, por qué de la prioridad |
| `#/mapa` | A4 tablero por capas: zonas, check-ins, pedidos de ayuda, equipos, bloqueos, rutas |
| `#/mapa/<h3>` | A5 detalle de la zona |
| `#/alerta` | A6 emitir alerta (con confirmación), cerrar evento, propuestas de la comunidad |
| `#/equipos` | A12 equipos y registro |
| `#/manual` | A9 reporte manual (teléfono, radio, en persona) |

Todo se actualiza solo por WebSocket (`/ws/admin`); el indicador de conexión está siempre visible.

## Organización

- `src/paginas/`: una pantalla por archivo. `src/componentes/`: un componente por archivo.
- `src/api/`: cliente HTTP, conexión en tiempo real y `useDatos` (carga y recarga ante avisos).
- `src/textos/es.js`: todos los textos. Para portugués, crear `pt.js` con las mismas claves.
- `src/util/colores.js`: colores con significado fijo (rojo actuar, ámbar explorar, verde estable,
  gris sin datos, azul acción), siempre acompañados de ícono o texto.
- `src/configuracion.js`: umbrales de presentación (los del motor están en el servidor).

Los identificadores se generan con `crypto.getRandomValues`: `crypto.randomUUID` no existe cuando
la web se abre por `http://` con la IP de la laptop, que es como se usa en la demo.

## Estilo del mapa de la app Android

`node scripts/estilo_movil.mjs` genera con `@protomaps/basemaps` el estilo de MapLibre (español y
portugués) y descarga sus fuentes e íconos a `movil/app/src/main/assets/mapa`. Se corre una vez,
con internet; el resultado se sube al repositorio y sirve para cualquier ciudad.

