package com.example.medusaalfha.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.medusaalfha.data.local.entity.PatrolReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PatrolReportDao {

    @Query("SELECT * FROM patrol_reports ORDER BY timestampMillis DESC")
    fun getAllReportsFlow(): Flow<List<PatrolReportEntity>>

    @Query("SELECT * FROM patrol_reports ORDER BY timestampMillis DESC")
    suspend fun getAllReports(): List<PatrolReportEntity>

    @Query("SELECT * FROM patrol_reports WHERE isSynced = 0")
    suspend fun getUnsyncedReports(): List<PatrolReportEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: PatrolReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReports(reports: List<PatrolReportEntity>)

    @Query("UPDATE patrol_reports SET isSynced = 1 WHERE id = :reportId")
    suspend fun markReportAsSynced(reportId: String)

    @Query("UPDATE patrol_reports SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markReportsAsSynced(ids: List<String>)

    @Query("SELECT COUNT(*) FROM patrol_reports WHERE isSynced = 0")
    suspend fun getUnsyncedCount(): Int
}
