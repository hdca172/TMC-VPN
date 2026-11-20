# TMC VPN V4.5 — Plan de pruebas previo a publicación

**Estado:** pendiente de ejecutar y registrar. **No habilitar GitHub Pages ni fusionar el PR #19 sin aprobación explícita.**

## Entorno y seguridad
- [ ] Registrar modelo Android y versión del sistema, sin incluir datos personales.
- [ ] Confirmar que el APK procede de una ejecución exitosa de GitHub Actions de la rama `main`.
- [ ] Confirmar versión de la app: 4.5.0 (versionCode 14).
- [ ] Utilizar exclusivamente perfiles WireGuard autorizados. Nunca subir archivos `.conf`, claves privadas o capturas con secretos.

## Instalación y datos
- [ ] Instalar encima de la versión anterior sin desinstalar.
- [ ] Confirmar que se conservan perfiles, favoritos y ajustes.
- [ ] Reiniciar la app y comprobar persistencia.

## Conexión y estabilidad
- [ ] Conectar WireGuard y comprobar navegación.
- [ ] Comprobar DNS con el túnel conectado.
- [ ] Desconectar y confirmar que la navegación habitual se restablece.
- [ ] Cambiar de perfil/servidor de forma segura y reconectar.
- [ ] Cambiar Wi-Fi → datos móviles y comprobar reconexión.
- [ ] Cambiar datos móviles → Wi-Fi y comprobar reconexión.
- [ ] Bloquear/desbloquear el teléfono y revisar el estado.
- [ ] Mantener la VPN activa 15 minutos y comprobar que no se cierra.
- [ ] Verificar que los contadores y el historial se actualizan.

## Descargas y presentación
- [ ] Abrir la página HTML localmente para revisar diseño móvil.
- [ ] Comprobar contraste, botones, enlaces y legibilidad.
- [ ] Abrir GitHub Actions desde el enlace de la página.
- [ ] Verificar que el ZIP incluye `app-debug.apk`.
- [ ] Confirmar que las advertencias de APK debug y vencimiento de artefactos son visibles.
- [ ] Confirmar que GitHub Pages sigue **desactivado**.

## Registro de incidencias
Para cada fallo registrar: paso, resultado esperado, resultado real, versión Android, captura sin secretos y si es reproducible.

## Criterios para proponer publicación
- Todas las pruebas críticas aprobadas, o incidencias documentadas y aceptadas.
- Ningún secreto en el repositorio ni en las capturas.
- Revisión de visibilidad del repositorio y de GitHub Pages.
- Aprobación explícita del propietario antes de fusionar el PR #19 o activar Pages.
