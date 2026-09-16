package com.example.ui.navigation

import android.app.Application
import android.util.Log
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.audit.AuditLogEntity
import com.example.data.auth.MedusaRole
import com.example.data.auth.UserSession
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.ui.screens.ActivationScreen
import com.example.ui.screens.MasterPanelAlphaScreen
import com.example.ui.screens.PortalResidentesScreen
import com.example.ui.screens.SecurityLockScreen
import com.example.ui.screens.SecurityScannerScreen
import com.example.ui.viewmodel.ActivationViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.util.UUID

private const val TAG = "MedusaNavGraph"

/**
 * Enrutador Central y NavHost Seguro para MEDUSA ALFHA.
 *
 * Filosofía corporativa: "Tiempo = Familia", la IA permanece y aprende.
 *
 * Pilares de Implementación:
 * 1. ENUMS Y DATA MODEL DE SESIÓN REAL:
 *    - Opera reactivamente con el estado de sesión [UserSession] y el Enum [MedusaRole].
 * 2. NAVHOST CON CLÁUSULAS DE SEGURIDAD DEFENSIVAS (RBAC REAL):
 *    - Panel Maestro (Admin): if (session.currentRole != MedusaRole.ADMINISTRACION) throw SecurityException("Intrusión detectada en Panel Maestro")
 *    - Caseta de Vigilancia: if (session.currentRole != MedusaRole.GUARDIA_CASETA) throw SecurityException("Intrusión detectada en Caseta de Vigilancia")
 *    - Portal del Residente: if (session.currentRole != MedusaRole.RESIDENTE) throw SecurityException("Intrusión detectada en Portal del Residente")
 *    - Ante cualquier infracción: purga la sesión activa, asienta el evento en la auditoría Room inmutable
 *      y redirige instantáneamente a la pantalla de bloqueo de PIN/Clave.
 * 3. PERSISTENCIA MÓVIL Y DESTRUCCIÓN DE BUFFER EN MEMORIA:
 *    - Al validar con éxito la llave de activación de un residente de las 300 viviendas o cualquier rol autorizado,
 *      limpia el historial de navegación de forma absoluta mediante `popUpTo(Screen.ActivationScreen.route) { inclusive = true }`.
 *      Esto impide físicamente que el botón "Atrás" del dispositivo Android pueda revelar o regresar a un panel previo.
 */
@Composable
fun MedusaNavGraph(
    navController: NavHostController = rememberNavController(),
    currentSession: UserSession? = null,
    modifier: Modifier = Modifier,
    activationViewModel: ActivationViewModel = viewModel(
        factory = ActivationViewModel.provideFactory(LocalContext.current.applicationContext as Application)
    )
) {
    // Extracción reactiva de la sesión desde el StateFlow del ActivationViewModel si no se pasa de forma estática
    val reactiveSession by activationViewModel.currentSession.collectAsState()
    val session = currentSession ?: reactiveSession
    val activeRole = session?.currentRole ?: MedusaRole.UNASSIGNED

    // Sincronización reactiva del rol y destrucción de búfer al cambiar la sesión
    LaunchedEffect(session?.activationKey, activeRole) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        Log.i(TAG, "🔐 Sincronización reactiva de sesión: Rol=$activeRole, Clave=${session?.activationKey}, Ruta=$currentRoute")

        when (activeRole) {
            MedusaRole.ADMINISTRACION -> {
                if (currentRoute != Screen.AdminDashboard.route) {
                    navController.navigate(Screen.AdminDashboard.route) {
                        popUpTo(Screen.ActivationScreen.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            MedusaRole.GUARDIA_CASETA -> {
                if (currentRoute != Screen.GuardDashboard.route) {
                    navController.navigate(Screen.GuardDashboard.route) {
                        popUpTo(Screen.ActivationScreen.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            MedusaRole.RESIDENTE -> {
                // Al validar con éxito la llave de activación de un residente de las 300 viviendas,
                // el enrutador limpia el historial de navegación de forma absoluta
                if (currentRoute != Screen.ResidentPortal.route) {
                    navController.navigate(Screen.ResidentPortal.route) {
                        popUpTo(Screen.ActivationScreen.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            MedusaRole.UNASSIGNED -> {
                if (currentRoute != null && currentRoute != Screen.ActivationScreen.route && !currentRoute.startsWith("security_lock")) {
                    navController.navigate(Screen.ActivationScreen.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.ActivationScreen.route,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(220)) },
        exitTransition = { fadeOut(animationSpec = tween(180)) },
        popEnterTransition = { fadeIn(animationSpec = tween(220)) },
        popExitTransition = { fadeOut(animationSpec = tween(180)) }
    ) {

        // =========================================================================
        // 1. PANTALLA DE INICIO POR DEFECTO: Activación para terminales sin asignar
        // =========================================================================
        composable(route = Screen.ActivationScreen.route) {
            ActivationScreen(
                activationViewModel = activationViewModel,
                onActivationSuccess = { authenticatedRole ->
                    Log.i(TAG, "✅ Activación exitosa con rol: $authenticatedRole. Purgando búfer previo.")
                    when (authenticatedRole) {
                        MedusaRole.ADMINISTRACION -> {
                            navController.navigate(Screen.AdminDashboard.route) {
                                popUpTo(Screen.ActivationScreen.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                        MedusaRole.GUARDIA_CASETA -> {
                            navController.navigate(Screen.GuardDashboard.route) {
                                popUpTo(Screen.ActivationScreen.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                        MedusaRole.RESIDENTE -> {
                            // Destrucción absoluta de buffer para residente de las 300 viviendas
                            navController.navigate(Screen.ResidentPortal.route) {
                                popUpTo(Screen.ActivationScreen.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                        MedusaRole.UNASSIGNED -> {
                            // Permanece en activación
                        }
                    }
                }
            )
        }

        // =========================================================================
        // 2. PANEL MAESTRO DE ADMINISTRACIÓN (Exclusivo para MedusaRole.ADMINISTRACION)
        // =========================================================================
        composable(route = Screen.AdminDashboard.route) {
            RbacDefensiveGuard(
                targetScreen = Screen.AdminDashboard,
                currentSession = session,
                activationViewModel = activationViewModel,
                navController = navController
            ) {
                MasterPanelAlphaScreen()
            }
        }

        // =========================================================================
        // 3. CASETA DE VIGILANCIA TÁCTICA (Exclusivo para MedusaRole.GUARDIA_CASETA)
        // =========================================================================
        composable(route = Screen.GuardDashboard.route) {
            RbacDefensiveGuard(
                targetScreen = Screen.GuardDashboard,
                currentSession = session,
                activationViewModel = activationViewModel,
                navController = navController
            ) {
                SecurityScannerScreen(
                    onSignOut = {
                        activationViewModel.logout()
                        navController.navigate(Screen.ActivationScreen.route) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        // =========================================================================
        // 4. PORTAL DEL RESIDENTE (Exclusivo para MedusaRole.RESIDENTE)
        // =========================================================================
        composable(route = Screen.ResidentPortal.route) {
            RbacDefensiveGuard(
                targetScreen = Screen.ResidentPortal,
                currentSession = session,
                activationViewModel = activationViewModel,
                navController = navController
            ) {
                PortalResidentesScreen(
                    activationViewModel = activationViewModel,
                    onLogoutClick = {
                        activationViewModel.logout()
                        navController.navigate(Screen.ActivationScreen.route) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }

        // =========================================================================
        // 5. PANTALLA DE BLOQUEO POR INTRUSIÓN Y VIOLACIÓN RBAC (PIN / CLAVE)
        // =========================================================================
        composable(
            route = Screen.SecurityLockScreen.route,
            arguments = listOf(
                navArgument("intrusionToken") { type = NavType.StringType },
                navArgument("reason") {
                    type = NavType.StringType
                    defaultValue = "Acceso no autorizado al nodo protegido"
                }
            )
        ) { backStackEntry ->
            val token = backStackEntry.arguments?.getString("intrusionToken") ?: "SEC-UNKNOWN-TOKEN"
            val rawReason = backStackEntry.arguments?.getString("reason") ?: "Acceso no autorizado"
            val decodedReason = try {
                URLDecoder.decode(rawReason, "UTF-8")
            } catch (e: Exception) {
                rawReason
            }

            SecurityLockScreen(
                intrusionToken = token,
                reason = decodedReason,
                onResetTerminal = {
                    Log.w(TAG, "🔄 Terminal reseteada tras intrusión: $token")
                    activationViewModel.logout()
                    navController.navigate(Screen.ActivationScreen.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

/**
 * Sobrecarga de conveniencia que acepta el Enum [MedusaRole] directamente para interoperabilidad.
 */
@Composable
fun MedusaNavGraph(
    currentRole: MedusaRole,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    activationViewModel: ActivationViewModel = viewModel(
        factory = ActivationViewModel.provideFactory(LocalContext.current.applicationContext as Application)
    )
) {
    val session by activationViewModel.currentSession.collectAsState()
    MedusaNavGraph(
        navController = navController,
        currentSession = session,
        modifier = modifier,
        activationViewModel = activationViewModel
    )
}

/**
 * Cláusula de Guarda Defensiva RBAC en tiempo de ejecución.
 *
 * Aplica las reglas estrictas de autorización de la Orden Maestra:
 * - AdminDashboard: if (session.currentRole != MedusaRole.ADMINISTRACION) throw SecurityException("Intrusión detectada en Panel Maestro")
 * - GuardDashboard: if (session.currentRole != MedusaRole.GUARDIA_CASETA) throw SecurityException("Intrusión detectada en Caseta de Vigilancia")
 * - ResidentPortal: if (session.currentRole != MedusaRole.RESIDENTE) throw SecurityException("Intrusión detectada en Portal del Residente")
 *
 * Ante cualquier fallo:
 * 1. Dispara la excepción normativamente para auditoría de pila.
 * 2. Borra la sesión en memoria y SharedPreferences mediante `activationViewModel.logout()`.
 * 3. Registra el evento en la auditoría local inmutable de Room [AppDatabase.auditLogDao].
 * 4. Redirige instantáneamente a la pantalla de bloqueo de PIN/Clave [Screen.SecurityLockScreen]
 *    con purga total del backstack (`popUpTo(0)`).
 */
@Composable
private fun RbacDefensiveGuard(
    targetScreen: Screen,
    currentSession: UserSession?,
    activationViewModel: ActivationViewModel,
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    var intrusionDispatched by remember { mutableStateOf(false) }

    val isAuthorized = when (targetScreen) {
        Screen.AdminDashboard -> currentSession?.currentRole == MedusaRole.ADMINISTRACION
        Screen.GuardDashboard -> currentSession?.currentRole == MedusaRole.GUARDIA_CASETA
        Screen.ResidentPortal -> currentSession?.currentRole == MedusaRole.RESIDENTE
        else -> true
    }

    if (!isAuthorized) {
        LaunchedEffect(targetScreen.route, currentSession?.currentRole) {
            if (!intrusionDispatched) {
                intrusionDispatched = true
                val intrusionToken = "INTRUSION-${UUID.randomUUID().toString().take(8).uppercase()}-${System.currentTimeMillis()}"

                try {
                    // Cláusulas de seguridad defensivas estrictas de la Orden Maestra:
                    when (targetScreen) {
                        Screen.AdminDashboard -> {
                            val session = currentSession
                            if (session == null || session.currentRole != MedusaRole.ADMINISTRACION) {
                                throw SecurityException("Intrusión detectada en Panel Maestro")
                            }
                        }
                        Screen.GuardDashboard -> {
                            val session = currentSession
                            if (session == null || session.currentRole != MedusaRole.GUARDIA_CASETA) {
                                throw SecurityException("Intrusión detectada en Caseta de Vigilancia")
                            }
                        }
                        Screen.ResidentPortal -> {
                            val session = currentSession
                            if (session == null || session.currentRole != MedusaRole.RESIDENTE) {
                                throw SecurityException("Intrusión detectada en Portal del Residente")
                            }
                        }
                        else -> {
                            throw SecurityException("Intrusión detectada en ruta protegida: ${targetScreen.route}")
                        }
                    }
                } catch (secEx: SecurityException) {
                    val intrusionMessage = secEx.message ?: "Intrusión detectada"
                    Log.e(
                        TAG,
                        "🚨 VIOLACIÓN CRÍTICA RBAC: $intrusionMessage | Token: $intrusionToken | Rol intento: ${currentSession?.currentRole} | Unidad: ${currentSession?.assignedUnitId}",
                        secEx
                    )

                    // 1. Borra la sesión en el dispositivo
                    activationViewModel.logout()

                    // 2. Registra el evento en la auditoría inmutable de Room
                    withContext(Dispatchers.IO) {
                        try {
                            val forensicJson = """{"token":"$intrusionToken","route":"${targetScreen.route}","attemptedRole":"${currentSession?.currentRole ?: MedusaRole.UNASSIGNED}","assignedUnit":"${currentSession?.assignedUnitId ?: ""}","condo":"${currentSession?.condominiumName ?: ""}"}"""
                            val auditLog = AuditLogEntity(
                                logId = intrusionToken,
                                timestamp = System.currentTimeMillis(),
                                operatorId = currentSession?.activationKey?.ifBlank { "TERMINAL_INTRUSORA" } ?: "TERMINAL_INTRUSORA",
                                eventDescription = "$intrusionMessage. Acceso bloqueado en ${targetScreen.title}.",
                                severity = AuditLogEntity.Severity.SECURITY_CRITICAL,
                                forensicPayload = forensicJson
                            )
                            db.securityAuditDao().insertLog(auditLog)
                            Log.i(TAG, "📝 Auditoría inmutable de intrusión asentada en Room con ID: ${auditLog.logId}")
                        } catch (e: Exception) {
                            Log.e(TAG, "Error persistiendo auditoría de intrusión en Room", e)
                        }
                    }

                    // 3. Redirige instantáneamente a la pantalla de bloqueo de PIN/Clave con destrucción de búfer
                    navController.navigate(Screen.SecurityLockScreen.createRoute(intrusionToken, intrusionMessage)) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }

        // Cortocircuito defensivo: Previene absolutamente la renderización del nodo protegido
        return
    }

    // Autorización certificada: Renderiza el contenido seguro
    content()
}
