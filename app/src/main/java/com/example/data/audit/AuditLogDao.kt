package com.example.data.audit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO de Auditoría de compatibilidad para los módulos del sistema MEDUSA.
 * Consulta la tabla inmutable [medusa_audit_logs].
 */
@Dao
interface AuditLogDao {

    @Query("SELECT * FROM medusa_audit_logs ORDER BY timestamp DESC")
    fun getAllAuditLogsFlow(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM medusa_audit_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentAuditLogs(limit: Int = 100): List<AuditLogEntity>

    @Query("SELECT * FROM medusa_audit_logs WHERE logId = :folio LIMIT 1")
    suspend fun getAuditByFolio(folio: String): AuditLogEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAuditLog(log: AuditLogEntity)

    @Query("SELECT COUNT(*) FROM medusa_audit_logs")
    suspend fun getAuditLogCount(): Int
}
