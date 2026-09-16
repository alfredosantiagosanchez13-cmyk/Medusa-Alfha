package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.location.GeofenceZone
import com.example.data.location.PradosGeofenceForegroundService
import com.example.data.location.PradosLocationConstants
import com.example.data.location.ProximityGateStateManager
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Tarjeta de Control Táctico de Apertura Automática de Puertas por Proximidad
 * para residentes de Los Prados Residencial (Av. de la Cantera 2750).
 *
 * Basada en Google Play Services Location API + BLE + Wi-Fi.
 * Filosofía: "TIEMPO = FAMILIA" (Cero filas en caseta).
 */
@Composable
fun ProximityGateControlCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val proximityState by ProximityGateStateManager.uiState.collectAsState()

    // Permisos de ubicación
    val permissionsToRequest = remember {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        list.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        val fineLocationGranted = permissionsMap[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (fineLocationGranted) {
            PradosGeofenceForegroundService.startService(context)
            Toast.makeText(context, "Servicio de proximidad satelital iniciado", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Se requiere permiso de ubicación precisa para monitorear el acceso", Toast.LENGTH_LONG).show()
        }
    }

    // Animación de pulso de radar
    val infiniteTransition = rememberInfiniteTransition(label = "RadarPulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseAlpha"
    )

    var showHistoryDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("proximity_gate_control_card"),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = when (proximityState.currentZone) {
                    GeofenceZone.SHORT_RADIUS_50M_GATE_OPENED -> listOf(SuccessGreen, CyanNeon)
                    GeofenceZone.BROAD_RADIUS_500M -> listOf(WarningOrange, GoldPrimary)
                    GeofenceZone.OUT_OF_RANGE -> listOf(NavyBorder, GoldPrimary.copy(alpha = 0.3f))
                }
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // CABECERA Y SWITCH
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = 0.15f))
                            .border(1.dp, GoldPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Apertura Automática (GPS + BLE)",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Piloto: Los Prados Residencial",
                            color = GrayMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                // Switch para encender/apagar el servicio en segundo plano
                Switch(
                    checked = proximityState.isServiceRunning,
                    onCheckedChange = { isChecked ->
                        if (isChecked) {
                            val hasFineLocation = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasFineLocation) {
                                PradosGeofenceForegroundService.startService(context)
                                Toast.makeText(context, "Monitoreo satelital activo en segundo plano", Toast.LENGTH_SHORT).show()
                            } else {
                                permissionLauncher.launch(permissionsToRequest)
                            }
                        } else {
                            PradosGeofenceForegroundService.stopService(context)
                            Toast.makeText(context, "Servicio de ubicación detenido", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GoldPrimary,
                        checkedTrackColor = GoldPrimary.copy(alpha = 0.3f),
                        uncheckedThumbColor = GrayMuted,
                        uncheckedTrackColor = NavyDark
                    ),
                    modifier = Modifier.testTag("switch_proximity_service")
                )
            }

            // RADAR VISUAL Y ESTADO DE DISTANCIA
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NavyDark)
                    .border(1.dp, NavyBorder, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Radar animado
                    Box(
                        modifier = Modifier.size(72.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val centerOffset = Offset(size.width / 2f, size.height / 2f)
                            val maxRadius = size.minDimension / 2f

                            // Círculos concéntricos de referencia (500m y 50m)
                            drawCircle(
                                color = NavyBorder,
                                radius = maxRadius,
                                center = centerOffset,
                                style = Stroke(width = 1.dp.toPx())
                            )
                            drawCircle(
                                color = NavyBorder.copy(alpha = 0.6f),
                                radius = maxRadius * 0.45f,
                                center = centerOffset,
                                style = Stroke(width = 1.dp.toPx())
                            )

                            // Pulso animado si el servicio está activo
                            if (proximityState.isServiceRunning) {
                                val pulseColor = when (proximityState.currentZone) {
                                    GeofenceZone.SHORT_RADIUS_50M_GATE_OPENED -> SuccessGreen
                                    GeofenceZone.BROAD_RADIUS_500M -> WarningOrange
                                    GeofenceZone.OUT_OF_RANGE -> CyanNeon
                                }
                                drawCircle(
                                    color = pulseColor.copy(alpha = pulseAlpha),
                                    radius = maxRadius * pulseRadius,
                                    center = centerOffset,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }

                        // Icono central representativo del vehículo o antena
                        Icon(
                            imageVector = when (proximityState.currentZone) {
                                GeofenceZone.SHORT_RADIUS_50M_GATE_OPENED -> Icons.Default.DirectionsCar
                                GeofenceZone.BROAD_RADIUS_500M -> Icons.Default.Sensors
                                GeofenceZone.OUT_OF_RANGE -> Icons.Default.LocationOn
                            },
                            contentDescription = null,
                            tint = when (proximityState.currentZone) {
                                GeofenceZone.SHORT_RADIUS_50M_GATE_OPENED -> SuccessGreen
                                GeofenceZone.BROAD_RADIUS_500M -> WarningOrange
                                GeofenceZone.OUT_OF_RANGE -> GoldPrimary
                            },
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Información de geoposición y distancia
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Badge de Zona
                        Surface(
                            color = when (proximityState.currentZone) {
                                GeofenceZone.SHORT_RADIUS_50M_GATE_OPENED -> SuccessGreen.copy(alpha = 0.2f)
                                GeofenceZone.BROAD_RADIUS_500M -> WarningOrange.copy(alpha = 0.2f)
                                GeofenceZone.OUT_OF_RANGE -> NavyCard
                            },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(
                                1.dp,
                                when (proximityState.currentZone) {
                                    GeofenceZone.SHORT_RADIUS_50M_GATE_OPENED -> SuccessGreen
                                    GeofenceZone.BROAD_RADIUS_500M -> WarningOrange
                                    GeofenceZone.OUT_OF_RANGE -> NavyBorder
                                }
                            )
                        ) {
                            Text(
                                text = proximityState.currentZone.label,
                                color = when (proximityState.currentZone) {
                                    GeofenceZone.SHORT_RADIUS_50M_GATE_OPENED -> SuccessGreen
                                    GeofenceZone.BROAD_RADIUS_500M -> WarningOrange
                                    GeofenceZone.OUT_OF_RANGE -> GrayMuted
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        // Distancia en metros
                        val distanceText = proximityState.currentDistanceMeters?.let {
                            if (it < 1000) "${it.toInt()} metros" else String.format("%.1f km", it / 1000f)
                        } ?: "Calculando..."

                        Text(
                            text = distanceText,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )

                        Text(
                            text = proximityState.currentZone.description,
                            color = GrayMuted,
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        )
                    }
                }
            }

            // CHIPS DE ESTADO DE HARDWARE (Google Play Services, BLE, Wi-Fi)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HardwareStatusChip(
                    icon = Icons.Default.MyLocation,
                    label = "GPS Google",
                    isActive = proximityState.isServiceRunning,
                    modifier = Modifier.weight(1f)
                )
                HardwareStatusChip(
                    icon = Icons.Default.Bluetooth,
                    label = "BLE Actuador",
                    isActive = proximityState.bleAvailable,
                    modifier = Modifier.weight(1f)
                )
                HardwareStatusChip(
                    icon = Icons.Default.Wifi,
                    label = "Wi-Fi Garita",
                    isActive = proximityState.wifiAvailable,
                    modifier = Modifier.weight(1f)
                )
            }

            // FILOSOFÍA "TIEMPO = FAMILIA" - MÉTRICA ACUMULADA
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = GoldPrimary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "TIEMPO DEVUELTO (CERO FILAS)",
                                color = GoldPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${proximityState.totalAutomaticOpeningsCount} ingresos fluidos acumulados",
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Text(
                        text = "+${proximityState.totalTimeSavedMinutes} min",
                        color = GoldPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // BOTONES DE ACCIÓN (DISPARO MANUAL Y SIMULADOR DE LLEGADA)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Botón: Disparo Manual Directo
                Button(
                    onClick = {
                        PradosGeofenceForegroundService.triggerManualOpen(context)
                        Toast.makeText(context, "Pulso de apertura enviado por BLE y Wi-Fi", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("btn_trigger_manual_gate"),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Abrir Portón",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Botón: Simular Llegada (Radio Corto 45m)
                OutlinedButton(
                    onClick = {
                        PradosGeofenceForegroundService.simulateApproach(context, 45f)
                        Toast.makeText(context, "Simulación: Aproximándose a Av. de la Cantera 2750...", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("btn_simulate_approach"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon),
                    border = BorderStroke(1.dp, CyanNeon),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Simular (50m)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // ENLACE A HISTORIAL DE APERTURAS
            if (proximityState.eventsHistory.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showHistoryDialog = true }
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Última apertura: ${proximityState.lastOpeningFolio ?: "N/A"}",
                        color = GrayMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Ver Historial >",
                        color = CyanNeon,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // DIÁLOGO DE HISTORIAL DE APERTURAS AUTOMÁTICAS
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            containerColor = NavyDark,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = GoldPrimary)
                    Text(
                        text = "Historial de Aperturas BLE/Wi-Fi",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Registro inmutable en Room SQLite de accesos en Radio Corto (50m):",
                        color = GrayMuted,
                        fontSize = 11.sp
                    )

                    proximityState.eventsHistory.forEach { event ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = NavyCard),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = event.folio,
                                        color = GoldPrimary,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = event.formattedTime,
                                        color = GrayMuted,
                                        fontSize = 10.sp
                                    )
                                }
                                Text(
                                    text = event.details,
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "Método: ${event.method} • Ahorro: +${event.timeSavedSeconds}s devueltos",
                                    color = SuccessGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("Cerrar", color = GoldPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun HardwareStatusChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = if (isActive) SuccessGreen.copy(alpha = 0.12f) else NavyDark,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            1.dp,
            if (isActive) SuccessGreen.copy(alpha = 0.5f) else NavyBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) SuccessGreen else GrayMuted,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = if (isActive) Color.White else GrayMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
