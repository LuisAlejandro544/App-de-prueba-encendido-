# Supresión Automática de Baches Vehiculares y Movimiento en Bolso (Zero-Config)

Plan de arquitectura técnica para incorporar un algoritmo pasivo de discriminación de movimiento que ignore de forma 100% automática y silenciosa los baches de motocicleta, irregularidades del terreno y rebotes en bolsos o riñoneras (koalas), sin que el usuario tenga que cambiar modos, ajustar sensibilidades ni tocar ninguna configuración.

---

### Decisiones Críticas Confirmadas

- **Comportamiento 100% Silencioso:** Ante baches o vibraciones de moto, el teléfono simplemente ignora el estímulo. No emite vibración háptica, no despierta la pantalla y no genera notificaciones.
- **Cero Configuración Manual (Zero-Config):** No se añaden botones molestos de "Modo Moto" ni conmutadores manuales. El algoritmo opera de manera autónoma y transparente en segundo plano.
- **Sin Ruido en la Interfaz:** No requiere pantallas adicionales ni sobrecarga visual; la lógica se integra directamente en el motor de detección de movimiento de bajo consumo.

---

## 1. Visión General y Concepto Central

### ¿Qué problema resuelve?
Cuando un usuario viaja en motocicleta o bicicleta llevando el teléfono en un bolso, mochila o riñonera (koala), el pavimento irregular y los baches generan aceleraciones bruscas que un acelerómetro estándar confunde con una agitación deliberada (*shake*).

### La Solución Pasiva
El algoritmo diferencia la física de un gesto humano deliberado frente a la vibración vehicular mediante tres firmas físicas naturales:
1. **Firma de Traqueteo Continuo (*Rattle & Road Noise*):** Un bache o el motor de la moto genera oscilaciones repetitivas en ventanas menores a 400 ms. Un gesto humano para ver la pantalla es un impulso singular seguido de quietud.
2. **Ventana Fisiológica de Mirada (*Viewing Angle*):** Cuando un humano levanta el teléfono para mirarlo, este queda con la pantalla orientada hacia arriba con una inclinación entre 30° y 85°. En un bolso de moto el dispositivo oscila horizontal, invertido o de lado.
3. **Escudo de Proximidad con Verificación Doble:** Si el teléfono está en un bolso o koala, cualquier lectura de cercanía al inicio o 150 ms tras el impacto anula el despertar.

```
                    [ Impacto / Bache detectado ]
                                │
                                ▼
               ¿El sensor de proximidad detecta roce?
                             /        \
                          (SÍ)        (NO)
                           │            │
                           ▼            ▼
                   [ IGNORAR ]   ¿Hay ráfaga de >2 picos en <1.5s?
                    (Silencio)    (Traqueteo / vibración de moto)
                                      /        \
                                   (SÍ)        (NO)
                                    │            │
                                    ▼            ▼
                            [ IGNORAR ]   ¿Ángulo fisiológico de
                             (Silencio)     mirada hacia la cara?
                                                /        \
                                             (NO)        (SÍ)
                                              │            │
                                              ▼            ▼
                                      [ IGNORAR ]   [ DESPERTAR PANTALLA ]
                                       (Silencio)   (Solo gesto humano real)
```

---

## 2. Experiencia de Usuario y Cero Fricción

- **Transparencia Total:** El usuario no tiene que acordarse de activar un perfil antes de subir a la moto ni desactivarlo al bajarse.
- **Preservación de Batería:** Al descartar de inmediato los baches en la primera capa matemática del acelerómetro, se evita encender la pantalla en vano y se mantiene la tasa de muestreo ultra baja (~15 Hz Eco).
- **Consistencia Visual:** No se altera la paleta de colores, iconos ni las pantallas de navegación ya existentes en Jetpack Compose, respetando el principio de estabilidad de interfaz.

---

## 3. Decisiones Técnicas y Compensaciones (Trade-Offs)

### Decisión 1: Filtrado Temporal de Frecuencia (Ráfagas vs. Impulso Único)
- **Enfoque Elegido:** Medir la ventana temporal entre picos de aceleración. Si se registran $\ge 3$ cruces de umbral en menos de 1500 ms, se cataloga como "vibración ambiental/vehicular continua" y se bloquea el disparo durante esa ráfaga más un período de calma de 2 segundos.
- **Por qué:** Un bache de moto nunca viene solo; la moto sigue rodando con microvibraciones. Un humano que quiere ver la hora agita una vez y mira el teléfono fijamente.
- **Alternativa descartada:** Usar GPS o Activity Recognition API (detectar `IN_VEHICLE`), ya que consumiría batería alta por GPS, requeriría servicios de Google Play (incompatible con distribución limpia e independiente en Uptodown) y solicitaría permisos intrusivos de ubicación.

### Decisión 2: Ventana Angular Tridimensional de Gravedad
- **Enfoque Elegido:** Descomponer el vector de gravedad normalizado ($Z / g$) para corroborar que la pantalla esté mirando hacia el usuario (ángulo de lectura entre 30° y 85° respecto al plano horizontal) al momento de autorizar el encendido.
- **Por qué:** En un koala o bolso durante el trayecto, el teléfono descansa de canto o boca abajo. Aunque un bache fuerte supere el umbral de aceleración, el vector $Z$ no apuntará en la dirección del rostro humano.

### Decisión 3: Verificación de Proximidad Asíncrona (Double-Check)
- **Enfoque Elegido:** Muestrear el sensor de proximidad en el momento exacto del impacto y a los 150 ms posteriores. Si el teléfono está en un koala, el tejido o las paredes del bolso cubrirán el sensor en alguno de los dos instantes.

---

## 4. Arquitectura de Implementación

```
┌────────────────────────────────────────────────────────────────────────┐
│                        MotionWakeDetector.kt                           │
├────────────────────────────────────────────────────────────────────────┤
│ - val roadNoiseFilter: RoadNoiseFilter (análisis de ráfagas continuas)  │
│ - fun isFacingHumanEyes(gx, gy, gz): Boolean (validación de postura)   │
│ - fun isNearPocketOrBag(): Boolean (sensor de proximidad instantáneo) │
│ - fun onSensorChanged(event: SensorEvent)                              │
│     ├── 1. Proximidad activa -> Return (Descartar)                     │
│     ├── 2. Registro de pico en buffer circular temporal                │
│     ├── 3. Si ráfaga continua detectada (moto/baches) -> Return        │
│     ├── 4. Si ángulo fuera de postura humana -> Return                 │
│     └── 5. Gesto legítimo -> Disparar onWakeTriggered()               │
└────────────────────────────────────────────────────────────────────────┘
                                   │
                                   ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        WakeMotionService.kt                            │
├────────────────────────────────────────────────────────────────────────┤
│ - Recibe la señal limpia y verificada                                  │
│ - Ejecuta WakeLock + WakeScreenActivity sin vibraciones parásitas       │
│ - Registra el evento en Room Database sobre Dispatchers.IO             │
└────────────────────────────────────────────────────────────────────────┘
```

### Cambios a realizar en archivos de código:
1. **`app/src/main/java/com/example/sensor/MotionWakeDetector.kt`**:
   - Incorporar la lógica matemática de detección de ráfagas continuas (`RoadNoiseFilter`).
   - Añadir la validación del vector de gravedad para ángulo fisiológico de lectura.
   - Añadir comentarios detallados en español explicando cada fórmula física.
2. **`app/src/test/java/com/example/ExampleUnitTest.kt`**:
   - Pruebas unitarias locales simulando:
     a) Ráfaga de baches de moto (3 impactos en 800 ms) $\rightarrow$ Debe ser ignorada (`assertFalse`).
     b) Levantamiento natural con postura de lectura $\rightarrow$ Debe ser aceptado (`assertTrue`).

---

## 5. Verificación y Pruebas

- **Compilación Limpia:** Ejecutar `compile_applet` para garantizar que no existan errores de sintaxis, tipos ni dependencias.
- **Suite de Pruebas Unitarias:** Ejecutar `gradle :app:testDebugUnitTest` para validar que los escenarios de baches y gestos normales se comporten según las fórmulas físicas diseñadas.
