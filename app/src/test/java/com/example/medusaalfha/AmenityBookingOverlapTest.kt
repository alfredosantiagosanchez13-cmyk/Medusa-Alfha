package com.example.medusaalfha

import com.example.medusaalfha.data.model.AmenityBooking
import com.example.medusaalfha.data.model.BookingStatus
import com.example.medusaalfha.data.repository.AmenityBookingRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmenityBookingOverlapTest {

    @Test
    fun testOverlapDetection_whenHoursOverlap_returnsTrue() {
        val existingBooking = AmenityBooking(
            id = "BOOK-1",
            amenityId = "ALBERCA_PALAPA",
            amenityName = "Palapa & Alberca",
            dateString = "2026-10-10",
            startTimeHour = 10,
            endTimeHour = 13,
            status = BookingStatus.CONFIRMADA
        )

        val candidateBooking = AmenityBooking(
            id = "BOOK-2",
            amenityId = "ALBERCA_PALAPA",
            amenityName = "Palapa & Alberca",
            dateString = "2026-10-10",
            startTimeHour = 12,
            endTimeHour = 15,
            status = BookingStatus.CONFIRMADA
        )

        assertTrue(
            "Debió detectar traslape entre 10-13 y 12-15 en la misma área y fecha",
            candidateBooking.overlapsWith(existingBooking)
        )
    }

    @Test
    fun testOverlapDetection_whenAdjacentHours_returnsFalse() {
        val existingBooking = AmenityBooking(
            id = "BOOK-1",
            amenityId = "ALBERCA_PALAPA",
            dateString = "2026-10-10",
            startTimeHour = 10,
            endTimeHour = 12,
            status = BookingStatus.CONFIRMADA
        )

        val candidateBooking = AmenityBooking(
            id = "BOOK-2",
            amenityId = "ALBERCA_PALAPA",
            dateString = "2026-10-10",
            startTimeHour = 12,
            endTimeHour = 14,
            status = BookingStatus.CONFIRMADA
        )

        assertFalse(
            "Horarios continuos contiguos (10-12 y 12-14) no deben considerarse traslape",
            candidateBooking.overlapsWith(existingBooking)
        )
    }

    @Test
    fun testOverlapDetection_whenDifferentAmenities_returnsFalse() {
        val bookingPalapa = AmenityBooking(
            id = "BOOK-1",
            amenityId = "ALBERCA_PALAPA",
            dateString = "2026-10-10",
            startTimeHour = 10,
            endTimeHour = 14,
            status = BookingStatus.CONFIRMADA
        )

        val bookingPadel = AmenityBooking(
            id = "BOOK-2",
            amenityId = "CANCHA_PADEL",
            dateString = "2026-10-10",
            startTimeHour = 10,
            endTimeHour = 14,
            status = BookingStatus.CONFIRMADA
        )

        assertFalse(
            "Reservas en diferentes áreas no deben presentar conflicto de traslape",
            bookingPadel.overlapsWith(bookingPalapa)
        )
    }

    @Test
    fun testOverlapDetection_whenStatusIsCancelled_returnsFalse() {
        val cancelledBooking = AmenityBooking(
            id = "BOOK-1",
            amenityId = "ALBERCA_PALAPA",
            dateString = "2026-10-10",
            startTimeHour = 10,
            endTimeHour = 14,
            status = BookingStatus.CANCELADA
        )

        val newBooking = AmenityBooking(
            id = "BOOK-2",
            amenityId = "ALBERCA_PALAPA",
            dateString = "2026-10-10",
            startTimeHour = 11,
            endTimeHour = 13,
            status = BookingStatus.CONFIRMADA
        )

        assertFalse(
            "Una reserva cancelada debe liberar el horario para nuevos residentes",
            newBooking.overlapsWith(cancelledBooking)
        )
    }

    @Test
    fun testRepositoryAvailabilityCheck_detectsConflictAccurately() {
        val repo = AmenityBookingRepository()
        val existing = listOf(
            AmenityBooking(
                id = "BOOK-A",
                amenityId = "CASA_CLUB",
                dateString = "2026-10-15",
                startTimeHour = 15,
                endTimeHour = 20,
                status = BookingStatus.CONFIRMADA
            )
        )

        val conflictingCandidate = AmenityBooking(
            id = "BOOK-B",
            amenityId = "CASA_CLUB",
            dateString = "2026-10-15",
            startTimeHour = 18,
            endTimeHour = 22,
            status = BookingStatus.CONFIRMADA
        )

        val (isAvailable, conflict) = repo.checkAvailability(conflictingCandidate, existing)
        assertFalse(isAvailable)
        assertNotNull(conflict)
        assertEquals("BOOK-A", conflict?.id)

        val nonConflictingCandidate = AmenityBooking(
            id = "BOOK-C",
            amenityId = "CASA_CLUB",
            dateString = "2026-10-15",
            startTimeHour = 10,
            endTimeHour = 14,
            status = BookingStatus.CONFIRMADA
        )

        val (isAvailableFree, conflictFree) = repo.checkAvailability(nonConflictingCandidate, existing)
        assertTrue(isAvailableFree)
        assertEquals(null, conflictFree)
    }
}
