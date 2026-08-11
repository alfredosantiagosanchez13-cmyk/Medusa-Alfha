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

object SamplePassRepository {

    private val initialPasses = mutableListOf(
        QrPassEntity(
            passCode = "MEDUSA-PASS-101",
            guestName = "Valeria Sofia Mendoza",
            guestDocument = "18.492.301-2",
            destinationHouse = "Casa #104",
            hostResidentName = "Carlos Mendoza",
            vehiclePlate = "KXYZ-98",
            passType = PassType.VISITOR_SINGLE,
            validUntilMillis = System.currentTimeMillis() + (8 * 3600 * 1000), // Valid for 8 hours
            maxEntries = 1,
            currentEntriesCount = 0,
            note = "Cena familiar / Ingreso por Portón Principal"
        ),
        QrPassEntity(
            passCode = "MEDUSA-PASS-102",
            guestName = "Marcos Esteban Rios (Uber Eats)",
            guestDocument = "16.123.890-K",
            destinationHouse = "Casa #208",
            hostResidentName = "Ana Maria Gomez",
            vehiclePlate = "DLPR-44",
            passType = PassType.DELIVERY_SERVICE,
            validUntilMillis = System.currentTimeMillis() + (2 * 3600 * 1000),
            maxEntries = 1,
            currentEntriesCount = 0,
            note = "Entrega de comida a domicilio"
        ),
        QrPassEntity(
            passCode = "MEDUSA-PASS-103",
            guestName = "Camila Andrea Silva",
            guestDocument = "19.876.543-1",
            destinationHouse = "Casa #302",
            hostResidentName = "Felipe Silva",
            vehiclePlate = null,
            passType = PassType.EVENT_GUEST,
            validUntilMillis = System.currentTimeMillis() - (3600 * 1000), // EXPIRED 1h ago
            maxEntries = 1,
            currentEntriesCount = 0,
            note = "Invitada Cumpleaños VIP en Club House"
        ),
        QrPassEntity(
            passCode = "MEDUSA-PASS-104",
            guestName = "Gonzalo Inostroza",
            guestDocument = "15.990.112-9",
            destinationHouse = "Casa #115",
            hostResidentName = "Patricia Soto",
            vehiclePlate = "BCDF-12",
            passType = PassType.VISITOR_SINGLE,
            validUntilMillis = System.currentTimeMillis() + (12 * 3600 * 1000),
            maxEntries = 1,
            currentEntriesCount = 1, // ALREADY USED
            note = "Reparación técnica de Fibra Óptica"
        ),
        QrPassEntity(
            passCode = "MEDUSA-PASS-105",
            guestName = "Dra. Romina Alarcón",
            guestDocument = "14.331.002-3",
            destinationHouse = "Casa #101",
            hostResidentName = "Directiva Condominio",
            vehiclePlate = "PORS-99",
            passType = PassType.RESIDENT_PERMANENT,
            validUntilMillis = System.currentTimeMillis() + (30L * 86400 * 1000), // 30 days
            maxEntries = 999,
            currentEntriesCount = 4,
            note = "Pase Frecuente Médico Residentes"
        )
    )

    private val activePassesMap = initialPasses.associateBy { it.passCode }.toMutableMap()

    fun addCustomPass(pass: QrPassEntity) {
        activePassesMap[pass.passCode] = pass
    }

    fun verifyCode(code: String): VerificationResult {
        val cleanCode = code.trim()
        val pass = activePassesMap[cleanCode] ?: return VerificationResult(
            passCode = cleanCode,
            status = PassStatus.INVALID,
            failureReason = "Código QR no registrado en el sistema MEDUSA ALFHA."
        )

        if (System.currentTimeMillis() > pass.validUntilMillis) {
            return VerificationResult(
                passCode = cleanCode,
                status = PassStatus.EXPIRED,
                qrPass = pass,
                failureReason = "El pase expiró el ${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(pass.validUntilMillis))}."
            )
        }

        if (pass.currentEntriesCount >= pass.maxEntries) {
            return VerificationResult(
                passCode = cleanCode,
                status = PassStatus.ALREADY_USED,
                qrPass = pass,
                failureReason = "Este pase único ya fue utilizado (${pass.currentEntriesCount}/${pass.maxEntries} usos)."
            )
        }

        return VerificationResult(
            passCode = cleanCode,
            status = PassStatus.VALID,
            qrPass = pass
        )
    }

    fun markPassAsUsed(passCode: String) {
        val pass = activePassesMap[passCode]
        if (pass != null) {
            val updated = pass.copy(currentEntriesCount = pass.currentEntriesCount + 1)
            activePassesMap[passCode] = updated
        }
    }

    fun getAllKnownPasses(): List<QrPassEntity> = activePassesMap.values.toList()
}
