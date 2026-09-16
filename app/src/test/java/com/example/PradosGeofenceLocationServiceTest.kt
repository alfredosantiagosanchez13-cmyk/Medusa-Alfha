package com.example

import com.example.data.core.AlphaCoreEngine
import com.example.data.location.GeofenceZone
import com.example.data.location.PradosLocationConstants
import com.example.data.location.ProximityGateStateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PradosGeofenceLocationServiceTest {

    @Test
    fun testPradosCoordinatesAndRadiiConstants() {
        assertEquals(20.643658, PradosLocationConstants.ACCESS_LATITUDE, 0.000001)
        assertEquals(-100.492812, PradosLocationConstants.ACCESS_LONGITUDE, 0.000001)
        assertEquals(500f, PradosLocationConstants.BROAD_RADIUS_METERS, 0.1f)
        assertEquals(50f, PradosLocationConstants.SHORT_RADIUS_METERS, 0.1f)
        assertEquals("Av. de la Cantera 2750, Código Postal 76116, Santiago de Querétaro, Qro.", PradosLocationConstants.ADDRESS)
    }

    @Test
    fun testDistanceCalculationAndZoneClassification() {
        // 1. Clasificación de Radio Corto (<= 50m)
        ProximityGateStateManager.updateLocation(
            PradosLocationConstants.ACCESS_LATITUDE,
            PradosLocationConstants.ACCESS_LONGITUDE,
            35f
        )
        val stateShort = ProximityGateStateManager.uiState.value
        assertEquals(GeofenceZone.SHORT_RADIUS_50M_GATE_OPENED, stateShort.currentZone)
        assertEquals(35f, stateShort.currentDistanceMeters ?: 0f, 0.1f)

        // 2. Clasificación de Radio Amplio (<= 500m)
        ProximityGateStateManager.updateLocation(
            20.645000,
            -100.493000,
            320f
        )
        val stateBroad = ProximityGateStateManager.uiState.value
        assertEquals(GeofenceZone.BROAD_RADIUS_500M, stateBroad.currentZone)
        assertEquals(320f, stateBroad.currentDistanceMeters ?: 0f, 0.1f)

        // 3. Clasificación Fuera de Perímetro (> 500m)
        ProximityGateStateManager.updateLocation(
            20.660000,
            -100.510000,
            2400f
        )
        val stateOut = ProximityGateStateManager.uiState.value
        assertEquals(GeofenceZone.OUT_OF_RANGE, stateOut.currentZone)
    }

    @Test
    fun testGateOpeningStateAndTiempoEsFamiliaMetric() {
        val testFolio = AlphaCoreEngine.generateUniqueFolio("PROX")
        assertTrue(testFolio.startsWith("PROX-"))

        val initialCount = ProximityGateStateManager.uiState.value.totalAutomaticOpeningsCount
        ProximityGateStateManager.recordGateOpeningSuccess(
            folio = testFolio,
            method = "BLE_Y_WI_FI",
            distanceMeters = 42f,
            details = "Apertura en Radio Corto 42m"
        )

        val updatedState = ProximityGateStateManager.uiState.value
        assertEquals(initialCount + 1, updatedState.totalAutomaticOpeningsCount)
        assertEquals(testFolio, updatedState.lastOpeningFolio)
        assertEquals("BLE_Y_WI_FI", updatedState.lastTriggerMethod)
        assertEquals(GeofenceZone.SHORT_RADIUS_50M_GATE_OPENED, updatedState.currentZone)
        assertNotNull(updatedState.lastOpeningTimestamp)
        assertTrue(updatedState.eventsHistory.any { it.folio == testFolio })
    }
}
