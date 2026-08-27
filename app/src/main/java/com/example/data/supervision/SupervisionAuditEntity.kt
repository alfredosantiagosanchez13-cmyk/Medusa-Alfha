package com.example.data.supervision

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(
    tableName = "supervision_audits",
    indices = [
        Index(value = ["folio"], unique = true),
        Index(value = ["areaName"]),
        Index(value = ["timestampMillis"])
    ]
)
data class SupervisionAuditEntity(
    @PrimaryKey
    val folio: String, // MED-YYYYMMDD-XXXX
    val supervisorName: String,
    val checkpointName: String,
    val areaName: String,
    val statusCondition: String, // OPTIMO, REGULAR, CRITICO, NOVEDAD
    val findingsDescription: String,
    val riskLevel: String, // BAJO, MEDIO, ALTO, CRITICO
    val correctiveActionRequired: String,
    val responsibleParty: String,
    val commitmentDate: String,
    val gpsCoordinates: String? = null,
    val photoEvidencePath: String? = null,
    val durationMinutes: Int = 15,
    val timestampMillis: Long = System.currentTimeMillis(),
    val isClosed: Boolean = false
) {
    val formattedTime: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))
}
