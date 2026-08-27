package com.example.data.validation

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO para la gestión y persistencia del Checklist de Validación de Campo en Room SQLite.
 */
@Dao
interface FieldValidationDao {

    @Query("SELECT * FROM field_validation_tests ORDER BY orderIndex ASC")
    fun getAllTestsFlow(): Flow<List<FieldValidationTestEntity>>

    @Query("SELECT * FROM field_validation_tests ORDER BY orderIndex ASC")
    suspend fun getAllTests(): List<FieldValidationTestEntity>

    @Query("SELECT COUNT(*) FROM field_validation_tests")
    suspend fun getTestCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInitialTests(tests: List<FieldValidationTestEntity>)

    @Update
    suspend fun updateTest(test: FieldValidationTestEntity)

    @Query("UPDATE field_validation_tests SET status = :status, evidenceReference = :evidence, observations = :observations, updatedAtMillis = :timestamp WHERE testId = :testId")
    suspend fun updateTestResult(
        testId: String,
        status: String,
        evidence: String,
        observations: String,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE field_validation_tests SET status = 'PENDIENTE', evidenceReference = '', observations = '', updatedAtMillis = :timestamp")
    suspend fun resetAllTests(timestamp: Long = System.currentTimeMillis())
}
