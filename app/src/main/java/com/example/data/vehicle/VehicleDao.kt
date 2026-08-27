package com.example.data.vehicle

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    // ==========================================
    // VEHÍCULOS REGISTRADOS (PADRÓN VEHICULAR)
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: VehicleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicles(vehicles: List<VehicleEntity>)

    @Update
    suspend fun updateVehicle(vehicle: VehicleEntity)

    @Query("DELETE FROM vehicles WHERE plate = :plate")
    suspend fun deleteVehicle(plate: String)

    @Query("SELECT * FROM vehicles ORDER BY unitId ASC, plate ASC")
    fun getAllVehicles(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles ORDER BY unitId ASC, plate ASC")
    suspend fun getAllVehiclesList(): List<VehicleEntity>

    @Query("SELECT * FROM vehicles WHERE unitId = :unitId ORDER BY isPrimary DESC, plate ASC")
    fun getVehiclesByUnit(unitId: String): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE unitId = :unitId ORDER BY isPrimary DESC, plate ASC")
    suspend fun getVehiclesByUnitList(unitId: String): List<VehicleEntity>

    @Query("SELECT * FROM vehicles WHERE UPPER(REPLACE(plate, ' ', '')) = UPPER(REPLACE(:plate, ' ', '')) LIMIT 1")
    suspend fun getVehicleByPlate(plate: String): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE tagRfid = :tagRfid AND tagRfid != '' LIMIT 1")
    suspend fun getVehicleByTag(tagRfid: String): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE qrAccessCode = :qrCode AND qrAccessCode != '' LIMIT 1")
    suspend fun getVehicleByQr(qrCode: String): VehicleEntity?

    @Query("SELECT COUNT(*) FROM vehicles")
    suspend fun countTotalVehicles(): Int

    @Query("SELECT COUNT(*) FROM vehicles WHERE status = 'ACTIVO'")
    suspend fun countActiveVehicles(): Int

    // ==========================================
    // MOVIMIENTOS Y ACCESOS VEHICULARES
    // ==========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccessLog(log: VehicleAccessLogEntity)

    @Update
    suspend fun updateAccessLog(log: VehicleAccessLogEntity)

    @Query("SELECT * FROM vehicle_access_logs ORDER BY entryTimestampMillis DESC")
    fun getAllAccessLogs(): Flow<List<VehicleAccessLogEntity>>

    @Query("SELECT * FROM vehicle_access_logs ORDER BY entryTimestampMillis DESC")
    suspend fun getAllAccessLogsList(): List<VehicleAccessLogEntity>

    @Query("SELECT * FROM vehicle_access_logs WHERE unitId = :unitId ORDER BY entryTimestampMillis DESC")
    fun getAccessLogsByUnit(unitId: String): Flow<List<VehicleAccessLogEntity>>

    @Query("SELECT * FROM vehicle_access_logs WHERE UPPER(REPLACE(plate, ' ', '')) = UPPER(REPLACE(:plate, ' ', '')) ORDER BY entryTimestampMillis DESC")
    fun getAccessLogsByPlate(plate: String): Flow<List<VehicleAccessLogEntity>>

    @Query("SELECT * FROM vehicle_access_logs WHERE status = 'DENTRO_DEL_CONDOMINIO' ORDER BY entryTimestampMillis DESC")
    fun getVehiclesInside(): Flow<List<VehicleAccessLogEntity>>

    @Query("SELECT * FROM vehicle_access_logs WHERE status = 'DENTRO_DEL_CONDOMINIO' ORDER BY entryTimestampMillis DESC")
    suspend fun getVehiclesInsideList(): List<VehicleAccessLogEntity>

    @Query("SELECT * FROM vehicle_access_logs WHERE folio = :folio LIMIT 1")
    suspend fun getAccessLogByFolio(folio: String): VehicleAccessLogEntity?

    @Query("SELECT * FROM vehicle_access_logs WHERE UPPER(REPLACE(plate, ' ', '')) = UPPER(REPLACE(:plate, ' ', '')) AND status = 'DENTRO_DEL_CONDOMINIO' ORDER BY entryTimestampMillis DESC LIMIT 1")
    suspend fun getActiveInsideLogByPlate(plate: String): VehicleAccessLogEntity?

    @Query("SELECT COUNT(*) FROM vehicle_access_logs WHERE status = 'DENTRO_DEL_CONDOMINIO'")
    suspend fun countVehiclesInside(): Int

    @Query("SELECT COUNT(*) FROM vehicle_access_logs WHERE entryTimestampMillis >= :startOfDayMillis")
    suspend fun countEntriesSince(startOfDayMillis: Long): Int

    @Query("SELECT COUNT(*) FROM vehicle_access_logs WHERE exitTimestampMillis >= :startOfDayMillis")
    suspend fun countExitsSince(startOfDayMillis: Long): Int

    @Query("SELECT COUNT(*) FROM vehicle_access_logs WHERE isAuthorized = 0 OR accessCategory = 'VEHICULO_NO_AUTORIZADO'")
    suspend fun countUnauthorizedAccesses(): Int
}
