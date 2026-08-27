package com.example.data.alerts

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Entity(
    tableName = "operational_alerts",
    indices = [
        Index(value = ["folio"], unique = true),
        Index(value = ["priorityLevel"]),
        Index(value = ["status"]),
        Index(value = ["timestampMillis"]),
        Index(value = ["alertType"]),
        Index(value = ["originModule"])
    ]
)
data class OperationalAlertEntity(
    @PrimaryKey
    val folio: String, // Folio único: ALF-ALT-YYYYMMDD-XXXX
    val alertType: String, // INCIDENCIA_REPETITIVA, ANOMALIA_PERMANENCIA, SUPERVISION_CRITICA, HORARIO_ATIPICO, SLA_VENCIDO, PATRON_ACCESO_ATIPICO, etc.
    val priorityLevel: String, // CRITICA, ALTA, PREVENTIVA, INFORMATIVA, MEDIA
    val whatHappened: String, // QUÉ PASA (descripción objetiva de los hechos)
    val whereLocation: String, // DÓNDE
    val whenFormatted: String, // CUÁNDO
    val whyItMatters: String, // POR QUÉ IMPORTA
    val whoMustAttend: String, // QUIÉN DEBE ATENDER
    val recommendedAction: String, // QUÉ ACCIÓN REQUIERE
    val originModule: String = "OPERACIONAL", // ACCESOS, INCIDENCIAS, RONDINES, VEHICULOS, VISITANTES, MANTENIMIENTO, RESERVAS
    val evidenceSummary: String = "", // Evidencia concreta numérica y fáctica desde Room
    val explanationReason: String = "", // Explicación del motivo de la detección
    val status: String = "ACTIVA", // ACTIVA, EN_REVISION, CONFIRMADA, DESCARTADA, RESUELTA
    val relatedEntitiesCount: Int = 1,
    val relatedFoliosJson: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
    val resolvedAtMillis: Long? = null,
    val resolvedBy: String? = null,
    val resolutionNotes: String? = null,
    val hashIntegrity: String = ""
) {
    val formattedDate: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestampMillis))
}
