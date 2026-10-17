# Servidor ENLACE

API, motor de decisión y datos. Corre en una laptop en red local, **sin internet**.
Python 3.11+, FastAPI, SQLite. Monolito modular: cada carpeta de `app/` es un módulo con su README.

## Puesta en marcha

```bash
cd servidor
python -m venv .venv
.venv/Scripts/python -m pip install -e ".[dev,preparacion]"  # Windows (Linux/macOS: .venv/bin/python)

# Una vez, con internet: mapa de calles y mapa base de la ciudad activa (ver preparacion/)
.venv/Scripts/python ../preparacion/descargar_osm.py

.venv/Scripts/python datos/semilla.py --reiniciar   # ciudad activa: celdas y equipos sintéticos
.venv/Scripts/python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

No hay migraciones de base de datos: si cambia el modelo, volver a cargar la semilla con
`--reiniciar`.

- Documentación interactiva de la API: <http://127.0.0.1:8000/docs>
- Demos de punta a punta (con el servidor corriendo y la semilla recién cargada):
  `datos/demo_paso1.py` (motor y decisiones) y `datos/demo_paso2.py` (rutas y bloqueos)
- Pruebas: `.venv/Scripts/python -m pytest` · formato: `.venv/Scripts/python -m black app tests datos`

## Configuración

Todo umbral, peso, plazo y clave vive en [`config/enlace.toml`](config/enlace.toml) (otra ruta con
la variable `ENLACE_CONFIG`). El servidor no arranca si falta un valor o si un grupo de pesos no
suma 1. La **ciudad activa** se elige en `[ciudad] activa`: cada ciudad es una carpeta en
`datos/ciudades/<id>/` con `ciudad.toml` (centro, radio, país, zona horaria) y `semilla.toml`
(datos de demostración). Managua es solo la ciudad de prueba.

## Módulos

| Carpeta | Qué hace |
|---|---|
| `app/modelos/` | Tablas SQLite, valores cerrados y esquemas de la API |
| `app/motor/` | Impacto inicial, fusión de evidencia, prioridad y recomendación de equipos |
| `app/activacion/` | Fuentes de alerta (CAP, sismológica, manual, comunitaria), firma Ed25519, emisión |
| `app/api/` | Rutas REST y WebSocket (fachada del sistema) |
| `app/eventos/` | Pub/sub interno entre módulos |
| `app/privacidad/` | Cierre de eventos y borrado programado de datos personales |
| `app/rutas/` | Grafo de calles, bloqueos, rutas con instrucciones y recálculo |
| `app/paquete/` | Pendiente: PDF del paquete de primera hora |

## Rutas agregadas a la tabla de CLAUDE.md (sección 8)

| Método | Ruta | Para qué |
|---|---|---|
| GET | `/eventos/activo` | Saber si hay un evento en curso |
| GET | `/alertas/activa` | Alerta firmada vigente, para la app que se conecta tarde |
| GET | `/alertas/clave-publica` | Clave Ed25519 con la que la app verifica las alertas |
| GET | `/propuestas` · POST `/propuestas/{id}/descartar` | Propuestas comunitarias (se aprueban con POST `/eventos`, fuente `comunitaria`) |
| GET | `/reportes/{id}` | Detalle del reporte (pantalla A3) |
| POST | `/asignaciones/{id}/atendida` | La unidad termina (pantalla B9) |
| POST | `/equipos/{id}/ubicacion` | Posición de la unidad mientras navega (solo equipos, nunca ciudadanos) |

Las rutas del administrador piden la cabecera `X-Clave-Admin` (en `/ws/admin`, el parámetro
`?clave=`). Es una clave fija para la demo; en producción, autenticación por usuario y nivel.

## Campos agregados al modelo de datos (sección 7)

- `Evento.profundidad_km`: distancia hipocentral del sismo.
- `Celda.tipos_construccion`, `impacto_inicial`, `suma_pesos`, `ultima_atencion`.
- `Reporte.id_evento`.
- `Bloqueo.reportantes`, `confirmado_por_brigada`, `nodo_u`, `nodo_v`, `clave_tramo`, `hora`, `actualizado`.
- `Asignacion.hora_fin`.
- Tabla `propuestas_comunitarias`.
