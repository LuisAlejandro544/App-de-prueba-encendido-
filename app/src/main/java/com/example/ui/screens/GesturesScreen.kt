package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.WavingHand
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.WakeViewModel

/**
 * GesturesScreen: Configuración detallada de gestos de movimiento y calibración de sensibilidad.
 */
@Composable
fun GesturesScreen(
    viewModel: WakeViewModel,
    modifier: Modifier = Modifier
) {
    val gestureMode by viewModel.gestureMode.collectAsStateWithLifecycle()
    val sensitivity by viewModel.sensitivity.collectAsStateWithLifecycle()
    val pocketProtection by viewModel.pocketProtection.collectAsStateWithLifecycle()
    val vibrateOnWake by viewModel.vibrateOnWake.collectAsStateWithLifecycle()
    val cooldownSeconds by viewModel.cooldownSeconds.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Título de la pantalla
        Text(
            text = "Gestos y Sensibilidad",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Elige cómo quieres encender el teléfono y calibra la fuerza requerida.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Sección de selección de tipo de gesto
        Text(
            text = "TIPO DE GESTO DETECTOR",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        GestureOptionCard(
            title = "Agitar el Teléfono (Shake)",
            description = "Enciende la pantalla al mover o sacudir firmemente el dispositivo con la mano.",
            icon = Icons.Default.Vibration,
            isSelected = gestureMode == "SHAKE",
            onClick = { viewModel.setGestureMode("SHAKE") },
            testTag = "gesture_shake_card"
        )

        Spacer(modifier = Modifier.height(10.dp))

        GestureOptionCard(
            title = "Levantar e Inclinar (Lift to Wake)",
            description = "Detecta cuando levantas el teléfono de una mesa y lo orientas hacia tu rostro.",
            icon = Icons.Default.StayCurrentPortrait,
            isSelected = gestureMode == "LIFT",
            onClick = { viewModel.setGestureMode("LIFT") },
            testTag = "gesture_lift_card"
        )

        Spacer(modifier = Modifier.height(10.dp))

        GestureOptionCard(
            title = "Sacar del Bolsillo (Pocket Wake)",
            description = "El sensor de proximidad detecta cuando el móvil sale del bolsillo y lo enciende al instante.",
            icon = Icons.Default.Security,
            isSelected = gestureMode == "POCKET",
            onClick = { viewModel.setGestureMode("POCKET") },
            testTag = "gesture_pocket_card"
        )

        Spacer(modifier = Modifier.height(10.dp))

        GestureOptionCard(
            title = "Cualquier Movimiento Notorio",
            description = "Responde a cualquier movimiento superior al umbral configurado.",
            icon = Icons.Default.AllInclusive,
            isSelected = gestureMode == "ANY",
            onClick = { viewModel.setGestureMode("ANY") },
            testTag = "gesture_any_card"
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Sección de Calibración de Sensibilidad
        Text(
            text = "CALIBRACIÓN DE SENSIBILIDAD",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Nivel de Sensibilidad",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    val sensitivityLabel = when {
                        sensitivity < 1.8f -> "Baja (Firme)"
                        sensitivity < 3.2f -> "Media (Equilibrada)"
                        sensitivity < 4.2f -> "Alta"
                        else -> "Ultra Sensible"
                    }

                    Text(
                        text = sensitivityLabel,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = sensitivity,
                    onValueChange = { viewModel.setSensitivity(it) },
                    valueRange = 1.0f..5.0f,
                    steps = 7,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sensitivity_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Text(
                    text = "A mayor sensibilidad, menor fuerza física necesitas para despertar la pantalla. Si notas encendidos accidentales al caminar, baja este valor.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Ajustes adicionales de protección
        Text(
            text = "PROTECCIÓN Y RESPUESTA",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Toggle Protección de Bolsillo
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Protección de Bolsillo (Proximidad)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Ignora cualquier movimiento si el sensor detecta que el móvil está dentro del pantalón o bolso.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = pocketProtection,
                    onCheckedChange = { viewModel.setPocketProtection(it) },
                    modifier = Modifier.testTag("pocket_protection_switch"),
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Toggle Vibración háptica
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Vibración al Encender",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Emite una sutil vibración táctil cuando el gesto ha sido reconocido con éxito.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = vibrateOnWake,
                    onCheckedChange = { viewModel.setVibrateOnWake(it) },
                    modifier = Modifier.testTag("vibrate_switch"),
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Slider Tiempo de Enfriamiento (Cooldown)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tiempo de Enfriamiento (Cooldown)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "${cooldownSeconds}s",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp
                    )
                }
                Slider(
                    value = cooldownSeconds.toFloat(),
                    onValueChange = { viewModel.setCooldownSeconds(it.toInt()) },
                    valueRange = 2f..15f,
                    steps = 12,
                    modifier = Modifier.fillMaxWidth().testTag("cooldown_slider")
                )
                Text(
                    text = "Segundos de espera tras un encendido para evitar encendidos repetidos mientras te mueves.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun GestureOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .border(
                1.5.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                modifier = Modifier.testTag("${testTag}_radio")
            )
        }
    }
}
