package com.example.data.auth

import android.util.Log

/**
 * Guardián de Acceso Nativo a Nodos Financieros en MEDUSA ALFHA.
 *
 * Implementa una barrera estricta a nivel de arquitectura y lógica móvil:
 * Si el rol asignado es GUARDIA_CASETA, bloquea nativamente el acceso a:
 * - Balances contables del condominio
 * - Saldos y adeudos de residentes
 * - Estados de cuenta bancarios / cuotas de mantenimiento
 * - Historiales de ingresos, transferencias y pagos
 */
object MedusaFinancialAccessGuard {
    private const val TAG = "MedusaFinancialGuard"

    @Volatile
    private var isFinancialNodeLocked: Boolean = true

    @Volatile
    private var activeRole: MedusaRole = MedusaRole.UNASSIGNED

    private fun safeLogInfo(message: String) {
        try {
            Log.i(TAG, message)
        } catch (_: Throwable) {
            println("[$TAG][INFO] $message")
        }
    }

    private fun safeLogWarn(message: String) {
        try {
            Log.w(TAG, message)
        } catch (_: Throwable) {
            println("[$TAG][WARN] $message")
        }
    }

    /**
     * Aplica la política de seguridad nativa según el rol RBAC activo.
     */
    fun applyRoleSecurityPolicy(role: MedusaRole) {
        activeRole = role
        if (role == MedusaRole.GUARDIA_CASETA) {
            isFinancialNodeLocked = true
            safeLogWarn(
                "🔒 [SEGURIDAD ACTIVA] Ecosistema móvil inicializado para GUARDIA_CASETA. " +
                    "Acceso a nodos de datos financieros BLOQUEADO nativamente."
            )
        } else if (role == MedusaRole.ADMINISTRACION) {
            isFinancialNodeLocked = false
            safeLogInfo("🔓 [SEGURIDAD ACTIVA] Acceso a nodos financieros habilitado para ADMINISTRACION.")
        } else {
            // RESIDENTE o UNASSIGNED no tienen acceso a nodos de administración financiera
            isFinancialNodeLocked = true
            safeLogInfo("🔒 [SEGURIDAD ACTIVA] Nodos financieros protegidos para rol: ${role.name}")
        }
    }

    /**
     * Indica si el acceso a la información financiera se encuentra restringido.
     */
    fun isFinancialAccessBlocked(): Boolean = isFinancialNodeLocked

    /**
     * Retorna el rol activo bajo el cual se evalúa la política financiera.
     */
    fun getActiveRole(): MedusaRole = activeRole

    /**
     * Lanza una excepción de seguridad si un componente intenta consultar o manipular
     * información financiera mientras el bloqueo nativo está activo.
     */
    @Throws(SecurityException::class)
    fun assertFinancialAccessAllowed() {
        if (isFinancialNodeLocked) {
            throw SecurityException(
                "ACCESO DENEGADO POR POLÍTICA MEDUSA ALFHA: El rol $activeRole tiene bloqueado " +
                    "nativamente el acceso a los nodos de información financiera."
            )
        }
    }
}
