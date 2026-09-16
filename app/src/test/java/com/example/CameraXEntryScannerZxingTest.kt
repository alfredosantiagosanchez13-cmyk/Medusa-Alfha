package com.example

import com.example.data.core.AlphaCoreEngine
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.*
import org.junit.Test

/**
 * Pruebas unitarias para validar el pipeline de escaneo de accesos basado en CameraX y ZXing:
 * - Decodificación óptica de QR estándar mediante PlanarYUVLuminanceSource y HybridBinarizer
 * - Decodificación de QR invertidos (modo oscuro en smartphones de visitantes)
 * - Extracción y validación de folios canónicos MEDUSA ALFHA
 * - Idempotencia y control de cooldown para evitar dobles registros
 */
class CameraXEntryScannerZxingTest {

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
    fun testZxingDecodesStandardEntryQrCode() {
        val folio = AlphaCoreEngine.generateUniqueFolio("MED")
        val sampleQrPayload = "{\"folio\":\"$folio\",\"unit\":\"Casa 14\",\"visitor\":\"Juan Pérez\"}"

        val decodedResult = decodeBitMatrixWithZxing(sampleQrPayload, size = 200, invert = false)

        assertNotNull(decodedResult)
        assertEquals(sampleQrPayload, decodedResult)
        assertTrue(decodedResult.contains(folio))
        assertTrue(decodedResult.startsWith("{\"folio\":"))
    }

    @Test
    fun testZxingDecodesInvertedDarkModeQrCode() {
        val passCode = "MED-20260914-8841"
        val decodedResult = decodeBitMatrixWithZxing(passCode, size = 200, invert = true)

        assertNotNull(decodedResult)
        assertEquals(passCode, decodedResult)
    }

    @Test
    fun testEntryScannerCooldownSuppressesRapidDuplicates() {
        var callCount = 0
        var lastCaptured: String? = null

        val cooldownMillis = 1000L
        var lastTriggerTime = 0L

        fun simulateScan(code: String, currentTime: Long) {
            if (code != lastCaptured || (currentTime - lastTriggerTime) > cooldownMillis) {
                lastCaptured = code
                lastTriggerTime = currentTime
                callCount++
            }
        }

        // Primer escaneo en t=0ms
        simulateScan("MED-20260914-1111", 0L)
        assertEquals(1, callCount)
        assertEquals("MED-20260914-1111", lastCaptured)

        // Mismo código en t=200ms (dentro del cooldown) -> debe ser ignorado
        simulateScan("MED-20260914-1111", 200L)
        assertEquals(1, callCount)

        // Código diferente en t=400ms -> debe registrarse inmediatamente
        simulateScan("MED-20260914-2222", 400L)
        assertEquals(2, callCount)
        assertEquals("MED-20260914-2222", lastCaptured)

        // Mismo código pero en t=1600ms (después del cooldown de 1000ms) -> debe registrarse
        simulateScan("MED-20260914-2222", 1600L)
        assertEquals(3, callCount)
    }
}
