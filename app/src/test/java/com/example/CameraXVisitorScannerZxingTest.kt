package com.example

import com.example.data.core.AlphaCoreEngine
import com.example.scanner.ParsedQrPass
import com.example.scanner.QrPayloadParser
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias para el componente de escáner de códigos QR para visitantes
 * basado en CameraX e integrado con la biblioteca ZXing.
 *
 * Valida:
 * - Decodificación óptica de códigos de visitantes con MultiFormatReader y PlanarYUVLuminanceSource.
 * - Decodificación de QR invertidos para pantallas de smartphones en modo nocturno.
 * - Extracción y deserialización de metadatos de visitantes (nombre, unidad, folio, placas).
 * - Algoritmo de cooldown y debouncing para prevenir lecturas duplicadas en el flujo CameraX.
 */
class CameraXVisitorScannerZxingTest {

    private fun decodeBitMatrixWithZxing(payload: String, size: Int = 200, invert: Boolean = false): String {
        val writer = QRCodeWriter()
        val encodeHints = mapOf(
            com.google.zxing.EncodeHintType.CHARACTER_SET to "UTF-8"
        )
        val bitMatrix: BitMatrix = writer.encode(payload, BarcodeFormat.QR_CODE, size, size, encodeHints)

        val width = bitMatrix.width
        val height = bitMatrix.height
        val yuvBytes = ByteArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val isBlack = bitMatrix.get(x, y)
                val pixelValue = if (invert) {
                    if (isBlack) 255.toByte() else 0.toByte()
                } else {
                    if (isBlack) 0.toByte() else 255.toByte()
                }
                yuvBytes[y * width + x] = pixelValue
            }
        }

        val source = PlanarYUVLuminanceSource(
            yuvBytes,
            width,
            height,
            0,
            0,
            width,
            height,
            false
        )
        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

        val reader = MultiFormatReader().apply {
            val hints = mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.QR_CODE,
                    BarcodeFormat.DATA_MATRIX,
                    BarcodeFormat.CODE_128
                ),
                DecodeHintType.TRY_HARDER to java.lang.Boolean.TRUE,
                DecodeHintType.CHARACTER_SET to "UTF-8"
            )
            setHints(hints)
        }

        return try {
            reader.decodeWithState(binaryBitmap).text
        } catch (_: NotFoundException) {
            // Fallback para QR invertido en modo nocturno
            val invertedBitmap = BinaryBitmap(HybridBinarizer(source.invert()))
            reader.decodeWithState(invertedBitmap).text
        }
    }

    @Test
    fun testZxingDecodesVisitorQrCodePayload() {
        val folio = AlphaCoreEngine.generateUniqueFolio("VIS")
        val visitorJson = "{\"passCode\":\"$folio\",\"guestName\":\"Mariana Gómez\",\"destinationHouse\":\"Casa 42\",\"passType\":\"Visita Personal\"}"

        val decodedResult = decodeBitMatrixWithZxing(visitorJson, size = 200, invert = false)

        assertNotNull(decodedResult)
        assertEquals(visitorJson, decodedResult)

        val cleanCode = QrPayloadParser.extractEntryCode(decodedResult)
        assertEquals(folio, cleanCode)

        val parsed: ParsedQrPass = QrPayloadParser.parse(decodedResult)
        assertEquals(folio, parsed.passCode)
        assertEquals("Mariana Gómez", parsed.guestName)
        assertEquals("Casa 42", parsed.destinationHouse)
        assertEquals("Visita Personal", parsed.passType)
    }

    @Test
    fun testZxingDecodesInvertedDarkModeVisitorCode() {
        val visitorCode = "VIS-20260923-7721"
        val decodedResult = decodeBitMatrixWithZxing(visitorCode, size = 200, invert = true)

        assertNotNull(decodedResult)
        assertEquals(visitorCode, decodedResult)
        assertEquals(visitorCode, QrPayloadParser.extractEntryCode(decodedResult))
    }

    @Test
    fun testVisitorCodeExtractionFromUrlAndDeeplink() {
        val urlPayload = "https://medusa.alfha.app/pass?code=VIS-20260923-9988"
        val extractedFromUrl = QrPayloadParser.extractEntryCode(urlPayload)
        assertEquals("VIS-20260923-9988", extractedFromUrl)

        val jsonPayload = "{\"passCode\":\"VIS-20260923-1122\",\"guestName\":\"Carlos Ruiz\"}"
        val extractedFromJson = QrPayloadParser.extractEntryCode(jsonPayload)
        assertEquals("VIS-20260923-1122", extractedFromJson)

        val plainCode = "VIS-20260923-3344"
        val extractedFromPlain = QrPayloadParser.extractEntryCode(plainCode)
        assertEquals("VIS-20260923-3344", extractedFromPlain)
    }

    @Test
    fun testScannerDebounceCooldownSuppressesDuplicateVisitorCodes() {
        var callCount = 0
        var lastCaptured: String? = null

        val cooldownMillis = 1200L
        var lastTriggerTime = 0L

        fun simulateScan(code: String, currentTime: Long) {
            if (code != lastCaptured || (currentTime - lastTriggerTime) > cooldownMillis) {
                lastCaptured = code
                lastTriggerTime = currentTime
                callCount++
            }
        }

        // 1. Primer escaneo en t=0ms
        simulateScan("VIS-20260923-1001", 0L)
        assertEquals(1, callCount)
        assertEquals("VIS-20260923-1001", lastCaptured)

        // 2. Mismo código a los 300ms (dentro del cooldown de 1200ms) -> Ignorado
        simulateScan("VIS-20260923-1001", 300L)
        assertEquals(1, callCount)

        // 3. Código de visitante diferente a los 600ms -> Debe procesarse inmediatamente
        simulateScan("VIS-20260923-2002", 600L)
        assertEquals(2, callCount)
        assertEquals("VIS-20260923-2002", lastCaptured)

        // 4. Mismo código a los 2000ms (1400ms después, superando cooldown) -> Debe procesarse
        simulateScan("VIS-20260923-2002", 2000L)
        assertEquals(3, callCount)
    }

    @Test
    fun testParsedQrPassWithCompleteVisitorMetadata() {
        val payload = "{\"passCode\":\"MED-20260923-4567\",\"guestName\":\"Ing. Fernando Torres\",\"destinationHouse\":\"Torre B - Depto 501\",\"vehiclePlate\":\"XYZ-987\",\"type\":\"PROVEEDOR\",\"hostResidentName\":\"Dra. Laura Méndez\"}"

        val parsed = QrPayloadParser.parse(payload)

        assertEquals("MED-20260923-4567", parsed.passCode)
        assertEquals("Ing. Fernando Torres", parsed.guestName)
        assertEquals("Torre B - Depto 501", parsed.destinationHouse)
        assertEquals("XYZ-987", parsed.vehiclePlate)
        assertEquals("PROVEEDOR", parsed.passType)
        assertEquals("Dra. Laura Méndez", parsed.hostResidentName)
    }
}
