package com.example.data.booking

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

/**
 * ESQUEMA OFICIAL DE FIRESTORE PARA RESERVAS DE ÁREAS COMUNES (COMMON AREA BOOKINGS)
 *
 * Ruta en Firestore (Aislamiento Multi-Inquilino):
 *   /condominiums/{condominiumId}/amenity_bookings/{folio}
 *
 * Atributos Principales:
 *   - folio: Identificador único de reserva (e.g., "RSV-20260823-1042")
 *   - condominiumId: Identificador de partición de condominio (e.g., "PRADOS_1")
 *   - amenityName: Nombre del área común reservada ("Quincho Principal", "Cancha de Pádel", etc.)
 *   - residentName: Nombre del residente solicitante
 *   - authorizedUnitNumber / unitId: Unidad residencial vinculada ("Casa 208")
 *   - userId: UID de Firebase Authentication del residente
 *   - bookingDate: Fecha de la reserva ("yyyy-MM-dd")
 *   - timeSlot: Rango horario reservado ("18:00 - 20:00")
 *   - startTimestamp: Timestamp nativo de Firestore de inicio de la reserva
 *   - endTimestamp: Timestamp nativo de Firestore de fin de la reserva
 *   - bookingTimeMillis: Timestamp numérico en epoch millis
 *   - durationMinutes: Duración en minutos (e.g., 120)
 *   - status: Estado de la reserva ("CONFIRMADA", "EN_CURSO", "COMPLETADA", "CANCELADA")
 *   - notes: Instrucciones o notas especiales del residente
 *   - reminderSent: Bandera de recordatorio previo enviado
 *   - cancelledBy: Identificador de quien canceló la reserva (si aplica)
 *   - cancellationReason: Motivo de cancelación (si aplica)
 *   - cancelledAtTimestamp: Timestamp de cancelación (si aplica)
 *   - timeSavedMinutes: Minutos de gestión automatizada ahorrados
 *   - createdAt: Timestamp nativo de creación
 *   - syncedAtMillis: Timestamp de sincronización
 */
data class FirestoreAmenityBooking(
    val folio: String,
    val condominiumId: String = "PRADOS_1",
    val amenityName: String,
    val residentName: String,
    val authorizedUnitNumber: String,
    val userId: String = "",
    val bookingDate: String = "",
    val timeSlot: String = "",
    val startTimestamp: Timestamp = Timestamp.now(),
    val endTimestamp: Timestamp? = null,
    val bookingTimeMillis: Long = System.currentTimeMillis(),
    val durationMinutes: Int = 120,
    val status: String = "CONFIRMADA",
    val notes: String = "",
    val reminderSent: Boolean = false,
    val cancelledBy: String? = null,
    val cancellationReason: String? = null,
    val cancelledAtTimestamp: Timestamp? = null,
    val cancelledAtMillis: Long? = null,
    val timeSavedMinutes: Int = 15,
    val createdAt: Timestamp = Timestamp.now(),
    val createdAtMillis: Long = System.currentTimeMillis(),
    val syncedAtMillis: Long = System.currentTimeMillis()
) {
    val unitId: String
        get() = authorizedUnitNumber

    /**
     * Convierte el modelo al formato de datos Map para escritura en Firestore.
     */
    fun toMap(): Map<String, Any?> = hashMapOf(
        "folio" to folio,
        "condominiumId" to condominiumId,
        "amenityName" to amenityName,
        "residentName" to residentName,
        "authorizedUnitNumber" to authorizedUnitNumber,
        "unitId" to authorizedUnitNumber,
        "userId" to userId,
        "residentId" to userId,
        "bookingDate" to bookingDate,
        "timeSlot" to timeSlot,
        "startTimestamp" to startTimestamp,
        "endTimestamp" to endTimestamp,
        "bookingTimeMillis" to bookingTimeMillis,
        "durationMinutes" to durationMinutes,
        "status" to status,
        "notes" to notes,
        "reminderSent" to reminderSent,
        "cancelledBy" to (cancelledBy ?: ""),
        "cancellationReason" to (cancellationReason ?: ""),
        "cancelledAtTimestamp" to cancelledAtTimestamp,
        "cancelledAtMillis" to (cancelledAtMillis ?: 0L),
        "timeSavedMinutes" to timeSavedMinutes,
        "createdAt" to createdAt,
        "createdAtMillis" to createdAtMillis,
        "syncedAtMillis" to syncedAtMillis
    )

    /**
     * Mapea hacia la entidad de base de datos local Room (AmenityBooking).
     */
    fun toAmenityBooking(localId: Long = 0): AmenityBooking {
        return AmenityBooking(
            id = localId,
            folio = folio,
            condominiumId = condominiumId,
            amenityName = amenityName,
            residentName = residentName,
            unitId = authorizedUnitNumber,
            bookingDate = bookingDate,
            timeSlot = timeSlot,
            bookingTimeMillis = bookingTimeMillis,
            durationMinutes = durationMinutes,
            reminderSent = reminderSent,
            status = status,
            cancelledBy = cancelledBy,
            cancellationReason = cancellationReason,
            cancelledAtMillis = cancelledAtMillis,
            timeSavedMinutes = timeSavedMinutes,
            notes = notes,
            createdAtMillis = createdAtMillis
        )
    }

    companion object {
        /**
         * Crea una instancia desde la entidad local Room AmenityBooking.
         */
        fun fromAmenityBooking(
            booking: AmenityBooking,
            userId: String = "",
            condoId: String = booking.condominiumId
        ): FirestoreAmenityBooking {
            val startTs = Timestamp(Date(booking.bookingTimeMillis))
            val endMillis = booking.bookingTimeMillis + (booking.durationMinutes * 60 * 1000L)
            val endTs = Timestamp(Date(endMillis))
            val cancelTs = booking.cancelledAtMillis?.let { Timestamp(Date(it)) }

            return FirestoreAmenityBooking(
                folio = booking.folio,
                condominiumId = condoId,
                amenityName = booking.amenityName,
                residentName = booking.residentName,
                authorizedUnitNumber = booking.unitId,
                userId = userId,
                bookingDate = booking.bookingDate,
                timeSlot = booking.timeSlot,
                startTimestamp = startTs,
                endTimestamp = endTs,
                bookingTimeMillis = booking.bookingTimeMillis,
                durationMinutes = booking.durationMinutes,
                status = booking.status,
                notes = booking.notes,
                reminderSent = booking.reminderSent,
                cancelledBy = booking.cancelledBy,
                cancellationReason = booking.cancellationReason,
                cancelledAtTimestamp = cancelTs,
                cancelledAtMillis = booking.cancelledAtMillis,
                timeSavedMinutes = booking.timeSavedMinutes,
                createdAt = Timestamp(Date(booking.createdAtMillis)),
                createdAtMillis = booking.createdAtMillis,
                syncedAtMillis = System.currentTimeMillis()
            )
        }

        /**
         * Deserializa un DocumentSnapshot de Firestore al modelo fuertemente tipado FirestoreAmenityBooking.
         */
        fun fromDocumentSnapshot(doc: DocumentSnapshot, defaultCondoId: String = "PRADOS_1"): FirestoreAmenityBooking? {
            return try {
                val folio = doc.getString("folio") ?: doc.id
                val condoId = doc.getString("condominiumId") ?: defaultCondoId
                val amenity = doc.getString("amenityName") ?: "Área Común"
                val resident = doc.getString("residentName") ?: "Residente"
                val unit = doc.getString("authorizedUnitNumber")
                    ?: doc.getString("unitId")
                    ?: "Unidad Desconocida"
                val userId = doc.getString("userId") ?: doc.getString("residentId") ?: ""
                val bookingDate = doc.getString("bookingDate") ?: ""
                val timeSlot = doc.getString("timeSlot") ?: ""

                val startTs = doc.getTimestamp("startTimestamp")
                val bookingMillis = doc.getLong("bookingTimeMillis")
                    ?: startTs?.toDate()?.time
                    ?: System.currentTimeMillis()
                val resolvedStartTs = startTs ?: Timestamp(Date(bookingMillis))

                val endTs = doc.getTimestamp("endTimestamp")
                val duration = doc.getLong("durationMinutes")?.toInt() ?: 120
                val status = doc.getString("status") ?: "CONFIRMADA"
                val notes = doc.getString("notes") ?: ""
                val reminder = doc.getBoolean("reminderSent") ?: false
                val cancelledBy = doc.getString("cancelledBy")
                val cancelReason = doc.getString("cancellationReason")
                val cancelTs = doc.getTimestamp("cancelledAtTimestamp")
                val cancelMillis = doc.getLong("cancelledAtMillis") ?: cancelTs?.toDate()?.time
                val timeSaved = doc.getLong("timeSavedMinutes")?.toInt() ?: 15

                val createdTs = doc.getTimestamp("createdAt")
                val createdMillis = doc.getLong("createdAtMillis") ?: createdTs?.toDate()?.time ?: System.currentTimeMillis()
                val resolvedCreatedTs = createdTs ?: Timestamp(Date(createdMillis))

                val syncedAt = doc.getLong("syncedAtMillis") ?: System.currentTimeMillis()

                FirestoreAmenityBooking(
                    folio = folio,
                    condominiumId = condoId,
                    amenityName = amenity,
                    residentName = resident,
                    authorizedUnitNumber = unit,
                    userId = userId,
                    bookingDate = bookingDate,
                    timeSlot = timeSlot,
                    startTimestamp = resolvedStartTs,
                    endTimestamp = endTs,
                    bookingTimeMillis = bookingMillis,
                    durationMinutes = duration,
                    status = status,
                    notes = notes,
                    reminderSent = reminder,
                    cancelledBy = cancelledBy,
                    cancellationReason = cancelReason,
                    cancelledAtTimestamp = cancelTs,
                    cancelledAtMillis = cancelMillis,
                    timeSavedMinutes = timeSaved,
                    createdAt = resolvedCreatedTs,
                    createdAtMillis = createdMillis,
                    syncedAtMillis = syncedAt
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
