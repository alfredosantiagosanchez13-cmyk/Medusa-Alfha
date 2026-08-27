package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AssignmentLate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AlfhaPermission
import com.example.auth.AlfhaSecurityContext
import com.example.auth.RbacValidationOutcome
import com.example.data.audit.AuditLogEntity
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.notifications.SmartNotificationHub
import com.example.data.passes.QrPassRepository
import com.example.data.passes.toQrPassEntity
import com.example.data.visitor.VisitorCheckIn
import com.example.data.visitor.VisitorCheckInRepository
import com.example.data.incident.EmergencyLocationEngine
import com.example.ui.components.OperationalEmergencyMapView
import com.example.scanner.GuestAccessLog
import com.example.scanner.PassStatus
import com.example.scanner.QrPassEntity
import com.example.scanner.VerificationResult
import com.example.scanner.VisitorEntry
import com.example.scanner.VisitorStatus
import com.example.ui.components.AmenityBookingSection
import com.example.ui.components.BrandHeaderHeroBanner
import com.example.ui.components.BrandPhilosophyDialog
import com.example.ui.components.BrandSplashScreen
import com.example.ui.components.CameraScannerView
import com.example.ui.components.PanicAlertEvent
import com.example.ui.components.IncidentCenterHub
import com.example.ui.components.PanicFloorPlanCard
import com.example.ui.components.PulsingPanicButton
import com.example.ui.components.PassVerificationSheet
import com.example.ui.components.PackageCenterHub
import com.example.ui.components.AmenityBookingHub
import com.example.ui.components.ResidentManagementHub
import com.example.ui.components.VehicleAccessControlHub
import com.example.ui.components.QrGeneratorDialog
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.People
import com.example.ui.components.RecentVisitorEntriesList
import com.example.ui.components.VoiceIncidentLoggerComponent
import com.example.ui.components.triggerScanHaptic
import com.example.ui.theme.CyanNeon
import androidx.compose.material.icons.filled.FactCheck
import com.example.ui.components.FieldValidationChecklistHub
import com.example.ui.components.FirebaseCloudSyncDialog
import androidx.compose.material.icons.filled.CloudSync
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.utils.ResidentNotificationManager
import kotlinx.coroutines.launch

enum class ActiveScreenTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SCANNER("Escáner", Icons.Default.QrCodeScanner),
    VALIDATION("Validación", Icons.Default.FactCheck),
    VEHICLES("Vehículos", Icons.Default.DirectionsCar),
    RESIDENTS("Residentes", Icons.Default.People),
    MAINTENANCE("Mantenimiento", Icons.Default.Build),
    AMENITIES("Amenidades", Icons.Default.EventAvailable),
    PACKAGES("Paquetería", Icons.Default.Inventory2),
    INCIDENTS("Incidencias", Icons.Default.AssignmentLate),
    GENERATOR("Generar QR", Icons.Default.QrCode),
    SUPERVISION("Supervisión", Icons.Default.Shield),
    MASTER_ALPHA("Panel Maestro", Icons.Default.Schedule),
    HISTORY("Historial", Icons.Default.History),
    ANALYTICS("Analítica", Icons.Default.Analytics),
    AI_COPILOT("Copiloto AI", Icons.Default.AutoAwesome)
}

@Composable
fun SecurityScannerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val db = remember { AppDatabase.getDatabase(context) }
    val visitorCheckInDao = remember { db.visitorCheckInDao() }
    val qrPassDao = remember { db.qrPassDao() }
    val repository = remember { VisitorCheckInRepository(visitorCheckInDao) }
    val qrPassRepository = remember { QrPassRepository(qrPassDao) }

    val roomCheckIns by repository.allCheckIns.collectAsState(initial = emptyList())
    val roomPasses by qrPassRepository.allPassesFlow.collectAsState(initial = emptyList())

    // Auto-seed initial data into Room DB on first launch
    LaunchedEffect(Unit) {
        qrPassRepository.seedInitialPassesIfEmpty()
        repository.seedInitialCheckInsIfEmpty()
    }

    val visitorEntries = remember(roomCheckIns) {
        roomCheckIns.map { it.toVisitorEntry() }
    }

    var currentTab by remember { mutableStateOf(ActiveScreenTab.SCANNER) }
    var activeVerificationResult by remember { mutableStateOf<VerificationResult?>(null) }
    var showQrGeneratorDialog by remember { mutableStateOf(false) }
    var manualCodeInput by remember { mutableStateOf("") }
    var activePanicAlert by remember { mutableStateOf<PanicAlertEvent?>(null) }
    var showFirebaseCloudDialog by remember { mutableStateOf(false) }
    var showSplash by remember { mutableStateOf(true) }
    var showPhilosophyDialog by remember { mutableStateOf(false) }

    fun verifyPassCode(code: String) {
        triggerScanHaptic(context)
        scope.launch {
            val result = qrPassRepository.verifyPassCode(code)
            activeVerificationResult = result
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = NavyDark,
            bottomBar = {
                NavigationBar(
                    containerColor = NavySurface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.testTag("main_bottom_navigation")
                ) {
                    ActiveScreenTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentTab = tab },
                            icon = {
                                if (tab == ActiveScreenTab.HISTORY && roomCheckIns.isNotEmpty()) {
                                    BadgedBox(
                                        badge = {
                                            Badge(
                                                containerColor = GoldPrimary,
                                                contentColor = NavyDark
                                            ) {
                                                Text("${roomCheckIns.size}", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.label,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = tab.label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = if (tab == ActiveScreenTab.AI_COPILOT) CyanNeon else NavyDark,
                                selectedTextColor = if (tab == ActiveScreenTab.AI_COPILOT) CyanNeon else GoldPrimary,
                                indicatorColor = if (tab == ActiveScreenTab.AI_COPILOT) CyanNeon.copy(alpha = 0.2f) else GoldPrimary,
                                unselectedIconColor = Color.Gray,
                                unselectedTextColor = Color.Gray
                            ),
                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 14.dp)
            ) {
                Spacer(modifier = Modifier.height(6.dp))

                // Brand Hero Banner with Golden Crest, Slogan & Live Status
                BrandHeaderHeroBanner(
                    onOpenPhilosophyModal = { showPhilosophyDialog = true }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Security Action Bar (Panic Button & Battery Status)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = NavySurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(SuccessGreen, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Control Garita Principal • Fuente Única Room",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            com.example.ui.components.ConnectivityStatusPill(showPendingBadge = true)

                            IconButton(
                                onClick = { showFirebaseCloudDialog = true },
                                modifier = Modifier
                                    .size(28.dp)
                                    .background(GoldPrimary.copy(alpha = 0.15f), CircleShape)
                                    .testTag("firebase_cloud_sync_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudSync,
                                    contentDescription = "Firebase Cloud Sync",
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            PulsingPanicButton(
                                isActive = activePanicAlert != null,
                                onClick = {
                                    if (activePanicAlert == null) {
                                        activePanicAlert = com.example.ui.components.SampleCondoUnits.getDefaultPanicEvent()
                                        scope.launch {
                                            EmergencyLocationEngine.triggerEmergencyAlert(
                                                context = context,
                                                db = db,
                                                emergencyType = "PÁNICO S.O.S.",
                                                locationName = "Manzana A - Casa 104",
                                                reportedBy = "Guardia de Garita 1",
                                                reportedByRole = "GUARDIA",
                                                details = "Alerta de pánico activada desde la consola táctica de Caseta."
                                            )
                                        }
                                        Toast.makeText(context, "🚨 ALERTA DE PÁNICO ACTIVADA CON GEOLOCALIZACIÓN GPS", Toast.LENGTH_LONG).show()
                                    } else {
                                        activePanicAlert = null
                                        Toast.makeText(context, "Alerta de pánico desactivada", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            com.example.ui.components.BatteryIndicatorPill(showDetailedLabel = false)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

            when (currentTab) {
                ActiveScreenTab.SCANNER -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Interactive Operational Map for Emergencies and Geolocation
                        if (activePanicAlert != null) {
                            item {
                                OperationalEmergencyMapView(
                                    db = db,
                                    userRole = "GUARDIA",
                                    onEmergencyResolvedOrClosed = {
                                        activePanicAlert = null
                                    }
                                )
                            }
                        }

                        // Live CameraX & ZXing Scanner Component
                        item {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "ESCÁNER EN TIEMPO REAL",
                                        color = GoldPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "CameraX & Room DB",
                                        color = CyanNeon,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                CameraScannerView(
                                    onQrScanned = { scannedCode ->
                                        verifyPassCode(scannedCode)
                                    }
                                )
                            }
                        }

                        // Manual Code Lookup Input
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = NavyCard),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "BÚSQUEDA MANUAL DE CÓDIGO QR / FOLIO",
                                        color = TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = manualCodeInput,
                                            onValueChange = { manualCodeInput = it },
                                            placeholder = { Text("Ej: MED-20260821-1001", color = Color.Gray, fontSize = 13.sp) },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = GoldPrimary,
                                                unfocusedBorderColor = Color.Gray,
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("manual_code_input_field")
                                        )

                                        Button(
                                            onClick = {
                                                if (manualCodeInput.isNotBlank()) {
                                                    verifyPassCode(manualCodeInput.trim())
                                                } else {
                                                    Toast.makeText(context, "Ingrese un código para verificar", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.testTag("verify_manual_code_button")
                                        ) {
                                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Verificar", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Quick Scan Preset Bar from Room DB
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "PRUEBAS RÁPIDAS DE PASES ROOM (${roomPasses.size})",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(roomPasses) { passRoom ->
                                        val passEntity = passRoom.toQrPassEntity()
                                        QuickPassChip(
                                            pass = passEntity,
                                            onClick = { verifyPassCode(passRoom.passCode) }
                                        )
                                    }
                                    item {
                                        QuickPassChip(
                                            pass = QrPassEntity(
                                                passCode = "CODIGO-INVALIDO-X",
                                                guestName = "Desconocido",
                                                guestDocument = "00.000.000-0",
                                                destinationHouse = "Sin Destino",
                                                hostResidentName = "N/A",
                                                passType = com.example.scanner.PassType.VISITOR_SINGLE,
                                                validUntilMillis = 0L
                                            ),
                                            isInvalidPreset = true,
                                            onClick = { verifyPassCode("CODIGO-INVALIDO-X") }
                                        )
                                    }
                                }
                            }
                        }

                        // Recent Visitor Entries Summary Widget
                        item {
                            RecentVisitorEntriesList(
                                entries = visitorEntries,
                                onStatusChange = { entry, newStatus ->
                                    val idLong = entry.id.toLongOrNull()
                                    if (idLong != null) {
                                        scope.launch {
                                            if (newStatus == VisitorStatus.DEPARTED) {
                                                repository.registerCheckOut(idLong, notes = "Salida confirmada en garita")
                                            } else {
                                                repository.updateCheckInStatus(
                                                    id = idLong,
                                                    status = newStatus.name,
                                                    notes = if (newStatus == VisitorStatus.VERIFIED) "Entrada verificada en Room DB" else "Entrada denegada en Room DB"
                                                )
                                            }
                                        }
                                        if (newStatus == VisitorStatus.VERIFIED) {
                                            ResidentNotificationManager.notifyCustomVisitorEntry(
                                                context = context,
                                                guestName = entry.visitorName,
                                                destinationHouse = entry.destinationHouse,
                                                hostResidentName = entry.hostResidentName,
                                                passTypeLabel = entry.passTypeLabel,
                                                vehiclePlate = entry.vehiclePlate
                                            )
                                            scope.launch {
                                                SmartNotificationHub.notifyVisitorEntry(
                                                    context = context,
                                                    db = db,
                                                    guestName = entry.visitorName,
                                                    unitId = entry.destinationHouse,
                                                    hostResidentName = entry.hostResidentName,
                                                    passTypeLabel = entry.passTypeLabel,
                                                    vehiclePlate = entry.vehiclePlate,
                                                    passFolio = entry.folio
                                                )
                                            }
                                        }
                                        val statusMsg = if (newStatus == VisitorStatus.VERIFIED) "Verificado y Notificado al Residente" else if (newStatus == VisitorStatus.DEPARTED) "Salida Registrada" else "Denegado"
                                        Toast.makeText(context, "Room DB: $statusMsg (${entry.visitorName})", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }

                ActiveScreenTab.VALIDATION -> {
                    FieldValidationChecklistHub(
                        db = db
                    )
                }

                ActiveScreenTab.GENERATOR -> {
                    com.example.ui.screens.QrGeneratorScreen(
                        onSimulateScan = { passCode ->
                            currentTab = ActiveScreenTab.SCANNER
                            verifyPassCode(passCode)
                        }
                    )
                }

                ActiveScreenTab.HISTORY -> {
                    com.example.ui.screens.VisitorHistoryScreen(
                        entries = visitorEntries,
                        onStatusChange = { entry, newStatus ->
                            val idLong = entry.id.toLongOrNull()
                            if (idLong != null) {
                                scope.launch {
                                    if (newStatus == VisitorStatus.DEPARTED) {
                                        repository.registerCheckOut(idLong, notes = "Salida confirmada en garita con 1 toque")
                                        val duration = entry.durationStay ?: "Normal"
                                        ResidentNotificationManager.notifyVisitorDeparted(
                                            context = context,
                                            guestName = entry.visitorName,
                                            destinationHouse = entry.destinationHouse,
                                            hostResidentName = entry.hostResidentName,
                                            durationStay = duration
                                        )
                                        SmartNotificationHub.notifyVisitorExit(
                                            context = context,
                                            db = db,
                                            guestName = entry.visitorName,
                                            unitId = entry.destinationHouse,
                                            hostResidentName = entry.hostResidentName,
                                            durationStay = duration,
                                            checkInFolio = entry.folio
                                        )
                                        db.auditLogDao().insertAuditLog(
                                            AuditLogEntity(
                                                folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                                                operatorName = "Guardia Garita 1",
                                                actionType = "CHECK_OUT_ONE_TOUCH",
                                                location = "Garita Principal",
                                                targetEntity = "${entry.visitorName} (${entry.folio})",
                                                changeDetails = "Salida táctica de 1 toque. Permanencia: $duration",
                                                resultStatus = "EXITOSO"
                                            )
                                        )
                                    } else {
                                        repository.updateCheckInStatus(
                                            id = idLong,
                                            status = newStatus.name,
                                            notes = if (newStatus == VisitorStatus.VERIFIED) "Entrada verificada por agente" else "Entrada rechazada por agente"
                                        )
                                    }
                                }
                                if (newStatus == VisitorStatus.VERIFIED) {
                                    ResidentNotificationManager.notifyCustomVisitorEntry(
                                        context = context,
                                        guestName = entry.visitorName,
                                        destinationHouse = entry.destinationHouse,
                                        hostResidentName = entry.hostResidentName,
                                        passTypeLabel = entry.passTypeLabel,
                                        vehiclePlate = entry.vehiclePlate
                                    )
                                }
                                val statusMsg = if (newStatus == VisitorStatus.VERIFIED) "Verificado y Notificado al Residente" else if (newStatus == VisitorStatus.DEPARTED) "Salida Registrada y Notificado al Residente" else "Denegado"
                                Toast.makeText(context, "Estado actualizado: $statusMsg", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onClearHistory = {
                            scope.launch {
                                repository.deleteAllCheckIns()
                            }
                            Toast.makeText(context, "Historial de Room DB borrado", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                ActiveScreenTab.VEHICLES -> {
                    VehicleAccessControlHub(
                        db = db,
                        userRole = "CASETA",
                        showNewVehicleFab = true
                    )
                }

                ActiveScreenTab.RESIDENTS -> {
                    ResidentManagementHub(
                        db = db,
                        onNavigateToQrGenerator = { unit, resident ->
                            currentTab = ActiveScreenTab.GENERATOR
                        },
                        onNavigateToPackages = { unit, resident ->
                            currentTab = ActiveScreenTab.PACKAGES
                        },
                        onNavigateToBookings = { unit ->
                            currentTab = ActiveScreenTab.AMENITIES
                        }
                    )
                }

                ActiveScreenTab.MAINTENANCE -> {
                    com.example.ui.components.MaintenanceHub(
                        db = db,
                        showNewOrderFab = true,
                        userRole = "SUPERVISOR"
                    )
                }

                ActiveScreenTab.AMENITIES -> {
                    AmenityBookingHub(
                        db = db,
                        canManage = true
                    )
                }

                ActiveScreenTab.PACKAGES -> {
                    PackageCenterHub(
                        db = db,
                        canRegister = true,
                        canDeliver = true
                    )
                }

                ActiveScreenTab.INCIDENTS -> {
                    IncidentCenterHub(
                        db = db,
                        initialRoleFilter = "CASETA",
                        showRoleSelector = true
                    )
                }

                ActiveScreenTab.SUPERVISION -> {
                    com.example.ui.screens.TacticalSupervisionScreen()
                }

                ActiveScreenTab.MASTER_ALPHA -> {
                    com.example.ui.screens.MasterPanelAlphaScreen()
                }

                ActiveScreenTab.ANALYTICS -> {
                    com.example.ui.screens.AnalyticsSummaryScreen()
                }

                ActiveScreenTab.AI_COPILOT -> {
                    com.example.ui.screens.CondoAiCopilotScreen()
                }
            }
        }

        // Active Pass Verification Sheet Modal
        activeVerificationResult?.let { result ->
            PassVerificationSheet(
                result = result,
                onConfirmEntry = {
                    val pass = result.qrPass
                    if (pass != null) {
                        val unifiedFolio = if (pass.passCode.startsWith("MED-")) pass.passCode else AlphaCoreEngine.generateUniqueFolio("MED")
                        scope.launch {
                            val outcome = AlfhaSecurityContext.enforcePermission(
                                db = db,
                                permission = AlfhaPermission.CREAR,
                                actionName = "Validar Entrada Visitante QR $unifiedFolio",
                                targetResource = pass.guestName,
                                location = "Garita Principal"
                            )
                            when (outcome) {
                                is RbacValidationOutcome.Granted -> {
                                    qrPassRepository.markPassAsUsed(pass.passCode)
                                    repository.insertCheckIn(
                                        VisitorCheckIn(
                                            folio = unifiedFolio,
                                            visitorName = pass.guestName,
                                            visitorDocument = pass.guestDocument,
                                            destinationHouse = pass.destinationHouse,
                                            passCode = pass.passCode,
                                            passTypeLabel = pass.passType.label,
                                            vehiclePlate = pass.vehiclePlate,
                                            status = "CHECKED_IN",
                                            guardNotes = "Ingreso Verificado y Autorizado con Escáner CameraX",
                                            hostResidentName = pass.hostResidentName
                                        )
                                    )
                                    db.auditLogDao().insertAuditLog(
                                        AuditLogEntity(
                                            folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                                            operatorName = "${outcome.user.name} (${outcome.user.alfhaRole.shortName})",
                                            actionType = "CHECK_IN_AUTHORIZED",
                                            location = "Garita Principal",
                                            targetEntity = "${pass.guestName} -> ${pass.destinationHouse} ($unifiedFolio)",
                                            changeDetails = "Ingreso QR validado. Folio unificado: $unifiedFolio",
                                            resultStatus = "EXITOSO"
                                        )
                                    )

                                    // Encolar para Sincronización Persistente Offline (FASE 19)
                                    try {
                                        com.example.data.sync.OfflineSyncEngine.enqueueOperation(
                                            db = db,
                                            operationType = "CHECK_IN",
                                            targetFolio = unifiedFolio,
                                            targetModule = "VISITANTES",
                                            payloadJson = "{\"folio\":\"$unifiedFolio\",\"guest\":\"${pass.guestName}\",\"doc\":\"${pass.guestDocument}\",\"unit\":\"${pass.destinationHouse}\",\"status\":\"CHECKED_IN\"}",
                                            operatorName = outcome.user.name,
                                            operatorRole = outcome.user.alfhaRole.shortName,
                                            locationName = "Garita Principal",
                                            deviceGateId = "Caseta 1 - Terminal Principal"
                                        )
                                    } catch (e: Exception) {
                                        android.util.Log.e("SecurityScannerScreen", "Error enqueuing sync: ${e.message}")
                                    }

                                    // Send push notification automatically to the resident
                                    ResidentNotificationManager.notifyResidentVisitorCheckedIn(
                                        context = context,
                                        pass = pass,
                                        guardNotes = "Ingreso Verificado con Escáner CameraX en Garita Principal"
                                    )
                                    SmartNotificationHub.notifyVisitorEntry(
                                        context = context,
                                        db = db,
                                        guestName = pass.guestName,
                                        unitId = pass.destinationHouse,
                                        hostResidentName = pass.hostResidentName,
                                        passTypeLabel = pass.passType.label,
                                        vehiclePlate = pass.vehiclePlate,
                                        passFolio = unifiedFolio
                                    )

                                    Toast.makeText(context, "✅ Ingreso Guardado en Room [$unifiedFolio] y Notificación Enviada", Toast.LENGTH_LONG).show()
                                }
                                is RbacValidationOutcome.Denied -> {
                                    Toast.makeText(context, "🚫 ${outcome.reason}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    activeVerificationResult = null
                },
                onDenyEntry = {
                    val pass = result.qrPass
                    if (pass != null) {
                        val unifiedFolio = if (pass.passCode.startsWith("MED-")) pass.passCode else AlphaCoreEngine.generateUniqueFolio("MED")
                        scope.launch {
                            val outcome = AlfhaSecurityContext.enforcePermission(
                                db = db,
                                permission = AlfhaPermission.CREAR,
                                actionName = "Rechazar Entrada Visitante $unifiedFolio",
                                targetResource = pass.guestName,
                                location = "Garita Principal"
                            )
                            when (outcome) {
                                is RbacValidationOutcome.Granted -> {
                                    repository.insertCheckIn(
                                        VisitorCheckIn(
                                            folio = unifiedFolio,
                                            visitorName = pass.guestName,
                                            visitorDocument = pass.guestDocument,
                                            destinationHouse = pass.destinationHouse,
                                            passCode = pass.passCode,
                                            passTypeLabel = pass.passType.label,
                                            vehiclePlate = pass.vehiclePlate,
                                            status = "DENEGADO",
                                            guardNotes = "Acceso Denegado con Escáner CameraX",
                                            hostResidentName = pass.hostResidentName
                                        )
                                    )
                                    db.auditLogDao().insertAuditLog(
                                        AuditLogEntity(
                                            folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                                            operatorName = "${outcome.user.name} (${outcome.user.alfhaRole.shortName})",
                                            actionType = "CHECK_IN_DENIED",
                                            location = "Garita Principal",
                                            targetEntity = "${pass.guestName} -> ${pass.destinationHouse} ($unifiedFolio)",
                                            changeDetails = "Acceso rechazado por guardia en garita",
                                            resultStatus = "DENEGADO"
                                        )
                                    )
                                    Toast.makeText(context, "🚫 Rechazo Almacenado en Room DB: ${pass.guestName}", Toast.LENGTH_SHORT).show()
                                }
                                is RbacValidationOutcome.Denied -> {
                                    Toast.makeText(context, "🚫 ${outcome.reason}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                    activeVerificationResult = null
                },
                onDismiss = { activeVerificationResult = null }
            )
        }

        // QR Generator Dialog
        if (showQrGeneratorDialog) {
            QrGeneratorDialog(
                onSimulateScan = { code ->
                    verifyPassCode(code)
                },
                onDismiss = { showQrGeneratorDialog = false }
            )
        }
    }

    // Philosophy & Brand Mission Dialog (Tiempo = Familia)
    if (showPhilosophyDialog) {
        BrandPhilosophyDialog(
            onDismiss = { showPhilosophyDialog = false }
        )
    }

    // Firebase Cloud Sync & Authentication Dialog
    if (showFirebaseCloudDialog) {
        FirebaseCloudSyncDialog(
            db = db,
            onDismiss = { showFirebaseCloudDialog = false }
        )
    }

    // Startup Splash Screen Animation
    AnimatedVisibility(
        visible = showSplash,
        enter = fadeIn(androidx.compose.animation.core.tween(300)),
        exit = fadeOut(androidx.compose.animation.core.tween(500))
    ) {
        BrandSplashScreen(
            onDismiss = { showSplash = false }
        )
    }
    }
}

@Composable
private fun QuickPassChip(
    pass: QrPassEntity,
    isInvalidPreset: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = NavyCard,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isInvalidPreset) ErrorRed.copy(alpha = 0.6f) else GoldPrimary.copy(alpha = 0.4f)
        ),
        modifier = Modifier.testTag("quick_pass_chip_${pass.passCode}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = if (isInvalidPreset) ErrorRed else CyanNeon,
                modifier = Modifier.size(14.dp)
            )
            Column {
                Text(
                    text = pass.guestName,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = pass.passCode,
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}
