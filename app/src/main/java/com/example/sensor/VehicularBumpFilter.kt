package com.example.sensor

/**
 * Filtro inteligente pasivo de supresión de baches y vibraciones vehiculares (Moto / Bicicleta / Bolso).
 *
 * Principios físicos aplicados de forma 100% automática y silenciosa:
 * 1. Discriminación de ráfagas continuas de baches: Un bache vehicular o el traqueteo de la calzada
 *    produce impactos repetitivos sucesivos en la carrocería. Para evitar que las muestras continuas
 *    de una misma sacudida humana (<160 ms) se cuenten erróneamente como baches independientes,
 *    se exige una separación temporal entre impactos. Si se registran 3 o más impactos distintos
 *    en menos de 1.5 segundos, se activa la supresión vehicular por 2 segundos.
 * 2. Descarte por orientación invertida fisiológica: Si el teléfono está en reposo boca abajo
 *    o con la pantalla apuntando al suelo (az < -3.5 m/s²), se descarta el impacto.
 * 3. Tolerancia a sacudidas humanas dinámicas: Permite movimientos deliberados con inversión
 *    de sentido sin bloquear la aceleración legítima de la mano.
 */
class VehicularBumpFilter {

    // Registro de marcas de tiempo de impactos distintos
    private val recentBumpTimestamps = mutableListOf<Long>()

    // Marca de tiempo hasta la cual se ignoran todos los movimientos por ruido vehicular continuo
    private var vehicleSuppressionUntil: Long = 0L

    // Marca de tiempo del último impacto registrado para no contar muestras de un mismo golpe
    private var lastRecordedBumpTime: Long = 0L

    /**
     * Evalúa si una aceleración debe ser ignorada por ser producto de baches o traqueteo continuo de moto.
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

        // 2. Descarte por orientación invertida:
        // Si el eje Z es fuertemente negativo (la pantalla está boca abajo contra el piso o bolso koala invertido),
        // se ignora inmediatamente cualquier golpe o bache.
        if (az < -3.5f) {
            return true
        }

        // 3. Análisis de frecuencia de impactos distintos (cadencia vehicular / carretera)
        if (deltaAcc > (threshold * 0.70f)) {
            // Solo registrar si han transcurrido al menos 160 ms desde el último impacto,
            // garantizando que las muestras sucesivas de una sacudida humana no se cuenten como baches múltiples.
            if (now - lastRecordedBumpTime >= 160L) {
                lastRecordedBumpTime = now
                // Eliminar impactos que ocurrieron hace más de 1500 milisegundos
                recentBumpTimestamps.removeAll { now - it > 1500L }
                recentBumpTimestamps.add(now)

                // Si hay 3 o más impactos en esa ventana temporal, se trata de una vibración vehicular continua
                if (recentBumpTimestamps.size >= 3) {
                    vehicleSuppressionUntil = now + 2000L
                    recentBumpTimestamps.clear()
                    return true
                }
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
        // az > -3.5f garantiza que la pantalla no está apuntando totalmente al piso
        // pitch entre -20° y 90° corresponde a sostener el móvil con la mano
        return az > -3.5f && (pitch in -20.0f..90.0f)
    }

    /**
     * Restablece el historial del filtro a un estado limpio.
     */
    fun reset() {
        recentBumpTimestamps.clear()
        vehicleSuppressionUntil = 0L
        lastRecordedBumpTime = 0L
    }
}
