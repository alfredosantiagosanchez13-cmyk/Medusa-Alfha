package com.example.medusaalfha.data.model

data class AmenityBooking(
    val id: String = "",
    val amenityId: String = "",
    val amenityName: String = "",
    val residentName: String = "",
    val residentHouse: String = "",
    val dateString: String = "",
    val startTimeHour: Int = 9,
    val endTimeHour: Int = 12,
    val guestCount: Int = 1,
    val status: BookingStatus = BookingStatus.CONFIRMED,
    val notes: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val condominiumId: String = "PRADOS_1"
) {
    val reservedSlots: List<Int>
        get() = (startTimeHour until endTimeHour).toList()

    val formattedTimeRange: String
        get() = String.format("%02d:00 - %02d:00", startTimeHour, endTimeHour)

    fun overlapsWith(other: AmenityBooking): Boolean {
        if (amenityId != other.amenityId || dateString != other.dateString) return false
        if (status == BookingStatus.CANCELLED || other.status == BookingStatus.CANCELLED) return false
        return startTimeHour < other.endTimeHour && other.startTimeHour < endTimeHour
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "amenityId" to amenityId,
        "amenityName" to amenityName,
        "residentName" to residentName,
        "residentHouse" to residentHouse,
        "dateString" to dateString,
        "startTimeHour" to startTimeHour,
        "endTimeHour" to endTimeHour,
        "guestCount" to guestCount,
        "status" to status.name,
        "notes" to notes,
        "createdAtMillis" to createdAtMillis,
        "condominiumId" to condominiumId
    )

    companion object {
        fun fromMap(map: Map<String, Any>): AmenityBooking {
            return AmenityBooking(
                id = map["id"] as? String ?: "",
                amenityId = map["amenityId"] as? String ?: "",
                amenityName = map["amenityName"] as? String ?: "",
                residentName = map["residentName"] as? String ?: "",
                residentHouse = map["residentHouse"] as? String ?: "",
                dateString = map["dateString"] as? String ?: "",
                startTimeHour = (map["startTimeHour"] as? Number)?.toInt() ?: 9,
                endTimeHour = (map["endTimeHour"] as? Number)?.toInt() ?: 12,
                guestCount = (map["guestCount"] as? Number)?.toInt() ?: 1,
                status = (map["status"] as? String)?.let { BookingStatus.fromString(it) } ?: BookingStatus.CONFIRMED,
                notes = map["notes"] as? String ?: "",
                createdAtMillis = (map["createdAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                condominiumId = map["condominiumId"] as? String ?: "PRADOS_1"
            )
        }
    }
}
