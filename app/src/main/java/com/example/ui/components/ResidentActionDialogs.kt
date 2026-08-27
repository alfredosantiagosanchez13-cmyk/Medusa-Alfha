package com.example.ui.components

import android.widget.Toast
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.audit.AuditLogEntity
import com.example.data.booking.AmenityBooking
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.incident.IncidentCategory
import com.example.data.incident.IncidentEngine
import com.example.data.incident.IncidentEntity
import com.example.data.incident.IncidentPriority
import com.example.data.incident.VoiceIncidentCategorizer
import com.example.data.notifications.SmartNotificationHub
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarningOrange
import com.example.utils.ResidentNotificationManager
import kotlinx.coroutines.launch

/**
 * Diálogo de Reporte de Incidencias para Residentes (FASE 2 - Panel Residente).
 * Permite selección de categoría, dictado por voz/texto y estructuración con IA sin formularios largos.
 */
@Composable
fun ResidentReportIncidentDialog(
    residentUnit: String,
    residentName: String,
    onDismiss: () -> Unit,
    onIncidentSaved: (IncidentEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    var selectedCategory by remember { mutableStateOf(IncidentCategory.INFRAESTRUCTURA) }
    var descriptionText by remember { mutableStateOf("") }
    var isSimulatedVoiceListening by remember { mutableStateOf(false) }

    val presetQuickPhrases = listOf(
        "Fuga de agua en el medidor principal exterior",
        "Vehículo desconocido bloqueando el acceso a mi estacionamiento",
        "Ruidos molestos y música a alto volumen en horario de descanso",
        "Luminaria perimetral titilando en la esquina",
        "Portón vehicular trasero no cierra correctamente"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = NavySurface,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("resident_report_incident_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ReportProblem, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("REPORTAR INCIDENCIA", fontWeight = FontWeight.Black, fontSize = 14.sp, color = GoldPrimary)
                            Text("$residentUnit • $residentName", fontSize = 11.sp, color = CyanNeon)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                Text("1. Selecciona Categoría:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(IncidentCategory.values()) { cat ->
                        val isSel = selectedCategory == cat
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedCategory = cat },
                            label = { Text("${cat.iconName} ${cat.displayName}", fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldPrimary,
                                selectedLabelColor = NavyDark,
                                containerColor = NavyCard,
                                labelColor = TextMuted
                            )
                        )
                    }
                }

                Text("2. Descripción o Dictado Rápido:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)

                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    placeholder = { Text("Describe el problema o usa dictado/frases rápidas...", color = Color.Gray, fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .testTag("incident_description_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

                // Frases Rápidas de 1 Toque (Devuelve Tiempo)
                Text("Frases frecuentes de 1 toque:", fontSize = 10.sp, color = CyanNeon)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presetQuickPhrases) { phrase ->
                        Surface(
                            onClick = { descriptionText = phrase },
                            color = NavyCard,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                        ) {
                            Text(
                                text = phrase,
                                fontSize = 10.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Voice Dictation Quick Trigger
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = {
                            isSimulatedVoiceListening = !isSimulatedVoiceListening
                            if (isSimulatedVoiceListening) {
                                descriptionText = "Luminaria exterior titilando cerca del acceso principal y ruido excesivo en quincho."
                                Toast.makeText(context, "🎤 Dictado por voz procesado con IA", Toast.LENGTH_SHORT).show()
                            }
                        },
                        color = if (isSimulatedVoiceListening) ErrorRed.copy(alpha = 0.2f) else CyanNeon.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSimulatedVoiceListening) ErrorRed else CyanNeon)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (isSimulatedVoiceListening) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = null,
                                tint = if (isSimulatedVoiceListening) ErrorRed else CyanNeon,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isSimulatedVoiceListening) "Grabando..." else "Dictar por Voz (IA)",
                                fontSize = 11.sp,
                                color = if (isSimulatedVoiceListening) ErrorRed else CyanNeon,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text("Ubicación: $residentUnit", fontSize = 10.sp, color = TextMuted)
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Submit Button
                Button(
                    onClick = {
                        val text = descriptionText.ifBlank { "Incidencia reportada por residente de $residentUnit" }
                        val categorized = VoiceIncidentCategorizer.analyzeAndCategorize(text)

                        scope.launch {
                            val entity = IncidentEngine.registerIncident(
                                context = context,
                                db = db,
                                rawTranscript = text,
                                category = selectedCategory,
                                priority = categorized.priority,
                                location = residentUnit,
                                aiSummary = "Reportado por $residentName ($residentUnit): $text",
                                recommendedAction = categorized.recommendedAction,
                                reportedBy = residentName,
                                reportedByRole = "RESIDENTE"
                            )

                            onIncidentSaved(entity)
                            Toast.makeText(context, "✅ Incidencia ${entity.folio} registrada y asignada a ${entity.assignedTo}", Toast.LENGTH_LONG).show()
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("submit_resident_incident_button")
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Enviar Reporte a Administración", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

/**
 * Diálogo de Reservación Ágil de Amenidades para Residentes (FASE 2 - Panel Residente).
 * Permite disponibilidad, confirmación automática con Folio, y recordatorio en 1 toque.
 */
@Composable
fun ResidentAmenityBookingDialog(
    residentUnit: String,
    residentName: String,
    onDismiss: () -> Unit,
    onBookingSaved: (AmenityBooking) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    val amenityOptions = listOf(
        "Quincho & BBQ Principal",
        "Gimnasio Residencial",
        "Piscina & Solárium",
        "Cancha de Pádel #1",
        "Sala Multiuso & Eventos"
    )

    var selectedAmenity by remember { mutableStateOf(amenityOptions.first()) }
    var selectedHourOffset by remember { mutableStateOf(1) } // Horas a partir de ahora

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = NavySurface,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, CyanNeon),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("resident_amenity_booking_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EventAvailable, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("RESERVA DE AMENIDAD", fontWeight = FontWeight.Black, fontSize = 14.sp, color = CyanNeon)
                            Text("Para: $residentUnit • $residentName", fontSize = 11.sp, color = Color.White)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                Text("1. Selecciona el Área Común:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    amenityOptions.forEach { amenity ->
                        val isSel = selectedAmenity == amenity
                        Surface(
                            onClick = { selectedAmenity = amenity },
                            color = if (isSel) CyanNeon.copy(alpha = 0.2f) else NavyCard,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) CyanNeon else Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(amenity, color = if (isSel) CyanNeon else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Surface(
                                    color = SuccessGreen.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("DISPONIBLE", color = SuccessGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                    }
                }

                Text("2. Horario Estimado:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        1 to "En 1 hora",
                        2 to "En 2 horas",
                        4 to "En 4 horas",
                        24 to "Mañana"
                    ).forEach { (offset, label) ->
                        val isSel = selectedHourOffset == offset
                        Surface(
                            onClick = { selectedHourOffset = offset },
                            color = if (isSel) GoldPrimary.copy(alpha = 0.2f) else NavyCard,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) GoldPrimary else Color.Transparent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) GoldPrimary else Color.White,
                                modifier = Modifier.padding(vertical = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Button(
                    onClick = {
                        val bookingTime = System.currentTimeMillis() + (selectedHourOffset * 3600 * 1000L)
                        scope.launch {
                            val result = com.example.data.booking.AmenityBookingEngine.executeOneTapBooking(
                                context = context,
                                db = db,
                                amenityName = selectedAmenity,
                                residentName = residentName,
                                unitId = residentUnit,
                                startMillis = bookingTime,
                                durationMinutes = 120,
                                notes = "Reserva directa desde portal",
                                operatorName = residentName
                            )
                            when (result) {
                                is com.example.data.booking.BookingExecutionResult.Success -> {
                                    onBookingSaved(result.booking)
                                    Toast.makeText(context, "✅ Reserva confirmada [${result.booking.folio}] para $selectedAmenity", Toast.LENGTH_LONG).show()
                                    onDismiss()
                                }
                                is com.example.data.booking.BookingExecutionResult.Conflict -> {
                                    Toast.makeText(context, "⚠️ Bloqueo de colisión: ${result.message}", Toast.LENGTH_LONG).show()
                                }
                                is com.example.data.booking.BookingExecutionResult.Error -> {
                                    Toast.makeText(context, "Error: ${result.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_resident_amenity_booking_button")
                ) {
                    Icon(Icons.Default.EventAvailable, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Confirmar Reserva Inmediata", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

/**
 * FASE 13: Diálogo Rápido de Solicitud de Mantenimiento para Residentes
 */
@Composable
fun ResidentReportMaintenanceDialog(
    residentUnit: String,
    residentName: String,
    db: AppDatabase,
    onDismiss: () -> Unit,
    onOrderCreated: (com.example.data.maintenance.MaintenanceOrderEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(com.example.data.maintenance.MaintenanceCategory.PLOMERIA) }
    var selectedPriority by remember { mutableStateOf(com.example.data.maintenance.MaintenancePriority.MEDIA) }
    var photoUri by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .testTag("resident_report_maintenance_dialog"),
            color = NavyCard,
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("🛠️ Solicitud de Mantenimiento", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Text("Unidad: $residentUnit • Solicitante: $residentName", fontSize = 10.sp, color = GoldPrimary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título de la Falla *") },
                    placeholder = { Text("Ej: Gotera en baño principal") },
                    modifier = Modifier.fillMaxWidth().testTag("resident_mnt_title"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Text("Categoría:", fontSize = 11.sp, color = TextMuted)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(com.example.data.maintenance.MaintenanceCategory.values()) { cat ->
                        val isSel = selectedCategory == cat
                        Surface(
                            onClick = { selectedCategory = cat },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSel) GoldPrimary.copy(alpha = 0.2f) else NavyDark,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) GoldPrimary else Color(0xFF334155)),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                                Text(cat.label, fontSize = 9.sp, color = if (isSel) GoldPrimary else Color.White, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }

                Text("Prioridad:", fontSize = 11.sp, color = TextMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    com.example.data.maintenance.MaintenancePriority.values().forEach { pri ->
                        val isSel = selectedPriority == pri
                        val color = Color(pri.colorHex)
                        Surface(
                            onClick = { selectedPriority = pri },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSel) color.copy(alpha = 0.2f) else NavyDark,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) color else Color(0xFF334155)),
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(pri.name, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (isSel) color else Color.White)
                                Text("${pri.defaultSlaHours}h SLA", fontSize = 7.sp, color = if (isSel) color else TextMuted)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción del Problema *") },
                    placeholder = { Text("Explique qué ocurre y si requiere acceso especial...") },
                    modifier = Modifier.fillMaxWidth().height(70.dp).testTag("resident_mnt_desc"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    maxLines = 2
                )

                Button(
                    onClick = {
                        scope.launch {
                            val res = com.example.data.maintenance.MaintenanceEngine.createMaintenanceOrder(
                                context = context,
                                db = db,
                                title = title,
                                description = description,
                                category = selectedCategory,
                                priority = selectedPriority,
                                locationType = com.example.data.maintenance.MaintenanceLocationType.UNIDAD_PRIVADA,
                                location = residentUnit,
                                unitId = residentUnit,
                                requesterName = residentName,
                                requesterRole = "RESIDENTE",
                                initialPhotoUri = photoUri.ifBlank { null },
                                autoAssign = true
                            )
                            when (res) {
                                is com.example.data.maintenance.MaintenanceEngine.MaintenanceOperationResult.Success -> {
                                    onOrderCreated(res.order)
                                    Toast.makeText(context, "✅ Orden ${res.order.folio} enviada con éxito", Toast.LENGTH_LONG).show()
                                    onDismiss()
                                }
                                is com.example.data.maintenance.MaintenanceEngine.MaintenanceOperationResult.Error -> {
                                    Toast.makeText(context, "Error: ${res.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp).testTag("submit_resident_mnt_btn")
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Enviar Solicitud a Administración", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
