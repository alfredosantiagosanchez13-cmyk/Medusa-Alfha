package com.example.medusaalfha.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.medusaalfha.data.model.VisitorEntry
import com.example.medusaalfha.data.model.VisitorStatus
import com.example.medusaalfha.ui.theme.*
import com.example.medusaalfha.ui.viewmodel.VisitorFilterTab
import com.example.medusaalfha.ui.viewmodel.VisitorUiState

/**
 * Componente oficial de Lista en Material 3 para desplegar los accesos de visitantes
 * obtenidos en tiempo real desde Cloud Firestore (/condominiums/PRADOS_1/visitor_logs).
 * 
 * Cumple con diseño Material 3, microinteracciones táctiles, badges semánticos de estado,
 * búsqueda reactiva y visualización de marcas de tiempo relativas y exactas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentVisitorEntriesList(
    uiState: VisitorUiState,
    onSearchChange: (String) -> Unit,
    onFilterSelect: (VisitorFilterTab) -> Unit,
    onSelectEntry: (VisitorEntry) -> Unit,
    onDismissDetail: () -> Unit,
    onMarkCheckOut: (String) -> Unit,
    onSeedData: () -> Unit,
    onQuickAddVisitor: (name: String, house: String, plate: String, type: String) -> Unit,
    onClearNotice: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }

    // Notificación flotante de confirmación (Snackbar / Toast M3)
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.userNotice) {
        uiState.userNotice?.let { notice ->
            snackbarHostState.showSnackbar(notice, withDismissAction = true)
            onClearNotice()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "VISITANTES RECIENTES",
                                color = GoldPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            // Indicador en vivo de Cloud Firestore
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(CyanNeon.copy(alpha = 0.2f))
                                    .border(1.dp, CyanNeon, CircleShape)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(CyanNeon)
                                    )
                                    Text(
                                        text = "FIRESTORE",
                                        color = CyanNeon,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Bitácora en tiempo real · Los Prados Residencial",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSeedData,
                        modifier = Modifier.testTag("btn_seed_firestore")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Sincronizar Firestore",
                            tint = GoldPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyDark,
                    titleContentColor = GoldPrimary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null, tint = NavyDark) },
                text = { Text("REGISTRAR VISITA", fontWeight = FontWeight.Bold, color = NavyDark) },
                containerColor = GoldPrimary,
                contentColor = NavyDark,
                modifier = Modifier.testTag("fab_add_visitor")
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = NavyDark,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // =========================================================================
            // 1. MÉTRICAS RÁPIDAS (METRIC CHIPS)
            // =========================================================================
            VisitorSummaryMetricsRow(
                totalCount = uiState.allEntries.size,
                insideCount = uiState.allEntries.count { it.status == VisitorStatus.DENTRO },
                completedCount = uiState.allEntries.count { it.status == VisitorStatus.SALIDA }
            )

            // =========================================================================
            // 2. BARRA DE BÚSQUEDA M3
            // =========================================================================
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChange,
                placeholder = {
                    Text(
                        text = "Buscar por nombre, casa, placa o folio...",
                        color = TextDim,
                        fontSize = 13.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = GoldPrimary
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Limpiar búsqueda",
                                tint = TextMuted
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = NavyBorder,
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedContainerColor = NavySurface,
                    unfocusedContainerColor = NavySurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("search_visitor_input")
            )

            // =========================================================================
            // 3. FILTROS RÁPIDOS M3 (FILTER CHIPS)
            // =========================================================================
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(VisitorFilterTab.values()) { tab ->
                    val isSelected = uiState.activeFilter == tab
                    FilterChip(
                        selected = isSelected,
                        onClick = { onFilterSelect(tab) },
                        label = {
                            Text(
                                text = tab.label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) NavyDark else GoldPrimary
                                )
                            }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = NavySurface,
                            labelColor = TextMuted,
                            selectedContainerColor = GoldPrimary,
                            selectedLabelColor = NavyDark
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = NavyBorder,
                            selectedBorderColor = GoldPrimary,
                            enabled = true,
                            selected = isSelected
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("filter_tab_${tab.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // =========================================================================
            // 4. LISTA DE ENTRADAS RECIENTES (LAZY COLUMN)
            // =========================================================================
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = GoldPrimary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Consultando registros en Cloud Firestore...",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else if (uiState.filteredEntries.isEmpty()) {
                // Estado Vacío Elegante
                EmptyVisitorEntriesView(
                    searchQuery = uiState.searchQuery,
                    onSeedSample = onSeedData,
                    onAddNew = { showAddDialog = true }
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("visitor_entries_list")
                ) {
                    items(
                        items = uiState.filteredEntries,
                        key = { it.folio.ifBlank { it.checkInTimestamp.toString() } }
                    ) { entry ->
                        VisitorEntryCard(
                            entry = entry,
                            onClick = { onSelectEntry(entry) },
                            onMarkOut = { onMarkCheckOut(entry.folio) }
                        )
                    }
                }
            }
        }
    }

    // Modal BottomSheet de Detalles
    uiState.selectedEntry?.let { entry ->
        VisitorEntryDetailBottomSheet(
            entry = entry,
            onDismiss = onDismissDetail,
            onMarkCheckOut = {
                onMarkCheckOut(entry.folio)
                onDismissDetail()
            }
        )
    }

    // Diálogo de Registro Rápido
    if (showAddDialog) {
        QuickAddVisitorDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, house, plate, type ->
                onQuickAddVisitor(name, house, plate, type)
                showAddDialog = false
            }
        )
    }
}

/**
 * Tarjeta individual para cada ingreso de visitante en Material 3.
 */
@Composable
fun VisitorEntryCard(
    entry: VisitorEntry,
    onClick: () -> Unit,
    onMarkOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = NavySurface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, NavyBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("visitor_item_${entry.folio}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Fila superior: Tipo, Folio y Badge de Estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(NavyCard)
                            .border(1.dp, GoldPrimary.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getIconForVisitorType(entry.visitorType),
                            contentDescription = entry.visitorType,
                            tint = GoldPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = entry.visitorType.uppercase(),
                        color = GoldLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "• ${entry.folio}",
                        color = TextDim,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Badge de Estado con color semántico
                VisitorStatusBadge(status = entry.status)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Nombre del Visitante
            Text(
                text = entry.visitorName,
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Destino (Casa / Lote de Los Prados)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    tint = CyanNeon,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = entry.destinationHouse,
                    color = CyanNeon,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Fila inferior: Timestamps y placa vehicular
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hora y tiempo relativo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${entry.formattedTime} (${entry.relativeTimeAgo})",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                // Placa vehicular o botón rápido de salida
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    entry.vehiclePlate?.let { plate ->
                        Surface(
                            color = NavyCard,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.8.dp, NavyBorder)
                        ) {
                            Text(
                                text = plate,
                                color = TextWhite,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (entry.status == VisitorStatus.DENTRO) {
                        FilledTonalButton(
                            onClick = onMarkOut,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF334155),
                                contentColor = TextWhite
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Salida", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Badge de Estado estilizado de acuerdo a Material 3 y estándares semánticos.
 */
@Composable
fun VisitorStatusBadge(status: VisitorStatus, modifier: Modifier = Modifier) {
    val (bgColor, textColor, borderColor) = when (status) {
        VisitorStatus.DENTRO -> Triple(
            StatusInsideContainer,
            StatusInsideGreen,
            StatusInsideGreen
        )
        VisitorStatus.SALIDA -> Triple(
            StatusCompletedContainer,
            StatusCompletedSlate,
            StatusCompletedSlate
        )
        VisitorStatus.AUTORIZADO -> Triple(
            StatusPendingContainer,
            StatusPendingCyan,
            StatusPendingCyan
        )
        VisitorStatus.DENEGADO -> Triple(
            StatusDeniedContainer,
            StatusDeniedRed,
            StatusDeniedRed
        )
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(0.8.dp, borderColor.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Text(
                text = status.label,
                color = textColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Resumen superior con contadores de actividad táctica.
 */
@Composable
private fun VisitorSummaryMetricsRow(
    totalCount: Int,
    insideCount: Int,
    completedCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricCard(
            label = "TOTAL HOY",
            value = totalCount.toString(),
            color = GoldPrimary,
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            label = "EN SITIO",
            value = insideCount.toString(),
            color = StatusInsideGreen,
            modifier = Modifier.weight(1f)
        )
        MetricCard(
            label = "SALIDAS",
            value = completedCount.toString(),
            color = StatusCompletedSlate,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = NavySurface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, NavyBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(text = value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}

/**
 * BottomSheet con detalles forenses del acceso del visitante.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitorEntryDetailBottomSheet(
    entry: VisitorEntry,
    onDismiss: () -> Unit,
    onMarkCheckOut: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DETALLE DE INGRESO",
                        color = GoldPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = entry.folio,
                        color = TextWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                VisitorStatusBadge(status = entry.status)
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = NavyBorder)
            Spacer(modifier = Modifier.height(14.dp))

            DetailFieldRow(label = "Visitante", value = entry.visitorName, icon = Icons.Default.Person)
            DetailFieldRow(label = "Destino", value = entry.destinationHouse, icon = Icons.Default.Home)
            DetailFieldRow(label = "Tipo", value = entry.visitorType, icon = Icons.Default.Category)
            DetailFieldRow(label = "Hora Ingreso", value = "${entry.formattedDate} · ${entry.formattedTime}", icon = Icons.Default.Login)
            entry.checkOutTimestamp?.let {
                val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                DetailFieldRow(label = "Hora Salida", value = sdf.format(java.util.Date(it)), icon = Icons.Default.Logout)
            }
            entry.vehiclePlate?.let {
                DetailFieldRow(label = "Placa Vehicular", value = it, icon = Icons.Default.DirectionsCar)
            }
            DetailFieldRow(label = "Autorizado Por", value = entry.authorizedBy, icon = Icons.Default.VerifiedUser)
            DetailFieldRow(label = "Caseta / Guardia", value = entry.guardName, icon = Icons.Default.Security)
            DetailFieldRow(label = "Método Acceso", value = entry.accessMethod, icon = Icons.Default.QrCodeScanner)

            if (entry.notes.isNotBlank()) {
                DetailFieldRow(label = "Observaciones", value = entry.notes, icon = Icons.Default.Notes)
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (entry.status == VisitorStatus.DENTRO) {
                Button(
                    onClick = onMarkCheckOut,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("REGISTRAR SALIDA DEL CONDOMINIO", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NavyBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Text("Cerrar", color = TextWhite)
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun DetailFieldRow(
    label: String,
    value: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = GoldPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = "$label: ", color = TextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(
            text = value,
            color = TextWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Diálogo para registrar rápidamente un visitante de prueba y verificar el enlace a Firestore.
 */
@Composable
fun QuickAddVisitorDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, house: String, plate: String, type: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var house by remember { mutableStateOf("Casa 54 · Circuito Los Álamos") }
    var plate by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("VISITANTE") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "REGISTRAR VISITA A FIRESTORE",
                color = GoldPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del visitante", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = house,
                    onValueChange = { house = it },
                    label = { Text("Casa / Destino en Los Prados", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = plate,
                    onValueChange = { plate = it },
                    label = { Text("Placa del vehículo (opcional)", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(name, house, plate, type)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark)
            ) {
                Text("Guardar en Firestore", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextMuted)
            }
        },
        containerColor = NavySurface,
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * Estado vacío cuando no hay registros de visitantes en Firestore.
 */
@Composable
fun EmptyVisitorEntriesView(
    searchQuery: String,
    onSeedSample: () -> Unit,
    onAddNew: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(NavyCard)
                    .border(1.5.dp, GoldPrimary.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Badge,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (searchQuery.isNotBlank()) "Sin resultados para \"$searchQuery\"" else "Sin registros de visitantes",
                color = TextWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (searchQuery.isNotBlank())
                    "Prueba con otro nombre, número de casa o placa vehicular."
                else
                    "La colección en Cloud Firestore no tiene ingresos registrados aún.",
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onSeedSample,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, GoldPrimary)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cargar Catálogo Prados", color = GoldPrimary, fontSize = 12.sp)
                }

                Button(
                    onClick = onAddNew,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Nuevo Ingreso", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun getIconForVisitorType(type: String): ImageVector {
    return when (type.uppercase()) {
        "PAQUETERÍA", "PAQUETERIA", "DELIVERY" -> Icons.Default.LocalShipping
        "SERVICIOS", "MANTENIMIENTO", "TÉCNICO" -> Icons.Default.Build
        "FAMILIAR" -> Icons.Default.Diversity3
        "PROVEEDOR" -> Icons.Default.Inventory2
        else -> Icons.Default.Person
    }
}
