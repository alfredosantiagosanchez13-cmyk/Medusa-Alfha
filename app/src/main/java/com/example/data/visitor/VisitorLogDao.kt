package com.example.data.visitor

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) para la persistencia local de la bitácora de visitantes en Room.
 */
@Dao
interface VisitorLogDao {

    @Query("SELECT * FROM visitor_logs ORDER BY arrivalTime DESC")
    fun getAllLogs(): Flow<List<VisitorLogEntity>>

    @Query("SELECT * FROM visitor_logs ORDER BY arrivalTime DESC")
    suspend fun getAllLogsList(): List<VisitorLogEntity>

    @Query("SELECT * FROM visitor_logs WHERE condominiumId = :condominiumId ORDER BY arrivalTime DESC")
    fun getLogsByCondominium(condominiumId: String): Flow<List<VisitorLogEntity>>

    @Query("SELECT * FROM visitor_logs WHERE folio = :folio LIMIT 1")
    suspend fun getByFolio(folio: String): VisitorLogEntity?

    @Query("SELECT * FROM visitor_logs WHERE isSynced = 0")
    suspend fun getUnsyncedLogs(): List<VisitorLogEntity>

    @Query("SELECT * FROM visitor_logs WHERE accessStatus = :status ORDER BY arrivalTime DESC")
    fun getLogsByStatus(status: String): Flow<List<VisitorLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: VisitorLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<VisitorLogEntity>)

    @Update
    suspend fun updateLog(log: VisitorLogEntity)

    @Query("UPDATE visitor_logs SET accessStatus = :status, departureTime = :departureTime, isSynced = 0 WHERE folio = :folio")
    suspend fun updateStatus(folio: String, status: String, departureTime: Long? = null)

    @Query("UPDATE visitor_logs SET isSynced = 1, lastSyncedAt = :syncedAt WHERE folio = :folio")
    suspend fun markAsSynced(folio: String, syncedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM visitor_logs WHERE folio = :folio")
    suspend fun deleteByFolio(folio: String)

    @Query("DELETE FROM visitor_logs")
    suspend fun deleteAllLogs()

    @Query("SELECT COUNT(*) FROM visitor_logs")
    suspend fun getLogCount(): Int

    @Query("SELECT COUNT(*) FROM visitor_logs WHERE accessStatus = 'CHECKED_IN' OR accessStatus = 'VERIFICADO'")
    suspend fun getActiveVisitorsCount(): Int
}
