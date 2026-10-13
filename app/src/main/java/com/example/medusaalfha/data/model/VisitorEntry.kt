package com.example.medusaalfha.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VisitorEntry(
    val folio: String = "",
    val visitorName: String = "",
    val visitorType: String = "Visita General",
    val destinationHouse: String = "",
    val vehiclePlate: String? = null,
    val accessMethod: String = "Código QR",
    val status: VisitorStatus = VisitorStatus.DENTRO,
    val checkInTimestamp: Long = System.currentTimeMillis(),
    val checkOutTimestamp: Long? = null,
    val notes: String? = null,
    val authorizedBy: String = "Residente Titular",
    val guardName: String = "Oficial de Turno",
    val condominiumId: String = "PRADOS_1"
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(checkInTimestamp))

    val formattedDate: String
        get() = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(checkInTimestamp))

    val relativeTimeAgo: String
        get() {
            val diffMinutes = (System.currentTimeMillis() - checkInTimestamp) / (1000 * 60)
            return when {
                diffMinutes < 1 -> "Hace un momento"
                diffMinutes < 60 -> "Hace $diffMinutes min"
                diffMinutes < 1440 -> "Hace ${diffMinutes / 60} h"
                else -> "Hace ${diffMinutes / 1440} d"
            }
        }

    fun toMap(): Map<String, Any?> = mapOf(
        "folio" to folio,
        "visitorName" to visitorName,
        "visitorType" to visitorType,
        "destinationHouse" to destinationHouse,
        "vehiclePlate" to vehiclePlate,
        "accessMethod" to accessMethod,
        "status" to status.name,
        "checkInTimestamp" to checkInTimestamp,
        "checkOutTimestamp" to checkOutTimestamp,
        "notes" to notes,
        "authorizedBy" to authorizedBy,
        "guardName" to guardName,
        "condominiumId" to condominiumId
    )

    companion object {
        fun fromMap(map: Map<String, Any>): VisitorEntry {
            val statusStr = map["status"] as? String ?: "DENTRO"
            return VisitorEntry(
                folio = map["folio"] as? String ?: "",
                visitorName = map["visitorName"] as? String ?: "",
                visitorType = map["visitorType"] as? String ?: "Visita General",
                destinationHouse = map["destinationHouse"] as? String ?: "",
                vehiclePlate = map["vehiclePlate"] as? String,
                accessMethod = map["accessMethod"] as? String ?: "Código QR",
                status = VisitorStatus.fromString(statusStr),
                checkInTimestamp = (map["checkInTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                checkOutTimestamp = (map["checkOutTimestamp"] as? Number)?.toLong(),
                notes = map["notes"] as? String,
                authorizedBy = map["authorizedBy"] as? String ?: "Residente Titular",
                guardName = map["guardName"] as? String ?: "Oficial de Turno",
                condominiumId = map["condominiumId"] as? String ?: "PRADOS_1"
            )
        }
    }
}
