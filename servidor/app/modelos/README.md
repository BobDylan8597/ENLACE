# Modelos

- `enums.py`: valores cerrados (tipos, estados, orígenes, capacidades…).
- `tablas.py`: tablas SQLite de la sección 7, más los campos agregados que lista el README del servidor.
  Todas las fechas se guardan en UTC; una fecha sin zona horaria se rechaza.
- `esquemas.py`: entrada y salida de la API (Pydantic). Las reglas de privacidad se aplican en
  los propios esquemas (ver `app/api/README.md`).

`CheckIn` y `Reporte` no tienen coordenadas: solo la celda h3. Las coordenadas exactas y los datos
personales viven únicamente en `Identidad`.
