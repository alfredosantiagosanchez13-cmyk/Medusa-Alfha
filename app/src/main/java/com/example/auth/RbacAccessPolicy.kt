package com.example.auth

enum class UserRole(val displayName: String, val description: String) {
    GUARD(
        displayName = "Guardia de Caseta",
        description = "Restringido únicamente a actualización de estado de visitantes y gestión de alertas."
    ),
    ADMIN(
        displayName = "Administrador de Sistema",
        description = "Acceso total a configuración del sistema, parámetros de seguridad y auditorías."
    )
}

enum class SystemPermission(val label: String, val category: String) {
    // Guard Permissions
    UPDATE_VISITOR_STATUS("Actualizar Estado de Visitante", "Gestión de Visitas"),
    MANAGE_PANIC_ALERTS("Gestionar Alertas de Pánico", "Atención de Emergencias"),
    SCAN_QR_PASS("Escanear Pase QR y Capturar Foto", "Control de Accesos"),

    // Admin-Only Permissions
    CONFIGURE_SYSTEM_SETTINGS("Configuración de Parámetros del Sistema", "Administración"),
    MODIFY_SECURITY_POLICIES("Modificar Políticas de Seguridad", "Administración"),
    AUDIT_FULL_DATABASE("Auditoría Completa de Base de Datos", "Reportes y Auditoría"),
    SYSTEM_RESET_AND_CLEAR("Resetear y Limpiar Registros", "Mantenimiento")
}

object RbacManager {

    private val guardPermissions = setOf(
        SystemPermission.UPDATE_VISITOR_STATUS,
        SystemPermission.MANAGE_PANIC_ALERTS,
        SystemPermission.SCAN_QR_PASS
    )

    private val adminPermissions = SystemPermission.values().toSet()

    fun hasPermission(role: UserRole, permission: SystemPermission): Boolean {
        return when (role) {
            UserRole.GUARD -> guardPermissions.contains(permission)
            UserRole.ADMIN -> adminPermissions.contains(permission)
        }
    }

    fun getPermissionsForRole(role: UserRole): Set<SystemPermission> {
        return when (role) {
            UserRole.GUARD -> guardPermissions
            UserRole.ADMIN -> adminPermissions
        }
    }

    /**
     * Checks whether an intent or prompt command is allowed under the current user role.
     */
    fun evaluateAiCommandAccess(role: UserRole, commandQuery: String): RbacEvaluationResult {
        val queryLower = commandQuery.lowercase()

        // Detect admin-only system configuration intent keywords
        val isSystemConfigQuery = queryLower.contains("configurar") ||
                queryLower.contains("configuracion") ||
                queryLower.contains("configuración") ||
                queryLower.contains("sensibilidad") ||
                queryLower.contains("umbral") ||
                queryLower.contains("politica") ||
                queryLower.contains("política") ||
                queryLower.contains("borrar base de datos") ||
                queryLower.contains("limpiar registros") ||
                queryLower.contains("resetear") ||
                queryLower.contains("parámetros de cámara") ||
                queryLower.contains("parametros")

        if (role == UserRole.GUARD && isSystemConfigQuery) {
            return RbacEvaluationResult.AccessDenied(
                requiredPermission = SystemPermission.CONFIGURE_SYSTEM_SETTINGS,
                reason = "El rol 'Guardia' tiene un conjunto de comandos restringido por política de acceso RBAC. Las configuraciones y ajustes de parámetros del sistema requieren permisos de 'Administrador'."
            )
        }

        return RbacEvaluationResult.AccessGranted
    }
}

sealed class RbacEvaluationResult {
    object AccessGranted : RbacEvaluationResult()
    data class AccessDenied(
        val requiredPermission: SystemPermission,
        val reason: String
    ) : RbacEvaluationResult()
}
