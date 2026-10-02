package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.AppDatabase
import com.example.data.UserPreferences
import com.example.data.WakeRepository

/**
 * WakeApplication: Clase de aplicación principal.
 * 
 * Propósito:
 * 1. Inicializar la base de datos local Room de forma diferida (Singleton).
 * 2. Registrar el canal de notificación requerido por Android 8.0+ (Oreo, API 26)
 *    para que el servicio en primer plano funcione legalmente sin ser destruido.
 * 3. Servir como punto central de inyección manual de dependencias livianas.
 */
class WakeApplication : Application() {

    // Instancia de base de datos Room para persistir estadísticas de pulsaciones evitadas
    val database by lazy { AppDatabase.getDatabase(this) }
    
    // Repositorio que maneja las operaciones con corrutinas e hilos de fondo
    val repository by lazy { WakeRepository(database.wakeDao()) }

    // Gestor de preferencias de usuario (sensibilidad, gestos, ahorro de energía)
    val preferences by lazy { UserPreferences(this) }

    override fun onCreate() {
        super.onCreate()
        crearCanalDeNotificacion()
    }

    /**
     * Crea el canal de notificación persistente obligatorio para Android 8.0+ (API 26+)
     * Usamos prioridad IMPORTANCE_LOW para que no emita sonidos molestos al usuario,
     * pero garantice que el sistema operativo no mate el proceso de monitoreo.
     */
    private fun crearCanalDeNotificacion() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(canal)
        }
    }

    companion object {
        const val CHANNEL_ID = "wake_motion_service_channel"
        const val NOTIFICATION_ID = 1001
    }
}
