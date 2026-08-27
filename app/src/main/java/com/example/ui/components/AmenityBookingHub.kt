package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SportsGolf
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.auth.AlfhaSecurityContext
import com.example.data.booking.AmenityBooking
import com.example.data.booking.AmenityBookingEngine
import com.example.data.booking.AmenityCatalogItem
import com.example.data.booking.AppDatabase
import com.example.data.booking.BookingExecutionResult
import com.example.data.booking.TimeSlotAvailability
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.utils.AmenityReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class AmenityHubTab(val label: String, val icon: ImageVector) {
    AVAILABILITY("Disponibilidad", Icons.Default.EventAvailable),
    ACTIVE("Próximas", Icons.Default.Today),
    HISTORY("Historial", Icons.Default.History)
}

@Composable
fun AmenityBookingHub(
    db: AppDatabase,
    modifier: Modifier = Modifier,
    filterUnitId: String? = null,
    canManage: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by AlfhaSecurityContext.currentUser.collectAsState()

    val allBookings by db.amenityBookingDao().getAllBookings().collectAsState(initial = emptyList())
    val filteredBookings = remember(allBookings, filterUnitId) {
        if (!filterUnitId.isNullOrBlank()) {
            allBookings.filter { it.unitId.equals(filterUnitId, ignoreCase = true) }
        } else {
            allBookings
        }
    }

    var selectedTab by remember { mutableStateOf(AmenityHubTab.AVAILABILITY) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedAmenityForDetail by remember { mutableStateOf<AmenityCatalogItem?>(null) }
    var bookingInOneTapSlot by remember { mutableStateOf<Pair<AmenityCatalogItem, TimeSlotAvailability>?>(null) }
    var bookingToCancel by remember { mutableStateOf<AmenityBooking?>(null) }
    var showQrDialogForBooking by remember { mutableStateOf<AmenityBooking?>(null) }

    // Seed initial demo data if database is empty to ensure immediate live usability
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val count = db.amenityBookingDao().getBookingsCount()
            if (count == 0) {
                val now = System.currentTimeMillis()
                val catalog = AmenityBookingEngine.CATALOG
                
                // Seed 2 active bookings for today
                AmenityBookingEngine.executeOneTapBooking(
                    context = context,
                    db = db,
                    amenityName = catalog[0].name, // Quincho
                    residentName = "Carlos Mendoza",
                    unitId = "Casa 208",
                    startMillis = now + (3 * 3600 * 1000L),
                    durationMinutes = 120,
                    notes = "Almuerzo familiar",
                    operatorName = "Carlos Mendoza"
                )

                AmenityBookingEngine.executeOneTapBooking(
                    context = context,
                    db = db,
                    amenityName = catalog[1].name, // Pádel 1
                    residentName = "Valeria Rojas",
                    unitId = "Depto 302",
                    startMillis = now + (5 * 3600 * 1000L),
                    durationMinutes = 120,
                    notes = "Partido de dobles",
                    operatorName = "Valeria Rojas"
                )
            }
        }
    }

    val activeBookings = remember(filteredBookings) {
        val now = System.currentTimeMillis()
        filteredBookings.filter { it.status != "CANCELADA" && (it.bookingTimeMillis + (it.durationMinutes * 60000)) >= now }
    }

    val totalTimeSavedMinutes = remember(filteredBookings) {
        filteredBookings.filter { it.status != "CANCELADA" }.sumOf { it.timeSavedMinutes }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("amenity_booking_hub_container"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. HERO KPI & TIEMPO DEVUELTO HEADER ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = NavyCard,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldPrimary.copy(alpha = 0.7f))
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
                                .size(40.dp)
                                .background(GoldPrimary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventAvailable,
                                contentDescription = "Reservas de Amenidades",
                                tint = GoldPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "FASE 11 • RESERVAS DE AMENIDADES",
                                color = GoldPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = if (filterUnitId != null) "Amenidades para $filterUnitId" else "Gestión de Áreas Comunes",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SuccessGreen.copy(alpha = 0.2f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen)
                    ) {
                        Text(
                            text = "TIEMPO DEVUELTO: +${totalTimeSavedMinutes} min",
                            color = SuccessGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // KPI Mini Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiMiniCard(
                        title = "ACTIVAS HOY",
                        value = "${activeBookings.size}",
                        accentColor = CyanNeon,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMiniCard(
                        title = "TOTAL ROOM",
                        value = "${filteredBookings.size}",
                        accentColor = GoldPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    KpiMiniCard(
                        title = "DISPONIBILIDAD",
                        value = "100% TIEMPO REAL",
                        accentColor = SuccessGreen,
                        modifier = Modifier.weight(1.3f)
                    )
                }
            }
        }

        // --- 2. TAB SELECTOR ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = NavyDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AmenityHubTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    val badgeCount = when (tab) {
                        AmenityHubTab.AVAILABILITY -> "${AmenityBookingEngine.CATALOG.size}"
                        AmenityHubTab.ACTIVE -> "${activeBookings.size}"
                        AmenityHubTab.HISTORY -> "${filteredBookings.size}"
                    }
                    Surface(
                        onClick = { selectedTab = tab },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) GoldPrimary else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                                tint = if (isSelected) NavyDark else TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${tab.label} ($badgeCount)",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                color = if (isSelected) NavyDark else Color.White
                            )
                        }
                    }
                }
            }
        }

        // --- 3. DYNAMIC CONTENT BY TAB ---
        when (selectedTab) {
            AmenityHubTab.AVAILABILITY -> {
                AvailabilityAndCatalogView(
                    db = db,
                    onSelectAmenity = { amenity -> selectedAmenityForDetail = amenity },
                    onBookSlotInOneTap = { amenity, slot ->
                        bookingInOneTapSlot = Pair(amenity, slot)
                    }
                )
            }

            AmenityHubTab.ACTIVE -> {
                ActiveBookingsListView(
                    bookings = activeBookings,
                    onCancelRequest = { booking -> bookingToCancel = booking },
                    onShowQr = { booking -> showQrDialogForBooking = booking },
                    onTriggerReminder = { booking ->
                        AmenityReminderManager.send15MinReminderNotification(
                            context = context,
                            bookingId = booking.id,
                            amenityName = booking.amenityName,
                            residentName = booking.residentName,
                            unitId = booking.unitId,
                            bookingTimeMillis = booking.bookingTimeMillis
                        )
                        Toast.makeText(context, "🔔 Notificación -15m enviada para ${booking.amenityName}", Toast.LENGTH_SHORT).show()
                        scope.launch(Dispatchers.IO) {
                            db.amenityBookingDao().markReminderSent(booking.id)
                        }
                    }
                )
            }

            AmenityHubTab.HISTORY -> {
                HistoryBookingsListView(
                    bookings = filteredBookings,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onShowQr = { booking -> showQrDialogForBooking = booking }
                )
            }
        }
    }

    // --- MODAL: CONFIRMAR RESERVA EN 1 TOQUE (CERO RECAPTURA) ---
    bookingInOneTapSlot?.let { (amenity, slot) ->
        val defaultResident = currentUser.name.ifBlank { "Residente Autorizado" }
        val defaultUnit = filterUnitId ?: currentUser.unitOrDepartment.ifBlank { "Casa 104" }
        var inputNotes by remember { mutableStateOf("") }
        var isProcessing by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { if (!isProcessing) bookingInOneTapSlot = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = NavySurface,
                border = androidx.compose.foundation.BorderStroke(2.dp, CyanNeon),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .testTag("one_tap_booking_dialog")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EventAvailable, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("RESERVA EN 1 TOQUE", fontWeight = FontWeight.Black, fontSize = 14.sp, color = CyanNeon)
                                Text(amenity.name, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        IconButton(onClick = { bookingInOneTapSlot = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                        }
                    }

                    // Auto-Filled Card (Cero Recaptura)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NavyDark,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            InfoRow(label = "Residente:", value = defaultResident, valueColor = Color.White)
                            InfoRow(label = "Unidad / Casa:", value = defaultUnit, valueColor = CyanNeon)
                            InfoRow(label = "Horario Asignado:", value = slot.slotLabel, valueColor = GoldPrimary)
                            InfoRow(label = "Aforo Máximo:", value = "${amenity.capacity} personas", valueColor = Color.White)
                            InfoRow(label = "Tiempo Devuelto:", value = "+15 min sin trámites", valueColor = SuccessGreen)
                        }
                    }

                    // Optional Notes Field
                    OutlinedTextField(
                        value = inputNotes,
                        onValueChange = { inputNotes = it },
                        label = { Text("Notas / Motivo (Opcional)", color = TextMuted, fontSize = 11.sp) },
                        placeholder = { Text("Ej: Reunión familiar, cumpleaños...", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    // Rules Summary
                    Surface(
                        color = GoldPrimary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Confirmación instantánea con bloqueo automático de colisiones.",
                                color = GoldPrimary,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Confirm Action Button
                    Button(
                        onClick = {
                            isProcessing = true
                            scope.launch {
                                val result = AmenityBookingEngine.executeOneTapBooking(
                                    context = context,
                                    db = db,
                                    amenityName = amenity.name,
                                    residentName = defaultResident,
                                    unitId = defaultUnit,
                                    startMillis = slot.startMillis,
                                    durationMinutes = ((slot.endMillis - slot.startMillis) / 60000).toInt(),
                                    notes = inputNotes,
                                    operatorName = defaultResident
                                )

                                when (result) {
                                    is BookingExecutionResult.Success -> {
                                        Toast.makeText(
                                            context,
                                            "✅ Reserva ${result.booking.folio} confirmada con éxito.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        bookingInOneTapSlot = null
                                        selectedTab = AmenityHubTab.ACTIVE
                                    }
                                    is BookingExecutionResult.Conflict -> {
                                        Toast.makeText(
                                            context,
                                            "⚠️ Bloqueo de Conflicto: ${result.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    is BookingExecutionResult.Error -> {
                                        Toast.makeText(context, "Error: ${result.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                isProcessing = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("confirm_one_tap_booking_btn"),
                        enabled = !isProcessing
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isProcessing) "Validando y Bloqueando..." else "Confirmar Reserva Inmediata",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    // --- MODAL: CANCELACIÓN DE RESERVA ---
    bookingToCancel?.let { booking ->
        var cancelReason by remember { mutableStateOf("Cambio de planes") }
        AlertDialog(
            onDismissRequest = { bookingToCancel = null },
            title = {
                Text(
                    text = "Cancelar Reserva [${booking.folio}]",
                    fontWeight = FontWeight.Bold,
                    color = ErrorRed
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "¿Deseas cancelar la reserva de ${booking.amenityName} para el horario ${booking.timeSlot}?",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "El horario quedará liberado inmediatamente en tiempo real para todos los residentes.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    OutlinedTextField(
                        value = cancelReason,
                        onValueChange = { cancelReason = it },
                        label = { Text("Motivo de cancelación", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val cancelled = AmenityBookingEngine.cancelBooking(
                                context = context,
                                db = db,
                                bookingId = booking.id,
                                cancelledBy = currentUser.name.ifBlank { "Administración" },
                                cancellationReason = cancelReason
                            )
                            if (cancelled) {
                                Toast.makeText(context, "Reserva cancelada y horario liberado.", Toast.LENGTH_SHORT).show()
                            }
                            bookingToCancel = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                ) {
                    Text("Confirmar Cancelación", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookingToCancel = null }) {
                    Text("Volver", color = TextMuted)
                }
            },
            containerColor = NavySurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // --- MODAL: VISOR DE FOLIO Y ACCESO QR ---
    showQrDialogForBooking?.let { booking ->
        Dialog(onDismissRequest = { showQrDialogForBooking = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = NavySurface,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "PASE DE ACCESO A AMENIDAD",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = GoldPrimary
                    )

                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(180.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.QrCode, contentDescription = null, tint = NavyDark, modifier = Modifier.size(120.dp))
                                Text(booking.folio, fontWeight = FontWeight.Black, fontSize = 11.sp, color = NavyDark)
                            }
                        }
                    }

                    Text(booking.amenityName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${booking.residentName} • ${booking.unitId}", color = CyanNeon, fontSize = 12.sp)
                    Text("Horario: ${booking.timeSlot} (${booking.bookingDate})", color = TextMuted, fontSize = 11.sp)

                    Button(
                        onClick = { showQrDialogForBooking = null },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cerrar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// SUB-VIEWS
// -------------------------------------------------------------

@Composable
private fun AvailabilityAndCatalogView(
    db: AppDatabase,
    onSelectAmenity: (AmenityCatalogItem) -> Unit,
    onBookSlotInOneTap: (AmenityCatalogItem, TimeSlotAvailability) -> Unit
) {
    val catalog = AmenityBookingEngine.CATALOG
    var selectedAmenity by remember { mutableStateOf(catalog.first()) }
    var selectedDateOffset by remember { mutableStateOf(0) } // 0 = Hoy, 1 = Mañana, 2 = Pasado mañana

    var availabilitySlots by remember { mutableStateOf<List<TimeSlotAvailability>>(emptyList()) }
    var isLoadingSlots by remember { mutableStateOf(true) }

    // Load slots when amenity or date changes
    LaunchedEffect(selectedAmenity, selectedDateOffset) {
        isLoadingSlots = true
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, selectedDateOffset)
        availabilitySlots = AmenityBookingEngine.getDailyAvailability(
            db = db,
            amenityName = selectedAmenity.name,
            targetDateCalendar = cal
        )
        isLoadingSlots = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Date Selector Bar
        Text(
            text = "1. SELECCIONA EL DÍA:",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = GoldPrimary,
            letterSpacing = 0.5.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                0 to "Hoy",
                1 to "Mañana",
                2 to "Pasado Mañana"
            ).forEach { (offset, label) ->
                val isSel = selectedDateOffset == offset
                Surface(
                    onClick = { selectedDateOffset = offset },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSel) CyanNeon.copy(alpha = 0.2f) else NavyDark,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) CyanNeon else Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = label,
                        color = if (isSel) CyanNeon else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Amenity Catalog Horizontal Selector
        Text(
            text = "2. SELECCIONA EL ÁREA COMÚN:",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = GoldPrimary,
            letterSpacing = 0.5.sp
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 4.dp)
        ) {
            items(catalog) { amenity ->
                val isSelected = selectedAmenity.id == amenity.id
                Surface(
                    onClick = { selectedAmenity = amenity },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) GoldPrimary.copy(alpha = 0.25f) else NavyCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.width(160.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = getAmenityIcon(amenity.name),
                                contentDescription = null,
                                tint = if (isSelected) GoldPrimary else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = NavyDark
                            ) {
                                Text(
                                    text = "Max ${amenity.capacity}p",
                                    fontSize = 9.sp,
                                    color = TextMuted,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = amenity.name,
                            color = if (isSelected) Color.White else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = "${amenity.openTime} - ${amenity.closeTime}",
                            color = TextMuted,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }

        // Amenity Selected Summary Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = NavyDark,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedAmenity.name.uppercase(Locale.getDefault()),
                        color = CyanNeon,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Aforo: ${selectedAmenity.capacity} personas",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = selectedAmenity.description,
                    color = TextMuted,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        // Live Real-Time Slot Availability List
        Text(
            text = "3. HORARIOS DISPONIBLES EN TIEMPO REAL (1-TOQUE):",
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            color = GoldPrimary,
            letterSpacing = 0.5.sp
        )

        if (isLoadingSlots) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                shape = RoundedCornerShape(12.dp),
                color = NavyCard
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Calculando disponibilidad en Room DB...", color = TextMuted, fontSize = 11.sp)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                availabilitySlots.forEach { slot ->
                    TimeSlotRowCard(
                        slot = slot,
                        onBook = { onBookSlotInOneTap(selectedAmenity, slot) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeSlotRowCard(
    slot: TimeSlotAvailability,
    onBook: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = if (slot.isAvailable) NavyCard else NavyDark.copy(alpha = 0.7f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (slot.isAvailable) SuccessGreen.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (slot.isAvailable) Icons.Default.EventAvailable else Icons.Default.Block,
                    contentDescription = null,
                    tint = if (slot.isAvailable) SuccessGreen else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = slot.slotLabel,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (slot.isAvailable) "Disponible para reserva inmediata" else "Ocupado por ${slot.bookedByUnit} (${slot.bookedByResident})",
                        color = if (slot.isAvailable) SuccessGreen else TextMuted,
                        fontSize = 10.sp
                    )
                }
            }

            if (slot.isAvailable) {
                Button(
                    onClick = onBook,
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = NavyDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("book_slot_btn_${slot.slotLabel}")
                ) {
                    Text("1-Toque", fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            } else {
                Surface(
                    color = ErrorRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "RESERVADO",
                        color = ErrorRed,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveBookingsListView(
    bookings: List<AmenityBooking>,
    onCancelRequest: (AmenityBooking) -> Unit,
    onShowQr: (AmenityBooking) -> Unit,
    onTriggerReminder: (AmenityBooking) -> Unit
) {
    if (bookings.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = NavyCard
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("No hay reservas activas en este momento.", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Explora la pestaña 'Disponibilidad' para reservar en 1 toque.", color = TextMuted, fontSize = 11.sp)
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            bookings.forEach { booking ->
                ActiveBookingCard(
                    booking = booking,
                    onCancel = { onCancelRequest(booking) },
                    onShowQr = { onShowQr(booking) },
                    onReminder = { onTriggerReminder(booking) }
                )
            }
        }
    }
}

@Composable
private fun ActiveBookingCard(
    booking: AmenityBooking,
    onCancel: () -> Unit,
    onShowQr: () -> Unit,
    onReminder: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = NavyCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Top Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(CyanNeon.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getAmenityIcon(booking.amenityName),
                            contentDescription = null,
                            tint = CyanNeon,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(booking.amenityName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("Folio: ${booking.folio}", color = GoldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SuccessGreen.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen)
                ) {
                    Text(
                        text = "CONFIRMADA",
                        color = SuccessGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Titular / Unidad:", color = TextMuted, fontSize = 10.sp)
                    Text("${booking.residentName} (${booking.unitId})", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Horario Asignado:", color = TextMuted, fontSize = 10.sp)
                    Text("${booking.timeSlot} • ${booking.bookingDate}", color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (booking.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Nota: ${booking.notes}", color = TextMuted, fontSize = 10.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShowQr,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(14.dp), tint = GoldPrimary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ver Pase", fontSize = 10.sp, color = GoldPrimary)
                }

                OutlinedButton(
                    onClick = onReminder,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(14.dp), tint = CyanNeon)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("-15m Test", fontSize = 10.sp, color = CyanNeon)
                }

                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.2f), contentColor = ErrorRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1.1f)
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancelar", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HistoryBookingsListView(
    bookings: List<AmenityBooking>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onShowQr: (AmenityBooking) -> Unit
) {
    val filtered = remember(bookings, searchQuery) {
        if (searchQuery.isBlank()) bookings
        else bookings.filter {
            it.amenityName.contains(searchQuery, ignoreCase = true) ||
            it.folio.contains(searchQuery, ignoreCase = true) ||
            it.residentName.contains(searchQuery, ignoreCase = true) ||
            it.unitId.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Buscar por folio, amenidad, residente o unidad...", fontSize = 11.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        if (filtered.isEmpty()) {
            Text("No se encontraron registros de reservas.", color = TextMuted, fontSize = 12.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                filtered.forEach { booking ->
                    HistoryItemCard(booking = booking, onShowQr = { onShowQr(booking) })
                }
            }
        }
    }
}

@Composable
private fun HistoryItemCard(
    booking: AmenityBooking,
    onShowQr: () -> Unit
) {
    val isCancelled = booking.status == "CANCELADA"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = NavyDark,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCancelled) ErrorRed.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(booking.amenityName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("[${booking.folio}]", color = GoldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Text("${booking.residentName} (${booking.unitId}) • ${booking.bookingDate} ${booking.timeSlot}", color = TextMuted, fontSize = 10.sp)
                if (isCancelled) {
                    Text("Motivo: ${booking.cancellationReason ?: "Cancelada"}", color = ErrorRed, fontSize = 9.sp)
                }
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isCancelled) ErrorRed.copy(alpha = 0.2f) else SuccessGreen.copy(alpha = 0.2f)
            ) {
                Text(
                    text = booking.status,
                    color = if (isCancelled) ErrorRed else SuccessGreen,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// HELPERS & MINI COMPONENTS
// -------------------------------------------------------------

@Composable
private fun KpiMiniCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = NavyDark,
        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            Text(value, color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMuted, fontSize = 11.sp)
        Text(value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private fun getAmenityIcon(amenityName: String): ImageVector {
    return when {
        amenityName.contains("Quincho", ignoreCase = true) || amenityName.contains("BBQ", ignoreCase = true) -> Icons.Default.SportsGolf
        amenityName.contains("Pádel", ignoreCase = true) || amenityName.contains("Tenis", ignoreCase = true) -> Icons.Default.SportsTennis
        amenityName.contains("Gimnasio", ignoreCase = true) || amenityName.contains("Gym", ignoreCase = true) -> Icons.Default.FitnessCenter
        amenityName.contains("Piscina", ignoreCase = true) || amenityName.contains("Pool", ignoreCase = true) -> Icons.Default.Pool
        amenityName.contains("Cowork", ignoreCase = true) || amenityName.contains("Business", ignoreCase = true) -> Icons.Default.Work
        amenityName.contains("Eventos", ignoreCase = true) || amenityName.contains("Multiuso", ignoreCase = true) -> Icons.Default.Group
        else -> Icons.Default.EventAvailable
    }
}
