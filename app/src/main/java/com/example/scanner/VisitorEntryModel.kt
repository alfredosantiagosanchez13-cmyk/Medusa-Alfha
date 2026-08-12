package com.example.scanner

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class VisitorStatus(val label: String) {
    PENDING("Pendiente"),
    VERIFIED("Verificado"),
    CHECKED_IN("Checked-In"),
    DEPARTED("Departed"),
    DENIED("Denegado")
}

data class VisitorEntry(
    val id: String = "VIS_${System.currentTimeMillis()}_${(1000..9999).random()}",
    val visitorName: String,
    val visitorDocument: String,
    val destinationHouse: String,
    val passCode: String,
    val passTypeLabel: String,
    val vehiclePlate: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
    val status: VisitorStatus,
    val photoPath: String? = null,
    val guardNotes: String? = null,
    val residentNotes: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.getDefault()).format(Date(timestampMillis))
}

object SampleVisitorEntries {
    fun getSampleEntries(): List<VisitorEntry> = listOf(
        VisitorEntry(
            id = "VIS-001",
            visitorName = "Valeria Sofía Mendoza",
            visitorDocument = "18.492.301-2",
            destinationHouse = "Casa #104",
            passCode = "MEDUSA-PASS-101",
            passTypeLabel = "Visita Ocasional",
            vehiclePlate = "KXYZ-98",
            timestampMillis = System.currentTimeMillis() - (5 * 60 * 1000),
            status = VisitorStatus.VERIFIED,
            guardNotes = "Ingreso autorizado por Garita Principal"
        ),
        VisitorEntry(
            id = "VIS-002",
            visitorName = "Marcos Esteban Ríos",
            visitorDocument = "16.123.890-K",
            destinationHouse = "Casa #208",
            passCode = "MEDUSA-PASS-102",
            passTypeLabel = "Delivery / Uber Eats",
            vehiclePlate = "DLPR-44",
            timestampMillis = System.currentTimeMillis() - (12 * 60 * 1000),
            status = VisitorStatus.PENDING,
            guardNotes = "En garita esperando confirmación de residente"
        ),
        VisitorEntry(
            id = "VIS-003",
            visitorName = "Camila Andrea Silva",
            visitorDocument = "19.876.543-1",
            destinationHouse = "Casa #302",
            passCode = "MEDUSA-PASS-103",
            passTypeLabel = "Invitado VIP",
            vehiclePlate = null,
            timestampMillis = System.currentTimeMillis() - (25 * 60 * 1000),
            status = VisitorStatus.DENIED,
            guardNotes = "Pase QR expirado hace 1 hora"
        ),
        VisitorEntry(
            id = "VIS-004",
            visitorName = "Gonzalo Inostroza",
            visitorDocument = "15.990.112-9",
            destinationHouse = "Casa #115",
            passCode = "MEDUSA-PASS-104",
            passTypeLabel = "Técnico Fibra Óptica",
            vehiclePlate = "BCDF-12",
            timestampMillis = System.currentTimeMillis() - (45 * 60 * 1000),
            status = VisitorStatus.VERIFIED,
            guardNotes = "Técnico en mantenimiento programado"
        ),
        VisitorEntry(
            id = "VIS-005",
            visitorName = "Rodrigo San Martín",
            visitorDocument = "17.430.881-5",
            destinationHouse = "Casa #501",
            passCode = "MEDUSA-PASS-202",
            passTypeLabel = "Servicio Técnico",
            vehiclePlate = "HPWL-88",
            timestampMillis = System.currentTimeMillis() - (2 * 60 * 1000),
            status = VisitorStatus.PENDING,
            guardNotes = "Verificación de carnet en proceso"
        )
    )
}
