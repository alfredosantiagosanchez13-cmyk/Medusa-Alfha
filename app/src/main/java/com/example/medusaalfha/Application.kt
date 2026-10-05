package com.example.medusaalfha

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

/**
 * Inicialización centralizada de la aplicación y servicios Firebase (Auth y Firestore).
 */
class Application : android.app.Application() {

    override fun onCreate() {
        super.onCreate()
        initFirebaseServices()
    }

    private fun initFirebaseServices() {
        try {
            // Inicializar FirebaseApp si aún no está inicializado
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d(TAG, "FirebaseApp inicializado manualmente con éxito.")
            } else {
                Log.d(TAG, "FirebaseApp ya inicializado automáticamente.")
            }

            // Configuración de Firestore con persistencia local
            val firestore = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        .build()
                )
                .build()
            firestore.firestoreSettings = settings
            Log.d(TAG, "Cloud Firestore configurado con caché persistente.")

            // Verificación y configuración de Firebase Auth
            val auth = FirebaseAuth.getInstance()
            Log.d(TAG, "Firebase Auth inicializado. Usuario actual: ${auth.currentUser?.uid ?: "Sin sesión"}")

            // Inicializar Canales de Notificación y Suscripciones FCM
            com.example.medusaalfha.data.fcm.ResidentNotificationHelper.createNotificationChannel(this)
            com.example.medusaalfha.data.fcm.ResidentNotificationHelper.subscribeToHouseTopic("casa_54")

        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando Firebase en Application: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "Application"
    }
}
