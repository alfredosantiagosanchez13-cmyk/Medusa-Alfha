package com.example.data.supervision

import android.content.Context
import com.example.data.alerts.OperationalAlertEntity
import com.example.data.audit.AuditLogEntity
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.core.LocalDataBackupManager
import com.example.data.incident.EmergencyLocationEngine
import com.example.data.incident.GpsCoordinates
import com.example.data.notifications.SmartNotificationHub
import com.example.utils.ResidentNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * FASE 17: MOTOR DE SUPERVISIÓN TÁCTICA Y RONDINES INTELIGENTES
 *
 * Funciones clave:
 * 1. Generación de folios automáticos de rondín (RON-YYYYMMDD-XXXX).
 * 2. Validación de proximidad GPS punto por punto (< 80 metros de tolerancia).
 * 3. Detección automática de puntos omitidos o fuera de ubicación.
 * 4. Generación de alertas críticas inmediatas y despacho multicanal.
 * 5. Cierre automático de rondín e informe ejecutivo certificado con firma SHA-256.
 * 6. Registro de Tiempo Devuelto (eliminación de formatos físicos, fotos dispersas y recaptura).
 */
object SupervisionTourEngine {

    const val GPS_TOLERANCE_METERS = 80.0f // Radio de validez geográfica en metros

    /**
     * Genera un folio único consecutivo para el rondín.
     */
    fun generateTourFolio(): String {
        return AlphaCoreEngine.generateUniqueFolio("RON")
    }

    /**
     * Calcula la distancia geodésica en metros entre dos coordenadas mediante Haversine.
     */
    fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Float {
        val earthRadius = 6371000.0 // Metros
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadius * c).toFloat()
    }

    /**
     * Valida si la ubicación actual del supervisor corresponde a la coordenada del punto de control.
     */
    fun validateCheckpointGps(
        currentGps: GpsCoordinates?,
        checkpoint: SupervisionCheckpoint
    ): GpsValidationResult {
        val targetCoordStr = "%.5f, %.5f".format(Locale.US, checkpoint.targetLat, checkpoint.targetLng)

        if (currentGps == null) {
            // Si el hardware no entrega fix satelital en interiores, se usa la coordenada esperada con bandera
            return GpsValidationResult(
                distanceMeters = 0f,
                isWithinTolerance = true,
                statusLabel = "GPS GARITA / RESIDENCIAL",
                targetCoordinatesFormatted = targetCoordStr,
                capturedCoordinatesFormatted = "$targetCoordStr (Estimado Garita)",
                accuracyMeters = null
            )
        }

        val distance = calculateDistanceMeters(
            lat1 = currentGps.latitude,
            lon1 = currentGps.longitude,
            lat2 = checkpoint.targetLat,
            lon2 = checkpoint.targetLng
        )

        val isWithin = distance <= GPS_TOLERANCE_METERS
        val capturedCoordStr = "%.5f, %.5f".format(Locale.US, currentGps.latitude, currentGps.longitude)

        val statusLabel = if (isWithin) {
            "EN RANGO (%.0f m)".format(Locale.US, distance)
        } else {
            "FUERA DE UBICACIÓN (%.0f m)".format(Locale.US, distance)
        }

        return GpsValidationResult(
            distanceMeters = distance,
            isWithinTolerance = isWithin,
            statusLabel = statusLabel,
            targetCoordinatesFormatted = targetCoordStr,
            capturedCoordinatesFormatted = capturedCoordStr,
            accuracyMeters = currentGps.accuracyMeters
        )
    }

    /**
     * Registra una inspección de punto de control en Room SQLite.
     */
    suspend fun recordCheckpointAudit(
        context: Context,
        db: AppDatabase,
        tourFolio: String,
        route: SupervisionRoute,
        checkpoint: SupervisionCheckpoint,
        condition: String, // OPTIMO, REGULAR, CRITICO, OMITIDO, FUERA_UBICACION
        findings: String,
        photoPath: String?,
        correctiveAction: String,
        responsibleParty: String,
        currentGps: GpsCoordinates?
    ): SupervisionAuditEntity = withContext(Dispatchers.IO) {
        val auditDao = db.supervisionAuditDao()
        val auditLogDao = db.auditLogDao()
        val alertDao = db.operationalAlertDao()

        val gpsResult = validateCheckpointGps(currentGps, checkpoint)
        val pointFolio = "${tourFolio}-${checkpoint.id}"
        val isCritical = condition.equals("CRITICO", ignoreCase = true)
        val isOmitted = condition.equals("OMITIDO", ignoreCase = true)

        val riskLevel = when {
            isCritical -> "CRITICO"
            condition.equals("REGULAR", ignoreCase = true) -> "MEDIO"
            isOmitted -> "ALTO"
            !gpsResult.isWithinTolerance -> "MEDIO"
            else -> "BAJO"
        }

        val effectiveCondition = if (!gpsResult.isWithinTolerance && condition == "OPTIMO") {
            "FUERA_UBICACION"
        } else {
            condition
        }

        val formattedGps = "${gpsResult.capturedCoordinatesFormatted} [${gpsResult.statusLabel}]"

        val auditEntity = SupervisionAuditEntity(
            folio = pointFolio,
            supervisorName = responsibleParty,
            checkpointName = checkpoint.name,
            areaName = checkpoint.area,
            statusCondition = effectiveCondition,
            findingsDescription = findings.ifBlank {
                if (isOmitted) "Punto omitido por supervisor durante el rondín."
                else "Verificación rutinaria de seguridad conforme a protocolo."
            },
            riskLevel = riskLevel,
            correctiveActionRequired = correctiveAction.ifBlank {
                if (isCritical || riskLevel == "ALTO") "Inspección técnica inmediata requerida."
                else "Mantener monitoreo estándar."
            },
            responsibleParty = responsibleParty,
            commitmentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
            gpsCoordinates = formattedGps,
            photoEvidencePath = photoPath,
            durationMinutes = 4,
            timestampMillis = System.currentTimeMillis(),
            isClosed = false
        )

        auditDao.insertAudit(auditEntity)

        // Encolar para Sincronización Persistente Offline (FASE 19)
        try {
            com.example.data.sync.OfflineSyncEngine.enqueueOperation(
                db = db,
                operationType = "SUPERVISION_TOUR",
                targetFolio = pointFolio,
                targetModule = "RONDINES",
                payloadJson = "{\"folio\":\"$pointFolio\",\"checkpoint\":\"${checkpoint.name}\",\"condition\":\"$effectiveCondition\",\"risk\":\"$riskLevel\",\"supervisor\":\"$responsibleParty\"}",
                operatorName = responsibleParty,
                operatorRole = "SUPERVISOR",
                locationName = checkpoint.name,
                latitude = currentGps?.latitude,
                longitude = currentGps?.longitude,
                evidencePaths = photoPath,
                deviceGateId = "Terminal Táctica de Supervisión"
            )
        } catch (e: Exception) {
            android.util.Log.e("SupervisionTourEngine", "Error enqueuing supervision sync: ${e.message}")
        }

        // Registrar en bitácora inmutable
        auditLogDao.insertAuditLog(
            AuditLogEntity(
                operatorName = responsibleParty,
                actionType = if (isOmitted) "CHECKPOINT_OMITTED" else "CHECKPOINT_AUDITED",
                location = checkpoint.name,
                targetEntity = pointFolio,
                changeDetails = "Punto ${checkpoint.sequence}/${route.checkpoints.size} [$effectiveCondition] ${gpsResult.statusLabel}"
            )
        )

        // Alerta automática si es un hallazgo CRÍTICO
        if (isCritical) {
            val alertFolio = AlphaCoreEngine.generateUniqueFolio("ALT-SUP")
            val alert = OperationalAlertEntity(
                folio = alertFolio,
                alertType = "HALLAZGO_CRITICO_RONDIN",
                priorityLevel = "CRITICA",
                whatHappened = "🚨 Hallazgo crítico en punto [${checkpoint.name}]: ${auditEntity.findingsDescription}",
                whereLocation = "${checkpoint.name} (${checkpoint.area})",
                whenFormatted = SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault()).format(Date()),
                whyItMatters = "Vulnerabilidad perimetral o de infraestructura detectada durante la supervisión táctica.",
                whoMustAttend = "Supervisor de Turno y Administrador",
                recommendedAction = auditEntity.correctiveActionRequired,
                status = "ACTIVA",
                relatedEntitiesCount = 1,
                relatedFoliosJson = pointFolio,
                timestampMillis = System.currentTimeMillis()
            )
            alertDao.insertAlert(alert)

            // Notificaciones multirrol
            SmartNotificationHub.notifySupervisorCriticalFinding(
                context = context,
                db = db,
                supervisionFolio = pointFolio,
                checkpointName = checkpoint.name,
                findingDetail = auditEntity.findingsDescription
            )

            SmartNotificationHub.notifyGuardCriticalAlert(
                context = context,
                db = db,
                alertFolio = alertFolio,
                location = checkpoint.name,
                description = auditEntity.findingsDescription,
                actionRequired = auditEntity.correctiveActionRequired
            )

            SmartNotificationHub.notifyBoardHighPriorityEvent(
                context = context,
                db = db,
                folio = alertFolio,
                titleSummary = "Hallazgo Crítico en ${checkpoint.name}",
                executiveDetail = auditEntity.findingsDescription,
                isCritical = true
            )

            ResidentNotificationManager.notifyCriticalIncident(
                context = context,
                folio = alertFolio,
                location = checkpoint.name,
                category = "HALLAZGO CRÍTICO EN RONDA",
                summary = auditEntity.findingsDescription
            )
        }

        auditEntity
    }

    /**
     * Cierra automáticamente el rondín, marcando puntos pendientes como OMITIDOS
     * y generando el informe ejecutivo con certificación criptográfica SHA-256.
     */
    suspend fun closeTourAndGenerateReport(
        context: Context,
        db: AppDatabase,
        tourFolio: String,
        route: SupervisionRoute,
        supervisorName: String,
        startTimeMillis: Long,
        activeAudits: List<SupervisionAuditEntity>
    ): SupervisionExecutiveReport = withContext(Dispatchers.IO) {
        val auditDao = db.supervisionAuditDao()
        val auditLogDao = db.auditLogDao()
        val endMillis = System.currentTimeMillis()
        val durationMins = ((endMillis - startTimeMillis) / (60 * 1000)).toInt().coerceAtLeast(1)

        // 1. Detectar puntos omitidos (no registrados en activeAudits)
        val recordedPointNames = activeAudits.map { it.checkpointName }.toSet()
        val allAuditsList = mutableListOf<SupervisionAuditEntity>()
        allAuditsList.addAll(activeAudits)

        route.checkpoints.forEach { cp ->
            if (!recordedPointNames.contains(cp.name)) {
                // Registrar automáticamente como OMITIDO
                val omittedAudit = SupervisionAuditEntity(
                    folio = "${tourFolio}-${cp.id}",
                    supervisorName = supervisorName,
                    checkpointName = cp.name,
                    areaName = cp.area,
                    statusCondition = "OMITIDO",
                    findingsDescription = "Punto de control no verificado al cierre de la ronda.",
                    riskLevel = "ALTO",
                    correctiveActionRequired = "Programar recorrido complementario para validar punto.",
                    responsibleParty = supervisorName,
                    commitmentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                    gpsCoordinates = "UBICACIÓN NO REGISTRADA [Punto Omitido]",
                    durationMinutes = 0,
                    timestampMillis = endMillis,
                    isClosed = true
                )
                auditDao.insertAudit(omittedAudit)
                allAuditsList.add(omittedAudit)
            }
        }

        // 2. Construir Informe Ejecutivo Inmutable
        val report = SupervisionExecutiveReport.buildFromAudits(
            tourFolio = tourFolio,
            supervisorName = supervisorName,
            mainLocation = route.name,
            tourAudits = allAuditsList,
            durationMinutes = durationMins
        )

        // 3. Registrar registro de cierre en Room
        val closingAudit = SupervisionAuditEntity(
            folio = tourFolio,
            supervisorName = supervisorName,
            checkpointName = "Cierre de Rondín [${route.code}]",
            areaName = route.name,
            statusCondition = if (report.criticalCount > 0) "CRITICO" else if (report.regularCount > 0) "REGULAR" else "OPTIMO",
            findingsDescription = "Rondín finalizado. Duración: $durationMins min. Puntos: ${allAuditsList.size} (${report.optimumCount} Óptimos, ${report.regularCount} Regulares, ${report.criticalCount} Críticos). Hash SHA-256: ${report.integrityHashSha256.take(12)}...",
            riskLevel = if (report.criticalCount > 0) "CRITICO" else if (report.regularCount > 0) "MEDIO" else "BAJO",
            correctiveActionRequired = report.correctiveActionsSummary,
            responsibleParty = supervisorName,
            commitmentDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
            gpsCoordinates = "Ruta Completa Certificada",
            durationMinutes = durationMins,
            timestampMillis = endMillis,
            isClosed = true
        )
        auditDao.insertAudit(closingAudit)

        // Encolar para Sincronización Persistente Offline (FASE 19)
        try {
            com.example.data.sync.OfflineSyncEngine.enqueueOperation(
                db = db,
                operationType = "SUPERVISION_TOUR",
                targetFolio = tourFolio,
                targetModule = "RONDINES",
                payloadJson = "{\"tourFolio\":\"$tourFolio\",\"route\":\"${route.name}\",\"optimum\":${report.optimumCount},\"regular\":${report.regularCount},\"critical\":${report.criticalCount},\"hash\":\"${report.integrityHashSha256}\"}",
                operatorName = supervisorName,
                operatorRole = "SUPERVISOR",
                locationName = route.name,
                deviceGateId = "Terminal Táctica de Supervisión"
            )
        } catch (e: Exception) {
            android.util.Log.e("SupervisionTourEngine", "Error enqueuing tour close sync: ${e.message}")
        }

        // 4. Registrar en Auditoría
        auditLogDao.insertAuditLog(
            AuditLogEntity(
                operatorName = supervisorName,
                actionType = "TOUR_CLOSED_CERTIFIED",
                location = route.name,
                targetEntity = tourFolio,
                changeDetails = "Cierre de rondín certificado con hash SHA-256. ${report.finalResult}"
            )
        )

        // 5. Limpiar estado de respaldo offline
        LocalDataBackupManager.clearOngoingTourState(context)

        // 6. Notificaciones y alertas
        ResidentNotificationManager.notifySupervisionClosed(
            context = context,
            folio = tourFolio,
            supervisorName = supervisorName,
            checkpointsCount = allAuditsList.size,
            durationMins = durationMins
        )

        SmartNotificationHub.autoResolveNotificationsForFolio(
            db = db,
            folio = tourFolio,
            resolvedBy = supervisorName
        )

        report
    }
}
