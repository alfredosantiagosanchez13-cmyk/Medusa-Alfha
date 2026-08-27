package com.example.data.sync

import android.content.Context
import com.example.data.audit.AuditLogEntity
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * FASE 19: MOTOR DE CONTINUIDAD OPERATIVA Y MODO OFFLINE ROBUSTO (MEDUSA ALFHA)
 * 
 * Reglas Innegociables:
 * 1. Room SQLite es la Fuente Única de Verdad local.
 * 2. Operación ininterrumpida sin conexión a internet.
 * 3. Idempotencia y protección estricta contra registros duplicados.
 * 4. Priorización absoluta de emergencias activas.
 * 5. Trazabilidad total con firma SHA-256 en AuditLogEntity.
 * 6. Contabilización automática de Tiempo Devuelto (TIEMPO = FAMILIA).
 */
object OfflineSyncEngine {

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isSyncInProgress = false

    /**
     * Inicializa el observador reactivo para sincronizar automáticamente al recuperar conexión.
     */
    fun initializeAutoSync(context: Context, db: AppDatabase) {
        val observer = NetworkConnectivityObserver.getInstance(context)
        engineScope.launch {
            observer.networkState.collect { state ->
                if (state.isConnected && state.hasInternetCapability && !state.isSimulatedOffline) {
                    val pendingCount = db.syncQueueDao().getPendingCount()
                    if (pendingCount > 0 && !isSyncInProgress) {
                        syncPendingOperations(context, db)
                    }
                }
            }
        }
    }

    /**
     * Encola una operación offline asegurando idempotencia y persistencia inmediata.
     * Retorna el folio de sincronización generado o existente.
     */
    suspend fun enqueueOperation(
        db: AppDatabase,
        operationType: String,
        targetFolio: String,
        targetModule: String,
        payloadJson: String,
        operatorName: String,
        operatorRole: String,
        locationName: String,
        latitude: Double? = null,
        longitude: Double? = null,
        evidencePaths: String? = null,
        deviceGateId: String = "Caseta 1 - Terminal Principal",
        customOperationId: String? = null
    ): String = withContext(Dispatchers.IO) {
        // Idempotencia: Verificar si ya existe una entrada pendiente o procesada para este targetFolio y tipo
        val existing = db.syncQueueDao().getByTargetFolio(targetFolio)
        if (existing != null) {
            return@withContext existing.syncFolio
        }

        val operationId = customOperationId ?: "OP-${UUID.randomUUID()}"
        val syncFolio = AlphaCoreEngine.generateUniqueFolio("SYN")
        val now = System.currentTimeMillis()

        // Tiempo devuelto estimado por no detener la operación ni requerir bitácora manual de papel
        val timeSavedSeconds = when (operationType) {
            "EMERGENCY_TRIGGER" -> 1200L // 20 min
            "INCIDENT_REGISTER" -> 600L  // 10 min
            "CHECK_IN", "CHECK_OUT" -> 180L // 3 min
            "SUPERVISION_TOUR" -> 300L  // 5 min
            "VEHICLE_ACCESS" -> 180L    // 3 min
            else -> 120L                 // 2 min
        }

        val entry = SyncQueueEntity(
            syncFolio = syncFolio,
            operationId = operationId,
            timestampMillis = now,
            operatorName = operatorName,
            operatorRole = operatorRole,
            operationType = operationType,
            targetFolio = targetFolio,
            targetModule = targetModule,
            payloadJson = payloadJson,
            locationName = locationName,
            latitude = latitude,
            longitude = longitude,
            evidencePaths = evidencePaths ?: "",
            status = "PENDIENTE",
            retryCount = 0,
            deviceGateId = deviceGateId,
            timeSavedSeconds = timeSavedSeconds,
            hashIntegrity = AlphaCoreEngine.computeIntegrityHash(syncFolio, operationId, operationType)
        )

        db.syncQueueDao().insertSyncEntry(entry)

        // Registro de Auditoría Inmutable
        val isEmg = entry.isEmergency
        db.auditLogDao().insertAuditLog(
            AuditLogEntity(
                folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                operatorName = "$operatorName ($operatorRole)",
                actionType = if (isEmg) "EMERGENCIA_ENCOLADA_OFFLINE" else "OPERACION_ENCOLADA_OFFLINE",
                location = locationName,
                targetEntity = targetFolio,
                changeDetails = "Operación $operationType encolada en Room para sincronización ($syncFolio). Módulo: $targetModule",
                resultStatus = if (isEmg) "CRITICO" else "EXITOSO",
                timestampMillis = now
            )
        )

        // Registro de Tiempo Devuelto por Continuidad Operativa Offline
        val minsSaved = (timeSavedSeconds / 60).toInt()
        db.auditLogDao().insertAuditLog(
            AuditLogEntity(
                folio = AlphaCoreEngine.generateUniqueFolio("TME"),
                operatorName = "ALFHA_OFFLINE_CONTINUITY",
                actionType = "TIEMPO_DEVUELTO_OFFLINE",
                location = locationName,
                targetEntity = targetFolio,
                changeDetails = "Ahorro de $minsSaved min por continuidad operativa offline sin papel, llamadas ni recaptura en $deviceGateId",
                resultStatus = "EXITOSO",
                timestampMillis = now
            )
        )

        return@withContext syncFolio
    }

    /**
     * Procesa todas las operaciones pendientes en la cola de sincronización.
     * Prioriza estrictamente EMERGENCIAS sobre cualquier otra transacción.
     */
    suspend fun syncPendingOperations(
        context: Context,
        db: AppDatabase
    ): SyncResult = withContext(Dispatchers.IO) {
        if (isSyncInProgress) {
            return@withContext SyncResult(success = true, syncedCount = 0, message = "Sincronización ya en curso.")
        }

        isSyncInProgress = true
        val observer = NetworkConnectivityObserver.getInstance(context)
        observer.setSynchronizing(true)

        val pending = db.syncQueueDao().getPendingSyncEntriesSync()
        if (pending.isEmpty()) {
            observer.setSynchronizing(false)
            isSyncInProgress = false
            return@withContext SyncResult(success = true, syncedCount = 0, message = "No hay operaciones pendientes de sincronización.")
        }

        var successCount = 0
        var failCount = 0
        val startTime = System.currentTimeMillis()

        try {
            for (entry in pending) {
                // Validación de integridad criptográfica antes de procesar
                val calculatedHash = AlphaCoreEngine.computeIntegrityHash(entry.syncFolio, entry.operationId, entry.operationType)
                if (calculatedHash != entry.hashIntegrity) {
                    val errorMsg = "Fallo de integridad SHA-256 en registro ${entry.syncFolio}. Posible alteración local."
                    db.syncQueueDao().recordError(entry.syncFolio, errorMsg)
                    db.auditLogDao().insertAuditLog(
                        AuditLogEntity(
                            folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                            operatorName = "SISTEMA_SINCRONIZACION",
                            actionType = "SYNC_ERROR_INTEGRIDAD",
                            location = entry.locationName,
                            targetEntity = entry.targetFolio,
                            changeDetails = errorMsg,
                            resultStatus = "DENEGADO",
                            timestampMillis = System.currentTimeMillis()
                        )
                    )
                    failCount++
                    continue
                }

                // Verificar si la conexión sigue activa antes de enviar
                val currentState = observer.networkState.value
                if (!currentState.isConnected || currentState.isSimulatedOffline) {
                    db.syncQueueDao().recordError(entry.syncFolio, "Sincronización interrumpida por pérdida de enlace de red.")
                    failCount++
                    break
                }

                // Marcar estado en sincronización
                db.syncQueueDao().updateStatus(entry.syncFolio, "SINCRONIZANDO")

                // Simular envío de enlace seguro a la red central ALFHA (con latencia no bloqueante de 50ms)
                delay(50)

                // Verificar si la conexión se interrumpió durante el envío
                val postSendState = observer.networkState.value
                if (!postSendState.isConnected || postSendState.isSimulatedOffline) {
                    db.syncQueueDao().recordError(entry.syncFolio, "Conexión interrumpida durante la transmisión de datos.")
                    failCount++
                    break
                }

                // Confirmar sincronización exitosa
                db.syncQueueDao().markSynced(entry.syncFolio)
                successCount++

                // Registrar en Auditoría
                db.auditLogDao().insertAuditLog(
                    AuditLogEntity(
                        folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                        operatorName = "${entry.operatorName} (${entry.operatorRole})",
                        actionType = if (entry.isEmergency) "SYNC_EMERGENCIA_EXITOSA" else "SYNC_OPERACION_EXITOSA",
                        location = entry.locationName,
                        targetEntity = entry.targetFolio,
                        changeDetails = "Operación ${entry.operationType} (${entry.syncFolio}) sincronizada con éxito en red central. Reintentos: ${entry.retryCount}",
                        resultStatus = "EXITOSO",
                        timestampMillis = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            failCount++
        } finally {
            observer.setSynchronizing(false)
            isSyncInProgress = false
        }

        val totalDuration = System.currentTimeMillis() - startTime
        val resultMessage = "Sincronización completada: $successCount exitosas, $failCount errores ($totalDuration ms)."

        return@withContext SyncResult(
            success = failCount == 0,
            syncedCount = successCount,
            failedCount = failCount,
            message = resultMessage
        )
    }

    data class SyncResult(
        val success: Boolean,
        val syncedCount: Int,
        val failedCount: Int = 0,
        val message: String
    )
}
