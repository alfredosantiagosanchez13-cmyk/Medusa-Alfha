package com.example.data.audit

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.core.AlphaCoreEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Entidad Inmutable de Auditoría Permanente y Forense para MEDUSA ALFHA.
 *
 * Satisface los requerimientos de persistencia estricta para:
 * 1. ENTIDAD INMUTABLE DE AUDITORÍA (`AuditLogEntity.kt`):
 *    - `@Entity(tableName = "medusa_audit_logs")`
 *    - `logId: String` (PrimaryKey con UUID por defecto)
 *    - `timestamp: Long` (Timestamp unix en milisegundos)
 *    - `operatorId: String` (Identificador del operador, terminal o sujeto)
 *    - `eventDescription: String` (Descripción del evento auditado)
 *    - `severity: String` (INFO, WARNING, SECURITY_CRITICAL)
 *    - `forensicPayload: String` (Volcado de metadatos en formato JSON)
 *
 * Mantiene compatibilidad total con parámetros opcionales y propiedades calculadas
 * para que toda la suite de motores existentes (EmergencyLocationEngine, PackageEngine, etc.)
 * funcione de manera transparente y unificada.
 */
@Entity(
    tableName = "medusa_audit_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["operatorId"]),
        Index(value = ["severity"])
    ]
)
data class AuditLogEntity(
    @PrimaryKey
    val logId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val operatorId: String = "SISTEMA",
    val eventDescription: String = "",
    val severity: String = Severity.INFO,
    val forensicPayload: String = "{}"
) {
    /**
     * Niveles canónicos de severidad forense para MEDUSA ALFHA.
     */
    object Severity {
        const val INFO = "INFO"
        const val WARNING = "WARNING"
        const val SECURITY_CRITICAL = "SECURITY_CRITICAL"
    }

    /**
     * Constructor secundario de interoperabilidad que permite a los motores y servicios del sistema
     * invocar la entidad con los nombres de campos del protocolo corporativo tradicional
     * (folio, operatorName, actionType, location, targetEntity, changeDetails, resultStatus, timestampMillis, sha256Signature).
     */
    constructor(
        folio: String = UUID.randomUUID().toString(),
        operatorName: String = "SISTEMA",
        actionType: String = "OPERACION",
        location: String = "TERMINAL",
        targetEntity: String = "GENERAL",
        changeDetails: String = "",
        resultStatus: String = "EXITOSO",
        timestampMillis: Long = System.currentTimeMillis(),
        sha256Signature: String = ""
    ) : this(
        logId = folio,
        timestamp = timestampMillis,
        operatorId = operatorName,
        eventDescription = if (changeDetails.isNotBlank()) changeDetails else "$actionType en $location ($targetEntity) -> $resultStatus",
        severity = when (resultStatus.uppercase(Locale.ROOT)) {
            "BLOQUEADO", "DENEGADO", "ERROR", "SECURITY_CRITICAL" -> Severity.SECURITY_CRITICAL
            "ALERTA", "WARNING" -> Severity.WARNING
            else -> Severity.INFO
        },
        forensicPayload = """{"actionType":"$actionType","location":"$location","targetEntity":"$targetEntity","resultStatus":"$resultStatus","sha256Signature":"${if (sha256Signature.isNotBlank()) sha256Signature else AlphaCoreEngine.computeIntegrityHash(folio, operatorName, targetEntity)}"}"""
    )

    private fun extractFromPayload(key: String): String? {
        val pattern = "\"$key\":\"([^\"]*)\"".toRegex()
        return pattern.find(forensicPayload)?.groupValues?.get(1)
    }

    // Propiedades de conveniencia y compatibilidad
    val folio: String get() = logId
    val operatorName: String get() = operatorId
    val actionType: String get() = extractFromPayload("actionType") ?: severity
    val location: String get() = extractFromPayload("location") ?: "LOCAL_TERMINAL"
    val targetEntity: String get() = extractFromPayload("targetEntity") ?: logId
    val changeDetails: String get() = eventDescription
    val resultStatus: String get() = extractFromPayload("resultStatus") ?: if (severity == Severity.SECURITY_CRITICAL) "BLOQUEADO" else "EXITOSO"
    val timestampMillis: Long get() = timestamp
    val sha256Signature: String get() {
        val sig = extractFromPayload("sha256Signature")
        return if (!sig.isNullOrBlank()) sig else AlphaCoreEngine.computeIntegrityHash(logId, operatorId, targetEntity)
    }
    val formattedTime: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
