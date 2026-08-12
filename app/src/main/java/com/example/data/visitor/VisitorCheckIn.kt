package com.example.data.visitor

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.scanner.VisitorEntry
import com.example.scanner.VisitorStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(tableName = "visitor_check_ins")
data class VisitorCheckIn(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val visitorName: String,
    val visitorDocument: String,
    val destinationHouse: String,
    val passCode: String,
    val passTypeLabel: String,
    val vehiclePlate: String? = null,
    val status: String, // "VERIFICADO", "PENDIENTE", "DENEGADO"
    val timestampMillis: Long = System.currentTimeMillis(),
    val guardNotes: String? = null,
    val guardName: String = "Agente #402 - Garita 1",
    val photoPath: String? = null,
    val residentNotes: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.getDefault()).format(Date(timestampMillis))

    fun toVisitorEntry(): VisitorEntry {
        val entryStatus = when (status.uppercase()) {
            "VERIFICADO" -> VisitorStatus.VERIFIED
            "DENEGADO" -> VisitorStatus.DENIED
            "CHECKED_IN", "CHECKED-IN" -> VisitorStatus.CHECKED_IN
            "DEPARTED" -> VisitorStatus.DEPARTED
            else -> VisitorStatus.PENDING
        }
        return VisitorEntry(
            id = id.toString(),
            visitorName = visitorName,
            visitorDocument = visitorDocument,
            destinationHouse = destinationHouse,
            passCode = passCode,
            passTypeLabel = passTypeLabel,
            vehiclePlate = vehiclePlate,
            timestampMillis = timestampMillis,
            status = entryStatus,
            photoPath = photoPath,
            guardNotes = guardNotes,
            residentNotes = residentNotes
        )
    }
}
