package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfReportExporter {

    fun generateMonthlyAuditPdf(context: Context, timeframe: String): File {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size: 595 x 842 pt
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint().apply {
            isAntiAlias = true
        }

        // Color Palette
        val navyHeader = Color.parseColor("#0B132B")
        val goldAccent = Color.parseColor("#FFD700")
        val cyanText = Color.parseColor("#00E5FF")
        val textDark = Color.parseColor("#1E293B")
        val textMuted = Color.parseColor("#64748B")
        val bgLight = Color.parseColor("#F8FAFC")
        val tableHeaderBg = Color.parseColor("#1E293B")
        val rowEvenBg = Color.parseColor("#F1F5F9")

        // 1. Header Banner
        paint.color = navyHeader
        canvas.drawRect(0f, 0f, 595f, 100f, paint)

        paint.color = goldAccent
        canvas.drawRect(0f, 96f, 595f, 100f, paint)

        // Header Title
        paint.color = Color.WHITE
        paint.textSize = 18f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("MEDUSA SECURITY SYSTEM • CONTROL RESIDENCIAL", 25f, 40f, paint)

        paint.color = goldAccent
        paint.textSize = 12f
        canvas.drawText("REPORTE OFICIAL DE AUDITORÍA Y RESERVA DE AMENIDADES", 25f, 60f, paint)

        paint.color = Color.LTGRAY
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val currentDate = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        canvas.drawText("Período: $timeframe | Emisión: $currentDate | Administrador ID: ADM-2026-809", 25f, 80f, paint)

        var currentY = 125f

        // 2. Executive Summary Metrics Box
        paint.color = bgLight
        canvas.drawRoundRect(25f, currentY, 570f, currentY + 65f, 8f, 8f, paint)

        paint.color = goldAccent
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(25f, currentY, 570f, currentY + 65f, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        paint.color = textDark
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("RESUMEN EJECUTIVO DE CUMPLIMIENTO", 40f, currentY + 22f, paint)

        paint.color = textMuted
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("• Total Accesos Validados: 1,268   • Incidentes de Seguridad: 0   • Tasa de Cumplimiento: 99.8%", 40f, currentY + 40f, paint)
        canvas.drawText("• Amenidad Más Reservada: Quincho & BBQ Principal (38% del total mensual)", 40f, currentY + 53f, paint)

        currentY += 85f

        // 3. Amenity Usage Table Section
        paint.color = textDark
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("1. AUDITORÍA Y USO DE AMENIDADES CONDOMINIO", 25f, currentY, paint)

        currentY += 12f

        // Table Header
        paint.color = tableHeaderBg
        canvas.drawRect(25f, currentY, 570f, currentY + 22f, paint)

        paint.color = Color.WHITE
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("AMENIDAD", 35f, currentY + 15f, paint)
        canvas.drawText("RESERVAS", 230f, currentY + 15f, paint)
        canvas.drawText("OCUPACIÓN", 330f, currentY + 15f, paint)
        canvas.drawText("HORARIO PICO", 440f, currentY + 15f, paint)

        currentY += 22f

        val amenityRows = listOf(
            Triple("Quincho & BBQ Principal", "482 Usos", "38% (Alta)"),
            Triple("Gimnasio Residencial", "342 Usos", "27% (Media-Alta)"),
            Triple("Piscina & Solarium", "228 Usos", "18% (Media)"),
            Triple("Cancha Pádel / Tenis", "152 Usos", "12% (Moderada)"),
            Triple("Co-Work & Sala Eventos", "64 Usos", "5% (Normal)")
        )

        amenityRows.forEachIndexed { index, (name, uses, occ) ->
            paint.color = if (index % 2 == 0) rowEvenBg else Color.WHITE
            canvas.drawRect(25f, currentY, 570f, currentY + 20f, paint)

            paint.color = textDark
            paint.textSize = 9f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(name, 35f, currentY + 14f, paint)
            canvas.drawText(uses, 230f, currentY + 14f, paint)
            canvas.drawText(occ, 330f, currentY + 14f, paint)
            canvas.drawText("17:00 - 21:00 h", 440f, currentY + 14f, paint)

            currentY += 20f
        }

        currentY += 20f

        // 4. Monthly Visitor Log Sample Table Section
        paint.color = textDark
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("2. MUESTRA AUDITADA DE HISTORIAL DE VISITANTES (MES $timeframe)", 25f, currentY, paint)

        currentY += 12f

        // Table Header
        paint.color = tableHeaderBg
        canvas.drawRect(25f, currentY, 570f, currentY + 22f, paint)

        paint.color = Color.WHITE
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("FECHA / HORA", 35f, currentY + 15f, paint)
        canvas.drawText("VISITANTE", 140f, currentY + 15f, paint)
        canvas.drawText("UNIDAD", 260f, currentY + 15f, paint)
        canvas.drawText("CATEGORÍA", 360f, currentY + 15f, paint)
        canvas.drawText("ESTADO", 470f, currentY + 15f, paint)

        currentY += 22f

        val visitorRows = listOf(
            listOf("12/08 07:15", "Juan Pérez", "Casa 104", "DELIVERY", "VALIDADO 🟢"),
            listOf("11/08 19:40", "María Elena Costa", "Depto 302", "FAMILIAR", "VALIDADO 🟢"),
            listOf("11/08 14:20", "TechServ Chile SpA", "Casa 208", "SERVICIOS", "VALIDADO 🟢"),
            listOf("10/08 18:05", "Roberto Alarcón", "Quincho BBQ", "EVENTO", "VALIDADO 🟢"),
            listOf("10/08 11:30", "Uber Driver #402", "Casa 115", "TRANSPORTE", "VALIDADO 🟢"),
            listOf("09/08 21:10", "Ignacio Soto", "Depto 101", "AMIGO", "VALIDADO 🟢")
        )

        visitorRows.forEachIndexed { index, row ->
            paint.color = if (index % 2 == 0) rowEvenBg else Color.WHITE
            canvas.drawRect(25f, currentY, 570f, currentY + 20f, paint)

            paint.color = textDark
            paint.textSize = 8.5f
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(row[0], 35f, currentY + 14f, paint)
            canvas.drawText(row[1], 140f, currentY + 14f, paint)
            canvas.drawText(row[2], 260f, currentY + 14f, paint)
            canvas.drawText(row[3], 360f, currentY + 14f, paint)

            paint.color = Color.parseColor("#16A34A")
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(row[4], 470f, currentY + 14f, paint)

            currentY += 20f
        }

        currentY += 40f

        // 5. Signatures and Certification Section
        paint.color = textMuted
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f

        // Line 1: Admin Signature
        canvas.drawLine(50f, currentY + 30f, 220f, currentY + 30f, paint)
        // Line 2: Security Chief Signature
        canvas.drawLine(375f, currentY + 30f, 545f, currentY + 30f, paint)

        paint.style = Paint.Style.FILL
        paint.color = textDark
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("FIRMA ADMINISTRADOR", 75f, currentY + 45f, paint)
        canvas.drawText("FIRMA JEFE DE SEGURIDAD", 390f, currentY + 45f, paint)

        paint.color = textMuted
        paint.textSize = 8f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Administración Condominio Medusa", 60f, currentY + 58f, paint)
        canvas.drawText("Control de Accesos & Garita Central", 385f, currentY + 58f, paint)

        // Page Footer
        paint.color = navyHeader
        canvas.drawRect(0f, 815f, 595f, 842f, paint)

        paint.color = Color.WHITE
        paint.textSize = 8f
        canvas.drawText("Documento de Auditoría Generado por Medusa Security App • Licencia Institucional • Página 1 de 1", 100f, 830f, paint)

        pdfDocument.finishPage(page)

        // Output File Creation
        val file = File(context.cacheDir, "Reporte_Auditoria_Medusa.pdf")
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        return file
    }

    fun sharePdf(context: Context, pdfFile: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, pdfFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Reporte Mensual de Auditoría y Amenidades - Medusa Security")
                putExtra(Intent.EXTRA_TEXT, "Adjunto reporte en formato PDF correspondiente al control mensual de visitas y reservas de amenidades.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Compartir Reporte PDF con..."))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al compartir PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun viewPdf(context: Context, pdfFile: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri: Uri = FileProvider.getUriForFile(context, authority, pdfFile)

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(viewIntent, "Abrir Reporte PDF con..."))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No hay visor PDF instalado. Use la opción compartir.", Toast.LENGTH_LONG).show()
        }
    }
}
