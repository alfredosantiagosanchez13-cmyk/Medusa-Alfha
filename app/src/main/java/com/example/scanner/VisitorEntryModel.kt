package com.example.scanner

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class VisitorStatus(val label: String) {
    PENDING("Pendiente"),
    VERIFIED("Verificado"),
    CHECKED_IN("En Condominio"),
    DEPARTED("Salida Registrada"),
    DENIED("Denegado")
}

data class VisitorEntry(
    val id: String = "VIS_${System.currentTimeMillis()}_${(1000..9999).random()}",
    val folio: String = "MED-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}-${(1000..9999).random()}",
    val visitorName: String,
    val visitorDocument: String,
    val destinationHouse: String,
    val passCode: String,
    val passTypeLabel: String,
    val vehiclePlate: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
    val checkOutMillis: Long? = null,
    val durationStay: String? = null,
    val status: VisitorStatus,
    val photoPath: String? = null,
    val guardNotes: String? = null,
    val residentNotes: String? = null,
    val hostResidentName: String = "Residente Anfitrión"
) {
    val authorizedUnitNumber: String
        get() = destinationHouse

    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.getDefault()).format(Date(timestampMillis))

    val formattedCheckOutTime: String?
        get() = checkOutMillis?.let { SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.getDefault()).format(Date(it)) }

    fun toVisitorCheckIn(): com.example.data.visitor.VisitorCheckIn {
        val statusStr = when (status) {
            VisitorStatus.CHECKED_IN -> "CHECKED_IN"
            VisitorStatus.VERIFIED -> "VERIFICADO"
            VisitorStatus.DEPARTED -> "DEPARTED"
            VisitorStatus.DENIED -> "DENEGADO"
            VisitorStatus.PENDING -> "PENDIENTE"
        }
        return com.example.data.visitor.VisitorCheckIn(
            id = id.toLongOrNull() ?: 0L,
            folio = folio,
            visitorName = visitorName,
            visitorDocument = visitorDocument,
            destinationHouse = destinationHouse,
            passCode = passCode,
            passTypeLabel = passTypeLabel,
            vehiclePlate = vehiclePlate,
            status = statusStr,
            timestampMillis = timestampMillis,
            checkOutMillis = checkOutMillis,
            guardNotes = guardNotes,
            residentNotes = residentNotes,
            hostResidentName = hostResidentName,
            photoPath = photoPath
        )
    }
}

