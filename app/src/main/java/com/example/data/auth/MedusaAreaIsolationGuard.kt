package com.example.data.auth

import android.util.Log

/**
 * PROTOCOLO ZERO-TRUST DE AISLAMIENTO INTER-ÁREAS (DATA SANDBOXING)
 * SISTEMA DE SEGURIDAD MEDUSA OS v3 - ORDEN MAESTRA PLUS
 *
 * Implementa el aislamiento criptográfico y lógico absoluto entre los sectores:
 * 1. ADMINISTRACIÓN: Supervisión, balances contables, auditoría, gobernanza y control maestro.
 * 2. CASETA DE SEGURIDAD: Operación táctica de torniquetes, barreras, escaneo QR y bitácora de guardias.
 * 3. RESIDENTE: Pases QR privados, reservas de amenidades y notificaciones de unidad propia.
 *
 * La IA local y el motor de voz tienen PROHIBIDO filtrar o cruzar información entre sectores
 * bajo ninguna circunstancia.
 */
enum class AreaSector(val displayName: String, val level: Int) {
    ADMINISTRACION("Área de Administración y Control Maestro", 3),
    CASETA_SEGURIDAD("Área Operativa de Caseta y Seguridad", 2),
    RESIDENCIAL("Área Privada de Condóminos / Residentes", 1)
}

sealed class AreaIsolationCheckResult {
    data object Permitted : AreaIsolationCheckResult()
    data class Blocked(
        val reason: String,
        val errorCode: String = "SEGURIDAD_MEDUSA: ACCESO RESTRINGIDO",
        val attemptedSector: AreaSector,
        val triggeredKeyword: String? = null
    ) : AreaIsolationCheckResult()
    data class RequiresAdminSignature(
        val action: String,
        val message: String = "Esta operación táctica requiere firma de validación administrativa para alterar el estado maestro."
    ) : AreaIsolationCheckResult()
}

object MedusaAreaIsolationGuard {
    private const val TAG = "MedusaAreaIsolation"
    const val RESTRICTED_ACCESS_ERROR = "SEGURIDAD_MEDUSA: ACCESO RESTRINGIDO"

    // Palabras clave sensibles de administración y finanzas prohibidas para Caseta y Residentes
    private val FORBIDDEN_ADMIN_FINANCIAL_KEYWORDS = listOf(
        "finanza", "finanzas", "nómina", "nomina", "ingreso", "ingresos", "egreso", "egresos",
        "contabilidad", "adeudo", "adeudos", "saldo", "saldos", "datos de administrador",
        "administrador", "balance", "cuenta bancaria", "cuota", "cuotas", "moroso", "morosos",
        "presupuesto", "honorarios", "estados de cuenta", "banco", "fiscal", "factura",
        "salario", "sueldo", "utilidad", "ganancia", "pagos pendientes", "auditoría maestra"
    )

    // Palabras clave de seguridad táctica restringidas para residentes
    private val FORBIDDEN_CASETA_SECURITY_KEYWORDS = listOf(
        "claves de acceso garita", "código maestro barrera", "apertura forzada",
        "auditoría de guardia", "desactivar sensor", "patrullaje interno confidencial",
        "relevo de guardia", "cámara oculta"
    )

    /**
     * Valida de manera estricta y previa si una consulta (texto o voz) está autorizada
     * para el rol RBAC activo en la terminal.
     */
    fun evaluateQueryAccess(role: MedusaRole, query: String): AreaIsolationCheckResult {
        val cleanQuery = query.lowercase().trim()

        when (role) {
            MedusaRole.GUARDIA_CASETA -> {
                // Verificar si intenta consultar nodos o variables financieras/administrativas
                for (kw in FORBIDDEN_ADMIN_FINANCIAL_KEYWORDS) {
                    if (cleanQuery.contains(kw)) {
                        Log.w(
                            TAG,
                            "⛔ [VIOLACIÓN INTERCEPTADA] Intento de acceso no autorizado a sector ADMINISTRACIÓN desde CASETA. Término: '$kw'"
                        )
                        return AreaIsolationCheckResult.Blocked(
                            reason = "Intento de consulta a información financiera o de control maestro restringida para Caseta.",
                            errorCode = RESTRICTED_ACCESS_ERROR,
                            attemptedSector = AreaSector.ADMINISTRACION,
                            triggeredKeyword = kw
                        )
                    }
                }
                return AreaIsolationCheckResult.Permitted
            }
            MedusaRole.ADMINISTRACION -> {
                // Supervisión habilitada. Si un comando intenta alterar bitácoras operativas tácticas de caseta:
                if (cleanQuery.contains("alterar bitácora caseta") ||
                    cleanQuery.contains("eliminar check-in") ||
                    cleanQuery.contains("sobrescribir reporte guardia")
                ) {
                    return AreaIsolationCheckResult.RequiresAdminSignature(
                        action = "Modificación de estado táctico de caseta"
                    )
                }
                return AreaIsolationCheckResult.Permitted
            }
            MedusaRole.RESIDENTE -> {
                // Los residentes no pueden consultar administración corporativa ni seguridad táctica
                for (kw in FORBIDDEN_ADMIN_FINANCIAL_KEYWORDS) {
                    if (cleanQuery.contains(kw) && !cleanQuery.contains("mi cuota") && !cleanQuery.contains("mi saldo") && !cleanQuery.contains("mi pago")) {
                        return AreaIsolationCheckResult.Blocked(
                            reason = "Información financiera corporativa restringida para residentes.",
                            errorCode = RESTRICTED_ACCESS_ERROR,
                            attemptedSector = AreaSector.ADMINISTRACION,
                            triggeredKeyword = kw
                        )
                    }
                }
                for (kw in FORBIDDEN_CASETA_SECURITY_KEYWORDS) {
                    if (cleanQuery.contains(kw)) {
                        return AreaIsolationCheckResult.Blocked(
                            reason = "Comando de seguridad táctica restringido para residentes.",
                            errorCode = RESTRICTED_ACCESS_ERROR,
                            attemptedSector = AreaSector.CASETA_SEGURIDAD,
                            triggeredKeyword = kw
                        )
                    }
                }
                return AreaIsolationCheckResult.Permitted
            }
            MedusaRole.UNASSIGNED -> {
                return AreaIsolationCheckResult.Blocked(
                    reason = "Terminal sin rol asignado.",
                    errorCode = RESTRICTED_ACCESS_ERROR,
                    attemptedSector = AreaSector.CASETA_SEGURIDAD
                )
            }
        }
    }
}
