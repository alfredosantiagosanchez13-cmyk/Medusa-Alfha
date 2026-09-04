package com.example.data.visitor

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.core.AlphaCoreEngine
import com.example.scanner.VisitorEntry
import com.example.scanner.VisitorStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(
    tableName = "visitor_check_ins",
    indices = [
        Index(value = ["folio"], unique = true),
        Index(value = ["passCode"]),
        Index(value = ["destinationHouse"]),
        Index(value = ["timestampMillis"]),
        Index(value = ["status"])
    ]
)
data class VisitorCheckIn(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val folio: String = AlphaCoreEngine.generateUniqueFolio("MED"),
    val visitorName: String,
    val visitorDocument: String,
    val destinationHouse: String,
    val passCode: String,
    val passTypeLabel: String,
    val vehiclePlate: String? = null,
    val status: String, // "VERIFICADO", "PENDIENTE", "DENEGADO", "CHECKED_IN", "DEPARTED"
    val timestampMillis: Long = System.currentTimeMillis(),
    val checkOutMillis: Long? = null,
    val guardNotes: String? = null,
    val guardName: String = "Agente #402 - Garita 1",
    val photoPath: String? = null,
    val residentNotes: String? = null,
    val hostResidentName: String = "Residente Anfitrión"
) {
    val authorizedUnitNumber: String
        get() = destinationHouse

    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.getDefault()).format(Date(timestampMillis))

    val formattedCheckOutTime: String?
        get() = checkOutMillis?.let { SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.getDefault()).format(Date(it)) }

    val durationStayFormatted: String
        get() = if (checkOutMillis != null) {
            AlphaCoreEngine.calculateDurationFormatted(timestampMillis, checkOutMillis)
        } else {
            val currentElapsed = System.currentTimeMillis() - timestampMillis
            val mins = currentElapsed / (60 * 1000)
            if (mins >= 60) "${mins / 60}h ${mins % 60}m (Activo)" else "$mins min (Activo)"
        }

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
            folio = folio,
            visitorName = visitorName,
            visitorDocument = visitorDocument,
            destinationHouse = destinationHouse,
            passCode = passCode,
            passTypeLabel = passTypeLabel,
            vehiclePlate = vehiclePlate,
            timestampMillis = timestampMillis,
            checkOutMillis = checkOutMillis,
            durationStay = durationStayFormatted,
            status = entryStatus,
            photoPath = photoPath,
            guardNotes = guardNotes,
            residentNotes = residentNotes,
            hostResidentName = hostResidentName
        )
    }
}
