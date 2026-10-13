package com.example.medusaalfha.data.model

data class ResidentAlert(
    val id: String = "",
    val type: AlertType = AlertType.VISITOR_ARRIVAL,
    val title: String = "",
    val message: String = "",
    val targetHouse: String = "",
    val timestampMillis: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap(),
    val isRead: Boolean = false
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "type" to type.name,
        "title" to title,
        "message" to message,
        "targetHouse" to targetHouse,
        "timestampMillis" to timestampMillis,
        "metadata" to metadata,
        "isRead" to isRead
    )

    companion object {
        fun fromMap(map: Map<String, Any>): ResidentAlert {
            val typeStr = map["type"] as? String ?: AlertType.VISITOR_ARRIVAL.name
            val typeEnum = try { AlertType.valueOf(typeStr) } catch (_: Exception) { AlertType.VISITOR_ARRIVAL }
            @Suppress("UNCHECKED_CAST")
            val meta = (map["metadata"] as? Map<String, String>) ?: emptyMap()
            return ResidentAlert(
                id = map["id"] as? String ?: "",
                type = typeEnum,
                title = map["title"] as? String ?: "",
                message = map["message"] as? String ?: "",
                targetHouse = map["targetHouse"] as? String ?: "",
                timestampMillis = (map["timestampMillis"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                metadata = meta,
                isRead = map["isRead"] as? Boolean ?: false
            )
        }
    }
}
