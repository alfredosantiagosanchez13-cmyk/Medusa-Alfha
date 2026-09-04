package com.example

import com.example.data.DataRepository
import com.example.data.FirestoreBooking
import com.example.data.FirestoreResident
import com.example.data.FirestoreVisitor
import com.example.data.booking.FirestoreAmenityBooking
import com.example.data.resident.ResidentEntity
import com.example.data.visitor.FirestoreVisitorLog
import com.example.data.visitor.VisitorCheckIn
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * Pruebas unitarias para DataRepository y los esquemas de colecciones de Firestore:
 * 1. 'residents'
 * 2. 'visitors'
 * 3. 'bookings'
 */
class DataRepositoryTest {

    @Test
    fun testCollectionConstants() {
        assertEquals("residents", DataRepository.COLLECTION_RESIDENTS)
        assertEquals("visitors", DataRepository.COLLECTION_VISITORS)
        assertEquals("bookings", DataRepository.COLLECTION_BOOKINGS)
        assertEquals("condominiums", DataRepository.COLLECTION_CONDOMINIUMS)
    }

    @Test
    fun testFirestoreResident_SerializationAndMapping() {
        val now = System.currentTimeMillis()
        val resident = FirestoreResident(
            id = "RES-2026-A104",
            condominiumId = "PRADOS_1",
            fullName = "Lorena Valenzuela",
            unitId = "Torre A - Depto 104",
            email = "lorena.valenzuela@example.com",
            phone = "+56987654321",
            status = "ACTIVO",
            occupancyType = "PROPIETARIO",
            linkedUserId = "UID_FIREBASE_LORENA_001",
            emergencyContactName = "Pedro Valenzuela",
            emergencyContactPhone = "+56911223344",
            vehicles = listOf("AA-BB-12", "CC-DD-34"),
            notes = "Acceso vehicular autorizado",
            createdAtMillis = now,
            updatedAtMillis = now
        )

        // 1. Verificar serialización a Map para Firestore
        val map = resident.toMap()
        assertEquals("RES-2026-A104", map["id"])
        assertEquals("PRADOS_1", map["condominiumId"])
        assertEquals("Lorena Valenzuela", map["fullName"])
        assertEquals("Torre A - Depto 104", map["unitId"])
        assertEquals("lorena.valenzuela@example.com", map["email"])
        assertEquals("ACTIVO", map["status"])
        assertEquals("UID_FIREBASE_LORENA_001", map["linkedUserId"])

        // 2. Reconstrucción desde Map
        val fromMap = FirestoreResident.fromMap(map)
        assertEquals("RES-2026-A104", fromMap.id)
        assertEquals("PRADOS_1", fromMap.condominiumId)
        assertEquals("Lorena Valenzuela", fromMap.fullName)
        assertEquals("Torre A - Depto 104", fromMap.unitId)
        assertEquals("ACTIVO", fromMap.status)
        assertEquals("UID_FIREBASE_LORENA_001", fromMap.linkedUserId)
        assertEquals(2, fromMap.vehicles.size)

        // 3. Conversión bidireccional con ResidentEntity de Room
        val entity: ResidentEntity = resident.toResidentEntity()
        assertEquals("RES-2026-A104", entity.id)
        assertEquals("Torre A - Depto 104", entity.unitId)
        assertEquals("Lorena Valenzuela", entity.fullName)
        assertEquals("UID_FIREBASE_LORENA_001", entity.linkedUserId)

        val backToFirestore = FirestoreResident.fromResidentEntity(entity, "PRADOS_1")
        assertEquals(resident.id, backToFirestore.id)
        assertEquals(resident.fullName, backToFirestore.fullName)
        assertEquals(resident.unitId, backToFirestore.unitId)
    }

    @Test
    fun testFirestoreVisitor_SerializationAndMapping() {
        val now = System.currentTimeMillis()
        val visitor = FirestoreVisitor(
            id = "VIS-20260904-8841",
            condominiumId = "PRADOS_1",
            visitorName = "Andrés Gómez",
            authorizedUnitNumber = "Casa 102",
            visitorDocument = "17.842.119-3",
            hostResidentId = "RES-2026-001",
            hostResidentName = "Carlos Mendoza",
            visitType = "DELIVERY",
            arrivalMode = "VEHICULAR",
            vehiclePlate = "XX-YY-99",
            passCode = "QR-VIS-8841",
            status = "CHECKED_IN",
            checkInTimestamp = Timestamp(Date(now)),
            checkInMillis = now,
            checkOutTimestamp = null,
            checkOutMillis = null,
            maxEntries = 1,
            currentEntries = 1,
            notes = "Entrega de paquetería urgente",
            guardName = "Guardia Garita 1",
            createdAtMillis = now,
            updatedAtMillis = now
        )

        // 1. Serialización a Map
        val map = visitor.toMap()
        assertEquals("VIS-20260904-8841", map["id"])
        assertEquals("VIS-20260904-8841", map["folio"])
        assertEquals("Andrés Gómez", map["visitorName"])
        assertEquals("Casa 102", map["authorizedUnitNumber"])
        assertEquals("Casa 102", map["destinationHouse"])
        assertEquals("XX-YY-99", map["vehiclePlate"])
        assertEquals("CHECKED_IN", map["status"])

        // 2. Reconstrucción desde Map
        val reconstructed = FirestoreVisitor.fromMap(map)
        assertEquals("VIS-20260904-8841", reconstructed.id)
        assertEquals("Andrés Gómez", reconstructed.visitorName)
        assertEquals("Casa 102", reconstructed.authorizedUnitNumber)
        assertEquals("DELIVERY", reconstructed.visitType)
        assertEquals("VEHICULAR", reconstructed.arrivalMode)
        assertEquals("XX-YY-99", reconstructed.vehiclePlate)

        // 3. Conversión a FirestoreVisitorLog y VisitorCheckIn
        val visitorLog: FirestoreVisitorLog = visitor.toFirestoreVisitorLog()
        assertEquals("VIS-20260904-8841", visitorLog.folio)
        assertEquals("Andrés Gómez", visitorLog.visitorName)
        assertEquals("Casa 102", visitorLog.authorizedUnitNumber)

        val checkIn: VisitorCheckIn = visitor.toVisitorCheckIn()
        assertEquals("VIS-20260904-8841", checkIn.folio)
        assertEquals("Andrés Gómez", checkIn.visitorName)
        assertEquals("Casa 102", checkIn.destinationHouse)
    }

    @Test
    fun testFirestoreBooking_SerializationAndMapping() {
        val now = System.currentTimeMillis()
        val booking = FirestoreBooking(
            id = "RSV-20260904-7721",
            condominiumId = "PRADOS_1",
            amenityId = "quincho_principal",
            amenityName = "Quincho & Asador Central",
            residentId = "UID_FIREBASE_CARLOS_MENDOZA",
            residentName = "Carlos Mendoza",
            unitId = "Casa 208",
            bookingDate = "2026-09-12",
            timeSlot = "18:00 - 22:00",
            startTimestamp = Timestamp(Date(now)),
            endTimestamp = Timestamp(Date(now + 14400000L)),
            startTimeMillis = now,
            endTimeMillis = now + 14400000L,
            durationMinutes = 240,
            status = "CONFIRMADA",
            numberOfGuests = 12,
            totalCost = 15000.0,
            notes = "Celebración familiar de cumpleaños",
            createdAtMillis = now,
            updatedAtMillis = now
        )

        // 1. Serialización a Map
        val map = booking.toMap()
        assertEquals("RSV-20260904-7721", map["id"])
        assertEquals("RSV-20260904-7721", map["folio"])
        assertEquals("Quincho & Asador Central", map["amenityName"])
        assertEquals("Carlos Mendoza", map["residentName"])
        assertEquals("Casa 208", map["unitId"])
        assertEquals("UID_FIREBASE_CARLOS_MENDOZA", map["residentId"])
        assertEquals("CONFIRMADA", map["status"])
        assertEquals(12, map["numberOfGuests"])

        // 2. Reconstrucción desde Map
        val reconstructed = FirestoreBooking.fromMap(map)
        assertEquals("RSV-20260904-7721", reconstructed.id)
        assertEquals("Quincho & Asador Central", reconstructed.amenityName)
        assertEquals("Carlos Mendoza", reconstructed.residentName)
        assertEquals("Casa 208", reconstructed.unitId)
        assertEquals("2026-09-12", reconstructed.bookingDate)
        assertEquals("18:00 - 22:00", reconstructed.timeSlot)
        assertEquals(240, reconstructed.durationMinutes)

        // 3. Conversión a FirestoreAmenityBooking
        val amenityBooking: FirestoreAmenityBooking = booking.toFirestoreAmenityBooking()
        assertEquals("RSV-20260904-7721", amenityBooking.folio)
        assertEquals("Quincho & Asador Central", amenityBooking.amenityName)
        assertEquals("Carlos Mendoza", amenityBooking.residentName)
        assertEquals("Casa 208", amenityBooking.authorizedUnitNumber)

        val backFromAmenity = FirestoreBooking.fromAmenityBooking(amenityBooking)
        assertEquals(booking.id, backFromAmenity.id)
        assertEquals(booking.amenityName, backFromAmenity.amenityName)
        assertEquals(booking.residentName, backFromAmenity.residentName)
        assertEquals(booking.unitId, backFromAmenity.unitId)
    }
}
