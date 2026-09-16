package com.example.data.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.supervision.GeoAlphaPoint
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Gestor de Geocercas de Google Play Services Location API para rondas de supervisión Geo-Alpha.
 *
 * Responsabilidades:
 * 1. Convertir catálogos de geopuntos predefinidos (GeoAlphaPoint) en instancias de Geofence de Google Play Services.
 * 2. Registrar solicitudes GeofencingRequest con disparadores inmediatos (INITIAL_TRIGGER_ENTER | INITIAL_TRIGGER_DWELL).
 * 3. Enrutar eventos satelitales al BroadcastReceiver dedicado (GeoAlphaGeofenceBroadcastReceiver).
 * 4. Proveer monitoreo GPS continuo complementario con FusedLocationProviderClient para redundancia y cobertura garantizada.
 * 5. Liberar geocercas activas al finalizar o cancelar la ronda.
 */
class GeoAlphaGeofenceManager(private val context: Context) {

    companion object {
        private const val TAG = "GeoAlphaGeofenceMgr"
        const val ACTION_GEO_ALPHA_GEOFENCE_EVENT = "com.example.medusa.ACTION_GEO_ALPHA_GEOFENCE_EVENT"
        private const val REQUEST_CODE_GEOFENCE = 701
    }

    private val geofencingClient: GeofencingClient by lazy {
        LocationServices.getGeofencingClient(context)
    }

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    private var activeLocationCallback: LocationCallback? = null

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeoAlphaGeofenceBroadcastReceiver::class.java).apply {
            action = ACTION_GEO_ALPHA_GEOFENCE_EVENT
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        PendingIntent.getBroadcast(context, REQUEST_CODE_GEOFENCE, intent, flags)
    }

    /**
     * Convierte una lista de geopuntos predefinidos en instancias de Geofence de Google Play Services.
     */
    fun createGeofences(points: List<GeoAlphaPoint>): List<Geofence> {
        return points.map { point ->
            Geofence.Builder()
                .setRequestId(point.id)
                .setCircularRegion(
                    point.latitude,
                    point.longitude,
                    point.radiusMeters
                )
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL)
                .setLoiteringDelay(1500) // 1.5 segundos de permanencia para certificar paso
                .setNotificationResponsiveness(1000) // Notificación en menos de 1 segundo
                .build()
        }
    }

    /**
     * Construye la solicitud GeofencingRequest con disparador inicial activado.
     */
    fun buildGeofencingRequest(geofences: List<Geofence>): GeofencingRequest {
        return GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER or GeofencingRequest.INITIAL_TRIGGER_DWELL)
            .addGeofences(geofences)
            .build()
    }

    /**
     * Registra las geocercas predefinidas de los puntos de ronda en Google Play Services Location API.
     */
    @SuppressLint("MissingPermission")
    fun registerGeofences(
        points: List<GeoAlphaPoint>,
        onSuccess: () -> Unit = {},
        onFailure: (Exception) -> Unit = {}
    ) {
        if (points.isEmpty()) {
            Log.w(TAG, "No se proporcionaron geopuntos para registrar geocercas.")
            onSuccess()
            return
        }

        if (!hasLocationPermission()) {
            val error = SecurityException("Permiso de ubicación (ACCESS_FINE_LOCATION) no concedido.")
            Log.e(TAG, "No se pueden registrar geocercas sin permisos de ubicación: ${error.message}")
            onFailure(error)
            return
        }

        try {
            val geofences = createGeofences(points)
            val request = buildGeofencingRequest(geofences)

            geofencingClient.addGeofences(request, geofencePendingIntent)
                .addOnSuccessListener {
                    Log.i(TAG, "✅ ${geofences.size} geocercas Geo-Alpha registradas exitosamente en Google Play Services.")
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Error al registrar geocercas en Google Play Services: ${e.message}", e)
                    onFailure(e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción inesperada en registerGeofences: ${e.message}", e)
            onFailure(e)
        }
    }

    /**
     * Remueve todas las geocercas activas registradas con el PendingIntent de Geo-Alpha.
     */
    fun removeGeofences(onComplete: () -> Unit = {}) {
        try {
            geofencingClient.removeGeofences(geofencePendingIntent)
                .addOnSuccessListener {
                    Log.i(TAG, "Geocercas de Geo-Alpha removidas exitosamente.")
                    onComplete()
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Aviso al remover geocercas: ${e.message}")
                    onComplete()
                }
        } catch (e: Exception) {
            Log.w(TAG, "Excepción al remover geocercas: ${e.message}")
            onComplete()
        }
    }

    /**
     * Remueve un subconjunto específico de geocercas por sus IDs (e.g. puntos ya cubiertos).
     */
    fun removeGeofencesByIds(pointIds: List<String>, onComplete: () -> Unit = {}) {
        if (pointIds.isEmpty()) {
            onComplete()
            return
        }
        try {
            geofencingClient.removeGeofences(pointIds)
                .addOnCompleteListener {
                    Log.i(TAG, "Geocercas especificas removidas: $pointIds")
                    onComplete()
                }
        } catch (e: Exception) {
            Log.w(TAG, "Error removiendo geocercas por IDs: ${e.message}")
            onComplete()
        }
    }

    /**
     * Inicia monitoreo GPS continuo complementario mediante FusedLocationProviderClient.
     * Permite evaluación redundante en tiempo real con precisión milimétrica.
     */
    @SuppressLint("MissingPermission")
    fun startContinuousLocationUpdates(
        intervalMillis: Long = 3000L,
        onLocation: (Location) -> Unit
    ) {
        if (!hasLocationPermission()) {
            Log.w(TAG, "No se puede iniciar FusedLocationProvider sin permisos de ubicación.")
            return
        }

        stopContinuousLocationUpdates()

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
            .setMinUpdateIntervalMillis(1500L)
            .setMinUpdateDistanceMeters(2.0f)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                onLocation(loc)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
            activeLocationCallback = callback
            Log.i(TAG, "📡 FusedLocationProviderClient activo para ronda Geo-Alpha (intervalo: ${intervalMillis}ms)")
        } catch (e: Exception) {
            Log.e(TAG, "Fallo al iniciar requestLocationUpdates: ${e.message}", e)
        }
    }

    /**
     * Detiene el monitoreo continuo de ubicación.
     */
    fun stopContinuousLocationUpdates() {
        activeLocationCallback?.let { callback ->
            try {
                fusedLocationClient.removeLocationUpdates(callback)
                Log.i(TAG, "FusedLocationProviderClient detenido.")
            } catch (e: Exception) {
                Log.w(TAG, "Error al detener location updates: ${e.message}")
            }
            activeLocationCallback = null
        }
    }

    /**
     * Valida si la aplicación cuenta con permisos de ubicación otorgados.
     */
    fun hasLocationPermission(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation || coarseLocation
    }
}
