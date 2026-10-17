package com.example.medusaalfha

import com.example.medusaalfha.data.model.CondominiumDirectory
import com.example.medusaalfha.data.model.HouseAlertLevel
import com.example.medusaalfha.ui.viewmodel.GuardStationViewModel
import com.example.medusaalfha.ui.viewmodel.ResidentConfirmationState
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GuardStationViewModelTest {

    private lateinit var viewModel: GuardStationViewModel

    @Before
    fun setUp() {
        viewModel = GuardStationViewModel()
    }

    @Test
    fun testAttendanceRestroomToggle_updatesStateAndLogs() {
        assertFalse(viewModel.uiState.value.attendance.isAwayAtRestroom)

        viewModel.setGuardAtRestroom(true)
        assertTrue(viewModel.uiState.value.attendance.isAwayAtRestroom)
        assertNotNull(viewModel.uiState.value.attendance.awaySinceTimestamp)
        assertTrue(viewModel.uiState.value.attendance.logs.isNotEmpty())
        assertTrue(viewModel.uiState.value.attendance.logs.first().contains("Guardia ausente en WC"))

        viewModel.setGuardAtRestroom(false)
        assertFalse(viewModel.uiState.value.attendance.isAwayAtRestroom)
        assertNull(viewModel.uiState.value.attendance.awaySinceTimestamp)
        assertTrue(viewModel.uiState.value.attendance.logs.first().contains("Guardia regresó de WC"))
    }

    @Test
    fun testPhotoEvidenceToggle_togglesBooleanFlags() {
        assertFalse(viewModel.uiState.value.hasIdPhoto)
        assertFalse(viewModel.uiState.value.hasVehiclePhoto)

        viewModel.toggleIdPhoto()
        assertTrue(viewModel.uiState.value.hasIdPhoto)

        viewModel.toggleVehiclePhoto()
        assertTrue(viewModel.uiState.value.hasVehiclePhoto)
    }

    @Test
    fun testMultiHouseSelection_addsAndRemovesHouses() {
        assertTrue(viewModel.uiState.value.selectedHouses.contains("Casa 54"))

        viewModel.toggleHouseSelection("Casa 12")
        assertTrue(viewModel.uiState.value.selectedHouses.contains("Casa 12"))
        assertEquals(2, viewModel.uiState.value.selectedHouses.size)

        viewModel.toggleHouseSelection("Casa 88")
        assertEquals(3, viewModel.uiState.value.selectedHouses.size)

        viewModel.toggleHouseSelection("Casa 12")
        assertFalse(viewModel.uiState.value.selectedHouses.contains("Casa 12"))
    }

    @Test
    fun testCondominiumDirectory_returnsConsignmentsAndHouses() {
        val house54 = CondominiumDirectory.findHouse("Casa 54")
        assertNotNull(house54)
        assertEquals("Familia González", house54?.residentName)
        assertEquals(HouseAlertLevel.ATTENTION, house54?.alertLevel)
        assertNotNull(house54?.specialConsignments)

        val houseClean = CondominiumDirectory.findHouse("12")
        assertNotNull(houseClean)
        assertEquals("Casa 12", houseClean?.houseId)
    }

    @Test
    fun testPickupQrVerification_removesPackageWhenTokenMatches() {
        val initialCount = viewModel.uiState.value.pendingPackages.size
        assertTrue(initialCount > 0)

        val targetToken = viewModel.uiState.value.pendingPackages.first().qrPickupToken
        viewModel.handlePickupQrScanned(targetToken)

        assertEquals(initialCount - 1, viewModel.uiState.value.pendingPackages.size)
        assertTrue(viewModel.uiState.value.systemNotice?.contains("entregado") == true || viewModel.uiState.value.systemNotice?.contains("ENTREGADO") == true)
    }

    @Test
    fun testBlock1_CriticalFatigueTrigger_detectsOver24Hours() {
        // Inicialmente el turno de prueba está configurado en ~7h (< 24h)
        assertFalse(viewModel.uiState.value.attendance.isCriticalFatigue)

        // Activamos la simulación de turno crítico (> 24 horas)
        viewModel.toggleSimulatedCriticalFatigue()
        assertTrue(viewModel.uiState.value.attendance.isCriticalFatigue)

        // Verificamos que el formato de tiempo exceda las 24 horas (ej. 25:xx)
        val elapsed = viewModel.uiState.value.attendance.elapsedFormatted
        assertTrue(elapsed.startsWith("25:") || elapsed.startsWith("24:"))

        // Restablecemos la simulación
        viewModel.toggleSimulatedCriticalFatigue()
        assertFalse(viewModel.uiState.value.attendance.isCriticalFatigue)
    }

    @Test
    fun testBlock1_BiometricShiftHandover_resetsShiftTimerAndRegistersNewGuard() {
        // Abrimos el modal de relevo
        viewModel.openShiftHandoverModal()
        assertTrue(viewModel.uiState.value.isShiftHandoverOpen)
        assertFalse(viewModel.uiState.value.isIncomingPhotoCaptured)
        assertFalse(viewModel.uiState.value.isBiometricSignatureVerified)

        // Captura de foto y verificación de firma biométrica
        viewModel.captureIncomingGuardPhoto()
        assertTrue(viewModel.uiState.value.isIncomingPhotoCaptured)

        viewModel.verifyBiometricSignature()
        assertTrue(viewModel.uiState.value.isBiometricSignatureVerified)

        // Confirmamos el relevo con el nuevo elemento
        viewModel.commitShiftChange(
            incomingGuardName = "Oficial Mario Estrada",
            incomingRank = "Comandante de Turno Delta"
        )

        // El modal se cierra y el oficial cambia
        assertFalse(viewModel.uiState.value.isShiftHandoverOpen)
        assertEquals("Oficial Mario Estrada", viewModel.uiState.value.attendance.guardName)
        assertEquals("Comandante de Turno Delta", viewModel.uiState.value.attendance.guardRank)

        // El contador de turno se reinicia a 00:00 y no tiene fatiga crítica
        assertFalse(viewModel.uiState.value.attendance.isCriticalFatigue)
        assertEquals("00:00", viewModel.uiState.value.attendance.elapsedFormatted)
    }

    @Test
    fun testBlock4_CheckpointValidationAndResetCircuit() {
        val initialCheckpoints = viewModel.uiState.value.checkpoints
        assertTrue(initialCheckpoints.isNotEmpty())
        assertEquals(0, initialCheckpoints.count { it.isValidated })

        // Validar el primer checkpoint (CP-01)
        val firstCp = initialCheckpoints.first()
        viewModel.validateCheckpointLocation(firstCp.id)

        val updatedCheckpoints = viewModel.uiState.value.checkpoints
        val validatedFirst = updatedCheckpoints.find { it.id == firstCp.id }
        assertNotNull(validatedFirst)
        assertTrue(validatedFirst!!.isValidated)
        assertNotNull(validatedFirst.validatedAtMillis)
        assertEquals(1, updatedCheckpoints.count { it.isValidated })
        assertTrue(viewModel.uiState.value.lastCheckpointFeedback?.contains("GPS OK") == true)

        // Validar segundo checkpoint (CP-02)
        val secondCp = updatedCheckpoints[1]
        viewModel.validateCheckpointLocation(secondCp.id)
        assertEquals(2, viewModel.uiState.value.checkpoints.count { it.isValidated })

        // Reiniciar circuito
        viewModel.resetPatrolCircuit()
        assertEquals(0, viewModel.uiState.value.checkpoints.count { it.isValidated })
        assertTrue(viewModel.uiState.value.checkpoints.none { it.isValidated })
    }

    @Test
    fun testBlock4_VoiceAssistantAndPatrolReportSave() {
        val initialLogsCount = viewModel.uiState.value.patrolLogs.size

        // Invocación directa del asistente con comando "Redactar el informe"
        viewModel.triggerVoiceDictationAssistant("Redactar el informe del rondín norte")

        // El informe se redacta con estructura militarizada
        // Esperamos que el formato táctico contenga encabezado y secciones requeridas
        Thread.sleep(2200) // delay en coroutine del ViewModel
        val generatedText = viewModel.uiState.value.patrolReportText
        assertTrue(generatedText.isNotBlank())
        assertTrue(generatedText.contains("INFORME TÁCTICO DE RONDÍN"))
        assertTrue(generatedText.contains("MEDUSA ALFHA"))

        // Archivar reporte
        viewModel.savePatrolReport()
        assertEquals(initialLogsCount + 1, viewModel.uiState.value.patrolLogs.size)
        assertEquals("", viewModel.uiState.value.patrolReportText)
        assertTrue(viewModel.uiState.value.systemNotice?.contains("bitácora") == true)
    }
}

