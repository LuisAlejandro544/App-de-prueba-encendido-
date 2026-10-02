package com.example.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sensor.SensorLiveSnapshot
import com.example.ui.WakeViewModel
import com.example.ui.components.SensorVisualizer

/**
 * DashboardScreen: Pantalla de Control Principal y Monitoreo en Vivo.
 * Permite activar o desactivar el servicio de encendido por movimiento,
 * observar la telemetría en tiempo real y ejecutar pruebas de pantalla.
 */
@Composable
fun DashboardScreen(
    viewModel: WakeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
    val totalClicks by viewModel.totalSavedClicks.collectAsStateWithLifecycle()
    val gestureMode by viewModel.gestureMode.collectAsStateWithLifecycle()
    val liveSensorData by viewModel.liveSensorData.collectAsStateWithLifecycle()
    val countdown by viewModel.testCountdown.collectAsStateWithLifecycle()
    val isDeviceAdminActive by viewModel.isDeviceAdminActive.collectAsStateWithLifecycle()
    val isOverlayPermissionGranted by viewModel.isOverlayPermissionGranted.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Encabezado con título y descripción de propósito
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "WakeGuard",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Cuidador del Botón de Encendido",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Indicador de estado compacto
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isRunning) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isRunning) "Activo" else "Detenido",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        val inAppShake by viewModel.inAppShakeEvent.collectAsStateWithLifecycle()
        val isRecentShake = inAppShake?.let { (timestamp, _) ->
            (System.currentTimeMillis() - timestamp) < 4000L
        } ?: false

        // Tarjeta Maestra con Interruptor Principal y Animación de Pulso
        MasterControlCard(
            isRunning = isRunning,
            totalSavedClicks = totalClicks,
            onToggle = { viewModel.toggleService(context) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Alerta visual interactiva en tiempo real al reconocer una sacudida dentro de la app
        AnimatedVisibility(visible = isRecentShake) {
            inAppShake?.let { (_, gForce) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("in_app_shake_success_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "¡Sacudida Firme Reconocida!",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Fuerza: %.1f G. El sensor y el algoritmo respondieron con éxito.".format(gForce / 9.8f),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tarjeta de Control Avanzado: Administrador de Dispositivo para Apagar al Sacudir
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("device_admin_control_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDeviceAdminActive) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(
                    listOf(
                        if (isDeviceAdminActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isDeviceAdminActive) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = if (isDeviceAdminActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Apagar Pantalla al Sacudir",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (isDeviceAdminActive) "Administrador Activo (Bloqueo directo)" else "Requiere permiso de Administrador",
                                fontSize = 11.sp,
                                color = if (isDeviceAdminActive) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!isDeviceAdminActive) {
                        Button(
                            onClick = { viewModel.requestDeviceAdmin(context) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("activate_device_admin_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Activar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Listo",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isDeviceAdminActive) "¡Configurado! Ahora sacudir el teléfono con la pantalla encendida la apagará y bloqueará al instante."
                    else "Otorga el permiso de Administrador de Dispositivos oficial de Android (nunca se desactiva solo) para bloquear la pantalla al sacudir.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Aviso si falta permiso de Superposición (Mostrar sobre otras apps) para garantizar el encendido
        if (!isOverlayPermissionGranted) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("overlay_permission_warning_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mostrar sobre otras aplicaciones",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "Garantiza que el móvil pueda encender la pantalla en Android 10+ y Tecno HiOS.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.requestOverlayPermission(context) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("grant_overlay_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text("Permitir", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Botón de prueba interactiva inmediata
        Button(
            onClick = { viewModel.startWakeTest(context) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("test_wake_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Probar",
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Probar Encendido Ahora (Simulador)",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Visualizador en Vivo de Acelerómetro y Proximidad
        SensorVisualizer(snapshot = liveSensorData)

        Spacer(modifier = Modifier.height(20.dp))

        // Tarjetas resumen de ventajas
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HighlightCard(
                title = "Gesto Activo",
                value = when (gestureMode) {
                    "SHAKE" -> "Agitar"
                    "LIFT" -> "Levantar"
                    "POCKET" -> "Bolsillo"
                    else -> "Cualquiera"
                },
                icon = Icons.Default.Sensors,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            HighlightCard(
                title = "Batería Eco",
                value = "Pausa en Pantalla",
                icon = Icons.Default.BatteryChargingFull,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Modal de Cuenta Regresiva de Prueba
    countdown?.let { count ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissTestDialog() },
            title = {
                Text(
                    text = if (count > 0) "Prueba en $count..." else "¡Encendiendo pantalla!",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (count > 0) {
                        Text(
                            text = "Bloquea o apaga la pantalla rápidamente y muévela para probar el encendido por movimiento.",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        CircularProgressIndicator(
                            progress = { count / 3f },
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            text = "¡Señal de WakeLock enviada! La pantalla se encenderá automáticamente sin tocar el botón físico.",
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissTestDialog() }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

/**
 * Tarjeta Maestra con botón de activación de diseño ergonómico.
 */
@Composable
private fun MasterControlCard(
    isRunning: Boolean,
    totalSavedClicks: Int,
    onToggle: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val glowColor by animateColorAsState(
        targetValue = if (isRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(400), label = "glowColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("master_control_card")
            .clip(RoundedCornerShape(28.dp))
            .clickable { onToggle() },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                listOf(
                    glowColor.copy(alpha = 0.7f),
                    glowColor.copy(alpha = 0.2f)
                )
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Botón Circular Central Pulsante
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                if (isRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                if (isRunning) MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .border(
                        3.dp,
                        if (isRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = if (isRunning) "Apagar" else "Encender",
                    tint = if (isRunning) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(54.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isRunning) "PROTECCIÓN ACTIVA" else "PROTECCIÓN DETENIDA",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isRunning) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isRunning) "Mueve o agita el teléfono para encender la pantalla" else "Toca aquí para encender la protección del botón",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Contador de pulsaciones protegidas
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Pulsaciones ahorradas",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$totalSavedClicks clicks evitados al botón físico",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun HighlightCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
