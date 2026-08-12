package com.example.data.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiGuardChatLogDao {

    @Query("SELECT * FROM ai_guard_chat_logs ORDER BY timestampMillis ASC")
    fun getAllChatLogs(): Flow<List<AiGuardChatLog>>

    @Query("SELECT * FROM ai_guard_chat_logs WHERE activeRole = :role ORDER BY timestampMillis ASC")
    fun getChatLogsByRole(role: String): Flow<List<AiGuardChatLog>>

    @Query("SELECT * FROM ai_guard_chat_logs WHERE isAccessDenied = 1 ORDER BY timestampMillis DESC")
    fun getAccessDeniedLogs(): Flow<List<AiGuardChatLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatLog(log: AiGuardChatLog)

    @Query("DELETE FROM ai_guard_chat_logs")
    suspend fun clearAllChatLogs()

    @Query("SELECT * FROM ai_guard_chat_logs WHERE content LIKE '%' || :searchQuery || '%' OR operatorName LIKE '%' || :searchQuery || '%' ORDER BY timestampMillis DESC")
    fun searchChatLogs(searchQuery: String): Flow<List<AiGuardChatLog>>
}
