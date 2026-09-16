package com.example.data.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent

/**
 * BroadcastReceiver para eventos de geocercas satelitales emitidos por Google Play Services Location API
 * durante la ejecución de rondas Geo-Alpha.
 *
 * Captura:
 * - GEOFENCE_TRANSITION_ENTER: El guardia ingresa al radio predefinido del geopunto (±35m a 45m).
 * - GEOFENCE_TRANSITION_DWELL: El guardia permanece dentro del radio del geopunto (loitering 1.5s).
 *
 * Despacha de forma asíncrona la detección a GeoAlphaRoundTracker y genera notificaciones locales.
 */
class GeoAlphaGeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeoAlphaGeofenceRx"
        const val EXTRA_TEST_POINT_ID = "EXTRA_TEST_POINT_ID"
        const val EXTRA_TEST_LATITUDE = "EXTRA_TEST_LATITUDE"
        const val EXTRA_TEST_LONGITUDE = "EXTRA_TEST_LONGITUDE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // 1. Soporte para eventos simulados / de prueba
        if (intent.hasExtra(EXTRA_TEST_POINT_ID)) {
            val testPointId = intent.getStringExtra(EXTRA_TEST_POINT_ID)
            if (!testPointId.isNullOrBlank()) {
                val mockLocation = if (intent.hasExtra(EXTRA_TEST_LATITUDE) && intent.hasExtra(EXTRA_TEST_LONGITUDE)) {
                    Location("TestBroadcast").apply {
                        latitude = intent.getDoubleExtra(EXTRA_TEST_LATITUDE, 0.0)
                        longitude = intent.getDoubleExtra(EXTRA_TEST_LONGITUDE, 0.0)
                        time = System.currentTimeMillis()
                    }
                } else {
                    null
                }
                Log.i(TAG, "Procesando evento simulado de geocerca para punto: $testPointId")
                GeoAlphaRoundTracker.onGeofenceEntered(context.applicationContext, testPointId, mockLocation)
                return
            }
        }

        // 2. Procesamiento de GeofencingEvent oficial de Google Play Services
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) {
            Log.w(TAG, "GeofencingEvent recibido es nulo. Acción: ${intent.action}")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.e(TAG, "Error en GeofencingEvent de Google Play Services: $errorMessage (Código: ${geofencingEvent.errorCode})")
            return
        }

        val transitionType = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: emptyList()
        val triggeringLocation = geofencingEvent.triggeringLocation

        Log.i(TAG, "Transición satelital detectada: $transitionType con ${triggeringGeofences.size} geocercas.")

        if (transitionType == Geofence.GEOFENCE_TRANSITION_ENTER || transitionType == Geofence.GEOFENCE_TRANSITION_DWELL) {
            for (geofence in triggeringGeofences) {
                val pointId = geofence.requestId
                Log.i(TAG, "🛰️ Google Play Services reporta entrada/permanencia a geocerca predefinida: $pointId")
                GeoAlphaRoundTracker.onGeofenceEntered(
                    context = context.applicationContext,
                    pointId = pointId,
                    location = triggeringLocation
                )
            }
        } else if (transitionType == Geofence.GEOFENCE_TRANSITION_EXIT) {
            for (geofence in triggeringGeofences) {
                Log.d(TAG, "Salida de geocerca: ${geofence.requestId}")
            }
        }
    }
}
