package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.auth.AlfhaSecurityContext
import com.example.data.booking.AppDatabase
import com.example.data.maintenance.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val NavyCardBorder = Color(0xFF334155)
private val AmberGold = Color(0xFFF59E0B)

/**
 * FASE 13: CENTRO DE MANTENIMIENTO Y ÓRDENES DE TRABAJO ALFHA
 *
 * Módulo Unificado de Gestión de Mantenimiento Preventivo y Correctivo:
 * - Ciclo de vida: REGISTRADO ➔ ASIGNADO ➔ EN_ATENCION ➔ RESUELTO ➔ CERRADO.
 * - Folio automático inmutable, categoría, prioridad y SLA.
 * - Contador dinámico de tiempo transcurrido y alertas de vencimiento.
 * - Asignación automática o personalizada de técnicos.
 * - Registro de materiales utilizados y costo en MXN.
 * - Evidencia de solución y cierre formal con responsable, fecha y hora.
 * - Fuente Única de Verdad: Room SQLite (Cero duplicación / recaptura).
 * - Integrado en Residentes, Administración, Supervisión, Mesa Directiva y Panel Maestro.
 */
@Composable
fun MaintenanceHub(
    db: AppDatabase,
    modifier: Modifier = Modifier,
    unitFilter: String? = null,
    showNewOrderFab: Boolean = true,
    userRole: String = "ADMINISTRACION"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by AlfhaSecurityContext.currentUser.collectAsState()

    val orders by db.maintenanceDao().getAllOrdersFlow().collectAsState(initial = emptyList())
    val units by db.unitDao().getAllUnitsFlow().collectAsState(initial = emptyList())

    var selectedStatusFilter by remember { mutableStateOf("TODAS") } // TODAS, ACTIVAS, REGISTRADO, ASIGNADO, EN_ATENCION, RESUELTO, CERRADO, SLA_ALERTA
    var selectedPriorityFilter by remember { mutableStateOf("TODAS") }
    var selectedCategoryFilter by remember { mutableStateOf<MaintenanceCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Dialogs state
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedOrderForAssign by remember { mutableStateOf<MaintenanceOrderEntity?>(null) }
    var selectedOrderForResolve by remember { mutableStateOf<MaintenanceOrderEntity?>(null) }
    var selectedOrderForClose by remember { mutableStateOf<MaintenanceOrderEntity?>(null) }
    var selectedOrderForDetail by remember { mutableStateOf<MaintenanceOrderEntity?>(null) }

    // Ticker para actualizar contadores SLA en vivo
    var currentMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        MaintenanceEngine.checkSlaAndTriggerAlerts(context, db)
        while (true) {
            delay(30_000) // refrescar contadores cada 30s
            currentMillis = System.currentTimeMillis()
            MaintenanceEngine.checkSlaAndTriggerAlerts(context, db)
        }
    }

    // Filtrado inteligente
    val filteredOrders = remember(orders, selectedStatusFilter, selectedPriorityFilter, selectedCategoryFilter, searchQuery, unitFilter) {
        orders.filter { order ->
            if (unitFilter != null && order.unitId != unitFilter) {
                return@filter false
            }

            val statusMatch = when (selectedStatusFilter) {
                "TODAS" -> true
                "ACTIVAS" -> order.status != "CERRADO"
                "SLA_ALERTA" -> (order.status != "CERRADO" && order.status != "RESUELTO") && (order.isSlaExceeded(currentMillis) || order.getRemainingSlaMinutes(currentMillis) <= 120)
                else -> order.status.equals(selectedStatusFilter, ignoreCase = true)
            }

            val priorityMatch = when (selectedPriorityFilter) {
                "TODAS" -> true
                else -> order.priority.name.equals(selectedPriorityFilter, ignoreCase = true)
            }

            val categoryMatch = selectedCategoryFilter == null || order.category == selectedCategoryFilter

            val searchMatch = if (searchQuery.isBlank()) true else {
                order.folio.contains(searchQuery, ignoreCase = true) ||
                        order.title.contains(searchQuery, ignoreCase = true) ||
                        order.location.contains(searchQuery, ignoreCase = true) ||
                        order.requesterName.contains(searchQuery, ignoreCase = true) ||
                        order.assignedTechnician.contains(searchQuery, ignoreCase = true) ||
                        order.description.contains(searchQuery, ignoreCase = true)
            }

            statusMatch && priorityMatch && categoryMatch && searchMatch
        }
    }

    // Métricas
    val totalCount = orders.size
    val activeCount = orders.count { it.status != "CERRADO" }
    val inProgressCount = orders.count { it.status == "EN_ATENCION" }
    val resolvedCount = orders.count { it.status == "RESUELTO" || it.status == "CERRADO" }
    val slaAlertCount = orders.count { (it.status != "CERRADO" && it.status != "RESUELTO") && (it.isSlaExceeded(currentMillis) || it.getRemainingSlaMinutes(currentMillis) <= 120) }
    val totalCostMxn = orders.sumOf { it.materialsCost }
    val totalTimeSavedMin = orders.sumOf { it.timeSavedMinutes }

    val currencyFormatter = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NavyDark)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. HEADER Y MÉTRICAS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                        Text(
                            text = if (unitFilter != null) "Mantenimiento: $unitFilter" else "Mantenimiento y Órdenes de Trabajo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "FASE 13: Asignación automática, SLA, costos, evidencia y trazabilidad Room",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )
                }

                if (showNewOrderFab) {
                    Button(
                        onClick = { showCreateDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("hub_create_order_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nueva OT", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }

            // Metric Chips Grid
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    MaintenanceMetricMiniChip(
                        title = "TOTAL OT",
                        value = "$totalCount",
                        icon = Icons.Default.Assignment,
                        color = CyanNeon
                    )
                }
                item {
                    MaintenanceMetricMiniChip(
                        title = "ACTIVAS",
                        value = "$activeCount",
                        icon = Icons.Default.PendingActions,
                        color = AmberGold
                    )
                }
                item {
                    MaintenanceMetricMiniChip(
                        title = "EN ATENCIÓN",
                        value = "$inProgressCount",
                        icon = Icons.Default.Engineering,
                        color = Color(0xFF38BDF8)
                    )
                }
                item {
                    MaintenanceMetricMiniChip(
                        title = "RESUELTAS",
                        value = "$resolvedCount",
                        icon = Icons.Default.CheckCircle,
                        color = SuccessGreen
                    )
                }
                item {
                    MaintenanceMetricMiniChip(
                        title = "ALERTAS SLA",
                        value = "$slaAlertCount",
                        icon = Icons.Default.Warning,
                        color = if (slaAlertCount > 0) ErrorRed else TextMuted
                    )
                }
                item {
                    MaintenanceMetricMiniChip(
                        title = "COSTO MATERIALES",
                        value = currencyFormatter.format(totalCostMxn),
                        icon = Icons.Default.AttachMoney,
                        color = GoldPrimary
                    )
                }
                item {
                    MaintenanceMetricMiniChip(
                        title = "TIEMPO DEVUELTO",
                        value = "${totalTimeSavedMin}m",
                        icon = Icons.Default.Schedule,
                        color = SuccessGreen
                    )
                }
            }

            // 2. BUSCADOR UNIVERSAL
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("maintenance_search_input"),
                placeholder = { Text("Buscar por folio, ubicación, falla, técnico o reportante...", color = TextMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = NavyCardBorder,
                    focusedContainerColor = NavyCard,
                    unfocusedContainerColor = NavyCard,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // 3. FILTROS RÁPIDOS POR ESTADO
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val statusTabs = listOf(
                    "TODAS" to "Todas",
                    "ACTIVAS" to "Activas ($activeCount)",
                    "SLA_ALERTA" to "⚠️ Alertas SLA ($slaAlertCount)",
                    "REGISTRADO" to "Registradas",
                    "ASIGNADO" to "Asignadas",
                    "EN_ATENCION" to "En Atención",
                    "RESUELTO" to "Resueltas",
                    "CERRADO" to "Cerradas"
                )

                items(statusTabs) { (tabKey, tabLabel) ->
                    val isSelected = selectedStatusFilter == tabKey
                    val chipColor = when (tabKey) {
                        "SLA_ALERTA" -> ErrorRed
                        "ACTIVAS" -> AmberGold
                        "EN_ATENCION" -> CyanNeon
                        "RESUELTO" -> SuccessGreen
                        else -> GoldPrimary
                    }

                    Surface(
                        onClick = { selectedStatusFilter = tabKey },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) chipColor.copy(alpha = 0.2f) else NavyCard,
                        border = BorderStroke(1.dp, if (isSelected) chipColor else NavyCardBorder),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = tabLabel,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) chipColor else Color.White
                            )
                        }
                    }
                }
            }

            // 4. LISTA DE ÓRDENES DE TRABAJO
            if (filteredOrders.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    color = NavyCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, NavyCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Handyman, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No hay órdenes de mantenimiento", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (searchQuery.isNotBlank()) "No coinciden resultados con '$searchQuery'" else "Crea una nueva solicitud con el botón '+ Nueva OT'",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
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
                    items(filteredOrders, key = { it.folio }) { order ->
                        MaintenanceOrderCard(
                            order = order,
                            currentMillis = currentMillis,
                            onAssignClick = { selectedOrderForAssign = order },
                            onStartAttentionClick = {
                                scope.launch {
                                    val operator = currentUser?.name ?: "Administración"
                                    val res = MaintenanceEngine.startAttention(context, db, order.folio, operator)
                                    if (res is MaintenanceEngine.MaintenanceOperationResult.Success) {
                                        Toast.makeText(context, "🛠️ OT ${order.folio} en atención", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onResolveClick = { selectedOrderForResolve = order },
                            onCloseClick = { selectedOrderForClose = order },
                            onDetailClick = { selectedOrderForDetail = order }
                        )
                    }
                }
            }
        }
    }

    // DIÁLOGOS
    if (showCreateDialog) {
        CreateMaintenanceOrderDialog(
            db = db,
            defaultUnit = unitFilter,
            onDismiss = { showCreateDialog = false },
            onOrderCreated = {
                showCreateDialog = false
                Toast.makeText(context, "✅ Orden ${it.folio} creada exitosamente", Toast.LENGTH_LONG).show()
            }
        )
    }

    selectedOrderForAssign?.let { order ->
        AssignTechnicianDialog(
            order = order,
            onDismiss = { selectedOrderForAssign = null },
            onAssignConfirmed = { tech, phone ->
                scope.launch {
                    val operator = currentUser?.name ?: "Administrador"
                    val res = MaintenanceEngine.assignTechnician(context, db, order.folio, tech, phone, operator)
                    selectedOrderForAssign = null
                    if (res is MaintenanceEngine.MaintenanceOperationResult.Success) {
                        Toast.makeText(context, "✅ Técnico asignado a ${order.folio}", Toast.LENGTH_SHORT).show()
                    } else if (res is MaintenanceEngine.MaintenanceOperationResult.Error) {
                        Toast.makeText(context, "Error: ${res.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    selectedOrderForResolve?.let { order ->
        ResolveMaintenanceDialog(
            order = order,
            onDismiss = { selectedOrderForResolve = null },
            onResolveConfirmed = { solution, materials, cost, photoUri ->
                scope.launch {
                    val operator = currentUser?.name ?: "Técnico Mantenimiento"
                    val res = MaintenanceEngine.resolveOrder(context, db, order.folio, solution, materials, cost, photoUri, operator)
                    selectedOrderForResolve = null
                    if (res is MaintenanceEngine.MaintenanceOperationResult.Success) {
                        Toast.makeText(context, "✅ OT ${order.folio} marcada como RESUELTA", Toast.LENGTH_SHORT).show()
                    } else if (res is MaintenanceEngine.MaintenanceOperationResult.Error) {
                        Toast.makeText(context, "Error: ${res.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    selectedOrderForClose?.let { order ->
        CloseMaintenanceDialog(
            order = order,
            onDismiss = { selectedOrderForClose = null },
            onCloseConfirmed = { notes, rating ->
                scope.launch {
                    val operator = currentUser?.name ?: "Administración"
                    val res = MaintenanceEngine.closeOrder(context, db, order.folio, operator, notes, rating)
                    selectedOrderForClose = null
                    if (res is MaintenanceEngine.MaintenanceOperationResult.Success) {
                        Toast.makeText(context, "🔒 OT ${order.folio} CERRADA formalmente", Toast.LENGTH_SHORT).show()
                    } else if (res is MaintenanceEngine.MaintenanceOperationResult.Error) {
                        Toast.makeText(context, "Error: ${res.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    selectedOrderForDetail?.let { order ->
        MaintenanceDetailDialog(
            order = order,
            currentMillis = currentMillis,
            onDismiss = { selectedOrderForDetail = null }
        )
    }
}

@Composable
private fun MaintenanceMetricMiniChip(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = NavyDark,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                Text(title, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}

/**
 * Tarjeta individual de Orden de Trabajo
 */
@Composable
fun MaintenanceOrderCard(
    order: MaintenanceOrderEntity,
    currentMillis: Long,
    onAssignClick: () -> Unit,
    onStartAttentionClick: () -> Unit,
    onResolveClick: () -> Unit,
    onCloseClick: () -> Unit,
    onDetailClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isOverdue = order.isSlaExceeded(currentMillis)
    val remainingMins = order.getRemainingSlaMinutes(currentMillis)
    val isRisk = !isOverdue && remainingMins <= 120 && order.status != "CERRADO" && order.status != "RESUELTO"

    val statusColor = when (order.status) {
        "REGISTRADO" -> AmberGold
        "ASIGNADO" -> CyanNeon
        "EN_ATENCION" -> Color(0xFF38BDF8)
        "RESUELTO" -> SuccessGreen
        "CERRADO" -> Color(0xFF94A3B8)
        else -> GoldPrimary
    }

    val priorityColor = Color(order.priority.colorHex)
    val cardBorderColor = if (isOverdue && order.status != "CERRADO" && order.status != "RESUELTO") ErrorRed else if (isRisk) AmberGold else NavyCardBorder

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onDetailClick() }
            .testTag("maintenance_card_${order.folio}"),
        color = NavyCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, cardBorderColor)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Fila 1: Folio, Prioridad, Ubicación y Estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = GoldPrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = order.folio,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = GoldPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        color = priorityColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, priorityColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = order.priority.label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = priorityColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = order.status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Fila 2: Título, Ubicación y Categoría
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = order.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(14.dp))
                        Text(
                            text = order.location,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CyanNeon
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Category, contentDescription = null, tint = TextMuted, modifier = Modifier.size(13.dp))
                        Text(
                            text = order.category.label,
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            // Fila 3: Descripción resumida
            Text(
                text = order.description,
                fontSize = 12.sp,
                color = Color(0xFFCBD5E1),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Fila 4: Stepper visual de ciclo de vida (5 estados)
            MaintenanceLifecycleStepper(currentStep = order.statusStepIndex)

            // Fila 5: SLA y Tiempo Transcurrido
            Surface(
                color = NavyDark,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (isOverdue && order.status != "CERRADO" && order.status != "RESUELTO") ErrorRed.copy(alpha = 0.4f) else NavyCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = if (isOverdue) Icons.Default.AlarmOff else Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (isOverdue && order.status != "CERRADO" && order.status != "RESUELTO") ErrorRed else GoldPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = order.getSlaStatusFormatted(currentMillis),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOverdue && order.status != "CERRADO" && order.status != "RESUELTO") ErrorRed else if (isRisk) AmberGold else Color.White
                        )
                    }

                    Text(
                        text = "Transcurrido: ${order.getElapsedTimeFormatted(currentMillis)}",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            // Fila 6: Técnico Asignado y Costos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Engineering, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                    Text(
                        text = "Técnico: ${order.assignedTechnician}",
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )

                    if (order.assignedTechnicianPhone.isNotBlank()) {
                        IconButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${order.assignedTechnicianPhone}"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Llamar técnico", tint = CyanNeon, modifier = Modifier.size(13.dp))
                        }
                    }
                }

                if (order.materialsCost > 0) {
                    Text(
                        text = "Mat: $${String.format(Locale.US, "%.2f", order.materialsCost)} MXN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen
                    )
                }
            }

            // Fila 7: Acciones Contextuales según Estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (order.status) {
                    "REGISTRADO" -> {
                        Button(
                            onClick = onAssignClick,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Asignar Técnico", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    "ASIGNADO" -> {
                        Button(
                            onClick = onStartAttentionClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = NavyDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Iniciar Atención", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onAssignClick,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, NavyCardBorder),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Reasignar", fontSize = 11.sp)
                        }
                    }
                    "EN_ATENCION" -> {
                        Button(
                            onClick = onResolveClick,
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = NavyDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Registrar Solución y Materiales", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    "RESUELTO" -> {
                        Button(
                            onClick = onCloseClick,
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Firmar Cierre Definitivo", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    "CERRADO" -> {
                        Surface(
                            color = NavyDark,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(32.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Orden Finalizada y Auditada", fontSize = 11.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Stepper Visual de 5 pasos para el ciclo de vida de mantenimiento
 */
@Composable
fun MaintenanceLifecycleStepper(currentStep: Int) {
    val steps = listOf("REGISTRO", "ASIGNADO", "ATENCIÓN", "RESUELTO", "CERRADO")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        steps.forEachIndexed { index, stepLabel ->
            val isCompleted = index <= currentStep
            val isCurrent = index == currentStep
            val stepColor = if (isCurrent) GoldPrimary else if (isCompleted) SuccessGreen else TextMuted.copy(alpha = 0.4f)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(if (isCompleted) stepColor else NavyDark)
                        .border(1.dp, stepColor, CircleShape)
                ) {
                    if (isCompleted && !isCurrent) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = NavyDark, modifier = Modifier.size(12.dp))
                    } else {
                        Text(
                            text = "${index + 1}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) NavyDark else Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = stepLabel,
                    fontSize = 8.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    color = if (isCurrent) GoldPrimary else if (isCompleted) Color.White else TextMuted,
                    maxLines = 1
                )
            }

            if (index < steps.size - 1) {
                val lineColor = if (index < currentStep) SuccessGreen else NavyCardBorder
                Box(
                    modifier = Modifier
                        .weight(0.6f)
                        .height(2.dp)
                        .background(lineColor)
                )
            }
        }
    }
}

/**
 * Diálogo para Registrar Nueva Orden de Trabajo
 */
@Composable
fun CreateMaintenanceOrderDialog(
    db: AppDatabase,
    defaultUnit: String? = null,
    onDismiss: () -> Unit,
    onOrderCreated: (MaintenanceOrderEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by AlfhaSecurityContext.currentUser.collectAsState()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(MaintenanceCategory.PLOMERIA) }
    var selectedPriority by remember { mutableStateOf(MaintenancePriority.MEDIA) }
    var locationType by remember { mutableStateOf(if (defaultUnit != null) MaintenanceLocationType.UNIDAD_PRIVADA else MaintenanceLocationType.UNIDAD_PRIVADA) }
    var location by remember { mutableStateOf(defaultUnit ?: "") }
    var unitId by remember { mutableStateOf(defaultUnit ?: "") }
    var requesterName by remember { mutableStateOf(currentUser?.name ?: "") }
    var requesterPhone by remember { mutableStateOf("") }
    var photoUriDescription by remember { mutableStateOf("") }
    var autoAssign by remember { mutableStateOf(true) }
    var customTechnician by remember { mutableStateOf("") }
    var customPhone by remember { mutableStateOf("") }

    val suggestedTech = remember(selectedCategory) { MaintenanceEngine.getSuggestedTechnician(selectedCategory) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .testTag("create_maintenance_dialog"),
            color = NavyCard,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Build, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                            Text("Nueva Solicitud de Mantenimiento", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                        }
                    }
                }

                // Título de la Falla
                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título de la Falla / Requerimiento *") },
                        placeholder = { Text("Ej: Fuga de agua en lavabo principal") },
                        modifier = Modifier.fillMaxWidth().testTag("maintenance_input_title"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = NavyCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                }

                // Tipo de Ubicación
                item {
                    Text("Tipo de Ubicación:", fontSize = 11.sp, color = TextMuted)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MaintenanceLocationType.values().forEach { type ->
                            val isSel = locationType == type
                            Surface(
                                onClick = {
                                    locationType = type
                                    if (type == MaintenanceLocationType.AREA_COMUN) {
                                        unitId = ""
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) CyanNeon.copy(alpha = 0.2f) else NavyDark,
                                border = BorderStroke(1.dp, if (isSel) CyanNeon else NavyCardBorder),
                                modifier = Modifier.weight(1f).height(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(type.label, fontSize = 11.sp, color = if (isSel) CyanNeon else Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Campo Ubicación
                item {
                    OutlinedTextField(
                        value = location,
                        onValueChange = {
                            location = it
                            if (locationType == MaintenanceLocationType.UNIDAD_PRIVADA) {
                                unitId = it
                            }
                        },
                        label = { Text(if (locationType == MaintenanceLocationType.UNIDAD_PRIVADA) "Número de Casa / Depto *" else "Área Común Específica *") },
                        placeholder = { Text(if (locationType == MaintenanceLocationType.UNIDAD_PRIVADA) "Ej: Casa 102 o Torre A 405" else "Ej: Alberca, Gimnasio, Garita") },
                        modifier = Modifier.fillMaxWidth().testTag("maintenance_input_location"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = NavyCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                }

                // Categoría
                item {
                    Text("Categoría Técnica:", fontSize = 11.sp, color = TextMuted)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(MaintenanceCategory.values()) { cat ->
                            val isSel = selectedCategory == cat
                            Surface(
                                onClick = { selectedCategory = cat },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) GoldPrimary.copy(alpha = 0.2f) else NavyDark,
                                border = BorderStroke(1.dp, if (isSel) GoldPrimary else NavyCardBorder),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text(cat.label, fontSize = 10.sp, color = if (isSel) GoldPrimary else Color.White, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                }

                // Prioridad y SLA
                item {
                    Text("Prioridad y SLA de Atención:", fontSize = 11.sp, color = TextMuted)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MaintenancePriority.values().forEach { pri ->
                            val isSel = selectedPriority == pri
                            val color = Color(pri.colorHex)
                            Surface(
                                onClick = { selectedPriority = pri },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) color.copy(alpha = 0.2f) else NavyDark,
                                border = BorderStroke(1.dp, if (isSel) color else NavyCardBorder),
                                modifier = Modifier.weight(1f).height(38.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(pri.name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isSel) color else Color.White)
                                    Text("SLA ${pri.defaultSlaHours}h", fontSize = 8.sp, color = if (isSel) color else TextMuted)
                                }
                            }
                        }
                    }
                }

                // Descripción Detallada
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Descripción Detallada de la Falla *") },
                        placeholder = { Text("Describa qué sucede, síntomas, si requiere corte de agua/energía, etc.") },
                        modifier = Modifier.fillMaxWidth().height(80.dp).testTag("maintenance_input_desc"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = NavyCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        maxLines = 3
                    )
                }

                // Evidencia Fotográfica / Adjunto
                item {
                    OutlinedTextField(
                        value = photoUriDescription,
                        onValueChange = { photoUriDescription = it },
                        label = { Text("Evidencia Fotográfica (Descripción / URI)") },
                        placeholder = { Text("Ej: foto_fuga_lavabo_01.jpg o descriptivo") },
                        leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = NavyCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                }

                // Asignación de Técnico
                item {
                    Surface(
                        color = NavyDark,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, NavyCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Engineering, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                    Text("Asignación de Técnico", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Auto-Asignar", fontSize = 10.sp, color = TextMuted)
                                    Switch(
                                        checked = autoAssign,
                                        onCheckedChange = { autoAssign = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary, checkedTrackColor = GoldPrimary.copy(alpha = 0.3f))
                                    )
                                }
                            }

                            if (autoAssign) {
                                Text(
                                    text = "Sugerido: ${suggestedTech.first} (${suggestedTech.second})",
                                    fontSize = 11.sp,
                                    color = CyanNeon,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                OutlinedTextField(
                                    value = customTechnician,
                                    onValueChange = { customTechnician = it },
                                    label = { Text("Nombre del Técnico / Empresa") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GoldPrimary,
                                        unfocusedBorderColor = NavyCardBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = customPhone,
                                    onValueChange = { customPhone = it },
                                    label = { Text("Teléfono de Contacto Técnico") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = GoldPrimary,
                                        unfocusedBorderColor = NavyCardBorder,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    ),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }

                // Botón Confirmar Creación
                item {
                    Button(
                        onClick = {
                            scope.launch {
                                val result = MaintenanceEngine.createMaintenanceOrder(
                                    context = context,
                                    db = db,
                                    title = title,
                                    description = description,
                                    category = selectedCategory,
                                    priority = selectedPriority,
                                    locationType = locationType,
                                    location = location,
                                    unitId = if (locationType == MaintenanceLocationType.UNIDAD_PRIVADA) unitId else null,
                                    requesterName = requesterName.ifBlank { "Residente" },
                                    requesterRole = currentUser?.role ?: "RESIDENTE",
                                    requesterPhone = requesterPhone,
                                    initialPhotoUri = photoUriDescription.ifBlank { null },
                                    customTechnician = if (!autoAssign) customTechnician else null,
                                    customTechnicianPhone = if (!autoAssign) customPhone else null,
                                    autoAssign = autoAssign
                                )

                                when (result) {
                                    is MaintenanceEngine.MaintenanceOperationResult.Success -> {
                                        onOrderCreated(result.order)
                                    }
                                    is MaintenanceEngine.MaintenanceOperationResult.Error -> {
                                        Toast.makeText(context, "Error: ${result.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("submit_maintenance_order_btn")
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Crear Orden de Trabajo Inmutable", fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * Diálogo para Asignar / Reasignar Técnico
 */
@Composable
fun AssignTechnicianDialog(
    order: MaintenanceOrderEntity,
    onDismiss: () -> Unit,
    onAssignConfirmed: (String, String) -> Unit
) {
    val suggested = remember(order.category) { MaintenanceEngine.getSuggestedTechnician(order.category) }
    var techName by remember { mutableStateOf(if (order.assignedTechnician != "Por Asignar") order.assignedTechnician else suggested.first) }
    var techPhone by remember { mutableStateOf(if (order.assignedTechnicianPhone.isNotBlank()) order.assignedTechnicianPhone else suggested.second) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            color = NavyCard,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f))
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
                    Text("Asignar Responsable OT [${order.folio}]", fontWeight = FontWeight.Bold, color = Color.White)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                Text(
                    text = "Orden: ${order.title} en ${order.location}",
                    fontSize = 12.sp,
                    color = CyanNeon
                )

                OutlinedTextField(
                    value = techName,
                    onValueChange = { techName = it },
                    label = { Text("Nombre del Técnico o Proveedor *") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = NavyCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = techPhone,
                    onValueChange = { techPhone = it },
                    label = { Text("Teléfono de Contacto") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = NavyCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Button(
                    onClick = {
                        if (techName.isNotBlank()) {
                            onAssignConfirmed(techName, techPhone)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Confirmar Asignación", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Diálogo para Resolver Orden: Solución, Materiales, Costo y Foto
 */
@Composable
fun ResolveMaintenanceDialog(
    order: MaintenanceOrderEntity,
    onDismiss: () -> Unit,
    onResolveConfirmed: (String, String?, Double, String?) -> Unit
) {
    var solutionNotes by remember { mutableStateOf("") }
    var materialsUsed by remember { mutableStateOf("") }
    var materialsCostStr by remember { mutableStateOf("") }
    var solutionPhotoUri by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            color = NavyCard,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f))
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                        Text("Dictamen y Solución OT [${order.folio}]", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                OutlinedTextField(
                    value = solutionNotes,
                    onValueChange = { solutionNotes = it },
                    label = { Text("Descripción de la Solución Realizada *") },
                    placeholder = { Text("Detalle qué reparación o cambio se ejecutó...") },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SuccessGreen,
                        unfocusedBorderColor = NavyCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    maxLines = 3
                )

                OutlinedTextField(
                    value = materialsUsed,
                    onValueChange = { materialsUsed = it },
                    label = { Text("Materiales y Refacciones Utilizadas") },
                    placeholder = { Text("Ej: 2 Válvulas esfera 1/2, Cinta teflón, Pegamento") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SuccessGreen,
                        unfocusedBorderColor = NavyCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = materialsCostStr,
                    onValueChange = { materialsCostStr = it },
                    label = { Text("Costo Total de Materiales (MXN)") },
                    placeholder = { Text("Ej: 450.00") },
                    leadingIcon = { Text("$", color = SuccessGreen, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SuccessGreen,
                        unfocusedBorderColor = NavyCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = solutionPhotoUri,
                    onValueChange = { solutionPhotoUri = it },
                    label = { Text("Evidencia Fotográfica de Solución") },
                    placeholder = { Text("Ej: foto_reparacion_finalizada.jpg") },
                    leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SuccessGreen,
                        unfocusedBorderColor = NavyCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                Button(
                    onClick = {
                        if (solutionNotes.isNotBlank()) {
                            val cost = materialsCostStr.toDoubleOrNull() ?: 0.0
                            onResolveConfirmed(solutionNotes, materialsUsed.ifBlank { null }, cost, solutionPhotoUri.ifBlank { null })
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = NavyDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Marcar como RESUELTO", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

/**
 * Diálogo para Cierre Formal de Orden de Trabajo
 */
@Composable
fun CloseMaintenanceDialog(
    order: MaintenanceOrderEntity,
    onDismiss: () -> Unit,
    onCloseConfirmed: (String?, Int?) -> Unit
) {
    var closureNotes by remember { mutableStateOf("") }
    var rating by remember { mutableStateOf(5) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            color = NavyCard,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                        Text("Cierre Definitivo OT [${order.folio}]", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                Text(
                    text = "Solución reportada: ${order.solutionDescription ?: "Trabajos completados"}",
                    fontSize = 12.sp,
                    color = Color.White
                )

                Text("Calificación de Satisfacción:", fontSize = 11.sp, color = TextMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    (1..5).forEach { star ->
                        IconButton(onClick = { rating = star }) {
                            Icon(
                                imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$star estrellas",
                                tint = if (star <= rating) GoldPrimary else TextMuted,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = closureNotes,
                    onValueChange = { closureNotes = it },
                    label = { Text("Notas de Conformidad / Cierre") },
                    placeholder = { Text("Ej: Trabajo recibido a entera satisfacción del residente.") },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = NavyCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    maxLines = 3
                )

                Button(
                    onClick = {
                        onCloseConfirmed(closureNotes.ifBlank { null }, rating)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Firmar Cierre Inmutable", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

/**
 * Diálogo de Historial y Detalle Completo 360°
 */
@Composable
fun MaintenanceDetailDialog(
    order: MaintenanceOrderEntity,
    currentMillis: Long,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            color = NavyCard,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Expediente de OT [${order.folio}]", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                            Text("Trazabilidad Completa en Room SQLite", fontSize = 10.sp, color = TextMuted)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                        }
                    }
                }

                item {
                    MaintenanceLifecycleStepper(currentStep = order.statusStepIndex)
                }

                item {
                    DetailSection(title = "INFORMACIÓN GENERAL") {
                        DetailItem("Título", order.title)
                        DetailItem("Ubicación", order.location)
                        DetailItem("Categoría", order.category.label)
                        DetailItem("Prioridad", "${order.priority.label} (SLA: ${order.slaTargetHours}h)")
                        DetailItem("Fecha Registro", order.formattedCreatedDate)
                        DetailItem("Fecha Límite SLA", order.formattedDeadline)
                        DetailItem("Reportado Por", "${order.requesterName} (${order.requesterRole})")
                        if (order.requesterPhone.isNotBlank()) DetailItem("Tel. Reportante", order.requesterPhone)
                    }
                }

                item {
                    DetailSection(title = "DESCRIPCIÓN DE LA FALLA") {
                        Text(order.description, fontSize = 12.sp, color = Color.White)
                        if (!order.initialPhotoUri.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("📷 Evidencia inicial: ${order.initialPhotoUri}", fontSize = 11.sp, color = CyanNeon)
                        }
                    }
                }

                item {
                    DetailSection(title = "ASIGNACIÓN Y ATENCIÓN") {
                        DetailItem("Técnico Asignado", order.assignedTechnician)
                        if (order.assignedTechnicianPhone.isNotBlank()) DetailItem("Teléfono Técnico", order.assignedTechnicianPhone)
                        if (order.assignedBy != null) DetailItem("Asignado Por", order.assignedBy)
                        if (order.attendedBy != null) DetailItem("Atendido Por", order.attendedBy)
                        DetailItem("Tiempo Transcurrido", order.getElapsedTimeFormatted(currentMillis))
                        DetailItem("Estado SLA", order.getSlaStatusFormatted(currentMillis))
                    }
                }

                if (order.solutionDescription != null || order.materialsCost > 0) {
                    item {
                        DetailSection(title = "SOLUCIÓN Y MATERIALES") {
                            order.solutionDescription?.let { DetailItem("Solución", it) }
                            order.materialsUsed?.let { DetailItem("Materiales", it) }
                            DetailItem("Costo Materiales", "$${String.format(Locale.US, "%.2f", order.materialsCost)} MXN")
                            order.solutionPhotoUri?.let { Text("📷 Evidencia solución: $it", fontSize = 11.sp, color = SuccessGreen) }
                            order.formattedResolvedDate?.let { DetailItem("Fecha Resolución", it) }
                            order.resolvedBy?.let { DetailItem("Resuelto Por", it) }
                        }
                    }
                }

                if (order.status == "CERRADO") {
                    item {
                        DetailSection(title = "CIERRE FORMAL") {
                            order.closedBy?.let { DetailItem("Cerrado Por", it) }
                            order.formattedClosedDate?.let { DetailItem("Fecha de Cierre", it) }
                            order.residentSatisfactionRating?.let { DetailItem("Satisfacción", "$it / 5 estrellas") }
                            order.closureNotes?.let { DetailItem("Notas Cierre", it) }
                        }
                    }
                }

                item {
                    Surface(
                        color = NavyDark,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tiempo Devuelto: ${order.timeSavedMinutes} minutos por automatización ALFHA",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = SuccessGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = NavyDark,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, NavyCardBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.Black, color = GoldPrimary)
            content()
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = TextMuted)
        Text(value, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
    }
}
