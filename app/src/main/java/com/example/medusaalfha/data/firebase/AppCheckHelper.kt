package com.example.medusaalfha.data.firebase

import android.content.Context
import android.util.Log
import com.example.medusaalfha.BuildConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Gestor de Firebase App Check con Play Integrity en Release y DebugProvider en Debug.
 * Protege los endpoints y llamadas a Cloud Firestore, asegurando que solo
 * los clientes autorizados y dispositivos válidos interactúen con la base de datos.
 */
object AppCheckHelper {
    private const val TAG = "AppCheckHelper"

    fun initializeAppCheck(context: Context) {
        try {
            val appCheck = FirebaseAppCheck.getInstance()

            if (BuildConfig.DEBUG) {
                // Modo Depuración y pruebas locales: DebugAppCheckProviderFactory
                appCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "Firebase App Check activo con DebugAppCheckProviderFactory (DEBUG buildType).")
            } else {
                // Modo Producción Release: Google Play Integrity Provider Factory
                appCheck.installAppCheckProviderFactory(
                    PlayIntegrityAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "Firebase App Check activo con PlayIntegrityAppCheckProviderFactory (RELEASE buildType).")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inicializando Firebase App Check: ${e.message}", e)
        }
    }

    private fun getSiteKeyFromResources(context: Context): String? {
        return try {
            val resId = context.resources.getIdentifier("recaptcha_site_key", "string", context.packageName)
            if (resId != 0) context.getString(resId).ifBlank { null } else null
        } catch (_: Exception) {
            null
        }
    }
}
