package com.example.data.booking

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad de Room para gestionar reservas de áreas comunes (Common Area Bookings).
 * Contiene identificadores de usuario (userId), nombre de la instalación (facilityName)
 * y ranuras de horario (timeSlot, startTimeMillis, endTimeMillis).
 */
@Entity(
    tableName = "common_area_bookings",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["facilityName"]),
        Index(value = ["bookingDate"]),
        Index(value = ["startTimeMillis"]),
        Index(value = ["endTimeMillis"]),
        Index(value = ["condominiumId"]),
        Index(value = ["status"]),
        Index(value = ["folio"])
    ]
)
data class CommonAreaBooking(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val folio: String = "", // e.g. "CAB-20260910-1042"
    val userId: String, // Identificador del usuario que reserva (e.g. "USR-104", "RES-MENDOZA")
    val userName: String = "", // Nombre legible del usuario (e.g. "Carlos Mendoza")
    val userUnit: String = "", // Casa o departamento (e.g. "Casa 104")
    val facilityName: String, // Nombre del área común (e.g. "Clubhouse Principal", "Cancha de Pádel", "Gimnasio", "Piscina")
    val bookingDate: String, // Formato "yyyy-MM-dd" para agrupamiento y calendario
    val timeSlot: String, // Ranura de horario textual (e.g. "18:00 - 20:00", "09:00 - 11:00")
    val startTimeMillis: Long, // Epoch timestamp de inicio de la reserva
    val endTimeMillis: Long, // Epoch timestamp de finalización de la reserva
    val condominiumId: String = "PRADOS_1", // Aislamiento multi-inquilino
    val status: String = "CONFIRMED", // "CONFIRMED", "IN_USE", "COMPLETED", "CANCELLED"
    val guestCount: Int = 1, // Cantidad estimada de acompañantes / invitados
    val specialRequests: String = "", // Solicitudes especiales (e.g. "Mobiliario adicional")
    val notes: String = "", // Notas de administración o seguridad
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)
