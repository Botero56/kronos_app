# Kronos — prototipo visual de la app (Android)

Kronos es una app de control de asistencia: los empleados marcan entrada y salida con
verificación biométrica y ubicación, y el administrador ve el dashboard, los empleados,
las solicitudes de permiso y los reportes.

Este repositorio contiene el **prototipo visual**: todas las pantallas están hechas en
**layouts XML** a partir del diseño de Figma y están **conectadas entre sí**, pero **no tienen
lógica** (no se validan campos, no se guardan datos, no hay backend).

**Integrantes del grupo:** Sebastian Giraldo · Sebastian Gonzalez · Alejandro Pacheco · Samuel Botero

- Diseño en Figma: <https://www.figma.com/proto/oaVJodp0P68iUT7EKH6hxK?node-id=0-1>
- Paquete: `com.example.kronos_` · minSdk 24 · targetSdk 37

---

## Descargar el APK

**[⬇ Descargar kronos.apk](https://github.com/Botero56/kronos_app/raw/main/apk/kronos.apk)**

En el teléfono: abrir el archivo descargado y permitir "Instalar apps desconocidas" cuando lo pida.
Es una versión de prueba (firmada con la llave de depuración), no la de la Play Store.

## Cómo ejecutarlo

1. Abrir la carpeta en **Android Studio** y esperar el *Gradle Sync*.
2. Conectar un teléfono con **depuración USB** activada (o crear un emulador).
3. Pulsar **Run ▶**.

Desde la terminal:

```bash
./gradlew assembleDebug                      # genera app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Para ver una pantalla sin ejecutar la app: abrir cualquier `app/src/main/res/layout/screen_*.xml`
y usar la pestaña **Design** o **Split**.

---

## Cómo funciona

### Estructura

```
app/src/main/
├── java/com/example/kronos_/MainActivity.kt   ← única Activity: muestra pantallas y las conecta
└── res/
    ├── layout/screen_*.xml      ← una pantalla por archivo (42)
    ├── layout/part_back.xml     ← flecha de regreso reutilizable
    ├── layout/part_admin_bar.xml← barra inferior del administrador
    ├── values/colors.xml        ← colores tomados de Figma
    ├── values/styles.xml        ← estilos: títulos, campos, botones, tarjetas, filas…
    ├── drawable/                ← íconos (vectores) y fondos (shapes)
    ├── color/tab_content.xml    ← color de las pestañas seleccionadas
    └── font/zen_dots.ttf        ← fuente del logo "KRONOS"
```

### Navegación (`MainActivity.kt`)

- Hay **una sola Activity**. Cada pantalla es un layout que se muestra con `setContentView`.
- La tabla `links` define las conexiones: **pantalla → (botón → pantalla destino)**.
  `BACK` significa "volver a la anterior".
- Se guarda un historial (pila) para que la flecha ← y el botón atrás del teléfono regresen.
- Reglas de la pila:
  - Ir a una **pantalla raíz** (`login`, `home`, `dashboard`) borra el historial.
  - Ir a una pantalla que **ya está en el historial** vuelve a ella en lugar de duplicarla.
- Toda vista con `id="@+id/back"` (la flecha de `part_back.xml`) vuelve atrás sola.
- La pantalla de *Verificación exitosa* termina en **Elige tu sede** si entró un empleado, o en el
  **Dashboard** si entró por el login de administrador.
- Las pestañas de método (rostro/huella y patrón/huella) usan los ids `tab_a`, `tab_b`,
  `area_a`, `area_b`; al tocarlas solo cambia lo que se ve.
- El Splash pasa solo al Login después de 1,5 s.

### Agregar una pantalla nueva

1. Crear `res/layout/screen_nombre.xml` usando los estilos de `styles.xml`
   (`Screen`, `Content`, `Txt.Title`, `Field`, `Btn.Primary`, `Btn.Secondary`, `Card`, `Row`…).
2. Si tiene flecha de regreso: `<include layout="@layout/part_back" />`.
3. Darle `id` a los botones y agregar una entrada en `links` dentro de `MainActivity.kt`.

---

## Flujos

**Empleado**

```
Splash → Login → Patrón o huella → Verificación exitosa → Elige tu sede → Home
Home → Marcar entrada → Verificación biométrica → Entrada registrada
Home → Marcar salida  → Verificación biométrica salida → Salida exitosa
Home → Sede (texto verde) → Fuera de rango → Sin ubicación
Home → 🔔 Notificaciones · ⚙ Ajustes · "Hola, Camila" → Perfil
Home → Última entrada → Mi historial · Horas → Mi resumen
Home → Mi horario · Solicitar permiso → Mis solicitudes → Detalle de solicitud
Ajustes → Política de privacidad → Ayuda → Contactar soporte → Sin conexión
Ajustes → Cerrar sesión → Login
```

**Administrador**

```
Login → "Entrar como administrador" → Login admin → Patrón o huella → Verificación exitosa → Dashboard
Barra inferior: Inicio (Dashboard) · Empleados · Solicitudes · Historial
Dashboard → "Dashboard · Sede Bello" → Crear/editar sede
Dashboard → tarjetas → Historial · Carlos Rúa (ausente) → Alertas
Dashboard → Generar reporte → Reporte generado → Descargar → Abrir archivo (vista previa)
                                              → Compartir
Empleados → + Agregar → Nuevo empleado → Empleado creado → Ver empleados
Solicitudes → (fila) → Revisar solicitud → Aprobar / Rechazar
Dashboard → ⇥ Cerrar sesión → Login
```

**Recuperar cuenta**

```
Login → ¿Olvidaste tu contraseña? → Recuperar → Nueva contraseña → Login
Login → Crear cuenta → Registro
```

---

## Pantallas

| # Figma | Pantalla | Layout |
|---|---|---|
| 1 | Splash | `screen_splash.xml` |
| 2 / 2.1 | Login / Login admin | `screen_login.xml`, `screen_login_admin.xml` |
| nueva | Autenticación por patrón o huella | `screen_auth.xml` |
| nueva | Verificación exitosa | `screen_auth_success.xml` |
| 3 | Registro de cuenta | `screen_register.xml` |
| 4 / 5 | Recuperar / Restablecer contraseña | `screen_recover.xml`, `screen_reset.xml` |
| 6 | Selección de sede | `screen_sede.xml` |
| 7 / 8 | Perfil / Cambiar contraseña | `screen_profile.xml`, `screen_change_password.xml` |
| 9 / 10 / 11 | Ajustes / Notificaciones / Ayuda | `screen_settings.xml`, `screen_notifications.xml`, `screen_help.xml` |
| 12 / 13 | Sin ubicación / Sin conexión | `screen_no_location.xml`, `screen_offline.xml` |
| 14 | Home del empleado | `screen_home.xml` |
| 15 / 15.1 | Verificación biométrica (entrada / salida) | `screen_biometric.xml`, `screen_biometric_out.xml` |
| 16 / 16.1 | Confirmación de entrada / salida | `screen_confirm_in.xml`, `screen_confirm_out.xml` |
| 17 | Marcaje fuera de rango | `screen_out_of_range.xml` |
| 18 / 19 | Mi historial / Solicitar permiso | `screen_history.xml`, `screen_request_permit.xml` |
| 30 / 31 | Mi horario / Mi resumen | `screen_schedule.xml`, `screen_summary.xml` |
| 32 / 33 | Mis solicitudes / Detalle | `screen_my_requests.xml`, `screen_request_detail.xml` |
| 20 / 21 | Dashboard / Empleados | `screen_dashboard.xml`, `screen_employees.xml` |
| 22 / 23 | Crear sede / Historial admin | `screen_sede_edit.xml`, `screen_admin_history.xml` |
| 24 / 29 | Generar reporte / Reporte generado | `screen_report.xml`, `screen_report_done.xml` |
| nueva | Descargar reporte | `screen_report_download.xml` |
| nueva | Vista previa del reporte (Abrir archivo) | `screen_report_view.xml` |
| nueva | Compartir reporte | `screen_report_share.xml` |
| 25 / 26 | Nuevo empleado / Empleado creado | `screen_new_employee.xml`, `screen_employee_created.xml` |
| 27 / 28 | Solicitudes / Revisar solicitud | `screen_admin_requests.xml`, `screen_review_request.xml` |
| 34 | Detalle de alertas | `screen_alerts.xml` |

---

## Historial de cambios

1. **De Compose a layouts XML.** La primera versión estaba en Jetpack Compose. Se rehicieron todas
   las pantallas como layouts XML y se quitó Compose del proyecto (código, plugin y dependencias).
2. **Diseño desde Figma.** Colores, tamaños de texto, bordes e íconos salen del archivo de Figma.
   Los íconos (campana, engranaje, flecha, persona, huella) se exportaron como SVG y se
   convirtieron a vectores de Android.
3. **Autenticación en el login (nuevo).** Después de "Iniciar sesión" aparecen dos vistas:
   *Patrón o huella* y *Verificación exitosa*. El empleado continúa a *Elige tu sede*.
4. **El administrador también se autentica.** El login de admin pasa por las mismas vistas y
   termina en el Dashboard.
5. **Reporte: Descargar, Abrir archivo y Compartir (nuevo).** Los botones de *Reporte generado*
   llevan a sus propias vistas; *Abrir archivo* muestra una vista previa del PDF.
6. **Ajustes probados en un teléfono real** (Galaxy S21 FE): se corrigió el botón
   "Entrar como administrador" que salía cortado, una franja en la vista previa del PDF y el
   fondo rosado de las pestañas de Solicitudes.

### Cosas que no están en Figma

Se agregaron para que todas las pantallas sean alcanzables:
- Botón **Cerrar sesión** en Ajustes.
- **Barra inferior** del administrador.
- **Flecha de regreso** en algunas pantallas que en Figma no la tienen.
- Las 5 vistas nuevas (autenticación, verificación exitosa, descargar, vista previa, compartir)
  siguen el estilo del resto de la app.

### Notas

- 16 pantallas se tomaron directamente de Figma. Las demás se hicieron con los mismos colores y
  textos del diseño (el plan gratuito de Figma limitó las consultas), así que pueden tener
  pequeñas diferencias de posición.
- Los textos están escritos directamente en los layouts (no en `strings.xml`) porque es un
  prototipo visual.

## Pruebas

- `./gradlew assembleDebug lintDebug` compila sin errores.
- Se recorrieron todos los flujos en un Galaxy S21 FE tocando cada botón: sin cierres de la app.
