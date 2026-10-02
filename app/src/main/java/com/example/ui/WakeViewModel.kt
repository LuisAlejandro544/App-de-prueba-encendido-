package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.WakeApplication
import com.example.data.UserPreferences
import com.example.data.WakeEvent
import com.example.data.WakeRepository
import com.example.receiver.WakeScreenActivity
import com.example.sensor.SensorLiveSnapshot
import com.example.service.WakeMotionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * WakeViewModel: ViewModel central que conecta la base de datos Room,
 * las preferencias del usuario, el servicio de segundo plano y los sensores.
 */
class WakeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as WakeApplication
    val repository: WakeRepository = app.repository
    val preferences: UserPreferences = app.preferences

    // Estado reactivo del servicio en ejecución
    val isServiceRunning: StateFlow<Boolean> = WakeMotionService.serviceRunningState

    // Datos reactivos de Room Database
    val totalSavedClicks: StateFlow<Int> = repository.totalSavedClicks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val recentEvents: StateFlow<List<WakeEvent>> = repository.recentEvents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shakeCount: StateFlow<Int> = repository.shakeCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val liftCount: StateFlow<Int> = repository.liftCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pocketCount: StateFlow<Int> = repository.pocketCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Preferencias reactivas
    val gestureMode = preferences.gestureMode
    val sensitivity = preferences.sensitivity
    val pocketProtection = preferences.pocketProtection
    val vibrateOnWake = preferences.vibrateOnWake
    val cooldownSeconds = preferences.cooldownSeconds
    val screenOnPause = preferences.screenOnPause
    val nightModeEnabled = preferences.nightModeEnabled
    val nightStartHour = preferences.nightStartHour
    val nightEndHour = preferences.nightEndHour
    val lowBatteryPause = preferences.lowBatteryPause
    val lowBatteryThreshold = preferences.lowBatteryThreshold
    val ecoSampling = preferences.ecoSampling
    val startOnBoot = preferences.startOnBoot

    // Encendido programado de rescate (Solo actúa ante 0% de batería, nunca con carga normal)
    val rescueWakeEnabled = preferences.rescueWakeEnabled
    val rescueWakeHour = preferences.rescueWakeHour
    val rescueWakeMinute = preferences.rescueWakeMinute
    val rescueArmed = preferences.rescueArmed

    // Snapshot en vivo de acelerómetro y proximidad para visualizador
    private val _liveSensorData = MutableStateFlow(SensorLiveSnapshot())
    val liveSensorData: StateFlow<SensorLiveSnapshot> = _liveSensorData.asStateFlow()

    // Evento observable en tiempo real cuando se detecta una sacudida con la app abierta
    val inAppShakeEvent: StateFlow<Pair<Long, Float>?> = WakeMotionService.inAppShakeDetectedEvent

    // Estados reactivos de permisos avanzados
    private val _isDeviceAdminActive = MutableStateFlow(false)
    val isDeviceAdminActive: StateFlow<Boolean> = _isDeviceAdminActive.asStateFlow()

    private val _isOverlayPermissionGranted = MutableStateFlow(false)
    val isOverlayPermissionGranted: StateFlow<Boolean> = _isOverlayPermissionGranted.asStateFlow()

    // Estado de la prueba interactiva del botón
    private val _testCountdown = MutableStateFlow<Int?>(null)
    val testCountdown: StateFlow<Int?> = _testCountdown.asStateFlow()

    init {
        checkAdvancedPermissions(application)

        // Corrutina en segundo plano para leer datos del sensor activo del servicio periódicamente
        viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                val detector = WakeMotionService.currentDetectorInstance
                if (detector != null) {
                    _liveSensorData.value = detector.liveSnapshot.value
                }
                delay(80) // ~12 fps para actualización fluida de medidores en UI sin gastar CPU
            }
        }
    }

    /**
     * Comprueba si el Administrador de Dispositivo y el permiso de superposición están habilitados.
     */
    fun checkAdvancedPermissions(context: Context) {
        _isDeviceAdminActive.value = com.example.receiver.WakeDeviceAdminReceiver.isDeviceAdminActive(context)
        _isOverlayPermissionGranted.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Lanza la pantalla de configuración del sistema para activar el Administrador de Dispositivos.
     */
    fun requestDeviceAdmin(context: Context) {
        val intent = com.example.receiver.WakeDeviceAdminReceiver.createAddAdminIntent(context).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Lanza la pantalla de ajustes de Android para conceder permiso de Mostrar sobre otras aplicaciones.
     */
    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    /**
     * Alterna el encendido o apagado del servicio en primer plano.
     */
    fun toggleService(context: Context) {
        val currentlyRunning = isServiceRunning.value
        val intent = Intent(context, WakeMotionService::class.java)

        if (currentlyRunning) {
            intent.action = WakeMotionService.ACTION_STOP
            context.startService(intent)
            preferences.setServiceEnabled(false)
        } else {
            intent.action = WakeMotionService.ACTION_START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            preferences.setServiceEnabled(true)
        }
    }

    /**
     * Ejecuta una prueba rápida de encendido con cuenta regresiva.
     */
    fun startWakeTest(context: Context) {
        viewModelScope.launch {
            for (i in 3 downTo 1) {
                _testCountdown.value = i
                delay(1000)
            }
            _testCountdown.value = 0

            // Disparar encendido de pantalla de prueba
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            try {
                @Suppress("DEPRECATION")
                val wakeLock = powerManager?.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                    "WakeGuard:TestWake"
                )
                wakeLock?.acquire(1500L)
            } catch (_: Exception) {}

            try {
                val wakeIntent = Intent(context, WakeScreenActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(wakeIntent)
            } catch (_: Exception) {}

            // Registrar evento de prueba en la base de datos
            repository.recordWakeEvent("TEST", 9.8f, 100)

            delay(1200)
            _testCountdown.value = null
        }
    }

    fun dismissTestDialog() {
        _testCountdown.value = null
    }

    // Setters de configuración
    fun setGestureMode(mode: String) = preferences.setGestureMode(mode)
    fun setSensitivity(value: Float) = preferences.setSensitivity(value)
    fun setPocketProtection(enabled: Boolean) = preferences.setPocketProtection(enabled)
    fun setVibrateOnWake(enabled: Boolean) = preferences.setVibrateOnWake(enabled)
    fun setCooldownSeconds(seconds: Int) = preferences.setCooldownSeconds(seconds)
    fun setScreenOnPause(enabled: Boolean) = preferences.setScreenOnPause(enabled)
    fun setNightModeEnabled(enabled: Boolean) = preferences.setNightModeEnabled(enabled)
    fun setNightHours(start: Int, end: Int) = preferences.setNightHours(start, end)
    fun setLowBatteryPause(enabled: Boolean) = preferences.setLowBatteryPause(enabled)
    fun setLowBatteryThreshold(threshold: Int) = preferences.setLowBatteryThreshold(threshold)
    fun setEcoSampling(eco: Boolean) = preferences.setEcoSampling(eco)
    fun setStartOnBoot(enabled: Boolean) = preferences.setStartOnBoot(enabled)

    fun setRescueWakeEnabled(enabled: Boolean, context: Context) {
        preferences.setRescueWakeEnabled(enabled)
        if (!enabled) {
            // Cancelar cualquier alarma previa
            com.example.receiver.ScheduledWakeReceiver.cancelarAlarmaRescate(context)
            preferences.setRescueArmed(false)
        }
    }

    fun setRescueWakeTime(hour: Int, minute: Int) {
        preferences.setRescueWakeTime(hour, minute)
    }

    /**
     * Intenta abrir la pantalla nativa del fabricante de "Encendido y Apagado Programado"
     * soportada a nivel de chip RTC (muy común en teléfonos MediaTek, Xiaomi y Samsung).
     */
    fun abrirAjustesEncendidoHardware(context: Context): Boolean {
        val intents = listOf(
            Intent("com.android.settings.SCHEDULE_POWER_ON_OFF"),
            Intent().setClassName("com.android.settings", "com.android.settings.Settings\$SchedulePowerOnOffActivity"),
            Intent().setClassName("com.android.settings", "com.android.settings.AutoPowerOnOffSettings"),
            Intent().setClassName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.PowerHideSettings"),
            Intent(android.provider.Settings.ACTION_SETTINGS)
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) {}
        }
        return false
    }

    fun clearStats() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
