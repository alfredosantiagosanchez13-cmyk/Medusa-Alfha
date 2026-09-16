package com.example.ui.navigation

import com.example.data.auth.MedusaRole

/**
 * Jerarquía sellada (Sealed Class) de navegación segura para MEDUSA ALFHA.
 * Define las rutas oficiales de la API de Jetpack Compose Navigation y sus
 * políticas de autorización RBAC (Role-Based Access Control).
 */
sealed class Screen(
    val route: String,
    val title: String,
    val requiredRole: MedusaRole? = null
) {
    /**
     * Pantalla de Activación inicial: Punto de entrada por defecto para terminales
     * no configuradas (MedusaRole.UNASSIGNED).
     */
    data object ActivationScreen : Screen(
        route = "activation_screen",
        title = "Activación de Terminal",
        requiredRole = null
    )

    /**
     * Panel Maestro de Administración: Exclusivo para personal administrativo.
     * Permite gestión financiera, auditoría, monitoreo de riesgos y enrolamiento.
     */
    data object AdminDashboard : Screen(
        route = "admin_dashboard",
        title = "Panel Maestro Administrativo",
        requiredRole = MedusaRole.ADMINISTRACION
    )

    /**
     * Caseta de Control y Vigilancia: Exclusivo para guardias de caseta.
     * Habilita escaneo QR, lectura de placas, bitácora y bloqueo de nodos financieros.
     */
    data object GuardDashboard : Screen(
        route = "guard_dashboard",
        title = "Caseta de Vigilancia Táctica",
        requiredRole = MedusaRole.GUARDIA_CASETA
    )

    /**
     * Portal del Residente: Exclusivo para residentes y condóminos autenticados.
     * Habilita generación de pases QR, historial de visitas y reservas.
     */
    data object ResidentPortal : Screen(
        route = "resident_portal",
        title = "Portal del Residente",
        requiredRole = MedusaRole.RESIDENTE
    )

    /**
     * Terminal Bloqueada por Intrusión: Destino forzado al detectar accesos
     * indebidos o violaciones a la jerarquía de roles RBAC.
     */
    data object SecurityLockScreen : Screen(
        route = "security_lock/{intrusionToken}?reason={reason}",
        title = "Terminal Bloqueada por Intrusión",
        requiredRole = null
    ) {
        fun createRoute(intrusionToken: String, reason: String = "Acceso no autorizado"): String {
            val encodedReason = java.net.URLEncoder.encode(reason, "UTF-8")
            return "security_lock/$intrusionToken?reason=$encodedReason"
        }
    }

    companion object {
        /**
         * Resuelve una pantalla a partir de su ruta textual de Compose.
         */
        fun fromRoute(route: String?): Screen {
            return when {
                route == null -> ActivationScreen
                route.startsWith("security_lock") -> SecurityLockScreen
                route == AdminDashboard.route -> AdminDashboard
                route == GuardDashboard.route -> GuardDashboard
                route == ResidentPortal.route -> ResidentPortal
                else -> ActivationScreen
            }
        }
    }
}
