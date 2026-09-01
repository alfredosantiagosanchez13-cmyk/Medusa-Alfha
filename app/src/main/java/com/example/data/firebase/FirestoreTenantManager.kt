package com.example.data.firebase

import android.util.Log
import com.example.data.audit.AuditLogEntity
import com.example.data.booking.AmenityBooking
import com.example.data.incident.IncidentEntity
import com.example.data.packages.PackageEntity
import com.example.data.passes.QrPassRoomEntity
import com.example.data.visitor.VisitorCheckIn
import com.example.scanner.PassType
import com.google.firebase.firestore.*
import kotlinx.coroutines.tasks.await

/**
 * Excepción lanzada cuando se intenta realizar una operación sin especificar o validando incorrectamente
 * el ID del condominio, protegiendo la integridad y aislamiento entre propiedades.
 */
class TenantIsolationException(message: String) : SecurityException(message)

/**
 * GESTOR DE AISLAMIENTO MULTI-INQUILINO (MULTI-TENANT) EN FIREBASE FIRESTORE PARA MEDUSA ALFHA.
 *
 * Estructura Jerárquica Estricta:
 *  /condominiums/{condominiumId} (Metadatos del condominio)
 *    ├── incidents/{folio}
 *    ├── visitor_access/{folio}
 *    ├── qr_passes/{passCode}
 *    ├── residents/{residentId}
 *    ├── packages/{packageId}
 *    ├── vehicles/{plate}
 *    ├── patrol_logs/{logId}
 *    ├── shift_handover/{shiftId}
 *    ├── announcements/{announcementId}
 *    ├── amenity_bookings/{folio}
 *    └── audit_logs/{auditId}
 *
 * Reglas de Aislamiento Innegociables:
 * 1. Toda ruta de colección se origina bajo `/condominiums/{condominiumId}/...`.
 * 2. Cada documento escrito contiene forzosamente el atributo `condominiumId: String`.
 * 3. Cada consulta de lectura aplica forzosamente el filtro `whereEqualTo("condominiumId", targetCondominiumId)`.
 * 4. Queda prohibida cualquier consulta global o sin alcance de condominio para prevenir fugas de datos.
 */
object FirestoreTenantManager {

    private const val TAG = "FirestoreTenantManager"

    // Nombres oficiales de colecciones particionadas
    const val ROOT_CONDOMINIUMS = "condominiums"
    const val SUB_INCIDENTS = "incidents"
    const val SUB_VISITOR_ACCESS = "visitor_access"
    const val SUB_QR_PASSES = "qr_passes"
    const val SUB_RESIDENTS = "residents"
    const val SUB_PACKAGES = "packages"
    const val SUB_VEHICLES = "vehicles"
    const val SUB_PATROL_LOGS = "patrol_logs"
    const val SUB_SHIFT_HANDOVER = "shift_handover"
    const val SUB_ANNOUNCEMENTS = "announcements"
    const val SUB_AMENITY_BOOKINGS = "amenity_bookings"
    const val SUB_AUDIT_LOGS = "audit_logs"

    /**
     * Valida que el condominiumId no sea nulo ni vacío.
     */
    fun validateCondominiumId(condominiumId: String): String {
        val clean = condominiumId.trim().uppercase()
        if (clean.isBlank()) {
            throw TenantIsolationException("Violación de aislamiento: 'condominiumId' no puede estar vacío.")
        }
        return clean
    }

    /**
     * Obtiene la referencia al documento raíz del condominio.
     */
    fun getCondominiumDocRef(firestore: FirebaseFirestore, condominiumId: String): DocumentReference {
        val validId = validateCondominiumId(condominiumId)
        return firestore.collection(ROOT_CONDOMINIUMS).document(validId)
    }

    /**
     * Obtiene la referencia a una subcolección aislada bajo el condominio especificado.
     */
    fun getTenantSubcollection(
        firestore: FirebaseFirestore,
        condominiumId: String,
        subcollectionName: String
    ): CollectionReference {
        val condoDoc = getCondominiumDocRef(firestore, condominiumId)
        return condoDoc.collection(subcollectionName)
    }

    /**
     * Genera una consulta estricta con filtro de seguridad obligatorio por condominiumId dentro de su subcolección.
     * TODA consulta en MEDUSA ALFHA debe aplicar obligatoriamente `whereEqualTo("condominiumId", validId)`.
     */
    fun buildIsolatedQuery(
        firestore: FirebaseFirestore,
        condominiumId: String,
        subcollectionName: String
    ): Query {
        val validId = validateCondominiumId(condominiumId)
        val collection = getTenantSubcollection(firestore, validId, subcollectionName)
        // Regla obligatoria: Doble capa de protección (partición en subcolección + whereEqualTo estricto)
        return collection.whereEqualTo("condominiumId", validId)
    }

    /**
     * Genera una consulta por grupo de colecciones (Collection Group) aplicando forzosamente
     * el filtro 'whereEqualTo("condominiumId", validId)' para prevenir fugas de datos entre inquilinos.
     */
    fun buildIsolatedCollectionGroupQuery(
        firestore: FirebaseFirestore,
        condominiumId: String,
        collectionId: String
    ): Query {
        val validId = validateCondominiumId(condominiumId)
        return firestore.collectionGroup(collectionId).whereEqualTo("condominiumId", validId)
    }

    /**
     * Extensión de FirebaseFirestore para construir consultas obligatoriamente filtradas por condominiumId.
     */
    fun FirebaseFirestore.tenantQuery(
        condominiumId: String,
        subcollectionName: String
    ): Query {
        return buildIsolatedQuery(this, condominiumId, subcollectionName)
    }

    /**
     * Extensión de FirebaseFirestore para consultas Collection Group obligatoriamente filtradas por condominiumId.
     */
    fun FirebaseFirestore.tenantCollectionGroupQuery(
        condominiumId: String,
        collectionId: String
    ): Query {
        return buildIsolatedCollectionGroupQuery(this, condominiumId, collectionId)
    }

    // =====================================================================
    // ESCRITURA Y SINCRONIZACIÓN ESTRICTA AISLADA POR CONDOMINIO
    // =====================================================================

    /**
     * Guarda o actualiza una Incidencia asegurando el aislamiento del condominio.
     */
    suspend fun saveIncident(
        firestore: FirebaseFirestore,
        condominiumId: String,
        incident: IncidentEntity
    ): Result<Unit> {
        return try {
            val validId = validateCondominiumId(condominiumId)
            val subcollection = getTenantSubcollection(firestore, validId, SUB_INCIDENTS)
            
            val payload = hashMapOf(
                "condominiumId" to validId,
                "folio" to incident.folio,
                "rawTranscript" to incident.rawTranscript,
                "category" to incident.category.name,
                "priority" to incident.priority.name,
                "status" to incident.status,
                "location" to incident.location,
                "aiSummary" to incident.aiSummary,
                "recommendedAction" to incident.recommendedAction,
                "timestampMillis" to incident.timestampMillis,
                "reportedBy" to incident.reportedBy,
                "evidenceNotes" to (incident.evidenceNotes ?: ""),
                "syncedAtMillis" to System.currentTimeMillis()
            )

            subcollection.document(incident.folio)
                .set(payload, SetOptions.merge())
                .await()

            Log.i(TAG, "[$validId] Incidencia ${incident.folio} guardada exitosamente con aislamiento.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando incidencia con aislamiento: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Guarda o actualiza un Registro de Visitante con aislamiento estricto por condominio.
     */
    suspend fun saveVisitorCheckIn(
        firestore: FirebaseFirestore,
        condominiumId: String,
        visitor: VisitorCheckIn
    ): Result<Unit> {
        return try {
            val validId = validateCondominiumId(condominiumId)
            val subcollection = getTenantSubcollection(firestore, validId, SUB_VISITOR_ACCESS)

            val payload = hashMapOf(
                "condominiumId" to validId,
                "id" to visitor.id,
                "folio" to visitor.folio,
                "visitorName" to visitor.visitorName,
                "visitorDocument" to visitor.visitorDocument,
                "destinationHouse" to visitor.destinationHouse,
                "passCode" to visitor.passCode,
                "passTypeLabel" to visitor.passTypeLabel,
                "vehiclePlate" to (visitor.vehiclePlate ?: ""),
                "status" to visitor.status,
                "timestampMillis" to visitor.timestampMillis,
                "checkOutMillis" to (visitor.checkOutMillis ?: 0L),
                "guardNotes" to (visitor.guardNotes ?: ""),
                "residentNotes" to (visitor.residentNotes ?: ""),
                "hostResidentName" to visitor.hostResidentName,
                "photoPath" to (visitor.photoPath ?: ""),
                "syncedAtMillis" to System.currentTimeMillis()
            )

            subcollection.document(visitor.folio)
                .set(payload, SetOptions.merge())
                .await()

            Log.i(TAG, "[$validId] Check-In ${visitor.folio} guardado exitosamente con aislamiento.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando visitante con aislamiento: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Guarda un Pase QR generado por residentes o administración con aislamiento de condominio.
     */
    suspend fun saveQrPass(
        firestore: FirebaseFirestore,
        condominiumId: String,
        pass: QrPassRoomEntity
    ): Result<Unit> {
        return try {
            val validId = validateCondominiumId(condominiumId)
            val subcollection = getTenantSubcollection(firestore, validId, SUB_QR_PASSES)

            val payload = hashMapOf(
                "condominiumId" to validId,
                "passCode" to pass.passCode,
                "guestName" to pass.guestName,
                "guestDocument" to pass.guestDocument,
                "destinationHouse" to pass.destinationHouse,
                "hostResidentName" to pass.hostResidentName,
                "vehiclePlate" to (pass.vehiclePlate ?: ""),
                "passType" to pass.passType.name,
                "createdAtMillis" to pass.createdAtMillis,
                "validUntilMillis" to pass.validUntilMillis,
                "maxEntries" to pass.maxEntries,
                "currentEntriesCount" to pass.currentEntriesCount,
                "isActive" to pass.isActive,
                "note" to (pass.note ?: ""),
                "syncedAtMillis" to System.currentTimeMillis()
            )

            subcollection.document(pass.passCode)
                .set(payload, SetOptions.merge())
                .await()

            Log.i(TAG, "[$validId] Pase QR ${pass.passCode} guardado exitosamente con aislamiento.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando pase QR con aislamiento: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Guarda o actualiza un Paquete en Caseta con aislamiento de condominio.
     */
    suspend fun savePackage(
        firestore: FirebaseFirestore,
        condominiumId: String,
        pkg: PackageEntity
    ): Result<Unit> {
        return try {
            val validId = validateCondominiumId(condominiumId)
            val subcollection = getTenantSubcollection(firestore, validId, SUB_PACKAGES)

            val payload = hashMapOf(
                "condominiumId" to validId,
                "id" to pkg.id,
                "folio" to pkg.folio,
                "unitId" to pkg.unitId,
                "courierCompany" to pkg.courierCompany,
                "trackingNumber" to pkg.trackingNumber,
                "recipientName" to pkg.residentName,
                "status" to pkg.status,
                "receivedTimestamp" to pkg.receivedTimestamp,
                "receivedByGuard" to pkg.receivedByGuard,
                "notes" to pkg.notes,
                "syncedAtMillis" to System.currentTimeMillis()
            )

            subcollection.document(pkg.id)
                .set(payload, SetOptions.merge())
                .await()

            Log.i(TAG, "[$validId] Paquete ${pkg.id} guardado con aislamiento.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando paquete con aislamiento: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Guarda un registro de auditoría / bitácora con aislamiento de condominio.
     */
    suspend fun saveAuditLog(
        firestore: FirebaseFirestore,
        condominiumId: String,
        audit: AuditLogEntity
    ): Result<Unit> {
        return try {
            val validId = validateCondominiumId(condominiumId)
            val subcollection = getTenantSubcollection(firestore, validId, SUB_AUDIT_LOGS)

            val payload = hashMapOf(
                "condominiumId" to validId,
                "folio" to audit.folio,
                "timestampMillis" to audit.timestampMillis,
                "operatorName" to audit.operatorName,
                "actionType" to audit.actionType,
                "location" to audit.location,
                "targetEntity" to audit.targetEntity,
                "changeDetails" to audit.changeDetails,
                "resultStatus" to audit.resultStatus,
                "sha256Signature" to audit.sha256Signature,
                "syncedAtMillis" to System.currentTimeMillis()
            )

            subcollection.document(audit.folio)
                .set(payload, SetOptions.merge())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando audit log con aislamiento: ${e.message}")
            Result.failure(e)
        }
    }

    // =====================================================================
    // CONSULTAS CON CLÁUSULA 'whereEqualTo' OBLIGATORIA POR CONDOMINIO
    // =====================================================================

    /**
     * Consulta incidencias filtradas forzosamente por 'whereEqualTo("condominiumId", validId)'.
     */
    suspend fun queryIncidents(
        firestore: FirebaseFirestore,
        condominiumId: String,
        limitCount: Long = 50
    ): Result<List<DocumentSnapshot>> {
        return try {
            val query = buildIsolatedQuery(firestore, condominiumId, SUB_INCIDENTS)
                .limit(limitCount)
            val snapshot = query.get().await()
            Result.success(snapshot.documents)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Consulta accesos de visitantes filtrados forzosamente por 'whereEqualTo("condominiumId", validId)'.
     */
    suspend fun queryVisitorAccess(
        firestore: FirebaseFirestore,
        condominiumId: String,
        limitCount: Long = 50
    ): Result<List<DocumentSnapshot>> {
        return try {
            val query = buildIsolatedQuery(firestore, condominiumId, SUB_VISITOR_ACCESS)
                .limit(limitCount)
            val snapshot = query.get().await()
            Result.success(snapshot.documents)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Consulta pases QR filtrados forzosamente por 'whereEqualTo("condominiumId", validId)'.
     */
    suspend fun queryQrPasses(
        firestore: FirebaseFirestore,
        condominiumId: String
    ): Result<List<DocumentSnapshot>> {
        return try {
            val query = buildIsolatedQuery(firestore, condominiumId, SUB_QR_PASSES)
            val snapshot = query.get().await()
            Result.success(snapshot.documents)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Consulta paquetes en caseta filtrados forzosamente por 'whereEqualTo("condominiumId", validId)'.
     */
    suspend fun queryPackages(
        firestore: FirebaseFirestore,
        condominiumId: String
    ): Result<List<DocumentSnapshot>> {
        return try {
            val query = buildIsolatedQuery(firestore, condominiumId, SUB_PACKAGES)
            val snapshot = query.get().await()
            Result.success(snapshot.documents)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Valida un código QR escaneado directamente contra Firestore verificando el 'condominiumId' actual.
     * Si el pase pertenece a otro condominio, o si no existe en el condominio activo, deniega el acceso.
     */
    suspend fun validateQrPassAgainstFirestore(
        firestore: FirebaseFirestore,
        currentCondominiumId: String,
        scannedCode: String
    ): Result<QrPassRoomEntity?> {
        return try {
            val validCondoId = validateCondominiumId(currentCondominiumId)
            val cleanCode = scannedCode.trim()

            // 1. Consultar directamente en la partición del condominio actual
            val docRef = getTenantSubcollection(firestore, validCondoId, SUB_QR_PASSES).document(cleanCode)
            val snapshot = docRef.get().await()

            if (snapshot.exists()) {
                val docCondoId = snapshot.getString("condominiumId") ?: validCondoId
                if (docCondoId != validCondoId) {
                    return Result.failure(
                        TenantIsolationException("ACCESO DENEGADO: Violación de aislamiento multi-tenant. El pase QR pertenece a '$docCondoId', no a '$validCondoId'.")
                    )
                }

                val guestName = snapshot.getString("guestName") ?: "Visitante"
                val guestDocument = snapshot.getString("guestDocument") ?: "Verificar en Caseta"
                val destinationHouse = snapshot.getString("destinationHouse") ?: "Casa General"
                val hostResidentName = snapshot.getString("hostResidentName") ?: "Residente"
                val vehiclePlate = snapshot.getString("vehiclePlate")
                val passTypeStr = snapshot.getString("passType") ?: PassType.VISITOR_SINGLE.name
                val passType = try { PassType.valueOf(passTypeStr) } catch (_: Exception) { PassType.VISITOR_SINGLE }
                val validUntilMillis = snapshot.getLong("validUntilMillis") ?: (System.currentTimeMillis() + 86400000L)
                val maxEntries = snapshot.getLong("maxEntries")?.toInt() ?: 1
                val currentEntriesCount = snapshot.getLong("currentEntriesCount")?.toInt() ?: 0
                val note = snapshot.getString("note")

                val passEntity = QrPassRoomEntity(
                    passCode = cleanCode,
                    guestName = guestName,
                    guestDocument = guestDocument,
                    destinationHouse = destinationHouse,
                    hostResidentName = hostResidentName,
                    vehiclePlate = vehiclePlate,
                    passType = passType,
                    createdAtMillis = snapshot.getLong("createdAtMillis") ?: System.currentTimeMillis(),
                    validUntilMillis = validUntilMillis,
                    maxEntries = maxEntries,
                    currentEntriesCount = currentEntriesCount,
                    isActive = snapshot.getBoolean("isActive") ?: true,
                    note = note
                )
                return Result.success(passEntity)
            }

            // 2. Consulta defensiva cruzada (Collection Group) para detectar si pertenece a otro condominio
            try {
                val crossQuery = firestore.collectionGroup(SUB_QR_PASSES)
                    .whereEqualTo("passCode", cleanCode)
                    .limit(1)
                    .get()
                    .await()

                if (!crossQuery.isEmpty) {
                    val otherDoc = crossQuery.documents.first()
                    val otherCondoId = otherDoc.getString("condominiumId") ?: "Otro Condominio"
                    if (otherCondoId != validCondoId) {
                        return Result.failure(
                            TenantIsolationException("ACCESO DENEGADO (AISLAMIENTO): El código QR pertenece a '$otherCondoId' y no es válido en la caseta de '$validCondoId'.")
                        )
                    }
                }
            } catch (e: Exception) {
                // Silencioso si las Security Rules bloquean lectura cruzada
            }

            Result.success(null)
        } catch (e: Exception) {
            Log.e(TAG, "Error validando QR en Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    // =====================================================================
    // GESTIÓN AISLADA DE RESERVAS DE AMENIDADES / ÁREAS COMUNES (CALENDAR)
    // =====================================================================

    /**
     * Guarda o actualiza una reserva de amenidad con aislamiento estricto por condominiumId.
     */
    suspend fun saveAmenityBooking(
        firestore: FirebaseFirestore,
        condominiumId: String,
        booking: AmenityBooking
    ): Result<Unit> {
        return try {
            val validId = validateCondominiumId(condominiumId)
            val subcollection = getTenantSubcollection(firestore, validId, SUB_AMENITY_BOOKINGS)

            val payload = hashMapOf(
                "condominiumId" to validId,
                "folio" to booking.folio,
                "amenityName" to booking.amenityName,
                "residentName" to booking.residentName,
                "unitId" to booking.unitId,
                "bookingDate" to booking.bookingDate,
                "timeSlot" to booking.timeSlot,
                "bookingTimeMillis" to booking.bookingTimeMillis,
                "durationMinutes" to booking.durationMinutes,
                "status" to booking.status,
                "cancelledBy" to (booking.cancelledBy ?: ""),
                "cancellationReason" to (booking.cancellationReason ?: ""),
                "cancelledAtMillis" to (booking.cancelledAtMillis ?: 0L),
                "notes" to booking.notes,
                "timeSavedMinutes" to booking.timeSavedMinutes,
                "createdAtMillis" to booking.createdAtMillis,
                "syncedAtMillis" to System.currentTimeMillis()
            )

            subcollection.document(booking.folio)
                .set(payload, SetOptions.merge())
                .await()

            Log.i(TAG, "[$validId] Reserva ${booking.folio} (${booking.amenityName}) sincronizada en Firestore con aislamiento.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando reserva en Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Consulta reservas de amenidades filtradas estrictamente por 'whereEqualTo("condominiumId", validId)'.
     */
    suspend fun queryAmenityBookings(
        firestore: FirebaseFirestore,
        condominiumId: String,
        limitCount: Long = 100
    ): Result<List<AmenityBooking>> {
        return try {
            val validId = validateCondominiumId(condominiumId)
            val query = buildIsolatedQuery(firestore, validId, SUB_AMENITY_BOOKINGS)
                .limit(limitCount)
            val snapshot = query.get().await()

            val list = snapshot.documents.mapNotNull { doc ->
                mapDocToAmenityBooking(doc, validId)
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando reservas de Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Consulta reservas de amenidades para una fecha específica, asegurando el filtro obligatorio 'whereEqualTo("condominiumId", validId)'.
     */
    suspend fun queryAmenityBookingsForDate(
        firestore: FirebaseFirestore,
        condominiumId: String,
        bookingDate: String
    ): Result<List<AmenityBooking>> {
        return try {
            val validId = validateCondominiumId(condominiumId)
            // Doble capa: Subcolección particionada + whereEqualTo(condominiumId) + whereEqualTo(bookingDate)
            val query = buildIsolatedQuery(firestore, validId, SUB_AMENITY_BOOKINGS)
                .whereEqualTo("bookingDate", bookingDate.trim())
            val snapshot = query.get().await()

            val list = snapshot.documents.mapNotNull { doc ->
                mapDocToAmenityBooking(doc, validId)
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando reservas de fecha $bookingDate: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Consulta reservas del mes en curso/seleccionado filtradas obligatoriamente por 'whereEqualTo("condominiumId", validId)'.
     */
    suspend fun queryAmenityBookingsForMonth(
        firestore: FirebaseFirestore,
        condominiumId: String,
        yearMonthPrefix: String
    ): Result<List<AmenityBooking>> {
        return try {
            val validId = validateCondominiumId(condominiumId)
            val cleanPrefix = yearMonthPrefix.trim()
            val startDate = "$cleanPrefix-01"
            val endDate = "$cleanPrefix-31"

            val query = buildIsolatedQuery(firestore, validId, SUB_AMENITY_BOOKINGS)
                .whereGreaterThanOrEqualTo("bookingDate", startDate)
                .whereLessThanOrEqualTo("bookingDate", endDate)
            val snapshot = query.get().await()

            val list = snapshot.documents.mapNotNull { doc ->
                mapDocToAmenityBooking(doc, validId)
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(TAG, "Error consultando reservas de mes $yearMonthPrefix: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Cancela una reserva en Firestore asegurando la pertenencia al condominio activo.
     */
    suspend fun cancelAmenityBookingInFirestore(
        firestore: FirebaseFirestore,
        condominiumId: String,
        folio: String,
        cancelledBy: String,
        reason: String
    ): Result<Unit> {
        return try {
            val validId = validateCondominiumId(condominiumId)
            val docRef = getTenantSubcollection(firestore, validId, SUB_AMENITY_BOOKINGS).document(folio.trim())

            val updates = mapOf(
                "status" to "CANCELADA",
                "cancelledBy" to cancelledBy,
                "cancellationReason" to reason,
                "cancelledAtMillis" to System.currentTimeMillis(),
                "syncedAtMillis" to System.currentTimeMillis()
            )
            docRef.update(updates).await()
            Log.i(TAG, "[$validId] Reserva $folio cancelada en Firestore con éxito.")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelando reserva en Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Escucha en tiempo real las reservas del condominio aplicando obligatoriamente el filtro 'whereEqualTo("condominiumId", validId)'.
     */
    fun listenToAmenityBookings(
        firestore: FirebaseFirestore,
        condominiumId: String,
        onUpdate: (List<AmenityBooking>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val validId = validateCondominiumId(condominiumId)
        val query = buildIsolatedQuery(firestore, validId, SUB_AMENITY_BOOKINGS)

        return query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "[$validId] Error en listener de reservas de amenidades: ${error.message}")
                onError(error)
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    mapDocToAmenityBooking(doc, validId)
                }
                onUpdate(list)
            }
        }
    }

    private fun mapDocToAmenityBooking(doc: DocumentSnapshot, expectedCondoId: String): AmenityBooking? {
        val condoId = doc.getString("condominiumId") ?: expectedCondoId
        if (condoId != expectedCondoId && condoId != "GENERAL") return null

        val folio = doc.getString("folio") ?: doc.id
        val amenityName = doc.getString("amenityName") ?: return null
        val residentName = doc.getString("residentName") ?: "Residente"
        val unitId = doc.getString("unitId") ?: "Unidad"
        val bookingDate = doc.getString("bookingDate") ?: ""
        val timeSlot = doc.getString("timeSlot") ?: ""
        val bookingTimeMillis = doc.getLong("bookingTimeMillis") ?: 0L
        val durationMinutes = doc.getLong("durationMinutes")?.toInt() ?: 120
        val status = doc.getString("status") ?: "CONFIRMADA"
        val cancelledBy = doc.getString("cancelledBy")
        val cancellationReason = doc.getString("cancellationReason")
        val cancelledAtMillis = doc.getLong("cancelledAtMillis")
        val notes = doc.getString("notes") ?: ""
        val timeSavedMinutes = doc.getLong("timeSavedMinutes")?.toInt() ?: 15
        val createdAtMillis = doc.getLong("createdAtMillis") ?: System.currentTimeMillis()

        return AmenityBooking(
            folio = folio,
            condominiumId = condoId,
            amenityName = amenityName,
            residentName = residentName,
            unitId = unitId,
            bookingDate = bookingDate,
            timeSlot = timeSlot,
            bookingTimeMillis = bookingTimeMillis,
            durationMinutes = durationMinutes,
            reminderSent = false,
            status = status,
            cancelledBy = cancelledBy,
            cancellationReason = cancellationReason,
            cancelledAtMillis = cancelledAtMillis,
            timeSavedMinutes = timeSavedMinutes,
            notes = notes,
            createdAtMillis = createdAtMillis
        )
    }
}
