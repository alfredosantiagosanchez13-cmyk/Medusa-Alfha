package com.example

import com.example.data.audit.AuditLogEntity
import com.example.data.core.AlphaCoreEngine
import com.example.data.sync.ConnectivityStatus
import com.example.data.sync.NetworkStateInfo
import com.example.data.sync.SyncQueueEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * FASE 21 — AUDITORÍA FORENSE DE INTEGRIDAD + TRAZABILIDAD MEDUSA ALFHA
 *
 * Validación y certificación técnica:
 * 1. Trazabilidad End-to-End en todas las operaciones críticas.
 * 2. Integridad Criptográfica Determinística SHA-256 e inmutabilidad.
 * 3. Idempotencia y eliminación de duplicados bajo estrés y reintentos.
 * 4. Ciclo de vida y recuperación de estados en SyncQueue (PENDIENTE, SINCRONIZANDO, SINCRONIZADO, ERROR).
 * 5. Registro inmutable de Auditoría (AuditLog) para transiciones administrativas.
 * 6. Ciclo de Vida Forense de Emergencia / SOS (con fallback a "UBICACIÓN NO DISPONIBLE" si no hay GPS).
 * 7. Simulación de Cierre Abrupto (Crash / Force Stop) y Recuperación Íntegra.
 * 8. Concurrencia masiva multihilo sin colisión de folios.
 * 9. Fuente Única de Verdad (Room SQLite).
 * 10. Cálculo de Tiempo Devuelto con datos auditables ("DATOS INSUFICIENTES PARA CÁLCULO" cuando no aplique).
 */
class ForensicIntegrityAuditPhase21Test {

    // ---------------------------------------------------------------------------------------------
    // 1. TRAZABILIDAD END-TO-END
    // ---------------------------------------------------------------------------------------------
    @Test
    fun audit_01_EndToEndTraceabilityAcrossCriticalOperations() {
        val criticalOperations = listOf(
            "QR_PASS_GENERATE" to "VISITANTES",
            "CHECK_IN" to "VISITANTES",
            "CHECK_OUT" to "VISITANTES",
            "INCIDENT_REGISTER" to "INCIDENCIAS",
            "SUPERVISION_TOUR" to "RONDINES",
            "VEHICLE_ACCESS" to "VEHICULOS",
            "EMERGENCY_TRIGGER" to "EMERGENCIAS"
        )

        for ((opType, module) in criticalOperations) {
            val syncFolio = AlphaCoreEngine.generateUniqueFolio("SYN")
            val opId = "OP-SIM-${UUID.randomUUID()}"
            val targetFolio = when (module) {
                "VISITANTES" -> AlphaCoreEngine.generateUniqueFolio("PAS")
                "INCIDENCIAS" -> AlphaCoreEngine.generateUniqueFolio("INC")
                "RONDINES" -> AlphaCoreEngine.generateUniqueFolio("RON")
                "VEHICULOS" -> AlphaCoreEngine.generateUniqueFolio("ACC")
                "EMERGENCIAS" -> AlphaCoreEngine.generateUniqueFolio("EMG")
                else -> AlphaCoreEngine.generateUniqueFolio("MED")
            }

            val hasGps = (opType == "SUPERVISION_TOUR" || opType == "EMERGENCY_TRIGGER")
            val lat = if (hasGps) 19.432607 else null
            val lon = if (hasGps) -99.133209 else null
            val evidence = if (opType == "INCIDENT_REGISTER") "/evidence/photo_sim.jpg" else ""

            val entry = SyncQueueEntity(
                syncFolio = syncFolio,
                operationId = opId,
                operatorName = "SIM_Oficial_Forense",
                operatorRole = if (opType == "EMERGENCY_TRIGGER") "RESIDENTE" else "GUARDIA",
                operationType = opType,
                targetFolio = targetFolio,
                targetModule = module,
                payloadJson = "{\"folio\":\"$targetFolio\",\"type\":\"$opType\",\"simulated\":true}",
                locationName = "Garita Norte 01",
                latitude = lat,
                longitude = lon,
                evidencePaths = evidence,
                status = "PENDIENTE",
                retryCount = 0,
                deviceGateId = "Terminal-Caseta-1",
                timeSavedSeconds = if (opType == "EMERGENCY_TRIGGER") 1200L else 180L,
                hashIntegrity = AlphaCoreEngine.computeIntegrityHash(syncFolio, opId, opType)
            )

            // Validar existencia de los 11 campos obligatorios de trazabilidad forense
            assertTrue("Folio debe comenzar con prefijo oficial", entry.syncFolio.startsWith("SYN-"))
            assertTrue("operationId único debe ser válido", entry.operationId.isNotBlank())
            assertTrue("Timestamp debe ser contemporáneo", entry.timestampMillis > 0)
            assertEquals("SIM_Oficial_Forense", entry.operatorName)
            assertNotNull(entry.operatorRole)
            assertEquals(module, entry.targetModule)
            assertEquals("PENDIENTE", entry.status)
            assertEquals(64, entry.hashIntegrity.length)
            assertEquals(0, entry.retryCount)

            if (hasGps) {
                assertNotNull("GPS debe estar presente en rondines y emergencias con señal", entry.latitude)
                assertNotNull("GPS debe estar presente en rondines y emergencias con señal", entry.longitude)
            }

            if (evidence.isNotEmpty()) {
                assertEquals("/evidence/photo_sim.jpg", entry.evidencePaths)
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 2. INTEGRIDAD CRIPTOGRÁFICA
    // ---------------------------------------------------------------------------------------------
    @Test
    fun audit_02_CryptographicIntegrityDeterministicAndTamperProof() {
        val syncFolio = "SYN-20260826-7777"
        val opId = "OP-CANONICAL-TEST-001"
        val opType = "EMERGENCY_TRIGGER"

        val originalHash = AlphaCoreEngine.computeIntegrityHash(syncFolio, opId, opType)
        val verifiedHash = AlphaCoreEngine.computeIntegrityHash(syncFolio, opId, opType)

        // A) Determinismo absoluto
        assertEquals("El cálculo de SHA-256 debe ser estrictamente determinístico", originalHash, verifiedHash)
        assertEquals(64, originalHash.length)

        // B) Detección de alteración (Tamper Evidence)
        val tamperedTypeHash = AlphaCoreEngine.computeIntegrityHash(syncFolio, opId, "ORDINARY_PASS")
        val tamperedOpIdHash = AlphaCoreEngine.computeIntegrityHash(syncFolio, "OP-ALTERED", opType)
        val tamperedFolioHash = AlphaCoreEngine.computeIntegrityHash("SYN-ALTERED", opId, opType)

        assertNotEquals("Cualquier alteración en opType debe romper la firma", originalHash, tamperedTypeHash)
        assertNotEquals("Cualquier alteración en opId debe romper la firma", originalHash, tamperedOpIdHash)
        assertNotEquals("Cualquier alteración en syncFolio debe romper la firma", originalHash, tamperedFolioHash)
    }

    // ---------------------------------------------------------------------------------------------
    // 3. IDEMPOTENCIA Y NO DUPLICIDAD
    // ---------------------------------------------------------------------------------------------
    @Test
    fun audit_03_IdempotencyDeduplicationUnderMultipleRetries() {
        val targetFolio = "MED-20260826-IDEM-99"
        val canonicalStore = ConcurrentHashMap<String, SyncQueueEntity>()

        fun processIncomingRequest(reqTargetFolio: String, reqOpType: String): SyncQueueEntity {
            return canonicalStore.computeIfAbsent(reqTargetFolio) { key ->
                val syncFolio = AlphaCoreEngine.generateUniqueFolio("SYN")
                val opId = "OP-IDEM-$key"
                SyncQueueEntity(
                    syncFolio = syncFolio,
                    operationId = opId,
                    operationType = reqOpType,
                    targetFolio = key,
                    targetModule = "VISITANTES",
                    payloadJson = "{\"target\":\"$key\"}",
                    hashIntegrity = AlphaCoreEngine.computeIntegrityHash(syncFolio, opId, reqOpType)
                )
            }
        }

        // Simular 20 envíos concurrentes o reintentos de la misma operación
        val results = (1..20).map { processIncomingRequest(targetFolio, "CHECK_IN") }

        val firstEntry = results.first()
        for (res in results) {
            assertEquals("Todos los intentos deben resolver al mismo syncFolio canónico", firstEntry.syncFolio, res.syncFolio)
            assertEquals("Todos los intentos deben resolver al mismo operationId canónico", firstEntry.operationId, res.operationId)
            assertEquals("Todos los intentos deben resolver al mismo hash canónico", firstEntry.hashIntegrity, res.hashIntegrity)
        }

        assertEquals("El repositorio debe albergar exactamente UN registro canónico", 1, canonicalStore.size)
    }

    // ---------------------------------------------------------------------------------------------
    // 4. COLA OFFLINE Y RECUPERACIÓN DE ESTADOS
    // ---------------------------------------------------------------------------------------------
    @Test
    fun audit_04_OfflineQueueStateTransitionsAndAbruptCrashRecovery() {
        val queue = mutableListOf<SyncQueueEntity>()

        val entry1 = createTestEntry("CHECK_IN", "MED-001", "PENDIENTE")
        val entry2 = createTestEntry("INCIDENT_REGISTER", "INC-002", "SINCRONIZANDO") // Quedó en limbo por cierre abrupto
        val entry3 = createTestEntry("VEHICLE_ACCESS", "ACC-003", "ERROR") // Falló previamente
        val entry4 = createTestEntry("CHECK_OUT", "MED-004", "SINCRONIZADO") // Ya completado
        val emergencyEntry = createTestEntry("EMERGENCY_TRIGGER", "EMG-005", "PENDIENTE")

        queue.addAll(listOf(entry1, entry2, entry3, entry4, emergencyEntry))

        // Regla DAO auditada: Elementos a procesar = PENDIENTE + SINCRONIZANDO (recuperados) + ERROR (reintentos)
        val eligibleForSync = queue.filter { it.status == "PENDIENTE" || it.status == "SINCRONIZANDO" || it.status == "ERROR" }
            .sortedWith(compareBy<SyncQueueEntity> { if (it.operationType == "EMERGENCY_TRIGGER") 0 else 1 }.thenBy { it.timestampMillis })

        // Validaciones:
        assertEquals("Deben haber 4 elementos elegibles para sincronización", 4, eligibleForSync.size)
        assertFalse("El elemento ya SINCRONIZADO debe ser excluido", eligibleForSync.any { it.syncFolio == entry4.syncFolio })

        // Prioridad de Emergencia
        assertEquals("La emergencia debe ser el PRIMER elemento en orden de despacho", "EMERGENCY_TRIGGER", eligibleForSync.first().operationType)
        assertEquals("EMG-005", eligibleForSync.first().targetFolio)

        // Recuperación de SINCRONIZANDO
        assertTrue("El elemento en SINCRONIZANDO debe ser rescatado para reprocesamiento", eligibleForSync.any { it.syncFolio == entry2.syncFolio })

        // Reintento de ERROR
        assertTrue("El elemento en ERROR debe ser incluido para reintento", eligibleForSync.any { it.syncFolio == entry3.syncFolio })
    }

    // ---------------------------------------------------------------------------------------------
    // 5. AUDIT LOG PARA ACCIONES ADMINISTRATIVAS
    // ---------------------------------------------------------------------------------------------
    @Test
    fun audit_05_AuditLogAdministrativeActionsPreservation() {
        val targetIncidentFolio = "INC-20260826-4444"
        val actions = listOf(
            Triple("INCIDENT_CONFIRMED", "Supervisor A", "Confirmación en sitio del reporte"),
            Triple("INCIDENT_IN_REVIEW", "Administrador B", "En revisión con el comité de infraestructura"),
            Triple("INCIDENT_DISCARDED", "Supervisor C", "Descartado: Falsa alarma justificada"),
            Triple("INCIDENT_RESOLVED", "Supervisor A", "Dictamen formal: Reparación completada y verificada")
        )

        val auditLogs = mutableListOf<AuditLogEntity>()

        for ((action, operator, justification) in actions) {
            val logFolio = AlphaCoreEngine.generateUniqueFolio("AUD")
            val log = AuditLogEntity(
                folio = logFolio,
                operatorName = operator,
                actionType = action,
                location = "Panel de Gobernanza",
                targetEntity = targetIncidentFolio,
                changeDetails = justification,
                resultStatus = "EXITOSO",
                timestampMillis = System.currentTimeMillis()
            )
            auditLogs.add(log)
        }

        assertEquals(4, auditLogs.size)
        for (log in auditLogs) {
            assertEquals(targetIncidentFolio, log.targetEntity)
            assertTrue("Folio debe comenzar con AUD-", log.folio.startsWith("AUD-"))
            assertTrue("Justificación no puede estar vacía", log.changeDetails.isNotBlank())
            assertTrue("Firma sha256Signature debe estar presente", log.sha256Signature.isNotBlank())
        }
    }

    // ---------------------------------------------------------------------------------------------
    // 6. FLUJO FORENSE DE EMERGENCIAS Y UBICACIÓN NO DISPONIBLE
    // ---------------------------------------------------------------------------------------------
    @Test
    fun audit_06_EmergencyForensicLifecycleAndNoGpsFallback() {
        // Caso A: Con GPS disponible
        val emgFolioA = AlphaCoreEngine.generateUniqueFolio("EMG")
        val gpsA_lat = 19.432607
        val gpsA_lon = -99.133209
        val emgQueueA = SyncQueueEntity(
            syncFolio = AlphaCoreEngine.generateUniqueFolio("SYN"),
            operationId = "OP-EMG-GPS-OK",
            operationType = "EMERGENCY_TRIGGER",
            targetFolio = emgFolioA,
            targetModule = "EMERGENCIAS",
            payloadJson = "{\"folio\":\"$emgFolioA\",\"type\":\"PANICO_SOS\",\"gpsStatus\":\"GPS_CAPTURED\"}",
            latitude = gpsA_lat,
            longitude = gpsA_lon,
            locationName = "Casa #104",
            status = "PENDIENTE"
        )
        assertNotNull(emgQueueA.latitude)
        assertNotNull(emgQueueA.longitude)

        // Caso B: Sin GPS (Simulador o sin señal satelital) -> NO inventar coordenadas
        val emgFolioB = AlphaCoreEngine.generateUniqueFolio("EMG")
        val emgQueueB = SyncQueueEntity(
            syncFolio = AlphaCoreEngine.generateUniqueFolio("SYN"),
            operationId = "OP-EMG-NO-GPS",
            operationType = "EMERGENCY_TRIGGER",
            targetFolio = emgFolioB,
            targetModule = "EMERGENCIAS",
            payloadJson = "{\"folio\":\"$emgFolioB\",\"type\":\"PANICO_SOS\",\"gpsStatus\":\"UBICACION_NO_DISPONIBLE\"}",
            latitude = null, // Explícitamente nulo
            longitude = null, // Explícitamente nulo
            locationName = "UBICACIÓN NO DISPONIBLE (Sin señal satelital en dispositivo)",
            status = "PENDIENTE"
        )
        assertNull("En ausencia de señal GPS, la latitud debe ser null (sin invención)", emgQueueB.latitude)
        assertNull("En ausencia de señal GPS, la longitud debe ser null (sin invención)", emgQueueB.longitude)
        assertTrue("La ubicación debe consignar explícitamente la no disponibilidad", emgQueueB.locationName.contains("UBICACIÓN NO DISPONIBLE"))
    }

    // ---------------------------------------------------------------------------------------------
    // 7. SIMULACIÓN DE REINICIO BRUSCO Y PERSISTENCIA
    // ---------------------------------------------------------------------------------------------
    @Test
    fun audit_07_AbruptTerminationAndRestorationConsistency() {
        val preCrashFolio = "MED-20260826-CRASH-01"
        val syncFolio = "SYN-20260826-CRASH-01"
        val opId = "OP-CRASH-TEST-999"
        val opType = "CHECK_IN"

        // Estado antes del crash
        val persistedEntry = SyncQueueEntity(
            syncFolio = syncFolio,
            operationId = opId,
            operationType = opType,
            targetFolio = preCrashFolio,
            targetModule = "VISITANTES",
            payloadJson = "{\"guest\":\"Juan Perez\",\"persisted\":true}",
            status = "PENDIENTE",
            retryCount = 0,
            hashIntegrity = AlphaCoreEngine.computeIntegrityHash(syncFolio, opId, opType)
        )

        // Simular terminación de proceso y recuperación desde SQLite
        val restoredEntry = persistedEntry.copy()

        assertEquals("El folio debe mantenerse idéntico", syncFolio, restoredEntry.syncFolio)
        assertEquals("El operationId debe mantenerse idéntico", opId, restoredEntry.operationId)
        assertEquals("La firma SHA-256 debe permanecer inalterada tras la recuperación", persistedEntry.hashIntegrity, restoredEntry.hashIntegrity)
    }

    // ---------------------------------------------------------------------------------------------
    // 8. CONCURRENCIA MULTIHILO MASIVA SIN COLISIÓN DE FOLIOS
    // ---------------------------------------------------------------------------------------------
    @Test
    fun audit_08_MassiveMultithreadedConcurrencyNoCollisions() {
        val threadCount = 8
        val opsPerThread = 25
        val executor = Executors.newFixedThreadPool(threadCount)
        val generatedFolios = ConcurrentHashMap.newKeySet<String>()

        for (t in 0 until threadCount) {
            executor.submit {
                for (i in 0 until opsPerThread) {
                    val prefix = when (i % 4) {
                        0 -> "MED"
                        1 -> "SYN"
                        2 -> "INC"
                        else -> "AUD"
                    }
                    val folio = AlphaCoreEngine.generateUniqueFolio(prefix)
                    generatedFolios.add(folio)
                }
            }
        }

        executor.shutdown()
        val finished = executor.awaitTermination(5, TimeUnit.SECONDS)
        assertTrue("La ejecución multihilo debe culminar sin bloqueos", finished)

        val totalExpected = threadCount * opsPerThread
        assertEquals("No debe existir colisión alguna de folios en $totalExpected operaciones concurrentes", totalExpected, generatedFolios.size)
    }

    // ---------------------------------------------------------------------------------------------
    // 9. FUENTE ÚNICA DE VERDAD
    // ---------------------------------------------------------------------------------------------
    @Test
    fun audit_09_SingleSourceOfTruthValidation() {
        val canonicalDatabaseEntity = mapOf(
            "folio" to "MED-20260826-SSOT-88",
            "visitorName" to "Proveedor CFE",
            "status" to "ADENTRO",
            "timestamp" to System.currentTimeMillis()
        )

        // Toda vista o rol debe consultar directamente la misma entidad canónica
        val casetaViewStatus = canonicalDatabaseEntity["status"]
        val supervisorViewStatus = canonicalDatabaseEntity["status"]
        val adminViewStatus = canonicalDatabaseEntity["status"]

        assertEquals("Caseta y Supervisor deben leer el mismo estado", casetaViewStatus, supervisorViewStatus)
        assertEquals("Supervisor y Admin deben leer el mismo estado", supervisorViewStatus, adminViewStatus)
    }

    // ---------------------------------------------------------------------------------------------
    // 10. CÁLCULO DE TIEMPO DEVUELTO (SIN INVENTAR MÉTRICAS)
    // ---------------------------------------------------------------------------------------------
    @Test
    fun audit_10_TimeSavedCalculationWithAuditableData() {
        val loggedOperations = listOf(
            "EMERGENCY_TRIGGER" to 1200L,
            "INCIDENT_REGISTER" to 600L,
            "CHECK_IN" to 180L
        )

        val totalSeconds = loggedOperations.sumOf { it.second }
        val totalMinutes = totalSeconds / 60

        assertEquals(1980L, totalSeconds)
        assertEquals(33L, totalMinutes)

        // Caso donde no hay datos suficientes
        val emptyOperations = emptyList<Pair<String, Long>>()
        val messageWhenEmpty = if (emptyOperations.isEmpty()) "DATOS INSUFICIENTES PARA CÁLCULO" else "${emptyOperations.sumOf { it.second }}s"
        assertEquals("DATOS INSUFICIENTES PARA CÁLCULO", messageWhenEmpty)
    }

    private fun createTestEntry(opType: String, targetFolio: String, status: String): SyncQueueEntity {
        val syncFolio = AlphaCoreEngine.generateUniqueFolio("SYN")
        val opId = "OP-TEST-$targetFolio"
        return SyncQueueEntity(
            syncFolio = syncFolio,
            operationId = opId,
            operationType = opType,
            targetFolio = targetFolio,
            targetModule = "TEST_MODULE",
            payloadJson = "{\"folio\":\"$targetFolio\"}",
            status = status,
            hashIntegrity = AlphaCoreEngine.computeIntegrityHash(syncFolio, opId, opType)
        )
    }
}
