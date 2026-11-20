# TMC VPN - Aprovisionamiento seguro

## Estado
No existe un servidor VPN aprovisionado ni verificado por este repositorio.

## Opciones
- Principal: VPS Linux con IP pública, preferentemente una instancia elegible para una oferta Always Free.
- Respaldo: segunda instancia autorizada, solo cuando esté disponible.
- Personalizado: perfil WireGuard importado por el usuario.

## Requisitos para el servidor real
1. Crear una cuenta propia en el proveedor y comprobar límites gratuitos, disponibilidad regional y facturación.
2. Aprovisionar una VM Ubuntu con IP pública y acceso SSH mediante clave.
3. Instalar WireGuard, habilitar forwarding IPv4 y configurar NAT hacia la interfaz de salida.
4. Permitir UDP 51820 en el firewall del sistema y reglas de red del proveedor.
5. Generar claves privadas exclusivamente en el dispositivo/servidor apropiado; no subirlas al repositorio.
6. Crear un perfil cliente con Address, PrivateKey, DNS y Peer con PublicKey, Endpoint y AllowedIPs.
7. Probar handshake, tráfico, DNS, cambios de red y desconexión antes de declarar servidor operativo.
8. Integrar la biblioteca oficial WireGuard Android y la autorización de VpnService; no simular conexión.

## Seguridad
No guardar credenciales de Oracle, claves SSH, perfiles WireGuard privados ni secretos en GitHub.
No afirmar que la VPN proporciona datos móviles gratuitos. Un túnel requiere conectividad previa.
