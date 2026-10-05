package com.example.medusaalfha.data.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.medusaalfha.MainActivity
import com.example.medusaalfha.R
import com.example.medusaalfha.data.model.AlertType
import com.example.medusaalfha.data.model.ResidentAlert
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Gestor de Notificaciones del Sistema y Canales de FCM para Residentes.
 */
object ResidentNotificationHelper {
    private const val TAG = "NotificationHelper"
    const val CHANNEL_ID = "medusa_resident_alerts_channel"
    const val CHANNEL_NAME = "Alertas de Residentes y Seguridad"
    const val CHANNEL_DESC = "Notificaciones en tiempo real para ingresos de visitas y reservas de áreas comunes."

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Canal de notificaciones FCM creado.")
        }
    }

    fun showLocalNotification(context: Context, alert: ResidentAlert) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("ALERT_ID", alert.id)
            putExtra("ALERT_TYPE", alert.type.name)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            alert.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val iconRes = when (alert.type) {
            AlertType.VISITOR_ARRIVAL -> R.drawable.ic_launcher_foreground
            AlertType.BOOKING_CONFIRMED -> R.drawable.ic_launcher_foreground
            AlertType.BOOKING_CANCELLED -> R.drawable.ic_launcher_foreground
            else -> R.drawable.ic_launcher_foreground
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(alert.title)
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${alert.message}\nDestino: ${alert.targetHouse}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(alert.id.hashCode(), notification)
        Log.d(TAG, "Notificación mostrada en dispositivo: ${alert.title}")
    }

    /**
     * Suscribe el dispositivo al tema de su vivienda (ej. "prados_casa_54")
     */
    fun subscribeToHouseTopic(houseRaw: String) {
        try {
            val sanitized = houseRaw.lowercase()
                .replace(Regex("[^a-z0-9_]"), "_")
                .trim('_')
            val topic = "topic_$sanitized"
            FirebaseMessaging.getInstance().subscribeToTopic(topic)
                .addOnSuccessListener {
                    Log.d(TAG, "Suscrito exitosamente a FCM topic: $topic")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Error suscribiendo a FCM topic: ${e.message}")
                }

            // Suscribir a alertas generales del condominio
            FirebaseMessaging.getInstance().subscribeToTopic("prados_general_alerts")
        } catch (e: Exception) {
            Log.w(TAG, "Advertencia al suscribir topic: ${e.message}")
        }
    }
}
