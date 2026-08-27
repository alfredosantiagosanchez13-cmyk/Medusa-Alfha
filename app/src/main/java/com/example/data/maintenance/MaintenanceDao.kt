package com.example.data.maintenance

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO para Órdenes de Trabajo y Mantenimiento (FASE 13).
 */
@Dao
interface MaintenanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: MaintenanceOrderEntity)

    @Update
    suspend fun updateOrder(order: MaintenanceOrderEntity)

    @Query("SELECT * FROM maintenance_orders ORDER BY timestampMillis DESC")
    fun getAllOrdersFlow(): Flow<List<MaintenanceOrderEntity>>

    @Query("SELECT * FROM maintenance_orders WHERE unitId = :unitId ORDER BY timestampMillis DESC")
    fun getOrdersByUnitFlow(unitId: String): Flow<List<MaintenanceOrderEntity>>

    @Query("SELECT * FROM maintenance_orders WHERE status != 'CERRADO' ORDER BY priority DESC, deadlineMillis ASC")
    fun getActiveOrdersFlow(): Flow<List<MaintenanceOrderEntity>>

    @Query("SELECT * FROM maintenance_orders WHERE folio = :folio LIMIT 1")
    suspend fun getOrderByFolio(folio: String): MaintenanceOrderEntity?

    @Query("SELECT COUNT(*) FROM maintenance_orders WHERE status != 'CERRADO'")
    fun getActiveCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM maintenance_orders WHERE status = 'RESUELTO' OR status = 'CERRADO'")
    fun getCompletedCountFlow(): Flow<Int>

    @Query("SELECT * FROM maintenance_orders")
    suspend fun getAllOrdersSnapshot(): List<MaintenanceOrderEntity>
}
