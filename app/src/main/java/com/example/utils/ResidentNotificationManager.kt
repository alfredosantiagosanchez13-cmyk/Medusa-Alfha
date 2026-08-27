package com.example.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.scanner.QrPassEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ResidentNotificationManager {

    const val CHANNEL_ID = "resident_visitor_checkin_channel"
    private const val CHANNEL_NAME = "Alertas de Ingreso de Visitas"
    private const val CHANNEL_DESC = "Notificaciones automáticas para residentes cuando su visita ingresa por garita"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun notifyResidentVisitorCheckedIn(
        context: Context,
        pass: QrPassEntity,
        guardNotes: String = "Ingreso Verificado por Control Garita"
    ) {
        // Ensure channel is initialized
        createNotificationChannel(context)

        // Check POST_NOTIFICATIONS permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Permission not yet granted, attempt anyway or log
            }
        }

        val currentTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "HISTORY")
            putExtra("VISITOR_NAME", pass.guestName)
            putExtra("DESTINATION_HOUSE", pass.destinationHouse)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val vehicleInfo = if (!pass.vehiclePlate.isNullOrBlank()) " • Patente: ${pass.vehiclePlate}" else ""
        val bigText = """
            Estimado/a ${pass.hostResidentName}:
            Su visita ${pass.guestName} (${pass.passType.label}) acaba de ser verificada e ingresó por el control de acceso de Garita Principal hacia ${pass.destinationHouse} a las $currentTimeStr hrs.$vehicleInfo
            
            Nota del Guardia: $guardNotes
        """.trimIndent()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🔔 ¡Tu Visita ha Ingresado! (${pass.destinationHouse})")
            .setContentText("${pass.guestName} fue verificado/a en garita para ${pass.hostResidentName}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(notificationId, notification)
    }

    fun notifyCustomVisitorEntry(
        context: Context,
        guestName: String,
        destinationHouse: String,
        hostResidentName: String,
        passTypeLabel: String,
        vehiclePlate: String? = null
    ) {
        createNotificationChannel(context)

        val currentTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val vehicleInfo = if (!vehiclePlate.isNullOrBlank()) " • Patente: $vehiclePlate" else ""
        val bigText = "Hola $hostResidentName, $guestName ($passTypeLabel) ha registrado su ingreso hacia $destinationHouse a las $currentTimeStr hrs.$vehicleInfo"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🔔 Ingreso Registrado: $destinationHouse")
            .setContentText("$guestName ingresó por garita hacia $destinationHouse")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(notificationId, notification)
    }

    fun notifyVisitorDeparted(
        context: Context,
        guestName: String,
        destinationHouse: String,
        hostResidentName: String,
        durationStay: String
    ) {
        createNotificationChannel(context)

        val currentTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bigText = "Estimado/a $hostResidentName, se ha registrado la salida de $guestName de $destinationHouse a las $currentTimeStr hrs. Tiempo de permanencia total: $durationStay."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🚗 Salida Registrada: $destinationHouse")
            .setContentText("$guestName salió del condominio (Permanencia: $durationStay)")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(notificationId, notification)
    }

    fun notifyCriticalIncident(
        context: Context,
        folio: String,
        location: String,
        category: String,
        summary: String
    ) {
        createNotificationChannel(context)

        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bigText = "ALERTA CRÍTICA [$folio]: $category en $location. Resumen: $summary. Personal de seguridad y administración notificados."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("⚠️ Alerta de Seguridad [$folio]")
            .setContentText("$category en $location")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(notificationId, notification)
    }

    fun notifyIncidentStatusChanged(
        context: Context,
        folio: String,
        status: String,
        resolutionSummary: String,
        location: String
    ) {
        createNotificationChannel(context)
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when (status) {
            "EN_ATENCION" -> "🛠️ Incidencia en Atención"
            "RESUELTO" -> "✅ Incidencia Resuelta"
            else -> "📋 Actualización de Incidencia"
        }

        val bigText = "Folio $folio ($location): Estado actualizado a $status. $resolutionSummary"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$statusText [$folio]")
            .setContentText("Estado: $status - $location")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(notificationId, notification)
    }

    fun notifyAmenityBooking(
        context: Context,
        amenityName: String,
        residentName: String,
        unitId: String,
        bookingTimeFormatted: String
    ) {
        createNotificationChannel(context)
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bigText = "Estimado/a $residentName ($unitId): Su reserva para $amenityName ha sido confirmada para el horario $bookingTimeFormatted."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📅 Reserva Confirmada: $amenityName")
            .setContentText("$unitId - $bookingTimeFormatted")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(notificationId, notification)
    }

    fun notifySupervisionClosed(
        context: Context,
        folio: String,
        supervisorName: String,
        checkpointsCount: Int,
        durationMins: Int
    ) {
        createNotificationChannel(context)
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bigText = "Ronda ejecutada por $supervisorName con $checkpointsCount checkpoints verificados en $durationMins min. Informe generado y certificado con SHA-256."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🛡️ Ronda Cerrada [$folio]")
            .setContentText("$supervisorName - $checkpointsCount puntos verificados")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(notificationId, notification)
    }
}
