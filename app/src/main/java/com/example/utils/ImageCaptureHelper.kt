package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utilidad para captura, guardado y compresión ligera de identificaciones oficiales (INE/Pasaporte)
 * y fotos de vehículos y placas para caseta y residentes.
 */
object ImageCaptureHelper {
    private const val TAG = "ImageCaptureHelper"

    /**
     * Guarda un Bitmap capturado en el almacenamiento interno privado de la aplicación
     * y retorna la ruta absoluta del archivo guardado.
     */
    fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap, prefix: String = "capture"): String? {
        return try {
            val dir = File(context.filesDir, "visitor_docs").apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val file = File(dir, "${prefix}_$timestamp.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando bitmap: ${e.message}", e)
            null
        }
    }

    /**
     * Copia un archivo o imagen seleccionado por el usuario (desde WhatsApp, Descargas o Galería)
     * al almacenamiento privado de la app para que esté siempre disponible de manera segura.
     */
    fun copyUriToInternalStorage(context: Context, uri: Uri, prefix: String = "doc_whatsapp"): String? {
        return try {
            val dir = File(context.filesDir, "visitor_docs").apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val file = File(dir, "${prefix}_$timestamp.jpg")

            context.contentResolver.openInputStream(uri)?.use { input ->
                // Decodificar y re-comprimir a un tamaño óptimo para rendimiento
                val originalBitmap = BitmapFactory.decodeStream(input)
                if (originalBitmap != null) {
                    val scaled = downscaleBitmapIfNeeded(originalBitmap, maxDimension = 1280)
                    FileOutputStream(file).use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
                    }
                    file.absolutePath
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copiando URI a almacenamiento interno: ${e.message}", e)
            null
        }
    }

    /**
     * Carga un Bitmap desde una ruta local de archivo de forma eficiente y segura.
     */
    fun loadBitmapFromPath(path: String, maxDimension: Int = 800): Bitmap? {
        return try {
            val file = File(path)
            if (!file.exists()) return null

            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, options)

            var sampleSize = 1
            while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(path, decodeOptions)
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando bitmap desde ruta $path: ${e.message}", e)
            null
        }
    }

    /**
     * Sobrecarga de conveniencia que acepta dimensiones ancho y alto.
     */
    fun loadBitmapFromPath(path: String, width: Int, height: Int): Bitmap? {
        return loadBitmapFromPath(path, kotlin.math.max(width, height))
    }

    /**
     * Escala proporcionalmente un bitmap si excede las dimensiones máximas recomendadas.
     */
    fun downscaleBitmapIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Convierte un bitmap a cadena Base64 compacta para visualización o respaldo en la nube.
     */
    fun bitmapToBase64(bitmap: Bitmap, quality: Int = 75): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        val byteArray = stream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * Decodifica una cadena Base64 a Bitmap.
     */
    fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error decodificando base64 a bitmap: ${e.message}", e)
            null
        }
    }
}
