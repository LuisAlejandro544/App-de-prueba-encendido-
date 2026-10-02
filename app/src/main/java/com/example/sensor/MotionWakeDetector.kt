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
 * Características clave:
 * 1. Monitorea el acelerómetro con muestreo eficiente (Eco o Fluido).
 * 2. Integra el sensor de proximidad con compatibilidad para sensores binarios (comunes en marcas como Tecno,
 *    Xiaomi y Motorola) y analógicos, evitando falsos positivos que bloqueen el encendido.
 * 3. Detección de sacudida firme y deliberada (doble golpe en sentido opuesto en eje X o Y).
 * 4. Ventana de enfriamiento (Cooldown) configurable para evitar bucles al caminar o viajar.
 */
class MotionWakeDetector(
    context: Context,
    private val onWakeTriggered: (triggerType: String, gForce: Float) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    // Prioriza sensor de acelerómetro con capacidad de despertar el procesador (wake-up sensor)
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    // Prioriza sensor de proximidad de hardware con capacidad wake-up
    private val proximitySensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY, true)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    // Estado en vivo observable por la interfaz de usuario
    private val _liveSnapshot = MutableStateFlow(SensorLiveSnapshot())
    val liveSnapshot: StateFlow<SensorLiveSnapshot> = _liveSnapshot.asStateFlow()

    // Configuración actual
    var isEnabled: Boolean = false
    var gestureMode: String = "SHAKE" // "SHAKE", "LIFT", "POCKET", "ANY"
    var sensitivity: Float = 2.5f
    var pocketProtectionEnabled: Boolean = true
    var cooldownMillis: Long = 5000L

    // Variables internas de estado de proximidad
    private var isNearPocket: Boolean = false
    private var lastWakeTime: Long = 0L
    private var lastProximityChangeTime: Long = 0L
    private var wasPreviouslyNear: Boolean = false

    // Detección de sacudida firme y deliberada (ida y vuelta con inversión de sentido)
    private var shakeStrokeCount: Int = 0
    private var firstStrokeTime: Long = 0L
    private var firstStrokeSign: Float = 0f
    private var firstStrokeAxis: String = "" // "X" o "Y"

    // Detección de levantamiento (Lift to wake)
    private var wasLyingFlat: Boolean = false
    private var flatDetectionTime: Long = 0L

    // Filtro inteligente de supresión pasiva de baches y vibraciones vehiculares (moto/bolso)
    private val vehicularFilter = VehicularBumpFilter()

    fun startListening(ecoSampling: Boolean) {
        vehicularFilter.reset()
        resetShakeState()
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
        resetShakeState()
        sensorManager.unregisterListener(this)
    }

    private fun resetShakeState() {
        shakeStrokeCount = 0
        firstStrokeTime = 0L
        firstStrokeSign = 0f
        firstStrokeAxis = ""
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val now = System.currentTimeMillis()

        // 1. Manejo del sensor de proximidad (Escudo anti-bolsillo)
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            val maxRange = event.sensor.maximumRange
            val distance = event.values[0]

            // Detección adaptativa para Tecno y Android genérico:
            // Muchos teléfonos Tecno, Xiaomi y Samsung tienen sensores binarios donde maxRange es 1.0f o 5.0f.
            // Si maxRange <= 2.0f, el estado "CERCA" solo ocurre si distance < maxRange (generalmente 0.0f).
            // Si el sensor es analógico (maxRange > 2.0f), se evalúa distance < 3.0f y distance < maxRange.
            val isCurrentlyNear = if (maxRange <= 2.0f) {
                distance < maxRange
            } else {
                distance < 3.0f && distance < maxRange
            }

            if (isNearPocket != isCurrentlyNear) {
                if (isNearPocket && !isCurrentlyNear) {
                    // Transición de CERCA a LEJOS (por ejemplo, al sacar el teléfono del bolsillo)
                    wasPreviouslyNear = true
                    lastProximityChangeTime = now
                }
                isNearPocket = isCurrentlyNear
            }
            updateSnapshot()
            return
        }

        // 2. Manejo del acelerómetro
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

            // Si el detector está desactivado o en periodo de enfriamiento, no procesar activaciones
            if (!isEnabled || isCoolingDown) return

            // Si la protección de bolsillo está activa y el sensor está tapado, descartar movimiento
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
        // Cálculo del umbral para sacudida firme y deliberada (sensibilidad 1.0 a 5.0)
        // Con sensibilidad por defecto 2.5: threshold = 15.0 m/s² (aprox 2.5 G totales)
        val shakeThreshold = 21.0f - (sensitivity * 2.4f)
        val liftThreshold = 18.0f - (sensitivity * 2.2f)

        // 1. Filtro pasivo anti-baches y anti-moto: Ignora baches de asfalto repetitivos
        // o si el teléfono está boca abajo mirando al suelo en un bolso koala.
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
                // Cualquier movimiento notorio que supere el umbral sin estar boca abajo
                if (deltaAcc > shakeThreshold && az > -3.5f) {
                    dispararEncendido("ANY", totalAcc)
                }
            }
        }
    }

    /**
     * Algoritmo de sacudida firme y deliberada (Shake):
     * Requiere un movimiento de ida y vuelta claro (inversión de sentido en eje X o eje Y)
     * en un intervalo de 120 ms a 700 ms, evitando activaciones accidentales por choques simples.
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
        // Verificar si la aceleración supera el umbral configurado
        if (deltaAcc > threshold) {
            val isAxisX = abs(ax) > abs(ay)
            val dominantAxis = if (isAxisX) "X" else "Y"
            val currentVal = if (isAxisX) ax else ay
            val currentSign = if (currentVal > 0) 1f else -1f

            if (shakeStrokeCount == 0) {
                // Primer golpe de aceleración firme de la sacudida
                shakeStrokeCount = 1
                firstStrokeTime = now
                firstStrokeSign = currentSign
                firstStrokeAxis = dominantAxis
            } else {
                // Segundo golpe: comprobar si es el contragolpe con inversión de dirección
                val elapsed = now - firstStrokeTime
                if (elapsed in 120L..700L) {
                    // Si el eje dominante coincide y el signo se invirtió (+ hacia - o viceversa)
                    if (dominantAxis == firstStrokeAxis && currentSign != firstStrokeSign) {
                        resetShakeState()
                        dispararEncendido("SHAKE", totalAcc)
                        return
                    }
                } else if (elapsed > 700L) {
                    // Si pasó demasiado tiempo, reiniciar el ciclo con este golpe como primero
                    shakeStrokeCount = 1
                    firstStrokeTime = now
                    firstStrokeSign = currentSign
                    firstStrokeAxis = dominantAxis
                }
            }
        }
    }

    /**
     * Algoritmo de levantamiento e inclinación (Lift to Wake):
     * Detecta cuando el teléfono pasa de reposo horizontal a inclinarse hacia el rostro del usuario.
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
            val isTiltedUpright = pitch in 20.0f..85.0f && ay > 2.8f && vehicularFilter.isNaturalViewingOrientation(az, pitch)
            if (isTiltedUpright && deltaAcc > 2.0f) {
                wasLyingFlat = false
                dispararEncendido("LIFT", totalAcc)
            }
        }
    }

    /**
     * Algoritmo de extracción de bolsillo o bolso:
     * Si el sensor de proximidad pasó de estar bloqueado a despejado en los últimos 1.2 segundos
     * y el usuario sostiene el terminal de frente.
     */
    private fun procesarExtraccionBolsillo(
        now: Long,
        deltaAcc: Float,
        totalAcc: Float,
        az: Float,
        pitch: Float
    ) {
        if (wasPreviouslyNear && (now - lastProximityChangeTime) < 1200) {
            if (deltaAcc > 2.2f && vehicularFilter.isNaturalViewingOrientation(az, pitch)) {
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
        // No se requiere acción ante cambios de precisión de hardware
    }
}
