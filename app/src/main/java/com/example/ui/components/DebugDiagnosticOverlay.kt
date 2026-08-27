package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.BuildConfig
import com.example.MedusaApplication
import com.example.data.booking.AppDatabase
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarningOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DiagnosticStatus {
    READY,
    WARNING,
    ERROR,
    CHECKING
}

data class DiagnosticItemState(
    val title: String,
    val detail: String,
    val status: DiagnosticStatus,
    val icon: ImageVector
)

@Composable
fun DebugDiagnosticOverlay(
    modifier: Modifier = Modifier
) {
    // Only display in debug builds
    if (!BuildConfig.DEBUG) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isExpanded by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    var lastCheckedTime by remember { mutableStateOf("") }

    var cameraState by remember {
        mutableStateOf(
            DiagnosticItemState(
                title = "Cámara & Sensores",
                detail = "Verificando...",
                status = DiagnosticStatus.CHECKING,
                icon = Icons.Default.PhotoCamera
            )
        )
    }

    var databaseState by remember {
        mutableStateOf(
            DiagnosticItemState(
                title = "Base de Datos Room",
                detail = "Verificando...",
                status = DiagnosticStatus.CHECKING,
                icon = Icons.Default.Storage
            )
        )
    }

    var networkState by remember {
        mutableStateOf(
            DiagnosticItemState(
                title = "Conectividad de Red",
                detail = "Verificando...",
                status = DiagnosticStatus.CHECKING,
                icon = Icons.Default.Wifi
            )
        )
    }

    fun performDiagnostics() {
        scope.launch {
            isChecking = true

            // 1. Camera Diagnostic
            val hasHardware = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

            cameraState = when {
                hasHardware && hasPermission -> DiagnosticItemState(
                    title = "Cámara & Sensores",
                    detail = "Hardware disponible • Permiso Concedido",
                    status = DiagnosticStatus.READY,
                    icon = Icons.Default.PhotoCamera
                )
                hasHardware && !hasPermission -> DiagnosticItemState(
                    title = "Cámara & Sensores",
                    detail = "Hardware OK • Falta Conceder Permiso",
                    status = DiagnosticStatus.WARNING,
                    icon = Icons.Default.PhotoCamera
                )
                else -> DiagnosticItemState(
                    title = "Cámara & Sensores",
                    detail = "Hardware no detectado o emulador sin cámara",
                    status = DiagnosticStatus.ERROR,
                    icon = Icons.Default.PhotoCamera
                )
            }

            // 2. Database Diagnostic (Async)
            databaseState = try {
                withContext(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(context)
                    val checkInsCount = db.visitorCheckInDao().getCheckInCount()
                    val bookingsCount = db.amenityBookingDao().getBookingsCount()
                    val memoryMode = if (MedusaApplication.isDatabaseAvailable) "SQLite Room (Persistente)" else "In-Memory Fallback"
                    DiagnosticItemState(
                        title = "Base de Datos Room",
                        detail = "$memoryMode • $checkInsCount Visitas, $bookingsCount Reservas",
                        status = DiagnosticStatus.READY,
                        icon = Icons.Default.Storage
                    )
                }
            } catch (e: Exception) {
                DiagnosticItemState(
                    title = "Base de Datos Room",
                    detail = "Excepción DB: ${e.localizedMessage ?: "Fallo de conexión"}",
                    status = DiagnosticStatus.ERROR,
                    icon = Icons.Default.Storage
                )
            }

            // 3. Network Diagnostic
            val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = connMgr?.activeNetwork
            val networkCapabilities = connMgr?.getNetworkCapabilities(activeNetwork)
            val isConnected = networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            val netDetail = when {
                networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Conectado vía Wi-Fi (En línea)"
                networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Conectado vía Datos Móviles (En línea)"
                networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Conectado vía Ethernet (En línea)"
                isConnected -> "Conexión a Internet activa"
                else -> "Modo Sin Conexión (Operación Local Offline)"
            }

            networkState = DiagnosticItemState(
                title = "Conectividad de Red",
                detail = netDetail,
                status = if (isConnected) DiagnosticStatus.READY else DiagnosticStatus.WARNING,
                icon = Icons.Default.Wifi
            )

            lastCheckedTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            isChecking = false
        }
    }

    LaunchedEffect(Unit) {
        performDiagnostics()
    }

    Box(
        modifier = modifier
            .testTag("debug_diagnostic_overlay"),
        contentAlignment = Alignment.TopEnd
    ) {
        if (!isExpanded) {
            // Collapsed Quick Badge Pill
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { isExpanded = true }
                    .border(1.dp, GoldPrimary.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .testTag("debug_diagnostic_badge"),
                color = NavyDark.copy(alpha = 0.92f),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                color = if (cameraState.status == DiagnosticStatus.READY && databaseState.status == DiagnosticStatus.READY) {
                                    SuccessGreen
                                } else {
                                    WarningOrange
                                },
                                shape = CircleShape
                            )
                    )

                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = "Debug Diagnostic",
                        tint = GoldPrimary,
                        modifier = Modifier.size(13.dp)
                    )

                    Text(
                        text = "DEBUG APK",
                        color = GoldPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )

                    Text(
                        text = "CAM: ${if (cameraState.status == DiagnosticStatus.READY) "OK" else "!"} | DB: ${if (databaseState.status == DiagnosticStatus.READY) "OK" else "!"}",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            // Expanded Detailed Diagnostic Panel Card
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .animateContentSize()
                    .border(1.5.dp, GoldPrimary, RoundedCornerShape(16.dp))
                    .testTag("debug_diagnostic_panel"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header with Title, Refresh & Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(GoldPrimary.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BugReport,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "DIAGNÓSTICO EN TIEMPO REAL",
                                    color = GoldPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Debug APK • $lastCheckedTime",
                                    color = TextMuted,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { performDiagnostics() },
                                modifier = Modifier
                                    .size(30.dp)
                                    .testTag("debug_diagnostic_refresh_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refrescar Diagnóstico",
                                    tint = CyanNeon,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { isExpanded = false },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar Overlay",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Diagnostic Item 1: Camera
                    DiagnosticRowItem(item = cameraState)

                    // Diagnostic Item 2: Database
                    DiagnosticRowItem(item = databaseState)

                    // Diagnostic Item 3: Network
                    DiagnosticRowItem(item = networkState)

                    // Footer Quick Info
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NavyDark,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Estado General:",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                            Text(
                                text = if (cameraState.status == DiagnosticStatus.READY && databaseState.status == DiagnosticStatus.READY) {
                                    "🟢 Listo para Pruebas Garita"
                                } else {
                                    "🟡 Verificaciones Pendientes"
                                },
                                color = if (cameraState.status == DiagnosticStatus.READY && databaseState.status == DiagnosticStatus.READY) {
                                    SuccessGreen
                                } else {
                                    WarningOrange
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticRowItem(item: DiagnosticItemState) {
    val statusColor = when (item.status) {
        DiagnosticStatus.READY -> SuccessGreen
        DiagnosticStatus.WARNING -> WarningOrange
        DiagnosticStatus.ERROR -> ErrorRed
        DiagnosticStatus.CHECKING -> CyanNeon
    }

    val statusIcon = when (item.status) {
        DiagnosticStatus.READY -> Icons.Default.CheckCircle
        DiagnosticStatus.WARNING -> Icons.Default.Warning
        DiagnosticStatus.ERROR -> Icons.Default.Error
        DiagnosticStatus.CHECKING -> Icons.Default.Refresh
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = NavyCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(statusColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(15.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.detail,
                    color = TextMuted,
                    fontSize = 9.sp,
                    maxLines = 2
                )
            }

            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
