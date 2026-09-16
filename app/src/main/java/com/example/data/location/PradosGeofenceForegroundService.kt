package com.example.data.location

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Servicio en segundo plano con Google Play Services Location API para monitorear
 * el ingreso a las coordenadas 20.643658, -100.492812 (Av. de la Cantera 2750).
 *
 * Activa el Radio Corto (50m) para la apertura automática de puertas mediante BLE o Wi-Fi.
 * Filosofía: "TIEMPO = FAMILIA" (acceso fluido sin demoras en garita).
 */
class PradosGeofenceForegroundService : Service() {

    companion object {
        private const val TAG = "PradosLocationService"

        fun startService(context: Context) {
            val intent = Intent(context, PradosGeofenceForegroundService::class.java).apply {
                action = PradosLocationConstants.ACTION_START_PROXIMITY_SERVICE
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, PradosGeofenceForegroundService::class.java).apply {
                action = PradosLocationConstants.ACTION_STOP_PROXIMITY_SERVICE
            }
            context.startService(intent)
        }

        fun triggerManualOpen(context: Context) {
            val intent = Intent(context, PradosGeofenceForegroundService::class.java).apply {
                action = PradosLocationConstants.ACTION_TRIGGER_MANUAL_OPEN
            }
            context.startService(intent)
        }

        fun simulateApproach(context: Context, targetDistanceMeters: Float = 45f) {
            val intent = Intent(context, PradosGeofenceForegroundService::class.java).apply {
                action = PradosLocationConstants.ACTION_SIMULATE_APPROACH
                putExtra("TARGET_DISTANCE", targetDistanceMeters)
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geofenceManager: PradosGeofenceManager
    private var locationCallback: LocationCallback? = null
    private var isTracking = false

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geofenceManager = PradosGeofenceManager(this)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            PradosLocationConstants.ACTION_STOP_PROXIMITY_SERVICE -> {
                stopTracking()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            PradosLocationConstants.ACTION_TRIGGER_MANUAL_OPEN -> {
                serviceScope.launch {
                    BleWifiGateController.triggerProximityGateOpening(
                        context = applicationContext,
                        distanceMeters = 20f,
                        forceTrigger = true
                    )
                }
                return START_STICKY
            }

            PradosLocationConstants.ACTION_SIMULATE_APPROACH -> {
                val targetDistance = intent.getFloatExtra("TARGET_DISTANCE", 45f)
                executeSimulatedApproach(targetDistance)
                return START_STICKY
            }

            else -> {
                startForegroundServiceWithNotification()
                startLocationTracking()
                registerGoogleGeofences()
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopTracking()
        ProximityGateStateManager.updateServiceStatus(false)
        Log.i(TAG, "PradosGeofenceForegroundService destruido")
    }

    private fun startForegroundServiceWithNotification() {
        val initialNotification = buildNotification("Iniciando monitoreo satelital...", 999f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                PradosLocationConstants.FOREGROUND_NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(PradosLocationConstants.FOREGROUND_NOTIFICATION_ID, initialNotification)
        }
        ProximityGateStateManager.updateServiceStatus(true)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        if (isTracking) return
        isTracking = true

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
            .setMinUpdateIntervalMillis(2000L)
            .setMinUpdateDistanceMeters(2f)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                processNewLocation(location)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            Log.i(TAG, "FusedLocationProviderClient activado con alta precisión")
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando requestLocationUpdates: ${e.message}", e)
        }
    }

    private fun registerGoogleGeofences() {
        geofenceManager.registerGeofences(
            onSuccess = {
                Log.i(TAG, "Geocercas de 500m y 50m registradas correctamente")
            },
            onFailure = { e ->
                Log.w(TAG, "Registro de geocercas en Google Play Services: ${e.message}")
            }
        )
    }

    private fun processNewLocation(location: Location) {
        val distance = geofenceManager.calculateDistanceToGate(location.latitude, location.longitude)
        ProximityGateStateManager.updateLocation(location.latitude, location.longitude, distance)

        updateNotification("Distancia a Garita: ${distance.toInt()}m", distance)

        // 1. Radio Corto (<= 50m) -> Apertura Automática mediante BLE o Wi-Fi
        if (distance <= PradosLocationConstants.SHORT_RADIUS_METERS) {
            Log.i(TAG, "🎯 DENTRO DE RADIO CORTO (${distance.toInt()}m) -> ACTIVANDO APERTURA BLE/WI-FI")
            serviceScope.launch {
                BleWifiGateController.triggerProximityGateOpening(
                    context = applicationContext,
                    distanceMeters = distance,
                    forceTrigger = false
                )
            }
        }
        // 2. Radio Amplio (<= 500m) -> Pre-autorización y alerta pasiva en caseta
        else if (distance <= PradosLocationConstants.BROAD_RADIUS_METERS) {
            ProximityGateStateManager.recordPreAuthorization(distance)
        }
    }

    private fun executeSimulatedApproach(targetDistance: Float) {
        serviceScope.launch {
            Log.i(TAG, "Iniciando simulación de aproximación a Av. de la Cantera 2750")

            // Paso 1: Enfoque a 650m
            ProximityGateStateManager.updateLocation(20.648000, -100.496000, 650f)
            updateNotification("Aproximándose: 650m", 650f)
            delay(1200)

            // Paso 2: Ingreso a Radio Amplio (450m) -> Pre-autorización
            ProximityGateStateManager.updateLocation(20.646000, -100.494000, 450f)
            updateNotification("Radio Amplio: 450m (Pre-autorización caseta)", 450f)
            delay(1500)

            // Paso 3: Ingreso a Radio Corto (targetDistance, ej: 45m) -> Apertura BLE / Wi-Fi
            ProximityGateStateManager.updateLocation(
                PradosLocationConstants.ACCESS_LATITUDE,
                PradosLocationConstants.ACCESS_LONGITUDE,
                targetDistance
            )
            updateNotification("Radio Corto: ${targetDistance.toInt()}m (Disparando Apertura)", targetDistance)

            BleWifiGateController.triggerProximityGateOpening(
                context = applicationContext,
                distanceMeters = targetDistance,
                forceTrigger = true
            )
        }
    }

    private fun stopTracking() {
        if (!isTracking) return
        isTracking = false
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        geofenceManager.removeGeofences()
    }

    private fun buildNotification(statusText: String, distanceMeters: Float): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("NAVIGATE_TO", "resident_dashboard")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val zoneDesc = when {
            distanceMeters <= PradosLocationConstants.SHORT_RADIUS_METERS -> "Radio Corto (<50m) • Portón Abierto"
            distanceMeters <= PradosLocationConstants.BROAD_RADIUS_METERS -> "Radio Amplio (<500m) • Pre-autorizado"
            else -> "Monitoreando Av. de la Cantera 2750"
        }

        return NotificationCompat.Builder(this, PradosLocationConstants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("🚗 Proximidad Garita Los Prados (GPS)")
            .setContentText("$statusText • $zoneDesc")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Servicio activo de Google Play Services Location.\n" +
                    "Destino: Av. de la Cantera 2750 (20.643658, -100.492812).\n" +
                    "Apertura automática en Radio Corto (50m) vía BLE / Wi-Fi.\n" +
                    "Filosofía: TIEMPO = FAMILIA."
                )
            )
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String, distanceMeters: Float) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(
                PradosLocationConstants.FOREGROUND_NOTIFICATION_ID,
                buildNotification(statusText, distanceMeters)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Error actualizando notificación foreground: ${e.message}")
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // Canal 1: Servicio en segundo plano
            val serviceChannel = NotificationChannel(
                PradosLocationConstants.NOTIFICATION_CHANNEL_ID,
                PradosLocationConstants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = PradosLocationConstants.NOTIFICATION_CHANNEL_DESC
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(serviceChannel)

            // Canal 2: Avisos de apertura exitosa del portón
            val gateChannel = NotificationChannel(
                PradosLocationConstants.GATE_OPENED_CHANNEL_ID,
                PradosLocationConstants.GATE_OPENED_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = PradosLocationConstants.GATE_OPENED_CHANNEL_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 300)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(gateChannel)
        }
    }
}
