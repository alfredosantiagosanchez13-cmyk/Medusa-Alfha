package com.example.data.passes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QrPassDao {

    @Query("SELECT * FROM qr_passes ORDER BY createdAtMillis DESC")
    fun getAllPassesFlow(): Flow<List<QrPassRoomEntity>>

    @Query("SELECT * FROM qr_passes ORDER BY createdAtMillis DESC")
    suspend fun getAllPassesList(): List<QrPassRoomEntity>

    @Query("SELECT * FROM qr_passes WHERE passCode = :passCode LIMIT 1")
    suspend fun getPassByCode(passCode: String): QrPassRoomEntity?

    @Query("SELECT * FROM qr_passes WHERE destinationHouse = :house ORDER BY createdAtMillis DESC")
    fun getPassesByHouse(house: String): Flow<List<QrPassRoomEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPass(pass: QrPassRoomEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPasses(passes: List<QrPassRoomEntity>)

    @Update
    suspend fun updatePass(pass: QrPassRoomEntity)

    @Query("UPDATE qr_passes SET currentEntriesCount = currentEntriesCount + 1 WHERE passCode = :passCode")
    suspend fun incrementUsage(passCode: String)

    @Query("SELECT COUNT(*) FROM qr_passes")
    suspend fun getPassCount(): Int

    @Query("DELETE FROM qr_passes WHERE passCode = :passCode")
    suspend fun deletePass(passCode: String)

    @Query("DELETE FROM qr_passes")
    suspend fun deleteAllPasses()
}
