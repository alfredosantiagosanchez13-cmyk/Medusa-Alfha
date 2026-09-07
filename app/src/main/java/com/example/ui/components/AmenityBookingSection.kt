package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Room
import androidx.compose.material.icons.filled.SportsGolf
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.booking.AmenityBooking
import com.example.data.booking.AppDatabase
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmenityBookingSection(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    val bookings by db.amenityBookingDao().getAllBookings().collectAsState(initial = emptyList())

    // New Booking Form State
    var showNewBookingForm by remember { mutableStateOf(false) }
    var showFullCalendarModal by remember { mutableStateOf(false) }
    var selectedAmenity by remember { mutableStateOf("Quincho & BBQ Principal") }
    var residentNameInput by remember { mutableStateOf("") }
    var unitInput by remember { mutableStateOf("Casa 104") }
    var minutesFromNow by remember { mutableStateOf("25") }
    var expandedAmenityDropdown by remember { mutableStateOf(false) }
    var bookingMode by remember { mutableStateOf(0) } // 0: Calendario Visual de Horarios, 1: Minutos Relativos

    var selectedDateCalendar by remember {
        mutableStateOf(Calendar.getInstance().apply {
            if (get(Calendar.HOUR_OF_DAY) >= 20) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        })
    }

    val defaultTimeSlots = remember {
        listOf(
            Pair("08:00 - 10:00", 8),
            Pair("10:00 - 12:00", 10),
            Pair("12:00 - 14:00", 12),
            Pair("14:00 - 16:00", 14),
            Pair("16:00 - 18:00", 16),
            Pair("18:00 - 20:00", 18),
            Pair("20:00 - 22:00", 20)
        )
    }
    var selectedSlotStartHour by remember { mutableStateOf(10) }

    val amenityOptions = listOf(
        "Quincho & BBQ Principal",
        "Gimnasio Residencial",
        "Piscina & Solarium",
        "Cancha de Pádel",
        "Sala de Co-Work"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("amenity_booking_room_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldPrimary.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
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
                        Icon(
                            imageVector = Icons.Default.EventAvailable,
                            contentDescription = "Amenity Bookings",
                            tint = GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = "SISTEMA DE NOTIFICACIONES • ROOM DB",
                            color = GoldPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Reservas de Amenidades (Recordatorio 15m)",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Header Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CyanNeon.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "ROOM ${bookings.size} ACTIVAS",
                        color = CyanNeon,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Button(
                    onClick = { showFullCalendarModal = true },
                    colors = ButtonDefaults.buttonColors(containerColor = NavyDark, contentColor = GoldPrimary),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.7f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp), tint = GoldPrimary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ver Calendario Completo", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Info Alert Box
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = NavyDark,
                border = androidx.compose.foundation.BorderStroke(1.dp, CyanNeon.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = CyanNeon,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "El sistema programa automáticamente una notificación del sistema 15 minutos antes del inicio de cada reserva guardada en Room DB.",
                        color = TextMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bookings List
            if (bookings.isEmpty()) {
                Text(
                    text = "No hay reservas pendientes registradas.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    bookings.take(4).forEach { booking ->
                        BookingItemRow(
                            booking = booking,
                            onTriggerTestReminder = {
                                AmenityReminderManager.send15MinReminderNotification(
                                    context = context,
                                    bookingId = booking.id,
                                    amenityName = booking.amenityName,
                                    residentName = booking.residentName,
                                    unitId = booking.unitId,
                                    bookingTimeMillis = booking.bookingTimeMillis
                                )
                                Toast.makeText(
                                    context,
                                    "🔔 Notificación de 15 min enviada para ${booking.amenityName}",
                                    Toast.LENGTH_SHORT
                                ).show()

                                coroutineScope.launch(Dispatchers.IO) {
                                    db.amenityBookingDao().markReminderSent(booking.id)
                                }
                            },
                            onTrigger1HourReminder = {
                                AmenityReminderManager.sendOneHourReminderNotification(
                                    context = context,
                                    bookingId = booking.id,
                                    amenityName = booking.amenityName,
                                    residentName = booking.residentName,
                                    unitId = booking.unitId,
                                    bookingTimeMillis = booking.bookingTimeMillis
                                )
                                Toast.makeText(
                                    context,
                                    "⏰ Notificación de 1 hora antes enviada para ${booking.amenityName}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onDeleteBooking = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    AmenityReminderManager.cancelReminder(context, booking.id)
                                    db.amenityBookingDao().deleteBooking(booking.id)
                                }
                                Toast.makeText(context, "Reserva eliminada de Room DB", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // New Booking Expansion Toggle
            if (!showNewBookingForm) {
                Button(
                    onClick = { showNewBookingForm = true },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_new_amenity_booking_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Crear Nueva Reserva con Recordatorio 15m", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                // Form to Add Booking to Room DB
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = NavySurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "NUEVA RESERVA DE AMENIDAD (ROOM PERSISTENCE)",
                            color = GoldPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Amenity Dropdown
                        ExposedDropdownMenuBox(
                            expanded = expandedAmenityDropdown,
                            onExpandedChange = { expandedAmenityDropdown = !expandedAmenityDropdown }
                        ) {
                            OutlinedTextField(
                                value = selectedAmenity,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Amenidad", color = TextMuted, fontSize = 10.sp) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAmenityDropdown) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldPrimary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = expandedAmenityDropdown,
                                onDismissRequest = { expandedAmenityDropdown = false }
                            ) {
                                amenityOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            selectedAmenity = option
                                            expandedAmenityDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = residentNameInput,
                                onValueChange = { residentNameInput = it },
                                placeholder = { Text("Residente (Ej: Juan Perez)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldPrimary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            OutlinedTextField(
                                value = unitInput,
                                onValueChange = { unitInput = it },
                                placeholder = { Text("Unidad", fontSize = 10.sp) },
                                modifier = Modifier.width(100.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldPrimary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Selector de Modo: Calendario Visual vs Minutos
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(NavyDark, RoundedCornerShape(8.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                onClick = { bookingMode = 0 },
                                shape = RoundedCornerShape(6.dp),
                                color = if (bookingMode == 0) GoldPrimary else Color.Transparent,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = if (bookingMode == 0) NavyDark else TextMuted,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "Calendario & Horarios",
                                        fontSize = 10.sp,
                                        fontWeight = if (bookingMode == 0) FontWeight.Bold else FontWeight.Normal,
                                        color = if (bookingMode == 0) NavyDark else Color.White
                                    )
                                }
                            }

                            Surface(
                                onClick = { bookingMode = 1 },
                                shape = RoundedCornerShape(6.dp),
                                color = if (bookingMode == 1) GoldPrimary else Color.Transparent,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = if (bookingMode == 1) NavyDark else TextMuted,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "En Minutos (+)",
                                        fontSize = 10.sp,
                                        fontWeight = if (bookingMode == 1) FontWeight.Bold else FontWeight.Normal,
                                        color = if (bookingMode == 1) NavyDark else Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (bookingMode == 0) {
                            // --- VISTA DE CALENDARIO VISUAL INTERACTIVO ---
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(NavyDark, RoundedCornerShape(10.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val dateFormatter = remember { SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES")) }

                                // Encabezado de Fecha
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            val prev = (selectedDateCalendar.clone() as Calendar).apply {
                                                add(Calendar.DAY_OF_YEAR, -1)
                                            }
                                            selectedDateCalendar = prev
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ChevronLeft, contentDescription = "Día anterior", tint = Color.White)
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "FECHA SELECCIONADA",
                                            color = TextMuted,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = dateFormatter.format(selectedDateCalendar.time).replaceFirstChar { it.uppercase() },
                                            color = GoldPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val next = (selectedDateCalendar.clone() as Calendar).apply {
                                                add(Calendar.DAY_OF_YEAR, 1)
                                            }
                                            selectedDateCalendar = next
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ChevronRight, contentDescription = "Día siguiente", tint = Color.White)
                                    }
                                }

                                // Selector rápido de días (Hoy, Mañana, +2 días, +3 días)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(
                                        0 to "Hoy",
                                        1 to "Mañana",
                                        2 to "+2 d",
                                        3 to "+3 d",
                                        4 to "+4 d"
                                    ).forEach { (offset, label) ->
                                        val calForOffset = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
                                        val isCurrent = selectedDateCalendar.get(Calendar.DAY_OF_YEAR) == calForOffset.get(Calendar.DAY_OF_YEAR) &&
                                                selectedDateCalendar.get(Calendar.YEAR) == calForOffset.get(Calendar.YEAR)

                                        Surface(
                                            onClick = { selectedDateCalendar = calForOffset },
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isCurrent) CyanNeon.copy(alpha = 0.25f) else NavySurface,
                                            border = BorderStroke(1.dp, if (isCurrent) CyanNeon else Color.White.copy(alpha = 0.1f)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(vertical = 4.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontSize = 9.sp,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isCurrent) CyanNeon else Color.White
                                                )
                                                Text(
                                                    text = "${calForOffset.get(Calendar.DAY_OF_MONTH)}",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = if (isCurrent) CyanNeon else TextMuted
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                // Título de Horarios
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "HORARIOS DISPONIBLES ($selectedAmenity):",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Bloques de 2 horas",
                                        color = TextMuted,
                                        fontSize = 9.sp
                                    )
                                }

                                // Grilla de Horarios
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    defaultTimeSlots.chunked(2).forEach { rowSlots ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            rowSlots.forEach { (slotLabel, startHour) ->
                                                // Comprobar conflicto con reservas existentes
                                                val isConflict = bookings.any { b ->
                                                    if (!b.amenityName.equals(selectedAmenity, ignoreCase = true)) {
                                                        false
                                                    } else {
                                                        val bCal = Calendar.getInstance().apply { timeInMillis = b.bookingTimeMillis }
                                                        bCal.get(Calendar.YEAR) == selectedDateCalendar.get(Calendar.YEAR) &&
                                                                bCal.get(Calendar.DAY_OF_YEAR) == selectedDateCalendar.get(Calendar.DAY_OF_YEAR) &&
                                                                bCal.get(Calendar.HOUR_OF_DAY) == startHour
                                                    }
                                                }

                                                val isSelected = selectedSlotStartHour == startHour

                                                Surface(
                                                    onClick = {
                                                        if (!isConflict) {
                                                            selectedSlotStartHour = startHour
                                                        } else {
                                                            Toast.makeText(context, "Horario ocupado por otra reserva", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = when {
                                                        isSelected && !isConflict -> GoldPrimary.copy(alpha = 0.2f)
                                                        isConflict -> ErrorRed.copy(alpha = 0.1f)
                                                        else -> NavySurface
                                                    },
                                                    border = BorderStroke(
                                                        1.dp,
                                                        when {
                                                            isSelected && !isConflict -> GoldPrimary
                                                            isConflict -> ErrorRed.copy(alpha = 0.4f)
                                                            else -> Color.White.copy(alpha = 0.1f)
                                                        }
                                                    ),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = slotLabel,
                                                            fontSize = 10.sp,
                                                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                                            color = if (isSelected) GoldPrimary else if (isConflict) TextMuted else Color.White
                                                        )

                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = if (isConflict) ErrorRed.copy(alpha = 0.2f) else SuccessGreen.copy(alpha = 0.2f)
                                                        ) {
                                                            Text(
                                                                text = if (isConflict) "OCUPADO" else "LIBRE",
                                                                color = if (isConflict) ErrorRed else SuccessGreen,
                                                                fontSize = 8.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // Si la fila tiene un solo elemento, completar con Spacer
                                            if (rowSlots.size == 1) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Modo Manual en Minutos
                            OutlinedTextField(
                                value = minutesFromNow,
                                onValueChange = { minutesFromNow = it.filter { char -> char.isDigit() } },
                                label = { Text("Inicio en (minutos desde ahora)", color = TextMuted, fontSize = 10.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldPrimary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showNewBookingForm = false },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancelar", color = TextMuted, fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    val name = residentNameInput.ifBlank { "Residente Garita" }
                                    val unit = unitInput.ifBlank { "Casa 104" }

                                    val bookingTime = if (bookingMode == 0) {
                                        val targetCal = (selectedDateCalendar.clone() as Calendar).apply {
                                            set(Calendar.HOUR_OF_DAY, selectedSlotStartHour)
                                            set(Calendar.MINUTE, 0)
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }
                                        targetCal.timeInMillis
                                    } else {
                                        val offsetMinutes = minutesFromNow.toIntOrNull() ?: 25
                                        System.currentTimeMillis() + (offsetMinutes * 60 * 1000L)
                                    }

                                    val newBooking = AmenityBooking(
                                        amenityName = selectedAmenity,
                                        residentName = name,
                                        unitId = unit,
                                        bookingTimeMillis = bookingTime,
                                        reminderSent = false
                                    )

                                    coroutineScope.launch(Dispatchers.IO) {
                                        val generatedId = db.amenityBookingDao().insertBooking(newBooking)
                                        val savedWithId = newBooking.copy(id = generatedId)
                                        AmenityReminderManager.scheduleOneHourReminder(context, savedWithId)
                                        AmenityReminderManager.schedule15MinReminder(context, savedWithId)
                                    }

                                    val timeLabel = SimpleDateFormat("dd/MM HH:mm 'hrs'", Locale.getDefault()).format(Date(bookingTime))
                                    Toast.makeText(
                                        context,
                                        "✅ Reserva guardada ($timeLabel). Recordatorios programados (1h y 15m antes).",
                                        Toast.LENGTH_LONG
                                    ).show()

                                    showNewBookingForm = false
                                    residentNameInput = ""
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = NavyDark),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_booking_room_btn")
                            ) {
                                Text("Guardar Reserva", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal de Calendario Completo Interactivo
    if (showFullCalendarModal) {
        Dialog(
            onDismissRequest = { showFullCalendarModal = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.92f)
                    .padding(vertical = 12.dp)
                    .testTag("full_amenity_calendar_dialog"),
                shape = RoundedCornerShape(20.dp),
                color = NavySurface,
                border = BorderStroke(1.5.dp, GoldPrimary)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
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
                                    .size(36.dp)
                                    .background(GoldPrimary.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "CALENDARIO DE ÁREAS COMUNES",
                                    color = GoldPrimary,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "Disponibilidad visual interactiva y reservas",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        IconButton(onClick = { showFullCalendarModal = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    AmenityCalendarView(
                        db = db,
                        initialCondominiumId = "PRADOS_1",
                        onShowQrPass = { booking ->
                            Toast.makeText(context, "Reserva: ${booking.amenityName} (${booking.folio})", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookingItemRow(
    booking: AmenityBooking,
    onTriggerTestReminder: () -> Unit,
    onTrigger1HourReminder: () -> Unit,
    onDeleteBooking: () -> Unit
) {
    val dateFormatted = remember(booking.bookingTimeMillis) {
        SimpleDateFormat("HH:mm 'h' (dd/MM)", Locale.getDefault()).format(Date(booking.bookingTimeMillis))
    }

    val icon = when {
        booking.amenityName.contains("Quincho", ignoreCase = true) -> Icons.Default.SportsGolf
        booking.amenityName.contains("Gimnasio", ignoreCase = true) -> Icons.Default.FitnessCenter
        booking.amenityName.contains("Piscina", ignoreCase = true) -> Icons.Default.Pool
        booking.amenityName.contains("Pádel", ignoreCase = true) -> Icons.Default.SportsTennis
        else -> Icons.Default.EventAvailable
    }

    val reminder15TimeFormatted = remember(booking.bookingTimeMillis) {
        val reminderMillis = booking.bookingTimeMillis - (15 * 60 * 1000)
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(reminderMillis))
    }

    val reminder1hTimeFormatted = remember(booking.bookingTimeMillis) {
        val reminderMillis = booking.bookingTimeMillis - (60 * 60 * 1000)
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(reminderMillis))
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = NavyDark,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (booking.reminderSent) SuccessGreen.copy(alpha = 0.5f) else GoldPrimary.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                if (booking.reminderSent) SuccessGreen.copy(alpha = 0.2f) else GoldPrimary.copy(alpha = 0.2f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (booking.reminderSent) SuccessGreen else GoldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = booking.amenityName,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${booking.residentName} (${booking.unitId}) • Inicio: $dateFormatted",
                            color = TextMuted,
                            fontSize = 10.sp
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (booking.reminderSent) Icons.Default.Notifications else Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = if (booking.reminderSent) SuccessGreen else CyanNeon,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Recordatorios: 1h antes ($reminder1hTimeFormatted) y 15m ($reminder15TimeFormatted)",
                                color = if (booking.reminderSent) SuccessGreen else CyanNeon,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onTrigger1HourReminder,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon.copy(alpha = 0.2f), contentColor = CyanNeon),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_1h_reminder_btn_${booking.id}")
                ) {
                    Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Alerta 1h", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onTriggerTestReminder,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary.copy(alpha = 0.2f), contentColor = GoldPrimary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("test_reminder_btn_${booking.id}")
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Alerta 15m", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
