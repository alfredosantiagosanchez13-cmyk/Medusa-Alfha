package com.example.medusaalfha.data.repository

import android.util.Log
import com.example.medusaalfha.data.model.AmenityBooking
import com.example.medusaalfha.data.model.AmenityCatalog
import com.example.medusaalfha.data.model.BookingStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Repositorio de Reservas de Áreas Comunes integrado con Cloud Firestore.
 * Previene traslapes y gestiona la disponibilidad en tiempo real.
 */
class AmenityBookingRepository(
    private val firestoreProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) {
    private val firestore by lazy { firestoreProvider() }

    companion object {
        private const val TAG = "AmenityBookingRepo"
        const val DEFAULT_CONDOMINIUM_ID = "PRADOS_1"
        const val SUB_BOOKINGS = "amenity_bookings"
    }

    // Caché local en memoria para respuesta instantánea y contingencia offline
    private val inMemoryBookings = mutableListOf<AmenityBooking>()

    init {
        inMemoryBookings.addAll(createDefaultSampleBookings())
    }

    /**
     * Flujo reactivo en tiempo real de todas las reservas de un condominio.
     */
    fun getBookingsFlow(
        condominiumId: String = DEFAULT_CONDOMINIUM_ID
    ): Flow<List<AmenityBooking>> = callbackFlow {
        // Emitir datos en memoria de inmediato
        trySend(inMemoryBookings.toList())

        var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
        try {
            val collectionRef = firestore.collection("condominiums")
                .document(condominiumId)
                .collection(SUB_BOOKINGS)
                .orderBy("dateString", Query.Direction.ASCENDING)

            listenerRegistration = collectionRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error escuchando amenity_bookings de Firestore: ${error.message}")
                    trySend(inMemoryBookings.toList())
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val cloudList = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { AmenityBooking.fromMap(it) }
                    }
                    // Actualizar caché local
                    synchronized(inMemoryBookings) {
                        inMemoryBookings.clear()
                        inMemoryBookings.addAll(cloudList)
                    }
                    trySend(cloudList)
                } else {
                    // Si la colección de Firestore está vacía, emitir los de prueba y permitir seeding
                    trySend(inMemoryBookings.toList())
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Excepción al registrar listener de amenity_bookings: ${t.message}")
            trySend(inMemoryBookings.toList())
        }

        awaitClose {
            listenerRegistration?.remove()
        }
    }

    /**
     * Valida si un horario para un área común y fecha está libre de traslapes.
     * Retorna Pair(isAvailable, conflictingBooking)
     */
    fun checkAvailability(
        candidate: AmenityBooking,
        currentBookings: List<AmenityBooking> = inMemoryBookings
    ): Pair<Boolean, AmenityBooking?> {
        val conflict = currentBookings.firstOrNull { existing ->
            existing.overlapsWith(candidate)
        }
        return Pair(conflict == null, conflict)
    }

    /**
     * Registra una nueva reserva validando estrictamente que no exista traslape de horario.
     */
    suspend fun createBooking(
        booking: AmenityBooking,
        condominiumId: String = DEFAULT_CONDOMINIUM_ID
    ): Result<AmenityBooking> {
        val (isAvailable, conflict) = checkAvailability(booking)
        if (!isAvailable && conflict != null) {
            val errorMsg = "Conflicto de horario: El área '${booking.amenityName}' ya está reservada de ${conflict.formattedTimeRange()} por ${conflict.residentHouse} (${conflict.residentName})."
            return Result.failure(IllegalStateException(errorMsg))
        }

        // Validación de coherencia de horario
        if (booking.startTimeHour >= booking.endTimeHour) {
            return Result.failure(IllegalArgumentException("La hora de inicio debe ser menor que la hora de término."))
        }

        val area = AmenityCatalog.findById(booking.amenityId)
        if (booking.startTimeHour < area.openHour || booking.endTimeHour > area.closeHour) {
            return Result.failure(IllegalArgumentException("El horario solicitado está fuera del horario operativo del área (${area.openHour}:00 a ${area.closeHour}:00)."))
        }

        // Agregar de inmediato a la memoria local (optimistic update)
        synchronized(inMemoryBookings) {
            inMemoryBookings.removeAll { it.id == booking.id }
            inMemoryBookings.add(booking)
        }

        return try {
            firestore.collection("condominiums")
                .document(condominiumId)
                .collection(SUB_BOOKINGS)
                .document(booking.id)
                .set(booking.toMap())
                .await()
            Log.d(TAG, "Reserva ${booking.id} guardada exitosamente en Cloud Firestore.")
            Result.success(booking)
        } catch (e: Exception) {
            Log.w(TAG, "Advertencia: guardada localmente, sincronización en cola: ${e.message}")
            // Éxito resiliente local
            Result.success(booking)
        }
    }

    /**
     * Cancela una reserva existente.
     */
    suspend fun cancelBooking(
        bookingId: String,
        condominiumId: String = DEFAULT_CONDOMINIUM_ID
    ): Result<Unit> {
        synchronized(inMemoryBookings) {
            val index = inMemoryBookings.indexOfFirst { it.id == bookingId }
            if (index != -1) {
                val old = inMemoryBookings[index]
                inMemoryBookings[index] = old.copy(status = BookingStatus.CANCELADA)
            }
        }

        return try {
            firestore.collection("condominiums")
                .document(condominiumId)
                .collection(SUB_BOOKINGS)
                .document(bookingId)
                .update("status", BookingStatus.CANCELADA.name)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Cancelación local registrada: ${e.message}")
            Result.success(Unit)
        }
    }

    /**
     * Siembra registros iniciales de demostración en Firestore si la colección está vacía.
     */
    suspend fun seedSampleBookings(condominiumId: String = DEFAULT_CONDOMINIUM_ID): Result<Int> {
        val samples = createDefaultSampleBookings()
        return try {
            val batch = firestore.batch()
            val collectionRef = firestore.collection("condominiums")
                .document(condominiumId)
                .collection(SUB_BOOKINGS)

            samples.forEach { sample ->
                val doc = collectionRef.document(sample.id)
                batch.set(doc, sample.toMap())
            }
            batch.commit().await()
            Result.success(samples.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Genera datos de ejemplo realistas para el día actual y los días subsecuentes.
     */
    fun createDefaultSampleBookings(): List<AmenityBooking> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val today = sdf.format(cal.time)

        cal.add(Calendar.DAY_OF_YEAR, 1)
        val tomorrow = sdf.format(cal.time)

        cal.add(Calendar.DAY_OF_YEAR, 1)
        val dayAfter = sdf.format(cal.time)

        return listOf(
            AmenityBooking(
                id = "BOOK-101",
                amenityId = "ALBERCA_PALAPA",
                amenityName = "Palapa & Alberca Los Álamos",
                residentName = "Arq. Laura Méndez",
                residentHouse = "Casa 18 · Cto. Encinos",
                dateString = today,
                startTimeHour = 12,
                endTimeHour = 15,
                guestCount = 14,
                status = BookingStatus.CONFIRMADA,
                notes = "Cumpleaños infantil familiar. Uso exclusivo de palapa techada."
            ),
            AmenityBooking(
                id = "BOOK-102",
                amenityId = "CANCHA_PADEL",
                amenityName = "Cancha de Pádel y Tenis",
                residentName = "Lic. Carlos Santana",
                residentHouse = "Casa 34 · Cto. Los Álamos",
                dateString = today,
                startTimeHour = 17,
                endTimeHour = 19,
                guestCount = 4,
                status = BookingStatus.CONFIRMADA,
                notes = "Partido amistoso dobles nocturno."
            ),
            AmenityBooking(
                id = "BOOK-103",
                amenityId = "ASADORES_GRILL",
                amenityName = "Terraza Grill & Asadores",
                residentName = "Dr. Roberto Villaseñor",
                residentHouse = "Casa 52 · Cto. Cedros",
                dateString = today,
                startTimeHour = 19,
                endTimeHour = 22,
                guestCount = 10,
                status = BookingStatus.CONFIRMADA,
                notes = "Reunión de vecinos de manzana."
            ),
            AmenityBooking(
                id = "BOOK-104",
                amenityId = "CASA_CLUB",
                amenityName = "Casa Club Salón de Eventos",
                residentName = "Ing. Sofía Morales",
                residentHouse = "Casa 07 · Cto. Palmas",
                dateString = tomorrow,
                startTimeHour = 15,
                endTimeHour = 21,
                guestCount = 45,
                status = BookingStatus.CONFIRMADA,
                notes = "Aniversario de bodas. Mobiliario contratado."
            ),
            AmenityBooking(
                id = "BOOK-105",
                amenityId = "CANCHA_PADEL",
                amenityName = "Cancha de Pádel y Tenis",
                residentName = "Daniel Escobedo",
                residentHouse = "Casa 29 · Cto. Los Álamos",
                dateString = dayAfter,
                startTimeHour = 8,
                endTimeHour = 10,
                guestCount = 2,
                status = BookingStatus.CONFIRMADA,
                notes = "Entrenamiento matutino individual."
            )
        )
    }
}
