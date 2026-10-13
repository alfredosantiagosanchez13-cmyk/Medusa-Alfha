package com.example.medusaalfha.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.medusaalfha.data.model.PatrolCheckpoint

@Entity(tableName = "checkpoints")
data class CheckpointEntity(
    @PrimaryKey val id: String,
    val code: String,
    val name: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val toleranceMeters: Double = 15.0,
    val isValidated: Boolean = false,
    val validatedAtMillis: Long? = null,
    val mapXPercent: Float = 0.5f,
    val mapYPercent: Float = 0.5f,
    val isSynced: Boolean = false,
    val lastUpdatedMillis: Long = System.currentTimeMillis()
) {
    fun toDomainModel(): PatrolCheckpoint = PatrolCheckpoint(
        id = id,
        code = code,
        name = name,
        description = description,
        latitude = latitude,
        longitude = longitude,
        toleranceMeters = toleranceMeters,
        isValidated = isValidated,
        validatedAtMillis = validatedAtMillis,
        mapXPercent = mapXPercent,
        mapYPercent = mapYPercent,
        isSynced = isSynced
    )

    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "code" to code,
        "name" to name,
        "description" to description,
        "latitude" to latitude,
        "longitude" to longitude,
        "toleranceMeters" to toleranceMeters,
        "isValidated" to isValidated,
        "validatedAtMillis" to validatedAtMillis,
        "mapXPercent" to mapXPercent,
        "mapYPercent" to mapYPercent,
        "lastUpdatedMillis" to lastUpdatedMillis
    )

    companion object {
        fun fromDomainModel(model: PatrolCheckpoint, isSynced: Boolean = false): CheckpointEntity =
            CheckpointEntity(
                id = model.id,
                code = model.code,
                name = model.name,
                description = model.description,
                latitude = model.latitude,
                longitude = model.longitude,
                toleranceMeters = model.toleranceMeters,
                isValidated = model.isValidated,
                validatedAtMillis = model.validatedAtMillis,
                mapXPercent = model.mapXPercent,
                mapYPercent = model.mapYPercent,
                isSynced = isSynced,
                lastUpdatedMillis = System.currentTimeMillis()
            )

        fun fromFirestoreMap(map: Map<String, Any?>): CheckpointEntity? {
            val id = map["id"] as? String ?: return null
            return CheckpointEntity(
                id = id,
                code = map["code"] as? String ?: "",
                name = map["name"] as? String ?: "",
                description = map["description"] as? String ?: "",
                latitude = (map["latitude"] as? Number)?.toDouble() ?: 0.0,
                longitude = (map["longitude"] as? Number)?.toDouble() ?: 0.0,
                toleranceMeters = (map["toleranceMeters"] as? Number)?.toDouble() ?: 15.0,
                isValidated = map["isValidated"] as? Boolean ?: false,
                validatedAtMillis = (map["validatedAtMillis"] as? Number)?.toLong(),
                mapXPercent = (map["mapXPercent"] as? Number)?.toFloat() ?: 0.5f,
                mapYPercent = (map["mapYPercent"] as? Number)?.toFloat() ?: 0.5f,
                isSynced = true,
                lastUpdatedMillis = (map["lastUpdatedMillis"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}
