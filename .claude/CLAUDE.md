# Kronos — notas para Claude

Prototipo **visual** de una app Android de asistencia. Todo está en layouts XML; **sin lógica**
(no validar, no guardar, no backend) salvo que el usuario lo pida. Documentación completa en `README.md`.

## Arquitectura
- Una sola Activity: `app/src/main/java/com/example/kronos_/MainActivity.kt` (AppCompatActivity).
- Pantallas: `app/src/main/res/layout/screen_*.xml`. Piezas: `part_back.xml` (flecha, id `back`),
  `part_admin_bar.xml` (ids `nav_dashboard`, `nav_employees`, `nav_requests`, `nav_history`).
- Navegación = mapa `links` en MainActivity: `pantalla -> (id de vista -> pantalla destino)`. `BACK` = volver.
  - `roots` (`login`, `home`, `dashboard`) limpian la pila; ir a una pantalla ya en la pila vuelve a ella.
  - `auth_success` -> `sede` (empleado) o `dashboard` si `screen_login_admin` está en la pila.
  - Pestañas de método: ids `tab_a`, `tab_b`, `area_a`, `area_b` (toggle genérico en `setupTabs`).
- Pantalla nueva = layout + ids en botones + entrada en `links`.

## Estilo (reusar, no hardcodear)
- Colores en `values/colors.xml` (tokens de Figma: `ink`, `muted`, `hint`, `line`, `card`, `primary`
  #00AEEF, `link`, `ok`, `warn`, `bad` + `_bg`).
- Estilos en `values/styles.xml`: `Screen`, `Content`, `Card`, `Row`, `Col`, `Divider`, `Txt.*`,
  `Field`, `Select`, `Btn.Primary`, `Btn.Secondary`, `IconBtn`, `Tab`, `NavItem`.
- Tema: `Theme.Material3.Light.NoActionBar` (sin modo oscuro). Textos en español, directos en el XML.
- Íconos de Figma convertidos a vector: `ic_arrow_left`, `ic_bell`, `ic_settings`, `ic_user`,
  `ic_fingerprint`, `ic_user_round`. El resto son Material (`ic_home`, `ic_mail`, `ic_share`…).

## Figma
- Archivo `oaVJodp0P68iUT7EKH6hxK` (cuenta Starter: **límite de llamadas MCP**, se agotó una vez).
- Solo 16 pantallas se calcaron de Figma; el resto sigue el mismo estilo. Si hay cupo, revisar contra Figma.

## Verificar
- `./gradlew assembleDebug lintDebug` (lint debe quedar sin errores; minSdk 24).
- Teléfono del usuario: Galaxy S21 FE por USB. `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
  Si sale "unauthorized", el usuario debe aceptar el aviso de depuración USB.
- Al cambiar `links`, confirmar que cada id existe en su layout (si no, la app se cierra al abrir la pantalla).

## Repo
- GitHub público: https://github.com/Botero56/kronos_app (rama `main`).
