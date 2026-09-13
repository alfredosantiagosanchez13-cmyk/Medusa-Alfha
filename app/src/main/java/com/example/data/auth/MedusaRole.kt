package com.example.data.auth

/**
 * Control de Acceso Basado en Roles (RBAC) para MEDUSA ALFHA.
 * Define las identidades jerárquicas y niveles de autorización en el ecosistema móvil.
 */
enum class MedusaRole(
    val code: String,
    val displayName: String,
    val description: String
) {
    ADMINISTRACION(
        code = "ADMINISTRACION",
        displayName = "Administración del Condominio",
        description = "Acceso total a finanzas, auditoría, configuración de condominios y supervisión general"
    ),
    GUARDIA_CASETA(
        code = "GUARDIA_CASETA",
        displayName = "Guardia de Caseta / Control de Acceso",
        description = "Operación de torniquetes, barreras, escaneo QR y bitácora táctica con bloqueo de nodos financieros"
    ),
    RESIDENTE(
        code = "RESIDENTE",
        displayName = "Residente / Condómino",
        description = "Generación de pases QR, reserva de amenidades y recepción de comunicados comunitarios"
    ),
    UNASSIGNED(
        code = "UNASSIGNED",
        displayName = "Sin Rol Asignado",
        description = "Perfil sin autorizaciones ni acceso a los módulos operativos de MEDUSA ALFHA"
    );

    /**
     * Determina si el rol tiene privilegios para consultar o modificar datos financieros
     * (cuotas de mantenimiento, estados de cuenta, balances, morosidad).
     */
    fun hasFinancialAccess(): Boolean {
        return this == ADMINISTRACION
    }

    /**
     * Determina si el rol requiere bloqueo nativo de nodos financieros.
     * Si es GUARDIA_CASETA, el bloqueo es estricto e inmutable.
     */
    fun requiresFinancialNodeLock(): Boolean {
        return this == GUARDIA_CASETA || this == UNASSIGNED
    }

    companion object {
        /**
         * Transforma una cadena de texto proveniente de Firestore o API en el Enum MedusaRole correspondiente.
         */
        fun fromString(value: String?): MedusaRole {
            if (value.isNullOrBlank()) return UNASSIGNED
            val normalized = value.trim().uppercase()
            return when {
                normalized == "ADMINISTRACION" || normalized == "ADMIN" || normalized == "ADMINISTRADOR" -> ADMINISTRACION
                normalized == "GUARDIA_CASETA" || normalized == "GUARDIA" || normalized == "CASETA" || normalized == "SEGURIDAD" -> GUARDIA_CASETA
                normalized == "RESIDENTE" || normalized == "CONDOMINO" || normalized == "PROPIETARIO" -> RESIDENTE
                normalized == "UNASSIGNED" -> UNASSIGNED
                else -> {
                    entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) } ?: UNASSIGNED
                }
            }
        }
    }
}
