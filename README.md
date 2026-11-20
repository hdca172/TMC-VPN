# TMC VPN V4.5

Aplicación Android con soporte de túneles WireGuard mediante perfiles autorizados. Incluye selección de proveedores/perfiles, favoritos, diagnóstico de red, estadísticas, recuperación y registro local de conexiones.

## Descargar APK de prueba

**[📦 Ver compilaciones y descargar TMC VPN V4.5](https://github.com/hdca172/TMC-VPN/actions/workflows/android-build.yml?query=branch%3Amain)**

1. Abre el enlace de compilaciones e inicia sesión en GitHub si es necesario.
2. Selecciona la ejecución más reciente de la rama **main** que tenga un indicador verde (**Success**).
3. En la sección **Artifacts**, pulsa **TMC-VPN-V4.5-debug** para descargar el archivo ZIP.
4. Extrae el ZIP y abre **app-debug.apk** en tu Android.
5. Si Android lo solicita, autoriza la instalación desde la aplicación que utilizas para abrir el APK y desactiva ese permiso después.

> **Importante:** es un APK **debug**, para pruebas. No es un APK de producción firmado con una clave de publicación. Los artefactos de GitHub Actions se conservan durante **30 días**. La descarga de artefactos puede requerir iniciar sesión en GitHub. Comprueba siempre que el archivo procede de este repositorio y de una ejecución exitosa de **main**.

**[📋 Historial de ejecuciones de main](https://github.com/hdca172/TMC-VPN/actions?query=branch%3Amain)** · **[🔧 Código fuente](https://github.com/hdca172/TMC-VPN)**

## Requisitos

- Android 8.0 o superior.
- Un perfil WireGuard `.conf` válido para un servidor bajo tu control o autorizado.
- Conexión a Internet existente; una VPN no proporciona datos móviles gratuitos.

## Uso

1. Importa un perfil WireGuard autorizado.
2. En **Inicio**, elige el proveedor/servidor importado.
3. Concede el permiso VPN de Android y pulsa **Conectar WireGuard**.
4. Comprueba la navegación y el DNS. Desconecta antes de cambiar de perfil.

WireGuard autentica mediante claves, no con usuario y contraseña. No se admiten otros protocolos sin implementar y verificar un motor específico.

## Compilación

El workflow **TMC VPN - Android APK CI** ejecuta `gradle :app:assembleDebug --no-daemon --stacktrace` con JDK 17 y publica el artefacto **TMC-VPN-V4.5-debug**.

Compilación local: `gradle :app:assembleDebug --no-daemon` con JDK 17 y Android SDK instalado.

## Estado de publicación y seguridad

La versión Android declarada es **4.5.0** (`versionCode 14`). V4.5 está integrada en `main` mediante el PR #17. La distribución se mantiene como **APK de prueba** mediante GitHub Actions; no se ha creado un release de producción.

No publiques perfiles ni claves privadas en el repositorio. La validación de navegación en un dispositivo no sustituye pruebas de reconexión, DNS, cambio de servidor y regresión.
