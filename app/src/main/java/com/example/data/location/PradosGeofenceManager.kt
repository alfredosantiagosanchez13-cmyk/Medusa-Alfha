package com.example.data.location

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/**
 * Administrador de Geocercas de Google Play Services Location API
 * para Los Prados Residencial (Av. de la Cantera 2750).
 */
class PradosGeofenceManager(private val context: Context) {

    companion object {
        private const val TAG = "PradosGeofenceManager"
    }

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, PradosGeofenceBroadcastReceiver::class.java).apply {
            action = PradosLocationConstants.ACTION_GEOFENCE_EVENT
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    /**
     * Construye las geocercas oficiales para Los Prados:
     * 1. Radio Amplio (500m): Pre-autorización
     * 2. Radio Corto (50m): Apertura por BLE / Wi-Fi
     */
    fun createGeofences(): List<Geofence> {
        val broadGeofence = Geofence.Builder()
            .setRequestId(PradosLocationConstants.GEOFENCE_BROAD_ID)
            .setCircularRegion(
                PradosLocationConstants.ACCESS_LATITUDE,
                PradosLocationConstants.ACCESS_LONGITUDE,
                PradosLocationConstants.BROAD_RADIUS_METERS
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        val shortGeofence = Geofence.Builder()
            .setRequestId(PradosLocationConstants.GEOFENCE_SHORT_ID)
            .setCircularRegion(
                PradosLocationConstants.ACCESS_LATITUDE,
                PradosLocationConstants.ACCESS_LONGITUDE,
                PradosLocationConstants.SHORT_RADIUS_METERS
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL)
            .setLoiteringDelay(3000) // 3 segundos de dwell
            .build()

        return listOf(broadGeofence, shortGeofence)
    }

    /**
     * Registra las geocercas en el cliente de Google Play Services.
     */
    @SuppressLint("MissingPermission")
    fun registerGeofences(onSuccess: () -> Unit = {}, onFailure: (Exception) -> Unit = {}) {
        try {
            val geofences = createGeofences()
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                .addGeofences(geofences)
                .build()

            geofencingClient.addGeofences(request, geofencePendingIntent)
                .addOnSuccessListener {
                    Log.i(TAG, "Geocercas de Los Prados (500m y 50m) registradas con éxito en Google Play Services")
                    onSuccess()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Fallo al registrar geocercas en Google Play Services: ${e.message}", e)
                    onFailure(e)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al registrar geocercas: ${e.message}", e)
            onFailure(e)
        }
    }

    /**
     * Remueve las geocercas activas.
     */
    fun removeGeofences(onComplete: () -> Unit = {}) {
        geofencingClient.removeGeofences(geofencePendingIntent)
            .addOnCompleteListener {
                Log.i(TAG, "Geocercas de Los Prados removidas")
                onComplete()
            }
    }

    /**
     * Calcula la distancia en metros desde una coordenada hasta la garita de Los Prados.
     */
    fun calculateDistanceToGate(lat: Double, lng: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            lat, lng,
            PradosLocationConstants.ACCESS_LATITUDE,
            PradosLocationConstants.ACCESS_LONGITUDE,
            results
        )
        return results[0]
    }
}
