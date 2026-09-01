package com.example.data.firebase

import android.util.Log
import com.example.data.audit.AuditLogEntity
import com.example.data.booking.AppDatabase
import com.example.data.incident.IncidentCategory
import com.example.data.incident.IncidentEntity
import com.example.data.incident.IncidentPriority
import com.example.data.packages.PackageEntity
import com.example.data.passes.QrPassRoomEntity
import com.example.data.visitor.VisitorCheckIn
import com.example.scanner.PassType
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Servicio de sincronización y persistencia en tiempo real con Firebase Firestore
 * aplicando Aislamiento Estricto por Condominio (condominiumId).
 */
class FirebaseFirestoreSyncService(
    private val db: AppDatabase,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val tag = "FirebaseFirestoreSync"

    private val _syncStatus = MutableStateFlow("Listo para sincronización en tiempo real")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _activeCondominiumId = MutableStateFlow("PARAISO")
    val activeCondominiumId: StateFlow<String> = _activeCondominiumId.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var incidentsListener: ListenerRegistration? = null
    private var accessLogsListener: ListenerRegistration? = null
    private var qrPassesListener: ListenerRegistration? = null

    private val firestore: FirebaseFirestore?
        get() = FirebaseConfigHelper.getFirestore()

    /**
     * Inicia o reinicia la escucha de cambios en tiempo real desde Firestore
     * filtrada estrictamente por el `condominiumId` activo.
     */
    fun startRealtimeListeners(condominiumId: String = _activeCondominiumId.value) {
        val validCondoId = FirestoreTenantManager.validateCondominiumId(condominiumId)
        _activeCondominiumId.value = validCondoId

        val fs = firestore
        if (fs == null) {
            _syncStatus.value = "Modo Local Autónomo (Room SQLite) · [$validCondoId]"
            return
        }

        // Limpiar listeners previos para evitar cruces
        stopRealtimeListeners()

        try {
            _isListening.value = true
            _syncStatus.value = "Sincronizando en tiempo real con Firestore [$validCondoId]..."

            // 1. Escucha en tiempo real de Incidencias aisladas por condominio
            val incidentsQuery = FirestoreTenantManager.buildIsolatedQuery(
                firestore = fs,
                condominiumId = validCondoId,
                subcollectionName = FirestoreTenantManager.SUB_INCIDENTS
            )

            incidentsListener = incidentsQuery.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(tag, "[$validCondoId] Error en listener de incidencias: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshots != null && !snapshots.isEmpty) {
                    scope.launch {
                        for (doc in snapshots.documents) {
                            try {
                                val docCondoId = doc.getString("condominiumId")
                                // Verificación estricta de inquilino en memoria
                                if (docCondoId != null && !docCondoId.equals(validCondoId, ignoreCase = true)) {
                                    Log.w(tag, "⚠️ Cruce de datos prevenido: Documento ${doc.id} pertenece a $docCondoId y no a $validCondoId")
                                    continue
                                }

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
                                Log.w(tag, "Error procesando doc de incidencia: ${e.message}")
                            }
                        }
                    }
                }
            }

            // 2. Escucha en tiempo real de Pases QR aislados por condominio
            val qrQuery = FirestoreTenantManager.buildIsolatedQuery(
                firestore = fs,
                condominiumId = validCondoId,
                subcollectionName = FirestoreTenantManager.SUB_QR_PASSES
            )

            qrPassesListener = qrQuery.addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e(tag, "[$validCondoId] Error en listener de pases QR: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshots != null && !snapshots.isEmpty) {
                    scope.launch {
                        for (doc in snapshots.documents) {
                            try {
                                val docCondoId = doc.getString("condominiumId")
                                if (docCondoId != null && !docCondoId.equals(validCondoId, ignoreCase = true)) {
                                    continue
                                }
                                val passCode = doc.getString("passCode") ?: doc.id
                                val guestName = doc.getString("guestName") ?: "Visitante"
                                val destination = doc.getString("destinationHouse") ?: "Casa"
                                val hostName = doc.getString("hostResidentName") ?: "Residente"
                                val plate = doc.getString("vehiclePlate")
                                val passTypeStr = doc.getString("passType") ?: "VISITOR_SINGLE"
                                val validUntil = doc.getLong("validUntilMillis") ?: (System.currentTimeMillis() + 86400000L)
                                val maxEntries = doc.getLong("maxEntries")?.toInt() ?: 1
                                val currentEntries = doc.getLong("currentEntriesCount")?.toInt() ?: 0
                                val isActive = doc.getBoolean("isActive") ?: true
                                val note = doc.getString("note")

                                val pType = try {
                                    PassType.valueOf(passTypeStr)
                                } catch (e: Exception) {
                                    PassType.VISITOR_SINGLE
                                }

                                val entity = QrPassRoomEntity(
                                    passCode = passCode,
                                    guestName = guestName,
                                    guestDocument = "Verificar en Caseta",
                                    destinationHouse = destination,
                                    hostResidentName = hostName,
                                    vehiclePlate = plate,
                                    passType = pType,
                                    validUntilMillis = validUntil,
                                    maxEntries = maxEntries,
                                    currentEntriesCount = currentEntries,
                                    note = note,
                                    isActive = isActive
                                )
                                db.qrPassDao().insertPass(entity)
                            } catch (e: Exception) {
                                Log.w(tag, "Error procesando pase QR: ${e.message}")
                            }
                        }
                    }
                }
            }

            Log.i(tag, "[$validCondoId] Listeners en tiempo real de Firestore activados con aislamiento total.")
        } catch (e: Exception) {
            Log.e(tag, "Fallo al iniciar listeners con aislamiento: ${e.message}")
            _isListening.value = false
            _syncStatus.value = "Error al iniciar sincronización: ${e.message}"
        }
    }

    /**
     * Sube una incidencia a Firestore filtrada bajo el condominio correspondiente.
     */
    suspend fun pushIncidentToFirestore(incident: IncidentEntity, condominiumId: String = _activeCondominiumId.value): Result<Unit> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firestore no disponible."))
        val result = FirestoreTenantManager.saveIncident(fs, condominiumId, incident)
        if (result.isSuccess) {
            _syncStatus.value = "Incidencia ${incident.folio} sincronizada en [$condominiumId]."
        }
        return result
    }

    /**
     * Sube un registro de acceso de visitante a Firestore bajo el condominio correspondiente.
     */
    suspend fun pushVisitorCheckInToFirestore(visitor: VisitorCheckIn, condominiumId: String = _activeCondominiumId.value): Result<Unit> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firestore no disponible."))
        val result = FirestoreTenantManager.saveVisitorCheckIn(fs, condominiumId, visitor)
        if (result.isSuccess) {
            _syncStatus.value = "Acceso ${visitor.folio} sincronizado en [$condominiumId]."
        }
        return result
    }

    /**
     * Sube un pase QR a Firestore bajo el condominio correspondiente.
     */
    suspend fun pushQrPassToFirestore(pass: QrPassRoomEntity, condominiumId: String = _activeCondominiumId.value): Result<Unit> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firestore no disponible."))
        return FirestoreTenantManager.saveQrPass(fs, condominiumId, pass)
    }

    /**
     * Sube un paquete a Firestore bajo el condominio correspondiente.
     */
    suspend fun pushPackageToFirestore(pkg: PackageEntity, condominiumId: String = _activeCondominiumId.value): Result<Unit> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firestore no disponible."))
        return FirestoreTenantManager.savePackage(fs, condominiumId, pkg)
    }

    /**
     * Sube un registro de auditoría a Firestore bajo el condominio correspondiente.
     */
    suspend fun pushAuditLogToFirestore(audit: AuditLogEntity, condominiumId: String = _activeCondominiumId.value): Result<Unit> {
        val fs = firestore ?: return Result.failure(IllegalStateException("Firestore no disponible."))
        return FirestoreTenantManager.saveAuditLog(fs, condominiumId, audit)
    }

    /**
     * Detiene los listeners en tiempo real y libera recursos.
     */
    fun stopRealtimeListeners() {
        incidentsListener?.remove()
        accessLogsListener?.remove()
        qrPassesListener?.remove()
        incidentsListener = null
        accessLogsListener = null
        qrPassesListener = null
        _isListening.value = false
        _syncStatus.value = "Sincronización en tiempo real pausada."
    }
}
