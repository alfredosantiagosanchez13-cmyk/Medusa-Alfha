package com.example.data.audit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditLogDao {

    @Query("SELECT * FROM audit_logs ORDER BY timestampMillis DESC")
    fun getAllAuditLogsFlow(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs ORDER BY timestampMillis DESC LIMIT :limit")
    suspend fun getRecentAuditLogs(limit: Int = 100): List<AuditLogEntity>

    @Query("SELECT * FROM audit_logs WHERE folio = :folio LIMIT 1")
    suspend fun getAuditByFolio(folio: String): AuditLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Query("SELECT COUNT(*) FROM audit_logs")
    suspend fun getAuditLogCount(): Int
}
