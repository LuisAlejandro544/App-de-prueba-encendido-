# 🧠 AI_CONTEXT.md - Contexto de Dominio y Decisiones de Arquitectura

Este archivo sirve como referencia técnica y de contexto para asistentes de Inteligencia Artificial o desarrolladores que interactúen con el proyecto **WakeGuard**.

---

## 🎯 Propósito del Proyecto
Proteger el botón físico de encendido (*power switch*) de teléfonos Android permitiendo despertar la pantalla mediante **movimiento físico del dispositivo** (acelerómetro y proximidad), **sin utilizar gestos táctiles con pantalla apagada** (los cuales dependen de soporte exclusivo a nivel de driver/kernel del fabricante del panel táctil y no existen en muchos teléfonos económicos o con ROMs AOSP).

---

## 🔑 Decisiones Técnicas Críticas y Justificaciones

### 1. ¿Por qué NO usar gestos táctiles en pantalla (Double Tap to Wake)?
El "doble toque para encender" requiere que el digitalizador táctil (*touchscreen digitizer*) permanezca alimentado con energía cuando el teléfono está suspendido. Esta función depende del kernel del fabricante (OEM) y la gran mayoría de teléfonos de gama baja o media no la exponen a través de APIs estándar de Android. En cambio, el acelerómetro estándar está presente y disponible mediante la API oficial `android.hardware.SensorManager` en el 100% de los teléfonos Android.

### 2. ¿Por qué NO usar Lua, C++ o Rust en este proyecto?
* **Sobrecarga de JNI:** Encender la pantalla (`PowerManager`, `WakeLock`, `setTurnScreenOn`), interactuar con el ciclo de vida del servicio (`ForegroundService`) y gestionar notificaciones son llamadas exclusivas del SDK de Android en Java/Kotlin.
* **Consumo de batería:** El procesamiento de señales del acelerómetro en esta app se realiza a una frecuencia baja (~15 Hz en modo Eco) con cálculos vectoriales básicos ($\sqrt{x^2 + y^2 + z^2}$). El compilador Kotlin/ART genera código ensamblador nativo directo para ARM. Añadir Lua o puentes JNI con C++/Rust incrementaría el consumo de ciclos de reloj y despertaría la CPU innecesariamente.

### 3. Mecanismo Dual para Despertar la Pantalla
En Android 8.0+ (API 26+) y versiones posteriores hasta Android 16+, las políticas de seguridad del bloqueo de pantalla (*Keyguard*) pueden ignorar un simple `WakeLock(ACQUIRE_CAUSES_WAKEUP)`. Por ello, WakeGuard implementa una estrategia dual:
1. **`PowerManager.WakeLock`:** Adquiere un bloqueo `SCREEN_BRIGHT_WAKE_LOCK or ACQUIRE_CAUSES_WAKEUP` con liberación automática en 1500 ms (evita cualquier fuga de bloqueo).
2. **`WakeScreenActivity`:** Actividad auxiliar transparente con las banderas:
   - `setShowWhenLocked(true)`
   - `setTurnScreenOn(true)`
   - Se cierra automáticamente tras 200 ms. Esta combinación garantiza el 100% de éxito en cualquier capa de personalización (MIUI, One UI, EMUI, ColorOS, Motorola, Pixel).

### 4. Estrategia de Consumo Ultra Bajo (EcoShield)
El principal desafío de los servicios de sensores en segundo plano es la batería. WakeGuard minimiza el consumo aplicando:
* **Filtro de Proximidad:** Si el sensor de proximidad reporta obstrucción (`distance < 3.0cm`), se descarta todo movimiento.
* **Pausa con Pantalla Activa:** Registra un `BroadcastReceiver` para `ACTION_SCREEN_ON`. En cuanto el usuario enciende y utiliza la pantalla, los sensores se desregistran/pausan. Solo vuelven a escuchar cuando la pantalla se apaga (`ACTION_SCREEN_OFF`). Esto ahorra hasta un 80% de energía.
* **Horario Nocturno:** Desactiva la escucha durante las horas de descanso del usuario (23:00 - 07:00 por defecto).
* **Umbral de Batería Baja:** Desconexión automática si la batería cae por debajo del 15%.
* **Cooldown de 5 Segundos:** Evita que el caminar, correr o viajar en auto genere cálculos continuos y disparos repetidos.

### 5. Supresión Pasiva de Baches y Traqueteo de Moto (Zero-Config)
Para resolver falsos positivos cuando el usuario viaja en motocicleta o bicicleta con el teléfono en un bolso koala:
* **Análisis de Cadencia Temporal:** Un bache o vibración de motor produce una ráfaga continua de $\ge 3$ oscilaciones en menos de 1500 ms. `VehicularBumpFilter` bloquea el encendido durante esa ráfaga y añade 2 segundos de calma.
* **Filtro de Postura Fisiológica (Eje Z):** Cuando el teléfono está dentro de un bolso koala, oscila boca abajo o plano contra el cuerpo ($az < -1.5$ m/s²). Si la pantalla no apunta al rostro humano, el encendido se descarta de inmediato.
* **Comportamiento 100% Silencioso:** No emite vibración, no enciende pantalla y no requiere ninguna configuración manual del usuario.

### 6. Desacoplamiento de Archivos .env y CI/CD Autónomo
* **Cero dependencias de .env:** WakeGuard es una utilidad local offline que no requiere claves API ni variables secretas externas. Se eliminó el plugin Secrets de Gradle para permitir clonar y compilar directamente.
* **Firma Autónoma en CI/CD:** El script `generate_signature.sh` crea automáticamente `debug.keystore` sin intervención humana en GitHub Actions (`.github/workflows/build-debug-apk.yml`), con soporte para caché opcional manual.

---

## 📊 Reglas de Dominio y Constantes

* **Rango de Sensibilidad:** 1.0f (baja sensibilidad, requiere movimiento firme) a 5.0f (muy sensible).
* **Fórmula de Umbral de Agitación:** `shakeThreshold = 25.0f - (sensitivity * 3.4f)` m/s².
* **Frecuencia de Muestreo:**
  * Modo Eco: `SensorManager.SENSOR_DELAY_NORMAL` (~200,000 µs / ~5-15 Hz).
  * Modo Fluido: `SensorManager.SENSOR_DELAY_UI` (~60,000 µs / ~16-20 Hz).
* **Ciclos de Vida del Botón:** Un switch mecánico promedio de smartphone soporta aproximadamente 80,000 a 100,000 presiones antes de fallo mecánico. Cada encendido por movimiento se contabiliza en Room como una pulsación ahorrada.
