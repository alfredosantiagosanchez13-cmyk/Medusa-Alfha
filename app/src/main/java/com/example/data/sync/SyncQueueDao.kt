package com.example.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * FASE 19: DAO de Cola de Sincronización Persistente en Room SQLite.
 */
@Dao
interface SyncQueueDao {

    @Query("SELECT * FROM sync_queue ORDER BY CASE WHEN operationType = 'EMERGENCY_TRIGGER' THEN 0 ELSE 1 END, timestampMillis ASC")
    fun getAllSyncEntriesFlow(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDIENTE' OR status = 'ERROR' OR status = 'SINCRONIZANDO' ORDER BY CASE WHEN operationType = 'EMERGENCY_TRIGGER' THEN 0 ELSE 1 END, timestampMillis ASC")
    fun getPendingSyncEntriesFlow(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDIENTE' OR status = 'ERROR' OR status = 'SINCRONIZANDO' ORDER BY CASE WHEN operationType = 'EMERGENCY_TRIGGER' THEN 0 ELSE 1 END, timestampMillis ASC")
    suspend fun getPendingSyncEntriesSync(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE operationId = :operationId LIMIT 1")
    suspend fun getByOperationId(operationId: String): SyncQueueEntity?

    @Query("SELECT * FROM sync_queue WHERE targetFolio = :targetFolio LIMIT 1")
    suspend fun getByTargetFolio(targetFolio: String): SyncQueueEntity?

    @Query("SELECT * FROM sync_queue WHERE syncFolio = :syncFolio LIMIT 1")
    suspend fun getBySyncFolio(syncFolio: String): SyncQueueEntity?

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDIENTE' OR status = 'ERROR'")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDIENTE' OR status = 'ERROR'")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'ERROR'")
    fun getErrorCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'SINCRONIZADO'")
    fun getSyncedCountFlow(): Flow<Int>

    @Query("SELECT MAX(lastAttemptMillis) FROM sync_queue WHERE status = 'SINCRONIZADO'")
    fun getLastSuccessfulSyncTimeFlow(): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSyncEntry(entry: SyncQueueEntity): Long

    @Update
    suspend fun updateSyncEntry(entry: SyncQueueEntity)

    @Query("UPDATE sync_queue SET status = :status, lastAttemptMillis = :attemptTime WHERE syncFolio = :syncFolio")
    suspend fun updateStatus(syncFolio: String, status: String, attemptTime: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET status = 'SINCRONIZADO', lastAttemptMillis = :attemptTime, errorMessage = null WHERE syncFolio = :syncFolio")
    suspend fun markSynced(syncFolio: String, attemptTime: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET status = 'ERROR', retryCount = retryCount + 1, lastAttemptMillis = :attemptTime, errorMessage = :errorMsg WHERE syncFolio = :syncFolio")
    suspend fun recordError(syncFolio: String, errorMsg: String, attemptTime: Long = System.currentTimeMillis())

    @Query("SELECT DISTINCT deviceGateId FROM sync_queue WHERE status = 'PENDIENTE' OR status = 'ERROR'")
    fun getDevicesWithPendingOperationsFlow(): Flow<List<String>>

    @Query("SELECT SUM(timeSavedSeconds) FROM sync_queue")
    fun getTotalTimeSavedSecondsFlow(): Flow<Long?>
}
