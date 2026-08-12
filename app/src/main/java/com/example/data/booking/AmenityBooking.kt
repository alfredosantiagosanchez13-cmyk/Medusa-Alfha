package com.example.data.booking

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "amenity_bookings")
data class AmenityBooking(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amenityName: String, // e.g. "Quincho & BBQ Principal", "Gimnasio Residencial", "Piscina"
    val residentName: String, // e.g. "Carlos Mendoza"
    val unitId: String, // e.g. "Casa 208"
    val bookingTimeMillis: Long, // Epoch timestamp for booking start
    val durationMinutes: Int = 120,
    val reminderSent: Boolean = false,
    val status: String = "CONFIRMADA", // "CONFIRMADA", "EN CURSO", "COMPLETADA"
    val createdAtMillis: Long = System.currentTimeMillis()
)
