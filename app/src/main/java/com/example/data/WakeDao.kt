package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * WakeDao: Interfaz de acceso a datos para eventos de encendido por movimiento.
 * Proporciona consultas reactivas usando Kotlin Coroutines Flow para que la UI
 * se actualice automáticamente en tiempo real.
 */
@Dao
interface WakeDao {

    /**
     * Obtiene todos los eventos de encendido ordenados del más reciente al más antiguo.
     */
    @Query("SELECT * FROM wake_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<WakeEvent>>

    /**
     * Obtiene únicamente los últimos N eventos para mostrarlos en el historial reciente sin sobrecargar memoria.
     */
    @Query("SELECT * FROM wake_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEvents(limit: Int = 30): Flow<List<WakeEvent>>

    /**
     * Cuenta total de encendidos realizados por movimiento (= pulsaciones físicas ahorradas).
     */
    @Query("SELECT COUNT(*) FROM wake_events")
    fun getTotalSavedClicks(): Flow<Int>

    /**
     * Cuenta de encendidos filtrados por tipo de gesto.
     */
    @Query("SELECT COUNT(*) FROM wake_events WHERE triggerType = :type")
    fun getCountByType(type: String): Flow<Int>

    /**
     * Cuenta de encendidos ocurridos en las últimas 24 horas (desde 'sinceTimestamp').
     */
    @Query("SELECT COUNT(*) FROM wake_events WHERE timestamp >= :sinceTimestamp")
    fun getCountSince(sinceTimestamp: Long): Flow<Int>

    /**
     * Inserta un nuevo evento de encendido en la base de datos (función suspendida para corrutinas).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: WakeEvent): Long

    /**
     * Limpia todo el historial de eventos si el usuario decide reiniciar sus estadísticas.
     */
    @Query("DELETE FROM wake_events")
    suspend fun clearAll()
}
