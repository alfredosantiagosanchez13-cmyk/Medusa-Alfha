package com.example.medusaalfha.data.repository

import android.content.Context
import android.util.Log
import com.example.medusaalfha.data.local.MedusaDatabase
import com.example.medusaalfha.data.local.entity.CheckpointEntity
import com.example.medusaalfha.data.local.entity.PatrolReportEntity
import com.example.medusaalfha.data.model.CondominiumPatrolMapCatalog
import com.example.medusaalfha.data.model.PatrolCheckpoint
import com.example.medusaalfha.data.model.PatrolLogEntry
import com.example.medusaalfha.data.network.NetworkConnectivityMonitor
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class PatrolSyncState(
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val pendingCheckpointsCount: Int = 0,
    val pendingReportsCount: Int = 0,
    val lastSyncTimestampMillis: Long? = null,
    val statusMessage: String = "Capa local Room activa"
) {
    val totalPendingCount: Int get() = pendingCheckpointsCount + pendingReportsCount
}

/**
 * Repositorio de Sincronización Local con Room y Cloud Firestore.
 *
 * Implementa la arquitectura Offline-First:
 * 1. Todos los checkpoints y reportes se persisten de forma inmediata en SQLite (Room).
 * 2. La sincronización hacia Firestore se realiza ÚNICAMENTE cuando se detecta una
 *    conexión activa a Internet (verificada por NetworkConnectivityMonitor).
 * 3. En caso de reconexión, sincroniza automáticamente las transacciones pendientes.
 */
class PatrolSyncRepository(
    private val database: MedusaDatabase,
    private val networkMonitor: NetworkConnectivityMonitor,
    private val firestoreProvider: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() },
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private val firestore by lazy { firestoreProvider() }
    private val checkpointDao = database.checkpointDao()
    private val reportDao = database.patrolReportDao()

    companion object {
        private const val TAG = "PatrolSyncRepository"
        const val COLLECTION_CONDOMINIUMS = "condominiums"
        const val SUBCOLLECTION_CHECKPOINTS = "patrol_checkpoints"
        const val SUBCOLLECTION_REPORTS = "patrol_reports"
        const val DEFAULT_CONDOMINIUM_ID = "PRADOS_1"

        @Volatile
        private var INSTANCE: PatrolSyncRepository? = null

        fun getInstance(context: Context): PatrolSyncRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PatrolSyncRepository(
                    database = MedusaDatabase.getInstance(context),
                    networkMonitor = NetworkConnectivityMonitor(context)
                ).also { INSTANCE = it }
            }
        }
    }

    private val _syncState = MutableStateFlow(PatrolSyncState())
    val syncState: StateFlow<PatrolSyncState> = _syncState.asStateFlow()

    val checkpointsFlow: Flow<List<PatrolCheckpoint>> = checkpointDao.getAllCheckpointsFlow()
        .map { list -> list.map { it.toDomainModel() } }
        .distinctUntilChanged()

    val reportsFlow: Flow<List<PatrolLogEntry>> = reportDao.getAllReportsFlow()
        .map { list -> list.map { it.toDomainModel() } }
        .distinctUntilChanged()

    init {
        // 1. Inicializar datos semilla en Room si la base de datos está vacía
        coroutineScope.launch {
            seedInitialCheckpointsIfEmpty()
            updatePendingCounts()
        }

        // 2. Monitorear cambios de conectividad en tiempo real: sincronizar si hay red activa
        coroutineScope.launch {
            networkMonitor.observeConnectivity().collect { isOnline ->
                Log.d(TAG, "Cambio de conectividad detectado: isOnline = $isOnline")
                _syncState.update { it.copy(isOnline = isOnline) }
                if (isOnline) {
                    _syncState.update { it.copy(statusMessage = "Conexión detectada. Sincronizando datos con Firestore...") }
                    syncPendingData()
                } else {
                    val pending = _syncState.value.totalPendingCount
                    _syncState.update {
                        it.copy(
                            statusMessage = if (pending > 0)
                                "Sin conexión. $pending cambios guardados en Room (pendientes de sincronizar)."
                            else
                                "Sin conexión. Operando en modo local seguro (Room)."
                        )
                    }
                }
            }
        }
    }

    /**
     * Si la base local de Room no contiene checkpoints, se cargan los iniciales del condominio.
     */
    private suspend fun seedInitialCheckpointsIfEmpty() {
        try {
            val count = checkpointDao.getCount()
            if (count == 0) {
                val seed = CondominiumPatrolMapCatalog.getInitialCheckpoints().map {
                    CheckpointEntity.fromDomainModel(it, isSynced = false)
                }
                checkpointDao.insertCheckpoints(seed)
                Log.d(TAG, "Se insertaron ${seed.size} checkpoints iniciales en Room.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando checkpoints en Room: ${e.message}", e)
        }
    }

    /**
     * Valida la posición de un checkpoint.
     * Siempre actualiza Room primero. Si hay internet activo, sube a Firestore y marca isSynced = true.
     */
    suspend fun validateCheckpoint(checkpointId: String): String {
        val now = System.currentTimeMillis()
        val isOnline = networkMonitor.isOnline()

        // 1. Persistencia local inmediata en Room (Offline-First)
        checkpointDao.updateValidationStatus(
            checkpointId = checkpointId,
            isValidated = true,
            validatedAt = now,
            isSynced = false,
            updatedAt = now
        )

        updatePendingCounts()

        val cp = checkpointDao.getCheckpointById(checkpointId)
        val cpName = cp?.name ?: checkpointId

        // 2. Si no hay internet, asegurar que permanezca en Room y notificar
        if (!isOnline) {
            _syncState.update {
                it.copy(
                    isOnline = false,
                    statusMessage = "Checkpoint validado en local (Room). Se sincronizará con Firestore al detectar Internet."
                )
            }
            return "GPS OK (Caché Room): $cpName guardado en almacenamiento local."
        }

        // 3. Si hay conexión activa, sincronizar a Firestore
        return try {
            if (cp != null) {
                pushCheckpointToFirestore(cp)
                checkpointDao.markAsSynced(listOf(checkpointId))
                updatePendingCounts()
                _syncState.update {
                    it.copy(
                        isOnline = true,
                        lastSyncTimestampMillis = System.currentTimeMillis(),
                        statusMessage = "Checkpoint sincronizado con Firestore exitosamente."
                    )
                }
                "GPS OK: $cpName validado y sincronizado con Cloud Firestore."
            } else {
                "GPS OK: Checkpoint registrado en Room."
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallo al sincronizar checkpoint con Firestore (${e.message}). Queda en Room.", e)
            _syncState.update {
                it.copy(
                    statusMessage = "Guardado en Room. Pendiente de envío a Firestore."
                )
            }
            "GPS OK (Guardado en Room): Pendiente de envío a Firestore."
        }
    }

    /**
     * Restablece el circuito de rondín para una nueva ronda.
     */
    suspend fun resetCircuit() {
        val now = System.currentTimeMillis()
        val isOnline = networkMonitor.isOnline()

        // 1. Reset en Room
        checkpointDao.resetAllCircuit(now = now, isSynced = false)
        updatePendingCounts()

        if (!isOnline) {
            _syncState.update {
                it.copy(
                    isOnline = false,
                    statusMessage = "Circuito reiniciado en Room (Modo Offline)."
                )
            }
            return
        }

        // 2. Si hay red, sincronizar estado reseteado a Firestore
        try {
            syncPendingCheckpoints()
            _syncState.update {
                it.copy(
                    isOnline = true,
                    lastSyncTimestampMillis = System.currentTimeMillis(),
                    statusMessage = "Circuito reiniciado y sincronizado con Firestore."
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error sincronizando reseteo de circuito a Firestore: ${e.message}")
        }
    }

    /**
     * Guarda un informe de rondín.
     * Persiste inmediatamente en Room con isSynced = false.
     * Si hay internet activo, lo sube a Firestore y marca isSynced = true.
     */
    suspend fun saveReport(zone: String, reportText: String, isAiAssisted: Boolean = false): PatrolLogEntry {
        val now = System.currentTimeMillis()
        val reportId = "ROND-${now % 100000}"
        val isOnline = networkMonitor.isOnline()

        val entity = PatrolReportEntity(
            id = reportId,
            timestampMillis = now,
            zone = zone,
            reportText = reportText,
            isAiAssisted = isAiAssisted,
            isSynced = false,
            createdAtMillis = now
        )

        // 1. Guardar en Room (Garantía local inmediata)
        reportDao.insertReport(entity)
        updatePendingCounts()

        // 2. Si no hay conexión, se queda en Room
        if (!isOnline) {
            _syncState.update {
                it.copy(
                    isOnline = false,
                    statusMessage = "Informe archivado en Room (Caché local). Se subirá a Firestore al detectar red."
                )
            }
            return entity.toDomainModel()
        }

        // 3. Si hay conexión activa, subir a Firestore
        try {
            pushReportToFirestore(entity)
            reportDao.markReportAsSynced(reportId)
            updatePendingCounts()
            _syncState.update {
                it.copy(
                    isOnline = true,
                    lastSyncTimestampMillis = System.currentTimeMillis(),
                    statusMessage = "Informe subido y sincronizado con Cloud Firestore."
                )
            }
            return entity.copy(isSynced = true).toDomainModel()
        } catch (e: Exception) {
            Log.w(TAG, "Error subiendo informe a Firestore (${e.message}). Queda en Room.", e)
            _syncState.update {
                it.copy(
                    statusMessage = "Informe archivado en Room (Fallo de red al conectar a Firestore)."
                )
            }
            return entity.toDomainModel()
        }
    }

    /**
     * Sincroniza todos los checkpoints y reportes que están pendientes en Room
     * hacia Firestore, SI Y SÓLO SI hay conexión activa a Internet.
     */
    suspend fun syncPendingData(): Boolean {
        if (!networkMonitor.isOnline()) {
            Log.d(TAG, "Sincronización abortada: No hay conexión activa a Internet.")
            _syncState.update {
                it.copy(
                    isOnline = false,
                    statusMessage = "Sin conexión activa. Datos protegidos en Room."
                )
            }
            return false
        }

        _syncState.update { it.copy(isSyncing = true, statusMessage = "Sincronizando con Cloud Firestore...") }

        return try {
            val checkpointsSynced = syncPendingCheckpoints()
            val reportsSynced = syncPendingReports()

            updatePendingCounts()
            val now = System.currentTimeMillis()

            val msg = if (checkpointsSynced > 0 || reportsSynced > 0) {
                "Sincronización exitosa: $checkpointsSynced checkpoints y $reportsSynced informes subidos a Firestore."
            } else {
                "Caché local de Room y Cloud Firestore en sincronía total."
            }

            _syncState.update {
                it.copy(
                    isSyncing = false,
                    isOnline = true,
                    lastSyncTimestampMillis = now,
                    statusMessage = msg
                )
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error durante sincronización con Firestore: ${e.message}", e)
            updatePendingCounts()
            _syncState.update {
                it.copy(
                    isSyncing = false,
                    statusMessage = "Error en enlace con Firestore: ${e.message}. Datos seguros en Room."
                )
            }
            false
        }
    }

    private suspend fun syncPendingCheckpoints(): Int {
        val unsynced = checkpointDao.getUnsyncedCheckpoints()
        if (unsynced.isEmpty()) return 0

        var successCount = 0
        for (cp in unsynced) {
            try {
                pushCheckpointToFirestore(cp)
                checkpointDao.markAsSynced(listOf(cp.id))
                successCount++
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo sincronizar checkpoint ${cp.id}: ${e.message}")
            }
        }
        return successCount
    }

    private suspend fun syncPendingReports(): Int {
        val unsynced = reportDao.getUnsyncedReports()
        if (unsynced.isEmpty()) return 0

        var successCount = 0
        for (rep in unsynced) {
            try {
                pushReportToFirestore(rep)
                reportDao.markReportAsSynced(rep.id)
                successCount++
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo sincronizar informe ${rep.id}: ${e.message}")
            }
        }
        return successCount
    }

    private suspend fun pushCheckpointToFirestore(entity: CheckpointEntity) {
        firestore.collection(COLLECTION_CONDOMINIUMS)
            .document(DEFAULT_CONDOMINIUM_ID)
            .collection(SUBCOLLECTION_CHECKPOINTS)
            .document(entity.id)
            .set(entity.toFirestoreMap())
            .await()
    }

    private suspend fun pushReportToFirestore(entity: PatrolReportEntity) {
        firestore.collection(COLLECTION_CONDOMINIUMS)
            .document(DEFAULT_CONDOMINIUM_ID)
            .collection(SUBCOLLECTION_REPORTS)
            .document(entity.id)
            .set(entity.toFirestoreMap())
            .await()
    }

    private suspend fun updatePendingCounts() {
        val pendingCheckpoints = checkpointDao.getUnsyncedCount()
        val pendingReports = reportDao.getUnsyncedCount()
        _syncState.update {
            it.copy(
                pendingCheckpointsCount = pendingCheckpoints,
                pendingReportsCount = pendingReports
            )
        }
    }
}
