package com.example.data.audit

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.core.AlphaCoreEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Entidad de Auditoría Total Inmutable para MEDUSA ALFHA (FASE 14).
 * Principio: "QUIÉN, QUÉ, CUÁNDO, DÓNDE, SOBRE QUÉ, QUÉ CAMBIÓ, RESULTADO"
 */
@Entity(
    tableName = "audit_logs",
    indices = [
        Index(value = ["folio"], unique = true),
        Index(value = ["actionType"]),
        Index(value = ["timestampMillis"]),
        Index(value = ["operatorName"])
    ]
)
data class AuditLogEntity(
    @PrimaryKey
    val folio: String = AlphaCoreEngine.generateUniqueFolio("AUD"),
    val operatorName: String,
    val actionType: String, // CHECK_IN, CHECK_OUT, QR_GENERATED, INCIDENT_LOGGED, INCIDENT_RESOLVED, SUPERVISION_RECORDED, SUPERVISION_CLOSED, AMENITY_BOOKED
    val location: String, // Garita 1, App Residente, Club House, etc.
    val targetEntity: String, // Folio o Identificador afectado
    val changeDetails: String,
    val resultStatus: String = "EXITOSO", // EXITOSO, ALERTA, DENEGADO
    val timestampMillis: Long = System.currentTimeMillis(),
    val sha256Signature: String = AlphaCoreEngine.computeIntegrityHash(folio, operatorName, targetEntity)
) {
    val formattedTime: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))
}
