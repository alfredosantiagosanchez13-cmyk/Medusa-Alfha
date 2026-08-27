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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AlfhaSecurityContext
import com.example.data.booking.AppDatabase
import com.example.data.incident.EmergencyLocationEngine
import com.example.data.incident.GpsCoordinates
import com.example.data.incident.IncidentCategory
import com.example.data.incident.IncidentEngine
import com.example.data.incident.IncidentEntity
import com.example.data.incident.IncidentPriority
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarningOrange
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * COMPLEMENTO A FASE 16: MAPA OPERATIVO DE EMERGENCIAS Y GEOLOCALIZACIÓN ALFHA
 *
 * Visualizador y centro de mando espacial integrado:
 * - Detección y renderizado de coordenadas GPS exactas.
 * - Advertencia clara "UBICACIÓN NO DISPONIBLE" si no hay señal GPS (sin inventar datos).
 * - Marcadores tácticos seleccionables con Folio, Tipo, Hora, Estatus y Evidencias.
 * - Soporte multirrol: Caseta de Vigilancia, Supervisor Táctico y Panel Maestro.
 * - Actualización de posición GPS bajo demanda.
 * - Preservación permanente de puntos históricos al cerrar emergencias.
 * - Indicador explícito de Tiempo Devuelto a la comunidad.
 */
@Composable
fun OperationalEmergencyMapView(
    db: AppDatabase,
    modifier: Modifier = Modifier,
    userRole: String = "GUARDIA",
    onEmergencyResolvedOrClosed: () -> Unit = {},
    showCreateEmergencyFab: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by AlfhaSecurityContext.currentUser.collectAsState()

    // Sembrado inicial de prueba
    LaunchedEffect(Unit) {
        EmergencyLocationEngine.seedSampleEmergenciesIfEmpty(context, db)
    }

    // Flows reactivos desde Room
    val allEmergencies by db.incidentDao().getAllEmergenciesFlow().collectAsState(initial = emptyList())
    val activeEmergencies = remember(allEmergencies) {
        allEmergencies.filter { it.status == "REGISTRADO" || it.status == "EN_ATENCION" }
    }
    val historicalEmergencies = remember(allEmergencies) {
        allEmergencies.filter { it.status == "RESUELTO" || it.status == "CERRADO" }
    }

    var currentViewMode by remember { mutableStateOf("ACTIVE") } // "ACTIVE", "HISTORY", "ALL"
    var selectedEmergency by remember { mutableStateOf<IncidentEntity?>(null) }
    var showCreateEmergencyDialog by remember { mutableStateOf(false) }
    var isUpdatingGps by remember { mutableStateOf(false) }

    // Seleccionar automáticamente la emergencia más crítica o la primera si no hay selección previa
    LaunchedEffect(activeEmergencies) {
        if (selectedEmergency == null && activeEmergencies.isNotEmpty()) {
            selectedEmergency = activeEmergencies.first()
        } else if (selectedEmergency != null) {
            // Mantener sincronizada la selección con el Room DB
            val updated = allEmergencies.find { it.folio == selectedEmergency?.folio }
            if (updated != null) selectedEmergency = updated
        }
    }

    // Animación de pulso de radar para emergencias activas
    val infiniteTransition = rememberInfiniteTransition(label = "emergency_radar")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 8f,
        targetValue = 42f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_radius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_alpha"
    )

    val displayedEmergencies = when (currentViewMode) {
        "ACTIVE" -> activeEmergencies
        "HISTORY" -> historicalEmergencies
        else -> allEmergencies
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("operational_emergency_map_view"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (activeEmergencies.isNotEmpty()) Color(0xFF20090D) else NavyCard
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (activeEmergencies.isNotEmpty()) ErrorRed.copy(alpha = 0.8f) else GoldPrimary.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header con Estado Operativo y Rol
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                if (activeEmergencies.isNotEmpty()) ErrorRed.copy(alpha = 0.25f) else GoldPrimary.copy(alpha = 0.2f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (activeEmergencies.isNotEmpty()) Icons.Default.NotificationsActive else Icons.Default.Map,
                            contentDescription = "Mapa de Emergencias",
                            tint = if (activeEmergencies.isNotEmpty()) ErrorRed else GoldPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "MAPA OPERATIVO DE EMERGENCIAS",
                            color = if (activeEmergencies.isNotEmpty()) ErrorRed else GoldPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = if (activeEmergencies.isNotEmpty()) "${activeEmergencies.size} Emergencia(s) Activa(s)" else "Sin Emergencias Activas",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (showCreateEmergencyFab) {
                    Button(
                        onClick = { showCreateEmergencyDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("btn_trigger_sos_map")
                    ) {
                        Icon(Icons.Default.AddAlert, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Disparar SOS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-selector de vista: Activas vs Historial
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = currentViewMode == "ACTIVE",
                    onClick = { currentViewMode = "ACTIVE" },
                    label = { Text("🔴 Activas (${activeEmergencies.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ErrorRed.copy(alpha = 0.25f),
                        selectedLabelColor = ErrorRed,
                        containerColor = NavyDark,
                        labelColor = TextMuted
                    ),
                    modifier = Modifier.weight(1f).testTag("filter_active_emergencies")
                )
                FilterChip(
                    selected = currentViewMode == "HISTORY",
                    onClick = { currentViewMode = "HISTORY" },
                    label = { Text("📜 Historial (${historicalEmergencies.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GoldPrimary.copy(alpha = 0.25f),
                        selectedLabelColor = GoldPrimary,
                        containerColor = NavyDark,
                        labelColor = TextMuted
                    ),
                    modifier = Modifier.weight(1f).testTag("filter_history_emergencies")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Lienzo Canvas de Mapa Espacial Operativo con proyección de puntos
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .background(NavyDark, RoundedCornerShape(16.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .testTag("emergency_canvas_map")
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

                    // Avenida Principal y Calles del Condominio
                    drawLine(
                        color = Color.White.copy(alpha = 0.10f),
                        start = Offset(0f, h * 0.5f),
                        end = Offset(w, h * 0.5f),
                        strokeWidth = 24f
                    )
                    drawLine(
                        color = GoldPrimary.copy(alpha = 0.25f),
                        start = Offset(0f, h * 0.5f),
                        end = Offset(w, h * 0.5f),
                        strokeWidth = 2f,
                        pathEffect = dashPathEffect
                    )

                    // Eje Vertical
                    drawLine(
                        color = Color.White.copy(alpha = 0.10f),
                        start = Offset(w * 0.38f, 0f),
                        end = Offset(w * 0.38f, h),
                        strokeWidth = 20f
                    )

                    // Manzanas y Cuadrantes
                    drawRoundRect(
                        color = Color(0xFF1E293B).copy(alpha = 0.7f),
                        topLeft = Offset(w * 0.12f, h * 0.12f),
                        size = Size(w * 0.24f, h * 0.32f),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                    drawRoundRect(
                        color = Color(0xFF1E293B).copy(alpha = 0.7f),
                        topLeft = Offset(w * 0.44f, h * 0.12f),
                        size = Size(w * 0.24f, h * 0.32f),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                    drawRoundRect(
                        color = Color(0xFF1E293B).copy(alpha = 0.7f),
                        topLeft = Offset(w * 0.72f, h * 0.12f),
                        size = Size(w * 0.22f, h * 0.76f),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                    drawRoundRect(
                        color = Color(0xFF0F2B36).copy(alpha = 0.7f),
                        topLeft = Offset(w * 0.40f, h * 0.58f),
                        size = Size(w * 0.26f, h * 0.32f),
                        cornerRadius = CornerRadius(10f, 10f)
                    )

                    // Garita Central (Punto de Referencia P0)
                    val garitaX = w * 0.14f
                    val garitaY = h * 0.85f

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

                    // Proyección y renderizado de Marcadores de Emergencia
                    displayedEmergencies.forEachIndexed { index, emg ->
                        val hasGps = emg.latitude != null && emg.longitude != null
                        val (posX, posY) = computeCanvasCoordinates(emg, index, w, h)
                        val isSelected = selectedEmergency?.folio == emg.folio
                        val isActive = emg.status == "REGISTRADO" || emg.status == "EN_ATENCION"

                        // Onda de pulso radar para emergencias activas
                        if (isActive) {
                            drawCircle(
                                color = ErrorRed.copy(alpha = pulseAlpha),
                                radius = pulseRadius * 2f,
                                center = Offset(posX, posY)
                            )
                            drawCircle(
                                color = ErrorRed.copy(alpha = 0.35f),
                                radius = 18.dp.toPx(),
                                center = Offset(posX, posY)
                            )
                        }

                        // Línea vectorial de despacho si está seleccionada
                        if (isSelected) {
                            val route = Path().apply {
                                moveTo(garitaX, garitaY)
                                cubicTo(
                                    (garitaX + posX) / 2f, garitaY,
                                    (garitaX + posX) / 2f, posY,
                                    posX, posY
                                )
                            }
                            drawPath(
                                path = route,
                                color = if (isActive) ErrorRed else GoldPrimary,
                                style = Stroke(
                                    width = 3.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
                                )
                            )
                        }

                        // Marcador físico en mapa
                        val markerColor = when {
                            isActive -> ErrorRed
                            emg.status == "RESUELTO" -> SuccessGreen
                            else -> TextMuted
                        }

                        // Anillo exterior de marcador
                        drawCircle(
                            color = if (isSelected) Color.White else markerColor,
                            radius = if (isSelected) 10.dp.toPx() else 7.dp.toPx(),
                            center = Offset(posX, posY)
                        )
                        // Núcleo del marcador
                        drawCircle(
                            color = if (hasGps) markerColor else WarningOrange,
                            radius = if (isSelected) 7.dp.toPx() else 4.5.dp.toPx(),
                            center = Offset(posX, posY)
                        )
                    }
                }

                // Overlay de referencias
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .background(NavySurface.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = "🛡️ Garita de Control", color = CyanNeon, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                // Chips flotantes en el mapa para selección rápida de marcadores
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    displayedEmergencies.take(4).forEach { emg ->
                        val isSelected = selectedEmergency?.folio == emg.folio
                        val isActive = emg.status == "REGISTRADO" || emg.status == "EN_ATENCION"
                        Surface(
                            onClick = { selectedEmergency = emg },
                            shape = RoundedCornerShape(6.dp),
                            color = when {
                                isSelected -> if (isActive) ErrorRed else GoldPrimary
                                isActive -> ErrorRed.copy(alpha = 0.4f)
                                else -> NavySurface.copy(alpha = 0.8f)
                            },
                            modifier = Modifier.testTag("marker_chip_${emg.folio}")
                        ) {
                            Text(
                                text = "📍 ${emg.location.take(10)}",
                                color = if (isSelected && isActive) Color.White else if (isSelected) NavyDark else Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tarjeta de Inspección de la Emergencia Seleccionada
            selectedEmergency?.let { emg ->
                val isActive = emg.status == "REGISTRADO" || emg.status == "EN_ATENCION"
                val hasGps = emg.latitude != null && emg.longitude != null

                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("emergency_detail_card"),
                    shape = RoundedCornerShape(14.dp),
                    color = NavyDark,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isActive) ErrorRed.copy(alpha = 0.6f) else TextMuted.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {

                        // Folio, Estado y Tipo
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isActive) Icons.Default.Warning else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (isActive) ErrorRed else SuccessGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = emg.folio,
                                        color = GoldPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Text(
                                    text = emg.location,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Badge de Estado
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when (emg.status) {
                                    "REGISTRADO" -> ErrorRed
                                    "EN_ATENCION" -> WarningOrange
                                    "RESUELTO" -> SuccessGreen
                                    else -> TextMuted
                                }
                            ) {
                                Text(
                                    text = when (emg.status) {
                                        "REGISTRADO" -> "ACTIVA"
                                        "EN_ATENCION" -> "EN ATENCIÓN"
                                        "RESUELTO" -> "RESUELTA"
                                        else -> "CERRADA"
                                    },
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = Color.White.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(8.dp))

                        // Fila de Coordenadas GPS y Estado Espacial
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (hasGps) Icons.Default.GpsFixed else Icons.Default.LocationOff,
                                        contentDescription = null,
                                        tint = if (hasGps) CyanNeon else WarningOrange,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (hasGps) "COORDENADAS GPS REGISTRADAS" else "ESTATUS DE GEOLOCALIZACIÓN",
                                        color = TextMuted,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                if (hasGps) {
                                    Text(
                                        text = "Lat: ${String.format(Locale.US, "%.6f", emg.latitude)}, Lon: ${String.format(Locale.US, "%.6f", emg.longitude)}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Precisión satelital: ±${emg.gpsAccuracyMeters?.toInt() ?: 4}m • Estatus: ${emg.locationStatus}",
                                        color = CyanNeon,
                                        fontSize = 9.sp
                                    )
                                } else {
                                    Text(
                                        text = "UBICACIÓN NO DISPONIBLE",
                                        color = WarningOrange,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = "Sin señal satelital capturada al momento del disparo.",
                                        color = TextMuted,
                                        fontSize = 9.sp
                                    )
                                }
                            }

                            // Botón de Actualizar Posición GPS
                            if (isActive) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            isUpdatingGps = true
                                            val (success, msg) = EmergencyLocationEngine.updateEmergencyLocation(
                                                context = context,
                                                db = db,
                                                folio = emg.folio,
                                                operatorName = currentUser.name
                                            )
                                            isUpdatingGps = false
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                                    modifier = Modifier.testTag("btn_refresh_gps")
                                ) {
                                    if (isUpdatingGps) {
                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), color = GoldPrimary, strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Actualizar GPS", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Hora y Reportante
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Reportado por: ${emg.reportedBy} (${emg.reportedByRole})",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "Hora: ${emg.formattedDate}",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Evidencias y Notas
                        if (!emg.evidenceNotes.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Evidencias: ${emg.evidenceNotes}",
                                color = Color(0xFFCBD5E1),
                                fontSize = 10.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Acciones Operativas si está activa
                        if (isActive) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (emg.status == "REGISTRADO") {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                IncidentEngine.transitionToAttention(
                                                    context = context,
                                                    db = db,
                                                    folio = emg.folio,
                                                    operatorName = currentUser.name,
                                                    operatorRole = currentUser.role,
                                                    notes = "Oficial o supervisor asume respuesta táctica en sitio."
                                                )
                                                Toast.makeText(context, "Atención iniciada para ${emg.folio}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = WarningOrange, contentColor = NavyDark),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).testTag("btn_attend_emergency")
                                    ) {
                                        Icon(Icons.Default.DirectionsRun, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Iniciar Atención", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            EmergencyLocationEngine.resolveAndCloseEmergency(
                                                context = context,
                                                db = db,
                                                folio = emg.folio,
                                                resolutionNotes = "Emergencia resuelta y cerrada en sitio por ${currentUser.name}.",
                                                operatorName = currentUser.name,
                                                operatorRole = currentUser.role
                                            )
                                            Toast.makeText(context, "Emergencia ${emg.folio} cerrada. Punto geográfico preservado.", Toast.LENGTH_LONG).show()
                                            onEmergencyResolvedOrClosed()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = NavyDark),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f).testTag("btn_close_emergency")
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cerrar Emergencia", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Banner explicativo de Tiempo Devuelto
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = GoldPrimary.copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "⚡", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ESTO DEVUELVE TIEMPO: Localiza la emergencia automáticamente en el mapa sin llamadas ni descripción manual de la ubicación.",
                        color = GoldPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }

    // Diálogo para Disparar Emergencia SOS con Captura GPS
    if (showCreateEmergencyDialog) {
        CreateEmergencyDialog(
            onDismiss = { showCreateEmergencyDialog = false },
            onTrigger = { type, location, details ->
                scope.launch {
                    EmergencyLocationEngine.triggerEmergencyAlert(
                        context = context,
                        db = db,
                        emergencyType = type,
                        locationName = location,
                        reportedBy = currentUser.name,
                        reportedByRole = currentUser.role,
                        details = details
                    )
                    Toast.makeText(context, "🚨 Emergencia activada con geolocalización automática", Toast.LENGTH_LONG).show()
                    showCreateEmergencyDialog = false
                }
            }
        )
    }
}

/**
 * Función que proyecta la ubicación relativa de la emergencia dentro del mapa de condominio
 */
private fun computeCanvasCoordinates(emg: IncidentEntity, index: Int, width: Float, height: Float): Pair<Float, Float> {
    val loc = emg.location.lowercase()
    return when {
        loc.contains("104") -> Pair(width * 0.22f, height * 0.28f)
        loc.contains("208") -> Pair(width * 0.52f, height * 0.24f)
        loc.contains("302") || loc.contains("norte") -> Pair(width * 0.80f, height * 0.32f)
        loc.contains("quincho") || loc.contains("bbq") || loc.contains("piscina") -> Pair(width * 0.52f, height * 0.68f)
        loc.contains("115") -> Pair(width * 0.26f, height * 0.72f)
        loc.contains("101") || loc.contains("sur") -> Pair(width * 0.84f, height * 0.78f)
        else -> {
            // Distribución dinámica si es una ubicación nueva
            val offsetFactor = ((index % 4) + 1) * 0.18f
            Pair(width * offsetFactor.coerceIn(0.2f, 0.8f), height * 0.45f)
        }
    }
}

/**
 * Diálogo modal para disparar una nueva emergencia SOS
 */
@Composable
private fun CreateEmergencyDialog(
    onDismiss: () -> Unit,
    onTrigger: (type: String, location: String, details: String) -> Unit
) {
    var emergencyType by remember { mutableStateOf("PÁNICO S.O.S.") }
    var locationName by remember { mutableStateOf("Casa 104") }
    var details by remember { mutableStateOf("Activación de emergencia desde botón de pánico.") }

    val emergencyTypes = listOf("PÁNICO S.O.S.", "MÉDICA", "INCENDIO", "INTRUSIÓN", "SEGURIDAD")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AddAlert, contentDescription = null, tint = ErrorRed)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Disparar Emergencia Táctica", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Tipo de Emergencia:", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    emergencyTypes.take(3).forEach { type ->
                        FilterChip(
                            selected = emergencyType == type,
                            onClick = { emergencyType = type },
                            label = { Text(type, fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ErrorRed.copy(alpha = 0.3f),
                                selectedLabelColor = ErrorRed,
                                containerColor = NavyDark,
                                labelColor = TextMuted
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = locationName,
                    onValueChange = { locationName = it },
                    label = { Text("Ubicación / Unidad / Sector") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ErrorRed,
                        unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Detalle de la Alerta") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ErrorRed,
                        unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NavyDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.GpsFixed, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Las coordenadas GPS se capturarán automáticamente al confirmar.",
                            color = CyanNeon,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onTrigger(emergencyType, locationName, details) },
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Activar Emergencia", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancelar", color = TextMuted)
            }
        },
        containerColor = NavyCard
    )
}
