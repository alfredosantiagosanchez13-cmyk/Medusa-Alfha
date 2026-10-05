package com.example.medusaalfha.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.medusaalfha.data.model.AmenityArea
import com.example.medusaalfha.data.model.AmenityBooking
import com.example.medusaalfha.data.model.AmenityCatalog
import com.example.medusaalfha.data.model.BookingStatus
import com.example.medusaalfha.data.repository.AmenityBookingRepository
import com.example.medusaalfha.data.repository.ResidentAlertRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CalendarDay(
    val dateString: String,      // YYYY-MM-DD
    val dayOfWeekLetter: String, // L, M, M, J, V, S, D
    val dayOfMonthNumber: String,// 05, 06, etc.
    val isToday: Boolean,
    val isSelected: Boolean,
    val hasBookings: Boolean
)

data class TimeSlotSlot(
    val hour: Int,
    val hourLabel: String,
    val isAvailable: Boolean,
    val activeBooking: AmenityBooking? = null
)

data class BookingUiState(
    val isLoading: Boolean = false,
    val allBookings: List<AmenityBooking> = emptyList(),
    val selectedDateString: String = AmenityBooking.todayDateString(),
    val selectedAmenityId: String? = null, // null = Todas
    val calendarDays: List<CalendarDay> = emptyList(),
    val filteredBookings: List<AmenityBooking> = emptyList(),
    val timeSlots: List<TimeSlotSlot> = emptyList(),
    val isAddDialogOpen: Boolean = false,
    val selectedDetailBooking: AmenityBooking? = null,
    val userNotice: String? = null,
    val errorMessage: String? = null
)

data class NewBookingFormState(
    val amenityId: String = "ALBERCA_PALAPA",
    val residentName: String = "",
    val residentHouse: String = "",
    val startHour: Int = 11,
    val endHour: Int = 14,
    val guestCount: Int = 6,
    val notes: String = "",
    val conflictBooking: AmenityBooking? = null,
    val isCheckingAvailability: Boolean = false
)

class AmenityBookingViewModel(
    application: Application,
    private val repository: AmenityBookingRepository = AmenityBookingRepository(),
    private val alertRepository: ResidentAlertRepository = ResidentAlertRepository()
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(BookingUiState())
    val uiState: StateFlow<BookingUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(NewBookingFormState())
    val formState: StateFlow<NewBookingFormState> = _formState.asStateFlow()

    init {
        val todayStr = AmenityBooking.todayDateString()
        _uiState.value = _uiState.value.copy(
            selectedDateString = todayStr,
            calendarDays = generateCalendarDays(todayStr, emptyList())
        )
        listenToBookings()
    }

    private fun listenToBookings() {
        _uiState.value = _uiState.value.copy(isLoading = true)

        repository.getBookingsFlow()
            .onEach { bookingsList ->
                val activeDate = _uiState.value.selectedDateString
                val amenityFilter = _uiState.value.selectedAmenityId
                val filtered = filterBookings(bookingsList, activeDate, amenityFilter)
                val days = generateCalendarDays(activeDate, bookingsList)
                val slots = generateTimeSlots(activeDate, amenityFilter, bookingsList)

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    allBookings = bookingsList,
                    calendarDays = days,
                    filteredBookings = filtered,
                    timeSlots = slots,
                    errorMessage = null
                )
                // Actualizar validación de traslape en el formulario si está abierto
                revalidateFormConflict(bookingsList)
            }
            .catch { e ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Sincronización en contingencia local: ${e.message}"
                )
            }
            .launchIn(viewModelScope)
    }

    fun onSelectDate(dateString: String) {
        val days = generateCalendarDays(dateString, _uiState.value.allBookings)
        val filtered = filterBookings(_uiState.value.allBookings, dateString, _uiState.value.selectedAmenityId)
        val slots = generateTimeSlots(dateString, _uiState.value.selectedAmenityId, _uiState.value.allBookings)

        _uiState.value = _uiState.value.copy(
            selectedDateString = dateString,
            calendarDays = days,
            filteredBookings = filtered,
            timeSlots = slots
        )
        revalidateFormConflict(_uiState.value.allBookings)
    }

    fun onFilterAmenity(amenityId: String?) {
        val filtered = filterBookings(_uiState.value.allBookings, _uiState.value.selectedDateString, amenityId)
        val slots = generateTimeSlots(_uiState.value.selectedDateString, amenityId, _uiState.value.allBookings)

        _uiState.value = _uiState.value.copy(
            selectedAmenityId = amenityId,
            filteredBookings = filtered,
            timeSlots = slots
        )
    }

    fun onOpenAddDialog(initialHour: Int? = null) {
        val preferredHour = initialHour ?: 10
        val targetAmenity = _uiState.value.selectedAmenityId ?: "ALBERCA_PALAPA"
        val initialForm = NewBookingFormState(
            amenityId = targetAmenity,
            residentName = "",
            residentHouse = "Casa 54 · Circuito Los Álamos",
            startHour = preferredHour,
            endHour = (preferredHour + 2).coerceAtMost(22),
            guestCount = 4,
            notes = ""
        )
        _formState.value = initialForm
        revalidateFormConflict(_uiState.value.allBookings)
        _uiState.value = _uiState.value.copy(isAddDialogOpen = true)
    }

    fun onDismissAddDialog() {
        _uiState.value = _uiState.value.copy(isAddDialogOpen = false)
    }

    fun onOpenDetailBooking(booking: AmenityBooking) {
        _uiState.value = _uiState.value.copy(selectedDetailBooking = booking)
    }

    fun onDismissDetailBooking() {
        _uiState.value = _uiState.value.copy(selectedDetailBooking = null)
    }

    fun onFormAmenityChanged(amenityId: String) {
        val area = AmenityCatalog.findById(amenityId)
        val clampedStart = _formState.value.startHour.coerceIn(area.openHour, area.closeHour - 1)
        val clampedEnd = (clampedStart + 2).coerceIn(clampedStart + 1, area.closeHour)
        _formState.value = _formState.value.copy(
            amenityId = amenityId,
            startHour = clampedStart,
            endHour = clampedEnd
        )
        revalidateFormConflict(_uiState.value.allBookings)
    }

    fun onFormResidentNameChanged(name: String) {
        _formState.value = _formState.value.copy(residentName = name)
    }

    fun onFormResidentHouseChanged(house: String) {
        _formState.value = _formState.value.copy(residentHouse = house)
    }

    fun onFormStartHourChanged(startHour: Int) {
        val newEnd = if (startHour >= _formState.value.endHour) (startHour + 1).coerceAtMost(23) else _formState.value.endHour
        _formState.value = _formState.value.copy(startHour = startHour, endHour = newEnd)
        revalidateFormConflict(_uiState.value.allBookings)
    }

    fun onFormEndHourChanged(endHour: Int) {
        val newStart = if (endHour <= _formState.value.startHour) (endHour - 1).coerceAtLeast(6) else _formState.value.startHour
        _formState.value = _formState.value.copy(startHour = newStart, endHour = endHour)
        revalidateFormConflict(_uiState.value.allBookings)
    }

    fun onFormGuestCountChanged(guests: Int) {
        _formState.value = _formState.value.copy(guestCount = guests)
    }

    fun onFormNotesChanged(notes: String) {
        _formState.value = _formState.value.copy(notes = notes)
    }

    private fun revalidateFormConflict(bookings: List<AmenityBooking>) {
        val form = _formState.value
        val area = AmenityCatalog.findById(form.amenityId)
        val candidate = AmenityBooking(
            id = "TEMP_VALIDATION",
            amenityId = form.amenityId,
            amenityName = area.name,
            dateString = _uiState.value.selectedDateString,
            startTimeHour = form.startHour,
            endTimeHour = form.endHour,
            status = BookingStatus.CONFIRMADA
        )
        val (isAvailable, conflict) = repository.checkAvailability(candidate, bookings)
        _formState.value = form.copy(conflictBooking = if (!isAvailable) conflict else null)
    }

    fun submitBooking() {
        val form = _formState.value
        if (form.conflictBooking != null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "No se puede confirmar: el horario se traslapa con la reserva de ${form.conflictBooking.residentHouse}."
            )
            return
        }

        val area = AmenityCatalog.findById(form.amenityId)
        val newBooking = AmenityBooking(
            id = "BOOK-${System.currentTimeMillis() % 100000}",
            amenityId = form.amenityId,
            amenityName = area.name,
            residentName = form.residentName.ifBlank { "Residente Titular" },
            residentHouse = form.residentHouse.ifBlank { "Casa 54 · Circuito Los Álamos" },
            dateString = _uiState.value.selectedDateString,
            startTimeHour = form.startHour,
            endTimeHour = form.endHour,
            guestCount = form.guestCount,
            status = BookingStatus.CONFIRMADA,
            notes = form.notes.ifBlank { "Reserva registrada desde panel táctico" },
            createdAtMillis = System.currentTimeMillis()
        )

        viewModelScope.launch {
            val result = repository.createBooking(newBooking)
            if (result.isSuccess) {
                // Notificar en tiempo real al dispositivo del residente y seguridad (FCM)
                alertRepository.dispatchBookingConfirmedAlert(
                    context = getApplication(),
                    amenityName = newBooking.amenityName,
                    destinationHouse = newBooking.residentHouse,
                    dateString = newBooking.dateString,
                    timeRange = newBooking.formattedTimeRange(),
                    bookingId = newBooking.id
                )

                _uiState.value = _uiState.value.copy(
                    isAddDialogOpen = false,
                    userNotice = "¡Reserva confirmada con éxito para ${area.badgeLabel} (${newBooking.formattedTimeRange()})!"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = result.exceptionOrNull()?.message ?: "Error al registrar reserva."
                )
            }
        }
    }

    fun cancelBooking(bookingId: String) {
        viewModelScope.launch {
            val result = repository.cancelBooking(bookingId)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    selectedDetailBooking = null,
                    userNotice = "Reserva cancelada correctamente. El horario ha quedado liberado."
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "No se pudo cancelar: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun seedSampleBookings() {
        viewModelScope.launch {
            val result = repository.seedSampleBookings()
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(
                    userNotice = "Se sincronizaron ${result.getOrNull()} reservas de muestra con Cloud Firestore."
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    userNotice = "Reservas activas en almacenamiento local."
                )
            }
        }
    }

    fun clearNotice() {
        _uiState.value = _uiState.value.copy(userNotice = null, errorMessage = null)
    }

    // Helper functions
    private fun filterBookings(
        all: List<AmenityBooking>,
        dateStr: String,
        amenityId: String?
    ): List<AmenityBooking> {
        return all.filter { booking ->
            val dateMatches = booking.dateString == dateStr
            val amenityMatches = amenityId == null || booking.amenityId == amenityId
            dateMatches && amenityMatches
        }.sortedBy { it.startTimeHour }
    }

    private fun generateCalendarDays(selectedDate: String, bookings: List<AmenityBooking>): List<CalendarDay> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dayOfWeekFormat = SimpleDateFormat("EEE", Locale("es", "MX"))
        val dayNumberFormat = SimpleDateFormat("dd", Locale.getDefault())
        val todayStr = AmenityBooking.todayDateString()

        val cal = Calendar.getInstance()
        val list = mutableListOf<CalendarDay>()

        for (i in 0 until 14) {
            val date = cal.time
            val dStr = sdf.format(date)
            val hasBook = bookings.any { it.dateString == dStr && it.status != BookingStatus.CANCELADA }

            list.add(
                CalendarDay(
                    dateString = dStr,
                    dayOfWeekLetter = dayOfWeekFormat.format(date).take(3).uppercase(Locale.getDefault()),
                    dayOfMonthNumber = dayNumberFormat.format(date),
                    isToday = dStr == todayStr,
                    isSelected = dStr == selectedDate,
                    hasBookings = hasBook
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return list
    }

    private fun generateTimeSlots(
        dateStr: String,
        amenityId: String?,
        allBookings: List<AmenityBooking>
    ): List<TimeSlotSlot> {
        val activeBookings = allBookings.filter {
            it.dateString == dateStr &&
            it.status != BookingStatus.CANCELADA &&
            (amenityId == null || it.amenityId == amenityId)
        }

        val startDayHour = 7
        val endDayHour = 22
        val slots = mutableListOf<TimeSlotSlot>()

        for (hour in startDayHour..endDayHour) {
            val bookingAtHour = activeBookings.firstOrNull {
                hour >= it.startTimeHour && hour < it.endTimeHour
            }
            slots.add(
                TimeSlotSlot(
                    hour = hour,
                    hourLabel = String.format(Locale.getDefault(), "%02d:00", hour),
                    isAvailable = bookingAtHour == null,
                    activeBooking = bookingAtHour
                )
            )
        }
        return slots
    }
}
