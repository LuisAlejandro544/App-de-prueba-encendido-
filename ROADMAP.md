# 🗺️ Roadmap de WakeGuard

Este documento define la trayectoria técnica y de producto para el ciclo de vida de **WakeGuard**.

---

## 📌 Fase 1: Núcleo y Estabilidad Base (✅ COMPLETADO)
- [x] **Configuración del Entorno:**
  - [x] Soporte para Android 8.0 Oreo (`minSdk = 26`) hasta Android 16 (`targetSdk = 36`).
  - [x] Arquitecturas compatibles para 32-bit (`armeabi-v7a`) y 64-bit (`arm64-v8a`).
  - [x] Icono adaptativo personalizado con capa de 66dp segura y recursos rasterizados PNG32.
- [x] **Motor de Detección de Sensores:**
  - [x] Algoritmo para agitación (*Shake*) con filtrado de picos opuestos.
  - [x] Algoritmo para elevación e inclinación (*Lift to Wake*).
  - [x] Algoritmo de extracción de bolsillo (*Pocket Wake*).
  - [x] Escudo de proximidad (*Pocket Guard*) para descartar falsos positivos.
- [x] **Servicio en Primer Plano (Foreground Service):**
  - [x] Canal de notificación persistente de baja intrusión (`IMPORTANCE_LOW`).
  - [x] Acciones rápidas de Pausar / Reanudar desde la notificación.
  - [x] WakeLock seguro con temporizador de liberación garantizado (1500 ms).
  - [x] Actividad ultraligera `WakeScreenActivity` (`setShowWhenLocked` y `setTurnScreenOn`).
- [x] **Persistencia Local con Room:**
  - [x] Entidad `WakeEvent`, DAO reactivo con `Flow<T>` y `WakeRepository`.
  - [x] Persistencia de configuración con `UserPreferences` y `StateFlow`.
- [x] **Diseño Modular UI (5 Pantallas Jetpack Compose):**
  - [x] Pantalla de Control y Telemetría en Vivo (Visualizador de aceleración X, Y, Z e inclinación).
  - [x] Pantalla de Gestos y Sensibilidad interactiva.
  - [x] Pantalla de Batería Eco (Pausa con pantalla activa, modo noche, límite bajo).
  - [x] Pantalla de Estadísticas y Salud del Botón Físico.
  - [x] Pantalla de Guía de Fabricantes y Consejos de Cuidado Mecánico.

---

## 🚀 Fase 2: Experiencia, Automatización Pasiva y CI/CD (✅ COMPLETADO)
- [x] **Encendido Programado de Rescate (Exclusivo al 0%):**
  - [x] Ranura única para configurar hora exacta de auto-encendido.
  - [x] Lógica de no-intervención con carga normal (0 molestias ni despertares involuntarios).
  - [x] Detección de batería <= 1% y `ACTION_SHUTDOWN` para armar la alarma `RTC_WAKEUP`.
  - [x] Integración de enlace directo con el encendido de hardware RTC del fabricante (MediaTek / MIUI / Samsung).
  - [x] Receptor `ScheduledWakeReceiver` con condición de guarda de batería.
- [x] **Supresión Pasiva de Baches Vehiculares y Viajes en Moto (Zero-Config):**
  - [x] Clase `VehicularBumpFilter` para descarte de impactos repetitivos de carretera.
  - [x] Análisis temporal de ráfagas continuas (>2 picos en <1.5s) con ventana de calma de 2 segundos.
  - [x] Filtro de postura fisiológica (descarta sacudidas si la pantalla está boca abajo en un koala o bolso).
  - [x] Supresión 100% silenciosa (sin vibración, sin pantalla encendida, sin necesidad de activar modos manuales).
- [x] **Compilación Limpia y Automatización CI/CD:**
  - [x] Eliminación de dependencias de archivos `.env` en Gradle.
  - [x] Script `generate_signature.sh` no interactivo para crear `debug.keystore` al vuelo sin esperar intervención.
  - [x] Workflow de GitHub Actions (`.github/workflows/build-debug-apk.yml`) con compilación de APK Debug y soporte para caché de Gradle manual y opcional.

---

## 📈 Fase 3: Analítica de Desgaste y Personalización (Próximo)
- [ ] **Asistente de Calibración en Vivo:**
  - [ ] Test guiado donde el usuario agita el teléfono 3 veces y la app calcula automáticamente su umbral óptimo según su fuerza de agarre.
- [ ] **Tile en Ajustes Rápidos (Quick Settings Tile):**
  - [ ] Botón en la cortina de notificaciones de Android para pausar/reanudar WakeGuard con un toque.
- [ ] **Gráfico Semanal de Ahorro:**
  - [ ] Gráfico de barras nativo en Compose que muestre cuántas pulsaciones físicas se han evitado cada día de la semana.
- [ ] **Patrones Hápticos Personalizados:**
  - [ ] Permitir elegir entre vibración suave, doble pulso o silencio total al encender la pantalla.

---

## 🌐 Fase 4: Optimización para Distribución Externa (Uptodown / F-Droid / APK Directo)
- [ ] **Generación de Builds Reproducibles:**
  - [ ] Verificación de APK firmado sin dependencias de servicios privativos de Google Play Store obligatorios.
  - [ ] Verificación de licencias de dependencias 100% compatibles con código cerrado o libre distribución.
- [ ] **Comprobador de Sensores al Primer Inicio:**
  - [ ] Diagnóstico de hardware automático que detecta si el dispositivo cuenta con acelerómetro, giroscopio y sensor de proximidad real o virtual (ultrasónico).
