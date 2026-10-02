# Plan de Reparación y Optimización: Detección Firme por Sacudida (Shake to Wake) en Tecno / Android

Plan de ingeniería para solucionar de raíz el problema donde el teléfono no enciende al sacudirlo, ajustando los algoritmos de detección, corrigiendo la lógica del sensor de proximidad en dispositivos Tecno (HiOS), permitiendo pruebas con pantalla encendida y garantizando la robustez ante la agresiva gestión de batería de HiOS.

---

### Decisiones Confirmadas con el Usuario

- **Tipo de Gesto Solicitado:** Sacudida firme y deliberada (evita falsos toques al caminar o manipular el móvil casualmente).
- **Entorno de Prueba:** De ambas formas (con la pantalla apagada/bloqueada y con la pantalla encendida dentro de la app).
- **Dispositivo Principal:** Marca **Tecno** (Capa de personalización **HiOS**, basada en Android, con optimizador de batería Phone Master / Battery Lab y sensor de proximidad binario o virtual).

---

## 1. Visión General del Problema y Causa Raíz

Tras el análisis exhaustivo del código, se identificaron cuatro cuellos de botella que provocaban el fallo reportado por el usuario:

```
                            [ Sacudida con la mano ]
                                       │
                                       ▼
  ┌────────────────────────────────────────────────────────────────────────┐
  │ FALLO 1: Sensor de Proximidad en Tecno                                 │
  │ Condición errónea: `distance < 3.0f || distance < maxRange`            │
  │ En Tecno con sensor binario (maxRange=1.0cm), libre reporta 1.0cm.     │
  │ Como 1.0 < 3.0 es VERDADERO, la app asume que está tapado en bolsillo. │
  └───────────────────────────────────┬────────────────────────────────────┘
                                       │ (Bloqueaba el 100% de los intentos)
                                       ▼
  ┌────────────────────────────────────────────────────────────────────────┐
  │ FALLO 2: Supresión Falsa en VehicularBumpFilter                        │
  │ El sensor muestrea a 20-50 Hz. Una sacudida humana genera 3 muestras   │
  │ consecutivas en <100 ms. El filtro las contaba como 3 baches de moto   │
  │ y activaba un bloqueo temporal de 2 segundos.                          │
  └───────────────────────────────────┬────────────────────────────────────┘
                                       │
                                       ▼
  ┌────────────────────────────────────────────────────────────────────────┐
  │ FALLO 3: Pausa por Pantalla Encendida (screenOnPause)                  │
  │ Al probar la sacudida dentro de la app o mirando la pantalla, el       │
  │ detector se desactiva para ahorrar batería, sin aviso visual al usuario│
  └───────────────────────────────────┬────────────────────────────────────┘
                                       │
                                       ▼
  ┌────────────────────────────────────────────────────────────────────────┐
  │ FALLO 4: Políticas de Fondo en HiOS (Tecno Phone Master)               │
  │ HiOS bloquea inicios de actividad transparentes en segundo plano si    │
  │ no se configuran permisos de auto-arranque y pantalla completa.        │
  └────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Solución Técnica Propuesta

### A. Corrección del Sensor de Proximidad (`MotionWakeDetector.kt`)
* **Problema:** En teléfonos Tecno con sensor binario donde `maximumRange` es `1.0 cm` (o `5.0 cm`), evaluar `distance < 3.0f` siempre devuelve `true` cuando el sensor está despejado si el rango máximo es 1.0.
* **Solución Robusta:** 
  ```kotlin
  // El estado "CERCA" solo es válido si la distancia medida es menor al rango máximo
  // Y menor a 2.0 cm (para sensores continuos con rangos grandes).
  val isCurrentlyNear = if (maxRange <= 2.0f) {
      distance < maxRange // En sensores binarios (0 = cerca, 1 = lejos)
  } else {
      distance < 3.0f && distance < maxRange // En sensores con escala analógica/continua
  }
  ```

### B. Diferenciación de Sacudida Firme vs. Baches de Moto (`VehicularBumpFilter.kt` y `MotionWakeDetector.kt`)
* **Problema:** Un bache vehicular produce aceleración errática en múltiples ejes o vibración mecánica de alta frecuencia, mientras que una **sacudida humana deliberada** consiste en un golpe de ida y vuelta claro con inversión de signo en el eje dominante (vector X o Y).
* **Solución:**
  1. En `VehicularBumpFilter`: No contar muestras de acelerómetro consecutivas como impactos independientes. Exigir un tiempo mínimo de separación entre impactos de baches (>180 ms) para no confundir una curva de aceleración continua de una sacudida con una ráfaga de baches.
  2. En `MotionWakeDetector`: Ajustar el algoritmo de agitación para que requiera **2 picos firmes en sentido opuesto** (ida y vuelta deliberada) dentro de una ventana de 200 ms a 650 ms, con una aceleración neta de $\Delta acc \ge 14.5\text{ m/s}^2$ (firme y controlada).
  3. No abortar la sacudida por fluctuaciones dinámicas del eje Z durante el movimiento rápido.

### C. Soporte para Pruebas en Vivo con Pantalla Encendida
* En la pantalla principal (`DashboardScreen`) y en `GesturesScreen`, incorporar un **Modo de Calibración / Prueba de Sacudida en Vivo**:
  * Cuando el usuario está dentro de la app observando la pantalla, la app detectará la sacudida y proporcionará **retroalimentación háptica inmediata (vibración) y visual** (tarjeta iluminada en verde "¡Gesto Firme Reconocido!"), demostrando que el sensor responde perfectamente sin forzar a apagar la pantalla a ciegas.
  * Cuando la pantalla se apague, el servicio activará el encendido real mediante `WakeLock` y `WakeScreenActivity`.

### D. Optimización para Tecno (HiOS) y Android 10-15
* En `WakeScreenActivity`: Asegurar banderas `turnScreenOn` y `showWhenLocked` con respaldo de `PowerManager.SCREEN_BRIGHT_WAKE_LOCK or ACQUIRE_CAUSES_WAKEUP`.
* En `GuideScreen`: Añadir una tarjeta instructiva específica para **Tecno / Infinix (HiOS / XOS)** explicando cómo fijar la app en el administrador de tareas (candado) y desactivar las restricciones en **Phone Master -> Batería -> Inicio automático**.

---

## 3. Diagrama de Arquitectura del Módulo Reparado

```
┌────────────────────────────────────────────────────────────────────────┐
│                        MotionWakeDetector.kt                           │
├────────────────────────────────────────────────────────────────────────┤
│ 1. Filtro de Proximidad Binario/Analógico para Tecno (Sin falsos CERCA)│
│ 2. Detección de Inversión de Signo (+X/-X o +Y/-Y) en 200-650ms       │
│ 3. Umbral Firme: DeltaAcc > 14.5 m/s² (Sin disparos accidentales)      │
│ 4. Modo Test en App: Emite evento visual/háptico si la pantalla está ON│
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ (Gesto humano verificado)
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        WakeMotionService.kt                            │
├────────────────────────────────────────────────────────────────────────┤
│ - Si pantalla está APAGADA:                                            │
│   * WakeLock (SCREEN_BRIGHT_WAKE_LOCK or ACQUIRE_CAUSES_WAKEUP)       │
│   * Dispara WakeScreenActivity con setShowWhenLocked/turnScreenOn      │
│   * Vibración de confirmación                                          │
│   * Registro en Room Database (Dispatchers.IO)                         │
│ - Si pantalla está ENCENDIDA en la app:                                │
│   * Emite evento en vivo para feedback visual en tiempo real           │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Archivos Involucrados y Cambios Concretos

1. **`app/src/main/java/com/example/sensor/MotionWakeDetector.kt`**:
   - Corrección de la lectura de proximidad para sensores de Tecno (`maxRange <= 2.0f`).
   - Algoritmo de agitación con doble pico de signo alterno para sacudida deliberada y firme.
   - Manejo del estado de prueba interactiva cuando la pantalla está encendida.
   - Comentarios explicativos en español en toda la lógica.

2. **`app/src/main/java/com/example/sensor/VehicularBumpFilter.kt`**:
   - Corrección para no tratar lecturas consecutivas de alta frecuencia (<180 ms) como baches separados de moto.
   - Permitir oscilaciones dinámicas de la aceleración durante la sacudida humana.
   - Comentarios explicativos en español.

3. **`app/src/main/java/com/example/service/WakeMotionService.kt`**:
   - Comunicación de eventos de sacudida detectados cuando la pantalla está activa para la interfaz de prueba.
   - Fortalecimiento de la adquisición de WakeLock compatible con capas como HiOS de Tecno.
   - Comentarios explicativos en español.

4. **`app/src/main/java/com/example/ui/screens/DashboardScreen.kt`**:
   - Visualización interactiva que notifica de inmediato cuando se reconoce una sacudida firme mientras la app está abierta ("¡Sacudida Detectada con Éxito!").

5. **`app/src/main/java/com/example/ui/screens/GuideScreen.kt`**:
   - Instrucciones específicas paso a paso para usuarios de **Tecno / Infinix (HiOS)** para evitar que el sistema cierre el servicio en segundo plano.

6. **`app/src/test/java/com/example/ExampleUnitTest.kt`**:
   - Pruebas unitarias de regresión para validar el nuevo cálculo de proximidad y la discriminación de sacudida firme frente a baches vehiculares.

---

## 5. Protocolo de Verificación

* **Compilación:** Ejecutar `compile_applet` para asegurar cero errores de tipos, sintaxis y compatibilidad con Android 8.0+.
* **Pruebas Unitarias:** Ejecutar `gradle :app:testDebugUnitTest` para certificar que el algoritmo matemático aprueba la sacudida firme y rechaza los baches vehiculares.
