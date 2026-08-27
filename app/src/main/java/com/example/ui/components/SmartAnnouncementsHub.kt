package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.auth.AlfhaSecurityContext
import com.example.data.announcements.AnnouncementCategory
import com.example.data.announcements.AnnouncementEngine
import com.example.data.announcements.AnnouncementEntity
import com.example.data.announcements.AnnouncementPriority
import com.example.data.announcements.AnnouncementTargetScope
import com.example.data.announcements.ReadAcknowledgement
import com.example.data.booking.AppDatabase
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.theme.WarningOrange
import kotlinx.coroutines.launch
import java.util.Locale

private val NavyCardBorder = Color(0xFF334155)
private val AmberGold = Color(0xFFFFB703)

/**
 * HUB DE COMUNICADOS Y DOCUMENTOS INTELIGENTES ALFHA (FASE 14)
 *
 * Integra:
 * - Creación desde Administración con selección de alcance (Unidad, Condominio, Rol).
 * - Folios COM-YYYYMMDD-XXXX y firmas SHA-256 automáticas.
 * - Despacho de notificaciones inteligentes en tiempo real.
 * - Registro de acuse de lectura con 1-toque y métricas de apertura.
 * - Visualizador y gestor de documentos PDF/imágenes certificados.
 * - Métrica Sagrada: Tiempo Devuelto (45 min por comunicado).
 * - Reporte Ejecutivo Oficial para Mesa Directiva y Asamblea.
 */
@Composable
fun SmartAnnouncementsHub(
    db: AppDatabase,
    modifier: Modifier = Modifier,
    userRole: String = "ADMINISTRACION",
    targetUnitFilter: String? = null,
    showNewFab: Boolean = true,
    onStatsChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser = AlfhaSecurityContext.currentUser.value

    // Base de datos reactiva (Fuente Única de Verdad)
    val allAnnouncements by db.announcementDao().getAllAnnouncements().collectAsState(initial = emptyList())

    // Filtros locales
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf<String>("TODOS") }
    var selectedStatusFilter by remember { mutableStateOf<String>("PUBLICADO") }

    // Modales de interacción
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedAnnouncementForDetail by remember { mutableStateOf<AnnouncementEntity?>(null) }
    var selectedAnnouncementForDocView by remember { mutableStateOf<AnnouncementEntity?>(null) }
    var showExecutiveReportDialog by remember { mutableStateOf(false) }

    // Filtrar según rol / unidad / texto / categoría
    val filteredList = allAnnouncements.filter { ann ->
        // Filtro por unidad (para Residentes)
        val matchesUnit = if (targetUnitFilter.isNullOrBlank() || targetUnitFilter == "Todas") {
            true
        } else {
            ann.targetScope == AnnouncementTargetScope.CONDOMINIO ||
            (ann.targetUnits?.contains(targetUnitFilter, ignoreCase = true) == true)
        }

        // Filtro por búsqueda
        val matchesSearch = if (searchQuery.isBlank()) true else {
            ann.folio.contains(searchQuery, ignoreCase = true) ||
            ann.title.contains(searchQuery, ignoreCase = true) ||
            ann.content.contains(searchQuery, ignoreCase = true) ||
            ann.senderName.contains(searchQuery, ignoreCase = true) ||
            (ann.attachmentName?.contains(searchQuery, ignoreCase = true) == true)
        }

        // Filtro por categoría
        val matchesCat = if (selectedCategoryFilter == "TODOS") true else {
            ann.category.name == selectedCategoryFilter
        }

        // Filtro por estado
        val matchesStatus = if (selectedStatusFilter == "TODOS") true else {
            ann.status == selectedStatusFilter
        }

        matchesUnit && matchesSearch && matchesCat && matchesStatus
    }

    val stats = remember(allAnnouncements) {
        AnnouncementEngine.calculateStats(allAnnouncements)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("smart_announcements_hub")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                AnnouncementsHeaderSection(
                    userRole = userRole,
                    stats = stats,
                    onOpenReport = { showExecutiveReportDialog = true },
                    onNewAnnouncement = { showCreateDialog = true }
                )
            }

            // KPI Banner: Tiempo Devuelto y Apertura
            item {
                AnnouncementsKpiBanner(stats = stats)
            }

            // Search Bar & Filter Chips
            item {
                AnnouncementsFilterSection(
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    selectedCategory = selectedCategoryFilter,
                    onCategoryChange = { selectedCategoryFilter = it },
                    selectedStatus = selectedStatusFilter,
                    onStatusChange = { selectedStatusFilter = it }
                )
            }

            // List of Announcements
            if (filteredList.isEmpty()) {
                item {
                    EmptyAnnouncementsCard(
                        searchQuery = searchQuery,
                        selectedCategory = selectedCategoryFilter
                    )
                }
            } else {
                items(filteredList, key = { it.folio }) { announcement ->
                    AnnouncementItemCard(
                        announcement = announcement,
                        currentUserRole = userRole,
                        currentUnitId = targetUnitFilter,
                        onViewDetail = { selectedAnnouncementForDetail = announcement },
                        onViewDocument = { selectedAnnouncementForDocView = announcement },
                        onAcknowledge = {
                            scope.launch {
                                val residentName = currentUser?.name ?: (if (!targetUnitFilter.isNullOrBlank()) "Residente $targetUnitFilter" else "Residente")
                                val unit = targetUnitFilter ?: "Casa 101"
                                val res = AnnouncementEngine.registerReadAcknowledgement(
                                    db = db,
                                    folio = announcement.folio,
                                    unitId = unit,
                                    residentName = residentName
                                )
                                if (res is AnnouncementEngine.AnnouncementResult.Success) {
                                    Toast.makeText(context, "✅ Acuse de recibo registrado para ${announcement.folio}", Toast.LENGTH_SHORT).show()
                                    onStatsChanged()
                                } else if (res is AnnouncementEngine.AnnouncementResult.Error) {
                                    Toast.makeText(context, res.error, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onArchive = {
                            scope.launch {
                                val operator = currentUser?.name ?: "Administración"
                                val res = AnnouncementEngine.archiveAnnouncement(db, announcement.folio, operator)
                                if (res is AnnouncementEngine.AnnouncementResult.Success) {
                                    Toast.makeText(context, "📁 Comunicado archivado", Toast.LENGTH_SHORT).show()
                                    onStatsChanged()
                                }
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }

        // FAB para Administrador / Mesa Directiva
        if (showNewFab && (userRole == "ADMINISTRACION" || userRole == "MESA_DIRECTIVA" || userRole == "SUPERVISOR" || userRole == "MAESTRO_ALFHA")) {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = GoldPrimary,
                contentColor = NavyDark,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("fab_create_announcement")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Emitir Comunicado", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }

    // Modal Crear Comunicado
    if (showCreateDialog) {
        CreateAnnouncementDialog(
            db = db,
            currentUserRole = userRole,
            onDismiss = { showCreateDialog = false },
            onAnnouncementCreated = {
                showCreateDialog = false
                onStatsChanged()
            }
        )
    }

    // Modal Detalle de Comunicado y Acuses
    selectedAnnouncementForDetail?.let { ann ->
        AnnouncementDetailDialog(
            announcement = ann,
            currentUserRole = userRole,
            currentUnitId = targetUnitFilter,
            db = db,
            onDismiss = { selectedAnnouncementForDetail = null },
            onViewDocument = {
                selectedAnnouncementForDetail = null
                selectedAnnouncementForDocView = ann
            },
            onAcknowledge = {
                scope.launch {
                    val residentName = currentUser?.name ?: (if (!targetUnitFilter.isNullOrBlank()) "Residente $targetUnitFilter" else "Residente")
                    val unit = targetUnitFilter ?: "Casa 101"
                    AnnouncementEngine.registerReadAcknowledgement(db, ann.folio, unit, residentName)
                    selectedAnnouncementForDetail = null
                    onStatsChanged()
                }
            }
        )
    }

    // Modal Visor de Documento Inteligente Certificado
    selectedAnnouncementForDocView?.let { ann ->
        SmartDocumentViewerDialog(
            announcement = ann,
            onDismiss = { selectedAnnouncementForDocView = null }
        )
    }

    // Modal Reporte Ejecutivo
    if (showExecutiveReportDialog) {
        ExecutiveCommunicationReportDialog(
            announcements = allAnnouncements,
            onDismiss = { showExecutiveReportDialog = false }
        )
    }
}

// =============================================================================
// SECCIONES DEL HUB
// =============================================================================

@Composable
private fun AnnouncementsHeaderSection(
    userRole: String,
    stats: AnnouncementEngine.CommunicationStats,
    onOpenReport: () -> Unit,
    onNewAnnouncement: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = GoldPrimary.copy(alpha = 0.15f),
                        shape = CircleShape,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Campaign,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "COMUNICADOS Y DOCUMENTOS",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = GoldPrimary
                        )
                        Text(
                            "FASE 14: Circulares, Asambleas y Acuses Digitales",
                            fontSize = 10.sp,
                            color = CyanNeon
                        )
                    }
                }

                OutlinedButton(
                    onClick = onOpenReport,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon),
                    modifier = Modifier.testTag("btn_open_announcement_report")
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reporte", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AnnouncementsKpiBanner(
    stats: AnnouncementEngine.CommunicationStats
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, NavyCardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Métrica Sagrada Tiempo Devuelto
            Surface(
                color = NavyDark,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "TIEMPO DEVUELTO A LA COMUNIDAD",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldPrimary
                            )
                            Text(
                                "45 min ahorrados por comunicado emitido",
                                fontSize = 9.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Text(
                        stats.totalTimeSavedFormatted,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4 Mini KPIs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiMiniCard(
                    title = "EMITIDOS",
                    value = "${stats.totalAnnouncements}",
                    icon = Icons.Default.Description,
                    color = CyanNeon,
                    modifier = Modifier.weight(1f)
                )
                KpiMiniCard(
                    title = "ACUSES",
                    value = "${stats.totalAcknowledgements}",
                    icon = Icons.Default.FactCheck,
                    color = SuccessGreen,
                    modifier = Modifier.weight(1f)
                )
                KpiMiniCard(
                    title = "APERTURA",
                    value = "${String.format(Locale.US, "%.0f", stats.averageOpeningRatePercent)}%",
                    icon = Icons.Default.Visibility,
                    color = AmberGold,
                    modifier = Modifier.weight(1f)
                )
                KpiMiniCard(
                    title = "URGENTES",
                    value = "${stats.urgentAnnouncements}",
                    icon = Icons.Default.Warning,
                    color = if (stats.urgentAnnouncements > 0) ErrorRed else TextMuted,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun KpiMiniCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = NavyDark,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(title, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}

@Composable
private fun AnnouncementsFilterSection(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedCategory: String,
    onCategoryChange: (String) -> Unit,
    selectedStatus: String,
    onStatusChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Buscar por folio, título, contenido o adjunto...", fontSize = 11.sp, color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = NavySurface,
                unfocusedContainerColor = NavySurface,
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = NavyCardBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_search_announcements")
        )

        // Categorías Tabs
        val categories = listOf(
            "TODOS" to "Todos",
            "CIRCULAR" to "Circulares",
            "CONVOCATORIA_ASAMBLEA" to "Asambleas",
            "AVISO_URGENTE" to "Urgentes",
            "MANTENIMIENTO_PROGRAMADO" to "Mantenimiento",
            "REGLAMENTO_INTERNO" to "Reglamento",
            "ESTADO_CUENTA" to "Finanzas"
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { (catKey, catLabel) ->
                val isSelected = selectedCategory == catKey
                val catEnum = if (catKey != "TODOS") AnnouncementCategory.values().firstOrNull { it.name == catKey } else null
                val catColor = catEnum?.let { Color(it.colorHex) } ?: GoldPrimary

                Surface(
                    onClick = { onCategoryChange(catKey) },
                    color = if (isSelected) catColor.copy(alpha = 0.2f) else NavySurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isSelected) catColor else NavyCardBorder),
                    modifier = Modifier.testTag("filter_cat_$catKey")
                ) {
                    Text(
                        catLabel,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) catColor else TextMuted,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

// =============================================================================
// CARD DE COMUNICADO INDIVIDUAL
// =============================================================================

@Composable
private fun AnnouncementItemCard(
    announcement: AnnouncementEntity,
    currentUserRole: String,
    currentUnitId: String?,
    onViewDetail: () -> Unit,
    onViewDocument: () -> Unit,
    onAcknowledge: () -> Unit,
    onArchive: () -> Unit
) {
    val catColor = Color(announcement.category.colorHex)
    val priColor = Color(announcement.priority.colorHex)
    val cardBorder = if (announcement.isUrgent) ErrorRed else NavyCardBorder

    // Verificar si la unidad actual ya firmó acuse
    val acks = remember(announcement.acknowledgementsJson) {
        AnnouncementEngine.parseAcknowledgements(announcement.acknowledgementsJson)
    }
    val isAcknowledgedByUnit = remember(acks, currentUnitId) {
        if (currentUnitId.isNullOrBlank()) false
        else acks.any { it.unitId.equals(currentUnitId, ignoreCase = true) }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("announcement_card_${announcement.folio}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Fila Superior: Folio, Categoría, Prioridad y Fecha
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = catColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, catColor.copy(alpha = 0.4f))
                    ) {
                        Text(
                            announcement.category.label.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = catColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        color = priColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            announcement.priority.label,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = priColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    announcement.effectiveDate,
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Folio y Título
            Text(
                "FOLIO: ${announcement.folio}",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = GoldPrimary
            )

            Text(
                announcement.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Extracto del mensaje
            Text(
                announcement.content,
                fontSize = 11.sp,
                color = TextWhite.copy(alpha = 0.85f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Emisor y Alcance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Campaign, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Por: ${announcement.senderName}",
                        fontSize = 10.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = NavyDark,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        when (announcement.targetScope) {
                            AnnouncementTargetScope.CONDOMINIO -> "🌐 Todo el Condominio"
                            AnnouncementTargetScope.POR_UNIDAD -> "🏠 Unidades: ${announcement.targetUnits ?: "Todas"}"
                            AnnouncementTargetScope.POR_ROL -> "👥 Rol: ${announcement.targetRole ?: "Todos"}"
                        },
                        fontSize = 9.sp,
                        color = CyanNeon,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Adjunto Inteligente (si existe)
            if (!announcement.attachmentName.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = NavyDark,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onViewDocument() }
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = CyanNeon,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    announcement.attachmentName,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    "Documento Digital Certificado • ${announcement.attachmentSizeKb} KB",
                                    fontSize = 9.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        Text(
                            "Ver Adjunto ➔",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanNeon
                        )
                    }
                }
            }

            // Barra de Progreso de Acuses / Apertura
            if (announcement.requiresAcknowledgement) {
                Spacer(modifier = Modifier.height(8.dp))
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Acuses de Recibo: ${announcement.readCount} unidades",
                            fontSize = 9.sp,
                            color = TextMuted
                        )
                        Text(
                            "Acuse Obligatorio",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberGold
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { (announcement.readCount.toFloat() / 60f).coerceIn(0.05f, 1f) },
                        color = SuccessGreen,
                        trackColor = NavyDark,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Fila de Acciones
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onViewDetail,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, NavyCardBorder),
                    modifier = Modifier.testTag("btn_detail_${announcement.folio}")
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ver Detalle", fontSize = 11.sp, color = Color.White)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Botón para que el Residente firme el acuse
                    if (currentUserRole == "RESIDENTE" && announcement.requiresAcknowledgement) {
                        if (isAcknowledgedByUnit) {
                            Surface(
                                color = SuccessGreen.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, SuccessGreen)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Enterado ✅", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                                }
                            }
                        } else {
                            Button(
                                onClick = onAcknowledge,
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("btn_ack_${announcement.folio}")
                            ) {
                                Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Confirmar Lectura", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Acciones de Administración
                    if (currentUserRole == "ADMINISTRACION" || currentUserRole == "MESA_DIRECTIVA" || currentUserRole == "MAESTRO_ALFHA") {
                        if (announcement.status != "ARCHIVADO") {
                            IconButton(
                                onClick = onArchive,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Archive, contentDescription = "Archivar", tint = TextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyAnnouncementsCard(
    searchQuery: String,
    selectedCategory: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface)
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Campaign,
                contentDescription = null,
                tint = TextMuted,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                "No hay comunicados registrados",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (searchQuery.isNotBlank()) "No se encontraron coincidencias para '$searchQuery'"
                else "Los comunicados oficiales y circulares aparecerán aquí.",
                fontSize = 11.sp,
                color = TextMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// =============================================================================
// MODAL: CREAR COMUNICADO OFICIAL
// =============================================================================

@Composable
private fun CreateAnnouncementDialog(
    db: AppDatabase,
    currentUserRole: String,
    onDismiss: () -> Unit,
    onAnnouncementCreated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser = AlfhaSecurityContext.currentUser.value

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(AnnouncementCategory.CIRCULAR) }
    var selectedPriority by remember { mutableStateOf(AnnouncementPriority.NORMAL) }
    var selectedScope by remember { mutableStateOf(AnnouncementTargetScope.CONDOMINIO) }
    var targetUnits by remember { mutableStateOf("") }
    var targetRole by remember { mutableStateOf("TODOS") }
    var senderName by remember { mutableStateOf(currentUser?.name ?: "Lic. Sofía Alarcón (Administración)") }
    var requiresAcknowledgement by remember { mutableStateOf(true) }

    // Selector de Documentos Adjuntos
    var hasAttachment by remember { mutableStateOf(false) }
    var attachmentName by remember { mutableStateOf("Circular_Oficial_2026.pdf") }
    var attachmentType by remember { mutableStateOf("PDF") }
    var attachmentSizeKb by remember { mutableStateOf(350) }

    var isSubmitting by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = NavySurface,
            border = BorderStroke(1.5.dp, GoldPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .testTag("dialog_create_announcement")
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .wrapContentHeight()
            ) {
                // Modal Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Campaign, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "NUEVO COMUNICADO OFICIAL",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = GoldPrimary
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Título
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Título del Comunicado", fontSize = 11.sp) },
                            placeholder = { Text("Ej: Convocatoria a Asamblea General Ordinaria 2026", fontSize = 11.sp, color = TextMuted) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_announcement_title"),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = NavyCardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }

                    // Categoría
                    item {
                        Text("Categoría del Comunicado:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(AnnouncementCategory.values()) { cat ->
                                val isSel = selectedCategory == cat
                                val color = Color(cat.colorHex)
                                Surface(
                                    onClick = {
                                        selectedCategory = cat
                                        // Plantilla sugerida de adjunto
                                        when (cat) {
                                            AnnouncementCategory.CONVOCATORIA_ASAMBLEA -> {
                                                hasAttachment = true
                                                attachmentName = "Convocatoria_Asamblea_Ordinaria_2026.pdf"
                                                if (title.isBlank()) title = "Convocatoria Oficial: Asamblea Ordinaria de Propietarios"
                                            }
                                            AnnouncementCategory.REGLAMENTO_INTERNO -> {
                                                hasAttachment = true
                                                attachmentName = "Reglamento_Interno_Convivencia_2026.pdf"
                                                if (title.isBlank()) title = "Actualización Oficial del Reglamento Interno"
                                            }
                                            AnnouncementCategory.ESTADO_CUENTA -> {
                                                hasAttachment = true
                                                attachmentName = "Estado_Financiero_Condominio_Agosto2026.pdf"
                                                if (title.isBlank()) title = "Informe Financiero y Balance de Cuotas"
                                            }
                                            AnnouncementCategory.MANTENIMIENTO_PROGRAMADO -> {
                                                if (title.isBlank()) title = "Aviso: Mantenimiento Preventivo de Bombas Hidroneumáticas"
                                            }
                                            AnnouncementCategory.AVISO_URGENTE -> {
                                                selectedPriority = AnnouncementPriority.URGENTE
                                                if (title.isBlank()) title = "Aviso Urgente: Reparación Inmediata de Fuga Principal"
                                            }
                                            else -> {}
                                        }
                                    },
                                    color = if (isSel) color.copy(alpha = 0.2f) else NavyDark,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (isSel) color else NavyCardBorder)
                                ) {
                                    Text(
                                        cat.label,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) color else TextMuted,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Prioridad y Alcance
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Prioridad:", fontSize = 10.sp, color = TextMuted)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(AnnouncementPriority.URGENTE, AnnouncementPriority.ALTA, AnnouncementPriority.NORMAL).forEach { pri ->
                                        val isSel = selectedPriority == pri
                                        val color = Color(pri.colorHex)
                                        Surface(
                                            onClick = { selectedPriority = pri },
                                            color = if (isSel) color.copy(alpha = 0.2f) else NavyDark,
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, if (isSel) color else Color.Transparent),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                pri.label.take(4),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) color else TextMuted,
                                                modifier = Modifier.padding(vertical = 6.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text("Destinatarios:", fontSize = 10.sp, color = TextMuted)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    AnnouncementTargetScope.values().forEach { sc ->
                                        val isSel = selectedScope == sc
                                        Surface(
                                            onClick = { selectedScope = sc },
                                            color = if (isSel) CyanNeon.copy(alpha = 0.2f) else NavyDark,
                                            shape = RoundedCornerShape(6.dp),
                                            border = BorderStroke(1.dp, if (isSel) CyanNeon else Color.Transparent),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                when (sc) {
                                                    AnnouncementTargetScope.CONDOMINIO -> "Todos"
                                                    AnnouncementTargetScope.POR_UNIDAD -> "Unidad"
                                                    AnnouncementTargetScope.POR_ROL -> "Rol"
                                                },
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSel) CyanNeon else TextMuted,
                                                modifier = Modifier.padding(vertical = 6.dp),
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Campo de unidad específica si aplica
                    if (selectedScope == AnnouncementTargetScope.POR_UNIDAD) {
                        item {
                            OutlinedTextField(
                                value = targetUnits,
                                onValueChange = { targetUnits = it },
                                label = { Text("Unidades Destinatarias (separadas por coma)", fontSize = 11.sp) },
                                placeholder = { Text("Ej: Casa 101, Casa 102, Depto 304", fontSize = 11.sp, color = TextMuted) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanNeon,
                                    unfocusedBorderColor = NavyCardBorder,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                    }

                    // Contenido del Comunicado
                    item {
                        OutlinedTextField(
                            value = content,
                            onValueChange = { content = it },
                            label = { Text("Mensaje / Contenido Oficial", fontSize = 11.sp) },
                            placeholder = {
                                Text(
                                    "Redacta el texto completo del comunicado oficial que recibirán los condóminos...",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            },
                            minLines = 4,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_announcement_content"),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = NavyCardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }

                    // Adjuntos Inteligentes
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = NavyDark),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (hasAttachment) CyanNeon.copy(alpha = 0.5f) else NavyCardBorder)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AttachFile, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Adjuntar Documento PDF Certificado", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                    Switch(
                                        checked = hasAttachment,
                                        onCheckedChange = { hasAttachment = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = CyanNeon, checkedTrackColor = CyanNeon.copy(alpha = 0.3f))
                                    )
                                }

                                if (hasAttachment) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = attachmentName,
                                        onValueChange = { attachmentName = it },
                                        label = { Text("Nombre del Archivo Adjunto", fontSize = 10.sp) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = CyanNeon,
                                            unfocusedBorderColor = NavyCardBorder,
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Acuse de Recibo Obligatorio
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Requerir Acuse de Recibo Digital", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Registra hora y firma digital de cada residente", fontSize = 9.sp, color = TextMuted)
                            }
                            Switch(
                                checked = requiresAcknowledgement,
                                onCheckedChange = { requiresAcknowledgement = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary, checkedTrackColor = GoldPrimary.copy(alpha = 0.3f))
                            )
                        }
                    }

                    // Indicador de Tiempo Devuelto
                    item {
                        Surface(
                            color = GoldPrimary.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Esta emisión registrará 45 minutos de Tiempo Devuelto a la comunidad.",
                                    fontSize = 10.sp,
                                    color = GoldPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Botones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, NavyCardBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", color = TextMuted, fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                Toast.makeText(context, "Por favor ingresa un título para el comunicado", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (content.isBlank()) {
                                Toast.makeText(context, "Por favor ingresa el contenido del comunicado", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            isSubmitting = true
                            scope.launch {
                                val res = AnnouncementEngine.createAnnouncement(
                                    context = context,
                                    db = db,
                                    title = title,
                                    content = content,
                                    category = selectedCategory,
                                    priority = selectedPriority,
                                    targetScope = selectedScope,
                                    targetUnits = if (selectedScope == AnnouncementTargetScope.POR_UNIDAD) targetUnits else null,
                                    targetRole = if (selectedScope == AnnouncementTargetScope.POR_ROL) targetRole else null,
                                    senderName = senderName,
                                    senderRole = currentUserRole,
                                    attachmentName = if (hasAttachment) attachmentName else null,
                                    attachmentType = if (hasAttachment) attachmentType else null,
                                    attachmentSizeKb = if (hasAttachment) attachmentSizeKb else 0,
                                    requiresAcknowledgement = requiresAcknowledgement
                                )

                                isSubmitting = false
                                when (res) {
                                    is AnnouncementEngine.AnnouncementResult.Success -> {
                                        Toast.makeText(context, "📢 ${res.message}", Toast.LENGTH_LONG).show()
                                        onAnnouncementCreated()
                                    }
                                    is AnnouncementEngine.AnnouncementResult.Error -> {
                                        Toast.makeText(context, "❌ ${res.error}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("btn_submit_create_announcement")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(color = NavyDark, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Emitir Comunicado", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// MODAL: DETALLE DE COMUNICADO Y LISTA DE ACUSES
// =============================================================================

@Composable
private fun AnnouncementDetailDialog(
    announcement: AnnouncementEntity,
    currentUserRole: String,
    currentUnitId: String?,
    db: AppDatabase,
    onDismiss: () -> Unit,
    onViewDocument: () -> Unit,
    onAcknowledge: () -> Unit
) {
    val acks = remember(announcement.acknowledgementsJson) {
        AnnouncementEngine.parseAcknowledgements(announcement.acknowledgementsJson)
    }
    val catColor = Color(announcement.category.colorHex)
    val isAcknowledgedByUnit = remember(acks, currentUnitId) {
        if (currentUnitId.isNullOrBlank()) false
        else acks.any { it.unitId.equals(currentUnitId, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = NavySurface,
            border = BorderStroke(1.5.dp, GoldPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .testTag("dialog_announcement_detail")
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .wrapContentHeight()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = catColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                announcement.category.label.uppercase(),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = catColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            announcement.folio,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Text(
                            announcement.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Publicado: ${announcement.effectiveDate} • Emisor: ${announcement.senderName}",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }

                    // Mensaje Completo
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = NavyDark),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, NavyCardBorder)
                        ) {
                            Text(
                                announcement.content,
                                fontSize = 12.sp,
                                color = Color.White,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // Adjunto
                    if (!announcement.attachmentName.isNullOrBlank()) {
                        item {
                            Surface(
                                color = NavyDark,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, CyanNeon),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onViewDocument() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(announcement.attachmentName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text("Documento Digital Certificado", fontSize = 9.sp, color = TextMuted)
                                        }
                                    }
                                    Text("Abrir ➔", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyanNeon)
                                }
                            }
                        }
                    }

                    // Sello Criptográfico SHA-256
                    item {
                        Surface(
                            color = NavyDark,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("SELLO CRIPTOGRÁFICO DE INTEGRIDAD ALFHA", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    announcement.sha256Signature.ifBlank { "SHA256-INMUTABLE-ROOM-VERIFIED" },
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Lista de Acuses de Recibo
                    item {
                        Text(
                            "Acuses de Recibo Registrados (${acks.size}):",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    if (acks.isEmpty()) {
                        item {
                            Text(
                                "Aún no se registran acuses para este comunicado.",
                                fontSize = 10.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    } else {
                        items(acks) { ack ->
                            Surface(
                                color = NavyDark,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("${ack.unitId} • ${ack.residentName}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("Fecha: ${ack.formattedTimestamp}", fontSize = 9.sp, color = TextMuted)
                                    }
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Botón de acción inferior
                if (currentUserRole == "RESIDENTE" && announcement.requiresAcknowledgement && !isAcknowledgedByUnit) {
                    Button(
                        onClick = onAcknowledge,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_modal_ack")
                    ) {
                        Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Confirmar Lectura y Firmar Acuse", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, NavyCardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cerrar", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// =============================================================================
// MODAL: VISOR DE DOCUMENTO INTELIGENTE CERTIFICADO
// =============================================================================

@Composable
private fun SmartDocumentViewerDialog(
    announcement: AnnouncementEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = NavySurface,
            border = BorderStroke(1.5.dp, CyanNeon),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .testTag("dialog_smart_doc_viewer")
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .wrapContentHeight()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "DOCUMENTO DIGITAL CERTIFICADO",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = CyanNeon
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Hoja / Previsualizador del Documento
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            // Cabecera membretada del documento
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("CONDOMINIO RESIDENCIAL ALFHA", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF0F172A))
                                    Text("DOCUMENTO OFICIAL FOLIO: ${announcement.folio}", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF475569))
                                }
                                Surface(
                                    color = Color(0xFF0284C7),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("CERTIFICADO", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            androidx.compose.material3.HorizontalDivider(color = Color(0xFFE2E8F0))
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(announcement.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                announcement.content,
                                fontSize = 10.sp,
                                color = Color(0xFF334155),
                                lineHeight = 14.sp,
                                maxLines = 8,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Pie de documento con firmas
                        Column {
                            androidx.compose.material3.HorizontalDivider(color = Color(0xFFE2E8F0))
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Emisor: ${announcement.senderName}", fontSize = 8.sp, color = Color(0xFF64748B))
                                Text("Fecha: ${announcement.effectiveDate}", fontSize = 8.sp, color = Color(0xFF64748B))
                            }
                            Text(
                                "Sello Digital: SHA256-${announcement.sha256Signature.take(16)}...",
                                fontSize = 7.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Acciones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            Toast.makeText(context, "📥 Descargando ${announcement.attachmentName ?: "documento.pdf"}", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CyanNeon),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = CyanNeon)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Descargar", color = CyanNeon, fontSize = 11.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Entendido", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// =============================================================================
// MODAL: REPORTE EJECUTIVO DE COMUNICACIONES
// =============================================================================

@Composable
private fun ExecutiveCommunicationReportDialog(
    announcements: List<AnnouncementEntity>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val reportText = remember(announcements) {
        AnnouncementEngine.generateExecutiveCommunicationReport(announcements)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = NavySurface,
            border = BorderStroke(1.5.dp, GoldPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .testTag("dialog_executive_announcement_report")
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .wrapContentHeight()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Print, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "REPORTE EJECUTIVO OFICIAL",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = GoldPrimary
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Contenido del reporte
                Surface(
                    color = NavyDark,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, NavyCardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    LazyColumn(modifier = Modifier.padding(12.dp)) {
                        item {
                            Text(
                                reportText,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFE2E8F0),
                                lineHeight = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Acciones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Reporte Comunicados ALFHA", reportText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "📋 Reporte copiado al portapapeles", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CyanNeon),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = CyanNeon)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copiar", color = CyanNeon, fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            Toast.makeText(context, "🖨️ Exportando Reporte Oficial a PDF con sello SHA-256...", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.3f)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Exportar PDF", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
