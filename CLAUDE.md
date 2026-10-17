# CLAUDE.md — ENLACE

Contexto del proyecto para Claude Code. Léelo completo antes de cualquier tarea.

## 1. Contexto

Proyecto para el **Rally Latinoamericano de Innovación**, Reto 09: "Optimización y priorización de recursos
y esfuerzos en la post-emergencia". El reto pide ayudar a las autoridades a **decidir con rapidez** a quién
atender primero, dónde concentrar esfuerzos y qué recursos asignar tras un desastre (sismo, inundación,
huracán, alud), con cortes de comunicación, rutas bloqueadas e información incompleta. Debe ser viable en
toda Latinoamérica.

**Idea central:** ENLACE tiene dos partes. El **motor de decisión** (el cerebro) estima el impacto desde el
primer minuto, sin esperar reportes, y lo corrige con cada evidencia que llega por la **red de captura
multicanal** (el sistema nervioso), que funciona incluso sin internet.

Frase guía: *"Decidir cuando todavía no se ve nada, y corregir con cada dato que llega."*

## 2. Criterios de evaluación (rúbrica del jurado)

- a) Innovación (30): innovadora, creativa, tecnología apropiada, usabilidad.
- b) Impacto social (20): problema específico, valor social sobre valor de mercado, calidad de vida.
- c) Prefactibilidad económica/ambiental (20): sustentable, eficiencia energética, optimiza recursos, mejora ambiental.
- d) Viabilidad técnica (20): base teórica, recursos de fácil obtención, acorde a usuarios, técnicamente validada.
- e) Presentación (10).

Toda decisión técnica debe favorecer: **funcionar sin internet, ser demostrable en vivo, ser simple de explicar.**

## 3. Componentes del sistema

1. **Servidor** (Python/FastAPI): API, motor de decisión, datos. Corre en una laptop en red local SIN internet.
2. **Plataforma web del administrador** (React): centro de reportes, tablero con mapa, alertas, equipos.
3. **App Android ENLACE** (Kotlin): un solo APK con dos modos: **usuario** (ciudadano) y **unidad** (brigada).
4. **App receptora de SMS** (Kotlin, mínima): recibe SMS con formato de reporte y los reenvía al servidor.

## 4. Stack tecnológico

**Servidor:** Python 3.11+, FastAPI (REST + WebSocket), SQLite, h3 (celdas hexagonales, resolución 8),
GeoPandas, Shapely, OSMnx + NetworkX (calles y rutas), NumPy, Pandas, APScheduler (borrado programado),
WeasyPrint (PDF del paquete de primera hora), cryptography (firma Ed25519 de alertas), pytest.

**Web:** React + Vite, Leaflet, h3-js, Tailwind CSS, WebSocket nativo. Mapa base de calles servido localmente
(tiles OSM en la laptop). Capa satelital opcional solo con internet (Esri World Imagery, con atribución).

**Android:** Kotlin, Jetpack Compose, MVVM, Hilt, Room, WorkManager, Retrofit + OkHttp (WebSocket),
Google Nearby Connections (estrategia P2P_CLUSTER), Fused Location Provider, MapLibre Native u osmdroid
con mapas offline. minSdk 26.

**Producción (solo mencionar, no implementar):** PostgreSQL + PostGIS, OSRM/GraphHopper, Firebase Cloud
Messaging / APNs, versión iOS con Multipeer Connectivity, nodos LoRa (Meshtastic), imágenes satelitales.

## 5. Patrones de diseño

- Servidor: **monolito modular** (no microservicios). Módulos independientes.
- Android: **Clean Architecture** (presentación / dominio / datos) + **MVVM** + **Repositorio**.
- **Offline-first con Outbox**: todo reporte se guarda local primero y luego se envía.
- **Strategy** para canales de envío (Internet, Nearby, SMS) y para modelos de amenaza (sismo, inundación, huracán).
- **Chain of Responsibility** para elegir canal: internet → Nearby → ofrecer SMS.
- **Adapter** para fuentes de alerta (CAP, sismológica, manual, comunitaria).
- **Pipeline** en el motor: validar → deduplicar → ponderar → actualizar celda → recalcular prioridades → recomendar.
- **Pub/Sub interno** en el servidor ("reporte_recibido", "bloqueo_cambiado", etc.).
- **Observer**: Kotlin Flow en la app, WebSocket en la web.
- **Gossip** (propagación epidémica) en la red Nearby.
- **Facade** en la API.

## 6. Estructura del repositorio

```
enlace/
├── CLAUDE.md
├── servidor/
│   ├── app/
│   │   ├── main.py
│   │   ├── api/            # rutas REST y WebSocket
│   │   ├── modelos/        # esquemas Pydantic y tablas
│   │   ├── motor/
│   │   │   ├── impacto.py
│   │   │   ├── amenazas/   # sismo.py, inundacion.py (Strategy)
│   │   │   ├── fusion.py
│   │   │   ├── prioridad.py
│   │   │   └── asignacion.py
│   │   ├── activacion/
│   │   │   ├── fuentes/    # fuente_cap.py, fuente_sismologica.py, fuente_manual.py, fuente_comunitaria.py
│   │   │   ├── firma.py
│   │   │   └── emisor.py
│   │   ├── rutas/          # grafo de calles, bloqueos, cálculo de rutas
│   │   ├── privacidad/     # borrado programado, agregación anónima
│   │   ├── paquete/        # PDF del paquete de primera hora
│   │   └── eventos/        # pub/sub interno
│   ├── datos/              # ciudad preparada, evento de prueba, semillas
│   └── tests/
├── web/                    # React + Vite
├── movil/                  # App Android ENLACE (Kotlin)
│   └── app/src/main/java/.../
│       ├── presentacion/   # consentimiento/, usuario/, unidad/
│       ├── dominio/
│       ├── datos/          # local/, remoto/, nearby/, canales/
│       └── ubicacion/
├── receptor-sms/
└── preparacion/            # scripts: descargar_osm.py, calcular_poblacion.py,
                            # asignar_vulnerabilidad.py, validar_evento.py
```

## 7. Modelo de datos

- **Evento**: id, tipo (sismo|inundacion|huracan|otro), magnitud, lat, lon, zona_afectada (lista de h3),
  fuente (cap|sismologica|manual|comunitaria), hora, estado (activo|cerrado), fecha_cierre.
- **Celda**: id_h3, nombre_zona, poblacion_por_hora (JSON 24 valores), vulnerabilidad (0–1),
  infraestructura_critica (JSON), impacto_estimado, certeza (0–1), prioridad (0–100),
  categoria (actuar|explorar|estable|explorar_despues).
- **Reporte**: id_mensaje (UUID único), id_anonimo, id_h3, origen (app|sms|nearby|radio|brigada),
  tipo (bien|ayuda|evaluacion), personas, atrapados, heridos, vulnerables, necesidades (lista),
  nivel_dano (solo brigada), confiabilidad, saltos_nearby, hora, estado (sin_asignar|asignado|atendido|descartado),
  id_reporte_padre (si fue fusionado como duplicado).
- **CheckIn**: id_anonimo, id_h3, id_evento, hora, estado (sin_respuesta|bien|ayuda), personas, vulnerables.
- **UbicacionAlerta**: id_anonimo, id_evento, lat, lon, hora, fecha_borrado. SOLO si la persona eligió
  compartir su ubicación exacta durante emergencias.
- **Identidad**: id_anonimo, nombre, telefono, lat, lon, condiciones, id_evento, fecha_borrado.
  SOLO existe si la persona pidió ayuda.
- **Equipo**: id, nombre, organismo, capacidades (rescate|medico|agua|reconocimiento|maquinaria),
  lat, lon, estado (disponible|en_ruta|ocupado).
- **Asignacion**: id, id_equipo, id_reporte, id_h3, motivo (texto explicativo), tiempo_estimado_min,
  ruta (GeoJSON), decision (pendiente|aceptada|modificada|rechazada), decidido_por, hora.
- **Bloqueo**: id, lat, lon, tipo (derrumbe|agua|escombros|cables|via_rota|otro), alcance (total|solo_a_pie),
  confirmaciones, estado (sin_confirmar|probable|confirmado|descartado).
- **Usuario admin**: id, nombre, organismo, nivel (coordinador|operador|brigada|admin_organismo).

## 8. API del servidor

| Método | Ruta | Función |
|---|---|---|
| POST | /eventos | Emitir alerta (manual o desde fuente) |
| POST | /eventos/{id}/cerrar | Cerrar evento e iniciar plazo de borrado |
| GET | /celdas | Celdas con prioridad, categoría y totales de check-in (incluye población crítica) |
| GET | /ubicaciones | Puntos exactos consentidos del evento activo (solo brigada y admin) |
| GET | /celdas/{id_h3} | Detalle de celda |
| POST | /reportes | Uno o varios reportes (lotes para mulas de datos) |
| GET | /reportes | Bandeja ordenada por prioridad, con filtros |
| POST | /checkins | Ubicación anónima al activarse el evento |
| POST | /sms/entrante | Reportes desde el receptor de SMS |
| POST | /bloqueos | Reportar, confirmar o descartar bloqueo |
| GET | /bloqueos | Bloqueos activos |
| GET | /recomendaciones | Próximas decisiones con explicación |
| POST | /asignaciones/{id}/decision | Aceptar, modificar o rechazar |
| GET | /equipos · POST /equipos | Consulta y registro de equipos |
| GET | /rutas?origen=..&destino=.. | Ruta más rápida evitando bloqueos |
| GET | /paquete/{escenario} | PDF del paquete de primera hora |
| WS | /ws/admin | Actualizaciones en tiempo real para la web |
| WS | /ws/movil/{id} | Alertas firmadas, asignaciones y rutas para la app |

## 9. Motor de decisión

1. **Impacto inicial**: por celda, `personas_afectadas = Σ ocupantes(hora) × P(daño grave | intensidad, tipo_construcción)`.
   Intensidad desde ShakeMap del evento de prueba (o atenuación por magnitud y distancia).
2. **Fusión de evidencia**: cada reporte ajusta la celda según su confiabilidad:
   brigada = alta (1.0), varios coincidentes = media-alta (0.7), app/SMS único = media (0.5), radio sin verificar = media-baja (0.3).
   La certeza crece con la suma de pesos (p. ej. `certeza = 1 − exp(−Σpesos)`).
3. **Calibración con check-ins**: si la proporción de "sin respuesta" o "ayuda" en una celda es alta, se ajusta el impacto de toda la celda.
4. **Clasificación**: impacto alto + certeza alta = actuar; impacto alto + certeza baja = explorar;
   impacto bajo + certeza alta = estable; impacto bajo + certeza baja = explorar_despues. Umbrales configurables.
5. **Prioridad (0–100)**: combina impacto, urgencia (aumenta con las horas), vulnerabilidad especial y tiempo sin atención.
6. **Asignación**: recorrer celdas/reportes por prioridad; elegir el equipo disponible con la capacidad requerida
   (atrapados → rescate; heridos → médico; explorar → reconocimiento) y menor tiempo de llegada. Guardar `motivo` legible.
7. **La decisión final siempre es humana.** El motor solo recomienda.

## 10. Rutas y bloqueos

- Grafo de calles de OSM ponderado por **tiempo de viaje** (no distancia).
- sin_confirmar → penalizar la arista (×3); probable → penalizar fuerte (×10); confirmado → eliminar arista;
  solo_a_pie → eliminar para vehículos; descartado → restaurar.
- Al cambiar un bloqueo, recalcular solo las rutas activas que lo atraviesan y notificar a esas unidades.
- La ruta se entrega como GeoJSON + lista de instrucciones paso a paso.

## 11. Activación (Alerta ENLACE)

- Solo el servidor emite alertas, y solo con fuente oficial (CAP, sismológica) o confirmación humana
  (manual o aprobación de propuesta comunitaria).
- Propuesta comunitaria: ≥4 pedidos de ayuda en la misma celda en 10 min sin alerta activa → proponer al operador.
- Cada alerta va **firmada (Ed25519)**. La app descarta alertas sin firma válida.
- Entrega: WebSocket local (demo) / push (producción), propagación Nearby, mulas de datos.
- **NO implementar detección de sacudida por acelerómetro** (fuera del alcance de la demo).

## 12. Red Nearby (app)

- Estrategia P2P_CLUSTER. Mensaje = JSON compacto con id_mensaje, saltos, hora, tipo, payload.
- Al recibir: si id_mensaje ya existe → ignorar. Si no → guardar en Outbox y retransmitir con saltos+1.
- Límite de 10 saltos. Mensajes tipo: reporte, checkin, bloqueo, alerta (firmada).
- Modo unidad = **mula de datos**: acepta todos los mensajes y los envía en lote a POST /reportes al tener conexión.

## 13. Privacidad (reglas obligatorias)

- Consentimiento en pantalla propia (no en términos y condiciones): ubicación exacta | automático |
  con confirmación | no compartir.
- **Nunca rastreo continuo.** La ubicación se pide (última conocida) solo al recibir una alerta válida
  (y una vez más si la persona avisa "Estoy bien").
- El check-in guarda la celda h3 y conteos anónimos del hogar (personas, vulnerables), nunca nombre ni teléfono.
- **Ubicación exacta durante emergencias:** solo si la persona eligió esa opción. El punto va en una tabla
  aparte (`UbicacionAlerta`), lo ven solo brigadas y administradores, y se borra al cerrar el evento
  (`dias_borrado_ubicaciones`, por defecto 0). Si la persona cambia de opción, su punto se borra.
- Perfil del usuario guardado solo en el celular. Identidad (nombre, teléfono, condiciones) se envía **solo** al pedir ayuda,
  y del servidor solo llega al centro de coordinación y a la unidad asignada a ese pedido (para encontrar y llamar a la persona).
- La web muestra totales por celda; puntos exactos solo para pedidos de ayuda y para quienes eligieron compartirlos.
- Al cerrar el evento, los datos personales (Identidad) se borran tras un plazo configurable (por defecto 30 días).
  Los agregados anónimos se conservan.
- La app ciudadana NO usa permiso SEND_SMS: abre la app de mensajes con el texto prellenado (ACTION_SENDTO).

## 14. Formato SMS

`ENL <lat>,<lon> P<personas> A<atrapados> H<heridos> <necesidades>`
Necesidades: R=rescate, M=médico, W=agua, F=refugio. Ejemplo: `ENL 13.10,-85.20 P4 A1 H1 RM`

## 15. Roles e interfaces

**Roles:** usuario (ciudadano) y administrador, con niveles coordinador, operador, brigada (app modo unidad)
y administrador de organismo.

**Interfaces a CONSTRUIR para la demo (23):**
- Usuario: U2 consentimiento, U5 inicio, U6 mapa, U8 alerta activa, U10 reporte rápido, U11 confirmación de envío,
  U12 seguimiento del pedido, U13 reportar bloqueo (ubicación), U14 reportar bloqueo (tipo), U18 perfil.
- Unidad: B2 inicio, B3 notificación de asignación (pantalla completa), B5 navegación guiada, B6 ruta recalculada,
  B8 evaluación de zona, B9 zona atendida.
- Admin web: A2 centro de reportes, A3 detalle del reporte, A4 tablero con mapa por capas, A5 detalle de celda,
  A6 emitir alerta, A9 reporte manual, A12 equipos.

**Interfaces a SIMULAR (21) o MENCIONAR (14):** el resto (bienvenida, perfil inicial, confirmar bloqueos,
mis reportes, mula de datos, propuestas comunitarias, seguimiento de asignaciones, paquetes, cierre de evento,
permisos, bitácora, configuración, receptor SMS, etc.). No implementarlas hasta terminar las 23 principales.

## 16. Sistema visual

- Colores con significado fijo: **rojo** = actuar / pedir ayuda; **ámbar** = explorar / sin confirmar;
  **verde** = estable / a salvo; **gris** = sin datos; **azul** = acción principal, brigadas, rutas.
- Siempre acompañar el color con ícono o texto (daltonismo).
- App usuario: botones críticos grandes (mín. 48 dp de alto; en alerta activa, botones de ancho completo),
  texto mínimo 14 sp, modo oscuro por defecto durante una alerta.
- Indicador de conexión siempre visible: internet / red cercana (n celulares) / sin conexión.
- Textos en español, en oración (sentence case), cortos, sin tecnicismos. Preparar para portugués (strings externos).
- Web: centro de reportes es la vista inicial del admin; bandeja ordenada por prioridad; panel de detalle con
  equipo sugerido destacado y botón "Asignar" como acción principal.

## 17. Flujo de la demostración

1. Admin emite alerta (A6) → la app recibe alerta firmada (U8) → check-ins llenan el mapa (A4).
2. Usuario toca "Necesito ayuda" (U10) en modo avión → el reporte viaja por Nearby → una unidad (mula) lo entrega.
3. Reporte aparece en el centro de reportes (A2) con prioridad → admin asigna (A3).
4. Unidad recibe notificación (B3) → navega (B5) → un usuario reporta bloqueo (U13–U14) → ruta recalculada (B6).
5. Usuario ve "Ayuda en camino" (U12) → unidad evalúa (B8) y marca atendido (B9) → prioridades se recalculan.
6. Paralelamente: validación retrospectiva del modelo con un sismo real (script en preparacion/validar_evento.py).

**Infraestructura:** 1 laptop (servidor + web), 1 router Wi-Fi sin internet, 3–4 celulares Android, proyector.

## 18. Orden de construcción

1. Servidor: modelos, API, datos semilla (30–40 reportes, 300 check-ins ficticios, 8 equipos), motor, WebSocket.
2. Rutas y bloqueos en el servidor.
3. Web: centro de reportes → detalle → tablero con mapa → emitir alerta → equipos → reporte manual.
4. App Android: consentimiento → alerta → reporte → Outbox → canales → Nearby → modo unidad → navegación.
5. Receptor SMS.
6. Scripts de preparación de ciudad y validación retrospectiva.

**Regla:** no avanzar a un paso hasta que el anterior funcione de punta a punta.

## 19. Convenciones

- Código, nombres de variables, tablas y endpoints en **español** (consistente con este documento), sin tildes en identificadores.
- Nombre del producto en la interfaz: **ENLACE**. Paquete Android sugerido: `com.enlace.app`.
- Python: tipado con type hints, Pydantic para esquemas, formateo con black, pruebas con pytest.
- Kotlin: convenciones oficiales, Compose sin XML, un ViewModel por pantalla.
- Web: componentes funcionales con hooks, un componente por archivo.
- Configuración (umbrales, pesos, plazos, URLs) en archivos de configuración, nunca valores mágicos en el código.
- Cada módulo con un README breve.

## 20. Instrucciones para Claude Code

- Trabaja en pasos pequeños y verificables. Al terminar cada paso, ejecútalo o pruébalo y muestra el resultado.
- Antes de cambios grandes de estructura, explica el plan y espera confirmación.
- Escribe pruebas para el motor de decisión (fusión, clasificación, prioridad, asignación) y para rutas con bloqueos.
- Si algo del documento es ambiguo o técnicamente inviable, dilo y propone una alternativa en lugar de improvisar.
- Respeta siempre las reglas de privacidad de la sección 13.
- Prioriza que la demo funcione sin internet.