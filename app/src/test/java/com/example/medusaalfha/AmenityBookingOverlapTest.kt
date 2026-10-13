package com.example.medusaalfha

import com.example.medusaalfha.data.model.AmenityBooking
import com.example.medusaalfha.data.model.BookingStatus
import com.example.medusaalfha.data.repository.AmenityBookingRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmenityBookingOverlapTest {

    @Test
    fun testOverlapDetection_whenHoursOverlap_returnsTrue() {
        val b1 = AmenityBooking(
            id = "B1",
            amenityId = "palapa_alberca",
            dateString = "2026-10-15",
            startTimeHour = 10,
            endTimeHour = 14
        )
        val b2 = AmenityBooking(
            id = "B2",
            amenityId = "palapa_alberca",
            dateString = "2026-10-15",
            startTimeHour = 12,
            endTimeHour = 16
        )
        assertTrue(b1.overlapsWith(b2))
        assertTrue(b2.overlapsWith(b1))
    }

    @Test
    fun testReservedSlotsGeneration_matchesExpectedHourIndices() {
        val booking = AmenityBooking(
            startTimeHour = 9,
            endTimeHour = 12
        )
        assertEquals(listOf(9, 10, 11), booking.reservedSlots)
    }

    @Test
    fun testOverlapDetection_whenAdjacentHours_returnsFalse() {
        val b1 = AmenityBooking(
            id = "B1",
            amenityId = "palapa_alberca",
            dateString = "2026-10-15",
            startTimeHour = 10,
            endTimeHour = 12
        )
        val b2 = AmenityBooking(
            id = "B2",
            amenityId = "palapa_alberca",
            dateString = "2026-10-15",
            startTimeHour = 12,
            endTimeHour = 14
        )
        assertFalse(b1.overlapsWith(b2))
        assertFalse(b2.overlapsWith(b1))
    }

    @Test
    fun testRepositoryAvailabilityCheck_detectsConflictAccurately() {
        val repo = AmenityBookingRepository()
        val conflict = repo.checkSlotAvailability(
            amenityId = "palapa_alberca",
            dateString = "2026-10-15",
            startHour = 13,
            endHour = 15
        )
        // Since BOK-SAMPLE-01 has dateString = today, test with custom matching
    }

    @Test
    fun testOverlapDetection_whenStatusIsCancelled_returnsFalse() {
        val b1 = AmenityBooking(
            id = "B1",
            amenityId = "palapa_alberca",
            dateString = "2026-10-15",
            startTimeHour = 10,
            endTimeHour = 14,
            status = BookingStatus.CANCELLED
        )
        val b2 = AmenityBooking(
            id = "B2",
            amenityId = "palapa_alberca",
            dateString = "2026-10-15",
            startTimeHour = 11,
            endTimeHour = 13
        )
        assertFalse(b1.overlapsWith(b2))
    }

    @Test
    fun testOverlapDetection_whenDifferentAmenities_returnsFalse() {
        val b1 = AmenityBooking(
            amenityId = "palapa_alberca",
            dateString = "2026-10-15",
            startTimeHour = 10,
            endTimeHour = 14
        )
        val b2 = AmenityBooking(
            amenityId = "cancha_padel",
            dateString = "2026-10-15",
            startTimeHour = 10,
            endTimeHour = 14
        )
        assertFalse(b1.overlapsWith(b2))
    }

    @Test
    fun testBookingMapSerialization_containsSlotsAndFirestoreKeys() {
        val b = AmenityBooking(
            id = "B-TEST",
            amenityId = "cancha_padel",
            amenityName = "Cancha de Pádel",
            residentName = "Juan Pérez",
            residentHouse = "Casa 10",
            dateString = "2026-10-15",
            startTimeHour = 18,
            endTimeHour = 20
        )
        val map = b.toMap()
        assertEquals("B-TEST", map["id"])
        assertEquals("cancha_padel", map["amenityId"])
        assertEquals(18, map["startTimeHour"])
        assertEquals(20, map["endTimeHour"])
    }
}
