package com.example.data.visitor

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitorCheckInDao {
    @Query("SELECT * FROM visitor_check_ins ORDER BY timestampMillis DESC")
    fun getAllCheckIns(): Flow<List<VisitorCheckIn>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckIn(checkIn: VisitorCheckIn): Long

    @Query("UPDATE visitor_check_ins SET status = :status, guardNotes = :notes WHERE id = :id")
    suspend fun updateCheckInStatus(id: Long, status: String, notes: String?)

    @Query("UPDATE visitor_check_ins SET residentNotes = :notes WHERE id = :id")
    suspend fun updateResidentNotes(id: Long, notes: String?)

    @Query("DELETE FROM visitor_check_ins WHERE id = :id")
    suspend fun deleteCheckInById(id: Long)

    @Query("DELETE FROM visitor_check_ins")
    suspend fun deleteAllCheckIns()

    @Query("SELECT COUNT(*) FROM visitor_check_ins")
    suspend fun getCheckInCount(): Int
}
