package com.example.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.WakeApplication
import com.example.receiver.WakeScreenActivity
import com.example.sensor.MotionWakeDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * WakeMotionService: Servicio en primer plano (Foreground Service) que gestiona
 * el monitoreo del acelerómetro de forma eficiente para encender la pantalla.
 *
 * Estrategias de optimización de batería y usabilidad:
 * 1. Monitoreo pasivo continuo cuando la pantalla está apagada.
 * 2. Soporte para pruebas en vivo mientras la app WakeGuard está abierta en primer plano.
 * 3. Pausa del sensor cuando el usuario usa otras aplicaciones con pantalla encendida.
 * 4. Suspensión en horario nocturno y corte automático ante batería baja.
 * 5. WakeLock seguro con liberación garantizada para evitar drenaje de energía.
 */
class WakeMotionService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var wakeDetector: MotionWakeDetector? = null
    private var powerManager: PowerManager? = null
    private var vibrator: Vibrator? = null

    private var isManuallyPaused = false
    private var isScreenCurrentlyOn = false
    private var currentBatteryPct = 100

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenCurrentlyOn = true
                    evaluarEstadoDeDeteccion("Pantalla encendida")
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenCurrentlyOn = false
                    evaluarEstadoDeDeteccion("Pantalla apagada (Monitoreo activo)")
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level != -1 && scale != -1) {
                        currentBatteryPct = (level * 100 / scale.toFloat()).toInt()

                        // Comprobar si la batería cae al límite crítico (<= 1%) para armar el encendido de rescate
                        verificarArmadoDeRescate()

                        evaluarEstadoDeDeteccion()
                    }
                }
                Intent.ACTION_SHUTDOWN -> {
                    // Armar rescate si el sistema se está apagando por descarga total
                    armarRescatePorApagadoInminente()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        // Registrar receptores de eventos del sistema (pantalla y batería)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SHUTDOWN)
        }
        registerReceiver(screenReceiver, filter)

        isScreenCurrentlyOn = powerManager?.isInteractive ?: false

        inicializarDetector()
    }

    private fun inicializarDetector() {
        wakeDetector = MotionWakeDetector(this) { triggerType, gForce ->
            despertarPantalla(triggerType, gForce)
        }
        currentDetectorInstance = wakeDetector
        activeServiceInstance = this

        val app = application as? WakeApplication ?: return
        val prefs = app.preferences

        // Sincronizar configuraciones reactivas con el detector
        serviceScope.launch {
            prefs.gestureMode.collect { mode ->
                wakeDetector?.gestureMode = mode
            }
        }
        serviceScope.launch {
            prefs.sensitivity.collect { sens ->
                wakeDetector?.sensitivity = sens
            }
        }
        serviceScope.launch {
            prefs.pocketProtection.collect { pocket ->
                wakeDetector?.pocketProtectionEnabled = pocket
            }
        }
        serviceScope.launch {
            prefs.cooldownSeconds.collect { cooldownSec ->
                wakeDetector?.cooldownMillis = cooldownSec * 1000L
            }
        }

        wakeDetector?.startListening(prefs.ecoSampling.value)
        evaluarEstadoDeDeteccion()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                _serviceRunningState.value = true
                val notification = construirNotificacion("WakeGuard activo: Listo para encender al mover")
                startForeground(WakeApplication.NOTIFICATION_ID, notification)
                evaluarEstadoDeDeteccion()
            }
            ACTION_STOP -> {
                detenerServicio()
            }
            ACTION_TOGGLE_PAUSE -> {
                isManuallyPaused = !isManuallyPaused
                evaluarEstadoDeDeteccion()
            }
            ACTION_REEVALUATE -> {
                evaluarEstadoDeDeteccion()
            }
        }
        return START_STICKY
    }

    /**
     * Comprueba si la batería cayó a descarga total (<= 1%) para armar el encendido de rescate.
     */
    private fun verificarArmadoDeRescate() {
        val app = application as? WakeApplication ?: return
        val prefs = app.preferences

        if (prefs.rescueWakeEnabled.value && currentBatteryPct <= 1 && !prefs.rescueArmed.value) {
            val hora = prefs.rescueWakeHour.value
            val minuto = prefs.rescueWakeMinute.value
            com.example.receiver.ScheduledWakeReceiver.programarAlarmaRescate(this, hora, minuto)
            prefs.setRescueArmed(true)
            actualizarNotificacion("Batería Crítica: Auto-encendido de rescate armado para las %02d:%02d".format(hora, minuto))
        }
    }

    private fun armarRescatePorApagadoInminente() {
        val app = application as? WakeApplication ?: return
        val prefs = app.preferences

        if (prefs.rescueWakeEnabled.value) {
            val hora = prefs.rescueWakeHour.value
            val minuto = prefs.rescueWakeMinute.value
            com.example.receiver.ScheduledWakeReceiver.programarAlarmaRescate(this, hora, minuto)
            prefs.setRescueArmed(true)
        }
    }

    /**
     * Evalúa las condiciones para pausar o habilitar la detección de sensores.
     */
    fun evaluarEstadoDeDeteccion(mensajePersonalizado: String? = null) {
        val app = application as? WakeApplication ?: return
        val prefs = app.preferences

        // 1. Pausa manual solicitada por el usuario
        if (isManuallyPaused) {
            wakeDetector?.isEnabled = false
            actualizarNotificacion("Pausado manualmente desde la barra de estado")
            return
        }

        // 2. Ahorro de batería con pantalla encendida:
        // Si la pantalla está encendida pero la app está en primer plano, dejamos el sensor ACTIVO
        // para que el usuario pueda probar y calibrar la sacudida en vivo.
        // Si el usuario está usando otra app o la pantalla de inicio, se pausa el sensor.
        if (isScreenCurrentlyOn && prefs.screenOnPause.value && !isAppInForeground) {
            wakeDetector?.isEnabled = false
            actualizarNotificacion(mensajePersonalizado ?: "Pantalla encendida: Sensor en reposo (Eco)")
            return
        }

        // 3. Pausa por batería baja
        if (prefs.lowBatteryPause.value && currentBatteryPct <= prefs.lowBatteryThreshold.value) {
            wakeDetector?.isEnabled = false
            actualizarNotificacion("Pausado: Batería baja (${currentBatteryPct}%)")
            return
        }

        // 4. Pausa por horario nocturno
        if (prefs.nightModeEnabled.value) {
            val horaActual = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val inicio = prefs.nightStartHour.value
            val fin = prefs.nightEndHour.value
            val enHorarioNocturno = if (inicio > fin) {
                horaActual >= inicio || horaActual < fin
            } else {
                horaActual in inicio until fin
            }

            if (enHorarioNocturno) {
                wakeDetector?.isEnabled = false
                actualizarNotificacion("Modo Noche: Monitoreo en reposo hasta las $fin:00")
                return
            }
        }

        // Detección habilitada y lista
        wakeDetector?.isEnabled = true
        actualizarNotificacion(mensajePersonalizado ?: "WakeGuard listo: Mueve el teléfono para encender")
    }

    /**
     * Enciende la pantalla mediante WakeLock y actividad auxiliar o emite evento de prueba si la pantalla ya está activa.
     */
    private fun despertarPantalla(triggerType: String, gForce: Float) {
        val app = application as? WakeApplication ?: return
        val prefs = app.preferences

        // 1. Retroalimentación háptica (vibración clara de 70 ms)
        if (prefs.vibrateOnWake.value) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(70)
                }
            } catch (_: Exception) { }
        }

        // Si la pantalla ya está encendida (modo prueba en la app), emitir evento visual para la interfaz
        if (isScreenCurrentlyOn) {
            _inAppShakeDetectedEvent.value = Pair(System.currentTimeMillis(), gForce)
            return
        }

        // 2. Adquirir WakeLock para iluminar la pantalla por 1.5 segundos
        try {
            @Suppress("DEPRECATION")
            val wakeLock = powerManager?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "WakeGuard:MotionScreenWake"
            )
            wakeLock?.acquire(1500L)
        } catch (_: Exception) { }

        // 3. Iniciar WakeScreenActivity auxiliar para sobrepasar políticas de bloqueo estrictas (Tecno HiOS, MIUI, etc.)
        try {
            val wakeIntent = Intent(this, WakeScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(wakeIntent)
        } catch (_: Exception) { }

        // 4. Registrar evento en la base de datos Room mediante Corrutina en Dispatchers.IO
        serviceScope.launch(Dispatchers.IO) {
            app.repository.recordWakeEvent(
                triggerType = triggerType,
                gForce = gForce,
                batteryLevel = currentBatteryPct
            )
        }
    }

    private fun construirNotificacion(texto: String): Notification {
        val pendingIntentMain = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseIntent = Intent(this, WakeMotionService::class.java).apply {
            action = ACTION_TOGGLE_PAUSE
        }
        val pendingPause = PendingIntent.getService(
            this,
            1,
            pauseIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val textoAccion = if (isManuallyPaused) "Reanudar" else "Pausar"

        return NotificationCompat.Builder(this, WakeApplication.CHANNEL_ID)
            .setContentTitle("WakeGuard: Cuidador de Botón")
            .setContentText(texto)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntentMain)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause, textoAccion, pendingPause)
            .build()
    }

    private fun actualizarNotificacion(texto: String) {
        val notification = construirNotificacion(texto)
        val manager = getSystemService(NOTIFICATION_SERVICE) as? android.app.NotificationManager
        manager?.notify(WakeApplication.NOTIFICATION_ID, notification)
    }

    private fun detenerServicio() {
        _serviceRunningState.value = false
        wakeDetector?.stopListening()
        wakeDetector = null
        currentDetectorInstance = null
        activeServiceInstance = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        detenerServicio()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) { }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.example.action.START"
        const val ACTION_STOP = "com.example.action.STOP"
        const val ACTION_TOGGLE_PAUSE = "com.example.action.TOGGLE_PAUSE"
        const val ACTION_REEVALUATE = "com.example.action.REEVALUATE"

        private val _serviceRunningState = MutableStateFlow(false)
        val serviceRunningState: StateFlow<Boolean> = _serviceRunningState.asStateFlow()

        // Evento observable en tiempo real cuando se detecta sacudida con pantalla encendida en la app
        private val _inAppShakeDetectedEvent = MutableStateFlow<Pair<Long, Float>?>(null)
        val inAppShakeDetectedEvent: StateFlow<Pair<Long, Float>?> = _inAppShakeDetectedEvent.asStateFlow()

        // Puntero estático para que la UI observe los sensores en vivo
        var currentDetectorInstance: MotionWakeDetector? = null

        // Puntero de instancia activa
        var activeServiceInstance: WakeMotionService? = null

        // Bandera de app en primer plano para permitir prueba de sacudida en vivo
        var isAppInForeground: Boolean = false
            set(value) {
                field = value
                activeServiceInstance?.evaluarEstadoDeDeteccion()
            }
    }
}
