package com.example.medusaalfha.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.medusaalfha.data.model.CondominiumDirectory
import com.example.medusaalfha.data.model.HouseAlertLevel
import com.example.medusaalfha.data.model.PackageDeliveryItem
import com.example.medusaalfha.ui.theme.*
import com.example.medusaalfha.ui.viewmodel.GuardStationUiState
import com.example.medusaalfha.ui.viewmodel.GuardStationViewModel
import com.example.medusaalfha.ui.viewmodel.ResidentConfirmationState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardStationScreen(
    viewModel: GuardStationViewModel,
    onOpenCameraScanner: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.systemNotice, uiState.attendanceNotice) {
        val notice = uiState.systemNotice ?: uiState.attendanceNotice
        if (notice != null) {
            snackbarHostState.showSnackbar(notice)
            viewModel.clearSystemNotice()
        }
    }

    Scaffold(
        containerColor = CyberBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Identidad de Marca Fija: TIEMPO = FAMILIA
            BrandMottoFooter()
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
        ) {
            // BLOQUE 1: Encabezado de Control de Fatiga y Relevo Biométrico
            item {
                FatigueControlAndBiometricHeader(
                    uiState = uiState,
                    onOpenShiftHandover = { viewModel.openShiftHandoverModal() },
                    onToggleSimulateFatigue = { viewModel.toggleSimulatedCriticalFatigue() }
                )
            }

            // Módulo 1: Entrada de Datos y Asistencia
            item {
                GuardAttendanceSection(
                    uiState = uiState,
                    onSetAway = { viewModel.setGuardAtRestroom(true) },
                    onSetReturn = { viewModel.setGuardAtRestroom(false) }
                )
            }

            // Módulo 2: Visitantes y Proveedores (Entrada con Notificación Cruzada)
            item {
                CrossNotificationVisitorSection(
                    uiState = uiState,
                    onClassificationChanged = { viewModel.onAccessClassificationChanged(it) },
                    onObservationsChanged = { viewModel.onGeneralObservationsChanged(it) },
                    onToggleIdPhoto = { viewModel.toggleIdPhoto() },
                    onToggleVehiclePhoto = { viewModel.toggleVehiclePhoto() },
                    onToggleHouse = { viewModel.toggleHouseSelection(it) },
                    onDispatchNotification = { viewModel.dispatchCrossNotificationToResident(context) },
                    onResetConfirmation = { viewModel.resetConfirmationState() }
                )
            }

            // Módulo 3: Paquetería Inteligente (AI Visión + QR Contraentrega)
            item {
                SmartPackageSection(
                    uiState = uiState,
                    onScanOcr = { viewModel.simulateAiVisionOcrScan() },
                    onHouseChanged = { viewModel.onDetectedHouseChanged(it) },
                    onCarrierChanged = { viewModel.onPackageCarrierChanged(it) },
                    onTrackingChanged = { viewModel.onPackageTrackingInputChanged(it) },
                    onRegisterPackage = { viewModel.registerAndTokenizePackage(context) },
                    onStartPickupScan = { viewModel.setPickupScannerActive(true) },
                    onSimulateQrPickup = { token -> viewModel.handlePickupQrScanned(token) }
                )
            }

            // BLOQUE 4: Bitácora de Rondines con Mapa Georreferenciado & Asistente de IA por Voz
            item {
                PatrolLogVoiceAssistantSection(
                    uiState = uiState,
                    onReportTextChanged = { viewModel.onPatrolReportTextChanged(it) },
                    onTriggerVoice = { rawVoice -> viewModel.triggerVoiceDictationAssistant(rawVoice) },
                    onValidateCheckpoint = { viewModel.validateCheckpointLocation(it) },
                    onSelectCheckpoint = { viewModel.selectCheckpoint(it) },
                    onResetCircuit = { viewModel.resetPatrolCircuit() },
                    onSaveReport = { viewModel.savePatrolReport() },
                    onTriggerSync = { viewModel.triggerManualSync() }
                )
            }
        }
    }

    // Modal de Cambio de Turno / Relevo Biométrico (BLOQUE 1)
    if (uiState.isShiftHandoverOpen) {
        ShiftHandoverBottomSheet(
            onDismiss = { viewModel.closeShiftHandoverModal() },
            isPhotoCaptured = uiState.isIncomingPhotoCaptured,
            isBiometricVerified = uiState.isBiometricSignatureVerified,
            onCapturePhoto = { viewModel.captureIncomingGuardPhoto() },
            onVerifyBiometric = { viewModel.verifyBiometricSignature() },
            onCommitShift = { name, rank -> viewModel.commitShiftChange(name, rank) }
        )
    }

    // Modal de Escaneo de QR para Recolección de Paquetes
    if (uiState.isPickupScannerActive) {
        QrScannerBottomSheet(
            onDismiss = { viewModel.setPickupScannerActive(false) },
            onQrScanned = { token -> viewModel.handlePickupQrScanned(token) },
            scannedPass = null,
            onConfirmPass = {},
            onDismissPass = {}
        )
    }
}

// -------------------------------------------------------------
// BLOQUE 1: Encabezado de Control de Fatiga y Relevo Biométrico
// -------------------------------------------------------------
@Composable
private fun FatigueControlAndBiometricHeader(
    uiState: GuardStationUiState,
    onOpenShiftHandover: () -> Unit,
    onToggleSimulateFatigue: () -> Unit
) {
    val attendance = uiState.attendance
    val isCritical = attendance.isCriticalFatigue

    // Parpadeo reactivo suave si está en estado crítico (>= 24 horas)
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "fatigue_pulse")
    val borderAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "border_alpha"
    )

    val cardBorderColor = if (isCritical) {
        AlertRedFatigue.copy(alpha = borderAlpha)
    } else {
        NeonBlue.copy(alpha = 0.6f)
    }

    val timerColor = if (isCritical) AlertRedFatigue else StatusNeonGreen

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = CyberSurface,
        border = BorderStroke(if (isCritical) 2.dp else 1.dp, cardBorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("block1_fatigue_control_header")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Fila Principal: Perfil del Guardia + Timer + Botón de Cambio de Turno
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Perfil en Turno: Avatar circular con anillo dorado brillante + Nombre y Rango
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(GoldMetallic.copy(alpha = 0.4f), Color(0xFF1A1A24))
                                )
                            )
                            .border(2.5.dp, GoldMetallic, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Fotografía Guardia Activo",
                            tint = GoldMetallic,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = attendance.guardName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = GoldMetallic.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = attendance.guardId,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = GoldMetallic,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = attendance.guardRank,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NeonBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Contador de Turno Activo (HH:MM)
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .clickable(onClick = onToggleSimulateFatigue) // Facilita probar el disparador >= 24h
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = "TURNO ACTIVO",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCritical) AlertRedFatigue else CyberTextMuted,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = attendance.elapsedFormatted,
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = timerColor
                    )

                    Text(
                        text = if (isCritical) "CRÍTICO (>=24h)" else "NORMAL (<24h)",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCritical) AlertRedFatigue else StatusNeonGreen
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Acción [🔄 Cambio de Turno] en formato Cápsula
                Button(
                    onClick = onOpenShiftHandover,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCritical) AlertRedFatigue else CyberSurfaceElevated,
                        contentColor = if (isCritical) Color.White else GoldMetallic
                    ),
                    border = BorderStroke(1.2.dp, if (isCritical) AlertRedFatigue else GoldMetallic),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("shift_handover_capsule_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Cambio de Turno",
                        modifier = Modifier.size(16.dp),
                        tint = if (isCritical) Color.White else GoldMetallic
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Cambio de Turno",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // Banner Crítico de Advertencia (Parpadeante ante >= 24 horas)
            if (isCritical) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AlertRedFatigue.copy(alpha = 0.2f),
                    border = BorderStroke(1.5.dp, AlertRedFatigue.copy(alpha = borderAlpha)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("critical_fatigue_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AlertRedFatigue,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "⚠ ADVERTENCIA: GUARDIA LABORANDO MÁS DE SU TURNO",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = AlertRedFatigue,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Relevo obligatorio inmediato. Notificación síncrona activa en administración y residentes.",
                                fontSize = 10.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            CircuitDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // Sub-barra de estatus del recinto
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Caseta de Vigilancia Principal · Terminal Alfa",
                        fontSize = 11.sp,
                        color = CyberTextMuted
                    )
                }

                Text(
                    text = "Toca el contador para simular prueba >24h",
                    fontSize = 9.sp,
                    color = CyberTextMuted.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Modal de Relevo Biométrico y Validación Facial (BLOQUE 1)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShiftHandoverBottomSheet(
    onDismiss: () -> Unit,
    isPhotoCaptured: Boolean,
    isBiometricVerified: Boolean,
    onCapturePhoto: () -> Unit,
    onVerifyBiometric: () -> Unit,
    onCommitShift: (String, String) -> Unit
) {
    var incomingName by remember { mutableStateOf("Oficial Roberto Méndez") }
    var incomingRank by remember { mutableStateOf("Segundo Oficial · Turno Bravo") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = CyberBackground,
        modifier = Modifier.testTag("shift_handover_modal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(GoldMetallic.copy(alpha = 0.2f))
                            .border(1.dp, GoldMetallic, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.SyncLock, contentDescription = null, tint = GoldMetallic, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "PROTOCOLO DE RELEVO BIOMÉTRICO",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = GoldMetallic
                        )
                        Text(
                            text = "Bloqueo preventivo de terminal hasta validar elemento entrante",
                            fontSize = 10.sp,
                            color = NeonBlue
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            CircuitDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Datos del Oficial Entrante
            OutlinedTextField(
                value = incomingName,
                onValueChange = { incomingName = it },
                label = { Text("Nombre y Apellidos del Guardia Entrante *", color = GoldMetallic) },
                colors = cyberTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = incomingRank,
                onValueChange = { incomingRank = it },
                label = { Text("Rango / Cargo Operativo *", color = CyberTextMuted) },
                colors = cyberTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Dos Pasos Biométricos Requeridos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Paso 1: Validación Facial por Cámara
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isPhotoCaptured) Color(0xFF0C2417) else CyberSurfaceElevated,
                    border = BorderStroke(1.dp, if (isPhotoCaptured) StatusNeonGreen else CyberBorderCircuit),
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp)
                        .clickable(onClick = onCapturePhoto)
                        .testTag("handover_camera_step")
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isPhotoCaptured) Icons.Default.CheckCircle else Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = if (isPhotoCaptured) StatusNeonGreen else NeonBlue,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Foto Rostro",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Text(
                            text = if (isPhotoCaptured) "Verificado (Cámara)" else "Activar Cámara",
                            fontSize = 10.sp,
                            color = if (isPhotoCaptured) StatusNeonGreen else GoldMuted
                        )
                    }
                }

                // Paso 2: Firma Biométrica / Huella
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isBiometricVerified) Color(0xFF0C2417) else CyberSurfaceElevated,
                    border = BorderStroke(1.dp, if (isBiometricVerified) StatusNeonGreen else CyberBorderCircuit),
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp)
                        .clickable(onClick = onVerifyBiometric)
                        .testTag("handover_fingerprint_step")
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isBiometricVerified) Icons.Default.VerifiedUser else Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = if (isBiometricVerified) StatusNeonGreen else GoldMetallic,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Firma Biométrica",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Text(
                            text = if (isBiometricVerified) "Validada (Sensor)" else "Capturar Huella",
                            fontSize = 10.sp,
                            color = if (isBiometricVerified) StatusNeonGreen else GoldMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Botón de Confirmación y Reinicio de Contador
            val canCommit = isPhotoCaptured && isBiometricVerified && incomingName.isNotBlank()
            Button(
                onClick = { onCommitShift(incomingName, incomingRank) },
                enabled = canCommit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("commit_shift_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldMetallic,
                    contentColor = Color.Black,
                    disabledContainerColor = CyberSurfaceElevated,
                    disabledContentColor = CyberTextMuted
                )
            ) {
                Icon(Icons.Default.LockReset, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Confirmar Relevo y Reiniciar Contador (00:00)",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// -------------------------------------------------------------
// 1. Módulo de Entrada de Datos y Asistencia
// -------------------------------------------------------------
@Composable
private fun GuardAttendanceSection(
    uiState: GuardStationUiState,
    onSetAway: () -> Unit,
    onSetReturn: () -> Unit
) {
    CyberCardContainer(
        title = "1. MÓDULO DE ASISTENCIA & CONTROL DE CASETA",
        subtitle = "Gestión de presencia física y registro inmediato de relevos"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Acciones Rápidas (Row)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Botón Naranja de Atención: Guardia en WC
                Button(
                    onClick = onSetAway,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("guard_wc_away_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.attendance.isAwayAtRestroom) StatusOrangeAlert else StatusOrangeAlert.copy(alpha = 0.85f),
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, if (uiState.attendance.isAwayAtRestroom) Color.White else Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.Default.Wc,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Guardia en WC",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                // Botón Verde de Éxito: Regreso de WC
                Button(
                    onClick = onSetReturn,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("guard_wc_return_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!uiState.attendance.isAwayAtRestroom) StatusNeonGreen else StatusNeonGreen.copy(alpha = 0.4f),
                        contentColor = Color.Black
                    ),
                    border = BorderStroke(1.dp, if (!uiState.attendance.isAwayAtRestroom) NeonBlue else Color.Transparent)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Regreso de WC",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            // Registro de último evento
            if (uiState.attendance.logs.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyberSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = NeonBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Último registro: ${uiState.attendance.logs.first()}",
                            fontSize = 11.sp,
                            color = CyberTextMuted
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// 2. Módulo Avanzado de Visitantes y Proveedores (Notificación Cruzada)
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrossNotificationVisitorSection(
    uiState: GuardStationUiState,
    onClassificationChanged: (String) -> Unit,
    onObservationsChanged: (String) -> Unit,
    onToggleIdPhoto: () -> Unit,
    onToggleVehiclePhoto: () -> Unit,
    onToggleHouse: (String) -> Unit,
    onDispatchNotification: () -> Unit,
    onResetConfirmation: () -> Unit
) {
    val classifications = listOf(
        "Visita General",
        "Proveedor / Contratista",
        "Servicio de Paquetería",
        "Mudanza / Carga Pesada",
        "Visita Frecuente / Familiar",
        "Emergencia Médica / Oficial"
    )

    var dropdownExpanded by remember { mutableStateOf(false) }

    CyberCardContainer(
        title = "2. VISITANTES & PROVEEDORES · NOTIFICACIÓN CRUZADA",
        subtitle = "Control de acceso con doble evidencia fotográfica y despacho satelital"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // 2.1 Selector Desplegable ExposedDropdownMenuBox
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded && !uiState.isFormControlsLocked,
                onExpandedChange = { if (!uiState.isFormControlsLocked) dropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = uiState.selectedAccessClassification,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Clasificación de Acceso", color = GoldMetallic) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    colors = cyberTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .testTag("access_classification_dropdown"),
                    enabled = !uiState.isFormControlsLocked
                )

                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.background(CyberSurfaceElevated)
                ) {
                    classifications.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(text = option, color = Color.White, fontSize = 13.sp) },
                            onClick = {
                                onClassificationChanged(option)
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // 2.2 Campo de Observaciones Libres
            OutlinedTextField(
                value = uiState.generalObservations,
                onValueChange = onObservationsChanged,
                label = { Text("Observaciones de Caseta / Motivo", color = CyberTextMuted) },
                placeholder = { Text("Ej. Técnico de Telmex con gafete #44012; solicita acceso a postes...", color = Color.DarkGray) },
                colors = cyberTextFieldColors(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .testTag("visitor_observations_field"),
                enabled = !uiState.isFormControlsLocked
            )

            // 2.3 Doble Evidencia Fotográfica (Fila con dos Cards oscuras simétricas)
            Text(
                text = "Doble Evidencia Fotográfica Requerida:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = GoldMetallic
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card 1: Foto Identificación / INE
                PhotoEvidenceCard(
                    title = "Foto Identificación / INE",
                    hasPhoto = uiState.hasIdPhoto,
                    onClick = onToggleIdPhoto,
                    modifier = Modifier.weight(1f).testTag("photo_card_ine"),
                    enabled = !uiState.isFormControlsLocked
                )

                // Card 2: Foto Vehículo / Placa
                PhotoEvidenceCard(
                    title = "Foto Vehículo / Placa",
                    hasPhoto = uiState.hasVehiclePhoto,
                    onClick = onToggleVehiclePhoto,
                    modifier = Modifier.weight(1f).testTag("photo_card_vehicle"),
                    enabled = !uiState.isFormControlsLocked
                )
            }

            // 2.4 Selector Multicasas Dinámico (Scroll horizontal de FilterChips)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Viviendas Destino (Selección Múltiple):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldMetallic
                    )
                    Text(
                        text = "${uiState.selectedHouses.size} seleccionada(s)",
                        fontSize = 11.sp,
                        color = NeonBlue
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(CondominiumDirectory.activeHouses) { houseInfo ->
                        val isSelected = uiState.selectedHouses.contains(houseInfo.houseId)
                        FilterChip(
                            selected = isSelected,
                            onClick = { if (!uiState.isFormControlsLocked) onToggleHouse(houseInfo.houseId) },
                            label = {
                                Text(
                                    text = houseInfo.houseId,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = CyberSurfaceElevated,
                                labelColor = CyberTextPrimary,
                                selectedContainerColor = GoldMetallic,
                                selectedLabelColor = Color.Black
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = CyberBorderCircuit,
                                selectedBorderColor = GoldMetallic
                            ),
                            enabled = !uiState.isFormControlsLocked
                        )
                    }
                }
            }

            // 2.5 Botón de Despacho y Estado de Bloqueo / Espera
            when (uiState.confirmationState) {
                ResidentConfirmationState.IDLE -> {
                    Button(
                        onClick = onDispatchNotification,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("send_notification_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonBlue,
                            contentColor = Color.Black
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Enviar Notificación al Residente",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }

                ResidentConfirmationState.WAITING_CONFIRMATION -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CyberSurfaceElevated,
                        border = BorderStroke(1.5.dp, GoldMetallic),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("waiting_confirmation_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = GoldMetallic,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Esperando Confirmación del Residente... [Aceptar / Rechazar]",
                                fontWeight = FontWeight.Bold,
                                color = GoldMetallic,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Controles bloqueados por protocolo de seguridad activa",
                                fontSize = 11.sp,
                                color = CyberTextMuted
                            )
                        }
                    }
                }

                ResidentConfirmationState.ACCEPTED -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StatusNeonGreen.copy(alpha = 0.15f),
                        border = BorderStroke(1.5.dp, StatusNeonGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Verified, contentDescription = null, tint = StatusNeonGreen)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "¡ACCESO AUTORIZADO POR RESIDENTE!",
                                    fontWeight = FontWeight.Bold,
                                    color = StatusNeonGreen,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onResetConfirmation,
                                colors = ButtonDefaults.buttonColors(containerColor = CyberSurfaceElevated),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Registrar Nuevo Visitante", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }

                ResidentConfirmationState.REJECTED -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StatusRed.copy(alpha = 0.15f),
                        border = BorderStroke(1.5.dp, StatusRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Acceso Denegado por Residente",
                            color = StatusRed,
                            modifier = Modifier.padding(14.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// Componente de Tarjeta para Evidencia Fotográfica
// -------------------------------------------------------------
@Composable
private fun PhotoEvidenceCard(
    title: String,
    hasPhoto: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (hasPhoto) Color(0xFF0F2537) else CyberSurfaceElevated,
        border = BorderStroke(1.dp, if (hasPhoto) NeonBlue else CyberBorderCircuit),
        modifier = modifier
            .height(105.dp)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (hasPhoto) Icons.Default.CheckCircle else Icons.Default.PhotoCamera,
                contentDescription = title,
                tint = if (hasPhoto) StatusNeonGreen else NeonBlue,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (hasPhoto) Color.White else CyberTextMuted,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (hasPhoto) "Capturado (1/1)" else "Tocar para Capturar",
                fontSize = 9.sp,
                color = if (hasPhoto) StatusNeonGreen else GoldMuted
            )
        }
    }
}

// -------------------------------------------------------------
// 3. Módulo Automatizado de Paquetería Inteligente (AI Visión + QR)
// -------------------------------------------------------------
@Composable
private fun SmartPackageSection(
    uiState: GuardStationUiState,
    onScanOcr: () -> Unit,
    onHouseChanged: (String) -> Unit,
    onCarrierChanged: (String) -> Unit,
    onTrackingChanged: (String) -> Unit,
    onRegisterPackage: () -> Unit,
    onStartPickupScan: () -> Unit,
    onSimulateQrPickup: (String) -> Unit
) {
    val matchedHouseInfo = CondominiumDirectory.findHouse(uiState.detectedPackageHouse)

    CyberCardContainer(
        title = "3. PAQUETERÍA INTELIGENTE · AI VISIÓN + QR",
        subtitle = "Lectura OCR automática, avisos de consigna y código contraentrega"
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // 3.1 Botón de Escáner de Etiqueta con IA
            Button(
                onClick = onScanOcr,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("ai_ocr_scan_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberSurfaceElevated,
                    contentColor = NeonBlue
                ),
                border = BorderStroke(1.dp, NeonBlue)
            ) {
                if (uiState.isOcrScanning) {
                    CircularProgressIndicator(
                        color = NeonBlue,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analizando etiqueta con AI Visión OCR...", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                } else {
                    Icon(imageVector = Icons.Default.DocumentScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("[📸 Escanear Etiqueta de Paquete]", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // Aviso Verde de Éxito en Detección por IA
            AnimatedVisibility(visible = uiState.ocrSuccessFeedback != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = StatusNeonGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, StatusNeonGreen)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = StatusNeonGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.ocrSuccessFeedback ?: "",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusNeonGreen
                        )
                    }
                }
            }

            // Campos del Paquete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = uiState.detectedPackageHouse,
                    onValueChange = onHouseChanged,
                    label = { Text("Casa Destino *", color = GoldMetallic) },
                    colors = cyberTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).testTag("package_destination_house_field")
                )

                OutlinedTextField(
                    value = uiState.packageCarrier,
                    onValueChange = onCarrierChanged,
                    label = { Text("Empresa Mensajería", color = CyberTextMuted) },
                    colors = cyberTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
            }

            // 3.2 Banners de Alertas Especiales por Casa (Verde / Amarillo)
            if (matchedHouseInfo?.specialConsignments != null) {
                val bannerBorder = if (matchedHouseInfo.alertLevel == HouseAlertLevel.ATTENTION) StatusOrangeAlert else StatusNeonGreen
                val bannerBg = bannerBorder.copy(alpha = 0.12f)
                val icon = if (matchedHouseInfo.alertLevel == HouseAlertLevel.ATTENTION) Icons.Default.Warning else Icons.Default.Info

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = bannerBg,
                    border = BorderStroke(1.dp, bannerBorder),
                    modifier = Modifier.fillMaxWidth().testTag("house_consignments_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = bannerBorder, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Consigna Especial para ${matchedHouseInfo.houseId} (${matchedHouseInfo.residentName}):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = bannerBorder
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = matchedHouseInfo.specialConsignments,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // 3.3 Botón de Envío y Tokenización
            Button(
                onClick = onRegisterPackage,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("register_package_token_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldMetallic,
                    contentColor = Color.Black
                )
            ) {
                Icon(Icons.Default.SendToMobile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Comprobar y Registrar Paquete (Generar QR)",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }

            // 3.4 Paquetes Pendientes de Entrega & Escáner de Recolección Rápida
            CircuitDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Paquetes en Resguardo (${uiState.pendingPackages.size} pendientes):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = GoldMetallic
                    )
                    Text(
                        text = "Valida con el código QR del celular del residente",
                        fontSize = 10.sp,
                        color = CyberTextMuted
                    )
                }

                // Botón flotante / directo con el icono de un código QR
                FloatingActionButton(
                    onClick = onStartPickupScan,
                    modifier = Modifier.size(44.dp).testTag("quick_pickup_qr_fab"),
                    containerColor = NeonBlue,
                    contentColor = Color.Black,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = "Escanear QR de Recolección")
                }
            }

            // Lista de Paquetes en Resguardo
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.pendingPackages.forEach { pkg ->
                    PendingPackageCard(
                        pkg = pkg,
                        onSimulateScan = { onSimulateQrPickup(pkg.qrPickupToken) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingPackageCard(
    pkg: PackageDeliveryItem,
    onSimulateScan: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = CyberSurfaceElevated,
        border = BorderStroke(1.dp, CyberBorderCircuit),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = pkg.destinationHouse,
                        fontWeight = FontWeight.Bold,
                        color = GoldMetallic,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "· ${pkg.carrierName}",
                        fontSize = 11.sp,
                        color = CyberTextMuted
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Guía: ${pkg.trackingCode}",
                    fontSize = 11.sp,
                    color = CyberTextPrimary
                )
                Text(
                    text = "Token: ${pkg.qrPickupToken}",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = NeonBlue
                )
            }

            OutlinedButton(
                onClick = onSimulateScan,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, NeonBlue),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.QrCode, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Validar QR", fontSize = 11.sp, color = NeonBlue)
            }
        }
    }
}

// -------------------------------------------------------------
// Nota: BLOQUE 4 (PatrolLogVoiceAssistantSection) se encuentra
// implementado de forma modular en PatrolLogVoiceAssistantSection.kt
// -------------------------------------------------------------

// -------------------------------------------------------------
// Contenedor Reutilizable CyberCardContainer
// -------------------------------------------------------------
@Composable
private fun CyberCardContainer(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CyberSurface,
        border = BorderStroke(0.75.dp, CyberBorderNeon),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                ),
                color = GoldMetallic
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = CyberTextMuted
            )

            Spacer(modifier = Modifier.height(12.dp))
            CircuitDivider()
            Spacer(modifier = Modifier.height(14.dp))

            content()
        }
    }
}

// -------------------------------------------------------------
// Divisor con Efecto de Circuito Neón
// -------------------------------------------------------------
@Composable
private fun CircuitDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        NeonBlue.copy(alpha = 0.5f),
                        NeonBlue,
                        NeonBlue.copy(alpha = 0.5f),
                        Color.Transparent
                    )
                )
            )
    )
}

// -------------------------------------------------------------
// Pie de Página Fijo Estilizado: TIEMPO = FAMILIA
// -------------------------------------------------------------
@Composable
private fun BrandMottoFooter() {
    Surface(
        color = CyberSurface,
        border = BorderStroke(0.5.dp, CyberBorderGold),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(GoldMetallic)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "TIEMPO = FAMILIA",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 3.sp,
                color = GoldMetallic
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(GoldMetallic)
            )
        }
    }
}

// -------------------------------------------------------------
// Configuración de Colores de Campos de Texto Cyber-Security
// -------------------------------------------------------------
@Composable
private fun cyberTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NeonBlue,
    unfocusedBorderColor = CyberBorderCircuit,
    focusedTextColor = CyberTextPrimary,
    unfocusedTextColor = CyberTextPrimary,
    focusedContainerColor = CyberSurfaceElevated,
    unfocusedContainerColor = CyberSurfaceElevated,
    cursorColor = NeonBlue
)
