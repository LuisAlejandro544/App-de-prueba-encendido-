package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.WakeViewModel

/**
 * GuideScreen: Guía para evitar que el sistema cierre la app en segundo plano
 * en marcas con cierres agresivos (Xiaomi, Samsung, Huawei, etc.) y consejos
 * de mantenimiento para el botón de encendido físico.
 */
@Composable
fun GuideScreen(
    viewModel: WakeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val startOnBoot by viewModel.startOnBoot.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Guía y Permisos del Sistema",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Configuraciones recomendadas para que tu teléfono nunca cierre el servicio.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Auto-arranque al encender el teléfono
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
                        text = "Iniciar al Encender el Teléfono",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Activa automáticamente WakeGuard cuando reinicies o prendas tu celular.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = startOnBoot,
                    onCheckedChange = { viewModel.setStartOnBoot(it) },
                    modifier = Modifier.testTag("start_on_boot_switch"),
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Botón de Exclusión de Batería de Android
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BatteryAlert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Exclusión de Ahorro de Batería OEM",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Muchos fabricantes (Xiaomi, Samsung, Realme) cierran apps que usan sensores en reposo para inflar la duración de su batería. Marca WakeGuard como 'Sin restricciones' para funcionamiento continuo.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("open_battery_settings_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Abrir Ajustes de Batería del Móvil", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Guía por Marcas
        Text(
            text = "PASOS SEGÚN LA MARCA DE TU CELULAR",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        BrandInstructionCard(
            brand = "Xiaomi / Redmi / POCO (MIUI / HyperOS)",
            steps = "1. Ve a Ajustes > Aplicaciones > WakeGuard.\n2. Activa 'Inicio automático'.\n3. En Ahorro de batería elige 'Sin restricciones'.\n4. En la pantalla de apps recientes, mantén pulsada la app y toca el icono del candado."
        )

        Spacer(modifier = Modifier.height(10.dp))

        BrandInstructionCard(
            brand = "Samsung Galaxy (One UI)",
            steps = "1. Ve a Ajustes > Cuidado del dispositivo > Batería.\n2. Entra en 'Límites de uso en segundo plano'.\n3. En 'Aplicaciones que nunca se suspenden', pulsa el signo '+' y añade WakeGuard."
        )

        Spacer(modifier = Modifier.height(10.dp))

        BrandInstructionCard(
            brand = "Tecno / Infinix (HiOS / XOS)",
            steps = "1. Abre la aplicación 'Phone Master' que viene preinstalada en tu Tecno.\n2. Entra en Batería / Ahorro de energía > 'Gestión de inicio automático' y ACTIVA WakeGuard.\n3. En Ajustes > Apps > WakeGuard > Batería, selecciona 'Sin restricciones' o desactiva la optimización.\n4. En la pantalla de aplicaciones recientes de HiOS, desliza hacia abajo o toca el candado para fijar WakeGuard y evitar que se cierre al limpiar la memoria."
        )

        Spacer(modifier = Modifier.height(10.dp))

        BrandInstructionCard(
            brand = "Motorola / Google Pixel / Nokia",
            steps = "1. Ve a Ajustes > Apps > WakeGuard > Batería.\n2. Selecciona 'Sin restricciones'."
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Consejos de Cuidado del Botón
        Text(
            text = "CONSEJOS DE CUIDADO DEL BOTÓN FÍSICO",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                TipRow(text = "Usa WakeGuard para encender la pantalla con movimiento y complementa con el widget del sistema o doble toque para apagarla.")
                Spacer(modifier = Modifier.height(8.dp))
                TipRow(text = "No presiones el botón de encendido con las uñas; usa la yema del dedo para evitar desalinear la membrana interna.")
                Spacer(modifier = Modifier.height(8.dp))
                TipRow(text = "Mantén limpia la ranura del botón de polvo o pelusas del bolsillo con un cepillo suave seco.")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun BrandInstructionCard(brand: String, steps: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = brand,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = steps,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun TipRow(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(16.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
        )
    }
}
