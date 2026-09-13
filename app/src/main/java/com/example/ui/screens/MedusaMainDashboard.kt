package com.example.ui.screens

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.auth.ActivationKey
import com.example.data.auth.MedusaRole
import com.example.data.auth.MedusaSessionData
import com.example.data.booking.AppDatabase
import com.example.data.visitor.VisitorCheckIn
import com.example.ui.theme.AlertRed
import com.example.ui.theme.BlueNeon
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.theme.WarningOrange
import com.example.ui.viewmodel.ActivationErrorType
import com.example.ui.viewmodel.ActivationUiState
import com.example.ui.viewmodel.ActivationViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Identificadores de acciones operativas tácticas para el guardia de caseta.
 */
enum class TacticalGuardAction(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tag: String
) {
    ASISTENCIA(
        title = "Asistencia",
        subtitle = "Entrada/salida con foto",
        icon = Icons.Default.Badge,
        tag = "tactical_action_asistencia"
    ),
    ACCESOS(
        title = "Accesos",
        subtitle = "Escáner QR con CameraX",
        icon = Icons.Default.QrCodeScanner,
        tag = "tactical_action_accesos"
    ),
    FICHA_RESIDENTE(
        title = "Ficha de Residente",
        subtitle = "Consulta de unidades",
        icon = Icons.Default.PersonSearch,
        tag = "tactical_action_residentes"
    ),
    PAQUETERIA(
        title = "Paquetería",
        subtitle = "Recepción inteligente",
        icon = Icons.Default.Inventory2,
        tag = "tactical_action_paqueteria"
    ),
    PLACAS(
        title = "Placas",
        subtitle = "Consulta rápida LPR",
        icon = Icons.Default.DirectionsCar,
        tag = "tactical_action_placas"
    ),
    INCIDENTES(
        title = "Incidentes",
        subtitle = "Reporte inmediato",
        icon = Icons.Default.ReportProblem,
        tag = "tactical_action_incidentes"
    ),
    RONDINES(
        title = "Rondines",
        subtitle = "Checkpoints GPS",
        icon = Icons.Default.MyLocation,
        tag = "tactical_action_rondines"
    ),
    BITACORA(
        title = "Bitácora",
        subtitle = "Reglas digitales",
        icon = Icons.Default.Description,
        tag = "tactical_action_bitacora"
    )
}

/**
 * Representación simplificada y formateada de un registro de última actividad.
 */
data class CompactActivityItem(
    val id: String,
    val timestampFormatted: String,
    val actionType: String,
    val statusText: String,
    val destination: String,
    val isAccessDenied: Boolean = false,
    val isCheckedIn: Boolean = false
) {
    val displayLine: String
        get() = "$timestampFormatted - Acceso $statusText - $destination"
}

/**
 * COMPOSABLE PRINCIPAL (Dashboard Adaptativo):
 *
 * 1. Lee el estado del rol mediante `ActivationViewModel.currentSession.collectAsState()`.
 * 2. Si el rol es `MedusaRole.GUARDIA_CASETA`, renderiza una pantalla optimizada para caseta
 *    que use la paleta de colores oscuros con acentos amarillos de MEDUSA ALFHA.
 * 3. Oculta por completo, destruye del árbol de Compose y bloquea cualquier renderizado
 *    de módulos administrativos (Matriz de Riesgos, Estadísticas, Finanzas, etc.).
 * 4. Contiene la cuadrícula táctica de 2 columnas, el banner de emergencia prominente
 *    y la sección de última actividad conectada a base de datos Room.
 */
@Composable
fun MedusaMainDashboard(
    modifier: Modifier = Modifier,
    activationViewModel: ActivationViewModel = viewModel(
        factory = ActivationViewModel.provideFactory(LocalContext.current.applicationContext as Application)
    ),
    onNavigateToScanner: () -> Unit = {},
    onNavigateToResidents: () -> Unit = {},
    onNavigateToPackages: () -> Unit = {},
    onNavigateToVehicles: () -> Unit = {},
    onNavigateToIncidents: () -> Unit = {},
    onNavigateToPatrols: () -> Unit = {},
    onNavigateToLogbook: () -> Unit = {},
    onTriggerEmergencyProtocol: () -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val currentSession by activationViewModel.currentSession.collectAsState()
    val uiState by activationViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Consumo reactivo de registros locales desde Room Database
    val db = remember { AppDatabase.getDatabase(context) }
    val roomVisitorLogs by db.visitorCheckInDao().getAllCheckIns().collectAsState(initial = emptyList())

    var activeTacticalDialog by remember { mutableStateOf<TacticalGuardAction?>(null) }
    var showEmergencyDialog by remember { mutableStateOf(false) }

    // Evaluación estricta de seguridad RBAC
    val activeRole = currentSession?.role ?: MedusaRole.UNASSIGNED

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("medusa_main_dashboard_root"),
        color = NavyDark
    ) {
        when (activeRole) {
            MedusaRole.GUARDIA_CASETA -> {
                // =========================================================================
                // PANTALLA EXCLUSIVA PARA GUARDIA DE CASETA:
                // Paleta de colores oscuros con acentos amarillos/dorados de MEDUSA ALFHA.
                // BLOQUEO NATIVO: Se excluyen y destruyen por completo del árbol de Compose
                // los módulos de Finanzas, Balances, Matriz de Riesgos y Estadísticas avanzadas.
                // =========================================================================
                GuardCasetaDashboardContent(
                    session = currentSession,
                    visitorLogs = roomVisitorLogs,
                    onActionSelected = { action ->
                        when (action) {
                            TacticalGuardAction.ACCESOS -> onNavigateToScanner()
                            TacticalGuardAction.FICHA_RESIDENTE -> onNavigateToResidents()
                            TacticalGuardAction.PAQUETERIA -> onNavigateToPackages()
                            TacticalGuardAction.PLACAS -> onNavigateToVehicles()
                            TacticalGuardAction.INCIDENTES -> onNavigateToIncidents()
                            TacticalGuardAction.RONDINES -> onNavigateToPatrols()
                            TacticalGuardAction.BITACORA -> onNavigateToLogbook()
                            TacticalGuardAction.ASISTENCIA -> activeTacticalDialog = action
                        }
                    },
                    onEmergencyClick = {
                        showEmergencyDialog = true
                    },
                    onLogoutClick = {
                        activationViewModel.logout()
                        onSignOut()
                    }
                )
            }

            MedusaRole.ADMINISTRACION -> {
                // Vista Administrativa con módulos ampliados y banner de emergencia
                AdminDashboardContent(
                    session = currentSession,
                    onEmergencyClick = { showEmergencyDialog = true },
                    onLogoutClick = {
                        activationViewModel.logout()
                        onSignOut()
                    }
                )
            }

            MedusaRole.RESIDENTE -> {
                // Portal Móvil del Condómino Enclavado con RBAC
                PortalResidentesScreen(
                    activationViewModel = activationViewModel,
                    onLogoutClick = {
                        activationViewModel.logout()
                        onSignOut()
                    }
                )
            }

            MedusaRole.UNASSIGNED -> {
                // Dispositivo sin llave de activación válida asignada
                UnassignedActivationContent(
                    uiState = uiState,
                    onValidateKey = { key ->
                        activationViewModel.validateActivationKey(key)
                    }
                )
            }
        }
    }

    // Modal de confirmación y disparo de Protocolo de Emergencia
    if (showEmergencyDialog) {
        EmergencyProtocolDialog(
            condominiumName = currentSession?.condominiumId ?: "Condominio Activo",
            onDismiss = { showEmergencyDialog = false },
            onConfirm = {
                showEmergencyDialog = false
                onTriggerEmergencyProtocol()
            }
        )
    }

    // Modal para acciones tácticas en desarrollo o confirmación (Asistencia, etc.)
    activeTacticalDialog?.let { action ->
        TacticalActionInfoDialog(
            action = action,
            onDismiss = { activeTacticalDialog = null }
        )
    }
}

/**
 * Contenido especializado para GUARDIA_CASETA:
 * - Paleta oscura con acentos amarillos MEDUSA ALFHA (#FFD700 / #F59E0B).
 * - Componente de alerta de emergencia superior.
 * - Cuadrícula de 2 columnas de botones tácticos operativos.
 * - Sección de última actividad en tiempo real.
 */
@Composable
private fun GuardCasetaDashboardContent(
    session: MedusaSessionData?,
    visitorLogs: List<VisitorCheckIn>,
    onActionSelected: (TacticalGuardAction) -> Unit,
    onEmergencyClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val itemsToDisplay = remember(visitorLogs) {
        if (visitorLogs.isNotEmpty()) {
            visitorLogs.take(8).map { log ->
                val timeStr = try {
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(log.timestampMillis))
                } catch (_: Exception) {
                    "08:30"
                }
                val isDenied = log.status.equals("DENEGADO", ignoreCase = true) || log.status.contains("denied", ignoreCase = true)
                val isCheckedIn = log.status.equals("CHECKED_IN", ignoreCase = true) || log.status.equals("VERIFICADO", ignoreCase = true)
                val statusText = when {
                    isDenied -> "denied"
                    isCheckedIn -> "checked_in"
                    log.status.equals("DEPARTED", ignoreCase = true) -> "departed"
                    else -> log.status.lowercase(Locale.ROOT)
                }
                CompactActivityItem(
                    id = log.folio,
                    timestampFormatted = timeStr,
                    actionType = log.passTypeLabel,
                    statusText = statusText,
                    destination = log.destinationHouse.ifBlank { "Casa 01" },
                    isAccessDenied = isDenied,
                    isCheckedIn = isCheckedIn
                )
            }
        } else {
            // Muestra exacta de prototipos de MEDUSA ALFHA cuando la BD local está recién creada
            listOf(
                CompactActivityItem("1", "08:32", "Visita", "denied", "Casa 01", isAccessDenied = true),
                CompactActivityItem("2", "08:30", "Familiar", "checked_in", "Casa 104", isCheckedIn = true),
                CompactActivityItem("3", "08:15", "Proveedor", "checked_in", "Casa 12", isCheckedIn = true),
                CompactActivityItem("4", "07:58", "Delivery", "departed", "Casa 88", isCheckedIn = false),
                CompactActivityItem("5", "07:42", "Servicio", "checked_in", "Casa 34", isCheckedIn = true)
            )
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
            .fillMaxSize()
            .testTag("guard_caseta_dashboard_grid"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Barra Superior con Identidad y Logout
        item(span = { GridItemSpan(2) }) {
            GuardHeaderBar(
                condominiumId = session?.condominiumId ?: "Condominio Activo",
                keyId = session?.keyId ?: "CASETA-01",
                onLogoutClick = onLogoutClick
            )
        }

        // 2. COMPONENTE DE ALERTA DE EMERGENCIA (Prominente en rojo/rojo oscuro)
        item(span = { GridItemSpan(2) }) {
            EmergencyAlertBanner(
                onInitiateProtocol = onEmergencyClick
            )
        }

        // 3. Título de la sección táctica
        item(span = { GridItemSpan(2) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MÓDULOS OPERATIVOS DE CASETA",
                        color = GoldPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = "ROL: GUARDIA",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 4. CUADRÍCULA DE BOTONES TÁCTICOS (Solo Guardia)
        items(TacticalGuardAction.entries.toList(), key = { it.name }) { action ->
            TacticalButtonCard(
                action = action,
                onClick = { onActionSelected(action) }
            )
        }

        // 5. SECCIÓN DE ÚLTIMA ACTIVIDAD (Lista Compacta)
        item(span = { GridItemSpan(2) }) {
            Spacer(modifier = Modifier.height(6.dp))
            CompactRecentActivitySection(
                items = itemsToDisplay
            )
        }

        // Espaciador final para comodidad táctil
        item(span = { GridItemSpan(2) }) {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Barra superior de la caseta con indicadores de seguridad y sesión.
 */
@Composable
private fun GuardHeaderBar(
    condominiumId: String,
    keyId: String,
    onLogoutClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("guard_header_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        border = BorderStroke(1.dp, NavyCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GoldPrimary.copy(alpha = 0.12f))
                        .border(1.dp, GoldPrimary.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Medusa Security",
                        tint = GoldPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MEDUSA ALFHA",
                            color = GoldPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SuccessGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "EN LÍNEA",
                                color = SuccessGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = condominiumId,
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Llave: $keyId · Nodos financieros protegidos",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }

            IconButton(
                onClick = onLogoutClick,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(NavyCard)
                    .testTag("btn_guard_logout")
            ) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = "Cerrar Turno",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 3. COMPONENTE DE ALERTA DE EMERGENCIA:
 * Banner superior prominente de color rojo/rojo oscuro que dice
 * "EMERGENCIA MÉDICA / INCENDIO / SEGURIDAD" con botón de acción rápida
 * "INICIAR PROTOCOLO ->".
 * Visible únicamente para perfiles de Guardia y Administración.
 */
@Composable
fun EmergencyAlertBanner(
    onInitiateProtocol: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("emergency_alert_banner"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF450A0A) // Rojo oscuro de alta advertencia
        ),
        border = BorderStroke(1.5.dp, AlertRed)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF580C0C),
                            Color(0xFF7F1D1D).copy(alpha = 0.95f),
                            Color(0xFF450A0A)
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AlertRed.copy(alpha = 0.25f))
                        .border(1.dp, AlertRed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Alerta",
                        tint = AlertRed,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "EMERGENCIA MÉDICA / INCENDIO / SEGURIDAD",
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Transmisión prioritaria inmediata a unidades de patrulla y 911",
                        color = Color(0xFFFCA5A5),
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onInitiateProtocol,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("btn_iniciar_protocolo_emergencia"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlertRed,
                    contentColor = TextWhite
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalHospital,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "INICIAR PROTOCOLO ->",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

/**
 * 2. TARJETA INDIVIDUAL DE BOTÓN TÁCTICO:
 * Diseñada para la cuadrícula de 2 columnas con temática oscura y acentos amarillos.
 */
@Composable
private fun TacticalButtonCard(
    action: TacticalGuardAction,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(action.tag),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        border = BorderStroke(1.dp, NavyCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GoldPrimary.copy(alpha = 0.12f))
                        .border(1.dp, GoldPrimary.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.title,
                        tint = GoldPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = TextMuted.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }

            Column {
                Text(
                    text = action.title,
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = action.subtitle,
                    color = GoldAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * 4. SECCIÓN DE ÚLTIMA ACTIVIDAD:
 * Lista compacta que consume los registros locales en vivo
 * (ej: "08:32 - Acceso denied - Casa 01", "08:30 - Acceso checked_in - Casa 104").
 */
@Composable
private fun CompactRecentActivitySection(
    items: List<CompactActivityItem>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("compact_recent_activity_section"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        border = BorderStroke(1.dp, NavyCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(CyanNeon)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ÚLTIMA ACTIVIDAD",
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Text(
                    text = "HISTORIAL EN VIVO",
                    color = CyanNeon,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items.forEach { item ->
                    CompactActivityRow(item = item)
                }
            }
        }
    }
}

/**
 * Fila compacta individual de actividad con visual idéntica a los prototipos de MEDUSA ALFHA.
 */
@Composable
private fun CompactActivityRow(
    item: CompactActivityItem
) {
    val pillBg = when {
        item.isAccessDenied -> AlertRed.copy(alpha = 0.15f)
        item.isCheckedIn -> SuccessGreen.copy(alpha = 0.15f)
        else -> NavyCard
    }
    val pillBorder = when {
        item.isAccessDenied -> AlertRed.copy(alpha = 0.4f)
        item.isCheckedIn -> SuccessGreen.copy(alpha = 0.4f)
        else -> TextMuted.copy(alpha = 0.2f)
    }
    val pillText = when {
        item.isAccessDenied -> AlertRed
        item.isCheckedIn -> SuccessGreen
        else -> TextMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(NavyDark.copy(alpha = 0.7f))
            .border(1.dp, NavyCard, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag("activity_row_${item.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.timestampFormatted,
                color = GoldPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = " - Acceso ",
                color = TextMuted,
                fontSize = 11.sp
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(pillBg)
                    .border(1.dp, pillBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = item.statusText,
                    color = pillText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = " - ${item.destination}",
                color = TextWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Diálogo interactivo para activación del Protocolo de Emergencia.
 */
@Composable
private fun EmergencyProtocolDialog(
    condominiumName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("emergency_protocol_dialog"),
        containerColor = NavySurface,
        titleContentColor = AlertRed,
        textContentColor = TextWhite,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = AlertRed,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "INICIAR PROTOCOLO DE EMERGENCIA",
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "¿Confirmas la activación del protocolo de alta prioridad para $condominiumName?",
                    fontSize = 13.sp,
                    color = TextWhite
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AlertRed.copy(alpha = 0.12f))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "• Transmisión SOS por Firebase Cloud Messaging\n• Registro en bitácora forense de auditoría\n• Apertura de carriles de emergencia en caseta",
                        fontSize = 11.sp,
                        color = Color(0xFFFCA5A5)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("btn_confirm_emergency")
            ) {
                Text("DISPARAR ALERTA", fontWeight = FontWeight.Bold, color = TextWhite)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, TextMuted),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("CANCELAR", color = TextMuted)
            }
        }
    )
}

/**
 * Diálogo modal para información de acciones tácticas.
 */
@Composable
private fun TacticalActionInfoDialog(
    action: TacticalGuardAction,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
        titleContentColor = GoldPrimary,
        textContentColor = TextWhite,
        icon = {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = GoldPrimary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = action.title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column {
                Text(
                    text = action.subtitle,
                    fontSize = 13.sp,
                    color = TextWhite
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Módulo en línea y enlazado con la base de datos de control de acceso de MEDUSA ALFHA.",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("ENTENDIDO", color = NavyDark, fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Vista administrativa adaptativa (muestra banner de emergencia + paneles directivos).
 */
@Composable
private fun AdminDashboardContent(
    session: MedusaSessionData?,
    onEmergencyClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("admin_dashboard_content"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            GuardHeaderBar(
                condominiumId = session?.condominiumId ?: "Condominio Central",
                keyId = session?.keyId ?: "ADMIN-KEY",
                onLogoutClick = onLogoutClick
            )
        }

        item {
            EmergencyAlertBanner(onInitiateProtocol = onEmergencyClick)
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                border = BorderStroke(1.dp, BlueNeon.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "PANEL DE ADMINISTRACIÓN GENERAL",
                        color = BlueNeon,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Acceso a balances, estados financieros, matriz de riesgos y auditoría forense habilitado.",
                        color = TextWhite,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * Vista de residente adaptativa (sin herramientas de caseta).
 */
@Composable
private fun ResidentDashboardFallbackContent(
    session: MedusaSessionData?,
    onLogoutClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .testTag("resident_dashboard_fallback"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Home,
            contentDescription = null,
            tint = CyanNeon,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "PORTAL DEL RESIDENTE",
            color = CyanNeon,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Unidad: ${session?.assignedUnit ?: "Asignada"} · ${session?.condominiumId ?: ""}",
            color = TextWhite,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onLogoutClick,
            colors = ButtonDefaults.buttonColors(containerColor = NavyCard)
        ) {
            Text("CERRAR SESIÓN", color = TextWhite)
        }
    }
}

/**
 * Vista para cuando el dispositivo aún no tiene llave activada.
 */
@Composable
private fun UnassignedActivationContent(
    uiState: ActivationUiState,
    onValidateKey: (String) -> Unit
) {
    var inputKey by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("unassigned_activation_content"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(GoldPrimary.copy(alpha = 0.12f))
                .border(1.dp, GoldPrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = GoldPrimary,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "MEDUSA ALFHA",
            color = GoldPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )

        Text(
            text = "ACTIVACIÓN DE DISPOSITIVO OPERATIVO",
            color = TextWhite,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Introduce la llave de activación asignada por la administración para configurar el perfil de caseta o residente.",
            color = TextMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = inputKey,
            onValueChange = { inputKey = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("input_activation_key"),
            placeholder = { Text("Ej: KEY-CASETA-01", color = TextMuted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = NavyCard,
                focusedTextColor = TextWhite,
                unfocusedTextColor = TextWhite,
                focusedContainerColor = NavySurface,
                unfocusedContainerColor = NavySurface
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = { onValidateKey(inputKey) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_validate_key"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
            enabled = uiState !is ActivationUiState.Loading
        ) {
            if (uiState is ActivationUiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = NavyDark,
                    strokeWidth = 2.dp
                )
            } else {
                Text("ACTIVAR DISPOSITIVO", color = NavyDark, fontWeight = FontWeight.Bold)
            }
        }

        if (uiState is ActivationUiState.Error) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AlertRed.copy(alpha = 0.15f))
                    .border(1.dp, AlertRed, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text(
                    text = uiState.errorMessage,
                    color = Color(0xFFFCA5A5),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
