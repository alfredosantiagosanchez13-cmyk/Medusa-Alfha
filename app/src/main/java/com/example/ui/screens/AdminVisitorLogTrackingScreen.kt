@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.House
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.booking.AppDatabase
import com.example.data.visitor.FirestoreSyncStatus
import com.example.data.visitor.FirestoreVisitorLog
import com.example.data.visitor.FirestoreVisitorTracker
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
 * PANTALLA OFICIAL DE SEGUIMIENTO Y BITÁCORA DE VISITANTES EN FIRESTORE
 * (VISITOR LOG TRACKING SCREEN PARA ADMINISTRADORES)
 *
 * Almacena y monitorea en tiempo real:
 * - Timestamps nativos de entrada (entry timestamp) y salida (exit timestamp) en Firestore.
 * - Información del visitante (nombre, documento, unidad habitacional, anfitrión, vehículo, etc.).
 * - Estado de estadía activa y cálculo automático del tiempo de permanencia.
 * - Registro de salida (Check-Out) con actualización atómica en la nube.
 * - Aislamiento multi-inquilino en `/condominiums/{condominiumId}/visitor_logs`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminVisitorLogTrackingScreen(
    db: AppDatabase,
    condominiumId: String = "PRADOS_1",
    adminName: String = "Administrador General",
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Inicializar tracker en tiempo real
    val tracker = remember(condominiumId) {
        FirestoreVisitorTracker(context, db, condominiumId)
    }

    DisposableEffect(tracker) {
        onDispose {
            tracker.destroy()
        }
    }

    val visitorLogs by tracker.visitorLogs.collectAsState()
    val syncStatus by tracker.syncStatus.collectAsState()
    val statusMessage by tracker.statusMessage.collectAsState()
    val metrics by tracker.metrics.collectAsState()
    val isRefreshing by tracker.isRefreshing.collectAsState()

    // Estados de UI locales
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("TODOS") }
    var selectedLogForDetail by remember { mutableStateOf<FirestoreVisitorLog?>(null) }
    var showNewEntryDialog by remember { mutableStateOf(false) }
    var logToConfirmExit by remember { mutableStateOf<FirestoreVisitorLog?>(null) }
    var exitNotesInput by remember { mutableStateOf("") }

    // Filtrado de logs
    val filteredLogs = remember(visitorLogs, searchQuery, selectedFilter) {
        val calNow = java.util.Calendar.getInstance()
        calNow.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calNow.set(java.util.Calendar.MINUTE, 0)
        calNow.set(java.util.Calendar.SECOND, 0)
        calNow.set(java.util.Calendar.MILLISECOND, 0)
        val startOfToday = calNow.timeInMillis

        visitorLogs.filter { log ->
            val matchesQuery = searchQuery.isBlank() ||
                    log.visitorName.contains(searchQuery, ignoreCase = true) ||
                    log.authorizedUnitNumber.contains(searchQuery, ignoreCase = true) ||
                    log.folio.contains(searchQuery, ignoreCase = true) ||
                    log.hostResidentName.contains(searchQuery, ignoreCase = true) ||
                    log.visitorDocument.contains(searchQuery, ignoreCase = true) ||
                    (log.vehiclePlate?.contains(searchQuery, ignoreCase = true) == true)

            val matchesFilter = when (selectedFilter) {
                "EN_CONDOMINIO" -> log.status == "CHECKED_IN" || log.status == "VERIFICADO"
                "SALIDAS" -> log.status == "DEPARTED" || log.checkOutMillis != null
                "VEHICULOS" -> !log.vehiclePlate.isNullOrBlank()
                "HOY" -> log.timestampMillis >= startOfToday
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NavyDark)
            .testTag("admin_visitor_tracking_screen")
    ) {
        // =========================================================================
        // CABECERA SUPERIOR ADMINISTRATIVA
        // =========================================================================
        Surface(
            color = NavySurface,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("admin_visitor_back_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Volver",
                                    tint = GoldPrimary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(GoldPrimary.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                .border(1.dp, GoldPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Bitácora",
                                tint = GoldPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Bitácora de Visitantes",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Auditoría de Ingresos y Salidas • Firestore Cloud",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Botones de acción rápida: Refrescar y Nuevo Ingreso
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { tracker.refreshFromCloud() },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("admin_visitor_refresh_btn")
                        ) {
                            if (isRefreshing) {
                                CircularProgressIndicator(
                                    color = CyanNeon,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refrescar Firestore",
                                    tint = CyanNeon
                                )
                            }
                        }

                        Button(
                            onClick = { showNewEntryDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier
                                .height(40.dp)
                                .testTag("admin_new_visitor_entry_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = NavyDark,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Nuevo Ingreso",
                                color = NavyDark,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Estado de Sincronización Firestore en Vivo
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NavyDark.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        val statusColor = when (syncStatus) {
                            FirestoreSyncStatus.CONNECTED_LIVE -> SuccessGreen
                            FirestoreSyncStatus.CONNECTING -> CyanNeon
                            FirestoreSyncStatus.SYNCED_CACHE -> GoldPrimary
                            FirestoreSyncStatus.OFFLINE_ROOM_FALLBACK -> WarningOrange
                            FirestoreSyncStatus.ERROR -> ErrorRed
                        }

                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(statusColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusMessage,
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "/condominiums/$condominiumId/visitor_logs",
                        color = TextMuted.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // =========================================================================
        // TARJETAS KPI DE RESUMEN ADMINISTRATIVO
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AdminKpiCard(
                title = "Total Visitas",
                value = "${metrics.totalLogsCount}",
                icon = Icons.Default.Badge,
                accentColor = GoldPrimary,
                modifier = Modifier.weight(1f),
                tag = "kpi_total_visitors"
            )

            AdminKpiCard(
                title = "En Condominio",
                value = "${metrics.currentlyInsideCount}",
                icon = Icons.Default.HourglassBottom,
                accentColor = SuccessGreen,
                modifier = Modifier.weight(1f),
                tag = "kpi_inside_visitors"
            )

            AdminKpiCard(
                title = "Salidas",
                value = "${metrics.departedCount}",
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                accentColor = CyanNeon,
                modifier = Modifier.weight(1f),
                tag = "kpi_departed_visitors"
            )

            AdminKpiCard(
                title = "Prom. Estadía",
                value = metrics.averageStayFormatted,
                icon = Icons.Default.Timer,
                accentColor = WarningOrange,
                modifier = Modifier.weight(1f),
                tag = "kpi_avg_stay"
            )
        }

        // =========================================================================
        // BARRA DE BÚSQUEDA Y FILTROS
        // =========================================================================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar por visitante, unidad, folio o placa...", color = TextMuted, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar", tint = GoldPrimary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = TextMuted)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = NavySurface,
                    unfocusedContainerColor = NavySurface,
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = Color(0xFF2A364F),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_visitor_search_query")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Chips de filtrado
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AdminFilterChip(
                    label = "Todos (${metrics.totalLogsCount})",
                    selected = selectedFilter == "TODOS",
                    onClick = { selectedFilter = "TODOS" },
                    tag = "admin_filter_chip_all"
                )

                AdminFilterChip(
                    label = "🟢 En Condominio (${metrics.currentlyInsideCount})",
                    selected = selectedFilter == "EN_CONDOMINIO",
                    onClick = { selectedFilter = "EN_CONDOMINIO" },
                    tag = "admin_filter_chip_inside"
                )

                AdminFilterChip(
                    label = "🏁 Salidas (${metrics.departedCount})",
                    selected = selectedFilter == "SALIDAS",
                    onClick = { selectedFilter = "SALIDAS" },
                    tag = "admin_filter_chip_departed"
                )

                AdminFilterChip(
                    label = "🚗 Con Vehículo",
                    selected = selectedFilter == "VEHICULOS",
                    onClick = { selectedFilter = "VEHICULOS" },
                    tag = "admin_filter_chip_vehicles"
                )

                AdminFilterChip(
                    label = "📅 Hoy (${metrics.todayEntriesCount})",
                    selected = selectedFilter == "HOY",
                    onClick = { selectedFilter = "HOY" },
                    tag = "admin_filter_chip_today"
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // =========================================================================
        // LISTADO DE REGISTROS DE VISITANTES CON TIMESTAMPS
        // =========================================================================
        if (filteredLogs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(NavySurface, CircleShape)
                            .border(1.dp, GoldPrimary.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = GoldPrimary.copy(alpha = 0.7f),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (searchQuery.isNotEmpty()) "No se encontraron registros coincidentes" else "No hay registros en la bitácora de Firestore",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (searchQuery.isNotEmpty()) "Prueba ajustando el término de búsqueda o limpiando los filtros." else "Los nuevos accesos registrados por guardias en caseta o administración aparecerán aquí en tiempo real.",
                        color = TextMuted,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showNewEntryDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("admin_empty_state_add_btn")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = NavyDark)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Registrar Primer Acceso", color = NavyDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredLogs, key = { it.folio }) { log ->
                    AdminVisitorCard(
                        log = log,
                        onViewDetail = { selectedLogForDetail = log },
                        onRecordExit = {
                            logToConfirmExit = log
                            exitNotesInput = "Salida autorizada por caseta/administración"
                        }
                    )
                }
            }
        }
    }

    // =========================================================================
    // DIÁLOGO: REGISTRAR NUEVO INGRESO EN FIRESTORE
    // =========================================================================
    if (showNewEntryDialog) {
        AdminNewVisitorEntryDialog(
            condominiumId = condominiumId,
            adminName = adminName,
            onDismiss = { showNewEntryDialog = false },
            onSave = { name, unit, doc, host, plate, passType, notes ->
                scope.launch {
                    val result = tracker.recordVisitorEntry(
                        visitorName = name,
                        authorizedUnitNumber = unit,
                        hostResidentName = host,
                        visitorDocument = doc,
                        passTypeLabel = passType,
                        vehiclePlate = plate,
                        guardName = adminName,
                        guardNotes = notes
                    )
                    if (result.isSuccess) {
                        Toast.makeText(context, "✅ Ingreso registrado y guardado en Firestore", Toast.LENGTH_SHORT).show()
                        showNewEntryDialog = false
                    } else {
                        Toast.makeText(context, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // =========================================================================
    // DIÁLOGO: CONFIRMAR REGISTRO DE SALIDA (CHECK-OUT) EN FIRESTORE
    // =========================================================================
    logToConfirmExit?.let { targetLog ->
        AlertDialog(
            onDismissRequest = { logToConfirmExit = null },
            containerColor = NavySurface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = CyanNeon,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Registrar Salida de Visitante",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "¿Deseas registrar la salida de ${targetLog.visitorName} de la unidad ${targetLog.authorizedUnitNumber}?",
                        color = Color.White,
                        fontSize = 13.sp
                    )

                    // Información de tiempo transcurrido
                    val currentStay = FirestoreVisitorTracker.calculateStayDuration(targetLog.timestampMillis, null)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NavyDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "⏱️ Tiempo en condominio: $currentStay",
                                color = GoldPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Ingreso: ${FirestoreVisitorTracker.formatTimestamp(targetLog.timestamp, targetLog.timestampMillis)}",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "Salida a registrar: ${SimpleDateFormat("HH:mm:ss • dd/MM/yyyy", Locale.getDefault()).format(Date())}",
                                color = CyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    OutlinedTextField(
                        value = exitNotesInput,
                        onValueChange = { exitNotesInput = it },
                        label = { Text("Observaciones de Salida", color = TextMuted, fontSize = 11.sp) },
                        placeholder = { Text("Ej. Salida normal en vehículo sin novedades", color = TextMuted) },
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = NavyDark,
                            unfocusedContainerColor = NavyDark,
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = Color(0xFF2A364F),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_exit_notes")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val res = tracker.recordVisitorExit(targetLog.folio, exitNotesInput)
                            if (res.isSuccess) {
                                Toast.makeText(context, "🏁 Salida registrada exitosamente en Firestore", Toast.LENGTH_SHORT).show()
                                logToConfirmExit = null
                            } else {
                                Toast.makeText(context, "Error: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("confirm_record_exit_btn")
                ) {
                    Text("Confirmar Salida", color = NavyDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { logToConfirmExit = null }) {
                    Text("Cancelar", color = TextMuted)
                }
            }
        )
    }

    // =========================================================================
    // DIÁLOGO: DETALLE Y EXPEDIENTE COMPLETO DEL REGISTRO
    // =========================================================================
    selectedLogForDetail?.let { detailLog ->
        AdminVisitorDetailDialog(
            log = detailLog,
            condominiumId = condominiumId,
            onDismiss = { selectedLogForDetail = null },
            onRecordExit = {
                selectedLogForDetail = null
                logToConfirmExit = detailLog
                exitNotesInput = "Salida autorizada por caseta/administración"
            }
        )
    }
}

/**
 * Tarjeta individual de visitante con indicadores claros de Entry/Exit Timestamps
 */
@Composable
fun AdminVisitorCard(
    log: FirestoreVisitorLog,
    onViewDetail: () -> Unit,
    onRecordExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isInside = log.status == "CHECKED_IN" || log.status == "VERIFICADO"
    val isDeparted = log.status == "DEPARTED" || log.checkOutMillis != null

    val statusColor = when {
        isInside -> SuccessGreen
        isDeparted -> CyanNeon
        else -> WarningOrange
    }

    val statusLabel = when {
        isInside -> "DENTRO"
        isDeparted -> "SALIÓ"
        else -> "PRE-REGISTRO"
    }

    val stayDuration = FirestoreVisitorTracker.calculateStayDuration(log.timestampMillis, log.checkOutMillis)

    Card(
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isInside) SuccessGreen.copy(alpha = 0.5f) else Color(0xFF22304A)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onViewDetail() }
            .testTag("visitor_log_card_${log.folio}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Fila superior: Nombre, Folio y Estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(statusColor.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, statusColor.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isDeparted) Icons.AutoMirrored.Filled.ExitToApp else Icons.Default.Person,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = log.visitorName,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("visitor_name_${log.folio}")
                        )
                        Text(
                            text = "Doc: ${log.visitorDocument} • Tipo: ${log.passTypeLabel}",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                // Badge de estado
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Fila de Información de Unidad y Vehículo
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Unidad Destino
                Surface(
                    color = NavySurface,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("visitor_unit_${log.folio}")
                    ) {
                        Icon(Icons.Default.House, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = log.authorizedUnitNumber,
                            color = GoldPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Placa de vehículo si existe
                if (!log.vehiclePlate.isNullOrBlank()) {
                    Surface(
                        color = NavySurface,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyanNeon.copy(alpha = 0.3f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = log.vehiclePlate,
                                color = CyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Anfitrión
                Text(
                    text = "Anfitrión: ${log.hostResidentName}",
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // =====================================================================
            // TIMESTAMPS DE ENTRADA Y SALIDA (Requisito Principal del Usuario)
            // =====================================================================
            Surface(
                color = NavyDark.copy(alpha = 0.7f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    // Timestamp de Entrada
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Login,
                                contentDescription = "Ingreso",
                                tint = SuccessGreen,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Entrada:",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = FirestoreVisitorTracker.formatTimestamp(log.timestamp, log.timestampMillis),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Timestamp de Salida
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Salida",
                                tint = if (isDeparted) CyanNeon else WarningOrange,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Salida:",
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (isDeparted) {
                            Text(
                                text = FirestoreVisitorTracker.formatTimestamp(log.checkOutTimestamp, log.checkOutMillis),
                                color = CyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            Text(
                                text = "🟢 EN ESTADÍA ($stayDuration)",
                                color = SuccessGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Duración total calculada
                    if (isDeparted) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "⏱️ Tiempo de permanencia:",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                            Text(
                                text = stayDuration,
                                color = GoldPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Barra inferior de acciones
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Folio: #${log.folio.takeLast(10)}",
                    color = TextMuted.copy(alpha = 0.6f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Si está adentro, mostrar botón directo para registrar salida en Firestore
                    if (isInside) {
                        Button(
                            onClick = onRecordExit,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .testTag("admin_checkout_button_${log.folio}")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = NavyDark, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Registrar Salida", color = NavyDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = onViewDetail,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("admin_view_detail_button_${log.folio}")
                    ) {
                        Text("Ver Detalle", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Modal para Registrar Nuevo Acceso directamente en Firestore
 */
@Composable
fun AdminNewVisitorEntryDialog(
    condominiumId: String,
    adminName: String,
    onDismiss: () -> Unit,
    onSave: (name: String, unit: String, doc: String, host: String, plate: String?, passType: String, notes: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("") }
    var document by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var selectedPassType by remember { mutableStateOf("Visita General") }
    var notes by remember { mutableStateOf("") }

    val passTypes = listOf("Visita General", "Familiar", "Servicio Técnico", "Delivery", "Proveedor", "Mudanza")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
                .testTag("admin_new_visitor_dialog")
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Título
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(GoldPrimary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Nuevo Ingreso a Firestore",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Colección: /condominiums/$condominiumId/visitor_logs",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("btn_cancel_visitor_dialog")) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Campos
                AdminFormField(
                    label = "Nombre del Visitante *",
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Ej. Roberto Carlos Morales",
                    icon = Icons.Default.Person,
                    tag = "input_visitor_name"
                )

                Spacer(modifier = Modifier.height(10.dp))

                AdminFormField(
                    label = "Unidad Habitacional Autorizada *",
                    value = unit,
                    onValueChange = { unit = it },
                    placeholder = "Ej. Casa 104 o Depto 302",
                    icon = Icons.Default.House,
                    tag = "input_unit_number"
                )

                Spacer(modifier = Modifier.height(10.dp))

                AdminFormField(
                    label = "Residente Anfitrión que Autoriza *",
                    value = host,
                    onValueChange = { host = it },
                    placeholder = "Ej. Carlos Mendoza",
                    icon = Icons.Default.Badge,
                    tag = "input_host_resident"
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        AdminFormField(
                            label = "Documento / ID",
                            value = document,
                            onValueChange = { document = it },
                            placeholder = "INE / RUT / DNI",
                            icon = Icons.Default.Badge,
                            tag = "input_visitor_document"
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        AdminFormField(
                            label = "Placa Vehículo",
                            value = plate,
                            onValueChange = { plate = it },
                            placeholder = "Ej. HZ-WP-99",
                            icon = Icons.Default.DirectionsCar,
                            tag = "input_vehicle_plate"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tipo de Visita Chips
                Text("Tipo de Acceso", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    passTypes.forEach { type ->
                        FilterChip(
                            selected = selectedPassType == type,
                            onClick = { selectedPassType = type },
                            label = { Text(type, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldPrimary,
                                selectedLabelColor = NavyDark,
                                containerColor = NavyDark,
                                labelColor = TextMuted
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selectedPassType == type,
                                borderColor = Color(0xFF2A364F),
                                selectedBorderColor = GoldPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                AdminFormField(
                    label = "Observaciones de Garita / Motivo",
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = "Ej. Visita familiar autorizada por llamada telefónica",
                    icon = Icons.Default.Info,
                    tag = "input_entry_notes"
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Botones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = TextMuted)
                    }

                    val canSave = name.isNotBlank() && unit.isNotBlank() && host.isNotBlank()

                    Button(
                        onClick = {
                            if (canSave) {
                                onSave(name, unit, document, host, plate, selectedPassType, notes)
                            }
                        },
                        enabled = canSave,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            disabledContainerColor = GoldPrimary.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_save_firestore_visitor")
                    ) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = NavyDark)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Guardar Acceso", color = NavyDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Diálogo de Detalle y Auditoría Completa del Log en Firestore
 */
@Composable
fun AdminVisitorDetailDialog(
    log: FirestoreVisitorLog,
    condominiumId: String,
    onDismiss: () -> Unit,
    onRecordExit: () -> Unit
) {
    val context = LocalContext.current
    val isInside = log.status == "CHECKED_IN" || log.status == "VERIFICADO"
    val stayDuration = FirestoreVisitorTracker.calculateStayDuration(log.timestampMillis, log.checkOutMillis)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
                .testTag("admin_visitor_detail_dialog")
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Expediente de Auditoría", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Folio: #${log.folio}", color = GoldPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("btn_close_visitor_detail")) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Ubicación en Firestore
                Card(
                    colors = CardDefaults.cardColors(containerColor = NavyDark),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ruta en Firebase Firestore:", color = TextMuted, fontSize = 10.sp)
                            Text(
                                text = "/condominiums/$condominiumId/visitor_logs/${log.folio}",
                                color = CyanNeon,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Folio", log.folio)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Folio copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Detalle de Timestamps
                Text("REGISTROS CRONOLÓGICOS", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = NavyDark),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AuditDetailRow(
                            label = "Timestamp de Entrada:",
                            value = FirestoreVisitorTracker.formatTimestamp(log.timestamp, log.timestampMillis),
                            valueColor = SuccessGreen
                        )

                        AuditDetailRow(
                            label = "Timestamp de Salida:",
                            value = if (log.checkOutMillis != null) {
                                FirestoreVisitorTracker.formatTimestamp(log.checkOutTimestamp, log.checkOutMillis)
                            } else {
                                "🟢 ESTADÍA ACTIVA"
                            },
                            valueColor = if (log.checkOutMillis != null) CyanNeon else WarningOrange
                        )

                        AuditDetailRow(
                            label = "Permanencia Total:",
                            value = stayDuration,
                            valueColor = GoldPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Información del Visitante
                Text("DATOS DEL VISITANTE", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = NavyDark),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AuditDetailRow(label = "Nombre:", value = log.visitorName)
                        AuditDetailRow(label = "Documento / ID:", value = log.visitorDocument)
                        AuditDetailRow(label = "Unidad Autorizada:", value = log.authorizedUnitNumber, valueColor = GoldPrimary)
                        AuditDetailRow(label = "Residente Anfitrión:", value = log.hostResidentName)
                        AuditDetailRow(label = "Tipo de Pase:", value = log.passTypeLabel)
                        AuditDetailRow(label = "Placa del Vehículo:", value = log.vehiclePlate ?: "Sin vehículo (Peatonal)")
                        AuditDetailRow(label = "Código Pase:", value = log.passCode.ifBlank { "N/A" })
                        AuditDetailRow(label = "Estado Actual:", value = log.status, valueColor = if (isInside) SuccessGreen else CyanNeon)
                        AuditDetailRow(label = "Registrado Por:", value = log.guardName)
                        AuditDetailRow(label = "Notas de Garita:", value = log.guardNotes ?: "Sin observaciones")
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Botones de acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val jsonSummary = """
                                FOLIO: ${log.folio}
                                VISITANTE: ${log.visitorName}
                                DOCUMENTO: ${log.visitorDocument}
                                UNIDAD: ${log.authorizedUnitNumber}
                                ANFITRIÓN: ${log.hostResidentName}
                                ENTRADA: ${FirestoreVisitorTracker.formatTimestamp(log.timestamp, log.timestampMillis)}
                                SALIDA: ${if (log.checkOutMillis != null) FirestoreVisitorTracker.formatTimestamp(log.checkOutTimestamp, log.checkOutMillis) else "EN ESTADIA"}
                                PERMANENCIA: $stayDuration
                                PLACA: ${log.vehiclePlate ?: "N/A"}
                            """.trimIndent()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Registro Visitante", jsonSummary)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Resumen copiado para reporte", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_copy_visitor_log_json")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copiar Reporte", color = TextMuted, fontSize = 12.sp)
                    }

                    if (isInside) {
                        Button(
                            onClick = onRecordExit,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_detail_checkout_action")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = NavyDark, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Registrar Salida", color = NavyDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditDetailRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(0.45f))
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(0.55f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun AdminFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    tag: String
) {
    Column {
        Text(label, color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = TextMuted.copy(alpha = 0.6f), fontSize = 12.sp) },
            leadingIcon = { Icon(icon, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = NavyDark,
                unfocusedContainerColor = NavyDark,
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = Color(0xFF2A364F),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag)
        )
    }
}

@Composable
private fun AdminFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = GoldPrimary,
            selectedLabelColor = NavyDark,
            containerColor = NavySurface,
            labelColor = TextMuted
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color(0xFF2A364F),
            selectedBorderColor = GoldPrimary
        ),
        modifier = Modifier.testTag(tag)
    )
}

@Composable
private fun AdminKpiCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    tag: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF22304A)),
        modifier = modifier.testTag(tag)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = title,
                color = TextMuted,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
