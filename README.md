# TMC VPN V4.4

Aplicación Android con soporte de túneles WireGuard mediante perfiles autorizados. Incluye selección de proveedores/perfiles, favoritos, diagnóstico de red, estadísticas, recuperación y registro local de conexiones.

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

GitHub Actions > **TMC VPN - V3 APK CI** > ejecución reciente > artefacto **TMC-VPN-V3-debug**. El nombre histórico del flujo no representa la versión de la aplicación. La versión Android declarada es **4.4.0**.

Compilación local: `gradle :app:assembleDebug --no-daemon` con JDK 17 y Android SDK instalado.

## Estado de publicación

La integración a `main` se revisa mediante el PR #16. No publiques perfiles ni claves privadas en el repositorio. La validación de navegación en un dispositivo no sustituye pruebas de reconexión, DNS, cambio de servidor y regresión.
