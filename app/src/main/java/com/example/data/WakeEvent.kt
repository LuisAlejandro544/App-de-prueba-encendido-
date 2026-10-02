package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * WakeEvent: Entidad Room que representa cada encendido de pantalla realizado
 * mediante movimiento, permitiendo calcular con precisión cuántas pulsaciones
 * físicas mecánicas se le han ahorrado al botón de encendido del teléfono.
 */
@Entity(tableName = "wake_events")
data class WakeEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Marca de tiempo en milisegundos cuando ocurrió el evento
    val timestamp: Long = System.currentTimeMillis(),
    
    // Tipo de gesto que activó el encendido: SHAKE (agitar), LIFT (levantar), POCKET (sacar de bolsillo), TEST (prueba manual)
    val triggerType: String,
    
    // Magnitud de la fuerza G o aceleración registrada
    val gForce: Float,
    
    // Nivel de batería al momento del encendido
    val batteryLevel: Int
)
