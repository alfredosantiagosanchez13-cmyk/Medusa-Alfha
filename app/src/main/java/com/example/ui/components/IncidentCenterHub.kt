package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.auth.AlfhaSecurityContext
import com.example.data.booking.AppDatabase
import com.example.data.incident.IncidentCategory
import com.example.data.incident.IncidentEngine
import com.example.data.incident.IncidentEntity
import com.example.data.incident.IncidentPriority
import com.example.data.incident.VoiceIncidentCategorizer
import com.example.data.incident.EmergencyLocationEngine
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * FASE 9: CENTRO DE INCIDENCIAS Y SEGUIMIENTO ALFHA
 *
 * Módulo Unificado de Gestión de Incidentes Omnicanal:
 * - Ciclo de vida completo: REGISTRADO ➔ EN_ATENCION ➔ RESUELTO ➔ CERRADO.
 * - Clasificación, priorización y asignación automática por IA.
 * - Monitoreo de tiempo transcurrido y auto-escalamiento por SLA.
 * - Trazabilidad vinculada: Folio, Usuario, Ubicación, Fecha/Hora, Evidencias, Responsable, Historial y Resultado.
 * - Registro automático de Tiempo Devuelto a la comunidad.
 * - Integrado en Administración, Caseta, Supervisor, Mesa Directiva y Panel Maestro.
 */
@Composable
fun IncidentCenterHub(
    db: AppDatabase,
    modifier: Modifier = Modifier,
    initialRoleFilter: String = "ALL",
    showRoleSelector: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by AlfhaSecurityContext.currentUser.collectAsState()

    val incidents by db.incidentDao().getAllIncidentsFlow().collectAsState(initial = emptyList())

    var selectedStatusFilter by remember { mutableStateOf("TODAS") } // TODAS, REGISTRADO, EN_ATENCION, RESUELTO, CERRADO, ESCALADAS
    var selectedPriorityFilter by remember { mutableStateOf("TODAS") }
    var selectedRoleFilter by remember { mutableStateOf(initialRoleFilter) }
    var searchQuery by remember { mutableStateOf("") }
    var showEmergencyMapView by remember { mutableStateOf(false) }

    // Dialog state
    var showNewIncidentDialog by remember { mutableStateOf(false) }
    var selectedIncidentForEvidence by remember { mutableStateOf<IncidentEntity?>(null) }
    var selectedIncidentForResolution by remember { mutableStateOf<IncidentEntity?>(null) }
    var selectedIncidentForClosure by remember { mutableStateOf<IncidentEntity?>(null) }
    var selectedIncidentForReassignment by remember { mutableStateOf<IncidentEntity?>(null) }

    // Auto-escalation periodic scanner
    LaunchedEffect(Unit) {
        // Run auto-escalation check immediately on load
        IncidentEngine.checkAndAutoEscalateIncidents(context, db)
        // Repeat check periodically
        while (true) {
            delay(60_000) // check every minute
            IncidentEngine.checkAndAutoEscalateIncidents(context, db)
        }
    }

    // Filter incidents
    val filteredIncidents = remember(incidents, selectedStatusFilter, selectedPriorityFilter, selectedRoleFilter, searchQuery) {
        incidents.filter { inc ->
            val statusMatch = when (selectedStatusFilter) {
                "TODAS" -> true
                "ESCALADAS" -> inc.isEscalated && inc.status != "CERRADO"
                else -> inc.status.equals(selectedStatusFilter, ignoreCase = true)
            }

            val priorityMatch = when (selectedPriorityFilter) {
                "TODAS" -> true
                else -> inc.priority.name.equals(selectedPriorityFilter, ignoreCase = true)
            }

            val roleMatch = when (selectedRoleFilter) {
                "ALL" -> true
                else -> inc.assignedRole.equals(selectedRoleFilter, ignoreCase = true) || inc.reportedByRole.equals(selectedRoleFilter, ignoreCase = true)
            }

            val searchMatch = if (searchQuery.isBlank()) true else {
                inc.folio.contains(searchQuery, ignoreCase = true) ||
                        inc.location.contains(searchQuery, ignoreCase = true) ||
                        inc.reportedBy.contains(searchQuery, ignoreCase = true) ||
                        inc.aiSummary.contains(searchQuery, ignoreCase = true) ||
                        inc.rawTranscript.contains(searchQuery, ignoreCase = true) ||
                        inc.assignedTo.contains(searchQuery, ignoreCase = true)
            }

            statusMatch && priorityMatch && roleMatch && searchMatch
        }
    }

    // Metric counters
    val totalCount = incidents.size
    val inProgressCount = incidents.count { it.status == "EN_ATENCION" }
    val pendingCount = incidents.count { it.status == "REGISTRADO" }
    val resolvedCount = incidents.count { it.status == "RESUELTO" }
    val closedCount = incidents.count { it.status == "CERRADO" }
    val escalatedCount = incidents.count { it.isEscalated && it.status != "CERRADO" }
    val totalTimeSavedMinutes = remember(incidents) {
        incidents.sumOf { it.timeSavedMinutes }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. CABECERA TÁCTICA FASE 9 ---
        Card(
            colors = CardDefaults.cardColors(containerColor = NavyCard),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (escalatedCount > 0) ErrorRed.copy(alpha = 0.7f) else GoldPrimary.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (escalatedCount > 0) ErrorRed.copy(alpha = 0.2f) else CyanNeon.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (escalatedCount > 0) Icons.Default.ReportProblem else Icons.Default.AssignmentLate,
                                contentDescription = null,
                                tint = if (escalatedCount > 0) ErrorRed else CyanNeon,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "CENTRO DE INCIDENCIAS Y SEGUIMIENTO",
                                color = GoldPrimary,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "FASE 9 • Flujo Unificado • SLA y Asignación Automática",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Botón Nueva Incidencia
                    Button(
                        onClick = { showNewIncidentDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("open_new_incident_dialog_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nuevo Reporte", fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scorecard de Métricas Operativas y Tiempo Devuelto
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IncidentKpiBadge(
                        title = "Abiertas",
                        count = "${pendingCount + inProgressCount}",
                        color = if ((pendingCount + inProgressCount) > 0) WarningOrange else SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                    IncidentKpiBadge(
                        title = "En Atención",
                        count = inProgressCount.toString(),
                        color = CyanNeon,
                        modifier = Modifier.weight(1f)
                    )
                    IncidentKpiBadge(
                        title = "Escaladas",
                        count = escalatedCount.toString(),
                        color = if (escalatedCount > 0) ErrorRed else TextMuted,
                        modifier = Modifier.weight(1f)
                    )
                    IncidentKpiBadge(
                        title = "Resueltas",
                        count = "${resolvedCount + closedCount}",
                        color = SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                    IncidentKpiBadge(
                        title = "Tiempo Devuelto",
                        count = "${totalTimeSavedMinutes}m",
                        color = GoldPrimary,
                        modifier = Modifier.weight(1.2f)
                    )
                }
            }
        }

        // --- 2. SELECTOR DE PERSPECTIVA POR ROL (Si está habilitado) ---
        if (showRoleSelector) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val roles = listOf(
                    "ALL" to "🌐 Todas las Áreas",
                    "ADMINISTRACION" to "🏢 Administración",
                    "GUARDIA" to "👮 Caseta / Garita",
                    "SUPERVISOR" to "🛡️ Supervisión Táctica",
                    "RESIDENTE" to "🏡 Reportadas por Residentes"
                )

                items(roles) { (code, label) ->
                    val isSelected = selectedRoleFilter == code
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedRoleFilter = code },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = GoldPrimary,
                            containerColor = NavyCard,
                            labelColor = TextMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = TextMuted.copy(alpha = 0.3f),
                            selectedBorderColor = GoldPrimary
                        ),
                        modifier = Modifier.testTag("filter_role_${code.lowercase()}")
                    )
                }
            }
        }

        // --- 3. BARRA DE FILTROS DE ESTADO OBLIGATORIO ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                val statusTabs = listOf(
                    "TODAS" to "Todas",
                    "REGISTRADO" to "1. Registradas",
                    "EN_ATENCION" to "2. En Atención",
                    "RESUELTO" to "3. Resueltas",
                    "CERRADO" to "4. Cerradas",
                    "ESCALADAS" to "🚨 Escaladas"
                )

                items(statusTabs) { (statusCode, label) ->
                    val isSelected = selectedStatusFilter == statusCode
                    val tagColor = when (statusCode) {
                        "REGISTRADO" -> WarningOrange
                        "EN_ATENCION" -> CyanNeon
                        "RESUELTO" -> SuccessGreen
                        "CERRADO" -> TextMuted
                        "ESCALADAS" -> ErrorRed
                        else -> GoldPrimary
                    }

                    AssistChip(
                        onClick = { selectedStatusFilter = statusCode },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                                color = if (isSelected) tagColor else TextMuted
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isSelected) tagColor.copy(alpha = 0.15f) else NavySurface
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = if (isSelected) tagColor else TextMuted.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.testTag("filter_status_${statusCode.lowercase()}")
                    )
                }
            }

            // Botón de Mapa Operativo de Emergencias GPS
            IconButton(
                onClick = { showEmergencyMapView = !showEmergencyMapView },
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (showEmergencyMapView) ErrorRed.copy(alpha = 0.25f) else NavySurface)
                    .testTag("toggle_emergency_map_button")
            ) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = "Mapa de Emergencias",
                    tint = if (showEmergencyMapView) ErrorRed else GoldPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Botón de escaneo manual de SLA
            IconButton(
                onClick = {
                    scope.launch {
                        val count = IncidentEngine.checkAndAutoEscalateIncidents(context, db)
                        if (count > 0) {
                            Toast.makeText(context, "🚨 $count incidencias escaladas por exceder SLA", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "✅ SLA al día. No hay incidencias excedidas.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(NavySurface)
                    .testTag("check_sla_button")
            ) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = "Verificar SLA",
                    tint = GoldPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // --- MAPA OPERATIVO DE EMERGENCIAS (COMPLEMENTO A FASE 16) ---
        AnimatedVisibility(visible = showEmergencyMapView) {
            OperationalEmergencyMapView(
                db = db,
                userRole = selectedRoleFilter,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }

        // --- 4. LISTA DE INCIDENCIAS UNIFICADAS ---
        if (filteredIncidents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NavyCard)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.CheckCircleOutline,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Sin incidencias en esta vista",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "No se encontraron reportes con los filtros seleccionados.",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredIncidents, key = { it.folio }) { incident ->
                    IncidentDetailUnifiedCard(
                        incident = incident,
                        onAttend = {
                            scope.launch {
                                IncidentEngine.transitionToAttention(
                                    context = context,
                                    db = db,
                                    folio = incident.folio,
                                    operatorName = currentUser.name,
                                    operatorRole = currentUser.role
                                )
                                Toast.makeText(context, "✅ Incidencia ${incident.folio} pasada a EN ATENCIÓN", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onAddEvidence = {
                            selectedIncidentForEvidence = incident
                        },
                        onResolve = {
                            selectedIncidentForResolution = incident
                        },
                        onClose = {
                            selectedIncidentForClosure = incident
                        },
                        onReassign = {
                            selectedIncidentForReassignment = incident
                        }
                    )
                }
            }
        }
    }

    // --- DIÁLOGOS OPERATIVOS ---

    // 1. Diálogo de Nueva Incidencia (Voz + Texto + Auto-Asignación)
    if (showNewIncidentDialog) {
        NewIncidentCreationDialog(
            db = db,
            onDismiss = { showNewIncidentDialog = false },
            onIncidentCreated = {
                showNewIncidentDialog = false
            }
        )
    }

    // 2. Diálogo de Adjuntar Evidencia
    selectedIncidentForEvidence?.let { inc ->
        EvidenceCaptureDialog(
            incident = inc,
            onDismiss = { selectedIncidentForEvidence = null },
            onSaveEvidence = { evidenceNotes ->
                scope.launch {
                    IncidentEngine.appendEvidence(
                        context = context,
                        db = db,
                        folio = inc.folio,
                        evidenceText = evidenceNotes,
                        operatorName = currentUser.name,
                        operatorRole = currentUser.role
                    )
                    Toast.makeText(context, "📸 Evidencia registrada para ${inc.folio}", Toast.LENGTH_SHORT).show()
                    selectedIncidentForEvidence = null
                }
            }
        )
    }

    // 3. Diálogo de Resolución Formal (RESUELTO)
    selectedIncidentForResolution?.let { inc ->
        ResolutionFormalDialog(
            incident = inc,
            onDismiss = { selectedIncidentForResolution = null },
            onConfirmResolution = { notes ->
                scope.launch {
                    IncidentEngine.transitionToResolved(
                        context = context,
                        db = db,
                        folio = inc.folio,
                        resolutionNotes = notes,
                        operatorName = currentUser.name,
                        operatorRole = currentUser.role
                    )
                    Toast.makeText(context, "✅ Incidencia ${inc.folio} RESUELTA satisfactoriamente", Toast.LENGTH_SHORT).show()
                    selectedIncidentForResolution = null
                }
            }
        )
    }

    // 4. Diálogo de Cierre Definitivo (CERRADO)
    selectedIncidentForClosure?.let { inc ->
        ClosureFormalDialog(
            incident = inc,
            onDismiss = { selectedIncidentForClosure = null },
            onConfirmClosure = { notes ->
                scope.launch {
                    IncidentEngine.transitionToClosed(
                        context = context,
                        db = db,
                        folio = inc.folio,
                        closureNotes = notes,
                        operatorName = currentUser.name,
                        operatorRole = currentUser.role
                    )
                    Toast.makeText(context, "🏛️ Incidencia ${inc.folio} CERRADA formalmente", Toast.LENGTH_SHORT).show()
                    selectedIncidentForClosure = null
                }
            }
        )
    }

    // 5. Diálogo de Reasignación de Responsable
    selectedIncidentForReassignment?.let { inc ->
        ReassignmentDialog(
            incident = inc,
            onDismiss = { selectedIncidentForReassignment = null },
            onReassigned = { newResp, newRole ->
                scope.launch {
                    db.incidentDao().reassignIncident(inc.folio, newResp, newRole)
                    Toast.makeText(context, "🔄 Reasignada a $newResp ($newRole)", Toast.LENGTH_SHORT).show()
                    selectedIncidentForReassignment = null
                }
            }
        )
    }
}

/**
 * Tarjeta Unificada de Incidencia con Stepper y Trazabilidad Completa
 */
@Composable
fun IncidentDetailUnifiedCard(
    incident: IncidentEntity,
    onAttend: () -> Unit,
    onAddEvidence: () -> Unit,
    onResolve: () -> Unit,
    onClose: () -> Unit,
    onReassign: () -> Unit
) {
    val prioColor = when (incident.priority) {
        IncidentPriority.CRITICA -> ErrorRed
        IncidentPriority.ALTA -> WarningOrange
        IncidentPriority.MEDIA -> GoldPrimary
        IncidentPriority.BAJA -> SuccessGreen
    }

    val isSlaOverdue = incident.isSlaExceeded()
    val elapsedTime = incident.getElapsedTimeFormatted()
    val slaText = incident.getSlaStatusFormatted()

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (incident.status == "CERRADO") NavySurface.copy(alpha = 0.5f) else NavyCard
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            if (incident.isEscalated || isSlaOverdue) 1.5.dp else 0.8.dp,
            if (incident.isEscalated || isSlaOverdue) ErrorRed.copy(alpha = 0.9f) else prioColor.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("incident_card_${incident.folio}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // --- HEADER: Folio, Prioridad, Tiempo Transcurrido, Categoría ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = incident.folio,
                        color = GoldPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black
                    )

                    Surface(
                        color = prioColor.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, prioColor)
                    ) {
                        Text(
                            text = incident.priority.displayName,
                            color = prioColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (incident.isEscalated) {
                        Surface(
                            color = ErrorRed.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.5.dp, ErrorRed)
                        ) {
                            Text(
                                text = "🚨 ESCALADO",
                                color = ErrorRed,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = elapsedTime,
                    color = if (isSlaOverdue) ErrorRed else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // --- STEPPER VISUAL DE ESTADOS OBLIGATORIOS ---
            IncidentLifecycleStepper(currentStepIndex = incident.statusStepIndex)

            // --- CATEGORÍA Y ASIGNACIÓN AUTOMÁTICA ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = incident.category.iconName, fontSize = 14.sp)
                    Text(
                        text = incident.category.displayName,
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    color = NavySurface,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.5.dp, CyanNeon.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(12.dp))
                        Text(
                            text = "${incident.assignedTo} (${incident.assignedRole})",
                            color = CyanNeon,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // --- UBICACIÓN Y REPORTANTE ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "📍 ${incident.location}",
                    color = GoldPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Reportó: ${incident.reportedBy} (${incident.reportedByRole})",
                    color = TextMuted,
                    fontSize = 10.5.sp
                )
            }

            // --- GEOLOCALIZACIÓN GPS EXACTA / UBICACIÓN NO DISPONIBLE ---
            val hasGps = incident.latitude != null && incident.longitude != null
            Surface(
                color = if (hasGps) NavySurface else Color(0xFF2D1600),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.5.dp, if (hasGps) CyanNeon.copy(alpha = 0.35f) else WarningOrange.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (hasGps) Icons.Default.GpsFixed else Icons.Default.LocationOff,
                            contentDescription = null,
                            tint = if (hasGps) CyanNeon else WarningOrange,
                            modifier = Modifier.size(14.dp)
                        )
                        Column {
                            if (hasGps) {
                                Text(
                                    text = "GPS: ${String.format(java.util.Locale.US, "%.5f", incident.latitude)}, ${String.format(java.util.Locale.US, "%.5f", incident.longitude)} (±${incident.gpsAccuracyMeters?.toInt() ?: 5}m)",
                                    color = Color.White,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Estatus: ${incident.locationStatus}",
                                    color = CyanNeon,
                                    fontSize = 9.sp
                                )
                            } else {
                                Text(
                                    text = "⚠️ UBICACIÓN NO DISPONIBLE",
                                    color = WarningOrange,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Sin fijación satelital capturada al momento del registro.",
                                    color = TextMuted,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }

            // --- RESUMEN IA Y ACCIÓN RECOMENDADA ---
            Surface(
                color = NavySurface,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = incident.aiSummary,
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 16.sp
                    )
                    Text(
                        text = "Acción: ${incident.recommendedAction}",
                        color = CyanNeon,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            // --- INDICADOR DINÁMICO DE SLA ---
            Surface(
                color = if (isSlaOverdue) ErrorRed.copy(alpha = 0.15f) else NavySurface,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(0.5.dp, if (isSlaOverdue) ErrorRed else TextMuted.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = slaText,
                        color = if (isSlaOverdue) ErrorRed else TextMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Tiempo Devuelto: +${incident.timeSavedMinutes}m",
                        color = GoldPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // --- EVIDENCIAS ADJUNTAS (Si existen) ---
            if (!incident.evidenceNotes.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(NavySurface)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(12.dp))
                        Text("Evidencias y Peritaje en Sitio:", color = CyanNeon, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = incident.evidenceNotes,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.5.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            // --- DICTAMEN DE RESOLUCIÓN O CIERRE ---
            if (!incident.resolutionNotes.isNullOrBlank()) {
                Surface(
                    color = SuccessGreen.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(0.5.dp, SuccessGreen.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("✅ Dictamen de Resolución (${incident.resolvedBy ?: "Operador"}):", color = SuccessGreen, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        Text(incident.resolutionNotes, color = Color.White, fontSize = 11.sp)
                    }
                }
            }

            if (!incident.closureNotes.isNullOrBlank()) {
                Surface(
                    color = NavySurface,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("🏛️ Constancia de Cierre (${incident.closedBy ?: "Mesa Directiva"}):", color = GoldPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        Text(incident.closureNotes, color = TextMuted, fontSize = 10.5.sp)
                    }
                }
            }

            // --- BOTONES DE ACCIÓN SECUENCIAL (REGISTRADO -> EN_ATENCION -> RESUELTO -> CERRADO) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (incident.status) {
                    "REGISTRADO" -> {
                        Button(
                            onClick = onAttend,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).testTag("attend_incident_button_${incident.folio}")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Atender", fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }

                        OutlinedButton(
                            onClick = onReassign,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Reasignar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    "EN_ATENCION" -> {
                        OutlinedButton(
                            onClick = onAddEvidence,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon),
                            border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).testTag("add_evidence_button_${incident.folio}")
                        ) {
                            Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Evidencia", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onResolve,
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = NavyDark),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).testTag("resolve_incident_button_${incident.folio}")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resolver", fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    "RESUELTO" -> {
                        OutlinedButton(
                            onClick = onAddEvidence,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                            border = BorderStroke(0.5.dp, TextMuted.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Ver Evidencia", fontSize = 10.5.sp)
                        }

                        Button(
                            onClick = onClose,
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f).testTag("close_incident_button_${incident.folio}")
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cerrar Ticket", fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    "CERRADO" -> {
                        Surface(
                            color = NavySurface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🔒 Ciclo Completo Concluido", color = TextMuted, fontSize = 11.sp)
                                Text("Auditoría SHA-256 Sellada", color = GoldPrimary, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Stepper Visual para los 4 Estados Obligatorios
 */
@Composable
private fun IncidentLifecycleStepper(currentStepIndex: Int) {
    val steps = listOf("REGISTRADO", "EN ATENCIÓN", "RESUELTO", "CERRADO")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NavySurface)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, name ->
            val isDone = index < currentStepIndex
            val isCurrent = index == currentStepIndex
            val stepColor = when {
                isDone -> SuccessGreen
                isCurrent -> when (index) {
                    0 -> WarningOrange
                    1 -> CyanNeon
                    2 -> SuccessGreen
                    else -> GoldPrimary
                }
                else -> TextMuted.copy(alpha = 0.4f)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(stepColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = NavyDark, modifier = Modifier.size(11.dp))
                    } else {
                        Text("${index + 1}", color = NavyDark, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }

                Text(
                    text = name,
                    fontSize = 9.5.sp,
                    fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Normal,
                    color = if (isCurrent) Color.White else if (isDone) SuccessGreen else TextMuted
                )
            }

            if (index < steps.lastIndex) {
                Text("➔", color = TextMuted.copy(alpha = 0.3f), fontSize = 10.sp)
            }
        }
    }
}

/**
 * Diálogo de Creación de Incidencia con Voz, Texto y Asignación Automática
 */
@Composable
private fun NewIncidentCreationDialog(
    db: AppDatabase,
    onDismiss: () -> Unit,
    onIncidentCreated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by AlfhaSecurityContext.currentUser.collectAsState()

    var transcriptText by remember { mutableStateOf("") }
    var locationInput by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }

    val categorized = remember(transcriptText) {
        if (transcriptText.isNotBlank()) VoiceIncidentCategorizer.analyzeAndCategorize(transcriptText) else null
    }

    val assignedPair = remember(categorized) {
        if (categorized != null) IncidentEngine.autoAssignResponsible(categorized.category, categorized.priority)
        else Pair("Oficial de Guardia en Turno", "GUARDIA")
    }

    val targetSla = remember(categorized) {
        if (categorized != null) IncidentEngine.getSlaMinutes(categorized.priority) else 45
    }

    val speechIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull() ?: ""
            if (spokenText.isNotBlank()) {
                transcriptText = spokenText
                val autoCat = VoiceIncidentCategorizer.analyzeAndCategorize(spokenText)
                if (locationInput.isBlank()) {
                    locationInput = autoCat.location
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isListening = true
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Describa la novedad o incidencia...")
            }
            try {
                speechIntentLauncher.launch(intent)
            } catch (e: Exception) {
                isListening = false
                Toast.makeText(context, "Reconocimiento no disponible", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Permiso de micrófono denegado", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NavyCard),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("new_incident_dialog")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, tint = GoldPrimary)
                        Text(
                            text = "Nuevo Reporte de Incidencia",
                            color = GoldPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    // Botón Micrófono
                    IconButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                isListening = true
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL")
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Describa la novedad...")
                                }
                                speechIntentLauncher.launch(intent)
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isListening) ErrorRed else CyanNeon.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                            contentDescription = "Dictar por voz",
                            tint = if (isListening) Color.White else CyanNeon
                        )
                    }
                }

                OutlinedTextField(
                    value = transcriptText,
                    onValueChange = { transcriptText = it },
                    label = { Text("Descripción o Dictado de Novedad") },
                    placeholder = { Text("Ej. Vehículo bloqueando portón oriente / Fuga de agua en medidor 104...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp)
                        .testTag("incident_description_input")
                )

                OutlinedTextField(
                    value = locationInput,
                    onValueChange = { locationInput = it },
                    label = { Text("Ubicación Específica") },
                    placeholder = { Text("Ej. Casa 104 / Garita 1 / Alberca / Portón Poniente") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("incident_location_input")
                )

                // Previsualización de Auto-Clasificación por IA
                categorized?.let { cat ->
                    Surface(
                        color = NavySurface,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.5.dp, CyanNeon.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Categoría: ${cat.category.displayName}", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Prioridad: ${cat.priority.displayName}", color = if (cat.priority == IncidentPriority.CRITICA) ErrorRed else WarningOrange, fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                            Text("Responsable Asignado: ${assignedPair.first} (${assignedPair.second})", color = CyanNeon, fontSize = 11.sp)
                            Text("Tiempo Objetivo SLA: $targetSla minutos", color = TextMuted, fontSize = 10.5.sp)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (transcriptText.isBlank()) {
                                Toast.makeText(context, "Por favor ingrese una descripción", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            val autoCat = categorized ?: VoiceIncidentCategorizer.analyzeAndCategorize(transcriptText)
                            val finalLocation = locationInput.ifBlank { autoCat.location }

                            scope.launch {
                                val entity = IncidentEngine.registerIncident(
                                    context = context,
                                    db = db,
                                    rawTranscript = transcriptText,
                                    category = autoCat.category,
                                    priority = autoCat.priority,
                                    location = finalLocation,
                                    aiSummary = autoCat.aiSummary,
                                    recommendedAction = autoCat.recommendedAction,
                                    reportedBy = currentUser.name,
                                    reportedByRole = currentUser.role
                                )
                                Toast.makeText(context, "✅ Incidencia ${entity.folio} registrada y asignada a ${entity.assignedTo}", Toast.LENGTH_LONG).show()
                                onIncidentCreated()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("confirm_register_incident_button")
                    ) {
                        Text("Registrar Incidencia", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

/**
 * Diálogo de Captura de Evidencia
 */
@Composable
private fun EvidenceCaptureDialog(
    incident: IncidentEntity,
    onDismiss: () -> Unit,
    onSaveEvidence: (String) -> Unit
) {
    var evidenceText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NavyCard),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = CyanNeon)
                    Text(
                        text = "Adjuntar Evidencia en Sitio",
                        color = CyanNeon,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Folio: ${incident.folio} • Ubicación: ${incident.location}",
                    color = TextMuted,
                    fontSize = 11.5.sp
                )

                OutlinedTextField(
                    value = evidenceText,
                    onValueChange = { evidenceText = it },
                    label = { Text("Detalle de Hallazgo / Peritaje / Foto Metadata") },
                    placeholder = { Text("Ej. Se constata fuga contenida con llave de paso cerrada. Fotos adjuntas en archivo pericial.") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp)
                        .testTag("evidence_notes_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (evidenceText.isNotBlank()) {
                                onSaveEvidence(evidenceText)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("confirm_add_evidence_button")
                    ) {
                        Text("Guardar Evidencia", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Diálogo de Resolución Formal
 */
@Composable
private fun ResolutionFormalDialog(
    incident: IncidentEntity,
    onDismiss: () -> Unit,
    onConfirmResolution: (String) -> Unit
) {
    var resolutionNotes by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NavyCard),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = SuccessGreen)
                    Text(
                        text = "Emitir Dictamen de Resolución",
                        color = SuccessGreen,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Folio: ${incident.folio} • ${incident.category.displayName}",
                    color = TextMuted,
                    fontSize = 11.5.sp
                )

                OutlinedTextField(
                    value = resolutionNotes,
                    onValueChange = { resolutionNotes = it },
                    label = { Text("Dictamen de Solución Aplicada") },
                    placeholder = { Text("Ej. Válvula reemplazada por personal técnico. Prueba de presión hidrostática superada.") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SuccessGreen,
                        unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 90.dp)
                        .testTag("resolution_notes_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val notes = resolutionNotes.ifBlank { "Incidencia resuelta satisfactoriamente según protocolo estándar." }
                            onConfirmResolution(notes)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = NavyDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("confirm_resolve_button")
                    ) {
                        Text("Confirmar Resolución", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Diálogo de Cierre Definitivo
 */
@Composable
private fun ClosureFormalDialog(
    incident: IncidentEntity,
    onDismiss: () -> Unit,
    onConfirmClosure: (String) -> Unit
) {
    var closureNotes by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NavyCard),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = GoldPrimary)
                    Text(
                        text = "Cierre Definitivo de Incidencia",
                        color = GoldPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "El cierre formal sella el ticket y registra la constancia en el libro mayor de gobernanza.",
                    color = TextMuted,
                    fontSize = 11.5.sp
                )

                OutlinedTextField(
                    value = closureNotes,
                    onValueChange = { closureNotes = it },
                    label = { Text("Constancia Final de Cierre") },
                    placeholder = { Text("Ej. Verificación de conformidad recibida del residente. Ticket concluido.") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("closure_notes_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val notes = closureNotes.ifBlank { "Ticket auditado y cerrado conforme a estándares ALFHA." }
                            onConfirmClosure(notes)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("confirm_close_button")
                    ) {
                        Text("Cerrar Ticket", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Diálogo de Reasignación
 */
@Composable
private fun ReassignmentDialog(
    incident: IncidentEntity,
    onDismiss: () -> Unit,
    onReassigned: (String, String) -> Unit
) {
    val options = listOf(
        Pair("Oficial de Guardia (Garita 1)", "GUARDIA"),
        Pair("Oficial de Ronda Perimetral", "GUARDIA"),
        Pair("Supervisor Táctico Esteban Silva", "SUPERVISOR"),
        Pair("Coordinación de Mantenimiento", "ADMINISTRACION"),
        Pair("Administración General", "ADMINISTRACION"),
        Pair("Comité de Seguridad y Convivencia", "MESA_DIRECTIVA")
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NavyCard),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Reasignar Responsable de Ticket",
                    color = GoldPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Folio: ${incident.folio} • Actual: ${incident.assignedTo}",
                    color = TextMuted,
                    fontSize = 11.5.sp
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 260.dp)
                ) {
                    items(options) { (respName, role) ->
                        Surface(
                            color = NavySurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.5.dp, if (incident.assignedTo == respName) GoldPrimary else TextMuted.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onReassigned(respName, role) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(respName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(role, color = CyanNeon, fontSize = 10.sp)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cerrar", color = TextMuted)
                    }
                }
            }
        }
    }
}

/**
 * Mini Badge para KPIs
 */
@Composable
private fun IncidentKpiBadge(
    title: String,
    count: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = NavySurface.copy(alpha = 0.7f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(0.5.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = count, color = color, fontSize = 15.sp, fontWeight = FontWeight.Black)
            Text(text = title, color = TextMuted, fontSize = 8.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}
