package com.example.medusaalfha.data.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Catálogo de Áreas Comunes de Los Prados Residencial.
 */
data class AmenityArea(
    val id: String,
    val name: String,
    val description: String,
    val maxCapacity: Int,
    val openHour: Int = 8,
    val closeHour: Int = 22,
    val badgeLabel: String,
    val rulesSummary: String
)

/**
 * Modelo de Reserva de Área Común sincronizado con Cloud Firestore.
 */
data class AmenityBooking(
    val id: String = "BOOK-${System.currentTimeMillis() % 100000}",
    val amenityId: String = "",
    val amenityName: String = "",
    val residentName: String = "",
    val residentHouse: String = "",
    val dateString: String = "", // Formato YYYY-MM-DD
    val startTimeHour: Int = 10,  // e.g. 10 para 10:00 AM
    val endTimeHour: Int = 12,    // e.g. 12 para 12:00 PM
    val guestCount: Int = 1,
    val status: BookingStatus = BookingStatus.CONFIRMADA,
    val notes: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val condominiumId: String = "PRADOS_1"
) {
    /**
     * Valida si esta reserva se traslapa con otra reserva en la misma área común y fecha.
     */
    fun overlapsWith(other: AmenityBooking): Boolean {
        if (this.id == other.id) return false
        if (this.amenityId != other.amenityId) return false
        if (this.dateString != other.dateString) return false
        if (this.status == BookingStatus.CANCELADA || other.status == BookingStatus.CANCELADA) return false

        // Dos intervalos [A_start, A_end) y [B_start, B_end) se traslapan si max(A_start, B_start) < min(A_end, B_end)
        val maxStart = max(this.startTimeHour, other.startTimeHour)
        val minEnd = min(this.endTimeHour, other.endTimeHour)
        return maxStart < minEnd
    }

    fun formattedTimeRange(): String {
        val startStr = String.format(Locale.getDefault(), "%02d:00", startTimeHour)
        val endStr = String.format(Locale.getDefault(), "%02d:00", endTimeHour)
        return "$startStr - $endStr"
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
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
    }

    companion object {
        fun fromMap(data: Map<String, Any?>): AmenityBooking {
            return AmenityBooking(
                id = data["id"] as? String ?: "",
                amenityId = data["amenityId"] as? String ?: "",
                amenityName = data["amenityName"] as? String ?: "",
                residentName = data["residentName"] as? String ?: "",
                residentHouse = data["residentHouse"] as? String ?: "",
                dateString = data["dateString"] as? String ?: "",
                startTimeHour = (data["startTimeHour"] as? Number)?.toInt() ?: 10,
                endTimeHour = (data["endTimeHour"] as? Number)?.toInt() ?: 12,
                guestCount = (data["guestCount"] as? Number)?.toInt() ?: 1,
                status = try {
                    BookingStatus.valueOf(data["status"] as? String ?: "CONFIRMADA")
                } catch (_: Exception) {
                    BookingStatus.CONFIRMADA
                },
                notes = data["notes"] as? String ?: "",
                createdAtMillis = (data["createdAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                condominiumId = data["condominiumId"] as? String ?: "PRADOS_1"
            )
        }

        fun todayDateString(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return sdf.format(Date())
        }
    }
}

enum class BookingStatus {
    CONFIRMADA,
    EN_CURSO,
    FINALIZADA,
    CANCELADA
}

object AmenityCatalog {
    val AMENITIES = listOf(
        AmenityArea(
            id = "ALBERCA_PALAPA",
            name = "Palapa & Alberca Los Álamos",
            description = "Área acuática con camastros, sombrillas y palapa techada para reuniones familiares.",
            maxCapacity = 35,
            openHour = 9,
            closeHour = 21,
            badgeLabel = "Alberca & Palapa",
            rulesSummary = "Música a volumen moderado. No envases de vidrio dentro del perímetro de alberca."
        ),
        AmenityArea(
            id = "CASA_CLUB",
            name = "Casa Club Salón de Eventos",
            description = "Salón climatizado equipado con cocineta, mesas, sillas y terraza panorámica.",
            maxCapacity = 75,
            openHour = 10,
            closeHour = 23,
            badgeLabel = "Casa Club",
            rulesSummary = "Depósito de garantía requerido. Cierre a las 23:00 hrs obligatorio por reglamento."
        ),
        AmenityArea(
            id = "CANCHA_PADEL",
            name = "Cancha de Pádel y Tenis",
            description = "Cancha reglamentaria con iluminación LED nocturna y césped sintético profesional.",
            maxCapacity = 4,
            openHour = 7,
            closeHour = 22,
            badgeLabel = "Cancha Pádel",
            rulesSummary = "Uso de calzado deportivo de suela no marcante. Máximo 2 horas continuas por vivienda."
        ),
        AmenityArea(
            id = "ASADORES_GRILL",
            name = "Terraza Grill & Asadores",
            description = "Zona al aire libre con asadores de carbón de acero inoxidable, bancas y tarja.",
            maxCapacity = 20,
            openHour = 12,
            closeHour = 22,
            badgeLabel = "Asadores Grill",
            rulesSummary = "Apagar brasas al terminar. Dejar área limpia y desinfectada."
        ),
        AmenityArea(
            id = "GIMNASIO",
            name = "Gimnasio Fitness Residencial",
            description = "Área de acondicionamiento físico con caminadoras, pesas libres y máquinas multifuerza.",
            maxCapacity = 12,
            openHour = 6,
            closeHour = 22,
            badgeLabel = "Gimnasio",
            rulesSummary = "Toalla personal obligatoria. Regresar mancuernas a su rack tras su uso."
        )
    )

    fun findById(id: String): AmenityArea {
        return AMENITIES.firstOrNull { it.id == id } ?: AMENITIES.first()
    }
}
