package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.data.booking.AppDatabase
import com.example.ui.screens.SecurityScannerScreen
import com.example.ui.theme.MEDUSAALFHATheme
import com.example.ui.theme.NavyDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {

    companion object {
        private const val TAG = "AppDiagnostic"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Execute system diagnostics on launch to log Camera, Database, and Network status
        runSystemDiagnostics()

        setContent {
            MEDUSAALFHATheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NavyDark
                ) {
                    SecurityScannerScreen()
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

        // 3. Database Diagnostic Check (performed asynchronously to prevent main-thread I/O blocking)
        lifecycleScope.launch {
            val dbStatus = checkDatabaseDiagnostic()
            Log.i(TAG, "🗄️ DATABASE STATUS: $dbStatus")
            Log.i(TAG, "================ [ DIAGNOSTIC COMPLETED ] ================")
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
