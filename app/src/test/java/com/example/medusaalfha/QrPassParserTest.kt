package com.example.medusaalfha

import com.example.medusaalfha.data.scanner.ParsedVisitorPass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QrPassParserTest {

    @Test
    fun testParseJsonPass() {
        val jsonPayload = """{"folio":"VIS-2026-888","visitorName":"Ing. Alejandro Garza","destinationHouse":"Casa 54 · Cto. Álamos","vehiclePlate":"XYZ-1234","notes":"Cita de trabajo"}"""
        val pass = ParsedVisitorPass.parse(jsonPayload)

        assertEquals("VIS-2026-888", pass.folio)
        assertEquals("Ing. Alejandro Garza", pass.visitorName)
        assertEquals("Casa 54 · Cto. Álamos", pass.destinationHouse)
        assertEquals("XYZ-1234", pass.vehiclePlate)
        assertEquals("Cita de trabajo", pass.notes)

        val entry = pass.toVisitorEntry()
        assertEquals("VIS-2026-888", entry.folio)
        assertEquals("Ing. Alejandro Garza", entry.visitorName)
        assertEquals("Casa 54 · Cto. Álamos", entry.destinationHouse)
        assertEquals("Código QR Escaneado", entry.accessMethod)
    }

    @Test
    fun testParseUrlWithQueryParams() {
        val urlPayload = "https://medusa.alfha.mx/pass?folio=QR-990&name=Mariana+Torres&house=Casa+12&plate=ABC-99"
        val pass = ParsedVisitorPass.parse(urlPayload)

        assertEquals("QR-990", pass.folio)
        assertEquals("Mariana Torres", pass.visitorName)
        assertEquals("Casa 12", pass.destinationHouse)
        assertEquals("ABC-99", pass.vehiclePlate)
    }

    @Test
    fun testParsePipeDelimitedPass() {
        val pipePayload = "FOL-777 | Roberto Ramos | Casa 88 · Cto. Encinos | JHM-90"
        val pass = ParsedVisitorPass.parse(pipePayload)

        assertEquals("FOL-777", pass.folio)
        assertEquals("Roberto Ramos", pass.visitorName)
        assertEquals("Casa 88 · Cto. Encinos", pass.destinationHouse)
        assertEquals("JHM-90", pass.vehiclePlate)
    }

    @Test
    fun testParseSimpleFolio() {
        val simpleFolio = "FOL-2026-901"
        val pass = ParsedVisitorPass.parse(simpleFolio)

        assertEquals("FOL-2026-901", pass.folio)
        assertEquals("Pase QR #FOL-2026-901", pass.visitorName)
    }
}
