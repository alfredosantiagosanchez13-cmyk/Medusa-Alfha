package com.example

import com.example.data.visitor.FirestoreVisitorLog
import com.example.data.visitor.VisitorLogDao
import com.example.data.visitor.VisitorLogEntity
import com.example.data.visitor.VisitorLogRepository
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * Pruebas unitarias para validar los modelos de datos en Room y Firestore para Visitor Log,
 * verificando explícitamente los campos: visitorName, arrivalTime y accessStatus,
 * así como la lógica del repositorio de sincronización.
 */
class VisitorLogSyncTest {

    @Test
    fun testVisitorLogEntity_roomModelFieldsAndDefaults() {
        val arrival = System.currentTimeMillis()
        val entity = VisitorLogEntity(
            id = 1,
            folio = "VIS-TEST-001",
            visitorName = "Lucía Hernández",
            arrivalTime = arrival,
            accessStatus = "CHECKED_IN",
            authorizedUnitNumber = "Casa #102",
            condominiumId = "PRADOS_1",
            visitorDocument = "19.345.678-9",
            passCode = "QR-PASS-102",
            passTypeLabel = "Visita Frecuente",
            vehiclePlate = "KLMN-22",
            guardName = "Oficial Torres",
            guardNotes = "Acceso normal",
            departureTime = null,
            isSynced = false
        )

        // Verificar campos requeridos
        assertEquals("Lucía Hernández", entity.visitorName)
        assertEquals(arrival, entity.arrivalTime)
        assertEquals("CHECKED_IN", entity.accessStatus)
        assertEquals("Casa #102", entity.authorizedUnitNumber)
        assertFalse(entity.isSynced)
        assertNotNull(entity.formattedArrivalTime)
    }

    @Test
    fun testFirestoreVisitorLog_firestoreModelFieldsAndSerialization() {
        val arrival = System.currentTimeMillis()
        val firestoreModel = FirestoreVisitorLog.create(
            visitorName = "Roberto Arismendi",
            arrivalTime = arrival,
            accessStatus = "VERIFICADO",
            authorizedUnitNumber = "Depto 401",
            condominiumId = "PRADOS_1",
            folio = "VIS-MED-999"
        )

        // Verificar campos requeridos en el modelo de Firestore
        assertEquals("Roberto Arismendi", firestoreModel.visitorName)
        assertEquals(arrival, firestoreModel.arrivalTime)
        assertEquals("VERIFICADO", firestoreModel.accessStatus)
        assertEquals(arrival, firestoreModel.timestampMillis)
        assertEquals("VERIFICADO", firestoreModel.status)

        // Verificar serialización a mapa de Firestore
        val map = firestoreModel.toMap()
        assertEquals("Roberto Arismendi", map["visitorName"])
        assertEquals(arrival, map["arrivalTime"])
        assertEquals("VERIFICADO", map["accessStatus"])
        assertEquals(arrival, map["timestampMillis"])
        assertEquals("VERIFICADO", map["status"])
        assertEquals("Depto 401", map["authorizedUnitNumber"])
    }

    @Test
    fun testBidirectionalConversion_RoomToFirestoreAndBack() {
        val arrival = 1718000000000L
        val departure = 1718003600000L

        val originalRoomEntity = VisitorLogEntity(
            id = 42,
            folio = "VIS-CONV-123",
            visitorName = "Catalina Parra",
            arrivalTime = arrival,
            accessStatus = "DEPARTED",
            authorizedUnitNumber = "Casa #305",
            condominiumId = "PRADOS_1",
            visitorDocument = "14.234.567-8",
            passCode = "QR-305",
            passTypeLabel = "Delivery",
            vehiclePlate = "ABCD-99",
            guardName = "Agente Caseta",
            guardNotes = "Entrega completada",
            departureTime = departure,
            isSynced = true,
            lastSyncedAt = 1718003650000L
        )

        // Conversión Room -> Firestore
        val firestoreLog = originalRoomEntity.toFirestoreModel()
        assertEquals("Catalina Parra", firestoreLog.visitorName)
        assertEquals(arrival, firestoreLog.arrivalTime)
        assertEquals("DEPARTED", firestoreLog.accessStatus)
        assertEquals("Casa #305", firestoreLog.authorizedUnitNumber)
        assertEquals(departure, firestoreLog.checkOutMillis)

        // Conversión Firestore -> Room
        val reconstructedRoomEntity = firestoreLog.toVisitorLogEntity(localId = 42)
        assertEquals(originalRoomEntity.id, reconstructedRoomEntity.id)
        assertEquals(originalRoomEntity.folio, reconstructedRoomEntity.folio)
        assertEquals(originalRoomEntity.visitorName, reconstructedRoomEntity.visitorName)
        assertEquals(originalRoomEntity.arrivalTime, reconstructedRoomEntity.arrivalTime)
        assertEquals(originalRoomEntity.accessStatus, reconstructedRoomEntity.accessStatus)
        assertEquals(originalRoomEntity.authorizedUnitNumber, reconstructedRoomEntity.authorizedUnitNumber)
        assertEquals(originalRoomEntity.departureTime, reconstructedRoomEntity.departureTime)
        assertTrue(reconstructedRoomEntity.isSynced)
    }

    @Test
    fun testRepository_offlineRecordingAndStatusUpdate() = runBlocking {
        // Mock en memoria del DAO
        val memoryStore = mutableMapOf<String, VisitorLogEntity>()
        var idCounter = 1L

        val fakeDao = object : VisitorLogDao {
            override fun getAllLogs(): Flow<List<VisitorLogEntity>> = flowOf(memoryStore.values.toList())
            override suspend fun getAllLogsList(): List<VisitorLogEntity> = memoryStore.values.toList()
            override fun getLogsByCondominium(condominiumId: String): Flow<List<VisitorLogEntity>> =
                flowOf(memoryStore.values.filter { it.condominiumId == condominiumId })
            override suspend fun getByFolio(folio: String): VisitorLogEntity? = memoryStore[folio]
            override suspend fun getUnsyncedLogs(): List<VisitorLogEntity> =
                memoryStore.values.filter { !it.isSynced }
            override fun getLogsByStatus(status: String): Flow<List<VisitorLogEntity>> =
                flowOf(memoryStore.values.filter { it.accessStatus == status })
            override suspend fun insertLog(log: VisitorLogEntity): Long {
                val assignedId = if (log.id == 0L) idCounter++ else log.id
                val toSave = log.copy(id = assignedId)
                memoryStore[log.folio] = toSave
                return assignedId
            }
            override suspend fun insertLogs(logs: List<VisitorLogEntity>) {
                logs.forEach { insertLog(it) }
            }
            override suspend fun updateLog(log: VisitorLogEntity) {
                memoryStore[log.folio] = log
            }
            override suspend fun updateStatus(folio: String, status: String, departureTime: Long?) {
                memoryStore[folio]?.let {
                    memoryStore[folio] = it.copy(accessStatus = status, departureTime = departureTime, isSynced = false)
                }
            }
            override suspend fun markAsSynced(folio: String, syncedAt: Long) {
                memoryStore[folio]?.let {
                    memoryStore[folio] = it.copy(isSynced = true, lastSyncedAt = syncedAt)
                }
            }
            override suspend fun deleteByFolio(folio: String) {
                memoryStore.remove(folio)
            }
            override suspend fun deleteAllLogs() {
                memoryStore.clear()
            }
            override suspend fun getLogCount(): Int = memoryStore.size
            override suspend fun getActiveVisitorsCount(): Int =
                memoryStore.values.count { it.accessStatus == "CHECKED_IN" || it.accessStatus == "VERIFICADO" }
        }

        // Instanciar repositorio sin conexión a Firestore (simulando modo offline)
        val repository = VisitorLogRepository(
            visitorLogDao = fakeDao,
            firestoreProvider = { null },
            activeCondominiumId = "PRADOS_1"
        )

        // 1. Registrar llegada de visitante
        val arrivalTime = System.currentTimeMillis()
        val result = repository.recordVisitorArrival(
            visitorName = "Guillermo Tell",
            arrivalTime = arrivalTime,
            accessStatus = "CHECKED_IN",
            authorizedUnitNumber = "Casa #201",
            guardNotes = "Visita autorizada"
        )

        assertTrue(result.isSuccess)
        val saved = result.getOrNull()!!
        assertEquals("Guillermo Tell", saved.visitorName)
        assertEquals(arrivalTime, saved.arrivalTime)
        assertEquals("CHECKED_IN", saved.accessStatus)
        // En modo offline sin Firestore, se marca isSynced = false para resiliencia
        assertFalse(saved.isSynced)

        // 2. Verificar conteo activo
        assertEquals(1, repository.getActiveVisitorsCount())

        // 3. Registrar salida del visitante
        val departureTime = arrivalTime + 3600000L
        val departureResult = repository.recordVisitorDeparture(
            folio = saved.folio,
            departureTime = departureTime,
            departureNotes = "Retiro por garita principal"
        )

        assertTrue(departureResult.isSuccess)
        val departed = departureResult.getOrNull()!!
        assertEquals("DEPARTED", departed.accessStatus)
        assertEquals(departureTime, departed.departureTime)
        assertEquals(0, repository.getActiveVisitorsCount())

        // 4. Intentar sincronización en modo offline y validar manejo gracioso
        val syncResult = repository.syncAll()
        assertFalse(syncResult.isSuccess)
        assertTrue(syncResult.errorMessage!!.contains("offline", ignoreCase = true) || syncResult.errorMessage!!.contains("disponible", ignoreCase = true))
    }
}
