package com.example.medusaalfha.data.firebase

import android.content.Context
import android.util.Log
import com.example.medusaalfha.BuildConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

/**
 * Gestor de Firebase App Check con reCAPTCHA Enterprise.
 * Protege los endpoints y llamadas a Cloud Firestore, asegurando que solo
 * los clientes autorizados y dispositivos válidos interactúen con la base de datos.
 */
object AppCheckHelper {
    private const val TAG = "AppCheckHelper"
    
    // Identificador de sitio reCAPTCHA Enterprise para el proyecto medusa-alfha
    private const val DEFAULT_RECAPTCHA_SITE_KEY = "6LcvkLAqAAAAAI3k_medusa_enterprise_site_key"

    fun initializeAppCheck(context: Context, siteKey: String? = null) {
        try {
            val appCheck = FirebaseAppCheck.getInstance()

            if (BuildConfig.DEBUG) {
                // Modo Depuración y pruebas locales: DebugAppCheckProviderFactory
                appCheck.installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                Log.d(TAG, "Firebase App Check activo con DebugAppCheckProviderFactory.")
            } else {
                // Modo Producción Release: reCAPTCHA Enterprise Provider Factory
                val resolvedSiteKey = siteKey 
                    ?: getSiteKeyFromResources(context) 
                    ?: DEFAULT_RECAPTCHA_SITE_KEY

                appCheck.installAppCheckProviderFactory(
                    RecaptchaAppCheckProviderFactory.getInstance(resolvedSiteKey)
                )
                Log.d(TAG, "Firebase App Check activo con reCAPTCHA Enterprise Provider.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Advertencia inicializando Firebase App Check: ${e.message}")
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
