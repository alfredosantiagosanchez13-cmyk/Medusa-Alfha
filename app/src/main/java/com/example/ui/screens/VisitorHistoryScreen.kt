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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.House
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    val pendingCount = entries.count { it.status == VisitorStatus.PENDING }
    val verifiedCount = entries.count { it.status == VisitorStatus.VERIFIED }
    val deniedCount = entries.count { it.status == VisitorStatus.DENIED }

    val filteredEntries = entries.filter { entry ->
        val matchesSearch = searchQuery.isBlank() ||
                entry.visitorName.contains(searchQuery, ignoreCase = true) ||
                entry.destinationHouse.contains(searchQuery, ignoreCase = true) ||
                entry.visitorDocument.contains(searchQuery, ignoreCase = true) ||
                entry.passCode.contains(searchQuery, ignoreCase = true) ||
                (entry.vehiclePlate?.contains(searchQuery, ignoreCase = true) == true)

        val matchesFilter = when (selectedFilter) {
            "VERIFICADOS" -> entry.status == VisitorStatus.VERIFIED
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
                                text = "HISTORIAL DE ESCANEOS Y VISITAS",
                                color = GoldPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Registro de Accesos QR",
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
                        label = "Total Escaneos",
                        count = entries.size,
                        color = CyanNeon,
                        modifier = Modifier.weight(1f)
                    )
                    HistoryKpiBadge(
                        label = "Verificados",
                        count = verifiedCount,
                        color = SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                    HistoryKpiBadge(
                        label = "Pendientes",
                        count = pendingCount,
                        color = WarningOrange,
                        modifier = Modifier.weight(1f)
                    )
                    HistoryKpiBadge(
                        label = "Denegados",
                        count = deniedCount,
                        color = ErrorRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Box
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar por nombre, RUT, casa, vehículo o pase...", color = Color.Gray, fontSize = 12.sp) },
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
            listOf("TODOS", "VERIFICADOS", "PENDIENTES", "DENEGADOS").forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
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
                    "¿Está seguro de que desea eliminar todos los registros de escaneos y visitas de la sesión actual?",
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
    onVerify: () -> Unit,
    onDeny: () -> Unit
) {
    val (statusLabel, statusColor, statusIcon) = when (entry.status) {
        VisitorStatus.VERIFIED -> Triple("VERIFICADO", SuccessGreen, Icons.Default.CheckCircle)
        VisitorStatus.CHECKED_IN -> Triple("CHECKED-IN", CyanNeon, Icons.Default.CheckCircle)
        VisitorStatus.DEPARTED -> Triple("DEPARTED", Color(0xFF9CA3AF), Icons.Default.Schedule)
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
            // Header Row: Status & Timestamp
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = entry.formattedTime,
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
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
                        Text(text = entry.destinationHouse, color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // QR Pass & Vehicle Plate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${entry.passTypeLabel} • QR: ${entry.passCode}",
                    color = TextMuted,
                    fontSize = 11.sp
                )

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
        VisitorStatus.CHECKED_IN -> Quadruple("Checked-In", CyanNeon.copy(alpha = 0.18f), CyanNeon, Icons.Default.CheckCircle)
        VisitorStatus.DEPARTED -> Quadruple("Departed", Color(0xFF9CA3AF).copy(alpha = 0.18f), Color(0xFFE5E7EB), Icons.Default.Schedule)
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
                    text = "Detalle de Registro de Visita",
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
                        .height(110.dp)
                        .background(NavyDark, shape = RoundedCornerShape(12.dp))
                        .border(1.dp, GoldPrimary.copy(alpha = 0.3f), shape = RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!entry.photoPath.isNullOrEmpty()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = "Foto de Visitante Capturada",
                                tint = GoldPrimary,
                                modifier = Modifier.size(42.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "📸 Captura de Rostro Guardada",
                                color = GoldPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = entry.photoPath,
                                color = TextMuted,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Badge,
                                contentDescription = "Sin Foto",
                                tint = TextMuted,
                                modifier = Modifier.size(38.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Sin captura fotográfica adjunta",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
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
                    DetailRow(label = "Visitante", value = entry.visitorName, icon = Icons.Default.Person)
                    DetailRow(label = "Documento RUT", value = entry.visitorDocument, icon = Icons.Default.Badge)
                    DetailRow(label = "Unidad Destino", value = entry.destinationHouse, icon = Icons.Default.House)
                    DetailRow(label = "Tipo de Pase", value = "${entry.passTypeLabel} (${entry.passCode})", icon = Icons.Default.QrCode)
                    if (!entry.vehiclePlate.isNullOrEmpty()) {
                        DetailRow(label = "Patente Vehículo", value = entry.vehiclePlate, icon = Icons.Default.DirectionsCar)
                    }
                    DetailRow(label = "Fecha y Hora Entrada", value = formattedFullDate, icon = Icons.Default.Schedule)
                }

                if (!entry.guardNotes.isNullOrEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NavyDark, shape = RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text("Notas del Guardia / Sistema:", color = TextMuted, fontSize = 10.sp)
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
                        placeholder = { Text("Ej: Entregó paquete de Amazon, familiar autorizado...", color = TextMuted, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF374151),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                val entryIdLong = entry.id.toLongOrNull() ?: 0L
                                if (entryIdLong > 0L) {
                                    repository.updateResidentNotes(entryIdLong, residentNotesInput)
                                    Toast.makeText(context, "📝 Nota de residente guardada en Room DB", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Nota actualizada localmente", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Text("Guardar Nota de Residente", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                shape = RoundedCornerShape(8.dp)
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
    icon: androidx.compose.ui.graphics.vector.ImageVector
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
            Icon(imageVector = icon, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(14.dp))
            Text(text = label, color = TextMuted, fontSize = 11.sp)
        }
        Text(text = value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

