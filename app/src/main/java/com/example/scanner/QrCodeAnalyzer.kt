package com.example.scanner

import android.graphics.ImageFormat
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.RGBLuminanceSource

class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(
                BarcodeFormat.QR_CODE,
                BarcodeFormat.DATA_MATRIX,
                BarcodeFormat.AZTEC,
                BarcodeFormat.CODE_128
            ),
            DecodeHintType.TRY_HARDER to java.lang.Boolean.TRUE,
            DecodeHintType.CHARACTER_SET to "UTF-8"
        )
        setHints(hints)
    }

    private var lastScannedText: String? = null
    private var lastScannedTimestamp: Long = 0L

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null && (
                    mediaImage.format == ImageFormat.YUV_420_888 ||
                    mediaImage.format == ImageFormat.YUV_422_888 ||
                    mediaImage.format == ImageFormat.YUV_444_888
                )
        ) {
            try {
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                val plane = mediaImage.planes[0]
                val width = mediaImage.width
                val height = mediaImage.height

                val rawYData = extractYPlaneData(plane, width, height)
                val rotatedYData = rotateYData(rawYData, width, height, rotationDegrees)

                val (finalWidth, finalHeight) = if (rotationDegrees == 90 || rotationDegrees == 270) {
                    Pair(height, width)
                } else {
                    Pair(width, height)
                }

                val source = PlanarYUVLuminanceSource(
                    rotatedYData,
                    finalWidth,
                    finalHeight,
                    0,
                    0,
                    finalWidth,
                    finalHeight,
                    false
                )
                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

                var decodedText: String? = null
                try {
                    val result = reader.decodeWithState(binaryBitmap)
                    decodedText = result.text
                } catch (_: NotFoundException) {
                    // Try inverted luminance source for dark-mode inverted QR codes
                    try {
                        val invertedBitmap = BinaryBitmap(HybridBinarizer(source.invert()))
                        val result = reader.decodeWithState(invertedBitmap)
                        decodedText = result.text
                    } catch (_: Exception) {
                        // Not found in this frame
                    }
                }

                val currentTime = System.currentTimeMillis()
                if (!decodedText.isNullOrBlank() && (decodedText != lastScannedText || currentTime - lastScannedTimestamp > 1200)) {
                    lastScannedText = decodedText
                    lastScannedTimestamp = currentTime
                    onQrCodeScanned(decodedText)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                reader.reset()
            }
        }
        imageProxy.close()
    }

    private fun extractYPlaneData(plane: android.media.Image.Plane, width: Int, height: Int): ByteArray {
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        if (rowStride == width) {
            buffer.rewind()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            return bytes
        }
        val bytes = ByteArray(width * height)
        val rowBuffer = ByteArray(rowStride)
        buffer.rewind()
        for (row in 0 until height) {
            val remaining = buffer.remaining()
            if (remaining >= rowStride) {
                buffer.get(rowBuffer, 0, rowStride)
                System.arraycopy(rowBuffer, 0, bytes, row * width, width)
            } else if (remaining > 0) {
                buffer.get(rowBuffer, 0, remaining)
                System.arraycopy(rowBuffer, 0, bytes, row * width, width.coerceAtMost(remaining))
            }
        }
        return bytes
    }

    private fun rotateYData(data: ByteArray, width: Int, height: Int, rotation: Int): ByteArray {
        if (rotation == 0) return data
        val rotated = ByteArray(data.size)
        when (rotation) {
            90 -> {
                var i = 0
                for (x in 0 until width) {
                    for (y in height - 1 downTo 0) {
                        rotated[i++] = data[y * width + x]
                    }
                }
            }
            180 -> {
                for (i in data.indices) {
                    rotated[data.size - 1 - i] = data[i]
                }
            }
            270 -> {
                var i = 0
                for (x in width - 1 downTo 0) {
                    for (y in 0 until height) {
                        rotated[i++] = data[y * width + x]
                    }
                }
            }
            else -> return data
        }
        return rotated
    }
}
