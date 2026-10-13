package com.example.medusaalfha.data.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer
import java.util.EnumMap

/**
 * ImageAnalysis.Analyzer de CameraX para procesar frames y decodificar códigos QR
 * en tiempo real de pases de visitantes y credenciales de acceso.
 */
class QrCodeImageAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.POSSIBLE_FORMATS, listOf(com.google.zxing.BarcodeFormat.QR_CODE))
            put(DecodeHintType.TRY_HARDER, java.lang.Boolean.TRUE)
            put(DecodeHintType.CHARACTER_SET, "UTF-8")
        }
        setHints(hints)
    }

    private var lastScannedTimestamp = 0L

    override fun analyze(image: ImageProxy) {
        val currentTimestamp = System.currentTimeMillis()
        // Limitar la cadencia de escaneo para evitar ráfagas duplicadas
        if (currentTimestamp - lastScannedTimestamp < 1200) {
            image.close()
            return
        }

        val buffer: ByteBuffer = image.planes[0].buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)

        val width = image.width
        val height = image.height

        val source = PlanarYUVLuminanceSource(
            data,
            width,
            height,
            0,
            0,
            width,
            height,
            false
        )

        val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

        try {
            val result = reader.decodeWithState(binaryBitmap)
            val qrText = result.text?.trim()
            if (!qrText.isNullOrEmpty()) {
                lastScannedTimestamp = currentTimestamp
                onQrCodeScanned(qrText)
            }
        } catch (_: Exception) {
            // Frame sin código QR legible
        } finally {
            reader.reset()
            image.close()
        }
    }
}
