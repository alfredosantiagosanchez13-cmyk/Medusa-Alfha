package com.example.data.booking

import android.content.Context
import com.example.auth.AlfhaSecurityContext
import com.example.data.audit.AuditLogEntity
import com.example.data.core.AlphaCoreEngine
import com.example.data.firebase.FirebaseConfigHelper
import com.example.data.firebase.FirestoreTenantManager
import com.example.data.notifications.SmartNotificationHub
import com.example.utils.AmenityReminderManager
import com.example.utils.ResidentNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AmenityCatalogItem(
    val id: String,
    val name: String,
    val category: String,
    val capacity: Int,
    val maxHoursPerBooking: Int,
    val openTime: String, // "07:00"
    val closeTime: String, // "23:00"
    val description: String,
    val rules: List<String>,
    val requiresDeposit: Boolean = false
)

data class TimeSlotAvailability(
    val slotLabel: String, // e.g. "08:00 - 10:00"
    val startMillis: Long,
    val endMillis: Long,
    val isAvailable: Boolean,
    val bookedByUnit: String? = null,
    val bookedByResident: String? = null,
    val bookingFolio: String? = null,
    val bookingId: Long? = null
)

sealed class BookingExecutionResult {
    data class Success(val booking: AmenityBooking) : BookingExecutionResult()
    data class Conflict(val message: String, val conflictingBooking: AmenityBooking) : BookingExecutionResult()
    data class Error(val message: String) : BookingExecutionResult()
}

object AmenityBookingEngine {

    val CATALOG: List<AmenityCatalogItem> = listOf(
        AmenityCatalogItem(
            id = "AMN_QUINCHO",
            name = "Quincho & BBQ Principal",
            category = "Social / Gastronómico",
            capacity = 30,
            maxHoursPerBooking = 4,
            openTime = "10:00",
            closeTime = "23:00",
            description = "Parrilla techada con área de mesas, frigobar, lavacopas e iluminación LED para eventos familiares.",
            rules = listOf(
                "Música moderada después de las 22:00 hrs",
                "Entrega del área limpia y con parrilla apagada",
                "Máximo 30 invitados autorizados"
            )
        ),
        AmenityCatalogItem(
            id = "AMN_PADEL_1",
            name = "Cancha de Pádel #1",
            category = "Deportes",
            capacity = 4,
            maxHoursPerBooking = 2,
            openTime = "07:00",
            closeTime = "22:00",
            description = "Cancha de cristal reglamentaria con césped sintético e iluminación nocturna de alta potencia.",
            rules = listOf(
                "Uso obligatorio de zapatillas deportivas",
                "Prohibido ingreso de bebidas en envase de vidrio",
                "Respetar el horario de término puntual"
            )
        ),
        AmenityCatalogItem(
            id = "AMN_PADEL_2",
            name = "Cancha de Pádel #2",
            category = "Deportes",
            capacity = 4,
            maxHoursPerBooking = 2,
            openTime = "07:00",
            closeTime = "22:00",
            description = "Segunda cancha reglamentaria para torneos y juego recreativo de residentes.",
            rules = listOf(
                "Uso exclusivo para residentes y sus invitados",
                "Llevar pelotas y palas propias"
            )
        ),
        AmenityCatalogItem(
            id = "AMN_GYM",
            name = "Gimnasio Residencial",
            category = "Fitness",
            capacity = 12,
            maxHoursPerBooking = 2,
            openTime = "06:00",
            closeTime = "23:00",
            description = "Equipamiento cardiovascular, máquinas de fuerza y zona de pesas libres con aire acondicionado.",
            rules = listOf(
                "Uso de toalla individual obligatorio",
                "Sanitizar máquinas al terminar su uso",
                "Edad mínima 14 años sin tutor"
            )
        ),
        AmenityCatalogItem(
            id = "AMN_POOL",
            name = "Piscina & Solárium",
            category = "Recreación",
            capacity = 40,
            maxHoursPerBooking = 3,
            openTime = "09:00",
            closeTime = "20:00",
            description = "Piscina para adultos y niños, reposeras, duchas exteriores y área de descanso sombreada.",
            rules = listOf(
                "Ducha obligatoria antes de ingresar",
                "Menores de 12 años deben estar con un adulto",
                "Prohibido envases de vidrio y comida en el perímetro"
            )
        ),
        AmenityCatalogItem(
            id = "AMN_EVENTS",
            name = "Sala Multiuso & Eventos",
            category = "Eventos",
            capacity = 50,
            maxHoursPerBooking = 5,
            openTime = "10:00",
            closeTime = "00:00",
            description = "Salón climatizado para cumpleaños, celebraciones y reuniones vecinales con cocina equipada.",
            rules = listOf(
                "Requiere confirmación previa de lista de invitados",
                "Cierre estricto a las 00:00 hrs"
            )
        ),
        AmenityCatalogItem(
            id = "AMN_COWORK",
            name = "Coworking & Business Center",
            category = "Trabajo",
            capacity = 15,
            maxHoursPerBooking = 4,
            openTime = "07:00",
            closeTime = "22:00",
            description = "Espacio silencioso con WiFi de alta velocidad, escritorios ergonómicos y sala de reuniones privadas.",
            rules = listOf(
                "Mantener llamadas en cabinas o tono bajo",
                "Uso de auriculares para audio"
            )
        )
    )

    /**
     * Genera los slots estándar del día para una amenidad y calcula su disponibilidad en tiempo real consultando Room DB y tenant.
     */
    suspend fun getDailyAvailability(
        db: AppDatabase,
        amenityName: String,
        targetDateCalendar: Calendar = Calendar.getInstance(),
        condominiumId: String = "PRADOS_1"
    ): List<TimeSlotAvailability> = withContext(Dispatchers.IO) {
        val cal = targetDateCalendar.clone() as Calendar
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis
        val dayEnd = dayStart + (24 * 3600 * 1000L)
        val validCondo = condominiumId.uppercase().trim()

        // Traer todas las reservas activas para esta amenidad y condominio
        val existingBookings = db.amenityBookingDao().getAllBookingsList()
            .filter { 
                it.amenityName == amenityName && 
                it.status != "CANCELADA" && 
                (it.condominiumId.uppercase() == validCondo || it.condominiumId == "GENERAL") &&
                it.bookingTimeMillis in dayStart..dayEnd 
            }

        val slotHours = listOf(
            8 to 10,
            10 to 12,
            12 to 14,
            14 to 16,
            16 to 18,
            18 to 20,
            20 to 22
        )

        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        slotHours.map { (startHour, endHour) ->
            val slotCalStart = cal.clone() as Calendar
            slotCalStart.set(Calendar.HOUR_OF_DAY, startHour)
            slotCalStart.set(Calendar.MINUTE, 0)
            val slotStartMillis = slotCalStart.timeInMillis

            val slotCalEnd = cal.clone() as Calendar
            slotCalEnd.set(Calendar.HOUR_OF_DAY, endHour)
            slotCalEnd.set(Calendar.MINUTE, 0)
            val slotEndMillis = slotCalEnd.timeInMillis

            val slotLabel = "${dateFormat.format(Date(slotStartMillis))} - ${dateFormat.format(Date(slotEndMillis))}"

            // Verificar si hay conflicto de horario
            val conflicting = existingBookings.firstOrNull { b ->
                val bStart = b.bookingTimeMillis
                val bEnd = bStart + (b.durationMinutes * 60 * 1000L)
                (bStart < slotEndMillis && bEnd > slotStartMillis)
            }

            if (conflicting != null) {
                TimeSlotAvailability(
                    slotLabel = slotLabel,
                    startMillis = slotStartMillis,
                    endMillis = slotEndMillis,
                    isAvailable = false,
                    bookedByUnit = conflicting.unitId,
                    bookedByResident = conflicting.residentName,
                    bookingFolio = conflicting.folio,
                    bookingId = conflicting.id
                )
            } else {
                TimeSlotAvailability(
                    slotLabel = slotLabel,
                    startMillis = slotStartMillis,
                    endMillis = slotEndMillis,
                    isAvailable = true
                )
            }
        }
    }

    /**
     * Valida conflicto y ejecuta la reserva en 1 toque.
     * Genera Folio único, registra en Room DB, emite auditoría inmutable, registra Tiempo Devuelto y dispara notificaciones y sync a Firestore con aislamiento de condominiumId.
     */
    suspend fun executeOneTapBooking(
        context: Context,
        db: AppDatabase,
        amenityName: String,
        residentName: String,
        unitId: String,
        startMillis: Long,
        durationMinutes: Int = 120,
        notes: String = "",
        operatorName: String = residentName,
        condominiumId: String = "PRADOS_1"
    ): BookingExecutionResult = withContext(Dispatchers.IO) {
        val endMillis = startMillis + (durationMinutes * 60 * 1000L)
        val validCondo = condominiumId.uppercase().trim()

        // 1. Bloqueo automático de conflictos
        val conflicts = db.amenityBookingDao().findConflictingBookingsWithTenant(
            condominiumId = validCondo,
            amenityName = amenityName,
            newStartTime = startMillis,
            newEndTime = endMillis
        )

        if (conflicts.isNotEmpty()) {
            val conflict = conflicts.first()
            val format = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            val cTime = format.format(Date(conflict.bookingTimeMillis))
            return@withContext BookingExecutionResult.Conflict(
                message = "El horario seleccionado entra en conflicto con la reserva ${conflict.folio} de ${conflict.unitId} (${conflict.residentName}) programada para $cTime.",
                conflictingBooking = conflict
            )
        }

        // 2. Generar Folio Único
        val uniqueFolio = AlphaCoreEngine.generateUniqueFolio("RSV")
        val dateFormatted = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(startMillis))
        val timeSlotFormatted = "${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startMillis))} - ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(endMillis))}"

        val newBooking = AmenityBooking(
            folio = uniqueFolio,
            condominiumId = validCondo,
            amenityName = amenityName,
            residentName = residentName,
            unitId = unitId,
            bookingDate = dateFormatted,
            timeSlot = timeSlotFormatted,
            bookingTimeMillis = startMillis,
            durationMinutes = durationMinutes,
            reminderSent = false,
            status = "CONFIRMADA",
            timeSavedMinutes = 15,
            notes = notes,
            createdAtMillis = System.currentTimeMillis()
        )

        val insertedId = db.amenityBookingDao().insertBooking(newBooking)
        val savedBooking = newBooking.copy(id = insertedId)

        // 3. Sincronización aislada con Firebase Firestore
        try {
            val firestore = FirebaseConfigHelper.getFirestore()
            if (firestore != null) {
                FirestoreTenantManager.saveAmenityBooking(firestore, validCondo, savedBooking)
            }
        } catch (e: Exception) {
            android.util.Log.e("AmenityBookingEngine", "Error sincronizando reserva en Firestore: ${e.message}")
        }

        // 4. Registrar Auditoría Inmutable
        val auditFolio = AlphaCoreEngine.generateUniqueFolio("AUD")
        db.auditLogDao().insertAuditLog(
            AuditLogEntity(
                folio = auditFolio,
                operatorName = operatorName,
                actionType = "AMENITY_BOOKED",
                location = unitId,
                targetEntity = "$amenityName [$uniqueFolio] ($validCondo)",
                changeDetails = "Reserva en 1 toque confirmada para $residentName ($unitId) en $validCondo, horario $timeSlotFormatted ($dateFormatted). Aislamiento multi-tenant garantizado.",
                resultStatus = "CONFIRMADA"
            )
        )

        // 5. Programar Recordatorio y Despachar Notificaciones
        AmenityReminderManager.schedule15MinReminder(context, savedBooking)

        val scheduleFormatted = "$dateFormatted $timeSlotFormatted"
        ResidentNotificationManager.notifyAmenityBooking(
            context = context,
            amenityName = amenityName,
            residentName = residentName,
            unitId = unitId,
            bookingTimeFormatted = scheduleFormatted
        )

        SmartNotificationHub.notifyBookingConfirmed(
            context = context,
            db = db,
            amenityName = amenityName,
            residentName = residentName,
            unitId = unitId,
            scheduleFormatted = scheduleFormatted,
            bookingFolio = uniqueFolio
        )

        BookingExecutionResult.Success(savedBooking)
    }

    /**
     * Cancela una reserva, liberando el slot en tiempo real en Room DB y auditando la acción, y sincronizando cancelación con Firestore.
     */
    suspend fun cancelBooking(
        context: Context,
        db: AppDatabase,
        bookingId: Long,
        cancelledBy: String,
        cancellationReason: String = "Cancelado por el residente / administración",
        condominiumId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val allBookings = db.amenityBookingDao().getAllBookingsList()
        val booking = allBookings.find { it.id == bookingId } ?: return@withContext false

        val targetCondo = (condominiumId ?: booking.condominiumId).uppercase().trim()
        val now = System.currentTimeMillis()
        db.amenityBookingDao().cancelBooking(
            id = bookingId,
            cancelledBy = cancelledBy,
            reason = cancellationReason,
            nowMillis = now
        )

        // Cancelar en Firestore
        try {
            val firestore = FirebaseConfigHelper.getFirestore()
            if (firestore != null) {
                FirestoreTenantManager.cancelAmenityBookingInFirestore(
                    firestore = firestore,
                    condominiumId = targetCondo,
                    folio = booking.folio,
                    cancelledBy = cancelledBy,
                    reason = cancellationReason
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("AmenityBookingEngine", "Error cancelando en Firestore: ${e.message}")
        }

        // Cancelar recordatorio del sistema
        AmenityReminderManager.cancelReminder(context, bookingId)

        // Auditoría inmutable
        val auditFolio = AlphaCoreEngine.generateUniqueFolio("AUD")
        db.auditLogDao().insertAuditLog(
            AuditLogEntity(
                folio = auditFolio,
                operatorName = cancelledBy,
                actionType = "AMENITY_CANCELLED",
                location = booking.unitId,
                targetEntity = "${booking.amenityName} [${booking.folio}]",
                changeDetails = "Reserva cancelada por $cancelledBy en $targetCondo. Motivo: $cancellationReason. Horario liberado en tiempo real.",
                resultStatus = "CANCELADA"
            )
        )

        // Notificación de cancelación
        SmartNotificationHub.notifyBookingCancelled(
            context = context,
            db = db,
            amenityName = booking.amenityName,
            residentName = booking.residentName,
            unitId = booking.unitId,
            bookingFolio = booking.folio,
            reason = cancellationReason
        )

        true
    }
}
