package com.example.data.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

/**
 * Foreground Service para garantizar ejecución continua e ininterrumpida de rondas Geo-Alpha.
 * Permite que Google Play Services Location API y los eventos de geocercas satelitales
 * funcionen de manera óptima en segundo plano y con la pantalla bloqueada.
 */
class GeoAlphaRoundForegroundService : Service() {

    companion object {
        private const val TAG = "GeoAlphaFgService"
        const val CHANNEL_ID = "geo_alpha_round_tracking_channel"
        const val NOTIFICATION_ID = 8802

        const val ACTION_START = "com.example.medusa.ACTION_START_GEO_ALPHA_SERVICE"
        const val ACTION_STOP = "com.example.medusa.ACTION_STOP_GEO_ALPHA_SERVICE"
        const val ACTION_UPDATE_PROGRESS = "com.example.medusa.ACTION_UPDATE_GEO_ALPHA_PROGRESS"

        const val EXTRA_TOUR_FOLIO = "EXTRA_TOUR_FOLIO"
        const val EXTRA_CONDO_NAME = "EXTRA_CONDO_NAME"
        const val EXTRA_COVERED_COUNT = "EXTRA_COVERED_COUNT"
        const val EXTRA_TOTAL_COUNT = "EXTRA_TOTAL_COUNT"

        fun startService(context: Context, tourFolio: String, condoName: String, totalPoints: Int) {
            val intent = Intent(context, GeoAlphaRoundForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TOUR_FOLIO, tourFolio)
                putExtra(EXTRA_CONDO_NAME, condoName)
                putExtra(EXTRA_TOTAL_COUNT, totalPoints)
                putExtra(EXTRA_COVERED_COUNT, 0)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "No fue posible iniciar Foreground Service: ${e.message}")
            }
        }

        fun updateProgress(context: Context, coveredCount: Int, totalCount: Int) {
            val intent = Intent(context, GeoAlphaRoundForegroundService::class.java).apply {
                action = ACTION_UPDATE_PROGRESS
                putExtra(EXTRA_COVERED_COUNT, coveredCount)
                putExtra(EXTRA_TOTAL_COUNT, totalCount)
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "No fue posible actualizar progreso de Foreground Service: ${e.message}")
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, GeoAlphaRoundForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Error al detener Foreground Service: ${e.message}")
            }
        }
    }

    private var currentTourFolio: String = "GEO-ALPHA"
    private var currentCondoName: String = "Condominio"
    private var currentCovered: Int = 0
    private var currentTotal: Int = 6

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "Deteniendo GeoAlphaRoundForegroundService.")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_UPDATE_PROGRESS -> {
                currentCovered = intent.getIntExtra(EXTRA_COVERED_COUNT, currentCovered)
                currentTotal = intent.getIntExtra(EXTRA_TOTAL_COUNT, currentTotal)
                val notification = buildNotification()
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.notify(NOTIFICATION_ID, notification)
                return START_STICKY
            }

            ACTION_START, null -> {
                currentTourFolio = intent?.getStringExtra(EXTRA_TOUR_FOLIO) ?: currentTourFolio
                currentCondoName = intent?.getStringExtra(EXTRA_CONDO_NAME) ?: currentCondoName
                currentTotal = intent?.getIntExtra(EXTRA_TOTAL_COUNT, currentTotal) ?: currentTotal
                currentCovered = intent?.getIntExtra(EXTRA_COVERED_COUNT, currentCovered) ?: currentCovered

                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                Log.i(TAG, "GeoAlphaRoundForegroundService iniciado para $currentTourFolio ($currentCondoName)")
                return START_STICKY
            }

            else -> return START_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ronda Satelital Geo-Alpha Activa",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene activo el monitoreo de geocercas satelitales en segundo plano"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAVIGATE_TO", "CASETA_HUB")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            openIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val progressText = "$currentCovered de $currentTotal geopuntos cubiertos (${if (currentTotal > 0) (currentCovered * 100 / currentTotal) else 0}%)"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("🛰️ Ronda Geo-Alpha en Curso [$currentTourFolio]")
            .setContentText("$currentCondoName · $progressText")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Monitoreo continuo de geocercas Google Play Services activo en $currentCondoName.\n$progressText.\nEl sistema registra tus pasos automáticamente.")
            )
            .setProgress(currentTotal, currentCovered, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
