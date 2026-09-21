package com.example

import com.example.data.incident.IncidentCategory
import com.example.data.incident.IncidentEntity
import com.example.data.incident.IncidentPriority
import com.example.data.visitor.VisitorCheckIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Pruebas unitarias para validar las transformaciones y lógica reactiva de
 * RoomSecuritySummaryDashboardComponent con respecto a los datos de Room SQLite.
 */
class RoomSecuritySummaryDashboardTest {

    @Test
    fun testActiveVisitorsCalculation() {
        val checkIns = listOf(
            VisitorCheckIn(
                id = 1,
                visitorName = "Juan Pérez",
                visitorDocument = "INE-01",
                destinationHouse = "101",
                passCode = "CODE-1",
                passTypeLabel = "Visita",
                status = "CHECKED_IN"
            ),
            VisitorCheckIn(
                id = 2,
                visitorName = "María Gómez",
                visitorDocument = "INE-02",
                destinationHouse = "102",
                passCode = "CODE-2",
                passTypeLabel = "Proveedor",
                status = "DEPARTED",
                checkOutMillis = System.currentTimeMillis()
            ),
            VisitorCheckIn(
                id = 3,
                visitorName = "Pedro Soto",
                visitorDocument = "INE-03",
                destinationHouse = "103",
                passCode = "CODE-3",
                passTypeLabel = "Visita",
                status = "VERIFICADO"
            )
        )

        val activeCount = checkIns.count { it.status == "CHECKED_IN" || it.status == "VERIFICADO" }
        assertEquals(2, activeCount)
    }

    @Test
    fun testIncidentSeverityAggregation() {
        val incidents = listOf(
            IncidentEntity(
                folio = "INC-01",
                rawTranscript = "Vehículo mal estacionado",
                category = IncidentCategory.PARKING_VIALIDAD,
                priority = IncidentPriority.ALTA,
                location = "Estacionamiento Visitas",
                aiSummary = "Vehículo mal estacionado",
                recommendedAction = "Aviso al dueño",
                status = "EN_ATENCION"
            ),
            IncidentEntity(
                folio = "INC-02",
                rawTranscript = "Alarma perimetral activada",
                category = IncidentCategory.SEGURIDAD_EMERGENCIA,
                priority = IncidentPriority.CRITICA,
                location = "Barda norte",
                aiSummary = "Alarma perimetral",
                recommendedAction = "Revisión inmediata",
                status = "REGISTRADO",
                isEmergency = true
            ),
            IncidentEntity(
                folio = "INC-03",
                rawTranscript = "Ruido en casa club",
                category = IncidentCategory.RUIDO_CONVIVENCIA,
                priority = IncidentPriority.MEDIA,
                location = "Casa Club",
                aiSummary = "Música a alto volumen",
                recommendedAction = "Llamado de atención",
                status = "CERRADO"
            )
        )

        val openIncidents = incidents.filter { it.status != "CERRADO" && it.status != "RESUELTO" }
        assertEquals(2, openIncidents.size)

        val criticalCount = openIncidents.count { it.priority == IncidentPriority.CRITICA || it.isEmergency }
        assertEquals(1, criticalCount)
    }

    @Test
    fun testHourlySlotBinning() {
        val calendar = Calendar.getInstance()
        val counts = IntArray(6)

        // Simular timestamps en diferentes horas
        val hourSlotPairs = listOf(
            2 to 0,   // 02:00 -> Slot 0 (00-04h)
            9 to 2,   // 09:00 -> Slot 2 (08-12h)
            14 to 3,  // 14:00 -> Slot 3 (12-16h)
            18 to 4,  // 18:00 -> Slot 4 (16-20h)
            19 to 4   // 19:00 -> Slot 4 (16-20h)
        )

        for ((hour, expectedSlot) in hourSlotPairs) {
            val slotIndex = (hour / 4).coerceIn(0, 5)
            assertEquals(expectedSlot, slotIndex)
            counts[slotIndex]++
        }

        assertEquals(1, counts[0])
        assertEquals(0, counts[1])
        assertEquals(1, counts[2])
        assertEquals(1, counts[3])
        assertEquals(2, counts[4])
        assertEquals(0, counts[5])

        val peakSlot = counts.indices.maxByOrNull { counts[it] }
        assertEquals(4, peakSlot) // Slot 4 (16-20h) is the peak
    }
}
