# Privacidad

Reglas obligatorias de la sección 13 que dependen del tiempo:

- Al cerrar un evento (`POST /eventos/{id}/cerrar`), cada `Identidad` del evento recibe
  `fecha_borrado = cierre + dias_borrado` (30 días por defecto, en `config/enlace.toml`).
- Una `Identidad` creada sin evento activo recibe un plazo provisional desde su creación; si después
  se emite una alerta, pasa a ser parte de ese evento.
- Un trabajo de APScheduler borra las identidades vencidas cada `intervalo_revision_min`.
- Los datos anónimos (celdas, reportes, check-ins por celda) se conservan.
