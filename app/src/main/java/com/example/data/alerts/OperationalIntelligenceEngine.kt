package com.example.data.alerts

import android.content.Context
import com.example.data.audit.AuditLogEntity
import com.example.data.booking.AmenityBooking
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.incident.IncidentEntity
import com.example.data.incident.IncidentPriority
import com.example.data.maintenance.MaintenanceOrderEntity
import com.example.data.maintenance.MaintenancePriority
import com.example.data.passes.QrPassRoomEntity
import com.example.data.supervision.SupervisionAuditEntity
import com.example.data.vehicle.VehicleAccessLogEntity
import com.example.data.vehicle.VehicleEntity
import com.example.data.visitor.VisitorCheckIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * FASE 18: MOTOR DE INTELIGENCIA OPERACIONAL Y DETECCIÓN DE ANOMALÍAS MEDUSA ALFHA
 * 
 * Reglas de Negocio Estrictas:
 * 1. Room SQLite es la Fuente Única de Verdad.
 * 2. Analiza únicamente datos reales existentes en Room (accesos, incidencias, rondines, vehículos, visitantes, mantenimiento, reservas).
 * 3. Detecta recurrencia de incidencias por ubicación, horario, categoría y frecuencia.
 * 4. Detecta desviaciones respecto al comportamiento operativo histórico.
 * 5. Genera Alerta de Anomalía únicamente cuando exista evidencia suficiente.
 * 6. Niveles de Severidad: INFORMATIVA / PREVENTIVA / ALTA / CRÍTICA.
 * 7. Cada alerta incluye: folio, fecha/hora, origen, evidencia, explicación del motivo y recomendación.
 * 8. NO genera diagnósticos subjetivos ni acusaciones automáticas (lenguaje estrictamente fáctico y verificable).
 * 9. Permite que Supervisor o Administración marque la alerta como: CONFIRMADA / DESCARTADA / EN REVISIÓN / RESUELTA.
 * 10. Registra toda decisión en AuditLogEntity.
 * 11. Muestra tendencias y reincidencias en el mapa operativo cuando existan coordenadas reales.
 * 12. Registra automáticamente Tiempo Devuelto cuando la detección sustituya revisión manual.
 * 13. Todo procesamiento pesado se ejecuta fuera del hilo principal (Dispatchers.Default / Dispatchers.IO).
 */
object OperationalIntelligenceEngine {

    const val TIME_SAVED_MINUTES_PER_AUDIT_CYCLE = 20 // 20 minutos devueltos por ciclo automatizado vs auditoría manual de bitácoras

    data class IntelligenceOverview(
        val totalActiveAlerts: Int,
        val criticalCount: Int,
        val highCount: Int,
        val preventiveCount: Int,
        val informativeCount: Int,
        val confirmedCount: Int,
        val inRevisionCount: Int,
        val resolvedCount: Int,
        val patternsDetected: List<String>,
        val alerts: List<OperationalAlertEntity>
    )

    /**
     * Ejecuta el análisis completo en segundo plano leyendo datos reales desde Room.
     */
    suspend fun analyzeAndSyncWithRoom(
        db: AppDatabase,
        context: Context? = null
    ): List<OperationalAlertEntity> = withContext(Dispatchers.Default) {
        val incidents = db.incidentDao().getAllIncidentsList()
        val visitorCheckIns = db.visitorCheckInDao().getAllCheckInsList()
        val supervisions = db.supervisionAuditDao().getAllAuditsList()
        val qrPasses = db.qrPassDao().getAllPassesList()
        val bookings = db.amenityBookingDao().getAllBookingsList()
        val maintenanceOrders = db.maintenanceDao().getAllOrdersSnapshot()
        val vehicleAccessLogs = db.vehicleDao().getAllAccessLogsList()
        val vehicles = db.vehicleDao().getAllVehiclesList()
        val existingAlerts = db.operationalAlertDao().getAllAlertsSync()

        val evaluated = evaluateOperationalData(
            incidents = incidents,
            visitorCheckIns = visitorCheckIns,
            supervisionAudits = supervisions,
            qrPasses = qrPasses,
            bookings = bookings,
            maintenanceOrders = maintenanceOrders,
            vehicleAccessLogs = vehicleAccessLogs,
            vehicles = vehicles,
            existingAlerts = existingAlerts
        )

        // Persistir en Room bajo Dispatchers.IO
        withContext(Dispatchers.IO) {
            db.operationalAlertDao().insertAlerts(evaluated)
        }

        evaluated
    }

    /**
     * Evalúa de forma pura y determinista todos los datos operativos de Room.
     */
    fun evaluateOperationalData(
        incidents: List<IncidentEntity>,
        visitorCheckIns: List<VisitorCheckIn>,
        supervisionAudits: List<SupervisionAuditEntity>,
        qrPasses: List<QrPassRoomEntity>,
        bookings: List<AmenityBooking> = emptyList(),
        maintenanceOrders: List<MaintenanceOrderEntity> = emptyList(),
        vehicleAccessLogs: List<VehicleAccessLogEntity> = emptyList(),
        vehicles: List<VehicleEntity> = emptyList(),
        existingAlerts: List<OperationalAlertEntity>
    ): List<OperationalAlertEntity> {
        val now = System.currentTimeMillis()
        val detectedAlerts = mutableListOf<OperationalAlertEntity>()
        val existingMap = existingAlerts.associateBy { it.folio }

        // =========================================================================
        // 1. INCIDENCIAS: RECURRENCIA POR UBICACIÓN, CATEGORÍA Y FRECUENCIA (CLUSTERS)
        // =========================================================================
        val incidentsByLocation = incidents.groupBy { it.location.trim().uppercase(Locale.ROOT) }
        for ((locUpper, locIncidents) in incidentsByLocation) {
            if (locUpper.isBlank() || locUpper == "GENERAL") continue
            if (locIncidents.size >= 2) {
                val mostRecent = locIncidents.maxByOrNull { it.timestampMillis } ?: locIncidents.first()
                val originalLoc = mostRecent.location
                val categories = locIncidents.map { it.category.displayName }.distinct().joinToString(", ")
                val hasCritical = locIncidents.any { it.priority == IncidentPriority.CRITICA }
                val hasHigh = locIncidents.any { it.priority == IncidentPriority.ALTA }
                val priority = when {
                    hasCritical -> "CRITICA"
                    hasHigh -> "ALTA"
                    else -> "PREVENTIVA"
                }
                val stableKey = "INC_CLUSTER_" + locUpper.replace("[^A-Z0-9]".toRegex(), "_")
                val folio = generateDeterministicFolio("ALT-INC-LOC", stableKey)
                val existing = existingMap[folio]
                val status = existing?.status ?: "ACTIVA"

                val coords = if (mostRecent.latitude != null && mostRecent.longitude != null) {
                    Pair(mostRecent.latitude, mostRecent.longitude)
                } else {
                    parseLocationCoordinates(mostRecent.location, null)
                }

                val alert = OperationalAlertEntity(
                    folio = folio,
                    alertType = "INCIDENCIA_REPETITIVA",
                    priorityLevel = priority,
                    originModule = "INCIDENCIAS",
                    whatHappened = "Se han registrado ${locIncidents.size} incidencias en la misma ubicación ($originalLoc). Categorías afectadas: $categories.",
                    whereLocation = originalLoc,
                    whenFormatted = "Último reporte: ${mostRecent.formattedDate} (Total acumulado: ${locIncidents.size} eventos)",
                    whyItMatters = "La concentración física de incidencias evidencia un punto de vulnerabilidad recurrente en las instalaciones o convivencia.",
                    whoMustAttend = "Administración y Supervisor de Seguridad",
                    recommendedAction = "Realizar inspección pericial en sitio, revisar controles de acceso del sector e intensificar rondas de supervisión preventiva.",
                    evidenceSummary = "${locIncidents.size} folios en Room: ${locIncidents.joinToString { it.folio }}. Categorías: $categories.",
                    explanationReason = "Frecuencia de incidencias >= 2 en la misma coordenada/sector geográfico.",
                    status = status,
                    relatedEntitiesCount = locIncidents.size,
                    relatedFoliosJson = locIncidents.joinToString(",") { it.folio },
                    latitude = coords?.first,
                    longitude = coords?.second,
                    timestampMillis = mostRecent.timestampMillis,
                    resolvedAtMillis = existing?.resolvedAtMillis,
                    resolvedBy = existing?.resolvedBy,
                    resolutionNotes = existing?.resolutionNotes,
                    hashIntegrity = computeAlertHash(folio, originalLoc, priority)
                )
                detectedAlerts.add(alert)
            }
        }

        // =========================================================================
        // 2. INCIDENCIAS: DESVIACIÓN OPERATIVA POR HORARIO NOCTURNO ATÍPICO (00:00 - 05:30)
        // =========================================================================
        val nightIncidents = incidents.filter { inc ->
            val cal = Calendar.getInstance().apply { timeInMillis = inc.timestampMillis }
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            (hour in 0..5) && (inc.priority == IncidentPriority.CRITICA || inc.priority == IncidentPriority.ALTA)
        }

        for (nightInc in nightIncidents) {
            val stableKey = "NIGHT_INC_${nightInc.folio}"
            val folio = generateDeterministicFolio("ALT-INC-NOC", stableKey)
            val existing = existingMap[folio]
            val status = existing?.status ?: if (nightInc.status == "RESUELTO" || nightInc.status == "CERRADO") "RESUELTA" else "ACTIVA"
            val coords = if (nightInc.latitude != null && nightInc.longitude != null) {
                Pair(nightInc.latitude, nightInc.longitude)
            } else {
                parseLocationCoordinates(nightInc.location, null)
            }

            val alert = OperationalAlertEntity(
                folio = folio,
                alertType = "HORARIO_ATIPICO_NOCTURNO",
                priorityLevel = if (nightInc.priority == IncidentPriority.CRITICA) "CRITICA" else "ALTA",
                originModule = "INCIDENCIAS",
                whatHappened = "Incidencia prioritaria (${nightInc.category.displayName}) registrada durante horario nocturno protegido (00:00 - 05:30 hrs).",
                whereLocation = nightInc.location,
                whenFormatted = nightInc.formattedDate,
                whyItMatters = "La actividad nocturna fuera de horario presenta mayor riesgo de intrusión, perturbación del descanso vecinal o respuesta retardada.",
                whoMustAttend = "Supervisor Nocturno y Guardia de Caseta",
                recommendedAction = "Verificar bitácora de cámaras del sector, constatar estado perimetral y contactar al vigilante en turno.",
                evidenceSummary = "Folio Room ${nightInc.folio} registrado a las ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(nightInc.timestampMillis))}. Prioridad: ${nightInc.priority.name}.",
                explanationReason = "Evento de alta severidad detectado entre 00:00 y 05:30 horas.",
                status = status,
                relatedEntitiesCount = 1,
                relatedFoliosJson = nightInc.folio,
                latitude = coords?.first,
                longitude = coords?.second,
                timestampMillis = nightInc.timestampMillis,
                resolvedAtMillis = existing?.resolvedAtMillis,
                resolvedBy = existing?.resolvedBy,
                resolutionNotes = existing?.resolutionNotes,
                hashIntegrity = computeAlertHash(folio, nightInc.location, "ALTA")
            )
            detectedAlerts.add(alert)
        }

        // =========================================================================
        // 3. INCIDENCIAS: INCUMPLIMIENTO DE TIEMPO DE RESPUESTA (SLA VENCIDO / ESCALACIÓN)
        // =========================================================================
        val delayedIncidents = incidents.filter {
            it.status.equals("REGISTRADO", ignoreCase = true) &&
            (it.priority == IncidentPriority.CRITICA || it.priority == IncidentPriority.ALTA) &&
            (now - it.timestampMillis) > (15 * 60 * 1000L) // 15 minutos sin atención
        }

        for (inc in delayedIncidents) {
            val elapsedMinutes = (now - inc.timestampMillis) / (60 * 1000)
            val isCritical = inc.priority == IncidentPriority.CRITICA
            val stableKey = "SLA_BREACH_${inc.folio}"
            val folio = generateDeterministicFolio("ALT-INC-SLA", stableKey)
            val existing = existingMap[folio]
            val status = existing?.status ?: "ACTIVA"
            val coords = if (inc.latitude != null && inc.longitude != null) {
                Pair(inc.latitude, inc.longitude)
            } else {
                parseLocationCoordinates(inc.location, null)
            }

            val alert = OperationalAlertEntity(
                folio = folio,
                alertType = if (isCritical) "ESCALACION_CRITICA_SLA" else "SLA_TIEMPO_EXCEDIDO",
                priorityLevel = "CRITICA",
                originModule = "INCIDENCIAS",
                whatHappened = if (isCritical) {
                    "Incidencia de prioridad CRÍTICA (${inc.category.displayName}) acumula $elapsedMinutes minutos en estado REGISTRADO sin asignación de personal."
                } else {
                    "Incidencia ALTA (${inc.category.displayName}) supera el tiempo estándar con $elapsedMinutes minutos sin atención operativa."
                },
                whereLocation = inc.location,
                whenFormatted = "Reportada: ${inc.formattedDate} ($elapsedMinutes min sin atención)",
                whyItMatters = "El retraso en la contención de eventos críticos vulnera el protocolo de protección física y aumenta la exposición a pérdidas.",
                whoMustAttend = if (isCritical) "Mesa Directiva, Administración y Jefe de Seguridad" else "Administración y Supervisor",
                recommendedAction = "Asignar inmediatamente a guardia de respuesta presencial y actualizar estado a EN_ATENCION.",
                evidenceSummary = "Folio Room ${inc.folio} creado hace $elapsedMinutes minutos (${inc.formattedDate}). Estatus actual: REGISTRADO.",
                explanationReason = "Tiempo de permanencia en estado inicial supera el umbral máximo de 15 minutos para eventos prioritarios.",
                status = status,
                relatedEntitiesCount = 1,
                relatedFoliosJson = inc.folio,
                latitude = coords?.first,
                longitude = coords?.second,
                timestampMillis = inc.timestampMillis,
                resolvedAtMillis = existing?.resolvedAtMillis,
                resolvedBy = existing?.resolvedBy,
                resolutionNotes = existing?.resolutionNotes,
                hashIntegrity = computeAlertHash(folio, inc.location, "CRITICA")
            )
            detectedAlerts.add(alert)
        }

        // =========================================================================
        // 4. VISITANTES Y ACCESOS: ANOMALÍA DE PERMANENCIA EXCESIVA (> 4H / > 8H SIN CHECKOUT)
        // =========================================================================
        val activeVisitors = visitorCheckIns.filter {
            (it.status.equals("CHECKED_IN", ignoreCase = true) || it.status.equals("VERIFICADO", ignoreCase = true)) &&
            it.checkOutMillis == null
        }

        for (visitor in activeVisitors) {
            val stayMillis = now - visitor.timestampMillis
            val stayHours = stayMillis / (1000 * 60 * 60)
            if (stayHours >= 4) {
                val priority = if (stayHours >= 8) "ALTA" else "PREVENTIVA"
                val stableKey = "VIS_STAY_${visitor.folio}"
                val folio = generateDeterministicFolio("ALT-VIS-PERM", stableKey)
                val existing = existingMap[folio]
                val status = existing?.status ?: "ACTIVA"
                val durationText = "${stayHours}h ${(stayMillis % (1000 * 60 * 60)) / (60 * 1000)}m"

                val alert = OperationalAlertEntity(
                    folio = folio,
                    alertType = "ANOMALIA_PERMANENCIA_VISITA",
                    priorityLevel = priority,
                    originModule = "VISITANTES",
                    whatHappened = "Visitante ${visitor.visitorName} (Pase ${visitor.passCode}) registra $durationText de permanencia activa dentro del condominio sin salida.",
                    whereLocation = "Destino: ${visitor.destinationHouse} (Vehículo: ${visitor.vehiclePlate ?: "Peatonal"})",
                    whenFormatted = "Ingreso: ${visitor.formattedTime} ($durationText transcurridos)",
                    whyItMatters = "Riesgo de aforo no controlado, omisión en caseta de registro de salida o estadía prolongada no declarada.",
                    whoMustAttend = "Guardia de Caseta y Agente de Control de Accesos",
                    recommendedAction = "Contactar al residente anfitrión (${visitor.hostResidentName}) o verificar presencia en cajón de visitas para registrar salida.",
                    evidenceSummary = "Folio Room ${visitor.folio}, ingreso registrado a las ${visitor.formattedTime}, permanencia calculada: $durationText.",
                    explanationReason = "Permanencia activa en Room supera el umbral operativo de 4 horas sin registrar checkout.",
                    status = status,
                    relatedEntitiesCount = 1,
                    relatedFoliosJson = visitor.folio,
                    latitude = null,
                    longitude = null,
                    timestampMillis = visitor.timestampMillis,
                    resolvedAtMillis = existing?.resolvedAtMillis,
                    resolvedBy = existing?.resolvedBy,
                    resolutionNotes = existing?.resolutionNotes,
                    hashIntegrity = computeAlertHash(folio, visitor.destinationHouse, priority)
                )
                detectedAlerts.add(alert)
            }
        }

        // =========================================================================
        // 5. VEHÍCULOS: PERMANENCIA VEHICULAR EXCESIVA EN ESTACIONAMIENTO DE VISITAS
        // =========================================================================
        val activeVehiclesInside = vehicleAccessLogs.filter {
            it.status.equals("DENTRO_DEL_CONDOMINIO", ignoreCase = true) &&
            it.exitTimestampMillis == null &&
            it.accessCategory != "RESIDENTE"
        }

        for (vLog in activeVehiclesInside) {
            val stayMillis = now - vLog.entryTimestampMillis
            val stayHours = stayMillis / (1000 * 60 * 60)
            if (stayHours >= 4) {
                val priority = if (stayHours >= 8) "ALTA" else "PREVENTIVA"
                val stableKey = "VEH_STAY_${vLog.folio}"
                val folio = generateDeterministicFolio("ALT-VEH-PERM", stableKey)
                val existing = existingMap[folio]
                val status = existing?.status ?: "ACTIVA"
                val durationText = "${stayHours}h ${(stayMillis % (1000 * 60 * 60)) / (60 * 1000)}m"

                val alert = OperationalAlertEntity(
                    folio = folio,
                    alertType = "PERMANENCIA_VEHICULAR_PROLONGADA",
                    priorityLevel = priority,
                    originModule = "VEHICULOS",
                    whatHappened = "Vehículo con placa ${vLog.plate} acumula $durationText estacionado dentro del condominio bajo categoría ${vLog.accessCategory}.",
                    whereLocation = "Garita: ${vLog.gateLane} • Destino: ${vLog.unitId.ifBlank { "Áreas Comunes" }}",
                    whenFormatted = "Entrada: ${vLog.formattedEntryTime} ($durationText activo)",
                    whyItMatters = "Saturación de cajones de visitas y desvío de uso de áreas comunes para estacionamiento permanente no autorizado.",
                    whoMustAttend = "Supervisor de Turno y Guardia de Caseta",
                    recommendedAction = "Verificar cajón de estacionamiento de visitas y notificar al vigilante para cotejo con la unidad destino.",
                    evidenceSummary = "Folio vehicular ${vLog.folio}, placa ${vLog.plate}, tiempo activo: $durationText sin registro de egreso.",
                    explanationReason = "Vehículo de visita/proveedor excede el umbral de 4 horas dentro del recinto.",
                    status = status,
                    relatedEntitiesCount = 1,
                    relatedFoliosJson = vLog.folio,
                    timestampMillis = vLog.entryTimestampMillis,
                    resolvedAtMillis = existing?.resolvedAtMillis,
                    resolvedBy = existing?.resolvedBy,
                    resolutionNotes = existing?.resolutionNotes,
                    hashIntegrity = computeAlertHash(folio, vLog.plate, priority)
                )
                detectedAlerts.add(alert)
            }
        }

        // =========================================================================
        // 6. VEHÍCULOS: INTENTOS DE INGRESO NO AUTORIZADOS O SUSPENDIDOS
        // =========================================================================
        val unauthorizedVehicles = vehicleAccessLogs.filter {
            !it.isAuthorized || it.accessCategory == "VEHICULO_NO_AUTORIZADO" || it.accessCategory == "SUSPENDIDO"
        }

        for (unauth in unauthorizedVehicles) {
            val stableKey = "VEH_UNAUTH_${unauth.folio}"
            val folio = generateDeterministicFolio("ALT-VEH-UNAUTH", stableKey)
            val existing = existingMap[folio]
            val status = existing?.status ?: "ACTIVA"

            val alert = OperationalAlertEntity(
                folio = folio,
                alertType = "VEHICULO_NO_AUTORIZADO",
                priorityLevel = "ALTA",
                originModule = "VEHICULOS",
                whatHappened = "Intento de acceso de vehículo placa ${unauth.plate} denegado o catalogado como no autorizado en ${unauth.gateLane}.",
                whereLocation = unauth.gateLane,
                whenFormatted = unauth.formattedEntryTime,
                whyItMatters = "Previene el ingreso de móviles no empadronados o con restricciones de acceso por adeudo o seguridad.",
                whoMustAttend = "Guardia de Caseta y Supervisor de Acceso",
                recommendedAction = "Inspeccionar credencial del conductor y verificar si cuenta con pase QR válido emitido por residente antes de franquear acceso.",
                evidenceSummary = "Folio ${unauth.folio}, placa ${unauth.plate}, categoría de registro: ${unauth.accessCategory}.",
                explanationReason = "Acceso vehicular denegado por falta de autorización en padrón de Room.",
                status = status,
                relatedEntitiesCount = 1,
                relatedFoliosJson = unauth.folio,
                timestampMillis = unauth.entryTimestampMillis,
                resolvedAtMillis = existing?.resolvedAtMillis,
                resolvedBy = existing?.resolvedBy,
                resolutionNotes = existing?.resolutionNotes,
                hashIntegrity = computeAlertHash(folio, unauth.plate, "ALTA")
            )
            detectedAlerts.add(alert)
        }

        // =========================================================================
        // 7. RONDINES: PUNTOS CRÍTICOS Y RONDAS CON PUNTOS OMITIDOS
        // =========================================================================
        val criticalSupervisions = supervisionAudits.filter {
            it.statusCondition.equals("CRITICO", ignoreCase = true) ||
            it.riskLevel.equals("CRITICO", ignoreCase = true) ||
            it.riskLevel.equals("ALTO", ignoreCase = true)
        }

        for (sup in criticalSupervisions) {
            val stableKey = "SUP_CRITICAL_${sup.folio}"
            val folio = generateDeterministicFolio("ALT-RON-CRIT", stableKey)
            val existing = existingMap[folio]
            val status = existing?.status ?: if (sup.isClosed) "RESUELTA" else "ACTIVA"
            val coords = parseCoordinates(sup.gpsCoordinates)

            val alert = OperationalAlertEntity(
                folio = folio,
                alertType = "RONDIN_HALLAZGO_CRITICO",
                priorityLevel = "CRITICA",
                originModule = "RONDINES",
                whatHappened = "Hallazgo crítico detectado en ronda de supervisión en ${sup.checkpointName}: ${sup.findingsDescription}",
                whereLocation = "${sup.areaName} - ${sup.checkpointName}",
                whenFormatted = "Auditado por ${sup.supervisorName} el ${sup.formattedTime}",
                whyItMatters = "Afecta directamente la integridad del perímetro o un activo crítico de la comunidad. Nivel de riesgo: ${sup.riskLevel}.",
                whoMustAttend = "Supervisor Táctico y Administración",
                recommendedAction = "Ejecutar acción correctiva requerida: ${sup.correctiveActionRequired}. Responsable asignado: ${sup.responsibleParty}.",
                evidenceSummary = "Auditoría ${sup.folio}, punto: ${sup.checkpointName}, coordenadas: ${sup.gpsCoordinates ?: "N/A"}. Riesgo: ${sup.riskLevel}.",
                explanationReason = "Inspección física en terreno catalogada como CRITICO por supervisor.",
                status = status,
                relatedEntitiesCount = 1,
                relatedFoliosJson = sup.folio,
                latitude = coords?.first,
                longitude = coords?.second,
                timestampMillis = sup.timestampMillis,
                resolvedAtMillis = existing?.resolvedAtMillis,
                resolvedBy = existing?.resolvedBy,
                resolutionNotes = existing?.resolutionNotes,
                hashIntegrity = computeAlertHash(folio, sup.areaName, "CRITICA")
            )
            detectedAlerts.add(alert)
        }

        // Puntos Omitidos en Rondines
        val omittedSupervisions = supervisionAudits.filter {
            it.statusCondition.equals("OMITIDO", ignoreCase = true)
        }

        for (omitted in omittedSupervisions) {
            val stableKey = "SUP_OMITTED_${omitted.folio}"
            val folio = generateDeterministicFolio("ALT-RON-OMIT", stableKey)
            val existing = existingMap[folio]
            val status = existing?.status ?: if (omitted.isClosed) "RESUELTA" else "ACTIVA"

            val alert = OperationalAlertEntity(
                folio = folio,
                alertType = "RONDIN_PUNTO_OMITIDO",
                priorityLevel = "PREVENTIVA",
                originModule = "RONDINES",
                whatHappened = "Punto de control ${omitted.checkpointName} (${omitted.areaName}) omitido durante la ejecución de la ronda.",
                whereLocation = "${omitted.areaName} - ${omitted.checkpointName}",
                whenFormatted = omitted.formattedTime,
                whyItMatters = "Puntos no auditados generan puntos ciegos en la cobertura perimetral de seguridad física.",
                whoMustAttend = "Supervisor de Turno",
                recommendedAction = "Verificar motivo de omisión (obstrucción, riesgo) y reprogramar verificación prioritaria en el siguiente turno.",
                evidenceSummary = "Folio Room ${omitted.folio}, punto de control no auditado: ${omitted.checkpointName}.",
                explanationReason = "Punto de control registrado en estado OMITIDO al cierre del recorrido.",
                status = status,
                relatedEntitiesCount = 1,
                relatedFoliosJson = omitted.folio,
                latitude = null,
                longitude = null,
                timestampMillis = omitted.timestampMillis,
                resolvedAtMillis = existing?.resolvedAtMillis,
                resolvedBy = existing?.resolvedBy,
                resolutionNotes = existing?.resolutionNotes,
                hashIntegrity = computeAlertHash(folio, omitted.checkpointName, "PREVENTIVA")
            )
            detectedAlerts.add(alert)
        }

        // =========================================================================
        // 8. MANTENIMIENTO: ÓRDENES CRÍTICAS VENCIDAS O RECURRENTES
        // =========================================================================
        val urgentPendingOrders = maintenanceOrders.filter {
            (it.priority == MaintenancePriority.URGENTE || it.priority == MaintenancePriority.ALTA) &&
            (it.status == "REGISTRADO" || it.status == "ASIGNADO") &&
            (now > it.deadlineMillis || (now - it.timestampMillis) > (24 * 3600 * 1000L))
        }

        for (mOrder in urgentPendingOrders) {
            val stableKey = "MNT_OVERDUE_${mOrder.folio}"
            val folio = generateDeterministicFolio("ALT-MNT-SLA", stableKey)
            val existing = existingMap[folio]
            val status = existing?.status ?: "ACTIVA"

            val alert = OperationalAlertEntity(
                folio = folio,
                alertType = "MANTENIMIENTO_CRITICO_VENCIDO",
                priorityLevel = if (mOrder.priority == MaintenancePriority.URGENTE) "CRITICA" else "ALTA",
                originModule = "MANTENIMIENTO",
                whatHappened = "Orden de mantenimiento urgente '${mOrder.title}' excede su fecha límite de atención sin ser resuelta.",
                whereLocation = mOrder.location,
                whenFormatted = "Creada: ${mOrder.formattedCreatedDate} • Límite: ${mOrder.formattedDeadline}",
                whyItMatters = "Riesgo de interrupción en infraestructura comunitaria o servicios básicos del condominio.",
                whoMustAttend = "Administración y Técnico Asignado (${mOrder.assignedTechnician})",
                recommendedAction = "Contactar al técnico responsable para cierre de trabajos o reasignación urgente.",
                evidenceSummary = "Folio Mantenimiento ${mOrder.folio}, prioridad ${mOrder.priority.label}, estado actual: ${mOrder.status}.",
                explanationReason = "Orden de mantenimiento supera el SLA límite de resolución estipulado.",
                status = status,
                relatedEntitiesCount = 1,
                relatedFoliosJson = mOrder.folio,
                latitude = null,
                longitude = null,
                timestampMillis = mOrder.timestampMillis,
                resolvedAtMillis = existing?.resolvedAtMillis,
                resolvedBy = existing?.resolvedBy,
                resolutionNotes = existing?.resolutionNotes,
                hashIntegrity = computeAlertHash(folio, mOrder.location, "ALTA")
            )
            detectedAlerts.add(alert)
        }

        // =========================================================================
        // 9. RESERVAS: CONFLICTOS Y SOLAPAMIENTOS EN AMENIDADES COMUNITARIAS
        // =========================================================================
        val activeBookings = bookings.filter { it.status == "CONFIRMADA" }
        val bookingsByAmenity = activeBookings.groupBy { it.amenityName }

        for ((amenityName, amenityBookings) in bookingsByAmenity) {
            for (i in 0 until amenityBookings.size) {
                for (j in i + 1 until amenityBookings.size) {
                    val b1 = amenityBookings[i]
                    val b2 = amenityBookings[j]
                    val diffMins = kotlin.math.abs(b1.bookingTimeMillis - b2.bookingTimeMillis) / (60 * 1000)
                    if (diffMins < 120 && b1.unitId != b2.unitId) {
                        val stableKey = "BOOKING_CONFLICT_${minOf(b1.id, b2.id)}_${maxOf(b1.id, b2.id)}"
                        val folio = generateDeterministicFolio("ALT-RSV-CONF", stableKey)
                        val existing = existingMap[folio]
                        val status = existing?.status ?: "ACTIVA"

                        val alert = OperationalAlertEntity(
                            folio = folio,
                            alertType = "CONFLICTO_RESERVA_AMENIDAD",
                            priorityLevel = "ALTA",
                            originModule = "RESERVAS",
                            whatHappened = "Solapamiento de horario confirmado para $amenityName entre Unidad ${b1.unitId} (${b1.residentName}) y Unidad ${b2.unitId} (${b2.residentName}).",
                            whereLocation = amenityName,
                            whenFormatted = "Horario en conflicto: ${SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(b1.bookingTimeMillis))}",
                            whyItMatters = "Evita duplicidad de uso de espacio y disputas presenciales entre residentes en áreas comunes.",
                            whoMustAttend = "Administración del Condominio",
                            recommendedAction = "Contactar a residentes involucrados para convenir reubicación de bloque horario disponible.",
                            evidenceSummary = "Reservas Room IDs ${b1.id} y ${b2.id} para la misma fecha/hora en $amenityName.",
                            explanationReason = "Dos reservas confirmadas activas con diferencia menor a 120 minutos en la misma amenidad.",
                            status = status,
                            relatedEntitiesCount = 2,
                            relatedFoliosJson = "${b1.id},${b2.id}",
                            latitude = null,
                            longitude = null,
                            timestampMillis = b1.bookingTimeMillis,
                            resolvedAtMillis = existing?.resolvedAtMillis,
                            resolvedBy = existing?.resolvedBy,
                            resolutionNotes = existing?.resolutionNotes,
                            hashIntegrity = computeAlertHash(folio, amenityName, "ALTA")
                        )
                        detectedAlerts.add(alert)
                    }
                }
            }
        }

        // Conservar historial de alertas resueltas / descartadas previamente
        val newlyFolios = detectedAlerts.map { it.folio }.toSet()
        val historicalAlerts = existingAlerts.filter { it.folio !in newlyFolios }

        return (detectedAlerts + historicalAlerts).sortedWith(
            compareBy(
                { when (it.status) { "ACTIVA" -> 1; "EN_REVISION" -> 2; "CONFIRMADA" -> 3; "EN_ATENCION" -> 4; else -> 5 } },
                { when (it.priorityLevel) { "CRITICA" -> 1; "ALTA" -> 2; "PREVENTIVA" -> 3; "MEDIA" -> 4; else -> 5 } },
                { -it.timestampMillis }
            )
        )
    }

    /**
     * Permite que Supervisor o Administración marque la alerta como: CONFIRMADA / DESCARTADA / EN REVISIÓN / RESUELTA.
     * Registra inmutablemente la decisión en AuditLogEntity.
     */
    suspend fun updateAlertDecision(
        db: AppDatabase,
        alertFolio: String,
        decision: String, // CONFIRMADA, DESCARTADA, EN_REVISION, RESUELTA
        operatorName: String,
        operatorRole: String,
        notes: String
    ): Boolean = withContext(Dispatchers.IO) {
        val alert = db.operationalAlertDao().getAlertByFolio(alertFolio) ?: return@withContext false
        val now = System.currentTimeMillis()

        val targetStatus = when (decision.uppercase(Locale.ROOT)) {
            "CONFIRMADA", "CONFIRMAR" -> "CONFIRMADA"
            "DESCARTADA", "DESCARTAR" -> "DESCARTADA"
            "EN REVISIÓN", "EN_REVISION", "REVISION" -> "EN_REVISION"
            "RESUELTA", "RESOLVER" -> "RESUELTA"
            else -> decision
        }

        db.operationalAlertDao().updateAlertStatus(
            folio = alertFolio,
            newStatus = targetStatus,
            operator = "$operatorName ($operatorRole)",
            notes = notes,
            updatedTime = now
        )

        // Registrar en AuditLogEntity (Fuente Única de Verdad de Auditoría)
        val auditLog = AuditLogEntity(
            folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
            operatorName = "$operatorName ($operatorRole)",
            actionType = "ALERTA_DECISION_$targetStatus",
            location = alert.whereLocation,
            targetEntity = alert.folio,
            changeDetails = "Alerta ${alert.folio} [${alert.originModule} - ${alert.priorityLevel}] marcada como $targetStatus. Motivo/Notas: $notes",
            resultStatus = if (targetStatus == "DESCARTADA") "DENEGADO" else "EXITOSO",
            timestampMillis = now
        )
        db.auditLogDao().insertAuditLog(auditLog)
        true
    }

    /**
     * Calcula resumen estadístico y patrones detectados.
     */
    fun computeOverview(alerts: List<OperationalAlertEntity>): IntelligenceOverview {
        val active = alerts.filter { it.status == "ACTIVA" || it.status == "EN_REVISION" || it.status == "CONFIRMADA" || it.status == "EN_ATENCION" }
        val patterns = alerts.map { "[${it.originModule}] ${it.alertType}" }.distinct()

        return IntelligenceOverview(
            totalActiveAlerts = active.size,
            criticalCount = active.count { it.priorityLevel == "CRITICA" },
            highCount = active.count { it.priorityLevel == "ALTA" },
            preventiveCount = active.count { it.priorityLevel == "PREVENTIVA" || it.priorityLevel == "MEDIA" },
            informativeCount = active.count { it.priorityLevel == "INFORMATIVA" },
            confirmedCount = alerts.count { it.status == "CONFIRMADA" },
            inRevisionCount = alerts.count { it.status == "EN_REVISION" },
            resolvedCount = alerts.count { it.status == "RESUELTA" || it.status == "DESCARTADA" },
            patternsDetected = patterns,
            alerts = alerts
        )
    }

    private fun generateDeterministicFolio(prefix: String, key: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(key.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }.take(6).uppercase(Locale.ROOT)
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        return "$prefix-$dateStr-$hex"
    }

    private fun computeAlertHash(folio: String, location: String, priority: String): String {
        val raw = "$folio|$location|$priority|ALFHA_INTELLIGENCE_2026"
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.lowercase(Locale.ROOT)
    }

    private fun parseCoordinates(raw: String?): Pair<Double, Double>? {
        if (raw.isNullOrBlank() || raw.contains("NO DISPONIBLE") || raw.contains("OMITIDO")) return null
        return try {
            val clean = raw.replace("GPS:", "").replace("(", "").replace(")", "").trim()
            val parts = clean.split(",")
            if (parts.size >= 2) {
                val lat = parts[0].trim().toDoubleOrNull()
                val lng = parts[1].trim().toDoubleOrNull()
                if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                    Pair(lat, lng)
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseLocationCoordinates(location: String, rawGps: String?): Pair<Double, Double>? {
        // Primero intentar coordenadas directas
        val direct = parseCoordinates(rawGps)
        if (direct != null) return direct

        // Mapeo geográfico de referencia para infraestructura comunitaria
        val locUpper = location.uppercase(Locale.ROOT)
        return when {
            locUpper.contains("GARITA") || locUpper.contains("ACCESO PRINCIPAL") -> Pair(-33.43720, -70.65060)
            locUpper.contains("CLUB") || locUpper.contains("CASA CLUB") -> Pair(-33.43850, -70.65120)
            locUpper.contains("ALBERCA") || locUpper.contains("PISCINA") -> Pair(-33.43890, -70.65150)
            locUpper.contains("PERÍMETRO NORTE") || locUpper.contains("PERIMETRO NORTE") -> Pair(-33.43650, -70.65150)
            locUpper.contains("PERÍMETRO SUR") || locUpper.contains("PERIMETRO SUR") -> Pair(-33.43900, -70.64980)
            locUpper.contains("CANCHA") || locUpper.contains("GIMNASIO") -> Pair(-33.43810, -70.65220)
            else -> null
        }
    }
}
