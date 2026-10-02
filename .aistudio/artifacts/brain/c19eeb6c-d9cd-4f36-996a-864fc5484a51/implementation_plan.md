# Plan de Implementación: Administrador de Dispositivos, Sacudir para Apagar/Bloquear y Encendido en Reposo Profundo (Deep Sleep)

Plan técnico para dotar a WakeGuard de capacidades avanzadas de administración de hardware: permitir **apagar y bloquear la pantalla al sacudir** mediante la API nativa de **Administrador de Dispositivos** (`DeviceAdminReceiver`), y resolver el encendido con pantalla apagada evitando que la CPU entre en suspensión profunda (*Deep Sleep*) mediante `PARTIAL_WAKE_LOCK`, sensor Wake-Up y permiso de superposición (`SYSTEM_ALERT_WINDOW`).

---

### Decisiones Confirmadas con el Usuario

- **Función Bidireccional Completa:** Sacudir para **encender** (cuando la pantalla esté apagada) y sacudir para **apagar y bloquear** (cuando la pantalla esté encendida).
- **Tipo de Privilegio Avanzado Elegido:** **Administrador de Dispositivos de Android (`DevicePolicyManager` / `DeviceAdminReceiver`)**.
  - *Razón crítica:* Los servicios de accesibilidad son desactivados con frecuencia por optimizadores de batería de terceros o por capas como HiOS de Tecno. En contraste, el permiso de Administrador de Dispositivos es una política del sistema operativo Android que **nunca se desactiva sola**.
- **Comportamiento con Pantalla Encendida:** Al sacudir el teléfono mientras la pantalla está activa, el dispositivo se bloquea y apaga de inmediato (`devicePolicyManager.lockNow()`), protegiendo el botón tanto al prender como al apagar.

---

## 1. Visión General del Sistema y Flujo de Operación

```
                          [ Sacudida Deliberada con la Mano ]
                                           │
                                           ▼
                             ¿Cómo está la pantalla ahora?
                                     /           \
                                    /             \
                    [ PANTALLA ENCENDIDA ]     [ PANTALLA APAGADA ]
                              │                         │
                              ▼                         ▼
                 ¿Admin de Dispositivo Activo?   ¿CPU en Deep Sleep evitada?
                          /        \             (PARTIAL_WAKE_LOCK activo)
                       (SÍ)        (NO)                 │
                        │            │                  ▼
                        ▼            ▼         [ WakeLock + Activity ]
                 [ lockNow() ]  [ Mostrar Guía ](Ilumina pantalla sin bloqueo)
                 (Apaga pantalla  de activación         │
                  inmediatamente) de Admin              ▼
                        │                      [ Pantalla Encendida ]
                        ▼
                 [ Pantalla Apagada ]
```

---

## 2. Solución Técnica Detallada

### A. Módulo de Administrador de Dispositivos (`receiver/WakeDeviceAdminReceiver.kt`)
1. **Creación del Receptor de Políticas:**
   - Crear `WakeDeviceAdminReceiver` extendiendo `DeviceAdminReceiver`.
   - Crear recurso XML `res/xml/device_admin_policies.xml` declarando la directiva obligatoria `<force-lock />`.
   - Registrar el receptor en `AndroidManifest.xml` con el permiso `android.permission.BIND_DEVICE_ADMIN`.
2. **Acción de Bloqueo Inmediato:**
   - Método `bloquearPantallaPorGesto()` en `WakeMotionService`: si la pantalla está interactiva (`isScreenCurrentlyOn == true`), invoca `devicePolicyManager.lockNow()` con vibración háptica suave y registro en Room (`SHAKE_LOCK`).
3. **Flujo de Activación desde la UI:**
   - En `DashboardScreen` y `GuideScreen`, agregar tarjeta interactiva con interruptor/botón que detecta si el permiso de Administrador está activo (`dpm.isAdminActive(component)`).
   - Si no está activo, lanza el intent oficial del sistema `DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN` con mensaje explicativo claro.

### B. Corrección de la Escucha en Reposo Profundo (*CPU Deep Sleep*)
* **Problema:** En Android 10+ (y Tecno HiOS), cuando la pantalla se apaga, a los 3-5 segundos el kernel de Linux suspende la CPU (Application Processor). Los eventos del acelerómetro se detienen por completo.
* **Solución:**
  1. En `WakeMotionService`, adquirir un `PARTIAL_WAKE_LOCK` exclusivo de CPU únicamente mientras la pantalla esté apagada y el servicio esté en monitoreo activo.
  2. Solicitar sensor con bandera Wake-Up de hardware:
     `sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true) ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)`
  3. Liberar el `PARTIAL_WAKE_LOCK` tan pronto la pantalla se encienda para garantizar un consumo mínimo de batería.

### C. Permiso de Superposición (`SYSTEM_ALERT_WINDOW`)
* **Problema:** Desde Android 10, el sistema bloquea que un servicio en segundo plano inicie una Activity para encender la pantalla a menos que cuente con permiso de superposición ("Mostrar sobre otras apps").
* **Solución:**
  1. Declarar `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />` en `AndroidManifest.xml`.
  2. Añadir acceso directo en la UI para conceder "Mostrar sobre otras aplicaciones" (`Settings.ACTION_MANAGE_OVERLAY_PERMISSION`), permitiendo que `WakeScreenActivity` sobrepase cualquier restricción de HiOS.

---

## 3. Archivos Involucrados y Cambios Concretos

1. **`app/src/main/res/xml/device_admin_policies.xml` (Nuevo):**
   - Declaración de la directiva `<device-admin><uses-policies><force-lock /></uses-policies></device-admin>`.

2. **`app/src/main/java/com/example/receiver/WakeDeviceAdminReceiver.kt` (Nuevo):**
   - Receptor oficial para administrar el estado de administrador de dispositivo con métodos en español.

3. **`app/src/main/AndroidManifest.xml`:**
   - Declaración del permiso `SYSTEM_ALERT_WINDOW`.
   - Declaración del receptor `WakeDeviceAdminReceiver` con `BIND_DEVICE_ADMIN` y metadatos de políticas.

4. **`app/src/main/java/com/example/service/WakeMotionService.kt`:**
   - Gestión de `PARTIAL_WAKE_LOCK` en reposo con pantalla apagada.
   - Integración de `DevicePolicyManager.lockNow()` cuando se sacude con la pantalla encendida.
   - Manejo del sensor con capacidad Wake-Up.

5. **`app/src/main/java/com/example/ui/WakeViewModel.kt`:**
   - Estado reactivo para comprobar si el Administrador de Dispositivos y el permiso de superposición están activos.
   - Métodos para lanzar las pantallas del sistema para otorgar cada permiso.

6. **`app/src/main/java/com/example/ui/screens/DashboardScreen.kt` & `GuideScreen.kt`:**
   - Tarjetas de estado dedicadas para Administrador de Dispositivos ("Bloqueo al Sacudir") y "Mostrar sobre otras apps", con botón de activación directa.
   - Indicador visual claro del estado de ambos permisos.

7. **`app/src/test/java/com/example/ExampleUnitTest.kt`:**
   - Pruebas unitarias para validar las condiciones de bloqueo y desbloqueo bidireccional.

---

## 4. Protocolo de Verificación

* **Compilación:** Ejecutar `compile_applet` para garantizar que la nueva arquitectura de Administrador de Dispositivos y recursos XML compile de forma limpia.
* **Pruebas Unitarias:** Ejecutar `gradle :app:testDebugUnitTest` para validar que la lógica no tenga regresiones.
