package com.example.medusaalfha.data.model

enum class BookingStatus(val label: String) {
    CONFIRMED("Confirmada"),
    PENDING("Pendiente"),
    CANCELLED("Cancelada");

    companion object {
        fun fromString(value: String): BookingStatus {
            return try {
                valueOf(value.uppercase())
            } catch (_: Exception) {
                CONFIRMED
            }
        }
    }
}
