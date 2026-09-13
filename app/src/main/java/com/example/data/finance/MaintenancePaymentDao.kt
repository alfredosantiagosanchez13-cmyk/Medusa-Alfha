package com.example.data.finance

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO para consulta y almacenamiento del histórico de cuotas y recibos de pago.
 */
@Dao
interface MaintenancePaymentDao {

    @Query("SELECT * FROM maintenance_payments WHERE unitId = :unitId ORDER BY paymentDateMillis DESC")
    fun getPaymentsForUnit(unitId: String): Flow<List<MaintenancePaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: MaintenancePaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<MaintenancePaymentEntity>)

    @Query("SELECT * FROM maintenance_payments WHERE receiptFolio = :folio LIMIT 1")
    suspend fun getPaymentByFolio(folio: String): MaintenancePaymentEntity?

    @Query("SELECT COUNT(*) FROM maintenance_payments WHERE unitId = :unitId")
    suspend fun getPaymentCountForUnit(unitId: String): Int
}
