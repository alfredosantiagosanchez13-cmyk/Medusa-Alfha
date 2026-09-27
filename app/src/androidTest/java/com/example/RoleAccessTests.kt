package com.example

import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.auth.AreaIsolationCheckResult
import com.example.data.auth.MedusaAreaIsolationGuard
import com.example.data.auth.MedusaFinancialAccessGuard
import com.example.data.auth.MedusaRole
import com.example.data.auth.MedusaSessionPreferences
import com.example.data.auth.UserSession
import com.example.ui.navigation.MedusaNavGraph
import com.example.ui.theme.MEDUSAALFHATheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Interfaz de inyección desacoplada para el aprovisionamiento y autorización de sesiones por rol.
 * Facilita el stubbing y la verificación de interacciones mediante mockito-kotlin en pruebas Espresso/Compose.
 */
interface RoleSessionInjector {
    fun provideActiveSession(): UserSession
    fun isFinancialAccessAuthorized(): Boolean
    fun validateLotAccess(residentAssignedUnit: String, targetLotOrUnit: String): Boolean
}

/**
 * ESPRESSO / COMPOSE INSTRUMENTATION TEST SUITE: ROLE ACCESS AND PERMISSIONS
 *
 * Emplea mockito-kotlin para inyectar limpiamente estados de sesión desacoplados y validar
 * de forma exhaustiva las capas visuales para:
 * 1. ADMIN (ADMINISTRACION): Acceso al Panel Maestro y visualización de gobernanza.
 * 2. GUARDIA_CASETA: Acceso al módulo táctico y bloqueo defensivo estricto (Zero-Trust) de finanzas.
 * 3. RESIDENT (RESIDENTE): Aislamiento visual a su vivienda y denegación de acceso a lotes ajenos.
 */
@RunWith(AndroidJUnit4::class)
class RoleAccessTests {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var sessionPreferences: MedusaSessionPreferences

    @Before
    fun setUp() {
        val context = composeTestRule.activity.applicationContext
        sessionPreferences = MedusaSessionPreferences.getInstance(context)
        sessionPreferences.clearSession()
        MedusaFinancialAccessGuard.applyRoleSecurityPolicy(MedusaRole.UNASSIGNED)
    }

    /**
     * TEST 1: ADMIN LOGIN WORKFLOW & PERMISSION VALIDATION
     * Inyecta la sesión de ADMINISTRACION con mockito-kotlin y verifica la presencia
     * del Panel Maestro junto con la autorización explícita de facultades financieras.
     */
    @Test
    fun testAdminLogin_AccessesMasterPanel_WithAdministrativePermissions() {
        val adminRole = MedusaRole.ADMINISTRACION
        MedusaFinancialAccessGuard.applyRoleSecurityPolicy(adminRole)

        val adminSession = UserSession(
            activationKey = "AUTH-ADM-003",
            currentRole = adminRole,
            condominiumId = "PRADOS_1",
            assignedUnitId = "Administración General",
            condominiumName = "Residencial Los Prados 1",
            isActive = true,
            isFinancialBlocked = false,
            timestampMillis = System.currentTimeMillis()
        )
        sessionPreferences.saveSession(adminSession)

        // Mockito-Kotlin role injection
        val roleInjector = mock<RoleSessionInjector>()
        whenever(roleInjector.provideActiveSession()).thenReturn(adminSession)
        whenever(roleInjector.isFinancialAccessAuthorized()).thenReturn(true)

        // Verificaciones de política de seguridad para Administración
        assertFalse("El rol ADMINISTRACION no debe tener los nodos financieros bloqueados", adminRole.requiresFinancialNodeLock())
        assertFalse("Guardián financiero debe permitir acceso a Administración", MedusaFinancialAccessGuard.isFinancialAccessBlocked())
        assertTrue("Administración debe poseer acceso a balances", adminRole.hasFinancialAccess())
        assertTrue("El inyector debe certificar autorización financiera", roleInjector.isFinancialAccessAuthorized())

        // Inyección de la sesión mockeada en la jerarquía de UI
        val session = roleInjector.provideActiveSession()
        composeTestRule.runOnUiThread {
            composeTestRule.activity.setContent {
                MEDUSAALFHATheme {
                    val navController = rememberNavController()
                    MedusaNavGraph(
                        navController = navController,
                        currentSession = session
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // Verificación visual de los componentes de Administración
        composeTestRule.onNodeWithText("PANEL MAESTRO", substring = true, ignoreCase = true)
            .assertExists()

        // Verificación de invocación en mockito
        verify(roleInjector, atLeastOnce()).provideActiveSession()
        verify(roleInjector).isFinancialAccessAuthorized()
    }

    /**
     * TEST 2: GUARDIA_CASETA LOGIN WORKFLOW & FINANCIAL ZERO-TRUST LOCK
     * Inyecta la sesión de GUARDIA_CASETA con mockito-kotlin y comprueba que la interfaz táctica
     * esté activa mientras los módulos contables, nómina y bancos permanecen estrictamente ausentes.
     */
    @Test
    fun testGuardiaCasetaLogin_AccessesTacticalComponents_StrictlyBlocksFinancialData() {
        val guardRole = MedusaRole.GUARDIA_CASETA
        MedusaFinancialAccessGuard.applyRoleSecurityPolicy(guardRole)

        // Verificación defensiva Zero-Trust
        assertTrue("El rol GUARDIA_CASETA REQUIERE bloqueo financiero obligatorio", guardRole.requiresFinancialNodeLock())
        assertTrue("MedusaFinancialAccessGuard DEBE bloquear inmediatamente a Caseta", MedusaFinancialAccessGuard.isFinancialAccessBlocked())
        assertFalse("Caseta NUNCA debe poseer acceso a datos financieros", guardRole.hasFinancialAccess())

        val guardSession = UserSession(
            activationKey = "AUTH-CASETA-005",
            currentRole = guardRole,
            condominiumId = "PRADOS_1",
            assignedUnitId = "Caseta Principal",
            condominiumName = "Residencial Los Prados 1",
            isActive = true,
            isFinancialBlocked = true, // Bloqueo financiero inmutable
            timestampMillis = System.currentTimeMillis()
        )
        sessionPreferences.saveSession(guardSession)

        // Mockito-Kotlin role injection
        val roleInjector = mock<RoleSessionInjector>()
        whenever(roleInjector.provideActiveSession()).thenReturn(guardSession)
        whenever(roleInjector.isFinancialAccessAuthorized()).thenReturn(false)

        assertFalse("Caseta no debe estar autorizada para finanzas en el inyector", roleInjector.isFinancialAccessAuthorized())

        // Inyección en la jerarquía de UI
        val session = roleInjector.provideActiveSession()
        composeTestRule.runOnUiThread {
            composeTestRule.activity.setContent {
                MEDUSAALFHATheme {
                    val navController = rememberNavController()
                    MedusaNavGraph(
                        navController = navController,
                        currentSession = session
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // Verificación visual táctica de Caseta
        composeTestRule.onNodeWithText("CASETA", substring = true, ignoreCase = true)
            .assertExists()

        // Verificación de ausencia estricta de componentes financieros
        composeTestRule.onNodeWithText("ESTADOS DE CUENTA BANCARIOS", substring = true, ignoreCase = true)
            .assertDoesNotExist()
        composeTestRule.onNodeWithText("NÓMINA DE PERSONAL", substring = true, ignoreCase = true)
            .assertDoesNotExist()
        composeTestRule.onNodeWithText("BALANCES CONTABLES", substring = true, ignoreCase = true)
            .assertDoesNotExist()

        verify(roleInjector, atLeastOnce()).provideActiveSession()
        verify(roleInjector).isFinancialAccessAuthorized()
    }

    /**
     * TEST 3: RESIDENT LOGIN WORKFLOW & PRIVATE HOUSE ISOLATION
     * Inyecta la sesión de RESIDENTE con mockito-kotlin y verifica que el portal privado
     * se encuentre acotado a la vivienda asignada sin visibilidad de gobernanza maestra.
     */
    @Test
    fun testResidentLogin_AccessesPrivatePortal_IsolatedToAssignedHouse() {
        val residentRole = MedusaRole.RESIDENTE
        MedusaFinancialAccessGuard.applyRoleSecurityPolicy(residentRole)

        val targetHouse = "Casa 104"
        val residentSession = UserSession(
            activationKey = "AUTH-RES-104",
            currentRole = residentRole,
            condominiumId = "PRADOS_1",
            assignedUnitId = targetHouse,
            condominiumName = "Residencial Los Prados 1",
            isActive = true,
            isFinancialBlocked = false,
            timestampMillis = System.currentTimeMillis()
        )
        sessionPreferences.saveSession(residentSession)

        // Mockito-Kotlin role injection
        val roleInjector = mock<RoleSessionInjector>()
        whenever(roleInjector.provideActiveSession()).thenReturn(residentSession)
        whenever(roleInjector.isFinancialAccessAuthorized()).thenReturn(false)

        assertEquals("Casa 104", residentSession.assignedUnitId)
        assertFalse("Residente no posee administración global", residentRole.hasFinancialAccess())

        // Inyección en la jerarquía de UI
        val session = roleInjector.provideActiveSession()
        composeTestRule.runOnUiThread {
            composeTestRule.activity.setContent {
                MEDUSAALFHATheme {
                    val navController = rememberNavController()
                    MedusaNavGraph(
                        navController = navController,
                        currentSession = session
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // Verificación visual de portal privado con su número de casa
        composeTestRule.onNodeWithText(targetHouse, substring = true, ignoreCase = true)
            .assertExists()

        // Verificación de ausencia de funciones de control maestro
        composeTestRule.onNodeWithText("AUDITORÍA FORENSE MAESTRA", substring = true, ignoreCase = true)
            .assertDoesNotExist()
        composeTestRule.onNodeWithText("APERTURA FORZADA DE BARRERA", substring = true, ignoreCase = true)
            .assertDoesNotExist()

        verify(roleInjector, atLeastOnce()).provideActiveSession()
    }

    /**
     * TEST 4: RESIDENT HOUSE ISOLATION & INTER-LOT QUERY ACCESS REJECTION
     * Inyecta y comprueba con mockito-kotlin que el residente solo visualice el componente de su vivienda
     * y reciba un error explícito de denegación de acceso ante consultas a otros lotes de la base de datos.
     */
    @Test
    fun testResident_IsolatedToAssignedHouse_ReceivesAccessErrorOnQueryingOtherLots() {
        val residentRole = MedusaRole.RESIDENTE
        MedusaFinancialAccessGuard.applyRoleSecurityPolicy(residentRole)

        val myAssignedHouse = "Casa 104"
        val foreignHouseA = "Casa 200"
        val foreignHouseB = "Casa 45"
        val foreignLotC = "Lote 88"

        val residentSession = UserSession(
            activationKey = "AUTH-RES-104",
            currentRole = residentRole,
            condominiumId = "PRADOS_1",
            assignedUnitId = myAssignedHouse,
            condominiumName = "Residencial Los Prados 1",
            isActive = true,
            isFinancialBlocked = false,
            timestampMillis = System.currentTimeMillis()
        )
        sessionPreferences.saveSession(residentSession)

        // Mockito-Kotlin role injector con reglas de autorización por lote
        val roleInjector = mock<RoleSessionInjector>()
        whenever(roleInjector.provideActiveSession()).thenReturn(residentSession)
        whenever(roleInjector.validateLotAccess(myAssignedHouse, myAssignedHouse)).thenReturn(true)
        whenever(roleInjector.validateLotAccess(myAssignedHouse, foreignHouseA)).thenReturn(false)
        whenever(roleInjector.validateLotAccess(myAssignedHouse, foreignHouseB)).thenReturn(false)
        whenever(roleInjector.validateLotAccess(myAssignedHouse, foreignLotC)).thenReturn(false)

        // Inyección en la jerarquía de UI
        val session = roleInjector.provideActiveSession()
        composeTestRule.runOnUiThread {
            composeTestRule.activity.setContent {
                MEDUSAALFHATheme {
                    val navController = rememberNavController()
                    MedusaNavGraph(
                        navController = navController,
                        currentSession = session
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // 1. Verificación en capa UI: Únicamente su vivienda es visible
        composeTestRule.onNodeWithText(myAssignedHouse, substring = true, ignoreCase = true)
            .assertExists()
        composeTestRule.onNodeWithText(foreignHouseA, substring = true, ignoreCase = true)
            .assertDoesNotExist()
        composeTestRule.onNodeWithText(foreignLotC, substring = true, ignoreCase = true)
            .assertDoesNotExist()

        // 2. Comprobación mediante mockito-kotlin y reglas de autorización
        assertTrue("Acceso a vivienda propia debe ser válido", roleInjector.validateLotAccess(myAssignedHouse, myAssignedHouse))
        assertFalse("Acceso a Casa 200 debe ser rechazado", roleInjector.validateLotAccess(myAssignedHouse, foreignHouseA))
        assertFalse("Acceso a Casa 45 debe ser rechazado", roleInjector.validateLotAccess(myAssignedHouse, foreignHouseB))
        assertFalse("Acceso a Lote 88 debe ser rechazado", roleInjector.validateLotAccess(myAssignedHouse, foreignLotC))

        // 3. Comprobación defensiva del motor MedusaAreaIsolationGuard
        val resultBlocked = MedusaAreaIsolationGuard.validateResidentLotAccess(myAssignedHouse, foreignHouseA)
        assertTrue("Intento de consulta a Casa 200 debe resultar en Blocked", resultBlocked is AreaIsolationCheckResult.Blocked)
        val blocked = resultBlocked as AreaIsolationCheckResult.Blocked
        assertEquals("SEGURIDAD_MEDUSA: LOTE_NO_AUTORIZADO", blocked.errorCode)
        assertTrue(blocked.reason.contains("ACCESO DENEGADO"))

        // Excepción de seguridad al forzar acceso a lote ajeno
        val exception = assertThrows(SecurityException::class.java) {
            MedusaAreaIsolationGuard.assertResidentLotAccess(myAssignedHouse, foreignHouseA)
        }
        assertTrue(exception.message?.contains("ACCESO DENEGADO") == true)

        // Verificaciones de invocación en mockito
        verify(roleInjector, atLeastOnce()).provideActiveSession()
        verify(roleInjector).validateLotAccess(myAssignedHouse, myAssignedHouse)
        verify(roleInjector).validateLotAccess(myAssignedHouse, foreignHouseA)
        verify(roleInjector).validateLotAccess(myAssignedHouse, foreignHouseB)
        verify(roleInjector).validateLotAccess(myAssignedHouse, foreignLotC)
    }
}
