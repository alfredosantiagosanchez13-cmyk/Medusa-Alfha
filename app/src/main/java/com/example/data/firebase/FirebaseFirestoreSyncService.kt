package com.example.data.firebase

import android.util.Log
import com.example.data.booking.AppDatabase
import com.example.data.incident.IncidentCategory
import com.example.data.incident.IncidentEntity
import com.example.data.incident.IncidentPriority
import com.example.data.visitor.VisitorCheckIn
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Servicio de persistencia y sincronización en tiempo real con Firebase Firestore.
 * Sincroniza bidireccionalmente las colecciones clave con Room SQLite como Fuente Única de Verdad.
 */
class FirebaseFirestoreSyncService(
    private val db: AppDatabase,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val tag = "FirebaseFirestoreSync"

    private val _syncStatus = MutableStateFlow("Listo para sincronización en tiempo real")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var incidentsListener: ListenerRegistration? = null
    private var accessLogsListener: ListenerRegistration? = null

    private val firestore: FirebaseFirestore?
        get() = FirebaseConfigHelper.getFirestore()

    /**
     * Inicia la escucha de cambios en tiempo real desde Firestore.
     */
    fun startRealtimeListeners() {
        val fs = firestore
        if (fs == null) {
            _syncStatus.value = "Firestore no disponible (Modo local activo)"
            return
        }

        if (_isListening.value) return

        try {
            _isListening.value = true
            _syncStatus.value = "Escuchando actualizaciones en tiempo real de Firestore..."

            // 1. Escucha en tiempo real de Incidencias
            incidentsListener = fs.collection("incidents")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e(tag, "Error en listener de incidencias: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshots != null && !snapshots.isEmpty) {
                        scope.launch {
                            for (doc in snapshots.documents) {
                                try {
                                    val folio = doc.getString("folio") ?: doc.id
                                    val rawTranscript = doc.getString("rawTranscript") ?: doc.getString("title") ?: "Incidencia en la Nube"
                                    val catStr = doc.getString("category") ?: "SEGURIDAD"
                                    val prioStr = doc.getString("priority") ?: "MEDIA"
                                    val location = doc.getString("location") ?: "UBICACIÓN NO DISPONIBLE"
                                    val aiSummary = doc.getString("aiSummary") ?: rawTranscript
                                    val recommendedAction = doc.getString("recommendedAction") ?: "Verificar en sitio"
                                    val timestampMillis = doc.getLong("timestampMillis") ?: System.currentTimeMillis()
                                    val reportedBy = doc.getString("reportedBy") ?: "Sistema Remoto"
                                    val status = doc.getString("status") ?: "REGISTRADO"

                                    val category = try {
                                        IncidentCategory.valueOf(catStr.uppercase())
                                    } catch (e: Exception) {
                                        IncidentCategory.SEGURIDAD_EMERGENCIA
                                    }

                                    val priority = try {
                                        IncidentPriority.valueOf(prioStr.uppercase())
                                    } catch (e: Exception) {
                                        IncidentPriority.MEDIA
                                    }

                                    val incident = IncidentEntity(
                                        folio = folio,
                                        rawTranscript = rawTranscript,
                                        category = category,
                                        priority = priority,
                                        location = location,
                                        aiSummary = aiSummary,
                                        recommendedAction = recommendedAction,
                                        timestampMillis = timestampMillis,
                                        reportedBy = reportedBy,
                                        status = status
                                    )
                                    db.incidentDao().insertIncident(incident)
                                } catch (e: Exception) {
                                    Log.w(tag, "Error al procesar doc de incidencia: ${e.message}")
                                }
                            }
                        }
                    }
                }

            Log.i(tag, "Listeners en tiempo real de Firestore iniciados exitosamente.")
        } catch (e: Exception) {
            Log.e(tag, "Fallo al iniciar listeners: ${e.message}")
            _isListening.value = false
            _syncStatus.value = "Error al iniciar sincronización: ${e.message}"
        }
    }

    /**
     * Sube una incidencia a Firestore en tiempo real.
     */
    suspend fun pushIncidentToFirestore(incident: IncidentEntity): Result<Unit> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firestore no disponible."))
        return try {
            val data = hashMapOf(
                "folio" to incident.folio,
                "rawTranscript" to incident.rawTranscript,
                "category" to incident.category.name,
                "priority" to incident.priority.name,
                "status" to incident.status,
                "location" to incident.location,
                "aiSummary" to incident.aiSummary,
                "recommendedAction" to incident.recommendedAction,
                "timestampMillis" to incident.timestampMillis,
                "reportedBy" to incident.reportedBy,
                "updatedAt" to System.currentTimeMillis()
            )
            fs.collection("incidents").document(incident.folio)
                .set(data, SetOptions.merge())
                .await()
            _syncStatus.value = "Incidencia ${incident.folio} sincronizada con Firestore."
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error al subir incidencia a Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Sube un registro de acceso de visitante a Firestore.
     */
    suspend fun pushVisitorCheckInToFirestore(visitor: VisitorCheckIn): Result<Unit> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firestore no disponible."))
        return try {
            val data = hashMapOf(
                "id" to visitor.id,
                "folio" to visitor.folio,
                "visitorName" to visitor.visitorName,
                "visitorDocument" to visitor.visitorDocument,
                "destinationHouse" to visitor.destinationHouse,
                "passCode" to visitor.passCode,
                "passTypeLabel" to visitor.passTypeLabel,
                "vehiclePlate" to (visitor.vehiclePlate ?: ""),
                "status" to visitor.status,
                "timestampMillis" to visitor.timestampMillis,
                "checkOutMillis" to (visitor.checkOutMillis ?: 0L),
                "guardNotes" to (visitor.guardNotes ?: ""),
                "residentNotes" to (visitor.residentNotes ?: ""),
                "hostResidentName" to visitor.hostResidentName,
                "updatedAt" to System.currentTimeMillis()
            )
            fs.collection("visitor_access").document(visitor.folio)
                .set(data, SetOptions.merge())
                .await()
            _syncStatus.value = "Acceso ${visitor.folio} sincronizado con Firestore."
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Error al subir acceso a Firestore: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Detiene los listeners en tiempo real.
     */
    fun stopRealtimeListeners() {
        incidentsListener?.remove()
        accessLogsListener?.remove()
        incidentsListener = null
        accessLogsListener = null
        _isListening.value = false
        _syncStatus.value = "Sincronización en tiempo real pausada."
    }
}
