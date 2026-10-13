package com.example.medusaalfha.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.medusaalfha.data.model.PatrolLogEntry

@Entity(tableName = "patrol_reports")
data class PatrolReportEntity(
    @PrimaryKey val id: String,
    val timestampMillis: Long,
    val zone: String,
    val reportText: String,
    val isAiAssisted: Boolean = false,
    val isSynced: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    fun toDomainModel(): PatrolLogEntry = PatrolLogEntry(
        id = id,
        timestampMillis = timestampMillis,
        zone = zone,
        reportText = reportText,
        isAiAssisted = isAiAssisted,
        isSynced = isSynced
    )

    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "timestampMillis" to timestampMillis,
        "zone" to zone,
        "reportText" to reportText,
        "isAiAssisted" to isAiAssisted,
        "createdAtMillis" to createdAtMillis
    )

    companion object {
        fun fromDomainModel(model: PatrolLogEntry, isSynced: Boolean = false): PatrolReportEntity =
            PatrolReportEntity(
                id = model.id,
                timestampMillis = model.timestampMillis,
                zone = model.zone,
                reportText = model.reportText,
                isAiAssisted = model.isAiAssisted,
                isSynced = isSynced,
                createdAtMillis = System.currentTimeMillis()
            )

        fun fromFirestoreMap(map: Map<String, Any?>): PatrolReportEntity? {
            val id = map["id"] as? String ?: return null
            return PatrolReportEntity(
                id = id,
                timestampMillis = (map["timestampMillis"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                zone = map["zone"] as? String ?: "",
                reportText = map["reportText"] as? String ?: "",
                isAiAssisted = map["isAiAssisted"] as? Boolean ?: false,
                isSynced = true,
                createdAtMillis = (map["createdAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}
