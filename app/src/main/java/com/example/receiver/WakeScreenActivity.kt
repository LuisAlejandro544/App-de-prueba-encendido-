package com.example.receiver

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager

/**
 * WakeScreenActivity: Actividad transparente auxiliar para Android 8.0+ (API 26+).
 * 
 * En Android 8.0 y versiones superiores, la API de PowerManager.ACQUIRE_CAUSES_WAKEUP
 * en ocasiones no enciende la pantalla si el bloqueo de pantalla (Keyguard) tiene políticas
 * estrictas del fabricante.
 * 
 * Esta actividad invoca formalmente 'setShowWhenLocked(true)' y 'setTurnScreenOn(true)',
 * garantizando que la pantalla se ilumine y luego se cierra inmediatamente sin interrumpir
 * la pantalla de bloqueo ni la experiencia del usuario.
 */
class WakeScreenActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configuración para Android 8.0+ (API 26+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        // Cierra la actividad inmediatamente después de despertar la pantalla (200ms)
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) {
                finish()
            }
        }, 200)
    }
}
