package com.example.data.booking

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "amenity_bookings",
    indices = [
        Index(value = ["folio"]),
        Index(value = ["amenityName"]),
        Index(value = ["unitId"]),
        Index(value = ["bookingTimeMillis"]),
        Index(value = ["status"])
    ]
)
data class AmenityBooking(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val folio: String = "", // e.g. "RSV-20260823-1042"
    val amenityName: String, // e.g. "Quincho & BBQ Principal", "Gimnasio Residencial", "Piscina & Solárium"
    val residentName: String, // e.g. "Carlos Mendoza"
    val unitId: String, // e.g. "Casa 208"
    val bookingDate: String = "", // Formato "dd/MM/yyyy" o "yyyy-MM-dd"
    val timeSlot: String = "", // Formato "18:00 - 20:00"
    val bookingTimeMillis: Long = System.currentTimeMillis(), // Epoch timestamp for booking start
    val durationMinutes: Int = 120,
    val reminderSent: Boolean = false,
    val status: String = "CONFIRMADA", // "CONFIRMADA", "EN CURSO", "COMPLETADA", "CANCELADA"
    val cancelledBy: String? = null,
    val cancellationReason: String? = null,
    val cancelledAtMillis: Long? = null,
    val timeSavedMinutes: Int = 15,
    val notes: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

