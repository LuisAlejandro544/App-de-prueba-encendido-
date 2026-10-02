#!/usr/bin/env bash
# ==============================================================================
# Script de Generación de Firma Automática para Compilación Debug en CI/CD
# ==============================================================================
# Este script crea de forma 100% no interactiva y autónoma el archivo 'debug.keystore'
# requerido para firmar el APK Debug en entornos de GitHub Actions o compilación local.
#
# Características:
# 1. No espera respuestas por teclado (-noprompt).
# 2. Utiliza las credenciales estándar de debug de Android:
#    - Alias: androiddebugkey
#    - Contraseña: android
# 3. Compatible con cualquier runner de Linux (Ubuntu, Debian) con OpenJDK/JDK instalado.
# ==============================================================================

set -euo pipefail

# Directorio raíz del proyecto (donde se ejecuta o ubica el script)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KEYSTORE_PATH="${SCRIPT_DIR}/debug.keystore"

echo "=========================================================="
echo "🛡️ WakeGuard: Comprobando y Generando Firma Debug para CI"
echo "=========================================================="

if [ -f "${KEYSTORE_PATH}" ]; then
    echo "✅ El archivo de firma '${KEYSTORE_PATH}' ya existe. No es necesario regenerarlo."
    exit 0
fi

# Verificar si keytool está presente en el entorno
if ! command -v keytool &> /dev/null; then
    echo "❌ Error: 'keytool' no está instalado en el sistema. Asegúrate de tener JDK instalado."
    exit 1
fi

echo "🔑 Creando nuevo 'debug.keystore' de forma automática y no interactiva..."

keytool -genkeypair \
    -alias "androiddebugkey" \
    -keypass "android" \
    -keystore "${KEYSTORE_PATH}" \
    -storepass "android" \
    -dname "CN=Android Debug, O=Android, C=US" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -noprompt

if [ -f "${KEYSTORE_PATH}" ]; then
    echo "🎉 ¡Firma 'debug.keystore' generada con éxito en: ${KEYSTORE_PATH}!"
    ls -lh "${KEYSTORE_PATH}"
else
    echo "❌ Error: No se pudo generar el archivo 'debug.keystore'."
    exit 1
fi

echo "=========================================================="
echo "✨ Listo para compilar el APK Debug sin dependencias externas"
echo "=========================================================="
