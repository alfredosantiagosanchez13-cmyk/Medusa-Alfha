package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted

data class UnitLocation(
    val unitId: String,
    val residentName: String,
    val blockLabel: String,
    val xRatio: Float, // 0.0f to 1.0f on canvas width
    val yRatio: Float, // 0.0f to 1.0f on canvas height
    val distanceMeters: Int
)

data class PanicAlertEvent(
    val id: String,
    val unit: UnitLocation,
    val alertType: String,
    val severity: String, // "CRÍTICO", "ALTO", "MODERADO"
    val timestamp: String,
    val isResolved: Boolean = false
)

object SampleCondoUnits {
    val units = listOf(
        UnitLocation("Casa 104", "Fam. González", "Manzana A - Lote 4", 0.22f, 0.30f, 110),
        UnitLocation("Casa 208", "Carlos Mendoza", "Manzana B - Lote 8", 0.48f, 0.25f, 180),
        UnitLocation("Depto 302", "Valeria Rojas", "Torre Norte - Piso 3", 0.78f, 0.35f, 240),
        UnitLocation("Quincho BBQ", "Área Común", "Sector Amenidades", 0.50f, 0.65f, 95),
        UnitLocation("Casa 115", "Dr. Fernando Silva", "Manzana A - Lote 15", 0.28f, 0.72f, 150),
        UnitLocation("Depto 101", "Mariana López", "Torre Sur - Piso 1", 0.82f, 0.78f, 290)
    )

    val garitaLocation = UnitLocation("Garita Principal", "Puesto Guardia", "Acceso Central", 0.12f, 0.88f, 0)

    fun getDefaultPanicEvent(): PanicAlertEvent {
        return PanicAlertEvent(
            id = "PANIC-2026-904",
            unit = units[0], // Casa 104
            alertType = "🔴 ALERTA DE PÁNICO MÉRICA / S.O.S.",
            severity = "CRÍTICO",
            timestamp = "Hace 45 seg (07:44:12)"
        )
    }
}

@Composable
fun PanicFloorPlanCard(
    activeAlert: PanicAlertEvent?,
    onSimulatePanicTrigger: (UnitLocation) -> Unit,
    onResolveAlert: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedUnit by remember { mutableStateOf<UnitLocation?>(activeAlert?.unit) }

    val currentAlertUnit = activeAlert?.unit ?: selectedUnit ?: SampleCondoUnits.units[0]

    // Pulsing radar wave animation for active panic unit
    val infiniteTransition = rememberInfiniteTransition(label = "panic_pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = 45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_radius"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("panic_floor_plan_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (activeAlert != null) Color(0xFF2C0E14) else NavyCard
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (activeAlert != null) Color(0xFFEF4444) else GoldPrimary.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (activeAlert != null) Color(0xFFEF4444) else GoldPrimary.copy(alpha = 0.2f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (activeAlert != null) Icons.Default.NotificationsActive else Icons.Default.Map,
                            contentDescription = "Mapa de Pánico",
                            tint = if (activeAlert != null) Color.White else GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = if (activeAlert != null) "🚨 ALERTA DE PÁNICO ACTIVA" else "UBICACIÓN ESPACIAL • PLANO CONDOMINIO",
                            color = if (activeAlert != null) Color(0xFFEF4444) else GoldPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = if (activeAlert != null) "${activeAlert.unit.unitId} - ${activeAlert.unit.residentName}" else "Plano de Unidades y Garita",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (activeAlert != null) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFEF4444)
                    ) {
                        Text(
                            text = "⚡ S.O.S. ALTA PRIORIDAD",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Canvas Floor Plan Representation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(NavyDark, RoundedCornerShape(14.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Draw condominium grid/street layout
                    val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

                    // Main Street / Avenida Principal
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(0f, h * 0.5f),
                        end = Offset(w, h * 0.5f),
                        strokeWidth = 24f
                    )
                    drawLine(
                        color = GoldPrimary.copy(alpha = 0.3f),
                        start = Offset(0f, h * 0.5f),
                        end = Offset(w, h * 0.5f),
                        strokeWidth = 2f,
                        pathEffect = dashPathEffect
                    )

                    // Vertical Connecting Street
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(w * 0.35f, 0f),
                        end = Offset(w * 0.35f, h),
                        strokeWidth = 20f
                    )

                    // Draw Sector Blocks (Manzanas)
                    // Block A
                    drawRoundRect(
                        color = Color(0xFF1E293B),
                        topLeft = Offset(w * 0.15f, h * 0.12f),
                        size = Size(w * 0.28f, h * 0.30f),
                        cornerRadius = CornerRadius(12f, 12f)
                    )

                    // Block B
                    drawRoundRect(
                        color = Color(0xFF1E293B),
                        topLeft = Offset(w * 0.45f, h * 0.12f),
                        size = Size(w * 0.22f, h * 0.30f),
                        cornerRadius = CornerRadius(12f, 12f)
                    )

                    // Towers Zone
                    drawRoundRect(
                        color = Color(0xFF1E293B),
                        topLeft = Offset(w * 0.70f, h * 0.12f),
                        size = Size(w * 0.24f, h * 0.75f),
                        cornerRadius = CornerRadius(12f, 12f)
                    )

                    // Club House / Amenity Zone
                    drawRoundRect(
                        color = Color(0xFF0F2B36),
                        topLeft = Offset(w * 0.42f, h * 0.58f),
                        size = Size(w * 0.24f, h * 0.32f),
                        cornerRadius = CornerRadius(12f, 12f)
                    )

                    // Draw Garita Location Pin (Origin)
                    val garitaX = w * SampleCondoUnits.garitaLocation.xRatio
                    val garitaY = h * SampleCondoUnits.garitaLocation.yRatio

                    drawCircle(
                        color = CyanNeon.copy(alpha = 0.3f),
                        radius = 16.dp.toPx(),
                        center = Offset(garitaX, garitaY)
                    )
                    drawCircle(
                        color = CyanNeon,
                        radius = 7.dp.toPx(),
                        center = Offset(garitaX, garitaY)
                    )

                    // Draw All Units as Map Nodes
                    SampleCondoUnits.units.forEach { unit ->
                        val uX = w * unit.xRatio
                        val uY = h * unit.yRatio
                        val isTargeted = currentAlertUnit.unitId == unit.unitId
                        val isPanicActive = activeAlert != null && activeAlert.unit.unitId == unit.unitId

                        if (isPanicActive) {
                            // Pulsing radar wave from alerted unit location
                            drawCircle(
                                color = Color(0xFFEF4444).copy(alpha = pulseAlpha),
                                radius = pulseRadius * 2f,
                                center = Offset(uX, uY)
                            )
                            drawCircle(
                                color = Color(0xFFEF4444).copy(alpha = 0.4f),
                                radius = 18.dp.toPx(),
                                center = Offset(uX, uY)
                            )
                        }

                        // Response Vector Line from Garita to Target Unit
                        if (isTargeted) {
                            val routePath = Path().apply {
                                moveTo(garitaX, garitaY)
                                cubicTo(
                                    (garitaX + uX) / 2f, garitaY,
                                    (garitaX + uX) / 2f, uY,
                                    uX, uY
                                )
                            }
                            drawPath(
                                path = routePath,
                                color = if (isPanicActive) Color(0xFFEF4444) else GoldPrimary,
                                style = Stroke(
                                    width = 3.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)
                                )
                            )
                        }

                        // Draw Unit Marker Node
                        drawCircle(
                            color = when {
                                isPanicActive -> Color(0xFFEF4444)
                                isTargeted -> GoldPrimary
                                else -> Color.Gray.copy(alpha = 0.6f)
                            },
                            radius = if (isTargeted) 8.dp.toPx() else 5.dp.toPx(),
                            center = Offset(uX, uY)
                        )
                    }
                }

                // Overlay Unit Text Badges for Garita and Alert Unit
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(NavySurface.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = "🏠 Garita (Tú)", color = CyanNeon, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(
                            if (activeAlert != null) Color(0xFFEF4444) else GoldPrimary,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (activeAlert != null) "🚨 ${currentAlertUnit.unitId} (${currentAlertUnit.distanceMeters}m)" else "📍 ${currentAlertUnit.unitId}",
                        color = NavyDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Unit Distance & Spatial Details Panel
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = NavyDark
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "UBICACIÓN DETALLADA",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${currentAlertUnit.unitId} • ${currentAlertUnit.blockLabel}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Residente: ${currentAlertUnit.residentName}",
                            color = CyanNeon,
                            fontSize = 11.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "DISTANCIA GARITA",
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${currentAlertUnit.distanceMeters} Metros",
                            color = GoldPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Est. Respuesta: ~${(currentAlertUnit.distanceMeters / 2.5).toInt()} seg",
                            color = SuccessGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Emergency Buttons or Simulation Trigger
            if (activeAlert != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                Toast.makeText(context, "Patrulla de ronda despachada a ${currentAlertUnit.unitId} 🏃‍♂️", Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dispatch_guard_patrol_btn")
                        ) {
                            Icon(Icons.Default.DirectionsRun, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Despachar Ronda", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                triggerPanicHaptic(context)
                                Toast.makeText(context, "🚨 Sirena de Garita Activada para ${currentAlertUnit.unitId}", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("trigger_siren_btn")
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Activar Sirena", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                Toast.makeText(context, "Llamando a Emergencias Policiales / 911 📞", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("call_police_btn")
                        ) {
                            Icon(Icons.Default.PhoneInTalk, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Llamar 911", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                onResolveAlert()
                                Toast.makeText(context, "Alerta de Pánico en ${currentAlertUnit.unitId} Atendida y Resuelta", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = NavyDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("resolve_panic_btn")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resolver Alerta", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Simulation selector chips for guards/testing
                Column {
                    Text(
                        text = "SIMULAR DISPARO DE PÁNICO RESIDENCIAL (PRUEBA GARITA)",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SampleCondoUnits.units.take(3).forEach { unit ->
                            Surface(
                                onClick = {
                                    triggerPanicHaptic(context)
                                    selectedUnit = unit
                                    onSimulatePanicTrigger(unit)
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedUnit?.unitId == unit.unitId) GoldPrimary.copy(alpha = 0.2f) else NavyDark,
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("simulate_panic_${unit.unitId}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 6.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(unit.unitId, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
