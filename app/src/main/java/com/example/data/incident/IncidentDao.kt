package com.example.data.incident

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * FASE 9: DAO DE CENTRO DE INCIDENCIAS ALFHA
 * Operaciones reactivas y transaccionales para el ciclo completo:
 * REGISTRADO ➔ EN_ATENCION ➔ RESUELTO ➔ CERRADO
 */
@Dao
interface IncidentDao {

    @Query("SELECT * FROM incidents ORDER BY timestampMillis DESC")
    fun getAllIncidentsFlow(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents ORDER BY timestampMillis DESC")
    suspend fun getAllIncidentsList(): List<IncidentEntity>

    @Query("SELECT * FROM incidents WHERE status = :status ORDER BY timestampMillis DESC")
    fun getIncidentsByStatusFlow(status: String): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE assignedRole = :role OR :role = 'ALL' ORDER BY timestampMillis DESC")
    fun getIncidentsByRoleFlow(role: String): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incidents WHERE folio = :folio LIMIT 1")
    suspend fun getIncidentByFolio(folio: String): IncidentEntity?

    @Query("SELECT * FROM incidents WHERE isEscalated = 1 AND status != 'CERRADO' ORDER BY timestampMillis DESC")
    fun getEscalatedIncidentsFlow(): Flow<List<IncidentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncident(incident: IncidentEntity)

    @Update
    suspend fun updateIncident(incident: IncidentEntity)

    @Query("""
        UPDATE incidents 
        SET status = 'EN_ATENCION', attendedBy = :attendedBy, attendedAtMillis = :attendedAtMillis 
        WHERE folio = :folio
    """)
    suspend fun transitionToAttention(
        folio: String,
        attendedBy: String,
        attendedAtMillis: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE incidents 
        SET status = 'RESUELTO', resolutionNotes = :notes, resolvedBy = :resolvedBy, resolvedAtMillis = :resolvedAtMillis 
        WHERE folio = :folio
    """)
    suspend fun transitionToResolved(
        folio: String,
        notes: String,
        resolvedBy: String,
        resolvedAtMillis: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE incidents 
        SET status = 'CERRADO', closureNotes = :closureNotes, closedBy = :closedBy, closedAtMillis = :closedAtMillis 
        WHERE folio = :folio
    """)
    suspend fun transitionToClosed(
        folio: String,
        closureNotes: String,
        closedBy: String,
        closedAtMillis: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE incidents 
        SET evidenceNotes = CASE 
            WHEN evidenceNotes IS NULL OR evidenceNotes = '' THEN :newEvidence 
            ELSE evidenceNotes || '\n• ' || :newEvidence 
        END 
        WHERE folio = :folio
    """)
    suspend fun appendEvidenceNotes(folio: String, newEvidence: String)

    @Query("""
        UPDATE incidents 
        SET isEscalated = 1, escalationReason = :reason, escalatedAtMillis = :escalatedAtMillis 
        WHERE folio = :folio
    """)
    suspend fun markAsEscalated(
        folio: String,
        reason: String,
        escalatedAtMillis: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE incidents 
        SET assignedTo = :newResponsible, assignedRole = :newRole 
        WHERE folio = :folio
    """)
    suspend fun reassignIncident(
        folio: String,
        newResponsible: String,
        newRole: String
    )

    @Query("UPDATE incidents SET status = :status, resolutionNotes = :notes, resolvedAtMillis = :resolvedAt WHERE folio = :folio")
    suspend fun resolveIncident(
        folio: String,
        status: String,
        notes: String?,
        resolvedAt: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE incidents 
        SET latitude = :lat, longitude = :lon, gpsAccuracyMeters = :accuracy, 
            locationStatus = :locationStatus, gpsTimestampMillis = :gpsTimestamp 
        WHERE folio = :folio
    """)
    suspend fun updateIncidentCoordinates(
        folio: String,
        lat: Double?,
        lon: Double?,
        accuracy: Float?,
        locationStatus: String,
        gpsTimestamp: Long = System.currentTimeMillis()
    )

    @Query("""
        SELECT * FROM incidents 
        WHERE (isEmergency = 1 OR priority = 'CRITICA' OR category = 'SEGURIDAD_EMERGENCIA') 
        AND status IN ('REGISTRADO', 'EN_ATENCION') 
        ORDER BY timestampMillis DESC
    """)
    fun getActiveEmergenciesFlow(): Flow<List<IncidentEntity>>

    @Query("""
        SELECT * FROM incidents 
        WHERE isEmergency = 1 OR priority = 'CRITICA' OR category = 'SEGURIDAD_EMERGENCIA' 
        ORDER BY timestampMillis DESC
    """)
    fun getAllEmergenciesFlow(): Flow<List<IncidentEntity>>

    @Query("SELECT COUNT(*) FROM incidents")
    suspend fun getIncidentCount(): Int

    @Query("SELECT COUNT(*) FROM incidents WHERE status != 'CERRADO' AND status != 'RESUELTO'")
    suspend fun getOpenIncidentsCount(): Int

    @Query("DELETE FROM incidents WHERE folio = :folio")
    suspend fun deleteIncident(folio: String)
}
