package com.example.data.location

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver para eventos de geocercas emitidos por Google Play Services Location API.
 * Monitorea el ingreso a las coordenadas 20.643658, -100.492812 (Av. de la Cantera 2750):
 * - Radio Amplio (500m): Pre-autorización pasiva en caseta.
 * - Radio Corto (50m): Apertura automática de puertas mediante BLE / Wi-Fi.
 */
class PradosGeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PradosGeofenceReceiver"
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) {
            Log.w(TAG, "GeofencingEvent recibido es nulo")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.e(TAG, "Error en evento de geocerca: $errorMessage (Código: ${geofencingEvent.errorCode})")
            return
        }

        val transitionType = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: emptyList()

        Log.i(TAG, "Transición detectada: $transitionType con ${triggeringGeofences.size} geocercas")

        for (geofence in triggeringGeofences) {
            when (geofence.requestId) {
                PradosLocationConstants.GEOFENCE_SHORT_ID -> {
                    if (transitionType == Geofence.GEOFENCE_TRANSITION_ENTER ||
                        transitionType == Geofence.GEOFENCE_TRANSITION_DWELL
                    ) {
                        Log.i(TAG, "🚨 INGRESO A RADIO CORTO (50m) EN AV. DE LA CANTERA 2750 -> DISPARANDO BLE / WI-FI")
                        scope.launch {
                            BleWifiGateController.triggerProximityGateOpening(
                                context = context.applicationContext,
                                distanceMeters = 40f,
                                forceTrigger = false
                            )
                        }
                    }
                }

                PradosLocationConstants.GEOFENCE_BROAD_ID -> {
                    if (transitionType == Geofence.GEOFENCE_TRANSITION_ENTER) {
                        Log.i(TAG, "📡 INGRESO A RADIO AMPLIO (500m) -> PRE-AUTORIZACIÓN EN CASETA ACTIVADA")
                        ProximityGateStateManager.recordPreAuthorization(480f)
                    } else if (transitionType == Geofence.GEOFENCE_TRANSITION_EXIT) {
                        Log.i(TAG, "⬅️ SALIDA DE RADIO AMPLIO (500m) -> FUERA DE PERÍMETRO")
                        ProximityGateStateManager.updateLocation(0.0, 0.0, 600f)
                    }
                }
            }
        }
    }
}
