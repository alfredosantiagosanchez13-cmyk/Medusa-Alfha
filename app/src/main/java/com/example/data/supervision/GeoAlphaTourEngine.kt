package com.example.data.supervision

import android.content.Context
import com.example.data.alerts.OperationalAlertEntity
import com.example.data.audit.AuditLogEntity
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.ui.components.CondoTarget
import com.example.data.incident.GpsCoordinates
import com.example.data.incident.IncidentEntity
import com.example.data.notifications.SmartNotificationHub
import com.example.utils.ResidentNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * RECORRIDO VIRTUAL GEO-ALPHA · MOTOR DE SUPERVISIÓN INTELIGENTE
 *
 * Principio Rector: "EL SISTEMA REGISTRA AL GUARDIA, NO EL GUARDIA AL SISTEMA."
 * TIEMPO = FAMILIA (Cero botones manuales obligatorios, Cero QR físico en postes).
 *
 * Características Mandatarias:
 * 1. Detección automática por GPS/Geocercas de cada geopunto autorizado.
 * 2. Recorrido flexible: el guardia puede iniciar desde cualquier punto, recorrer en cualquier orden y sentido.
 * 3. Registro estricto de: Hora, Ubicación satelital, Estado de cobertura.
 * 4. Trazabilidad de la Ruta Realmente Realizada (secuencia cronológica de pasos).
 * 5. Determinación de Geopuntos Cubiertos vs Geopuntos No Cubiertos.
 * 6. Condición estricta: Todos los puntos obligatorios deben quedar cubiertos.
 *    Si hay pendientes, no se considera completa salvo autorización de supervisor.
 * 7. Folio único transversal: MED-YYYYMMDD-XXXX.
 * 8. Persistencia 100% en Room SQLite como Fuente Única de Verdad.
 */

data class GeoAlphaPoint(
    val id: String,
    val name: String,
    val area: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float = 45.0f,
    val isMandatory: Boolean = true,
    val description: String = ""
)

data class GeoAlphaDetection(
    val pointId: String,
    val pointName: String,
    val area: String,
    val sequenceOrder: Int, // 1, 2, 3... según orden real de paso
    val detectedAtMillis: Long,
    val formattedTime: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Float,
    val isAutomatic: Boolean = true,
    val findings: String = "Paso verificado automáticamente por geocerca satelital Geo-Alpha",
    val evidencePhotoPath: String? = null
)

data class ActiveGeoAlphaTourState(
    val tourFolio: String,
    val condo: CondoTarget,
    val guardName: String,
    val startedAtMillis: Long,
    val isRunning: Boolean = true,
    val allAuthorizedPoints: List<GeoAlphaPoint>,
    val coveredDetections: List<GeoAlphaDetection> = emptyList(),
    val linkedIncidents: List<IncidentEntity> = emptyList(),
    val isSupervisorAuthorizedOverride: Boolean = false,
    val supervisorOverrideNotes: String? = null
) {
    val coveredPointIds: Set<String>
        get() = coveredDetections.map { it.pointId }.toSet()

    val pendingPoints: List<GeoAlphaPoint>
        get() = allAuthorizedPoints.filter { !coveredPointIds.contains(it.id) }

    val coveragePercentage: Int
        get() = if (allAuthorizedPoints.isNotEmpty()) {
            (coveredPointIds.size * 100) / allAuthorizedPoints.size
        } else 100

    val isComplete: Boolean
        get() = pendingPoints.none { it.isMandatory } || isSupervisorAuthorizedOverride

    val durationMinutes: Int
        get() = ((System.currentTimeMillis() - startedAtMillis) / (60 * 1000)).toInt().coerceAtLeast(0)
}

data class GeoAlphaTourReport(
    val tourFolio: String,
    val condo: CondoTarget,
    val guardName: String,
    val startMillis: Long,
    val endMillis: Long,
    val durationFormatted: String,
    val totalCheckpoints: Int,
    val coveredCount: Int,
    val omittedCount: Int,
    val coveragePercentage: Int,
    val isComplete: Boolean,
    val actualRouteTraversed: List<GeoAlphaDetection>,
    val omittedPoints: List<GeoAlphaPoint>,
    val linkedIncidents: List<IncidentEntity>,
    val integrityHashSha256: String,
    val executiveSummary: String
)

object GeoAlphaTourEngine {

    // Coordenadas base de Los Prados Residencial (Querétaro, Qro.)
    const val BASE_LAT = 20.643658
    const val BASE_LNG = -100.492812

    // =========================================================================
    // CATÁLOGOS DE GEOPUNTOS AUTORIZADOS POR CONDOMINIO
    // =========================================================================
    val POINTS_PARAISO = listOf(
        GeoAlphaPoint(
            id = "GEO-PAR-01",
            name = "Garita de Acceso Principal Paraíso",
            area = "Acceso Principal",
            latitude = 20.643658,
            longitude = -100.492812,
            radiusMeters = 40f,
            isMandatory = true,
            description = "Punto de control de entrada vehicular y peatonal"
        ),
        GeoAlphaPoint(
            id = "GEO-PAR-02",
            name = "Alberca & Asoleadero",
            area = "Amenidades",
            latitude = 20.643880,
            longitude = -100.492310,
            radiusMeters = 35f,
            isMandatory = true,
            description = "Perímetro de seguridad de piscina y cuarto de bombas"
        ),
        GeoAlphaPoint(
            id = "GEO-PAR-03",
            name = "Jardín Central & Juegos Infantiles",
            area = "Áreas Verdes",
            latitude = 20.644120,
            longitude = -100.492150,
            radiusMeters = 40f,
            isMandatory = true,
            description = "Área de juegos y luminarias del parque"
        ),
        GeoAlphaPoint(
            id = "GEO-PAR-04",
            name = "Bodega de Mantenimiento & Servicios",
            area = "Servicios Generales",
            latitude = 20.644260,
            longitude = -100.492520,
            radiusMeters = 35f,
            isMandatory = true,
            description = "Tableros de control y almacén de insumos"
        ),
        GeoAlphaPoint(
            id = "GEO-PAR-05",
            name = "Perímetro Norte - Cerco Eléctrico",
            area = "Perímetro",
            latitude = 20.644510,
            longitude = -100.492020,
            radiusMeters = 45f,
            isMandatory = true,
            description = "Muro colindante y energizador de cerco perimetral"
        ),
        GeoAlphaPoint(
            id = "GEO-PAR-06",
            name = "Estacionamiento de Visitas",
            area = "Vialidad Interna",
            latitude = 20.643510,
            longitude = -100.492580,
            radiusMeters = 40f,
            isMandatory = true,
            description = "Cajones de visita y portón automatizado"
        )
    )

    val POINTS_PRADOS_1 = listOf(
        GeoAlphaPoint(
            id = "GEO-PR1-01",
            name = "Caseta Principal Prados 1",
            area = "Acceso Principal",
            latitude = 20.643658,
            longitude = -100.492812,
            radiusMeters = 40f,
            isMandatory = true,
            description = "Garita de acceso Los Prados 1"
        ),
        GeoAlphaPoint(
            id = "GEO-PR1-02",
            name = "Calle 1 (Inicio · Casas 1 a 25)",
            area = "Calle 1 (Bali 2R)",
            latitude = 20.644210,
            longitude = -100.493120,
            radiusMeters = 40f,
            isMandatory = true,
            description = "Primer tramo residencial Calle 1"
        ),
        GeoAlphaPoint(
            id = "GEO-PR1-03",
            name = "Calle 1 (Fondo · Casas 26 a 49)",
            area = "Calle 1 (Bali 2R)",
            latitude = 20.644720,
            longitude = -100.493450,
            radiusMeters = 40f,
            isMandatory = true,
            description = "Retorno y fondo Calle 1"
        ),
        GeoAlphaPoint(
            id = "GEO-PR1-04",
            name = "Calle 2 (Tramo Bali 2R)",
            area = "Calle 2",
            latitude = 20.644150,
            longitude = -100.493620,
            radiusMeters = 40f,
            isMandatory = true,
            description = "Primer segmento Calle 2"
        ),
        GeoAlphaPoint(
            id = "GEO-PR1-05",
            name = "Calle 2 (Tramo Bali 3R · Fondo)",
            area = "Calle 2",
            latitude = 20.644630,
            longitude = -100.493920,
            radiusMeters = 40f,
            isMandatory = true,
            description = "Sector casas Bali 3R y retorno"
        ),
        GeoAlphaPoint(
            id = "GEO-PR1-06",
            name = "Transformador & Perímetro Posterior",
            area = "Perímetro e Infraestructura",
            latitude = 20.644910,
            longitude = -100.493230,
            radiusMeters = 40f,
            isMandatory = true,
            description = "Subestación eléctrica y barda perimetral"
        )
    )

    val POINTS_PRADOS_2 = listOf(
        GeoAlphaPoint(
            id = "GEO-PR2-01",
            name = "Caseta Principal Prados 2",
            area = "Acceso Principal",
            latitude = 20.643658,
            longitude = -100.492812,
            radiusMeters = 40f,
            isMandatory = true,
            description = "Garita de acceso Los Prados 2"
        ),
        GeoAlphaPoint(
            id = "GEO-PR2-02",
            name = "Calle 3 (Sector Norte)",
            area = "Calle 3 (Bali 2R)",
            latitude = 20.643320,
            longitude = -100.493520,
            radiusMeters = 40f,
            isMandatory = true,
            description = "Tramo residencial Calle 3"
        ),
        GeoAlphaPoint(
            id = "GEO-PR2-03",
            name = "Calle 4 (Sector Sur)",
            area = "Calle 4 (Bali 2R/3R)",
            latitude = 20.642910,
            longitude = -100.493820,
            radiusMeters = 40f,
            isMandatory = true,
            description = "Vialidad interna Calle 4"
        ),
        GeoAlphaPoint(
            id = "GEO-PR2-04",
            name = "Área de Amenidades & Palapa",
            area = "Amenidades Comunitarias",
            latitude = 20.643120,
            longitude = -100.493120,
            radiusMeters = 35f,
            isMandatory = true,
            description = "Palapa de convivencia y sanitarios"
        ),
        GeoAlphaPoint(
            id = "GEO-PR2-05",
            name = "Malla Ciclónica Perimetral",
            area = "Perímetro Poniente",
            latitude = 20.642820,
            longitude = -100.494210,
            radiusMeters = 45f,
            isMandatory = true,
            description = "Límite exterior y sensores de movimiento"
        ),
        GeoAlphaPoint(
            id = "GEO-PR2-06",
            name = "Portón de Servicios",
            area = "Acceso Servicios",
            latitude = 20.643520,
            longitude = -100.494020,
            radiusMeters = 40f,
            isMandatory = true,
            description = "Portón secundario de proveedores"
        )
    )

    val POINTS_PRADOS_3 = listOf(
        GeoAlphaPoint(
            id = "GEO-PR3-01",
            name = "Caseta Principal Prados 3",
            area = "Acceso Principal",
            latitude = 20.643658,
            longitude = -100.492812,
            radiusMeters = 40f,
            isMandatory = true,
            description = "Garita de acceso Los Prados 3"
        ),
        GeoAlphaPoint(
            id = "GEO-PR3-02",
            name = "Calle 5 (Topacio 3R · Poniente)",
            area = "Calle 5",
            latitude = 20.642510,
            longitude = -100.492920,
            radiusMeters = 40f,
            isMandatory = true,
            description = "Tramo de 45 casas modelo Topacio 3R"
        ),
        GeoAlphaPoint(
            id = "GEO-PR3-03",
            name = "Calle 6 (Topacio 3R · Oriente)",
            area = "Calle 6",
            latitude = 20.642230,
            longitude = -100.492620,
            radiusMeters = 40f,
            isMandatory = true,
            description = "Tramo de 31 casas modelo Topacio 3R"
        ),
        GeoAlphaPoint(
            id = "GEO-PR3-04",
            name = "Área Verde Central",
            area = "Parque Comunitario",
            latitude = 20.642420,
            longitude = -100.492410,
            radiusMeters = 40f,
            isMandatory = true,
            description = "Andador peatonal y áreas de descanso"
        ),
        GeoAlphaPoint(
            id = "GEO-PR3-05",
            name = "Contenedores de Basura & Reciclaje",
            area = "Servicios Limpieza",
            latitude = 20.642110,
            longitude = -100.493020,
            radiusMeters = 35f,
            isMandatory = true,
            description = "Isla ecológica y control sanitario"
        ),
        GeoAlphaPoint(
            id = "GEO-PR3-06",
            name = "Límite de Barda Perimetral Sur",
            area = "Perímetro Sur",
            latitude = 20.641920,
            longitude = -100.492510,
            radiusMeters = 45f,
            isMandatory = true,
            description = "Muro sur colindante y luminarias solares"
        )
    )

    fun getPointsForCondo(condo: CondoTarget): List<GeoAlphaPoint> {
        return when (condo) {
            CondoTarget.PARAISO -> POINTS_PARAISO
            CondoTarget.PRADOS_1 -> POINTS_PRADOS_1
            CondoTarget.PRADOS_2 -> POINTS_PRADOS_2
            CondoTarget.PRADOS_3 -> POINTS_PRADOS_3
        }
    }

    /**
     * Retorna la lista consolidada de todos los geopuntos predefinidos en todos los condominios.
     */
    fun getAllPredefinedPoints(): List<GeoAlphaPoint> {
        return (POINTS_PARAISO + POINTS_PRADOS_1 + POINTS_PRADOS_2 + POINTS_PRADOS_3).distinctBy { it.id }
    }

    /**
     * Busca un geopunto predefinido por su identificador único (e.g. GEO-PAR-01).
     */
    fun findPointById(pointId: String): GeoAlphaPoint? {
        return getAllPredefinedPoints().find { it.id.equals(pointId.trim(), ignoreCase = true) }
    }

    /**
     * Calcula distancia geodésica mediante Haversine en metros.
     */
    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val earthRadius = 6371000.0 // metros
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadius * c).toFloat()
    }

    /**
     * Inicia una nueva ronda Geo-Alpha generando un folio único transversal MED-YYYYMMDD-XXXX.
     */
    fun startTour(condo: CondoTarget, guardName: String): ActiveGeoAlphaTourState {
        val tourFolio = AlphaCoreEngine.generateUniqueFolio("MED")
        val points = getPointsForCondo(condo)
        return ActiveGeoAlphaTourState(
            tourFolio = tourFolio,
            condo = condo,
            guardName = guardName.ifBlank { "Guardia en Turno (${condo.shortTag})" },
            startedAtMillis = System.currentTimeMillis(),
            isRunning = true,
            allAuthorizedPoints = points,
            coveredDetections = emptyList(),
            linkedIncidents = emptyList()
        )
    }

    /**
     * Procesa una actualización de coordenadas GPS detectada en vivo.
     * Si el guardia entra en el radio de CUALQUIER geopunto autorizado no cubierto aún,
     * MEDUSA lo detecta automáticamente, registrando hora, fix satelital y orden real de recorrido.
     */
    fun evaluateGpsPosition(
        currentState: ActiveGeoAlphaTourState,
        lat: Double,
        lng: Double
    ): Pair<ActiveGeoAlphaTourState, GeoAlphaDetection?> {
        if (!currentState.isRunning) return Pair(currentState, null)

        val alreadyCoveredIds = currentState.coveredPointIds
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        for (point in currentState.allAuthorizedPoints) {
            if (alreadyCoveredIds.contains(point.id)) continue

            val distance = calculateDistanceMeters(lat, lng, point.latitude, point.longitude)
            if (distance <= point.radiusMeters) {
                val now = System.currentTimeMillis()
                val detection = GeoAlphaDetection(
                    pointId = point.id,
                    pointName = point.name,
                    area = point.area,
                    sequenceOrder = currentState.coveredDetections.size + 1,
                    detectedAtMillis = now,
                    formattedTime = timeFormat.format(Date(now)),
                    latitude = lat,
                    longitude = lng,
                    distanceMeters = distance,
                    isAutomatic = true,
                    findings = "Paso detectado automáticamente por geocerca Geo-Alpha (${String.format(Locale.US, "%.1fm", distance)})"
                )

                val updatedList = currentState.coveredDetections + detection
                val updatedState = currentState.copy(coveredDetections = updatedList)
                return Pair(updatedState, detection)
            }
        }

        return Pair(currentState, null)
    }

    /**
     * Simulación de paso por geopunto para pruebas en emulador/contenedor.
     * Permite validar la detección automática sin requerir desplazamiento físico real.
     */
    fun simulatePassByPoint(
        currentState: ActiveGeoAlphaTourState,
        pointId: String,
        findings: String? = null,
        evidencePhotoPath: String? = null
    ): Pair<ActiveGeoAlphaTourState, GeoAlphaDetection?> {
        if (!currentState.isRunning) return Pair(currentState, null)

        val point = currentState.allAuthorizedPoints.find { it.id == pointId } ?: return Pair(currentState, null)
        if (currentState.coveredPointIds.contains(point.id)) return Pair(currentState, null)

        val now = System.currentTimeMillis()
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        val detection = GeoAlphaDetection(
            pointId = point.id,
            pointName = point.name,
            area = point.area,
            sequenceOrder = currentState.coveredDetections.size + 1,
            detectedAtMillis = now,
            formattedTime = timeFormat.format(Date(now)),
            latitude = point.latitude,
            longitude = point.longitude,
            distanceMeters = 3.5f,
            isAutomatic = true,
            findings = findings?.ifBlank { null }
                ?: "Paso verificado automáticamente por geocerca Geo-Alpha (Simulación Táctica)",
            evidencePhotoPath = evidencePhotoPath
        )

        val updatedList = currentState.coveredDetections + detection
        val updatedState = currentState.copy(coveredDetections = updatedList)
        return Pair(updatedState, detection)
    }

    /**
     * Vincula un incidente detectado durante la ronda al estado activo.
     */
    fun linkIncident(currentState: ActiveGeoAlphaTourState, incident: IncidentEntity): ActiveGeoAlphaTourState {
        return currentState.copy(linkedIncidents = currentState.linkedIncidents + incident)
    }

    /**
     * Autorización de supervisor para cierre anticipado con puntos pendientes.
     */
    fun authorizeSupervisorOverride(
        currentState: ActiveGeoAlphaTourState,
        supervisorNotes: String
    ): ActiveGeoAlphaTourState {
        return currentState.copy(
            isSupervisorAuthorizedOverride = true,
            supervisorOverrideNotes = supervisorNotes.ifBlank { "Cierre anticipado autorizado por Supervisor de Turno." }
        )
    }

    /**
     * Finaliza la ronda Geo-Alpha, persiste inmediatamente en Room (SupervisionAuditDao, AuditLogDao),
     * genera el informe ejecutivo inmutable con firma criptográfica SHA-256 y dispara notificaciones.
     */
    suspend fun finalizeTour(
        context: Context,
        db: AppDatabase,
        state: ActiveGeoAlphaTourState
    ): GeoAlphaTourReport = withContext(Dispatchers.IO) {
        val endMillis = System.currentTimeMillis()
        val durationMinutes = ((endMillis - state.startedAtMillis) / (60 * 1000)).toInt().coerceAtLeast(1)
        val durationFormatted = AlphaCoreEngine.calculateDurationFormatted(state.startedAtMillis, endMillis)
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        val auditDao = db.supervisionAuditDao()
        val auditLogDao = db.auditLogDao()

        // 1. Guardar cada geopunto cubierto como registro de auditoría individual
        state.coveredDetections.forEach { det ->
            val pointFolio = "${state.tourFolio}-${det.pointId}"
            val auditEntity = SupervisionAuditEntity(
                folio = pointFolio,
                supervisorName = state.guardName,
                checkpointName = det.pointName,
                areaName = "${state.condo.displayName} · ${det.area}",
                statusCondition = "OPTIMO",
                findingsDescription = "Paso #${det.sequenceOrder} a las ${det.formattedTime}. ${det.findings}",
                riskLevel = "BAJO",
                correctiveActionRequired = "Mantener protocolo rutinario",
                responsibleParty = state.guardName,
                commitmentDate = dateFormat.format(Date(det.detectedAtMillis)),
                gpsCoordinates = String.format(Locale.US, "%.5f, %.5f [Geo-Alpha ±%.0fm]", det.latitude, det.longitude, det.distanceMeters),
                photoEvidencePath = det.evidencePhotoPath,
                durationMinutes = 2,
                timestampMillis = det.detectedAtMillis,
                isClosed = true
            )
            auditDao.insertAudit(auditEntity)
        }

        // 2. Guardar geopuntos omitidos si la ronda fue cerrada con pendientes
        val omittedPoints = state.pendingPoints
        omittedPoints.forEach { pt ->
            val pointFolio = "${state.tourFolio}-${pt.id}"
            val auditEntity = SupervisionAuditEntity(
                folio = pointFolio,
                supervisorName = state.guardName,
                checkpointName = pt.name,
                areaName = "${state.condo.displayName} · ${pt.area}",
                statusCondition = "OMITIDO",
                findingsDescription = "Geopunto no recorrido durante la ronda. " +
                        (state.supervisorOverrideNotes?.let { "Autorización Supervisor: $it" } ?: "Alerta de cobertura incompleta."),
                riskLevel = "ALTO",
                correctiveActionRequired = "Programar ronda de verificación complementaria",
                responsibleParty = state.guardName,
                commitmentDate = dateFormat.format(Date(endMillis)),
                gpsCoordinates = "NO_DETECTADO",
                durationMinutes = 0,
                timestampMillis = endMillis,
                isClosed = true
            )
            auditDao.insertAudit(auditEntity)
        }

        // 3. Generar Resumen de la Ruta Realmente Realizada
        val traversedSummary = if (state.coveredDetections.isNotEmpty()) {
            state.coveredDetections.joinToString(" ➔ ") { "${it.sequenceOrder}. ${it.pointName} (${it.formattedTime})" }
        } else {
            "Sin puntos recorridos"
        }

        // 4. Calcular Hash SHA-256 de Integridad
        val hashPayload = "${state.tourFolio}|${state.condo.name}|${state.guardName}|${state.startedAtMillis}|$endMillis|${state.coveredDetections.size}|${omittedPoints.size}|MEDUSA_GEO_ALPHA_2026"
        val hashBytes = MessageDigest.getInstance("SHA-256").digest(hashPayload.toByteArray(Charsets.UTF_8))
        val sha256 = hashBytes.joinToString("") { "%02x".format(it) }.lowercase(Locale.US)

        val isComplete = state.isComplete
        val conditionLabel = if (isComplete && omittedPoints.isEmpty()) "OPTIMO" else if (isComplete) "REGULAR" else "CRITICO"

        val executiveSummary = buildString {
            append("Ronda Geo-Alpha en ${state.condo.displayName}. ")
            append("Cobertura: ${state.coveragePercentage}% (${state.coveredDetections.size}/${state.allAuthorizedPoints.size} puntos). ")
            append("Duración: $durationFormatted. ")
            if (omittedPoints.isNotEmpty()) {
                append("Puntos omitidos: ${omittedPoints.size}. ")
                if (state.isSupervisorAuthorizedOverride) {
                    append("Cierre autorizado por supervisor: '${state.supervisorOverrideNotes}'. ")
                }
            }
            if (state.linkedIncidents.isNotEmpty()) {
                append("Incidencias vinculadas: ${state.linkedIncidents.size}. ")
            }
            append("Hash SHA-256: ${sha256.take(16)}...")
        }

        // 5. Insertar Registro Maestro de Cierre en Room
        val masterAudit = SupervisionAuditEntity(
            folio = state.tourFolio,
            supervisorName = state.guardName,
            checkpointName = "Ronda Geo-Alpha [${state.condo.displayName}]",
            areaName = state.condo.displayName,
            statusCondition = conditionLabel,
            findingsDescription = "Ruta realmente realizada: $traversedSummary. $executiveSummary",
            riskLevel = if (isComplete && omittedPoints.isEmpty()) "BAJO" else if (isComplete) "MEDIO" else "ALTO",
            correctiveActionRequired = if (omittedPoints.isEmpty()) "Operación en orden." else "Revisar puntos omitidos.",
            responsibleParty = state.guardName,
            commitmentDate = dateFormat.format(Date(endMillis)),
            gpsCoordinates = "Av. de la Cantera 2750 [Querétaro]",
            durationMinutes = durationMinutes,
            timestampMillis = endMillis,
            isClosed = true
        )
        auditDao.insertAudit(masterAudit)

        // 6. Registrar en Bitácora Inmutable
        auditLogDao.insertAuditLog(
            AuditLogEntity(
                operatorName = state.guardName,
                actionType = "GEO_ALPHA_TOUR_FINALIZED",
                location = state.condo.displayName,
                targetEntity = state.tourFolio,
                changeDetails = "Ronda completada al ${state.coveragePercentage}%. Duración $durationFormatted. Puntos cubiertos: ${state.coveredDetections.size}/${state.allAuthorizedPoints.size}. Hash: ${sha256.take(12)}"
            )
        )

        // 7. Notificaciones
        ResidentNotificationManager.notifySupervisionClosed(
            context = context,
            folio = state.tourFolio,
            supervisorName = state.guardName,
            checkpointsCount = state.coveredDetections.size,
            durationMins = durationMinutes
        )

        GeoAlphaTourReport(
            tourFolio = state.tourFolio,
            condo = state.condo,
            guardName = state.guardName,
            startMillis = state.startedAtMillis,
            endMillis = endMillis,
            durationFormatted = durationFormatted,
            totalCheckpoints = state.allAuthorizedPoints.size,
            coveredCount = state.coveredDetections.size,
            omittedCount = omittedPoints.size,
            coveragePercentage = state.coveragePercentage,
            isComplete = isComplete,
            actualRouteTraversed = state.coveredDetections,
            omittedPoints = omittedPoints,
            linkedIncidents = state.linkedIncidents,
            integrityHashSha256 = sha256,
            executiveSummary = executiveSummary
        )
    }

    /**
     * Genera la estructura inmutable del informe Geo-Alpha para validación programática o previsualización.
     */
    fun generateReportSnapshot(
        state: ActiveGeoAlphaTourState,
        endMillis: Long = System.currentTimeMillis()
    ): GeoAlphaTourReport {
        val durationFormatted = AlphaCoreEngine.calculateDurationFormatted(state.startedAtMillis, endMillis)
        val omittedPoints = state.pendingPoints
        val hashPayload = "${state.tourFolio}|${state.condo.name}|${state.guardName}|${state.startedAtMillis}|$endMillis|${state.coveredDetections.size}|${omittedPoints.size}|MEDUSA_GEO_ALPHA_2026"
        val hashBytes = MessageDigest.getInstance("SHA-256").digest(hashPayload.toByteArray(Charsets.UTF_8))
        val sha256 = hashBytes.joinToString("") { "%02x".format(it) }.lowercase(Locale.US)
        val isComplete = state.isComplete

        val executiveSummary = buildString {
            append("Ronda Geo-Alpha en ${state.condo.displayName}. ")
            append("Cobertura: ${state.coveragePercentage}% (${state.coveredDetections.size}/${state.allAuthorizedPoints.size} puntos). ")
            append("Duración: $durationFormatted. ")
            if (omittedPoints.isNotEmpty()) {
                append("Puntos omitidos: ${omittedPoints.size}. ")
                if (state.isSupervisorAuthorizedOverride) {
                    append("Cierre autorizado por supervisor: '${state.supervisorOverrideNotes}'. ")
                }
            }
            if (state.linkedIncidents.isNotEmpty()) {
                append("Incidencias vinculadas: ${state.linkedIncidents.size}. ")
            }
            append("Hash SHA-256: ${sha256.take(16)}...")
        }

        return GeoAlphaTourReport(
            tourFolio = state.tourFolio,
            condo = state.condo,
            guardName = state.guardName,
            startMillis = state.startedAtMillis,
            endMillis = endMillis,
            durationFormatted = durationFormatted,
            totalCheckpoints = state.allAuthorizedPoints.size,
            coveredCount = state.coveredDetections.size,
            omittedCount = omittedPoints.size,
            coveragePercentage = state.coveragePercentage,
            isComplete = isComplete,
            actualRouteTraversed = state.coveredDetections,
            omittedPoints = omittedPoints,
            linkedIncidents = state.linkedIncidents,
            integrityHashSha256 = sha256,
            executiveSummary = executiveSummary
        )
    }
}
