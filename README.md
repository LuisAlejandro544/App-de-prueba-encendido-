# WakeGuard 🛡️⚡
**Protector del Botón de Encendido mediante Movimiento Inteligente de Ultra Bajo Consumo**

---

## 📱 ¿Qué es WakeGuard?

En muchos teléfonos Android, el botón físico de encendido (*power button*) sufre un desgaste mecánico continuo: un usuario promedio presiona este botón entre 80 y 150 veces al día. Los microinterruptores mecánicos internos tienen una vida útil limitada y son propensos a hundirse o dejar de hacer contacto.

Muchos dispositivos no cuentan con soporte nativo de hardware o ROM para gestos táctiles con pantalla apagada (como el doble toque para despertar). **WakeGuard resuelve este problema encendiendo la pantalla simplemente al mover, agitar o levantar el teléfono**, sin tocar ningún botón físico y con un consumo de batería prácticamente imperceptible (< 1.2% al día).

---

## ✨ Características Principales

* **🚀 Encendido por Movimiento de Hardware:** Utiliza el acelerómetro del dispositivo para despertar la pantalla mediante gestos naturales.
* **🏍️ Supresión Pasiva Anti-Baches y Viajes en Moto (Zero-Config):**
  * Detecta la cadencia de traqueteo vehicular continuo (3 o más oscilaciones rápidas en < 1.5 s) e ignora de inmediato los baches de asfalto.
  * Descarta cualquier impacto si el teléfono está boca abajo o en posición invertida dentro de un bolso, mochila o riñonera (koala).
  * **100% Silencioso e Invisible:** No vibra, no enciende la pantalla y no requiere que el usuario active ni configure modos manuales.
* **🛡️ Escudo Anti-Bolsillo:** Integra el sensor de proximidad. Si el teléfono está dentro de un bolsillo, bolso o boca abajo sobre una mesa, se ignora todo movimiento para evitar encendidos accidentales.
* **🌱 Tecnología EcoShield (Ultra Ahorro de Batería):**
  * **Pausa en Pantalla Activa:** Cuando la pantalla ya está encendida y estás usando el móvil, los sensores se pausan automáticamente (ahorrando hasta un 80% de energía).
  * **Modo Noche / Horario de Descanso:** Suspensión total durante las horas de sueño (0% de gasto nocturno).
  * **Corte por Batería Baja:** Se detiene si la batería desciende del nivel de seguridad configurado (ej. 15%).
  * **Muestreo Eco Normal:** Muestreo a ~15 Hz para reducir drásticamente el consumo de CPU.
* **📊 Salud del Botón y Contador de Clicks Evitados:** Registra cada activación en una base de datos local **Room**, calculando las pulsaciones físicas ahorradas y la vida útil mecánica preservada.
* **⏰ Encendido Programado de Rescate (Exclusivo al 0%):** Configuración única para que el dispositivo se auto-encienda si se apaga por batería agotada (0%), vinculable con el RTC del fabricante. No interviene ni despierta el terminal mientras tenga carga normal, y **NUNCA apaga** el dispositivo.
* **🧩 Interfaz Modular en Material Design 3:** Cinco pantallas dedicadas y organizadas con navegación inferior ergonómica y telemetría en vivo con gráficos de aceleración triaxial (X, Y, Z e inclinación).
* **🤖 Compatibilidad Universal:** Soporta desde **Android 8.0 (API 26, Oreo)** hasta Android 16 (API 36).
* **🔒 Compilación Limpia sin Archivos .env:** La app no depende de archivos `.env` ni claves externas para compilar o ejecutarse.

---

## 🛠️ Stack Tecnológico

| Componente | Tecnología |
| :--- | :--- |
| **Lenguaje** | Kotlin 2.2+ (100% nativo con Corrutinas y StateFlow) |
| **Interfaz de Usuario** | Jetpack Compose con Material Design 3 |
| **Arquitectura** | MVVM (Model - View - ViewModel) + Repository Pattern |
| **Persistencia Local** | Room Database + KSP (Kotlin Symbol Processing) |
| **Servicio de Fondo** | Android Foreground Service con notificación persistente |
| **Sensores** | `Sensor.TYPE_ACCELEROMETER` y `Sensor.TYPE_PROXIMITY` |
| **Filtro Vehicular** | `VehicularBumpFilter` (análisis temporal de cadencia y postura triaxial Z) |
| **Wake Mechanism** | `PowerManager.WakeLock` + `WakeScreenActivity` (`setShowWhenLocked` / `setTurnScreenOn`) |
| **CI / CD** | GitHub Actions (`build-debug-apk.yml`) con generación autónoma de firma |

---

## 📂 Requisitos del Sistema

* **Versión mínima de Android:** Android 8.0 (API nivel 26).
* **Versión objetivo (Target SDK):** Android 15 / 16 (API nivel 36).
* **Arquitecturas soportadas:** ARMv7 (32-bit), ARM64-v8a (64-bit), x86 y x86_64.
* **Hardware requerido:** Acelerómetro básico (presente en el 100% de smartphones).

---

## 🚀 Compilación y Generación de APK

### 1. Compilación Local

No se requieren archivos `.env`. Para compilar y probar:

```bash
# Otorgar permisos al script y al wrapper de Gradle
chmod +x ./generate_signature.sh ./gradlew

# Generar la firma debug de forma autónoma (si no existe)
./generate_signature.sh

# Ejecutar las pruebas unitarias locales (Robolectric y JUnit)
./gradlew testDebugUnitTest

# Generar el archivo APK Debug instalable
./gradlew assembleDebug

# El APK generado se ubica en:
# app/build/outputs/apk/debug/app-debug.apk
```

### 2. Compilación en la Nube con GitHub Actions

El repositorio incluye el flujo de trabajo automatizado en `.github/workflows/build-debug-apk.yml`:
* **Descarga del código:** Clona automáticamente el repositorio.
* **Firma autónoma garantizada:** Ejecuta `./generate_signature.sh` para crear `debug.keystore` al vuelo sin esperar interacción de usuario.
* **Caché opcional manual:** En la pestaña *Actions*, puedes ejecutarlo manualmente activando o desactivando la casilla de caché según tu preferencia.
* **Artefacto directo:** Publica el archivo `WakeGuard-Debug-APK` listo para descargar e instalar en el móvil.

---

## ⚙️ Permisos Utilizados y Justificación

* `android.permission.WAKE_LOCK`: Ilumina la pantalla al detectar el gesto de movimiento válido.
* `android.permission.FOREGROUND_SERVICE`: Obligatorio en Android 8.0+ para mantener activo el monitoreo en segundo plano.
* `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`: Requerido en Android 14+ para declarar formalmente el propósito del servicio.
* `android.permission.POST_NOTIFICATIONS`: Requerido en Android 13+ para mostrar el estado y los controles rápidos de pausa.
* `android.permission.VIBRATE`: Proporciona confirmación háptica suave al activar la pantalla (desactivada ante baches vehiculares).
* `android.permission.RECEIVE_BOOT_COMPLETED`: Permite reanudar la protección tras encender el móvil si el usuario lo activa.
* `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: Guía para evitar que capas agresivas cierren el servicio.

---

## 💡 Recomendaciones para Marcas con Cierres Agresivos

Si instalas el APK en marcas con gestores de batería agresivos:
1. **Xiaomi / POCO / Redmi (MIUI / HyperOS):** En Ajustes > Aplicaciones > WakeGuard, activa **Inicio automático**, selecciona **Sin restricciones** en ahorro de batería y coloca el **candado** en la vista de aplicaciones recientes.
2. **Samsung Galaxy (One UI):** Añade WakeGuard en *Ajustes > Cuidado del dispositivo > Batería > Aplicaciones que nunca se suspenden*.
3. **Motorola / Pixel:** Configura el uso de batería de la app en **Sin restricciones**.
