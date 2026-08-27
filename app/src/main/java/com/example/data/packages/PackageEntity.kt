package com.example.data.packages

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad de Paquetería en Room SQLite (FASE 10: PAQUETERÍA)
 * Fuente Única de Verdad para recepción, notificación y entrega de paquetería.
 */
@Entity(
    tableName = "packages",
    indices = [
        Index(value = ["folio"], unique = true),
        Index(value = ["unitId"]),
        Index(value = ["status"]),
        Index(value = ["receivedTimestamp"])
    ]
)
data class PackageEntity(
    @PrimaryKey
    val id: String,
    val folio: String, // Folio único: PKG-YYYYMMDD-XXXX
    val unitId: String, // Domicilio / Unidad (ej: Torre A - Depto 402)
    val residentName: String, // Nombre del residente destinatario
    val residentId: String = "", // ID / Correo del residente en AlfhaUserEntity
    val courierCompany: String, // Amazon, Mercado Libre, DHL, FedEx, Estafeta, Uber Eats, Otro
    val trackingNumber: String = "", // Guía de rastreo o número de paquete
    val packageSize: String = "MEDIANO", // SOBRE, CHICO, MEDIANO, GRANDE
    val locationInGuardhouse: String = "Estante Principal", // Ubicación física en caseta
    val receivedTimestamp: Long = System.currentTimeMillis(),
    val receivedByGuard: String = "Guardia en Turno",
    val status: String = "RECIBIDO", // RECIBIDO, NOTIFICADO, ENTREGADO
    val notifiedTimestamp: Long? = null,
    val deliveredTimestamp: Long? = null,
    val deliveredByGuard: String? = null,
    val receivedByRecipientName: String? = null, // Nombre de quien retiró el paquete
    val recipientSignatureHash: String? = null, // Firma digital o token de validación
    val notes: String = "",
    val timeSavedMinutes: Int = 10 // Ahorro estimado de tiempo en minutos
)
