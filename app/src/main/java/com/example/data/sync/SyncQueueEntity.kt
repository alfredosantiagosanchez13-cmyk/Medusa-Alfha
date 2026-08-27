package com.example.data.sync

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.core.AlphaCoreEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FASE 19: CONTINUIDAD OPERATIVA + MODO OFFLINE ROBUSTO
 * Entidad de cola persistente para operaciones fuera de línea en Room SQLite.
 *
 * Cada operación conserva:
 * - Folio único y UUID de idempotencia
 * - Fecha/hora local exacta
 * - Usuario y rol responsable
 * - Tipo de operación y datos completos (JSON)
 * - Coordenadas GPS y rutas de evidencias
 * - Estado (PENDIENTE / SINCRONIZANDO / SINCRONIZADO / ERROR)
 * - Conteo de reintentos y tiempo devuelto estimado
 * - Firma criptográfica SHA-256 para inmutabilidad
 */
@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["syncFolio"], unique = true),
        Index(value = ["operationId"], unique = true),
        Index(value = ["status"]),
        Index(value = ["operationType"]),
        Index(value = ["targetFolio"]),
        Index(value = ["deviceGateId"]),
        Index(value = ["timestampMillis"])
    ]
)
data class SyncQueueEntity(
    @PrimaryKey
    val syncFolio: String = AlphaCoreEngine.generateUniqueFolio("SYN"),
    val operationId: String, // UUID o clave de idempotencia única
    val timestampMillis: Long = System.currentTimeMillis(),
    val operatorName: String = "Guardia de Caseta",
    val operatorRole: String = "GUARDIA",
    val operationType: String, // CHECK_IN, CHECK_OUT, QR_VALIDATION, INCIDENT_REGISTER, EMERGENCY_TRIGGER, SUPERVISION_TOUR, VEHICLE_ACCESS, MAINTENANCE_ORDER, PACKAGE_LOG
    val targetFolio: String, // Folio de la entidad (MED-..., EMG-..., INC-..., PAS-..., etc.)
    val targetModule: String = "ACCESOS", // VISITANTES, INCIDENCIAS, EMERGENCIAS, RONDINES, VEHICULOS, MANTENIMIENTO, PAQUETERIA
    val payloadJson: String, // Serialización completa del registro
    val locationName: String = "Garita Principal",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val evidencePaths: String = "", // Rutas o URIs locales de evidencias fotográficas
    val status: String = "PENDIENTE", // PENDIENTE, SINCRONIZANDO, SINCRONIZADO, ERROR
    val retryCount: Int = 0,
    val lastAttemptMillis: Long? = null,
    val errorMessage: String? = null,
    val deviceGateId: String = "Caseta 1 - Terminal Principal",
    val timeSavedSeconds: Long = 180L, // 3 min por operación offline sin papel
    val hashIntegrity: String = AlphaCoreEngine.computeIntegrityHash(syncFolio, operationId, operationType)
) {
    val formattedTime: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))

    val formattedLastAttempt: String?
        get() = lastAttemptMillis?.let { SimpleDateFormat("HH:mm:ss - dd/MM", Locale.getDefault()).format(Date(it)) }

    val isEmergency: Boolean
        get() = operationType == "EMERGENCY_TRIGGER" || targetModule == "EMERGENCIAS"
}
