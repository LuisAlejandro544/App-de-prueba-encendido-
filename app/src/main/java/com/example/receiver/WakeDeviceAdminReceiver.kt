package com.example.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * WakeDeviceAdminReceiver: Receptor de Administrador de Dispositivos oficial de Android.
 *
 * Utilidad y justificación técnica:
 * 1. A diferencia de los Servicios de Accesibilidad (que capas como HiOS de Tecno o MIUI desactivan
 *    automáticamente para ahorrar RAM), el permiso de Administrador de Dispositivo es una directiva
 *    formal a nivel de sistema que nunca es desactivada arbitrariamente por el sistema operativo.
 * 2. Otorga a la app el método 'DevicePolicyManager.lockNow()', permitiendo apagar y bloquear la pantalla
 *    al instante cuando el usuario sacude el teléfono mientras la pantalla está encendida.
 */
class WakeDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
    }

    companion object {
        /**
         * Obtiene el ComponentName representativo de este receptor.
         */
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, WakeDeviceAdminReceiver::class.java)
        }

        /**
         * Comprueba si el usuario ya ha activado WakeGuard como Administrador de Dispositivo.
         */
        fun isDeviceAdminActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            return dpm?.isAdminActive(getComponentName(context)) ?: false
        }

        /**
         * Bloquea y apaga la pantalla de inmediato mediante la API nativa de Android.
         * Devuelve true si el bloqueo se ejecutó con éxito.
         */
        fun lockScreen(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager ?: return false
            val adminComponent = getComponentName(context)
            return if (dpm.isAdminActive(adminComponent)) {
                try {
                    dpm.lockNow()
                    true
                } catch (_: Exception) {
                    false
                }
            } else {
                false
            }
        }

        /**
         * Crea el Intent del sistema para solicitar al usuario la activación del Administrador.
         */
        fun createAddAdminIntent(context: Context): Intent {
            return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, getComponentName(context))
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "WakeGuard necesita permisos de Administrador de Dispositivo exclusivamente para apagar y bloquear la pantalla cuando la sacudas, evitando el desgaste de tu botón físico."
                )
            }
        }
    }
}
