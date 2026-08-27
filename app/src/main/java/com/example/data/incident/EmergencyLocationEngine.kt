package com.example.data.incident

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.example.data.audit.AuditLogEntity
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.notifications.NotificationCategory
import com.example.data.notifications.NotificationPriority
import com.example.data.notifications.SmartNotificationHub
import com.example.auth.AlfhaRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * COMPLEMENTO A FASE 16: MOTOR DE GEOLOCALIZACIÓN Y MAPA OPERATIVO DE EMERGENCIAS ALFHA
 *
 * Principios operativos:
 * 1. Captura automática de coordenadas GPS en cada emergencia.
 * 2. Si no existe GPS, marcar estrictamente "UBICACIÓN NO DISPONIBLE" (sin inventar coordenadas).
 * 3. Conservación permanente de coordenadas, folio, evidencias e historial de cierres.
 * 4. Actualización en tiempo real de posición y sincronización entre Caseta, Supervisor y Panel Maestro.
 * 5. Registro de TIEMPO DEVUELTO (localización inmediata sin llamadas ni descripciones manuales).
 */
data class GpsCoordinates(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val timestampMillis: Long = System.currentTimeMillis(),
    val provider: String = "GPS"
)

object EmergencyLocationEngine {

    const val STATUS_GPS_CAPTURED = "GPS_CAPTURADO"
    const val STATUS_NO_GPS = "UBICACIÓN NO DISPONIBLE"
    const val STATUS_UPDATED = "ACTUALIZADO"

    /**
     * Intenta capturar las coordenadas GPS reales del dispositivo.
     * Retorna null si no hay permisos o no hay señal satelital/red disponible.
     * NUNCA inventa coordenadas si el proveedor no está activo.
     */
    fun captureCurrentGps(context: Context): GpsCoordinates? {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            return null
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        var bestLocation: Location? = null

        // 1. Intentar GPS Provider
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            try {
                val loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (loc != null) bestLocation = loc
            } catch (e: SecurityException) {
                // Ignore
            }
        }

        // 2. Intentar Network Provider como respaldo si no hay fix satelital
        if (bestLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            try {
                val loc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (loc != null) bestLocation = loc
            } catch (e: SecurityException) {
                // Ignore
            }
        }

        // 3. Intentar Passive Provider
        if (bestLocation == null && locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            try {
                val loc = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                if (loc != null) bestLocation = loc
            } catch (e: SecurityException) {
                // Ignore
            }
        }

        return bestLocation?.let {
            GpsCoordinates(
                latitude = it.latitude,
                longitude = it.longitude,
                accuracyMeters = if (it.hasAccuracy()) it.accuracy else null,
                timestampMillis = it.time.takeIf { t -> t > 0 } ?: System.currentTimeMillis(),
                provider = it.provider ?: "GPS"
            )
        }
    }

    /**
     * Dispara y registra una emergencia activa en el sistema con captura GPS automática.
     */
    suspend fun triggerEmergencyAlert(
        context: Context,
        db: AppDatabase,
        emergencyType: String, // "PÁNICO S.O.S.", "MÉDICA", "INCENDIO", "INTRUSIÓN", "SEGURIDAD"
        locationName: String, // "Casa 104", "Torre Norte Depto 302", "Área Piscina"
        reportedBy: String,
        reportedByRole: String,
        details: String,
        manualGps: GpsCoordinates? = null
    ): IncidentEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val folio = AlphaCoreEngine.generateUniqueFolio("EMG")

        // Captura automática de GPS (o coordenadas pasadas si se forzó lectura previa)
        val gps = manualGps ?: captureCurrentGps(context)
        val locationStatus = if (gps != null) STATUS_GPS_CAPTURED else STATUS_NO_GPS

        val timeSavedMinutes = 25 // 25 min ahorrados en geolocalización directa sin llamadas de ubicación

        val emergencyEntity = IncidentEntity(
            folio = folio,
            rawTranscript = "🚨 ALERTA DE EMERGENCIA: $emergencyType en $locationName. Detalle: $details",
            category = IncidentCategory.SEGURIDAD_EMERGENCIA,
            priority = IncidentPriority.CRITICA,
            location = locationName,
            aiSummary = "Emergencia activa ($emergencyType) reportada por $reportedBy. Coordenadas: ${if (gps != null) "${gps.latitude}, ${gps.longitude}" else "NO DISPONIBLE"}",
            recommendedAction = "Despachar de inmediato patrulla táctica a $locationName. Activar sirena perimetral y enlace 911.",
            timestampMillis = now,
            guardName = reportedBy,
            reportedBy = reportedBy,
            reportedByRole = reportedByRole,
            status = "REGISTRADO",
            assignedTo = "Supervisor Táctico & Cuadrilla de Garita",
            assignedRole = "SUPERVISOR",
            targetSlaMinutes = 15,
            isEscalated = true,
            escalatedAtMillis = now,
            escalationReason = "EMERGENCIA CRÍTICA INMEDIATA ($emergencyType)",
            evidenceNotes = "Evento de emergencia generado. Estatus GPS: $locationStatus",
            timeSavedMinutes = timeSavedMinutes,
            latitude = gps?.latitude,
            longitude = gps?.longitude,
            gpsAccuracyMeters = gps?.accuracyMeters,
            locationStatus = locationStatus,
            gpsTimestampMillis = gps?.timestampMillis,
            isEmergency = true
        )

        // 1. Guardar en Room SQLite
        db.incidentDao().insertIncident(emergencyEntity)

        // 2. Registro inmutable de Auditoría
        val gpsDetail = if (gps != null) {
            "Lat: ${gps.latitude}, Lon: ${gps.longitude} (Precisión: ±${gps.accuracyMeters ?: 0f}m)"
        } else {
            "UBICACIÓN NO DISPONIBLE (Sin señal satelital en dispositivo)"
        }

        db.auditLogDao().insertAuditLog(
            AuditLogEntity(
                folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                operatorName = "$reportedBy ($reportedByRole)",
                actionType = "EMERGENCIA_DISPARADA",
                location = locationName,
                targetEntity = folio,
                changeDetails = "Emergencia $folio [$emergencyType] activada. GPS: $gpsDetail. Estatus: $locationStatus",
                resultStatus = "CRITICO",
                timestampMillis = now
            )
        )

        // 3. Registro de Tiempo Devuelto
        db.auditLogDao().insertAuditLog(
            AuditLogEntity(
                folio = AlphaCoreEngine.generateUniqueFolio("TME"),
                operatorName = "ALFHA_GEOLOCATION_ENGINE",
                actionType = "TIEMPO_DEVUELTO",
                location = locationName,
                targetEntity = folio,
                changeDetails = "Ahorro de $timeSavedMinutes minutos devueltos al localizar la emergencia sin llamadas ni descripción manual de la ubicación.",
                resultStatus = "EXITOSO",
                timestampMillis = now
            )
        )

        // 4. Encolar en Cola de Sincronización Persistente (FASE 19: Offline Robusto)
        try {
            com.example.data.sync.OfflineSyncEngine.enqueueOperation(
                db = db,
                operationType = "EMERGENCY_TRIGGER",
                targetFolio = folio,
                targetModule = "EMERGENCIAS",
                payloadJson = "{\"folio\":\"$folio\",\"type\":\"$emergencyType\",\"location\":\"$locationName\",\"reportedBy\":\"$reportedBy\",\"gpsStatus\":\"$locationStatus\"}",
                operatorName = reportedBy,
                operatorRole = reportedByRole,
                locationName = locationName,
                latitude = gps?.latitude,
                longitude = gps?.longitude,
                evidencePaths = emergencyEntity.evidenceNotes,
                deviceGateId = "Dispositivo Operador ($reportedBy)"
            )
        } catch (e: Exception) {
            android.util.Log.e("EmergencyLocationEngine", "Error enqueuing emergency sync: ${e.message}")
        }

        // 5. Notificaciones push inteligentes críticas
        try {
            SmartNotificationHub.notifyGuardCriticalAlert(
                context = context,
                db = db,
                alertFolio = folio,
                location = locationName,
                description = "$emergencyType. $gpsDetail.",
                actionRequired = "Despachar ronda o acudir a la ubicación señalada en el mapa"
            )

            SmartNotificationHub.notifySupervisorCriticalAlert(
                context = context,
                db = db,
                alertFolio = folio,
                location = locationName,
                finding = "$emergencyType. $gpsDetail."
            )

            SmartNotificationHub.notifyAdminCriticalEvent(
                context = context,
                db = db,
                eventFolio = folio,
                eventType = emergencyType,
                details = "$details. $gpsDetail"
            )

            SmartNotificationHub.notifyBoardHighPriorityEvent(
                context = context,
                db = db,
                folio = folio,
                titleSummary = "🚨 Emergencia en $locationName: $emergencyType",
                executiveDetail = "$details ($gpsDetail)",
                isCritical = true
            )
        } catch (e: Exception) {
            android.util.Log.e("EmergencyLocationEngine", "Error sending emergency notifications: ${e.message}")
        }

        emergencyEntity
    }

    /**
     * Actualiza la posición GPS de una emergencia existente cuando corresponde.
     */
    suspend fun updateEmergencyLocation(
        context: Context,
        db: AppDatabase,
        folio: String,
        operatorName: String
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val incident = db.incidentDao().getIncidentByFolio(folio)
            ?: return@withContext Pair(false, "Emergencia no encontrada")

        val newGps = captureCurrentGps(context)
        val now = System.currentTimeMillis()

        if (newGps != null) {
            db.incidentDao().updateIncidentCoordinates(
                folio = folio,
                lat = newGps.latitude,
                lon = newGps.longitude,
                accuracy = newGps.accuracyMeters,
                locationStatus = STATUS_UPDATED,
                gpsTimestamp = now
            )

            db.incidentDao().appendEvidenceNotes(
                folio,
                "[$operatorName - ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now))}]: Coordenadas GPS actualizadas a Lat: ${newGps.latitude}, Lon: ${newGps.longitude} (±${newGps.accuracyMeters ?: 0f}m)"
            )

            db.auditLogDao().insertAuditLog(
                AuditLogEntity(
                    folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                    operatorName = operatorName,
                    actionType = "EMERGENCIA_GPS_ACTUALIZADO",
                    location = incident.location,
                    targetEntity = folio,
                    changeDetails = "Posición GPS actualizada por $operatorName: Lat ${newGps.latitude}, Lon ${newGps.longitude}",
                    resultStatus = "EXITOSO",
                    timestampMillis = now
                )
            )

            Pair(true, "Coordenadas GPS actualizadas: Lat ${newGps.latitude}, Lon ${newGps.longitude} (±${newGps.accuracyMeters?.toInt() ?: 0}m)")
        } else {
            // Si no se pudo obtener señal satelital
            db.incidentDao().updateIncidentCoordinates(
                folio = folio,
                lat = incident.latitude,
                lon = incident.longitude,
                accuracy = incident.gpsAccuracyMeters,
                locationStatus = if (incident.latitude != null) STATUS_GPS_CAPTURED else STATUS_NO_GPS,
                gpsTimestamp = now
            )

            db.auditLogDao().insertAuditLog(
                AuditLogEntity(
                    folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                    operatorName = operatorName,
                    actionType = "EMERGENCIA_GPS_INTENTO",
                    location = incident.location,
                    targetEntity = folio,
                    changeDetails = "Intento de actualización GPS por $operatorName: UBICACIÓN NO DISPONIBLE (Sin señal satelital)",
                    resultStatus = "ALERTA",
                    timestampMillis = now
                )
            )

            Pair(false, "UBICACIÓN NO DISPONIBLE: No se detectó señal satelital ni fix de red.")
        }
    }

    /**
     * Cierra la emergencia conservando las coordenadas y el punto histórico permanentemente.
     */
    suspend fun resolveAndCloseEmergency(
        context: Context,
        db: AppDatabase,
        folio: String,
        resolutionNotes: String,
        operatorName: String,
        operatorRole: String
    ): Boolean = withContext(Dispatchers.IO) {
        val incident = db.incidentDao().getIncidentByFolio(folio) ?: return@withContext false
        val now = System.currentTimeMillis()

        // Transición a RESUELTO y CERRADO conservando coordenadas en el registro
        db.incidentDao().transitionToResolved(folio, resolutionNotes, operatorName, now)
        db.incidentDao().transitionToClosed(folio, "Emergencia cerrada con éxito. Punto geográfico preservado en historial.", operatorName, now)

        db.auditLogDao().insertAuditLog(
            AuditLogEntity(
                folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                operatorName = "$operatorName ($operatorRole)",
                actionType = "EMERGENCIA_CERRADA",
                location = incident.location,
                targetEntity = folio,
                changeDetails = "Emergencia $folio cerrada. Coordenadas conservadas: ${incident.latitude ?: "N/D"}, ${incident.longitude ?: "N/D"}. Dictamen: $resolutionNotes",
                resultStatus = "EXITOSO",
                timestampMillis = now
            )
        )

        true
    }

    /**
     * Sembrado de emergencias demostrativas iniciales si no hay registros en la base de datos
     */
    suspend fun seedSampleEmergenciesIfEmpty(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        val emergencyCount = db.incidentDao().getIncidentCount()
        if (emergencyCount == 0) {
            val now = System.currentTimeMillis()

            // 1. Emergencia Activa con GPS Capturado
            val emg1 = IncidentEntity(
                folio = "EMG-20260825-1044",
                rawTranscript = "🚨 Pánico Residencial activado desde Casa 104. Solicitud de asistencia médica inmediata.",
                category = IncidentCategory.SEGURIDAD_EMERGENCIA,
                priority = IncidentPriority.CRITICA,
                location = "Casa 104",
                aiSummary = "Alerta de pánico médica activada por residente. Cuadrilla y paramédicos despachados.",
                recommendedAction = "Verificar estado del residente y coordinar ingreso de ambulancia en Garita 1.",
                timestampMillis = now - (5 * 60 * 1000), // Hace 5 minutos
                guardName = "Oficial Mario Silva (Garita 1)",
                reportedBy = "Fam. González",
                reportedByRole = "RESIDENTE",
                status = "REGISTRADO",
                assignedTo = "Supervisor Táctico & Cuadrilla Alfa",
                assignedRole = "SUPERVISOR",
                targetSlaMinutes = 15,
                isEscalated = true,
                escalatedAtMillis = now - (5 * 60 * 1000),
                escalationReason = "PÁNICO MÉDICO ACTIVO",
                evidenceNotes = "Disparo recibido por consola táctica. Sirena de cuadrilla encendida.",
                timeSavedMinutes = 25,
                latitude = 19.432608,
                longitude = -99.133209,
                gpsAccuracyMeters = 4.2f,
                locationStatus = STATUS_GPS_CAPTURED,
                gpsTimestampMillis = now - (5 * 60 * 1000),
                isEmergency = true
            )

            // 2. Emergencia Activa en Atención SIN GPS (Ubicación No Disponible)
            val emg2 = IncidentEntity(
                folio = "EMG-20260825-3021",
                rawTranscript = "🚨 Activación de sensor de humo y botón de emergencia en Torre Norte Depto 302.",
                category = IncidentCategory.SEGURIDAD_EMERGENCIA,
                priority = IncidentPriority.CRITICA,
                location = "Torre Norte Depto 302",
                aiSummary = "Posible conato de incendio o humo en departamento 302. Guardia verificando en sitio.",
                recommendedAction = "Oficial de ronda en ascenso por escaleras de Torre Norte. Verificar red de hidrantes.",
                timestampMillis = now - (12 * 60 * 1000), // Hace 12 minutos
                guardName = "Guardia de Caseta",
                reportedBy = "Valeria Rojas (Depto 302)",
                reportedByRole = "RESIDENTE",
                status = "EN_ATENCION",
                assignedTo = "Oficial de Ronda Perimetral",
                assignedRole = "GUARDIA",
                targetSlaMinutes = 15,
                attendedAtMillis = now - (10 * 60 * 1000),
                attendedBy = "Oficial Rodrigo Gómez",
                isEscalated = false,
                evidenceNotes = "Guardia confirma olor a humo en pasillo piso 3. Extintor PQS en mano.",
                timeSavedMinutes = 25,
                latitude = null,
                longitude = null,
                gpsAccuracyMeters = null,
                locationStatus = STATUS_NO_GPS,
                gpsTimestampMillis = null,
                isEmergency = true
            )

            // 3. Emergencia Histórica CERRADA con punto conservado
            val emg3 = IncidentEntity(
                folio = "EMG-20260824-0088",
                rawTranscript = "🚨 Alerta de seguridad perimetral: Intrusión detectada en cerca electrificada Zona Quincho BBQ.",
                category = IncidentCategory.SEGURIDAD_EMERGENCIA,
                priority = IncidentPriority.CRITICA,
                location = "Quincho BBQ (Perímetro Sur)",
                aiSummary = "Corte en línea perimetral sur. Verificado por patrulla táctica y descartado como falso contacto de rama.",
                recommendedAction = "Despejar follaje y reactivar cerco de seguridad.",
                timestampMillis = now - (24 * 60 * 60 * 1000), // Ayer
                guardName = "Supervisor Táctico",
                reportedBy = "Sistema Telemétrico ALFHA",
                reportedByRole = "SISTEMA",
                status = "CERRADO",
                assignedTo = "Supervisor Táctico",
                assignedRole = "SUPERVISOR",
                targetSlaMinutes = 15,
                attendedAtMillis = now - (24 * 60 * 60 * 1000) + 120000,
                attendedBy = "Supervisor Silva",
                resolvedAtMillis = now - (24 * 60 * 60 * 1000) + 900000,
                resolvedBy = "Supervisor Silva",
                closedAtMillis = now - (24 * 60 * 60 * 1000) + 1800000,
                closedBy = "Administración",
                closureNotes = "Cerca restablecida. Rama retirada por jardinería. Sin vulneración a residentes.",
                evidenceNotes = "Punto geográfico registrado y confirmado en coordenadas perimetrales.",
                timeSavedMinutes = 30,
                latitude = 19.431850,
                longitude = -99.132100,
                gpsAccuracyMeters = 3.5f,
                locationStatus = STATUS_GPS_CAPTURED,
                gpsTimestampMillis = now - (24 * 60 * 60 * 1000),
                isEmergency = true
            )

            db.incidentDao().insertIncident(emg1)
            db.incidentDao().insertIncident(emg2)
            db.incidentDao().insertIncident(emg3)
        }
    }
}
