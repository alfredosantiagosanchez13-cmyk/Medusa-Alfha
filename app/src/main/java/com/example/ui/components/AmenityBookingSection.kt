package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.booking.AmenityBooking
import com.example.data.booking.AppDatabase
import com.example.ui.theme.CyanNeon
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

    // Pre-populate seed data in Room DB if empty
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val count = db.amenityBookingDao().getPendingReminders(0).size
            if (count == 0) {
                val now = System.currentTimeMillis()
                val seedBookings = listOf(
                    AmenityBooking(
                        amenityName = "Quincho & BBQ Principal",
                        residentName = "Carlos Mendoza",
                        unitId = "Casa 208",
                        bookingTimeMillis = now + (20 * 60 * 1000), // In 20 minutes
                        reminderSent = false
                    ),
                    AmenityBooking(
                        amenityName = "Cancha de Pádel",
                        residentName = "Valeria Rojas",
                        unitId = "Depto 302",
                        bookingTimeMillis = now + (45 * 60 * 1000), // In 45 minutes
                        reminderSent = false
                    ),
                    AmenityBooking(
                        amenityName = "Gimnasio Residencial",
                        residentName = "Mariana López",
                        unitId = "Depto 101",
                        bookingTimeMillis = now + (90 * 60 * 1000), // In 90 minutes
                        reminderSent = false
                    )
                )
                seedBookings.forEach { booking ->
                    val id = db.amenityBookingDao().insertBooking(booking)
                    val saved = booking.copy(id = id)
                    AmenityReminderManager.schedule15MinReminder(context, saved)
                }
            }
        }
    }

    // New Booking Form State
    var showNewBookingForm by remember { mutableStateOf(false) }
    var selectedAmenity by remember { mutableStateOf("Quincho & BBQ Principal") }
    var residentNameInput by remember { mutableStateOf("") }
    var unitInput by remember { mutableStateOf("Casa 104") }
    var minutesFromNow by remember { mutableStateOf("25") }
    var expandedAmenityDropdown by remember { mutableStateOf(false) }

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
                                    val offsetMinutes = minutesFromNow.toIntOrNull() ?: 25
                                    val bookingTime = System.currentTimeMillis() + (offsetMinutes * 60 * 1000L)

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
                                        AmenityReminderManager.schedule15MinReminder(context, savedWithId)
                                    }

                                    Toast.makeText(
                                        context,
                                        "✅ Reserva guardada en Room DB. Recordatorio programado 15 min antes.",
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
}

@Composable
private fun BookingItemRow(
    booking: AmenityBooking,
    onTriggerTestReminder: () -> Unit,
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

    val reminderTimeFormatted = remember(booking.bookingTimeMillis) {
        val reminderMillis = booking.bookingTimeMillis - (15 * 60 * 1000)
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
        Row(
            modifier = Modifier.padding(10.dp),
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
                            text = if (booking.reminderSent) "Notificación enviada" else "Recordatorio a las $reminderTimeFormatted hrs (-15m)",
                            color = if (booking.reminderSent) SuccessGreen else CyanNeon,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Button(
                onClick = onTriggerTestReminder,
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon.copy(alpha = 0.2f), contentColor = CyanNeon),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("test_reminder_btn_${booking.id}")
            ) {
                Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Probar -15m", fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
