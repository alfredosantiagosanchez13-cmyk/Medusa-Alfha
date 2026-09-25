package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.app.Application
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.example.data.booking.AppDatabase
import com.example.data.firebase.FirebaseAuthManager
import com.example.data.sync.OfflineSyncEngine
import com.example.ui.components.DebugDiagnosticOverlay
import com.example.ui.navigation.MedusaNavGraph
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.SecurityScannerScreen
import com.example.ui.theme.MEDUSAALFHATheme
import com.example.data.notifications.SmartNotificationHub
import com.example.ui.theme.NavyDark
import com.example.ui.viewmodel.ActivationViewModel
import com.example.utils.AmenityReminderManager
import com.example.utils.ResidentNotificationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    companion object {
        private const val TAG = "AppDiagnostic"
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.i(TAG, "🔔 Notification permission granted: $isGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Notification Channels for Resident Check-ins, Amenity Reminders, and Smart Event Notifications
        try {
            SmartNotificationHub.initializeChannels(this)
            ResidentNotificationManager.createNotificationChannel(this)
            AmenityReminderManager.createNotificationChannel(this)
        } catch (t: Throwable) {
            Log.e(TAG, "Notification channel creation failed: ${t.message}", t)
        }

        // Request POST_NOTIFICATIONS on Android 13+ inside try-catch block
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Notification permission request failed: ${t.message}", t)
        }

        // Execute system diagnostics on launch to log Camera, Database, Firebase, and Network status
        runSystemDiagnostics()

        // Inicializar Motor de Sincronización Automática Offline/Online y FCM en hilo de fondo
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val appDb = AppDatabase.getDatabase(applicationContext)
                OfflineSyncEngine.initializeAutoSync(applicationContext, appDb)
                Log.i(TAG, "🔄 OfflineSyncEngine initialized safely on Dispatchers.IO")
            } catch (t: Throwable) {
                Log.e(TAG, "Safe fallback: Failed to initialize OfflineSyncEngine: ${t.message}", t)
            }

            // Inicializar Firebase Cloud Messaging (FCM) de forma segura en hilo de fondo
            try {
                com.example.data.fcm.FcmNotificationManager.initialize(
                    context = applicationContext,
                    currentUser = null,
                    condominiumId = "Los Prados Residencial"
                )
                Log.i(TAG, "🔥 FcmNotificationManager initialized successfully on Dispatchers.IO")
            } catch (t: Throwable) {
                Log.w(TAG, "Safe fallback: FCM initialization skipped or offline: ${t.message}")
            }
        }

        setContent {
            MEDUSAALFHATheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NavyDark
                ) {
                    val context = LocalContext.current
                    val activationViewModel: ActivationViewModel = viewModel(
                        factory = ActivationViewModel.provideFactory(context.applicationContext as Application)
                    )
                    val currentSession by activationViewModel.currentSession.collectAsState()
                    val navController = rememberNavController()

                    Box(modifier = Modifier.fillMaxSize()) {
                        MedusaNavGraph(
                            navController = navController,
                            currentSession = currentSession,
                            activationViewModel = activationViewModel
                        )

                        DebugDiagnosticOverlay(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(top = 8.dp, end = 14.dp)
                        )
                    }
                }
            }
        }
    }

    /**
     * Diagnostic utility function that checks and logs the availability status of:
     * 1. Camera hardware and permissions
     * 2. Room Database connectivity and initialization
     * 3. Network connection state and internet capabilities
     */
    private fun runSystemDiagnostics() {
        Log.i(TAG, "================ [ SYSTEM STARTUP DIAGNOSTIC ] ================")

        // 1. Camera Diagnostic Check
        val cameraStatus = checkCameraDiagnostic()
        Log.i(TAG, "📷 CAMERA STATUS: $cameraStatus")

        // 2. Network Diagnostic Check
        val networkStatus = checkNetworkDiagnostic()
        Log.i(TAG, "🌐 NETWORK STATUS: $networkStatus")

        // 3. Firebase & Cloud Services Diagnostic Check
        val firebaseStatus = checkFirebaseDiagnostic()
        Log.i(TAG, "☁️ FIREBASE / CLOUD STATUS: $firebaseStatus")

        // 4. Database Diagnostic Check (performed asynchronously to prevent main-thread I/O blocking)
        lifecycleScope.launch {
            val dbStatus = checkDatabaseDiagnostic()
            Log.i(TAG, "🗄️ DATABASE STATUS: $dbStatus")
            Log.i(TAG, "================ [ DIAGNOSTIC COMPLETED ] ================")
        }
    }

    private fun checkFirebaseDiagnostic(): String {
        return try {
            val isAvailable = MedusaApplication.isFirebaseAvailable
            if (isAvailable) {
                "Firebase initialized and active"
            } else {
                "Firebase unconfigured or offline mode active (Safe fallback)"
            }
        } catch (e: Exception) {
            "Firebase diagnostic exception (handled safely): ${e.message}"
        }
    }

    private fun checkCameraDiagnostic(): String {
        return try {
            val hasCameraFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
            val hasCameraPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

            val cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraCount = cameraManager?.cameraIdList?.size ?: 0

            "Available=$hasCameraFeature, SensorsDetected=$cameraCount, PermissionGranted=$hasCameraPermission"
        } catch (e: Exception) {
            "Error evaluating camera service: ${e.message}"
        }
    }

    private fun checkNetworkDiagnostic(): String {
        return try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager == null) {
                return "ConnectivityManager not available"
            }

            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork == null) {
                return "Disconnected (No active network)"
            }

            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            if (capabilities == null) {
                return "Connected (Capabilities unknown)"
            }

            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val isEthernet = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

            val transportType = when {
                isWifi -> "Wi-Fi"
                isCellular -> "Cellular Data"
                isEthernet -> "Ethernet"
                else -> "Other Transport"
            }

            "Connected ($transportType), HasInternetCapability=$hasInternet, Validated=$isValidated"
        } catch (e: Exception) {
            "Error evaluating network state: ${e.message}"
        }
    }

    private suspend fun checkDatabaseDiagnostic(): String = withContext(Dispatchers.IO) {
        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val isOpen = db.openHelper.readableDatabase.isOpen
            val version = db.openHelper.readableDatabase.version
            "Initialized=true, IsOpen=$isOpen, SQLiteVersion=$version, RoomVersion=4"
        } catch (e: Exception) {
            "Database Initialization Error: ${e.message}"
        }
    }
}
