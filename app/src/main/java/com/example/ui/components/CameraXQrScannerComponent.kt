package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.ViewGroup
import android.widget.Toast
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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
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
import com.example.scanner.PassType
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Resultado estructurado del procesamiento de un código QR escaneado.
 */
sealed class QrScanProcessResult {
    data class Success(
        val folio: String,
        val visitorName: String,
        val destinationHouse: String,
        val passCode: String,
        val passTypeLabel: String,
        val hostResidentName: String,
        val vehiclePlate: String?,
        val arrivalTimeMillis: Long,
        val isSyncedToFirestore: Boolean,
        val firestorePath: String,
        val message: String
    ) : QrScanProcessResult()

    data class Denied(
        val passCode: String,
        val reason: String,
        val guestName: String? = null,
        val destinationHouse: String? = null
    ) : QrScanProcessResult()

    data class Error(
        val passCode: String,
        val errorMessage: String
    ) : QrScanProcessResult()

    data class Processing(val code: String) : QrScanProcessResult()
}

/**
 * Estado de retroalimentación inmediata (éxito/fallo) para el visor visual HUD y háptico.
 */
data class ImmediateScanFeedback(
    val isSuccess: Boolean,
    val title: String,
    val subtitle: String,
    val code: String,
    val details: String? = null,
    val isFirestoreSynced: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Componente de escáner de códigos QR basado en CameraX e integrado con ZXing.
 *
 * Características principales:
 * - Ciclo de vida CameraX acoplado a Jetpack Compose con ProcessCameraProvider.
 * - Análisis de fotogramas YUV_420_888 mediante MultiFormatReader de ZXing con detección invertida para modo oscuro.
 * - Decodificación de códigos de acceso y validación defensiva en la base de datos Room.
 * - Creación y actualización automática del registro de visitantes en Firestore (/condominiums/{condoId}/visitor_logs).
 * - Sincronización local en Room (VisitorCheckIn) y bitácora de auditoría inmutable (AuditLogEntity).
 * - Retícula táctica animada (láser de escaneo, esquinas HUD, tap-to-focus).
 * - Linterna/Flash (Torch), alternancia frontal/trasera y fallback de captura manual.
 */
@Composable
fun CameraXQrScannerComponent(
    modifier: Modifier = Modifier,
    condominiumId: String = "PRADOS_1",
    guardName: String = "Oficial de Seguridad - Garita 1",
    onAccessProcessed: (QrScanProcessResult) -> Unit = {},
    onDismissOrClose: (() -> Unit)? = null,
    isPaused: Boolean = false,
    enableTorch: Boolean = true,
    enableCameraFlip: Boolean = true,
    enableManualFallback: Boolean = true
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
    var activeResult by remember { mutableStateOf<QrScanProcessResult?>(null) }
    var immediateFeedback by remember { mutableStateOf<ImmediateScanFeedback?>(null) }
    var showManualDialog by remember { mutableStateOf(false) }
    var manualCodeInput by remember { mutableStateOf("") }
    var tapFocusPoint by remember { mutableStateOf<Offset?>(null) }

    // Auto-dismiss de feedback inmediato tras 4.2 segundos
    LaunchedEffect(immediateFeedback) {
        if (immediateFeedback != null) {
            kotlinx.coroutines.delay(4200)
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

    // Solicitar permiso de cámara automáticamente si aún no ha sido concedido
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            try {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Función interna para procesar el código de acceso capturado y actualizar Firestore
    fun processAccessCode(rawCode: String) {
        if (isProcessing) return
        val cleanCode = QrPayloadParser.extractEntryCode(rawCode)
        if (cleanCode.isBlank()) {
            val failFeedback = ImmediateScanFeedback(
                isSuccess = false,
                title = "CÓDIGO NO VÁLIDO",
                subtitle = "Formato no Reconocido",
                code = rawCode.take(24),
                details = "El código QR escaneado no contiene credenciales válidas"
            )
            immediateFeedback = failFeedback
            triggerScanHaptic(context, hapticFeedback, isSuccess = false)
            val denied = QrScanProcessResult.Denied(
                passCode = rawCode.take(24),
                reason = "Contenido de código QR no válido para control de acceso"
            )
            activeResult = denied
            onAccessProcessed(denied)
            return
        }

        isProcessing = true
        activeResult = QrScanProcessResult.Processing(cleanCode)

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
                    val failFeedback = ImmediateScanFeedback(
                        isSuccess = false,
                        title = "ACCESO DENEGADO",
                        subtitle = "Pase QR Vencido",
                        code = cleanCode,
                        details = "Expiró el ${localPass?.formattedValidUntil ?: "fecha pasada"}"
                    )
                    immediateFeedback = failFeedback
                    triggerScanHaptic(context, hapticFeedback, isSuccess = false)
                    val denied = QrScanProcessResult.Denied(
                        passCode = cleanCode,
                        reason = "Pase QR vencido el ${localPass?.formattedValidUntil ?: "fecha pasada"}",
                        guestName = localPass?.guestName,
                        destinationHouse = localPass?.destinationHouse
                    )
                    activeResult = denied
                    onAccessProcessed(denied)
                    isProcessing = false
                    return@launch
                }

                if (isExhausted) {
                    val failFeedback = ImmediateScanFeedback(
                        isSuccess = false,
                        title = "ACCESO DENEGADO",
                        subtitle = "Límite Agotado",
                        code = cleanCode,
                        details = "Superó el límite de ${localPass?.maxEntries} entradas"
                    )
                    immediateFeedback = failFeedback
                    triggerScanHaptic(context, hapticFeedback, isSuccess = false)
                    val denied = QrScanProcessResult.Denied(
                        passCode = cleanCode,
                        reason = "Límite de entradas agotado (${localPass?.maxEntries} max)",
                        guestName = localPass?.guestName,
                        destinationHouse = localPass?.destinationHouse
                    )
                    activeResult = denied
                    onAccessProcessed(denied)
                    isProcessing = false
                    return@launch
                }

                if (isInactive) {
                    val failFeedback = ImmediateScanFeedback(
                        isSuccess = false,
                        title = "ACCESO DENEGADO",
                        subtitle = "Pase Revocado / Inactivo",
                        code = cleanCode,
                        details = "Pase cancelado o inactivo en el sistema"
                    )
                    immediateFeedback = failFeedback
                    triggerScanHaptic(context, hapticFeedback, isSuccess = false)
                    val denied = QrScanProcessResult.Denied(
                        passCode = cleanCode,
                        reason = "Pase QR cancelado o revocado en el sistema",
                        guestName = localPass?.guestName,
                        destinationHouse = localPass?.destinationHouse
                    )
                    activeResult = denied
                    onAccessProcessed(denied)
                    isProcessing = false
                    return@launch
                }

                // Definir datos consolidados del visitante
                val visitorName = localPass?.guestName
                    ?: parsedPayload.guestName
                    ?: "Visitante Autorizado ($cleanCode)"
                val destinationHouse = localPass?.destinationHouse
                    ?: parsedPayload.destinationHouse
                    ?: "Casa de Prados Residencial"
                val hostResidentName = localPass?.hostResidentName
                    ?: parsedPayload.hostResidentName
                    ?: "Residente Anfitrión"
                val passTypeLabel = localPass?.passType?.label
                    ?: parsedPayload.passType
                    ?: "Visita Autorizada"
                val vehiclePlate = localPass?.vehiclePlate ?: parsedPayload.vehiclePlate
                val guestDocument = localPass?.guestDocument ?: "INE / Identificación Oficial"

                // 2. Generar Folio Único de Check-In
                val checkInFolio = AlphaCoreEngine.generateUniqueFolio("CHK")

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
                    guardNotes = "Ingreso autorizado y registrado mediante escáner CameraX + ZXing",
                    hostResidentName = hostResidentName,
                    timestamp = Timestamp(Date(now)),
                    timestampMillis = now,
                    syncedAtMillis = now
                )

                // 4. Actualizar Firestore Visitor Log
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

                // 5. Persistir en Room Database para disponibilidad offline y reactividad en vivo
                val roomCheckIn = VisitorCheckIn(
                    folio = checkInFolio,
                    visitorName = visitorName,
                    visitorDocument = guestDocument,
                    destinationHouse = destinationHouse,
                    passCode = cleanCode,
                    passTypeLabel = passTypeLabel,
                    vehiclePlate = vehiclePlate,
                    status = "CHECKED_IN",
                    guardNotes = "Ingreso validado con escáner CameraX",
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
                    // Registrar en bitácora de auditoría inmutable
                    val auditLog = AuditLogEntity(
                        logId = AlphaCoreEngine.generateUniqueFolio("AUD"),
                        timestamp = now,
                        operatorId = guardName,
                        eventDescription = "QR SCANNER: Ingreso validado para $visitorName -> $destinationHouse (Pase: $cleanCode). Firestore Sync: $isFirestoreSynced",
                        severity = AuditLogEntity.Severity.INFO,
                        forensicPayload = """{"folio":"$checkInFolio","passCode":"$cleanCode","house":"$destinationHouse","firestoreSynced":$isFirestoreSynced}"""
                    )
                    db.auditLogDao().insertAuditLog(auditLog)
                }

                // 6. Feedback háptico de éxito y overlay visual inmediato
                val successFeedback = ImmediateScanFeedback(
                    isSuccess = true,
                    title = "¡ACCESO AUTORIZADO!",
                    subtitle = "$visitorName -> $destinationHouse",
                    code = cleanCode,
                    details = "Folio: $checkInFolio • Tipo: $passTypeLabel",
                    isFirestoreSynced = isFirestoreSynced
                )
                immediateFeedback = successFeedback
                triggerScanHaptic(context, hapticFeedback, isSuccess = true)

                val successResult = QrScanProcessResult.Success(
                    folio = checkInFolio,
                    visitorName = visitorName,
                    destinationHouse = destinationHouse,
                    passCode = cleanCode,
                    passTypeLabel = passTypeLabel,
                    hostResidentName = hostResidentName,
                    vehiclePlate = vehiclePlate,
                    arrivalTimeMillis = now,
                    isSyncedToFirestore = isFirestoreSynced,
                    firestorePath = "/condominiums/$condominiumId/visitor_logs/$checkInFolio",
                    message = "Ingreso autorizado y registrado exitosamente"
                )

                activeResult = successResult
                onAccessProcessed(successResult)

            } catch (e: Exception) {
                e.printStackTrace()
                val failFeedback = ImmediateScanFeedback(
                    isSuccess = false,
                    title = "ERROR DE PROCESAMIENTO",
                    subtitle = "Fallo al validar código",
                    code = cleanCode,
                    details = e.message ?: "Error al registrar el acceso"
                )
                immediateFeedback = failFeedback
                triggerScanHaptic(context, hapticFeedback, isSuccess = false)
                val errorResult = QrScanProcessResult.Error(
                    passCode = cleanCode,
                    errorMessage = e.message ?: "Error al procesar el código de acceso"
                )
                activeResult = errorResult
            } finally {
                isProcessing = false
            }
        }
    }

    // Enlazar CameraX Lifecycle
    LaunchedEffect(hasCameraPermission, cameraLensFacing, isPaused, lifecycleOwner) {
        if (!hasCameraPermission || isPaused) {
            camera = null
            return@LaunchedEffect
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analyzer = CameraXQrCodeZxingAnalyzer(
                    cooldownMillis = 1500L,
                    onQrCodeScanned = { rawCode ->
                        if (!isPaused && !isProcessing && activeResult == null) {
                            // Feedback táctil instantáneo al momento de decodificar fotograma
                            triggerInstantDecodeTick(context, hapticFeedback)
                            coroutineScope.launch(Dispatchers.Main) {
                                processAccessCode(rawCode)
                            }
                        }
                    }
                )

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build().apply {
                        setAnalyzer(cameraExecutor, analyzer)
                    }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(cameraLensFacing)
                    .build()

                cameraProvider.unbindAll()
                val boundCamera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                camera = boundCamera

                if (isTorchOn) {
                    boundCamera.cameraControl.enableTorch(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Contenedor principal con diseño táctico de seguridad
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NavyDark)
            .border(
                1.5.dp,
                when (activeResult) {
                    is QrScanProcessResult.Success -> SuccessGreen
                    is QrScanProcessResult.Denied -> WarningOrange
                    else -> GoldPrimary.copy(alpha = 0.5f)
                },
                RoundedCornerShape(20.dp)
            )
            .testTag("camerax_qr_scanner_component")
    ) {
        // Encabezado del Escáner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NavyCard)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "ESCÁNER CAMERAX + ZXING",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Actualiza bitácora en Firestore & SQLite",
                        color = CyanNeon,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Indicador de conexión con Firestore
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (FirebaseConfigHelper.getFirestore() != null) SuccessGreen.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, if (FirebaseConfigHelper.getFirestore() != null) SuccessGreen.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (FirebaseConfigHelper.getFirestore() != null) Icons.Default.CloudDone else Icons.Default.Storage,
                            contentDescription = null,
                            tint = if (FirebaseConfigHelper.getFirestore() != null) SuccessGreen else GoldPrimary,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = if (FirebaseConfigHelper.getFirestore() != null) "Firestore Online" else "SQLite Cache",
                            color = if (FirebaseConfigHelper.getFirestore() != null) SuccessGreen else GoldPrimary,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (onDismissOrClose != null) {
                    IconButton(
                        onClick = onDismissOrClose,
                        modifier = Modifier.size(28.dp).testTag("btn_close_scanner")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar Escáner",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Visor de la Cámara con Overlay HUD Táctico
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(Color.Black)
                .pointerInput(camera, isPaused) {
                    if (!isPaused) {
                        detectTapGestures { offset ->
                            camera?.let { cam ->
                                try {
                                    tapFocusPoint = offset
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
                }
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                // Flash visual reactivo en todo el visor al decodificar fotograma
                val flashColor by animateColorAsState(
                    targetValue = when {
                        immediateFeedback?.isSuccess == true -> SuccessGreen.copy(alpha = 0.22f)
                        immediateFeedback?.isSuccess == false -> WarningOrange.copy(alpha = 0.28f)
                        isProcessing -> GoldPrimary.copy(alpha = 0.12f)
                        else -> Color.Transparent
                    },
                    animationSpec = tween(durationMillis = 350),
                    label = "viewfinder_flash_color"
                )

                if (flashColor != Color.Transparent) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(flashColor)
                    )
                }

                // Retícula táctica con animación láser ZXing y estado dinámico
                TacticalQrScannerHudOverlay(
                    modifier = Modifier.fillMaxSize(),
                    isProcessing = isProcessing,
                    isSuccess = immediateFeedback?.isSuccess == true || activeResult is QrScanProcessResult.Success,
                    isDenied = (immediateFeedback != null && !immediateFeedback!!.isSuccess) || activeResult is QrScanProcessResult.Denied || activeResult is QrScanProcessResult.Error
                )

                // Animación visual de enfoque táctil
                tapFocusPoint?.let { point ->
                    TapToFocusIndicator(
                        point = point,
                        onAnimationEnd = { tapFocusPoint = null }
                    )
                }

                // OVERLAY VISUAL DE ESTADO INMEDIATO (ÉXITO / FALLO) TRAS LA DECODIFICACIÓN
                androidx.compose.animation.AnimatedVisibility(
                    visible = immediateFeedback != null,
                    enter = fadeIn(tween(160)) + scaleIn(
                        initialScale = 0.75f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ),
                    exit = fadeOut(tween(220)) + scaleOut(
                        targetScale = 0.88f,
                        animationSpec = tween(220)
                    ),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 20.dp)
                ) {
                    val currentFeedback = immediateFeedback
                    if (currentFeedback != null) {
                        ImmediateStatusFeedbackOverlay(
                            feedback = currentFeedback,
                            onDismiss = {
                                immediateFeedback = null
                                activeResult = null
                            }
                        )
                    }
                }

                // Barra de herramientas flotante sobre la cámara (Linterna, Girar, Teclado Manual)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (enableTorch) {
                        IconButton(
                            onClick = {
                                camera?.let { cam ->
                                    val nextState = !isTorchOn
                                    cam.cameraControl.enableTorch(nextState)
                                    isTorchOn = nextState
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isTorchOn) GoldPrimary else Color.Black.copy(alpha = 0.55f))
                                .border(1.dp, GoldPrimary.copy(alpha = 0.6f), CircleShape)
                                .testTag("btn_toggle_torch")
                        ) {
                            Icon(
                                imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Linterna",
                                tint = if (isTorchOn) NavyDark else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (enableCameraFlip) {
                            IconButton(
                                onClick = {
                                    cameraLensFacing = if (cameraLensFacing == CameraSelector.LENS_FACING_BACK) {
                                        CameraSelector.LENS_FACING_FRONT
                                    } else {
                                        CameraSelector.LENS_FACING_BACK
                                    }
                                    isTorchOn = false
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .border(1.dp, CyanNeon.copy(alpha = 0.6f), CircleShape)
                                    .testTag("btn_flip_camera")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FlipCameraAndroid,
                                    contentDescription = "Cambiar Cámara",
                                    tint = CyanNeon,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (enableManualFallback) {
                            IconButton(
                                onClick = { showManualDialog = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .border(1.dp, GoldPrimary.copy(alpha = 0.6f), CircleShape)
                                    .testTag("btn_manual_entry")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Entrada Manual",
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                // Indicador inferior dentro del visor con feedback de estado y háptico
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = BorderStroke(
                        1.dp,
                        when {
                            immediateFeedback?.isSuccess == true -> SuccessGreen.copy(alpha = 0.7f)
                            immediateFeedback?.isSuccess == false -> WarningOrange.copy(alpha = 0.7f)
                            isProcessing -> GoldPrimary.copy(alpha = 0.5f)
                            else -> Color.White.copy(alpha = 0.15f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val statusDotColor = when {
                            immediateFeedback?.isSuccess == true -> SuccessGreen
                            immediateFeedback?.isSuccess == false -> WarningOrange
                            isProcessing -> GoldPrimary
                            else -> CyanNeon
                        }
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                        Text(
                            text = when {
                                immediateFeedback?.isSuccess == true -> "✓ Código Válido • Feedback háptico confirmado"
                                immediateFeedback?.isSuccess == false -> "⛔ Acceso denegado • Alerta háptica ejecutada"
                                isProcessing -> "⏳ Procesando código en Firestore..."
                                else -> "Alinee el código QR dentro del marco"
                            },
                            color = when {
                                immediateFeedback?.isSuccess == true -> SuccessGreen
                                immediateFeedback?.isSuccess == false -> WarningOrange
                                isProcessing -> GoldPrimary
                                else -> Color.White.copy(alpha = 0.85f)
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

            } else {
                // Estado sin permiso de cámara
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = WarningOrange,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Permiso de Cámara Requerido",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "El escaneo óptico con CameraX y ZXing requiere acceso al sensor de la cámara.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Conceder Permiso", color = NavyDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // =========================================================================
        // Panel Inferior: Resultado de Procesamiento en Firestore & Room
        // =========================================================================
        AnimatedVisibility(
            visible = activeResult != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            when (val res = activeResult) {
                is QrScanProcessResult.Success -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = NavySurface,
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "ACCESO AUTORIZADO",
                                        color = SuccessGreen,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SuccessGreen.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f))
                                ) {
                                    Text(
                                        text = res.folio,
                                        color = SuccessGreen,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = res.visitorName,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(imageVector = Icons.Default.Home, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(13.dp))
                                    Text(text = res.destinationHouse, color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }

                                res.vehiclePlate?.let { plate ->
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                                        Text(text = plate, color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Badge de Sincronización en Firestore
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (res.isSyncedToFirestore) SuccessGreen.copy(alpha = 0.12f) else WarningOrange.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, if (res.isSyncedToFirestore) SuccessGreen.copy(alpha = 0.3f) else WarningOrange.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (res.isSyncedToFirestore) Icons.Default.CloudDone else Icons.Default.Storage,
                                        contentDescription = null,
                                        tint = if (res.isSyncedToFirestore) SuccessGreen else WarningOrange,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (res.isSyncedToFirestore) "Sincronizado en Firestore (${res.firestorePath})" else "Guardado en SQLite Local (Pendiente Sync)",
                                        color = if (res.isSyncedToFirestore) SuccessGreen else WarningOrange,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { activeResult = null },
                                    modifier = Modifier.weight(1f).testTag("btn_scan_next"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                                ) {
                                    Text("Escanear Siguiente", color = NavyDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                if (onDismissOrClose != null) {
                                    OutlinedButton(
                                        onClick = onDismissOrClose,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                                    ) {
                                        Text("Cerrar", color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                is QrScanProcessResult.Denied -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = NavySurface,
                        border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = WarningOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "ACCESO RECHAZADO",
                                    color = WarningOrange,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = res.reason,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Text(
                                text = "Código: ${res.passCode}",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = { activeResult = null },
                                modifier = Modifier.fillMaxWidth().testTag("btn_retry_scan"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = WarningOrange)
                            ) {
                                Text("Reintentar Escaneo", color = NavyDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                is QrScanProcessResult.Error -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = NavySurface,
                        border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = WarningOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "ERROR DE PROCESAMIENTO",
                                    color = WarningOrange,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = res.errorMessage,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = { activeResult = null },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = WarningOrange)
                            ) {
                                Text("Aceptar y Reintentar", color = NavyDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                is QrScanProcessResult.Processing -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = NavySurface,
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = GoldPrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Validando pase y actualizando Firestore...",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                null -> {}
            }
        }
    }

    // Diálogo de Captura Manual de Folio de Acceso
    if (showManualDialog) {
        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            containerColor = NavyCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = GoldPrimary)
                    Text("Ingreso Manual de Código", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Ingrese el folio del pase QR impreso o digital (ej: MED-PRADOS-QR012):",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp
                    )

                    OutlinedTextField(
                        value = manualCodeInput,
                        onValueChange = { manualCodeInput = it.uppercase() },
                        placeholder = { Text("MED-PRADOS-...", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("input_manual_qr_code"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = manualCodeInput.trim()
                        if (input.isNotBlank()) {
                            showManualDialog = false
                            processAccessCode(input)
                            manualCodeInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("btn_confirm_manual_qr")
                ) {
                    Text("Validar e Ingresar", color = NavyDark, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualDialog = false }) {
                    Text("Cancelar", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
        )
    }
}

/**
 * Diálogo modal de pantalla completa para abrir el escáner de códigos QR desde cualquier pantalla.
 */
@Composable
fun CameraXQrScannerDialog(
    onDismiss: () -> Unit,
    condominiumId: String = "PRADOS_1",
    guardName: String = "Oficial de Seguridad - Garita 1",
    onAccessProcessed: (QrScanProcessResult) -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(NavyDark.copy(alpha = 0.95f))
                .padding(16.dp),
            color = Color.Transparent
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CameraXQrScannerComponent(
                    condominiumId = condominiumId,
                    guardName = guardName,
                    onAccessProcessed = onAccessProcessed,
                    onDismissOrClose = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                )
            }
        }
    }
}

/**
 * Overlay HUD táctico de alta visibilidad para el visor de la cámara.
 * Dibuja las esquinas de enfoque dinámicas, cuadrícula y el rayo láser animado o bloqueado.
 */
@Composable
private fun TacticalQrScannerHudOverlay(
    modifier: Modifier = Modifier,
    isProcessing: Boolean,
    isSuccess: Boolean,
    isDenied: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "laser_transition")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isProcessing) 850 else 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_sweep"
    )

    val reticleColor by animateColorAsState(
        targetValue = when {
            isSuccess -> SuccessGreen
            isDenied -> WarningOrange
            isProcessing -> GoldPrimary
            else -> CyanNeon
        },
        animationSpec = tween(280),
        label = "reticle_color_anim"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val boxSize = (width.coerceAtMost(height) * 0.72f).coerceAtMost(250.dp.toPx())
        val left = (width - boxSize) / 2f
        val top = (height - boxSize) / 2f
        val right = left + boxSize
        val bottom = top + boxSize
        val cornerLength = if (isSuccess || isDenied) 44.dp.toPx() else 36.dp.toPx()
        val cornerStroke = if (isSuccess || isDenied) 5.dp.toPx() else 4.dp.toPx()

        // 1. Esquinas de Enfoque HUD (Top-Left, Top-Right, Bottom-Left, Bottom-Right)
        // Top-Left
        drawLine(reticleColor, Offset(left, top), Offset(left + cornerLength, top), cornerStroke)
        drawLine(reticleColor, Offset(left, top), Offset(left, top + cornerLength), cornerStroke)

        // Top-Right
        drawLine(reticleColor, Offset(right, top), Offset(right - cornerLength, top), cornerStroke)
        drawLine(reticleColor, Offset(right, top), Offset(right, top + cornerLength), cornerStroke)

        // Bottom-Left
        drawLine(reticleColor, Offset(left, bottom), Offset(left + cornerLength, bottom), cornerStroke)
        drawLine(reticleColor, Offset(left, bottom), Offset(left, bottom - cornerLength), cornerStroke)

        // Bottom-Right
        drawLine(reticleColor, Offset(right, bottom), Offset(right - cornerLength, bottom), cornerStroke)
        drawLine(reticleColor, Offset(right, bottom), Offset(right, bottom - cornerLength), cornerStroke)

        // 2. Rayo Láser de Escaneo Óptico o línea fija de confirmación
        if (isSuccess || isDenied) {
            val midY = top + (boxSize / 2f)
            drawLine(
                color = reticleColor,
                start = Offset(left + 4.dp.toPx(), midY),
                end = Offset(right - 4.dp.toPx(), midY),
                strokeWidth = 3.5.dp.toPx()
            )
        } else {
            val laserY = top + (boxSize * laserProgress)
            drawLine(
                color = reticleColor.copy(alpha = 0.85f),
                start = Offset(left + 6.dp.toPx(), laserY),
                end = Offset(right - 6.dp.toPx(), laserY),
                strokeWidth = 2.5.dp.toPx()
            )
        }
    }
}

/**
 * Tarjeta HUD visual que se superpone inmediatamente en el centro del visor
 * de la cámara al decodificar un código, proporcionando feedback instantáneo (éxito o rechazo).
 */
@Composable
private fun ImmediateStatusFeedbackOverlay(
    feedback: ImmediateScanFeedback,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = if (feedback.isSuccess) SuccessGreen else WarningOrange
    val statusIcon = if (feedback.isSuccess) Icons.Default.CheckCircle else Icons.Default.Warning

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("immediate_status_feedback_overlay"),
        shape = RoundedCornerShape(20.dp),
        color = NavyDark.copy(alpha = 0.94f),
        border = BorderStroke(2.dp, statusColor),
        shadowElevation = 14.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icono central con halo de color
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.18f))
                    .border(1.5.dp, statusColor.copy(alpha = 0.8f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = if (feedback.isSuccess) "Éxito" else "Fallo",
                    tint = statusColor,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Título de estado
            Text(
                text = feedback.title,
                color = statusColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Subtítulo (Nombre visitante o Motivo de denegación)
            Text(
                text = feedback.subtitle,
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!feedback.details.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = feedback.details,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 10.5.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Fila de Badges Técnicos (Folio, Háptico, Sincronización)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Badge de Folio / Código
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = feedback.code.take(16),
                        color = CyanNeon,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Badge Háptico
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "📳 Háptico OK",
                        color = statusColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (feedback.isSuccess && feedback.isFirestoreSynced) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SuccessGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = "☁️ Firestore",
                            color = SuccessGreen,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botón de acción rápida para reanudar el escaneo
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .testTag("btn_dismiss_immediate_feedback"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = statusColor,
                    contentColor = NavyDark
                )
            ) {
                Icon(
                    imageVector = if (feedback.isSuccess) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = NavyDark
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (feedback.isSuccess) "Listo • Siguiente Escaneo" else "Reintentar Escaneo",
                    color = NavyDark,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Indicador visual animado al tocar la pantalla para enfocar (Tap-to-Focus).
 */
@Composable
private fun TapToFocusIndicator(
    point: Offset,
    onAnimationEnd: () -> Unit
) {
    var animationTrigger by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (animationTrigger) 0.6f else 1.2f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "focus_scale"
    )

    LaunchedEffect(point) {
        animationTrigger = true
        delay(400)
        onAnimationEnd()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color = GoldPrimary,
            radius = 28.dp.toPx() * scale,
            center = point,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

/**
 * Emite un micro-tick táctil instantáneo en el momento exacto en que ZXing decodifica un código QR.
 */
private fun triggerInstantDecodeTick(context: Context, hapticFeedback: HapticFeedback? = null) {
    try {
        hapticFeedback?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    } catch (_: Exception) {}

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator?.let { vibrator ->
                if (vibrator.hasVibrator()) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            @Suppress("DEPRECATION")
            vibrator?.vibrate(25L)
        }
    } catch (_: Exception) {}
}

/**
 * Emite patrones de respuesta háptica diferenciada para éxito o fallo tras la decodificación y validación.
 * - Éxito: Doble pulso rítmico y limpio de confirmación.
 * - Fallo: Triple pulso pesado de advertencia/rechazo.
 */
private fun triggerScanHaptic(
    context: Context,
    hapticFeedback: HapticFeedback? = null,
    isSuccess: Boolean = true
) {
    // 1. Compose UI Haptic Feedback
    try {
        hapticFeedback?.performHapticFeedback(HapticFeedbackType.LongPress)
    } catch (_: Exception) {}

    // 2. Android Vibrator Service
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            val vibrator = vibratorManager?.defaultVibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (isSuccess) {
                    val timings = longArrayOf(0, 45, 55, 90)
                    val amplitudes = intArrayOf(0, 190, 0, 255)
                    if (vibrator.hasAmplitudeControl()) {
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    } else {
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
                    }
                } else {
                    val timings = longArrayOf(0, 90, 60, 90, 60, 180)
                    val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255)
                    if (vibrator.hasAmplitudeControl()) {
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    } else {
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
                    }
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (isSuccess) {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 45, 55, 90), -1))
                } else {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 90, 60, 90, 60, 180), -1))
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            @Suppress("DEPRECATION")
            if (isSuccess) {
                vibrator?.vibrate(longArrayOf(0, 45, 55, 90), -1)
            } else {
                vibrator?.vibrate(longArrayOf(0, 90, 60, 90, 60, 180), -1)
            }
        }
    } catch (_: Exception) {}
}

/**
 * Analizador de códigos QR integrado con ZXing para fotogramas CameraX YUV_420_888.
 */
class CameraXQrCodeZxingAnalyzer(
    private val cooldownMillis: Long = 1500L,
    private val onQrCodeScanned: (String) -> Unit
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

    private var lastScannedValue: String? = null
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
                    // Fallback con inversión de luminancia para pantallas de teléfonos en modo oscuro
                    try {
                        val invertedBitmap = BinaryBitmap(HybridBinarizer(source.invert()))
                        val result = reader.decodeWithState(invertedBitmap)
                        decodedText = result.text
                    } catch (_: Exception) {
                        // Código no detectado en este fotograma
                    }
                }

                val currentTime = System.currentTimeMillis()
                if (!decodedText.isNullOrBlank()) {
                    val isDifferent = decodedText != lastScannedValue
                    val isPastCooldown = currentTime - lastScannedTimestamp > cooldownMillis
                    if (isDifferent || isPastCooldown) {
                        lastScannedValue = decodedText
                        lastScannedTimestamp = currentTime
                        onQrCodeScanned(decodedText)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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

    private fun rotateYData(data: ByteArray, width: Int, height: Int, rotation: Int): ByteArray {
        if (rotation == 0) return data
        val rotated = ByteArray(data.size)
        when (rotation) {
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
                    rotated[data.size - 1 - i] = data[i]
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
