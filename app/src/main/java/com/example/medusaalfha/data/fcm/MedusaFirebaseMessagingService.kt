package com.example.medusaalfha.data.fcm

import android.util.Log
import com.example.medusaalfha.data.model.AlertType
import com.example.medusaalfha.data.model.ResidentAlert
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Servicio FCM para recepción en segundo plano y primer plano de alertas para residentes.
 */
class MedusaFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nuevo FCM Token de dispositivo registrado: $token")
        registerTokenToFirestore(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Mensaje FCM recibido de: ${remoteMessage.from}")

        val data = remoteMessage.data
        val notification = remoteMessage.notification

        val title = data["title"] ?: notification?.title ?: "Notificación de Caseta"
        val message = data["message"] ?: notification?.body ?: "Nueva actividad registrada en el condominio"
        val rawType = data["type"] ?: "VISITOR_ARRIVAL"
        val targetHouse = data["targetHouse"] ?: "Casa 54 · Circuito Los Álamos"

        val alertType = try {
            AlertType.valueOf(rawType)
        } catch (_: Exception) {
            AlertType.VISITOR_ARRIVAL
        }

        val alert = ResidentAlert(
            id = data["id"] ?: "ALT-${System.currentTimeMillis() % 100000}",
            type = alertType,
            title = title,
            message = message,
            targetHouse = targetHouse,
            timestampMillis = System.currentTimeMillis(),
            metadata = data
        )

        // Mostrar notificación local en la bandeja del sistema
        ResidentNotificationHelper.showLocalNotification(applicationContext, alert)
    }

    private fun registerTokenToFirestore(token: String) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val tokenData = mapOf(
                "token" to token,
                "registeredAt" to System.currentTimeMillis(),
                "devicePlatform" to "android",
                "condominiumId" to "PRADOS_1"
            )
            firestore.collection("condominiums")
                .document("PRADOS_1")
                .collection("device_tokens")
                .document(token)
                .set(tokenData)
                .addOnSuccessListener {
                    Log.d(TAG, "Token FCM sincronizado exitosamente con Cloud Firestore.")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Advertencia: no se pudo guardar token FCM en Firestore: ${e.message}")
                }
        } catch (e: Exception) {
            Log.w(TAG, "Error registrando token FCM: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "MedusaFCMService"
    }
}
