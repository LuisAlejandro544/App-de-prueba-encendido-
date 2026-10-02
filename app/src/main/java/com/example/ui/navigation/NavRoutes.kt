package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Destinos principales de navegación de WakeGuard.
 * Cada destino representa una pantalla dedicada para evitar interfaces saturadas.
 */
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Control : Screen("control", "Control", Icons.Default.PowerSettingsNew)
    object Gestures : Screen("gestures", "Gestos", Icons.Default.Sensors)
    object Battery : Screen("battery", "Batería Eco", Icons.Default.BatteryChargingFull)
    object Stats : Screen("stats", "Salud Botón", Icons.Default.Insights)
    object Guide : Screen("guide", "Guía y Ajustes", Icons.Default.HelpOutline)
}

val bottomNavScreens = listOf(
    Screen.Control,
    Screen.Gestures,
    Screen.Battery,
    Screen.Stats,
    Screen.Guide
)
