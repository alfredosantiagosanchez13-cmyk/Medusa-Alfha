package com.example

import com.example.scanner.QrPayloadParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pruebas unitarias de auditoría para QrPayloadParser y la extracción resiliente de códigos de acceso.
 * Orden MEDUSA Alfha: Resiliencia ante códigos planos, JSON, URLs y Deeplinks.
 */
class QrPayloadParserAndVerificationTest {

    @Test
    fun testExtractEntryCode_DirectCode() {
        val code = "MED-20260904-1001"
        assertEquals("MED-20260904-1001", QrPayloadParser.extractEntryCode(code))
    }

    @Test
    fun testExtractEntryCode_WithQuotesAndWhitespace() {
        val raw = "  \"MED-20260904-2002\"  "
        assertEquals("MED-20260904-2002", QrPayloadParser.extractEntryCode(raw))
    }

    @Test
    fun testExtractEntryCode_JsonPayload() {
        val jsonPayload = """
            {
                "passCode": "MED-JSON-9988",
                "guestName": "Carlos Mendoza",
                "house": "Casa 45",
                "residentName": "Familia Ramos"
            }
        """.trimIndent()

        assertEquals("MED-JSON-9988", QrPayloadParser.extractEntryCode(jsonPayload))

        val parsed = QrPayloadParser.parse(jsonPayload)
        assertEquals("MED-JSON-9988", parsed.passCode)
        assertEquals("Carlos Mendoza", parsed.guestName)
        assertEquals("Casa 45", parsed.destinationHouse)
        assertEquals("Familia Ramos", parsed.hostResidentName)
    }

    @Test
    fun testExtractEntryCode_UrlQueryParam() {
        val url = "https://medusa.app/verify?code=MED-URL-5544&tenant=PRADOS_1"
        assertEquals("MED-URL-5544", QrPayloadParser.extractEntryCode(url))
    }

    @Test
    fun testExtractEntryCode_DeeplinkQueryParam() {
        val deeplink = "medusa://access?passCode=MED-DEEP-1122"
        assertEquals("MED-DEEP-1122", QrPayloadParser.extractEntryCode(deeplink))
    }

    @Test
    fun testExtractEntryCode_UrlPathSegment() {
        val urlPath = "https://medusa-control.web.app/passes/MED-2026-PATH"
        assertEquals("MED-2026-PATH", QrPayloadParser.extractEntryCode(urlPath))
    }

    @Test
    fun testExtractEntryCode_EmptyOrBlank() {
        assertEquals("", QrPayloadParser.extractEntryCode("   "))
        val parsed = QrPayloadParser.parse("")
        assertEquals("", parsed.passCode)
        assertNull(parsed.guestName)
    }
}
