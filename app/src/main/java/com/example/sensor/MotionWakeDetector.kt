package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Datos en tiempo real del sensor para visualización en la interfaz de usuario.
 */
data class SensorLiveSnapshot(
    val ax: Float = 0f,
    val ay: Float = 0f,
    val az: Float = 0f,
    val totalAcceleration: Float = 9.8f,
    val pitchAngle: Float = 0f, // Grados de inclinación vertical
    val rollAngle: Float = 0f,  // Grados de rotación lateral
    val isNearPocket: Boolean = false,
    val isCooldown: Boolean = false
)

/**
 * MotionWakeDetector: Procesador inteligente de sensores de movimiento y proximidad.
 * 
 * Lógica de ahorro de energía y precisión:
 * 1. Monitorea el acelerómetro con muestreo controlado (SENSOR_DELAY_NORMAL o UI).
 * 2. Integra el sensor de proximidad como "Escudo de Bolsillo": si el sensor está tapado
 *    (dentro de un pantalón, bolso o boca abajo sobre una mesa), se descarta todo movimiento,
 *    evitando encendidos involuntarios y ahorrando batería de la pantalla y CPU.
 * 3. Aplica ventana de enfriamiento (Cooldown) configurable para evitar bucles de encendido al caminar.
 */
class MotionWakeDetector(
    context: Context,
    private val onWakeTriggered: (triggerType: String, gForce: Float) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximitySensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    // Estado en vivo observable por la interfaz de usuario
    private val _liveSnapshot = MutableStateFlow(SensorLiveSnapshot())
    val liveSnapshot: StateFlow<SensorLiveSnapshot> = _liveSnapshot.asStateFlow()

    // Configuración actual
    var isEnabled: Boolean = false
    var gestureMode: String = "SHAKE" // "SHAKE", "LIFT", "POCKET", "ANY"
    var sensitivity: Float = 2.5f
    var pocketProtectionEnabled: Boolean = true
    var cooldownMillis: Long = 5000L

    // Variables internas de estado
    private var isNearPocket: Boolean = false
    private var lastWakeTime: Long = 0L
    private var lastProximityChangeTime: Long = 0L
    private var wasPreviouslyNear: Boolean = false

    // Detección de agitación con picos alternados para evitar falsos positivos
    private var shakePeakCount: Int = 0
    private var lastShakePeakTime: Long = 0L
    private var lastAxisSign: Float = 0f

    // Detección de levantamiento (Lift to wake)
    private var wasLyingFlat: Boolean = false
    private var flatDetectionTime: Long = 0L

    // Filtro inteligente de supresión pasiva de baches y vibraciones vehiculares (moto/bolso)
    private val vehicularFilter = VehicularBumpFilter()

    fun startListening(ecoSampling: Boolean) {
        vehicularFilter.reset()
        val delay = if (ecoSampling) SensorManager.SENSOR_DELAY_NORMAL else SensorManager.SENSOR_DELAY_UI
        accelerometer?.let {
            sensorManager.registerListener(this, it, delay)
        }
        proximitySensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stopListening() {
        vehicularFilter.reset()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val now = System.currentTimeMillis()

        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            val maxRange = event.sensor.maximumRange
            val distance = event.values[0]
            val isCurrentlyNear = distance < 3.0f || distance < maxRange

            if (isNearPocket != isCurrentlyNear) {
                if (isNearPocket && !isCurrentlyNear) {
                    // Transición de CERCA a LEJOS (sacado del bolsillo)
                    wasPreviouslyNear = true
                    lastProximityChangeTime = now
                }
                isNearPocket = isCurrentlyNear
            }
            updateSnapshot()
            return
        }

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val ax = event.values[0]
            val ay = event.values[1]
            val az = event.values[2]

            val totalAcc = sqrt(ax * ax + ay * ay + az * az)
            val deltaAcc = abs(totalAcc - SensorManager.GRAVITY_EARTH)

            // Cálculo de ángulos de inclinación (en grados)
            val pitch = (atan2(ay.toDouble(), sqrt((ax * ax + az * az).toDouble())) * 180.0 / Math.PI).toFloat()
            val roll = (atan2(-ax.toDouble(), az.toDouble()) * 180.0 / Math.PI).toFloat()

            val isCoolingDown = (now - lastWakeTime) < cooldownMillis

            _liveSnapshot.value = SensorLiveSnapshot(
                ax = ax,
                ay = ay,
                az = az,
                totalAcceleration = totalAcc,
                pitchAngle = pitch,
                rollAngle = roll,
                isNearPocket = isNearPocket,
                isCooldown = isCoolingDown
            )

            // Si el detector está desactivado o en periodo de enfriamiento, no procesar triggers
            if (!isEnabled || isCoolingDown) return

            // Si la protección de bolsillo está activa y el sensor está tapado, abortar inmediatamente
            if (pocketProtectionEnabled && isNearPocket) return

            evaluarGestos(now, ax, ay, az, totalAcc, deltaAcc, pitch)
        }
    }

    private fun updateSnapshot() {
        val current = _liveSnapshot.value
        _liveSnapshot.value = current.copy(isNearPocket = isNearPocket)
    }

    /**
     * Evalúa el gesto configurado por el usuario con filtros matemáticos y de supresión vehicular.
     */
    private fun evaluarGestos(
        now: Long,
        ax: Float,
        ay: Float,
        az: Float,
        totalAcc: Float,
        deltaAcc: Float,
        pitch: Float
    ) {
        // Cálculo de umbral basado en la sensibilidad elegida por el usuario (1.0 = baja, 5.0 = alta)
        val shakeThreshold = 25.0f - (sensitivity * 3.4f)
        val liftThreshold = 18.0f - (sensitivity * 2.2f)

        // 1. Filtro pasivo anti-baches y anti-moto: Si el movimiento es parte de un traqueteo
        // vehicular repetitivo o el dispositivo está en orientación invertida (boca abajo en el bolso),
        // se ignora de forma 100% silenciosa (sin vibrar y sin encender la pantalla).
        if (vehicularFilter.shouldSuppressBump(now, deltaAcc, shakeThreshold, az, pitch)) {
            return
        }

        when (gestureMode) {
            "SHAKE" -> {
                procesarAgitacion(now, ax, ay, az, pitch, deltaAcc, shakeThreshold, totalAcc)
            }
            "LIFT" -> {
                procesarLevantamiento(now, ay, az, pitch, deltaAcc, liftThreshold, totalAcc)
            }
            "POCKET" -> {
                procesarExtraccionBolsillo(now, deltaAcc, totalAcc, az, pitch)
            }
            "ANY" -> {
                // Cualquier movimiento significativo superior al umbral pero en postura ergonómica válida
                if (deltaAcc > (shakeThreshold * 0.75f) && vehicularFilter.isNaturalViewingOrientation(az, pitch)) {
                    dispararEncendido("ANY", totalAcc)
                }
            }
        }
    }

    /**
     * Algoritmo de agitación (Shake):
     * Requiere al menos dos aceleraciones fuertes en sentido opuesto en un periodo de 550 ms
     * y que el dispositivo esté en orientación de lectura visible para el usuario.
     */
    private fun procesarAgitacion(
        now: Long,
        ax: Float,
        ay: Float,
        az: Float,
        pitch: Float,
        deltaAcc: Float,
        threshold: Float,
        totalAcc: Float
    ) {
        // Descartar si el teléfono no tiene una orientación de visualización natural
        if (!vehicularFilter.isNaturalViewingOrientation(az, pitch)) {
            return
        }

        if (deltaAcc > threshold) {
            val dominantAxis = if (abs(ax) > abs(ay)) ax else ay
            val currentSign = if (dominantAxis > 0) 1f else -1f

            if (now - lastShakePeakTime < 550) {
                if (currentSign != lastAxisSign) {
                    shakePeakCount++
                    if (shakePeakCount >= 2) {
                        shakePeakCount = 0
                        dispararEncendido("SHAKE", totalAcc)
                        return
                    }
                }
            } else {
                shakePeakCount = 1
            }
            lastShakePeakTime = now
            lastAxisSign = currentSign
        }
    }

    /**
     * Algoritmo de levantamiento e inclinación (Lift to Wake):
     * Detecta cuando el teléfono pasa de estar plano (en una mesa o en reposo con z ~ 9.8)
     * a estar inclinado hacia el rostro del usuario (ay entre 3.5 y 8.5 m/s² y pitch entre 25° y 75°).
     */
    private fun procesarLevantamiento(
        now: Long,
        ay: Float,
        az: Float,
        pitch: Float,
        deltaAcc: Float,
        threshold: Float,
        totalAcc: Float
    ) {
        val isCurrentlyFlat = abs(az) > 7.5f && abs(ay) < 4.0f
        if (isCurrentlyFlat) {
            wasLyingFlat = true
            flatDetectionTime = now
            return
        }

        // Si estuvo en reposo plano en los últimos 3 segundos y ahora se eleva e inclina de frente
        if (wasLyingFlat && (now - flatDetectionTime) < 3000) {
            val isTiltedUpright = pitch in 22.0f..80.0f && ay > 3.0f && vehicularFilter.isNaturalViewingOrientation(az, pitch)
            if (isTiltedUpright && deltaAcc > 2.0f) {
                wasLyingFlat = false
                dispararEncendido("LIFT", totalAcc)
            }
        }
    }

    /**
     * Algoritmo de extracción de bolsillo o bolso:
     * Si el sensor de proximidad pasó de estar bloqueado a desbloqueado en los últimos 1.2 segundos,
     * se extrajo del bolso y se encuentra de cara al usuario, se enciende la pantalla al instante.
     */
    private fun procesarExtraccionBolsillo(
        now: Long,
        deltaAcc: Float,
        totalAcc: Float,
        az: Float,
        pitch: Float
    ) {
        if (wasPreviouslyNear && (now - lastProximityChangeTime) < 1200) {
            if (deltaAcc > 2.5f && vehicularFilter.isNaturalViewingOrientation(az, pitch)) {
                wasPreviouslyNear = false
                dispararEncendido("POCKET", totalAcc)
            }
        }
    }

    private fun dispararEncendido(type: String, gForce: Float) {
        lastWakeTime = System.currentTimeMillis()
        onWakeTriggered(type, gForce)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No se requiere acción en cambio de precisión
    }
}
