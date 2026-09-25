package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.data.audit.AuditLogEntity
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.firebase.FirebaseConfigHelper
import com.example.data.firebase.FirestoreTenantManager
import com.example.data.passes.QrPassRoomEntity
import com.example.data.visitor.FirestoreVisitorLog
import com.example.data.visitor.VisitorCheckIn
import com.example.scanner.ParsedQrPass
import com.example.scanner.QrPayloadParser
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
import com.google.firebase.Timestamp
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraXVisitorScanner"

/**
 * Estados del resultado de escaneo y procesamiento de códigos de visitantes.
 */
sealed interface VisitorScanResult {
    data object Idle : VisitorScanResult
    data class Processing(val code: String) : VisitorScanResult
    data class Success(
        val visitorCheckIn: VisitorCheckIn,
        val passEntity: QrPassRoomEntity?,
        val rawCode: String,
        val folio: String,
        val isFirestoreSynced: Boolean,
        val message: String
    ) : VisitorScanResult
    data class Denied(
        val rawCode: String,
        val reason: String,
        val guestName: String? = null,
        val destinationHouse: String? = null
    ) : VisitorScanResult
}

/**
 * Datos del banner visual inmediato sobre la vista de la cámara.
 */
private data class ImmediateVisitorFeedback(
    val isSuccess: Boolean,
    val title: String,
    val visitorName: String,
    val destinationHouse: String,
    val code: String,
    val details: String
)

/**
 * Componente modular de escaneo de códigos QR para visitantes basado en CameraX
 * e integrado con la biblioteca ZXing.
 *
 * Características principales:
 * - Ciclo de vida CameraX enlazado a Jetpack Compose con ProcessCameraProvider.
 * - Análisis de fotogramas YUV_420_888 mediante MultiFormatReader de ZXing con detección invertida
 *   para pantallas OLED de smartphones de visitantes en modo oscuro.
 * - Decodificación y validación automática de códigos de pase de visitantes en la base de datos Room.
 * - Registro inmediato en Firestore (/condominiums/{condoId}/visitor_logs) con Timestamp nativo.
 * - Persistencia local reactiva en Room (VisitorCheckIn) y bitácora de auditoría inmutable (AuditLogEntity).
 * - Retícula táctica animada con rayo láser, esquinas HUD y tap-to-focus en tiempo real.
 * - Controles para linterna / flash (Torch), alternancia frontal/trasera y diálogo para ingreso manual.
 */
@Composable
fun CameraXVisitorScannerComponent(
    modifier: Modifier = Modifier,
    condominiumId: String = "PRADOS_1",
    guardName: String = "Oficial de Seguridad - Garita 1",
    onVisitorScanned: (VisitorScanResult) -> Unit = {},
    onDismiss: (() -> Unit)? = null,
    isPaused: Boolean = false,
    enableTorch: Boolean = true,
    enableCameraFlip: Boolean = true,
    enableManualEntry: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val db = remember { AppDatabase.getDatabase(context) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var cameraLensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var isProcessing by remember { mutableStateOf(false) }
    var activeResult by remember { mutableStateOf<VisitorScanResult>(VisitorScanResult.Idle) }
    var immediateFeedback by remember { mutableStateOf<ImmediateVisitorFeedback?>(null) }
    var showManualDialog by remember { mutableStateOf(false) }
    var manualCodeInput by remember { mutableStateOf("") }
    var tapFocusPoint by remember { mutableStateOf<Offset?>(null) }

    // Auto-dismiss de feedback inmediato tras 4 segundos
    LaunchedEffect(immediateFeedback) {
        if (immediateFeedback != null) {
            delay(4000)
            immediateFeedback = null
        }
    }

    // Hilo dedicado único para análisis de fotogramas de la cámara
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

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

    // Solicitar permiso de cámara automáticamente al montar si aún no fue otorgado
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            try {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            } catch (e: Exception) {
                Log.w(TAG, "Error solicitando permiso de cámara: ${e.message}")
            }
        }
    }

    // Función interna para procesar el código de visitante capturado
    fun processVisitorCode(rawCode: String) {
        if (isProcessing) return
        val cleanCode = QrPayloadParser.extractEntryCode(rawCode)
        if (cleanCode.isBlank()) {
            val failFeedback = ImmediateVisitorFeedback(
                isSuccess = false,
                title = "CÓDIGO NO VÁLIDO",
                visitorName = "Desconocido",
                destinationHouse = "Sin Destino",
                code = rawCode.take(24),
                details = "El código QR escaneado no contiene credenciales de visitante válidas"
            )
            immediateFeedback = failFeedback
            triggerVibrationHaptic(context, hapticFeedback, isSuccess = false)
            val denied = VisitorScanResult.Denied(
                rawCode = rawCode.take(24),
                reason = "Contenido de código QR no válido para control de accesos"
            )
            activeResult = denied
            onVisitorScanned(denied)
            return
        }

        isProcessing = true
        activeResult = VisitorScanResult.Processing(cleanCode)

        coroutineScope.launch {
            try {
                val now = System.currentTimeMillis()
                val parsedPayload = QrPayloadParser.parse(rawCode)

                // 1. Consultar el pase en Room Database
                val localPass: QrPassRoomEntity? = withContext(Dispatchers.IO) {
                    db.qrPassDao().getPassByCode(cleanCode)
                }

                // Determinar validez del pase
                val isExpired = localPass?.let { now > it.validUntilMillis } ?: false
                val isExhausted = localPass?.let { it.currentEntriesCount >= it.maxEntries } ?: false
                val isInactive = localPass?.let { !it.isActive } ?: false

                if (isExpired) {
                    val failFeedback = ImmediateVisitorFeedback(
                        isSuccess = false,
                        title = "ACCESO DENEGADO",
                        visitorName = localPass?.guestName ?: parsedPayload.guestName ?: "Visitante",
                        destinationHouse = localPass?.destinationHouse ?: parsedPayload.destinationHouse ?: "Sin Casa",
                        code = cleanCode,
                        details = "Pase QR vencido el ${localPass?.formattedValidUntil ?: "fecha expirada"}"
                    )
                    immediateFeedback = failFeedback
                    triggerVibrationHaptic(context, hapticFeedback, isSuccess = false)
                    val denied = VisitorScanResult.Denied(
                        rawCode = cleanCode,
                        reason = "Pase QR vencido el ${localPass?.formattedValidUntil ?: "fecha expirada"}",
                        guestName = localPass?.guestName ?: parsedPayload.guestName,
                        destinationHouse = localPass?.destinationHouse ?: parsedPayload.destinationHouse
                    )
                    activeResult = denied
                    onVisitorScanned(denied)
                    isProcessing = false
                    return@launch
                }

                if (isExhausted) {
                    val failFeedback = ImmediateVisitorFeedback(
                        isSuccess = false,
                        title = "ACCESO DENEGADO",
                        visitorName = localPass?.guestName ?: parsedPayload.guestName ?: "Visitante",
                        destinationHouse = localPass?.destinationHouse ?: parsedPayload.destinationHouse ?: "Sin Casa",
                        code = cleanCode,
                        details = "Superó el límite de ${localPass?.maxEntries} entradas"
                    )
                    immediateFeedback = failFeedback
                    triggerVibrationHaptic(context, hapticFeedback, isSuccess = false)
                    val denied = VisitorScanResult.Denied(
                        rawCode = cleanCode,
                        reason = "Límite de entradas agotado (${localPass?.maxEntries} max)",
                        guestName = localPass?.guestName ?: parsedPayload.guestName,
                        destinationHouse = localPass?.destinationHouse ?: parsedPayload.destinationHouse
                    )
                    activeResult = denied
                    onVisitorScanned(denied)
                    isProcessing = false
                    return@launch
                }

                if (isInactive) {
                    val failFeedback = ImmediateVisitorFeedback(
                        isSuccess = false,
                        title = "ACCESO DENEGADO",
                        visitorName = localPass?.guestName ?: parsedPayload.guestName ?: "Visitante",
                        destinationHouse = localPass?.destinationHouse ?: parsedPayload.destinationHouse ?: "Sin Casa",
                        code = cleanCode,
                        details = "Pase cancelado o inactivo en el sistema"
                    )
                    immediateFeedback = failFeedback
                    triggerVibrationHaptic(context, hapticFeedback, isSuccess = false)
                    val denied = VisitorScanResult.Denied(
                        rawCode = cleanCode,
                        reason = "Pase de visitante inactivo o cancelado",
                        guestName = localPass?.guestName ?: parsedPayload.guestName,
                        destinationHouse = localPass?.destinationHouse ?: parsedPayload.destinationHouse
                    )
                    activeResult = denied
                    onVisitorScanned(denied)
                    isProcessing = false
                    return@launch
                }

                // Resolver datos del visitante
                val visitorName = localPass?.guestName
                    ?: parsedPayload.guestName
                    ?: "Visitante Autorizado ($cleanCode)"
                val destinationHouse = localPass?.destinationHouse
                    ?: parsedPayload.destinationHouse
                    ?: "Unidad Residencial"
                val hostResidentName = localPass?.hostResidentName
                    ?: parsedPayload.hostResidentName
                    ?: "Residente Anfitrión"
                val passTypeLabel = localPass?.passType?.label
                    ?: parsedPayload.passType
                    ?: "Visita Autorizada"
                val vehiclePlate = localPass?.vehiclePlate ?: parsedPayload.vehiclePlate
                val guestDocument = localPass?.guestDocument ?: "INE / Identificación Oficial"

                // 2. Generar Folio Único de Check-In
                val checkInFolio = AlphaCoreEngine.generateUniqueFolio("VIS")

                // 3. Crear entidad estructurada FirestoreVisitorLog para la nube
                val firestoreVisitorLog = FirestoreVisitorLog(
                    folio = checkInFolio,
                    visitorName = visitorName,
                    authorizedUnitNumber = destinationHouse,
                    condominiumId = condominiumId,
                    visitorDocument = guestDocument,
                    passCode = cleanCode,
                    passTypeLabel = passTypeLabel,
                    vehiclePlate = vehiclePlate,
                    status = "CHECKED_IN",
                    guardName = guardName,
                    guardNotes = "Ingreso de visitante autorizado vía escáner CameraX + ZXing",
                    hostResidentName = hostResidentName,
                    timestamp = Timestamp(Date(now)),
                    timestampMillis = now,
                    syncedAtMillis = now
                )

                // 4. Registrar en Firestore si está disponible
                var isFirestoreSynced = false
                val firestore = FirebaseConfigHelper.getFirestore()
                if (firestore != null) {
                    val saveResult = withContext(Dispatchers.IO) {
                        FirestoreTenantManager.saveVisitorLog(
                            firestore = firestore,
                            condominiumId = condominiumId,
                            visitorLog = firestoreVisitorLog
                        )
                    }
                    isFirestoreSynced = saveResult.isSuccess
                }

                // 5. Persistir en Room Database para disponibilidad local inmediata
                val roomCheckIn = VisitorCheckIn(
                    folio = checkInFolio,
                    visitorName = visitorName,
                    visitorDocument = guestDocument,
                    destinationHouse = destinationHouse,
                    passCode = cleanCode,
                    passTypeLabel = passTypeLabel,
                    vehiclePlate = vehiclePlate,
                    status = "CHECKED_IN",
                    guardNotes = "Ingreso validado con escáner CameraX + ZXing",
                    guardName = guardName,
                    hostResidentName = hostResidentName,
                    timestampMillis = now
                )

                withContext(Dispatchers.IO) {
                    db.visitorCheckInDao().insertCheckIn(roomCheckIn)
                    // Si el pase existe en Room, registrar uso adicional
                    if (localPass != null) {
                        db.qrPassDao().incrementUsage(cleanCode)
                    }

                    // Registrar en bitácora inmutable de auditoría
                    val audit = AuditLogEntity(
                        logId = AlphaCoreEngine.generateUniqueFolio("AUD"),
                        timestamp = now,
                        operatorId = guardName,
                        eventDescription = "VISITOR QR SCANNER: Ingreso validado para $visitorName -> $destinationHouse (Código: $cleanCode). Firestore Sync: $isFirestoreSynced",
                        severity = AuditLogEntity.Severity.INFO,
                        forensicPayload = """{"folio":"$checkInFolio","passCode":"$cleanCode","house":"$destinationHouse","firestoreSynced":$isFirestoreSynced}"""
                    )
                    db.auditLogDao().insertAuditLog(audit)
                }

                // 6. Feedback háptico y visual de éxito
                triggerVibrationHaptic(context, hapticFeedback, isSuccess = true)

                val successFeedback = ImmediateVisitorFeedback(
                    isSuccess = true,
                    title = "VISITANTE AUTORIZADO",
                    visitorName = visitorName,
                    destinationHouse = destinationHouse,
                    code = cleanCode,
                    details = "Folio: $checkInFolio • $passTypeLabel"
                )
                immediateFeedback = successFeedback

                val successResult = VisitorScanResult.Success(
                    visitorCheckIn = roomCheckIn,
                    passEntity = localPass,
                    rawCode = cleanCode,
                    folio = checkInFolio,
                    isFirestoreSynced = isFirestoreSynced,
                    message = "Ingreso de visitante confirmado exitosamente"
                )
                activeResult = successResult
                onVisitorScanned(successResult)

            } catch (e: Exception) {
                Log.e(TAG, "Error procesando código de visitante: ${e.message}", e)
                val failFeedback = ImmediateVisitorFeedback(
                    isSuccess = false,
                    title = "ERROR EN PROCESAMIENTO",
                    visitorName = "No procesado",
                    destinationHouse = "Garita",
                    code = cleanCode,
                    details = e.message ?: "Fallo interno al validar el código"
                )
                immediateFeedback = failFeedback
                triggerVibrationHaptic(context, hapticFeedback, isSuccess = false)
                val denied = VisitorScanResult.Denied(
                    rawCode = cleanCode,
                    reason = "Error de validación: ${e.message}"
                )
                activeResult = denied
                onVisitorScanned(denied)
            } finally {
                isProcessing = false
            }
        }
    }

    // Enlazar el ciclo de vida de CameraX con Preview + ImageAnalysis
    LaunchedEffect(hasCameraPermission, cameraLensFacing, isPaused) {
        if (!hasCameraPermission || isPaused) return@LaunchedEffect

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                // Configurar ImageAnalysis con el analizador ZXing
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val analyzer = CameraXVisitorZxingAnalyzer(
                    cooldownMillis = 1500L,
                    onVisitorCodeDecoded = { scannedRaw ->
                        if (!isProcessing) {
                            processVisitorCode(scannedRaw)
                        }
                    }
                )

                imageAnalysis.setAnalyzer(cameraExecutor, analyzer)

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(cameraLensFacing)
                    .build()

                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                // Restaurar estado de linterna si estaba activa
                if (isTorchOn) {
                    camera?.cameraControl?.enableTorch(true)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Fallo al enlazar cámara CameraX: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Interfaz visual principal del escáner CameraX
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NavyDark)
            .testTag("camera_x_visitor_scanner_component")
    ) {
        if (hasCameraPermission) {
            // Vista en vivo con CameraX y SurfaceView
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("camera_x_visitor_preview_view")
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            tapFocusPoint = offset
                            val factory = SurfaceOrientedMeteringPointFactory(
                                size.width.toFloat(),
                                size.height.toFloat()
                            )
                            val point = factory.createPoint(offset.x, offset.y)
                            val action = FocusMeteringAction.Builder(point).build()
                            camera?.cameraControl?.startFocusAndMetering(action)
                        }
                    }
            )

            // Retícula táctica de escaneo superpuesta (HUD)
            TacticalVisitorViewfinderOverlay(
                modifier = Modifier.fillMaxSize(),
                tapPoint = tapFocusPoint,
                isScanningActive = !isPaused && !isProcessing
            )

            // Indicador de tap-to-focus animado
            tapFocusPoint?.let { point ->
                LaunchedEffect(point) {
                    delay(1200)
                    tapFocusPoint = null
                }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .padding(point.x.dp, point.y.dp, 0.dp, 0.dp)
                        .border(1.5.dp, CyanNeon, CircleShape)
                )
            }

        } else {
            // Pantalla informativa de permiso de cámara no concedido
            CameraPermissionRationaleCard(
                onRequestPermission = {
                    try {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error solicitando permiso: ${e.message}")
                    }
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Barra superior con controles de acción (Flash, Cámara, Ingreso Manual, Cerrar)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Badge táctico de estado
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = NavyDark.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Lector Activo",
                        tint = GoldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CAMERAX + ZXING ACTIVO",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Controles de hardware
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (enableTorch && hasCameraPermission) {
                    IconButton(
                        onClick = {
                            isTorchOn = !isTorchOn
                            camera?.cameraControl?.enableTorch(isTorchOn)
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .background(NavyDark.copy(alpha = 0.8f), CircleShape)
                            .border(1.dp, if (isTorchOn) GoldPrimary else Color.White.copy(alpha = 0.3f), CircleShape)
                            .testTag("visitor_scanner_torch_button")
                    ) {
                        Icon(
                            imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Linterna",
                            tint = if (isTorchOn) GoldPrimary else Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (enableCameraFlip && hasCameraPermission) {
                    IconButton(
                        onClick = {
                            cameraLensFacing = if (cameraLensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .background(NavyDark.copy(alpha = 0.8f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                            .testTag("visitor_scanner_flip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Alternar Cámara",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (enableManualEntry) {
                    IconButton(
                        onClick = { showManualDialog = true },
                        modifier = Modifier
                            .size(42.dp)
                            .background(NavyDark.copy(alpha = 0.8f), CircleShape)
                            .border(1.dp, CyanNeon.copy(alpha = 0.6f), CircleShape)
                            .testTag("visitor_scanner_manual_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Entrada Manual",
                            tint = CyanNeon
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(42.dp)
                            .background(NavyDark.copy(alpha = 0.8f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            .testTag("visitor_scanner_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar Escáner",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Banner de feedback inmediato flotante superior
        AnimatedVisibility(
            visible = immediateFeedback != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 74.dp, start = 16.dp, end = 16.dp)
        ) {
            immediateFeedback?.let { feedback ->
                VisitorScanFeedbackBanner(
                    feedback = feedback,
                    onDismiss = { immediateFeedback = null }
                )
            }
        }

        // Indicador de procesamiento en curso
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = NavySurface,
                    border = BorderStroke(1.5.dp, GoldPrimary),
                    shadowElevation = 12.dp
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = GoldPrimary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "VALIDANDO CÓDIGO QR...",
                                color = GoldPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Consultando base de datos y Firestore",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Panel inferior con instrucciones tácticas de escaneo
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = NavyDark.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(GoldPrimary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Escáner",
                        tint = GoldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ENFOQUE EL CÓDIGO QR DEL VISITANTE",
                        color = GoldPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Alinee el pase digital o impreso dentro de las guías amarillas para validar el ingreso.",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }

    // Diálogo modal para ingreso manual de folio si el código QR físico está dañado
    if (showManualDialog) {
        ManualCodeInputDialog(
            code = manualCodeInput,
            onCodeChange = { manualCodeInput = it },
            onDismiss = {
                showManualDialog = false
                manualCodeInput = ""
            },
            onSubmit = { inputCode ->
                showManualDialog = false
                manualCodeInput = ""
                processVisitorCode(inputCode)
            }
        )
    }
}

/**
 * Superposición táctica HUD con retícula, esquinas iluminadas y rayo láser animado.
 */
@Composable
private fun TacticalVisitorViewfinderOverlay(
    modifier: Modifier = Modifier,
    tapPoint: Offset? = null,
    isScanningActive: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "laser_sweep")
    val laserYRatio by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // Determinar ventana central de escaneo proporcional (cuadrada)
        val boxSize = minOf(canvasWidth * 0.72f, canvasHeight * 0.44f, 320.dp.toPx())
        val boxLeft = (canvasWidth - boxSize) / 2f
        val boxTop = (canvasHeight - boxSize) / 2.3f
        val boxRight = boxLeft + boxSize
        val boxBottom = boxTop + boxSize

        // 1. Dibujar máscara de atenuación oscura exterior
        val darkScrim = Color(0x99090D16)
        // Arriba
        drawRect(darkScrim, Offset(0f, 0f), Size(canvasWidth, boxTop))
        // Abajo
        drawRect(darkScrim, Offset(0f, boxBottom), Size(canvasWidth, canvasHeight - boxBottom))
        // Izquierda
        drawRect(darkScrim, Offset(0f, boxTop), Size(boxLeft, boxSize))
        // Derecha
        drawRect(darkScrim, Offset(boxRight, boxTop), Size(canvasWidth - boxRight, boxSize))

        // 2. Borde sutil del recuadro
        drawRoundRect(
            color = Color.White.copy(alpha = 0.15f),
            topLeft = Offset(boxLeft, boxTop),
            size = Size(boxSize, boxSize),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx(), 16.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )

        // 3. Esquinas tácticas doradas (HUD brackets)
        val bracketLength = 32.dp.toPx()
        val bracketStroke = 4.dp.toPx()
        val cornerColor = GoldPrimary

        // Esquina superior izquierda
        drawLine(cornerColor, Offset(boxLeft, boxTop), Offset(boxLeft + bracketLength, boxTop), bracketStroke)
        drawLine(cornerColor, Offset(boxLeft, boxTop), Offset(boxLeft, boxTop + bracketLength), bracketStroke)

        // Esquina superior derecha
        drawLine(cornerColor, Offset(boxRight, boxTop), Offset(boxRight - bracketLength, boxTop), bracketStroke)
        drawLine(cornerColor, Offset(boxRight, boxTop), Offset(boxRight, boxTop + bracketLength), bracketStroke)

        // Esquina inferior izquierda
        drawLine(cornerColor, Offset(boxLeft, boxBottom), Offset(boxLeft + bracketLength, boxBottom), bracketStroke)
        drawLine(cornerColor, Offset(boxLeft, boxBottom), Offset(boxLeft, boxBottom - bracketLength), bracketStroke)

        // Esquina inferior derecha
        drawLine(cornerColor, Offset(boxRight, boxBottom), Offset(boxRight - bracketLength, boxBottom), bracketStroke)
        drawLine(cornerColor, Offset(boxRight, boxBottom), Offset(boxRight, boxBottom - bracketLength), bracketStroke)

        // 4. Rayo láser animado horizontal si el escáner está activo
        if (isScanningActive) {
            val laserY = boxTop + (boxSize * laserYRatio)
            drawLine(
                color = CyanNeon.copy(alpha = 0.85f),
                start = Offset(boxLeft + 8.dp.toPx(), laserY),
                end = Offset(boxRight - 8.dp.toPx(), laserY),
                strokeWidth = 2.5.dp.toPx()
            )
            // Resplandor del rayo láser
            drawLine(
                color = CyanNeon.copy(alpha = 0.25f),
                start = Offset(boxLeft + 12.dp.toPx(), laserY),
                end = Offset(boxRight - 12.dp.toPx(), laserY),
                strokeWidth = 7.dp.toPx()
            )
        }
    }
}

/**
 * Banner superior de feedback inmediato al verificar un código de visitante.
 */
@Composable
private fun VisitorScanFeedbackBanner(
    feedback: ImmediateVisitorFeedback,
    onDismiss: () -> Unit
) {
    val borderColor = if (feedback.isSuccess) SuccessGreen else WarningOrange
    val icon = if (feedback.isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("visitor_scan_result_banner"),
        shape = RoundedCornerShape(16.dp),
        color = NavySurface.copy(alpha = 0.96f),
        border = BorderStroke(1.5.dp, borderColor),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(borderColor.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = borderColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = feedback.title,
                        color = borderColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.White.copy(alpha = 0.1f)
                    ) {
                        Text(
                            text = feedback.code,
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = feedback.visitorName,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Destino: ${feedback.destinationHouse} • ${feedback.details}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Descartar",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Diálogo para ingreso manual de códigos si el QR físico está deteriorado.
 */
@Composable
private fun ManualCodeInputDialog(
    code: String,
    onCodeChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = GoldPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "INGRESO MANUAL DE CÓDIGO",
                    color = GoldPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Ingrese el folio o código alfanumérico del pase de visitante (ejemplo: MED-20260923-1001 o VIS-458):",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = onCodeChange,
                    label = { Text("Código de Pase / Folio") },
                    placeholder = { Text("MED-2026...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_code_input_field")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (code.isNotBlank()) {
                        onSubmit(code.trim())
                    }
                },
                enabled = code.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                modifier = Modifier.testTag("manual_code_submit_button")
            ) {
                Text("Validar Código", color = NavyDark, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = NavySurface,
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * Pantalla informativa de solicitud de permisos de cámara.
 */
@Composable
private fun CameraPermissionRationaleCard(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(24.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(GoldPrimary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Cámara Requerida",
                    tint = GoldPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "PERMISO DE CÁMARA REQUERIDO",
                color = GoldPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "El lector óptico de códigos QR con CameraX y ZXing requiere acceso al sensor de la cámara del dispositivo para verificar los pases de visitantes en la caseta de seguridad.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Conceder Permiso de Cámara",
                    color = NavyDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Diálogo modal para abrir el escáner de códigos de visitantes en pantalla completa.
 */
@Composable
fun CameraXVisitorScannerDialog(
    isOpen: Boolean,
    onDismissRequest: () -> Unit,
    condominiumId: String = "PRADOS_1",
    guardName: String = "Oficial de Seguridad - Garita 1",
    onVisitorScanned: (VisitorScanResult) -> Unit = {}
) {
    if (!isOpen) return

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = NavyDark
        ) {
            CameraXVisitorScannerComponent(
                condominiumId = condominiumId,
                guardName = guardName,
                onVisitorScanned = { result ->
                    onVisitorScanned(result)
                    if (result is VisitorScanResult.Success) {
                        // El usuario o la pantalla decide si mantenerlo abierto o cerrarlo
                    }
                },
                onDismiss = onDismissRequest
            )
        }
    }
}

/**
 * Analizador de fotogramas CameraX YUV_420_888 de alto rendimiento integrado con ZXing.
 *
 * Incluye soporte nativo para decodificación de códigos QR estándar e invertidos
 * (modo oscuro en smartphones de visitantes), rotación adaptativa de planos Y y
 * supresión de lecturas duplicadas mediante cooldown.
 */
class CameraXVisitorZxingAnalyzer(
    private val cooldownMillis: Long = 1200L,
    private val onVisitorCodeDecoded: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(
                BarcodeFormat.QR_CODE,
                BarcodeFormat.DATA_MATRIX,
                BarcodeFormat.AZTEC,
                BarcodeFormat.CODE_128,
                BarcodeFormat.PDF_417
            ),
            DecodeHintType.TRY_HARDER to java.lang.Boolean.TRUE,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )
        setHints(hints)
    }

    private var lastScannedCode: String? = null
    private var lastScannedTimestamp: Long = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null && (
                mediaImage.format == ImageFormat.YUV_420_888 ||
                mediaImage.format == ImageFormat.YUV_422_888 ||
                mediaImage.format == ImageFormat.YUV_444_888
            )
        ) {
            try {
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                val plane = mediaImage.planes[0]
                val width = mediaImage.width
                val height = mediaImage.height

                val rawYData = extractYPlaneData(plane, width, height)
                val rotatedYData = rotateYData(rawYData, width, height, rotationDegrees)

                val (finalWidth, finalHeight) = if (rotationDegrees == 90 || rotationDegrees == 270) {
                    Pair(height, width)
                } else {
                    Pair(width, height)
                }

                val source = PlanarYUVLuminanceSource(
                    rotatedYData,
                    finalWidth,
                    finalHeight,
                    0,
                    0,
                    finalWidth,
                    finalHeight,
                    false
                )
                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

                var decodedText: String? = null
                try {
                    val result = reader.decodeWithState(binaryBitmap)
                    decodedText = result.text
                } catch (_: NotFoundException) {
                    // Fallback para códigos QR en teléfonos con modo oscuro invertido
                    try {
                        val invertedBitmap = BinaryBitmap(HybridBinarizer(source.invert()))
                        val result = reader.decodeWithState(invertedBitmap)
                        decodedText = result.text
                    } catch (_: Exception) {
                        // No detectado en este fotograma
                    }
                }

                val currentTime = System.currentTimeMillis()
                if (!decodedText.isNullOrBlank()) {
                    val isDifferent = decodedText != lastScannedCode
                    val isPastCooldown = currentTime - lastScannedTimestamp > cooldownMillis
                    if (isDifferent || isPastCooldown) {
                        lastScannedCode = decodedText
                        lastScannedTimestamp = currentTime
                        onVisitorCodeDecoded(decodedText)
                    }
                }

            } catch (e: Exception) {
                Log.w(TAG, "Error en análisis ZXing del fotograma: ${e.message}")
            } finally {
                reader.reset()
            }
        }
        imageProxy.close()
    }

    private fun extractYPlaneData(plane: android.media.Image.Plane, width: Int, height: Int): ByteArray {
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        if (rowStride == width) {
            buffer.rewind()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            return bytes
        }
        val bytes = ByteArray(width * height)
        val rowBuffer = ByteArray(rowStride)
        buffer.rewind()
        for (row in 0 until height) {
            val remaining = buffer.remaining()
            if (remaining >= rowStride) {
                buffer.get(rowBuffer, 0, rowStride)
                System.arraycopy(rowBuffer, 0, bytes, row * width, width)
            } else if (remaining > 0) {
                buffer.get(rowBuffer, 0, remaining)
                System.arraycopy(rowBuffer, 0, bytes, row * width, width.coerceAtMost(remaining))
            }
        }
        return bytes
    }

    private fun rotateYData(data: ByteArray, width: Int, height: Int, rotationDegrees: Int): ByteArray {
        if (rotationDegrees == 0) return data
        val rotated = ByteArray(data.size)
        when (rotationDegrees) {
            90 -> {
                var i = 0
                for (x in 0 until width) {
                    for (y in height - 1 downTo 0) {
                        rotated[i++] = data[y * width + x]
                    }
                }
            }
            180 -> {
                for (i in data.indices) {
                    rotated[i] = data[data.size - 1 - i]
                }
            }
            270 -> {
                var i = 0
                for (x in width - 1 downTo 0) {
                    for (y in 0 until height) {
                        rotated[i++] = data[y * width + x]
                    }
                }
            }
            else -> return data
        }
        return rotated
    }
}

/**
 * Disparador de respuesta háptica y vibración física ante escaneo.
 */
private fun triggerVibrationHaptic(
    context: Context,
    haptic: HapticFeedback,
    isSuccess: Boolean
) {
    try {
        if (isSuccess) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    } catch (_: Exception) {}

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            val vibrator = vibratorManager?.defaultVibrator
            if (vibrator?.hasVibrator() == true) {
                if (isSuccess) {
                    vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    val timings = longArrayOf(0, 60, 60, 60)
                    val amplitudes = intArrayOf(0, 180, 0, 180)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator?.hasVibrator() == true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(80)
                }
            }
        }
    } catch (_: Exception) {}
}
