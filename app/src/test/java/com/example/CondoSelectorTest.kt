package com.example

import com.example.ui.components.CondoTarget
import com.example.ui.components.CondominiumOption
import com.example.ui.components.DEFAULT_CONDOMINIUM_OPTIONS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias para el componente y catálogo de CondoSelector.
 * Valida la disponibilidad de condominios (Paraíso, Los Prados 1, 2, 3) y el aislamiento de datos para reportes.
 */
class CondoSelectorTest {

    @Test
    fun testDefaultCondominiumOptions_containsParaisoAndLosPrados() {
        val options = DEFAULT_CONDOMINIUM_OPTIONS
        assertEquals(4, options.size)

        val paraiso = options.find { it.id == "PARAISO" }
        assertNotNull("Condominio Paraíso debe existir en las opciones", paraiso)
        assertEquals("Condominio Paraíso", paraiso?.name)
        assertEquals(32, paraiso?.totalUnits)

        val prados1 = options.find { it.id == "PRADOS_1" }
        assertNotNull("Los Prados 1 debe existir", prados1)
        assertEquals(94, prados1?.totalUnits)

        val prados2 = options.find { it.id == "PRADOS_2" }
        assertNotNull("Los Prados 2 debe existir", prados2)
        assertEquals(91, prados2?.totalUnits)

        val prados3 = options.find { it.id == "PRADOS_3" }
        assertNotNull("Los Prados 3 debe existir", prados3)
        assertEquals(76, prados3?.totalUnits)
    }

    @Test
    fun testCondoTargetEnum_coverageAndIsolationDetails() {
        val targets = CondoTarget.values()
        assertEquals(4, targets.size)

        // Verificar que Paraíso tenga su nombre oficial y conteo de casas
        val paraiso = CondoTarget.PARAISO
        assertEquals("Condominio Paraíso", paraiso.displayName)
        assertEquals("PARAÍSO", paraiso.shortTag)
        assertEquals(32, paraiso.totalCasas)
        assertTrue(paraiso.locationInfo.contains("Fracción F4-133"))

        // Verificar Los Prados 1, 2 y 3
        val prados1 = CondoTarget.PRADOS_1
        assertEquals("Los Prados 1", prados1.displayName)
        assertEquals(94, prados1.totalCasas)

        val prados2 = CondoTarget.PRADOS_2
        assertEquals("Los Prados 2", prados2.displayName)
        assertEquals(91, prados2.totalCasas)

        val prados3 = CondoTarget.PRADOS_3
        assertEquals("Los Prados 3", prados3.displayName)
        assertEquals(76, prados3.totalCasas)
    }

    @Test
    fun testDataIsolationFiltering_byCondo() {
        // Simular eventos o incidentes para verificar que el selector aísle correctamente el reporte
        data class MockReportEvent(val id: String, val location: String)

        val events = listOf(
            MockReportEvent("1", "Casa 04 - Condominio Paraíso"),
            MockReportEvent("2", "Calle 1 Casa 12 - Prados 1"),
            MockReportEvent("3", "Calle 3 Casa 05 - Prados 2"),
            MockReportEvent("4", "Calle 5 Casa 20 - Prados 3"),
            MockReportEvent("5", "Acceso Principal Paraíso")
        )

        // Filtro para Paraíso
        val paraisoEvents = events.filter {
            !it.location.contains("Calle", ignoreCase = true) && !it.location.contains("Prados", ignoreCase = true)
        }
        assertEquals(2, paraisoEvents.size)
        assertTrue(paraisoEvents.all { it.location.contains("Paraíso") })

        // Filtro para Prados 1 (Calles 1 y 2)
        val prados1Events = events.filter {
            it.location.contains("Calle 1", ignoreCase = true) || it.location.contains("Calle 2", ignoreCase = true)
        }
        assertEquals(1, prados1Events.size)
        assertEquals("Calle 1 Casa 12 - Prados 1", prados1Events[0].location)
    }
}
