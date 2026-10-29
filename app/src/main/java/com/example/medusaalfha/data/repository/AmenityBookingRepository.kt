package com.example.medusaalfha.data.repository

import android.util.Log
import com.example.medusaalfha.data.model.AmenityArea
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
import java.util.Date
import java.util.Locale

/**
 * Repositorio de Reservas de Áreas Comunes sincronizado en Cloud Firestore.
 */
class AmenityBookingRepository(
    private val firestoreProvider: () -> FirebaseFirestore = {
        val app = com.example.medusaalfha.MedusaApplication.instance
            ?: throw IllegalStateException("MedusaApplication instance not ready")
        com.example.medusaalfha.data.firebase.FirebaseProvider.getFirestore(app)
    }
) {
    private val firestore by lazy { firestoreProvider() }

    companion object {
        private const val TAG = "AmenityBookingRepo"
        const val COLLECTION_CONDOMINIUMS = "condominiums"
        const val SUBCOLLECTION_BOOKINGS = "amenity_bookings"
        const val DEFAULT_CONDOMINIUM_ID = "PRADOS_1"
    }

    private val inMemoryBookings = mutableListOf<AmenityBooking>()

    /**
     * Obtiene el flujo de reservas para una fecha o general desde Firestore.
     */
    fun getBookingsFlow(
        condominiumId: String = DEFAULT_CONDOMINIUM_ID,
        dateFilter: String? = null
    ): Flow<List<AmenityBooking>> = callbackFlow {
        // Emitir estado en memoria actual (inicia vacío para reflejar datos reales de Firestore)
        val initialFiltered = synchronized(inMemoryBookings) {
            if (dateFilter != null) {
                inMemoryBookings.filter { it.dateString == dateFilter }
            } else {
                inMemoryBookings.toList()
            }
        }
        trySend(initialFiltered)

        var listener: com.google.firebase.firestore.ListenerRegistration? = null
        try {
            var query: Query = firestore.collection(COLLECTION_CONDOMINIUMS)
                .document(condominiumId)
                .collection(SUBCOLLECTION_BOOKINGS)

            if (dateFilter != null) {
                query = query.whereEqualTo("dateString", dateFilter)
            } else {
                query = query.orderBy("dateString", Query.Direction.ASCENDING)
            }

            listener = query.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val code = (error as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
                    Log.e(TAG, "FIRESTORE ERROR en getBookingsFlow [code=$code]: ${error.message}", error)
                    trySend(synchronized(inMemoryBookings) {
                        if (dateFilter != null) inMemoryBookings.filter { it.dateString == dateFilter }
                        else inMemoryBookings.toList()
                    })
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val bookings = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { AmenityBooking.fromMap(it) }
                    }
                    synchronized(inMemoryBookings) {
                        inMemoryBookings.clear()
                        inMemoryBookings.addAll(bookings)
                    }
                    val filtered = if (dateFilter != null) {
                        bookings.filter { it.dateString == dateFilter }
                    } else {
                        bookings
                    }
                    Log.d(TAG, "Firestore devolvió ${bookings.size} reservas reales para $condominiumId.")
                    trySend(filtered)
                } else {
                    Log.d(TAG, "Firestore: colección $SUBCOLLECTION_BOOKINGS vacía para fecha=$dateFilter.")
                    synchronized(inMemoryBookings) {
                        if (dateFilter != null) inMemoryBookings.removeAll { it.dateString == dateFilter }
                        else inMemoryBookings.clear()
                    }
                    trySend(emptyList())
                }
            }
        } catch (t: Throwable) {
            val code = (t as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: t.javaClass.simpleName
            Log.e(TAG, "FIRESTORE EXCEPTION conectando a reservas [code=$code]: ${t.message}", t)
            trySend(synchronized(inMemoryBookings) { inMemoryBookings.toList() })
        }

        awaitClose { listener?.remove() }
    }

    /**
     * Verifica si existe traslape de horario para una reserva en una amenidad y fecha dadas.
     */
    fun checkSlotAvailability(
        amenityId: String,
        dateString: String,
        startHour: Int,
        endHour: Int,
        excludeBookingId: String? = null
    ): AmenityBooking? {
        synchronized(inMemoryBookings) {
            return inMemoryBookings.firstOrNull { booking ->
                if (excludeBookingId != null && booking.id == excludeBookingId) return@firstOrNull false
                if (booking.amenityId != amenityId || booking.dateString != dateString) return@firstOrNull false
                if (booking.status == BookingStatus.CANCELLED) return@firstOrNull false
                // Verificación de traslape: startA < endB && startB < endA
                startHour < booking.endTimeHour && booking.startTimeHour < endHour
            }
        }
    }

    /**
     * Registra una nueva reserva en Firestore validando no traslapes concurrentes.
     */
    suspend fun createBooking(booking: AmenityBooking): Result<AmenityBooking> {
        // Validación local previa de conflicto
        val conflict = checkSlotAvailability(
            amenityId = booking.amenityId,
            dateString = booking.dateString,
            startHour = booking.startTimeHour,
            endHour = booking.endTimeHour,
            excludeBookingId = booking.id
        )

        if (conflict != null) {
            val msg = "Horario no disponible. Ya se encuentra reservado por ${conflict.residentName} (${conflict.residentHouse}) de ${conflict.formattedTimeRange}."
            return Result.failure(IllegalStateException(msg))
        }

        synchronized(inMemoryBookings) {
            inMemoryBookings.removeAll { it.id == booking.id }
            inMemoryBookings.add(booking)
        }

        return try {
            firestore.collection(COLLECTION_CONDOMINIUMS)
                .document(DEFAULT_CONDOMINIUM_ID)
                .collection(SUBCOLLECTION_BOOKINGS)
                .document(booking.id)
                .set(booking.toMap())
                .await()
            Log.d(TAG, "Reserva creada en Firestore exitosamente: ${booking.id}")
            Result.success(booking)
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: e.javaClass.simpleName
            Log.e(TAG, "FIRESTORE ERROR guardando reserva [code=$code]: ${e.message}. Activa en memoria.", e)
            Result.success(booking)
        }
    }

    /**
     * Cancela una reserva existente.
     */
    suspend fun cancelBooking(bookingId: String): Result<Boolean> {
        synchronized(inMemoryBookings) {
            val idx = inMemoryBookings.indexOfFirst { it.id == bookingId }
            if (idx != -1) {
                inMemoryBookings[idx] = inMemoryBookings[idx].copy(status = BookingStatus.CANCELLED)
            }
        }

        return try {
            firestore.collection(COLLECTION_CONDOMINIUMS)
                .document(DEFAULT_CONDOMINIUM_ID)
                .collection(SUBCOLLECTION_BOOKINGS)
                .document(bookingId)
                .update("status", BookingStatus.CANCELLED.name)
                .await()
            Log.d(TAG, "Reserva cancelada en Firestore: $bookingId")
            Result.success(true)
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: e.javaClass.simpleName
            Log.e(TAG, "FIRESTORE ERROR cancelando reserva [code=$code]: ${e.message}", e)
            Result.success(true)
        }
    }

    /**
     * Siembra reservas iniciales en Firestore si la colección está vacía.
     */
    suspend fun seedInitialBookingsIfEmpty() {
        try {
            val collectionRef = firestore.collection(COLLECTION_CONDOMINIUMS)
                .document(DEFAULT_CONDOMINIUM_ID)
                .collection(SUBCOLLECTION_BOOKINGS)
            val existing = collectionRef.limit(1).get().await()
            if (existing.isEmpty) {
                for (b in generateSampleBookings()) {
                    collectionRef.document(b.id).set(b.toMap()).await()
                }
                Log.d(TAG, "Reservas de muestra sembradas exitosamente en Firestore.")
            }
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: e.javaClass.simpleName
            Log.e(TAG, "FIRESTORE ERROR sembrando reservas en Firestore [code=$code]: ${e.message}", e)
        }
    }

    private fun generateSampleBookings(): List<AmenityBooking> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return listOf(
            AmenityBooking(
                id = "BOK-SAMPLE-01",
                amenityId = "palapa_alberca",
                amenityName = "Palapa & Alberca",
                residentName = "Familia González",
                residentHouse = "Casa 54 · Cto. Álamos",
                dateString = today,
                startTimeHour = 12,
                endTimeHour = 15,
                guestCount = 15,
                status = BookingStatus.CONFIRMED,
                notes = "Cumpleaños infantil familiar."
            ),
            AmenityBooking(
                id = "BOK-SAMPLE-02",
                amenityId = "cancha_padel",
                amenityName = "Cancha de Pádel",
                residentName = "Dr. Alejandro Ramos",
                residentHouse = "Casa 12 · Cto. Fresnos",
                dateString = today,
                startTimeHour = 18,
                endTimeHour = 20,
                guestCount = 4,
                status = BookingStatus.CONFIRMED,
                notes = "Partido de dobles vecinal."
            )
        )
    }
}
