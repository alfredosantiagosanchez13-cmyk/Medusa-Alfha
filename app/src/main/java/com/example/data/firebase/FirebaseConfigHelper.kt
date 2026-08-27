package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gestor central de inicialización y verificación de Firebase en MEDUSA ALFHA.
 * Maneja la inicialización segura, fallback en caso de no contar aún con google-services.json
 * y provee instancias seguras de FirebaseAuth y FirebaseFirestore.
 */
object FirebaseConfigHelper {

    private const val TAG = "FirebaseConfigHelper"

    private val _isFirebaseAvailable = MutableStateFlow(false)
    val isFirebaseAvailable: StateFlow<Boolean> = _isFirebaseAvailable.asStateFlow()

    private val _initializationStatusMessage = MutableStateFlow("Verificando configuración de Firebase...")
    val initializationStatusMessage: StateFlow<String> = _initializationStatusMessage.asStateFlow()

    /**
     * Inicializa Firebase de manera segura en el arranque de la aplicación.
     */
    fun initialize(context: Context): Boolean {
        return try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val app = FirebaseApp.initializeApp(context)
                if (app != null) {
                    _isFirebaseAvailable.value = true
                    _initializationStatusMessage.value = "Firebase inicializado correctamente."
                    configureFirestoreSettings()
                    true
                } else {
                    _isFirebaseAvailable.value = false
                    _initializationStatusMessage.value = "Requiere colocar 'google-services.json' en /app para sincronización en la nube."
                    false
                }
            } else {
                _isFirebaseAvailable.value = true
                _initializationStatusMessage.value = "Firebase activo y conectado."
                configureFirestoreSettings()
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase no configurado o google-services.json ausente: ${e.message}")
            _isFirebaseAvailable.value = false
            _initializationStatusMessage.value = "Modo Local Autónomo (Room SQLite). Para activar la nube, configure google-services.json."
            false
        }
    }

    private fun configureFirestoreSettings() {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true) // Persistencia en disco para soporte offline
                .build()
            firestore.firestoreSettings = settings
            Log.i(TAG, "Firestore configurado con persistencia offline activa.")
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo aplicar configuración de Firestore: ${e.message}")
        }
    }

    fun getAuth(): FirebaseAuth? {
        return try {
            if (_isFirebaseAvailable.value) FirebaseAuth.getInstance() else null
        } catch (e: Exception) {
            null
        }
    }

    fun getFirestore(): FirebaseFirestore? {
        return try {
            if (_isFirebaseAvailable.value) FirebaseFirestore.getInstance() else null
        } catch (e: Exception) {
            null
        }
    }
}
