# Guion de la demostración de ENLACE

Rally Latinoamericano de Innovación · Reto 09. Duración: **8 a 10 minutos** de demo en vivo, más
preguntas. Sigue el flujo de la sección 17 del CLAUDE.md.

> Frase guía: *«Decidir cuando todavía no se ve nada, y corregir con cada dato que llega.»*

## Equipo y roles

| Rol | Quién | Dispositivo | Qué muestra |
|---|---|---|---|
| Presentador | — | — | Cuenta la historia y señala la pantalla |
| Operador | — | Laptop + proyector | Centro de coordinación (web) |
| Ciudadana A | — | Celular 1 (modo comunidad) | Alerta, "Estoy bien", ubicación exacta |
| Ciudadano B | — | Celular 2 (modo comunidad, **modo avión** con Bluetooth y Wi-Fi activos) | Pedido de ayuda sin internet |
| Brigada | — | Celular 3 (modo unidad, por ejemplo Cruz Roja Unidad 2) | Asignación, navegación, evaluación |
| (Opcional) Receptor SMS | — | Celular con chip | Pedido por SMS |

## Antes de la demo

**El día anterior**
- [ ] Laptop, router y celulares cargados; cargadores y cable USB a mano.
- [ ] En `servidor/config/enlace.toml`, `[sms] numero_receptor` con el número real del receptor.
- [ ] Los datos de la ciudad ya preparados (mapa, calles, población, vulnerabilidad: ver `preparacion/`).
- [ ] La web compilada: `npm run build` en `web/`.
- [ ] En cada celular (Xiaomi, Honor, Oppo, Vivo): **Inicio automático** para ENLACE activado.
- [ ] En el celular de la brigada: **avisos a pantalla completa** permitidos (el aviso naranja de su inicio lleva al ajuste).
- [ ] No reinstalar la app después de esto: reinstalar quita el permiso de pantalla completa.

**Una hora antes** (en `servidor/`)
1. Router propio sin internet, y la laptop conectada a él con IP fija.
2. Con el servidor detenido: `python datos/ensayo_demo.py preparar`. Borra la base y carga la ciudad real.
3. Iniciar el servidor: `uvicorn app.main:app --host 0.0.0.0 --port 8000`.
4. `python datos/ensayo_demo.py verificar`. Todo tiene que salir con ✓. Usar la dirección de la red
   del router (no la de adaptadores virtuales como `192.168.56.x`).
5. En cada celular, en **Conexión**, escribir esa dirección (por ejemplo `192.168.0.10:8000`) y
   comprobar "Conectado a ENLACE". Con eso cada celular descarga el mapa de la ciudad.
6. Celular 3 en modo unidad, con la clave de brigada y su equipo.
7. Activar **No molestar** en los celulares: que no aparezcan notificaciones personales en la proyección.

**Diez minutos antes**
- [ ] Web abierta en **Centro de reportes** con la sesión iniciada y el proyector funcionando.
- [ ] En otra terminal: `python datos/ensayo_demo.py fondo`. Queda esperando la alerta.
- [ ] El Celular 2 en modo avión, con Bluetooth y Wi-Fi encendidos, junto al Celular 3.
- [ ] Todos los celulares en el inicio de ENLACE, con "En línea" (salvo el 2: "Red cercana").

## La demostración, paso a paso

| Min | Quién | Qué se hace | Qué se dice | Interfaces |
|---|---|---|---|---|
| 0:00 | Presentador | — | El problema: tras un desastre no hay datos y hay que decidir ya. ENLACE es un cerebro (motor) y un sistema nervioso (captura multicanal). | — |
| 0:45 | Operador | **Emitir alerta**: sismo M6.2 en el epicentro, confirmar. | Solo el servidor emite, y siempre con confirmación humana. Sale **firmada**: un celular descarta cualquier alerta falsa. | A6 |
| 1:15 | Ciudadana A | La alerta llega al celular (verificada) y se envía su ubicación. | Llega sin internet, por la red local. La persona eligió compartir su ubicación exacta: se borra al cerrar la emergencia. | U8 |
| 1:30 | Operador | **Mapa**: zonas por prioridad, población crítica, personas ubicadas. (`fondo` está llenando la ciudad.) | Desde el primer minuto, sin un solo reporte, el modelo estima el impacto por zona con población real y daño esperado. Cada check-in lo corrige. | A4, A5 |
| 2:30 | Ciudadana A | Toca **Estoy bien**. Su punto pasa a verde en el mapa. | El mapa se corrige en vivo con cada dato. | U8 |
| 3:00 | Ciudadano B | En modo avión: **Necesito ayuda**, 2 atrapadas, Rescate. "Enviado a 1 celular cercano". | Sin internet ni señal: el pedido viaja de celular en celular. | U10, U11 |
| 3:30 | Brigada | La unidad, que hace de **mula de datos**, entrega el pedido. | Cualquier celular con ENLACE lo lleva hasta alguien con conexión. | B10 |
| 4:00 | Operador | **Centro de reportes**: el pedido aparece arriba por prioridad. Detalle: equipo sugerido y el **porqué**. **Asignar**. | El motor recomienda y explica; la decisión final siempre es humana. | A2, A3 |
| 4:45 | Brigada | Se abre la asignación a pantalla completa (aunque esté bloqueado). **Ir ahora**. | Qué atender, a quién llamar, y la ruta más rápida evitando bloqueos. | B3, B5 |
| 5:30 | Ciudadana A | **Reportar calle bloqueada** sobre la ruta: derrumbe. | Cualquier persona puede avisar de una calle cortada. | U13, U14 |
| 5:50 | Brigada | Aviso **Ruta recalculada**. | Solo se recalculan las rutas que pasan por ese bloqueo. | B6 |
| 6:15 | Ciudadano B | **Mis reportes**: "Ayuda en camino · Cruz Roja Unidad 2 llega en unos 6 min". | La persona sabe que la ayuda viene. | U12 |
| 6:45 | Brigada | **Evaluar la zona** (grave, 3 personas) y **Marcar zona atendida**. | La evaluación de una brigada es la evidencia más confiable (peso 1,0). | B8, B9 |
| 7:15 | Operador | **Mapa**: la zona cambia de prioridad. **Primera hora**: la hoja para los organismos. | Las prioridades se recalculan con cada dato. | A4, A11 |
| 7:45 | Presentador | Mostrar la validación (ver abajo). | No es una maqueta: el modelo se validó con sismos reales. | — |
| 8:30 | Presentador | Cierre: viable en toda Latinoamérica, sin internet, con hardware común. | — | — |

**Plan B, si algo falla en vivo**
- **La red cercana no conecta:** el Ciudadano B ofrece **Enviar por SMS** (U11). Si no hay receptor, el Operador carga el pedido en **Reporte manual** (A9), como si llegara por radio.
- **La asignación no se abre sola:** en el celular de la brigada, tocar la tarjeta "Asignación en curso".
- **El proyector no muestra el celular:** contar el paso y mostrar el resultado en la web.

## Interfaces que se mencionan (no se muestran)

Se nombran en una frase, cuando el paso lo pide:

| Código | Interfaz | Cuándo mencionarla |
|---|---|---|
| U19 | Privacidad: borrar mis datos | Al hablar de la ubicación exacta (min 1:15) |
| U21 | Estado de la red cercana | En el pedido sin internet (3:00) |
| U22 | Idioma portugués | En el cierre: "preparada para Brasil" |
| B13 | Bitácora del equipo | En la evaluación (6:45) |
| A14 | Bitácora de decisiones | Al asignar: "queda registrado quién decidió" |
| A15 | Configuración de umbrales y pesos | Al explicar el porqué de la prioridad |
| A16 | Usuarios y niveles de acceso | Al ingresar: coordinador, operador, brigada |
| A17 | Estado del receptor SMS | En el plan B o en el cierre |
| A18 | Fuentes de alerta (CAP, sismológica) | Al emitir: "también puede llegar del sismológico nacional" |
| A19 | Privacidad y borrado programado | En el cierre del evento |
| A20 | Exportar datos anónimos | En el cierre: "para aprender de cada emergencia" |
| A21 | Reportes por radio | En el plan B |
| A22 | Validación del modelo | En el minuto 7:45 |
| A23 | Preparar una ciudad nueva | En el cierre: "cualquier ciudad en minutos" |

## Datos para responder al jurado

| Criterio | Qué mostrar o decir |
|---|---|
| Innovación (30) | Estimación desde el minuto 0 que se corrige con evidencia ponderada; alertas firmadas; red celular a celular sin internet; mula de datos. |
| Impacto social (20) | Prioriza a quien no puede pedir ayuda (población vulnerable sin responder); explica cada decisión; privacidad por diseño (borrado al cerrar). |
| Prefactibilidad (20) | Una laptop, un router y celulares Android comunes. Datos abiertos (OpenStreetMap, Kontur). Sin servidores en la nube ni licencias. |
| Viabilidad técnica (20) | Validación con sismos reales: **210 observaciones** (Costa Rica 2012, México 2017), **72 % dentro de ±1 grado** de intensidad ([informe](preparacion/resultados/validacion_sismos.md)). Más de 250 pruebas automáticas. |
| Presentación (10) | Este guion; una sola historia de punta a punta. |

Limitación que conviene decir antes de que la pregunten: cerca del epicentro el modelo
**sobreestima** (Managua 2014). Por eso la estimación inicial arranca con certeza baja y las zonas
quedan en "explorar" hasta que llega evidencia.

## Al terminar

1. Operador: **Emitir alerta → Cerrar evento**. Empieza el plazo de borrado de los datos personales y las ubicaciones exactas se borran en ese momento.
2. Desactivar **No molestar** en los celulares.
3. Para otra pasada: detener el servidor y repetir desde «Una hora antes», paso 2.
