package com.example.medusaalfha.data.repository

import android.util.Log
import com.example.medusaalfha.data.model.VisitorEntry
import com.example.medusaalfha.data.model.VisitorStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repositorio de Visitantes con sincronización en tiempo real vía Cloud Firestore
 * y fallback en memoria para operación ininterrumpida.
 */
class VisitorRepository(
    private val firestoreProvider: () -> FirebaseFirestore = {
        val app = com.example.medusaalfha.MedusaApplication.instance
            ?: throw IllegalStateException("MedusaApplication instance not ready")
        com.example.medusaalfha.data.firebase.FirebaseProvider.getFirestore(app)
    }
) {
    private val firestore by lazy { firestoreProvider() }

    companion object {
        private const val TAG = "VisitorRepository"
        const val COLLECTION_CONDOMINIUMS = "condominiums"
        const val SUBCOLLECTION_VISITORS = "visitor_entries"
        const val SUBCOLLECTION_VISITOR_LOGS = "visitor_logs"
        const val DEFAULT_CONDOMINIUM_ID = "PRADOS_1"
    }

    private val inMemoryFallbackList = mutableListOf<VisitorEntry>()

    /**
     * Retorna un Flow con la lista ordenada de visitantes desde Firestore
     * ordenado descendentemente por checkInTimestamp.
     */
    fun getVisitorEntriesFlow(condominiumId: String = DEFAULT_CONDOMINIUM_ID): Flow<List<VisitorEntry>> = callbackFlow {
        // Emitir estado en memoria actual (inicia vacío para reflejar datos reales de Firestore)
        val initialList = synchronized(inMemoryFallbackList) { inMemoryFallbackList.toList() }
        trySend(initialList)

        var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
        try {
            val collectionRef = firestore.collection(COLLECTION_CONDOMINIUMS)
                .document(condominiumId)
                .collection(SUBCOLLECTION_VISITORS)
                .orderBy("checkInTimestamp", Query.Direction.DESCENDING)
                .limit(50)

            listenerRegistration = collectionRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    val code = (error as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
                    Log.e(TAG, "FIRESTORE ERROR en getVisitorEntriesFlow [code=$code]: ${error.message}", error)
                    trySend(synchronized(inMemoryFallbackList) { inMemoryFallbackList.toList() })
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val entries = snapshot.documents.mapNotNull { doc ->
                        doc.data?.let { VisitorEntry.fromMap(it) }
                    }
                    synchronized(inMemoryFallbackList) {
                        inMemoryFallbackList.clear()
                        inMemoryFallbackList.addAll(entries)
                    }
                    Log.d(TAG, "Firestore devolvió ${entries.size} visitantes reales para $condominiumId.")
                    trySend(entries)
                } else {
                    Log.d(TAG, "Firestore: colección $SUBCOLLECTION_VISITORS vacía para $condominiumId (0 registros encontrados).")
                    synchronized(inMemoryFallbackList) { inMemoryFallbackList.clear() }
                    trySend(emptyList())
                }
            }
        } catch (t: Throwable) {
            val code = (t as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: t.javaClass.simpleName
            Log.e(TAG, "FIRESTORE EXCEPTION conectando a visitantes [code=$code]: ${t.message}", t)
            trySend(synchronized(inMemoryFallbackList) { inMemoryFallbackList.toList() })
        }

        awaitClose {
            listenerRegistration?.remove()
        }
    }

    /**
     * Fuerza una lectura manual y fresca desde Cloud Firestore para pull-to-refresh.
     */
    suspend fun refreshVisitorEntries(condominiumId: String = DEFAULT_CONDOMINIUM_ID): Result<List<VisitorEntry>> {
        return try {
            val snapshot = firestore.collection(COLLECTION_CONDOMINIUMS)
                .document(condominiumId)
                .collection(SUBCOLLECTION_VISITORS)
                .orderBy("checkInTimestamp", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()

            val entries = if (!snapshot.isEmpty) {
                snapshot.documents.mapNotNull { doc ->
                    doc.data?.let { VisitorEntry.fromMap(it) }
                }
            } else {
                emptyList()
            }

            synchronized(inMemoryFallbackList) {
                inMemoryFallbackList.clear()
                inMemoryFallbackList.addAll(entries)
            }
            Log.d(TAG, "Bitácora actualizada exitosamente desde Firestore: ${entries.size} registros reales.")
            Result.success(entries)
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: e.javaClass.simpleName
            Log.e(TAG, "FIRESTORE ERROR refrescando visitantes desde Firestore [code=$code]: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Registra un nuevo ingreso de visitante en Firestore y actualiza el caché.
     */
    suspend fun registerVisitorEntry(entry: VisitorEntry): Result<VisitorEntry> {
        // Asegurar que esté en memoria
        synchronized(inMemoryFallbackList) {
            inMemoryFallbackList.removeAll { it.folio == entry.folio }
            inMemoryFallbackList.add(0, entry)
        }

        return try {
            firestore.collection(COLLECTION_CONDOMINIUMS)
                .document(DEFAULT_CONDOMINIUM_ID)
                .collection(SUBCOLLECTION_VISITORS)
                .document(entry.folio)
                .set(entry.toMap())
                .await()
            Log.d(TAG, "Visitante guardado en Firestore exitosamente: ${entry.folio}")
            Result.success(entry)
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: e.javaClass.simpleName
            Log.e(TAG, "FIRESTORE ERROR guardando visitante [code=$code]: ${e.message}. Se mantiene localmente.", e)
            Result.success(entry)
        }
    }

    /**
     * Registra la salida de un visitante (Check-out).
     */
    suspend fun markVisitorCheckOut(folio: String): Result<Boolean> {
        val now = System.currentTimeMillis()
        synchronized(inMemoryFallbackList) {
            val idx = inMemoryFallbackList.indexOfFirst { it.folio == folio }
            if (idx != -1) {
                val current = inMemoryFallbackList[idx]
                inMemoryFallbackList[idx] = current.copy(
                    status = VisitorStatus.SALIDA,
                    checkOutTimestamp = now
                )
            }
        }

        return try {
            firestore.collection(COLLECTION_CONDOMINIUMS)
                .document(DEFAULT_CONDOMINIUM_ID)
                .collection(SUBCOLLECTION_VISITORS)
                .document(folio)
                .update(
                    mapOf(
                        "status" to VisitorStatus.SALIDA.name,
                        "checkOutTimestamp" to now
                    )
                )
                .await()
            Log.d(TAG, "Salida de visitante registrada en Firestore: $folio")
            Result.success(true)
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: e.javaClass.simpleName
            Log.e(TAG, "FIRESTORE ERROR actualizando salida en Firestore [code=$code]: ${e.message}", e)
            Result.success(true)
        }
    }

    /**
     * Consulta el estado actual de un folio de visitante para determinar si ya ingresó.
     */
    suspend fun checkVisitorCurrentStatus(
        folio: String,
        condominiumId: String = DEFAULT_CONDOMINIUM_ID
    ): VisitorStatus? {
        synchronized(inMemoryFallbackList) {
            val local = inMemoryFallbackList.find { it.folio.equals(folio, ignoreCase = true) }
            if (local != null) return local.status
        }

        return try {
            val doc = firestore.collection(COLLECTION_CONDOMINIUMS)
                .document(condominiumId)
                .collection(SUBCOLLECTION_VISITORS)
                .document(folio)
                .get()
                .await()
            if (doc.exists()) {
                val statusStr = doc.getString("status") ?: "DENTRO"
                try {
                    VisitorStatus.valueOf(statusStr)
                } catch (_: Exception) {
                    VisitorStatus.DENTRO
                }
            } else {
                null
            }
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: e.javaClass.simpleName
            Log.e(TAG, "FIRESTORE ERROR consultando estado de visitante [code=$code]: ${e.message}", e)
            null
        }
    }

    /**
     * Inicializa datos de muestra en Firestore si aún no se han subido.
     */
    suspend fun seedInitialDataIfEmpty() {
        try {
            val collectionRef = firestore.collection(COLLECTION_CONDOMINIUMS)
                .document(DEFAULT_CONDOMINIUM_ID)
                .collection(SUBCOLLECTION_VISITORS)
            val existing = collectionRef.limit(1).get().await()
            if (existing.isEmpty) {
                for (item in generateSampleEntries()) {
                    collectionRef.document(item.folio).set(item.toMap()).await()
                }
                Log.d(TAG, "Datos de muestra sembrados exitosamente en Cloud Firestore.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Aviso: No se pudo sembrar datos en Firestore: ${e.message}")
        }
    }

    private fun generateSampleEntries(): List<VisitorEntry> {
        val now = System.currentTimeMillis()
        return listOf(
            VisitorEntry(
                folio = "VIS-2026-1041",
                visitorName = "Arq. Sofía Villaseñor",
                visitorType = "VISITANTE",
                destinationHouse = "Casa 54 · Circuito Los Álamos",
                vehiclePlate = "UMC-341-K",
                accessMethod = "Pase QR Digital",
                status = VisitorStatus.SALIDA,
                checkInTimestamp = now - 1000 * 60 * 75,
                checkOutTimestamp = now - 1000 * 60 * 15,
                notes = "Reunión de supervisión de obra.",
                authorizedBy = "Residente Titular"
            ),
            VisitorEntry(
                folio = "VIS-2026-1042",
                visitorName = "Amazon Logistics (Pali 2r)",
                visitorType = "PAQUETERÍA",
                destinationHouse = "Casa 12 · Calle 1 (Pali 2r)",
                vehiclePlate = "LE-44-102",
                accessMethod = "Confirmación en Caseta",
                status = VisitorStatus.DENTRO,
                checkInTimestamp = now - 1000 * 60 * 18,
                notes = "Entrega con confirmación telefónica en caseta.",
                authorizedBy = "Caseta de Vigilancia"
            ),
            VisitorEntry(
                folio = "VIS-2026-1043",
                visitorName = "Lic. Carlos Mendoza",
                visitorType = "VISITANTE",
                destinationHouse = "Casa 54 · Cto. Álamos",
                vehiclePlate = "NZA-98-12",
                accessMethod = "Código QR",
                status = VisitorStatus.DENTRO,
                checkInTimestamp = now - 1000 * 60 * 25,
                notes = "Reunión familiar programada. Pase temporal activo.",
                authorizedBy = "Arq. González (Titular)"
            ),
            VisitorEntry(
                folio = "VIS-2026-1044",
                visitorName = "Ing. Mariana Torres",
                visitorType = "VISITANTE",
                destinationHouse = "Casa 88 · Cto. Encinos",
                vehiclePlate = "JHM-31-90",
                accessMethod = "Acceso Manual",
                status = VisitorStatus.SALIDA,
                checkInTimestamp = now - 1000 * 60 * 180,
                checkOutTimestamp = now - 1000 * 60 * 35,
                notes = "Revisión técnica de paneles solares.",
                authorizedBy = "Ing. Torres"
            ),
            VisitorEntry(
                folio = "VIS-2026-1045",
                visitorName = "Técnico Telmex Fibra",
                visitorType = "PAQUETERÍA",
                destinationHouse = "Casa 31 · Cto. Álamos",
                vehiclePlate = "UB-88-71",
                accessMethod = "Pase Digital",
                status = VisitorStatus.SALIDA,
                checkInTimestamp = now - 1000 * 60 * 120,
                checkOutTimestamp = now - 1000 * 60 * 45,
                notes = "Instalación de fibra óptica autorizada por administración.",
                authorizedBy = "Administración Prados"
            ),
            VisitorEntry(
                folio = "VIS-2026-1046",
                visitorName = "Uber Eats / Repartidor",
                visitorType = "PAQUETERÍA",
                destinationHouse = "Casa 09 · Cto. Los Encinos",
                vehiclePlate = "NLD-44",
                accessMethod = "Pase QR Temporal",
                status = VisitorStatus.SALIDA,
                checkInTimestamp = now - 1000 * 60 * 60,
                checkOutTimestamp = now - 1000 * 60 * 50,
                notes = "Entrega rápida de alimentos.",
                authorizedBy = "Residente Casa 09"
            ),
            VisitorEntry(
                folio = "VIS-2026-1047",
                visitorName = "Dr. Roberto Garza",
                visitorType = "VISITANTE",
                destinationHouse = "Casa 77 · Cto. Prados Norte",
                vehiclePlate = "MMX-99",
                accessMethod = "Pase Pre-autorizado",
                status = VisitorStatus.AUTORIZADO,
                checkInTimestamp = now - 1000 * 60 * 5,
                notes = "Visita médica programada.",
                authorizedBy = "Familia Garza"
            )
        )
    }
}
