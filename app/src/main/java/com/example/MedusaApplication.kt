package com.example

import android.app.Application
import android.util.Log
import com.example.data.booking.AppDatabase
import com.example.data.notifications.SmartNotificationHub
import com.example.utils.AmenityReminderManager
import com.example.utils.ResidentNotificationManager

class MedusaApplication : Application() {

    companion object {
        private const val TAG = "MedusaApplication"
        var isDatabaseAvailable: Boolean = false
            private set
        var isFirebaseAvailable: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Initializing MedusaApplication startup sequence...")

        // 1. Initialize Notification Channels within try-catch block
        initializeNotificationChannels()

        // 2. Initialize Room Database within try-catch block
        initializeRoomDatabase()

        // 3. Initialize Firebase services safely (supports missing credentials / offline environments)
        initializeFirebaseSafely()
    }

    private fun initializeNotificationChannels() {
        try {
            SmartNotificationHub.initializeChannels(this)
            ResidentNotificationManager.createNotificationChannel(this)
            AmenityReminderManager.createNotificationChannel(this)
            Log.i(TAG, "Notification channels successfully created.")
        } catch (e: Exception) {
            Log.e(TAG, "Warning: Failed to create notification channels: ${e.message}", e)
        }
    }

    private fun initializeRoomDatabase() {
        try {
            val db = AppDatabase.getDatabase(this)
            isDatabaseAvailable = db.isOpen || true
            Log.i(TAG, "Room Database successfully pre-initialized.")
        } catch (e: Exception) {
            isDatabaseAvailable = false
            Log.e(TAG, "Warning: Failed to pre-initialize Room Database on application startup: ${e.message}", e)
        }
    }

    private fun initializeFirebaseSafely() {
        try {
            val initialized = com.example.data.firebase.FirebaseConfigHelper.initialize(this)
            isFirebaseAvailable = initialized
            Log.i(TAG, "Firebase initialized safely via FirebaseConfigHelper: isAvailable=$isFirebaseAvailable")
        } catch (e: Exception) {
            isFirebaseAvailable = false
            Log.w(TAG, "Firebase initialization skipped or credentials missing: ${e.message}")
        }
    }
}
