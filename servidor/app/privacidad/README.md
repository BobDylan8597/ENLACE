# Privacidad

Reglas obligatorias de la sección 13 que dependen del tiempo:

- Al cerrar un evento (`POST /eventos/{id}/cerrar`), cada `Identidad` del evento recibe
  `fecha_borrado = cierre + dias_borrado` (30 días por defecto, en `config/enlace.toml`).
- Una `Identidad` creada sin evento activo recibe un plazo provisional desde su creación; si después
  se emite una alerta, pasa a ser parte de ese evento.
- Los puntos exactos compartidos al llegar la alerta (`UbicacionAlerta`, solo de quienes eligieron
  "ubicación exacta durante emergencias") se borran al cerrar el evento, o tras
  `dias_borrado_ubicaciones` si es mayor que 0. Un check-in sin punto borra el anterior.
  Ver `ubicaciones.py`.
- Un trabajo de APScheduler borra las identidades y los puntos vencidos cada `intervalo_revision_min`.
- Los datos anónimos (celdas, reportes, check-ins por celda) se conservan.
