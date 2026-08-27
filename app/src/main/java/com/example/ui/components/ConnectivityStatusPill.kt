package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.booking.AppDatabase
import com.example.data.sync.ConnectivityStatus
import com.example.data.sync.NetworkConnectivityObserver
import com.example.data.sync.OfflineSyncEngine
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarningOrange
import kotlinx.coroutines.launch

/**
 * FASE 19: Indicador Visible ONLINE / OFFLINE / SINCRONIZANDO.
 * Muestra el estado de red y el total de operaciones encoladas en Room SQLite.
 */
@Composable
fun ConnectivityStatusPill(
    modifier: Modifier = Modifier,
    showPendingBadge: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val networkObserver = remember { NetworkConnectivityObserver.getInstance(context) }

    val networkState by networkObserver.networkState.collectAsState()
    val pendingCount by db.syncQueueDao().getPendingCountFlow().collectAsState(initial = 0)

    var showDialog by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val (badgeBgColor, badgeBorderColor, textColor, iconVector) = when (networkState.status) {
        ConnectivityStatus.ONLINE -> Quadruple(
            SuccessGreen.copy(alpha = 0.15f),
            SuccessGreen.copy(alpha = 0.6f),
            SuccessGreen,
            if (networkState.transportType.contains("Wi-Fi")) Icons.Default.Wifi else Icons.Default.Public
        )
        ConnectivityStatus.OFFLINE -> Quadruple(
            Color(0xFFE53935).copy(alpha = 0.2f),
            Color(0xFFE53935).copy(alpha = 0.7f),
            Color(0xFFFF5252),
            Icons.Default.CloudOff
        )
        ConnectivityStatus.SYNCHRONIZING -> Quadruple(
            CyanNeon.copy(alpha = 0.2f),
            CyanNeon.copy(alpha = 0.8f),
            CyanNeon,
            Icons.Default.CloudSync
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = badgeBgColor,
        modifier = modifier
            .border(1.dp, badgeBorderColor, RoundedCornerShape(20.dp))
            .clickable { showDialog = true }
            .testTag("connectivity_status_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (networkState.status == ConnectivityStatus.SYNCHRONIZING) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = "Sincronizando",
                    tint = textColor,
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(rotationAngle)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(textColor, CircleShape)
                )
            }

            Text(
                text = networkState.status.label,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )

            if (showPendingBadge && pendingCount > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = WarningOrange.copy(alpha = 0.3f),
                    modifier = Modifier.border(0.8.dp, WarningOrange, RoundedCornerShape(10.dp))
                ) {
                    Text(
                        text = "$pendingCount",
                        color = WarningOrange,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = NavyDark,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SignalCellularAlt,
                        contentDescription = null,
                        tint = GoldPrimary
                    )
                    Text(
                        text = "ESTADO DE CONECTIVIDAD",
                        color = GoldPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyCard),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Modo Actual:", color = TextMuted, fontSize = 12.sp)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = networkState.status.label,
                                        color = textColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Tipo de Enlace:", color = TextMuted, fontSize = 12.sp)
                                Text(networkState.transportType, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Operaciones en Cola Room:", color = TextMuted, fontSize = 12.sp)
                                Text(
                                    text = "$pendingCount pendientes",
                                    color = if (pendingCount > 0) WarningOrange else SuccessGreen,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Fuente de Verdad:", color = TextMuted, fontSize = 12.sp)
                                Text("Room SQLite (Local)", color = CyanNeon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Interruptor de Modo Offline Simulado
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyCard.copy(alpha = 0.7f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Simular Corte de Red (Offline)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Prueba de continuidad operativa y cola",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                            Switch(
                                checked = networkState.isSimulatedOffline,
                                onCheckedChange = { checked ->
                                    networkObserver.toggleSimulatedOffline(checked)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFFF5252),
                                    checkedTrackColor = Color(0xFFB71C1C)
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pendingCount > 0 && networkState.status != ConnectivityStatus.SYNCHRONIZING) {
                        Button(
                            onClick = {
                                scope.launch {
                                    OfflineSyncEngine.syncPendingOperations(context, db)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sincronizar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    OutlinedButton(
                        onClick = { showDialog = false },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cerrar", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
