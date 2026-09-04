package com.example.scanner

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PassStatus {
    VALID,
    EXPIRED,
    ALREADY_USED,
    INVALID
}

enum class PassType(val label: String) {
    VISITOR_SINGLE("Visita Ocasional"),
    RESIDENT_PERMANENT("Residente Frecuente"),
    DELIVERY_SERVICE("Servicio / Delivery"),
    EVENT_GUEST("Invitado de Evento VIP")
}

data class QrPassEntity(
    val passCode: String,
    val guestName: String,
    val guestDocument: String,
    val destinationHouse: String,
    val hostResidentName: String,
    val vehiclePlate: String? = null,
    val passType: PassType,
    val validUntilMillis: Long,
    val maxEntries: Int = 1,
    var currentEntriesCount: Int = 0,
    val note: String? = null
)

data class VerificationResult(
    val passCode: String,
    val status: PassStatus,
    val qrPass: QrPassEntity? = null,
    val failureReason: String? = null,
    val condominiumId: String? = null,
    val isFirestoreValidated: Boolean = false,
    val hostResidentPhone: String? = null,
    val hostResidentEmail: String? = null,
    val verificationTimestamp: Long = System.currentTimeMillis()
)

data class GuestAccessLog(
    val id: String = "LOG_${System.currentTimeMillis()}_${(1000..9999).random()}",
    val passCode: String,
    val guestName: String,
    val destinationHouse: String,
    val passTypeLabel: String,
    val vehiclePlate: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
    val isApproved: Boolean,
    val guardName: String = "Agente #402 - Garita 1"
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.getDefault()).format(Date(timestampMillis))
}
