package com.example.medusaalfha.data.repository

import android.util.Log
import com.example.medusaalfha.data.model.VisitorEntry
import com.example.medusaalfha.data.model.VisitorStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repositorio de Visitantes para Cloud Firestore con persistencia y actualización reactiva.
 */
class VisitorRepository(
    private val firestoreProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) {
    private val firestore by lazy { firestoreProvider() }

    companion object {
        private const val TAG = "VisitorRepository"
        const val DEFAULT_CONDOMINIUM_ID = "PRADOS_1"
        const val SUB_VISITOR_LOGS = "visitor_logs"
    }

    /**
     * Escucha en tiempo real los registros recientes de visitantes desde Firestore.
     */
    fun getRecentVisitorEntries(
        condominiumId: String = DEFAULT_CONDOMINIUM_ID,
        limit: Long = 30
    ): Flow<List<VisitorEntry>> = callbackFlow {
        var listenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null
        try {
            val collectionRef = firestore.collection("condominiums")
                .document(condominiumId)
                .collection(SUB_VISITOR_LOGS)
                .orderBy("checkInTimestamp", Query.Direction.DESCENDING)
                .limit(limit)

            listenerRegistration = collectionRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error escuchando visitor_logs de Firestore: ${error.message}")
                    // Si falla o no hay conexión, emitir lista con fallback de contingencia
                    trySend(getFallbackSampleEntries())
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val list = snapshot.documents.mapNotNull { doc ->
                        val data = doc.data ?: return@mapNotNull null
                        VisitorEntry.fromMap(data, doc.id)
                    }
                    trySend(list)
                } else {
                    // Colección vacía: se emite lista vacía para permitir sembrado inicial
                    trySend(emptyList())
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Excepción registrando listener de visitor_logs: ${t.message}")
            trySend(getFallbackSampleEntries())
        }

        awaitClose {
            listenerRegistration?.remove()
        }
    }

    /**
     * Registra un nuevo acceso de visitante en Firestore.
     */
    suspend fun registerVisitorCheckIn(
        entry: VisitorEntry,
        condominiumId: String = DEFAULT_CONDOMINIUM_ID
    ): Result<String> {
        return try {
            val targetFolio = if (entry.folio.isNotBlank()) entry.folio else "VIS-${System.currentTimeMillis() % 100000}"
            val finalEntry = entry.copy(folio = targetFolio, condominiumId = condominiumId)

            firestore.collection("condominiums")
                .document(condominiumId)
                .collection(SUB_VISITOR_LOGS)
                .document(targetFolio)
                .set(finalEntry.toMap())
                .await()

            Log.i(TAG, "✅ Visitante registrado con éxito en Firestore: $targetFolio")
            Result.success(targetFolio)
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando visitante en Firestore: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Registra la salida del visitante en Firestore actualizando su status y timestamp de salida.
     */
    suspend fun registerVisitorCheckOut(
        folio: String,
        condominiumId: String = DEFAULT_CONDOMINIUM_ID
    ): Result<Unit> {
        return try {
            val updates = hashMapOf<String, Any?>(
                "status" to VisitorStatus.SALIDA.name,
                "checkOutTimestamp" to com.google.firebase.Timestamp.now()
            )

            firestore.collection("condominiums")
                .document(condominiumId)
                .collection(SUB_VISITOR_LOGS)
                .document(folio)
                .update(updates)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error al registrar salida de visitante: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Siembra registros de ejemplo de Los Prados Residencial en Firestore.
     */
    suspend fun seedInitialVisitorEntriesIfEmpty(
        condominiumId: String = DEFAULT_CONDOMINIUM_ID
    ): Result<Int> {
        return try {
            val collectionRef = firestore.collection("condominiums")
                .document(condominiumId)
                .collection(SUB_VISITOR_LOGS)

            val existing = collectionRef.limit(1).get().await()
            if (!existing.isEmpty) {
                return Result.success(existing.size())
            }

            val samples = getFallbackSampleEntries()
            for (item in samples) {
                collectionRef.document(item.folio).set(item.toMap()).await()
            }
            Result.success(samples.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error sembrando registros de visitantes: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Catálogo realista basado en Los Prados Residencial (261 casas).
     */
    fun getFallbackSampleEntries(): List<VisitorEntry> {
        val now = System.currentTimeMillis()
        return listOf(
            VisitorEntry(
                folio = "VIS-2026-1041",
                visitorName = "Arq. Sofía Villaseñor",
                destinationHouse = "Casa 54 · Circuito Los Álamos",
                visitorType = "VISITANTE",
                checkInTimestamp = now - (15 * 60 * 1000), // Hace 15 min
                status = VisitorStatus.DENTRO,
                vehiclePlate = "UMC-341-K",
                authorizedBy = "Familia Arismendi",
                guardName = "Oficial Juan Pérez · Garita 1",
                accessMethod = "QR PASS DINÁMICO",
                notes = "Revisión arquitectónica de ampliación",
                condominiumId = "PRADOS_1"
            ),
            VisitorEntry(
                folio = "VIS-2026-1040",
                visitorName = "Amazon Logistics (Javier Rivas)",
                destinationHouse = "Casa 12 · Calle 1 (Bali 2r)",
                visitorType = "PAQUETERÍA",
                checkInTimestamp = now - (42 * 60 * 1000), // Hace 42 min
                status = VisitorStatus.DENTRO,
                vehiclePlate = "NLL-891-B",
                authorizedBy = "Gisela Contreras Cervantes",
                guardName = "Oficial Juan Pérez · Garita 1",
                accessMethod = "TAG RECEPTOR CASETA",
                notes = "Entrega en puerta paquete mediano",
                condominiumId = "PRADOS_1"
            ),
            VisitorEntry(
                folio = "VIS-2026-1039",
                visitorName = "Téc. Martín Morales (Megacable)",
                destinationHouse = "Casa 87 · Calle 2 (Bali 3r)",
                visitorType = "SERVICIOS",
                checkInTimestamp = now - (2 * 60 * 60 * 1000), // Hace 2 horas
                checkOutTimestamp = now - (35 * 60 * 1000),
                status = VisitorStatus.SALIDA,
                vehiclePlate = "SS-4521-F",
                authorizedBy = "Lic. Patricia Ruiz",
                guardName = "Oficial Juan Pérez · Garita 1",
                accessMethod = "CREDENCIAL INE FÍSICA",
                notes = "Instalación de fibra óptica",
                condominiumId = "PRADOS_1"
            ),
            VisitorEntry(
                folio = "VIS-2026-1038",
                visitorName = "Lic. Rodrigo Echeverría",
                destinationHouse = "Casa 104 · Manzana A",
                visitorType = "FAMILIAR",
                checkInTimestamp = now - (3 * 60 * 60 * 1000),
                status = VisitorStatus.DENTRO,
                vehiclePlate = "PZF-110-D",
                authorizedBy = "Mesa Directiva Central",
                guardName = "Oficial Juan Pérez · Garita 1",
                accessMethod = "QR PASS RESIDENTE",
                notes = "Reunión de consejo",
                condominiumId = "PRADOS_1"
            ),
            VisitorEntry(
                folio = "VIS-2026-1037",
                visitorName = "Purificadora Bonafont (Ruta 4)",
                destinationHouse = "Casa 21 · Calle 1 (Bali 2r)",
                visitorType = "PROVEEDOR",
                checkInTimestamp = now - (4 * 60 * 60 * 1000),
                checkOutTimestamp = now - (3 * 60 * 60 * 1000 + 40 * 60 * 1000),
                status = VisitorStatus.SALIDA,
                vehiclePlate = "LE-88390",
                authorizedBy = "Residente Titular",
                guardName = "Garita 1",
                accessMethod = "PADRÓN PROVEEDORES",
                notes = "Suministro de garrafones",
                condominiumId = "PRADOS_1"
            ),
            VisitorEntry(
                folio = "VIS-2026-1036",
                visitorName = "Conductor Uber (Nissan Versa)",
                destinationHouse = "Casa 18 · Calle 1",
                visitorType = "VISITANTE",
                checkInTimestamp = now - (5 * 60 * 60 * 1000),
                checkOutTimestamp = now - (4 * 60 * 60 * 1000 + 50 * 60 * 1000),
                status = VisitorStatus.SALIDA,
                vehiclePlate = "UMF-902-C",
                authorizedBy = "Residente",
                guardName = "Garita 1",
                accessMethod = "IDENTIFICACIÓN Y LLAMADA",
                notes = "Descenso de pasajeros",
                condominiumId = "PRADOS_1"
            ),
            VisitorEntry(
                folio = "VIS-2026-1035",
                visitorName = "Vendedor No Autorizado",
                destinationHouse = "Sin Destino",
                visitorType = "VISITANTE",
                checkInTimestamp = now - (6 * 60 * 60 * 1000),
                status = VisitorStatus.DENEGADO,
                vehiclePlate = "Sin Vehículo",
                authorizedBy = "NO AUTORIZADO",
                guardName = "Oficial Juan Pérez · Garita 1",
                accessMethod = "INTENTO PEATONAL",
                notes = "Venta por catálogo no permitida en reglamento",
                condominiumId = "PRADOS_1"
            )
        )
    }
}
