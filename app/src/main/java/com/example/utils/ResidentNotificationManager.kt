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

    const val EMERGENCY_CHANNEL_ID = "security_emergency_sos_channel"
    private const val EMERGENCY_CHANNEL_NAME = "🚨 Alertas Críticas de Emergencia S.O.S."
    private const val EMERGENCY_CHANNEL_DESC = "Alertas de máxima prioridad enviadas por residentes a personal de seguridad con ubicación GPS"

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
        createEmergencyNotificationChannel(context)
    }

    fun createEmergencyNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val emgChannel = NotificationChannel(
                EMERGENCY_CHANNEL_ID,
                EMERGENCY_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = EMERGENCY_CHANNEL_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800)
                enableLights(true)
                lightColor = android.graphics.Color.RED
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(emgChannel)
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

    fun notifySecurityEmergencyAlert(
        context: Context,
        payload: com.example.data.fcm.EmergencyAlertFcmPayload
    ) {
        createEmergencyNotificationChannel(context)
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "SECURITY_SCANNER")
            putExtra("EXTRA_NOTIFICATION_TYPE", "RESIDENT_EMERGENCY_ALERT")
            putExtra("EMERGENCY_FOLIO", payload.alertFolio)
            putExtra("RESIDENT_UNIT", payload.residentUnit)
            putExtra("RESIDENT_NAME", payload.residentName)
            putExtra("EMERGENCY_TYPE", payload.emergencyType)
            putExtra("LATITUDE", payload.latitude ?: 0.0)
            putExtra("LONGITUDE", payload.longitude ?: 0.0)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(payload.timestampMillis))
        val coordsStr = if (payload.latitude != null && payload.longitude != null) {
            "Lat: ${String.format(Locale.US, "%.5f", payload.latitude)}, Lon: ${String.format(Locale.US, "%.5f", payload.longitude)} (±${payload.gpsAccuracyMeters?.toInt() ?: 15}m)"
        } else {
            "Ubicación por Padrón de Unidad Habitacional"
        }

        val bigText = """
            🚨 ALERTA CRÍTICA DE EMERGENCIA
            • Unidad Habitacional: ${payload.residentUnit}
            • Residente: ${payload.residentName}
            • Tipo: ${payload.emergencyType}
            • Ubicación: $coordsStr
            • Estatus GPS: ${payload.locationStatus}
            • Hora: $timeStr hrs
            • Folio de Auditoría: ${payload.alertFolio}
            ${if (payload.details.isNotBlank()) "• Detalle: ${payload.details}" else ""}
            
            ⚠️ ACCIÓN REQUERIDA: Despachar patrulla táctica de inmediato a ${payload.residentUnit}.
        """.trimIndent()

        val notification = NotificationCompat.Builder(context, EMERGENCY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("🚨 ¡EMERGENCIA EN ${payload.residentUnit}! - ${payload.emergencyType}")
            .setContentText("Residente ${payload.residentName} solicita auxilio en ${payload.residentUnit} ($coordsStr)")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 800))
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(notificationId, notification)
    }

    /**
     * Notificación Push en tiempo real enviada desde la Caseta al Residente
     * solicitando autorización de acceso con fotos tomadas en garita.
     */
    fun notifyAccessAuthorizationRequest(
        context: Context,
        requestId: String,
        visitorName: String,
        accessType: String,
        destinationHouse: String,
        vehiclePlate: String? = null,
        hasIdPhoto: Boolean = false,
        hasVehiclePhoto: Boolean = false
    ) {
        createNotificationChannel(context)
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "RESIDENT_DASHBOARD")
            putExtra("EXTRA_REQUEST_ID", requestId)
            putExtra("EXTRA_NOTIFICATION_TYPE", "ACCESS_AUTHORIZATION_REQUEST")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val photosSummary = buildString {
            if (hasIdPhoto && hasVehiclePhoto) append(" • 📸 2 fotos adjuntas (INE y Vehículo/Placas)")
            else if (hasIdPhoto) append(" • 📸 Foto de INE adjunta")
            else if (hasVehiclePhoto) append(" • 🚗 Foto de Vehículo adjunta")
        }

        val plateInfo = if (!vehiclePlate.isNullOrBlank()) " • Placas: $vehiclePlate" else ""
        val bigText = """
            🚨 SOLICITUD DE ACCESO EN GARITA PRINCIPAL
            • Visitante: $visitorName
            • Tipo: $accessType
            • Destino: $destinationHouse$plateInfo$photosSummary
            
            ⚠️ Acción Requerida: Abre la app para autorizar o negar el acceso en 1 toque.
        """.trimIndent()

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🚨 Visita en Garita para $destinationHouse")
            .setContentText("$visitorName ($accessType) solicita ingreso")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVibrate(longArrayOf(0, 300, 150, 300))
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(notificationId, notification)
    }

    /**
     * Notificación del resultado de la autorización enviada a la caseta / sistema.
     */
    fun notifyAccessDecisionResult(
        context: Context,
        requestId: String,
        visitorName: String,
        destinationHouse: String,
        authorized: Boolean
    ) {
        createNotificationChannel(context)
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "CASETA")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (authorized) "🟢 Acceso AUTORIZADO - $destinationHouse" else "🔴 Acceso DENEGADO - $destinationHouse"
        val message = if (authorized) {
            "El residente de $destinationHouse AUTORIZÓ el ingreso de $visitorName. Proceder a abrir pluma."
        } else {
            "El residente de $destinationHouse DENEGÓ el ingreso de $visitorName. No permitir acceso."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(if (authorized) android.R.drawable.ic_dialog_info else android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(notificationId, notification)
    }
}
