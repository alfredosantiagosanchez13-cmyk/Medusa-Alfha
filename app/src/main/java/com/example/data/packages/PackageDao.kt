package com.example.data.packages

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PackageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPackage(pkg: PackageEntity)

    @Update
    suspend fun updatePackage(pkg: PackageEntity)

    @Delete
    suspend fun deletePackage(pkg: PackageEntity)

    @Query("SELECT * FROM packages ORDER BY receivedTimestamp DESC")
    fun getAllPackagesFlow(): Flow<List<PackageEntity>>

    @Query("SELECT * FROM packages WHERE status != 'ENTREGADO' ORDER BY receivedTimestamp ASC")
    fun getPendingPackagesFlow(): Flow<List<PackageEntity>>

    @Query("SELECT * FROM packages WHERE status = 'ENTREGADO' ORDER BY deliveredTimestamp DESC")
    fun getDeliveredPackagesFlow(): Flow<List<PackageEntity>>

    @Query("SELECT * FROM packages WHERE unitId = :unitId ORDER BY receivedTimestamp DESC")
    fun getPackagesByUnitFlow(unitId: String): Flow<List<PackageEntity>>

    @Query("SELECT * FROM packages WHERE folio = :folio LIMIT 1")
    suspend fun getPackageByFolio(folio: String): PackageEntity?

    @Query("SELECT * FROM packages WHERE id = :id LIMIT 1")
    suspend fun getPackageById(id: String): PackageEntity?

    @Query("SELECT COUNT(*) FROM packages WHERE status != 'ENTREGADO'")
    fun countPendingPackagesFlow(): Flow<Int>

    @Query("SELECT * FROM packages ORDER BY receivedTimestamp DESC")
    suspend fun getAllPackagesList(): List<PackageEntity>

    @Query("SELECT * FROM packages WHERE status != 'ENTREGADO' ORDER BY receivedTimestamp ASC")
    suspend fun getPendingPackagesList(): List<PackageEntity>
}
