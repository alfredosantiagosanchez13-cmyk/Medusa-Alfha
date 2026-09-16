package com.example.data.auth

/**
 * Data Class oficial de Sesión Real de Usuario para producción en MEDUSA ALFHA.
 * Modela el enclave de identidad y persistencia en el dispositivo.
 *
 * Filosofía corporativa: "Tiempo = Familia", la IA permanece y aprende.
 *
 * @param activationKey Llave criptográfica canónica utilizada para enrolar la terminal.
 * @param currentRole Nivel de acceso y autorización RBAC asignado a esta terminal.
 * @param condominiumId Identificador único del desarrollo habitacional (ej: "PRADOS_1").
 * @param assignedUnitId Identificador de la unidad residencial (ej: "Casa 73", o vacío para administración).
 * @param condominiumName Nombre formal del condominio (ej: "Los Prados 1").
 * @param isActive Bandera que certifica si la sesión se encuentra vigente y no revocada.
 * @param isFinancialBlocked Bandera de aislamiento estricto de nodos financieros (activa para guardias de caseta).
 * @param timestampMillis Marca de tiempo UNIX del enrolamiento o última validación exitosa.
 */
data class UserSession(
    val activationKey: String,
    val currentRole: MedusaRole,
    val condominiumId: String,
    val assignedUnitId: String = "",
    val condominiumName: String = "Los Prados 1",
    val isActive: Boolean = true,
    val isFinancialBlocked: Boolean = (currentRole == MedusaRole.GUARDIA_CASETA),
    val timestampMillis: Long = System.currentTimeMillis()
) {
    /**
     * Propiedades de interoperabilidad y retrocompatibilidad con componentes existentes.
     */
    val role: MedusaRole get() = currentRole
    val keyId: String get() = activationKey
    val assignedUnit: String? get() = assignedUnitId.ifBlank { null }
    val activatedAtMillis: Long get() = timestampMillis

    /**
     * Determina si la sesión corresponde a un residente de las 300 viviendas.
     */
    fun isResident(): Boolean = currentRole == MedusaRole.RESIDENTE

    /**
     * Determina si la sesión corresponde a la administración general.
     */
    fun isAdmin(): Boolean = currentRole == MedusaRole.ADMINISTRACION

    /**
     * Determina si la sesión corresponde a un puesto táctico de caseta.
     */
    fun isGuard(): Boolean = currentRole == MedusaRole.GUARDIA_CASETA
}

/**
 * Alias de compatibilidad con la denominación previa de sesión.
 */
typealias MedusaSessionData = UserSession
