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
import com.example.receiver.WakeDeviceAdminReceiver
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
 * el monitoreo del acelerómetro de forma eficiente para encender y apagar la pantalla.
 *
 * Características avanzadas:
 * 1. Monitoreo continuo cuando la pantalla está apagada usando CPU Partial WakeLock para evitar
 *    que el kernel de Android entre en Deep Sleep y congele las lecturas de aceleración.
 * 2. Apagado y bloqueo inmediato mediante la API oficial de Administrador de Dispositivos (lockNow)
 *    al sacudir el teléfono con la pantalla encendida.
 * 3. Encendido garantizado mediante WakeLock y actividad auxiliar transparente.
 * 4. Pausa de ahorro cuando el usuario navega en otras aplicaciones.
 */
class WakeMotionService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var wakeDetector: MotionWakeDetector? = null
    private var powerManager: PowerManager? = null
    private var vibrator: Vibrator? = null

    // Bloqueo de CPU para evitar que el procesador entre en suspensión profunda (Deep Sleep) con la pantalla apagada
    private var cpuPartialWakeLock: PowerManager.WakeLock? = null

    private var isManuallyPaused = false
    private var isScreenCurrentlyOn = false
    private var currentBatteryPct = 100

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenCurrentlyOn = true
                    // Al encender la pantalla liberamos el bloqueo de CPU para no consumir energía innecesaria
                    liberarCpuWakeLock()
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
            gestionarGestoDeMovimiento(triggerType, gForce)
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
                val notification = construirNotificacion("WakeGuard activo: Sacude para encender o apagar")
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
     * Adquiere bloqueo de CPU seguro para que el acelerómetro no se detenga al suspenderse el móvil.
     */
    private fun adquirirCpuWakeLock() {
        if (cpuPartialWakeLock == null) {
            cpuPartialWakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "WakeGuard:CpuMotionMonitor"
            )
        }
        if (cpuPartialWakeLock?.isHeld == false) {
            try {
                cpuPartialWakeLock?.acquire()
            } catch (_: Exception) {}
        }
    }

    /**
     * Libera el bloqueo de CPU para evitar consumo innecesario.
     */
    private fun liberarCpuWakeLock() {
        if (cpuPartialWakeLock?.isHeld == true) {
            try {
                cpuPartialWakeLock?.release()
            } catch (_: Exception) {}
        }
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
            liberarCpuWakeLock()
            actualizarNotificacion("Pausado manualmente desde la barra de estado")
            return
        }

        // 2. Ahorro de batería con pantalla encendida:
        // Si la pantalla está encendida pero la app está en primer plano O el usuario tiene el Administrador activo
        // para apagar la pantalla, dejamos el sensor ACTIVO.
        // Si el usuario está usando otra app y screenOnPause está activo sin función de apagar, pausamos.
        val tieneAdminParaApagar = WakeDeviceAdminReceiver.isDeviceAdminActive(this)
        if (isScreenCurrentlyOn && prefs.screenOnPause.value && !isAppInForeground && !tieneAdminParaApagar) {
            wakeDetector?.isEnabled = false
            liberarCpuWakeLock()
            actualizarNotificacion(mensajePersonalizado ?: "Pantalla encendida: Sensor en reposo (Eco)")
            return
        }

        // 3. Pausa por batería baja
        if (prefs.lowBatteryPause.value && currentBatteryPct <= prefs.lowBatteryThreshold.value) {
            wakeDetector?.isEnabled = false
            liberarCpuWakeLock()
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
                liberarCpuWakeLock()
                actualizarNotificacion("Modo Noche: Monitoreo en reposo hasta las $fin:00")
                return
            }
        }

        // Detección habilitada: Si la pantalla está apagada, mantener la CPU despierta para no perder eventos
        wakeDetector?.isEnabled = true
        if (!isScreenCurrentlyOn) {
            adquirirCpuWakeLock()
            actualizarNotificacion(mensajePersonalizado ?: "WakeGuard listo: Sacude para encender pantalla")
        } else {
            liberarCpuWakeLock()
            actualizarNotificacion(mensajePersonalizado ?: "WakeGuard listo: Sacude para apagar pantalla")
        }
    }

    /**
     * Gestiona el gesto de movimiento de forma bidireccional:
     * - Si la pantalla está encendida -> APAGA y BLOQUEA mediante Administrador de Dispositivos.
     * - Si la pantalla está apagada -> ENCIENDE mediante WakeLock y actividad auxiliar.
     */
    private fun gestionarGestoDeMovimiento(triggerType: String, gForce: Float) {
        val app = application as? WakeApplication ?: return
        val prefs = app.preferences

        // 1. CASO PANTALLA ENCENDIDA: SACUDIR PARA APAGAR / BLOQUEAR
        if (isScreenCurrentlyOn) {
            val adminActive = WakeDeviceAdminReceiver.isDeviceAdminActive(this)
            if (adminActive) {
                // Vibración háptica de confirmación de apagado
                if (prefs.vibrateOnWake.value) {
                    vibrar(65)
                }

                // Bloqueo y apagado formal de pantalla
                val bloqueado = WakeDeviceAdminReceiver.lockScreen(this)
                if (bloqueado) {
                    _inAppShakeDetectedEvent.value = Pair(System.currentTimeMillis(), gForce)

                    // Registrar evento de apagado en la base de datos Room sobre Dispatchers.IO
                    serviceScope.launch(Dispatchers.IO) {
                        app.repository.recordWakeEvent(
                            triggerType = "SHAKE_LOCK",
                            gForce = gForce,
                            batteryLevel = currentBatteryPct
                        )
                    }
                }
            } else {
                // Si el Administrador no está activo aún, emitir evento visual para informar al usuario
                if (prefs.vibrateOnWake.value) {
                    vibrar(50)
                }
                _inAppShakeDetectedEvent.value = Pair(System.currentTimeMillis(), gForce)
            }
            return
        }

        // 2. CASO PANTALLA APAGADA: SACUDIR PARA ENCENDER
        if (prefs.vibrateOnWake.value) {
            vibrar(75)
        }

        // Adquirir WakeLock para iluminar la pantalla por 1.5 segundos
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

        // Iniciar WakeScreenActivity auxiliar para sobrepasar políticas de bloqueo estrictas (Tecno HiOS, etc.)
        try {
            val wakeIntent = Intent(this, WakeScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            startActivity(wakeIntent)
        } catch (_: Exception) { }

        // Registrar evento de encendido en la base de datos Room mediante Corrutina en Dispatchers.IO
        serviceScope.launch(Dispatchers.IO) {
            app.repository.recordWakeEvent(
                triggerType = triggerType,
                gForce = gForce,
                batteryLevel = currentBatteryPct
            )
        }
    }

    private fun vibrar(milisegundos: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(milisegundos, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(milisegundos)
            }
        } catch (_: Exception) { }
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
        liberarCpuWakeLock()
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

        // Evento observable en tiempo real cuando se detecta sacudida con pantalla encendida
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
