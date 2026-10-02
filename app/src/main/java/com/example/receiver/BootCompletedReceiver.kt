package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.WakeApplication
import com.example.service.WakeMotionService

/**
 * BootCompletedReceiver: Inicia el servicio automáticamente si el dispositivo se reinicia
 * y el usuario tiene activada la opción de auto-arranque en la configuración.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val app = context.applicationContext as? WakeApplication ?: return
        val prefs = app.preferences

        if (prefs.isServiceEnabled.value && prefs.startOnBoot.value) {
            val serviceIntent = Intent(context, WakeMotionService::class.java).apply {
                action = WakeMotionService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
