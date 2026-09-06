package com.example.data.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.booking.AppDatabase
import com.example.data.notifications.SmartNotificationHub
import com.example.utils.ResidentNotificationManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Servicio de Firebase Cloud Messaging para MEDUSA ALFHA.
 * Recibe en tiempo real notificaciones push de FCM cuando el personal de
 * seguridad escanea con éxito el código QR de una visita en caseta.
 */
class MedusaFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "MedusaFcmService"
        const val FCM_CHANNEL_ID = ResidentNotificationManager.CHANNEL_ID
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "🔥 Nuevo FCM Registration Token recibido: $token")
        
        // Guardar token en el gestor y sincronizar con Firestore
        serviceScope.launch {
            try {
                FcmNotificationManager.onNewFcmTokenReceived(applicationContext, token)
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando nuevo token FCM: ${e.message}", e)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.i(TAG, "📩 Mensaje Push FCM recibido desde: ${remoteMessage.from}")

        val data = remoteMessage.data
        val notification = remoteMessage.notification

        // Extraer campos estructurados del mensaje de escaneo de visita
        val messageType = data["type"] ?: "VISITOR_QR_SCANNED"
        val guestName = data["guestName"] ?: notification?.title?.replace("🔔 ¡Tu Visita ha Ingresado!", "")?.trim() ?: "Visita Autorizada"
        val unitId = data["targetUnitId"] ?: data["destinationHouse"] ?: ""
        val hostResidentName = data["hostResidentName"] ?: "Estimado Residente"
        val passTypeLabel = data["passTypeLabel"] ?: "Visita General"
        val vehiclePlate = data["vehiclePlate"]?.takeIf { it.isNotBlank() }
        val passFolio = data["passFolio"] ?: "VIS-${System.currentTimeMillis() % 10000}"
        val passCode = data["passCode"] ?: ""
        val guardName = data["guardName"] ?: "Guardia de Caseta"
        val gateLocation = data["gateLocation"] ?: "Garita Principal"
        val guardNotes = data["guardNotes"] ?: "Acceso verificado por personal de seguridad con escáner CameraX"

        val title = notification?.title ?: data["title"] ?: "🔔 ¡Tu Visita ha Ingresado! ($unitId)"
        val body = notification?.body ?: data["body"] ?: run {
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val plateStr = if (!vehiclePlate.isNullOrBlank()) " • Patente: $vehiclePlate" else ""
            "Hola $hostResidentName, su visita $guestName ($passTypeLabel) acaba de ingresar por $gateLocation a las $timeStr hrs.$plateStr"
        }

        val payload = VisitorCheckInFcmPayload(
            type = messageType,
            event = data["event"] ?: "VISITOR_CHECK_IN",
            passFolio = passFolio,
            passCode = passCode,
            guestName = guestName,
            guestDocument = data["guestDocument"] ?: "",
            targetUnitId = unitId,
            hostResidentName = hostResidentName,
            passTypeLabel = passTypeLabel,
            vehiclePlate = vehiclePlate,
            guardName = guardName,
            gateLocation = gateLocation,
            guardNotes = guardNotes,
            scanTimestamp = data["scanTimestamp"]?.toLongOrNull() ?: System.currentTimeMillis(),
            condominiumId = data["condominiumId"] ?: "Los Prados Residencial",
            title = title,
            body = body
        )

        // Registrar en el gestor de estado para actualización visual inmediata en Composable
        FcmNotificationManager.notifyPayloadReceivedLocally(payload)

        // 1. Despachar notificación visible del sistema Android
        displaySystemNotification(payload)

        // 2. Persistir en base de datos local Room y hub inteligente
        serviceScope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                SmartNotificationHub.notifyVisitorEntry(
                    context = applicationContext,
                    db = db,
                    guestName = guestName,
                    unitId = unitId.ifBlank { "Unidad" },
                    hostResidentName = hostResidentName,
                    passTypeLabel = passTypeLabel,
                    vehiclePlate = vehiclePlate,
                    passFolio = passFolio
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error persistiendo notificación FCM en Room: ${e.message}")
            }
        }
    }

    private fun displaySystemNotification(payload: VisitorCheckInFcmPayload) {
        try {
            ResidentNotificationManager.createNotificationChannel(applicationContext)

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()

            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("NAVIGATE_TO", "RESIDENT_DASHBOARD")
                putExtra("EXTRA_NOTIFICATION_TYPE", "VISITOR_CHECK_IN")
                putExtra("VISITOR_NAME", payload.guestName)
                putExtra("DESTINATION_HOUSE", payload.targetUnitId)
                putExtra("PASS_FOLIO", payload.passFolio)
            }

            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(payload.scanTimestamp))
            val vehicleInfo = if (!payload.vehiclePlate.isNullOrBlank()) " • Patente: ${payload.vehiclePlate}" else ""
            val bigText = """
                Estimado/a ${payload.hostResidentName}:
                Su visita ${payload.guestName} (${payload.passTypeLabel}) ha sido verificada exitosamente e ingresó por ${payload.gateLocation} hacia ${payload.targetUnitId} a las $timeStr hrs.$vehicleInfo
                
                👮 Verificado por: ${payload.guardName}
                📝 Folio: ${payload.passFolio}
                💬 Nota: ${payload.guardNotes}
            """.trimIndent()

            val notification = NotificationCompat.Builder(applicationContext, FCM_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(payload.title)
                .setContentText("${payload.guestName} ingresó por caseta hacia ${payload.targetUnitId}")
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 300, 150, 300, 150, 450))
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build()

            notificationManager.notify(notificationId, notification)
            Log.i(TAG, "🔔 Notificación del sistema disparada con éxito para visita: ${payload.guestName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error mostrando notificación FCM: ${e.message}", e)
        }
    }
}
