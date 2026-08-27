package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ConnectivityStatus(val label: String, val badgeIcon: String) {
    ONLINE("ONLINE", "🟢"),
    OFFLINE("OFFLINE", "🔴"),
    SYNCHRONIZING("SINCRONIZANDO", "🔄")
}

data class NetworkStateInfo(
    val status: ConnectivityStatus = ConnectivityStatus.ONLINE,
    val isConnected: Boolean = true,
    val transportType: String = "Wi-Fi",
    val hasInternetCapability: Boolean = true,
    val isSimulatedOffline: Boolean = false,
    val lastStateChangeMillis: Long = System.currentTimeMillis()
)

/**
 * FASE 19: Observador en Tiempo Real del Estado de Red y Conectividad.
 * Permite alternar modo simulación offline para pruebas operativas.
 */
class NetworkConnectivityObserver private constructor(private val context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _networkState = MutableStateFlow(computeInitialState())
    val networkState: StateFlow<NetworkStateInfo> = _networkState.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        registerNetworkCallback()
    }

    private fun computeInitialState(): NetworkStateInfo {
        return try {
            val activeNetwork = connectivityManager.activeNetwork
            val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
            if (activeNetwork == null || caps == null) {
                NetworkStateInfo(
                    status = ConnectivityStatus.OFFLINE,
                    isConnected = false,
                    transportType = "Sin Conexión",
                    hasInternetCapability = false
                )
            } else {
                val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val transport = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos Móviles"
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    else -> "Red Local"
                }
                NetworkStateInfo(
                    status = if (hasInternet) ConnectivityStatus.ONLINE else ConnectivityStatus.OFFLINE,
                    isConnected = true,
                    transportType = transport,
                    hasInternetCapability = hasInternet
                )
            }
        } catch (e: Exception) {
            NetworkStateInfo(
                status = ConnectivityStatus.OFFLINE,
                isConnected = false,
                transportType = "Error Diagnóstico",
                hasInternetCapability = false
            )
        }
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (_networkState.value.isSimulatedOffline) return
                updateRealNetworkStatus()
            }

            override fun onLost(network: Network) {
                if (_networkState.value.isSimulatedOffline) return
                _networkState.value = _networkState.value.copy(
                    status = ConnectivityStatus.OFFLINE,
                    isConnected = false,
                    transportType = "Sin Conexión",
                    hasInternetCapability = false,
                    lastStateChangeMillis = System.currentTimeMillis()
                )
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (_networkState.value.isSimulatedOffline) return
                updateRealNetworkStatus()
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            // Fallback si permisos no disponibles
        }
    }

    fun updateRealNetworkStatus() {
        val state = computeInitialState()
        if (!_networkState.value.isSimulatedOffline) {
            _networkState.value = state
        }
    }

    fun setSynchronizing(isSyncing: Boolean) {
        if (isSyncing) {
            _networkState.value = _networkState.value.copy(status = ConnectivityStatus.SYNCHRONIZING)
        } else {
            if (_networkState.value.isSimulatedOffline) {
                _networkState.value = _networkState.value.copy(status = ConnectivityStatus.OFFLINE)
            } else {
                updateRealNetworkStatus()
            }
        }
    }

    /**
     * Permite activar/desactivar simulación de corte de conexión para validar continuidad operativa.
     */
    fun toggleSimulatedOffline(forceOffline: Boolean) {
        if (forceOffline) {
            _networkState.value = NetworkStateInfo(
                status = ConnectivityStatus.OFFLINE,
                isConnected = false,
                transportType = "Modo Offline (Prueba de Continuidad)",
                hasInternetCapability = false,
                isSimulatedOffline = true,
                lastStateChangeMillis = System.currentTimeMillis()
            )
        } else {
            _networkState.value = _networkState.value.copy(isSimulatedOffline = false)
            updateRealNetworkStatus()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: NetworkConnectivityObserver? = null

        fun getInstance(context: Context): NetworkConnectivityObserver {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkConnectivityObserver(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
