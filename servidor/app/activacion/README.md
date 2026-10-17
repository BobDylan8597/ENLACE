# Activación (Alerta ENLACE)

Solo el servidor emite alertas, y solo con fuente oficial o confirmación humana.

- `fuentes/` (patrón Adapter): cada fuente convierte su formato en un `NuevoEvento`.
  - `fuente_manual.py`: el operador emite desde la web (A6).
  - `fuente_cap.py`: Common Alerting Protocol 1.2; solo acepta `status` Actual y `msgType` Alert.
  - `fuente_sismologica.py`: sismo en GeoJSON (formato USGS y de muchas redes nacionales).
  - `fuente_comunitaria.py`: ≥4 personas distintas pidiendo ayuda en la misma celda en 10 min
    sin alerta activa genera una **propuesta**; se emite solo si un operador la aprueba.
- `emisor.py`: crea el evento, suma los pedidos que llegaron antes de la alerta, estima el impacto
  inicial de todas las celdas y calcula la zona afectada.
- `firma.py`: firma Ed25519.

## Formato de una alerta firmada

```json
{"contenido": "{\"tipo_mensaje\":\"alerta\",\"id_evento\":1,...}", "firma": "<base64>", "algoritmo": "Ed25519"}
```

Se firma el **texto exacto** de `contenido`. La app verifica la firma sobre esos bytes (UTF-8) con
la clave pública de `GET /alertas/clave-publica` (32 bytes en base64) y solo después interpreta el
JSON. Así no hace falta reproducir ningún formato canónico en Kotlin. Una alerta sin firma válida
se descarta.

Las claves se generan en el primer arranque en `servidor/claves/`, que nunca se sube al repositorio.
