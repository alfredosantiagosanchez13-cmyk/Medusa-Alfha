package com.example.data.location

import android.content.Context
import android.location.Location
import android.util.Log
import com.example.data.booking.AppDatabase
import com.example.data.core.LocalDataBackupManager
import com.example.data.incident.IncidentEntity
import com.example.data.supervision.ActiveGeoAlphaTourState
import com.example.ui.components.CondoTarget
import com.example.data.supervision.GeoAlphaDetection
import com.example.data.supervision.GeoAlphaPoint
import com.example.data.supervision.GeoAlphaTourEngine
import com.example.data.supervision.GeoAlphaTourReport
import com.example.utils.ResidentNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Coordinador de Estado y Monitoreo de Rondas Satelitales Geo-Alpha.
 *
 * Integra:
 * 1. Google Play Services Location API GeofencingClient para detección pasiva ultra-eficiente por hardware.
 * 2. Google Play Services FusedLocationProviderClient para chequeo continuo redundante por GPS de alta precisión.
 * 3. Actualización reactiva del estado inmutable ActiveGeoAlphaTourState.
 * 4. Certificación y respaldo automático contra cierres de app en LocalDataBackupManager y Room SQLite.
 */
object GeoAlphaRoundTracker {

    private const val TAG = "GeoAlphaRoundTracker"

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _activeTour = MutableStateFlow<ActiveGeoAlphaTourState?>(null)
    val activeTour: StateFlow<ActiveGeoAlphaTourState?> = _activeTour.asStateFlow()

    private val _isTrackingActive = MutableStateFlow(false)
    val isTrackingActive: StateFlow<Boolean> = _isTrackingActive.asStateFlow()

    private val _registeredGeofenceCount = MutableStateFlow(0)
    val registeredGeofenceCount: StateFlow<Int> = _registeredGeofenceCount.asStateFlow()

    private val _latestDetection = MutableSharedFlow<GeoAlphaDetection>(extraBufferCapacity = 16)
    val latestDetection: SharedFlow<GeoAlphaDetection> = _latestDetection.asSharedFlow()

    private val _latestLocation = MutableStateFlow<Location?>(null)
    val latestLocation: StateFlow<Location?> = _latestLocation.asStateFlow()

    @Volatile
    private var geofenceManagerInstance: GeoAlphaGeofenceManager? = null

    fun getGeofenceManager(context: Context): GeoAlphaGeofenceManager {
        return geofenceManagerInstance ?: synchronized(this) {
            geofenceManagerInstance ?: GeoAlphaGeofenceManager(context.applicationContext).also {
                geofenceManagerInstance = it
            }
        }
    }

    /**
     * Inicia una nueva ronda Geo-Alpha registrando los geopuntos predefinidos en Google Play Services Geofencing.
     */
    fun startTracking(
        context: Context,
        condo: CondoTarget,
        guardName: String,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        val appContext = context.applicationContext
        val newTour = GeoAlphaTourEngine.startTour(condo, guardName)
        _activeTour.value = newTour
        _isTrackingActive.value = true

        val points = GeoAlphaTourEngine.getPointsForCondo(condo)
        val manager = getGeofenceManager(appContext)

        // 1. Registrar geocercas en Google Play Services
        manager.registerGeofences(
            points = points,
            onSuccess = {
                _registeredGeofenceCount.value = points.size
                Log.i(TAG, "Ronda ${newTour.tourFolio} iniciada con ${points.size} geocercas activas en Google Play Services.")
                onSuccess()
            },
            onFailure = { e ->
                Log.w(TAG, "Aviso: no fue posible registrar geocercas en Google Play Services: ${e.message}. Continuando con rastreo GPS redundante.")
                _registeredGeofenceCount.value = points.size
                onFailure(e)
            }
        )

        // 2. Iniciar rastreo continuo con FusedLocationProviderClient (redundancia de doble capa)
        manager.startContinuousLocationUpdates(intervalMillis = 3000L) { loc ->
            onLocationUpdated(appContext, loc)
        }

        // 3. Iniciar Foreground Service para protección en segundo plano / pantalla bloqueada
        GeoAlphaRoundForegroundService.startService(
            appContext,
            tourFolio = newTour.tourFolio,
            condoName = condo.displayName,
            totalPoints = points.size
        )

        // 4. Guardar respaldo local
        scope.launch {
            LocalDataBackupManager.saveOngoingTourState(
                context = appContext,
                isTourActive = true,
                tourStartMillis = newTour.startedAtMillis,
                supervisorName = guardName,
                currentGps = "${condo.displayName} [Geo-Alpha Iniciada]"
            )
        }
    }

    /**
     * Invocado cuando Google Play Services Location API detecta la entrada (GEOFENCE_TRANSITION_ENTER o DWELL)
     * a una geocerca predefinida.
     */
    fun onGeofenceEntered(
        context: Context,
        pointId: String,
        location: Location?
    ) {
        val currentTour = _activeTour.value
        if (currentTour == null) {
            Log.w(TAG, "Evento de geocerca recibido ($pointId) pero no hay ronda activa en memoria.")
            return
        }

        // Verificar si el punto ya fue cubierto en esta ronda
        val alreadyCovered = currentTour.coveredDetections.any { it.pointId.equals(pointId, ignoreCase = true) }
        if (alreadyCovered) {
            Log.d(TAG, "Geopunto $pointId ya fue cubierto previamente en esta ronda. Omitiendo duplicado.")
            return
        }

        // Buscar el geopunto en los pendientes de la ronda
        val point = currentTour.pendingPoints.find { it.id.equals(pointId, ignoreCase = true) }
            ?: GeoAlphaTourEngine.findPointById(pointId)

        if (point == null) {
            Log.w(TAG, "Geopunto $pointId no pertenece al catálogo autorizado de esta ronda.")
            return
        }

        val now = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now))
        val lat = location?.latitude ?: point.latitude
        val lng = location?.longitude ?: point.longitude
        val dist = if (location != null) {
            GeoAlphaTourEngine.calculateDistanceMeters(location.latitude, location.longitude, point.latitude, point.longitude)
        } else {
            0.0f
        }

        val detection = GeoAlphaDetection(
            pointId = point.id,
            pointName = point.name,
            area = point.area,
            sequenceOrder = currentTour.coveredDetections.size + 1,
            detectedAtMillis = now,
            formattedTime = timeStr,
            latitude = lat,
            longitude = lng,
            distanceMeters = dist,
            isAutomatic = true,
            findings = "Paso detectado automáticamente mediante Google Play Services Geofencing API (Radio: ±${point.radiusMeters.toInt()}m)"
        )

        val updatedTour = currentTour.copy(
            coveredDetections = currentTour.coveredDetections + detection
        )

        _activeTour.value = updatedTour
        _latestDetection.tryEmit(detection)

        Log.i(TAG, "🎯 GEOCERCA DETECTADA: ${point.name} (${point.id}) #${detection.sequenceOrder} - Cobertura: ${updatedTour.coveragePercentage}%")

        // 1. Notificación al guardia (sonido + vibración táctica)
        ResidentNotificationManager.notifyGeofenceCheckpointDetected(
            context = context.applicationContext,
            pointName = point.name,
            areaName = point.area,
            sequence = detection.sequenceOrder,
            totalPoints = updatedTour.allAuthorizedPoints.size,
            tourFolio = updatedTour.tourFolio
        )

        // 2. Actualizar notificación del Foreground Service
        GeoAlphaRoundForegroundService.updateProgress(
            context = context.applicationContext,
            coveredCount = updatedTour.coveredDetections.size,
            totalCount = updatedTour.allAuthorizedPoints.size
        )

        // 3. Actualizar respaldo persistente
        scope.launch {
            LocalDataBackupManager.saveOngoingTourState(
                context = context.applicationContext,
                isTourActive = true,
                tourStartMillis = updatedTour.startedAtMillis,
                supervisorName = updatedTour.guardName,
                currentGps = String.format(Locale.US, "%.5f, %.5f [%s]", lat, lng, point.name)
            )
        }
    }

    /**
     * Invocado por el FusedLocationProviderClient continuo para evaluación redundante de doble capa.
     */
    fun onLocationUpdated(context: Context, location: Location) {
        _latestLocation.value = location
        val currentTour = _activeTour.value ?: return

        val (updated, newDet) = GeoAlphaTourEngine.evaluateGpsPosition(
            currentState = currentTour,
            lat = location.latitude,
            lng = location.longitude
        )

        if (newDet != null) {
            _activeTour.value = updated
            _latestDetection.tryEmit(newDet)

            Log.i(TAG, "🛰️ DOBLE CAPA GPS DETECCIÓN: ${newDet.pointName} (${newDet.pointId}) a ${newDet.distanceMeters.toInt()}m")

            ResidentNotificationManager.notifyGeofenceCheckpointDetected(
                context = context.applicationContext,
                pointName = newDet.pointName,
                areaName = newDet.area,
                sequence = newDet.sequenceOrder,
                totalPoints = updated.allAuthorizedPoints.size,
                tourFolio = updated.tourFolio
            )

            GeoAlphaRoundForegroundService.updateProgress(
                context = context.applicationContext,
                coveredCount = updated.coveredDetections.size,
                totalCount = updated.allAuthorizedPoints.size
            )
        }
    }

    /**
     * Permite simular el paso o ingreso a una geocerca para pruebas, emuladores o verificación manual.
     */
    fun simulateGeofencePass(context: Context, pointId: String) {
        val point = GeoAlphaTourEngine.findPointById(pointId)
        val mockLoc = point?.let {
            Location("MockGPS").apply {
                latitude = it.latitude
                longitude = it.longitude
                accuracy = 3.5f
                time = System.currentTimeMillis()
            }
        }
        onGeofenceEntered(context, pointId, mockLoc)
    }

    /**
     * Vincula una incidencia ocurrida durante el recorrido.
     */
    fun linkIncident(incident: IncidentEntity) {
        val cur = _activeTour.value ?: return
        _activeTour.value = GeoAlphaTourEngine.linkIncident(cur, incident)
    }

    /**
     * Autoriza el cierre con puntos pendientes mediante firma de supervisor.
     */
    fun authorizeSupervisorOverride(notes: String) {
        val cur = _activeTour.value ?: return
        _activeTour.value = GeoAlphaTourEngine.authorizeSupervisorOverride(cur, notes)
    }

    /**
     * Finaliza la ronda, remueve las geocercas satelitales en Google Play Services, detiene el Foreground Service,
     * y genera el reporte inmutable certificado en Room.
     */
    suspend fun finalizeRound(
        context: Context,
        db: AppDatabase
    ): GeoAlphaTourReport? {
        val cur = _activeTour.value ?: return null
        val appContext = context.applicationContext

        // 1. Remover geocercas en Google Play Services Location API
        val manager = getGeofenceManager(appContext)
        manager.removeGeofences()
        manager.stopContinuousLocationUpdates()

        // 2. Detener Foreground Service
        GeoAlphaRoundForegroundService.stopService(appContext)

        // 3. Finalizar y certificar en Room
        val report = GeoAlphaTourEngine.finalizeTour(appContext, db, cur)

        // 4. Limpiar estado activo
        _activeTour.value = null
        _isTrackingActive.value = false
        _registeredGeofenceCount.value = 0

        // 5. Limpiar respaldo en disco
        LocalDataBackupManager.clearOngoingTourState(appContext)

        Log.i(TAG, "🏁 Ronda finalizada exitosamente. Folio: ${report.tourFolio}, Cobertura: ${report.coveragePercentage}%")
        return report
    }

    /**
     * Cancela la ronda activa liberando geocercas de Google Play Services.
     */
    fun cancelRound(context: Context) {
        val appContext = context.applicationContext
        val manager = getGeofenceManager(appContext)
        manager.removeGeofences()
        manager.stopContinuousLocationUpdates()
        GeoAlphaRoundForegroundService.stopService(appContext)

        _activeTour.value = null
        _isTrackingActive.value = false
        _registeredGeofenceCount.value = 0

        scope.launch {
            LocalDataBackupManager.clearOngoingTourState(appContext)
        }
        Log.i(TAG, "Ronda cancelada y geocercas liberadas.")
    }
}
