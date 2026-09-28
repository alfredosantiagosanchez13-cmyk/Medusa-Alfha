package com.example.data.passes

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.scanner.QrPassStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface QrPassDao {

    @Query("SELECT * FROM qr_passes ORDER BY createdAtMillis DESC")
    fun getAllPassesFlow(): Flow<List<QrPassRoomEntity>>

    @Query("SELECT * FROM qr_passes ORDER BY createdAtMillis DESC")
    suspend fun getAllPassesList(): List<QrPassRoomEntity>

    @Query("SELECT * FROM qr_passes WHERE passCode = :passCode LIMIT 1")
    suspend fun getPassByCode(passCode: String): QrPassRoomEntity?

    @Query("SELECT * FROM qr_passes WHERE destinationHouse = :house OR assignedUnit = :house ORDER BY createdAtMillis DESC")
    fun getPassesByHouse(house: String): Flow<List<QrPassRoomEntity>>

    @Query("SELECT * FROM qr_passes WHERE residentId = :residentId ORDER BY createdAtMillis DESC")
    fun getPassesByResidentId(residentId: String): Flow<List<QrPassRoomEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPass(pass: QrPassRoomEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPasses(passes: List<QrPassRoomEntity>)

    @Update
    suspend fun updatePass(pass: QrPassRoomEntity)

    @Query("UPDATE qr_passes SET currentEntriesCount = currentEntriesCount + 1 WHERE passCode = :passCode")
    suspend fun incrementUsage(passCode: String)

    @Query("UPDATE qr_passes SET status = :status WHERE passCode = :passCode")
    suspend fun updatePassStatus(passCode: String, status: QrPassStatus)

    @Query("UPDATE qr_passes SET status = 'VALIDADO' WHERE passCode = :passCode AND (status = 'EMITIDO' OR status = 'VALIDADO')")
    suspend fun markPassAsValidated(passCode: String)

    @Query("UPDATE qr_passes SET status = 'USADO', currentEntriesCount = currentEntriesCount + 1, isActive = 0 WHERE passCode = :passCode")
    suspend fun markPassAsUsedAndClose(passCode: String)

    @Query("UPDATE qr_passes SET status = 'EXPIRADO', isActive = 0 WHERE passCode = :passCode")
    suspend fun markPassAsExpired(passCode: String)

    @Query("UPDATE qr_passes SET status = 'RECHAZADO', isActive = 0 WHERE passCode = :passCode")
    suspend fun markPassAsRejected(passCode: String)

    @Query("UPDATE qr_passes SET status = 'CANCELADO', isActive = 0 WHERE passCode = :passCode")
    suspend fun cancelPass(passCode: String)

    @Query("UPDATE qr_passes SET isActive = 0 WHERE passCode = :passCode")
    suspend fun deactivatePass(passCode: String)

    @Query("SELECT COUNT(*) FROM qr_passes")
    suspend fun getPassCount(): Int

    @Query("DELETE FROM qr_passes WHERE passCode = :passCode")
    suspend fun deletePass(passCode: String)

    @Query("DELETE FROM qr_passes")
    suspend fun deleteAllPasses()
}
