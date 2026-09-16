package com.example.data.visitor

import android.util.Log
import com.example.data.core.AlphaCoreEngine
import com.example.data.firebase.FirebaseConfigHelper
import com.example.data.firebase.FirestoreTenantManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Resultado de una operación de sincronización bidireccional de Visitor Logs.
 */
data class VisitorLogSyncResult(
    val pushedCount: Int = 0,
    val pulledCount: Int = 0,
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)

/**
 * Repositorio de alta resiliencia para la Bitácora de Visitantes (Visitor Logs).
 *
 * Sincroniza bidireccionalmente los modelos de datos entre Room (SQLite local para funcionamiento offline)
 * y Cloud Firestore (/condominiums/{condoId}/visitor_logs/{folio}) para auditoría y monitoreo remoto.
 *
 * Contiene y gestiona explícitamente los campos requeridos:
 * - visitorName: Nombre completo del visitante.
 * - arrivalTime: Marca temporal de llegada (milisegundos / Timestamp).
 * - accessStatus: Estado de acceso (e.g., "CHECKED_IN", "DEPARTED", "VERIFICADO", "DENEGADO", "PENDIENTE").
 */
class VisitorLogRepository(
    private val visitorLogDao: VisitorLogDao,
    private val firestoreProvider: () -> FirebaseFirestore? = { FirebaseConfigHelper.getFirestore() },
    var activeCondominiumId: String = "PRADOS_1"
) {

    companion object {
        private const val TAG = "VisitorLogRepository"
    }

    /**
     * Flujo reactivo local de todos los registros de visitantes ordenados por arrivalTime descendente.
     */
    val allLogs: Flow<List<VisitorLogEntity>> = visitorLogDao.getAllLogs()

    /**
     * Flujo reactivo de registros filtrados por condominio.
     */
    fun getLogsByCondo(condoId: String = activeCondominiumId): Flow<List<VisitorLogEntity>> {
        return visitorLogDao.getLogsByCondominium(condoId)
    }

    /**
     * Flujo reactivo de registros filtrados por estado de acceso.
     */
    fun getLogsByStatus(status: String): Flow<List<VisitorLogEntity>> {
        return visitorLogDao.getLogsByStatus(status)
    }

    /**
     * Registra un nuevo ingreso de visitante en Room y lo sincroniza de inmediato a Firestore.
     * Si no hay red, permanece guardado localmente con isSynced = false para posterior sincronización.
     */
    suspend fun recordVisitorArrival(
        visitorName: String,
        arrivalTime: Long = System.currentTimeMillis(),
        accessStatus: String = "CHECKED_IN",
        authorizedUnitNumber: String = "",
        visitorDocument: String = "Sin Documento",
        passCode: String = "",
        passTypeLabel: String = "Visita General",
        vehiclePlate: String? = null,
        guardName: String = "Agente Caseta",
        guardNotes: String? = null,
        hostResidentName: String = "Residente",
        residentNotes: String? = null,
        condominiumId: String = activeCondominiumId
    ): Result<VisitorLogEntity> = withContext(Dispatchers.IO) {
        try {
            val folio = AlphaCoreEngine.generateUniqueFolio("VIS")
            val entity = VisitorLogEntity(
                id = 0,
                folio = folio,
                visitorName = visitorName.trim(),
                arrivalTime = arrivalTime,
                accessStatus = accessStatus.trim(),
                authorizedUnitNumber = authorizedUnitNumber.trim(),
                condominiumId = condominiumId.trim(),
                visitorDocument = visitorDocument.trim(),
                passCode = passCode.trim(),
                passTypeLabel = passTypeLabel.trim(),
                vehiclePlate = vehiclePlate?.trim()?.ifBlank { null },
                guardName = guardName.trim(),
                guardNotes = guardNotes?.trim()?.ifBlank { null },
                hostResidentName = hostResidentName.trim(),
                residentNotes = residentNotes?.trim()?.ifBlank { null },
                departureTime = null,
                isSynced = false,
                lastSyncedAt = null
            )

            val insertedId = visitorLogDao.insertLog(entity)
            val savedEntity = entity.copy(id = insertedId)

            // Intentar sincronizar en la nube de inmediato
            val synced = syncSingleLogToFirestore(savedEntity, condominiumId)
            val finalEntity = if (synced) {
                val now = System.currentTimeMillis()
                visitorLogDao.markAsSynced(savedEntity.folio, now)
                savedEntity.copy(isSynced = true, lastSyncedAt = now)
            } else {
                savedEntity
            }

            Result.success(finalEntity)
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando ingreso de visitante: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Registra la salida de un visitante actualizando departureTime y accessStatus = "DEPARTED".
     */
    suspend fun recordVisitorDeparture(
        folio: String,
        departureTime: Long = System.currentTimeMillis(),
        departureNotes: String? = null,
        condominiumId: String = activeCondominiumId
    ): Result<VisitorLogEntity> = withContext(Dispatchers.IO) {
        try {
            val existing = visitorLogDao.getByFolio(folio)
                ?: return@withContext Result.failure(Exception("No se encontró el registro con folio $folio"))

            val updatedNotes = if (departureNotes.isNullOrBlank()) {
                existing.guardNotes
            } else {
                "${existing.guardNotes ?: ""}\n[Salida]: $departureNotes".trim()
            }

            val updatedEntity = existing.copy(
                accessStatus = "DEPARTED",
                departureTime = departureTime,
                guardNotes = updatedNotes,
                isSynced = false
            )

            visitorLogDao.insertLog(updatedEntity)

            // Sincronizar actualización a Firestore
            val synced = syncSingleLogToFirestore(updatedEntity, condominiumId)
            val finalEntity = if (synced) {
                val now = System.currentTimeMillis()
                visitorLogDao.markAsSynced(updatedEntity.folio, now)
                updatedEntity.copy(isSynced = true, lastSyncedAt = now)
            } else {
                updatedEntity
            }

            Result.success(finalEntity)
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando salida de visitante: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Actualiza el estado de acceso de un registro (e.g., a "VERIFICADO", "DENEGADO", etc.).
     */
    suspend fun updateAccessStatus(
        folio: String,
        newStatus: String,
        condominiumId: String = activeCondominiumId
    ): Result<VisitorLogEntity> = withContext(Dispatchers.IO) {
        try {
            val existing = visitorLogDao.getByFolio(folio)
                ?: return@withContext Result.failure(Exception("No se encontró el registro con folio $folio"))

            val updated = existing.copy(
                accessStatus = newStatus,
                isSynced = false
            )
            visitorLogDao.insertLog(updated)

            val synced = syncSingleLogToFirestore(updated, condominiumId)
            val finalEntity = if (synced) {
                val now = System.currentTimeMillis()
                visitorLogDao.markAsSynced(updated.folio, now)
                updated.copy(isSynced = true, lastSyncedAt = now)
            } else {
                updated
            }

            Result.success(finalEntity)
        } catch (e: Exception) {
            Log.e(TAG, "Error actualizando estado de acceso: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Sube un solo registro a Firestore. Retorna true si tuvo éxito, false si falló o no hay red.
     */
    suspend fun syncSingleLogToFirestore(
        log: VisitorLogEntity,
        condominiumId: String = activeCondominiumId
    ): Boolean = withContext(Dispatchers.IO) {
        val fs = firestoreProvider() ?: return@withContext false
        try {
            val firestoreModel = log.toFirestoreModel()
            val validCondoId = FirestoreTenantManager.validateCondominiumId(condominiumId)
            val subcollection = FirestoreTenantManager.getTenantSubcollection(
                fs,
                validCondoId,
                FirestoreTenantManager.SUB_VISITOR_LOGS
            )

            val payload = firestoreModel.toMap().toMutableMap().apply {
                put("condominiumId", validCondoId)
                put("visitorName", log.visitorName)
                put("arrivalTime", log.arrivalTime)
                put("accessStatus", log.accessStatus)
            }

            subcollection.document(log.folio)
                .set(payload, SetOptions.merge())
                .await()

            Log.i(TAG, "Registro de visitante ${log.folio} sincronizado con Firestore exitosamente.")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Fallo al sincronizar registro ${log.folio} a Firestore: ${e.message}")
            false
        }
    }

    /**
     * Sube a Firestore todos los registros locales que tienen isSynced = false.
     * Retorna el número de registros exitosamente sincronizados.
     */
    suspend fun pushUnsyncedLogs(condominiumId: String = activeCondominiumId): Int = withContext(Dispatchers.IO) {
        val unsynced = visitorLogDao.getUnsyncedLogs()
        if (unsynced.isEmpty()) return@withContext 0

        var count = 0
        for (log in unsynced) {
            val success = syncSingleLogToFirestore(log, condominiumId)
            if (success) {
                visitorLogDao.markAsSynced(log.folio, System.currentTimeMillis())
                count++
            }
        }
        count
    }

    /**
     * Descarga registros desde Firestore y los concilia en la base de datos local de Room.
     * Retorna el número de registros nuevos o actualizados.
     */
    suspend fun pullRemoteLogs(
        condominiumId: String = activeCondominiumId,
        limitCount: Long = 100
    ): Result<Int> = withContext(Dispatchers.IO) {
        val fs = firestoreProvider()
            ?: return@withContext Result.failure(Exception("Firestore no disponible en este dispositivo"))

        try {
            val remoteResult = FirestoreTenantManager.queryVisitorLogs(fs, condominiumId, limitCount)
            if (remoteResult.isSuccess) {
                val remoteLogs = remoteResult.getOrNull() ?: emptyList()
                var updatedCount = 0

                for (remote in remoteLogs) {
                    val existing = visitorLogDao.getByFolio(remote.folio)
                    val localId = existing?.id ?: 0L

                    // Solo actualizar si no existe o si los datos en la nube son más recientes/diferentes
                    // y el registro local no tiene cambios pendientes de subir (isSynced == true)
                    val canOverwrite = existing == null || existing.isSynced

                    if (canOverwrite) {
                        val mapped = remote.toVisitorLogEntity(localId = localId).copy(
                            isSynced = true,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                        visitorLogDao.insertLog(mapped)
                        updatedCount++
                    }
                }

                Result.success(updatedCount)
            } else {
                Result.failure(remoteResult.exceptionOrNull() ?: Exception("Error consultando logs remotos"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando logs desde Firestore: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Ejecuta una sincronización bidireccional completa (Push cambios locales -> Pull cambios remotos).
     */
    suspend fun syncAll(condominiumId: String = activeCondominiumId): VisitorLogSyncResult = withContext(Dispatchers.IO) {
        val fs = firestoreProvider()
        if (fs == null) {
            return@withContext VisitorLogSyncResult(
                pushedCount = 0,
                pulledCount = 0,
                isSuccess = false,
                errorMessage = "Servicio de Firestore no disponible. Operando en modo local fuera de línea."
            )
        }

        try {
            // 1. Push
            val pushed = pushUnsyncedLogs(condominiumId)

            // 2. Pull
            val pullResult = pullRemoteLogs(condominiumId)
            val pulled = pullResult.getOrDefault(0)

            VisitorLogSyncResult(
                pushedCount = pushed,
                pulledCount = pulled,
                isSuccess = pullResult.isSuccess,
                errorMessage = pullResult.exceptionOrNull()?.message
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error en ciclo completo de sincronización: ${e.message}", e)
            VisitorLogSyncResult(
                pushedCount = 0,
                pulledCount = 0,
                isSuccess = false,
                errorMessage = e.message
            )
        }
    }

    /**
     * Escucha reactiva en tiempo real de Firestore, actualizando Room conforme llegan cambios de la nube.
     */
    fun observeRemoteRealtime(condominiumId: String = activeCondominiumId): Flow<List<VisitorLogEntity>> = callbackFlow {
        val fs = firestoreProvider()
        if (fs == null) {
            channel.close()
            return@callbackFlow
        }

        val validCondoId = FirestoreTenantManager.validateCondominiumId(condominiumId)
        val subcollection = FirestoreTenantManager.getTenantSubcollection(
            fs,
            validCondoId,
            FirestoreTenantManager.SUB_VISITOR_LOGS
        )

        var registration: ListenerRegistration? = null
        try {
            registration = subcollection.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "SnapshotListener error: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val logs = snapshot.documents.mapNotNull { doc ->
                        FirestoreVisitorLog.fromDocumentSnapshot(doc, validCondoId)?.toVisitorLogEntity()
                    }
                    trySend(logs)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando snapshot listener: ${e.message}")
        }

        awaitClose {
            registration?.remove()
        }
    }

    /**
     * Sembrado de datos iniciales en caso de estar vacía la tabla local.
     */
    suspend fun seedInitialLogsIfEmpty(condominiumId: String = activeCondominiumId) = withContext(Dispatchers.IO) {
        if (visitorLogDao.getLogCount() == 0) {
            val now = System.currentTimeMillis()
            val sampleLogs = listOf(
                VisitorLogEntity(
                    folio = AlphaCoreEngine.generateUniqueFolio("VIS"),
                    visitorName = "Valeria Sofía Mendoza",
                    arrivalTime = now - (60 * 60 * 1000), // Hace 1 hora
                    accessStatus = "DEPARTED",
                    authorizedUnitNumber = "Casa #104",
                    condominiumId = condominiumId,
                    visitorDocument = "18.492.301-2",
                    passCode = "PASS-101",
                    passTypeLabel = "Visita Ocasional",
                    vehiclePlate = "KXYZ-98",
                    guardName = "Agente #402 - Garita 1",
                    guardNotes = "Ingreso autorizado y salida registrada",
                    hostResidentName = "Carlos Mendoza",
                    departureTime = now - (10 * 60 * 1000),
                    isSynced = false
                ),
                VisitorLogEntity(
                    folio = AlphaCoreEngine.generateUniqueFolio("VIS"),
                    visitorName = "Marcos Esteban Ríos",
                    arrivalTime = now - (20 * 60 * 1000), // Hace 20 min
                    accessStatus = "CHECKED_IN",
                    authorizedUnitNumber = "Casa #208",
                    condominiumId = condominiumId,
                    visitorDocument = "16.123.890-K",
                    passCode = "PASS-102",
                    passTypeLabel = "Delivery / Uber Eats",
                    vehiclePlate = "DLPR-44",
                    guardName = "Agente #402 - Garita 1",
                    guardNotes = "En condominio entregando pedido",
                    hostResidentName = "Ana María Gómez",
                    departureTime = null,
                    isSynced = false
                ),
                VisitorLogEntity(
                    folio = AlphaCoreEngine.generateUniqueFolio("VIS"),
                    visitorName = "Gonzalo Inostroza",
                    arrivalTime = now - (40 * 60 * 1000),
                    accessStatus = "CHECKED_IN",
                    authorizedUnitNumber = "Casa #115",
                    condominiumId = condominiumId,
                    visitorDocument = "15.990.112-9",
                    passCode = "PASS-104",
                    passTypeLabel = "Técnico Fibra Óptica",
                    vehiclePlate = "BCDF-12",
                    guardName = "Agente #402 - Garita 1",
                    guardNotes = "Mantenimiento programado",
                    hostResidentName = "Patricia Soto",
                    departureTime = null,
                    isSynced = false
                ),
                VisitorLogEntity(
                    folio = AlphaCoreEngine.generateUniqueFolio("VIS"),
                    visitorName = "Rodrigo San Martín",
                    arrivalTime = now - (5 * 60 * 1000),
                    accessStatus = "VERIFICADO",
                    authorizedUnitNumber = "Casa #501",
                    condominiumId = condominiumId,
                    visitorDocument = "17.430.881-5",
                    passCode = "PASS-202",
                    passTypeLabel = "Servicio Técnico",
                    vehiclePlate = "HPWL-88",
                    guardName = "Agente #402 - Garita 1",
                    guardNotes = "Verificación en proceso en caseta",
                    hostResidentName = "Familia San Martín",
                    departureTime = null,
                    isSynced = false
                )
            )

            sampleLogs.forEach { log ->
                val id = visitorLogDao.insertLog(log)
                // Intentar sync en segundo plano si hay Firestore disponible
                syncSingleLogToFirestore(log.copy(id = id), condominiumId)
            }
        }
    }

    suspend fun getActiveVisitorsCount(): Int {
        return visitorLogDao.getActiveVisitorsCount()
    }

    suspend fun getLogCount(): Int {
        return visitorLogDao.getLogCount()
    }
}
