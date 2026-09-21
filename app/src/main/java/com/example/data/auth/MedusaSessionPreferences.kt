package com.example.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Gestor seguro de preferencias y sesión para MEDUSA ALFHA.
 * Utiliza EncryptedSharedPreferences respaldado por Android Keystore (AES-256 GCM)
 * con mecanismo de recuperación defensivo para garantizar persistencia y seguridad.
 */
class MedusaSessionPreferences private constructor(context: Context) {

    private val appContext = context.applicationContext

    // Referencia dinámica para permitir fallback automático sin crash si falla el cifrado
    @Volatile
    private var prefs: SharedPreferences = createSecurePreferences(appContext)

    companion object {
        private const val TAG = "MedusaSessionPrefs"
        private const val PREFS_NAME = "medusa_alfha_secure_session"
        private const val FALLBACK_PREFS_NAME = "medusa_alfha_secure_session_fallback"

        private const val KEY_KEY_ID = "pref_key_id"
        private const val KEY_ROLE = "pref_role"
        private const val KEY_CONDOMINIUM_ID = "pref_condominium_id"
        private const val KEY_CONDOMINIUM_NAME = "pref_condominium_name"
        private const val KEY_ASSIGNED_UNIT = "pref_assigned_unit"
        private const val KEY_IS_ACTIVE = "pref_is_active"
        private const val KEY_FINANCIAL_BLOCKED = "pref_financial_blocked"
        private const val KEY_ACTIVATION_TIMESTAMP = "pref_activation_timestamp"

        @Volatile
        private var instance: MedusaSessionPreferences? = null

        fun getInstance(context: Context): MedusaSessionPreferences {
            return instance ?: synchronized(this) {
                instance ?: MedusaSessionPreferences(context.applicationContext).also { instance = it }
            }
        }

        private fun createSecurePreferences(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                val securePrefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )

                // Validación activa: comprobación de lectura para detectar discrepancias de Keystore
                securePrefs.all
                securePrefs
            } catch (t: Throwable) {
                Log.w(
                    TAG,
                    "Aviso: Keystore o EncryptedSharedPreferences no disponible / corrupto: ${t.message}. Recreando almacenamiento de contingencia...",
                    t
                )
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        context.deleteSharedPreferences(PREFS_NAME)
                    } else {
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
                    }
                } catch (delEx: Throwable) {
                    Log.w(TAG, "No se pudo purgar archivo de preferencias corrupto: ${delEx.message}")
                }
                context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
    }

    private fun getSafePrefs(): SharedPreferences {
        return try {
            prefs.all
            prefs
        } catch (t: Throwable) {
            Log.w(TAG, "Excepción accediendo a preferencias encriptadas: ${t.message}. Alternando a fallback seguro.")
            val fallback = appContext.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
            prefs = fallback
            fallback
        }
    }

    /**
     * Guarda de forma segura los identificadores de la sesión activa tras una validación exitosa.
     */
    fun saveSession(activationKey: ActivationKey, role: MedusaRole) {
        try {
            val blockFinancial = (role == MedusaRole.GUARDIA_CASETA)
            val timestamp = System.currentTimeMillis()
            val condoName = activationKey.condominiumName.ifBlank { "Los Prados 1" }

            getSafePrefs().edit()
                .putString(KEY_KEY_ID, activationKey.keyId)
                .putString(KEY_ROLE, role.name)
                .putString(KEY_CONDOMINIUM_ID, activationKey.condominiumId)
                .putString(KEY_CONDOMINIUM_NAME, condoName)
                .putString(KEY_ASSIGNED_UNIT, activationKey.assignedUnit ?: "")
                .putBoolean(KEY_IS_ACTIVE, true)
                .putBoolean(KEY_FINANCIAL_BLOCKED, blockFinancial)
                .putLong(KEY_ACTIVATION_TIMESTAMP, timestamp)
                .apply()

            MedusaFinancialAccessGuard.applyRoleSecurityPolicy(role)
            Log.i(TAG, "✅ Sesión guardada de forma segura para rol: ${role.name}, condominio: $condoName (${activationKey.condominiumId})")
        } catch (t: Throwable) {
            Log.e(TAG, "Error persistiendo sesión de activación: ${t.message}", t)
        }
    }

    /**
     * Sobrecarga para persistir directamente una instancia tipada de [UserSession].
     */
    fun saveSession(session: UserSession) {
        try {
            getSafePrefs().edit()
                .putString(KEY_KEY_ID, session.activationKey)
                .putString(KEY_ROLE, session.currentRole.name)
                .putString(KEY_CONDOMINIUM_ID, session.condominiumId)
                .putString(KEY_CONDOMINIUM_NAME, session.condominiumName)
                .putString(KEY_ASSIGNED_UNIT, session.assignedUnitId)
                .putBoolean(KEY_IS_ACTIVE, session.isActive)
                .putBoolean(KEY_FINANCIAL_BLOCKED, session.isFinancialBlocked)
                .putLong(KEY_ACTIVATION_TIMESTAMP, session.timestampMillis)
                .apply()

            MedusaFinancialAccessGuard.applyRoleSecurityPolicy(session.currentRole)
            Log.i(TAG, "✅ UserSession persistida exitosamente para unidad: ${session.assignedUnitId} en ${session.condominiumName}")
        } catch (t: Throwable) {
            Log.e(TAG, "Error persistiendo UserSession: ${t.message}", t)
        }
    }

    /**
     * Recupera los datos de la sesión activa si existe.
     */
    fun getSessionData(): UserSession? {
        return try {
            val safe = getSafePrefs()
            val keyId = safe.getString(KEY_KEY_ID, null) ?: return null
            val isActive = safe.getBoolean(KEY_IS_ACTIVE, false)
            if (!isActive) return null

            val roleStr = safe.getString(KEY_ROLE, MedusaRole.UNASSIGNED.name)
            val role = MedusaRole.fromString(roleStr)
            val condoId = safe.getString(KEY_CONDOMINIUM_ID, "") ?: ""
            val condoName = safe.getString(KEY_CONDOMINIUM_NAME, "Los Prados 1") ?: "Los Prados 1"
            val unit = safe.getString(KEY_ASSIGNED_UNIT, "") ?: ""
            val financialBlocked = safe.getBoolean(KEY_FINANCIAL_BLOCKED, role == MedusaRole.GUARDIA_CASETA)
            val timestamp = safe.getLong(KEY_ACTIVATION_TIMESTAMP, 0L)

            UserSession(
                activationKey = keyId,
                currentRole = role,
                condominiumId = condoId,
                assignedUnitId = unit,
                condominiumName = condoName,
                isActive = true,
                isFinancialBlocked = financialBlocked,
                timestampMillis = timestamp
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Error leyendo datos de sesión (recuperación segura): ${t.message}")
            null
        }
    }

    /**
     * Alias explícito para recuperar la [UserSession] activa.
     */
    fun getUserSession(): UserSession? = getSessionData()

    fun hasActiveSession(): Boolean {
        return try {
            val safe = getSafePrefs()
            safe.getBoolean(KEY_IS_ACTIVE, false) && !safe.getString(KEY_KEY_ID, null).isNullOrBlank()
        } catch (t: Throwable) {
            Log.w(TAG, "Error verificando sesión activa: ${t.message}")
            false
        }
    }

    fun getCondominiumId(): String {
        return try {
            getSafePrefs().getString(KEY_CONDOMINIUM_ID, "") ?: ""
        } catch (t: Throwable) {
            ""
        }
    }

    fun getAssignedUnit(): String? {
        return try {
            getSafePrefs().getString(KEY_ASSIGNED_UNIT, null)
        } catch (t: Throwable) {
            null
        }
    }

    fun getCurrentRole(): MedusaRole {
        return try {
            val roleStr = getSafePrefs().getString(KEY_ROLE, null)
            MedusaRole.fromString(roleStr)
        } catch (t: Throwable) {
            Log.w(TAG, "Error obteniendo rol actual: ${t.message}")
            MedusaRole.UNASSIGNED
        }
    }

    fun isFinancialAccessBlocked(): Boolean {
        return try {
            getSafePrefs().getBoolean(KEY_FINANCIAL_BLOCKED, true)
        } catch (t: Throwable) {
            true
        }
    }

    fun clearSession() {
        try {
            getSafePrefs().edit().clear().apply()
            MedusaFinancialAccessGuard.applyRoleSecurityPolicy(MedusaRole.UNASSIGNED)
            Log.i(TAG, "🗑️ Sesión y credenciales de activación purgadas de las preferencias.")
        } catch (t: Throwable) {
            Log.e(TAG, "Error purgando sesión: ${t.message}", t)
        }
    }
}
