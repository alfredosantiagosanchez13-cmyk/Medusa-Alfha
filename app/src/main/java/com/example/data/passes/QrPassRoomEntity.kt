package com.example.data.passes

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.core.AlphaCoreEngine
import com.example.scanner.PassType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(
    tableName = "qr_passes",
    indices = [
        Index(value = ["passCode"], unique = true),
        Index(value = ["destinationHouse"]),
        Index(value = ["validUntilMillis"])
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
    val integrityHash: String = AlphaCoreEngine.computeIntegrityHash(passCode, guestDocument, destinationHouse),
    val isActive: Boolean = true
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > validUntilMillis

    val isExhausted: Boolean
        get() = currentEntriesCount >= maxEntries

    val isValidForEntry: Boolean
        get() = isActive && !isExpired && !isExhausted

    val formattedValidUntil: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(validUntilMillis))
}
