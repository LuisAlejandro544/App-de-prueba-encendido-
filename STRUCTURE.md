# 🏗️ Estructura del Proyecto WakeGuard

Este documento detalla la arquitectura de archivos, capas de software y el flujo de datos de **WakeGuard**.

---

## 🌳 Árbol de Archivos

```
WakeGuard/
├── .github/
│   └── workflows/
│       └── build-debug-apk.yml      # CI/CD: Descarga de código, firma autónoma, caché opcional y build de APK Debug
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml              # Declaración de permisos, servicios, actividades y receptores
│   │   │   ├── java/com/example/
│   │   │   │   ├── WakeApplication.kt          # Inicialización de dependencias y canal de notificación
│   │   │   │   ├── MainActivity.kt             # Punto de entrada de Compose y barra de navegación inferior
│   │   │   │   ├── data/
│   │   │   │   │   ├── WakeEvent.kt            # Entidad Room para el historial de encendidos
│   │   │   │   │   ├── WakeDao.kt              # Consultas reactivas (Flow) e inserción con Room
│   │   │   │   │   ├── AppDatabase.kt          # Base de datos Room (Singleton)
│   │   │   │   │   ├── WakeRepository.kt       # Abstracción de datos sobre Dispatchers.IO
│   │   │   │   │   └── UserPreferences.kt      # Almacén de configuración reactivo con StateFlow
│   │   │   │   ├── sensor/
│   │   │   │   │   ├── MotionWakeDetector.kt   # Orquestador de acelerómetro y proximidad
│   │   │   │   │   └── VehicularBumpFilter.kt  # Filtro pasivo anti-baches de moto y postura en bolso koala
│   │   │   │   ├── service/
│   │   │   │   │   └── WakeMotionService.kt    # Foreground Service para monitoreo continuo en segundo plano
│   │   │   │   ├── receiver/
│   │   │   │   │   ├── WakeScreenActivity.kt   # Actividad transparente para garantizar encendido en Android 8+
│   │   │   │   │   ├── BootCompletedReceiver.kt# Receptor para reanudar el servicio tras reiniciar el móvil
│   │   │   │   │   └── ScheduledWakeReceiver.kt# Receptor RTC_WAKEUP para auto-encendido exclusivo ante 0% de batería
│   │   │   │   └── ui/
│   │   │   │       ├── WakeViewModel.kt        # ViewModel MVVM con corrutinas y estado unificado
│   │   │   │       ├── theme/
│   │   │   │       │   ├── Color.kt            # Paleta de colores M3 de alto contraste tecnológico
│   │   │   │       │   ├── Theme.kt            # ColorScheme y temas Claro/Oscuro
│   │   │   │       │   └── Type.kt             # Tipografía Material 3
│   │   │   │       ├── navigation/
│   │   │   │       │   └── NavRoutes.kt        # Rutas e iconos de navegación inferior
│   │   │   │       ├── components/
│   │   │   │       │   └── SensorVisualizer.kt # Visualizador animado de telemetría de aceleración e inclinación
│   │   │   │       └── screens/
│   │   │   │           ├── DashboardScreen.kt  # Pantalla 1: Control Maestro, telemetría y prueba interactiva
│   │   │   │           ├── GesturesScreen.kt   # Pantalla 2: Tipos de gestos, sensibilidad y protección bolsillo
│   │   │   │           ├── BatteryScreen.kt    # Pantalla 3: Reglas de ahorro EcoShield y modo nocturno
│   │   │   │           ├── StatsScreen.kt      # Pantalla 4: Contador de clicks evitados y salud del botón
│   │   │   │           └── GuideScreen.kt      # Pantalla 5: Guía por marcas (Xiaomi/Samsung) y auto-arranque
│   │   │   └── res/
│   │   │       ├── drawable/
│   │   │       │   ├── app_icon_fg.jpg         # Imagen frontal generada para el icono adaptativo
│   │   │       │   ├── ic_launcher_background.xml # Fondo adaptativo (#0F172A)
│   │   │       │   └── ic_launcher_foreground.xml # Layer-list centrado en zona segura de 66dp
│   │   │       ├── mipmap-*/                   # Bitmaps PNG32 para compatibilidad estándar
│   │   │       └── values/
│   │   │           └── strings.xml             # Textos y nombres de recursos
│   │   └── test/java/com/example/
│   │       ├── ExampleRobolectricTest.kt       # Prueba unitaria local de recursos con Robolectric
│   │       └── ExampleUnitTest.kt              # Pruebas unitarias de lógica y filtro vehicular de baches
│   └── build.gradle.kts                        # Configuración de compilación limpia (sin dependencia de .env)
├── gradle/
│   ├── libs.versions.toml                      # Catálogo centralizado de versiones y librerías
│   └── wrapper/
│       ├── gradle-wrapper.jar                  # Binario del wrapper de Gradle
│       └── gradle-wrapper.properties           # Especificación oficial de versión Gradle
├── generate_signature.sh                       # Script bash autónomo para generar debug.keystore en CI/CD
├── gradlew                                     # Wrapper ejecutable para Linux/macOS
├── gradlew.bat                                 # Wrapper ejecutable para Windows
├── metadata.json                               # Metadatos del entorno AI Studio
├── README.md                                   # Documentación general del proyecto
├── ROADMAP.md                                  # Hitos y futuras versiones
├── STRUCTURE.md                                # Estructura y arquitectura de software (este archivo)
├── AI_CONTEXT.md                               # Contexto de dominio para asistentes de IA
└── AGENTS.md                                   # Instrucciones operativas y directrices
```

---

## 🔄 Flujo de Datos y Arquitectura de Sensores

```
                        [ Sensores Físicos ]
                    (Acelerómetro / Proximidad)
                                │
                                ▼
                     [ MotionWakeDetector ]
                                │
                    ┌───────────┴───────────┐
                    ▼                       ▼
          [ Escudo Proximidad ]    [ VehicularBumpFilter ]
          (Cerca / Lejos en bolso)  (Cadencia de baches y ángulo Z)
                    │                       │
                    └───────────┬───────────┘
                                ▼
                     ¿Es gesto humano válido?
                             /     \
                          (NO)     (SÍ)
                           │         │
                           ▼         ▼
                      [ Silencio ]  [ WakeMotionService ]
                     (No vibra,      (Foreground Service)
                      No despierta)          │
                                ┌────────────┴────────────┐
                                ▼                         ▼
                     [ WakeLock + Activity ]     [ WakeRepository ]
                     (Ilumina pantalla)          (Guarda en Room IO)
```

---

## 🧩 Descripción de Componentes Clave

### 1. Detección Inteligente Pasiva (`sensor/`)
* **`VehicularBumpFilter.kt`**: Algoritmo autónomo de discriminación de baches y vibraciones de moto.
  * Mide la frecuencia de oscilación temporal: ante $\ge 3$ picos bruscos en menos de 1500 ms, activa una supresión temporal de 2 segundos.
  * Valida la postura del teléfono: si la pantalla apunta hacia el piso ($az < -1.5$ m/s²), descarta el golpe de inmediato.
  * Opera en silencio total sin consumir recursos del hilo principal.
* **`MotionWakeDetector.kt`**: Centraliza el acelerómetro y el sensor de proximidad aplicando el filtro vehicular, control de enfriamiento (*cooldown*) y el cálculo de gestos configurados.

### 2. Automatización y CI/CD
* **`generate_signature.sh`**: Script Bash no interactivo que genera de forma automática el archivo `debug.keystore` con `keytool` en cualquier entorno de integración continua, sin requerir contraseñas manuales.
* **`.github/workflows/build-debug-apk.yml`**: Flujo de GitHub Actions listo para descargar el código, ejecutar la firma autónoma, permitir caché de Gradle opcional/manual y empaquetar el APK Debug descargable.
* **`build.gradle.kts`**: Configurado para compilar de manera 100% limpia sin requerir archivos `.env` locales.
