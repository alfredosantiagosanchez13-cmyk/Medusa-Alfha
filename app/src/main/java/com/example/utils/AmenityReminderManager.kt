package com.example.utils

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
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
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.i("AmenityReminderReceiver", "Reinicio de dispositivo detectado: reprogramando recordatorios de áreas comunes...")
            AmenityReminderManager.rescheduleAllUpcomingReminders(context)
            return
        }

        val bookingId = intent.getLongExtra("BOOKING_ID", -1L)
        val amenityName = intent.getStringExtra("AMENITY_NAME") ?: "Área Común"
        val residentName = intent.getStringExtra("RESIDENT_NAME") ?: "Residente"
        val unitId = intent.getStringExtra("UNIT_ID") ?: "Unidad"
        val bookingTimeMillis = intent.getLongExtra("BOOKING_TIME", System.currentTimeMillis())
        val reminderType = intent.getStringExtra("REMINDER_TYPE") ?: "ONE_HOUR"

        Log.i("AmenityReminderReceiver", "Alarma recibida para reserva #$bookingId en $amenityName ($reminderType)")

        if (bookingId != -1L) {
            when (reminderType) {
                "ONE_HOUR" -> {
                    AmenityReminderManager.sendOneHourReminderNotification(
                        context = context,
                        bookingId = bookingId,
                        amenityName = amenityName,
                        residentName = residentName,
                        unitId = unitId,
                        bookingTimeMillis = bookingTimeMillis
                    )
                }
                else -> {
                    AmenityReminderManager.send15MinReminderNotification(
                        context = context,
                        bookingId = bookingId,
                        amenityName = amenityName,
                        residentName = residentName,
                        unitId = unitId,
                        bookingTimeMillis = bookingTimeMillis
                    )
                }
            }

            // Marcar en Room que el recordatorio fue despachado
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    db.amenityBookingDao().markReminderSent(bookingId)
                } catch (e: Exception) {
                    Log.e("AmenityReminderReceiver", "Error actualizando recordatorio en DB: ${e.message}")
                }
            }
        }
    }
}

/**
 * Programador local de notificaciones para reservas de áreas comunes (Common Area Slots).
 * Activa recordatorios locales mediante AlarmManager exactamente 1 hora antes del turno.
 */
object AmenityReminderManager {
    const val CHANNEL_ID = "amenity_booking_reminders"
    private const val CHANNEL_NAME = "Recordatorios de Áreas Comunes"
    private const val TAG = "AmenityReminderManager"

    fun getOneHourPendingIntentId(bookingId: Long): Int =
        ((bookingId.hashCode() and 0xFFFF) * 10 + 1)

    fun get15MinPendingIntentId(bookingId: Long): Int =
        ((bookingId.hashCode() and 0xFFFF) * 10 + 2)

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones automáticas enviadas 1 hora antes de su reserva de área común"
                enableVibration(true)
                enableLights(true)
                setShowBadge(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Programa una alarma local exactamente 1 hora antes (60 minutos) del turno de área común.
     */
    fun scheduleOneHourReminder(context: Context, booking: AmenityBooking) {
        createNotificationChannel(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: run {
            Log.e(TAG, "AlarmManager no disponible en el sistema")
            return
        }

        val intent = Intent(context, AmenityReminderReceiver::class.java).apply {
            putExtra("BOOKING_ID", booking.id)
            putExtra("AMENITY_NAME", booking.amenityName)
            putExtra("RESIDENT_NAME", booking.residentName)
            putExtra("UNIT_ID", booking.unitId)
            putExtra("BOOKING_TIME", booking.bookingTimeMillis)
            putExtra("REMINDER_TYPE", "ONE_HOUR")
        }

        val requestCode = getOneHourPendingIntentId(booking.id)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Exactamente 1 hora antes (60 min = 3,600,000 milisegundos)
        val triggerAtMillis = booking.bookingTimeMillis - (60 * 60 * 1000L)
        val now = System.currentTimeMillis()

        if (triggerAtMillis > now) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
                val timeStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(triggerAtMillis))
                Log.i(TAG, "⏰ Recordatorio de 1 hora programado para el área común '${booking.amenityName}' a las $timeStr")
            } catch (e: Exception) {
                Log.w(TAG, "Aviso de permiso de alarma exacta: ${e.message}. Usando set estándar...")
                try {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } catch (ex: Exception) {
                    Log.e(TAG, "Fallo al programar alarma: ${ex.message}", ex)
                }
            }
        } else if (booking.bookingTimeMillis > now) {
            // El turno inicia en menos de 1 hora pero aún en el futuro.
            // Programar disparo inmediato (en 2 segundos) para alertar al residente.
            Log.i(TAG, "El turno inicia en menos de 1 hora. Programando aviso prioritario inmediato.")
            try {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    now + 2000L,
                    pendingIntent
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error en disparo inmediato: ${e.message}")
            }
        }
    }

    /**
     * Mantiene retrocompatibilidad con el recordatorio de 15 minutos.
     */
    fun schedule15MinReminder(context: Context, booking: AmenityBooking) {
        createNotificationChannel(context)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AmenityReminderReceiver::class.java).apply {
            putExtra("BOOKING_ID", booking.id)
            putExtra("AMENITY_NAME", booking.amenityName)
            putExtra("RESIDENT_NAME", booking.residentName)
            putExtra("UNIT_ID", booking.unitId)
            putExtra("BOOKING_TIME", booking.bookingTimeMillis)
            putExtra("REMINDER_TYPE", "15_MIN")
        }

        val requestCode = get15MinPendingIntentId(booking.id)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAtMillis = booking.bookingTimeMillis - (15 * 60 * 1000L)
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

    /**
     * Construye y despacha la notificación local visual de 1 hora antes.
     */
    fun sendOneHourReminderNotification(
        context: Context,
        bookingId: Long,
        amenityName: String,
        residentName: String,
        unitId: String,
        bookingTimeMillis: Long
    ) {
        createNotificationChannel(context)

        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(bookingTimeMillis))
        val dateStr = SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(bookingTimeMillis))

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            getOneHourPendingIntentId(bookingId),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "⏰ Recordatorio: $amenityName en 1 Hora"
        val shortContent = "Su turno en $amenityName para $residentName ($unitId) comienza a las $timeStr hrs."
        val bigText = "🔔 RECORDATORIO DE ÁREA COMÚN (EN 1 HORA)\n\n" +
                "Estimado/a $residentName ($unitId):\n" +
                "Su turno reservado en el área común '$amenityName' comenzará en 1 hora (a las $timeStr hrs, $dateStr).\n\n" +
                "📌 Puntos a tener en cuenta:\n" +
                "• Su código QR o comprobante de reserva estará disponible en el portal.\n" +
                "• Por favor recuerde respetar las normas del área común y el aforo permitido.\n" +
                "• La caseta de seguridad registrará su acceso puntualmente."

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(shortContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(getOneHourPendingIntentId(bookingId), notification)

        // Registrar en Room database que reminderSent = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                db.amenityBookingDao().markReminderSent(bookingId)
            } catch (e: Exception) {
                Log.e(TAG, "Error actualizando reminderSent en DB: ${e.message}")
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
            get15MinPendingIntentId(bookingId),
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

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(get15MinPendingIntentId(bookingId), notification)
    }

    /**
     * Cancela cualquier alarma programada (tanto de 1 hora como de 15 minutos) para esta reserva.
     */
    fun cancelReminder(context: Context, bookingId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AmenityReminderReceiver::class.java)

        // Cancelar alarma de 1 hora
        val pIntent1h = PendingIntent.getBroadcast(
            context,
            getOneHourPendingIntentId(bookingId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pIntent1h != null) {
            alarmManager.cancel(pIntent1h)
            pIntent1h.cancel()
        }

        // Cancelar alarma de 15 minutos
        val pIntent15m = PendingIntent.getBroadcast(
            context,
            get15MinPendingIntentId(bookingId),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pIntent15m != null) {
            alarmManager.cancel(pIntent15m)
            pIntent15m.cancel()
        }
        Log.i(TAG, "Recordatorios locales cancelados para la reserva #$bookingId")
    }

    /**
     * Reprograma todos los recordatorios futuros de áreas comunes al iniciar la app o reiniciar el sistema.
     */
    fun rescheduleAllUpcomingReminders(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val allBookings = db.amenityBookingDao().getAllBookingsList()
                val now = System.currentTimeMillis()
                val futureBookings = allBookings.filter {
                    it.status != "CANCELADA" && it.bookingTimeMillis > now
                }
                for (booking in futureBookings) {
                    scheduleOneHourReminder(context, booking)
                }
                Log.i(TAG, "Reprogramados ${futureBookings.size} recordatorios de área común exitosamente.")
            } catch (e: Exception) {
                Log.e(TAG, "Error reprogramando recordatorios: ${e.message}", e)
            }
        }
    }
}

