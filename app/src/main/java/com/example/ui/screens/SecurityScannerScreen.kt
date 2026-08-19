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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
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
import com.example.data.booking.AppDatabase
import com.example.data.visitor.VisitorCheckIn
import com.example.data.visitor.VisitorCheckInRepository
import com.example.scanner.GuestAccessLog
import com.example.scanner.PassStatus
import com.example.scanner.QrPassEntity
import com.example.scanner.SamplePassRepository
import com.example.scanner.SampleVisitorEntries
import com.example.scanner.VerificationResult
import com.example.scanner.VisitorEntry
import com.example.scanner.VisitorStatus
import kotlinx.coroutines.launch
import com.example.ui.components.CameraScannerView
import com.example.ui.components.PanicAlertEvent
import com.example.ui.components.PanicFloorPlanCard
import com.example.ui.components.PulsingPanicButton
import com.example.ui.components.PassVerificationSheet
import com.example.ui.components.QrGeneratorDialog
import com.example.ui.components.RecentVisitorEntriesList
import com.example.ui.components.triggerScanHaptic
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox

enum class ActiveScreenTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SCANNER("Escáner", Icons.Default.QrCodeScanner),
    GENERATOR("Generar QR", Icons.Default.QrCode),
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
    val repository = remember { VisitorCheckInRepository(visitorCheckInDao) }

    val roomCheckIns by repository.allCheckIns.collectAsState(initial = emptyList())

    // Auto-seed initial sample data into Room DB on first launch
    LaunchedEffect(Unit) {
        if (visitorCheckInDao.getCheckInCount() == 0) {
            SampleVisitorEntries.getSampleEntries().forEach { sample ->
                visitorCheckInDao.insertCheckIn(
                    VisitorCheckIn(
                        visitorName = sample.visitorName,
                        visitorDocument = sample.visitorDocument,
                        destinationHouse = sample.destinationHouse,
                        passCode = sample.passCode,
                        passTypeLabel = sample.passTypeLabel,
                        vehiclePlate = sample.vehiclePlate,
                        status = sample.status.name,
                        timestampMillis = sample.timestampMillis,
                        guardNotes = sample.guardNotes
                    )
                )
            }
        }
    }

    val visitorEntries = remember(roomCheckIns) {
        roomCheckIns.map { it.toVisitorEntry() }
    }

    var currentTab by remember { mutableStateOf(ActiveScreenTab.SCANNER) }
    var activeVerificationResult by remember { mutableStateOf<VerificationResult?>(null) }
    var showQrGeneratorDialog by remember { mutableStateOf(false) }
    var manualCodeInput by remember { mutableStateOf("") }
    var activePanicAlert by remember { mutableStateOf<PanicAlertEvent?>(null) }

    fun verifyPassCode(code: String) {
        triggerScanHaptic(context)
        val result = SamplePassRepository.verifyCode(code)
        activeVerificationResult = result
    }

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
            Spacer(modifier = Modifier.height(8.dp))

            // Guard Header & Station Status
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = NavySurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(GoldPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = NavyDark,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "MEDUSA ALFHA • SEGURIDAD",
                                color = GoldPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "Control de Accesos Garita",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PulsingPanicButton(
                            isActive = activePanicAlert != null,
                            onClick = {
                                if (activePanicAlert == null) {
                                    activePanicAlert = com.example.ui.components.SampleCondoUnits.getDefaultPanicEvent()
                                    Toast.makeText(context, "🚨 ALERTA DE PÁNICO ACTIVADA EN MANZANA A - CASA 104", Toast.LENGTH_LONG).show()
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

            Spacer(modifier = Modifier.height(10.dp))

            when (currentTab) {
                ActiveScreenTab.SCANNER -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Interactive Floor-Plan Map for Panic Alerts (displayed with high priority when triggered)
                        if (activePanicAlert != null) {
                            item {
                                PanicFloorPlanCard(
                                    activeAlert = activePanicAlert,
                                    onSimulatePanicTrigger = { unit ->
                                        activePanicAlert = PanicAlertEvent(
                                            id = "PANIC-${System.currentTimeMillis() % 10000}",
                                            unit = unit,
                                            alertType = "🚨 ALERTA DE PÁNICO RESIDENCIAL",
                                            severity = "CRÍTICO",
                                            timestamp = "Reciente"
                                        )
                                        Toast.makeText(context, "🚨 Alerta disparada en ${unit.unitId} (${unit.residentName})", Toast.LENGTH_LONG).show()
                                    },
                                    onResolveAlert = {
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
                                        text = "CameraX & ZXing",
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
                                        text = "BÚSQUEDA MANUAL DE CÓDIGO QR",
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
                                            placeholder = { Text("Ej: MEDUSA-PASS-101", color = Color.Gray, fontSize = 13.sp) },
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

                        // Quick Scan Preset Bar
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "PRUEBAS RÁPIDAS DE PASES REGISTRADOS",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(SamplePassRepository.getAllKnownPasses()) { pass ->
                                        QuickPassChip(
                                            pass = pass,
                                            onClick = { verifyPassCode(pass.passCode) }
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
                                            repository.updateCheckInStatus(
                                                id = idLong,
                                                status = newStatus.name,
                                                notes = if (newStatus == VisitorStatus.VERIFIED) "Entrada verificada en Room DB por agente" else "Entrada rechazada en Room DB por agente"
                                            )
                                        }
                                        val statusMsg = if (newStatus == VisitorStatus.VERIFIED) "Verificado" else "Denegado"
                                        Toast.makeText(context, "Room DB Actualizado: $statusMsg (${entry.visitorName})", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
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
                                    repository.updateCheckInStatus(
                                        id = idLong,
                                        status = newStatus.name,
                                        notes = if (newStatus == VisitorStatus.VERIFIED) "Entrada verificada por agente" else "Entrada rechazada por agente"
                                    )
                                }
                                val statusMsg = if (newStatus == VisitorStatus.VERIFIED) "Verificado" else "Denegado"
                                Toast.makeText(context, "Estado actualizado en Room DB: $statusMsg", Toast.LENGTH_SHORT).show()
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
                        SamplePassRepository.markPassAsUsed(pass.passCode)
                        scope.launch {
                            repository.insertCheckIn(
                                VisitorCheckIn(
                                    visitorName = pass.guestName,
                                    visitorDocument = pass.guestDocument,
                                    destinationHouse = pass.destinationHouse,
                                    passCode = pass.passCode,
                                    passTypeLabel = pass.passType.label,
                                    vehiclePlate = pass.vehiclePlate,
                                    status = "VERIFICADO",
                                    guardNotes = "Ingreso Verificado con Escáner CameraX"
                                )
                            )
                        }
                        Toast.makeText(context, "✅ Ingreso Guardado en Base de Datos Room: ${pass.guestName}", Toast.LENGTH_LONG).show()
                    }
                    activeVerificationResult = null
                },
                onDenyEntry = {
                    val pass = result.qrPass
                    if (pass != null) {
                        scope.launch {
                            repository.insertCheckIn(
                                VisitorCheckIn(
                                    visitorName = pass.guestName,
                                    visitorDocument = pass.guestDocument,
                                    destinationHouse = pass.destinationHouse,
                                    passCode = pass.passCode,
                                    passTypeLabel = pass.passType.label,
                                    vehiclePlate = pass.vehiclePlate,
                                    status = "DENEGADO",
                                    guardNotes = "Acceso Denegado con Escáner CameraX"
                                )
                            )
                        }
                        Toast.makeText(context, "🚫 Rechazo Almacenado en Room DB: ${pass.guestName}", Toast.LENGTH_SHORT).show()
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
