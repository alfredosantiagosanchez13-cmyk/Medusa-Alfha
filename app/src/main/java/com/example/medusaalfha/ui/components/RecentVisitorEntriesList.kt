package com.example.medusaalfha.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.medusaalfha.data.model.VisitorEntry
import com.example.medusaalfha.data.model.VisitorStatus
import com.example.medusaalfha.ui.theme.*
import com.example.medusaalfha.ui.viewmodel.VisitorFilterTab
import com.example.medusaalfha.ui.viewmodel.VisitorUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentVisitorEntriesList(
    uiState: VisitorUiState,
    onSearchChanged: (String) -> Unit,
    onFilterSelected: (VisitorFilterTab) -> Unit,
    onSelectEntry: (VisitorEntry) -> Unit,
    onDismissDetail: () -> Unit,
    onCheckOut: (String) -> Unit,
    onOpenAddDialog: () -> Unit,
    onOpenScanner: () -> Unit,
    onRegisterQuickVisitor: (String, String, String, String) -> Unit,
    onDismissAddDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Métricas rápidas superiores
        VisitorSummaryMetricsRow(
            totalInside = uiState.totalInsideCount,
            totalToday = uiState.totalTodayCount,
            totalAuthorized = uiState.totalAuthorizedCount
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Barra de búsqueda con botón de Escaneo QR integrado
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChanged,
                placeholder = { Text("Buscar visita, casa, placa...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar") },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChanged("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TealAccent,
                    unfocusedBorderColor = BorderGray
                ),
                singleLine = true
            )

            FilledTonalIconButton(
                onClick = onOpenScanner,
                modifier = Modifier
                    .size(52.dp)
                    .testTag("scan_qr_header_button"),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = TealAccent.copy(alpha = 0.15f),
                    contentColor = TealAccent
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Escanear Pase QR",
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Pestañas de Filtro
        ScrollableTabRow(
            selectedTabIndex = uiState.currentFilter.ordinal,
            edgePadding = 16.dp,
            divider = {},
            containerColor = Color.Transparent
        ) {
            VisitorFilterTab.values().forEach { tab ->
                val selected = uiState.currentFilter == tab
                Tab(
                    selected = selected,
                    onClick = { onFilterSelected(tab) },
                    text = {
                        Text(
                            text = tab.title,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) TealAccent else TextMuted
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Lista de visitantes
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TealAccent)
            }
        } else if (uiState.filteredEntries.isEmpty()) {
            EmptyVisitorEntriesView(
                searchQuery = uiState.searchQuery,
                onClearSearch = { onSearchChanged("") },
                onOpenAddDialog = onOpenAddDialog
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 80.dp, top = 4.dp)
            ) {
                items(uiState.filteredEntries, key = { it.folio }) { entry ->
                    VisitorEntryCard(
                        entry = entry,
                        onClick = { onSelectEntry(entry) },
                        onCheckOut = { onCheckOut(entry.folio) }
                    )
                }
            }
        }
    }

    // Modal de Detalle
    if (uiState.selectedEntry != null) {
        VisitorEntryDetailBottomSheet(
            entry = uiState.selectedEntry,
            onDismiss = onDismissDetail,
            onCheckOut = {
                onCheckOut(uiState.selectedEntry.folio)
                onDismissDetail()
            }
        )
    }

    // Diálogo de Registro Rápido
    if (uiState.isAddDialogOpen) {
        QuickAddVisitorDialog(
            onDismiss = onDismissAddDialog,
            onConfirm = onRegisterQuickVisitor
        )
    }
}

@Composable
fun VisitorSummaryMetricsRow(
    totalInside: Int,
    totalToday: Int,
    totalAuthorized: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MetricCard(
            label = "En Sitio",
            value = totalInside.toString(),
            color = StatusGreen,
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            label = "Autorizados",
            value = totalAuthorized.toString(),
            color = Color(0xFF2563EB),
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            label = "Total Hoy",
            value = totalToday.toString(),
            color = NavyPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
            Text(text = label, fontSize = 11.sp, color = TextMuted)
        }
    }
}

@Composable
fun VisitorEntryCard(
    entry: VisitorEntry,
    onClick: () -> Unit,
    onCheckOut: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                            .background(TealAccent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = TealAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = entry.visitorName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = NavyPrimary
                        )
                        Text(
                            text = "${entry.visitorType} · Folio: ${entry.folio}",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                VisitorStatusBadge(status = entry.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = entry.destinationHouse,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = NavyPrimary
                    )
                }

                if (!entry.vehiclePlate.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Text(
                            text = entry.vehiclePlate,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ingreso: ${entry.formattedTime} (${entry.relativeTimeAgo})",
                    fontSize = 11.sp,
                    color = TextMuted
                )

                if (entry.status == VisitorStatus.DENTRO) {
                    OutlinedButton(
                        onClick = onCheckOut,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusAmber)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Salida",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Marcar Salida", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun VisitorStatusBadge(status: VisitorStatus) {
    val (bgColor, textColor) = when (status) {
        VisitorStatus.DENTRO -> Color(0xFFDCFCE7) to StatusGreen
        VisitorStatus.SALIDA -> Color(0xFFF1F5F9) to TextMuted
        VisitorStatus.AUTORIZADO -> Color(0xFFDBEAFE) to Color(0xFF1D4ED8)
        VisitorStatus.DENEGADO -> Color(0xFFFEE2E2) to StatusRed
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = status.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitorEntryDetailBottomSheet(
    entry: VisitorEntry,
    onDismiss: () -> Unit,
    onCheckOut: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Detalle del Acceso",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = NavyPrimary
                )
                VisitorStatusBadge(status = entry.status)
            }

            Spacer(modifier = Modifier.height(16.dp))

            DetailFieldRow("Visitante", entry.visitorName, Icons.Default.Person)
            DetailFieldRow("Tipo", entry.visitorType, Icons.Default.Info)
            DetailFieldRow("Destino", entry.destinationHouse, Icons.Default.Home)
            if (!entry.vehiclePlate.isNullOrBlank()) {
                DetailFieldRow("Placas", entry.vehiclePlate, Icons.Default.DirectionsCar)
            }
            DetailFieldRow("Folio", entry.folio, Icons.Default.ConfirmationNumber)
            DetailFieldRow("Método", entry.accessMethod, Icons.Default.QrCode)
            DetailFieldRow("Hora de Entrada", "${entry.formattedDate} · ${entry.formattedTime}", Icons.Default.Schedule)
            DetailFieldRow("Autorizado Por", entry.authorizedBy, Icons.Default.CheckCircle)
            if (!entry.notes.isNullOrBlank()) {
                DetailFieldRow("Observaciones", entry.notes, Icons.Default.Notes)
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (entry.status == VisitorStatus.DENTRO) {
                Button(
                    onClick = onCheckOut,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = StatusAmber)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Registrar Salida del Condominio")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun DetailFieldRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = "$label: ", fontSize = 13.sp, color = TextMuted)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NavyPrimary)
    }
}

@Composable
fun QuickAddVisitorDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var house by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar Ingreso Rápido", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del Visitante *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = house,
                    onValueChange = { house = it },
                    label = { Text("Vivienda Destino * (ej. Casa 54)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = plate,
                    onValueChange = { plate = it },
                    label = { Text("Placas (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observaciones (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, house, plate, notes) },
                enabled = name.isNotBlank() && house.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
            ) {
                Text("Registrar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun EmptyVisitorEntriesView(
    searchQuery: String,
    onClearSearch: () -> Unit,
    onOpenAddDialog: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.SearchOff,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (searchQuery.isNotEmpty()) "No se encontraron visitantes coincidentes" else "No hay registros disponibles",
            color = NavyPrimary,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (searchQuery.isNotEmpty()) {
            TextButton(onClick = onClearSearch) { Text("Limpiar Filtros") }
        } else {
            Button(
                onClick = onOpenAddDialog,
                colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
            ) {
                Text("Registrar Primer Acceso")
            }
        }
    }
}
