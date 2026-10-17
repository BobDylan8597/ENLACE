# App Android ENLACE

Un solo APK (`com.enlace.app`) con dos modos: **usuario** (ciudadano) y **unidad** (brigada).
Kotlin, Jetpack Compose, Clean Architecture (presentacion / dominio / datos) + MVVM, Hilt.
minSdk 26 (Android 8), compileSdk 37, targetSdk 36.

## Compilar e instalar

Requiere el JDK que trae Android Studio y el SDK en `%LOCALAPPDATA%\Android\Sdk`
(`local.properties` apunta ahí y no se sube al repositorio).

```bash
cd movil
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # Git Bash en Windows
./gradlew testDebugUnitTest      # pruebas
./gradlew installDebug           # instala en el celular conectado por USB
```

En el celular: activar **Opciones de desarrollador → Depuración USB** y aceptar el aviso de la
laptop al conectarlo.

## Conectar con el servidor

El servidor debe escuchar en la red (`uvicorn app.main:app --host 0.0.0.0 --port 8000`) y el
firewall de Windows debe permitir el puerto 8000. En la app se escribe la IP de la laptop en la red
Wi-Fi (por ejemplo `192.168.1.10`; el puerto 8000 se agrega solo). La demo usa `http` en la red
local (`res/xml/seguridad_red.xml`); en producción, solo `https`.

## Organización (CLAUDE.md, sección 6)

| Carpeta | Qué contiene |
|---|---|
| `dominio/` | Reglas puras, sin Android: probadas con JUnit |
| `datos/remoto/` | API del servidor (Retrofit) |
| `datos/local/` | Datos que solo viven en el celular |
| `datos/nearby/` | Red cercana (Nearby Connections, gossip) |
| `presentacion/` | Pantallas Compose, una por archivo, con su ViewModel |
| `presentacion/unidad/` | Modo unidad: activar (clave de brigada y equipo), B2 inicio, B3 asignación, B8 evaluación, B9 zona atendida |
| `ubicacion/` | Ubicación: una sola consulta al recibir una alerta válida o al pedir ayuda |
| `di/` | Módulos de Hilt |

## Modo unidad (brigada)

- Se activa desde el inicio ("Soy de un equipo de respuesta") con la **clave de brigada**, distinta
  de la del administrador, y eligiendo el equipo.
- El servicio en primer plano se conecta a `/ws/movil/equipo-<n>?clave=…` y recibe la asignación
  (B3, a pantalla completa aunque el celular esté bloqueado), las rutas recalculadas y, al
  reconectarse, la asignación en curso o el aviso de que no tiene ninguna.
- B2 muestra lo más urgente cerca: zonas con población crítica, personas que no confirmaron que
  están bien y pedidos pendientes. El mapa con estas capas llega en el paso 4.8.
- **Mula de datos:** lo que llega por la red cercana entra al Outbox y se entrega en lote apenas
  hay conexión; B2 muestra cuántos mensajes lleva.

Las versiones están en `gradle/libs.versions.toml`.
