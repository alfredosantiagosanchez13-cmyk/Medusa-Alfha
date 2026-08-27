package com.example.data.alerts

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface OperationalAlertDao {
    @Query("SELECT * FROM operational_alerts ORDER BY CASE priorityLevel WHEN 'CRITICA' THEN 1 WHEN 'ALTA' THEN 2 WHEN 'PREVENTIVA' THEN 3 WHEN 'MEDIA' THEN 4 ELSE 5 END ASC, timestampMillis DESC")
    fun getAllAlerts(): Flow<List<OperationalAlertEntity>>

    @Query("SELECT * FROM operational_alerts WHERE status = 'ACTIVA' OR status = 'EN_ATENCION' OR status = 'EN_REVISION' OR status = 'CONFIRMADA' ORDER BY CASE priorityLevel WHEN 'CRITICA' THEN 1 WHEN 'ALTA' THEN 2 WHEN 'PREVENTIVA' THEN 3 WHEN 'MEDIA' THEN 4 ELSE 5 END ASC, timestampMillis DESC")
    fun getActiveAlerts(): Flow<List<OperationalAlertEntity>>

    @Query("SELECT * FROM operational_alerts WHERE status = 'ACTIVA' OR status = 'EN_ATENCION' OR status = 'EN_REVISION' OR status = 'CONFIRMADA'")
    suspend fun getActiveAlertsSync(): List<OperationalAlertEntity>

    @Query("SELECT * FROM operational_alerts ORDER BY timestampMillis DESC")
    suspend fun getAllAlertsSync(): List<OperationalAlertEntity>

    @Query("SELECT * FROM operational_alerts WHERE folio = :folio LIMIT 1")
    suspend fun getAlertByFolio(folio: String): OperationalAlertEntity?

    @Query("SELECT COUNT(*) FROM operational_alerts WHERE status = 'ACTIVA' OR status = 'EN_REVISION' OR status = 'CONFIRMADA'")
    fun getActiveAlertCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM operational_alerts WHERE (status = 'ACTIVA' OR status = 'EN_REVISION' OR status = 'CONFIRMADA') AND priorityLevel = 'CRITICA'")
    fun getCriticalActiveAlertCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlert(alert: OperationalAlertEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlerts(alerts: List<OperationalAlertEntity>)

    @Update
    suspend fun updateAlert(alert: OperationalAlertEntity)

    @Query("UPDATE operational_alerts SET status = :status, resolvedBy = :operator, resolutionNotes = :notes, resolvedAtMillis = :resolvedTime WHERE folio = :folio")
    suspend fun resolveAlert(folio: String, status: String, operator: String, notes: String, resolvedTime: Long = System.currentTimeMillis())

    @Query("UPDATE operational_alerts SET status = 'EN_ATENCION', resolvedBy = :operator, resolutionNotes = :notes WHERE folio = :folio")
    suspend fun markInAttention(folio: String, operator: String, notes: String = "En atención por personal operativo")

    @Query("UPDATE operational_alerts SET status = 'EN_REVISION', resolvedBy = :operator, resolutionNotes = :notes WHERE folio = :folio")
    suspend fun markInRevision(folio: String, operator: String, notes: String = "En revisión por supervisión")

    @Query("UPDATE operational_alerts SET status = 'CONFIRMADA', resolvedBy = :operator, resolutionNotes = :notes WHERE folio = :folio")
    suspend fun markConfirmed(folio: String, operator: String, notes: String = "Anomalía confirmada por supervisión")

    @Query("UPDATE operational_alerts SET status = 'DESCARTADA', resolvedBy = :operator, resolutionNotes = :notes, resolvedAtMillis = :resolvedTime WHERE folio = :folio")
    suspend fun markDiscarded(folio: String, operator: String, notes: String = "Anomalía descartada tras verificación", resolvedTime: Long = System.currentTimeMillis())

    @Query("UPDATE operational_alerts SET status = :newStatus, resolvedBy = :operator, resolutionNotes = :notes, resolvedAtMillis = :updatedTime WHERE folio = :folio")
    suspend fun updateAlertStatus(folio: String, newStatus: String, operator: String, notes: String, updatedTime: Long = System.currentTimeMillis())
}
