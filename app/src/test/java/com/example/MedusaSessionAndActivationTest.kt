package com.example

import com.example.data.auth.ActivationKey
import com.example.data.auth.MedusaFinancialAccessGuard
import com.example.data.auth.MedusaRole
import com.example.ui.viewmodel.ActivationErrorType
import com.example.ui.viewmodel.ActivationUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias de arquitectura MVVM para el Gestor de Sesiones y Roles de MEDUSA ALFHA.
 * Valida RBAC (MedusaRole), la entidad Firestore ActivationKey y la política de bloqueo nativo financiero.
 */
class MedusaSessionAndActivationTest {

    @Test
    fun testMedusaRoleEnum_allRequiredStatesExist() {
        val roles = MedusaRole.entries.map { it.name }
        assertTrue("ADMINISTRACION debe existir", roles.contains("ADMINISTRACION"))
        assertTrue("GUARDIA_CASETA debe existir", roles.contains("GUARDIA_CASETA"))
        assertTrue("RESIDENTE debe existir", roles.contains("RESIDENTE"))
        assertTrue("UNASSIGNED debe existir", roles.contains("UNASSIGNED"))
        assertEquals(4, MedusaRole.entries.size)
    }

    @Test
    fun testMedusaRole_parsingAndNormalization() {
        assertEquals(MedusaRole.ADMINISTRACION, MedusaRole.fromString("ADMINISTRACION"))
        assertEquals(MedusaRole.ADMINISTRACION, MedusaRole.fromString("admin"))
        assertEquals(MedusaRole.GUARDIA_CASETA, MedusaRole.fromString("GUARDIA_CASETA"))
        assertEquals(MedusaRole.GUARDIA_CASETA, MedusaRole.fromString("guardia"))
        assertEquals(MedusaRole.GUARDIA_CASETA, MedusaRole.fromString("caseta"))
        assertEquals(MedusaRole.RESIDENTE, MedusaRole.fromString("RESIDENTE"))
        assertEquals(MedusaRole.RESIDENTE, MedusaRole.fromString("residente"))
        assertEquals(MedusaRole.UNASSIGNED, MedusaRole.fromString(null))
        assertEquals(MedusaRole.UNASSIGNED, MedusaRole.fromString(""))
        assertEquals(MedusaRole.UNASSIGNED, MedusaRole.fromString("ROL_INEXISTENTE_XYZ"))
    }

    @Test
    fun testActivationKey_entityFieldsAndProperties() {
        val key = ActivationKey(
            keyId = "KEY-ALFHA-2026-X9",
            role = "GUARDIA_CASETA",
            condominiumId = "CONDO_PARAISO_01",
            assignedUnit = null,
            isActive = true
        )

        assertEquals("KEY-ALFHA-2026-X9", key.keyId)
        assertEquals("GUARDIA_CASETA", key.role)
        assertEquals("CONDO_PARAISO_01", key.condominiumId)
        assertNull("assignedUnit es opcional para guardias", key.assignedUnit)
        assertTrue(key.isActive)
        assertEquals(MedusaRole.GUARDIA_CASETA, key.medusaRole)
        assertFalse("No debe estar expirada sin timestamp de expiración", key.isExpired())

        val residentKey = ActivationKey(
            keyId = "KEY-RES-004",
            role = "RESIDENTE",
            condominiumId = "CONDO_LOS_PRADOS_2",
            assignedUnit = "Casa 42 - Manzana B",
            isActive = true
        )
        assertEquals(MedusaRole.RESIDENTE, residentKey.medusaRole)
        assertEquals("Casa 42 - Manzana B", residentKey.assignedUnit)
        assertNotNull(residentKey.assignedUnit)
    }

    @Test
    fun testActivationKey_expirationHandling() {
        val pastTime = System.currentTimeMillis() - 100000L
        val expiredKey = ActivationKey(
            keyId = "KEY-EXP",
            role = "RESIDENTE",
            condominiumId = "CONDO_01",
            isActive = true,
            expirationTimestampMillis = pastTime
        )
        assertTrue(expiredKey.isExpired())

        val futureTime = System.currentTimeMillis() + 100000L
        val activeKey = ActivationKey(
            keyId = "KEY-ACTIVE",
            role = "RESIDENTE",
            condominiumId = "CONDO_01",
            isActive = true,
            expirationTimestampMillis = futureTime
        )
        assertFalse(activeKey.isExpired())
    }

    @Test
    fun testFinancialAccessGuard_guardiaCasetaNativeLock() {
        // Al asignar GUARDIA_CASETA, el ecosistema móvil debe bloquear nativamente nodos financieros
        MedusaFinancialAccessGuard.applyRoleSecurityPolicy(MedusaRole.GUARDIA_CASETA)

        assertTrue(
            "Guardia de caseta debe tener el acceso financiero bloqueado nativamente",
            MedusaFinancialAccessGuard.isFinancialAccessBlocked()
        )
        assertEquals(MedusaRole.GUARDIA_CASETA, MedusaFinancialAccessGuard.getActiveRole())

        assertThrows(SecurityException::class.java) {
            MedusaFinancialAccessGuard.assertFinancialAccessAllowed()
        }
    }

    @Test
    fun testFinancialAccessGuard_administracionPrivileges() {
        // ADMINISTRACION debe tener acceso habilitado
        MedusaFinancialAccessGuard.applyRoleSecurityPolicy(MedusaRole.ADMINISTRACION)

        assertFalse(
            "Administración no debe tener bloqueo financiero",
            MedusaFinancialAccessGuard.isFinancialAccessBlocked()
        )
        assertEquals(MedusaRole.ADMINISTRACION, MedusaFinancialAccessGuard.getActiveRole())

        // No debe lanzar excepción
        MedusaFinancialAccessGuard.assertFinancialAccessAllowed()
    }

    @Test
    fun testFinancialAccessGuard_residenteAndUnassignedProtected() {
        MedusaFinancialAccessGuard.applyRoleSecurityPolicy(MedusaRole.RESIDENTE)
        assertTrue(MedusaFinancialAccessGuard.isFinancialAccessBlocked())

        MedusaFinancialAccessGuard.applyRoleSecurityPolicy(MedusaRole.UNASSIGNED)
        assertTrue(MedusaFinancialAccessGuard.isFinancialAccessBlocked())
    }

    @Test
    fun testActivationUiState_structure() {
        val idleState: ActivationUiState = ActivationUiState.Idle
        assertEquals(ActivationUiState.Idle, idleState)

        val loadingState: ActivationUiState = ActivationUiState.Loading
        assertEquals(ActivationUiState.Loading, loadingState)

        val successState = ActivationUiState.Success(
            activationKey = ActivationKey("KEY-1", "GUARDIA_CASETA", "CONDO-A", null, true),
            role = MedusaRole.GUARDIA_CASETA,
            message = "Activado exitosamente",
            isFinancialBlocked = true
        )
        assertEquals(MedusaRole.GUARDIA_CASETA, successState.role)
        assertTrue(successState.isFinancialBlocked)

        val errorState = ActivationUiState.Error(
            errorMessage = "La llave no existe",
            errorType = ActivationErrorType.KEY_NOT_FOUND
        )
        assertEquals(ActivationErrorType.KEY_NOT_FOUND, errorState.errorType)
        assertEquals("La llave no existe", errorState.errorMessage)
    }

    @Test
    fun testTacticalGuardAction_exactlyEightButtonsWithCorrectProperties() {
        val actions = com.example.ui.screens.TacticalGuardAction.entries.toList()
        assertEquals("Deben existir exactamente 8 botones operativos para caseta", 8, actions.size)

        val titles = actions.map { it.title }
        assertTrue(titles.contains("Asistencia"))
        assertTrue(titles.contains("Accesos"))
        assertTrue(titles.contains("Ficha de Residente"))
        assertTrue(titles.contains("Paquetería"))
        assertTrue(titles.contains("Placas"))
        assertTrue(titles.contains("Incidentes"))
        assertTrue(titles.contains("Rondines"))
        assertTrue(titles.contains("Bitácora"))

        // Verificar que cada acción tenga tags y subtítulos definidos
        actions.forEach { action ->
            assertTrue(action.tag.startsWith("tactical_action_"))
            assertTrue(action.subtitle.isNotBlank())
        }
    }

    @Test
    fun testCompactActivityItem_displayFormatting() {
        val item = com.example.ui.screens.CompactActivityItem(
            id = "FOLIO-101",
            timestampFormatted = "08:32",
            actionType = "Visita",
            statusText = "denied",
            destination = "Casa 01",
            isAccessDenied = true
        )

        assertEquals("08:32 - Acceso denied - Casa 01", item.displayLine)
        assertTrue(item.isAccessDenied)
        assertFalse(item.isCheckedIn)

        val itemCheckedIn = com.example.ui.screens.CompactActivityItem(
            id = "FOLIO-102",
            timestampFormatted = "08:30",
            actionType = "Residente",
            statusText = "checked_in",
            destination = "Casa 104",
            isCheckedIn = true
        )

        assertEquals("08:30 - Acceso checked_in - Casa 104", itemCheckedIn.displayLine)
        assertTrue(itemCheckedIn.isCheckedIn)
        assertFalse(itemCheckedIn.isAccessDenied)
    }
}
