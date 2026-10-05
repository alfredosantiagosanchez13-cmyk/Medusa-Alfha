package com.example.medusaalfha.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.OutdoorGrill
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.medusaalfha.data.model.AmenityArea
import com.example.medusaalfha.data.model.AmenityBooking
import com.example.medusaalfha.data.model.AmenityCatalog
import com.example.medusaalfha.data.model.BookingStatus
import com.example.medusaalfha.ui.theme.CyanNeon
import com.example.medusaalfha.ui.theme.GoldLight
import com.example.medusaalfha.ui.theme.GoldPrimary
import com.example.medusaalfha.ui.theme.NavyBorder
import com.example.medusaalfha.ui.theme.NavyCard
import com.example.medusaalfha.ui.theme.NavyDark
import com.example.medusaalfha.ui.theme.NavySurface
import com.example.medusaalfha.ui.theme.StatusCompletedSlate
import com.example.medusaalfha.ui.theme.StatusDeniedRed
import com.example.medusaalfha.ui.theme.StatusInsideContainer
import com.example.medusaalfha.ui.theme.StatusInsideGreen
import com.example.medusaalfha.ui.theme.TextDim
import com.example.medusaalfha.ui.theme.TextMuted
import com.example.medusaalfha.ui.theme.TextWhite
import com.example.medusaalfha.ui.viewmodel.BookingUiState
import com.example.medusaalfha.ui.viewmodel.CalendarDay
import com.example.medusaalfha.ui.viewmodel.NewBookingFormState
import com.example.medusaalfha.ui.viewmodel.TimeSlotSlot
import java.util.Locale

@Composable
fun AmenityBookingCalendarHub(
    uiState: BookingUiState,
    formState: NewBookingFormState,
    onSelectDate: (String) -> Unit,
    onFilterAmenity: (String?) -> Unit,
    onOpenAddDialog: (Int?) -> Unit,
    onDismissAddDialog: () -> Unit,
    onOpenDetailBooking: (AmenityBooking) -> Unit,
    onDismissDetailBooking: () -> Unit,
    onFormAmenityChanged: (String) -> Unit,
    onFormResidentNameChanged: (String) -> Unit,
    onFormResidentHouseChanged: (String) -> Unit,
    onFormStartHourChanged: (Int) -> Unit,
    onFormEndHourChanged: (Int) -> Unit,
    onFormGuestCountChanged: (Int) -> Unit,
    onFormNotesChanged: (String) -> Unit,
    onSubmitBooking: () -> Unit,
    onCancelBooking: (String) -> Unit,
    onSeedData: () -> Unit,
    onClearNotice: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedViewTabIndex by remember { mutableIntStateOf(0) } // 0: Línea de Tiempo, 1: Reservas Activas

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NavyDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Header con Título y Estado Firestore
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ÁREAS COMUNES",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = GoldPrimary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0F3D3E))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = null,
                                    tint = CyanNeon,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "FIRESTORE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanNeon
                                )
                            }
                        }
                    }
                    Text(
                        text = "Disponibilidad y prevención de traslapes en tiempo real",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted)
                    )
                }

                Button(
                    onClick = { onOpenAddDialog(null) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = NavyDark
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("new_booking_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "RESERVAR",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            // Franja de Notificación / Alerta
            AnimatedVisibility(visible = uiState.userNotice != null || uiState.errorMessage != null) {
                val isError = uiState.errorMessage != null
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isError) Color(0xFF450A0A) else Color(0xFF064E3B)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable { onClearNotice() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isError) StatusDeniedRed else StatusInsideGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uiState.errorMessage ?: uiState.userNotice ?: "",
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Selector Calendario Horizontal (Días)
            Text(
                text = "SELECCIÓN DE FECHA",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = GoldMutedOrTextDim(),
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("calendar_day_strip")
            ) {
                items(uiState.calendarDays, key = { it.dateString }) { day ->
                    CalendarDayItem(
                        day = day,
                        onClick = { onSelectDate(day.dateString) }
                    )
                }
            }

            // Filtro por Área Común (Horizontal scroll chips)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = uiState.selectedAmenityId == null,
                    onClick = { onFilterAmenity(null) },
                    label = { Text("Todas las áreas", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GoldPrimary,
                        selectedLabelColor = NavyDark,
                        containerColor = NavyCard,
                        labelColor = TextMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = NavyBorder,
                        selectedBorderColor = GoldPrimary,
                        enabled = true,
                        selected = uiState.selectedAmenityId == null
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                AmenityCatalog.AMENITIES.forEach { area ->
                    val isSelected = uiState.selectedAmenityId == area.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { onFilterAmenity(if (isSelected) null else area.id) },
                        leadingIcon = {
                            Icon(
                                imageVector = getAmenityIcon(area.id),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (isSelected) NavyDark else GoldLight
                            )
                        },
                        label = { Text(area.badgeLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldPrimary,
                            selectedLabelColor = NavyDark,
                            containerColor = NavyCard,
                            labelColor = TextWhite
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = NavyBorder,
                            selectedBorderColor = GoldPrimary,
                            enabled = true,
                            selected = isSelected
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            // Pestañas de Vista: [Línea de Tiempo] vs [Reservas Confirmadas]
            TabRow(
                selectedTabIndex = selectedViewTabIndex,
                containerColor = NavySurface,
                contentColor = GoldPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedViewTabIndex]),
                        color = GoldPrimary,
                        height = 2.dp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Tab(
                    selected = selectedViewTabIndex == 0,
                    onClick = { selectedViewTabIndex = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Disponibilidad por Hora", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = selectedViewTabIndex == 1,
                    onClick = { selectedViewTabIndex = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Lista (${uiState.filteredBookings.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Contenido según la pestaña seleccionada
            if (selectedViewTabIndex == 0) {
                // Vista de Línea de Tiempo Horaria
                HourlyTimelineView(
                    timeSlots = uiState.timeSlots,
                    onOpenAddDialog = onOpenAddDialog,
                    onOpenDetailBooking = onOpenDetailBooking,
                    modifier = Modifier.weight(1f)
                )
            } else {
                // Vista de Lista de Reservaciones
                BookingsListView(
                    bookings = uiState.filteredBookings,
                    onOpenDetailBooking = onOpenDetailBooking,
                    onOpenAddDialog = { onOpenAddDialog(null) },
                    onSeedData = onSeedData,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Diálogo para Agregar Nueva Reserva con Detección de Traslape
        if (uiState.isAddDialogOpen) {
            NewBookingDialog(
                formState = formState,
                selectedDate = uiState.selectedDateString,
                onAmenityChanged = onFormAmenityChanged,
                onResidentNameChanged = onFormResidentNameChanged,
                onResidentHouseChanged = onFormResidentHouseChanged,
                onStartHourChanged = onFormStartHourChanged,
                onEndHourChanged = onFormEndHourChanged,
                onGuestCountChanged = onFormGuestCountChanged,
                onNotesChanged = onFormNotesChanged,
                onDismiss = onDismissAddDialog,
                onSubmit = onSubmitBooking
            )
        }

        // Hoja de Detalle de Reserva
        uiState.selectedDetailBooking?.let { detailBooking ->
            BookingDetailDialog(
                booking = detailBooking,
                onDismiss = onDismissDetailBooking,
                onCancelBooking = { onCancelBooking(detailBooking.id) }
            )
        }
    }
}

@Composable
private fun CalendarDayItem(
    day: CalendarDay,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (day.isSelected) GoldPrimary else NavyBorder,
        label = "dayBorder"
    )
    val bgColor by animateColorAsState(
        targetValue = if (day.isSelected) NavySurface else NavyCard,
        label = "dayBg"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(borderColor, borderColor))),
        modifier = Modifier
            .width(62.dp)
            .height(84.dp)
            .clickable(onClick = onClick)
            .testTag("day_${day.dateString}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = day.dayOfWeekLetter,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (day.isSelected) GoldPrimary else TextMuted
            )

            Text(
                text = day.dayOfMonthNumber,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = if (day.isSelected) TextWhite else TextWhite.copy(alpha = 0.85f)
            )

            // Indicador de Reservas o Etiqueta "HOY"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (day.isToday) {
                    Text(
                        text = "HOY",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CyanNeon
                    )
                } else if (day.hasBookings) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary)
                    )
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun HourlyTimelineView(
    timeSlots: List<TimeSlotSlot>,
    onOpenAddDialog: (Int) -> Unit,
    onOpenDetailBooking: (AmenityBooking) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 6.dp)
    ) {
        items(timeSlots, key = { it.hour }) { slot ->
            TimeSlotRow(
                slot = slot,
                onAddClick = { onOpenAddDialog(slot.hour) },
                onBookingClick = { slot.activeBooking?.let(onOpenDetailBooking) }
            )
        }
    }
}

@Composable
private fun TimeSlotRow(
    slot: TimeSlotSlot,
    onAddClick: () -> Unit,
    onBookingClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Etiqueta de Hora
        Text(
            text = slot.hourLabel,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = if (slot.isAvailable) TextMuted else GoldPrimary
            ),
            modifier = Modifier.width(52.dp)
        )

        // Contenedor del Intervalo
        if (slot.isAvailable) {
            Surface(
                color = NavyCard.copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, NavyBorder),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(onClick = onAddClick)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(StatusInsideGreen)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Disponible para reservar",
                            fontSize = 12.sp,
                            color = TextMuted
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(NavySurface)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "+ Reservar",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldLight
                        )
                    }
                }
            }
        } else {
            val booking = slot.activeBooking!!
            Surface(
                color = Color(0xFF1E2842),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldPrimary.copy(alpha = 0.6f)),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(onClick = onBookingClick)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = getAmenityIcon(booking.amenityId),
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = booking.residentHouse,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${booking.amenityName} · ${booking.formattedTimeRange()}",
                                fontSize = 10.sp,
                                color = TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2E1B1B))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "OCUPADO",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFF8A80)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingsListView(
    bookings: List<AmenityBooking>,
    onOpenDetailBooking: (AmenityBooking) -> Unit,
    onOpenAddDialog: () -> Unit,
    onSeedData: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (bookings.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.EventAvailable,
                contentDescription = null,
                tint = GoldPrimary.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Sin reservas para este día o área",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextWhite
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "El horario se encuentra 100% disponible sin conflictos.",
                fontSize = 12.sp,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpenAddDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Crear Primera Reserva", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = onSeedData,
                    border = androidx.compose.foundation.BorderStroke(1.dp, NavyBorder),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Cargar Ejemplos", color = GoldLight, fontSize = 12.sp)
                }
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            items(bookings, key = { it.id }) { booking ->
                BookingCardItem(
                    booking = booking,
                    onClick = { onOpenDetailBooking(booking) }
                )
            }
        }
    }
}

@Composable
private fun BookingCardItem(
    booking: AmenityBooking,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        shape = RoundedCornerShape(12.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(NavyBorder, NavyBorder))),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("booking_card_${booking.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
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
                            .background(NavySurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getAmenityIcon(booking.amenityId),
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = booking.amenityName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextWhite
                            )
                        )
                        Text(
                            text = "Folio: ${booking.id}",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            when (booking.status) {
                                BookingStatus.CONFIRMADA -> StatusInsideContainer
                                BookingStatus.EN_CURSO -> Color(0xFF164E63)
                                BookingStatus.CANCELADA -> Color(0xFF450A0A)
                                else -> NavySurface
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = booking.status.name,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (booking.status) {
                            BookingStatus.CONFIRMADA -> StatusInsideGreen
                            BookingStatus.EN_CURSO -> CyanNeon
                            BookingStatus.CANCELADA -> StatusDeniedRed
                            else -> TextMuted
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = GoldLight,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = booking.formattedTimeRange(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = GoldLight
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = CyanNeon,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = booking.residentHouse,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = TextWhite
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${booking.guestCount} pers.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                }
            }

            if (booking.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Nota: ${booking.notes}",
                    fontSize = 11.sp,
                    color = TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NewBookingDialog(
    formState: NewBookingFormState,
    selectedDate: String,
    onAmenityChanged: (String) -> Unit,
    onResidentNameChanged: (String) -> Unit,
    onResidentHouseChanged: (String) -> Unit,
    onStartHourChanged: (Int) -> Unit,
    onEndHourChanged: (Int) -> Unit,
    onGuestCountChanged: (Int) -> Unit,
    onNotesChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    val selectedArea = AmenityCatalog.findById(formState.amenityId)
    val hasConflict = formState.conflictBooking != null

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(GoldPrimary, NavyBorder))),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("new_booking_dialog")
        ) {
            LazyColumn(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "RESERVAR ÁREA COMÚN",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = GoldPrimary
                        )
                    )
                    Text(
                        text = "Fecha seleccionada: $selectedDate",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }

                // Selector de Área Común
                item {
                    Text(
                        text = "ÁREA COMÚN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldLight
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(AmenityCatalog.AMENITIES) { area ->
                            val isSel = area.id == formState.amenityId
                            FilterChip(
                                selected = isSel,
                                onClick = { onAmenityChanged(area.id) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = getAmenityIcon(area.id),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                label = { Text(area.badgeLabel, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldPrimary,
                                    selectedLabelColor = NavyDark,
                                    containerColor = NavyCard,
                                    labelColor = TextWhite
                                )
                            )
                        }
                    }
                }

                // Selector de Horas con Sliders
                item {
                    Text(
                        text = "HORARIO DE USO: ${String.format(Locale.getDefault(), "%02d:00", formState.startHour)} a ${String.format(Locale.getDefault(), "%02d:00", formState.endHour)} (${formState.endHour - formState.startHour} horas)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = GoldLight
                    )
                    Text(
                        text = "Horario permitido: ${selectedArea.openHour}:00 a ${selectedArea.closeHour}:00",
                        fontSize = 10.sp,
                        color = TextMuted
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Inicio: ${formState.startHour}:00", fontSize = 11.sp, color = TextWhite)
                        Text("Término: ${formState.endHour}:00", fontSize = 11.sp, color = TextWhite)
                    }

                    Slider(
                        value = formState.startHour.toFloat(),
                        onValueChange = { onStartHourChanged(it.toInt()) },
                        valueRange = selectedArea.openHour.toFloat()..(selectedArea.closeHour - 1).toFloat(),
                        steps = (selectedArea.closeHour - selectedArea.openHour - 2).coerceAtLeast(0),
                        colors = SliderDefaults.colors(
                            thumbColor = GoldPrimary,
                            activeTrackColor = GoldPrimary,
                            inactiveTrackColor = NavyBorder
                        )
                    )

                    Slider(
                        value = formState.endHour.toFloat(),
                        onValueChange = { onEndHourChanged(it.toInt()) },
                        valueRange = (formState.startHour + 1).toFloat()..selectedArea.closeHour.toFloat(),
                        steps = (selectedArea.closeHour - formState.startHour - 2).coerceAtLeast(0),
                        colors = SliderDefaults.colors(
                            thumbColor = CyanNeon,
                            activeTrackColor = CyanNeon,
                            inactiveTrackColor = NavyBorder
                        )
                    )
                }

                // BANNER DE VALIDACIÓN DE TRASLAPE EN VIVO
                item {
                    if (hasConflict) {
                        val conf = formState.conflictBooking!!
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusDeniedRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EventBusy,
                                    contentDescription = null,
                                    tint = StatusDeniedRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "TRASLAPE DE HORARIO DETECTADO",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        color = Color(0xFFFF8A80)
                                    )
                                    Text(
                                        text = "Ya reservado de ${conf.formattedTimeRange()} por ${conf.residentHouse} (${conf.residentName}). Selecciona otro horario.",
                                        fontSize = 11.sp,
                                        color = TextWhite
                                    )
                                }
                            }
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusInsideGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = StatusInsideGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Horario Disponible: No existen traslapes.",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color(0xFFA7F3D0)
                                )
                            }
                        }
                    }
                }

                // Vivienda y Nombre
                item {
                    OutlinedTextField(
                        value = formState.residentHouse,
                        onValueChange = onResidentHouseChanged,
                        label = { Text("Vivienda / Residencia") },
                        placeholder = { Text("Ej. Casa 54 · Circuito Los Álamos") },
                        leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = GoldLight) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = NavyBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = formState.residentName,
                        onValueChange = onResidentNameChanged,
                        label = { Text("Nombre del Residente Titular") },
                        placeholder = { Text("Ej. Arq. Rodrigo Gómez") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = GoldLight) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = NavyBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Invitados
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Cantidad de Invitados", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhite)
                            Text("Capacidad máx: ${selectedArea.maxCapacity} pers.", fontSize = 10.sp, color = TextMuted)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onGuestCountChanged((formState.guestCount - 1).coerceAtLeast(1)) }
                            ) {
                                Text("-", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                            }
                            Text(
                                text = "${formState.guestCount}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = TextWhite,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            IconButton(
                                onClick = { onGuestCountChanged((formState.guestCount + 1).coerceAtMost(selectedArea.maxCapacity)) }
                            ) {
                                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                            }
                        }
                    }
                }

                // Notas
                item {
                    OutlinedTextField(
                        value = formState.notes,
                        onValueChange = onNotesChanged,
                        label = { Text("Propósito / Observaciones") },
                        placeholder = { Text("Ej. Convivio familiar, cumpleaños...") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = NavyBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Botones Cancelar / Confirmar
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancelar", color = TextMuted)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onSubmit,
                            enabled = !hasConflict,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldPrimary,
                                contentColor = NavyDark,
                                disabledContainerColor = NavyBorder,
                                disabledContentColor = TextDim
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("confirm_booking_button")
                        ) {
                            Text("CONFIRMAR RESERVA", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookingDetailDialog(
    booking: AmenityBooking,
    onDismiss: () -> Unit,
    onCancelBooking: () -> Unit
) {
    val area = AmenityCatalog.findById(booking.amenityId)
    var showConfirmCancel by remember { androidx.compose.runtime.mutableStateOf(false) }

    if (showConfirmCancel) {
        AlertDialog(
            onDismissRequest = { showConfirmCancel = false },
            title = { Text("¿Cancelar esta reserva?", fontWeight = FontWeight.Bold) },
            text = { Text("El horario ${booking.formattedTimeRange()} quedará libre para que otros residentes puedan reservarlo en Cloud Firestore.") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmCancel = false
                        onCancelBooking()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDeniedRed)
                ) {
                    Text("Sí, Cancelar Reserva")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmCancel = false }) {
                    Text("Regresar")
                }
            },
            containerColor = NavySurface,
            titleContentColor = TextWhite,
            textContentColor = TextMuted
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(GoldPrimary, NavyBorder))),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = booking.amenityName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = TextWhite
                            )
                        )
                        Text(
                            text = "Folio Oficial: ${booking.id}",
                            fontSize = 11.sp,
                            color = GoldLight
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Cancel, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                // Datos clave
                Surface(
                    color = NavyCard,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NavyBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DetailRow("Fecha:", booking.dateString)
                        DetailRow("Horario:", booking.formattedTimeRange())
                        DetailRow("Titular:", booking.residentName)
                        DetailRow("Vivienda:", booking.residentHouse)
                        DetailRow("Asistentes:", "${booking.guestCount} personas (Máx: ${area.maxCapacity})")
                        DetailRow("Estado:", booking.status.name)
                        if (booking.notes.isNotBlank()) {
                            DetailRow("Propósito:", booking.notes)
                        }
                    }
                }

                // Reglamento de área común
                Surface(
                    color = Color(0xFF131D33),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = area.rulesSummary,
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }

                // Acciones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (booking.status != BookingStatus.CANCELADA) {
                        OutlinedButton(
                            onClick = { showConfirmCancel = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDeniedRed),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StatusDeniedRed)
                        ) {
                            Text("Liberar Horario", fontSize = 12.sp)
                        }
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark)
                    ) {
                        Text("Aceptar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = TextMuted)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextWhite)
    }
}

private fun getAmenityIcon(amenityId: String): ImageVector {
    return when (amenityId) {
        "ALBERCA_PALAPA" -> Icons.Default.Pool
        "CASA_CLUB" -> Icons.Default.MeetingRoom
        "CANCHA_PADEL" -> Icons.Default.SportsTennis
        "ASADORES_GRILL" -> Icons.Default.OutdoorGrill
        "GIMNASIO" -> Icons.Default.FitnessCenter
        else -> Icons.Default.EventAvailable
    }
}

@Composable
private fun GoldMutedOrTextDim(): Color = GoldLight.copy(alpha = 0.8f)
