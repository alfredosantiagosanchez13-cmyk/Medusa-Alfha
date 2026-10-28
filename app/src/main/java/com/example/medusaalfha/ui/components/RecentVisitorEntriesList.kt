package com.example.medusaalfha.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Header Táctico: VISITANTES RECIENTES • FIRESTORE
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "VISITANTES RECIENTES",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        color = GoldMetallic,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF042F2E),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TealAccent.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(TealAccent)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "FIRESTORE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealAccent
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Bitácora en tiempo real · Los Prados Residencial",
                    fontSize = 12.sp,
                    color = CyberTextMuted
                )
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("refresh_visitor_log_button")
            ) {
                if (uiState.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = NeonBlue,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sincronizar",
                        tint = GoldMetallic,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Métricas rápidas superiores (TOTAL HOY, EN SITIO, SALIDAS)
        VisitorSummaryMetricsRow(
            totalInside = uiState.totalInsideCount,
            totalToday = uiState.totalTodayCount,
            totalCheckedOut = uiState.totalCheckedOutCount
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Barra de búsqueda con botón de Escaneo QR integrado
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                color = CyberSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorderCircuit)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = GoldMetallic,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchChanged,
                        placeholder = {
                            Text(
                                text = "Buscar por nombre, casa, placa o folio...",
                                color = CyberTextMuted,
                                fontSize = 13.sp
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = true
                    )
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChanged("") }) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Limpiar",
                                tint = CyberTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            FilledTonalIconButton(
                onClick = onOpenScanner,
                modifier = Modifier
                    .size(52.dp)
                    .testTag("scan_qr_header_button"),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = TealAccent.copy(alpha = 0.2f),
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

        // Pestañas de Filtro (Chips: Todos, En Sitio, Salidas, Paquetería)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VisitorFilterTab.values().forEach { tab ->
                val selected = uiState.currentFilter == tab
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) GoldMetallic else CyberSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (selected) GoldMetallic else CyberBorderCircuit
                    ),
                    modifier = Modifier
                        .clickable { onFilterSelected(tab) }
                        .testTag("filter_tab_${tab.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = tab.title,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) Color.Black else CyberTextMuted
                        )
                    }
                }
            }
        }

        // Aviso discreto de sincronización con Firestore
        if (uiState.refreshFeedbackMessage != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                color = TealAccent.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TealAccent.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = TealAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = uiState.refreshFeedbackMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = TealAccent,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        val pullRefreshState = rememberPullToRefreshState()

        // Contenedor Pull-To-Refresh de la bitácora
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            state = pullRefreshState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("visitor_pull_to_refresh_box"),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = uiState.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    containerColor = CyberSurfaceElevated,
                    color = NeonBlue
                )
            }
        ) {
            if (uiState.isLoading && !uiState.isRefreshing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = TealAccent)
                }
            } else if (uiState.filteredEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    EmptyVisitorEntriesView(
                        searchQuery = uiState.searchQuery,
                        onClearSearch = { onSearchChanged("") },
                        onOpenAddDialog = onOpenAddDialog
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
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
    totalCheckedOut: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MetricCard(
            label = "TOTAL HOY",
            value = totalToday.toString(),
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            label = "EN SITIO",
            value = totalInside.toString(),
            color = EmeraldGreen,
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            label = "SALIDAS",
            value = totalCheckedOut.toString(),
            color = NeonBlue,
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
        color = CyberSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorderCircuit),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = CyberTextMuted,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
        }
    }
}

@Composable
fun VisitorEntryCard(
    entry: VisitorEntry,
    onClick: () -> Unit,
    onCheckOut: () -> Unit
) {
    val isPackage = entry.visitorType.contains("paquet", ignoreCase = true) ||
            entry.visitorName.contains("amazon", ignoreCase = true) ||
            entry.visitorName.contains("mercado", ignoreCase = true) ||
            entry.visitorName.contains("logistics", ignoreCase = true)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CyberSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberBorderCircuit),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("visitor_card_${entry.folio}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPackage) Color(0xFF1E293B) else TealAccent.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPackage) Icons.Default.LocalShipping else Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isPackage) Color(0xFF38BDF8) else TealAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isPackage) "PAQUETERÍA • ${entry.folio}" else "VISITANTE • ${entry.folio}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberTextMuted
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = entry.visitorName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }

                VisitorStatusBadge(status = entry.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Destino con icono de casa en cyan
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = entry.destinationHouse,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF38BDF8)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Horario y Placas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = CyberTextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${entry.formattedTime} (${entry.relativeTimeAgo})",
                        fontSize = 11.sp,
                        color = CyberTextMuted
                    )
                }

                if (!entry.vehiclePlate.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E293B),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFF334155))
                    ) {
                        Text(
                            text = entry.vehiclePlate,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (entry.status == VisitorStatus.DENTRO) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onCheckOut,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusAmber),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StatusAmber.copy(alpha = 0.5f)),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Marcar Salida",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Marcar Salida", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun VisitorStatusBadge(status: VisitorStatus) {
    val (label, bg, fg) = when (status) {
        VisitorStatus.DENTRO -> Triple("• En Sitio", Color(0xFF064E3B), EmeraldGreen)
        VisitorStatus.SALIDA -> Triple("• Salida Registrada", Color(0xFF1E293B), Color(0xFF94A3B8))
        VisitorStatus.AUTORIZADO -> Triple("• Autorizado", Color(0xFF1E3A8A), Color(0xFF60A5FA))
        VisitorStatus.DENEGADO -> Triple("• Denegado", Color(0xFF450A0A), AlertRed)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
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
        Icon(imageVector = icon, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = "$label: ", fontSize = 13.sp, color = CyberTextMuted)
        Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
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
        containerColor = CyberSurface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = GoldMetallic)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Registrar Ingreso Rápido", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del Visitante *") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = CyberBorderCircuit,
                        focusedLabelColor = NeonBlue,
                        unfocusedLabelColor = CyberTextMuted
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_quick_visitor_name")
                )
                OutlinedTextField(
                    value = house,
                    onValueChange = { house = it },
                    label = { Text("Vivienda Destino * (ej. Casa 54)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = CyberBorderCircuit,
                        focusedLabelColor = NeonBlue,
                        unfocusedLabelColor = CyberTextMuted
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_quick_visitor_house")
                )
                OutlinedTextField(
                    value = plate,
                    onValueChange = { plate = it },
                    label = { Text("Placas (opcional)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = CyberBorderCircuit,
                        focusedLabelColor = NeonBlue,
                        unfocusedLabelColor = CyberTextMuted
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_quick_visitor_plate")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observaciones (opcional)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = CyberBorderCircuit,
                        focusedLabelColor = NeonBlue,
                        unfocusedLabelColor = CyberTextMuted
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_quick_visitor_notes")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, house, plate, notes) },
                enabled = name.isNotBlank() && house.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic, contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_confirm_quick_visitor")
            ) {
                Text("Registrar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = CyberTextMuted) }
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
            tint = CyberTextMuted,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (searchQuery.isNotEmpty()) "No se encontraron visitantes coincidentes" else "No hay registros disponibles",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (searchQuery.isNotEmpty()) "Prueba con otro término de búsqueda o folio" else "Los ingresos de caseta y pases QR se listarán aquí",
            color = CyberTextMuted,
            fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))
        if (searchQuery.isNotEmpty()) {
            OutlinedButton(
                onClick = onClearSearch,
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonBlue),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Limpiar Filtros")
            }
        } else {
            Button(
                onClick = onOpenAddDialog,
                colors = ButtonDefaults.buttonColors(containerColor = GoldMetallic, contentColor = Color.Black),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Registrar Primer Acceso", fontWeight = FontWeight.Bold)
            }
        }
    }
}
