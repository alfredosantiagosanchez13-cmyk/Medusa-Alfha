package com.example.ui.components

import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.example.data.firebase.FirebaseConfigHelper
import com.example.data.firebase.FirestoreTenantManager
import com.example.ui.theme.*
import com.example.utils.AmenityReminderManager
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Componente de Calendario Interactivo en Jetpack Compose para visualizar y reservar áreas comunes
 * con consultas forzosamente aisladas y filtradas por 'condominiumId' en Firebase Firestore y Room SQLite.
 */
@Composable
fun AmenityCalendarView(
    db: AppDatabase,
    modifier: Modifier = Modifier,
    initialCondominiumId: String = "PRADOS_1",
    filterUnitId: String? = null,
    onShowQrPass: (AmenityBooking) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by AlfhaSecurityContext.currentUser.collectAsState()

    var activeCondoId by remember { mutableStateOf(initialCondominiumId.uppercase().trim()) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>("TODAS") }
    var selectedAmenityFilter by remember { mutableStateOf<AmenityCatalogItem?>(null) }

    // Fecha actualmente seleccionada en el calendario
    var currentDisplayMonthCalendar by remember {
        mutableStateOf(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) })
    }
    var selectedDateCalendar by remember { mutableStateOf(Calendar.getInstance()) }

    // Observar reservas en Room DB filtradas por condominio
    val roomBookings by db.amenityBookingDao().getBookingsByCondominium(activeCondoId).collectAsState(initial = emptyList())

    // Estado de sincronización Firestore
    var isFirestoreSynced by remember { mutableStateOf(false) }
    var firestoreSyncError by remember { mutableStateOf<String?>(null) }

    // Diálogos de acción
    var bookingSlotToConfirm by remember { mutableStateOf<Pair<AmenityCatalogItem, TimeSlotAvailability>?>(null) }
    var bookingToCancel by remember { mutableStateOf<AmenityBooking?>(null) }
    var showNewBookingCustomDialog by remember { mutableStateOf(false) }

    // 1. Escuchar Firestore con aislamiento multi-inquilino obligatorio
    DisposableEffect(activeCondoId) {
        val firestore = FirebaseConfigHelper.getFirestore()
        var registration: ListenerRegistration? = null

        if (firestore != null) {
            try {
                registration = FirestoreTenantManager.listenToAmenityBookings(
                    firestore = firestore,
                    condominiumId = activeCondoId,
                    onUpdate = { firestoreList ->
                        isFirestoreSynced = true
                        firestoreSyncError = null
                        // Actualizar en Room local para disponibilidad offline
                        scope.launch(Dispatchers.IO) {
                            firestoreList.forEach { fbBooking ->
                                db.amenityBookingDao().insertBooking(fbBooking)
                            }
                        }
                    },
                    onError = { err ->
                        firestoreSyncError = err.message
                        isFirestoreSynced = false
                    }
                )
            } catch (e: Exception) {
                firestoreSyncError = e.message
            }
        }

        onDispose {
            registration?.remove()
        }
    }

    // Filtrar reservas por unidad si aplica
    val tenantBookings = remember(roomBookings, activeCondoId, filterUnitId) {
        val filtered = roomBookings.filter {
            it.condominiumId.equals(activeCondoId, ignoreCase = true) || it.condominiumId == "GENERAL"
        }
        if (!filterUnitId.isNullOrBlank()) {
            filtered.filter { it.unitId.equals(filterUnitId, ignoreCase = true) }
        } else {
            filtered
        }
    }

    // Formato de fecha del día seleccionado (yyyy-MM-dd)
    val selectedDateString = remember(selectedDateCalendar) {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(selectedDateCalendar.time)
    }

    // Reservas correspondientes al día seleccionado
    val dayBookings = remember(tenantBookings, selectedDateString, selectedAmenityFilter) {
        tenantBookings.filter { b ->
            val matchesDate = b.bookingDate == selectedDateString || run {
                val bCal = Calendar.getInstance().apply { timeInMillis = b.bookingTimeMillis }
                bCal.get(Calendar.YEAR) == selectedDateCalendar.get(Calendar.YEAR) &&
                        bCal.get(Calendar.MONTH) == selectedDateCalendar.get(Calendar.MONTH) &&
                        bCal.get(Calendar.DAY_OF_MONTH) == selectedDateCalendar.get(Calendar.DAY_OF_MONTH)
            }
            val matchesAmenity = selectedAmenityFilter == null || b.amenityName == selectedAmenityFilter?.name
            matchesDate && matchesAmenity && b.status != "CANCELADA"
        }
    }

    // Slots calculados para el día seleccionado
    var calculatedSlots by remember { mutableStateOf<List<TimeSlotAvailability>>(emptyList()) }
    var isLoadingSlots by remember { mutableStateOf(false) }

    val activeAmenityForSlots = selectedAmenityFilter ?: AmenityBookingEngine.CATALOG.first()

    LaunchedEffect(selectedDateCalendar, activeAmenityForSlots, tenantBookings) {
        isLoadingSlots = true
        calculatedSlots = AmenityBookingEngine.getDailyAvailability(
            db = db,
            amenityName = activeAmenityForSlots.name,
            targetDateCalendar = selectedDateCalendar,
            condominiumId = activeCondoId
        )
        isLoadingSlots = false
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("amenity_calendar_component_root"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- 1. BANNER MULTI-INQUILINO CON SELECCIÓN DE CONDOMINIO ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = NavyCard,
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f))
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
                                .background(GoldPrimary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "CALENDARIO DE ÁREAS COMUNES",
                                color = GoldPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Disponibilidad y Reservas",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Badge de Aislamiento Tenant
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isFirestoreSynced) SuccessGreen.copy(alpha = 0.2f) else NavyDark,
                        border = BorderStroke(1.dp, if (isFirestoreSynced) SuccessGreen else CyanNeon.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(if (isFirestoreSynced) SuccessGreen else CyanNeon, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isFirestoreSynced) "☁️ FIRESTORE AISLADO" else "💾 ROOM LOCAL",
                                color = if (isFirestoreSynced) SuccessGreen else CyanNeon,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Selector rápido de Condominio (Multi-Tenant Isolation)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "PRADOS_1" to "Los Prados 1",
                        "PRADOS_2" to "Los Prados 2",
                        "PRADOS_3" to "Los Prados 3",
                        "PARAISO" to "Condominio Paraíso"
                    ).forEach { (id, label) ->
                        val isSelected = activeCondoId == id
                        Surface(
                            onClick = { activeCondoId = id },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) GoldPrimary else NavyDark,
                            border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) NavyDark else TextMuted,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp),
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // --- 2. SELECTOR DE ÁREA COMÚN / FILTRO ---
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            item {
                val isSelected = selectedAmenityFilter == null
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedAmenityFilter = null },
                    label = { Text("🌟 Todas las Áreas", fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GoldPrimary,
                        selectedLabelColor = NavyDark,
                        containerColor = NavyCard,
                        labelColor = TextMuted
                    )
                )
            }
            items(AmenityBookingEngine.CATALOG) { item ->
                val isSelected = selectedAmenityFilter?.id == item.id
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedAmenityFilter = if (isSelected) null else item },
                    leadingIcon = {
                        Icon(getAmenityIcon(item.name), contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text(item.name, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanNeon,
                        selectedLabelColor = NavyDark,
                        selectedLeadingIconColor = NavyDark,
                        containerColor = NavyCard,
                        labelColor = TextMuted,
                        iconColor = TextMuted
                    )
                )
            }
        }

        // --- 3. CALENDARIO MENSUAL INTERACTIVO (GRID) ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = NavyDark,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header del Mes y Navegación
                val monthFormatter = SimpleDateFormat("MMMM yyyy", Locale("es", "ES"))
                val currentMonthLabel = remember(currentDisplayMonthCalendar) {
                    monthFormatter.format(currentDisplayMonthCalendar.time).replaceFirstChar { it.uppercase() }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val next = (currentDisplayMonthCalendar.clone() as Calendar).apply {
                                    add(Calendar.MONTH, -1)
                                }
                                currentDisplayMonthCalendar = next
                            }
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Mes Anterior", tint = CyanNeon)
                        }

                        Text(
                            text = currentMonthLabel,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )

                        IconButton(
                            onClick = {
                                val next = (currentDisplayMonthCalendar.clone() as Calendar).apply {
                                    add(Calendar.MONTH, 1)
                                }
                                currentDisplayMonthCalendar = next
                            }
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Mes Siguiente", tint = CyanNeon)
                        }
                    }

                    // Botón "Hoy"
                    OutlinedButton(
                        onClick = {
                            val today = Calendar.getInstance()
                            selectedDateCalendar = today
                            currentDisplayMonthCalendar = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Today, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hoy", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Días de la semana (LUN, MAR, MIÉ, JUE, VIE, SÁB, DOM)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM").forEach { dayName ->
                        Text(
                            text = dayName,
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Grid de Días del Mes
                MonthDaysGrid(
                    displayMonth = currentDisplayMonthCalendar,
                    selectedDate = selectedDateCalendar,
                    allTenantBookings = tenantBookings,
                    selectedAmenityFilter = selectedAmenityFilter,
                    onSelectDate = { newDate ->
                        selectedDateCalendar = newDate
                    }
                )
            }
        }

        // --- 4. PANEL DE DETALLE Y RESERVA DEL DÍA SELECCIONADO ---
        val selectedDayFormatted = remember(selectedDateCalendar) {
            SimpleDateFormat("EEEE d 'de' MMMM, yyyy", Locale("es", "ES")).format(selectedDateCalendar.time)
                .replaceFirstChar { it.uppercase() }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = NavyCard,
            border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Título del Día y Acción de Nueva Reserva
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "AGENDA DEL DÍA",
                            color = CyanNeon,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = selectedDayFormatted,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = { showNewBookingCustomDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("new_booking_custom_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reservar Área", fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Subsección 1: Reservas confirmadas para este día
                Text(
                    text = "RESERVAS CONFIRMADAS (${dayBookings.size}):",
                    color = GoldPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (dayBookings.isEmpty()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = NavyDark
                    ) {
                        Text(
                            text = "No hay reservas registradas para este día en $activeCondoId. ¡Horarios 100% libres!",
                            color = TextMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        dayBookings.forEach { booking ->
                            ConfirmedBookingCard(
                                booking = booking,
                                onShowQr = { onShowQrPass(booking) },
                                onCancel = { bookingToCancel = booking }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Subsección 2: Horarios y Reserva Rápida en 1 Toque para el Área Activa
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SLOTS DISPONIBLES (${activeAmenityForSlots.name}):",
                        color = CyanNeon,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NavyDark
                    ) {
                        Text(
                            text = "Aforo ${activeAmenityForSlots.capacity}p",
                            color = TextMuted,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoadingSlots) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = CyanNeon, modifier = Modifier.size(24.dp))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        calculatedSlots.forEach { slot ->
                            SlotRowItem(
                                slot = slot,
                                onBook = {
                                    bookingSlotToConfirm = Pair(activeAmenityForSlots, slot)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // --- MODAL: CONFIRMACIÓN EN 1 TOQUE (CERO RECAPTURA) ---
    bookingSlotToConfirm?.let { (amenity, slot) ->
        val defaultResident = currentUser.name.ifBlank { "Residente" }
        val defaultUnit = filterUnitId ?: currentUser.unitOrDepartment
        var inputNotes by remember { mutableStateOf("") }
        var isProcessing by remember { mutableStateOf(false) }

        Dialog(onDismissRequest = { if (!isProcessing) bookingSlotToConfirm = null }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = NavySurface,
                border = BorderStroke(2.dp, CyanNeon),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EventAvailable, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("RESERVAR EN 1 TOQUE", fontWeight = FontWeight.Black, fontSize = 13.sp, color = CyanNeon)
                                Text(amenity.name, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        IconButton(onClick = { bookingSlotToConfirm = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                        }
                    }

                    // Ficha de Resumen con Aislamiento Tenant
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NavyDark,
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            InfoRow(label = "Condominio:", value = activeCondoId, valueColor = GoldPrimary)
                            InfoRow(label = "Fecha:", value = selectedDateString, valueColor = Color.White)
                            InfoRow(label = "Horario:", value = slot.slotLabel, valueColor = CyanNeon)
                            InfoRow(label = "Residente:", value = defaultResident, valueColor = Color.White)
                            InfoRow(label = "Unidad / Casa:", value = defaultUnit, valueColor = CyanNeon)
                            InfoRow(label = "Aforo:", value = "Máximo ${amenity.capacity} personas", valueColor = Color.White)
                        }
                    }

                    OutlinedTextField(
                        value = inputNotes,
                        onValueChange = { inputNotes = it },
                        label = { Text("Notas / Motivo", color = TextMuted, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

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
                                    operatorName = defaultResident,
                                    condominiumId = activeCondoId
                                )

                                when (result) {
                                    is BookingExecutionResult.Success -> {
                                        Toast.makeText(
                                            context,
                                            "✅ Reserva ${result.booking.folio} confirmada para $activeCondoId.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        bookingSlotToConfirm = null
                                    }
                                    is BookingExecutionResult.Conflict -> {
                                        Toast.makeText(
                                            context,
                                            "⚠️ Conflicto: ${result.message}",
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
                            .height(48.dp),
                        enabled = !isProcessing
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isProcessing) "Sincronizando..." else "Confirmar Reserva",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    // --- MODAL: RESERVA PERSONALIZADA ---
    if (showNewBookingCustomDialog) {
        var selectedAmenityForCustom by remember { mutableStateOf(AmenityBookingEngine.CATALOG.first()) }
        var selectedTimeSlotIndex by remember { mutableStateOf(0) }
        var customResidentName by remember { mutableStateOf(currentUser.name) }
        var customUnit by remember { mutableStateOf(filterUnitId ?: currentUser.unitOrDepartment) }
        var customNotes by remember { mutableStateOf("") }
        var isSubmitting by remember { mutableStateOf(false) }

        val timeSlots = listOf(
            "08:00 - 10:00" to (8 to 10),
            "10:00 - 12:00" to (10 to 12),
            "12:00 - 14:00" to (12 to 14),
            "14:00 - 16:00" to (14 to 16),
            "16:00 - 18:00" to (16 to 18),
            "18:00 - 20:00" to (18 to 20),
            "20:00 - 22:00" to (20 to 22)
        )

        Dialog(onDismissRequest = { if (!isSubmitting) showNewBookingCustomDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = NavySurface,
                border = BorderStroke(2.dp, GoldPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AddCircle, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("NUEVA RESERVA DE ÁREA", fontWeight = FontWeight.Black, fontSize = 13.sp, color = GoldPrimary)
                        }
                        IconButton(onClick = { showNewBookingCustomDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted)
                        }
                    }

                    Text("Selecciona Área Común:", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(AmenityBookingEngine.CATALOG) { amn ->
                            val isSel = selectedAmenityForCustom.id == amn.id
                            Surface(
                                onClick = { selectedAmenityForCustom = amn },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) GoldPrimary else NavyDark,
                                border = BorderStroke(1.dp, if (isSel) GoldPrimary else Color.White.copy(alpha = 0.1f))
                            ) {
                                Text(
                                    text = amn.name,
                                    color = if (isSel) NavyDark else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Text("Selecciona Horario (${selectedDateString}):", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        itemsIndexed(timeSlots) { index, slotData ->
                            val (label, _) = slotData
                            val isSel = selectedTimeSlotIndex == index
                            Surface(
                                onClick = { selectedTimeSlotIndex = index },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) CyanNeon else NavyDark,
                                border = BorderStroke(1.dp, if (isSel) CyanNeon else Color.White.copy(alpha = 0.1f))
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) NavyDark else Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = customResidentName,
                        onValueChange = { customResidentName = it },
                        label = { Text("Nombre del Residente", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = customUnit,
                        onValueChange = { customUnit = it },
                        label = { Text("Casa / Unidad", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = customNotes,
                        onValueChange = { customNotes = it },
                        label = { Text("Notas / Motivo", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            isSubmitting = true
                            scope.launch {
                                val (startH, endH) = timeSlots[selectedTimeSlotIndex].second
                                val calStart = (selectedDateCalendar.clone() as Calendar).apply {
                                    set(Calendar.HOUR_OF_DAY, startH)
                                    set(Calendar.MINUTE, 0)
                                }
                                val startMillis = calStart.timeInMillis
                                val durationMins = (endH - startH) * 60

                                val result = AmenityBookingEngine.executeOneTapBooking(
                                    context = context,
                                    db = db,
                                    amenityName = selectedAmenityForCustom.name,
                                    residentName = customResidentName,
                                    unitId = customUnit,
                                    startMillis = startMillis,
                                    durationMinutes = durationMins,
                                    notes = customNotes,
                                    operatorName = customResidentName,
                                    condominiumId = activeCondoId
                                )

                                when (result) {
                                    is BookingExecutionResult.Success -> {
                                        Toast.makeText(context, "✅ Reserva ${result.booking.folio} creada.", Toast.LENGTH_LONG).show()
                                        showNewBookingCustomDialog = false
                                    }
                                    is BookingExecutionResult.Conflict -> {
                                        Toast.makeText(context, "⚠️ Conflicto: ${result.message}", Toast.LENGTH_LONG).show()
                                    }
                                    is BookingExecutionResult.Error -> {
                                        Toast.makeText(context, "Error: ${result.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                isSubmitting = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = !isSubmitting
                    ) {
                        Text(
                            text = if (isSubmitting) "Guardando..." else "Confirmar Reserva para $activeCondoId",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    // --- MODAL: CANCELACIÓN ---
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
                        text = "¿Deseas cancelar la reserva de ${booking.amenityName} en $activeCondoId para ${booking.timeSlot}?",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "El horario quedará liberado inmediatamente en Firestore y Room DB.",
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
                                cancellationReason = cancelReason,
                                condominiumId = activeCondoId
                            )
                            if (cancelled) {
                                Toast.makeText(context, "Reserva cancelada y horario liberado en $activeCondoId.", Toast.LENGTH_SHORT).show()
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
}

// -------------------------------------------------------------
// COMPONENTES AUXILIARES DEL CALENDARIO
// -------------------------------------------------------------

@Composable
private fun MonthDaysGrid(
    displayMonth: Calendar,
    selectedDate: Calendar,
    allTenantBookings: List<AmenityBooking>,
    selectedAmenityFilter: AmenityCatalogItem?,
    onSelectDate: (Calendar) -> Unit
) {
    val monthCal = displayMonth.clone() as Calendar
    monthCal.set(Calendar.DAY_OF_MONTH, 1)

    val daysInMonth = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
    // En Java Calendar, Domingo es 1, Lunes es 2 ... Sábado es 7. Convertir a Lunes = 0 .. Domingo = 6
    val firstDayOfWeekIndex = (monthCal.get(Calendar.DAY_OF_WEEK) + 5) % 7

    val today = Calendar.getInstance()
    val isCurrentMonthAndYear = today.get(Calendar.YEAR) == displayMonth.get(Calendar.YEAR) &&
            today.get(Calendar.MONTH) == displayMonth.get(Calendar.MONTH)

    // Agrupar reservas por día del mes
    val bookingsCountByDay = remember(allTenantBookings, displayMonth, selectedAmenityFilter) {
        val map = mutableMapOf<Int, Int>()
        allTenantBookings.filter { b ->
            b.status != "CANCELADA" && (selectedAmenityFilter == null || b.amenityName == selectedAmenityFilter.name)
        }.forEach { b ->
            val bCal = Calendar.getInstance().apply { timeInMillis = b.bookingTimeMillis }
            if (bCal.get(Calendar.YEAR) == displayMonth.get(Calendar.YEAR) &&
                bCal.get(Calendar.MONTH) == displayMonth.get(Calendar.MONTH)
            ) {
                val day = bCal.get(Calendar.DAY_OF_MONTH)
                map[day] = (map[day] ?: 0) + 1
            }
        }
        map
    }

    val totalCells = ((firstDayOfWeekIndex + daysInMonth + 6) / 7) * 7

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until (totalCells / 7)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - firstDayOfWeekIndex + 1

                    if (dayNumber in 1..daysInMonth) {
                        val isToday = isCurrentMonthAndYear && today.get(Calendar.DAY_OF_MONTH) == dayNumber
                        val isSelected = selectedDate.get(Calendar.YEAR) == displayMonth.get(Calendar.YEAR) &&
                                selectedDate.get(Calendar.MONTH) == displayMonth.get(Calendar.MONTH) &&
                                selectedDate.get(Calendar.DAY_OF_MONTH) == dayNumber

                        val bookingCount = bookingsCountByDay[dayNumber] ?: 0

                        DayCell(
                            dayNumber = dayNumber,
                            isToday = isToday,
                            isSelected = isSelected,
                            bookingCount = bookingCount,
                            onClick = {
                                val newCal = (displayMonth.clone() as Calendar).apply {
                                    set(Calendar.DAY_OF_MONTH, dayNumber)
                                }
                                onSelectDate(newCal)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        // Celda vacía para rellenar inicio o fin de mes
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    dayNumber: Int,
    isToday: Boolean,
    isSelected: Boolean,
    bookingCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isSelected -> CyanNeon
        isToday -> GoldPrimary.copy(alpha = 0.25f)
        else -> Color.Transparent
    }

    val textColor = when {
        isSelected -> NavyDark
        isToday -> GoldPrimary
        else -> Color.White
    }

    Box(
        modifier = modifier
            .height(44.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .border(
                width = if (isToday && !isSelected) 1.dp else 0.dp,
                color = if (isToday && !isSelected) GoldPrimary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$dayNumber",
                color = textColor,
                fontSize = 12.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Black else FontWeight.Medium
            )

            // Indicador de reservas en el día
            if (bookingCount > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(
                                if (isSelected) NavyDark else if (bookingCount >= 3) ErrorRed else GoldPrimary,
                                CircleShape
                            )
                    )
                    if (bookingCount > 1) {
                        Text(
                            text = "$bookingCount",
                            fontSize = 8.sp,
                            color = if (isSelected) NavyDark else GoldPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmedBookingCard(
    booking: AmenityBooking,
    onShowQr: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = NavyDark,
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(GoldPrimary.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(getAmenityIcon(booking.amenityName), contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(booking.amenityName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("${booking.residentName} • ${booking.unitId}", color = CyanNeon, fontSize = 10.sp)
                    Text("Horario: ${booking.timeSlot} • Folio: ${booking.folio}", color = TextMuted, fontSize = 9.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onShowQr, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.QrCode, contentDescription = "Ver QR", tint = GoldPrimary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Cancel, contentDescription = "Cancelar", tint = ErrorRed, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun SlotRowItem(
    slot: TimeSlotAvailability,
    onBook: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (slot.isAvailable) NavyDark else NavyDark.copy(alpha = 0.5f),
        border = BorderStroke(
            1.dp,
            if (slot.isAvailable) SuccessGreen.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (slot.isAvailable) Icons.Default.EventAvailable else Icons.Default.Block,
                    contentDescription = null,
                    tint = if (slot.isAvailable) SuccessGreen else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(slot.slotLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (slot.isAvailable) "Disponible" else "Reservado por ${slot.bookedByUnit}",
                        color = if (slot.isAvailable) SuccessGreen else TextMuted,
                        fontSize = 9.sp
                    )
                }
            }

            if (slot.isAvailable) {
                Button(
                    onClick = onBook,
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = NavyDark),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("1-Toque", fontSize = 10.sp, fontWeight = FontWeight.Black)
                }
            } else {
                Surface(
                    color = ErrorRed.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "OCUPADO",
                        color = ErrorRed,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextMuted, fontSize = 11.sp)
        Text(value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private fun getAmenityIcon(amenityName: String): ImageVector {
    val lower = amenityName.lowercase()
    return when {
        lower.contains("pádel") || lower.contains("padel") || lower.contains("tenis") -> Icons.Default.SportsTennis
        lower.contains("alberca") || lower.contains("piscina") || lower.contains("pool") -> Icons.Default.Pool
        lower.contains("quincho") || lower.contains("asador") || lower.contains("grill") -> Icons.Default.Group
        lower.contains("gimnasio") || lower.contains("gym") || lower.contains("fitness") -> Icons.Default.FitnessCenter
        lower.contains("coworking") || lower.contains("trabajo") || lower.contains("business") -> Icons.Default.Work
        lower.contains("golf") || lower.contains("putting") -> Icons.Default.SportsGolf
        lower.contains("eventos") || lower.contains("salón") || lower.contains("salon") -> Icons.Default.Group
        else -> Icons.Default.EventAvailable
    }
}
