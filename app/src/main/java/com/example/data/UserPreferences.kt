package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * UserPreferences: Gestión de configuración de usuario con persistencia en SharedPreferences.
 * Expone StateFlow reactivos para que tanto la interfaz de Compose como el servicio
 * en segundo plano se sincronicen al instante ante cualquier cambio sin reiniciar.
 */
class UserPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("wake_motion_prefs", Context.MODE_PRIVATE)

    // Estado del servicio (Encendido / Apagado)
    private val _isServiceEnabled = MutableStateFlow(prefs.getBoolean(KEY_SERVICE_ENABLED, false))
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

    // Modo de gesto: "SHAKE", "LIFT", "POCKET", "ANY"
    private val _gestureMode = MutableStateFlow(prefs.getString(KEY_GESTURE_MODE, "SHAKE") ?: "SHAKE")
    val gestureMode: StateFlow<String> = _gestureMode.asStateFlow()

    // Sensibilidad (1.0 = Baja, 2.5 = Media, 4.0 = Alta, 5.0 = Muy Alta)
    private val _sensitivity = MutableStateFlow(prefs.getFloat(KEY_SENSITIVITY, 2.5f))
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()

    // Protección de bolsillo mediante sensor de proximidad
    private val _pocketProtection = MutableStateFlow(prefs.getBoolean(KEY_POCKET_PROTECTION, true))
    val pocketProtection: StateFlow<Boolean> = _pocketProtection.asStateFlow()

    // Vibración de retroalimentación háptica al encender
    private val _vibrateOnWake = MutableStateFlow(prefs.getBoolean(KEY_VIBRATE_ON_WAKE, true))
    val vibrateOnWake: StateFlow<Boolean> = _vibrateOnWake.asStateFlow()

    // Tiempo de enfriamiento en segundos entre activaciones (Cooldown para evitar encendidos repetidos)
    private val _cooldownSeconds = MutableStateFlow(prefs.getInt(KEY_COOLDOWN_SECONDS, 5))
    val cooldownSeconds: StateFlow<Int> = _cooldownSeconds.asStateFlow()

    // Pausar detección cuando la pantalla ya está encendida (AHORRO CRÍTICO DE BATERÍA)
    private val _screenOnPause = MutableStateFlow(prefs.getBoolean(KEY_SCREEN_ON_PAUSE, true))
    val screenOnPause: StateFlow<Boolean> = _screenOnPause.asStateFlow()

    // Modo nocturno / Horario de descanso (evita encendidos mientras se duerme)
    private val _nightModeEnabled = MutableStateFlow(prefs.getBoolean(KEY_NIGHT_MODE_ENABLED, true))
    val nightModeEnabled: StateFlow<Boolean> = _nightModeEnabled.asStateFlow()

    private val _nightStartHour = MutableStateFlow(prefs.getInt(KEY_NIGHT_START_HOUR, 23))
    val nightStartHour: StateFlow<Int> = _nightStartHour.asStateFlow()

    private val _nightEndHour = MutableStateFlow(prefs.getInt(KEY_NIGHT_END_HOUR, 7))
    val nightEndHour: StateFlow<Int> = _nightEndHour.asStateFlow()

    // Pausar automáticamente si la batería está baja
    private val _lowBatteryPause = MutableStateFlow(prefs.getBoolean(KEY_LOW_BATTERY_PAUSE, true))
    val lowBatteryPause: StateFlow<Boolean> = _lowBatteryPause.asStateFlow()

    private val _lowBatteryThreshold = MutableStateFlow(prefs.getInt(KEY_LOW_BATTERY_THRESHOLD, 15))
    val lowBatteryThreshold: StateFlow<Int> = _lowBatteryThreshold.asStateFlow()

    // Modo de muestreo del sensor Eco (SENSOR_DELAY_NORMAL) vs Fluido (SENSOR_DELAY_UI)
    private val _ecoSampling = MutableStateFlow(prefs.getBoolean(KEY_ECO_SAMPLING, true))
    val ecoSampling: StateFlow<Boolean> = _ecoSampling.asStateFlow()

    // Iniciar con el sistema al encender el teléfono (Auto-arranque)
    private val _startOnBoot = MutableStateFlow(prefs.getBoolean(KEY_START_ON_BOOT, false))
    val startOnBoot: StateFlow<Boolean> = _startOnBoot.asStateFlow()

    // Encendido Programado de Rescate al 0% (Solo interviene ante descarga total, NUNCA apaga)
    private val _rescueWakeEnabled = MutableStateFlow(prefs.getBoolean(KEY_RESCUE_WAKE_ENABLED, false))
    val rescueWakeEnabled: StateFlow<Boolean> = _rescueWakeEnabled.asStateFlow()

    private val _rescueWakeHour = MutableStateFlow(prefs.getInt(KEY_RESCUE_WAKE_HOUR, 7))
    val rescueWakeHour: StateFlow<Int> = _rescueWakeHour.asStateFlow()

    private val _rescueWakeMinute = MutableStateFlow(prefs.getInt(KEY_RESCUE_WAKE_MINUTE, 0))
    val rescueWakeMinute: StateFlow<Int> = _rescueWakeMinute.asStateFlow()

    private val _rescueArmed = MutableStateFlow(prefs.getBoolean(KEY_RESCUE_ARMED, false))
    val rescueArmed: StateFlow<Boolean> = _rescueArmed.asStateFlow()

    fun setServiceEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SERVICE_ENABLED, enabled).apply()
        _isServiceEnabled.value = enabled
    }

    fun setGestureMode(mode: String) {
        prefs.edit().putString(KEY_GESTURE_MODE, mode).apply()
        _gestureMode.value = mode
    }

    fun setSensitivity(value: Float) {
        prefs.edit().putFloat(KEY_SENSITIVITY, value).apply()
        _sensitivity.value = value
    }

    fun setPocketProtection(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_POCKET_PROTECTION, enabled).apply()
        _pocketProtection.value = enabled
    }

    fun setVibrateOnWake(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VIBRATE_ON_WAKE, enabled).apply()
        _vibrateOnWake.value = enabled
    }

    fun setCooldownSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_COOLDOWN_SECONDS, seconds).apply()
        _cooldownSeconds.value = seconds
    }

    fun setScreenOnPause(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCREEN_ON_PAUSE, enabled).apply()
        _screenOnPause.value = enabled
    }

    fun setNightModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NIGHT_MODE_ENABLED, enabled).apply()
        _nightModeEnabled.value = enabled
    }

    fun setNightHours(startHour: Int, endHour: Int) {
        prefs.edit()
            .putInt(KEY_NIGHT_START_HOUR, startHour)
            .putInt(KEY_NIGHT_END_HOUR, endHour)
            .apply()
        _nightStartHour.value = startHour
        _nightEndHour.value = endHour
    }

    fun setLowBatteryPause(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOW_BATTERY_PAUSE, enabled).apply()
        _lowBatteryPause.value = enabled
    }

    fun setLowBatteryThreshold(threshold: Int) {
        prefs.edit().putInt(KEY_LOW_BATTERY_THRESHOLD, threshold).apply()
        _lowBatteryThreshold.value = threshold
    }

    fun setEcoSampling(eco: Boolean) {
        prefs.edit().putBoolean(KEY_ECO_SAMPLING, eco).apply()
        _ecoSampling.value = eco
    }

    fun setStartOnBoot(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_START_ON_BOOT, enabled).apply()
        _startOnBoot.value = enabled
    }

    fun setRescueWakeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RESCUE_WAKE_ENABLED, enabled).apply()
        _rescueWakeEnabled.value = enabled
    }

    fun setRescueWakeTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_RESCUE_WAKE_HOUR, hour)
            .putInt(KEY_RESCUE_WAKE_MINUTE, minute)
            .apply()
        _rescueWakeHour.value = hour
        _rescueWakeMinute.value = minute
    }

    fun setRescueArmed(armed: Boolean) {
        prefs.edit().putBoolean(KEY_RESCUE_ARMED, armed).apply()
        _rescueArmed.value = armed
    }

    companion object {
        private const val KEY_SERVICE_ENABLED = "key_service_enabled"
        private const val KEY_GESTURE_MODE = "key_gesture_mode"
        private const val KEY_SENSITIVITY = "key_sensitivity"
        private const val KEY_POCKET_PROTECTION = "key_pocket_protection"
        private const val KEY_VIBRATE_ON_WAKE = "key_vibrate_on_wake"
        private const val KEY_COOLDOWN_SECONDS = "key_cooldown_seconds"
        private const val KEY_SCREEN_ON_PAUSE = "key_screen_on_pause"
        private const val KEY_NIGHT_MODE_ENABLED = "key_night_mode_enabled"
        private const val KEY_NIGHT_START_HOUR = "key_night_start_hour"
        private const val KEY_NIGHT_END_HOUR = "key_night_end_hour"
        private const val KEY_LOW_BATTERY_PAUSE = "key_low_battery_pause"
        private const val KEY_LOW_BATTERY_THRESHOLD = "key_low_battery_threshold"
        private const val KEY_ECO_SAMPLING = "key_eco_sampling"
        private const val KEY_START_ON_BOOT = "key_start_on_boot"
        private const val KEY_RESCUE_WAKE_ENABLED = "key_rescue_wake_enabled"
        private const val KEY_RESCUE_WAKE_HOUR = "key_rescue_wake_hour"
        private const val KEY_RESCUE_WAKE_MINUTE = "key_rescue_wake_minute"
        private const val KEY_RESCUE_ARMED = "key_rescue_armed"
    }
}
