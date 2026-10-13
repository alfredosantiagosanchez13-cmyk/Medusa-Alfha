package com.example.medusaalfha.ui.viewmodel

import androidx.lifecycle.ViewModel
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CalendarDay(
    val dateString: String,
    val dayOfMonthNumber: Int,
    val dayOfWeekLetter: String,
    val isToday: Boolean,
    val isSelected: Boolean,
    val hasBookings: Boolean = false
)

data class TimeSlotSlot(
    val hour: Int,
    val hourLabel: String,
    val isAvailable: Boolean,
    val activeBooking: AmenityBooking? = null
)

data class NewBookingFormState(
    val amenityId: String = "palapa_alberca",
    val dateString: String = "",
    val startHour: Int = 10,
    val durationHours: Int = 3,
    val guestCount: Int = 10,
    val residentName: String = "Familia González",
    val residentHouse: String = "Casa 54 · Cto. Álamos",
    val notes: String = "",
    val isCheckingAvailability: Boolean = false,
    val conflictBooking: AmenityBooking? = null
) {
    val endHour: Int get() = startHour + durationHours
}

data class BookingUiState(
    val selectedAmenityId: String = "palapa_alberca",
    val selectedDateString: String = "",
    val calendarDays: List<CalendarDay> = emptyList(),
    val allBookings: List<AmenityBooking> = emptyList(),
    val filteredBookings: List<AmenityBooking> = emptyList(),
    val timeSlots: List<TimeSlotSlot> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successNotice: String? = null,
    val isAddDialogOpen: Boolean = false,
    val selectedDetailBooking: AmenityBooking? = null
)

class AmenityBookingViewModel(
    private val repository: AmenityBookingRepository = AmenityBookingRepository(),
    private val alertRepository: ResidentAlertRepository = ResidentAlertRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookingUiState())
    val uiState: StateFlow<BookingUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(NewBookingFormState())
    val formState: StateFlow<NewBookingFormState> = _formState.asStateFlow()

    private val todayString: String

    init {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        todayString = sdf.format(Date())

        _uiState.value = _uiState.value.copy(
            selectedDateString = todayString,
            calendarDays = generateCalendarDays(todayString)
        )
        _formState.value = _formState.value.copy(dateString = todayString)

        listenToBookings()
    }

    private fun listenToBookings() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getBookingsFlow().collect { bookings ->
                _uiState.value = _uiState.value.copy(
                    allBookings = bookings,
                    isLoading = false
                )
                filterBookings()
            }
        }
    }

    fun onAmenitySelected(amenityId: String) {
        _uiState.value = _uiState.value.copy(selectedAmenityId = amenityId)
        _formState.value = _formState.value.copy(amenityId = amenityId)
        filterBookings()
    }

    fun onDateSelected(dateString: String) {
        _uiState.value = _uiState.value.copy(
            selectedDateString = dateString,
            calendarDays = generateCalendarDays(dateString)
        )
        _formState.value = _formState.value.copy(dateString = dateString)
        filterBookings()
    }

    fun onSelectTimeSlot(hour: Int) {
        val area = AmenityCatalog.getAreaById(_uiState.value.selectedAmenityId)
        val maxHour = area?.endHour ?: 22
        val duration = if (hour + 2 <= maxHour) 2 else (maxHour - hour).coerceAtLeast(1)

        _formState.value = _formState.value.copy(
            startHour = hour,
            durationHours = duration
        )
        revalidateFormConflict()
        _uiState.value = _uiState.value.copy(isAddDialogOpen = true)
    }

    fun onOpenAddDialog() {
        revalidateFormConflict()
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
        _formState.value = _formState.value.copy(amenityId = amenityId)
        revalidateFormConflict()
    }

    fun onFormDateChanged(dateStr: String) {
        _formState.value = _formState.value.copy(dateString = dateStr)
        revalidateFormConflict()
    }

    fun onFormStartHourChanged(hour: Int) {
        _formState.value = _formState.value.copy(startHour = hour)
        revalidateFormConflict()
    }

    fun onFormDurationChanged(duration: Int) {
        _formState.value = _formState.value.copy(durationHours = duration)
        revalidateFormConflict()
    }

    fun onFormResidentNameChanged(name: String) {
        _formState.value = _formState.value.copy(residentName = name)
    }

    fun onFormResidentHouseChanged(house: String) {
        _formState.value = _formState.value.copy(residentHouse = house)
    }

    fun onFormGuestCountChanged(count: Int) {
        _formState.value = _formState.value.copy(guestCount = count)
    }

    fun onFormNotesChanged(notes: String) {
        _formState.value = _formState.value.copy(notes = notes)
    }

    fun onQuickDurationSelected(hours: Int) {
        _formState.value = _formState.value.copy(durationHours = hours)
        revalidateFormConflict()
    }

    fun clearNotice() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successNotice = null)
    }

    private fun revalidateFormConflict() {
        val f = _formState.value
        val conflict = repository.checkSlotAvailability(
            amenityId = f.amenityId,
            dateString = f.dateString,
            startHour = f.startHour,
            endHour = f.endHour
        )
        _formState.value = f.copy(conflictBooking = conflict)
    }

    fun submitBooking(context: android.content.Context) {
        val form = _formState.value
        val area = AmenityCatalog.getAreaById(form.amenityId) ?: return

        val newBooking = AmenityBooking(
            id = "BOK-${System.currentTimeMillis() % 100000}",
            amenityId = form.amenityId,
            amenityName = area.name,
            residentName = form.residentName.trim(),
            residentHouse = form.residentHouse.trim(),
            dateString = form.dateString,
            startTimeHour = form.startHour,
            endTimeHour = form.endHour,
            guestCount = form.guestCount,
            status = BookingStatus.CONFIRMED,
            notes = form.notes.trim()
        )

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val result = repository.createBooking(newBooking)
            if (result.isSuccess) {
                // Notificar alerta al residente
                try {
                    alertRepository.dispatchBookingConfirmedAlert(
                        context = context,
                        amenityName = area.name,
                        destinationHouse = newBooking.residentHouse,
                        dateString = newBooking.dateString,
                        timeRange = newBooking.formattedTimeRange,
                        bookingId = newBooking.id
                    )
                } catch (_: Exception) {}

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isAddDialogOpen = false,
                    successNotice = "¡Reserva confirmada exitosamente para ${area.name}!"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Error al reservar horario."
                )
            }
        }
    }

    fun cancelBooking(bookingId: String) {
        viewModelScope.launch {
            repository.cancelBooking(bookingId)
            _uiState.value = _uiState.value.copy(
                selectedDetailBooking = null,
                successNotice = "Reserva cancelada."
            )
        }
    }

    fun seedSampleBookings() {
        viewModelScope.launch {
            repository.seedInitialBookingsIfEmpty()
        }
    }

    private fun filterBookings() {
        val state = _uiState.value
        val amenityId = state.selectedAmenityId
        val date = state.selectedDateString

        val filtered = state.allBookings.filter {
            it.amenityId == amenityId && it.dateString == date && it.status != BookingStatus.CANCELLED
        }.sortedBy { it.startTimeHour }

        val area = AmenityCatalog.getAreaById(amenityId)
        val slots = generateTimeSlots(area, date, filtered)

        _uiState.value = state.copy(
            filteredBookings = filtered,
            timeSlots = slots
        )
    }

    private fun generateTimeSlots(
        area: AmenityArea?,
        dateString: String,
        bookings: List<AmenityBooking>
    ): List<TimeSlotSlot> {
        val start = area?.startHour ?: 8
        val end = area?.endHour ?: 22

        return (start until end).map { hour ->
            val active = bookings.find { it.startTimeHour <= hour && hour < it.endTimeHour }
            TimeSlotSlot(
                hour = hour,
                hourLabel = String.format("%02d:00", hour),
                isAvailable = (active == null),
                activeBooking = active
            )
        }
    }

    private fun generateCalendarDays(selectedDate: String): List<CalendarDay> {
        val days = mutableListOf<CalendarDay>()
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dayFormat = SimpleDateFormat("d", Locale.getDefault())
        val weekFormat = SimpleDateFormat("EEEEE", Locale("es", "MX"))

        for (i in 0 until 14) {
            val dStr = sdf.format(cal.time)
            val dNum = dayFormat.format(cal.time).toIntOrNull() ?: 1
            val dLetter = weekFormat.format(cal.time).uppercase()
            days.add(
                CalendarDay(
                    dateString = dStr,
                    dayOfMonthNumber = dNum,
                    dayOfWeekLetter = dLetter,
                    isToday = (dStr == todayString),
                    isSelected = (dStr == selectedDate)
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return days
    }
}
