# 🤖 AGENTS.md - Directrices Operativas para Agentes de Desarrollo

Este documento establece las reglas obligatorias, restricciones de ingeniería y protocolos de trabajo para cualquier agente que desarrolle o mantenga este proyecto.

---

## ⚠️ Reglas Críticas de Cumplimiento Obligatorio

1. **Contexto del Usuario (Solo Teléfono):**
   * El usuario opera exclusivamente desde un dispositivo móvil (no tiene PC). Las soluciones deben ser autónomas, ejecutarse limpiamente dentro de Android y no requerir comandos manuales externos complejos.

2. **Prohibición de Propiedades del Sistema del Kernel:**
   * **NUNCA** utilices comandos o modificaciones basadas en `persist.sys.*` o modificaciones a nivel de root/build.prop que puedan poner en riesgo la estabilidad del firmware.

3. **Arquitecturas y Compatibilidad Multi-Plataforma:**
   * Asegurar que todo código sea compatible con arquitecturas de **32 bits (`armeabi-v7a`)** y **64 bits (`arm64-v8a`)**.
   * La versión mínima de Android del proyecto es **Android 8.0 Oreo (API 26)**. No subas ni bajes el `minSdk` sin requerimiento explícito.

4. **Uso de Dependencias y Licencias:**
   * No agregues librerías con licencias copyleft restrictivas (como GPL v3 o AGPL) que fuercen la liberación obligatoria de código o causen litigios legales al usuario. Prioriza licencias Apache 2.0 o MIT.
   * Las dependencias deben ser 100% funcionales y oficiales (evita paquetes ficticios o mocks incompletos).

5. **Nombres de Archivos y Marcas Registradas:**
   * Evita incluir nombres de marcas registradas o comerciales protegidas por derechos de autor en rutas de archivos o identificadores internos que puedan generar conflictos al usuario al publicar en tiendas de APKs alternativas (como Uptodown o F-Droid).

6. **Diseño de Interfaz: Modular y No Extremadamente Minimalista:**
   * Al usuario **no le gusta el minimalismo extremo** ni las interfaces donde todo esté amontonado en una sola pantalla.
   * Divide la aplicación en **pantallas dedicadas** accesibles mediante navegación inferior (*Bottom Navigation*), tarjetas informativas, barras de telemetría y explicaciones claras.
   * En cada cambio, **NO modifiques el diseño general ni los iconos** a menos que el usuario lo solicite expresamente.

7. **Comentarios y Legibilidad del Código:**
   * Para evitar confusiones, **cada archivo de código debe tener comentarios explicativos en español** que aclaren para qué sirve la lógica, los métodos y las clases que contiene.

8. **Gestión Inteligente de Corrutinas (No saturar el hilo principal):**
   * Las operaciones de base de datos Room, preferencias SharedPreferences y cálculos intensivos deben ejecutarse en `Dispatchers.IO` o `Dispatchers.Default`.
   * El hilo principal (`Dispatchers.Main`) queda reservado estrictamente para la composición visual de Jetpack Compose.

9. **Archivos de Commit:**
   * Si existe un archivo `commit_message.txt`, asegúrate de que su contenido esté en **español** y no lo actualices a menos que el usuario lo solicite explícitamente.

---

## 🛠️ Protocolo de Verificación

Antes de concluir cualquier cambio de código, el agente debe verificar la integridad del proyecto ejecutando:

1. `compile_applet`: Garantiza que el código Kotlin, recursos XML y Gradle sincronicen y compilen sin errores de sintaxis o tipos.
2. `gradle :app:testDebugUnitTest`: Ejecuta la suite de pruebas unitarias locales (Robolectric y JUnit).

Cualquier advertencia de lint o error de compilación debe ser corregido antes de entregar la respuesta final.
