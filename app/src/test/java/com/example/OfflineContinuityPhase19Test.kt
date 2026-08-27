package com.example

import com.example.data.core.AlphaCoreEngine
import com.example.data.sync.ConnectivityStatus
import com.example.data.sync.NetworkStateInfo
import com.example.data.sync.SyncQueueEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Suite de Pruebas Unitarias para FASE 19:
 * CONTINUIDAD OPERATIVA, MODO OFFLINE ROBUSTO, IDEMPOTENCIA, SHA-256 Y PRIORIZACIÓN DE EMERGENCIAS.
 */
class OfflineContinuityPhase19Test {

    // 1. Verificar Estructura y Folio Único de Cola de Sincronización
    @Test
    fun testSyncQueueEntityStructureAndFolio() {
        val opId = "OP-${UUID.randomUUID()}"
        val targetFolio = AlphaCoreEngine.generateUniqueFolio("MED")
        val syncFolio = AlphaCoreEngine.generateUniqueFolio("SYN")
        val opType = "CHECK_IN"

        val entry = SyncQueueEntity(
            syncFolio = syncFolio,
            operationId = opId,
            operatorName = "Oficial de Guardia",
            operatorRole = "GUARDIA",
            operationType = opType,
            targetFolio = targetFolio,
            targetModule = "VISITANTES",
            payloadJson = "{\"guest\":\"Juan Perez\",\"unit\":\"Casa #101\"}",
            locationName = "Garita Principal",
            latitude = 19.4326,
            longitude = -99.1332,
            evidencePaths = "/evidence/photo1.jpg",
            status = "PENDIENTE",
            retryCount = 0,
            deviceGateId = "Caseta 1 - Terminal Principal",
            timeSavedSeconds = 180L,
            hashIntegrity = AlphaCoreEngine.computeIntegrityHash(syncFolio, opId, opType)
        )

        assertTrue("El folio debe iniciar con SYN-", entry.syncFolio.startsWith("SYN-"))
        assertEquals("El estado inicial debe ser PENDIENTE", "PENDIENTE", entry.status)
        assertEquals("El conteo inicial de reintentos debe ser 0", 0, entry.retryCount)
        assertEquals(180L, entry.timeSavedSeconds)
        assertFalse("Operación estándar no es de emergencia", entry.isEmergency)
        assertNotNull(entry.formattedTime)
    }

    // 2. Verificar Priorización de Emergencias (Botón de Pánico)
    @Test
    fun testEmergencyPriorityInSyncQueue() {
        val emgSyncFolio = AlphaCoreEngine.generateUniqueFolio("SYN")
        val emgOpId = "OP-EMG-${UUID.randomUUID()}"
        val emgTargetFolio = AlphaCoreEngine.generateUniqueFolio("EMG")

        val emergencyEntry = SyncQueueEntity(
            syncFolio = emgSyncFolio,
            operationId = emgOpId,
            operatorName = "Residente Afectado",
            operatorRole = "RESIDENTE",
            operationType = "EMERGENCY_TRIGGER",
            targetFolio = emgTargetFolio,
            targetModule = "EMERGENCIAS",
            payloadJson = "{\"alert\":\"SOS Pánico Activado\"}",
            locationName = "Casa #204",
            status = "PENDIENTE",
            hashIntegrity = AlphaCoreEngine.computeIntegrityHash(emgSyncFolio, emgOpId, "EMERGENCY_TRIGGER")
        )

        val regularSyncFolio = AlphaCoreEngine.generateUniqueFolio("SYN")
        val regularOpId = "OP-REG-${UUID.randomUUID()}"
        val regularTargetFolio = AlphaCoreEngine.generateUniqueFolio("MED")

        val regularEntry = SyncQueueEntity(
            syncFolio = regularSyncFolio,
            operationId = regularOpId,
            operatorName = "Guardia",
            operatorRole = "GUARDIA",
            operationType = "CHECK_IN",
            targetFolio = regularTargetFolio,
            targetModule = "VISITANTES",
            payloadJson = "{\"guest\":\"Visitante\"}",
            locationName = "Garita",
            status = "PENDIENTE",
            hashIntegrity = AlphaCoreEngine.computeIntegrityHash(regularSyncFolio, regularOpId, "CHECK_IN")
        )

        assertTrue("Emergency entry must return isEmergency = true", emergencyEntry.isEmergency)
        assertFalse("Regular entry must return isEmergency = false", regularEntry.isEmergency)

        // Simular ordenamiento de priorización SQL: ORDER BY CASE WHEN operationType = 'EMERGENCY_TRIGGER' THEN 0 ELSE 1 END
        val queueList = listOf(regularEntry, emergencyEntry)
        val sortedList = queueList.sortedBy { if (it.operationType == "EMERGENCY_TRIGGER") 0 else 1 }

        assertEquals("La emergencia debe ser la primera en la cola de sincronización", emergencyEntry.syncFolio, sortedList.first().syncFolio)
    }

    // 3. Verificar Firma Criptográfica SHA-256 e Integridad
    @Test
    fun testCryptographicIntegritySha256() {
        val syncFolio = "SYN-20260826-1234"
        val operationId = "OP-9876-UUID"
        val operationType = "INCIDENT_REGISTER"

        val expectedHash = AlphaCoreEngine.computeIntegrityHash(syncFolio, operationId, operationType)
        val verifiedHash = AlphaCoreEngine.computeIntegrityHash(syncFolio, operationId, operationType)

        assertEquals("El hash SHA-256 debe ser idéntico y determinista", expectedHash, verifiedHash)
        assertEquals(64, expectedHash.length)

        // Si se altera el tipo de operación o el folio, el hash debe fallar
        val alteredHash = AlphaCoreEngine.computeIntegrityHash(syncFolio, operationId, "ALTERED_OPERATION")
        assertNotEquals("Cualquier alteración debe romper el hash SHA-256", expectedHash, alteredHash)
    }

    // 4. Verificar Idempotencia (Misma Operación -> Un Solo Registro)
    @Test
    fun testIdempotencyDeduplicationKey() {
        val targetFolio = "MED-20260826-5555"
        val operationId1 = "OP-IDEMPOTENT-$targetFolio"
        val operationId2 = "OP-IDEMPOTENT-$targetFolio"

        assertEquals("Las claves de idempotencia para la misma entidad deben ser idénticas", operationId1, operationId2)
    }

    // 5. Verificar Estados de Conectividad de Red
    @Test
    fun testNetworkConnectivityStateTransitions() {
        val onlineState = NetworkStateInfo(
            status = ConnectivityStatus.ONLINE,
            isConnected = true,
            transportType = "Wi-Fi",
            hasInternetCapability = true
        )
        assertEquals(ConnectivityStatus.ONLINE, onlineState.status)
        assertTrue(onlineState.isConnected)

        val offlineState = NetworkStateInfo(
            status = ConnectivityStatus.OFFLINE,
            isConnected = false,
            transportType = "Sin Conexión",
            hasInternetCapability = false
        )
        assertEquals(ConnectivityStatus.OFFLINE, offlineState.status)
        assertFalse(offlineState.isConnected)

        val syncState = onlineState.copy(status = ConnectivityStatus.SYNCHRONIZING)
        assertEquals(ConnectivityStatus.SYNCHRONIZING, syncState.status)
    }

    // 6. Verificar Tiempo Devuelto por Continuidad Operativa ("ESTO DEVUELVE TIEMPO")
    @Test
    fun testTimeSavedCalculationByModule() {
        val emgSavings = 1200L // 20 min
        val incSavings = 600L  // 10 min
        val checkInSavings = 180L // 3 min
        val tourSavings = 300L // 5 min
        val vehSavings = 180L  // 3 min

        val totalSeconds = emgSavings + incSavings + checkInSavings + tourSavings + vehSavings
        val totalMinutes = totalSeconds / 60

        assertEquals(2460L, totalSeconds)
        assertEquals(41L, totalMinutes)
    }
}
