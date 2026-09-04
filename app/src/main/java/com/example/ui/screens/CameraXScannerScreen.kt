package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.booking.AppDatabase
import com.example.data.audit.AuditLogEntity
import com.example.data.core.AlphaCoreEngine
import com.example.data.notifications.SmartNotificationHub
import com.example.data.passes.QrPassRepository
import com.example.data.passes.QrPassRoomEntity
import com.example.data.visitor.VisitorCheckIn
import com.example.data.visitor.VisitorCheckInRepository
import com.example.scanner.PassStatus
import com.example.scanner.PassType
import com.example.scanner.QrCodeAnalyzer
import com.example.scanner.QrPassEntity
import com.example.scanner.QrPayloadParser
import com.example.scanner.VerificationResult
import com.example.data.DataRepository
import com.example.data.FirestoreVisitor
import com.example.data.firebase.FirestoreTenantManager
import com.example.ui.components.CondoTarget
import com.example.ui.components.triggerScanHaptic
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarningOrange
import com.example.utils.ResidentNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Pantalla completa de previsualización CameraX con retícula táctica de escaneo
 * para capturar, procesar y validar códigos QR en tiempo real para control de accesos.
 */
@Composable
fun CameraXScannerScreen(
    db: AppDatabase,
    selectedCondo: CondoTarget = CondoTarget.PARAISO,
    onCondoChanged: ((CondoTarget) -> Unit)? = null,
    onBackToDashboard: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var activeCondo by remember(selectedCondo) { mutableStateOf(selectedCondo) }

    // Repositorios Room
    val qrPassRepository = remember { QrPassRepository(db.qrPassDao()) }
    val visitorRepository = remember { VisitorCheckInRepository(db.visitorCheckInDao()) }

    // Check-ins de Room en tiempo real
    val recentCheckIns by visitorRepository.allCheckIns.collectAsState(initial = emptyList())
    val roomPasses by qrPassRepository.allPassesFlow.collectAsState(initial = emptyList())

    // Estado del permiso de cámara
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Se requiere permiso de cámara para el escaneo en caseta", Toast.LENGTH_SHORT).show()
        }
    }

    // Parámetros de la cámara
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var cameraLensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var isScanningActive by remember { mutableStateOf(true) }
    var isScanSuccessActive by remember { mutableStateOf(false) }

    // Tap-to-focus animation state
    var tapFocusPoint by remember { mutableStateOf<Offset?>(null) }
    var tapFocusAnimTrigger by remember { mutableStateOf(0) }

    // Estado de verificación y procesamiento
    var activeVerificationResult by remember { mutableStateOf<VerificationResult?>(null) }
    var lastScannedCode by remember { mutableStateOf<String?>(null) }
    var isGateOpeningAnimation by remember { mutableStateOf(false) }
    var gateAnimationProgress by remember { mutableStateOf(0f) }

    // Modales secundarios
    var showManualInputDialog by remember { mutableStateOf(false) }
    var manualInputText by remember { mutableStateOf("") }
    var showTestSimulatorDrawer by remember { mutableStateOf(true) }
    var showRecentLogDrawer by remember { mutableStateOf(false) }

    // Vista previa de CameraX
    val previewView = remember(context) {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Solicitar permiso en el primer render si no está concedido
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            try {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Función principal para verificar y procesar un código QR capturado
    fun processCapturedQrCode(scannedRawCode: String) {
        val cleanCode = QrPayloadParser.extractEntryCode(scannedRawCode)
        if (cleanCode.isBlank() || isGateOpeningAnimation) return

        triggerScanHaptic(context)
        isScanSuccessActive = true

        scope.launch {
            delay(500)
            isScanSuccessActive = false

            // Consultar validación contra el repositorio Room y Firestore con aislamiento del condominio activo
            val firestore = com.example.data.firebase.FirebaseConfigHelper.getFirestoreInstance()
            val result = qrPassRepository.verifyPassCode(
                code = cleanCode,
                currentCondominiumId = activeCondo.name,
                firestore = firestore
            )

            lastScannedCode = cleanCode
            activeVerificationResult = result
        }
    }

    // Enlazar el ciclo de vida de CameraX con Preview + ImageAnalysis
    LaunchedEffect(hasCameraPermission, cameraLensFacing, lifecycleOwner) {
        if (!hasCameraPermission) {
            camera = null
            return@LaunchedEffect
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build().apply {
                        setAnalyzer(
                            Executors.newSingleThreadExecutor(),
                            QrCodeAnalyzer { scannedCode ->
                                if (isScanningActive && activeVerificationResult == null) {
                                    scope.launch(Dispatchers.Main) {
                                        processCapturedQrCode(scannedCode)
                                    }
                                }
                            }
                        )
                    }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(cameraLensFacing)
                    .build()

                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                if (isTorchOn) {
                    camera?.cameraControl?.enableTorch(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Contadores en vivo para el condominio seleccionado
    val condoCheckIns: List<VisitorCheckIn> = remember(recentCheckIns, selectedCondo) {
        recentCheckIns.filter { entry ->
            when (selectedCondo) {
                CondoTarget.PARAISO -> entry.destinationHouse.contains("Casa", ignoreCase = true) || entry.destinationHouse.contains("Paraíso", ignoreCase = true)
                CondoTarget.PRADOS_1 -> entry.destinationHouse.contains("Calle 1", ignoreCase = true) || entry.destinationHouse.contains("Calle 2", ignoreCase = true)
                CondoTarget.PRADOS_2 -> entry.destinationHouse.contains("Calle 3", ignoreCase = true) || entry.destinationHouse.contains("Calle 4", ignoreCase = true)
                CondoTarget.PRADOS_3 -> entry.destinationHouse.contains("Calle 5", ignoreCase = true) || entry.destinationHouse.contains("Calle 6", ignoreCase = true)
            }
        }
    }
    val activeVisitorsCount = condoCheckIns.count { it.status == "CHECKED_IN" || it.status == "VERIFIED" }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(NavyDark)
            .testTag("camerax_scanner_screen_root")
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        // 1. CAPA DE PREVISUALIZACIÓN DE CÁMARA (O SOLICITUD DE PERMISO)
        if (!hasCameraPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = CyanNeon.copy(alpha = 0.12f),
                    border = BorderStroke(2.dp, CyanNeon),
                    modifier = Modifier.size(90.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = CyanNeon,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "ACCESO A CÁMARA REQUERIDO",
                    color = GoldPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "El lector óptico CameraX requiere permiso de hardware para escanear y autenticar códigos QR en la garita de seguridad de ${selectedCondo.displayName}.",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        try {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(48.dp)
                        .testTag("btn_grant_camera_permission")
                ) {
                    Icon(Icons.Default.Security, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("HABILITAR CAMERAX", fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
        } else {
            // Live CameraX Preview View
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(camera) {
                        detectTapGestures { offset ->
                            tapFocusPoint = offset
                            tapFocusAnimTrigger++
                            camera?.let { cam ->
                                try {
                                    val factory = SurfaceOrientedMeteringPointFactory(
                                        size.width.toFloat(),
                                        size.height.toFloat()
                                    )
                                    val point = factory.createPoint(offset.x, offset.y)
                                    val action = FocusMeteringAction.Builder(point).build()
                                    cam.cameraControl.startFocusAndMetering(action)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }
            )

            // 2. RETÍCULA TÁCTICA Y OVERLAY SUPERPUESTO
            TacticalScannerOverlay(
                isSuccess = isScanSuccessActive,
                selectedCondo = selectedCondo
            )

            // Tap-To-Focus Animated Ring Indicator
            tapFocusPoint?.let { point ->
                FocusTapIndicator(
                    point = point,
                    trigger = tapFocusAnimTrigger,
                    onAnimationEnd = { tapFocusPoint = null }
                )
            }

            // 3. BARRA SUPERIOR DE TELEMETRÍA Y CONTROLES
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back button / Dashboard button
                    Surface(
                        onClick = { onBackToDashboard?.invoke() },
                        color = NavyDark.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f)),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (onBackToDashboard != null) Icons.Default.ArrowBack else Icons.Default.Security,
                                contentDescription = "Regresar",
                                tint = CyanNeon,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Telemetría del Escáner y Condominio
                    Surface(
                        color = NavyDark.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(SuccessGreen, CircleShape)
                            )
                            Text(
                                text = "CAMERAX LIVE • ${selectedCondo.shortTag}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "[$activeVisitorsCount EN PREDIO]",
                                color = CyanNeon,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    // Acciones rápidas (Linterna, Flip cámara, Teclado)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Linterna / Torch Toggle
                        IconButton(
                            onClick = {
                                val newState = !isTorchOn
                                camera?.cameraControl?.enableTorch(newState)
                                isTorchOn = newState
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .background(NavyDark.copy(alpha = 0.85f), CircleShape)
                                .border(1.dp, if (isTorchOn) GoldPrimary else Color.Gray, CircleShape)
                                .testTag("btn_toggle_torch")
                        ) {
                            Icon(
                                imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Linterna",
                                tint = if (isTorchOn) GoldPrimary else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Flip Camera
                        IconButton(
                            onClick = {
                                cameraLensFacing = if (cameraLensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .background(NavyDark.copy(alpha = 0.85f), CircleShape)
                                .border(1.dp, CyanNeon.copy(alpha = 0.6f), CircleShape)
                                .testTag("btn_flip_camera")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlipCameraAndroid,
                                contentDescription = "Cambiar Lente",
                                tint = CyanNeon,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Manual Code Input
                        IconButton(
                            onClick = { showManualInputDialog = true },
                            modifier = Modifier
                                .size(38.dp)
                                .background(NavyDark.copy(alpha = 0.85f), CircleShape)
                                .border(1.dp, GoldPrimary.copy(alpha = 0.6f), CircleShape)
                                .testTag("btn_manual_code_input")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = "Ingreso Manual",
                                tint = GoldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Selector de Condominio Rápido si hay callback
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(CondoTarget.values()) { target ->
                        val isSelected = target == activeCondo
                        Surface(
                            onClick = {
                                activeCondo = target
                                onCondoChanged?.invoke(target)
                            },
                            color = if (isSelected) GoldPrimary else NavyDark.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color.DarkGray)
                        ) {
                            Text(
                                text = target.shortTag,
                                color = if (isSelected) NavyDark else Color.LightGray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // 4. ANIMACIÓN DE APERTURA DE PLUMA (FEEDBACK AL AUTORIZAR)
            AnimatedVisibility(
                visible = isGateOpeningAnimation,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Surface(
                    color = NavyCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(2.dp, SuccessGreen),
                    shadowElevation = 12.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = SuccessGreen.copy(alpha = 0.2f),
                            border = BorderStroke(2.dp, SuccessGreen),
                            modifier = Modifier.size(60.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.MeetingRoom,
                                    contentDescription = null,
                                    tint = SuccessGreen,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Text(
                            text = "PLUMA VEHICULAR ELEVADA",
                            color = SuccessGreen,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )

                        Text(
                            text = "Acceso autorizado • Pase libre a ${selectedCondo.displayName}",
                            color = Color.White,
                            fontSize = 12.sp
                        )

                        LinearProgressIndicator(
                            progress = { gateAnimationProgress },
                            color = SuccessGreen,
                            trackColor = Color.DarkGray,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )

                        Text(
                            text = "Cierre automático de seguridad activo...",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 5. BANNER INFORMATIVO INFERIOR CON TIRAS DE PRUEBA Y BITÁCORA
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, NavyDark.copy(alpha = 0.95f), NavyDark)
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Tira de Pases Rápidos para Testing (Excelente para emuladores y pruebas de garita)
                if (showTestSimulatorDrawer) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🧪 PRUEBAS DE ESCANEO RÁPIDO (GARITA):",
                            color = GoldPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (showRecentLogDrawer) "Ver Pruebas" else "Ver Bitácora (${condoCheckIns.size})",
                            color = CyanNeon,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showRecentLogDrawer = !showRecentLogDrawer }
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (showRecentLogDrawer) {
                        // Resumen de la bitácora reciente
                        Surface(
                            color = NavyCard,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("ÚLTIMOS INGRESOS EN ${selectedCondo.displayName.uppercase()}:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                if (condoCheckIns.isEmpty()) {
                                    Text("Sin visitas registradas hoy.", color = TextMuted, fontSize = 11.sp)
                                } else {
                                    condoCheckIns.take(3).forEach { checkIn ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(checkIn.visitorName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                Text("${checkIn.destinationHouse} • ${checkIn.passTypeLabel}", color = TextMuted, fontSize = 9.sp)
                                            }
                                            Surface(
                                                color = if (checkIn.status == "DEPARTED") GoldPrimary.copy(alpha = 0.2f) else SuccessGreen.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    if (checkIn.status == "DEPARTED") "SALIDA" else "EN PREDIO",
                                                    color = if (checkIn.status == "DEPARTED") GoldPrimary else SuccessGreen,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Tira horizontal con chips de simulación
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                Surface(
                                    onClick = {
                                        // Simular visita a Casa 14 Paraíso
                                        val code = "MEDUSA-VISITA-PARAISO-14-998"
                                        processCapturedQrCode(code)
                                    },
                                    color = NavySurface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, GoldPrimary),
                                    modifier = Modifier.testTag("test_chip_casa14")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Home, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(12.dp))
                                        Text("Casa 14 (Paraíso)", color = GoldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            item {
                                Surface(
                                    onClick = {
                                        // Simular proveedor DHL
                                        val code = "MEDUSA-VISITA-PRADOS_1-26-884"
                                        processCapturedQrCode(code)
                                    },
                                    color = NavySurface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, CyanNeon),
                                    modifier = Modifier.testTag("test_chip_dhl")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(12.dp))
                                        Text("Paquetería DHL", color = CyanNeon, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            item {
                                Surface(
                                    onClick = {
                                        // Usar un pase real de Room si existe, o generar uno
                                        val pass = roomPasses.firstOrNull()
                                        val code = pass?.passCode ?: "MED-${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}-1001"
                                        processCapturedQrCode(code)
                                    },
                                    color = NavySurface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, SuccessGreen),
                                    modifier = Modifier.testTag("test_chip_room_pass")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.QrCode, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(12.dp))
                                        Text("Pase Room DB", color = SuccessGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            item {
                                Surface(
                                    onClick = {
                                        // Pase Expirado
                                        processCapturedQrCode("MEDUSA-EXPIRADO-2025-001")
                                    },
                                    color = NavySurface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, ErrorRed),
                                    modifier = Modifier.testTag("test_chip_expired")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(12.dp))
                                        Text("Pase Expirado", color = ErrorRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. MODAL DE PROCESAMIENTO Y CONFIRMACIÓN DE INGRESO DEL VISITANTE
        activeVerificationResult?.let { result ->
            VisitorCheckInProcessingDialog(
                result = result,
                selectedCondo = activeCondo,
                onDismiss = { activeVerificationResult = null },
                onAuthorizeCheckIn = { passEntity, note, vehiclePlate ->
                    scope.launch {
                        val folio = if (passEntity.passCode.startsWith("MED-")) passEntity.passCode else AlphaCoreEngine.generateUniqueFolio("ACC")

                        // 1. Guardar en Room SQLite
                        visitorRepository.insertCheckIn(
                            VisitorCheckIn(
                                folio = folio,
                                visitorName = passEntity.guestName,
                                visitorDocument = passEntity.guestDocument.ifBlank { "ID Verificada en Caseta" },
                                destinationHouse = passEntity.destinationHouse,
                                passCode = passEntity.passCode,
                                passTypeLabel = passEntity.passType.label,
                                vehiclePlate = vehiclePlate.ifBlank { passEntity.vehiclePlate ?: "SIN PLACAS" },
                                status = "CHECKED_IN",
                                guardNotes = note.ifBlank { "Ingreso verificado y capturado con escáner CameraX en ${activeCondo.displayName}" },
                                hostResidentName = passEntity.hostResidentName
                            )
                        )

                        // 2. Marcar pase como usado en Room
                        qrPassRepository.markPassAsUsed(passEntity.passCode)

                        // 3. Registrar en Auditoría Local
                        db.auditLogDao().insertAuditLog(
                            AuditLogEntity(
                                folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                                operatorName = "Guardia Garita ${activeCondo.shortTag}",
                                actionType = "CHECK_IN_CAMERAX",
                                location = "Garita ${activeCondo.displayName}",
                                targetEntity = "${passEntity.guestName} ($folio)",
                                changeDetails = "Ingreso autorizado y capturado con visor CameraX a ${passEntity.destinationHouse}",
                                resultStatus = "EXITOSO"
                            )
                        )

                        // 4. Sincronizar en Firestore Central con aislamiento de inquilino
                        val firestore = com.example.data.firebase.FirebaseConfigHelper.getFirestoreInstance()
                        if (firestore != null) {
                            try {
                                val dataRepo = DataRepository(firestore, activeCondo.name)
                                val firestoreVisitor = FirestoreVisitor(
                                    id = folio,
                                    condominiumId = activeCondo.name,
                                    visitorName = passEntity.guestName,
                                    authorizedUnitNumber = passEntity.destinationHouse,
                                    visitorDocument = passEntity.guestDocument.ifBlank { "ID Verificada en Caseta" },
                                    hostResidentName = passEntity.hostResidentName,
                                    visitType = passEntity.passType.name,
                                    vehiclePlate = vehiclePlate.ifBlank { passEntity.vehiclePlate ?: "" },
                                    passCode = passEntity.passCode,
                                    status = "CHECKED_IN",
                                    guardNotes = note.ifBlank { "Ingreso verificado con escáner CameraX" },
                                    guardName = "Guardia Garita ${activeCondo.shortTag}"
                                )
                                dataRepo.saveVisitor(firestoreVisitor, activeCondo.name)
                                FirestoreTenantManager.saveVisitorLog(
                                    firestore = firestore,
                                    condominiumId = activeCondo.name,
                                    visitorLog = firestoreVisitor.toFirestoreVisitorLog()
                                )
                            } catch (e: Exception) {
                                // Fallback local seguro
                            }
                        }

                        // 5. Notificar al residente anfitrión
                        ResidentNotificationManager.notifyResidentVisitorCheckedIn(
                            context = context,
                            pass = passEntity,
                            guardNotes = "Ingreso verificado y autorizado con escáner CameraX"
                        )
                        SmartNotificationHub.notifyVisitorEntry(
                            context = context,
                            db = db,
                            guestName = passEntity.guestName,
                            unitId = passEntity.destinationHouse,
                            hostResidentName = passEntity.hostResidentName,
                            passTypeLabel = passEntity.passType.label,
                            vehiclePlate = vehiclePlate.ifBlank { passEntity.vehiclePlate },
                            passFolio = folio
                        )

                        // 6. Disparar animación de barrera abierta
                        activeVerificationResult = null
                        isGateOpeningAnimation = true
                        for (i in 1..20) {
                            gateAnimationProgress = i / 20f
                            delay(100)
                        }
                        delay(800)
                        isGateOpeningAnimation = false
                        gateAnimationProgress = 0f

                        Toast.makeText(context, "✅ INGRESO REGISTRADO Y PLUMA ELEVADA [$folio]", Toast.LENGTH_LONG).show()
                    }
                },
                onDenyAccess = { passEntity, reason ->
                    scope.launch {
                        val folio = AlphaCoreEngine.generateUniqueFolio("DEN")
                        visitorRepository.insertCheckIn(
                            VisitorCheckIn(
                                folio = folio,
                                visitorName = passEntity.guestName,
                                visitorDocument = passEntity.guestDocument,
                                destinationHouse = passEntity.destinationHouse,
                                passCode = passEntity.passCode,
                                passTypeLabel = passEntity.passType.label,
                                vehiclePlate = passEntity.vehiclePlate ?: "N/A",
                                status = "DENIED",
                                guardNotes = "Acceso Denegado en Caseta: $reason",
                                hostResidentName = passEntity.hostResidentName
                            )
                        )
                        db.auditLogDao().insertAuditLog(
                            AuditLogEntity(
                                folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                                operatorName = "Guardia Garita ${activeCondo.shortTag}",
                                actionType = "ACCESS_DENIED_CAMERAX",
                                location = "Garita ${activeCondo.displayName}",
                                targetEntity = "${passEntity.guestName} ($folio)",
                                changeDetails = "Acceso denegado: $reason",
                                resultStatus = "DENEGADO"
                            )
                        )

                        val firestore = com.example.data.firebase.FirebaseConfigHelper.getFirestoreInstance()
                        if (firestore != null) {
                            try {
                                val dataRepo = DataRepository(firestore, activeCondo.name)
                                val firestoreVisitor = FirestoreVisitor(
                                    id = folio,
                                    condominiumId = activeCondo.name,
                                    visitorName = passEntity.guestName,
                                    authorizedUnitNumber = passEntity.destinationHouse,
                                    visitorDocument = passEntity.guestDocument,
                                    hostResidentName = passEntity.hostResidentName,
                                    visitType = passEntity.passType.name,
                                    vehiclePlate = passEntity.vehiclePlate ?: "N/A",
                                    passCode = passEntity.passCode,
                                    status = "DENIED",
                                    guardNotes = "Acceso Denegado: $reason",
                                    guardName = "Guardia Garita ${activeCondo.shortTag}"
                                )
                                dataRepo.saveVisitor(firestoreVisitor, activeCondo.name)
                            } catch (e: Exception) {
                                // Fallback local seguro
                            }
                        }

                        activeVerificationResult = null
                        Toast.makeText(context, "🚫 ACCESO DENEGADO Y REGISTRADO EN BITÁCORA", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }

        // 7. DIÁLOGO DE INGRESO MANUAL DE CÓDIGO
        if (showManualInputDialog) {
            AlertDialog(
                onDismissRequest = {
                    showManualInputDialog = false
                    manualInputText = ""
                },
                containerColor = NavyDark,
                shape = RoundedCornerShape(16.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Keyboard, contentDescription = null, tint = GoldPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ingreso Manual de Código", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Digite el folio del pase impreso o código enviado por el visitante:",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                        OutlinedTextField(
                            value = manualInputText,
                            onValueChange = { manualInputText = it },
                            placeholder = { Text("Ej: MED-20260821-1001", color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_manual_code_field")
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (manualInputText.isNotBlank()) {
                                showManualInputDialog = false
                                processCapturedQrCode(manualInputText.trim())
                                manualInputText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark)
                    ) {
                        Text("Verificar Código", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualInputDialog = false }) {
                        Text("Cancelar", color = Color.Gray)
                    }
                }
            )
        }
    }
}

/**
 * Retícula táctica con máscara translúcida y ventana de escaneo cristalina.
 */
@Composable
fun TacticalScannerOverlay(
    isSuccess: Boolean,
    selectedCondo: CondoTarget,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "laser_sweep")
    val laserYRatio by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_pos"
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()

        // Tamaño dinámico de la caja de escaneo (óptimo para móvil vertical)
        val boxDimension = (width * 0.72f).coerceIn(240f, 320f)
        val left = (width - boxDimension) / 2f
        val top = (height - boxDimension) / 2f - 30f
        val right = left + boxDimension
        val bottom = top + boxDimension

        val cornerLength = 36.dp.value * 2.5f
        val cornerStroke = if (isSuccess) 5.dp.value * 2.5f else 3.5.dp.value * 2.5f
        val cornerColor = if (isSuccess) SuccessGreen else GoldPrimary

        // DIBUJAR MÁSCARA Y LÁSER EN CANVAS
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maskColor = Color(0x730A1128)

            // Máscaras translúcidas fuera de la ventana de escaneo
            drawRect(color = maskColor, topLeft = Offset(0f, 0f), size = Size(width, top))
            drawRect(color = maskColor, topLeft = Offset(0f, bottom), size = Size(width, height - bottom))
            drawRect(color = maskColor, topLeft = Offset(0f, top), size = Size(left, boxDimension))
            drawRect(color = maskColor, topLeft = Offset(right, top), size = Size(width - right, boxDimension))

            // Borde sutil del marco de captura
            drawRect(
                color = if (isSuccess) SuccessGreen.copy(alpha = 0.8f) else CyanNeon.copy(alpha = 0.35f),
                topLeft = Offset(left, top),
                size = Size(boxDimension, boxDimension),
                style = Stroke(width = 1.5.dp.toPx())
            )

            // 4 Esquinas Tácticas HUD
            // Superior Izquierda
            drawLine(cornerColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth = cornerStroke)
            drawLine(cornerColor, Offset(left, top), Offset(left, top + cornerLength), strokeWidth = cornerStroke)

            // Superior Derecha
            drawLine(cornerColor, Offset(right, top), Offset(right - cornerLength, top), strokeWidth = cornerStroke)
            drawLine(cornerColor, Offset(right, top), Offset(right, top + cornerLength), strokeWidth = cornerStroke)

            // Inferior Izquierda
            drawLine(cornerColor, Offset(left, bottom), Offset(left + cornerLength, bottom), strokeWidth = cornerStroke)
            drawLine(cornerColor, Offset(left, bottom), Offset(left, bottom - cornerLength), strokeWidth = cornerStroke)

            // Inferior Derecha
            drawLine(cornerColor, Offset(right, bottom), Offset(right - cornerLength, bottom), strokeWidth = cornerStroke)
            drawLine(cornerColor, Offset(right, bottom), Offset(right, bottom - cornerLength), strokeWidth = cornerStroke)

            // Línea Láser Barredora con Gradiente
            val laserY = top + (boxDimension * laserYRatio)
            drawLine(
                color = if (isSuccess) SuccessGreen else CyanNeon,
                start = Offset(left + 8.dp.toPx(), laserY),
                end = Offset(right - 8.dp.toPx(), laserY),
                strokeWidth = if (isSuccess) 4.5.dp.toPx() else 2.5.dp.toPx()
            )
        }

        // Indicador de Instrucción en el centro superior de la ventana
        Column(
            modifier = Modifier
                .offset { IntOffset(0, (top - 55).toInt()) }
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = NavyDark.copy(alpha = 0.85f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (isSuccess) SuccessGreen else CyanNeon.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.CenterFocusStrong,
                        contentDescription = null,
                        tint = if (isSuccess) SuccessGreen else CyanNeon,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isSuccess) "¡CÓDIGO QR CAPTURADO!" else "ALINEE EL CÓDIGO QR DENTRO DEL RECUADRO",
                        color = if (isSuccess) SuccessGreen else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

/**
 * Anillo animado al pulsar la pantalla para enfocar (Tap-To-Focus feedback).
 */
@Composable
fun FocusTapIndicator(
    point: Offset,
    trigger: Int,
    onAnimationEnd: () -> Unit
) {
    var animScale by remember(trigger) { mutableStateOf(1.4f) }
    var animAlpha by remember(trigger) { mutableStateOf(1f) }

    val animatedScale by animateFloatAsState(
        targetValue = animScale,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "focus_scale"
    )

    LaunchedEffect(trigger) {
        animScale = 0.9f
        delay(450)
        animAlpha = 0f
        delay(100)
        onAnimationEnd()
    }

    Box(
        modifier = Modifier
            .offset { IntOffset((point.x - 30.dp.value).toInt(), (point.y - 30.dp.value).toInt()) }
            .size(60.dp)
            .scale(animatedScale)
            .border(2.dp, CyanNeon.copy(alpha = animAlpha), CircleShape)
    )
}

/**
 * Diálogo / Hoja modal táctica para procesar el ingreso del visitante con la información validada.
 */
@Composable
fun VisitorCheckInProcessingDialog(
    result: VerificationResult,
    selectedCondo: CondoTarget,
    onDismiss: () -> Unit,
    onAuthorizeCheckIn: (QrPassEntity, note: String, plate: String) -> Unit,
    onDenyAccess: (QrPassEntity, reason: String) -> Unit
) {
    var customNotes by remember { mutableStateOf("") }
    var inputPlate by remember(result) { mutableStateOf(result.qrPass?.vehiclePlate ?: "") }
    var denyReason by remember { mutableStateOf("") }
    var showDenyForm by remember { mutableStateOf(false) }

    // Generar entidad fallback si el código no existía en Room
    val passEntity = remember(result) {
        result.qrPass ?: QrPassEntity(
            passCode = result.passCode,
            guestName = "Visitante Folio #${result.passCode.takeLast(6)}",
            guestDocument = "Por Verificar en Caseta",
            destinationHouse = if (selectedCondo == CondoTarget.PARAISO) "Casa 01" else "Calle 1 #01",
            hostResidentName = "Residente ${selectedCondo.shortTag}",
            vehiclePlate = null,
            passType = PassType.VISITOR_SINGLE,
            validUntilMillis = System.currentTimeMillis() + (12 * 3600 * 1000)
        )
    }

    val isValid = result.status == PassStatus.VALID
    val isExpired = result.status == PassStatus.EXPIRED
    val isAlreadyUsed = result.status == PassStatus.ALREADY_USED
    val isInvalid = result.status == PassStatus.INVALID

    val statusColor = when {
        isValid -> SuccessGreen
        isExpired -> WarningOrange
        isAlreadyUsed -> Color(0xFFEAB308)
        else -> ErrorRed
    }

    val statusTitle = when {
        isValid -> "🟢 PASE VÁLIDO Y VIGENTE"
        isExpired -> "🟡 PASE EXPIRADO"
        isAlreadyUsed -> "⚠️ PASE YA UTILIZADO"
        else -> "🔴 CÓDIGO NO REGISTRADO"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCard,
        shape = RoundedCornerShape(18.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, statusColor)
                ) {
                    Text(
                        text = statusTitle,
                        color = statusColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.Gray)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Tarjeta de Identidad del Visitante
                Surface(
                    color = NavySurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = GoldPrimary.copy(alpha = 0.2f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = passEntity.guestName,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${passEntity.passType.label} • Folio: ${result.passCode.take(16)}",
                                    color = CyanNeon,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Destino y Anfitrión
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("DESTINO", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(passEntity.destinationHouse, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                                Text("ANFITRIÓN", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(passEntity.hostResidentName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                if (!result.hostResidentPhone.isNullOrBlank()) {
                                    val ctx = androidx.compose.ui.platform.LocalContext.current
                                    Surface(
                                        onClick = {
                                            try {
                                                val dialIntent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                                    data = android.net.Uri.parse("tel:${result.hostResidentPhone}")
                                                }
                                                ctx.startActivity(dialIntent)
                                            } catch (e: Exception) {
                                                Toast.makeText(ctx, "Tel: ${result.hostResidentPhone}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        color = CyanNeon.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.4f)),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(Icons.Default.Phone, contentDescription = "Llamar", tint = CyanNeon, modifier = Modifier.size(12.dp))
                                            Text(result.hostResidentPhone ?: "", color = CyanNeon, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // Badge de validación Firestore / Room
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = if (result.isFirestoreValidated) CyanNeon.copy(alpha = 0.12f) else NavyDark,
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (result.isFirestoreValidated) CyanNeon.copy(alpha = 0.4f) else Color.DarkGray)
                            ) {
                                Text(
                                    text = if (result.isFirestoreValidated) "☁️ FIRESTORE TENANT (${selectedCondo.shortTag})" else "🏠 VERIFICACIÓN ROOM LOCAL",
                                    color = if (result.isFirestoreValidated) CyanNeon else Color.LightGray,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "Usos: ${passEntity.currentEntriesCount}/${passEntity.maxEntries}",
                                color = if (passEntity.currentEntriesCount >= passEntity.maxEntries) WarningOrange else TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Motivo de Falla si no es válido
                if (!isValid && result.failureReason != null) {
                    Surface(
                        color = ErrorRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = result.failureReason,
                            color = ErrorRed,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                // Campos de Entrada para Garita (Placas y Notas)
                if (!showDenyForm) {
                    OutlinedTextField(
                        value = inputPlate,
                        onValueChange = { inputPlate = it.uppercase() },
                        label = { Text("Placas del Vehículo", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GoldPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = customNotes,
                        onValueChange = { customNotes = it },
                        label = { Text("Notas de Garita (Opcional)", fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GoldPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    OutlinedTextField(
                        value = denyReason,
                        onValueChange = { denyReason = it },
                        label = { Text("Motivo del Rechazo / Incidencia *", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = ErrorRed
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!showDenyForm) {
                    Button(
                        onClick = {
                            onAuthorizeCheckIn(passEntity, customNotes, inputPlate)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isValid) SuccessGreen else GoldPrimary,
                            contentColor = NavyDark
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_confirm_authorize_entry")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isValid) "AUTORIZAR INGRESO & ABRIR PLUMA" else "AUTORIZAR EXCEPCIONALMENTE",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDenyForm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            border = BorderStroke(1.dp, ErrorRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("DENEGAR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
                            border = BorderStroke(1.dp, Color.Gray),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Text("CANCELAR", fontSize = 11.sp)
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            if (denyReason.isNotBlank()) {
                                onDenyAccess(passEntity, denyReason)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("REGISTRAR ACCESO DENEGADO", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    TextButton(
                        onClick = { showDenyForm = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Volver a Opciones de Autorización", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            }
        },
        dismissButton = null
    )
}
