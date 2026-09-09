package com.example.data.visitor

import android.content.Context
import android.util.Log
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.firebase.FirebaseConfigHelper
import com.example.data.firebase.FirestoreTenantManager
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Estado de Conexión en Vivo con la Colección de Firestore
 */
enum class FirestoreSyncStatus {
    CONNECTING,
    CONNECTED_LIVE,
    SYNCED_CACHE,
    OFFLINE_ROOM_FALLBACK,
    ERROR
}

/**
 * Métricas Administrativas en Vivo para el Monitoreo de Visitas
 */
data class AdminVisitorMetrics(
    val totalLogsCount: Int = 0,
    val currentlyInsideCount: Int = 0,
    val departedCount: Int = 0,
    val averageStayMinutes: Long = 0,
    val averageStayFormatted: String = "0 min",
    val todayEntriesCount: Int = 0
)

/**
 * Gestor en Tiempo Real de Bitácora de Visitantes en Firestore para Administradores.
 *
 * Mantiene sincronización reactiva con Firestore (/condominiums/{condoId}/visitor_logs)
 * y persistencia bidireccional con Room Database local para resiliencia offline.
 */
class FirestoreVisitorTracker(
    val context: Context,
    val db: AppDatabase,
    var activeCondominiumId: String = "PRADOS_1"
) {
    private val trackerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var snapshotRegistration: ListenerRegistration? = null

    private val _visitorLogs = MutableStateFlow<List<FirestoreVisitorLog>>(emptyList())
    val visitorLogs: StateFlow<List<FirestoreVisitorLog>> = _visitorLogs.asStateFlow()

    private val _syncStatus = MutableStateFlow(FirestoreSyncStatus.CONNECTING)
    val syncStatus: StateFlow<FirestoreSyncStatus> = _syncStatus.asStateFlow()

    private val _statusMessage = MutableStateFlow("Conectando con Firestore...")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _metrics = MutableStateFlow(AdminVisitorMetrics())
    val metrics: StateFlow<AdminVisitorMetrics> = _metrics.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        // Cargar inmediatamente desde Room como baseline para velocidad instantánea
        loadFromRoomBaseline()
        // Iniciar listener reactivo en tiempo real con Firestore
        startRealtimeListener(activeCondominiumId)
    }

    private fun loadFromRoomBaseline() {
        trackerScope.launch {
            try {
                val cached = db.visitorCheckInDao().getAllCheckInsList()
                if (cached.isNotEmpty()) {
                    val converted = cached.map { FirestoreVisitorLog.fromVisitorCheckIn(it, activeCondominiumId) }
                    _visitorLogs.value = converted.sortedByDescending { it.timestampMillis }
                    updateMetrics(_visitorLogs.value)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error cargando baseline de Room: ${e.message}")
            }
        }
    }

    /**
     * Inicia la escucha reactiva con SnapshotListener de Firestore.
     */
    fun startRealtimeListener(condominiumId: String = activeCondominiumId) {
        activeCondominiumId = condominiumId
        snapshotRegistration?.remove()

        val firestore = FirebaseConfigHelper.getFirestore()
        if (firestore == null) {
            _syncStatus.value = FirestoreSyncStatus.OFFLINE_ROOM_FALLBACK
            _statusMessage.value = "Modo Offline: Operando con SQLite local"
            return
        }

        _syncStatus.value = FirestoreSyncStatus.CONNECTING
        _statusMessage.value = "Conectando a /condominiums/$condominiumId/visitor_logs"

        try {
            val collectionRef = firestore.collection(FirestoreTenantManager.COL_CONDOMINIUMS)
                .document(condominiumId)
                .collection(FirestoreTenantManager.SUB_VISITOR_LOGS)

            snapshotRegistration = collectionRef
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(150)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Error en Firestore snapshot listener: ${error.message}")
                        _syncStatus.value = FirestoreSyncStatus.OFFLINE_ROOM_FALLBACK
                        _statusMessage.value = "Error Firestore: ${error.localizedMessage ?: "Fallback a Room"}"
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        trackerScope.launch {
                            val logsList = snapshot.documents.mapNotNull { doc ->
                                FirestoreVisitorLog.fromDocumentSnapshot(doc, condominiumId)
                            }

                            // Si Firestore tiene registros, sincronizar hacia Room y actualizar StateFlow
                            if (logsList.isNotEmpty()) {
                                _visitorLogs.value = logsList
                                updateMetrics(logsList)
                                _syncStatus.value = FirestoreSyncStatus.CONNECTED_LIVE
                                _statusMessage.value = "En vivo: ${logsList.size} logs sincronizados"

                                // Respaldar asíncronamente en Room
                                try {
                                    val dao = db.visitorCheckInDao()
                                    for (log in logsList) {
                                        val checkIn = log.toVisitorCheckIn()
                                        val existing = dao.getCheckInByFolio(log.folio)
                                        if (existing == null) {
                                            dao.insertCheckIn(checkIn)
                                        } else if (existing.status != checkIn.status || existing.checkOutMillis != checkIn.checkOutMillis) {
                                            dao.insertCheckIn(checkIn.copy(id = existing.id))
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Error sincronizando snapshot a Room: ${e.message}")
                                }
                            } else {
                                // Colección vacía en la nube, verificar si tenemos datos locales para sembrar
                                _syncStatus.value = FirestoreSyncStatus.CONNECTED_LIVE
                                _statusMessage.value = "Firestore conectado (0 registros en nube)"
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al conectar Firestore listener: ${e.message}", e)
            _syncStatus.value = FirestoreSyncStatus.ERROR
            _statusMessage.value = "Error al iniciar conexión: ${e.message}"
        }
    }

    /**
     * Refresca manualmente la bitácora desde Firestore y fuerza sincronización.
     */
    fun refreshFromCloud() {
        trackerScope.launch {
            _isRefreshing.value = true
            try {
                val firestore = FirebaseConfigHelper.getFirestore()
                if (firestore != null) {
                    val result = FirestoreTenantManager.queryVisitorLogs(firestore, activeCondominiumId, limitCount = 100)
                    if (result.isSuccess) {
                        val logs = result.getOrNull() ?: emptyList()
                        if (logs.isNotEmpty()) {
                            _visitorLogs.value = logs
                            updateMetrics(logs)
                            _syncStatus.value = FirestoreSyncStatus.CONNECTED_LIVE
                            _statusMessage.value = "Actualizado: ${logs.size} registros desde Firestore"
                        }
                    }
                }
            } catch (e: Exception) {
                _statusMessage.value = "Error al refrescar: ${e.message}"
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /**
     * Registra un nuevo ingreso de visitante directamente en Firestore y Room DB.
     * Guarda el Timestamp nativo de ingreso, visitorName, authorizedUnitNumber y metadatos.
     */
    suspend fun recordVisitorEntry(
        visitorName: String,
        authorizedUnitNumber: String,
        hostResidentName: String,
        visitorDocument: String = "Sin Documento",
        passTypeLabel: String = "Visita General",
        vehiclePlate: String? = null,
        guardName: String = "Administración / Caseta Principal",
        guardNotes: String? = null,
        residentNotes: String? = null
    ): Result<FirestoreVisitorLog> = withContext(Dispatchers.IO) {
        try {
            val folio = AlphaCoreEngine.generateUniqueFolio("MED")
            val passCode = "PASS-${folio.takeLast(6)}"
            val nowTimestamp = Timestamp.now()
            val nowMillis = nowTimestamp.toDate().time

            val log = FirestoreVisitorLog(
                folio = folio,
                visitorName = visitorName.trim(),
                authorizedUnitNumber = authorizedUnitNumber.trim(),
                timestamp = nowTimestamp,
                timestampMillis = nowMillis,
                condominiumId = activeCondominiumId,
                visitorDocument = visitorDocument.trim().ifBlank { "Sin Documento" },
                passCode = passCode,
                passTypeLabel = passTypeLabel,
                vehiclePlate = vehiclePlate?.trim()?.ifBlank { null },
                status = "CHECKED_IN",
                guardName = guardName.trim(),
                guardNotes = guardNotes?.trim()?.ifBlank { null } ?: "Ingreso registrado por administración",
                residentNotes = residentNotes?.trim()?.ifBlank { null },
                hostResidentName = hostResidentName.trim().ifBlank { "Administración" },
                checkOutTimestamp = null,
                checkOutMillis = null,
                syncedAtMillis = nowMillis
            )

            // 1. Guardar en SQLite Room local
            val checkIn = log.toVisitorCheckIn()
            db.visitorCheckInDao().insertCheckIn(checkIn)

            // 2. Guardar en Firebase Firestore
            val firestore = FirebaseConfigHelper.getFirestore()
            if (firestore != null) {
                FirestoreTenantManager.saveVisitorLog(firestore, activeCondominiumId, log)
                FirestoreTenantManager.saveVisitorCheckIn(firestore, activeCondominiumId, checkIn)
            }

            // Actualizar lista local de inmediato para feedback instantáneo
            val updatedList = listOf(log) + _visitorLogs.value.filter { it.folio != log.folio }
            _visitorLogs.value = updatedList
            updateMetrics(updatedList)

            Result.success(log)
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando ingreso de visitante: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Registra el timestamp de salida (checkOutTimestamp) para un visitante en estadía activa.
     * Actualiza el documento en Firestore con el Timestamp nativo de salida y status "DEPARTED".
     */
    suspend fun recordVisitorExit(
        folio: String,
        exitNotes: String? = "Salida autorizada y confirmada por administración"
    ): Result<FirestoreVisitorLog> = withContext(Dispatchers.IO) {
        try {
            val currentLog = _visitorLogs.value.firstOrNull { it.folio == folio }
                ?: run {
                    val local = db.visitorCheckInDao().getCheckInByFolio(folio)
                    if (local != null) FirestoreVisitorLog.fromVisitorCheckIn(local, activeCondominiumId) else null
                } ?: return@withContext Result.failure(Exception("No se encontró el registro con folio $folio"))

            val nowTimestamp = Timestamp.now()
            val nowMillis = nowTimestamp.toDate().time

            val updatedNotes = if (exitNotes.isNullOrBlank()) {
                currentLog.guardNotes
            } else {
                "${currentLog.guardNotes ?: ""}\n[Salida]: $exitNotes".trim()
            }

            val updatedLog = currentLog.copy(
                status = "DEPARTED",
                checkOutTimestamp = nowTimestamp,
                checkOutMillis = nowMillis,
                guardNotes = updatedNotes,
                syncedAtMillis = nowMillis
            )

            // 1. Actualizar en Room local
            db.visitorCheckInDao().insertCheckIn(updatedLog.toVisitorCheckIn())

            // 2. Actualizar en Firestore
            val firestore = FirebaseConfigHelper.getFirestore()
            if (firestore != null) {
                FirestoreTenantManager.recordVisitorExitInFirestore(
                    firestore = firestore,
                    condominiumId = activeCondominiumId,
                    folio = folio,
                    checkOutTimestamp = nowTimestamp,
                    guardNotes = updatedNotes
                )
            }

            // Actualizar lista en memoria
            val updatedList = _visitorLogs.value.map { if (it.folio == folio) updatedLog else it }
            _visitorLogs.value = updatedList
            updateMetrics(updatedList)

            Result.success(updatedLog)
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando salida: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Calcula métricas operativas a partir de los registros de Firestore.
     */
    private fun updateMetrics(logs: List<FirestoreVisitorLog>) {
        val total = logs.size
        val inside = logs.count { it.status == "CHECKED_IN" || it.status == "VERIFICADO" }
        val departed = logs.count { it.status == "DEPARTED" }

        // Calcular duración promedio de visitas completadas
        val completedStays = logs.filter { it.status == "DEPARTED" && it.checkOutMillis != null && it.checkOutMillis > it.timestampMillis }
        val avgMinutes = if (completedStays.isNotEmpty()) {
            val totalMinutes = completedStays.sumOf { (it.checkOutMillis!! - it.timestampMillis) / (60 * 1000L) }
            totalMinutes / completedStays.size
        } else {
            0L
        }

        val avgFormatted = if (avgMinutes >= 60) {
            val hours = avgMinutes / 60
            val mins = avgMinutes % 60
            "${hours}h ${mins}m"
        } else {
            "${avgMinutes} min"
        }

        // Hoy
        val calNow = java.util.Calendar.getInstance()
        calNow.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calNow.set(java.util.Calendar.MINUTE, 0)
        calNow.set(java.util.Calendar.SECOND, 0)
        calNow.set(java.util.Calendar.MILLISECOND, 0)
        val startOfToday = calNow.timeInMillis

        val todayCount = logs.count { it.timestampMillis >= startOfToday }

        _metrics.value = AdminVisitorMetrics(
            totalLogsCount = total,
            currentlyInsideCount = inside,
            departedCount = departed,
            averageStayMinutes = avgMinutes,
            averageStayFormatted = avgFormatted,
            todayEntriesCount = todayCount
        )
    }

    fun destroy() {
        snapshotRegistration?.remove()
        snapshotRegistration = null
    }

    companion object {
        private const val TAG = "FirestoreVisitorTracker"

        fun formatTimestamp(timestamp: Timestamp?, fallbackMillis: Long? = null): String {
            val date = timestamp?.toDate() ?: fallbackMillis?.let { Date(it) } ?: return "No registrado"
            return SimpleDateFormat("HH:mm:ss • dd/MM/yyyy", Locale.getDefault()).format(date)
        }

        fun calculateStayDuration(entryMillis: Long, exitMillis: Long?): String {
            val end = exitMillis ?: System.currentTimeMillis()
            val diffMs = (end - entryMillis).coerceAtLeast(0)
            val minutes = diffMs / (60 * 1000)
            val hours = minutes / 60
            val remainingMins = minutes % 60
            return when {
                hours > 0 -> "${hours}h ${remainingMins}m"
                minutes > 0 -> "${minutes} min"
                else -> "< 1 min"
            }
        }
    }
}
