package com.example.medusaalfha

import com.example.medusaalfha.data.model.VisitorEntry
import com.example.medusaalfha.data.model.VisitorStatus
import com.example.medusaalfha.ui.viewmodel.VisitorFilterTab
import com.example.medusaalfha.ui.viewmodel.VisitorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisitorViewModelUnitTest {

    @Test
    fun testVisitorUiStateDefaults() {
        val state = VisitorUiState()
        assertTrue(state.entries.isEmpty())
        assertTrue(state.filteredEntries.isEmpty())
        assertEquals(VisitorFilterTab.ALL, state.currentFilter)
        assertEquals("", state.searchQuery)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun testVisitorFilterTabTitles() {
        assertEquals("Todos", VisitorFilterTab.ALL.title)
        assertEquals("En Sitio", VisitorFilterTab.INSIDE.title)
        assertEquals("Autorizados", VisitorFilterTab.AUTHORIZED.title)
        assertEquals("Salidas", VisitorFilterTab.CHECKED_OUT.title)
    }

    @Test
    fun testVisitorFilteringLogic() {
        val entry1 = VisitorEntry(
            folio = "FOL-001",
            visitorName = "Ana Gómez",
            destinationHouse = "Casa 12",
            status = VisitorStatus.DENTRO
        )
        val entry2 = VisitorEntry(
            folio = "FOL-002",
            visitorName = "Roberto Soto",
            destinationHouse = "Casa 25",
            status = VisitorStatus.AUTORIZADO
        )
        val entry3 = VisitorEntry(
            folio = "FOL-003",
            visitorName = "Paola Pérez",
            destinationHouse = "Casa 12",
            status = VisitorStatus.SALIDA
        )

        val list = listOf(entry1, entry2, entry3)

        // Filter Inside
        val insideList = list.filter { it.status == VisitorStatus.DENTRO }
        assertEquals(1, insideList.size)
        assertEquals("Ana Gómez", insideList.first().visitorName)

        // Filter Authorized
        val authList = list.filter { it.status == VisitorStatus.AUTORIZADO }
        assertEquals(1, authList.size)
        assertEquals("Roberto Soto", authList.first().visitorName)

        // Filter Checked Out
        val salidaList = list.filter { it.status == VisitorStatus.SALIDA }
        assertEquals(1, salidaList.size)
        assertEquals("Paola Pérez", salidaList.first().visitorName)

        // Search by query
        val searchHouse12 = list.filter { it.destinationHouse.contains("Casa 12") }
        assertEquals(2, searchHouse12.size)
    }
}
