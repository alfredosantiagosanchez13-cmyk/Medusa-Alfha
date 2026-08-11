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

class QrCodeAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
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

            try {
                val result = reader.decodeWithState(binaryBitmap)
                val qrText = result.text
                val currentTime = System.currentTimeMillis()

                if (!qrText.isNullOrBlank() && (qrText != lastScannedText || currentTime - lastScannedTimestamp > 1500)) {
                    lastScannedText = qrText
                    lastScannedTimestamp = currentTime
                    onQrCodeScanned(qrText)
                }
            } catch (_: NotFoundException) {
                // No QR code found in current frame
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
