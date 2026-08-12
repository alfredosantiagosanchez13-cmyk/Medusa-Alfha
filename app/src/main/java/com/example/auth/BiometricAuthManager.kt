package com.example.auth

import android.content.Context
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuthManager {

    enum class BiometricStatus {
        AVAILABLE,
        NOT_ENROLLED,
        HARDWARE_UNAVAILABLE,
        UNSUPPORTED
    }

    fun getBiometricStatus(context: Context): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NOT_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.HARDWARE_UNAVAILABLE
            else -> BiometricStatus.UNSUPPORTED
        }
    }

    fun promptBiometricAuth(
        activity: FragmentActivity,
        title: String = "Autenticación Biométrica",
        subtitle: String = "Confirma tu huella dactilar o rostro para acceder a la generación de pases QR",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    onError("Autenticación cancelada por el usuario")
                } else {
                    onError("Error de biometría: $errString")
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onError("Huella / Rostro no reconocido. Reintente.")
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )

        try {
            biometricPrompt.authenticate(promptInfoBuilder.build())
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback for devices without enrolled biometrics or credentials configured
            onError("Biometría no configurada: ${e.message}")
        }
    }
}
