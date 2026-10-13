package com.example.medusaalfha.data.model

enum class AlertType(val title: String) {
    VISITOR_ARRIVAL("Ingreso de Visita"),
    BOOKING_CONFIRMED("Reserva Confirmada"),
    PACKAGE_DELIVERED("Paquetería en Caseta"),
    SECURITY_PANIC("Alerta de Seguridad"),
    MAINTENANCE_NOTICE("Aviso de Administración")
}
