package com.example.medusaalfha.data.repository

import android.util.Log
import com.example.medusaalfha.data.model.Condominium
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CondominiumRepository(
    private val firestoreProvider: () -> FirebaseFirestore = {
        val app = com.example.medusaalfha.MedusaApplication.instance
            ?: throw IllegalStateException("MedusaApplication instance not ready")
        com.example.medusaalfha.data.firebase.FirebaseProvider.getFirestore(app)
    }
) {
    private val firestore by lazy { firestoreProvider() }
    companion object {
        private const val TAG = "CondominiumRepo"
        const val COLLECTION_CONDOMINIUMS = "condominiums"

        private val inMemoryFallbackList = mutableListOf(
            Condominium.DEFAULT_PRADOS,
            Condominium(
                id = "ALAMOS_2",
                name = "Los Álamos Residencial",
                address = "Blvd. Los Encinos #500",
                totalUnits = 96,
                adminContact = "Santiago Sánchez · Dir. Gral",
                gateCount = 1,
                activeStatus = "ACTIVO",
                createdAtMillis = 1700000050000L,
                createdBy = "alfredosantiagosanchez13@gmail.com"
            )
        )

        private val _activeCondominium = MutableStateFlow(Condominium.DEFAULT_PRADOS)
        val activeCondominium: StateFlow<Condominium> = _activeCondominium.asStateFlow()

        fun setActiveCondominium(condo: Condominium) {
            _activeCondominium.value = condo
        }
    }

    /**
     * Escucha en tiempo real todos los condominios registrados en Cloud Firestore.
     */
    fun getCondominiumsFlow(): Flow<List<Condominium>> = callbackFlow {
        var listenerRegistration: ListenerRegistration? = null
        try {
            listenerRegistration = firestore.collection(COLLECTION_CONDOMINIUMS)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        val code = (error as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
                        Log.e(TAG, "FIRESTORE ERROR en getCondominiumsFlow [code=$code]: ${error.message}", error)
                        trySend(inMemoryFallbackList.toList())
                        return@addSnapshotListener
                    }

                    if (snapshot != null && !snapshot.isEmpty) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { Condominium.fromMap(it) }
                        }
                        synchronized(inMemoryFallbackList) {
                            inMemoryFallbackList.clear()
                            inMemoryFallbackList.addAll(list)
                        }
                        Log.d(TAG, "Firestore devolvió ${list.size} condominios registrados.")
                        trySend(list)
                    } else {
                        Log.d(TAG, "Firestore: colección $COLLECTION_CONDOMINIUMS vacía.")
                        trySend(inMemoryFallbackList.toList())
                    }
                }
        } catch (t: Throwable) {
            val code = (t as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: t.javaClass.simpleName
            Log.e(TAG, "FIRESTORE EXCEPTION conectando a condominios [code=$code]: ${t.message}", t)
            trySend(inMemoryFallbackList.toList())
        }

        awaitClose {
            listenerRegistration?.remove()
        }
    }

    /**
     * Registra un nuevo condominio (Alta de Condominio) en Cloud Firestore con privilegios Master.
     */
    suspend fun registerCondominium(condo: Condominium): Result<Condominium> {
        return try {
            synchronized(inMemoryFallbackList) {
                inMemoryFallbackList.removeAll { it.id == condo.id }
                inMemoryFallbackList.add(0, condo)
            }

            firestore.collection(COLLECTION_CONDOMINIUMS)
                .document(condo.id)
                .set(condo.toMap())
                .await()

            Log.d(TAG, "Condominio ${condo.name} (${condo.id}) dado de alta con éxito en Firestore.")
            Result.success(condo)
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando condominio en Firestore: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Obtiene la lista actual en memoria o desde Firestore.
     */
    suspend fun fetchCondominiums(): List<Condominium> {
        return try {
            val snapshot = firestore.collection(COLLECTION_CONDOMINIUMS).get().await()
            if (!snapshot.isEmpty) {
                snapshot.documents.mapNotNull { it.data?.let { data -> Condominium.fromMap(data) } }
            } else {
                inMemoryFallbackList.toList()
            }
        } catch (e: Exception) {
            inMemoryFallbackList.toList()
        }
    }
}
