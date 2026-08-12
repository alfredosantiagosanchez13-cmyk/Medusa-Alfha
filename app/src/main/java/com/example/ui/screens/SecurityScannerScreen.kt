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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scanner.GuestAccessLog
import com.example.scanner.PassStatus
import com.example.scanner.QrPassEntity
import com.example.scanner.SamplePassRepository
import com.example.scanner.SampleVisitorEntries
import com.example.scanner.VerificationResult
import com.example.scanner.VisitorEntry
import com.example.scanner.VisitorStatus
import com.example.ui.components.CameraScannerView
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

enum class ActiveScreenTab {
    SCANNER, GENERATOR, HISTORY
}

@Composable
fun SecurityScannerScreen() {
    val context = LocalContext.current

    var currentTab by remember { mutableStateOf(ActiveScreenTab.SCANNER) }
    var activeVerificationResult by remember { mutableStateOf<VerificationResult?>(null) }
    var showQrGeneratorDialog by remember { mutableStateOf(false) }
    var manualCodeInput by remember { mutableStateOf("") }

    val visitorEntries = remember {
        mutableStateListOf<VisitorEntry>().apply {
            addAll(SampleVisitorEntries.getSampleEntries())
        }
    }

    fun verifyPassCode(code: String) {
        triggerScanHaptic(context)
        val result = SamplePassRepository.verifyCode(code)
        activeVerificationResult = result
    }

    Scaffold(
        containerColor = NavyDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Guard Header & Station Status
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = NavySurface,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldPrimary.copy(alpha = 0.8f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(GoldPrimary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = NavyDark,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "MEDUSA ALFHA • SEGURIDAD",
                                    color = GoldPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Control de Accesos Garita",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Button(
                            onClick = { currentTab = ActiveScreenTab.GENERATOR },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("open_qr_generator_button")
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Generar QR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Station Navigation Tabs (Escáner, Generar QR, Historial)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NavyDark, RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            onClick = { currentTab = ActiveScreenTab.SCANNER },
                            shape = RoundedCornerShape(10.dp),
                            color = if (currentTab == ActiveScreenTab.SCANNER) GoldPrimary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tab_scanner_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = if (currentTab == ActiveScreenTab.SCANNER) NavyDark else Color.Gray,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Escáner",
                                    color = if (currentTab == ActiveScreenTab.SCANNER) NavyDark else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Surface(
                            onClick = { currentTab = ActiveScreenTab.GENERATOR },
                            shape = RoundedCornerShape(10.dp),
                            color = if (currentTab == ActiveScreenTab.GENERATOR) GoldPrimary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tab_generator_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = null,
                                    tint = if (currentTab == ActiveScreenTab.GENERATOR) NavyDark else Color.Gray,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Generar QR",
                                    color = if (currentTab == ActiveScreenTab.GENERATOR) NavyDark else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Surface(
                            onClick = { currentTab = ActiveScreenTab.HISTORY },
                            shape = RoundedCornerShape(10.dp),
                            color = if (currentTab == ActiveScreenTab.HISTORY) GoldPrimary else Color.Transparent,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tab_history_btn")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = null,
                                    tint = if (currentTab == ActiveScreenTab.HISTORY) NavyDark else Color.Gray,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Historial (${visitorEntries.size})",
                                    color = if (currentTab == ActiveScreenTab.HISTORY) NavyDark else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            when (currentTab) {
                ActiveScreenTab.SCANNER -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
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
                                    val index = visitorEntries.indexOfFirst { it.id == entry.id }
                                    if (index != -1) {
                                        visitorEntries[index] = entry.copy(
                                            status = newStatus,
                                            guardNotes = if (newStatus == VisitorStatus.VERIFIED) "Entrada verificada por agente" else "Entrada rechazada por agente"
                                        )
                                        val statusMsg = if (newStatus == VisitorStatus.VERIFIED) "Verificado" else "Denegado"
                                        Toast.makeText(context, "Estado actualizado: $statusMsg (${entry.visitorName})", Toast.LENGTH_SHORT).show()
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
                            val index = visitorEntries.indexOfFirst { it.id == entry.id }
                            if (index != -1) {
                                visitorEntries[index] = entry.copy(
                                    status = newStatus,
                                    guardNotes = if (newStatus == VisitorStatus.VERIFIED) "Entrada verificada por agente" else "Entrada rechazada por agente"
                                )
                                val statusMsg = if (newStatus == VisitorStatus.VERIFIED) "Verificado" else "Denegado"
                                Toast.makeText(context, "Estado actualizado: $statusMsg (${entry.visitorName})", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onClearHistory = {
                            visitorEntries.clear()
                        }
                    )
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
                        visitorEntries.add(
                            0,
                            VisitorEntry(
                                visitorName = pass.guestName,
                                visitorDocument = pass.guestDocument,
                                destinationHouse = pass.destinationHouse,
                                passCode = pass.passCode,
                                passTypeLabel = pass.passType.label,
                                vehiclePlate = pass.vehiclePlate,
                                status = VisitorStatus.VERIFIED,
                                guardNotes = "Ingreso Verificado en Garita Principal"
                            )
                        )
                        Toast.makeText(context, "Ingreso Aprobado y Registrado: ${pass.guestName}", Toast.LENGTH_LONG).show()
                    }
                    activeVerificationResult = null
                },
                onDenyEntry = {
                    val pass = result.qrPass
                    if (pass != null) {
                        visitorEntries.add(
                            0,
                            VisitorEntry(
                                visitorName = pass.guestName,
                                visitorDocument = pass.guestDocument,
                                destinationHouse = pass.destinationHouse,
                                passCode = pass.passCode,
                                passTypeLabel = pass.passType.label,
                                vehiclePlate = pass.vehiclePlate,
                                status = VisitorStatus.DENIED,
                                guardNotes = "Acceso Denegado en Garita Principal"
                            )
                        )
                        Toast.makeText(context, "Acceso Rechazado para ${pass.guestName}", Toast.LENGTH_SHORT).show()
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
