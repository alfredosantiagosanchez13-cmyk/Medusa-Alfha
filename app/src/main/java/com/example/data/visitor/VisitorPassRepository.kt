package com.example.data.visitor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repositorio local de Historial de Visitantes para el Portal del Condómino.
 * Proporciona acceso reactivo (Flow) y almacenamiento garantizado en búfer Room.
 */
class VisitorPassRepository(
    private val visitorPassDao: VisitorPassDao
) {

    /**
     * Retorna el flujo reactivo de accesos para la unidad residencial.
     */
    fun getVisitorHistoryFlow(destinationHouse: String): Flow<List<VisitorPassEntity>> {
        return visitorPassDao.getVisitorHistoryFlow(destinationHouse)
    }

    /**
     * Asegura la existencia de registros históricos demostrativos si el búfer local está vacío.
     */
    suspend fun ensureInitialHistorySeeded(destinationHouse: String) = withContext(Dispatchers.IO) {
        val count = visitorPassDao.getCountByHouse(destinationHouse)
        if (count == 0) {
            val now = System.currentTimeMillis()
            val sampleRecords = listOf(
                VisitorPassEntity(
                    id = "VIS-${System.currentTimeMillis() % 100000}-01",
                    destinationHouse = destinationHouse,
                    visitorName = "Carlos Mendoza Silva",
                    passType = "Visita Personal",
                    status = "VERIFICADO",
                    timestampMillis = now - (2 * 3600 * 1000L), // hace 2 horas
                    exitTimestampMillis = now - (30 * 60 * 1000L),
                    vehiclePlate = "ABC-789",
                    folio = "ACC-2026-901",
                    accessPoint = "Garita Norte (Vehicular)",
                    guardNotes = "Identificación oficial cotejada. Acceso autorizado."
                ),
                VisitorPassEntity(
                    id = "VIS-${System.currentTimeMillis() % 100000}-02",
                    destinationHouse = destinationHouse,
                    visitorName = "Repartidor DHL Express",
                    passType = "Delivery / Paquetería",
                    status = "EXPIRADO",
                    timestampMillis = now - (18 * 3600 * 1000L), // ayer
                    exitTimestampMillis = now - (17 * 3600 * 1000L),
                    vehiclePlate = "FED-4512",
                    folio = "ACC-2026-874",
                    accessPoint = "Garita Principal (Peatonal)",
                    guardNotes = "Entrega de paquete recibida en portería."
                ),
                VisitorPassEntity(
                    id = "VIS-${System.currentTimeMillis() % 100000}-03",
                    destinationHouse = destinationHouse,
                    visitorName = "Técnico Telecom Totalplay",
                    passType = "Servicio Técnico",
                    status = "VERIFICADO",
                    timestampMillis = now - (48 * 3600 * 1000L), // hace 2 días
                    exitTimestampMillis = now - (46 * 3600 * 1000L),
                    vehiclePlate = "TOT-9821",
                    folio = "ACC-2026-792",
                    accessPoint = "Garita de Servicios",
                    guardNotes = "Mantenimiento de fibra óptica residencial."
                ),
                VisitorPassEntity(
                    id = "VIS-${System.currentTimeMillis() % 100000}-04",
                    destinationHouse = destinationHouse,
                    visitorName = "Visitante No Identificado",
                    passType = "Visita Ocasional",
                    status = "DENEGADO",
                    timestampMillis = now - (72 * 3600 * 1000L), // hace 3 días
                    exitTimestampMillis = null,
                    vehiclePlate = "SIN_PLACA",
                    folio = "DEN-2026-104",
                    accessPoint = "Garita Principal",
                    guardNotes = "Sin autorización previa ni confirmación del titular por interfono."
                )
            )
            visitorPassDao.insertPasses(sampleRecords)
        }
    }
}
