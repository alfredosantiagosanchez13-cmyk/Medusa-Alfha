package com.example.data.visitor

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO para la trazabilidad y consulta reactiva del Historial de Visitantes por Unidad.
 */
@Dao
interface VisitorPassDao {

    @Query("SELECT * FROM unit_visitor_history WHERE destinationHouse = :house ORDER BY timestampMillis DESC")
    fun getVisitorHistoryFlow(house: String): Flow<List<VisitorPassEntity>>

    @Query("SELECT * FROM unit_visitor_history ORDER BY timestampMillis DESC")
    fun getAllVisitorHistoryFlow(): Flow<List<VisitorPassEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPass(pass: VisitorPassEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPasses(passes: List<VisitorPassEntity>)

    @Query("SELECT COUNT(*) FROM unit_visitor_history WHERE destinationHouse = :house")
    suspend fun getCountByHouse(house: String): Int

    @Query("DELETE FROM unit_visitor_history WHERE destinationHouse = :house")
    suspend fun clearHistoryForHouse(house: String)
}
