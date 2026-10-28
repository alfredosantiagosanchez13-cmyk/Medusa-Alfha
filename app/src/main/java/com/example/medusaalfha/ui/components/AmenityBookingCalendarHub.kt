package com.example.medusaalfha.ui.components

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
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
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
        containerColor = CyberBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { bookingViewModel.onOpenAddDialog() },
                icon = { Icon(Icons.Default.Add, contentDescription = "Reservar", tint = Color.Black) },
                text = { Text("Nueva Reserva", fontWeight = FontWeight.Bold, color = Color.Black) },
                containerColor = GoldMetallic,
                contentColor = Color.Black,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("fab_new_booking")
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ENCABEZADO TÁCTICO: ÁREAS COMUNES & RESERVAS
            Surface(
                color = CyberSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NeonBlue.copy(alpha = 0.15f))
                                    .border(1.dp, NeonBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventAvailable,
                                    contentDescription = null,
                                    tint = NeonBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "ÁREAS COMUNES & RESERVAS",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 15.sp,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "Los Prados Residencial · Control de Amenidades",
                                    fontSize = 11.sp,
                                    color = CyberTextMuted
                                )
                            }
                        }

                        // Badge de Disponibilidad en Vivo
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = EmeraldGreen.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(StatusNeonGreen)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "EN LÍNEA",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldGreen
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Selector de Amenidades / Áreas comunes (Tab row estilizado)
                    val selectedIndex = AmenityCatalog.areas.indexOfFirst { it.id == uiState.selectedAmenityId }.coerceAtLeast(0)
                    ScrollableTabRow(
                        selectedTabIndex = selectedIndex,
                        edgePadding = 0.dp,
                        containerColor = CyberSurface,
                        contentColor = GoldMetallic,
                        indicator = { tabPositions ->
                            if (selectedIndex < tabPositions.size) {
                                TabRowDefaults.SecondaryIndicator(
                                    Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                                    color = GoldMetallic,
                                    height = 3.dp
                                )
                            }
                        },
                        divider = {}
                    ) {
                        AmenityCatalog.areas.forEachIndexed { index, area ->
                            val selected = area.id == uiState.selectedAmenityId
                            Tab(
                                selected = selected,
                                onClick = { bookingViewModel.onAmenitySelected(area.id) },
                                text = {
                                    Text(
                                        text = area.name.uppercase(),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                        color = if (selected) GoldMetallic else CyberTextMuted
                                    )
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selector horizontal de fecha (14 días próximos)
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = "CALENDARIO DE FECHAS DISPONIBLES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonBlue,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.calendarDays, key = { it.dateString }) { day ->
                        CalendarDayChip(
                            day = day,
                            onClick = { bookingViewModel.onDateSelected(day.dateString) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Info de la amenidad seleccionada
            val currentArea = AmenityCatalog.getAreaById(uiState.selectedAmenityId)
            if (currentArea != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CyberSurfaceElevated,
                    border = BorderStroke(1.dp, CyberBorderCircuit),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(NeonBlue.copy(alpha = 0.12f))
                                .border(1.dp, NeonBlue.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Place, contentDescription = null, tint = NeonBlue)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentArea.name,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentArea.description,
                                fontSize = 11.sp,
                                color = CyberTextMuted
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GoldMetallic.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, GoldMetallic.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "Cap: ${currentArea.capacity}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldMetallic,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = NeonBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "FRANJAS HORARIAS · ${uiState.selectedDateString}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                }

                Text(
                    text = "08:00 - 22:00",
                    fontSize = 11.sp,
                    color = CyberTextMuted,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Lista de franjas horarias
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
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
    val isSelected = day.isSelected
    val bg = if (isSelected) GoldMetallic else CyberSurfaceElevated
    val textC = if (isSelected) Color.Black else Color.White
    val subC = if (isSelected) Color.Black.copy(alpha = 0.75f) else CyberTextMuted
    val borderStroke = if (isSelected) {
        BorderStroke(1.dp, GoldMetallic)
    } else {
        BorderStroke(1.dp, CyberBorderCircuit)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bg,
        border = borderStroke,
        modifier = Modifier
            .width(54.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = day.dayOfWeekLetter,
                fontSize = 11.sp,
                color = subC,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = day.dayOfMonthNumber.toString(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = textC,
                fontFamily = FontFamily.Monospace
            )
            if (day.isToday) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color.Black else NeonBlue)
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
    val isAvail = slot.isAvailable
    val cardBorder = if (isAvail) {
        BorderStroke(1.dp, CyberBorderCircuit)
    } else {
        BorderStroke(1.dp, AlertRed.copy(alpha = 0.35f))
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CyberSurfaceElevated,
        border = cardBorder,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isAvail) onSelectSlot() else onOpenDetail()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Badge de Hora
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyberSurface,
                    border = BorderStroke(1.dp, CyberBorderCircuit)
                ) {
                    Text(
                        text = slot.hourLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = NeonBlue,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                if (isAvail) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(EmeraldGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Disponible",
                            fontSize = 13.sp,
                            color = EmeraldGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Column {
                        Text(
                            text = slot.activeBooking?.residentName ?: "Reservado",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${slot.activeBooking?.residentHouse} · ${slot.activeBooking?.formattedTimeRange}",
                            fontSize = 11.sp,
                            color = CyberTextMuted
                        )
                    }
                }
            }

            if (isAvail) {
                OutlinedButton(
                    onClick = onSelectSlot,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    border = BorderStroke(1.dp, NeonBlue),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonBlue)
                ) {
                    Text("Apartar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AlertRed.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "OCUPADO",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AlertRed,
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
        containerColor = CyberSurface,
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EventSeat, contentDescription = null, tint = GoldMetallic)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Reservar ${area?.name ?: "Área Común"}",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 17.sp
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (formState.conflictBooking != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AlertRed.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, AlertRed),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Conflicto: Ya reservado de ${formState.conflictBooking.formattedTimeRange} por ${formState.conflictBooking.residentName}.",
                            fontSize = 12.sp,
                            color = AlertRed,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyberSurfaceElevated,
                    border = BorderStroke(1.dp, CyberBorderCircuit),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Fecha: ${formState.dateString} · Horario: ${formState.startHour}:00 a ${formState.endHour}:00 (${formState.durationHours} hrs)",
                        fontSize = 11.sp,
                        color = GoldMetallic,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(8.dp)
                    )
                }

                OutlinedTextField(
                    value = formState.residentName,
                    onValueChange = onResidentNameChanged,
                    label = { Text("Nombre del Residente *") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = CyberBorderCircuit,
                        focusedLabelColor = NeonBlue,
                        unfocusedLabelColor = CyberTextMuted
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_booking_resident_name")
                )

                OutlinedTextField(
                    value = formState.residentHouse,
                    onValueChange = onResidentHouseChanged,
                    label = { Text("Vivienda * (ej. Casa 54)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = CyberBorderCircuit,
                        focusedLabelColor = NeonBlue,
                        unfocusedLabelColor = CyberTextMuted
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_booking_resident_house")
                )

                OutlinedTextField(
                    value = formState.notes,
                    onValueChange = onNotesChanged,
                    label = { Text("Motivo / Evento (opcional)") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = CyberBorderCircuit,
                        focusedLabelColor = NeonBlue,
                        unfocusedLabelColor = CyberTextMuted
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_booking_notes")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = formState.conflictBooking == null && formState.residentName.isNotBlank() && formState.residentHouse.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldMetallic,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_confirm_booking")
            ) {
                Text("Confirmar Reserva", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = CyberTextMuted)
            }
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
        containerColor = CyberSurface
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
                    color = GoldMetallic
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = EmeraldGreen.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, EmeraldGreen.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = booking.status.label,
                        fontSize = 11.sp,
                        color = EmeraldGreen,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            AmenityDetailFieldRow("Área Común", booking.amenityName, Icons.Default.Place)
            AmenityDetailFieldRow("Residente", booking.residentName, Icons.Default.Person)
            AmenityDetailFieldRow("Vivienda", booking.residentHouse, Icons.Default.Home)
            AmenityDetailFieldRow("Fecha", booking.dateString, Icons.Default.CalendarToday)
            AmenityDetailFieldRow("Horario", booking.formattedTimeRange, Icons.Default.Schedule)
            AmenityDetailFieldRow("Invitados estimados", "${booking.guestCount} personas", Icons.Default.Groups)
            if (booking.notes.isNotBlank()) {
                AmenityDetailFieldRow("Notas", booking.notes, Icons.AutoMirrored.Filled.Notes)
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
                    border = BorderStroke(1.dp, AlertRed),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed)
                ) {
                    Text("Cancelar Reserva", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun AmenityDetailFieldRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
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
