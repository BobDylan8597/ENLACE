# Eventos internos (pub/sub)

`bus.py`: los módulos publican temas (`reporte_recibido`, `celda_actualizada`,
`asignacion_cambiada`…) sin conocer a quién le interesan. `app/api/tiempo_real.py` se suscribe y
los reenvía por WebSocket.

`publicar()` funciona desde las rutas síncronas, que FastAPI ejecuta en otro hilo: las corrutinas
se entregan al bucle principal registrado con `vincular_bucle`. Si un manejador falla, el error
queda registrado y los demás manejadores reciben el mensaje igual.
