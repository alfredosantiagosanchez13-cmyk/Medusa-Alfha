package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.booking.AppDatabase
import com.example.data.fcm.EmergencyAlertFcmPayload
import com.example.data.fcm.FcmNotificationManager
import com.example.data.incident.EmergencyLocationEngine
import com.example.data.incident.GpsCoordinates
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.theme.WarningOrange
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Botón de Acción Rápida S.O.S. para la barra superior (TopAppBar) o flotante.
 * Emite una animación pulsante de advertencia y abre el diálogo de emergencia al presionar.
 */
@Composable
fun ResidentEmergencyTopBarButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "emergency_pulse_topbar")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .testTag("resident_emergency_sos_button")
            .padding(horizontal = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = ErrorRed.copy(alpha = 0.2f),
        border = BorderStroke(1.5.dp, ErrorRed.copy(alpha = glowAlpha))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .scale(pulseScale)
                        .background(ErrorRed.copy(alpha = 0.4f), CircleShape)
                )
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Emergencia S.O.S.",
                    tint = ErrorRed,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = "S.O.S.",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
    }
}

/**
 * Banner de Alto Impacto para el feed del Dashboard del Residente.
 * Muestra el estatus de la unidad, la capacidad de despacho FCM y un botón de llamada a la acción.
 */
@Composable
fun ResidentEmergencyBannerCard(
    residentUnit: String,
    condominiumName: String,
    modifier: Modifier = Modifier,
    onOpenEmergencyDialog: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "banner_pulse")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "border_alpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("resident_emergency_sos_banner"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.5.dp, ErrorRed.copy(alpha = borderAlpha))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ErrorRed.copy(alpha = 0.22f),
                            NavySurface.copy(alpha = 0.85f),
                            NavyCard
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ErrorRed.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, ErrorRed)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "BOTÓN DE EMERGENCIA S.O.S.",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Despacho Inmediato a Garita vía Firebase FCM",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = null,
                                tint = CyanNeon,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = residentUnit.ifBlank { "Unidad" },
                                color = CyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "En caso de pánico, intrusión o emergencia médica en ${residentUnit.ifBlank { "su unidad" }}, presione el botón para transmitir sus coordenadas y unidad a los guardias de seguridad en tiempo real.",
                    color = TextWhite.copy(alpha = 0.88f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onOpenEmergencyDialog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("resident_emergency_sos_banner_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ErrorRed,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ACTIVAR ALERTA DE EMERGENCIA S.O.S.",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

/**
 * Categorías de Emergencia disponibles para selección rápida por parte del residente.
 */
enum class EmergencyCategory(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
) {
    PANIC("Pánico S.O.S.", "Asistencia y auxilio inmediato de seguridad", Icons.Default.Warning, ErrorRed),
    MEDICAL("Emergencia Médica", "Requiere botiquín / ambulancia o paramédico", Icons.Outlined.MedicalServices, CyanNeon),
    INTRUSION("Intrusión / Sospecha", "Persona no identificada o ruidos extraños", Icons.Outlined.Shield, WarningOrange),
    FIRE("Incendio / Fuego", "Humo, fuego o riesgo de propagación", Icons.Outlined.LocalFireDepartment, GoldAccent)
}

/**
 * Diálogo Modal Completo de Emergencia.
 * Permite al residente:
 * 1. Visualizar su Unidad Habitacional asignada
 * 2. Visualizar y verificar la Ubicación GPS en tiempo real
 * 3. Seleccionar la categoría de emergencia
 * 4. Añadir notas de situación rápidas
 * 5. Despachar a Firebase Cloud Messaging (FCM) hacia todo el personal de seguridad
 */
@Composable
fun ResidentEmergencyDialog(
    residentUnit: String,
    residentName: String,
    residentId: String,
    condominiumName: String,
    db: AppDatabase,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedCategory by remember { mutableStateOf(EmergencyCategory.PANIC) }
    var notesText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var dispatchedAlert by remember { mutableStateOf<EmergencyAlertFcmPayload?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Captura de GPS local
    var currentGps by remember { mutableStateOf<GpsCoordinates?>(null) }
    var gpsStatusText by remember { mutableStateOf("Obteniendo coordenadas GPS...") }

    // Obtener GPS al abrir el diálogo
    LaunchedEffect(Unit) {
        val captured = EmergencyLocationEngine.captureCurrentGps(context)
        currentGps = captured
        gpsStatusText = if (captured != null) {
            val acc = captured.accuracyMeters?.toInt() ?: 10
            "Lat: ${String.format(Locale.US, "%.5f", captured.latitude)}, Lon: ${String.format(Locale.US, "%.5f", captured.longitude)} (±${acc}m)"
        } else {
            "Ubicación basada en Padrón de Unidad Habitacional ($residentUnit)"
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isSending) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .testTag("resident_emergency_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = NavySurface,
            border = BorderStroke(2.dp, if (dispatchedAlert != null) SuccessGreen else ErrorRed)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Cabecera
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (dispatchedAlert != null) SuccessGreen.copy(alpha = 0.2f) else ErrorRed.copy(alpha = 0.2f),
                            border = BorderStroke(1.5.dp, if (dispatchedAlert != null) SuccessGreen else ErrorRed)
                        ) {
                            Icon(
                                imageVector = if (dispatchedAlert != null) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (dispatchedAlert != null) SuccessGreen else ErrorRed,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (dispatchedAlert != null) "ALERTA TRANSMITIDA (FCM)" else "ALERTA DE EMERGENCIA",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = if (dispatchedAlert != null) "Personal de seguridad notificado" else "Despacho inmediato a personal de caseta",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("dismiss_emergency_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Estado de Éxito posterior al despacho
                if (dispatchedAlert != null) {
                    val alert = dispatchedAlert!!
                    EmergencySuccessView(
                        alert = alert,
                        context = context,
                        onClose = onDismiss
                    )
                } else {
                    // Pantalla de Formulario de Despacho
                    EmergencyFormView(
                        residentUnit = residentUnit,
                        residentName = residentName,
                        condominiumName = condominiumName,
                        currentGps = currentGps,
                        gpsStatusText = gpsStatusText,
                        selectedCategory = selectedCategory,
                        onCategorySelect = { selectedCategory = it },
                        notesText = notesText,
                        onNotesChange = { notesText = it },
                        isSending = isSending,
                        errorMessage = errorMessage,
                        onRefreshGps = {
                            val refreshed = EmergencyLocationEngine.captureCurrentGps(context)
                            currentGps = refreshed
                            gpsStatusText = if (refreshed != null) {
                                val acc = refreshed.accuracyMeters?.toInt() ?: 10
                                "Lat: ${String.format(Locale.US, "%.5f", refreshed.latitude)}, Lon: ${String.format(Locale.US, "%.5f", refreshed.longitude)} (±${acc}m)"
                            } else {
                                "Ubicación basada en Padrón de Unidad Habitacional ($residentUnit)"
                            }
                        },
                        onSendAlert = {
                            isSending = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    val result = FcmNotificationManager.sendResidentEmergencyAlertFcm(
                                        context = context,
                                        db = db,
                                        condominiumId = condominiumName,
                                        residentUnit = residentUnit.ifBlank { "Unidad Habitacional" },
                                        residentName = residentName.ifBlank { "Residente" },
                                        residentId = residentId,
                                        emergencyType = selectedCategory.label,
                                        details = notesText,
                                        manualGps = currentGps
                                    )

                                    if (result.isSuccess) {
                                        dispatchedAlert = result.getOrNull()
                                    } else {
                                        errorMessage = "No se pudo transmitir alerta: ${result.exceptionOrNull()?.message}"
                                    }
                                } catch (e: Exception) {
                                    errorMessage = "Error transmitiendo alerta: ${e.message}"
                                } finally {
                                    isSending = false
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * Vista con detalles de la unidad, estatus de GPS, selector y botón de despacho.
 */
@Composable
private fun EmergencyFormView(
    residentUnit: String,
    residentName: String,
    condominiumName: String,
    currentGps: GpsCoordinates?,
    gpsStatusText: String,
    selectedCategory: EmergencyCategory,
    onCategorySelect: (EmergencyCategory) -> Unit,
    notesText: String,
    onNotesChange: (String) -> Unit,
    isSending: Boolean,
    errorMessage: String?,
    onRefreshGps: () -> Unit,
    onSendAlert: () -> Unit
) {
    // 1. Tarjeta con Número de Unidad y Residente
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = CyanNeon,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "UNIDAD HABITACIONAL",
                        color = CyanNeon,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = CyanNeon.copy(alpha = 0.15f),
                    border = BorderStroke(0.5.dp, CyanNeon)
                ) {
                    Text(
                        text = "Padrón Verificado",
                        color = CyanNeon,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = residentUnit.ifBlank { "Unidad Sin Especificar" },
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.testTag("emergency_resident_unit_text")
            )

            Text(
                text = "Residente: ${residentName.ifBlank { "Titular de Cuenta" }} • $condominiumName",
                color = TextMuted,
                fontSize = 12.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // 2. Tarjeta de Ubicación y Estado de Coordenadas
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(
            1.dp,
            if (currentGps != null) SuccessGreen.copy(alpha = 0.4f) else GoldAccent.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (currentGps != null) Icons.Default.ShareLocation else Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = if (currentGps != null) SuccessGreen else GoldAccent,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = if (currentGps != null) "🛰️ COORDENADAS GPS CAPTURADAS" else "📍 UBICACIÓN RESIDENCIAL",
                        color = if (currentGps != null) SuccessGreen else GoldAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = gpsStatusText,
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("emergency_location_status_text")
                    )
                }
            }

            IconButton(
                onClick = onRefreshGps,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ShareLocation,
                    contentDescription = "Refrescar GPS",
                    tint = CyanNeon,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 3. Selector de Categoría de Emergencia
    Text(
        text = "TIPO DE EMERGENCIA",
        color = TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )

    Spacer(modifier = Modifier.height(8.dp))

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EmergencyCategory.values().forEach { category ->
            val isSelected = category == selectedCategory
            Surface(
                onClick = { onCategorySelect(category) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) category.color.copy(alpha = 0.2f) else NavyCard,
                border = BorderStroke(
                    1.5.dp,
                    if (isSelected) category.color else Color.White.copy(alpha = 0.1f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = category.color.copy(alpha = 0.25f)
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = category.color,
                            modifier = Modifier
                                .padding(8.dp)
                                .size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = category.label,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = category.description,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Seleccionado",
                            tint = category.color,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 4. Notas Rápidas / Detalles Opcionales
    Text(
        text = "DETALLE DE LA SITUACIÓN (OPCIONAL)",
        color = TextMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )

    Spacer(modifier = Modifier.height(6.dp))

    // Chips de sugerencia rápida
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf("Entrada forzada", "Persona herida", "Ruidos extraños").forEach { chip ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = NavyCard,
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier.clickable {
                    onNotesChange(if (notesText.isBlank()) chip else "$notesText. $chip")
                }
            ) {
                Text(
                    text = chip,
                    color = TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    OutlinedTextField(
        value = notesText,
        onValueChange = onNotesChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("emergency_details_input"),
        placeholder = {
            Text(
                text = "Describa brevemente lo sucedido si es seguro hacerlo...",
                color = TextMuted.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        },
        maxLines = 3,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = NavyCard,
            unfocusedContainerColor = NavyCard,
            focusedBorderColor = ErrorRed,
            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
        )
    )

    if (errorMessage != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = errorMessage,
            color = ErrorRed,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }

    Spacer(modifier = Modifier.height(18.dp))

    // 5. Botón de Disparo Inmediato
    Button(
        onClick = onSendAlert,
        enabled = !isSending,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .testTag("confirm_send_emergency_button"),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ErrorRed,
            contentColor = Color.White
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        if (isSending) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "TRANSMITIENDO VÍA FCM...",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "TRANSMITIR ALERTA A SEGURIDAD (FCM)",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "⚡ Se emitirá notificación inmediata con sirena en las terminales de caseta y supervisores de seguridad.",
        color = TextMuted,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Vista de Confirmación de Transmisión Exitosa.
 */
@Composable
private fun EmergencySuccessView(
    alert: EmergencyAlertFcmPayload,
    context: Context,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "¡ALERTA TRANSMITIDA CON ÉXITO!",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "El personal de seguridad de Garita ha recibido la notificación push de FCM en sus terminales con la información de su unidad y coordenadas.",
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Resumen de datos transmitidos
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = NavyDark.copy(alpha = 0.6f),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Folio Auditoría:", color = TextMuted, fontSize = 11.sp)
                        Text(text = alert.alertFolio, color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Unidad:", color = TextMuted, fontSize = 11.sp)
                        Text(text = alert.residentUnit, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Tipo:", color = TextMuted, fontSize = 11.sp)
                        Text(text = alert.emergencyType, color = ErrorRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Ubicación:", color = TextMuted, fontSize = 11.sp)
                        Text(
                            text = if (alert.latitude != null && alert.longitude != null) {
                                "${String.format(Locale.US, "%.4f", alert.latitude)}, ${String.format(Locale.US, "%.4f", alert.longitude)}"
                            } else {
                                "Padrón Unidad"
                            },
                            color = SuccessGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Accesos directos para llamada telefónica de apoyo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:911"))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, ErrorRed)
                ) {
                    Icon(imageVector = Icons.Default.Call, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "911", color = ErrorRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:5552345678"))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CyanNeon)
                ) {
                    Icon(imageVector = Icons.Default.Call, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Caseta", color = CyanNeon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("emergency_resolve_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NavySurface,
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Text(text = "Entendido / Cerrar Ventana", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
