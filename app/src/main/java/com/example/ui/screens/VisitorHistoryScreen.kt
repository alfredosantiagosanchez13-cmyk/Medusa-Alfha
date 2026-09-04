package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.House
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scanner.VisitorEntry
import com.example.scanner.VisitorStatus
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarningOrange
import kotlinx.coroutines.launch

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun VisitorHistoryScreen(
    entries: List<VisitorEntry>,
    onStatusChange: (VisitorEntry, VisitorStatus) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("TODOS") }
    var showClearDialog by remember { mutableStateOf(false) }
    var selectedDetailEntry by remember { mutableStateOf<VisitorEntry?>(null) }

    val insideCount = entries.count { it.status == VisitorStatus.CHECKED_IN || it.status == VisitorStatus.VERIFIED }
    val departedCount = entries.count { it.status == VisitorStatus.DEPARTED }
    val pendingCount = entries.count { it.status == VisitorStatus.PENDING }
    val deniedCount = entries.count { it.status == VisitorStatus.DENIED }

    val filteredEntries = entries.filter { entry ->
        val matchesSearch = searchQuery.isBlank() ||
                entry.visitorName.contains(searchQuery, ignoreCase = true) ||
                entry.destinationHouse.contains(searchQuery, ignoreCase = true) ||
                entry.visitorDocument.contains(searchQuery, ignoreCase = true) ||
                entry.passCode.contains(searchQuery, ignoreCase = true) ||
                entry.folio.contains(searchQuery, ignoreCase = true) ||
                (entry.vehiclePlate?.contains(searchQuery, ignoreCase = true) == true)

        val matchesFilter = when (selectedFilter) {
            "EN CONDOMINIO" -> entry.status == VisitorStatus.CHECKED_IN || entry.status == VisitorStatus.VERIFIED
            "SALIDAS" -> entry.status == VisitorStatus.DEPARTED
            "PENDIENTES" -> entry.status == VisitorStatus.PENDING
            "DENEGADOS" -> entry.status == VisitorStatus.DENIED
            else -> true
        }

        matchesSearch && matchesFilter
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("visitor_history_screen")
    ) {
        // Header Section
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = NavySurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(GoldPrimary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "TRAZABILIDAD Y REGISTRO ROOM",
                                color = GoldPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Historial Operativo de Accesos",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (entries.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearDialog = true },
                            modifier = Modifier.testTag("clear_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Limpiar Registro",
                                tint = ErrorRed.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Detailed KPI Counters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HistoryKpiBadge(
                        label = "Total Folios",
                        count = entries.size,
                        color = CyanNeon,
                        modifier = Modifier.weight(1f)
                    )
                    HistoryKpiBadge(
                        label = "En Sitio",
                        count = insideCount,
                        color = SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                    HistoryKpiBadge(
                        label = "Salidas",
                        count = departedCount,
                        color = Color(0xFF9CA3AF),
                        modifier = Modifier.weight(1f)
                    )
                    HistoryKpiBadge(
                        label = "Denegados",
                        count = deniedCount,
                        color = ErrorRed,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Firestore Database Schema Status Badge
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanNeon.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = CyanNeon,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Esquema Firestore /visitor_logs",
                                color = CyanNeon,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "timestamp • visitorName • authorizedUnitNumber",
                            color = GoldPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Box
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar por folio, nombre, RUT, casa o placa...", color = Color.Gray, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldPrimary) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = NavySurface,
                unfocusedContainerColor = NavySurface
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("history_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("TODOS", "EN CONDOMINIO", "SALIDAS", "PENDIENTES", "DENEGADOS").forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter, fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GoldPrimary,
                        selectedLabelColor = NavyDark,
                        containerColor = NavyCard,
                        labelColor = TextMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedFilter == filter,
                        borderColor = Color.Transparent,
                        selectedBorderColor = GoldPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("history_filter_$filter")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Scan Entries List
        if (filteredEntries.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "No se encontraron registros de visitas.",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Pruebe ajustando el filtro o buscando por un término distinto.",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredEntries, key = { it.id }) { entry ->
                    HistoryLogCard(
                        entry = entry,
                        onClick = { selectedDetailEntry = entry },
                        onCheckOut = { onStatusChange(entry, VisitorStatus.DEPARTED) },
                        onVerify = { onStatusChange(entry, VisitorStatus.VERIFIED) },
                        onDeny = { onStatusChange(entry, VisitorStatus.DENIED) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Confirmation dialog before clearing history
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text("Limpiar Historial de Accesos", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "¿Está seguro de que desea eliminar todos los registros de escaneos y visitas de la base de datos Room?",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearHistory()
                        showClearDialog = false
                        Toast.makeText(context, "Historial de registros limpiado", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Limpiar Todo", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancelar", color = Color.White)
                }
            },
            containerColor = NavySurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Detail modal dialog for selected visitor log entry
    selectedDetailEntry?.let { entry ->
        VisitorDetailDialog(
            entry = entry,
            onDismiss = { selectedDetailEntry = null }
        )
    }
}

@Composable
private fun HistoryKpiBadge(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$count",
                color = color,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun HistoryLogCard(
    entry: VisitorEntry,
    onClick: () -> Unit = {},
    onCheckOut: () -> Unit = {},
    onVerify: () -> Unit = {},
    onDeny: () -> Unit = {}
) {
    val (statusLabel, statusColor, statusIcon) = when (entry.status) {
        VisitorStatus.VERIFIED -> Triple("VERIFICADO", SuccessGreen, Icons.Default.CheckCircle)
        VisitorStatus.CHECKED_IN -> Triple("EN CONDOMINIO", CyanNeon, Icons.Default.CheckCircle)
        VisitorStatus.DEPARTED -> Triple("SALIDA REGISTRADA", Color(0xFF9CA3AF), Icons.Default.Schedule)
        VisitorStatus.PENDING -> Triple("PENDIENTE", WarningOrange, Icons.Default.HourglassTop)
        VisitorStatus.DENIED -> Triple("DENEGADO", ErrorRed, Icons.Default.Cancel)
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_card_${entry.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Folio & Status & Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = statusColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Text(
                    text = entry.folio,
                    color = GoldPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Visitor Info Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.visitorName,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Badge, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(12.dp))
                        Text(text = "RUT: ${entry.visitorDocument}", color = TextMuted, fontSize = 11.sp)
                    }
                }

                Surface(
                    color = NavySurface,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.House, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                        Text(text = "Unidad ${entry.authorizedUnitNumber}", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // QR Pass, Duration & Vehicle Plate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${entry.passTypeLabel} • Pase: ${entry.passCode}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    entry.durationStay?.let { stay ->
                        Text(
                            text = "⏱️ Permanencia: $stay",
                            color = CyanNeon,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                if (!entry.vehiclePlate.isNullOrEmpty()) {
                    Surface(
                        color = NavyDark,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(11.dp))
                            Text(text = entry.vehiclePlate, color = CyanNeon, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (!entry.guardNotes.isNullOrEmpty()) {
                Text(
                    text = "Observación: ${entry.guardNotes}",
                    color = Color.LightGray.copy(alpha = 0.8f),
                    fontSize = 11.sp
                )
            }

            // Action button to Check-Out if inside
            if (entry.status == VisitorStatus.CHECKED_IN || entry.status == VisitorStatus.VERIFIED) {
                Spacer(modifier = Modifier.height(2.dp))
                Button(
                    onClick = onCheckOut,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Registrar Salida de Condominio", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Action buttons if entry is PENDING
            if (entry.status == VisitorStatus.PENDING) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onDeny,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                    ) {
                        Text("Rechazar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onVerify,
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = NavyDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(34.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Aprobar Acceso", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun VisitorDetailDialog(
    entry: VisitorEntry,
    onDismiss: () -> Unit
) {
    val (statusLabel, statusBg, statusFg, statusIcon) = when (entry.status) {
        VisitorStatus.VERIFIED -> Quadruple("Aprobado", SuccessGreen.copy(alpha = 0.15f), SuccessGreen, Icons.Default.CheckCircle)
        VisitorStatus.CHECKED_IN -> Quadruple("En Condominio", CyanNeon.copy(alpha = 0.18f), CyanNeon, Icons.Default.CheckCircle)
        VisitorStatus.DEPARTED -> Quadruple("Salida Registrada", Color(0xFF9CA3AF).copy(alpha = 0.18f), Color(0xFFE5E7EB), Icons.Default.Schedule)
        VisitorStatus.PENDING -> Quadruple("Pendiente", GoldPrimary.copy(alpha = 0.15f), GoldPrimary, Icons.Default.HourglassTop)
        VisitorStatus.DENIED -> Quadruple("Denegado", ErrorRed.copy(alpha = 0.15f), ErrorRed, Icons.Default.Cancel)
    }

    val formattedFullDate = remember(entry.timestampMillis) {
        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault())
        sdf.format(java.util.Date(entry.timestampMillis))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Detalle de Folio Room",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusFg.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(imageVector = statusIcon, contentDescription = null, tint = statusFg, modifier = Modifier.size(14.dp))
                        Text(text = statusLabel, color = statusFg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Photo Snapshot / Avatar Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .background(NavyDark, shape = RoundedCornerShape(12.dp))
                        .border(1.dp, GoldPrimary.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Badge,
                            contentDescription = "Folio Room",
                            tint = GoldPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Folio Único: ${entry.folio}",
                            color = GoldPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Anfitrión: ${entry.hostResidentName}",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }

                // Visitor Details Data Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NavyDark, shape = RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailRow(label = "Visitante (visitorName)", value = entry.visitorName, icon = Icons.Default.Person)
                    DetailRow(label = "Documento RUT", value = entry.visitorDocument, icon = Icons.Default.Badge)
                    DetailRow(label = "Unidad Autorizada", value = entry.authorizedUnitNumber, icon = Icons.Default.House)
                    DetailRow(label = "Tipo de Pase", value = "${entry.passTypeLabel} (${entry.passCode})", icon = Icons.Default.QrCode)
                    if (!entry.vehiclePlate.isNullOrEmpty()) {
                        DetailRow(label = "Patente Vehículo", value = entry.vehiclePlate, icon = Icons.Default.DirectionsCar)
                    }
                    DetailRow(label = "Timestamp (timestamp)", value = formattedFullDate, icon = Icons.Default.Schedule)
                    entry.formattedCheckOutTime?.let { outTime ->
                        DetailRow(label = "Salida", value = outTime, icon = Icons.Default.ExitToApp)
                    }
                    entry.durationStay?.let { stay ->
                        DetailRow(label = "Permanencia", value = stay, icon = Icons.Default.Timer)
                    }
                }

                // Esquema Firestore Cloud Database
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF0F172A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanNeon.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Esquema Firestore /visitor_logs",
                                color = CyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text("• timestamp: ${entry.timestampMillis} ms (Firestore Timestamp)", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp)
                        Text("• visitorName: \"${entry.visitorName}\"", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp)
                        Text("• authorizedUnitNumber: \"${entry.authorizedUnitNumber}\"", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp)
                    }
                }

                if (!entry.guardNotes.isNullOrEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NavyDark, shape = RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text("Notas de Garita / Sistema:", color = TextMuted, fontSize = 10.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(entry.guardNotes, color = Color.White, fontSize = 11.sp)
                    }
                }

                // Custom Resident Text Notes Field
                var residentNotesInput by remember { mutableStateOf(entry.residentNotes ?: "") }
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val db = remember { com.example.data.booking.AppDatabase.getDatabase(context) }
                val repository = remember { com.example.data.visitor.VisitorCheckInRepository(db.visitorCheckInDao()) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NavyDark, shape = RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text("Nota Personal del Residente:", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = residentNotesInput,
                        onValueChange = { residentNotesInput = it },
                        placeholder = { Text("Ej: Entregó paquete, familiar autorizado...", color = TextMuted, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF374151),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val idLong = entry.id.toLongOrNull()
                            if (idLong != null) {
                                scope.launch {
                                    repository.updateResidentNotes(idLong, residentNotesInput)
                                }
                                Toast.makeText(context, "Nota guardada en base de datos", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Guardar Nota", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark)
            ) {
                Text("Cerrar", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = NavySurface,
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    icon: ImageVector
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
            Icon(imageVector = icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(13.dp))
            Text(text = label, color = TextMuted, fontSize = 11.sp)
        }
        Text(
            text = value,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
