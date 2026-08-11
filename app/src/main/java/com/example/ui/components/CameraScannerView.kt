package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.runtime.rememberCoroutineScope
import com.example.ui.theme.SuccessGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.scanner.QrCodeAnalyzer
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyDark
import java.util.concurrent.Executors

@Composable
fun CameraScannerView(
    onQrScanned: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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
    val coroutineScope = rememberCoroutineScope()

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

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(hasCameraPermission, cameraLensFacing, lifecycleOwner) {
        if (!hasCameraPermission) return@LaunchedEffect
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
                                triggerScanHaptic(context)
                                coroutineScope.launch(Dispatchers.Main) {
                                    isScanSuccessActive = true
                                    delay(1200)
                                    isScanSuccessActive = false
                                }
                                onQrScanned(scannedCode)
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(NavyDark)
            .border(2.dp, GoldPrimary.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .testTag("camera_scanner_container")
    ) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Scanning Overlay reticle with success state indicator
            ScannerOverlayReticle(isSuccess = isScanSuccessActive)

            // Scan Success Overlay Animation
            AnimatedVisibility(
                visible = isScanSuccessActive,
                enter = fadeIn(animationSpec = tween(150)) + scaleIn(initialScale = 0.7f, animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(250)) + scaleOut(targetScale = 0.9f, animationSpec = tween(250)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = SuccessGreen.copy(alpha = 0.95f),
                    shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White.copy(alpha = 0.9f)),
                    modifier = Modifier.testTag("scan_success_overlay_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Escaneo Exitoso",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "¡QR DETECTADO!",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // Flashlight & Camera Flip Controls
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        val newTorchState = !isTorchOn
                        camera?.cameraControl?.enableTorch(newTorchState)
                        isTorchOn = newTorchState
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .background(NavyDark.copy(alpha = 0.7f), CircleShape)
                        .border(1.dp, GoldPrimary, CircleShape)
                        .testTag("torch_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flashlight",
                        tint = if (isTorchOn) GoldPrimary else Color.White
                    )
                }

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
                        .background(NavyDark.copy(alpha = 0.7f), CircleShape)
                        .border(1.dp, CyanNeon, CircleShape)
                        .testTag("camera_flip_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Flip Camera",
                        tint = CyanNeon
                    )
                }
            }

            // Status Indicator Badge
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                color = NavyDark.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = CyanNeon,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "CameraX & ZXing Activo • Apunte al Pase QR",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(56.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Permiso de CÁMARA Requerido",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Active la cámara para permitir a los guardias de seguridad escanear y verificar códigos QR en tiempo real.",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("request_camera_permission_button")
                ) {
                    Text("Conceder Permiso de Cámara", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ScannerOverlayReticle(isSuccess: Boolean = false) {
    val infiniteTransition = rememberInfiniteTransition(label = "laser_animation")
    val laserYRatio by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val boxSize = width.coerceAtMost(height) * 0.65f
        val left = (width - boxSize) / 2f
        val top = (height - boxSize) / 2f
        val right = left + boxSize
        val bottom = top + boxSize

        val cornerLength = 28.dp.toPx()
        val cornerStroke = if (isSuccess) 6.dp.toPx() else 4.dp.toPx()

        val cornerColor = if (isSuccess) Color(0xFF10B981) else Color(0xFFFFD700)

        // Green flash background fill on scan success
        if (isSuccess) {
            drawRect(
                color = Color(0x3310B981),
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(boxSize, boxSize)
            )
        }

        // Top-Left
        drawLine(cornerColor, Offset(left, top), Offset(left + cornerLength, top), strokeWidth = cornerStroke)
        drawLine(cornerColor, Offset(left, top), Offset(left, top + cornerLength), strokeWidth = cornerStroke)

        // Top-Right
        drawLine(cornerColor, Offset(right, top), Offset(right - cornerLength, top), strokeWidth = cornerStroke)
        drawLine(cornerColor, Offset(right, top), Offset(right, top + cornerLength), strokeWidth = cornerStroke)

        // Bottom-Left
        drawLine(cornerColor, Offset(left, bottom), Offset(left + cornerLength, bottom), strokeWidth = cornerStroke)
        drawLine(cornerColor, Offset(left, bottom), Offset(left, bottom - cornerLength), strokeWidth = cornerStroke)

        // Bottom-Right
        drawLine(cornerColor, Offset(right, bottom), Offset(right - cornerLength, bottom), strokeWidth = cornerStroke)
        drawLine(cornerColor, Offset(right, bottom), Offset(right, bottom - cornerLength), strokeWidth = cornerStroke)

        // Laser line
        val laserY = top + (boxSize * laserYRatio)
        drawLine(
            color = if (isSuccess) Color(0xFF10B981) else Color(0xFF06B6D4),
            start = Offset(left + 8.dp.toPx(), laserY),
            end = Offset(right - 8.dp.toPx(), laserY),
            strokeWidth = if (isSuccess) 5.dp.toPx() else 3.dp.toPx()
        )
    }
}

fun triggerScanHaptic(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            val vibrator = vibratorManager?.defaultVibrator
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(120L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(120L)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
