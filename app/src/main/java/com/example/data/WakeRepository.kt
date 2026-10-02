package com.example.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * WakeRepository: Abstrae el acceso a los datos de la base de datos Room.
 * Garantiza que las operaciones de inserción y borrado se ejecuten fuera del
 * hilo principal usando Dispatchers.IO para evitar tirones en la interfaz de usuario.
 */
class WakeRepository(private val wakeDao: WakeDao) {

    // Flujo de eventos recientes observable de forma reactiva
    val recentEvents: Flow<List<WakeEvent>> = wakeDao.getRecentEvents(50)

    // Conteo total de pulsaciones físicas ahorradas
    val totalSavedClicks: Flow<Int> = wakeDao.getTotalSavedClicks()

    // Conteo de encendidos por sacudida
    val shakeCount: Flow<Int> = wakeDao.getCountByType("SHAKE")

    // Conteo de encendidos por elevación / inclinación
    val liftCount: Flow<Int> = wakeDao.getCountByType("LIFT")

    // Conteo de encendidos por extracción de bolsillo
    val pocketCount: Flow<Int> = wakeDao.getCountByType("POCKET")

    /**
     * Inserta un nuevo evento en segundo plano.
     */
    suspend fun recordWakeEvent(triggerType: String, gForce: Float, batteryLevel: Int) {
        withContext(Dispatchers.IO) {
            val event = WakeEvent(
                triggerType = triggerType,
                gForce = gForce,
                batteryLevel = batteryLevel
            )
            wakeDao.insertEvent(event)
        }
    }

    /**
     * Limpia el registro de eventos en segundo plano.
     */
    suspend fun clearHistory() {
        withContext(Dispatchers.IO) {
            wakeDao.clearAll()
        }
    }
}
