# API (fachada)

Rutas REST y WebSocket. La lista completa está en `/docs` con el servidor corriendo.

| Archivo | Rutas |
|---|---|
| `eventos.py` | Emitir y cerrar eventos, alerta vigente, clave pública, propuestas comunitarias |
| `celdas.py` | Totales anónimos por celda y detalle con el porqué de su prioridad |
| `reportes.py` | Reportes (uno o un lote), bandeja, detalle, check-ins, SMS entrante |
| `asignaciones.py` | Recomendaciones, decisión humana, zona atendida |
| `equipos.py` | Consulta y registro de equipos, posición de la unidad |
| `rutas.py` | Bloqueos (reportar, confirmar, descartar) y `GET /rutas` |
| `tiempo_real.py` | `/ws/admin` y `/ws/movil/{id}` |

## Reglas que se aplican aquí

- **Lotes de mulas de datos:** cada reporte se valida por separado. La respuesta separa
  `aceptados`, `repetidos` (ya recibidos: no es un error) y `rechazados` (con el motivo).
- **Privacidad:** el check-in no admite coordenadas. Un reporte con lat/lon se reduce a su celda,
  salvo los pedidos de ayuda, cuyo punto exacto y datos de contacto se guardan en `Identidad`.
- **SMS:** `formato_sms.py` interpreta `ENL <lat>,<lon> P<n> A<n> H<n> <RMWF>`. El mismo SMS
  reenviado dos veces genera el mismo `id_mensaje` y se descarta.

## Mensajes por WebSocket

`{"tipo": "<tema>", "datos": {...}}`

- `/ws/admin?clave=…`: todos los temas del bus (evento_emitido, celda_actualizada,
  reporte_recibido, recomendaciones_actualizadas, asignacion_cambiada, propuesta_comunitaria…).
- `/ws/movil/{id}`:
  - `alerta`: para todos, al emitirse y al conectarse si ya hay una activa;
  - `asignacion`: para `equipo-<n>`, con el destino (el punto exacto si es un pedido de ayuda)
    y la ruta en GeoJSON con instrucciones paso a paso;
  - `ruta_recalculada`: para `equipo-<n>`, cuando un bloqueo cambia su ruta;
  - `ayuda_en_camino`: para el `id_anonimo` de quien pidió ayuda;
  - `evento_cerrado`: para todos.
