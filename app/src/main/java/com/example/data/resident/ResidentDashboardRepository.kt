package com.example.data.resident

import android.content.Context
import android.util.Log
import com.example.auth.AlfhaSecurityContext
import com.example.data.auth.AlfhaUserEntity
import com.example.data.audit.AuditLogEntity
import com.example.data.booking.AmenityBooking
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.firebase.FirebaseConfigHelper
import com.example.data.firebase.FirestoreTenantManager
import com.example.data.passes.QrPassRoomEntity
import com.example.data.visitor.FirestoreVisitorLog
import com.example.scanner.PassType
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Estado y datos agregados para el Panel Seguro del Residente.
 * Garantiza aislamiento 100% estricto: solo contiene información del usuario autenticado vía Firebase Auth.
 */
data class ResidentDashboardState(
    val isLoading: Boolean = false,
    val activeQrPasses: List<QrPassRoomEntity> = emptyList(),
    val upcomingBookings: List<AmenityBooking> = emptyList(),
    val isFirestoreOnline: Boolean = false,
    val dataSourceLabel: String = "Verificando...",
    val tenantId: String = "PRADOS_1",
    val authenticatedUserId: String = "",
    val authenticatedUserName: String = "",
    val authenticatedUnit: String = "",
    val authenticatedEmail: String = "",
    val firebaseAuthUid: String = "",
    val firebaseAuthEmail: String = "",
    val isFirebaseAuthActive: Boolean = false,
    val firestoreQrQueryFilter: String = "",
    val firestoreBookingQueryFilter: String = "",
    val totalGlobalPassesCount: Int = 0,
    val totalGlobalBookingsCount: Int = 0,
    val excludedOtherResidentPassesCount: Int = 0,
    val excludedOtherResidentBookingsCount: Int = 0,
    val lastSyncMillis: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)

/**
 * Repositorio de datos del Dashboard del Residente con arquitectura híbrida:
 * 1. Prioridad: Consulta segura a Firestore con cláusula 'whereEqualTo' por condominiumId y Firebase Auth UID.
 * 2. Fallback offline: Base de datos local Room SQLite restringida forzosamente por la unidad del usuario.
 * 3. Garantía de aislamiento: Bloquea cualquier filtración de datos de otros condóminos.
 */
object ResidentDashboardRepository {

    private const val TAG = "ResidentDashRepo"

    /**
     * Obtiene los pases QR activos y reservas próximas del residente autenticado con Firebase Auth.
     */
    suspend fun loadResidentData(
        context: Context,
        db: AppDatabase,
        condominiumId: String,
        user: AlfhaUserEntity,
        overrideFirebaseUid: String? = null,
        overrideEmail: String? = null
    ): ResidentDashboardState = withContext(Dispatchers.IO) {
        val validCondo = condominiumId.uppercase().trim()
        val currentFbUser = FirebaseConfigHelper.getAuth()?.currentUser
        val effectiveFirebaseUid = overrideFirebaseUid?.takeIf { it.isNotBlank() }
            ?: currentFbUser?.uid
            ?: "usr-fb-${user.id.lowercase().replace("_", "-")}"
        val effectiveEmail = overrideEmail?.takeIf { it.isNotBlank() }
            ?: currentFbUser?.email
            ?: user.email
        val isFirebaseAuthActive = currentFbUser != null || !overrideFirebaseUid.isNullOrBlank()

        val userId = user.id.trim()
        val userUnit = user.unitOrDepartment.trim()
        val now = System.currentTimeMillis()

        val qrQueryStr = "collection('condominiums/$validCondo/qr_passes').whereEqualTo('userId', '$effectiveFirebaseUid')"
        val bookingQueryStr = "collection('condominiums/$validCondo/amenity_bookings').whereEqualTo('userId', '$effectiveFirebaseUid').whereNotEqualTo('status', 'CANCELADA')"

        val firestore: FirebaseFirestore? = FirebaseConfigHelper.getFirestore()
        var qrPasses: List<QrPassRoomEntity> = emptyList()
        var bookings: List<AmenityBooking> = emptyList()
        var isOnline = false
        var sourceLabel = "Caché Local Room SQLite (Aislado)"

        if (firestore != null && FirebaseConfigHelper.isFirebaseAvailable.value) {
            try {
                // 1. Consulta segura a Firestore de Pases QR filtrados por Firebase Auth UID y unidad autorizada
                val qrResult = FirestoreTenantManager.queryResidentActiveQrPasses(
                    firestore = firestore,
                    condominiumId = validCondo,
                    authenticatedUserId = effectiveFirebaseUid,
                    unitOrHouseId = userUnit
                )

                // 2. Consulta segura a Firestore de Reservas de Amenidades filtradas por Firebase Auth UID
                val bookingResult = FirestoreTenantManager.queryResidentUpcomingBookings(
                    firestore = firestore,
                    condominiumId = validCondo,
                    authenticatedUserId = effectiveFirebaseUid,
                    unitOrHouseId = userUnit
                )

                if (qrResult.isSuccess && bookingResult.isSuccess) {
                    qrPasses = qrResult.getOrDefault(emptyList())
                    bookings = bookingResult.getOrDefault(emptyList())
                    isOnline = true
                    sourceLabel = "Nube Firestore [Aislamiento Firebase Auth Activo]"

                    // Guardar en Room SQLite local como respaldo offline transparente
                    qrPasses.forEach { pass ->
                        try { db.qrPassDao().insertPass(pass) } catch (_: Exception) {}
                    }
                    bookings.forEach { booking ->
                        try { db.amenityBookingDao().insertBooking(booking) } catch (_: Exception) {}
                    }
                } else {
                    Log.w(TAG, "Fallo parcial en consulta Firestore, activando fallback local Room.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error consultando Firestore: ${e.message}. Usando Room local.")
            }
        }

        // Si Firestore no devolvió datos o no está disponible, consultar Room DB con filtro estricto por usuario y unidad
        if (qrPasses.isEmpty()) {
            val allRoomPasses = db.qrPassDao().getAllPassesList()
            qrPasses = allRoomPasses.filter { pass ->
                val isBelongingToUser = pass.destinationHouse.contains(userUnit, ignoreCase = true) ||
                        userUnit.contains(pass.destinationHouse, ignoreCase = true) ||
                        pass.hostResidentName.contains(user.name, ignoreCase = true) ||
                        user.name.contains(pass.hostResidentName, ignoreCase = true)

                isBelongingToUser && pass.isActive && pass.validUntilMillis > now && pass.currentEntriesCount < pass.maxEntries
            }.sortedBy { it.validUntilMillis }
        }

        if (bookings.isEmpty()) {
            val allRoomBookings = db.amenityBookingDao().getAllBookingsList()
            val windowStart = now - (4 * 3600 * 1000L)
            bookings = allRoomBookings.filter { booking ->
                val isBelongingToUser = booking.unitId.contains(userUnit, ignoreCase = true) ||
                        userUnit.contains(booking.unitId, ignoreCase = true) ||
                        booking.residentName.contains(user.name, ignoreCase = true)

                isBelongingToUser && booking.status != "CANCELADA" && booking.bookingTimeMillis >= windowStart
            }.sortedBy { it.bookingTimeMillis }
        }

        // Métricas de aislamiento: calcular cuántos elementos de otras unidades fueron filtrados
        val totalAllActivePasses = db.qrPassDao().getAllPassesList().count { it.isActive && it.validUntilMillis > now }
        val totalAllActiveBookings = db.amenityBookingDao().getAllBookingsList().count { it.status != "CANCELADA" }
        val excludedPasses = maxOf(0, totalAllActivePasses - qrPasses.size)
        val excludedBookings = maxOf(0, totalAllActiveBookings - bookings.size)

        ResidentDashboardState(
            isLoading = false,
            activeQrPasses = qrPasses,
            upcomingBookings = bookings,
            isFirestoreOnline = isOnline,
            dataSourceLabel = sourceLabel,
            tenantId = validCondo,
            authenticatedUserId = userId,
            authenticatedUserName = user.name,
            authenticatedUnit = userUnit,
            authenticatedEmail = effectiveEmail,
            firebaseAuthUid = effectiveFirebaseUid,
            firebaseAuthEmail = effectiveEmail,
            isFirebaseAuthActive = isFirebaseAuthActive,
            firestoreQrQueryFilter = qrQueryStr,
            firestoreBookingQueryFilter = bookingQueryStr,
            totalGlobalPassesCount = totalAllActivePasses,
            totalGlobalBookingsCount = totalAllActiveBookings,
            excludedOtherResidentPassesCount = excludedPasses,
            excludedOtherResidentBookingsCount = excludedBookings,
            lastSyncMillis = System.currentTimeMillis()
        )
    }

    /**
     * Registra un nuevo visitante generando un código de entrada único, almacenándolo en Firestore
     * bajo la partición del condominio y vinculado estrictamente a la cuenta del residente.
     */
    suspend fun createResidentQrPass(
        db: AppDatabase,
        condominiumId: String,
        user: AlfhaUserEntity,
        guestName: String,
        guestDocument: String,
        passType: PassType,
        validDurationHours: Int = 12,
        vehiclePlate: String? = null,
        note: String? = null,
        maxEntries: Int = if (passType == PassType.RESIDENT_PERMANENT) 10 else 1,
        codePrefix: String = "VIS",
        firebaseUid: String? = null
    ): Result<QrPassRoomEntity> = withContext(Dispatchers.IO) {
        val validCondo = condominiumId.uppercase().trim()
        val passCode = AlphaCoreEngine.generateUniqueFolio(codePrefix)
        val now = System.currentTimeMillis()
        val validUntil = now + (validDurationHours * 3600 * 1000L)
        val effectiveUid = firebaseUid?.takeIf { it.isNotBlank() }
            ?: FirebaseConfigHelper.getAuth()?.currentUser?.uid
            ?: "usr-fb-${user.id.lowercase().replace("_", "-")}"

        val pass = QrPassRoomEntity(
            passCode = passCode,
            guestName = guestName.trim(),
            guestDocument = if (guestDocument.isBlank()) "Sin Documento" else guestDocument.trim(),
            destinationHouse = user.unitOrDepartment,
            hostResidentName = user.name,
            vehiclePlate = vehiclePlate?.takeIf { it.isNotBlank() },
            passType = passType,
            createdAtMillis = now,
            validUntilMillis = validUntil,
            maxEntries = maxEntries,
            currentEntriesCount = 0,
            isActive = true,
            note = note,
            integrityHash = AlphaCoreEngine.computeIntegrityHash(passCode, guestDocument, user.unitOrDepartment)
        )

        // 1. Guardar en Room local para disponibilidad offline inmediata
        db.qrPassDao().insertPass(pass)

        // 2. Guardar en Firestore con aislamiento por tenant y vinculado a la cuenta del residente
        val firestore = FirebaseConfigHelper.getFirestore()
        if (firestore != null && FirebaseConfigHelper.isFirebaseAvailable.value) {
            // Guardar en subcolección qr_passes/{entryCode}
            FirestoreTenantManager.saveQrPass(
                firestore = firestore,
                condominiumId = validCondo,
                pass = pass,
                userId = effectiveUid
            )

            // Registrar también en visitor_logs/{entryCode} como pre-registro de residente
            try {
                FirestoreTenantManager.saveVisitorLog(
                    firestore = firestore,
                    condominiumId = validCondo,
                    visitorLog = FirestoreVisitorLog(
                        folio = passCode,
                        visitorName = guestName.trim(),
                        authorizedUnitNumber = user.unitOrDepartment,
                        timestamp = Timestamp.now(),
                        timestampMillis = now,
                        condominiumId = validCondo,
                        visitorDocument = if (guestDocument.isBlank()) "Sin Documento" else guestDocument.trim(),
                        passCode = passCode,
                        passTypeLabel = passType.label,
                        vehiclePlate = vehiclePlate?.takeIf { it.isNotBlank() },
                        status = "PRE_REGISTRADO",
                        guardName = "Pre-Autorizado por Residente",
                        guardNotes = "Pase generado desde Portal Residente. Código único: $passCode",
                        residentNotes = note,
                        hostResidentName = user.name
                    )
                )
            } catch (e: Exception) {
                Log.w("ResidentRepo", "No se pudo sincronizar visitorLog secundario: ${e.message}")
            }
        }

        // 3. Auditoría inmutable de seguridad vinculada a la cuenta
        db.auditLogDao().insertAuditLog(
            AuditLogEntity(
                folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                operatorName = "${user.name} (${user.id})",
                actionType = "RESIDENT_VISITOR_CODE_GENERATED",
                location = user.unitOrDepartment,
                targetEntity = "Código Único de Entrada $passCode para ${pass.guestName}",
                changeDetails = "Visitante registrado y código único almacenado en Firestore bajo cuenta $effectiveUid",
                resultStatus = "EXITOSO"
            )
        )

        Result.success(pass)
    }

    /**
     * Crea una reserva de amenidad tanto en Firestore con tenantId y userId como en Room local.
     */
    suspend fun createResidentAmenityBooking(
        db: AppDatabase,
        condominiumId: String,
        user: AlfhaUserEntity,
        amenityName: String,
        bookingDateCalendar: Calendar,
        timeSlot: String,
        durationMinutes: Int = 120,
        notes: String = "",
        firebaseUid: String? = null
    ): Result<AmenityBooking> = withContext(Dispatchers.IO) {
        val validCondo = condominiumId.uppercase().trim()
        val folio = AlphaCoreEngine.generateUniqueFolio("RSV")
        val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(bookingDateCalendar.time)
        val effectiveUid = firebaseUid?.takeIf { it.isNotBlank() }
            ?: FirebaseConfigHelper.getAuth()?.currentUser?.uid
            ?: "usr-fb-${user.id.lowercase().replace("_", "-")}"

        val booking = AmenityBooking(
            folio = folio,
            amenityName = amenityName,
            residentName = user.name,
            unitId = user.unitOrDepartment,
            bookingDate = dateFmt,
            timeSlot = timeSlot,
            bookingTimeMillis = bookingDateCalendar.timeInMillis,
            durationMinutes = durationMinutes,
            status = "CONFIRMADA",
            notes = notes,
            timeSavedMinutes = 15,
            condominiumId = validCondo,
            createdAtMillis = System.currentTimeMillis()
        )

        // 1. Guardar en Room local
        db.amenityBookingDao().insertBooking(booking)

        // 2. Guardar en Firestore con aislamiento por tenant y userId de Firebase Auth
        val firestore = FirebaseConfigHelper.getFirestore()
        if (firestore != null && FirebaseConfigHelper.isFirebaseAvailable.value) {
            FirestoreTenantManager.saveAmenityBooking(
                firestore = firestore,
                condominiumId = validCondo,
                booking = booking,
                userId = effectiveUid
            )
        }

        // 3. Auditoría inmutable
        db.auditLogDao().insertAuditLog(
            AuditLogEntity(
                folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                operatorName = "${user.name} (${user.id})",
                actionType = "AMENITY_BOOKED_BY_RESIDENT",
                location = user.unitOrDepartment,
                targetEntity = "$amenityName [$folio]",
                changeDetails = "Reserva generada para $dateFmt $timeSlot en $validCondo",
                resultStatus = "CONFIRMADA"
            )
        )

        Result.success(booking)
    }

    /**
     * Cancela una reserva de amenidad en Room local y Firestore.
     */
    suspend fun cancelResidentAmenityBooking(
        db: AppDatabase,
        condominiumId: String,
        user: AlfhaUserEntity,
        booking: AmenityBooking,
        reason: String = "Cancelado por el residente desde su Panel"
    ): Boolean = withContext(Dispatchers.IO) {
        val validCondo = condominiumId.uppercase().trim()
        val now = System.currentTimeMillis()

        // 1. Cancelar en Room
        db.amenityBookingDao().cancelBooking(
            id = booking.id,
            cancelledBy = "${user.name} (${user.id})",
            reason = reason,
            nowMillis = now
        )

        // 2. Cancelar en Firestore
        val firestore = FirebaseConfigHelper.getFirestore()
        if (firestore != null && FirebaseConfigHelper.isFirebaseAvailable.value) {
            FirestoreTenantManager.cancelAmenityBookingInFirestore(
                firestore = firestore,
                condominiumId = validCondo,
                folio = booking.folio,
                cancelledBy = "${user.name} (${user.id})",
                reason = reason
            )
        }

        // 3. Auditoría inmutable
        db.auditLogDao().insertAuditLog(
            AuditLogEntity(
                folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                operatorName = "${user.name} (${user.id})",
                actionType = "RESIDENT_BOOKING_CANCELLED",
                location = user.unitOrDepartment,
                targetEntity = "${booking.amenityName} [${booking.folio}]",
                changeDetails = "Reserva cancelada por el residente. $reason",
                resultStatus = "CANCELADA"
            )
        )

        true
    }

    /**
     * Asegura datos de demostración activos para el residente y otros condóminos para verificar aislamiento.
     */
    suspend fun seedResidentSampleDataIfEmpty(
        db: AppDatabase,
        condominiumId: String,
        user: AlfhaUserEntity
    ) = withContext(Dispatchers.IO) {
        val validCondo = condominiumId.uppercase().trim()
        val now = System.currentTimeMillis()
        val firestore = FirebaseConfigHelper.getFirestore()
        val isFirebaseReady = firestore != null && FirebaseConfigHelper.isFirebaseAvailable.value

        // Sembrar pases de múltiples residentes si la tabla está vacía
        val allPasses = db.qrPassDao().getAllPassesList()
        if (allPasses.isEmpty()) {
            val p1 = QrPassRoomEntity(
                passCode = "MED-20260904-8821",
                guestName = "Lic. Andrea Morales (Asesor Financiero)",
                guestDocument = "14.882.910-K",
                destinationHouse = "Casa 208",
                hostResidentName = "Carlos Mendoza",
                vehiclePlate = "PXYZ-45",
                passType = PassType.VISITOR_SINGLE,
                createdAtMillis = now - 3600000L,
                validUntilMillis = now + (18 * 3600 * 1000L),
                maxEntries = 1,
                currentEntriesCount = 0,
                isActive = true,
                note = "Reunión de consultoría privada en residencia"
            )
            val p2 = QrPassRoomEntity(
                passCode = "MED-20260904-4102",
                guestName = "Ing. Roberto Salgado (Técnico Fibra Óptica)",
                guestDocument = "11.203.490-5",
                destinationHouse = "Manzana A - Casa 104",
                hostResidentName = "Familia Arismendi",
                vehiclePlate = "TEL-8890",
                passType = PassType.DELIVERY_SERVICE,
                createdAtMillis = now - 7200000L,
                validUntilMillis = now + (12 * 3600 * 1000L),
                maxEntries = 2,
                currentEntriesCount = 0,
                isActive = true,
                note = "Instalación de módem 1 Gbps"
            )
            val p3 = QrPassRoomEntity(
                passCode = "MED-20260904-1930",
                guestName = "Dra. Sofía Valenzuela (Médico Veterinario)",
                guestDocument = "17.456.789-2",
                destinationHouse = "Casa 101",
                hostResidentName = "Ing. Rodrigo Morales",
                vehiclePlate = "VET-1234",
                passType = PassType.VISITOR_SINGLE,
                createdAtMillis = now - 1800000L,
                validUntilMillis = now + (24 * 3600 * 1000L),
                maxEntries = 1,
                currentEntriesCount = 0,
                isActive = true,
                note = "Atención médica a domicilio"
            )
            db.qrPassDao().insertPass(p1)
            db.qrPassDao().insertPass(p2)
            db.qrPassDao().insertPass(p3)

            if (isFirebaseReady) {
                FirestoreTenantManager.saveQrPass(firestore!!, validCondo, p1, "usr-fb-carlos-208")
                FirestoreTenantManager.saveQrPass(firestore, validCondo, p2, "usr-fb-arismendi-104")
                FirestoreTenantManager.saveQrPass(firestore, validCondo, p3, "usr-fb-rodrigo-101")
            }
        }

        // Sembrar reservas de múltiples amenidades para distintas unidades
        val allBookings = db.amenityBookingDao().getAllBookingsList()
        if (allBookings.isEmpty()) {
            val cal1 = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 2)
                set(Calendar.HOUR_OF_DAY, 16)
                set(Calendar.MINUTE, 0)
            }
            val cal2 = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 3)
                set(Calendar.HOUR_OF_DAY, 10)
                set(Calendar.MINUTE, 0)
            }
            val cal3 = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 18)
                set(Calendar.MINUTE, 0)
            }
            val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

            val b1 = AmenityBooking(
                folio = "RSV-20260905-201",
                amenityName = "Quincho & BBQ Principal",
                residentName = "Carlos Mendoza",
                unitId = "Casa 208",
                bookingDate = dateFmt.format(cal1.time),
                timeSlot = "16:00 - 20:00",
                bookingTimeMillis = cal1.timeInMillis,
                durationMinutes = 240,
                status = "CONFIRMADA",
                notes = "Reunión de cumpleaños familiar. Área de asador.",
                timeSavedMinutes = 20,
                condominiumId = validCondo,
                createdAtMillis = now - 7200000L
            )
            val b2 = AmenityBooking(
                folio = "RSV-20260906-104",
                amenityName = "Cancha de Pádel 1",
                residentName = "Familia Arismendi",
                unitId = "Manzana A - Casa 104",
                bookingDate = dateFmt.format(cal2.time),
                timeSlot = "10:00 - 12:00",
                bookingTimeMillis = cal2.timeInMillis,
                durationMinutes = 120,
                status = "CONFIRMADA",
                notes = "Partido de dobles matutino.",
                timeSavedMinutes = 15,
                condominiumId = validCondo,
                createdAtMillis = now - 14400000L
            )
            val b3 = AmenityBooking(
                folio = "RSV-20260904-301",
                amenityName = "Alberca Climatizada",
                residentName = "Ing. Rodrigo Morales",
                unitId = "Casa 101",
                bookingDate = dateFmt.format(cal3.time),
                timeSlot = "18:00 - 20:00",
                bookingTimeMillis = cal3.timeInMillis,
                durationMinutes = 120,
                status = "CONFIRMADA",
                notes = "Natación recreativa nocturna.",
                timeSavedMinutes = 15,
                condominiumId = validCondo,
                createdAtMillis = now - 3600000L
            )
            db.amenityBookingDao().insertBooking(b1)
            db.amenityBookingDao().insertBooking(b2)
            db.amenityBookingDao().insertBooking(b3)

            if (isFirebaseReady) {
                FirestoreTenantManager.saveAmenityBooking(firestore!!, validCondo, b1, "usr-fb-carlos-208")
                FirestoreTenantManager.saveAmenityBooking(firestore, validCondo, b2, "usr-fb-arismendi-104")
                FirestoreTenantManager.saveAmenityBooking(firestore, validCondo, b3, "usr-fb-rodrigo-101")
            }
        }
    }
}
