package com.example.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.WakeApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * ScheduledWakeReceiver: Receptor de alarma de hardware (RTC_WAKEUP) para el
 * Encendido Programado de Rescate.
 * 
 * Regla de Oro (Solicitud del Usuario):
 * - Este mecanismo actúa ÚNICAMENTE como contingencia ante descarga total (0%) o apagado por batería.
 * - Si el teléfono tiene carga normal (> 1%) y está encendido, la alarma se descarta pacíficamente
 *   sin intervenir ni encender la pantalla.
 * - NUNCA apaga el teléfono bajo ninguna circunstancia.
 */
class ScheduledWakeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return

        val app = context.applicationContext as? WakeApplication ?: return
        val prefs = app.preferences

        // Si el usuario no tiene habilitada la función de rescate, ignorar
        if (!prefs.rescueWakeEnabled.value) return

        // 1. Verificar el nivel actual de batería
        val batteryStatus: Intent? = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val currentPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else 100

        val estabaArmadoPorDescarga = prefs.rescueArmed.value

        // 2. Condición de guarda: si el teléfono tiene carga normal y no estaba armado por descarga,
        // no intervenimos ni despertamos la pantalla.
        if (currentPct > 1 && !estabaArmadoPorDescarga) {
            return
        }

        // 3. Ejecutar despertar de pantalla de rescate
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        try {
            @Suppress("DEPRECATION")
            val wakeLock = powerManager?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "WakeGuard:RescueWakeScreen"
            )
            wakeLock?.acquire(1500L)
        } catch (_: Exception) {}

        // Abrir WakeScreenActivity para garantizar que la pantalla se ilumine
        try {
            val wakeIntent = Intent(context, WakeScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            }
            context.startActivity(wakeIntent)
        } catch (_: Exception) {}

        // Vibración háptica suave
        if (prefs.vibrateOnWake.value) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    v?.vibrate(100)
                }
            } catch (_: Exception) {}
        }

        // Registrar evento en la base de datos Room usando Dispatchers.IO
        CoroutineScope(Dispatchers.IO).launch {
            app.repository.recordWakeEvent("RESCUE_SCHEDULED", 9.8f, currentPct)
            // Desarmar bandera de rescate una vez atendido
            prefs.setRescueArmed(false)
        }
    }

    companion object {
        const val REQUEST_CODE_RESCUE = 2024

        /**
         * Programa la alarma en AlarmManager usando RTC_WAKEUP para la hora configurada.
         */
        fun programarAlarmaRescate(context: Context, hora: Int, minuto: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            val intent = Intent(context, ScheduledWakeReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_RESCUE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hora)
                set(Calendar.MINUTE, minuto)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // Si la hora ya pasó hoy, programarla para mañana a la misma hora
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            } catch (_: SecurityException) {
                // Fallback seguro en caso de restricciones de exact alarms en Android 12+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.set(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
            }
        }

        /**
         * Cancela la alarma programada.
         */
        fun cancelarAlarmaRescate(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ScheduledWakeReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_RESCUE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }
}
