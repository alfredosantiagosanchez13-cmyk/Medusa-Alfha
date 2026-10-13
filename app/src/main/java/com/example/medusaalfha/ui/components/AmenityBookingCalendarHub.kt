package com.example.medusaalfha.ui.components

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.medusaalfha.data.model.AmenityArea
import com.example.medusaalfha.data.model.AmenityBooking
import com.example.medusaalfha.data.model.AmenityCatalog
import com.example.medusaalfha.data.model.BookingStatus
import com.example.medusaalfha.ui.theme.*
import com.example.medusaalfha.ui.viewmodel.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmenityBookingCalendarHub(
    bookingViewModel: AmenityBookingViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by bookingViewModel.uiState.collectAsState()
    val formState by bookingViewModel.formState.collectAsState()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage, uiState.successNotice) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            bookingViewModel.clearNotice()
        }
        uiState.successNotice?.let {
            snackbarHostState.showSnackbar(it)
            bookingViewModel.clearNotice()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { bookingViewModel.onOpenAddDialog() },
                icon = { Icon(Icons.Default.Add, contentDescription = "Reservar") },
                text = { Text("Nueva Reserva") },
                containerColor = TealAccent,
                contentColor = Color.White
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Selector de Amenidades / Áreas comunes
            ScrollableTabRow(
                selectedTabIndex = AmenityCatalog.areas.indexOfFirst { it.id == uiState.selectedAmenityId }.coerceAtLeast(0),
                edgePadding = 16.dp,
                containerColor = Color.White,
                divider = {}
            ) {
                AmenityCatalog.areas.forEach { area ->
                    val selected = area.id == uiState.selectedAmenityId
                    Tab(
                        selected = selected,
                        onClick = { bookingViewModel.onAmenitySelected(area.id) },
                        text = {
                            Text(
                                text = area.name,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) TealAccent else TextMuted
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selector horizontal de fecha (14 días próximos)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.calendarDays, key = { it.dateString }) { day ->
                    CalendarDayChip(
                        day = day,
                        onClick = { bookingViewModel.onDateSelected(day.dateString) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Info de la amenidad seleccionada
            val currentArea = AmenityCatalog.getAreaById(uiState.selectedAmenityId)
            if (currentArea != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shadowElevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(TealAccent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = TealAccent)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = currentArea.name,
                                fontWeight = FontWeight.Bold,
                                color = NavyPrimary
                            )
                            Text(
                                text = "${currentArea.description} · Capacidad: ${currentArea.capacity} pers.",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Horarios del día (${uiState.selectedDateString})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = NavyPrimary,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Lista de franjas horarias
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(uiState.timeSlots, key = { it.hour }) { slot ->
                    TimeSlotRow(
                        slot = slot,
                        onSelectSlot = { bookingViewModel.onSelectTimeSlot(slot.hour) },
                        onOpenDetail = { slot.activeBooking?.let { bookingViewModel.onOpenDetailBooking(it) } }
                    )
                }
            }
        }
    }

    // Modal de Nueva Reserva
    if (uiState.isAddDialogOpen) {
        CreateBookingDialog(
            formState = formState,
            onDismiss = { bookingViewModel.onDismissAddDialog() },
            onConfirm = { bookingViewModel.submitBooking(context) },
            onStartHourChanged = { bookingViewModel.onFormStartHourChanged(it) },
            onDurationChanged = { bookingViewModel.onFormDurationChanged(it) },
            onResidentNameChanged = { bookingViewModel.onFormResidentNameChanged(it) },
            onResidentHouseChanged = { bookingViewModel.onFormResidentHouseChanged(it) },
            onGuestCountChanged = { bookingViewModel.onFormGuestCountChanged(it) },
            onNotesChanged = { bookingViewModel.onFormNotesChanged(it) }
        )
    }

    // Modal de Detalle de Reserva
    if (uiState.selectedDetailBooking != null) {
        BookingDetailBottomSheet(
            booking = uiState.selectedDetailBooking!!,
            onDismiss = { bookingViewModel.onDismissDetailBooking() },
            onCancelBooking = { bookingViewModel.cancelBooking(uiState.selectedDetailBooking!!.id) }
        )
    }
}

@Composable
fun CalendarDayChip(day: CalendarDay, onClick: () -> Unit) {
    val bg = if (day.isSelected) TealAccent else Color.White
    val textC = if (day.isSelected) Color.White else NavyPrimary
    val subC = if (day.isSelected) Color.White.copy(alpha = 0.8f) else TextMuted

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        shadowElevation = if (day.isSelected) 3.dp else 1.dp,
        modifier = Modifier
            .width(52.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = day.dayOfWeekLetter, fontSize = 11.sp, color = subC, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = day.dayOfMonthNumber.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textC)
            if (day.isToday) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (day.isSelected) Color.White else TealAccent)
                )
            }
        }
    }
}

@Composable
fun TimeSlotRow(
    slot: TimeSlotSlot,
    onSelectSlot: () -> Unit,
    onOpenDetail: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (slot.isAvailable) onSelectSlot() else onOpenDetail()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = slot.hourLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = NavyPrimary
                )
                Spacer(modifier = Modifier.width(16.dp))
                if (slot.isAvailable) {
                    Text(
                        text = "Disponible",
                        fontSize = 13.sp,
                        color = StatusGreen,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Column {
                        Text(
                            text = slot.activeBooking?.residentName ?: "Reservado",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NavyPrimary
                        )
                        Text(
                            text = "${slot.activeBooking?.residentHouse} (${slot.activeBooking?.formattedTimeRange})",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            if (slot.isAvailable) {
                OutlinedButton(
                    onClick = onSelectSlot,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TealAccent)
                ) {
                    Text("Apartar", fontSize = 11.sp)
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFF1F5F9)
                ) {
                    Text(
                        text = "Ocupado",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CreateBookingDialog(
    formState: NewBookingFormState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onStartHourChanged: (Int) -> Unit,
    onDurationChanged: (Int) -> Unit,
    onResidentNameChanged: (String) -> Unit,
    onResidentHouseChanged: (String) -> Unit,
    onGuestCountChanged: (Int) -> Unit,
    onNotesChanged: (String) -> Unit
) {
    val area = AmenityCatalog.getAreaById(formState.amenityId)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reservar ${area?.name ?: "Área Común"}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (formState.conflictBooking != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Conflicto: Ya reservado de ${formState.conflictBooking.formattedTimeRange} por ${formState.conflictBooking.residentName}.",
                            fontSize = 12.sp,
                            color = StatusRed,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Text(
                    text = "Fecha: ${formState.dateString} · Horario: ${formState.startHour}:00 a ${formState.endHour}:00 (${formState.durationHours} hrs)",
                    fontSize = 12.sp,
                    color = TextMuted
                )

                OutlinedTextField(
                    value = formState.residentName,
                    onValueChange = onResidentNameChanged,
                    label = { Text("Nombre del Residente *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = formState.residentHouse,
                    onValueChange = onResidentHouseChanged,
                    label = { Text("Vivienda * (ej. Casa 54)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = formState.notes,
                    onValueChange = onNotesChanged,
                    label = { Text("Motivo / Evento (opcional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = formState.conflictBooking == null && formState.residentName.isNotBlank() && formState.residentHouse.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = TealAccent)
            ) {
                Text("Confirmar Reserva")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookingDetailBottomSheet(
    booking: AmenityBooking,
    onDismiss: () -> Unit,
    onCancelBooking: () -> Unit
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
                    text = "Detalle de Reserva",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = NavyPrimary
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFDCFCE7)
                ) {
                    Text(
                        text = booking.status.label,
                        fontSize = 11.sp,
                        color = StatusGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            DetailFieldRow("Área Común", booking.amenityName, Icons.Default.Place)
            DetailFieldRow("Residente", booking.residentName, Icons.Default.Person)
            DetailFieldRow("Vivienda", booking.residentHouse, Icons.Default.Home)
            DetailFieldRow("Fecha", booking.dateString, Icons.Default.CalendarToday)
            DetailFieldRow("Horario", booking.formattedTimeRange, Icons.Default.Schedule)
            DetailFieldRow("Invitados estimados", "${booking.guestCount} personas", Icons.Default.Groups)
            if (booking.notes.isNotBlank()) {
                DetailFieldRow("Notas", booking.notes, Icons.Default.Notes)
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (booking.status != BookingStatus.CANCELLED) {
                OutlinedButton(
                    onClick = {
                        onCancelBooking()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusRed)
                ) {
                    Text("Cancelar Reserva")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
