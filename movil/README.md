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
| `datos/nearby/`, `datos/canales/` | Red cercana y canales de envío (pendientes) |
| `presentacion/` | Pantallas Compose, una por archivo, con su ViewModel |
| `ubicacion/` | Ubicación, solo al recibir una alerta válida (pendiente) |
| `di/` | Módulos de Hilt |

Las versiones están en `gradle/libs.versions.toml`.
