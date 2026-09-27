package com.example.data.auth

import android.util.Log
import com.example.BuildConfig

/**
 * CONFIGURACIÓN DE DESARROLLO Y AISLAMIENTO DE CREDENCIALES DE PRUEBA.
 *
 * Directiva de Seguridad de la Orden Maestra:
 * "Las credenciales de prueba, si son indispensables para desarrollo, deben quedar
 * aisladas exclusivamente en una configuración de desarrollo y no formar parte del
 * flujo operativo ni de una compilación de producción.
 * NO DEBE EXISTIR DENTRO DEL APK UNA LLAVE MAESTRA QUE OTORGUE PRIVILEGIOS
 * ADMINISTRATIVOS O FINANCIEROS."
 */
object MedusaDevConfig {
    private const val TAG = "MedusaDevConfig"

    /**
     * Indica si el entorno actual es una compilación de depuración (DEBUG).
     * En compilaciones de lanzamiento (RELEASE/PRODUCTION), esta propiedad siempre es falsa.
     */
    val isDebugBuild: Boolean
        get() = BuildConfig.DEBUG

    /**
     * Llaves temporales reservadas ÚNICAMENTE para pruebas internas en compilaciones de depuración (DEBUG).
     * En producción (RELEASE), este mapa queda inoperativo y se rechaza cualquier evaluación.
     */
    private val DEBUG_ONLY_KEYS = mapOf(
        "DEV-ADM-PRADOS" to MedusaRole.ADMINISTRACION,
        "DEV-CASETA-PRADOS" to MedusaRole.GUARDIA_CASETA,
        "DEV-RESIDENTE-104" to MedusaRole.RESIDENTE
    )

    /**
     * Evalúa si una llave corresponde a una credencial de depuración.
     * Retorna el rol correspondiente ÚNICAMENTE si [BuildConfig.DEBUG] es true.
     * En cualquier compilación de producción devuelve null de forma estricta.
     */
    fun evaluateDebugKey(inputKey: String): MedusaRole? {
        if (!BuildConfig.DEBUG) {
            // Protección estricta contra bypass en compilaciones de producción
            return null
        }

        val normalized = inputKey.trim().uppercase()
        val matchedRole = DEBUG_ONLY_KEYS[normalized]
        if (matchedRole != null) {
            Log.w(
                TAG,
                "⚠️ [MODO DESARROLLO] Credencial de prueba DEBUG utilizada: $normalized -> $matchedRole. " +
                    "Este mecanismo está deshabilitado en compilaciones de producción."
            )
        }
        return matchedRole
    }

    /**
     * Proporciona la lista de credenciales de prueba exclusivamente para visualización
     * en el panel de diagnóstico de desarrollo (Debug Diagnostic Overlay).
     * Retorna una lista vacía en producción.
     */
    fun getAvailableDebugKeys(): List<Pair<String, MedusaRole>> {
        return if (BuildConfig.DEBUG) {
            DEBUG_ONLY_KEYS.toList()
        } else {
            emptyList()
        }
    }
}
