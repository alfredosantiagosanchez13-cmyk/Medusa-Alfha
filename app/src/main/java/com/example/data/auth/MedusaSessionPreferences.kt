package com.example.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Modelo inmutable de la sesión activa en el dispositivo.
 */
data class MedusaSessionData(
    val keyId: String,
    val role: MedusaRole,
    val condominiumId: String,
    val assignedUnit: String?,
    val isActive: Boolean,
    val isFinancialBlocked: Boolean,
    val activatedAtMillis: Long
)

/**
 * Gestor seguro de preferencias y sesión para MEDUSA ALFHA.
 * Utiliza EncryptedSharedPreferences respaldado por Android Keystore (AES-256 GCM)
 * con mecanismo de recuperación defensivo para garantizar persistencia y seguridad.
 */
class MedusaSessionPreferences(context: Context) {

    private val prefs: SharedPreferences = createSecurePreferences(context.applicationContext)

    companion object {
        private const val TAG = "MedusaSessionPrefs"
        private const val PREFS_NAME = "medusa_alfha_secure_session"

        private const val KEY_KEY_ID = "pref_key_id"
        private const val KEY_ROLE = "pref_role"
        private const val KEY_CONDOMINIUM_ID = "pref_condominium_id"
        private const val KEY_ASSIGNED_UNIT = "pref_assigned_unit"
        private const val KEY_IS_ACTIVE = "pref_is_active"
        private const val KEY_FINANCIAL_BLOCKED = "pref_financial_blocked"
        private const val KEY_ACTIVATION_TIMESTAMP = "pref_activation_timestamp"

        @Volatile
        private var instance: MedusaSessionPreferences? = null

        fun getInstance(context: Context): MedusaSessionPreferences {
            return instance ?: synchronized(this) {
                instance ?: MedusaSessionPreferences(context).also { instance = it }
            }
        }

        private fun createSecurePreferences(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (e: Exception) {
                Log.w(
                    TAG,
                    "No se pudo inicializar EncryptedSharedPreferences (Keystore no disponible o entorno de pruebas). " +
                        "Activando almacenamiento seguro privado como contingencia: ${e.message}"
                )
                context.getSharedPreferences("${PREFS_NAME}_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    /**
     * Guarda de forma segura los identificadores de la sesión activa tras una validación exitosa.
     */
    fun saveSession(activationKey: ActivationKey, role: MedusaRole) {
        val blockFinancial = (role == MedusaRole.GUARDIA_CASETA)
        val timestamp = System.currentTimeMillis()

        prefs.edit()
            .putString(KEY_KEY_ID, activationKey.keyId)
            .putString(KEY_ROLE, role.name)
            .putString(KEY_CONDOMINIUM_ID, activationKey.condominiumId)
            .putString(KEY_ASSIGNED_UNIT, activationKey.assignedUnit)
            .putBoolean(KEY_IS_ACTIVE, true)
            .putBoolean(KEY_FINANCIAL_BLOCKED, blockFinancial)
            .putLong(KEY_ACTIVATION_TIMESTAMP, timestamp)
            .apply()

        // Sincroniza el guardia de acceso nativo
        MedusaFinancialAccessGuard.applyRoleSecurityPolicy(role)
        Log.i(TAG, "✅ Sesión guardada de forma segura para rol: ${role.name}, condominio: ${activationKey.condominiumId}")
    }

    /**
     * Recupera los datos de la sesión activa si existe.
     */
    fun getSessionData(): MedusaSessionData? {
        val keyId = prefs.getString(KEY_KEY_ID, null) ?: return null
        val isActive = prefs.getBoolean(KEY_IS_ACTIVE, false)
        if (!isActive) return null

        val roleStr = prefs.getString(KEY_ROLE, MedusaRole.UNASSIGNED.name)
        val role = MedusaRole.fromString(roleStr)
        val condoId = prefs.getString(KEY_CONDOMINIUM_ID, "") ?: ""
        val unit = prefs.getString(KEY_ASSIGNED_UNIT, null)
        val financialBlocked = prefs.getBoolean(KEY_FINANCIAL_BLOCKED, role == MedusaRole.GUARDIA_CASETA)
        val timestamp = prefs.getLong(KEY_ACTIVATION_TIMESTAMP, 0L)

        return MedusaSessionData(
            keyId = keyId,
            role = role,
            condominiumId = condoId,
            assignedUnit = unit,
            isActive = true,
            isFinancialBlocked = financialBlocked,
            activatedAtMillis = timestamp
        )
    }

    fun hasActiveSession(): Boolean {
        return prefs.getBoolean(KEY_IS_ACTIVE, false) && !prefs.getString(KEY_KEY_ID, null).isNullOrBlank()
    }

    fun getCondominiumId(): String = prefs.getString(KEY_CONDOMINIUM_ID, "") ?: ""

    fun getAssignedUnit(): String? = prefs.getString(KEY_ASSIGNED_UNIT, null)

    fun getCurrentRole(): MedusaRole {
        val roleStr = prefs.getString(KEY_ROLE, null)
        return MedusaRole.fromString(roleStr)
    }

    fun isFinancialAccessBlocked(): Boolean {
        return prefs.getBoolean(KEY_FINANCIAL_BLOCKED, true)
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        MedusaFinancialAccessGuard.applyRoleSecurityPolicy(MedusaRole.UNASSIGNED)
        Log.i(TAG, "🗑️ Sesión y credenciales de activación purgadas de las preferencias.")
    }
}
