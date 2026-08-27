package com.example.data.supervision

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SupervisionAuditDao {

    @Query("SELECT * FROM supervision_audits ORDER BY timestampMillis DESC")
    fun getAllAuditsFlow(): Flow<List<SupervisionAuditEntity>>

    @Query("SELECT * FROM supervision_audits ORDER BY timestampMillis DESC")
    suspend fun getAllAuditsList(): List<SupervisionAuditEntity>

    @Query("SELECT * FROM supervision_audits WHERE folio = :folio LIMIT 1")
    suspend fun getAuditByFolio(folio: String): SupervisionAuditEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudit(audit: SupervisionAuditEntity)

    @Update
    suspend fun updateAudit(audit: SupervisionAuditEntity)

    @Query("SELECT COUNT(*) FROM supervision_audits")
    suspend fun getAuditCount(): Int

    @Query("UPDATE supervision_audits SET isClosed = 1 WHERE folio = :folio")
    suspend fun closeAudit(folio: String)
}
