package com.example.data.incident

import android.content.Context
import com.example.auth.AlfhaRole
import com.example.data.audit.AuditLogEntity
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.notifications.SmartNotificationHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * FASE 9: MOTOR DE GESTIÓN Y SEGUIMIENTO DE INCIDENCIAS ALFHA
 *
 * Automatiza:
 * 1. Clasificación y Priorización Inteligente.
 * 2. Asignación Automática de Responsable y Rol Operativo.
 * 3. Seguimiento de SLA y Escalamiento Automático por Exceso de Tiempo.
 * 4. Transiciones de Estado Obligatorias: REGISTRADO ➔ EN_ATENCION ➔ RESUELTO ➔ CERRADO.
 * 5. Generación de Registro de TIEMPO DEVUELTO para la Comunidad.
 * 6. Trazabilidad Inmutable en Auditoría SHA-256.
 */
object IncidentEngine {

    /**
     * Determina automáticamente el responsable y el rol operativo según categoría y prioridad
     */
    fun autoAssignResponsible(category: IncidentCategory, priority: IncidentPriority): Pair<String, String> {
        return when (category) {
            IncidentCategory.SEGURIDAD_EMERGENCIA -> when (priority) {
                IncidentPriority.CRITICA -> Pair("Supervisor Táctico & Cuadrilla Alfa", "SUPERVISOR")
                IncidentPriority.ALTA -> Pair("Oficial de Guardia (Garita 1)", "GUARDIA")
                else -> Pair("Guardia de Turno", "GUARDIA")
            }
            IncidentCategory.CONTROL_ACCESO -> Pair("Operador de Garita Principal", "GUARDIA")
            IncidentCategory.PARKING_VIALIDAD -> Pair("Oficial de Ronda Perimetral y Vialidad", "GUARDIA")
            IncidentCategory.RUIDO_CONVIVENCIA -> Pair("Administración & Mediador Comunitario", "ADMINISTRACION")
            IncidentCategory.INFRAESTRUCTURA -> Pair("Coordinación de Mantenimiento e Instalaciones", "ADMINISTRACION")
            IncidentCategory.GENERAL -> Pair("Administración General", "ADMINISTRACION")
        }
    }

    /**
     * Calcula los minutos objetivo de SLA según la prioridad de la incidencia
     */
    fun getSlaMinutes(priority: IncidentPriority): Int {
        return when (priority) {
            IncidentPriority.CRITICA -> 15  // 15 minutos (Emergencia)
            IncidentPriority.ALTA -> 45     // 45 minutos (Prioritaria)
            IncidentPriority.MEDIA -> 180   // 3 horas (Estándar)
            IncidentPriority.BAJA -> 1440   // 24 horas (Preventiva / General)
        }
    }

    /**
     * Registra una nueva incidencia en un solo flujo unificado y alimenta toda la plataforma
     */
    suspend fun registerIncident(
        context: Context,
        db: AppDatabase,
        rawTranscript: String,
        category: IncidentCategory,
        priority: IncidentPriority,
        location: String,
        aiSummary: String,
        recommendedAction: String,
        reportedBy: String,
        reportedByRole: String,
        evidenceNotes: String? = null
    ): IncidentEntity = withContext(Dispatchers.IO) {
        val folio = AlphaCoreEngine.generateUniqueFolio("INC")
        val (assignedTo, assignedRole) = autoAssignResponsible(category, priority)
        val targetSla = getSlaMinutes(priority)
        val timeSavedMinutes = 20 // 20 min ahorrados en bitácoras físicas, llamadas manuales y despacho telefónico

        val entity = IncidentEntity(
            folio = folio,
            rawTranscript = rawTranscript,
            category = category,
            priority = priority,
            location = location,
            aiSummary = aiSummary,
            recommendedAction = recommendedAction,
            timestampMillis = System.currentTimeMillis(),
            guardName = reportedBy,
            reportedBy = reportedBy,
            reportedByRole = reportedByRole,
            status = "REGISTRADO",
            assignedTo = assignedTo,
            assignedRole = assignedRole,
            targetSlaMinutes = targetSla,
            isEscalated = false,
            evidenceNotes = evidenceNotes,
            timeSavedMinutes = timeSavedMinutes
        )

        // 1. Inserción en Room DB (Fuente Única de Verdad)
        db.incidentDao().insertIncident(entity)

        // 2. Registro Inmutable en Auditoría SHA-256
        db.auditLogDao().insertAuditLog(
            AuditLogEntity(
                folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                operatorName = reportedBy,
                actionType = "INCIDENT_REGISTERED",
                location = location,
                targetEntity = folio,
                changeDetails = "Incidencia $folio ($category - $priority) registrada en $location. Asignada a: $assignedTo ($assignedRole). SLA: ${targetSla}m",
                resultStatus = "EXITOSO",
                timestampMillis = System.currentTimeMillis()
            )
        )

        // 3. Registro de TIEMPO DEVUELTO
        db.auditLogDao().insertAuditLog(
            AuditLogEntity(
                folio = AlphaCoreEngine.generateUniqueFolio("TME"),
                operatorName = "MEDUSA_AI_ENGINE",
                actionType = "TIEMPO_DEVUELTO",
                location = location,
                targetEntity = folio,
                changeDetails = "Ahorro de $timeSavedMinutes minutos generado por auto-clasificación IA, despacho y asignación inmediata de $folio",
                resultStatus = "EXITOSO",
                timestampMillis = System.currentTimeMillis()
            )
        )

        // 4. Encolar en Cola de Sincronización Persistente (FASE 19: Offline Robusto)
        try {
            com.example.data.sync.OfflineSyncEngine.enqueueOperation(
                db = db,
                operationType = "INCIDENT_REGISTER",
                targetFolio = folio,
                targetModule = "INCIDENCIAS",
                payloadJson = "{\"folio\":\"$folio\",\"category\":\"${category.displayName}\",\"priority\":\"${priority.displayName}\",\"location\":\"$location\",\"assignedTo\":\"$assignedTo\"}",
                operatorName = reportedBy,
                operatorRole = reportedByRole,
                locationName = location,
                evidencePaths = evidenceNotes,
                deviceGateId = "Terminal de Operación ($location)"
            )
        } catch (e: Exception) {
            android.util.Log.e("IncidentEngine", "Error enqueuing incident sync: ${e.message}")
        }

        // 5. Enrutamiento Inteligente de Notificaciones
        try {
            // Notificar al responsable asignado
            when (assignedRole) {
                "GUARDIA" -> SmartNotificationHub.notifyGuardIncidentAssigned(
                    context = context,
                    db = db,
                    folio = folio,
                    location = location,
                    category = category.displayName,
                    instructions = "$recommendedAction ($assignedTo)"
                )
                "SUPERVISOR" -> SmartNotificationHub.notifySupervisorCriticalFinding(
                    context = context,
                    db = db,
                    supervisionFolio = folio,
                    checkpointName = location,
                    findingDetail = "$rawTranscript. Asignado a: $assignedTo"
                )
                "ADMINISTRACION" -> if (priority == IncidentPriority.CRITICA || priority == IncidentPriority.ALTA) {
                    SmartNotificationHub.notifyAdminEscalatedIncident(
                        context = context,
                        db = db,
                        folio = folio,
                        location = location,
                        escalationReason = "Ticket Prioridad ${priority.displayName}: $aiSummary"
                    )
                }
            }

            // Si fue reportado por un residente, notificarle acuse de recibo
            if (reportedByRole == "RESIDENTE") {
                SmartNotificationHub.notifyResidentIncidentUpdate(
                    context = context,
                    db = db,
                    folio = folio,
                    unitId = location,
                    residentName = reportedBy,
                    category = category.displayName,
                    status = "REGISTRADO",
                    summary = "Asignado a $assignedTo. Tiempo objetivo: $targetSla min."
                )
            }

            // Notificación a Mesa Directiva si es Crítica
            if (priority == IncidentPriority.CRITICA) {
                SmartNotificationHub.notifyBoardHighPriorityEvent(
                    context = context,
                    db = db,
                    folio = folio,
                    titleSummary = "Alerta Crítica: $category en $location",
                    executiveDetail = rawTranscript,
                    isCritical = true
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("IncidentEngine", "Error dispatching smart notification: ${e.message}")
        }

        entity
    }

    /**
     * Transición a EN_ATENCION
     */
    suspend fun transitionToAttention(
        context: Context,
        db: AppDatabase,
        folio: String,
        operatorName: String,
        operatorRole: String,
        notes: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val incident = db.incidentDao().getIncidentByFolio(folio) ?: return@withContext false
        val now = System.currentTimeMillis()

        db.incidentDao().transitionToAttention(folio, operatorName, now)

        if (!notes.isNullOrBlank()) {
            db.incidentDao().appendEvidenceNotes(folio, "Inicio de Atención ($operatorName): $notes")
        }

        // Auditoría
        db.auditLogDao().insertAuditLog(
            AuditLogEntity(
                folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                operatorName = operatorName,
                actionType = "INCIDENT_ATTENTION",
                location = incident.location,
                targetEntity = folio,
                changeDetails = "Incidencia $folio asumida en atención por $operatorName ($operatorRole).",
                resultStatus = "EXITOSO",
                timestampMillis = now
            )
        )

        // Notificar actualización
        try {
            SmartNotificationHub.notifyResidentIncidentUpdate(
                context = context,
                db = db,
                folio = folio,
                unitId = incident.location,
                residentName = incident.reportedBy,
                category = incident.category.displayName,
                status = "EN_ATENCION",
                summary = "Atendido actualmente por $operatorName."
            )
        } catch (e: Exception) {
            android.util.Log.e("IncidentEngine", "Error notifying attention: ${e.message}")
        }

        true
    }

    /**
     * Adjunta notas de evidencia / hallazgo de campo
     */
    suspend fun appendEvidence(
        context: Context,
        db: AppDatabase,
        folio: String,
        evidenceText: String,
        operatorName: String,
        operatorRole: String
    ): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val formattedEvidence = "[$operatorName - $operatorRole]: $evidenceText"

        db.incidentDao().appendEvidenceNotes(folio, formattedEvidence)

        db.auditLogDao().insertAuditLog(
            AuditLogEntity(
                folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                operatorName = operatorName,
                actionType = "INCIDENT_EVIDENCE",
                location = "CAMPO",
                targetEntity = folio,
                changeDetails = "Evidencia adjunta a $folio: $evidenceText",
                resultStatus = "EXITOSO",
                timestampMillis = now
            )
        )

        true
    }

    /**
     * Transición a RESUELTO (Dictamen Formal de Resolución)
     */
    suspend fun transitionToResolved(
        context: Context,
        db: AppDatabase,
        folio: String,
        resolutionNotes: String,
        operatorName: String,
        operatorRole: String
    ): Boolean = withContext(Dispatchers.IO) {
        val incident = db.incidentDao().getIncidentByFolio(folio) ?: return@withContext false
        val now = System.currentTimeMillis()
        val extraTimeSaved = 15 // 15 min ahorrados en redacción de informe y notificación manual

        db.incidentDao().transitionToResolved(folio, resolutionNotes, operatorName, now)

        // Auto-resolver notificaciones pendientes asociadas a este folio
        SmartNotificationHub.autoResolveNotificationsForFolio(db, folio, operatorName)

        // Auditoría formal
        db.auditLogDao().insertAuditLog(
            AuditLogEntity(
                folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                operatorName = operatorName,
                actionType = "INCIDENT_RESOLVED",
                location = incident.location,
                targetEntity = folio,
                changeDetails = "Incidencia $folio RESUELTA por $operatorName. Dictamen: $resolutionNotes",
                resultStatus = "EXITOSO",
                timestampMillis = now
            )
        )

        // Registrar Tiempo Devuelto
        db.auditLogDao().insertAuditLog(
            AuditLogEntity(
                folio = AlphaCoreEngine.generateUniqueFolio("TME"),
                operatorName = "MEDUSA_AI_ENGINE",
                actionType = "TIEMPO_DEVUELTO",
                location = incident.location,
                targetEntity = folio,
                changeDetails = "Ahorro de $extraTimeSaved minutos por resolución digital y cierre automático de bitácora para $folio",
                resultStatus = "EXITOSO",
                timestampMillis = now
            )
        )

        // Notificar a Residente
        try {
            SmartNotificationHub.notifyResidentIncidentUpdate(
                context = context,
                db = db,
                folio = folio,
                unitId = incident.location,
                residentName = incident.reportedBy,
                category = incident.category.displayName,
                status = "RESUELTO",
                summary = "Dictamen de solución: $resolutionNotes (Resuelto por: $operatorName)"
            )
        } catch (e: Exception) {
            android.util.Log.e("IncidentEngine", "Error notifying resolution: ${e.message}")
        }

        true
    }

    /**
     * Transición a CERRADO (Cierre Definitivo de Ticket)
     */
    suspend fun transitionToClosed(
        context: Context,
        db: AppDatabase,
        folio: String,
        closureNotes: String,
        operatorName: String,
        operatorRole: String
    ): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val finalNotes = closureNotes.ifBlank { "Ticket cerrado satisfactoriamente conforme a protocolo de gobernanza." }

        db.incidentDao().transitionToClosed(folio, finalNotes, operatorName, now)

        db.auditLogDao().insertAuditLog(
            AuditLogEntity(
                folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                operatorName = operatorName,
                actionType = "INCIDENT_CLOSED",
                location = "ADMINISTRACION",
                targetEntity = folio,
                changeDetails = "Incidencia $folio CERRADA definitivamente por $operatorName. Cierre: $finalNotes",
                resultStatus = "EXITOSO",
                timestampMillis = now
            )
        )

        true
    }

    /**
     * Escanea periódicamente la base de datos y escala automáticamente las incidencias que hayan excedido su tiempo objetivo de SLA
     */
    suspend fun checkAndAutoEscalateIncidents(
        context: Context,
        db: AppDatabase
    ): Int = withContext(Dispatchers.IO) {
        val allIncidents = db.incidentDao().getAllIncidentsList()
        val now = System.currentTimeMillis()
        var escalatedCount = 0

        for (inc in allIncidents) {
            if ((inc.status == "REGISTRADO" || inc.status == "EN_ATENCION") && !inc.isEscalated) {
                if (inc.isSlaExceeded(now)) {
                    val diffMinutes = (now - inc.timestampMillis) / (60 * 1000)
                    val reason = "Excedió tiempo objetivo SLA (${inc.targetSlaMinutes} min). Transcurridos: ${diffMinutes} min."

                    db.incidentDao().markAsEscalated(inc.folio, reason, now)
                    escalatedCount++

                    // Notificación de alta prioridad por escalamiento a Administración
                    try {
                        SmartNotificationHub.notifyAdminEscalatedIncident(
                            context = context,
                            db = db,
                            folio = inc.folio,
                            location = inc.location,
                            escalationReason = "🚨 SLA EXCEDIDO ($diffMinutes min): ${inc.aiSummary}"
                        )
                        SmartNotificationHub.notifySupervisorUnattendedIncident(
                            context = context,
                            db = db,
                            folio = inc.folio,
                            location = inc.location,
                            elapsedMins = diffMinutes.toInt()
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("IncidentEngine", "Error notifying escalation: ${e.message}")
                    }

                    // Auditoría del escalamiento
                    db.auditLogDao().insertAuditLog(
                        AuditLogEntity(
                            folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                            operatorName = "ALFHA_SLA_ENGINE",
                            actionType = "INCIDENT_ESCALATED",
                            location = inc.location,
                            targetEntity = inc.folio,
                            changeDetails = "Escalamiento automático por SLA excedido: $reason",
                            resultStatus = "ALERTA",
                            timestampMillis = now
                        )
                    )
                }
            }
        }

        escalatedCount
    }
}
