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
        const val DEFAULT_CONDOMINIUM_ID = "PRADOS_1"
    }

    private val inMemoryFallbackList = mutableListOf<VisitorEntry>()

    init {
        inMemoryFallbackList.addAll(generateSampleEntries())
    }

    /**
     * Retorna un Flow con la lista ordenada de visitantes desde Firestore
     * ordenado descendentemente por checkInTimestamp.
     */
    fun getVisitorEntriesFlow(condominiumId: String = DEFAULT_CONDOMINIUM_ID): Flow<List<VisitorEntry>> = callbackFlow {
        // Emitir inmediatamente el estado en memoria para carga instantánea
        trySend(inMemoryFallbackList.toList())

        var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
        try {
            val collectionRef = firestore.collection(COLLECTION_CONDOMINIUMS)
                .document(condominiumId)
                .collection(SUBCOLLECTION_VISITORS)
                .orderBy("checkInTimestamp", Query.Direction.DESCENDING)
                .limit(50)

            listenerRegistration = collectionRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error escuchando visitantes de Firestore: ${error.message}. Usando memoria.")
                    trySend(inMemoryFallbackList.toList())
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
                    trySend(entries)
                } else {
                    // Si la colección está vacía en la nube, se proveen los datos base
                    trySend(inMemoryFallbackList.toList())
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Excepción conectando a Firestore: ${t.message}")
            trySend(inMemoryFallbackList.toList())
        }

        awaitClose {
            listenerRegistration?.remove()
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
            Log.w(TAG, "Error guardando visitante en Firestore: ${e.message}. Se mantiene localmente.")
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
            Log.w(TAG, "Error actualizando salida en Firestore: ${e.message}")
            Result.success(true)
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
                folio = "FOL-2026-901",
                visitorName = "Lic. Carlos Mendoza",
                visitorType = "Visita Familiar",
                destinationHouse = "Casa 54 · Cto. Álamos",
                vehiclePlate = "NZA-98-12",
                accessMethod = "Código QR",
                status = VisitorStatus.DENTRO,
                checkInTimestamp = now - 1000 * 60 * 25,
                notes = "Reunión familiar programada. Pase temporal activo.",
                authorizedBy = "Arq. González (Titular)"
            ),
            VisitorEntry(
                folio = "FOL-2026-902",
                visitorName = "Paquetería Express (Amazon)",
                visitorType = "Servicios & Entregas",
                destinationHouse = "Casa 12 · Cto. Fresnos",
                vehiclePlate = "LE-44-102",
                accessMethod = "Pase Digital",
                status = VisitorStatus.DENTRO,
                checkInTimestamp = now - 1000 * 60 * 45,
                notes = "Entrega de paquete con confirmación en caseta.",
                authorizedBy = "Caseta de Vigilancia"
            ),
            VisitorEntry(
                folio = "FOL-2026-903",
                visitorName = "Ing. Mariana Torres",
                visitorType = "Contratista / Mantenimiento",
                destinationHouse = "Casa 88 · Cto. Encinos",
                vehiclePlate = "JHM-31-90",
                accessMethod = "Acceso Manual",
                status = VisitorStatus.SALIDA,
                checkInTimestamp = now - 1000 * 60 * 180,
                checkOutTimestamp = now - 1000 * 60 * 15,
                notes = "Revisión técnica de paneles solares.",
                authorizedBy = "Ing. Torres"
            ),
            VisitorEntry(
                folio = "FOL-2026-904",
                visitorName = "Técnico Telmex Fibra",
                visitorType = "Servicios & Entregas",
                destinationHouse = "Casa 31 · Cto. Álamos",
                vehiclePlate = "UB-88-71",
                accessMethod = "Pase Digital",
                status = VisitorStatus.AUTORIZADO,
                checkInTimestamp = now - 1000 * 60 * 5,
                notes = "Instalación de fibra óptica autorizada por administración.",
                authorizedBy = "Administración Prados"
            )
        )
    }
}
