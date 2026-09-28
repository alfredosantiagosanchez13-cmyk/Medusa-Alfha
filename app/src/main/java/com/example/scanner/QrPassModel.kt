package com.example.scanner

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ESTADOS OFICIALES Y PERSISTENTES DEL PASE QR MEDUSA ALFHA:
 * EMITIDO   → Creado por el residente, pendiente de presentación en caseta.
 * VALIDADO  → Escaneado y validado positivamente por Caseta con firma ECDSA auténtica.
 * USADO     → Ingreso confirmado por el guardia de garita (barrera abierta). Agotado para entrada única.
 * EXPIRADO  → Superó la ventana de vigencia temporal autorizada.
 * CANCELADO → Revocado preventivamente por el residente titular.
 * RECHAZADO → Intento de ingreso no autorizado (firma alterada, vivienda no autorizada o manipulado).
 */
enum class QrPassStatus(val label: String) {
    EMITIDO("Emitido / Pendiente"),
    VALIDADO("Validado en Garita"),
    USADO("Ingreso Completado"),
    EXPIRADO("Vigencia Expirada"),
    CANCELADO("Cancelado por Residente"),
    RECHAZADO("Rechazado por Seguridad");

    fun toPassStatus(): PassStatus = when (this) {
        EMITIDO -> PassStatus.EMITIDO
        VALIDADO -> PassStatus.VALIDADO
        USADO -> PassStatus.USADO
        EXPIRADO -> PassStatus.EXPIRADO
        CANCELADO -> PassStatus.CANCELADO
        RECHAZADO -> PassStatus.RECHAZADO
    }
}

enum class PassStatus(val label: String) {
    EMITIDO("Emitido"),
    VALIDADO("Validado"),
    USADO("Usado"),
    EXPIRADO("Expirado"),
    CANCELADO("Cancelado"),
    RECHAZADO("Rechazado"),

    // Compatibilidad retroactiva con código existente
    VALID("Válido"),
    INVALID("Inválido"),
    ALREADY_USED("Ya utilizado"),
    EXPIRED("Expirado")
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
    val note: String? = null,
    val residentId: String = "RES-${destinationHouse.filter { it.isDigit() }.ifBlank { "000" }}",
    val assignedUnit: String = destinationHouse,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val status: QrPassStatus = QrPassStatus.EMITIDO,
    val digitalSignature: String = ""
) {
    fun toRoomEntity(): com.example.data.passes.QrPassRoomEntity {
        return com.example.data.passes.QrPassRoomEntity(
            passCode = passCode,
            guestName = guestName,
            guestDocument = guestDocument,
            destinationHouse = destinationHouse,
            hostResidentName = hostResidentName,
            vehiclePlate = vehiclePlate,
            passType = passType,
            validUntilMillis = validUntilMillis,
            maxEntries = maxEntries,
            currentEntriesCount = currentEntriesCount,
            note = note,
            createdAtMillis = createdAtMillis,
            isActive = (status == QrPassStatus.EMITIDO || status == QrPassStatus.VALIDADO) && currentEntriesCount < maxEntries,
            residentId = residentId,
            assignedUnit = assignedUnit,
            status = status,
            digitalSignature = digitalSignature
        )
    }
}

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
