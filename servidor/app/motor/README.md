# Motor de decisión

Estima el impacto desde el primer minuto y lo corrige con cada dato que llega.
**Solo recomienda: la decisión final siempre es humana.**

Los módulos son funciones puras (sin base de datos); `pipeline.py` los conecta con SQLite:
validar → deduplicar → ponderar → actualizar celda → recalcular prioridades → recomendar.

| Archivo | Qué calcula |
|---|---|
| `amenazas/sismo.py` | Intensidad MMI = a + b·M − c·log10(R) (Bakun y Wentworth, 1997) y P(daño grave) = Φ((MMI − mediana)/dispersión) por tipo de construcción |
| `amenazas/inundacion.py` | Pendiente (Strategy: misma interfaz que el sismo) |
| `impacto.py` | Ocupantes de la celda a la hora local del evento |
| `fusion.py` | Duplicados, peso de cada fuente y estimación con evidencia |
| `prioridad.py` | Categoría (actuar, explorar, estable, explorar_despues) y prioridad 0–100 |
| `asignacion.py` | Equipo disponible más rápido con la capacidad necesaria, y su motivo |

## Fusión de evidencia

Cada evidencia aporta una señal (fracción de ocupantes gravemente afectados) y un peso:

    certeza          = 1 − exp(−Σ pesos)
    fraccion_final   = (1 − certeza) · fraccion_modelo + certeza · promedio_ponderado(señales)
    impacto_estimado = max(ocupantes · fraccion_final, atrapados + heridos confirmados)

- Brigada: peso 1.0, señal medida según el nivel de daño observado.
- App, SMS o Nearby: peso 0.5 (0.7 si otra persona reporta lo mismo); "ayuda" sube la señal, "bien" la baja.
- Radio sin verificar: peso 0.3.
- Check-ins: tasa de "ayuda" y "sin respuesta" de la celda, con peso según cuántos llegaron.

Todos los valores están en `config/enlace.toml`. Las curvas de fragilidad son **ilustrativas**:
deben calibrarse con estudios de vulnerabilidad de cada país.

## Tiempo de llegada

En el paso 1 se calcula en línea recta × factor de desvío ÷ velocidad (`EstimadorLineaRecta`).
El paso 2 lo reemplaza por rutas reales sobre el grafo de calles, con la misma interfaz.
