package com.example.data

import android.util.Log
import com.example.data.booking.AmenityBooking
import com.example.data.booking.FirestoreAmenityBooking
import com.example.data.resident.ResidentEntity
import com.example.data.visitor.FirestoreVisitorLog
import com.example.data.visitor.VisitorCheckIn
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.UUID

// ============================================================================
// DATA MODELS FOR FIRESTORE COLLECTIONS
// ============================================================================

/**
 * Modelo representativo de un documento en la colección 'residents'.
 */
data class FirestoreResident(
    val id: String = "",
    val condominiumId: String = "PRADOS_1",
    val fullName: String = "",
    val unitId: String = "",
    val email: String = "",
    val phone: String = "",
    val status: String = "ACTIVO", // ACTIVO, INACTIVO, SUSPENDIDO
    val occupancyType: String = "PROPIETARIO", // PROPIETARIO, ARRENDATARIO, FAMILIAR, HABITANTE
    val linkedUserId: String = "", // Firebase Auth UID
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
    val vehicles: List<String> = emptyList(),
    val notes: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = hashMapOf(
        "id" to id,
        "condominiumId" to condominiumId,
        "fullName" to fullName,
        "unitId" to unitId,
        "email" to email,
        "phone" to phone,
        "status" to status,
        "occupancyType" to occupancyType,
        "linkedUserId" to linkedUserId,
        "emergencyContactName" to emergencyContactName,
        "emergencyContactPhone" to emergencyContactPhone,
        "vehicles" to vehicles,
        "notes" to notes,
        "createdAtMillis" to createdAtMillis,
        "updatedAtMillis" to updatedAtMillis
    )

    fun toResidentEntity(): ResidentEntity {
        return ResidentEntity(
            id = id,
            unitId = unitId,
            fullName = fullName,
            occupancyType = occupancyType,
            phone = phone,
            email = email,
            status = status,
            notes = notes,
            linkedUserId = linkedUserId,
            createdAtMillis = createdAtMillis,
            updatedAtMillis = updatedAtMillis
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>, docId: String = ""): FirestoreResident {
            val id = (map["id"] as? String)?.takeIf { it.isNotBlank() } ?: docId
            val condominiumId = (map["condominiumId"] as? String) ?: "PRADOS_1"
            val fullName = (map["fullName"] as? String) ?: ""
            val unitId = (map["unitId"] as? String) ?: (map["destinationHouse"] as? String) ?: ""
            val email = (map["email"] as? String) ?: ""
            val phone = (map["phone"] as? String) ?: ""
            val status = (map["status"] as? String) ?: "ACTIVO"
            val occupancyType = (map["occupancyType"] as? String) ?: "PROPIETARIO"
            val linkedUserId = (map["linkedUserId"] as? String) ?: (map["userId"] as? String) ?: ""
            val emergencyContactName = (map["emergencyContactName"] as? String) ?: ""
            val emergencyContactPhone = (map["emergencyContactPhone"] as? String) ?: ""
            @Suppress("UNCHECKED_CAST")
            val vehicles = (map["vehicles"] as? List<String>) ?: emptyList()
            val notes = (map["notes"] as? String) ?: ""
            val createdAt = (map["createdAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
            val updatedAt = (map["updatedAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()

            return FirestoreResident(
                id = id,
                condominiumId = condominiumId,
                fullName = fullName,
                unitId = unitId,
                email = email,
                phone = phone,
                status = status,
                occupancyType = occupancyType,
                linkedUserId = linkedUserId,
                emergencyContactName = emergencyContactName,
                emergencyContactPhone = emergencyContactPhone,
                vehicles = vehicles,
                notes = notes,
                createdAtMillis = createdAt,
                updatedAtMillis = updatedAt
            )
        }

        fun fromSnapshot(doc: DocumentSnapshot): FirestoreResident? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            return fromMap(data, doc.id)
        }

        fun fromResidentEntity(entity: ResidentEntity, condominiumId: String = "PRADOS_1"): FirestoreResident {
            return FirestoreResident(
                id = entity.id,
                condominiumId = condominiumId,
                fullName = entity.fullName,
                unitId = entity.unitId,
                email = entity.email,
                phone = entity.phone,
                status = entity.status,
                occupancyType = entity.occupancyType,
                linkedUserId = entity.linkedUserId,
                notes = entity.notes,
                createdAtMillis = entity.createdAtMillis,
                updatedAtMillis = entity.updatedAtMillis
            )
        }
    }
}

/**
 * Modelo representativo de un documento en la colección 'visitors'.
 */
data class FirestoreVisitor(
    val id: String = "",
    val condominiumId: String = "PRADOS_1",
    val visitorName: String = "",
    val authorizedUnitNumber: String = "",
    val visitorDocument: String = "",
    val hostResidentId: String = "",
    val hostResidentName: String = "",
    val visitType: String = "VISITA_PERSONAL",
    val arrivalMode: String = "PEATONAL",
    val vehiclePlate: String = "",
    val passCode: String = "",
    val status: String = "CHECKED_IN", // PENDING, CHECKED_IN, DEPARTED, DENIED
    val checkInTimestamp: Timestamp = Timestamp.now(),
    val checkInMillis: Long = System.currentTimeMillis(),
    val checkOutTimestamp: Timestamp? = null,
    val checkOutMillis: Long? = null,
    val maxEntries: Int = 1,
    val currentEntries: Int = 1,
    val notes: String = "",
    val guardName: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = hashMapOf(
        "id" to id,
        "folio" to id,
        "condominiumId" to condominiumId,
        "visitorName" to visitorName,
        "authorizedUnitNumber" to authorizedUnitNumber,
        "unitNumber" to authorizedUnitNumber,
        "destinationHouse" to authorizedUnitNumber,
        "visitorDocument" to visitorDocument,
        "hostResidentId" to hostResidentId,
        "hostResidentName" to hostResidentName,
        "visitType" to visitType,
        "passTypeLabel" to visitType,
        "arrivalMode" to arrivalMode,
        "vehiclePlate" to vehiclePlate,
        "passCode" to passCode,
        "status" to status,
        "timestamp" to checkInTimestamp,
        "checkInTimestamp" to checkInTimestamp,
        "checkInMillis" to checkInMillis,
        "timestampMillis" to checkInMillis,
        "checkOutTimestamp" to checkOutTimestamp,
        "checkOutMillis" to (checkOutMillis ?: 0L),
        "maxEntries" to maxEntries,
        "currentEntries" to currentEntries,
        "notes" to notes,
        "guardNotes" to notes,
        "guardName" to guardName,
        "createdAtMillis" to createdAtMillis,
        "updatedAtMillis" to updatedAtMillis
    )

    fun toFirestoreVisitorLog(): FirestoreVisitorLog {
        return FirestoreVisitorLog(
            folio = id,
            visitorName = visitorName,
            authorizedUnitNumber = authorizedUnitNumber,
            timestamp = checkInTimestamp,
            timestampMillis = checkInMillis,
            condominiumId = condominiumId,
            visitorDocument = visitorDocument,
            passCode = passCode,
            passTypeLabel = visitType,
            vehiclePlate = vehiclePlate.ifBlank { null },
            status = status,
            guardName = guardName.ifBlank { "Guardia Caseta" },
            guardNotes = notes.ifBlank { null },
            hostResidentName = hostResidentName,
            checkOutTimestamp = checkOutTimestamp,
            checkOutMillis = checkOutMillis
        )
    }

    fun toVisitorCheckIn(): VisitorCheckIn {
        return VisitorCheckIn(
            id = 0,
            folio = id,
            visitorName = visitorName,
            visitorDocument = visitorDocument,
            destinationHouse = authorizedUnitNumber,
            passCode = passCode,
            passTypeLabel = visitType,
            vehiclePlate = vehiclePlate,
            status = status,
            timestampMillis = checkInMillis,
            checkOutMillis = checkOutMillis,
            guardNotes = notes,
            guardName = guardName,
            hostResidentName = hostResidentName
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>, docId: String = ""): FirestoreVisitor {
            val id = (map["id"] as? String)
                ?: (map["folio"] as? String)
                ?: docId
            val condominiumId = (map["condominiumId"] as? String) ?: "PRADOS_1"
            val visitorName = (map["visitorName"] as? String) ?: ""
            val unit = (map["authorizedUnitNumber"] as? String)
                ?: (map["destinationHouse"] as? String)
                ?: (map["unitNumber"] as? String)
                ?: ""
            val visitorDoc = (map["visitorDocument"] as? String) ?: ""
            val hostId = (map["hostResidentId"] as? String) ?: (map["userId"] as? String) ?: ""
            val hostName = (map["hostResidentName"] as? String) ?: ""
            val visitType = (map["visitType"] as? String) ?: (map["passTypeLabel"] as? String) ?: "VISITA_PERSONAL"
            val arrivalMode = (map["arrivalMode"] as? String) ?: "PEATONAL"
            val vehiclePlate = (map["vehiclePlate"] as? String) ?: ""
            val passCode = (map["passCode"] as? String) ?: ""
            val status = (map["status"] as? String) ?: "CHECKED_IN"
            val checkInTs = (map["checkInTimestamp"] as? Timestamp)
                ?: (map["timestamp"] as? Timestamp)
                ?: Timestamp.now()
            val checkInMs = (map["checkInMillis"] as? Number)?.toLong()
                ?: (map["timestampMillis"] as? Number)?.toLong()
                ?: checkInTs.toDate().time
            val checkOutTs = (map["checkOutTimestamp"] as? Timestamp)
            val checkOutMs = (map["checkOutMillis"] as? Number)?.toLong()
            val maxEntries = (map["maxEntries"] as? Number)?.toInt() ?: 1
            val currentEntries = (map["currentEntries"] as? Number)?.toInt() ?: 1
            val notes = (map["notes"] as? String) ?: (map["guardNotes"] as? String) ?: ""
            val guardName = (map["guardName"] as? String) ?: ""
            val createdAt = (map["createdAtMillis"] as? Number)?.toLong() ?: checkInMs
            val updatedAt = (map["updatedAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()

            return FirestoreVisitor(
                id = id,
                condominiumId = condominiumId,
                visitorName = visitorName,
                authorizedUnitNumber = unit,
                visitorDocument = visitorDoc,
                hostResidentId = hostId,
                hostResidentName = hostName,
                visitType = visitType,
                arrivalMode = arrivalMode,
                vehiclePlate = vehiclePlate,
                passCode = passCode,
                status = status,
                checkInTimestamp = checkInTs,
                checkInMillis = checkInMs,
                checkOutTimestamp = checkOutTs,
                checkOutMillis = checkOutMs,
                maxEntries = maxEntries,
                currentEntries = currentEntries,
                notes = notes,
                guardName = guardName,
                createdAtMillis = createdAt,
                updatedAtMillis = updatedAt
            )
        }

        fun fromSnapshot(doc: DocumentSnapshot): FirestoreVisitor? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            return fromMap(data, doc.id)
        }

        fun fromVisitorLog(log: FirestoreVisitorLog): FirestoreVisitor {
            return FirestoreVisitor(
                id = log.folio,
                condominiumId = log.condominiumId,
                visitorName = log.visitorName,
                authorizedUnitNumber = log.authorizedUnitNumber,
                visitorDocument = log.visitorDocument,
                hostResidentName = log.hostResidentName,
                visitType = log.passTypeLabel,
                vehiclePlate = log.vehiclePlate ?: "",
                passCode = log.passCode,
                status = log.status,
                checkInTimestamp = log.timestamp,
                checkInMillis = log.timestampMillis,
                checkOutTimestamp = log.checkOutTimestamp,
                checkOutMillis = log.checkOutMillis,
                notes = log.guardNotes ?: log.residentNotes ?: "",
                guardName = log.guardName,
                createdAtMillis = log.timestampMillis,
                updatedAtMillis = log.syncedAtMillis
            )
        }

        fun fromVisitorCheckIn(checkIn: VisitorCheckIn, condominiumId: String = "PRADOS_1"): FirestoreVisitor {
            return FirestoreVisitor(
                id = checkIn.folio,
                condominiumId = condominiumId,
                visitorName = checkIn.visitorName,
                authorizedUnitNumber = checkIn.destinationHouse,
                visitorDocument = checkIn.visitorDocument,
                hostResidentName = checkIn.hostResidentName,
                visitType = checkIn.passTypeLabel,
                vehiclePlate = checkIn.vehiclePlate ?: "",
                passCode = checkIn.passCode,
                status = checkIn.status,
                checkInTimestamp = Timestamp(Date(checkIn.timestampMillis)),
                checkInMillis = checkIn.timestampMillis,
                checkOutTimestamp = checkIn.checkOutMillis?.let { Timestamp(Date(it)) },
                checkOutMillis = checkIn.checkOutMillis,
                notes = checkIn.guardNotes ?: "",
                guardName = checkIn.guardName,
                createdAtMillis = checkIn.timestampMillis,
                updatedAtMillis = System.currentTimeMillis()
            )
        }
    }
}

/**
 * Modelo representativo de un documento en la colección 'bookings'.
 */
data class FirestoreBooking(
    val id: String = "",
    val condominiumId: String = "PRADOS_1",
    val amenityId: String = "",
    val amenityName: String = "",
    val residentId: String = "",
    val residentName: String = "",
    val unitId: String = "",
    val bookingDate: String = "",
    val timeSlot: String = "",
    val startTimestamp: Timestamp = Timestamp.now(),
    val endTimestamp: Timestamp? = null,
    val startTimeMillis: Long = System.currentTimeMillis(),
    val endTimeMillis: Long = System.currentTimeMillis() + 7200000L,
    val durationMinutes: Int = 120,
    val status: String = "CONFIRMADA", // CONFIRMADA, PENDIENTE, EN_CURSO, COMPLETADA, CANCELADA
    val numberOfGuests: Int = 1,
    val totalCost: Double = 0.0,
    val notes: String = "",
    val cancelledBy: String? = null,
    val cancellationReason: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = hashMapOf(
        "id" to id,
        "folio" to id,
        "condominiumId" to condominiumId,
        "amenityId" to amenityId,
        "amenityName" to amenityName,
        "residentId" to residentId,
        "userId" to residentId,
        "residentName" to residentName,
        "unitId" to unitId,
        "authorizedUnitNumber" to unitId,
        "bookingDate" to bookingDate,
        "timeSlot" to timeSlot,
        "startTimestamp" to startTimestamp,
        "endTimestamp" to endTimestamp,
        "startTimeMillis" to startTimeMillis,
        "endTimeMillis" to endTimeMillis,
        "bookingTimeMillis" to startTimeMillis,
        "durationMinutes" to durationMinutes,
        "status" to status,
        "numberOfGuests" to numberOfGuests,
        "totalCost" to totalCost,
        "notes" to notes,
        "cancelledBy" to (cancelledBy ?: ""),
        "cancellationReason" to (cancellationReason ?: ""),
        "createdAtMillis" to createdAtMillis,
        "updatedAtMillis" to updatedAtMillis
    )

    fun toFirestoreAmenityBooking(): FirestoreAmenityBooking {
        return FirestoreAmenityBooking(
            folio = id,
            condominiumId = condominiumId,
            amenityName = amenityName,
            residentName = residentName,
            authorizedUnitNumber = unitId,
            userId = residentId,
            bookingDate = bookingDate,
            timeSlot = timeSlot,
            startTimestamp = startTimestamp,
            endTimestamp = endTimestamp,
            bookingTimeMillis = startTimeMillis,
            durationMinutes = durationMinutes,
            status = status,
            notes = notes,
            cancelledBy = cancelledBy,
            cancellationReason = cancellationReason,
            createdAtMillis = createdAtMillis,
            syncedAtMillis = updatedAtMillis
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>, docId: String = ""): FirestoreBooking {
            val id = (map["id"] as? String)
                ?: (map["folio"] as? String)
                ?: docId
            val condominiumId = (map["condominiumId"] as? String) ?: "PRADOS_1"
            val amenityId = (map["amenityId"] as? String) ?: ""
            val amenityName = (map["amenityName"] as? String) ?: ""
            val residentId = (map["residentId"] as? String) ?: (map["userId"] as? String) ?: ""
            val residentName = (map["residentName"] as? String) ?: ""
            val unitId = (map["unitId"] as? String) ?: (map["authorizedUnitNumber"] as? String) ?: ""
            val bookingDate = (map["bookingDate"] as? String) ?: ""
            val timeSlot = (map["timeSlot"] as? String) ?: ""
            val startTs = (map["startTimestamp"] as? Timestamp) ?: Timestamp.now()
            val endTs = (map["endTimestamp"] as? Timestamp)
            val startMs = (map["startTimeMillis"] as? Number)?.toLong()
                ?: (map["bookingTimeMillis"] as? Number)?.toLong()
                ?: startTs.toDate().time
            val endMs = (map["endTimeMillis"] as? Number)?.toLong()
                ?: endTs?.toDate()?.time
                ?: (startMs + 7200000L)
            val duration = (map["durationMinutes"] as? Number)?.toInt() ?: 120
            val status = (map["status"] as? String) ?: "CONFIRMADA"
            val numberOfGuests = (map["numberOfGuests"] as? Number)?.toInt() ?: 1
            val totalCost = (map["totalCost"] as? Number)?.toDouble() ?: 0.0
            val notes = (map["notes"] as? String) ?: ""
            val cancelledBy = map["cancelledBy"] as? String
            val cancellationReason = map["cancellationReason"] as? String
            val createdAt = (map["createdAtMillis"] as? Number)?.toLong() ?: startMs
            val updatedAt = (map["updatedAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()

            return FirestoreBooking(
                id = id,
                condominiumId = condominiumId,
                amenityId = amenityId,
                amenityName = amenityName,
                residentId = residentId,
                residentName = residentName,
                unitId = unitId,
                bookingDate = bookingDate,
                timeSlot = timeSlot,
                startTimestamp = startTs,
                endTimestamp = endTs,
                startTimeMillis = startMs,
                endTimeMillis = endMs,
                durationMinutes = duration,
                status = status,
                numberOfGuests = numberOfGuests,
                totalCost = totalCost,
                notes = notes,
                cancelledBy = cancelledBy,
                cancellationReason = cancellationReason,
                createdAtMillis = createdAt,
                updatedAtMillis = updatedAt
            )
        }

        fun fromSnapshot(doc: DocumentSnapshot): FirestoreBooking? {
            if (!doc.exists()) return null
            val data = doc.data ?: return null
            return fromMap(data, doc.id)
        }

        fun fromAmenityBooking(booking: FirestoreAmenityBooking): FirestoreBooking {
            return FirestoreBooking(
                id = booking.folio,
                condominiumId = booking.condominiumId,
                amenityId = booking.amenityName.lowercase().replace(" ", "_"),
                amenityName = booking.amenityName,
                residentId = booking.userId,
                residentName = booking.residentName,
                unitId = booking.authorizedUnitNumber,
                bookingDate = booking.bookingDate,
                timeSlot = booking.timeSlot,
                startTimestamp = booking.startTimestamp,
                endTimestamp = booking.endTimestamp,
                startTimeMillis = booking.bookingTimeMillis,
                endTimeMillis = booking.endTimestamp?.toDate()?.time ?: (booking.bookingTimeMillis + booking.durationMinutes * 60000L),
                durationMinutes = booking.durationMinutes,
                status = booking.status,
                notes = booking.notes,
                cancelledBy = booking.cancelledBy,
                cancellationReason = booking.cancellationReason,
                createdAtMillis = booking.createdAtMillis,
                updatedAtMillis = booking.syncedAtMillis
            )
        }
    }
}

/**
 * Resultado estructurado de la verificación de un código de acceso generado por residentes.
 */
data class ResidentEntryVerificationResult(
    val entryCode: String,
    val status: String, // "VALID", "EXPIRED", "ALREADY_USED", "INVALID", "CROSS_TENANT_MISMATCH"
    val isValid: Boolean,
    val visitor: FirestoreVisitor? = null,
    val resident: FirestoreResident? = null,
    val failureReason: String? = null,
    val condominiumId: String,
    val isFirestoreVerified: Boolean = true
)

// ============================================================================
// DATA REPOSITORY IMPLEMENTATION
// ============================================================================

/**
 * Repositorio de Datos usando Firebase Firestore para gestionar colecciones de:
 * 1. 'residents'
 * 2. 'visitors'
 * 3. 'bookings'
 *
 * Provee operaciones CRUD completas, consultas filtradas, observación reactiva con Flow,
 * y soporte tanto para colecciones raíz directas como para particionado multi-inquilino
 * bajo `/condominiums/{condominiumId}/...`.
 */
class DataRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    val defaultCondominiumId: String = "PRADOS_1"
) {

    companion object {
        private const val TAG = "DataRepository"
        const val COLLECTION_RESIDENTS = "residents"
        const val COLLECTION_VISITORS = "visitors"
        const val COLLECTION_BOOKINGS = "bookings"
        const val COLLECTION_CONDOMINIUMS = "condominiums"

        // Colecciones espejo para interoperabilidad en garita y amenidades
        const val SUB_VISITOR_LOGS = "visitor_logs"
        const val SUB_AMENITY_BOOKINGS = "amenity_bookings"
    }

    // ========================================================================
    // REFERENCIAS DE COLECCIONES
    // ========================================================================

    fun getResidentsCollection(condominiumId: String? = null): CollectionReference {
        val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()
        return if (condoId.isNotBlank()) {
            firestore.collection(COLLECTION_CONDOMINIUMS).document(condoId).collection(COLLECTION_RESIDENTS)
        } else {
            firestore.collection(COLLECTION_RESIDENTS)
        }
    }

    fun getRootResidentsCollection(): CollectionReference {
        return firestore.collection(COLLECTION_RESIDENTS)
    }

    fun getVisitorsCollection(condominiumId: String? = null): CollectionReference {
        val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()
        return if (condoId.isNotBlank()) {
            firestore.collection(COLLECTION_CONDOMINIUMS).document(condoId).collection(COLLECTION_VISITORS)
        } else {
            firestore.collection(COLLECTION_VISITORS)
        }
    }

    fun getRootVisitorsCollection(): CollectionReference {
        return firestore.collection(COLLECTION_VISITORS)
    }

    fun getBookingsCollection(condominiumId: String? = null): CollectionReference {
        val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()
        return if (condoId.isNotBlank()) {
            firestore.collection(COLLECTION_CONDOMINIUMS).document(condoId).collection(COLLECTION_BOOKINGS)
        } else {
            firestore.collection(COLLECTION_BOOKINGS)
        }
    }

    fun getRootBookingsCollection(): CollectionReference {
        return firestore.collection(COLLECTION_BOOKINGS)
    }

    // ========================================================================
    // 1. GESTIÓN DE COLECCIÓN 'residents'
    // ========================================================================

    /**
     * Guarda o actualiza un residente en Firestore.
     * Realiza escritura tanto en la subcolección del condominio como en la colección raíz 'residents'
     * para asegurar máxima compatibilidad de consultas.
     */
    suspend fun saveResident(
        resident: FirestoreResident,
        condominiumId: String? = null
    ): Result<FirestoreResident> = withContext(Dispatchers.IO) {
        try {
            val condoId = (condominiumId ?: resident.condominiumId.ifBlank { defaultCondominiumId }).trim().uppercase()
            val finalId = if (resident.id.isBlank()) "RES-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4).uppercase()}" else resident.id.trim()
            val model = resident.copy(id = finalId, condominiumId = condoId, updatedAtMillis = System.currentTimeMillis())
            val payload = model.toMap()

            // 1. Escritura en subcolección de condominio
            getResidentsCollection(condoId).document(finalId)
                .set(payload, SetOptions.merge())
                .await()

            // 2. Escritura en colección raíz 'residents'
            getRootResidentsCollection().document(finalId)
                .set(payload, SetOptions.merge())
                .await()

            Log.i(TAG, "Residente $finalId guardado exitosamente en colección 'residents' [Condo: $condoId]")
            Result.success(model)
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando residente: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Guarda un ResidentEntity de Room en la colección 'residents' de Firestore.
     */
    suspend fun saveResident(
        entity: ResidentEntity,
        condominiumId: String? = null
    ): Result<FirestoreResident> {
        val firestoreModel = FirestoreResident.fromResidentEntity(entity, condominiumId ?: defaultCondominiumId)
        return saveResident(firestoreModel, condominiumId)
    }

    /**
     * Obtiene un residente por su ID.
     */
    suspend fun getResidentById(
        residentId: String,
        condominiumId: String? = null
    ): Result<FirestoreResident?> = withContext(Dispatchers.IO) {
        try {
            val cleanId = residentId.trim()
            val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()

            // Buscar en subcolección de condominio
            val tenantDoc = getResidentsCollection(condoId).document(cleanId).get().await()
            if (tenantDoc.exists()) {
                val res = FirestoreResident.fromSnapshot(tenantDoc)
                return@withContext Result.success(res)
            }

            // Buscar en colección raíz 'residents'
            val rootDoc = getRootResidentsCollection().document(cleanId).get().await()
            if (rootDoc.exists()) {
                val res = FirestoreResident.fromSnapshot(rootDoc)
                return@withContext Result.success(res)
            }

            Result.success(null)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo residente $residentId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene la lista de residentes de un condominio, con filtro de estado opcional.
     */
    suspend fun getResidents(
        condominiumId: String? = null,
        status: String? = null
    ): Result<List<FirestoreResident>> = withContext(Dispatchers.IO) {
        try {
            val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()
            var query: Query = getResidentsCollection(condoId).whereEqualTo("condominiumId", condoId)

            if (!status.isNullOrBlank()) {
                query = query.whereEqualTo("status", status.trim())
            }

            val snapshot = query.get().await()
            var list = snapshot.documents.mapNotNull { FirestoreResident.fromSnapshot(it) }

            // Fallback a colección raíz si la subcolección está vacía
            if (list.isEmpty()) {
                var rootQuery: Query = getRootResidentsCollection().whereEqualTo("condominiumId", condoId)
                if (!status.isNullOrBlank()) {
                    rootQuery = rootQuery.whereEqualTo("status", status.trim())
                }
                val rootSnapshot = rootQuery.get().await()
                list = rootSnapshot.documents.mapNotNull { FirestoreResident.fromSnapshot(it) }
            }

            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error listando residentes: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Observa en tiempo real los cambios en la colección 'residents'.
     */
    fun observeResidents(condominiumId: String? = null): Flow<List<FirestoreResident>> = callbackFlow {
        val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()
        val query = getResidentsCollection(condoId).whereEqualTo("condominiumId", condoId)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Error observando colección residents: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { FirestoreResident.fromSnapshot(it) }
                trySend(list)
            }
        }

        awaitClose { registration.remove() }
    }

    /**
     * Consulta residentes por número de unidad o casa.
     */
    suspend fun getResidentsByUnit(
        unitId: String,
        condominiumId: String? = null
    ): Result<List<FirestoreResident>> = withContext(Dispatchers.IO) {
        try {
            val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()
            val cleanUnit = unitId.trim()
            val snapshot = getResidentsCollection(condoId)
                .whereEqualTo("condominiumId", condoId)
                .whereEqualTo("unitId", cleanUnit)
                .get()
                .await()

            val list = snapshot.documents.mapNotNull { FirestoreResident.fromSnapshot(it) }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo residentes de la unidad $unitId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Consulta un residente por su UID de autenticación vinculado.
     */
    suspend fun getResidentByUserId(
        userId: String,
        condominiumId: String? = null
    ): Result<FirestoreResident?> = withContext(Dispatchers.IO) {
        try {
            val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()
            val cleanUid = userId.trim()
            val snapshot = getResidentsCollection(condoId)
                .whereEqualTo("condominiumId", condoId)
                .whereEqualTo("linkedUserId", cleanUid)
                .get()
                .await()

            val doc = snapshot.documents.firstOrNull()
            Result.success(doc?.let { FirestoreResident.fromSnapshot(it) })
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo residente por userId $userId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Actualiza campos arbitrarios de un residente en 'residents'.
     */
    suspend fun updateResident(
        residentId: String,
        updates: Map<String, Any?>,
        condominiumId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanId = residentId.trim()
            val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()
            val modifiable = updates.toMutableMap()
            modifiable["updatedAtMillis"] = System.currentTimeMillis()

            getResidentsCollection(condoId).document(cleanId).update(modifiable).await()
            getRootResidentsCollection().document(cleanId).update(modifiable).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando residente $residentId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Elimina un residente (soporta soft-delete cambiando estado o eliminación física).
     */
    suspend fun deleteResident(
        residentId: String,
        softDelete: Boolean = true,
        condominiumId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanId = residentId.trim()
            val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()

            if (softDelete) {
                val updates = mapOf(
                    "status" to "BAJA_LOGICA",
                    "updatedAtMillis" to System.currentTimeMillis()
                )
                updateResident(cleanId, updates, condoId)
            } else {
                getResidentsCollection(condoId).document(cleanId).delete().await()
                getRootResidentsCollection().document(cleanId).delete().await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando residente $residentId: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ========================================================================
    // 2. GESTIÓN DE COLECCIÓN 'visitors'
    // ========================================================================

    /**
     * Guarda o actualiza un visitante en Firestore.
     * Escribe en `/condominiums/{condominiumId}/visitors/{id}`, `/visitors/{id}`,
     * y replica en `visitor_logs` para control en caseta.
     */
    suspend fun saveVisitor(
        visitor: FirestoreVisitor,
        condominiumId: String? = null
    ): Result<FirestoreVisitor> = withContext(Dispatchers.IO) {
        try {
            val condoId = (condominiumId ?: visitor.condominiumId.ifBlank { defaultCondominiumId }).trim().uppercase()
            val finalId = if (visitor.id.isBlank()) "VIS-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4).uppercase()}" else visitor.id.trim()
            val model = visitor.copy(id = finalId, condominiumId = condoId, updatedAtMillis = System.currentTimeMillis())
            val payload = model.toMap()

            // 1. Escritura en subcolección de condominio 'visitors'
            getVisitorsCollection(condoId).document(finalId)
                .set(payload, SetOptions.merge())
                .await()

            // 2. Escritura en colección raíz 'visitors'
            getRootVisitorsCollection().document(finalId)
                .set(payload, SetOptions.merge())
                .await()

            // 3. Sincronización espejo en subcolección 'visitor_logs' para guardias
            firestore.collection(COLLECTION_CONDOMINIUMS).document(condoId)
                .collection(SUB_VISITOR_LOGS).document(finalId)
                .set(payload, SetOptions.merge())
                .await()

            Log.i(TAG, "Visitante $finalId guardado exitosamente en colección 'visitors' [Condo: $condoId]")
            Result.success(model)
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando visitante: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Guarda un FirestoreVisitorLog existente adaptándolo a la colección 'visitors'.
     */
    suspend fun saveVisitor(
        log: FirestoreVisitorLog,
        condominiumId: String? = null
    ): Result<FirestoreVisitor> {
        val model = FirestoreVisitor.fromVisitorLog(log)
        return saveVisitor(model, condominiumId ?: log.condominiumId)
    }

    /**
     * Obtiene un visitante por su ID o Folio.
     */
    suspend fun getVisitorById(
        visitorId: String,
        condominiumId: String? = null
    ): Result<FirestoreVisitor?> = withContext(Dispatchers.IO) {
        try {
            val cleanId = visitorId.trim()
            val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()

            // 1. Buscar en subcolección 'visitors'
            val doc = getVisitorsCollection(condoId).document(cleanId).get().await()
            if (doc.exists()) {
                return@withContext Result.success(FirestoreVisitor.fromSnapshot(doc))
            }

            // 2. Buscar en colección raíz 'visitors'
            val rootDoc = getRootVisitorsCollection().document(cleanId).get().await()
            if (rootDoc.exists()) {
                return@withContext Result.success(FirestoreVisitor.fromSnapshot(rootDoc))
            }

            // 3. Fallback en subcolección 'visitor_logs'
            val logDoc = firestore.collection(COLLECTION_CONDOMINIUMS).document(condoId)
                .collection(SUB_VISITOR_LOGS).document(cleanId).get().await()
            if (logDoc.exists()) {
                return@withContext Result.success(FirestoreVisitor.fromSnapshot(logDoc))
            }

            Result.success(null)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo visitante $visitorId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene la lista de visitantes con filtro opcional por estado y límite.
     */
    suspend fun getVisitors(
        condominiumId: String? = null,
        status: String? = null,
        limit: Long = 100L
    ): Result<List<FirestoreVisitor>> = withContext(Dispatchers.IO) {
        try {
            val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()
            var query: Query = getVisitorsCollection(condoId).whereEqualTo("condominiumId", condoId)

            if (!status.isNullOrBlank()) {
                query = query.whereEqualTo("status", status.trim())
            }

            query = query.limit(limit)
            val snapshot = query.get().await()
            var list = snapshot.documents.mapNotNull { FirestoreVisitor.fromSnapshot(it) }

            // Fallback a 'visitor_logs' si la colección 'visitors' aún no tiene registros
            if (list.isEmpty()) {
                var fallbackQuery: Query = firestore.collection(COLLECTION_CONDOMINIUMS).document(condoId)
                    .collection(SUB_VISITOR_LOGS).whereEqualTo("condominiumId", condoId)
                if (!status.isNullOrBlank()) {
                    fallbackQuery = fallbackQuery.whereEqualTo("status", status.trim())
                }
                val fallbackSnap = fallbackQuery.limit(limit).get().await()
                list = fallbackSnap.documents.mapNotNull { FirestoreVisitor.fromSnapshot(it) }
            }

            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error listando visitantes: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Observa en tiempo real los visitantes de un condominio.
     */
    fun observeVisitors(
        condominiumId: String? = null,
        limit: Long = 100L
    ): Flow<List<FirestoreVisitor>> = callbackFlow {
        val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()
        val query = getVisitorsCollection(condoId)
            .whereEqualTo("condominiumId", condoId)
            .limit(limit)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Error observando colección visitors: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { FirestoreVisitor.fromSnapshot(it) }
                trySend(list)
            }
        }

        awaitClose { registration.remove() }
    }

    /**
     * Obtiene visitantes asociados a un residente anfitrión específico.
     */
    suspend fun getVisitorsByHost(
        hostResidentId: String,
        condominiumId: String? = null
    ): Result<List<FirestoreVisitor>> = withContext(Dispatchers.IO) {
        try {
            val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()
            val cleanHost = hostResidentId.trim()
            val snapshot = getVisitorsCollection(condoId)
                .whereEqualTo("condominiumId", condoId)
                .whereEqualTo("hostResidentId", cleanHost)
                .get()
                .await()

            val list = snapshot.documents.mapNotNull { FirestoreVisitor.fromSnapshot(it) }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo visitantes de anfitrión $hostResidentId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Actualiza el estado de un visitante (ej. marcar salida / check-out o rechazar).
     */
    suspend fun updateVisitorStatus(
        visitorId: String,
        newStatus: String,
        checkOut: Boolean = false,
        notes: String? = null,
        condominiumId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanId = visitorId.trim()
            val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()
            val nowMs = System.currentTimeMillis()

            val updates = mutableMapOf<String, Any?>(
                "status" to newStatus,
                "updatedAtMillis" to nowMs
            )

            if (checkOut) {
                updates["checkOutTimestamp"] = Timestamp.now()
                updates["checkOutMillis"] = nowMs
            }

            if (!notes.isNullOrBlank()) {
                updates["notes"] = notes
                updates["guardNotes"] = notes
            }

            getVisitorsCollection(condoId).document(cleanId).update(updates).await()
            getRootVisitorsCollection().document(cleanId).update(updates).await()
            firestore.collection(COLLECTION_CONDOMINIUMS).document(condoId)
                .collection(SUB_VISITOR_LOGS).document(cleanId).update(updates).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando estado del visitante $visitorId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Actualiza campos arbitrarios de un visitante.
     */
    suspend fun updateVisitor(
        visitorId: String,
        updates: Map<String, Any?>,
        condominiumId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanId = visitorId.trim()
            val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()
            val modifiable = updates.toMutableMap()
            modifiable["updatedAtMillis"] = System.currentTimeMillis()

            getVisitorsCollection(condoId).document(cleanId).update(modifiable).await()
            getRootVisitorsCollection().document(cleanId).update(modifiable).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando visitante $visitorId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Elimina un visitante de la colección.
     */
    suspend fun deleteVisitor(
        visitorId: String,
        condominiumId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanId = visitorId.trim()
            val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()

            getVisitorsCollection(condoId).document(cleanId).delete().await()
            getRootVisitorsCollection().document(cleanId).delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando visitante $visitorId: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ========================================================================
    // 3. GESTIÓN DE COLECCIÓN 'bookings'
    // ========================================================================

    /**
     * Guarda o actualiza una reserva en la colección 'bookings' de Firestore.
     * Escribe en `/condominiums/{condominiumId}/bookings/{id}`, `/bookings/{id}`
     * y replica en `amenity_bookings` para compatibilidad del módulo de amenidades.
     */
    suspend fun saveBooking(
        booking: FirestoreBooking,
        condominiumId: String? = null
    ): Result<FirestoreBooking> = withContext(Dispatchers.IO) {
        try {
            val condoId = (condominiumId ?: booking.condominiumId.ifBlank { defaultCondominiumId }).trim().uppercase()
            val finalId = if (booking.id.isBlank()) "RSV-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(4).uppercase()}" else booking.id.trim()
            val model = booking.copy(id = finalId, condominiumId = condoId, updatedAtMillis = System.currentTimeMillis())
            val payload = model.toMap()

            // 1. Escritura en subcolección de condominio 'bookings'
            getBookingsCollection(condoId).document(finalId)
                .set(payload, SetOptions.merge())
                .await()

            // 2. Escritura en colección raíz 'bookings'
            getRootBookingsCollection().document(finalId)
                .set(payload, SetOptions.merge())
                .await()

            // 3. Sincronización espejo en subcolección 'amenity_bookings'
            firestore.collection(COLLECTION_CONDOMINIUMS).document(condoId)
                .collection(SUB_AMENITY_BOOKINGS).document(finalId)
                .set(payload, SetOptions.merge())
                .await()

            Log.i(TAG, "Reserva $finalId guardada exitosamente en colección 'bookings' [Condo: $condoId]")
            Result.success(model)
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando reserva: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Guarda una reserva desde FirestoreAmenityBooking existente.
     */
    suspend fun saveBooking(
        booking: FirestoreAmenityBooking,
        condominiumId: String? = null
    ): Result<FirestoreBooking> {
        val model = FirestoreBooking.fromAmenityBooking(booking)
        return saveBooking(model, condominiumId ?: booking.condominiumId)
    }

    /**
     * Obtiene una reserva por su ID o Folio.
     */
    suspend fun getBookingById(
        bookingId: String,
        condominiumId: String? = null
    ): Result<FirestoreBooking?> = withContext(Dispatchers.IO) {
        try {
            val cleanId = bookingId.trim()
            val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()

            // 1. Buscar en subcolección 'bookings'
            val doc = getBookingsCollection(condoId).document(cleanId).get().await()
            if (doc.exists()) {
                return@withContext Result.success(FirestoreBooking.fromSnapshot(doc))
            }

            // 2. Buscar en colección raíz 'bookings'
            val rootDoc = getRootBookingsCollection().document(cleanId).get().await()
            if (rootDoc.exists()) {
                return@withContext Result.success(FirestoreBooking.fromSnapshot(rootDoc))
            }

            // 3. Fallback en subcolección 'amenity_bookings'
            val amenityDoc = firestore.collection(COLLECTION_CONDOMINIUMS).document(condoId)
                .collection(SUB_AMENITY_BOOKINGS).document(cleanId).get().await()
            if (amenityDoc.exists()) {
                return@withContext Result.success(FirestoreBooking.fromSnapshot(amenityDoc))
            }

            Result.success(null)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo reserva $bookingId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene la lista de reservas con filtros opcionales.
     */
    suspend fun getBookings(
        condominiumId: String? = null,
        status: String? = null,
        limit: Long = 100L
    ): Result<List<FirestoreBooking>> = withContext(Dispatchers.IO) {
        try {
            val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()
            var query: Query = getBookingsCollection(condoId).whereEqualTo("condominiumId", condoId)

            if (!status.isNullOrBlank()) {
                query = query.whereEqualTo("status", status.trim())
            }

            query = query.limit(limit)
            val snapshot = query.get().await()
            var list = snapshot.documents.mapNotNull { FirestoreBooking.fromSnapshot(it) }

            // Fallback a 'amenity_bookings' si 'bookings' está vacío
            if (list.isEmpty()) {
                var fallbackQuery: Query = firestore.collection(COLLECTION_CONDOMINIUMS).document(condoId)
                    .collection(SUB_AMENITY_BOOKINGS).whereEqualTo("condominiumId", condoId)
                if (!status.isNullOrBlank()) {
                    fallbackQuery = fallbackQuery.whereEqualTo("status", status.trim())
                }
                val fallbackSnap = fallbackQuery.limit(limit).get().await()
                list = fallbackSnap.documents.mapNotNull { FirestoreBooking.fromSnapshot(it) }
            }

            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error listando reservas: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Observa en tiempo real las reservas en la colección 'bookings'.
     */
    fun observeBookings(
        condominiumId: String? = null
    ): Flow<List<FirestoreBooking>> = callbackFlow {
        val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()
        val query = getBookingsCollection(condoId).whereEqualTo("condominiumId", condoId)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "Error observando colección bookings: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { FirestoreBooking.fromSnapshot(it) }
                trySend(list)
            }
        }

        awaitClose { registration.remove() }
    }

    /**
     * Obtiene las reservas asociadas a un residente específico (por su ID o UID).
     */
    suspend fun getBookingsByResident(
        residentId: String,
        condominiumId: String? = null
    ): Result<List<FirestoreBooking>> = withContext(Dispatchers.IO) {
        try {
            val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()
            val cleanId = residentId.trim()

            // Consulta por residentId
            val snapshot = getBookingsCollection(condoId)
                .whereEqualTo("condominiumId", condoId)
                .whereEqualTo("residentId", cleanId)
                .get()
                .await()

            val list = snapshot.documents.mapNotNull { FirestoreBooking.fromSnapshot(it) }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo reservas del residente $residentId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene reservas para una amenidad específica.
     */
    suspend fun getBookingsByAmenity(
        amenityIdOrName: String,
        condominiumId: String? = null
    ): Result<List<FirestoreBooking>> = withContext(Dispatchers.IO) {
        try {
            val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()
            val cleanAmenity = amenityIdOrName.trim()

            val snapshot = getBookingsCollection(condoId)
                .whereEqualTo("condominiumId", condoId)
                .whereEqualTo("amenityName", cleanAmenity)
                .get()
                .await()

            val list = snapshot.documents.mapNotNull { FirestoreBooking.fromSnapshot(it) }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo reservas de la amenidad $amenityIdOrName: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Cancela una reserva registrando el motivo y usuario que cancela.
     */
    suspend fun cancelBooking(
        bookingId: String,
        reason: String,
        cancelledBy: String = "RESIDENTE",
        condominiumId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanId = bookingId.trim()
            val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()
            val nowMs = System.currentTimeMillis()

            val updates = mapOf(
                "status" to "CANCELADA",
                "cancellationReason" to reason,
                "cancelledBy" to cancelledBy,
                "cancelledAtMillis" to nowMs,
                "cancelledAtTimestamp" to Timestamp.now(),
                "updatedAtMillis" to nowMs
            )

            getBookingsCollection(condoId).document(cleanId).update(updates).await()
            getRootBookingsCollection().document(cleanId).update(updates).await()
            firestore.collection(COLLECTION_CONDOMINIUMS).document(condoId)
                .collection(SUB_AMENITY_BOOKINGS).document(cleanId).update(updates).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelando reserva $bookingId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Actualiza el estado de una reserva.
     */
    suspend fun updateBookingStatus(
        bookingId: String,
        newStatus: String,
        condominiumId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanId = bookingId.trim()
            val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()
            val updates = mapOf(
                "status" to newStatus,
                "updatedAtMillis" to System.currentTimeMillis()
            )

            getBookingsCollection(condoId).document(cleanId).update(updates).await()
            getRootBookingsCollection().document(cleanId).update(updates).await()
            firestore.collection(COLLECTION_CONDOMINIUMS).document(condoId)
                .collection(SUB_AMENITY_BOOKINGS).document(cleanId).update(updates).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando estado de reserva $bookingId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Actualiza campos arbitrarios de una reserva.
     */
    suspend fun updateBooking(
        bookingId: String,
        updates: Map<String, Any?>,
        condominiumId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanId = bookingId.trim()
            val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()
            val modifiable = updates.toMutableMap()
            modifiable["updatedAtMillis"] = System.currentTimeMillis()

            getBookingsCollection(condoId).document(cleanId).update(modifiable).await()
            getRootBookingsCollection().document(cleanId).update(modifiable).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando reserva $bookingId: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Elimina una reserva de la colección.
     */
    suspend fun deleteBooking(
        bookingId: String,
        condominiumId: String? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cleanId = bookingId.trim()
            val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()

            getBookingsCollection(condoId).document(cleanId).delete().await()
            getRootBookingsCollection().document(cleanId).delete().await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error eliminando reserva $bookingId: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ========================================================================
    // VERIFICACIÓN DE CÓDIGOS DE INGRESO GENERADOS POR RESIDENTES
    // ========================================================================

    /**
     * Permite al personal de seguridad de garita verificar en tiempo real un código de acceso
     * generado por un residente, consultando las colecciones de Firestore ('qr_passes', 'visitors', 'residents')
     * y retornando los detalles completos del residente anfitrión, visitante y estado del pase.
     */
    suspend fun verifyResidentEntryCode(
        entryCode: String,
        condominiumId: String? = null
    ): Result<ResidentEntryVerificationResult> = withContext(Dispatchers.IO) {
        try {
            val cleanCode = entryCode.trim()
            val condoId = (condominiumId ?: defaultCondominiumId).trim().uppercase()

            if (cleanCode.isBlank()) {
                return@withContext Result.success(
                    ResidentEntryVerificationResult(
                        entryCode = cleanCode,
                        status = "INVALID",
                        isValid = false,
                        failureReason = "Código QR vacío o ilegible",
                        condominiumId = condoId
                    )
                )
            }

            // 1. Buscar en subcolección 'qr_passes' del condominio
            val qrPassDoc = firestore.collection(COLLECTION_CONDOMINIUMS)
                .document(condoId)
                .collection("qr_passes")
                .document(cleanCode)
                .get()
                .await()

            if (qrPassDoc.exists()) {
                val data = qrPassDoc.data ?: emptyMap()
                val passCondoId = (data["condominiumId"] as? String) ?: condoId
                val validUntil = (data["validUntilMillis"] as? Number)?.toLong() ?: 0L
                val maxEntries = (data["maxEntries"] as? Number)?.toInt() ?: 1
                val currentEntries = (data["currentEntriesCount"] as? Number)?.toInt() ?: 0
                val residentUnit = (data["destinationHouse"] as? String) ?: ""
                val hostName = (data["hostResidentName"] as? String) ?: ""
                val visitorName = (data["guestName"] as? String) ?: ""
                val vehiclePlate = (data["vehiclePlate"] as? String) ?: ""

                // Validar aislamiento multi-inquilino
                if (passCondoId.uppercase() != condoId.uppercase()) {
                    return@withContext Result.success(
                        ResidentEntryVerificationResult(
                            entryCode = cleanCode,
                            status = "CROSS_TENANT_MISMATCH",
                            isValid = false,
                            failureReason = "Pase emitido exclusivamente para $passCondoId, no para $condoId",
                            condominiumId = condoId
                        )
                    )
                }

                // Validar vigencia temporal y usos máximos
                val now = System.currentTimeMillis()
                val isExpired = validUntil in 1 until now
                val isAlreadyUsed = maxEntries in 1..currentEntries

                val status = when {
                    isExpired -> "EXPIRED"
                    isAlreadyUsed -> "ALREADY_USED"
                    else -> "VALID"
                }

                val failureReason = when {
                    isExpired -> "Código de acceso expirado"
                    isAlreadyUsed -> "Pase ya alcanzó el máximo de ingresos autorizados ($currentEntries/$maxEntries)"
                    else -> null
                }

                // Obtener datos de contacto del residente anfitrión
                var residentInfo: FirestoreResident? = null
                if (residentUnit.isNotBlank()) {
                    val residentList = getResidentsByUnit(residentUnit, condoId).getOrNull()
                    residentInfo = residentList?.firstOrNull()
                }

                val visitorObj = FirestoreVisitor(
                    id = cleanCode,
                    condominiumId = condoId,
                    visitorName = visitorName,
                    authorizedUnitNumber = residentUnit,
                    hostResidentName = hostName,
                    vehiclePlate = vehiclePlate,
                    passCode = cleanCode,
                    status = if (status == "VALID") "CHECKED_IN" else "DENIED",
                    maxEntries = maxEntries,
                    currentEntries = currentEntries
                )

                return@withContext Result.success(
                    ResidentEntryVerificationResult(
                        entryCode = cleanCode,
                        status = status,
                        isValid = status == "VALID",
                        visitor = visitorObj,
                        resident = residentInfo,
                        failureReason = failureReason,
                        condominiumId = condoId,
                        isFirestoreVerified = true
                    )
                )
            }

            // 2. Buscar en colección 'visitors' por ID de documento o campo passCode
            val visitorDoc = getVisitorById(cleanCode, condoId).getOrNull()
                ?: run {
                    val query = getVisitorsCollection(condoId)
                        .whereEqualTo("passCode", cleanCode)
                        .limit(1)
                        .get()
                        .await()
                    query.documents.firstOrNull()?.let { FirestoreVisitor.fromSnapshot(it) }
                }

            if (visitorDoc != null) {
                val isAlreadyUsed = visitorDoc.maxEntries in 1..visitorDoc.currentEntries

                var residentInfo: FirestoreResident? = null
                if (visitorDoc.hostResidentId.isNotBlank()) {
                    residentInfo = getResidentById(visitorDoc.hostResidentId, condoId).getOrNull()
                }
                if (residentInfo == null && visitorDoc.authorizedUnitNumber.isNotBlank()) {
                    val residentList = getResidentsByUnit(visitorDoc.authorizedUnitNumber, condoId).getOrNull()
                    residentInfo = residentList?.firstOrNull()
                }

                val status = if (isAlreadyUsed) "ALREADY_USED" else "VALID"
                val failureReason = if (isAlreadyUsed) "Acceso previo ya registrado para este visitante" else null

                return@withContext Result.success(
                    ResidentEntryVerificationResult(
                        entryCode = cleanCode,
                        status = status,
                        isValid = status == "VALID",
                        visitor = visitorDoc,
                        resident = residentInfo,
                        failureReason = failureReason,
                        condominiumId = condoId,
                        isFirestoreVerified = true
                    )
                )
            }

            // 3. No encontrado en Firestore
            Result.success(
                ResidentEntryVerificationResult(
                    entryCode = cleanCode,
                    status = "INVALID",
                    isValid = false,
                    failureReason = "Código no encontrado en el registro central del condominio",
                    condominiumId = condoId,
                    isFirestoreVerified = false
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error verificando código de ingreso $entryCode: ${e.message}", e)
            Result.failure(e)
        }
    }
}
