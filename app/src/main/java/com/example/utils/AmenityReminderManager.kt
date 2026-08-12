package com.example.utils

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.booking.AmenityBooking
import com.example.data.booking.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AmenityReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val bookingId = intent.getLongExtra("BOOKING_ID", -1L)
        val amenityName = intent.getStringExtra("AMENITY_NAME") ?: "Amenidad"
        val residentName = intent.getStringExtra("RESIDENT_NAME") ?: "Residente"
        val unitId = intent.getStringExtra("UNIT_ID") ?: "Unidad"
        val bookingTimeMillis = intent.getLongExtra("BOOKING_TIME", System.currentTimeMillis())

        if (bookingId != -1L) {
            AmenityReminderManager.send15MinReminderNotification(
                context = context,
                bookingId = bookingId,
                amenityName = amenityName,
                residentName = residentName,
                unitId = unitId,
                bookingTimeMillis = bookingTimeMillis
            )

            // Mark in Room database that reminder has been sent
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    db.amenityBookingDao().markReminderSent(bookingId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}

object AmenityReminderManager {
    const val CHANNEL_ID = "amenity_booking_reminders"
    private const val CHANNEL_NAME = "Recordatorios de Amenidades"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones enviadas 15 minutos antes del inicio de una reserva de amenidad"
                enableVibration(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun schedule15MinReminder(context: Context, booking: AmenityBooking) {
        createNotificationChannel(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AmenityReminderReceiver::class.java).apply {
            putExtra("BOOKING_ID", booking.id)
            putExtra("AMENITY_NAME", booking.amenityName)
            putExtra("RESIDENT_NAME", booking.residentName)
            putExtra("UNIT_ID", booking.unitId)
            putExtra("BOOKING_TIME", booking.bookingTimeMillis)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            booking.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 15 minutes before booking time
        val triggerAtMillis = booking.bookingTimeMillis - (15 * 60 * 1000)

        // If trigger time is in future, set exact alarm
        if (triggerAtMillis > System.currentTimeMillis()) {
            try {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } catch (e: Exception) {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        }
    }

    fun send15MinReminderNotification(
        context: Context,
        bookingId: Long,
        amenityName: String,
        residentName: String,
        unitId: String,
        bookingTimeMillis: Long
    ) {
        createNotificationChannel(context)

        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(bookingTimeMillis))

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            bookingId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("⏰ Recordatorio de Reserva (En 15 min)")
            .setContentText("$amenityName iniciará a las $timeStr h para $residentName ($unitId)")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Su reserva en $amenityName para $residentName ($unitId) comenzará a las $timeStr hrs (en 15 minutos). Por favor diríjase al control de garita o punto de acceso."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(bookingId.toInt(), notification)
    }

    fun cancelReminder(context: Context, bookingId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AmenityReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            bookingId.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
