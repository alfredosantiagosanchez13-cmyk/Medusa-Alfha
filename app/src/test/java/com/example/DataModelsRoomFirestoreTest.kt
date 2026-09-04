package com.example

import com.example.data.booking.AmenityBooking
import com.example.data.booking.FirestoreAmenityBooking
import com.example.data.profile.FirestoreUserProfile
import com.example.data.profile.UserProfileEntity
import com.example.data.visitor.FirestoreVisitorLog
import com.example.data.visitor.VisitorCheckIn
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * Pruebas unitarias de integridad y conversión para los modelos de datos de:
 * 1. Visitor Logs (Registro de Visitas)
 * 2. Common Area Bookings (Reservas de Áreas Comunes)
 * 3. User Profiles (Perfiles de Usuario)
 *
 * Valida la interoperabilidad entre Room SQLite y Firebase Firestore.
 */
class DataModelsRoomFirestoreTest {

    @Test
    fun testVisitorLog_RoomAndFirestoreMapping() {
        val nowMillis = System.currentTimeMillis()
        val roomEntity = VisitorCheckIn(
            id = 101,
            folio = "FOL-VIS-9988",
            visitorName = "Mariana Silva",
            visitorDocument = "18.992.112-K",
            destinationHouse = "Casa 208",
            passCode = "QR-998877",
            passTypeLabel = "Visita Frecuente",
            vehiclePlate = "BB-CC-44",
            status = "CHECKED_IN",
            timestampMillis = nowMillis,
            checkOutMillis = null,
            guardNotes = "Ingreso autorizado con código QR",
            guardName = "Guardia Central",
            photoPath = "/photos/vis_9988.jpg",
            residentNotes = "Pasa directo",
            hostResidentName = "Carlos Mendoza"
        )

        // Convertir Room -> Firestore
        val firestoreModel = FirestoreVisitorLog.fromVisitorCheckIn(roomEntity, "PRADOS_1")
        assertEquals("FOL-VIS-9988", firestoreModel.folio)
        assertEquals("Mariana Silva", firestoreModel.visitorName)
        assertEquals("Casa 208", firestoreModel.authorizedUnitNumber)
        assertEquals(nowMillis, firestoreModel.timestampMillis)
        assertNotNull(firestoreModel.timestamp)

        // Verificar serialización a mapa de Firestore
        val map = firestoreModel.toMap()
        assertEquals("Mariana Silva", map["visitorName"])
        assertEquals("Casa 208", map["authorizedUnitNumber"])
        assertEquals("Casa 208", map["destinationHouse"])
        assertEquals("PRADOS_1", map["condominiumId"])

        // Convertir Firestore -> Room
        val backToRoom = firestoreModel.toVisitorCheckIn()
        assertEquals("FOL-VIS-9988", backToRoom.folio)
        assertEquals("Mariana Silva", backToRoom.visitorName)
        assertEquals("Casa 208", backToRoom.destinationHouse)
        assertEquals("CHECKED_IN", backToRoom.status)
    }

    @Test
    fun testCommonAreaBooking_RoomAndFirestoreMapping() {
        val nowMillis = System.currentTimeMillis()
        val roomBooking = AmenityBooking(
            id = 42,
            folio = "RSV-20260904-001",
            condominiumId = "PRADOS_1",
            amenityName = "Quincho & BBQ Principal",
            residentName = "Carlos Mendoza",
            unitId = "Casa 208",
            bookingDate = "2026-09-10",
            timeSlot = "18:00 - 20:00",
            bookingTimeMillis = nowMillis,
            durationMinutes = 120,
            reminderSent = false,
            status = "CONFIRMADA",
            notes = "Reunión familiar"
        )

        // Convertir Room -> Firestore
        val firestoreBooking = FirestoreAmenityBooking.fromAmenityBooking(
            booking = roomBooking,
            userId = "USER_FIREBASE_123"
        )
        assertEquals("RSV-20260904-001", firestoreBooking.folio)
        assertEquals("Quincho & BBQ Principal", firestoreBooking.amenityName)
        assertEquals("Casa 208", firestoreBooking.authorizedUnitNumber)
        assertEquals("USER_FIREBASE_123", firestoreBooking.userId)
        assertEquals("CONFIRMADA", firestoreBooking.status)

        // Verificar serialización a mapa de Firestore
        val map = firestoreBooking.toMap()
        assertEquals("RSV-20260904-001", map["folio"])
        assertEquals("Quincho & BBQ Principal", map["amenityName"])
        assertEquals("Casa 208", map["authorizedUnitNumber"])
        assertEquals("Casa 208", map["unitId"])
        assertEquals("USER_FIREBASE_123", map["userId"])

        // Convertir Firestore -> Room
        val backToRoom = firestoreBooking.toAmenityBooking(localId = 42)
        assertEquals("RSV-20260904-001", backToRoom.folio)
        assertEquals("Quincho & BBQ Principal", backToRoom.amenityName)
        assertEquals("Casa 208", backToRoom.unitId)
        assertEquals("CONFIRMADA", backToRoom.status)
        assertEquals(nowMillis, backToRoom.bookingTimeMillis)
    }

    @Test
    fun testUserProfile_RoomAndFirestoreMapping() {
        val now = System.currentTimeMillis()
        val roomProfile = UserProfileEntity(
            userId = "USR-AUTH-777",
            condominiumId = "PRADOS_1",
            email = "carlos.mendoza@medusa.com",
            displayName = "Carlos Mendoza",
            role = "RESIDENTE",
            authorizedUnitNumber = "Casa 208",
            phoneNumber = "+56912345678",
            photoUrl = "https://firebasestorage.googleapis.com/v0/b/medusa/avatars/777.jpg",
            occupancyType = "PROPIETARIO",
            isActive = true,
            emergencyContactName = "Laura Mendoza",
            emergencyContactPhone = "+56987654321",
            fcmToken = "fcm_mock_token_abc",
            createdAtMillis = now,
            updatedAtMillis = now
        )

        // Convertir Room -> Firestore
        val firestoreProfile = FirestoreUserProfile.fromRoomEntity(roomProfile)
        assertEquals("USR-AUTH-777", firestoreProfile.userId)
        assertEquals("carlos.mendoza@medusa.com", firestoreProfile.email)
        assertEquals("Carlos Mendoza", firestoreProfile.displayName)
        assertEquals("RESIDENTE", firestoreProfile.role)
        assertEquals("Casa 208", firestoreProfile.authorizedUnitNumber)
        assertTrue(firestoreProfile.isActive)

        // Verificar serialización a mapa
        val map = firestoreProfile.toMap()
        assertEquals("USR-AUTH-777", map["userId"])
        assertEquals("USR-AUTH-777", map["uid"])
        assertEquals("carlos.mendoza@medusa.com", map["email"])
        assertEquals("Casa 208", map["authorizedUnitNumber"])
        assertEquals("Casa 208", map["unitNumber"])
        assertEquals("PRADOS_1", map["condominiumId"])

        // Convertir Firestore -> Room
        val backToRoom = firestoreProfile.toRoomEntity()
        assertEquals("USR-AUTH-777", backToRoom.userId)
        assertEquals("carlos.mendoza@medusa.com", backToRoom.email)
        assertEquals("Carlos Mendoza", backToRoom.displayName)
        assertEquals("Casa 208", backToRoom.authorizedUnitNumber)
        assertEquals(now, backToRoom.createdAtMillis)
        assertEquals(now, backToRoom.updatedAtMillis)
    }
}
