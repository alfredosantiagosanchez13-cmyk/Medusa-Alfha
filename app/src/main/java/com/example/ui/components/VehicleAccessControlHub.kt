package com.example.ui.components

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.auth.AlfhaSecurityContext
import com.example.data.booking.AppDatabase
import com.example.data.passes.QrPassRoomEntity
import com.example.data.resident.UnitEntity
import com.example.data.vehicle.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val NavyCardBorder = Color(0xFF334155)
private val AmberGold = Color(0xFFF59E0B)

enum class VehicleHubTab(val label: String, val icon: ImageVector) {
    ACTIVE_INSIDE("En Sitio", Icons.Default.DirectionsCar),
    REGISTRY("Padrón Vehicular", Icons.Default.Garage),
    ACCESS_LOGS("Bitácora Movimientos", Icons.Default.History),
    SCAN_TERMINAL("Terminal Garita", Icons.Default.QrCodeScanner),
    REPORT("Reporte Certificado", Icons.Default.Assessment)
}

/**
 * FASE 15: CONTROL VEHICULAR Y ACCESOS
 * 
 * Componente unificado e interactivo para la gestión de:
 * 1. Padrón vehicular de residentes (placa, marca, modelo, color, tag RFID, QR, estatus).
 * 2. Validación instantánea contra residentes y pases QR vigentes.
 * 3. Detección de vehículos no autorizados y generación de alertas inmediatas a Caseta y Panel Maestro ALFHA.
 * 4. Registro inmutable de entrada y salida con tiempo de estancia y cálculo de Tiempo Devuelto.
 * 5. Búsqueda rápida por placa e integración multirrol (Caseta, Residente, Administración, Supervisión, Mesa Directiva y Panel Maestro).
 */
@Composable
fun VehicleAccessControlHub(
    db: AppDatabase,
    modifier: Modifier = Modifier,
    unitFilter: String? = null,
    userRole: String = "ADMINISTRACION",
    showNewVehicleFab: Boolean = true,
    onStatsChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by AlfhaSecurityContext.currentUser.collectAsState()

    // Base de datos reactiva (Fuente Única de Verdad)
    val allVehicles by db.vehicleDao().getAllVehicles().collectAsState(initial = emptyList())
    val insideVehicles by db.vehicleDao().getVehiclesInside().collectAsState(initial = emptyList())
    val accessLogs by db.vehicleDao().getAllAccessLogs().collectAsState(initial = emptyList())
    val units by db.unitDao().getAllUnitsFlow().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableStateOf(if (unitFilter != null) VehicleHubTab.REGISTRY else VehicleHubTab.ACTIVE_INSIDE) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("TODOS") } // TODOS, ACTIVO, SUSPENDIDO, NO_AUTORIZADO

    // Diálogos de interacción
    var showRegisterVehicleDialog by remember { mutableStateOf(false) }
    var editingVehicle by remember { mutableStateOf<VehicleEntity?>(null) }
    var showScanTerminalDialog by remember { mutableStateOf(false) }
    var selectedLogForExit by remember { mutableStateOf<VehicleAccessLogEntity?>(null) }
    var selectedVehicleForDetail by remember { mutableStateOf<VehicleEntity?>(null) }
    var selectedLogForDetail by remember { mutableStateOf<VehicleAccessLogEntity?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }

    // Ticker para actualización en vivo de tiempos de permanencia
    var currentMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        VehicleAccessControlEngine.seedInitialVehiclesIfEmpty(context, db)
        while (true) {
            delay(30_000)
            currentMillis = System.currentTimeMillis()
        }
    }

    // Filtrados
    val filteredVehicles = remember(allVehicles, unitFilter, selectedStatusFilter, searchQuery) {
        allVehicles.filter { v ->
            if (unitFilter != null && v.unitId != unitFilter) return@filter false
            val statusMatch = when (selectedStatusFilter) {
                "TODOS" -> true
                else -> v.status.equals(selectedStatusFilter, ignoreCase = true)
            }
            val searchMatch = if (searchQuery.isBlank()) true else {
                v.plate.contains(searchQuery, ignoreCase = true) ||
                v.brand.contains(searchQuery, ignoreCase = true) ||
                v.model.contains(searchQuery, ignoreCase = true) ||
                v.ownerName.contains(searchQuery, ignoreCase = true) ||
                v.unitId.contains(searchQuery, ignoreCase = true) ||
                v.tagRfid.contains(searchQuery, ignoreCase = true)
            }
            statusMatch && searchMatch
        }
    }

    val filteredInsideVehicles = remember(insideVehicles, unitFilter, searchQuery) {
        insideVehicles.filter { log ->
            if (unitFilter != null && log.unitId != unitFilter) return@filter false
            if (searchQuery.isBlank()) true else {
                log.plate.contains(searchQuery, ignoreCase = true) ||
                log.unitId.contains(searchQuery, ignoreCase = true) ||
                log.driverOrOwnerName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val filteredLogs = remember(accessLogs, unitFilter, searchQuery) {
        accessLogs.filter { log ->
            if (unitFilter != null && log.unitId != unitFilter) return@filter false
            if (searchQuery.isBlank()) true else {
                log.plate.contains(searchQuery, ignoreCase = true) ||
                log.folio.contains(searchQuery, ignoreCase = true) ||
                log.unitId.contains(searchQuery, ignoreCase = true) ||
                log.driverOrOwnerName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val startOfDay = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val entriesTodayCount = remember(accessLogs, startOfDay) {
        accessLogs.count { it.entryTimestampMillis >= startOfDay }
    }
    val exitsTodayCount = remember(accessLogs, startOfDay) {
        accessLogs.count { it.exitTimestampMillis != null && it.exitTimestampMillis >= startOfDay }
    }
    val unauthorizedCount = remember(accessLogs) {
        accessLogs.count { !it.isAuthorized || it.accessCategory == "VEHICULO_NO_AUTORIZADO" }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("vehicle_access_control_hub")
    ) {
        // ==========================================
        // 1. SCORECARD SUPERIOR (KPIs OPERATIVOS)
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Card 1: Vehículos en Sitio
            Surface(
                modifier = Modifier.weight(1f),
                color = NavyCard,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (insideVehicles.isNotEmpty()) SuccessGreen.copy(alpha = 0.6f) else NavyCardBorder)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("EN SITIO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen)
                        )
                    }
                    Text(
                        text = "${insideVehicles.size}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text("Actualmente dentro", fontSize = 9.sp, color = TextMuted)
                }
            }

            // Card 2: Movimientos Hoy
            Surface(
                modifier = Modifier.weight(1f),
                color = NavyCard,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, NavyCardBorder)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("HOY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyanNeon)
                    Text(
                        text = "$entriesTodayCount / $exitsTodayCount",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text("Entradas / Salidas", fontSize = 9.sp, color = TextMuted)
                }
            }

            // Card 3: Padrón Registrado
            Surface(
                modifier = Modifier.weight(1f),
                color = NavyCard,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, NavyCardBorder)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("PADRÓN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                    Text(
                        text = "${allVehicles.size}",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Text("Vehículos registrados", fontSize = 9.sp, color = TextMuted)
                }
            }

            // Card 4: Alertas / No Autorizados
            Surface(
                modifier = Modifier.weight(1f),
                color = if (unauthorizedCount > 0) ErrorRed.copy(alpha = 0.15f) else NavyCard,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (unauthorizedCount > 0) ErrorRed.copy(alpha = 0.6f) else NavyCardBorder)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("ALERTAS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (unauthorizedCount > 0) ErrorRed else TextMuted)
                    Text(
                        text = "$unauthorizedCount",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = if (unauthorizedCount > 0) ErrorRed else Color.White
                    )
                    Text("No autorizados", fontSize = 9.sp, color = TextMuted)
                }
            }
        }

        // ==========================================
        // 2. ACCIONES RÁPIDAS Y SELECTOR DE PESTAÑAS
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            VehicleHubTab.values().forEach { tab ->
                val isSel = selectedTab == tab
                Surface(
                    onClick = { selectedTab = tab },
                    color = if (isSel) CyanNeon.copy(alpha = 0.2f) else NavySurface,
                    border = BorderStroke(1.dp, if (isSel) CyanNeon else NavyCardBorder),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (isSel) CyanNeon else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tab.label,
                            fontSize = 10.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSel) Color.White else TextMuted,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // ==========================================
        // 3. BARRA DE BÚSQUEDA Y BOTONES DE ACCIÓN
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar por placa, unidad, titular...", fontSize = 12.sp, color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar", tint = CyanNeon, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .testTag("vehicle_search_field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanNeon,
                    unfocusedBorderColor = NavyCardBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = NavySurface,
                    unfocusedContainerColor = NavySurface
                ),
                shape = RoundedCornerShape(8.dp)
            )

            // Botón Terminal Rápida Garita
            Button(
                onClick = { showScanTerminalDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .height(50.dp)
                    .testTag("btn_terminal_garita")
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear", tint = NavyDark, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Validar", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NavyDark)
            }

            // Botón Alta Vehículo (si tiene permiso)
            if (showNewVehicleFab) {
                Button(
                    onClick = {
                        editingVehicle = null
                        showRegisterVehicleDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("btn_add_vehicle")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar", tint = NavyDark, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Vehículo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NavyDark)
                }
            }
        }

        // ==========================================
        // 4. CONTENIDO PRINCIPAL POR PESTAÑA
        // ==========================================
        when (selectedTab) {
            VehicleHubTab.ACTIVE_INSIDE -> {
                VehiclesInsideView(
                    vehiclesInside = filteredInsideVehicles,
                    onRegisterExit = { log ->
                        selectedLogForExit = log
                    },
                    onViewDetail = { log ->
                        selectedLogForDetail = log
                    }
                )
            }

            VehicleHubTab.REGISTRY -> {
                VehicleRegistryView(
                    vehicles = filteredVehicles,
                    selectedStatus = selectedStatusFilter,
                    onStatusChange = { selectedStatusFilter = it },
                    onEditVehicle = { vehicle ->
                        editingVehicle = vehicle
                        showRegisterVehicleDialog = true
                    },
                    onDeleteVehicle = { vehicle ->
                        scope.launch {
                            VehicleAccessControlEngine.deleteVehicle(db, vehicle.plate, currentUser.name)
                            Toast.makeText(context, "Vehículo ${vehicle.plate} eliminado del padrón", Toast.LENGTH_SHORT).show()
                            onStatsChanged()
                        }
                    },
                    onViewDetail = { vehicle ->
                        selectedVehicleForDetail = vehicle
                    }
                )
            }

            VehicleHubTab.ACCESS_LOGS -> {
                VehicleAccessLogsView(
                    logs = filteredLogs,
                    onViewDetail = { log ->
                        selectedLogForDetail = log
                    }
                )
            }

            VehicleHubTab.SCAN_TERMINAL -> {
                VehicleGateTerminalView(
                    db = db,
                    onVehicleProcessed = {
                        onStatsChanged()
                    }
                )
            }

            VehicleHubTab.REPORT -> {
                VehicleReportView(
                    db = db
                )
            }
        }
    }

    // ==========================================
    // 5. DIÁLOGOS MODALES
    // ==========================================

    // Diálogo Alta / Edición de Vehículo
    if (showRegisterVehicleDialog) {
        DialogRegisterVehicle(
            vehicle = editingVehicle,
            units = units,
            preselectedUnit = unitFilter,
            onDismiss = { showRegisterVehicleDialog = false },
            onSave = { newVehicle ->
                scope.launch {
                    VehicleAccessControlEngine.saveVehicle(db, newVehicle, currentUser.name)
                    Toast.makeText(context, "Vehículo ${newVehicle.plate} guardado en padrón", Toast.LENGTH_SHORT).show()
                    showRegisterVehicleDialog = false
                    onStatsChanged()
                }
            }
        )
    }

    // Diálogo Terminal de Validación Garita
    if (showScanTerminalDialog) {
        DialogScanTerminal(
            db = db,
            onDismiss = { showScanTerminalDialog = false },
            onAccessGranted = {
                showScanTerminalDialog = false
                onStatsChanged()
            }
        )
    }

    // Diálogo Registrar Salida 1-Toque
    selectedLogForExit?.let { log ->
        AlertDialog(
            onDismissRequest = { selectedLogForExit = null },
            containerColor = NavyCard,
            shape = RoundedCornerShape(12.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null, tint = CyanNeon)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Registrar Salida Vehicular", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("¿Confirmar salida de la unidad vehicular?", fontSize = 13.sp, color = TextMuted)
                    Surface(
                        color = NavySurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Placa: ${log.plate}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CyanNeon)
                            Text("Unidad / Destino: ${log.unitId}", fontSize = 12.sp, color = Color.White)
                            Text("Conductor / Titular: ${log.driverOrOwnerName}", fontSize = 12.sp, color = TextMuted)
                            Text("Permanencia: ${log.stayDurationFormatted}", fontSize = 12.sp, color = GoldPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val currentLog = log
                        selectedLogForExit = null
                        scope.launch {
                            VehicleAccessControlEngine.registerVehicleExit(
                                context = context,
                                db = db,
                                plateOrFolio = currentLog.folio,
                                exitLane = "CARRIL_SALIDA_1",
                                operatorName = currentUser.name,
                                operatorRole = currentUser.role,
                                notes = "Salida confirmada desde terminal rápida"
                            )
                            Toast.makeText(context, "Salida registrada: ${currentLog.plate}", Toast.LENGTH_SHORT).show()
                            onStatsChanged()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon)
                ) {
                    Text("Confirmar Salida", color = NavyDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedLogForExit = null }) {
                    Text("Cancelar", color = TextMuted)
                }
            }
        )
    }

    // Diálogo Detalle Vehículo Padrón
    selectedVehicleForDetail?.let { vehicle ->
        DialogVehicleDetail(
            vehicle = vehicle,
            db = db,
            onDismiss = { selectedVehicleForDetail = null },
            onEdit = {
                selectedVehicleForDetail = null
                editingVehicle = vehicle
                showRegisterVehicleDialog = true
            }
        )
    }

    // Diálogo Detalle Movimiento Bitácora
    selectedLogForDetail?.let { log ->
        DialogLogDetail(
            log = log,
            onDismiss = { selectedLogForDetail = null }
        )
    }
}

// ====================================================================
// SUB-VISTA 1: VEHÍCULOS DENTRO DEL CONDOMINIO (TIEMPO REAL)
// ====================================================================
@Composable
private fun VehiclesInsideView(
    vehiclesInside: List<VehicleAccessLogEntity>,
    onRegisterExit: (VehicleAccessLogEntity) -> Unit,
    onViewDetail: (VehicleAccessLogEntity) -> Unit
) {
    if (vehiclesInside.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text("No hay vehículos registrados en sitio", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Todas las salidas han sido procesadas correctamente", fontSize = 12.sp, color = TextMuted)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(vehiclesInside, key = { it.folio }) { log ->
                Surface(
                    color = NavyCard,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (!log.isAuthorized) ErrorRed.copy(alpha = 0.8f) else NavyCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onViewDetail(log) }
                        .testTag("inside_vehicle_${log.plate}")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = CyanNeon.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = log.plate,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        color = CyanNeon,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${log.brand} ${log.model} ${if (log.color.isNotBlank()) "(${log.color})" else ""}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }

                            // Badge de Categoría
                            val badgeColor = when (log.accessCategory) {
                                "RESIDENTE_AUTORIZADO" -> SuccessGreen
                                "VISITANTE_PASE_QR" -> CyanNeon
                                "VEHICULO_NO_AUTORIZADO" -> ErrorRed
                                else -> AmberGold
                            }
                            Surface(
                                color = badgeColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = log.accessCategory.replace("_", " "),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = badgeColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Home, contentDescription = null, tint = TextMuted, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = log.unitId, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(Icons.Default.Person, contentDescription = null, tint = TextMuted, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = log.driverOrOwnerName, fontSize = 12.sp, color = TextMuted)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "Entrada: ${log.formattedEntryTime}", fontSize = 11.sp, color = TextMuted)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "Estancia: ${log.stayDurationFormatted}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                                }
                            }

                            Button(
                                onClick = { onRegisterExit(log) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon.copy(alpha = 0.2f)),
                                border = BorderStroke(1.dp, CyanNeon),
                                shape = RoundedCornerShape(6.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("btn_exit_${log.plate}")
                            ) {
                                Icon(Icons.Default.ExitToApp, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Salida", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyanNeon)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================
// SUB-VISTA 2: PADRÓN VEHICULAR (CATÁLOGO RESIDENCIAL)
// ====================================================================
@Composable
private fun VehicleRegistryView(
    vehicles: List<VehicleEntity>,
    selectedStatus: String,
    onStatusChange: (String) -> Unit,
    onEditVehicle: (VehicleEntity) -> Unit,
    onDeleteVehicle: (VehicleEntity) -> Unit,
    onViewDetail: (VehicleEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Filtros de Estatus
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val filters = listOf("TODOS", "ACTIVO", "SUSPENDIDO", "NO_AUTORIZADO")
            items(filters) { f ->
                val isSel = selectedStatus == f
                Surface(
                    onClick = { onStatusChange(f) },
                    color = if (isSel) GoldPrimary.copy(alpha = 0.2f) else NavySurface,
                    border = BorderStroke(1.dp, if (isSel) GoldPrimary else NavyCardBorder),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = f,
                        fontSize = 10.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSel) GoldPrimary else TextMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (vehicles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No hay vehículos que coincidan con los filtros", fontSize = 13.sp, color = TextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vehicles, key = { it.plate }) { vehicle ->
                    Surface(
                        color = NavyCard,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (vehicle.status == "SUSPENDIDO") ErrorRed.copy(alpha = 0.5f) else NavyCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onViewDetail(vehicle) }
                            .testTag("vehicle_card_${vehicle.plate}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = GoldPrimary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = vehicle.plate,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GoldPrimary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${vehicle.brand} ${vehicle.model}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                val statusColor = when (vehicle.status) {
                                    "ACTIVO" -> SuccessGreen
                                    "SUSPENDIDO" -> ErrorRed
                                    else -> AmberGold
                                }
                                Surface(
                                    color = statusColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = vehicle.status,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Unidad: ${vehicle.unitId} (${vehicle.relationship})", fontSize = 11.sp, color = Color.White)
                                    Text("Titular: ${vehicle.ownerName.ifBlank { "Sin titular asignado" }}", fontSize = 11.sp, color = TextMuted)
                                    if (vehicle.tagRfid.isNotBlank()) {
                                        Text("RFID: ${vehicle.tagRfid}", fontSize = 10.sp, color = CyanNeon, fontFamily = FontFamily.Monospace)
                                    }
                                }

                                Row {
                                    IconButton(
                                        onClick = { onEditVehicle(vehicle) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Editar", tint = TextMuted, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(
                                        onClick = { onDeleteVehicle(vehicle) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = ErrorRed.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================
// SUB-VISTA 3: BITÁCORA DE MOVIMIENTOS Y ACCESOS
// ====================================================================
@Composable
private fun VehicleAccessLogsView(
    logs: List<VehicleAccessLogEntity>,
    onViewDetail: (VehicleAccessLogEntity) -> Unit
) {
    if (logs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No hay registros en la bitácora vehicular", fontSize = 13.sp, color = TextMuted)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(logs, key = { it.folio }) { log ->
                Surface(
                    color = NavyCard,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (!log.isAuthorized) ErrorRed.copy(alpha = 0.6f) else NavyCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onViewDetail(log) }
                        .testTag("log_${log.folio}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = log.plate,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (log.isAuthorized) CyanNeon else ErrorRed,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${log.brand} ${log.model}",
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${log.unitId} • ${log.driverOrOwnerName}",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                            Text(
                                text = "Entrada: ${log.formattedEntryTime} ${log.formattedExitTime?.let { "• Salida: $it" } ?: "• (En sitio)"}",
                                fontSize = 10.sp,
                                color = if (log.isCurrentlyInside) SuccessGreen else TextMuted
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            val statusColor = if (log.isCurrentlyInside) SuccessGreen else CyanNeon
                            Surface(
                                color = statusColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (log.isCurrentlyInside) "DENTRO" else "FINALIZADO",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = log.stayDurationFormatted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================
// SUB-VISTA 4: TERMINAL TÁCTICA DE CASETA (SIMULADOR DE VALIDACIÓN)
// ====================================================================
@Composable
private fun VehicleGateTerminalView(
    db: AppDatabase,
    onVehicleProcessed: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by AlfhaSecurityContext.currentUser.collectAsState()

    var inputPlate by remember { mutableStateOf("") }
    var inputTagRfid by remember { mutableStateOf("") }
    var inputQrCode by remember { mutableStateOf("") }
    var selectedLane by remember { mutableStateOf("CARRIL_RESIDENTES_1") }
    var guardNotes by remember { mutableStateOf("") }

    var validationResult by remember { mutableStateOf<VehicleValidationResult?>(null) }
    var isValidating by remember { mutableStateOf(false) }
    var lastProcessedFolio by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                color = NavyCard,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = CyanNeon)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Terminal Garita: Verificación Instantánea", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text("Ingresa placa, tag RFID o código QR para validación 1-Toque contra Room.", fontSize = 11.sp, color = TextMuted)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Campo de Placa
                    OutlinedTextField(
                        value = inputPlate,
                        onValueChange = {
                            inputPlate = it.uppercase(Locale.getDefault())
                            validationResult = null
                        },
                        label = { Text("Placa Vehicular (e.g. ABC-1234)", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = CyanNeon) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("terminal_input_plate"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = NavyCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Acceso Rápido Simulado (Tags / QR de prueba)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                inputPlate = "ABC-1234"
                                inputTagRfid = "TAG-A104-1"
                                scope.launch {
                                    isValidating = true
                                    validationResult = VehicleAccessControlEngine.validateVehicle(db, "ABC-1234", "TAG-A104-1")
                                    isValidating = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NavySurface),
                            border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Simular Residente (ABC-1234)", fontSize = 10.sp, color = SuccessGreen, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                inputPlate = "XYZ-0000"
                                scope.launch {
                                    isValidating = true
                                    validationResult = VehicleAccessControlEngine.validateVehicle(db, "XYZ-0000")
                                    isValidating = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NavySurface),
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Simular No Autorizado", fontSize = 10.sp, color = ErrorRed, maxLines = 1)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Botón Validar Placa
                    Button(
                        onClick = {
                            if (inputPlate.isBlank() && inputTagRfid.isBlank() && inputQrCode.isBlank()) {
                                Toast.makeText(context, "Ingresa al menos una placa, tag o QR", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            scope.launch {
                                isValidating = true
                                validationResult = VehicleAccessControlEngine.validateVehicle(db, inputPlate, inputTagRfid, inputQrCode)
                                isValidating = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_validate_plate")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = NavyDark)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Validar Autorización", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NavyDark)
                    }
                }
            }
        }

        // Resultado de la Validación
        validationResult?.let { result ->
            item {
                Surface(
                    color = NavyCard,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.5.dp,
                        when (result) {
                            is VehicleValidationResult.AuthorizedResident,
                            is VehicleValidationResult.AuthorizedVisitorPass -> SuccessGreen
                            is VehicleValidationResult.DeniedSuspended -> AmberGold
                            is VehicleValidationResult.Unauthorized -> ErrorRed
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        when (result) {
                            is VehicleValidationResult.AuthorizedResident -> {
                                val v = result.vehicle
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("ACCESO AUTORIZADO - RESIDENTE", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Vehículo: ${v.plate} • ${v.brand} ${v.model} (${v.color})", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Unidad: ${v.unitId} • Titular: ${v.ownerName}", fontSize = 12.sp, color = TextMuted)
                                if (v.tagRfid.isNotBlank()) {
                                    Text("Tag RFID: ${v.tagRfid}", fontSize = 11.sp, color = CyanNeon)
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        scope.launch {
                                            val log = VehicleAccessControlEngine.registerVehicleEntry(
                                                context = context,
                                                db = db,
                                                validationResult = result,
                                                gateLane = selectedLane,
                                                operatorName = currentUser.name,
                                                operatorRole = currentUser.role,
                                                guardNotes = guardNotes
                                            )
                                            lastProcessedFolio = log.folio
                                            validationResult = null
                                            inputPlate = ""
                                            Toast.makeText(context, "Entrada autorizada: ${v.plate}", Toast.LENGTH_SHORT).show()
                                            onVehicleProcessed()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("btn_authorize_entry")
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, tint = NavyDark)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Abrir Portón y Registrar Entrada (1-Toque)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NavyDark)
                                }
                            }

                            is VehicleValidationResult.AuthorizedVisitorPass -> {
                                val p = result.qrPass
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("ACCESO AUTORIZADO - PASE QR", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CyanNeon)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Visitante: ${p.guestName} • Placa: ${result.plate}", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Destino: ${p.destinationHouse} • Anfitrión: ${p.hostResidentName}", fontSize = 12.sp, color = TextMuted)
                                Text("Pase Folio: ${p.passCode}", fontSize = 11.sp, color = CyanNeon)

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        scope.launch {
                                            val log = VehicleAccessControlEngine.registerVehicleEntry(
                                                context = context,
                                                db = db,
                                                validationResult = result,
                                                gateLane = selectedLane,
                                                operatorName = currentUser.name,
                                                operatorRole = currentUser.role,
                                                guardNotes = guardNotes
                                            )
                                            lastProcessedFolio = log.folio
                                            validationResult = null
                                            inputPlate = ""
                                            Toast.makeText(context, "Entrada autorizada de visita: ${result.plate}", Toast.LENGTH_SHORT).show()
                                            onVehicleProcessed()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .testTag("btn_authorize_visitor_entry")
                                ) {
                                    Icon(Icons.Default.LockOpen, contentDescription = null, tint = NavyDark)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Abrir Portón Visitas (1-Toque)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NavyDark)
                                }
                            }

                            is VehicleValidationResult.DeniedSuspended -> {
                                val v = result.vehicle
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = AmberGold, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("ACCESO RESTRINGIDO - VEHÍCULO SUSPENDIDO", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AmberGold)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(result.reason, fontSize = 12.sp, color = Color.White)
                                Text("Unidad: ${v.unitId} • Placa: ${v.plate}", fontSize = 11.sp, color = TextMuted)

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                VehicleAccessControlEngine.registerVehicleEntry(
                                                    context = context,
                                                    db = db,
                                                    validationResult = result,
                                                    gateLane = selectedLane,
                                                    operatorName = currentUser.name,
                                                    operatorRole = currentUser.role,
                                                    allowEmergencyEntry = false
                                                )
                                                validationResult = null
                                                inputPlate = ""
                                                Toast.makeText(context, "Acceso retenido y alerta despachada", Toast.LENGTH_SHORT).show()
                                                onVehicleProcessed()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Denegar y Notificar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                VehicleAccessControlEngine.registerVehicleEntry(
                                                    context = context,
                                                    db = db,
                                                    validationResult = result,
                                                    gateLane = selectedLane,
                                                    operatorName = currentUser.name,
                                                    operatorRole = currentUser.role,
                                                    guardNotes = "Acceso excepcional permitido por caseta.",
                                                    allowEmergencyEntry = true
                                                )
                                                validationResult = null
                                                inputPlate = ""
                                                Toast.makeText(context, "Acceso excepcional registrado", Toast.LENGTH_SHORT).show()
                                                onVehicleProcessed()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Acceso Excepcional", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NavyDark)
                                    }
                                }
                            }

                            is VehicleValidationResult.Unauthorized -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("🚨 VEHÍCULO NO AUTORIZADO", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Placa: ${result.plate} • Sin registro en padrón ni pase QR.", fontSize = 12.sp, color = Color.White)
                                Text("Al presionar 'Denegar o Generar Alerta', se emitirá notificación inmediata a Caseta y Panel Maestro.", fontSize = 11.sp, color = TextMuted)

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                VehicleAccessControlEngine.registerVehicleEntry(
                                                    context = context,
                                                    db = db,
                                                    validationResult = result,
                                                    gateLane = selectedLane,
                                                    operatorName = currentUser.name,
                                                    operatorRole = currentUser.role,
                                                    allowEmergencyEntry = false
                                                )
                                                validationResult = null
                                                inputPlate = ""
                                                Toast.makeText(context, "Alerta despachada a Caseta y Panel Maestro", Toast.LENGTH_LONG).show()
                                                onVehicleProcessed()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Denegar y Lanzar Alerta", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                VehicleAccessControlEngine.registerVehicleEntry(
                                                    context = context,
                                                    db = db,
                                                    validationResult = result,
                                                    gateLane = selectedLane,
                                                    operatorName = currentUser.name,
                                                    operatorRole = currentUser.role,
                                                    guardNotes = "Ingreso manual de emergencia / proveedor.",
                                                    allowEmergencyEntry = true
                                                )
                                                validationResult = null
                                                inputPlate = ""
                                                Toast.makeText(context, "Ingreso de emergencia registrado", Toast.LENGTH_SHORT).show()
                                                onVehicleProcessed()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Acceso Emergencia", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NavyDark)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================
// SUB-VISTA 5: REPORTE CERTIFICADO
// ====================================================================
@Composable
private fun VehicleReportView(
    db: AppDatabase
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reportText by remember { mutableStateOf("Generando reporte certificado...") }

    LaunchedEffect(Unit) {
        reportText = VehicleAccessControlEngine.generateVehicularAuditReport(db)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            color = NavyCard,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(modifier = Modifier.padding(14.dp)) {
                item {
                    Text(
                        text = reportText,
                        fontSize = 11.sp,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Button(
            onClick = {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, reportText)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, "Compartir Reporte Certificado Vehicular")
                context.startActivity(shareIntent)
            },
            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
        ) {
            Icon(Icons.Default.Share, contentDescription = null, tint = NavyDark)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Exportar y Compartir Reporte Certificado", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NavyDark)
        }
    }
}

// ====================================================================
// DIÁLOGO: ALTA / EDICIÓN DE VEHÍCULO
// ====================================================================
@Composable
private fun DialogRegisterVehicle(
    vehicle: VehicleEntity?,
    units: List<UnitEntity>,
    preselectedUnit: String?,
    onDismiss: () -> Unit,
    onSave: (VehicleEntity) -> Unit
) {
    var plate by remember { mutableStateOf(vehicle?.plate ?: "") }
    var brand by remember { mutableStateOf(vehicle?.brand ?: "") }
    var model by remember { mutableStateOf(vehicle?.model ?: "") }
    var color by remember { mutableStateOf(vehicle?.color ?: "") }
    var vehicleType by remember { mutableStateOf(vehicle?.vehicleType ?: "SUV") }
    var unitId by remember { mutableStateOf(vehicle?.unitId ?: preselectedUnit ?: units.firstOrNull()?.unitId ?: "Casa 101") }
    var ownerName by remember { mutableStateOf(vehicle?.ownerName ?: "") }
    var relationship by remember { mutableStateOf(vehicle?.relationship ?: "PROPIETARIO") }
    var tagRfid by remember { mutableStateOf(vehicle?.tagRfid ?: "") }
    var qrAccessCode by remember { mutableStateOf(vehicle?.qrAccessCode ?: "") }
    var status by remember { mutableStateOf(vehicle?.status ?: "ACTIVO") }
    var notes by remember { mutableStateOf(vehicle?.notes ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = NavyCard,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = if (vehicle == null) "Registrar Vehículo en Padrón" else "Editar Vehículo",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldPrimary
                    )
                    Text("Asociación con unidad y residente en Room SQLite.", fontSize = 11.sp, color = TextMuted)
                }

                item {
                    OutlinedTextField(
                        value = plate,
                        onValueChange = { plate = it.uppercase(Locale.getDefault()) },
                        label = { Text("Placa *", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_vehicle_plate"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = NavyCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = brand,
                            onValueChange = { brand = it },
                            label = { Text("Marca (e.g. Toyota)", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = NavyCardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it },
                            label = { Text("Modelo (e.g. RAV4)", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = NavyCardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = color,
                            onValueChange = { color = it },
                            label = { Text("Color (e.g. Blanco)", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = NavyCardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        OutlinedTextField(
                            value = unitId,
                            onValueChange = { unitId = it },
                            label = { Text("Unidad / Casa *", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = NavyCardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        label = { Text("Titular / Residente Responsable", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = NavyCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = tagRfid,
                            onValueChange = { tagRfid = it },
                            label = { Text("Tag RFID (Opcional)", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = NavyCardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        OutlinedTextField(
                            value = status,
                            onValueChange = { status = it.uppercase(Locale.getDefault()) },
                            label = { Text("Estatus (ACTIVO/SUSPENDIDO)", fontSize = 10.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = NavyCardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Observaciones / Cajón Asignado", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = NavyCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                item {
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
                                if (plate.isBlank() || unitId.isBlank()) return@Button
                                val newVehicle = VehicleEntity(
                                    plate = plate,
                                    brand = brand.ifBlank { "Sin marca" },
                                    model = model.ifBlank { "Sin modelo" },
                                    color = color.ifBlank { "Sin color" },
                                    vehicleType = vehicleType,
                                    unitId = unitId,
                                    ownerName = ownerName.ifBlank { "Residente de $unitId" },
                                    relationship = relationship,
                                    tagRfid = tagRfid,
                                    qrAccessCode = qrAccessCode,
                                    status = status.ifBlank { "ACTIVO" },
                                    notes = notes
                                )
                                onSave(newVehicle)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            modifier = Modifier.testTag("btn_save_vehicle")
                        ) {
                            Text("Guardar Vehículo", color = NavyDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================
// DIÁLOGO: MODAL ESCANEO / TERMINAL GARITA
// ====================================================================
@Composable
private fun DialogScanTerminal(
    db: AppDatabase,
    onDismiss: () -> Unit,
    onAccessGranted: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = NavyCard,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, CyanNeon),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Terminal Rápida Garita", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CyanNeon)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                VehicleGateTerminalView(
                    db = db,
                    onVehicleProcessed = onAccessGranted
                )
            }
        }
    }
}

// ====================================================================
// DIÁLOGO: DETALLE VEHÍCULO (360°)
// ====================================================================
@Composable
private fun DialogVehicleDetail(
    vehicle: VehicleEntity,
    db: AppDatabase,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    val logs by db.vehicleDao().getAccessLogsByPlate(vehicle.plate).collectAsState(initial = emptyList())

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = NavyCard,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Expediente Vehicular 360°", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                        }
                    }
                }

                item {
                    Surface(
                        color = NavySurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Placa: ${vehicle.plate}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = CyanNeon, fontFamily = FontFamily.Monospace)
                            Text("Vehículo: ${vehicle.brand} ${vehicle.model} (${vehicle.color})", fontSize = 13.sp, color = Color.White)
                            Text("Unidad Asignada: ${vehicle.unitId}", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Titular: ${vehicle.ownerName} (${vehicle.relationship})", fontSize = 12.sp, color = TextMuted)
                            if (vehicle.tagRfid.isNotBlank()) {
                                Text("Tag RFID: ${vehicle.tagRfid}", fontSize = 11.sp, color = GoldPrimary)
                            }
                            Text("Estatus: ${vehicle.status}", fontSize = 12.sp, color = if (vehicle.status == "ACTIVO") SuccessGreen else ErrorRed, fontWeight = FontWeight.Bold)
                            if (vehicle.notes.isNotBlank()) {
                                Text("Notas: ${vehicle.notes}", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                    }
                }

                item {
                    Text("Historial de Accesos Registrados (${logs.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                if (logs.isEmpty()) {
                    item {
                        Text("No se registran movimientos para esta placa.", fontSize = 11.sp, color = TextMuted)
                    }
                } else {
                    items(logs.take(5)) { log ->
                        Surface(
                            color = NavySurface,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Entrada: ${log.formattedEntryTime} • Carril: ${log.gateLane}", fontSize = 10.sp, color = Color.White)
                                Text("Salida: ${log.formattedExitTime ?: "En sitio"} • Estancia: ${log.stayDurationFormatted}", fontSize = 10.sp, color = GoldPrimary)
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = onEdit,
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = NavyDark)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Editar", color = NavyDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ====================================================================
// DIÁLOGO: DETALLE DE MOVIMIENTO EN BITÁCORA
// ====================================================================
@Composable
private fun DialogLogDetail(
    log: VehicleAccessLogEntity,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = NavyCard,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Detalle de Acceso Vehicular", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CyanNeon)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                Surface(
                    color = NavySurface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Folio: ${log.folio}", fontSize = 11.sp, color = CyanNeon, fontFamily = FontFamily.Monospace)
                        Text("Placa: ${log.plate} (${log.brand} ${log.model})", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Unidad / Destino: ${log.unitId}", fontSize = 12.sp, color = Color.White)
                        Text("Conductor / Titular: ${log.driverOrOwnerName}", fontSize = 12.sp, color = TextMuted)
                        Text("Categoría: ${log.accessCategory}", fontSize = 11.sp, color = GoldPrimary)
                        Text("Método: ${log.identificationMethod}", fontSize = 11.sp, color = TextMuted)
                        Text("Carril: ${log.gateLane}", fontSize = 11.sp, color = TextMuted)
                        Text("Hora Entrada: ${log.formattedEntryTime}", fontSize = 11.sp, color = SuccessGreen)
                        log.formattedExitTime?.let {
                            Text("Hora Salida: $it", fontSize = 11.sp, color = CyanNeon)
                        }
                        Text("Permanencia: ${log.stayDurationFormatted}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                        Text("Operador: ${log.operatorName} (${log.operatorRole})", fontSize = 10.sp, color = TextMuted)
                        if (log.guardNotes.isNotBlank()) {
                            Text("Notas: ${log.guardNotes}", fontSize = 11.sp, color = TextMuted)
                        }
                        Text("Hash SHA-256: ${log.hashIntegrity}", fontSize = 9.sp, color = TextMuted, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}
