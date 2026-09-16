package com.example.data.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class GeofenceZone(val label: String, val description: String) {
    OUT_OF_RANGE("Fuera de Perímetro", "A más de 500m de Av. de la Cantera 2750"),
    BROAD_RADIUS_500M("Radio Amplio (500m)", "Pre-autorización y alerta pasiva enviada a caseta"),
    SHORT_RADIUS_50M_GATE_OPENED("Radio Corto (50m)", "Portón activado automáticamente por BLE / Wi-Fi")
}

data class ProximityGateEvent(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val zone: GeofenceZone,
    val distanceMeters: Float,
    val method: String, // "BLE", "WI_FI", "BLE_Y_WI_FI", "SIMULACION"
    val folio: String,
    val details: String,
    val timeSavedSeconds: Int = 35 // Promedio de 35s devueltos por apertura touchless
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

data class ProximityGateUiState(
    val isServiceRunning: Boolean = false,
    val currentLatitude: Double = 0.0,
    val currentLongitude: Double = 0.0,
    val currentDistanceMeters: Float? = null,
    val currentZone: GeofenceZone = GeofenceZone.OUT_OF_RANGE,
    val lastOpeningFolio: String? = null,
    val lastOpeningTimestamp: Long? = null,
    val lastTriggerMethod: String? = null,
    val lastTriggerStatus: String? = null,
    val bleAvailable: Boolean = true,
    val wifiAvailable: Boolean = true,
    val totalTimeSavedMinutes: Int = 0,
    val totalAutomaticOpeningsCount: Int = 0,
    val eventsHistory: List<ProximityGateEvent> = emptyList()
)

object ProximityGateStateManager {

    private val _uiState = MutableStateFlow(ProximityGateUiState())
    val uiState: StateFlow<ProximityGateUiState> = _uiState.asStateFlow()

    fun updateServiceStatus(isRunning: Boolean) {
        _uiState.value = _uiState.value.copy(isServiceRunning = isRunning)
    }

    fun updateLocation(lat: Double, lng: Double, distanceMeters: Float) {
        val zone = when {
            distanceMeters <= PradosLocationConstants.SHORT_RADIUS_METERS -> GeofenceZone.SHORT_RADIUS_50M_GATE_OPENED
            distanceMeters <= PradosLocationConstants.BROAD_RADIUS_METERS -> GeofenceZone.BROAD_RADIUS_500M
            else -> GeofenceZone.OUT_OF_RANGE
        }

        _uiState.value = _uiState.value.copy(
            currentLatitude = lat,
            currentLongitude = lng,
            currentDistanceMeters = distanceMeters,
            currentZone = zone
        )
    }

    fun recordGateOpeningSuccess(
        folio: String,
        method: String,
        distanceMeters: Float,
        details: String
    ) {
        val newEvent = ProximityGateEvent(
            id = folio,
            zone = GeofenceZone.SHORT_RADIUS_50M_GATE_OPENED,
            distanceMeters = distanceMeters,
            method = method,
            folio = folio,
            details = details,
            timeSavedSeconds = 35
        )

        val updatedHistory = listOf(newEvent) + _uiState.value.eventsHistory.take(19)
        val newCount = _uiState.value.totalAutomaticOpeningsCount + 1
        val newTimeSaved = (newCount * 35) / 60

        _uiState.value = _uiState.value.copy(
            lastOpeningFolio = folio,
            lastOpeningTimestamp = System.currentTimeMillis(),
            lastTriggerMethod = method,
            lastTriggerStatus = "EXITO_APERTURA_ACTIVADA",
            currentZone = GeofenceZone.SHORT_RADIUS_50M_GATE_OPENED,
            totalAutomaticOpeningsCount = newCount,
            totalTimeSavedMinutes = newTimeSaved,
            eventsHistory = updatedHistory
        )
    }

    fun recordPreAuthorization(distanceMeters: Float) {
        if (_uiState.value.currentZone != GeofenceZone.BROAD_RADIUS_500M) {
            _uiState.value = _uiState.value.copy(
                currentZone = GeofenceZone.BROAD_RADIUS_500M,
                currentDistanceMeters = distanceMeters
            )
        }
    }

    fun updateHardwareStatus(ble: Boolean, wifi: Boolean) {
        _uiState.value = _uiState.value.copy(
            bleAvailable = ble,
            wifiAvailable = wifi
        )
    }
}
