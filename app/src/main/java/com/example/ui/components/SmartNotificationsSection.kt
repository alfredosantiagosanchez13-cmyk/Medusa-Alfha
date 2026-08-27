package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.example.auth.AlfhaPermission
import com.example.auth.AlfhaRole
import com.example.auth.AlfhaSecurityContext
import com.example.auth.RbacValidationOutcome
import com.example.data.booking.AppDatabase
import com.example.data.notifications.NotificationCategory
import com.example.data.notifications.NotificationPriority
import com.example.data.notifications.SmartNotificationEntity
import com.example.data.notifications.SmartNotificationHub
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * FASE 8: NOTIFICACIONES INTELIGENTES ALFHA
 *
 * Centro Inteligente de Gestión de Eventos y Notificaciones Reales.
 * - Priorización Cuádruple (CRÍTICA, ALTA, MEDIA, PREVENTIVA).
 * - Enrutamiento específico por Roles y Personas.
 * - Modo Panel Maestro (Oculta automáticamente resueltas y destaca las pendientes de acción humana).
 * - Anti-duplicados estricto y trazabilidad inmutable en Room DB.
 */
@Composable
fun SmartNotificationsSection(
    db: AppDatabase,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by AlfhaSecurityContext.currentUser.collectAsState()

    val allNotifications by db.smartNotificationDao().getAllNotificationsFlow().collectAsState(initial = emptyList())

    var selectedRoleFilter by remember { mutableStateOf<String>("ALL") }
    var selectedPriorityFilter by remember { mutableStateOf<String>("TODAS") }
    var selectedStatusFilter by remember { mutableStateOf<String>("PENDIENTES") } // PENDIENTES, TODAS, RESUELTAS
    var showEventSimulatorDialog by remember { mutableStateOf(false) }
    var resolvingNotification by remember { mutableStateOf<SmartNotificationEntity?>(null) }
    var resolutionNotes by remember { mutableStateOf("") }

    // Seed initial notifications if empty
    LaunchedEffect(Unit) {
        SmartNotificationHub.seedInitialNotificationsIfEmpty(context, db)
    }

    // Filter list
    val filteredNotifications = remember(allNotifications, selectedRoleFilter, selectedPriorityFilter, selectedStatusFilter) {
        allNotifications.filter { notif ->
            val roleMatch = when (selectedRoleFilter) {
                "ALL" -> true
                "MAESTRO_ALFHA" -> true
                "ACTIONABLE_ONLY" -> notif.requiresHumanAction && !notif.isResolved
                else -> notif.targetRole.equals(selectedRoleFilter, ignoreCase = true) || notif.targetRole == "ALL"
            }

            val priorityMatch = when (selectedPriorityFilter) {
                "TODAS" -> true
                else -> notif.priority.equals(selectedPriorityFilter, ignoreCase = true)
            }

            val statusMatch = when (selectedStatusFilter) {
                "PENDIENTES" -> if (selectedRoleFilter == "ACTIONABLE_ONLY") !notif.isResolved else !notif.isResolved || !notif.isRead
                "RESUELTAS" -> notif.isResolved
                else -> true
            }

            roleMatch && priorityMatch && statusMatch
        }
    }

    val pendingActionCount = remember(allNotifications) {
        allNotifications.count { it.requiresHumanAction && !it.isResolved }
    }
    val unreadCount = remember(allNotifications) {
        allNotifications.count { !it.isRead }
    }
    val criticalCount = remember(allNotifications) {
        allNotifications.count { it.priority == "CRITICA" && !it.isResolved }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. CABECERA TÁCTICA FASE 8 ---
        Card(
            colors = CardDefaults.cardColors(containerColor = NavyCard),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, if (criticalCount > 0) ErrorRed.copy(alpha = 0.6f) else CyanNeon.copy(alpha = 0.3f)),
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
                                .background(if (criticalCount > 0) ErrorRed.copy(alpha = 0.2f) else CyanNeon.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (criticalCount > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                contentDescription = null,
                                tint = if (criticalCount > 0) ErrorRed else CyanNeon,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "NOTIFICACIONES INTELIGENTES",
                                color = GoldPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "FASE 8 • Solo Eventos Reales • Cero Ruido",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Botón para simular eventos reales
                    OutlinedButton(
                        onClick = { showEventSimulatorDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("open_event_simulator_button")
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Emitir Evento", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Filosofía Operativa ALFHA
                Surface(
                    color = NavySurface.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, GoldPrimary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Objetivo Sagrado: Que la persona reciba la información exacta cuando necesita actuar, no que tenga que buscarla. ESTO DEVUELVE TIEMPO.",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // KPI Micro-Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiMiniBadge(
                        title = "Acción Humana",
                        count = pendingActionCount,
                        color = if (pendingActionCount > 0) WarningOrange else SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMiniBadge(
                        title = "Críticas Activas",
                        count = criticalCount,
                        color = if (criticalCount > 0) ErrorRed else TextMuted,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMiniBadge(
                        title = "No Leídas",
                        count = unreadCount,
                        color = if (unreadCount > 0) CyanNeon else TextMuted,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // --- 2. SELECTOR DE ROL DESTINATARIO ---
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "PERSPECTIVA POR ROL Y ALCANCE:",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val roleTabs = listOf(
                    "ACTIONABLE_ONLY" to "⚡ Panel Maestro (Acción)",
                    "ALL" to "🌐 Todas",
                    "RESIDENTE" to "🏡 Residente",
                    "GUARDIA" to "👮 Guardia",
                    "SUPERVISOR" to "🛡️ Supervisor",
                    "ADMINISTRACION" to "🏢 Administración",
                    "MESA_DIRECTIVA" to "🏛️ Mesa Directiva"
                )

                items(roleTabs) { (roleCode, label) ->
                    val isSelected = selectedRoleFilter == roleCode
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedRoleFilter = roleCode },
                        label = {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (roleCode == "ACTIONABLE_ONLY") ErrorRed.copy(alpha = 0.25f) else GoldPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = if (roleCode == "ACTIONABLE_ONLY") ErrorRed else GoldPrimary,
                            containerColor = NavyCard,
                            labelColor = TextMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = TextMuted.copy(alpha = 0.3f),
                            selectedBorderColor = if (roleCode == "ACTIONABLE_ONLY") ErrorRed else GoldPrimary
                        ),
                        modifier = Modifier.testTag("filter_role_${roleCode.lowercase()}")
                    )
                }
            }
        }

        // --- 3. FILTROS DE PRIORIDAD Y ESTADO ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Filtros de Prioridad
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                val priorities = listOf("TODAS", "CRITICA", "ALTA", "MEDIA", "PREVENTIVA")
                items(priorities) { prio ->
                    val isSelected = selectedPriorityFilter == prio
                    val badgeColor = when (prio) {
                        "CRITICA" -> ErrorRed
                        "ALTA" -> WarningOrange
                        "MEDIA" -> CyanNeon
                        "PREVENTIVA" -> SuccessGreen
                        else -> GoldPrimary
                    }

                    AssistChip(
                        onClick = { selectedPriorityFilter = prio },
                        label = {
                            Text(
                                text = prio,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                                color = if (isSelected) badgeColor else TextMuted
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isSelected) badgeColor.copy(alpha = 0.15f) else NavySurface
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = if (isSelected) badgeColor else TextMuted.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.testTag("filter_prio_${prio.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Selector de Estado (Pendientes / Todas / Resueltas)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(NavySurface)
                    .padding(2.dp)
            ) {
                listOf("PENDIENTES" to "Pendientes", "TODAS" to "Todas", "RESUELTAS" to "Cerradas").forEach { (code, lbl) ->
                    val isSel = selectedStatusFilter == code
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSel) CyanNeon.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { selectedStatusFilter = code }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = lbl,
                            fontSize = 10.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) CyanNeon else TextMuted
                        )
                    }
                }
            }
        }

        // --- 4. LISTA DE NOTIFICACIONES INTELIGENTES ---
        if (filteredNotifications.isEmpty()) {
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
                        text = "Sin notificaciones pendientes",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Todos los eventos han sido procesados o automatizados sin requerir acción.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredNotifications, key = { it.id }) { notif ->
                    SmartNotificationItemCard(
                        notification = notif,
                        onMarkAsRead = {
                            scope.launch {
                                SmartNotificationHub.markAsRead(db, notif.id)
                                Toast.makeText(context, "Notificación marcada como leída", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onResolveAction = {
                            resolvingNotification = notif
                            resolutionNotes = ""
                        }
                    )
                }
            }
        }
    }

    // --- DIÁLOGO DE RESOLUCIÓN CON RBAC Y AUDITORÍA ---
    resolvingNotification?.let { notif ->
        Dialog(onDismissRequest = { resolvingNotification = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
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
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = GoldPrimary)
                        Text(
                            text = "Resolver Acción de Notificación",
                            color = GoldPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Surface(
                        color = NavySurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = notif.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(text = notif.body, color = TextMuted, fontSize = 11.5.sp)
                            Text(text = "Folio Relacionado: ${notif.relatedFolio}", color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    OutlinedTextField(
                        value = resolutionNotes,
                        onValueChange = { resolutionNotes = it },
                        label = { Text("Dictamen / Nota de Resolución Formal") },
                        placeholder = { Text("Ej. Situación atendida en sitio, verificación completada conforme a protocolo.") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = TextMuted.copy(alpha = 0.4f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("resolution_notes_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { resolvingNotification = null }) {
                            Text("Cancelar", color = TextMuted)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val notes = resolutionNotes.ifBlank { "Acción resuelta formalmente en Panel ALFHA" }
                                scope.launch {
                                    val success = SmartNotificationHub.resolveNotificationWithSecurity(
                                        db = db,
                                        notificationId = notif.id,
                                        operatorName = currentUser.name,
                                        resolutionNotes = notes
                                    )
                                    if (success) {
                                        Toast.makeText(context, "✅ Notificación resuelta y registrada en auditoría", Toast.LENGTH_SHORT).show()
                                        resolvingNotification = null
                                    } else {
                                        Toast.makeText(context, "🚫 Requiere permiso de RESOLVER (RBAC)", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("confirm_resolve_notification_button")
                        ) {
                            Text("Confirmar Resolución", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // --- DIÁLOGO EMISOR DE EVENTOS REALES (SIMULADOR DE FASE 8) ---
    if (showEventSimulatorDialog) {
        EventSimulatorDialog(
            onDismiss = { showEventSimulatorDialog = false },
            onTriggerEvent = { eventType ->
                scope.launch {
                    when (eventType) {
                        "VISITOR_ENTRY" -> {
                            SmartNotificationHub.notifyVisitorEntry(
                                context = context,
                                db = db,
                                guestName = "Laura Ramos",
                                unitId = "Casa 204",
                                hostResidentName = "Familia Morales",
                                passTypeLabel = "Visita Frecuente",
                                vehiclePlate = "JAL-402-K",
                                passFolio = "MED-${System.currentTimeMillis() % 10000}"
                            )
                            Toast.makeText(context, "🔔 Evento: Entrada de Visitante emitida a Residente", Toast.LENGTH_SHORT).show()
                        }
                        "PACKAGE" -> {
                            SmartNotificationHub.notifyPackageReceived(
                                context = context,
                                db = db,
                                unitId = "Casa 102",
                                hostResidentName = "Familia González",
                                courierName = "MercadoLibre Full",
                                packageGuide = "PKG-ML-${System.currentTimeMillis() % 10000}",
                                guardName = "Oficial Ramírez"
                            )
                            Toast.makeText(context, "📦 Evento: Paquetería emitida a Residente", Toast.LENGTH_SHORT).show()
                        }
                        "GUARD_ALERT" -> {
                            val folio = "ALT-${System.currentTimeMillis() % 10000}"
                            SmartNotificationHub.notifyGuardCriticalAlert(
                                context = context,
                                db = db,
                                alertFolio = folio,
                                location = "Garita Poniente - Portón Vehicular",
                                description = "Vehículo sospechoso intenta ingreso sin placa ni código",
                                actionRequired = "Retener en bahía de inspección y aplicar protocolo de verificación"
                            )
                            Toast.makeText(context, "🚨 Evento: Alerta Crítica emitida a Guardia", Toast.LENGTH_SHORT).show()
                        }
                        "SUPERVISOR_FINDING" -> {
                            val supFolio = "SUP-${System.currentTimeMillis() % 10000}"
                            SmartNotificationHub.notifySupervisorCriticalFinding(
                                context = context,
                                db = db,
                                supervisionFolio = supFolio,
                                checkpointName = "Cerca Perimetral Este",
                                findingDetail = "Alambre de concertina cortado con probable intento de intrusión"
                            )
                            Toast.makeText(context, "🛡️ Evento: Hallazgo Crítico emitido a Supervisor", Toast.LENGTH_SHORT).show()
                        }
                        "ADMIN_ESCALATION" -> {
                            val incFolio = "INC-${System.currentTimeMillis() % 10000}"
                            SmartNotificationHub.notifyAdminEscalatedIncident(
                                context = context,
                                db = db,
                                folio = incFolio,
                                location = "Gimnasio Comunitario",
                                escalationReason = "Falla recurrente en aire acondicionado sin respuesta de proveedor por 72 hrs"
                            )
                            Toast.makeText(context, "📈 Evento: Incidencia Escalada a Administración", Toast.LENGTH_SHORT).show()
                        }
                        "BOARD_SUMMARY" -> {
                            SmartNotificationHub.notifyBoardExecutiveSummary(
                                context = context,
                                db = db,
                                periodLabel = "Cierre Mensual de Gestión",
                                hoursReturnedFormatted = "46.2",
                                incidentsResolvedCount = 38,
                                compliancePct = 99
                            )
                            Toast.makeText(context, "📊 Evento: Resumen Ejecutivo emitido a Mesa Directiva", Toast.LENGTH_SHORT).show()
                        }
                    }
                    showEventSimulatorDialog = false
                }
            }
        )
    }
}

/**
 * Tarjeta individual de Notificación Inteligente
 */
@Composable
private fun SmartNotificationItemCard(
    notification: SmartNotificationEntity,
    onMarkAsRead: () -> Unit,
    onResolveAction: () -> Unit
) {
    val prio = NotificationPriority.fromString(notification.priority)
    val prioColor = when (prio) {
        NotificationPriority.CRITICA -> ErrorRed
        NotificationPriority.ALTA -> WarningOrange
        NotificationPriority.MEDIA -> CyanNeon
        NotificationPriority.PREVENTIVA -> SuccessGreen
    }

    val timeFormatted = remember(notification.timestampMillis) {
        SimpleDateFormat("HH:mm • dd/MM", Locale.getDefault()).format(Date(notification.timestampMillis))
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isResolved) NavySurface.copy(alpha = 0.5f) else NavyCard
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            if (notification.requiresHumanAction && !notification.isResolved) 1.2.dp else 0.5.dp,
            if (notification.requiresHumanAction && !notification.isResolved) prioColor.copy(alpha = 0.8f) else TextMuted.copy(alpha = 0.2f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notification_card_${notification.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row (Priority + Role Target + Time + Status)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Priority Badge
                    Surface(
                        color = prioColor.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(0.5.dp, prioColor)
                    ) {
                        Text(
                            text = prio.label,
                            color = prioColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Target Role Badge
                    Surface(
                        color = NavySurface,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = notification.targetRole,
                            color = GoldPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (notification.requiresHumanAction && !notification.isResolved) {
                        Surface(
                            color = ErrorRed.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "ACCION REQUERIDA",
                                color = ErrorRed,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = timeFormatted,
                        color = TextMuted,
                        fontSize = 11.sp
                    )

                    if (notification.isResolved) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    } else if (!notification.isRead) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(CyanNeon)
                        )
                    }
                }
            }

            // Title
            Text(
                text = notification.title,
                color = if (notification.isResolved) TextMuted else Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold
            )

            // Body
            Text(
                text = notification.body,
                color = if (notification.isResolved) TextMuted.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                lineHeight = 16.sp
            )

            // Footer (Folio + Resolution info or Actions)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Related Folio badge
                Surface(
                    color = NavySurface,
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(0.5.dp, TextMuted.copy(alpha = 0.2f))
                ) {
                    Text(
                        text = "Folio: ${notification.relatedFolio}",
                        color = CyanNeon,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!notification.isRead && !notification.isResolved) {
                        TextButton(
                            onClick = onMarkAsRead,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Marcar leído", color = TextMuted, fontSize = 11.sp)
                        }
                    }

                    if (notification.requiresHumanAction && !notification.isResolved) {
                        Button(
                            onClick = onResolveAction,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (prio == NotificationPriority.CRITICA) ErrorRed else GoldPrimary,
                                contentColor = NavyDark
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("resolve_action_button_${notification.id}")
                        ) {
                            Text(
                                text = notification.actionLabel ?: "Resolver",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    } else if (notification.isResolved) {
                        Text(
                            text = "Resuelto por ${notification.resolvedBy ?: "Sistema"}",
                            color = SuccessGreen,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Mini Badge para KPIs en la cabecera
 */
@Composable
private fun KpiMiniBadge(
    title: String,
    count: Int,
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = count.toString(), color = color, fontSize = 16.sp, fontWeight = FontWeight.Black)
            Text(text = title, color = TextMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * Diálogo interactivo para emitir eventos de prueba reales
 */
@Composable
private fun EventSimulatorDialog(
    onDismiss: () -> Unit,
    onTriggerEvent: (String) -> Unit
) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = GoldPrimary)
                    Text(
                        text = "Emitir Evento Real de Prueba",
                        color = GoldPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Text(
                    text = "Selecciona un evento operativo real para validar la entrega y enrutamiento inteligente por rol:",
                    color = TextMuted,
                    fontSize = 12.sp
                )

                val events = listOf(
                    Triple("VISITOR_ENTRY", "Entrada de Visitante QR", "Notificación instantánea a Residente con datos y placa"),
                    Triple("PACKAGE", "Recepción de Paquetería", "Aviso a Residente con número de guía y oficial receptor"),
                    Triple("GUARD_ALERT", "Alerta Crítica en Garita", "Notificación P1 a Guardia con acción de contención requerida"),
                    Triple("SUPERVISOR_FINDING", "Hallazgo Crítico en Ronda", "Alerta P1 a Supervisor y Mesa Directiva para dictamen pericial"),
                    Triple("ADMIN_ESCALATION", "Escalamiento de Incidencia", "Aviso P2 a Administración por tiempo o criticidad de ticket"),
                    Triple("BOARD_SUMMARY", "Resumen Ejecutivo de Gestión", "Informe P3 a Mesa Directiva con métricas del Tiempo Devuelto")
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    items(events) { (type, title, desc) ->
                        Surface(
                            color = NavySurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.5.dp, CyanNeon.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTriggerEvent(type) }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(text = title, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                Text(text = desc, color = TextMuted, fontSize = 10.5.sp)
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
