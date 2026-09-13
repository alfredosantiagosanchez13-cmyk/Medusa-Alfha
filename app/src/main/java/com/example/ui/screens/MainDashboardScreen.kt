package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.booking.AmenityBooking
import com.example.data.booking.AppDatabase
import com.example.data.booking.CommonAreaBooking
import com.example.data.core.AlphaCoreEngine
import com.example.data.passes.QrPassRoomEntity
import com.example.data.visitor.VisitorCheckIn
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pestaña de filtrado en el Dashboard
 */
enum class DashboardFilterCategory(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    ALL("Resumen General", Icons.Default.FilterList),
    COMMON_AREAS("Áreas Comunes", Icons.Default.Apartment),
    QR_VISITORS("Visitantes QR", Icons.Default.QrCodeScanner)
}

/**
 * Pantalla de Inicio (Dashboard Principal)
 * Muestra el resumen de próximos eventos en áreas comunes y los visitantes
 * recientes registrados mediante códigos QR con lectura reactiva desde Room Database.
 */
@Composable
fun MainDashboardScreen(
    db: AppDatabase,
    onNavigateToTab: (ActiveScreenTab) -> Unit,
    onTriggerScan: () -> Unit,
    onOpenQrGenerator: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val commonAreaDao = remember { db.commonAreaBookingDao() }
    val visitorCheckInDao = remember { db.visitorCheckInDao() }
    val qrPassDao = remember { db.qrPassDao() }

    // Flujos reactivos de Room DB
    val allEvents by commonAreaDao.getAllBookings().collectAsState(initial = emptyList())
    val allCheckIns by visitorCheckInDao.getAllCheckIns().collectAsState(initial = emptyList())
    val allQrPasses by qrPassDao.getAllPassesFlow().collectAsState(initial = emptyList())

    var selectedFilter by remember { mutableStateOf(DashboardFilterCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var showCreateEventModal by remember { mutableStateOf(false) }
    var selectedEventDetail by remember { mutableStateOf<CommonAreaBooking?>(null) }
    var selectedVisitorDetail by remember { mutableStateOf<VisitorCheckIn?>(null) }

    // Auto-seed inicial inteligente si la base de datos de eventos está vacía
    LaunchedEffect(Unit) {
        if (commonAreaDao.getBookingsCount() == 0) {
            seedSampleCommonAreaEvents(commonAreaDao)
        }
    }

    // Filtrado de eventos próximos
    val upcomingEvents = remember(allEvents, searchQuery) {
        val now = System.currentTimeMillis() - (4 * 3600 * 1000L) // Incluye eventos en curso hoy
        allEvents.filter { event ->
            event.status != "CANCELLED" && (event.endTimeMillis >= now || event.bookingDate >= getTodayDateString())
        }.filter { event ->
            if (searchQuery.isBlank()) true else {
                event.facilityName.contains(searchQuery, ignoreCase = true) ||
                event.userName.contains(searchQuery, ignoreCase = true) ||
                event.userUnit.contains(searchQuery, ignoreCase = true)
            }
        }.sortedBy { it.startTimeMillis }
    }

    // Filtrado de visitantes QR recientes
    val recentVisitors = remember(allCheckIns, searchQuery) {
        allCheckIns.filter { checkIn ->
            if (searchQuery.isBlank()) true else {
                checkIn.visitorName.contains(searchQuery, ignoreCase = true) ||
                checkIn.passCode.contains(searchQuery, ignoreCase = true) ||
                checkIn.destinationHouse.contains(searchQuery, ignoreCase = true) ||
                (checkIn.vehiclePlate ?: "").contains(searchQuery, ignoreCase = true)
            }
        }.sortedByDescending { it.timestampMillis }
    }

    // KPIs en tiempo real
    val activeVisitorsInside = remember(allCheckIns) {
        allCheckIns.count { it.status == "CHECKED_IN" || it.status == "VERIFICADO" }
    }
    val totalEventsCount = remember(upcomingEvents) { upcomingEvents.size }
    val totalQrScansToday = remember(allCheckIns) {
        val todayStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        allCheckIns.count { it.formattedTime.contains(todayStr) || it.status == "CHECKED_IN" }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("main_dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // =====================================================================
        // 1. CABECERA EJECUTIVA / BIENVENIDA AL FRACCIONAMIENTO
        // =====================================================================
        item {
            ExecutiveHeroDashboardHeader(
                onTriggerScan = onTriggerScan,
                onOpenCreateEvent = { showCreateEventModal = true },
                onOpenQrGenerator = onOpenQrGenerator
            )
        }

        // =====================================================================
        // 2. MÉTRICAS CLAVE (KPIs) EN TIEMPO REAL
        // =====================================================================
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardKpiCard(
                    title = "Eventos Áreas",
                    value = "$totalEventsCount",
                    subtitle = "Próximas reservas",
                    icon = Icons.Default.Event,
                    accentColor = GoldPrimary,
                    modifier = Modifier.weight(1f)
                )

                DashboardKpiCard(
                    title = "Visitantes QR",
                    value = "$totalQrScansToday",
                    subtitle = "Registros hoy",
                    icon = Icons.Default.QrCodeScanner,
                    accentColor = CyanNeon,
                    modifier = Modifier.weight(1f)
                )

                DashboardKpiCard(
                    title = "En Sitio",
                    value = "$activeVisitorsInside",
                    subtitle = "Dentro del complejo",
                    icon = Icons.Default.Security,
                    accentColor = SuccessGreen,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // =====================================================================
        // 3. BARRA DE FILTROS RÁPIDOS Y BÚSQUEDA
        // =====================================================================
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Selector de categoría
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(DashboardFilterCategory.values()) { category ->
                        val isSelected = selectedFilter == category
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) GoldPrimary else NavyCard,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier
                                .clickable { selectedFilter = category }
                                .testTag("filter_tab_${category.name}")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = category.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) NavyDark else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = category.label,
                                    color = if (isSelected) NavyDark else Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Campo de búsqueda en vivo
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Buscar por área, visitante, folio QR, casa o placas...",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Buscar",
                            tint = GoldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Limpiar",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NavySurface,
                        unfocusedContainerColor = NavySurface,
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_search_input")
                )
            }
        }

        // =====================================================================
        // 4. SECCIÓN: PRÓXIMOS EVENTOS EN ÁREAS COMUNES
        // =====================================================================
        if (selectedFilter == DashboardFilterCategory.ALL || selectedFilter == DashboardFilterCategory.COMMON_AREAS) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(GoldPrimary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Próximos Eventos en Áreas Comunes",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GoldPrimary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "${upcomingEvents.size}",
                                color = GoldPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showCreateEventModal = true },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("btn_new_event_header")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Agendar Evento", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (upcomingEvents.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.Apartment,
                        title = "Sin eventos programados",
                        message = "No hay reservas próximas en áreas comunes que coincidan con la búsqueda.",
                        actionLabel = "Crear Reserva de Área",
                        onAction = { showCreateEventModal = true }
                    )
                }
            } else {
                items(upcomingEvents) { event ->
                    CommonAreaEventCard(
                        booking = event,
                        onClick = { selectedEventDetail = event }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        // =====================================================================
        // 5. SECCIÓN: VISITANTES RECIENTES CON CÓDIGOS QR
        // =====================================================================
        if (selectedFilter == DashboardFilterCategory.ALL || selectedFilter == DashboardFilterCategory.QR_VISITORS) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(CyanNeon, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Visitantes Recientes con Código QR",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyanNeon.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "${recentVisitors.size}",
                                color = CyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onTriggerScan,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("btn_scan_qr_header")
                    ) {
                        Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Escanear QR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (recentVisitors.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Default.QrCodeScanner,
                        title = "Sin registros recientes de QR",
                        message = "No se han detectado ingresos con código QR que coincidan con los filtros.",
                        actionLabel = "Abrir Escáner de Acceso",
                        onAction = onTriggerScan
                    )
                }
            } else {
                items(recentVisitors) { visitor ->
                    RecentQrVisitorCard(
                        visitor = visitor,
                        onClick = { selectedVisitorDetail = visitor },
                        onCheckOut = {
                            scope.launch {
                                visitorCheckInDao.registerCheckOut(
                                    id = visitor.id,
                                    notes = "Salida confirmada desde Dashboard"
                                )
                                Toast.makeText(
                                    context,
                                    "Salida registrada para ${visitor.visitorName}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }
            }
        }

        // Espacio final para asegurar scroll suave y respetar el dock inferior
        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // =========================================================================
    // MODALES Y DIÁLOGOS DE ACCIÓN RÁPIDA
    // =========================================================================

    // Modal para Agendar Evento en Área Común
    if (showCreateEventModal) {
        CreateCommonAreaEventDialog(
            onDismiss = { showCreateEventModal = false },
            onSave = { newBooking ->
                scope.launch {
                    commonAreaDao.insertBooking(newBooking)
                    // También sincronizar con la tabla de reservas de amenidades
                    db.amenityBookingDao().insertBooking(
                        AmenityBooking(
                            folio = newBooking.folio,
                            condominiumId = newBooking.condominiumId,
                            amenityName = newBooking.facilityName,
                            residentName = newBooking.userName,
                            unitId = newBooking.userUnit,
                            bookingDate = newBooking.bookingDate,
                            timeSlot = newBooking.timeSlot,
                            bookingTimeMillis = newBooking.startTimeMillis,
                            status = newBooking.status,
                            notes = newBooking.notes
                        )
                    )
                    Toast.makeText(
                        context,
                        "✅ Evento agendado en ${newBooking.facilityName} [${newBooking.folio}]",
                        Toast.LENGTH_LONG
                    ).show()
                    showCreateEventModal = false
                }
            }
        )
    }

    // Modal de Detalle de Evento en Área Común
    selectedEventDetail?.let { event ->
        EventDetailDialog(
            event = event,
            onDismiss = { selectedEventDetail = null },
            onCancelEvent = {
                scope.launch {
                    commonAreaDao.cancelBooking(event.id)
                    Toast.makeText(context, "Evento cancelado", Toast.LENGTH_SHORT).show()
                    selectedEventDetail = null
                }
            }
        )
    }

    // Modal de Detalle de Visitante QR
    selectedVisitorDetail?.let { visitor ->
        VisitorQrDetailDialog(
            visitor = visitor,
            onDismiss = { selectedVisitorDetail = null },
            onRegisterCheckOut = {
                scope.launch {
                    visitorCheckInDao.registerCheckOut(visitor.id, notes = "Salida confirmada en garita")
                    Toast.makeText(context, "Salida registrada con éxito", Toast.LENGTH_SHORT).show()
                    selectedVisitorDetail = null
                }
            }
        )
    }
}

// =============================================================================
// COMPONENTES DE UI MODULARES
// =============================================================================

@Composable
private fun ExecutiveHeroDashboardHeader(
    onTriggerScan: () -> Unit,
    onOpenCreateEvent: () -> Unit,
    onOpenQrGenerator: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = NavyCard,
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(GoldPrimary.copy(alpha = 0.5f), CyanNeon.copy(alpha = 0.35f))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = 0.15f))
                            .border(1.dp, GoldPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "MEDUSA ALFHA",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = SuccessGreen.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "EN LÍNEA",
                                    color = SuccessGreen,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = "Los Prados Residencial · Garita Principal",
                            fontSize = 11.sp,
                            color = CyanNeon
                        )
                    }
                }

                // Indicador de fecha
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "MX")).format(Date())
                            .replaceFirstChar { it.uppercase() },
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Turno Activo",
                        fontSize = 10.sp,
                        color = GoldPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botones de acción rápida
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTriggerScan,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("hero_scan_qr_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = NavyDark
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Escanear QR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onOpenCreateEvent,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("hero_reserve_event_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanNeon.copy(alpha = 0.18f),
                        contentColor = CyanNeon
                    ),
                    border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EventAvailable,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("+ Evento", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onOpenQrGenerator,
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .testTag("hero_generate_pass_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NavySurface,
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = GoldPrimary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Crear QR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DashboardKpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = NavySurface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = accentColor
            )

            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Tarjeta de Evento en Área Común
 */
@Composable
private fun CommonAreaEventCard(
    booking: CommonAreaBooking,
    onClick: () -> Unit
) {
    val facilityColor = when {
        booking.facilityName.contains("Club", ignoreCase = true) || booking.facilityName.contains("Salón", ignoreCase = true) -> GoldPrimary
        booking.facilityName.contains("Pádel", ignoreCase = true) || booking.facilityName.contains("Cancha", ignoreCase = true) -> CyanNeon
        booking.facilityName.contains("Asador", ignoreCase = true) || booking.facilityName.contains("BBQ", ignoreCase = true) -> Color(0xFFFF9800)
        booking.facilityName.contains("Alberca", ignoreCase = true) || booking.facilityName.contains("Piscina", ignoreCase = true) -> Color(0xFF00B0FF)
        else -> SuccessGreen
    }

    val isHappeningNow = remember(booking) {
        val now = System.currentTimeMillis()
        now in booking.startTimeMillis..booking.endTimeMillis
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("event_card_${booking.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(
            1.dp,
            if (isHappeningNow) facilityColor else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(facilityColor.copy(alpha = 0.15f))
                            .border(1.dp, facilityColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apartment,
                            contentDescription = null,
                            tint = facilityColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = booking.facilityName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Folio: ${booking.folio.ifBlank { "CAB-${booking.id}" }}",
                            fontSize = 10.sp,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Badge de estado
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        isHappeningNow -> SuccessGreen.copy(alpha = 0.2f)
                        booking.status == "CONFIRMED" -> facilityColor.copy(alpha = 0.18f)
                        else -> NavySurface
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            isHappeningNow -> SuccessGreen
                            booking.status == "CONFIRMED" -> facilityColor
                            else -> Color.White.copy(alpha = 0.2f)
                        }
                    )
                ) {
                    Text(
                        text = if (isHappeningNow) "● EN CURSO" else booking.status,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isHappeningNow) SuccessGreen else facilityColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Horario y fecha
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${booking.bookingDate} · ${booking.timeSlot}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.weight(1f))

                // Invitados
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.White.copy(alpha = 0.06f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${booking.guestCount} invitados",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Anfitrión / Residente
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = CyanNeon,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${booking.userUnit} · ${booking.userName.ifBlank { "Residente Autorizado" }}",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Ver detalle",
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Tarjeta de Visitante Reciente registrado mediante Código QR
 */
@Composable
private fun RecentQrVisitorCard(
    visitor: VisitorCheckIn,
    onClick: () -> Unit,
    onCheckOut: () -> Unit
) {
    val isInside = visitor.status == "CHECKED_IN" || visitor.status == "VERIFICADO"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("qr_visitor_card_${visitor.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(
            1.dp,
            if (isInside) SuccessGreen.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isInside) SuccessGreen.copy(alpha = 0.15f)
                                else Color.White.copy(alpha = 0.08f)
                            )
                            .border(
                                1.dp,
                                if (isInside) SuccessGreen else Color.White.copy(alpha = 0.2f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isInside) SuccessGreen else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = visitor.visitorName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = visitor.passCode,
                                fontSize = 10.sp,
                                color = GoldPrimary,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• ${visitor.passTypeLabel}",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }
                }

                // Estado de ingreso
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isInside) SuccessGreen.copy(alpha = 0.2f) else NavySurface,
                    border = BorderStroke(
                        1.dp,
                        if (isInside) SuccessGreen else Color.White.copy(alpha = 0.2f)
                    )
                ) {
                    Text(
                        text = if (isInside) "● EN SITIO" else "SALIDA",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isInside) SuccessGreen else TextMuted,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Destino, placas y tiempo
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = CyanNeon,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Destino: ${visitor.destinationHouse}",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.SemiBold
                    )
                }

                visitor.vehiclePlate?.let { plate ->
                    if (plate.isNotBlank() && plate != "PEATONAL") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = plate,
                                fontSize = 10.sp,
                                color = GoldPrimary,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ingreso: ${visitor.formattedTime}",
                    fontSize = 10.sp,
                    color = TextMuted
                )

                if (isInside) {
                    Button(
                        onClick = onCheckOut,
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorRed.copy(alpha = 0.15f),
                            contentColor = ErrorRed
                        ),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Registrar Salida", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Modal para agendar nuevo evento en área común
 */
@Composable
private fun CreateCommonAreaEventDialog(
    onDismiss: () -> Unit,
    onSave: (CommonAreaBooking) -> Unit
) {
    var facilityName by remember { mutableStateOf("Casa Club & Salón de Eventos") }
    var residentName by remember { mutableStateOf("Familia Residente") }
    var unitNumber by remember { mutableStateOf("Casa 104") }
    var bookingDate by remember { mutableStateOf(getTodayDateString()) }
    var timeSlot by remember { mutableStateOf("18:00 - 22:00") }
    var guestCountStr by remember { mutableStateOf("20") }
    var specialRequests by remember { mutableStateOf("") }

    val facilities = listOf(
        "Casa Club & Salón de Eventos",
        "Cancha de Pádel #1",
        "Cancha de Pádel #2",
        "Área de Asadores & BBQ Terraza",
        "Alberca Semiolímpica & Solárium",
        "Gimnasio Residencial"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = NavyDark,
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EventAvailable,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Agendar Evento / Reserva",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                Text(
                    text = "Selecciona el área común a reservar:",
                    fontSize = 11.sp,
                    color = TextMuted
                )

                // Chips de selección de instalación
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(facilities) { fac ->
                        val isSelected = fac == facilityName
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) GoldPrimary else NavyCard,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.clickable { facilityName = fac }
                        ) {
                            Text(
                                text = fac,
                                fontSize = 10.sp,
                                color = if (isSelected) NavyDark else Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = residentName,
                    onValueChange = { residentName = it },
                    label = { Text("Nombre del Anfitrión / Residente", fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GoldPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = unitNumber,
                        onValueChange = { unitNumber = it },
                        label = { Text("Vivienda / Casa", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GoldPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = guestCountStr,
                        onValueChange = { guestCountStr = it },
                        label = { Text("Invitados estimados", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GoldPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = bookingDate,
                        onValueChange = { bookingDate = it },
                        label = { Text("Fecha (yyyy-MM-dd)", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GoldPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = timeSlot,
                        onValueChange = { timeSlot = it },
                        label = { Text("Horario (ej. 18:00 - 22:00)", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GoldPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = specialRequests,
                    onValueChange = { specialRequests = it },
                    label = { Text("Notas o solicitudes especiales (opcional)", fontSize = 11.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GoldPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Text("Cancelar", color = Color.White)
                    }

                    Button(
                        onClick = {
                            val folio = "CAB-${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}-${(100..999).random()}"
                            val guests = guestCountStr.toIntOrNull() ?: 10
                            val newBooking = CommonAreaBooking(
                                folio = folio,
                                userId = unitNumber,
                                userName = residentName,
                                userUnit = unitNumber,
                                facilityName = facilityName,
                                bookingDate = bookingDate,
                                timeSlot = timeSlot,
                                startTimeMillis = System.currentTimeMillis() + 3600000L,
                                endTimeMillis = System.currentTimeMillis() + (5 * 3600000L),
                                status = "CONFIRMED",
                                guestCount = guests,
                                specialRequests = specialRequests,
                                notes = "Registrado desde Dashboard Principal"
                            )
                            onSave(newBooking)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            contentColor = NavyDark
                        )
                    ) {
                        Text("Guardar Reserva", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Modal de detalle de un evento en área común
 */
@Composable
private fun EventDetailDialog(
    event: CommonAreaBooking,
    onDismiss: () -> Unit,
    onCancelEvent: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = NavyDark,
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Detalle de Evento",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NavySurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = event.facilityName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary
                        )
                        Text(
                            text = "Folio: ${event.folio}",
                            fontSize = 11.sp,
                            color = TextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "📅 Fecha: ${event.bookingDate} · ⏰ ${event.timeSlot}",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                        Text(
                            text = "🏠 Unidad: ${event.userUnit} (${event.userName})",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                        Text(
                            text = "👥 Invitados registrados: ${event.guestCount}",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                        if (event.specialRequests.isNotBlank()) {
                            Text(
                                text = "📝 Notas: ${event.specialRequests}",
                                fontSize = 11.sp,
                                color = CyanNeon
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancelEvent,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed)
                    ) {
                        Text("Cancelar Evento")
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            contentColor = NavyDark
                        )
                    ) {
                        Text("Aceptar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Modal de detalle de un visitante con código QR
 */
@Composable
private fun VisitorQrDetailDialog(
    visitor: VisitorCheckIn,
    onDismiss: () -> Unit,
    onRegisterCheckOut: () -> Unit
) {
    val isInside = visitor.status == "CHECKED_IN" || visitor.status == "VERIFICADO"

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = NavyDark,
            border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.QrCode, contentDescription = null, tint = GoldPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Pase QR Verificado",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NavySurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = visitor.visitorName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Código QR: ${visitor.passCode}",
                            fontSize = 12.sp,
                            color = GoldPrimary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Tipo: ${visitor.passTypeLabel} · Doc: ${visitor.visitorDocument}",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                        Text(
                            text = "Destino: ${visitor.destinationHouse} (${visitor.hostResidentName})",
                            fontSize = 12.sp,
                            color = Color.White
                        )
                        visitor.vehiclePlate?.let {
                            Text(
                                text = "Vehículo / Placa: $it",
                                fontSize = 12.sp,
                                color = GoldPrimary
                            )
                        }
                        Text(
                            text = "Ingreso registrado: ${visitor.formattedTime}",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                        visitor.formattedCheckOutTime?.let {
                            Text(
                                text = "Salida registrada: $it",
                                fontSize = 11.sp,
                                color = ErrorRed
                            )
                        }
                    }
                }

                if (isInside) {
                    Button(
                        onClick = onRegisterCheckOut,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ErrorRed,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Registrar Salida en Garita", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NavySurface,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Cerrar")
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta de estado vacío
 */
@Composable
private fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = NavyCard,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = GoldPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = message,
                fontSize = 11.sp,
                color = TextMuted,
                textAlign = TextAlign.Center
            )

            Button(
                onClick = onAction,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldPrimary.copy(alpha = 0.15f),
                    contentColor = GoldPrimary
                ),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                modifier = Modifier.height(34.dp)
            ) {
                Text(actionLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Retorna la fecha de hoy en formato yyyy-MM-dd
 */
private fun getTodayDateString(): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}

/**
 * Sembrado inicial de eventos en áreas comunes para que el fraccionamiento
 * cuente con eventos realistas desde el primer momento.
 */
private suspend fun seedSampleCommonAreaEvents(dao: com.example.data.booking.CommonAreaBookingDao) {
    val now = System.currentTimeMillis()
    val today = getTodayDateString()

    val samples = listOf(
        CommonAreaBooking(
            folio = "CAB-20260912-101",
            userId = "RES-104",
            userName = "Familia Arismendi",
            userUnit = "Casa 104",
            facilityName = "Casa Club & Salón de Eventos",
            bookingDate = today,
            timeSlot = "18:00 - 22:00",
            startTimeMillis = now + (2 * 3600 * 1000L),
            endTimeMillis = now + (6 * 3600 * 1000L),
            condominiumId = "Los Prados Residencial",
            status = "CONFIRMED",
            guestCount = 28,
            specialRequests = "Montaje de mesas para celebración familiar de aniversario",
            notes = "Autorización de acceso para 12 vehículos de invitados en estacionamiento común"
        ),
        CommonAreaBooking(
            folio = "CAB-20260912-102",
            userId = "RES-042",
            userName = "Lic. Roberto Valenzuela",
            userUnit = "Casa 42",
            facilityName = "Cancha de Pádel #1",
            bookingDate = today,
            timeSlot = "19:00 - 21:00",
            startTimeMillis = now + 3600 * 1000L,
            endTimeMillis = now + (3 * 3600 * 1000L),
            condominiumId = "Los Prados Residencial",
            status = "CONFIRMED",
            guestCount = 4,
            specialRequests = "Encendido de iluminación nocturna de alta potencia",
            notes = "Torneo amistoso de residentes"
        ),
        CommonAreaBooking(
            folio = "CAB-20260913-103",
            userId = "RES-208",
            userName = "Ing. Carlos Mendoza",
            userUnit = "Casa 208",
            facilityName = "Área de Asadores & BBQ Terraza",
            bookingDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now + 86400000L)),
            timeSlot = "13:00 - 17:00",
            startTimeMillis = now + (20 * 3600 * 1000L),
            endTimeMillis = now + (24 * 3600 * 1000L),
            condominiumId = "Los Prados Residencial",
            status = "CONFIRMED",
            guestCount = 15,
            specialRequests = "Uso de parrilla doble de carbón",
            notes = "Entrega limpia y con parrilla apagada garantizada"
        ),
        CommonAreaBooking(
            folio = "CAB-20260913-104",
            userId = "RES-015",
            userName = "Dra. Marcela Lozano",
            userUnit = "Casa 15",
            facilityName = "Alberca Semiolímpica & Solárium",
            bookingDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now + 86400000L)),
            timeSlot = "10:00 - 13:00",
            startTimeMillis = now + (18 * 3600 * 1000L),
            endTimeMillis = now + (21 * 3600 * 1000L),
            condominiumId = "Los Prados Residencial",
            status = "CONFIRMED",
            guestCount = 8,
            specialRequests = "Área de camastros reservados",
            notes = "Clase de natación y convivencia familiar"
        )
    )

    dao.insertBookings(samples)
}
