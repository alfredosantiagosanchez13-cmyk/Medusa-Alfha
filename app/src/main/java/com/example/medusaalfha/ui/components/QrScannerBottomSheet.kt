package com.example.medusaalfha.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.medusaalfha.data.scanner.ParsedVisitorPass
import com.example.medusaalfha.data.scanner.QrCodeImageAnalyzer
import com.example.medusaalfha.ui.theme.NavyPrimary
import com.example.medusaalfha.ui.theme.StatusAmber
import com.example.medusaalfha.ui.theme.StatusGreen
import com.example.medusaalfha.ui.theme.TealAccent
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerBottomSheet(
    onDismiss: () -> Unit,
    onQrScanned: (String) -> Unit,
    scannedPass: ParsedVisitorPass?,
    isPassAlreadyInside: Boolean = false,
    isConfirming: Boolean = false,
    onConfirmPass: () -> Unit,
    onDismissPass: () -> Unit
) {
    val context = LocalContext.current
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
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = NavyPrimary,
        modifier = Modifier.testTag("qr_scanner_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Cabecera del Escáner
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(TealAccent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Escanear QR",
                            tint = TealAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Lector de Pases QR",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "Validación inmediata en Garita / Caseta",
                            fontSize = 11.sp,
                            color = Color.LightGray
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_scanner_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar Escáner",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (!hasCameraPermission) {
                // Estado de Permiso Requerido
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(Color(0xFF1E293B), RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = TealAccent,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Permiso de Cámara Requerido",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "La caseta necesita acceso a la cámara para escanear pases QR de visitantes.",
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("request_camera_permission_button")
                    ) {
                        Text("Permitir Acceso a la Cámara")
                    }
                }
            } else {
                // Visor de Cámara con CameraX
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    CameraPreviewView(
                        onQrCodeScanned = onQrScanned,
                        isFrozen = scannedPass != null
                    )

                    // Retícula de Mira / Escáner
                    Box(
                        modifier = Modifier
                            .size(210.dp)
                            .border(2.dp, TealAccent, RoundedCornerShape(16.dp))
                    )

                    Text(
                        text = "Apunta el código QR del pase dentro del cuadro",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Resultado del Escaneo / Confirmación de Pase
            AnimatedVisibility(visible = scannedPass != null) {
                scannedPass?.let { pass ->
                    val actionColor = if (isPassAlreadyInside) StatusAmber else TealAccent
                    val statusIcon = if (isPassAlreadyInside) Icons.AutoMirrored.Filled.ExitToApp else Icons.Default.CheckCircle
                    val statusLabel = if (isPassAlreadyInside) "Visita en Sitio · Salida" else "Pase Autorizado · Ingreso"

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scanned_pass_card")
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = statusIcon,
                                        contentDescription = null,
                                        tint = if (isPassAlreadyInside) StatusAmber else StatusGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = statusLabel,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = actionColor.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = if (isPassAlreadyInside) "Folio: ${pass.folio} (DENTRO)" else "Folio: ${pass.folio}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = actionColor,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Visitante: ${pass.visitorName}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Destino: Casa ${pass.destinationHouse}",
                                fontSize = 13.sp,
                                color = Color(0xFF94A3B8)
                            )
                            if (!pass.vehiclePlate.isNullOrBlank()) {
                                Text(
                                    text = "Placas: ${pass.vehiclePlate}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF38BDF8)
                                )
                            }

                            // Banner contextual de acción (Ingreso vs Salida)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = actionColor.copy(alpha = 0.12f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    actionColor.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isPassAlreadyInside) Icons.Default.Info else Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = actionColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isPassAlreadyInside)
                                            "El pase ya cuenta con registro previo en el condominio. Al confirmar se registrará la SALIDA."
                                        else
                                            "Al confirmar se registrará el INGRESO en Firestore y se enviará alerta en tiempo real a Casa ${pass.destinationHouse}.",
                                        fontSize = 11.sp,
                                        color = Color(0xFFE2E8F0),
                                        lineHeight = 15.sp
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onDismissPass,
                                    enabled = !isConfirming,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("discard_scan_button"),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
                                ) {
                                    Text("Reintentar")
                                }

                                Button(
                                    onClick = onConfirmPass,
                                    enabled = !isConfirming,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .testTag("confirm_scan_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = actionColor,
                                        contentColor = Color.White
                                    )
                                ) {
                                    if (isConfirming) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Color.White,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Guardando...", fontSize = 13.sp)
                                    } else {
                                        Icon(
                                            imageVector = if (isPassAlreadyInside) Icons.AutoMirrored.Filled.ExitToApp else Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (isPassAlreadyInside) "Marcar Salida" else "Confirmar Ingreso",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun CameraPreviewView(
    onQrCodeScanned: (String) -> Unit,
    isFrozen: Boolean,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(
                    cameraExecutor,
                    QrCodeImageAnalyzer { qrResult ->
                        if (!isFrozen) {
                            onQrCodeScanned(qrResult)
                        }
                    }
                )

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreviewView", "Error vinculando caso de uso de CameraX: ${e.message}", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier.fillMaxSize()
    )
}
