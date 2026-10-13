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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle(alert.title)
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${alert.message}\nDestino: ${alert.targetHouse}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(alert.id.hashCode(), notification)
            Log.d(TAG, "Notificación mostrada en dispositivo: ${alert.title}")
        } catch (t: Throwable) {
            Log.w(TAG, "No se pudo emitir la notificación: ${t.message}")
        }
    }

    /**
     * Suscribe el dispositivo al tema de su vivienda (ej. "prados_casa_54")
     * Controla excepciones de cuota o rate-limit como TOO_MANY_REGISTRATIONS y evita llamadas repetidas
     * usando caché local en SharedPreferences.
     */
    fun subscribeToHouseTopic(houseRaw: String, context: Context? = null) {
        try {
            if (context != null) {
                val googleApiAvailability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
                val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
                if (resultCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                    Log.i(TAG, "Google Play Services no disponible en este dispositivo/emulador (código: $resultCode). Se omite suscripción FCM.")
                    return
                }

                // Evitar registro repetitivo de tópicos si ya fueron registrados para este houseId
                val prefs = context.getSharedPreferences("fcm_topic_prefs", Context.MODE_PRIVATE)
                val lastSubscribed = prefs.getString("last_subscribed_house", null)
                val lastSubscribedTime = prefs.getLong("last_subscribed_timestamp", 0L)
                val isRecent = (System.currentTimeMillis() - lastSubscribedTime) < (24 * 60 * 60 * 1000) // 24 horas

                if (lastSubscribed == houseRaw && isRecent) {
                    Log.d(TAG, "Tópico FCM ya suscrito recientemente ($houseRaw). Omitiendo solicitud redundante para prevenir TOO_MANY_REGISTRATIONS.")
                    return
                }
            }

            val sanitized = houseRaw.lowercase()
                .replace(Regex("[^a-z0-9_]"), "_")
                .trim('_')
            val topic = "topic_$sanitized"

            val messaging = FirebaseMessaging.getInstance()

            messaging.token
                .addOnSuccessListener { token ->
                    Log.d(TAG, "FCM Token disponible: $token. Suscribiendo a tópico: $topic")
                    messaging.subscribeToTopic(topic)
                        .addOnSuccessListener {
                            Log.d(TAG, "Suscrito exitosamente a FCM topic: $topic")
                            if (context != null) {
                                context.getSharedPreferences("fcm_topic_prefs", Context.MODE_PRIVATE)
                                    .edit()
                                    .putString("last_subscribed_house", houseRaw)
                                    .putLong("last_subscribed_timestamp", System.currentTimeMillis())
                                    .apply()
                            }
                        }
                        .addOnFailureListener { e ->
                            handleFcmFailure("subscribeToTopic($topic)", e)
                        }
                }
                .addOnFailureListener { e ->
                    handleFcmFailure("getToken", e)
                }
        } catch (e: Exception) {
            handleFcmFailure("subscribeToHouseTopic", e)
        }
    }

    private fun handleFcmFailure(operation: String, error: Throwable) {
        val message = error.message ?: error.toString()
        if (message.contains("TOO_MANY_REGISTRATIONS", ignoreCase = true)) {
            Log.w(TAG, "FCM [$operation]: Límite de registros alcanzado en el dispositivo (TOO_MANY_REGISTRATIONS). El sistema opera normalmente mediante Firestore y notificaciones locales.")
        } else if (message.contains("SERVICE_NOT_AVAILABLE", ignoreCase = true)) {
            Log.w(TAG, "FCM [$operation]: Servicio de Google Play no disponible temporalmente. Sincronización diferida.")
        } else {
            Log.w(TAG, "Aviso FCM en [$operation]: $message")
        }
    }
}
