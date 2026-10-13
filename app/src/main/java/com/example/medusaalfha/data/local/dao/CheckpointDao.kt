package com.example.medusaalfha.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.medusaalfha.data.local.entity.CheckpointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CheckpointDao {

    @Query("SELECT * FROM checkpoints ORDER BY id ASC")
    fun getAllCheckpointsFlow(): Flow<List<CheckpointEntity>>

    @Query("SELECT * FROM checkpoints ORDER BY id ASC")
    suspend fun getAllCheckpoints(): List<CheckpointEntity>

    @Query("SELECT * FROM checkpoints WHERE isSynced = 0")
    suspend fun getUnsyncedCheckpoints(): List<CheckpointEntity>

    @Query("SELECT * FROM checkpoints WHERE id = :id LIMIT 1")
    suspend fun getCheckpointById(id: String): CheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckpoints(checkpoints: List<CheckpointEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckpoint(checkpoint: CheckpointEntity)

    @Query("UPDATE checkpoints SET isValidated = :isValidated, validatedAtMillis = :validatedAt, isSynced = :isSynced, lastUpdatedMillis = :updatedAt WHERE id = :checkpointId")
    suspend fun updateValidationStatus(
        checkpointId: String,
        isValidated: Boolean,
        validatedAt: Long?,
        isSynced: Boolean,
        updatedAt: Long
    )

    @Query("UPDATE checkpoints SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markAsSynced(ids: List<String>)

    @Query("UPDATE checkpoints SET isValidated = 0, validatedAtMillis = NULL, isSynced = :isSynced, lastUpdatedMillis = :now")
    suspend fun resetAllCircuit(now: Long, isSynced: Boolean = false)

    @Query("SELECT COUNT(*) FROM checkpoints")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM checkpoints WHERE isSynced = 0")
    suspend fun getUnsyncedCount(): Int
}
