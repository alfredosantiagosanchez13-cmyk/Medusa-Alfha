package com.example

import android.app.Application
import android.util.Log
import com.example.auth.FirebaseAuthProvider
import com.example.data.booking.AppDatabase
import com.example.data.firebase.FirebaseConfigHelper
import com.example.data.notifications.SmartNotificationHub
import com.example.utils.AmenityReminderManager
import com.example.utils.ResidentNotificationManager

/**
 * MainApplication: Clase principal de aplicación Android para MEDUSA ALFHA.
 * Inicializa los subsistemas centrales del sistema:
 * 1. Canales de notificación del sistema (SmartNotificationHub, ResidentNotificationManager, AmenityReminderManager).
 * 2. Base de datos Room SQLite local.
 * 3. Servicios centrales de Firebase y configuración de persistencia offline.
 * 4. Proveedor de autenticación FirebaseAuthProvider con soporte para Google Sign-In.
 */
open class MainApplication : Application() {

    private var _firebaseAuthProvider: FirebaseAuthProvider? = null

    val firebaseAuthProvider: FirebaseAuthProvider
        get() = _firebaseAuthProvider ?: synchronized(this) {
            _firebaseAuthProvider ?: FirebaseAuthProvider.initialize(this).also {
                _firebaseAuthProvider = it
            }
        }

    companion object {
        private const val TAG = "MainApplication"

        lateinit var instance: MainApplication
            protected set

        var isDatabaseAvailable: Boolean = false
            internal set

        var isFirebaseAvailable: Boolean = false
            internal set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Safe global uncaught exception handler to prevent hard crashes and log issues clearly
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "FATAL UNCAUGHT EXCEPTION in thread ${thread.name}: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        Log.i(TAG, "Initializing MainApplication startup sequence...")

        // 1. Canales de notificación
        initializeNotificationChannels()

        // 2. Base de datos Room SQLite
        initializeRoomDatabase()

        // 3. Inicialización segura de Firebase
        initializeFirebaseSafely()

        // 4. Inicialización del proveedor de autenticación con Google Sign-In
        initializeFirebaseAuthProvider()
    }

    protected open fun initializeNotificationChannels() {
        try {
            SmartNotificationHub.initializeChannels(this)
            ResidentNotificationManager.createNotificationChannel(this)
            AmenityReminderManager.createNotificationChannel(this)
            Log.i(TAG, "Notification channels successfully created.")
        } catch (e: Exception) {
            Log.e(TAG, "Warning: Failed to create notification channels: ${e.message}", e)
        }
    }

    protected open fun initializeRoomDatabase() {
        try {
            val db = AppDatabase.getDatabase(this)
            isDatabaseAvailable = db.isOpen || true
            Log.i(TAG, "Room Database successfully pre-initialized.")
        } catch (e: Exception) {
            isDatabaseAvailable = false
            Log.e(TAG, "Warning: Failed to pre-initialize Room Database on application startup: ${e.message}", e)
        }
    }

    protected open fun initializeFirebaseSafely() {
        try {
            val initialized = FirebaseConfigHelper.initialize(this)
            isFirebaseAvailable = initialized
            Log.i(TAG, "Firebase initialized safely via FirebaseConfigHelper: isAvailable=$isFirebaseAvailable")
        } catch (e: Exception) {
            isFirebaseAvailable = false
            Log.w(TAG, "Firebase initialization skipped or credentials missing: ${e.message}")
        }
    }

    protected open fun initializeFirebaseAuthProvider() {
        try {
            _firebaseAuthProvider = FirebaseAuthProvider.initialize(this)
            Log.i(TAG, "FirebaseAuthProvider successfully initialized in MainApplication.")
        } catch (e: Exception) {
            Log.e(TAG, "Warning: Failed to initialize FirebaseAuthProvider: ${e.message}", e)
        }
    }
}
