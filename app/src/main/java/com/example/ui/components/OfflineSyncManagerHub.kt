package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.booking.AppDatabase
import com.example.data.sync.ConnectivityStatus
import com.example.data.sync.NetworkConnectivityObserver
import com.example.data.sync.OfflineSyncEngine
import com.example.data.sync.SyncQueueEntity
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarningOrange
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SyncQueueFilter(val label: String) {
    ALL("Todas"),
    PENDING("Pendientes"),
    EMERGENCIES("🚨 Emergencias"),
    SYNCED("Sincronizadas"),
    ERRORS("Errores")
}

/**
 * FASE 19: CONSOLA DE CONTINUIDAD OPERATIVA Y COLA DE SINCRONIZACIÓN PERSISTENTE (PANEL MAESTRO ALFHA)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineSyncManagerHub(
    db: AppDatabase,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val observer = remember { NetworkConnectivityObserver.getInstance(context) }

    val networkState by observer.networkState.collectAsState()
    val allQueueEntries by db.syncQueueDao().getAllSyncEntriesFlow().collectAsState(initial = emptyList())
    val pendingCount by db.syncQueueDao().getPendingCountFlow().collectAsState(initial = 0)
    val errorCount by db.syncQueueDao().getErrorCountFlow().collectAsState(initial = 0)
    val syncedCount by db.syncQueueDao().getSyncedCountFlow().collectAsState(initial = 0)
    val lastSuccessfulSyncMillis by db.syncQueueDao().getLastSuccessfulSyncTimeFlow().collectAsState(initial = null)
    val pendingDevices by db.syncQueueDao().getDevicesWithPendingOperationsFlow().collectAsState(initial = emptyList())
    val totalTimeSavedSeconds by db.syncQueueDao().getTotalTimeSavedSecondsFlow().collectAsState(initial = 0L)

    var selectedFilter by remember { mutableStateOf(SyncQueueFilter.ALL) }
    var selectedEntryDetail by remember { mutableStateOf<SyncQueueEntity?>(null) }
    var isSyncingNow by remember { mutableStateOf(false) }

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

    val filteredList = remember(allQueueEntries, selectedFilter) {
        when (selectedFilter) {
            SyncQueueFilter.ALL -> allQueueEntries
            SyncQueueFilter.PENDING -> allQueueEntries.filter { it.status == "PENDIENTE" || it.status == "SINCRONIZANDO" }
            SyncQueueFilter.EMERGENCIES -> allQueueEntries.filter { it.isEmergency }
            SyncQueueFilter.SYNCED -> allQueueEntries.filter { it.status == "SINCRONIZADO" }
            SyncQueueFilter.ERRORS -> allQueueEntries.filter { it.status == "ERROR" }
        }
    }

    val totalTimeSavedHours = (totalTimeSavedSeconds ?: 0L) / 3600.0
    val totalTimeSavedMinutes = ((totalTimeSavedSeconds ?: 0L) % 3600) / 60

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Encabezado del Módulo
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CONTINUIDAD OPERATIVA + MODO OFFLINE",
                            color = GoldPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Room SQLite • Idempotencia • Tolerancia a Fallas de Red",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                    ConnectivityStatusPill()
                }
            }
        }

        // Métricas Globales de Conectividad y Cola
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Estado de Conectividad
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("ESTADO RED", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        when (networkState.status) {
                                            ConnectivityStatus.ONLINE -> SuccessGreen
                                            ConnectivityStatus.OFFLINE -> Color(0xFFFF5252)
                                            ConnectivityStatus.SYNCHRONIZING -> CyanNeon
                                        },
                                        CircleShape
                                    )
                            )
                            Text(
                                text = networkState.status.label,
                                color = when (networkState.status) {
                                    ConnectivityStatus.ONLINE -> SuccessGreen
                                    ConnectivityStatus.OFFLINE -> Color(0xFFFF5252)
                                    ConnectivityStatus.SYNCHRONIZING -> CyanNeon
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Text(
                            text = networkState.transportType,
                            color = TextMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Pendientes en Cola
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    border = BorderStroke(1.dp, if (pendingCount > 0) WarningOrange.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("EN COLA ROOM", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "$pendingCount",
                            color = if (pendingCount > 0) WarningOrange else SuccessGreen,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (pendingCount > 0) "Pendiente de envío" else "Todo al día",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }

                // Sincronizadas
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("SINCRONIZADAS", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "$syncedCount",
                            color = CyanNeon,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Idempotentes",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // Barra de Acción Rápida: SINCRONIZAR AHORA + Última Sync + Toggle Modo Offline Simulado
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "ACCIONES DE SINCRONIZACIÓN",
                                color = GoldPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    isSyncingNow = true
                                    val res = OfflineSyncEngine.syncPendingOperations(context, db)
                                    isSyncingNow = false
                                    Toast.makeText(context, res.message, Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldPrimary,
                                contentColor = NavyDark
                            ),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isSyncingNow,
                            modifier = Modifier.testTag("sync_now_button")
                        ) {
                            if (isSyncingNow) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .rotate(rotationAngle)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SINCRONIZAR AHORA", fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                        }
                    }

                    Divider(color = Color.White.copy(alpha = 0.08f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.History, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                            Text(
                                text = "Última sincronización exitosa: ${
                                    if (lastSuccessfulSyncMillis != null) {
                                        SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault()).format(Date(lastSuccessfulSyncMillis!!))
                                    } else {
                                        "Al momento (Room Local)"
                                    }
                                }",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Interruptor para simular caída de red
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NavyDark.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Simular Interrupción de Conectividad (Offline)",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Verifica que el escaneo, registro y emergencias operen sin internet",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = networkState.isSimulatedOffline,
                            onCheckedChange = { checked ->
                                observer.toggleSimulatedOffline(checked)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFFFF5252),
                                checkedTrackColor = Color(0xFFB71C1C)
                            )
                        )
                    }
                }
            }
        }

        // Casetas / Dispositivos con Operaciones Pendientes
        if (pendingDevices.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Devices, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(16.dp))
                            Text(
                                text = "CASETAS / TERMINALES CON COLA PENDIENTE (${pendingDevices.size})",
                                color = WarningOrange,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(pendingDevices) { deviceName ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = WarningOrange.copy(alpha = 0.15f),
                                    border = BorderStroke(0.8.dp, WarningOrange.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = deviceName,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Métrica de Tiempo Devuelto por Continuidad Offline
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CyanNeon.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = CyanNeon,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "TIEMPO DEVUELTO (CONTINUIDAD OFFLINE)",
                                color = CyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Cero llamadas, cero bitácoras de papel y cero recaptura manual",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Text(
                        text = if (totalTimeSavedHours >= 1.0) {
                            String.format(Locale.getDefault(), "%.1f hrs", totalTimeSavedHours)
                        } else {
                            "$totalTimeSavedMinutes min"
                        },
                        color = GoldPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Filtros de la Cola de Operaciones
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "COLA PERSISTENTE DE OPERACIONES ROOM (${filteredList.size})",
                    color = GoldPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(SyncQueueFilter.values()) { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = {
                                Text(
                                    text = filter.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedFilter == filter) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldPrimary,
                                selectedLabelColor = NavyDark,
                                containerColor = NavyCard,
                                labelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        // Lista de Operaciones en Cola
        if (filteredList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Sin operaciones pendientes en este filtro",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Todas las operaciones registradas localmente en Room están al día.",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        } else {
            items(filteredList) { entry ->
                SyncQueueEntryCard(
                    entry = entry,
                    onClick = { selectedEntryDetail = entry }
                )
            }
        }
    }

    // Modal de Detalle de Operación en Cola
    if (selectedEntryDetail != null) {
        val entry = selectedEntryDetail!!
        AlertDialog(
            onDismissRequest = { selectedEntryDetail = null },
            containerColor = NavyDark,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (entry.isEmergency) Icons.Default.WarningAmber else Icons.Default.Fingerprint,
                        contentDescription = null,
                        tint = if (entry.isEmergency) Color(0xFFFF5252) else GoldPrimary
                    )
                    Text(
                        text = "DETALLE DE OPERACIÓN OFFLINE",
                        color = GoldPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyCard),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Folio Sincronización: ${entry.syncFolio}", color = CyanNeon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Folio Entidad: ${entry.targetFolio}", color = Color.White, fontSize = 11.sp)
                            Text("Tipo: ${entry.operationType} • Módulo: ${entry.targetModule}", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text("Operador: ${entry.operatorName} (${entry.operatorRole})", color = TextMuted, fontSize = 11.sp)
                            Text("Ubicación: ${entry.locationName}", color = TextMuted, fontSize = 11.sp)
                            if (entry.latitude != null && entry.longitude != null) {
                                Text("GPS: ${entry.latitude}, ${entry.longitude}", color = SuccessGreen, fontSize = 10.sp)
                            }
                            Text("Fecha/Hora Local: ${entry.formattedTime}", color = TextMuted, fontSize = 10.sp)
                            Text("Dispositivo: ${entry.deviceGateId}", color = TextMuted, fontSize = 10.sp)
                            Text("Estado: ${entry.status} • Reintentos: ${entry.retryCount}", color = when (entry.status) {
                                "SINCRONIZADO" -> SuccessGreen
                                "ERROR" -> Color(0xFFFF5252)
                                else -> WarningOrange
                            }, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text("Firma Criptográfica SHA-256:", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = entry.hashIntegrity,
                        color = Color.LightGray,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .background(NavyCard, RoundedCornerShape(6.dp))
                            .padding(6.dp)
                    )

                    if (entry.errorMessage != null) {
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C).copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Error: ${entry.errorMessage}",
                                color = Color(0xFFFF8A80),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = { selectedEntryDetail = null },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cerrar", color = Color.White, fontSize = 12.sp)
                }
            }
        )
    }
}

@Composable
private fun SyncQueueEntryCard(
    entry: SyncQueueEntity,
    onClick: () -> Unit
) {
    val statusColor = when (entry.status) {
        "SINCRONIZADO" -> SuccessGreen
        "ERROR" -> Color(0xFFFF5252)
        "SINCRONIZANDO" -> CyanNeon
        else -> WarningOrange
    }

    val isEmg = entry.isEmergency

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEmg) Color(0xFFB71C1C).copy(alpha = 0.15f) else NavyCard
        ),
        border = BorderStroke(
            1.dp,
            if (isEmg) Color(0xFFFF5252).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, statusColor.copy(alpha = 0.5f)),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                isEmg -> Icons.Default.WarningAmber
                                entry.status == "SINCRONIZADO" -> Icons.Default.CheckCircle
                                entry.status == "ERROR" -> Icons.Default.ErrorOutline
                                else -> Icons.Default.CloudSync
                            },
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = entry.operationType,
                            color = if (isEmg) Color(0xFFFF5252) else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White.copy(alpha = 0.08f)
                        ) {
                            Text(
                                text = entry.targetModule,
                                color = GoldPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    Text(
                        text = "Ref: ${entry.targetFolio} • ${entry.locationName}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = TextMuted, modifier = Modifier.size(11.dp))
                        Text(
                            text = "${entry.operatorName} • ${entry.formattedTime}",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = statusColor.copy(alpha = 0.15f),
                border = BorderStroke(0.8.dp, statusColor.copy(alpha = 0.5f))
            ) {
                Text(
                    text = entry.status,
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}
