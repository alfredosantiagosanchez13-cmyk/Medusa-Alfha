package com.example.data.chat

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_guard_chat_logs")
data class AiGuardChatLog(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: String, // "USER" or "AI"
    val content: String,
    val activeRole: String, // "GUARD" or "ADMIN"
    val isAccessDenied: Boolean = false,
    val timestampMillis: Long = System.currentTimeMillis(),
    val operatorName: String = "Guardia de turno (Garita 1)",
    val isVoiceDictation: Boolean = false
)
