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
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavyCard
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Componente dedicado de escaneo de accesos vehiculares y peatonales basado en CameraX
 * y decodificación óptica de alto rendimiento mediante bibliotecas ZXing preexistentes.
 *
 * Ofrece:
 * - Ciclo de vida CameraX enlazado a Jetpack Compose.
 * - Análisis de fotogramas YUV-420-888 con rotación adaptativa para QR y códigos de barra.
 * - Soporte para QR oscuros/modo nocturno mediante inversión de luminancia ZXing.
 * - Retícula HUD táctica con rayo láser animado y esquinas de enfoque.
 * - Feedback háptico y visual ante lectura exitosa.
 * - Control de linterna / flash (torch), alternancia de cámara frontal/trasera y tap-to-focus.
 * - Diálogo de respaldo para captura manual de folios si el código físico está dañado.
 */
@Composable
fun CameraXEntryScannerComponent(
    onEntryScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
    isPaused: Boolean = false,
    title: String = "ESCÁNER DE ACCESO VEHICULAR Y PEATONAL",
    subtitle: String = "Alinee el código QR de acceso dentro del marco táctico",
    enableFlashlight: Boolean = true,
    enableCameraFlip: Boolean = true,
    enableManualEntryFallback: Boolean = true,
    onManualEntrySubmitted: ((String) -> Unit)? = null,
    cooldownMillis: Long = 1500L
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

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
    var isScanSuccessActive by remember { mutableStateOf(false) }
    var lastScannedCodePreview by remember { mutableStateOf<String?>(null) }

    // Tap-to-focus visual cue state
    var tapFocusPoint by remember { mutableStateOf<Offset?>(null) }

    // Manual entry dialog state
    var showManualDialog by remember { mutableStateOf(false) }
    var manualCodeInput by remember { mutableStateOf("") }

    // Single-thread analysis executor for camera frames
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

    // Auto-request camera permission if not granted
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            try {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // CameraX Lifecycle binding
    LaunchedEffect(hasCameraPermission, cameraLensFacing, isPaused, lifecycleOwner) {
        if (!hasCameraPermission || isPaused) {
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

                val entryAnalyzer = EntryQrCodeZxingAnalyzer(
                    cooldownMillis = cooldownMillis,
                    onQrDecoded = { decodedString ->
                        if (!isPaused && !isScanSuccessActive) {
                            coroutineScope.launch(Dispatchers.Main) {
                                triggerScanHaptic(context)
                                lastScannedCodePreview = decodedString
                                isScanSuccessActive = true
                                onEntryScanned(decodedString)
                                delay(1000)
                                isScanSuccessActive = false
                            }
                        }
                    }
                )

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build().apply {
                        setAnalyzer(cameraExecutor, entryAnalyzer)
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NavyDark)
            .border(2.dp, if (isScanSuccessActive) SuccessGreen else GoldPrimary.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .testTag("camera_entry_scanner_root")
    ) {
        // Encabezado táctico de garita
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(NavyCard)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = subtitle,
                        color = CyanNeon,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Pill de nivel de batería de caseta
            BatteryIndicatorPill(showDetailedLabel = false)
        }

        // Ventana del visor de cámara CameraX
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
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
                .testTag("camera_entry_preview")
        ) {
            if (!hasCameraPermission) {
                // UI cuando el permiso no está otorgado
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = WarningOrange,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Permiso de Cámara Requerido",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Para verificar códigos QR vehiculares y peatonales en tiempo real, habilite el acceso a la cámara.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            try {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("camera_entry_permission_button")
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Habilitar Cámara de Garita", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            } else {
                // Vista en vivo con CameraX y SurfaceView
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                // Retícula táctica de escaneo con láser animado
                EntryScannerReticle(isSuccess = isScanSuccessActive)

                // Anillo de enfoque táctil visual
                tapFocusPoint?.let { focusOffset ->
                    FocusRingIndicator(
                        centerOffset = focusOffset,
                        onAnimationEnd = { tapFocusPoint = null }
                    )
                }

                // Overlay de detección exitosa
                androidx.compose.animation.AnimatedVisibility(
                    visible = isScanSuccessActive,
                    enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.8f, animationSpec = tween(200)),
                    exit = fadeOut(tween(250)) + scaleOut(targetScale = 0.95f, animationSpec = tween(250)),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = SuccessGreen.copy(alpha = 0.95f),
                        shadowElevation = 8.dp,
                        border = BorderStroke(2.dp, Color.White),
                        modifier = Modifier.testTag("camera_entry_success_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Acceso Detectado",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "¡PASE DETECTADO!",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                lastScannedCodePreview?.let { code ->
                                    Text(
                                        text = code.take(24),
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Controles flotantes superiores (Linterna / Giro de Cámara)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (enableFlashlight) {
                        IconButton(
                            onClick = {
                                val nextState = !isTorchOn
                                camera?.cameraControl?.enableTorch(nextState)
                                isTorchOn = nextState
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(NavyDark.copy(alpha = 0.8f), CircleShape)
                                .border(1.dp, if (isTorchOn) GoldPrimary else Color.White.copy(alpha = 0.4f), CircleShape)
                                .testTag("camera_entry_torch_button")
                        ) {
                            Icon(
                                imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Linterna",
                                tint = if (isTorchOn) GoldPrimary else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (enableCameraFlip) {
                        IconButton(
                            onClick = {
                                cameraLensFacing = if (cameraLensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(NavyDark.copy(alpha = 0.8f), CircleShape)
                                .border(1.dp, CyanNeon, CircleShape)
                                .testTag("camera_entry_flip_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlipCameraAndroid,
                                contentDescription = "Alternar Cámara",
                                tint = CyanNeon,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Etiqueta informativa inferior con toque para enfocar
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = NavyDark.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = null,
                            tint = CyanNeon,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Toque la pantalla para reenfocar el sensor",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Barra inferior: Acceso manual de respaldo para el guardia
        if (enableManualEntryFallback) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NavyCard)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Lector CameraX + ZXing Óptico",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
                OutlinedButton(
                    onClick = { showManualDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                    modifier = Modifier.testTag("camera_entry_manual_button")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ingreso Manual", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Modal de entrada manual de respaldo
    if (showManualDialog) {
        AlertDialog(
            onDismissRequest = {
                showManualDialog = false
                manualCodeInput = ""
            },
            containerColor = NavyDark,
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = GoldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ingreso Manual de Folio", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Si la pantalla del visitante presenta reflejo o daño, digite el código o folio QR aquí:",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = manualCodeInput,
                        onValueChange = { manualCodeInput = it.uppercase() },
                        placeholder = { Text("Ej: MED-20260914-XXXX", color = Color.Gray, fontSize = 12.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("camera_entry_manual_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = manualCodeInput.trim()
                        if (trimmed.isNotBlank()) {
                            showManualDialog = false
                            manualCodeInput = ""
                            onManualEntrySubmitted?.invoke(trimmed) ?: onEntryScanned(trimmed)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                    modifier = Modifier.testTag("camera_entry_manual_submit_button")
                ) {
                    Text("Validar Acceso", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showManualDialog = false
                        manualCodeInput = ""
                    }
                ) {
                    Text("Cancelar", color = Color.Gray)
                }
            }
        )
    }
}

/**
 * Retícula táctica de escaneo para garitas de acceso vehicular/peatonal.
 */
@Composable
private fun EntryScannerReticle(isSuccess: Boolean = false) {
    val infiniteTransition = rememberInfiniteTransition(label = "entry_laser_anim")
    val laserYRatio by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(1250, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "entry_laser_y"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val boxSize = width.coerceAtMost(height) * 0.72f
        val left = (width - boxSize) / 2f
        val top = (height - boxSize) / 2f
        val right = left + boxSize
        val bottom = top + boxSize

        val cornerLength = 34.dp.toPx()
        val cornerStroke = if (isSuccess) 5.dp.toPx() else 3.5.dp.toPx()
        val cornerColor = if (isSuccess) Color(0xFF10B981) else Color(0xFFFFD700)

        // Máscara oscura semitransparente fuera de la ventana central de escaneo
        val maskColor = Color(0x660B132B)

        drawRect(color = maskColor, topLeft = Offset(0f, 0f), size = Size(width, top))
        drawRect(color = maskColor, topLeft = Offset(0f, bottom), size = Size(width, height - bottom))
        drawRect(color = maskColor, topLeft = Offset(0f, top), size = Size(left, boxSize))
        drawRect(color = maskColor, topLeft = Offset(right, top), size = Size(width - right, boxSize))

        // Borde fino de delimitación
        drawRect(
            color = if (isSuccess) Color(0x9910B981) else Color(0x33FFD700),
            topLeft = Offset(left, top),
            size = Size(boxSize, boxSize),
            style = Stroke(width = 1.dp.toPx())
        )

        // Esquina Superior Izquierda
        drawLine(cornerColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth = cornerStroke)
        drawLine(cornerColor, Offset(left, top), Offset(left, top + cornerLength), strokeWidth = cornerStroke)

        // Esquina Superior Derecha
        drawLine(cornerColor, Offset(right, top), Offset(right - cornerLength, top), strokeWidth = cornerStroke)
        drawLine(cornerColor, Offset(right, top), Offset(right, top + cornerLength), strokeWidth = cornerStroke)

        // Esquina Inferior Izquierda
        drawLine(cornerColor, Offset(left, bottom), Offset(left + cornerLength, bottom), strokeWidth = cornerStroke)
        drawLine(cornerColor, Offset(left, bottom), Offset(left, bottom - cornerLength), strokeWidth = cornerStroke)

        // Esquina Inferior Derecha
        drawLine(cornerColor, Offset(right, bottom), Offset(right - cornerLength, bottom), strokeWidth = cornerStroke)
        drawLine(cornerColor, Offset(right, bottom), Offset(right, bottom - cornerLength), strokeWidth = cornerStroke)

        // Haz láser móvil
        val laserY = top + (boxSize * laserYRatio)
        drawLine(
            color = if (isSuccess) Color(0xFF10B981) else Color(0xFF00E5FF),
            start = Offset(left + 8.dp.toPx(), laserY),
            end = Offset(right - 8.dp.toPx(), laserY),
            strokeWidth = if (isSuccess) 4.5.dp.toPx() else 2.5.dp.toPx()
        )
    }
}

/**
 * Indicador animado de anillo de enfoque cuando el usuario toca la pantalla.
 */
@Composable
private fun FocusRingIndicator(
    centerOffset: Offset,
    onAnimationEnd: () -> Unit
) {
    var animStarted by remember { mutableStateOf(false) }
    val radius by animateFloatAsState(
        targetValue = if (animStarted) 28.dp.value else 44.dp.value,
        animationSpec = tween(400, easing = FastOutSlowInEasing),
        label = "focus_radius"
    )
    val alpha by animateFloatAsState(
        targetValue = if (animStarted) 0f else 1f,
        animationSpec = tween(600, easing = LinearEasing),
        finishedListener = { onAnimationEnd() },
        label = "focus_alpha"
    )

    LaunchedEffect(Unit) {
        animStarted = true
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawCircle(
            color = CyanNeon.copy(alpha = alpha),
            radius = radius.dp.toPx(),
            center = centerOffset,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

/**
 * Analizador de códigos QR y códigos de acceso optimizado para CameraX
 * utilizando las dependencias de ZXing existentes en el proyecto.
 */
class EntryQrCodeZxingAnalyzer(
    private val cooldownMillis: Long = 1500L,
    private val onQrDecoded: (String) -> Unit
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
                    // Fallback para códigos QR de fondo oscuro / teléfonos en modo nocturno
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
                    val isDifferent = decodedText != lastScannedValue
                    val isPastCooldown = currentTime - lastScannedTimestamp > cooldownMillis
                    if (isDifferent || isPastCooldown) {
                        lastScannedValue = decodedText
                        lastScannedTimestamp = currentTime
                        onQrDecoded(decodedText)
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
