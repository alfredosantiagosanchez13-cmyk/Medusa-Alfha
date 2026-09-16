package com.example.data.audit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) de Seguridad Operativa y Forense para MEDUSA ALFHA.
 *
 * Filosofía de Persistencia Inmutable:
 * - Inserción estricta con [OnConflictStrategy.ABORT]: si existe una colisión o alteración de PK, aborta la transacción.
 * - Incorruptible: No se exponen métodos `@Update` ni `@Delete`. Los registros una vez asentados son permanentes.
 * - Flujo Reactivo Asíncrono: Consulta `Flow<List<AuditLogEntity>>` sin bloqueo de UI para supervisión en tiempo real.
 */
@Dao
interface SecurityAuditDao {

    /**
     * Inserción inmutable de un registro de auditoría forense.
     * Utiliza [OnConflictStrategy.ABORT] para garantizar que ningún registro sea sobreescrito ni mutado.
     */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLog(log: AuditLogEntity)

    /**
     * Consulta reactiva en tiempo real de los últimos 50 eventos forenses.
     * Diseñada para paneles de supervisión táctica, administración y dashboards en vivo sin bloquear el hilo principal.
     */
    @Query("SELECT * FROM medusa_audit_logs ORDER BY timestamp DESC LIMIT 50")
    fun getLiveAuditLogs(): Flow<List<AuditLogEntity>>

    /**
     * Consulta de eventos por nivel de severidad (ej. SECURITY_CRITICAL para incidentes de intrusión).
     */
    @Query("SELECT * FROM medusa_audit_logs WHERE severity = :severity ORDER BY timestamp DESC LIMIT 100")
    fun getLogsBySeverity(severity: String): Flow<List<AuditLogEntity>>

    /**
     * Búsqueda forense por identificador único de log.
     */
    @Query("SELECT * FROM medusa_audit_logs WHERE logId = :logId LIMIT 1")
    suspend fun getLogById(logId: String): AuditLogEntity?

    /**
     * Conteo total de registros forenses en la terminal.
     */
    @Query("SELECT COUNT(*) FROM medusa_audit_logs")
    suspend fun getTotalLogCount(): Int
}
