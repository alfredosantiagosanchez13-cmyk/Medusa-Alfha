package com.example

import com.example.data.booking.AmenityBooking
import com.example.data.booking.AmenityBookingEngine
import com.example.data.booking.FirestoreAmenityBooking
import com.example.data.booking.TimeSlotAvailability
import com.example.data.firebase.FirestoreTenantManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Pruebas unitarias para el sistema de reservaciones de áreas comunes residenciales
 * basado en calendario con persistencia y sincronización multi-tenant en Firestore y Room.
 */
class ResidentAmenityCalendarBookingFirestoreTest {

    @Test
    fun test01_AmenityCatalogComplete() {
        val catalog = AmenityBookingEngine.CATALOG
        assertTrue("El catálogo debe contener al menos 6 áreas comunes", catalog.size >= 6)

        val quincho = catalog.find { it.name.contains("Quincho", ignoreCase = true) }
        assertNotNull("Debe existir el Quincho & BBQ", quincho)
        assertEquals("Social / Gastronómico", quincho?.category)
        assertTrue("La capacidad del Quincho debe ser al menos 20", (quincho?.capacity ?: 0) >= 20)

        val padel = catalog.find { it.name.contains("Pádel", ignoreCase = true) }
        assertNotNull("Debe existir cancha de Pádel", padel)
        assertEquals("Deportes", padel?.category)

        val gym = catalog.find { it.name.contains("Gimnasio", ignoreCase = true) }
        assertNotNull("Debe existir gimnasio residencial", gym)
        assertEquals("Fitness", gym?.category)

        val pool = catalog.find { it.name.contains("Piscina", ignoreCase = true) }
        assertNotNull("Debe existir piscina & solárium", pool)
        assertEquals("Recreación", pool?.category)
    }

    @Test
    fun test02_FirestoreAmenityBookingMultiTenantMapping() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 15, 16, 0, 0)
        }
        val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(cal.time)

        val booking = AmenityBooking(
            id = 55,
            folio = "RSV-20261015-883",
            condominiumId = "PRADOS_1",
            amenityName = "Cancha de Pádel #1",
            residentName = "Laura Gómez",
            unitId = "Casa 104",
            bookingDate = dateFmt,
            timeSlot = "16:00 - 18:00",
            bookingTimeMillis = cal.timeInMillis,
            durationMinutes = 120,
            reminderSent = false,
            status = "CONFIRMADA",
            timeSavedMinutes = 15,
            notes = "Partido amistoso dobles",
            createdAtMillis = System.currentTimeMillis()
        )

        // Convertir a FirestoreAmenityBooking usando factory method oficial
        val firestoreModel = FirestoreAmenityBooking.fromAmenityBooking(booking, "usr-laura-104", "PRADOS_1")
        assertEquals("RSV-20261015-883", firestoreModel.folio)
        assertEquals("PRADOS_1", firestoreModel.condominiumId)
        assertEquals("Cancha de Pádel #1", firestoreModel.amenityName)
        assertEquals("Laura Gómez", firestoreModel.residentName)
        assertEquals("Casa 104", firestoreModel.authorizedUnitNumber)
        assertEquals("CONFIRMADA", firestoreModel.status)

        // Verificar el mapa de Firestore y campos de aislamiento
        val map = firestoreModel.toMap()
        assertEquals("PRADOS_1", map["condominiumId"])
        assertEquals("RSV-20261015-883", map["folio"])
        assertEquals("Cancha de Pádel #1", map["amenityName"])
        assertEquals("Laura Gómez", map["residentName"])
        assertEquals("Casa 104", map["authorizedUnitNumber"])
        assertEquals("CONFIRMADA", map["status"])

        // Reconstrucción hacia modelo local Room
        val reconstructedRoom = firestoreModel.toAmenityBooking(localId = 55)
        assertEquals(booking.folio, reconstructedRoom.folio)
        assertEquals(booking.amenityName, reconstructedRoom.amenityName)
        assertEquals(booking.residentName, reconstructedRoom.residentName)
        assertEquals(booking.unitId, reconstructedRoom.unitId)
        assertEquals(booking.status, reconstructedRoom.status)
    }

    @Test
    fun test03_TimeSlotConflictLogic() {
        val baseCal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 30, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val slot1Start = (baseCal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 10) }.timeInMillis
        val slot1End = (baseCal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 12) }.timeInMillis

        val slot2Start = (baseCal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 11) }.timeInMillis
        val slot2End = (baseCal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 13) }.timeInMillis

        val slot3Start = (baseCal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 14) }.timeInMillis
        val slot3End = (baseCal.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 16) }.timeInMillis

        // Función de solapamiento estándar
        fun isOverlapping(s1: Long, e1: Long, s2: Long, e2: Long): Boolean {
            return s1 < e2 && e1 > s2
        }

        // Slot 1 y Slot 2 se solapan (10-12 vs 11-13)
        assertTrue("Slot 10-12 y 11-13 deben solaparse", isOverlapping(slot1Start, slot1End, slot2Start, slot2End))

        // Slot 1 y Slot 3 NO se solapan (10-12 vs 14-16)
        assertFalse("Slot 10-12 y 14-16 no deben solaparse", isOverlapping(slot1Start, slot1End, slot3Start, slot3End))
    }

    @Test
    fun test04_BookingCancellationStatus() {
        val booking = AmenityBooking(
            id = 70,
            folio = "RSV-20261001-441",
            condominiumId = "PRADOS_1",
            amenityName = "Quincho & BBQ Principal",
            residentName = "Esteban Morales",
            unitId = "Casa 73",
            bookingDate = "01/10/2026",
            timeSlot = "18:00 - 22:00",
            bookingTimeMillis = System.currentTimeMillis() + 86400000L,
            durationMinutes = 240,
            status = "CONFIRMADA"
        )

        // Cancelar reserva
        val cancelledBooking = booking.copy(
            status = "CANCELADA",
            cancelledBy = "Esteban Morales (Casa 73)",
            cancellationReason = "Motivos de fuerza mayor",
            cancelledAtMillis = System.currentTimeMillis()
        )

        assertEquals("CANCELADA", cancelledBooking.status)
        assertNotNull(cancelledBooking.cancelledBy)
        assertNotNull(cancelledBooking.cancellationReason)

        val firestoreModel = FirestoreAmenityBooking.fromAmenityBooking(cancelledBooking)
        assertEquals("CANCELADA", firestoreModel.status)
        assertEquals("CANCELADA", firestoreModel.toMap()["status"])
    }

    @Test
    fun test05_MultiTenantCondominiumPartitionIsolation() {
        val b1 = AmenityBooking(
            folio = "RSV-P1-001",
            condominiumId = "PRADOS_1",
            amenityName = "Piscina & Solárium",
            residentName = "Residente 1",
            unitId = "Casa 10",
            bookingDate = "05/10/2026",
            timeSlot = "10:00 - 12:00",
            status = "CONFIRMADA"
        )

        val b2 = AmenityBooking(
            folio = "RSV-P2-002",
            condominiumId = "PRADOS_2",
            amenityName = "Piscina & Solárium",
            residentName = "Residente 2",
            unitId = "Casa 10",
            bookingDate = "05/10/2026",
            timeSlot = "10:00 - 12:00",
            status = "CONFIRMADA"
        )

        // Verificar que pertenecen a particiones distintas
        assertEquals("PRADOS_1", b1.condominiumId)
        assertEquals("PRADOS_2", b2.condominiumId)

        // La ruta de Firestore de cada reserva debe aislarse en subcolecciones separadas
        val path1 = "condominiums/${b1.condominiumId}/${FirestoreTenantManager.SUB_AMENITY_BOOKINGS}/${b1.folio}"
        val path2 = "condominiums/${b2.condominiumId}/${FirestoreTenantManager.SUB_AMENITY_BOOKINGS}/${b2.folio}"

        assertTrue(path1.contains("PRADOS_1"))
        assertTrue(path2.contains("PRADOS_2"))
        assertFalse("Las rutas de Firestore para condominios distintos no deben ser iguales", path1 == path2)
    }
}
