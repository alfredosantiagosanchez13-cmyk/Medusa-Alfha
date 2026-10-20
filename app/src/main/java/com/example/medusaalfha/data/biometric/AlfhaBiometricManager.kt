package com.example.medusaalfha.data.biometric

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object AlfhaBiometricManager {

    private const val TAG = "AlfhaBiometric"

    // Credenciales Maestras Exclusivas del Director General
    const val MASTER_DIRECTOR_NAME = "Santiago Sánchez"
    const val MASTER_DIRECTOR_TITLE = "Director General · ALFHA Master"
    const val MASTER_DIRECTOR_EMAIL = "alfredosantiagosanchez13@gmail.com"
    const val MASTER_ROLE_KEY = "ALFHA_MASTER_SUPER_ADMIN"

    val MASTER_AUTHORIZED_EMAILS = setOf(
        "alfredosantiagosanchez13@gmail.com",
        "alfhaseguridad070@gmail.com"
    )

    /**
     * Evalúa el estado del lector de huellas biométricas en este dispositivo.
     */
    fun checkBiometricStatus(context: Context): Int {
        val biometricManager = BiometricManager.from(context)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
    }

    /**
     * Devuelve una descripción en español sobre el estado del sensor de huellas.
     */
    fun getBiometricStatusDescription(context: Context): String {
        return when (checkBiometricStatus(context)) {
            BiometricManager.BIOMETRIC_SUCCESS ->
                "Lector de huella listo y habilitado en este teléfono."
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "No hay huellas registradas en los ajustes de seguridad de este dispositivo."
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                "Este dispositivo no cuenta con sensor de huella dactilar físico."
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "El hardware de huella dactilar se encuentra ocupado o no disponible."
            else ->
                "Sensor biométrico listo para autenticación de Santiago Sánchez."
        }
    }

    /**
     * Emite una pulsación háptica / vibración táctil de confirmación o alerta.
     */
    fun performHapticFeedback(context: Context, isSuccess: Boolean = true) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val vibrator = vibratorManager?.defaultVibrator
                if (isSuccess) {
                    vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else {
                    vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                }
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (isSuccess) {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(70)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 90, 60, 90), -1)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo emitir vibración: ${e.message}")
        }
    }

    /**
     * Inicia BiometricPrompt oficial de AndroidX con el lector de huellas del teléfono.
     * Garantiza acceso exclusivo al Director General Santiago Sánchez.
     */
    fun authenticateWithBiometricPrompt(
        activity: FragmentActivity,
        onSuccess: (userName: String, email: String) -> Unit,
        onError: (errorCode: Int, errorMessage: String) -> Unit,
        onCancel: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Lector de Huella ALFHA Master")
            .setSubtitle("Director General: $MASTER_DIRECTOR_NAME")
            .setDescription("Coloque su huella dactilar registrada en este dispositivo ($MASTER_DIRECTOR_EMAIL) para desbloquear el panel de control administrativo.")
            .setNegativeButtonText("Cancelar")
            .setConfirmationRequired(false)
            .build()

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    performHapticFeedback(activity, isSuccess = true)
                    Log.d(TAG, "BiometricPrompt exitoso: Huella verificada para $MASTER_DIRECTOR_NAME.")
                    onSuccess(MASTER_DIRECTOR_NAME, MASTER_DIRECTOR_EMAIL)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Log.w(TAG, "BiometricPrompt error ($errorCode): $errString")
                    if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    ) {
                        onCancel()
                    } else {
                        performHapticFeedback(activity, isSuccess = false)
                        onError(errorCode, errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    performHapticFeedback(activity, isSuccess = false)
                    Log.w(TAG, "BiometricPrompt: Huella dactilar no reconocida.")
                    onError(-1, "Huella dactilar no reconocida. Coloque el dedo registrado en este dispositivo.")
                }
            }
        )

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Excepción al invocar BiometricPrompt: ${e.message}", e)
            onError(-2, e.localizedMessage ?: "No se pudo iniciar el lector de huellas.")
        }
    }
}
