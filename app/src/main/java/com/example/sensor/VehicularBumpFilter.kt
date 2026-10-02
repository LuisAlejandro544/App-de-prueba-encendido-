package com.example.sensor

/**
 * Filtro inteligente pasivo de supresión de baches y vibraciones vehiculares (Moto / Bicicleta / Bolso).
 * 
 * Diseñado específicamente para resolver el problema de trayectos en motocicleta o bicicleta,
 * donde el teléfono va dentro de un bolso, koala o riñonera y sufre sacudidas por baches en el camino:
 * 
 * Principios físicos aplicados de forma 100% automática y silenciosa:
 * 1. Análisis de cadencia de impactos continuos: Los baches de la carretera y la vibración del motor
 *    generan múltiples picos de aceleración repetitivos en intervalos muy cortos (< 450 ms).
 *    Un gesto humano deliberado es un impulso singular seguido de quietud para ver la pantalla.
 *    Si se registran 3 o más impactos seguidos en menos de 1.5 segundos, se activa un bloqueo temporal
 *    de supresión vehicular.
 * 2. Descarte por orientación invertida (Pantalla oculta): En un koala o bolso durante el viaje en moto,
 *    el teléfono oscila plano contra el cuerpo o boca abajo (eje Z fuertemente negativo).
 *    Ningún usuario mira la pantalla mientras está invertida o mirando al suelo.
 * 3. Cero molestias: No emite vibración háptica, no despierta la pantalla y no requiere intervención
 *    ni configuración manual del usuario.
 */
class VehicularBumpFilter {

    // Registro de marcas de tiempo de impactos y oscilaciones recientes
    private val recentBumpTimestamps = mutableListOf<Long>()

    // Marca de tiempo hasta la cual se ignoran todos los movimientos por ruido vehicular
    private var vehicleSuppressionUntil: Long = 0L

    /**
     * Evalúa si una aceleración debe ser ignorada por ser producto de baches, vibración o traqueteo.
     * 
     * @param now Marca de tiempo actual en milisegundos.
     * @param deltaAcc Variación neta de aceleración respecto a la gravedad terrestre.
     * @param threshold Umbral de sensibilidad base configurado en la app.
     * @param az Componente del vector de aceleración en el eje Z (perpendicular a la pantalla).
     * @param pitch Inclinación vertical del dispositivo en grados.
     * @return true si el evento es un bache/vibración y DEBE IGNORARSE silenciosamente.
     */
    fun shouldSuppressBump(
        now: Long,
        deltaAcc: Float,
        threshold: Float,
        az: Float,
        pitch: Float
    ): Boolean {
        // 1. Si estamos dentro de un bloqueo activo por baches o vibración reciente de moto
        if (now < vehicleSuppressionUntil) {
            // Si el terreno sigue rugoso o continúan los baches, extender la protección
            if (deltaAcc > (threshold * 0.65f)) {
                vehicleSuppressionUntil = now + 1600L
            }
            return true
        }

        // 2. Descarte por orientación no visible:
        // Si el eje Z es fuertemente negativo (la pantalla está boca abajo dentro del bolso o koala),
        // se ignora inmediatamente cualquier golpe o bache.
        if (az < -1.5f) {
            return true
        }

        // 3. Análisis de frecuencia de impactos (detección de baches sucesivos / calzada irregular)
        if (deltaAcc > (threshold * 0.70f)) {
            // Eliminar impactos que ocurrieron hace más de 1500 milisegundos
            recentBumpTimestamps.removeAll { now - it > 1500L }
            recentBumpTimestamps.add(now)

            // Si hay 3 o más impactos en esa ventana temporal, se trata de una vibración vehicular continua
            if (recentBumpTimestamps.size >= 3) {
                // Activar supresión automática por 2 segundos adicionales de calma
                vehicleSuppressionUntil = now + 2000L
                recentBumpTimestamps.clear()
                return true
            }
        }

        return false
    }

    /**
     * Valida si el teléfono se encuentra en una orientación ergonómica normal
     * para que una persona pueda ver la pantalla de frente.
     * 
     * @param az Eje Z del acelerómetro.
     * @param pitch Inclinación en grados respecto a la vertical.
     * @return true si la pantalla está de cara al usuario.
     */
    fun isNaturalViewingOrientation(az: Float, pitch: Float): Boolean {
        // az > -1.5f garantiza que la pantalla no está apuntando al piso
        // pitch entre -15° y 88° corresponde a sostener el móvil con la mano
        return az > -1.5f && (pitch in -15.0f..88.0f)
    }

    /**
     * Restablece el historial del filtro a un estado limpio.
     */
    fun reset() {
        recentBumpTimestamps.clear()
        vehicleSuppressionUntil = 0L
    }
}
