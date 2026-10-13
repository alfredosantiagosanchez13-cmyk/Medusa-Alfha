package com.example.medusaalfha.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Monitor de conectividad de red para asegurar que la sincronización con Firestore
 * se realice exclusivamente cuando se detecte una conexión activa a Internet.
 */
class NetworkConnectivityMonitor(private val context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    companion object {
        private const val TAG = "NetworkMonitor"
    }

    /**
     * Comprueba de manera síncrona si hay conexión a internet activa y verificada.
     */
    fun isOnline(): Boolean {
        val cm = connectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                 capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                 capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                 capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    }

    /**
     * Emite reactivamente el estado de la conectividad a Internet.
     */
    fun observeConnectivity(): Flow<Boolean> = callbackFlow {
        val cm = connectivityManager
        if (cm == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        // Emisión inmediata del estado inicial
        trySend(isOnline())

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Red disponible detectada: $network")
                trySend(isOnline())
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Conexión de red perdida: $network")
                trySend(isOnline())
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                Log.d(TAG, "Capacidades de red modificadas (Internet=$hasInternet)")
                trySend(isOnline())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            cm.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo registrar NetworkCallback: ${e.message}")
            trySend(isOnline())
        }

        awaitClose {
            try {
                cm.unregisterNetworkCallback(networkCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Error desregistrando NetworkCallback: ${e.message}")
            }
        }
    }.distinctUntilChanged()
}
