package com.example.data.resident

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ResidentDao {

    @Query("SELECT * FROM residents WHERE isDeleted = 0 ORDER BY unitId ASC, fullName ASC")
    fun getAllActiveResidentsFlow(): Flow<List<ResidentEntity>>

    @Query("SELECT * FROM residents ORDER BY isDeleted ASC, unitId ASC")
    fun getAllResidentsWithDeletedFlow(): Flow<List<ResidentEntity>>

    @Query("SELECT * FROM residents WHERE unitId = :unitId AND isDeleted = 0")
    fun getResidentsByUnitFlow(unitId: String): Flow<List<ResidentEntity>>

    @Query("SELECT * FROM residents WHERE unitId = :unitId AND isDeleted = 0")
    suspend fun getResidentsByUnit(unitId: String): List<ResidentEntity>

    @Query("SELECT * FROM residents WHERE id = :id LIMIT 1")
    suspend fun getResidentById(id: String): ResidentEntity?

    @Query("SELECT * FROM residents WHERE email = :email AND isDeleted = 0 LIMIT 1")
    suspend fun getResidentByEmail(email: String): ResidentEntity?

    @Query("SELECT * FROM residents WHERE phone = :phone AND isDeleted = 0 LIMIT 1")
    suspend fun getResidentByPhone(phone: String): ResidentEntity?

    @Query("""
        SELECT * FROM residents 
        WHERE (fullName LIKE '%' || :query || '%' 
           OR unitId LIKE '%' || :query || '%' 
           OR phone LIKE '%' || :query || '%' 
           OR email LIKE '%' || :query || '%' 
           OR vehiclesJson LIKE '%' || :query || '%'
           OR authorizedPersonsJson LIKE '%' || :query || '%')
        ORDER BY isDeleted ASC, unitId ASC
    """)
    fun searchResidentsFlow(query: String): Flow<List<ResidentEntity>>

    @Query("""
        SELECT * FROM residents 
        WHERE vehiclesJson LIKE '%' || :plate || '%' AND isDeleted = 0
    """)
    suspend fun findResidentsByVehiclePlate(plate: String): List<ResidentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResident(resident: ResidentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResidents(residents: List<ResidentEntity>)

    @Update
    suspend fun updateResident(resident: ResidentEntity)

    @Query("UPDATE residents SET isDeleted = 1, status = 'BAJA_LOGICA', updatedAtMillis = :timestamp, updatedBy = :operatorName WHERE id = :id")
    suspend fun softDeleteResident(id: String, operatorName: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE residents SET isDeleted = 0, status = 'ACTIVO', updatedAtMillis = :timestamp, updatedBy = :operatorName WHERE id = :id")
    suspend fun restoreResident(id: String, operatorName: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM residents WHERE isDeleted = 0")
    suspend fun getActiveResidentCount(): Int

    @Query("SELECT COUNT(*) FROM residents")
    suspend fun getTotalResidentCount(): Int
}

@Dao
interface UnitDao {

    @Query("SELECT * FROM residential_units ORDER BY blockOrTower ASC, unitId ASC")
    fun getAllUnitsFlow(): Flow<List<UnitEntity>>

    @Query("SELECT * FROM residential_units WHERE unitId = :unitId LIMIT 1")
    suspend fun getUnitById(unitId: String): UnitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnit(unit: UnitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnits(units: List<UnitEntity>)

    @Update
    suspend fun updateUnit(unit: UnitEntity)

    @Query("SELECT COUNT(*) FROM residential_units")
    suspend fun getUnitCount(): Int
}
