package com.example.data.passes

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.core.AlphaCoreEngine
import com.example.scanner.PassType
import com.example.scanner.QrPassStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(
    tableName = "qr_passes",
    indices = [
        Index(value = ["passCode"], unique = true),
        Index(value = ["destinationHouse"]),
        Index(value = ["assignedUnit"]),
        Index(value = ["residentId"]),
        Index(value = ["validUntilMillis"]),
        Index(value = ["status"])
    ]
)
data class QrPassRoomEntity(
    @PrimaryKey
    val passCode: String, // Folio único: MED-YYYYMMDD-XXXX
    val guestName: String,
    val guestDocument: String,
    val destinationHouse: String,
    val hostResidentName: String,
    val vehiclePlate: String? = null,
    val passType: PassType = PassType.VISITOR_SINGLE,
    val validUntilMillis: Long,
    val maxEntries: Int = 1,
    val currentEntriesCount: Int = 0,
    val note: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val integrityHash: String = "",
    val isActive: Boolean = true,
    val residentId: String = "RES-${destinationHouse.filter { it.isDigit() }.ifBlank { "000" }}",
    val assignedUnit: String = destinationHouse,
    val status: QrPassStatus = QrPassStatus.EMITIDO,
    val digitalSignature: String = ""
) {
    val isExpired: Boolean
        get() = status == QrPassStatus.EXPIRADO || System.currentTimeMillis() > validUntilMillis

    val isExhausted: Boolean
        get() = currentEntriesCount >= maxEntries

    val isValidForEntry: Boolean
        get() = isActive && (status == QrPassStatus.EMITIDO || status == QrPassStatus.VALIDADO) && !isExpired && !isExhausted

    val formattedValidUntil: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(validUntilMillis))
}
