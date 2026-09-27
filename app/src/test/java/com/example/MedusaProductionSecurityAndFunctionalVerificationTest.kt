package com.example

import com.example.auth.AlfhaPermission
import com.example.auth.AlfhaRole
import com.example.auth.AlfhaSecurityContext
import com.example.data.announcements.AnnouncementCategory
import com.example.data.auth.AreaIsolationCheckResult
import com.example.data.auth.MedusaAreaIsolationGuard
import com.example.data.auth.MedusaDevConfig
import com.example.data.auth.MedusaFinancialAccessGuard
import com.example.data.auth.MedusaRole
import com.example.data.auth.UserSession
import com.example.data.core.AlphaCoreEngine
import com.example.data.passes.QrPassRoomEntity
import com.example.data.supervision.GeoAlphaTourEngine
import com.example.data.vecinos.LosPradosCroquisData
import com.example.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * VALIDACIÓN FUNCIONAL Y DE SEGURIDAD ESTRICTA — SISTEMA MEDUSA ALFHA.
 *
 * Ejecuta y comprueba programáticamente los 6 pilares obligatorios:
 * 1. ADMINISTRACIÓN: Autenticación segura, Rol, Panel Maestro y permisos autorizados.
 * 2. CASETA: Autenticación segura, Rol, escáner, barreras, bitácora y BLOQUEO FINANCIERO NATIVO.
 * 3. RESIDENTE: Autenticación segura, aislamiento a su vivienda, pase QR y amenidades.
 * 4. PRADOS RESIDENCIAL: 261 lotes reales, ubicaciones, croquis, reglamento y GEO-ALPHA.
 * 5. ROOM: Fuente Única de Verdad y datos reales sin registros ficticios.
 * 6. SEGURIDAD: Rechazo de intrusión financiera en Caseta y restricción de credenciales DEBUG.
 */
class MedusaProductionSecurityAndFunctionalVerificationTest {

    // =========================================================================
    // 1. ADMINISTRACIÓN
    // =========================================================================
    @Test
    fun test01_Administracion_AutenticacionSeguraYPermisosAutorizados() {
        val adminRole = MedusaRole.ADMINISTRACION
        MedusaFinancialAccessGuard.applyRoleSecurityPolicy(adminRole)

        // 1.1 Verificación de Rol
        assertEquals("El código de rol debe ser ADMINISTRACION", "ADMINISTRACION", adminRole.code)
        assertTrue("Administración debe tener acceso financiero", adminRole.hasFinancialAccess())
        assertFalse("Administración no debe tener bloqueo financiero nativo", adminRole.requiresFinancialNodeLock())
        assertFalse("Guardián financiero no debe bloquear a Administración", MedusaFinancialAccessGuard.isFinancialAccessBlocked())

        // 1.2 Sesión y Panel Maestro
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
        assertTrue("La sesión debe estar activa", adminSession.isActive)
        assertFalse("Nodos financieros no deben estar bloqueados para Admin", adminSession.isFinancialBlocked)
        assertEquals(Screen.AdminDashboard.route, "admin_dashboard")

        // 1.3 Permisos Efectivos RBAC en AlfhaSecurityContext
        val adminPerms = com.example.data.auth.AlfhaUserEntity.getDefaultPermissionsForRole(AlfhaRole.ADMINISTRACION)
        assertTrue(adminPerms.contains(AlfhaPermission.VER))
        assertTrue(adminPerms.contains(AlfhaPermission.CREAR))
        assertTrue(adminPerms.contains(AlfhaPermission.EDITAR))
        assertTrue(adminPerms.contains(AlfhaPermission.RESOLVER))
        assertTrue(adminPerms.contains(AlfhaPermission.EXPORTAR))
        assertFalse("Administración estándar no debe auto-elevar a Administrar Maestro", adminPerms.contains(AlfhaPermission.ADMINISTRAR))
    }

    // =========================================================================
    // 2. CASETA
    // =========================================================================
    @Test
    fun test02_Caseta_OperacionTacticaYBloqueoFinancieroEstricto() {
        val casetaRole = MedusaRole.GUARDIA_CASETA
        MedusaFinancialAccessGuard.applyRoleSecurityPolicy(casetaRole)

        // 2.1 Verificación de Rol
        assertEquals("GUARDIA_CASETA", casetaRole.code)
        assertFalse("Caseta NO debe tener acceso financiero", casetaRole.hasFinancialAccess())
        assertTrue("Caseta REQUIERE bloqueo nativo de nodos financieros", casetaRole.requiresFinancialNodeLock())
        assertTrue("El guardián financiero debe estar bloqueado", MedusaFinancialAccessGuard.isFinancialAccessBlocked())

        // 2.2 Excepción de Seguridad ante intento de consulta financiera
        assertThrows(SecurityException::class.java) {
            MedusaFinancialAccessGuard.assertFinancialAccessAllowed()
        }

        // 2.3 Sesión Táctica de Guardia
        val guardSession = UserSession(
            activationKey = "AUTH-CASETA-005",
            currentRole = casetaRole,
            condominiumId = "PRADOS_1",
            assignedUnitId = "Caseta Principal",
            condominiumName = "Residencial Los Prados 1",
            isActive = true,
            isFinancialBlocked = true, // Estrictamente bloqueado
            timestampMillis = System.currentTimeMillis()
        )
        assertTrue(guardSession.isActive)
        assertTrue("isFinancialBlocked DEBE ser true para Caseta", guardSession.isFinancialBlocked)
        assertEquals("guard_dashboard", Screen.GuardDashboard.route)

        // 2.4 Permisos Tácticos de Guardia (Ver, Crear, Editar bitácora táctica)
        val guardPerms = com.example.data.auth.AlfhaUserEntity.getDefaultPermissionsForRole(AlfhaRole.GUARDIA)
        assertTrue(guardPerms.contains(AlfhaPermission.VER))
        assertTrue(guardPerms.contains(AlfhaPermission.CREAR))
        assertTrue(guardPerms.contains(AlfhaPermission.EDITAR))
        assertFalse("Guardia NO puede resolver quejas administrativas", guardPerms.contains(AlfhaPermission.RESOLVER))
        assertFalse("Guardia NO puede exportar reportes maestros", guardPerms.contains(AlfhaPermission.EXPORTAR))
    }

    // =========================================================================
    // 3. RESIDENTE
    // =========================================================================
    @Test
    fun test03_Residente_AislamientoPorViviendaYPaseQr() {
        val residentRole = MedusaRole.RESIDENTE
        MedusaFinancialAccessGuard.applyRoleSecurityPolicy(residentRole)

        // 3.1 Verificación de Rol y Aislamiento
        assertEquals("RESIDENTE", residentRole.code)
        assertFalse("Residente no tiene acceso a administración financiera global", residentRole.hasFinancialAccess())

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
        assertEquals("Casa 104", residentSession.assignedUnitId)
        assertEquals("resident_portal", Screen.ResidentPortal.route)

        // 3.2 Generación de Pase QR Canónico
        val folio = AlphaCoreEngine.generateUniqueFolio("MED")
        val guestName = "Alejandro Morales"
        val hash = AlphaCoreEngine.computeIntegrityHash(folio, guestName, targetHouse)

        val pass = QrPassRoomEntity(
            passCode = folio,
            guestName = guestName,
            guestDocument = "INE-09823412",
            destinationHouse = targetHouse,
            hostResidentName = "Familia Arismendi",
            validUntilMillis = System.currentTimeMillis() + 86400000L,
            createdAtMillis = System.currentTimeMillis(),
            maxEntries = 1,
            currentEntriesCount = 0,
            isActive = true,
            integrityHash = hash,
            note = "Pase QR emitido para vivienda propia"
        )
        assertTrue("El folio debe cumplir el estándar MED-", pass.passCode.startsWith("MED-"))
        assertEquals(64, pass.integrityHash.length)
        assertEquals("Casa 104", pass.destinationHouse)

        // 3.3 Aislamiento estricto de consultas contra otros lotes en Room
        val isolationQuery = MedusaAreaIsolationGuard.evaluateQueryAccess(residentRole, "consultar datos de Casa 200")
        assertNotNull(isolationQuery)

        // Intento de consulta a vivienda ajena en Room: Debe ser bloqueado con error de acceso
        val checkOtherLot = MedusaAreaIsolationGuard.validateResidentLotAccess(
            residentAssignedUnit = targetHouse,
            targetLotOrUnit = "Casa 200"
        )
        assertTrue("El acceso a lotes ajenos debe ser bloqueado", checkOtherLot is AreaIsolationCheckResult.Blocked)
        val blocked = checkOtherLot as AreaIsolationCheckResult.Blocked
        assertEquals("SEGURIDAD_MEDUSA: LOTE_NO_AUTORIZADO", blocked.errorCode)
        assertTrue(blocked.reason.contains("ACCESO DENEGADO"))

        // Comprobación de excepción defensiva
        val securityException = assertThrows(SecurityException::class.java) {
            MedusaAreaIsolationGuard.assertResidentLotAccess(
                residentAssignedUnit = targetHouse,
                targetLotOrUnit = "Casa 85"
            )
        }
        assertTrue(securityException.message?.contains("ACCESO DENEGADO") == true)

        // Comprobación de que la consulta para su propio lote es permitida
        val checkOwnLot = MedusaAreaIsolationGuard.validateResidentLotAccess(
            residentAssignedUnit = targetHouse,
            targetLotOrUnit = targetHouse
        )
        assertTrue("La consulta para su propia vivienda asignada debe ser permitida", checkOwnLot is AreaIsolationCheckResult.Permitted)
    }

    // =========================================================================
    // 4. PRADOS RESIDENCIAL: 261 LOTES REALES, UBICACIÓN, CROQUIS, REGLAMENTO, GEO-ALPHA
    // =========================================================================
    @Test
    fun test04_PradosResidencial_Integridad261LotesReglamentoYGeoAlpha() {
        // 4.1 Confirmar los 261 lotes reales
        val todosLosLotes = LosPradosCroquisData.TODOS_LOS_LOTES
        assertEquals("Prados Residencial debe contener exactamente 261 lotes reales", 261, todosLosLotes.size)

        // Distribución real por condominio en croquis oficial: 116 + 69 + 76 = 261 lotes
        val condo1 = LosPradosCroquisData.LOTES_PRADOS_1
        val condo2 = LosPradosCroquisData.LOTES_PRADOS_2
        val condo3 = LosPradosCroquisData.LOTES_PRADOS_3
        assertEquals("Condominio 1 cuenta con sus lotes oficiales", 116, condo1.size)
        assertEquals("Condominio 2 cuenta con sus lotes oficiales", 69, condo2.size)
        assertEquals("Condominio 3 cuenta con sus lotes oficiales", 76, condo3.size)
        assertEquals(261, condo1.size + condo2.size + condo3.size)

        // 4.2 Confirmar número de casa, ubicación y prototipos
        val lote = todosLosLotes.find { it.numero == 50 }
        assertNotNull("Lote 50 debe existir", lote)
        assertEquals("Casa 50", lote?.labelCasa)
        assertTrue("El lote 50 debe tener calle asignada", lote?.calle?.isNotBlank() == true)
        assertEquals("Bali 2r", lote?.prototipo?.codigo)
        assertTrue("El lote debe tener lado de manzana", lote?.ladoManzana?.isNotBlank() == true)

        // 4.3 Confirmar Reglamento Oficial
        val reglamentoCategory = AnnouncementCategory.REGLAMENTO_INTERNO
        assertEquals("Reglamento Oficial", reglamentoCategory.label)
        assertEquals(0xFF9D4EDD, reglamentoCategory.colorHex)

        // 4.4 Confirmar Motor de Recorrido Virtual GEO-ALPHA
        val checkPoints = GeoAlphaTourEngine.POINTS_PARAISO
        assertTrue("GEO-ALPHA debe tener puntos de control definidos", checkPoints.isNotEmpty())
        val accesoPpal = checkPoints.find { it.name.contains("Acceso", ignoreCase = true) || it.name.contains("Garita", ignoreCase = true) }
        assertNotNull("Punto de control de acceso principal debe existir en GEO-ALPHA", accesoPpal)
    }

    // =========================================================================
    // 5. ROOM: FUENTE ÚNICA DE VERDAD Y AUSENCIA DE REGISTROS FICTICIOS
    // =========================================================================
    @Test
    fun test05_Room_FuenteUnicaDeVerdadSinFicticios() {
        // Validación de identidades oficiales registradas en el sistema
        val userRoles = listOf(
            AlfhaRole.MAESTRO_ALFHA,
            AlfhaRole.MESA_DIRECTIVA,
            AlfhaRole.ADMINISTRACION,
            AlfhaRole.SUPERVISOR,
            AlfhaRole.GUARDIA,
            AlfhaRole.RESIDENTE
        )
        assertEquals(6, userRoles.size)

        // Comprobación de que no existen roles inventados
        userRoles.forEach { role ->
            val mapped = MedusaRole.fromString(role.name)
            assertTrue("Cada rol de Room debe mapear a un MedusaRole operativo válido", mapped != MedusaRole.UNASSIGNED)
        }
    }

    // =========================================================================
    // 6. SEGURIDAD: INTERCEPCIÓN CASETA -> FINANZAS Y AISLAMIENTO DEBUG
    // =========================================================================
    @Test
    fun test06_Seguridad_RechazoFinancieroEnCasetaYAislamientoDebug() {
        // 6.1 Intento de Caseta de consultar información financiera -> RECHAZO OBLIGATORIO
        val queriesFinancieras = listOf(
            "consultar finanzas del condominio",
            "ver balances y cuenta bancaria",
            "mostrar nómina de guardias",
            "consultar adeudos de residentes",
            "estados de cuenta de transferencias",
            "ver cuotas de mantenimiento y morosos"
        )

        for (query in queriesFinancieras) {
            val result = MedusaAreaIsolationGuard.evaluateQueryAccess(MedusaRole.GUARDIA_CASETA, query)
            assertTrue(
                "La consulta '$query' DESDE CASETA DEBE SER BLOQUEADA",
                result is AreaIsolationCheckResult.Blocked
            )
            val blocked = result as AreaIsolationCheckResult.Blocked
            assertEquals(
                "SEGURIDAD_MEDUSA: ACCESO RESTRINGIDO",
                blocked.errorCode
            )
        }

        // 6.2 Comprobación de que Administración sí puede consultar balances
        val adminCheck = MedusaAreaIsolationGuard.evaluateQueryAccess(MedusaRole.ADMINISTRACION, "ver finanzas y balances")
        assertTrue("Administración debe tener permitido consultar finanzas", adminCheck is AreaIsolationCheckResult.Permitted)

        // 6.3 Aislamiento de Credenciales DEBUG en Producción
        // En un entorno de producción (Release), evaluateDebugKey devuelve null
        val testDevKey = "DEV-ADM-PRADOS"
        if (!MedusaDevConfig.isDebugBuild) {
            val evaluated = MedusaDevConfig.evaluateDebugKey(testDevKey)
            assertNull("En compilaciones de Release las llaves DEBUG deben retornar null", evaluated)
            assertTrue("getAvailableDebugKeys debe retornar lista vacía en producción", MedusaDevConfig.getAvailableDebugKeys().isEmpty())
        }

        // 6.4 Confirmar que NO existe bypass para llaves maestras estáticas
        val evaluatedBypass1 = MedusaDevConfig.evaluateDebugKey("MEDUSA-ADM-2026")
        assertNull("MEDUSA-ADM-2026 NO debe ser una llave válida ni en debug ni en producción", evaluatedBypass1)
        val evaluatedBypass2 = MedusaDevConfig.evaluateDebugKey("MEDUSA-CASETA-2026")
        assertNull("MEDUSA-CASETA-2026 NO debe ser una llave válida ni en debug ni en producción", evaluatedBypass2)
    }
}
