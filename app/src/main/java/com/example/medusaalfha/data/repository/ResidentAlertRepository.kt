package com.example.medusaalfha.data.repository

import android.content.Context
import android.util.Log
import com.example.medusaalfha.data.fcm.ResidentNotificationHelper
import com.example.medusaalfha.data.model.AlertType
import com.example.medusaalfha.data.model.ResidentAlert
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repositorio de Alertas y Notificaciones para Residentes y Seguridad.
 */
class ResidentAlertRepository(
    private val firestoreProvider: () -> FirebaseFirestore = {
        val app = com.example.medusaalfha.MedusaApplication.instance
            ?: throw IllegalStateException("MedusaApplication instance not ready")
        com.example.medusaalfha.data.firebase.FirebaseProvider.getFirestore(app)
    }
) {
    private val firestore by lazy { firestoreProvider() }

    companion object {
        private const val TAG = "ResidentAlertRepo"
        const val DEFAULT_CONDOMINIUM_ID = "PRADOS_1"
        const val SUB_ALERTS = "resident_alerts"
    }

    private val inMemoryAlerts = mutableListOf<ResidentAlert>()

    init {
        inMemoryAlerts.addAll(generateInitialSampleAlerts())
    }

    /**
     * Flujo de alertas en tiempo real desde Firestore.
     */
    fun getAlertsFlow(condominiumId: String = DEFAULT_CONDOMINIUM_ID): Flow<List<ResidentAlert>> = callbackFlow {
        // Emitir estado en memoria de forma inmediata
        trySend(inMemoryAlerts.toList())

        var listener: com.google.firebase.firestore.ListenerRegistration? = null
        try {
            val collectionRef = firestore.collection("condominiums")
                .document(condominiumId)
                .collection(SUB_ALERTS)
                .orderBy("timestampMillis", Query.Direction.DESCENDING)
                .limit(40)

            listener = collectionRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error leyendo alertas de Firestore: ${error.message}")
                    trySend(inMemoryAlerts.toList())
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val cloudList = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { ResidentAlert.fromMap(it) }
                    }
                    synchronized(inMemoryAlerts) {
                        inMemoryAlerts.clear()
                        inMemoryAlerts.addAll(cloudList)
                    }
                    trySend(cloudList)
                } else {
                    trySend(inMemoryAlerts.toList())
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Excepción al registrar listener de alertas: ${t.message}")
            trySend(inMemoryAlerts.toList())
        }

        awaitClose { listener?.remove() }
    }

    /**
     * Emite una alerta de llegada de visita y notifica al dispositivo.
     */
    suspend fun dispatchVisitorArrivalAlert(
        context: Context,
        visitorName: String,
        destinationHouse: String,
        folio: String,
        vehiclePlate: String?
    ): Result<ResidentAlert> {
        val plateText = if (!vehiclePlate.isNullOrBlank()) " · Vehículo: [$vehiclePlate]" else ""
        val alert = ResidentAlert(
            id = "ALT-VIS-${System.currentTimeMillis() % 100000}",
            type = AlertType.VISITOR_ARRIVAL,
            title = "🔔 Visita en Garita: $visitorName",
            message = "Ha ingresado $visitorName con destino a $destinationHouse.$plateText Folio: $folio.",
            targetHouse = destinationHouse,
            timestampMillis = System.currentTimeMillis(),
            metadata = mapOf(
                "visitorName" to visitorName,
                "folio" to folio,
                "plate" to (vehiclePlate ?: "")
            )
        )

        // Mostrar notificación local en el dispositivo del residente/caseta
        try {
            ResidentNotificationHelper.showLocalNotification(context, alert)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo mostrar notificación: ${e.message}")
        }

        // Agregar a memoria local
        synchronized(inMemoryAlerts) {
            inMemoryAlerts.add(0, alert)
        }

        // Guardar en Cloud Firestore para distribución a otros dispositivos
        return try {
            firestore.collection("condominiums")
                .document(DEFAULT_CONDOMINIUM_ID)
                .collection(SUB_ALERTS)
                .document(alert.id)
                .set(alert.toMap())
                .await()
            Log.d(TAG, "Alerta de visitante guardada en Firestore: ${alert.id}")
            Result.success(alert)
        } catch (e: Exception) {
            Log.w(TAG, "Alerta activa localmente: ${e.message}")
            Result.success(alert)
        }
    }

    /**
     * Emite una alerta de confirmación de reserva de área común.
     */
    suspend fun dispatchBookingConfirmedAlert(
        context: Context,
        amenityName: String,
        destinationHouse: String,
        dateString: String,
        timeRange: String,
        bookingId: String
    ): Result<ResidentAlert> {
        val alert = ResidentAlert(
            id = "ALT-BOK-${System.currentTimeMillis() % 100000}",
            type = AlertType.BOOKING_CONFIRMED,
            title = "📅 Reserva Confirmada: $amenityName",
            message = "Tu reserva para $amenityName quedó programada para el $dateString de $timeRange.",
            targetHouse = destinationHouse,
            timestampMillis = System.currentTimeMillis(),
            metadata = mapOf(
                "amenityName" to amenityName,
                "dateString" to dateString,
                "timeRange" to timeRange,
                "bookingId" to bookingId
            )
        )

        try {
            ResidentNotificationHelper.showLocalNotification(context, alert)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo mostrar notificación: ${e.message}")
        }

        synchronized(inMemoryAlerts) {
            inMemoryAlerts.add(0, alert)
        }

        return try {
            firestore.collection("condominiums")
                .document(DEFAULT_CONDOMINIUM_ID)
                .collection(SUB_ALERTS)
                .document(alert.id)
                .set(alert.toMap())
                .await()
            Log.d(TAG, "Alerta de reserva guardada en Firestore: ${alert.id}")
            Result.success(alert)
        } catch (e: Exception) {
            Log.w(TAG, "Alerta de reserva activa localmente: ${e.message}")
            Result.success(alert)
        }
    }

    private fun generateInitialSampleAlerts(): List<ResidentAlert> = listOf(
        ResidentAlert(
            id = "ALT-INIT-1",
            type = AlertType.VISITOR_ARRIVAL,
            title = "🔔 Acceso Vehicular: Ing. Roberto Garza",
            message = "Acceso autorizado por Caseta Principal a Casa 54 · Cto. Álamos.",
            targetHouse = "Casa 54 · Circuito Los Álamos",
            timestampMillis = System.currentTimeMillis() - 1000 * 60 * 15,
            metadata = mapOf("folio" to "VIS-9812", "plate" to "NZA-88-21")
        ),
        ResidentAlert(
            id = "ALT-INIT-2",
            type = AlertType.BOOKING_CONFIRMED,
            title = "📅 Reserva Aprobada: Palapa & Alberca",
            message = "Tu espacio para el fin de semana se encuentra reservado exitosamente.",
            targetHouse = "Casa 54 · Circuito Los Álamos",
            timestampMillis = System.currentTimeMillis() - 1000 * 60 * 65,
            metadata = mapOf("amenity" to "Palapa & Alberca")
        )
    )
}
