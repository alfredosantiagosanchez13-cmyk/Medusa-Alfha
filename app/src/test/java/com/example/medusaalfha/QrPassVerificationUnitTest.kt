package com.example.medusaalfha

import com.example.medusaalfha.data.model.VisitorEntry
import com.example.medusaalfha.data.model.VisitorStatus
import com.example.medusaalfha.data.scanner.ParsedVisitorPass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPassVerificationUnitTest {

    @Test
    fun testParsedPassToVisitorEntryWithStatuses() {
        val pass = ParsedVisitorPass(
            folio = "FOL-TEST-100",
            visitorName = "Mariana Ramos",
            destinationHouse = "Casa 42",
            vehiclePlate = "XYZ-987"
        )

        // Default status is DENTRO
        val entryCheckIn = pass.toVisitorEntry()
        assertEquals(VisitorStatus.DENTRO, entryCheckIn.status)
        assertEquals("FOL-TEST-100", entryCheckIn.folio)
        assertEquals("Mariana Ramos", entryCheckIn.visitorName)
        assertEquals("Casa 42", entryCheckIn.destinationHouse)
        assertEquals("XYZ-987", entryCheckIn.vehiclePlate)
        assertTrue(entryCheckIn.checkInTimestamp > 0)

        // Explicit SALIDA
        val entryCheckOut = pass.toVisitorEntry(status = VisitorStatus.SALIDA)
        assertEquals(VisitorStatus.SALIDA, entryCheckOut.status)
        assertNotNull(entryCheckOut.checkOutTimestamp)
    }

    @Test
    fun testAutomaticInOutToggleLogic() {
        val currentInsideFolios = setOf("FOL-INSIDE-1", "FOL-INSIDE-2")

        val scannedPass1 = ParsedVisitorPass(folio = "FOL-INSIDE-1", visitorName = "Carlos", destinationHouse = "Casa 10")
        val isInside1 = currentInsideFolios.contains(scannedPass1.folio)
        assertTrue("El visitante ya registrado debe marcarse como en sitio", isInside1)

        val targetStatus1 = if (isInside1) VisitorStatus.SALIDA else VisitorStatus.DENTRO
        assertEquals("Debe alternar automáticamente a marcar salida", VisitorStatus.SALIDA, targetStatus1)

        val scannedPassNew = ParsedVisitorPass(folio = "FOL-NEW-99", visitorName = "Lucia", destinationHouse = "Casa 15")
        val isInsideNew = currentInsideFolios.contains(scannedPassNew.folio)
        assertFalse("El nuevo visitante no debe marcarse como en sitio", isInsideNew)

        val targetStatusNew = if (isInsideNew) VisitorStatus.SALIDA else VisitorStatus.DENTRO
        assertEquals("Debe ser ingreso para visitante no registrado en sitio", VisitorStatus.DENTRO, targetStatusNew)
    }

    @Test
    fun testQrJsonPassParsingWithSpecialCharacters() {
        val rawJson = """{"folio":"FOL-PRADOS-01","visitorName":"José María O'Connor","destinationHouse":"Casa 101-B","vehiclePlate":"CDMX-456"}"""
        val parsed = ParsedVisitorPass.parse(rawJson)

        assertEquals("FOL-PRADOS-01", parsed.folio)
        assertEquals("José María O'Connor", parsed.visitorName)
        assertEquals("Casa 101-B", parsed.destinationHouse)
        assertEquals("CDMX-456", parsed.vehiclePlate)
    }

    @Test
    fun testQrPipeFormatParsing() {
        val rawPipe = "FOL-882 | Rodrigo Beltrán | Casa 33 | NLS-332 | Cita técnica"
        val parsed = ParsedVisitorPass.parse(rawPipe)

        assertEquals("FOL-882", parsed.folio)
        assertEquals("Rodrigo Beltrán", parsed.visitorName)
        assertEquals("Casa 33", parsed.destinationHouse)
        assertEquals("NLS-332", parsed.vehiclePlate)
        assertEquals("Cita técnica", parsed.notes)
    }
}
