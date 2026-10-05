package com.example.medusaalfha.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AlertType {
    VISITOR_ARRIVAL,
    BOOKING_CONFIRMED,
    BOOKING_CANCELLED,
    SECURITY_PATROL,
    GENERAL_NOTICE
}

data class ResidentAlert(
    val id: String = "ALT-${System.currentTimeMillis() % 100000}",
    val type: AlertType = AlertType.VISITOR_ARRIVAL,
    val title: String = "",
    val message: String = "",
    val targetHouse: String = "Casa 54 · Circuito Los Álamos",
    val timestampMillis: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap(),
    val isRead: Boolean = false,
    val condominiumId: String = "PRADOS_1"
) {
    fun formattedTime(): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(timestampMillis))
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "type" to type.name,
            "title" to title,
            "message" to message,
            "targetHouse" to targetHouse,
            "timestampMillis" to timestampMillis,
            "metadata" to metadata,
            "isRead" to isRead,
            "condominiumId" to condominiumId
        )
    }

    companion object {
        fun fromMap(data: Map<String, Any?>): ResidentAlert {
            return ResidentAlert(
                id = data["id"] as? String ?: "",
                type = try {
                    AlertType.valueOf(data["type"] as? String ?: "VISITOR_ARRIVAL")
                } catch (_: Exception) {
                    AlertType.VISITOR_ARRIVAL
                },
                title = data["title"] as? String ?: "",
                message = data["message"] as? String ?: "",
                targetHouse = data["targetHouse"] as? String ?: "Casa 54",
                timestampMillis = (data["timestampMillis"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                metadata = (data["metadata"] as? Map<*, *>)?.mapNotNull { (k, v) ->
                    if (k is String && v is String) k to v else null
                }?.toMap() ?: emptyMap(),
                isRead = data["isRead"] as? Boolean ?: false,
                condominiumId = data["condominiumId"] as? String ?: "PRADOS_1"
            )
        }
    }
}
