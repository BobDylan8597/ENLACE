# Receptor de SMS (paso 5)

App Android mínima (`com.enlace.receptor`) para el celular que recibe los pedidos de ayuda por
SMS. Sirve cuando no hay datos móviles ni red cercana: la persona envía desde la app ENLACE un SMS
ya escrito (formato de la sección 14) a este número.

```
ENL <lat>,<lon> P<personas> A<atrapados> H<heridos> <RMWF>      ej.: ENL 13.10,-85.20 P4 A1 H1 RM
```

## Qué hace

1. Recibe cada SMS (permiso `RECEIVE_SMS`, nunca envía SMS) y se queda **solo con los que
   empiezan con `ENL`**. Los demás mensajes no se guardan ni salen del celular.
2. Lo guarda en una cola en el celular (Outbox) y lo reenvía a `POST /sms/entrante` con la
   **clave de brigada**; si el servidor no responde, WorkManager reintenta solo.
3. Respuesta 401 (clave incorrecta): el SMS sigue pendiente hasta corregir la clave. 422 (formato
   que el servidor no entiende): queda como rechazado.
4. En pantalla el número de quien envió se ve oculto (`•••1234`); el número completo solo lo ve el
   centro de coordinación, en el detalle del pedido.

## Uso en la demo

1. Instalar en el celular con chip: `./gradlew installDebug` (en Xiaomi hay que aceptar la
   instalación en la pantalla del celular).
2. Abrir, dar el permiso de SMS, escribir la dirección del servidor y la clave de brigada, y tocar
   **Guardar y probar conexión**.
3. Poner el número de este celular en `servidor/config/enlace.toml` → `[sms] numero_receptor`: los
   celulares lo descargan al configurarse y lo ofrecen cuando no hay otra conexión.

La versión de desarrollo tiene **Simular un SMS de prueba**, que recorre el mismo camino que un SMS
real, para probar sin enviar mensajes.

```bash
cd receptor-sms
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew testDebugUnitTest assembleDebug
```

En producción: el número sería un código corto del operador o una pasarela SMS, y la app no podría
publicarse en Play Store sin una excepción de permisos de SMS (aquí se instala directamente).
