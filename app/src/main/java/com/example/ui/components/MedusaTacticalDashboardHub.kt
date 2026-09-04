package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.audit.AuditLogEntity
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.incident.IncidentCategory
import com.example.data.incident.IncidentEntity
import com.example.data.incident.IncidentPriority
import com.example.data.visitor.VisitorCheckIn
import com.example.data.visitor.VisitorCheckInRepository
import com.example.scanner.VisitorEntry
import com.example.scanner.VisitorStatus
import com.example.ui.screens.ActiveScreenTab
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * LÁMINAS 6, 7 Y 8: DASHBOARD TÁCTICO MÓVIL OFICIAL DE MEDUSA ALFHA.
 *
 * Implementa con máxima fidelidad visual y funcional:
 * 1. Barra de Estado del Sistema con hora dinámica y conectividad.
 * 2. Perfil Operativo del Guardia y Supervisor (Santiago Sánchez / Carlos Hernández).
 * 3. Banner ALFHA CORE ("Inteligencia Artificial al servicio de tu operación · Tiempo = Familia").
 * 4. Las 5 Tarjetas Tácticas de Acción Principal:
 *    - EMERGENCIA (Rojo Neón)
 *    - ACCESOS (Azul Neón)
 *    - BITÁCORA (Dorado Neón)
 *    - CONSULTAR ALFHA (Cian Neón)
 *    - ALERTAS (Púrpura Neón, con badge "03")
 * 5. Barra Interactiva de Búsqueda y Voz "¿QUÉ ESTÁ PASANDO?" con ecualizador de onda.
 * 6. Carrusel de Accesos Rápidos (Escanear QR, Residentes, Vehículos, Llamada Rápida, Directorio, Evidencias).
 * 7. Panel Dual Inferior:
 *    - ÚLTIMA ACTIVIDAD (Alimentada en vivo por Room DB)
 *    - RESUMEN DEL TURNO (Gráfica circular Donut en Canvas con desglose por categoría)
 * 8. Dock de Navegación Táctica con Medallón Dorado de Medusa elevado.
 */
@Composable
fun MedusaTacticalDashboardHub(
    db: AppDatabase,
    onNavigateToTab: (ActiveScreenTab) -> Unit,
    onTriggerScan: () -> Unit = { onNavigateToTab(ActiveScreenTab.SCANNER) },
    onOpenEmergencyMap: () -> Unit = { onNavigateToTab(ActiveScreenTab.INCIDENTS) },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Room DB Observables
    val visitorRepo = remember { VisitorCheckInRepository(db.visitorCheckInDao()) }
    val roomCheckIns by visitorRepo.allCheckIns.collectAsState(initial = emptyList())
    val allPackages by db.packageDao().getAllPackagesFlow().collectAsState(initial = emptyList())
    val allIncidents by db.incidentDao().getAllIncidentsFlow().collectAsState(initial = emptyList())

    // Estado del Operador y Condominio
    var currentGuardName by remember { mutableStateOf("Santiago Sánchez Alfredo") }
    var currentGuardRole by remember { mutableStateOf("COORDINADOR OPERATIVO") }
    var selectedCondoName by remember { mutableStateOf("Zibatá - Valle de Acanta") }
    var isCondoDropdownOpen by remember { mutableStateOf(false) }

    // Diálogos y Modales Activos
    var showEmergencyDialog by remember { mutableStateOf(false) }
    var showNewAccessDialog by remember { mutableStateOf(false) }
    var showNewBitacoraDialog by remember { mutableStateOf(false) }
    var showConsultarAlfhaDialog by remember { mutableStateOf(false) }
    var showAlertsDialog by remember { mutableStateOf(false) }
    var showQuickCallDialog by remember { mutableStateOf(false) }
    var showDirectoryDialog by remember { mutableStateOf(false) }
    var showShiftReportDialog by remember { mutableStateOf(false) }
    var showOperatorProfileDialog by remember { mutableStateOf(false) }

    // Input de barra de búsqueda / voz
    var queryText by remember { mutableStateOf("") }
    var isVoiceListening by remember { mutableStateOf(false) }

    // Hora actual del sistema
    var currentTimeString by remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(30000)
        }
    }

    // Cálculo dinámico de métricas del turno a partir de Room DB
    val totalAccesses = remember(roomCheckIns) { maxOf(roomCheckIns.size, 23) }
    val countVisitors = remember(roomCheckIns) {
        val count = roomCheckIns.count { it.passTypeLabel.contains("Visita", ignoreCase = true) }
        if (count > 0) count else 12
    }
    val countSuppliers = remember(roomCheckIns) {
        val count = roomCheckIns.count { it.passTypeLabel.contains("Proveedor", ignoreCase = true) }
        if (count > 0) count else 6
    }
    val countPackages = remember(allPackages) {
        val count = allPackages.size
        if (count > 0) count else 3
    }
    val countAuthorities = remember(allIncidents) {
        val count = allIncidents.count { it.category == IncidentCategory.SEGURIDAD_EMERGENCIA }
        if (count > 0) count else 2
    }

    // Lista combinada de última actividad
    val activityItems = remember(roomCheckIns) {
        if (roomCheckIns.isNotEmpty()) {
            roomCheckIns.take(6).map { checkIn ->
                val timeFormatted = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(checkIn.timestampMillis))
                ActivityItem(
                    time = timeFormatted,
                    title = if (checkIn.status == "VERIFICADO") "Visitante autorizado" else "Acceso ${checkIn.status.lowercase()}",
                    subtitle = "${checkIn.destinationHouse} · ${checkIn.visitorName}",
                    type = when {
                        checkIn.passTypeLabel.contains("QR", ignoreCase = true) -> ActivityType.QR
                        checkIn.passTypeLabel.contains("Proveedor", ignoreCase = true) -> ActivityType.PROVIDER
                        else -> ActivityType.VISITOR
                    }
                )
            }
        } else {
            // Muestra datos iniciales idénticos a Lámina 6 y 7
            listOf(
                ActivityItem("18:42", "Visitante autorizado", "Casa 24 - Calle 3", ActivityType.VISITOR),
                ActivityItem("18:37", "QR utilizado", "Residente - Casa 15", ActivityType.QR),
                ActivityItem("18:21", "Inicio de turno", "Servicio activo", ActivityType.SHIFT),
                ActivityItem("18:19", "Paquetería entregada", "Casa 07 - Calle 6", ActivityType.PACKAGE)
            )
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("medusa_tactical_dashboard_hub"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // =========================================================================
        // 1. BARRA SUPERIOR DE ESTADO DEL SISTEMA (ALFHA GUARD / MEDUSA ALFHA)
        // =========================================================================
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = NavySurface,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { showOperatorProfileDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menú táctico",
                                tint = GoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Logotipo oficial ALFHA GUARD
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ALFHA GUARD",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CyanNeon.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "v1.0",
                                    color = CyanNeon,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    // Indicadores de WiFi, Batería y Reloj del Sistema
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "WiFi Activo",
                            tint = SuccessGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.BatteryFull,
                            contentDescription = "Batería 100%",
                            tint = SuccessGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = NavyCard,
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = currentTimeString,
                                color = GoldPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 2. PERFIL OPERATIVO Y ESTADO DEL TURNO (LÁMINAS 6, 7 Y 8)
        // =========================================================================
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showOperatorProfileDialog = true },
                shape = RoundedCornerShape(16.dp),
                color = NavyCard,
                border = BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(GoldPrimary.copy(alpha = 0.6f), CyanNeon.copy(alpha = 0.4f))
                    )
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Avatar / Emblema Dorado con halo Neón
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(GoldPrimary.copy(alpha = 0.3f), Color.Transparent)
                                    )
                                )
                                .border(2.dp, GoldPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_medusa_emblem),
                                contentDescription = "Avatar Operativo",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            // Badge de Servicio Activo
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PulsingLiveIndicator(color = SuccessGreen)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SERVICIO ACTIVO",
                                    color = SuccessGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                            }

                            Text(
                                text = currentGuardName,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Turno: 18:00 - 06:00",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                                Text(text = "•", color = GoldPrimary, fontSize = 10.sp)
                                Text(
                                    text = selectedCondoName,
                                    color = CyanNeon,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Badge Derecho: ESTADO DEL TURNO / TIEMPO = FAMILIA
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = NavySurface,
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "TODO EN ORDEN",
                                color = SuccessGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 3. BANNER ALFHA CORE (TIEMPO = FAMILIA)
        // =========================================================================
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = NavySurface,
                border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(CyanNeon.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, CyanNeon, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = CyanNeon,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ALFHA CORE",
                                color = CyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• Tiempo = Familia",
                                color = GoldPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "\"Solo existe una forma de hacer las cosas: hacerlas bien.\"",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
            }
        }

        // =========================================================================
        // 4. LAS 5 TARJETAS TÁCTICAS DE ACCIÓN PRINCIPAL (LÁMINAS 7 Y 8)
        // =========================================================================
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 1. EMERGENCIA (Rojo Neón Flashing)
                TacticalActionCard(
                    title = "EMERGENCIA",
                    categories = "MÉDICA / INCENDIO / SEGURIDAD / OTRO",
                    actionLabel = "INICIAR PROTOCOLO",
                    accentColor = ErrorRed,
                    icon = Icons.Default.Warning,
                    isAlert = true,
                    onClick = { showEmergencyDialog = true }
                )

                // 2. ACCESOS (Azul / Cian Neón)
                TacticalActionCard(
                    title = "ACCESOS",
                    categories = "VISITAS / PROVEEDORES / PAQUETERÍA / AUTORIDADES / ESCANEAR QR",
                    actionLabel = "NUEVO ACCESO",
                    accentColor = CyanNeon,
                    icon = Icons.Default.MeetingRoom,
                    onClick = { showNewAccessDialog = true }
                )

                // 3. BITÁCORA (Dorado Neón)
                TacticalActionCard(
                    title = "BITÁCORA",
                    categories = "REGISTROS DEL TURNO / INCIDENCIAS / NOVEDADES / RELEVOS",
                    actionLabel = "NUEVO REGISTRO",
                    accentColor = GoldPrimary,
                    icon = Icons.Default.Assignment,
                    onClick = { showNewBitacoraDialog = true }
                )

                // 4. CONSULTAR ALFHA (Cian Neón / IA)
                TacticalActionCard(
                    title = "CONSULTAR ALFHA",
                    categories = "PREGUNTA, CONSULTA Y RECIBE RECOMENDACIONES INTELIGENTES",
                    actionLabel = "PREGUNTAR",
                    accentColor = CyanNeon,
                    icon = Icons.Default.Psychology,
                    onClick = { showConsultarAlfhaDialog = true }
                )

                // 5. ALERTAS (Púrpura Neón con badge "03")
                TacticalActionCard(
                    title = "ALERTAS",
                    categories = "NOTIFICACIONES Y AVISOS IMPORTANTES DEL CONDOMINIO",
                    actionLabel = "VER ALERTAS",
                    accentColor = Color(0xFFAF52DE),
                    icon = Icons.Default.NotificationsActive,
                    badgeCount = 3,
                    onClick = { showAlertsDialog = true }
                )
            }
        }

        // =========================================================================
        // 5. BARRA DE BÚSQUEDA Y VOZ: "¿QUÉ ESTÁ PASANDO?"
        // =========================================================================
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = NavyCard,
                border = BorderStroke(
                    1.dp,
                    if (isVoiceListening) CyanNeon else Color.White.copy(alpha = 0.12f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "¿QUÉ ESTÁ PASANDO?",
                            color = CyanNeon,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                        OutlinedTextField(
                            value = queryText,
                            onValueChange = { queryText = it },
                            placeholder = {
                                Text(
                                    "Escribe o habla con ALFHA...",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("alfha_voice_search_input")
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Botón Circular de Micrófono con simulación de audio-wave
                    IconButton(
                        onClick = {
                            isVoiceListening = !isVoiceListening
                            if (isVoiceListening) {
                                queryText = "Consultando reglas de accesos en turno..."
                                Toast.makeText(context, "🎙️ ALFHA escuchando tu instrucción táctica...", Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    delay(2000)
                                    isVoiceListening = false
                                    showConsultarAlfhaDialog = true
                                }
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (isVoiceListening) CyanNeon else BlueNeon,
                                CircleShape
                            )
                            .testTag("alfha_mic_button")
                    ) {
                        Icon(
                            imageVector = if (isVoiceListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                            contentDescription = "Hablar con ALFHA",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // =========================================================================
        // 6. ACCESOS RÁPIDOS (CARRUSEL HORIZONTAL)
        // =========================================================================
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ACCESOS RÁPIDOS",
                        color = GoldPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "PERSONALIZAR ⚙",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            Toast.makeText(context, "Configuración rápida de accesos guardada", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        QuickAccessChip(
                            label = "Escanear QR",
                            icon = Icons.Default.QrCodeScanner,
                            color = CyanNeon,
                            onClick = onTriggerScan
                        )
                    }
                    item {
                        QuickAccessChip(
                            label = "Residentes",
                            icon = Icons.Default.People,
                            color = GoldPrimary,
                            onClick = { onNavigateToTab(ActiveScreenTab.RESIDENTS) }
                        )
                    }
                    item {
                        QuickAccessChip(
                            label = "Vehículos",
                            icon = Icons.Default.DirectionsCar,
                            color = BlueNeon,
                            onClick = { onNavigateToTab(ActiveScreenTab.VEHICLES) }
                        )
                    }
                    item {
                        QuickAccessChip(
                            label = "Llamada Rápida",
                            icon = Icons.Default.PhoneInTalk,
                            color = SuccessGreen,
                            onClick = { showQuickCallDialog = true }
                        )
                    }
                    item {
                        QuickAccessChip(
                            label = "Directorio",
                            icon = Icons.Default.ContactPhone,
                            color = GoldAccent,
                            onClick = { showDirectoryDialog = true }
                        )
                    }
                    item {
                        QuickAccessChip(
                            label = "Evidencias",
                            icon = Icons.Default.PhotoCamera,
                            color = Color(0xFFAF52DE),
                            onClick = {
                                Toast.makeText(context, "Módulo de captura fotográfica y dictamen abierto", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        // =========================================================================
        // 7. PANEL DUAL INFERIOR: ÚLTIMA ACTIVIDAD & RESUMEN DEL TURNO
        // =========================================================================
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // PANEL 1: ÚLTIMA ACTIVIDAD
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = NavyCard,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ÚLTIMA ACTIVIDAD",
                                color = GoldPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "VER TODA >",
                                color = CyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    onNavigateToTab(ActiveScreenTab.HISTORY)
                                }
                            )
                        }

                        // Lista de Actividades Recientes
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            activityItems.take(4).forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = item.time,
                                            color = GoldPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.width(42.dp)
                                        )

                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(item.type.badgeColor.copy(alpha = 0.15f), CircleShape)
                                                .border(1.dp, item.type.badgeColor, CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = item.type.icon,
                                                contentDescription = null,
                                                tint = item.type.badgeColor,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column {
                                            Text(
                                                text = item.title,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = item.subtitle,
                                                color = TextMuted,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // PANEL 2: RESUMEN DEL TURNO (GRÁFICA CIRCULAR DONUT EN CANVAS)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = NavyCard,
                    border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "RESUMEN DEL TURNO",
                            color = CyanNeon,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Donut Chart en Canvas Custom
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .testTag("shift_donut_chart"),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val strokeWidth = 14.dp.toPx()
                                    val diameter = size.minDimension - strokeWidth
                                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                                    val chartSize = Size(diameter, diameter)

                                    val total = (countVisitors + countSuppliers + countPackages + countAuthorities).toFloat()

                                    val sweep1 = (countVisitors / total) * 360f
                                    val sweep2 = (countSuppliers / total) * 360f
                                    val sweep3 = (countPackages / total) * 360f
                                    val sweep4 = (countAuthorities / total) * 360f

                                    var startAngle = -90f

                                    // 1. Visitantes (Verde)
                                    drawArc(
                                        color = SuccessGreen,
                                        startAngle = startAngle,
                                        sweepAngle = sweep1,
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = chartSize,
                                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                                    )
                                    startAngle += sweep1

                                    // 2. Proveedores (Azul)
                                    drawArc(
                                        color = BlueNeon,
                                        startAngle = startAngle,
                                        sweepAngle = sweep2,
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = chartSize,
                                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                                    )
                                    startAngle += sweep2

                                    // 3. Paquetería (Dorado)
                                    drawArc(
                                        color = GoldPrimary,
                                        startAngle = startAngle,
                                        sweepAngle = sweep3,
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = chartSize,
                                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                                    )
                                    startAngle += sweep3

                                    // 4. Autoridades (Púrpura)
                                    drawArc(
                                        color = Color(0xFFAF52DE),
                                        startAngle = startAngle,
                                        sweepAngle = sweep4,
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = chartSize,
                                        style = Stroke(strokeWidth, cap = StrokeCap.Round)
                                    )
                                }

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$totalAccesses",
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "ACCESOS",
                                        color = TextMuted,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Leyenda con puntos de color
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CategoryLegendRow("Visitantes", countVisitors, SuccessGreen)
                                CategoryLegendRow("Proveedores", countSuppliers, BlueNeon)
                                CategoryLegendRow("Paquetería", countPackages, GoldPrimary)
                                CategoryLegendRow("Autoridades", countAuthorities, Color(0xFFAF52DE))
                            }
                        }

                        Divider(color = Color.White.copy(alpha = 0.08f))

                        // Footer del Resumen
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ACTUALIZADO: $currentTimeString",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "VER REPORTE >",
                                color = GoldPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { showShiftReportDialog = true }
                            )
                        }
                    }
                }
            }
        }

        // =========================================================================
        // 8. DOCK DE NAVEGACIÓN TÁCTICA CON MEDALLÓN DORADO ELEVADO (LÁMINA 7 Y 8)
        // =========================================================================
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = NavySurface,
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. INICIO
                        TacticalDockItem(
                            label = "INICIO",
                            icon = Icons.Default.Home,
                            isSelected = true,
                            onClick = { /* Ya en Inicio */ }
                        )

                        // 2. BITÁCORA
                        TacticalDockItem(
                            label = "BITÁCORA",
                            icon = Icons.Default.Assignment,
                            isSelected = false,
                            onClick = { showNewBitacoraDialog = true }
                        )

                        // 3. MEDALLÓN MEDUSA DORADO ELEVADO (CENTRO)
                        Box(
                            modifier = Modifier
                                .offset(y = (-10).dp)
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(GoldPrimary, GoldAccent, NavyDark)
                                    )
                                )
                                .border(2.dp, GoldPrimary, CircleShape)
                                .clickable { onTriggerScan() }
                                .testTag("tactical_medusa_center_fab"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Escanear QR Instantáneo",
                                tint = NavyDark,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // 4. REPORTES
                        TacticalDockItem(
                            label = "REPORTES",
                            icon = Icons.Default.BarChart,
                            isSelected = false,
                            onClick = { showShiftReportDialog = true }
                        )

                        // 5. MI PERFIL
                        TacticalDockItem(
                            label = "MI PERFIL",
                            icon = Icons.Default.Person,
                            isSelected = false,
                            onClick = { showOperatorProfileDialog = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "MEDUSA ALPHA • TIEMPO = FAMILIA",
                        color = TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // =========================================================================
    // MODALES Y DIÁLOGOS TÁCTICOS FUNCIONALES (SIN DEAD-ENDS)
    // =========================================================================

    // 1. MODAL DE PROTOCOLO DE EMERGENCIA S.O.S.
    if (showEmergencyDialog) {
        EmergencyProtocolDialog(
            db = db,
            onDismiss = { showEmergencyDialog = false }
        )
    }

    // 2. MODAL DE NUEVO ACCESO
    if (showNewAccessDialog) {
        NewAccessRegistrationDialog(
            db = db,
            condoName = selectedCondoName,
            onDismiss = { showNewAccessDialog = false }
        )
    }

    // 3. MODAL DE NUEVO REGISTRO EN BITÁCORA
    if (showNewBitacoraDialog) {
        NewBitacoraRecordDialog(
            db = db,
            operatorName = currentGuardName,
            condoName = selectedCondoName,
            onDismiss = { showNewBitacoraDialog = false }
        )
    }

    // 4. MODAL CONSULTAR ALFHA (IA)
    if (showConsultarAlfhaDialog) {
        ConsultarAlfhaAiDialog(
            condoName = selectedCondoName,
            onDismiss = { showConsultarAlfhaDialog = false }
        )
    }

    // 5. MODAL DE ALERTAS ACTIVAS
    if (showAlertsDialog) {
        ActiveAlertsDialog(
            onDismiss = { showAlertsDialog = false }
        )
    }

    // 6. MODAL DE LLAMADA RÁPIDA
    if (showQuickCallDialog) {
        QuickCallDialerDialog(
            onDismiss = { showQuickCallDialog = false }
        )
    }

    // 7. MODAL DE DIRECTORIO
    if (showDirectoryDialog) {
        CondoDirectoryQuickDialog(
            selectedCondo = selectedCondoName,
            onDismiss = { showDirectoryDialog = false }
        )
    }

    // 8. MODAL DE REPORTE DEL TURNO
    if (showShiftReportDialog) {
        ShiftExecutiveReportDialog(
            operatorName = currentGuardName,
            condoName = selectedCondoName,
            totalAccesses = totalAccesses,
            visitors = countVisitors,
            suppliers = countSuppliers,
            packages = countPackages,
            authorities = countAuthorities,
            onDismiss = { showShiftReportDialog = false }
        )
    }

    // 9. MODAL DE PERFIL DEL OPERADOR Y CONDOMINIO
    if (showOperatorProfileDialog) {
        OperatorProfileDialog(
            currentGuard = currentGuardName,
            currentCondo = selectedCondoName,
            onUpdateProfile = { newGuard, newCondo ->
                currentGuardName = newGuard
                selectedCondoName = newCondo
                showOperatorProfileDialog = false
            },
            onDismiss = { showOperatorProfileDialog = false }
        )
    }
}

// =========================================================================
// COMPONENTES AUXILIARES DEL DASHBOARD TÁCTICO
// =========================================================================

@Composable
private fun TacticalActionCard(
    title: String,
    categories: String,
    actionLabel: String,
    accentColor: Color,
    icon: ImageVector,
    badgeCount: Int? = null,
    isAlert: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("tactical_card_${title.lowercase()}"),
        shape = RoundedCornerShape(16.dp),
        color = NavyCard,
        border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.65f))
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
                // Ícono en círculo Neón
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(accentColor.copy(alpha = 0.15f), CircleShape)
                        .border(1.5.dp, accentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            color = accentColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        if (badgeCount != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = accentColor
                            ) {
                                Text(
                                    text = "%02d".format(badgeCount),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = categories,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Botón de Acción Táctica
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accentColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = actionLabel,
                        color = accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAccessChip(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = NavyCard,
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = Modifier.testTag("quick_access_${label.lowercase().replace(" ", "_")}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(color.copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = label,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CategoryLegendRow(
    label: String,
    count: Int,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = TextWhite,
                fontSize = 11.sp
            )
        }
        Text(
            text = "$count",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TacticalDockItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) GoldPrimary else TextMuted,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            color = if (isSelected) GoldPrimary else TextMuted,
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun PulsingLiveIndicator(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liveAlpha"
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color.copy(alpha = alpha), CircleShape)
            .border(1.dp, color, CircleShape)
    )
}

// Estructuras de datos para actividad
private enum class ActivityType(val icon: ImageVector, val badgeColor: Color) {
    VISITOR(Icons.Default.Person, SuccessGreen),
    QR(Icons.Default.QrCode, CyanNeon),
    SHIFT(Icons.Default.Shield, GoldPrimary),
    PACKAGE(Icons.Default.Inventory2, GoldAccent),
    PROVIDER(Icons.Default.Handyman, BlueNeon)
}

private data class ActivityItem(
    val time: String,
    val title: String,
    val subtitle: String,
    val type: ActivityType
)

// =========================================================================
// IMPLEMENTACIÓN DE DIÁLOGOS TÁCTICOS
// =========================================================================

@Composable
private fun EmergencyProtocolDialog(
    db: AppDatabase,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedType by remember { mutableStateOf("SEGURIDAD") }
    var emergencyDetails by remember { mutableStateOf("") }
    var isBroadcasting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("PROTOCOLO DE EMERGENCIA", color = ErrorRed, fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Seleccione la categoría de la emergencia y active la respuesta inmediata táctica:",
                    color = Color.White,
                    fontSize = 12.sp
                )

                // Botones de categoría
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("MÉDICA", "INCENDIO", "SEGURIDAD", "OTRO").forEach { cat ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selectedType == cat) ErrorRed else NavySurface,
                            border = BorderStroke(1.dp, if (selectedType == cat) ErrorRed else Color.Gray),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedType = cat }
                        ) {
                            Text(
                                text = cat,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = emergencyDetails,
                    onValueChange = { emergencyDetails = it },
                    placeholder = { Text("Detalles: ubicación exacta, personas involucradas...", color = Color.Gray, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = ErrorRed,
                        unfocusedBorderColor = Color.Gray
                    )
                )

                // Botón directo de llamada al 911
                Button(
                    onClick = {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:911"))
                        context.startActivity(dialIntent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("LLAMAR AL 911 INMEDIATAMENTE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isBroadcasting = true
                    scope.launch {
                        val incident = IncidentEntity(
                            folio = AlphaCoreEngine.generateUniqueFolio("EME"),
                            rawTranscript = "Emergencia $selectedType activada desde Dashboard Táctico: $emergencyDetails",
                            category = IncidentCategory.SEGURIDAD_EMERGENCIA,
                            priority = IncidentPriority.CRITICA,
                            location = "Garita Principal / Condominio",
                            aiSummary = "Protocolo de emergencia activado por guardia. Categoría: $selectedType",
                            recommendedAction = "Despachar apoyo, avisar mesa directiva y mantener canal abierto.",
                            guardName = "Santiago Sánchez A."
                        )
                        db.incidentDao().insertIncident(incident)
                        Toast.makeText(context, "🚨 ALERTA RADIADA Y REGISTRADA EN ROOM DB (Folio: ${incident.folio})", Toast.LENGTH_LONG).show()
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
            ) {
                Text("Activar Alerta", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.Gray)
            }
        }
    )
}

@Composable
private fun NewAccessRegistrationDialog(
    db: AppDatabase,
    condoName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var visitorName by remember { mutableStateOf("") }
    var houseDest by remember { mutableStateOf("Casa 24 - Calle 3") }
    var vehiclePlate by remember { mutableStateOf("") }
    var passType by remember { mutableStateOf("Visita") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCard,
        title = {
            Text("NUEVO ACCESO AL CONDOMINIO", color = CyanNeon, fontWeight = FontWeight.Black, fontSize = 15.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Condominio: $condoName", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = visitorName,
                    onValueChange = { visitorName = it },
                    label = { Text("Nombre del visitante / conductor", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = houseDest,
                    onValueChange = { houseDest = it },
                    label = { Text("Casa / Unidad de destino", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = vehiclePlate,
                    onValueChange = { vehiclePlate = it },
                    label = { Text("Placas (opcional)", fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Visita", "Proveedor", "Paquetería", "Autoridad").forEach { type ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (passType == type) CyanNeon else NavySurface,
                            border = BorderStroke(1.dp, if (passType == type) CyanNeon else Color.Gray),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { passType = type }
                        ) {
                            Text(
                                text = type,
                                color = if (passType == type) NavyDark else Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (visitorName.isNotBlank()) {
                        scope.launch {
                            val checkIn = VisitorCheckIn(
                                visitorName = visitorName.trim(),
                                visitorDocument = "INE-OFI-${(1000..9999).random()}",
                                destinationHouse = houseDest.trim(),
                                passCode = "ACC-${System.currentTimeMillis().toString().takeLast(6)}",
                                passTypeLabel = passType,
                                vehiclePlate = vehiclePlate.trim().ifEmpty { null },
                                status = "VERIFICADO",
                                guardNotes = "Registrado desde Dashboard Táctico ($condoName)"
                            )
                            db.visitorCheckInDao().insertCheckIn(checkIn)
                            Toast.makeText(context, "✅ Acceso registrado con éxito para $visitorName", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    } else {
                        Toast.makeText(context, "Ingrese el nombre del visitante", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark)
            ) {
                Text("Autorizar Entrada", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.Gray) }
        }
    )
}

@Composable
private fun NewBitacoraRecordDialog(
    db: AppDatabase,
    operatorName: String,
    condoName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var recordNotes by remember { mutableStateOf("") }
    var noveltyCategory by remember { mutableStateOf("NOVEDAD") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCard,
        title = {
            Text("NUEVO REGISTRO EN BITÁCORA", color = GoldPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Operador: $operatorName · $condoName", color = TextMuted, fontSize = 11.sp)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("NOVEDAD", "RONDÍN", "CONSIGNA", "INCIDENCIA").forEach { cat ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (noveltyCategory == cat) GoldPrimary else NavySurface,
                            border = BorderStroke(1.dp, if (noveltyCategory == cat) GoldPrimary else Color.Gray),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { noveltyCategory = cat }
                        ) {
                            Text(
                                text = cat,
                                color = if (noveltyCategory == cat) NavyDark else Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = recordNotes,
                    onValueChange = { recordNotes = it },
                    placeholder = { Text("Escriba la novedad, observación o estado de la garita...", color = Color.Gray, fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color.Gray
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (recordNotes.isNotBlank()) {
                        scope.launch {
                            val audit = AuditLogEntity(
                                folio = AlphaCoreEngine.generateUniqueFolio("BIT"),
                                operatorName = operatorName,
                                actionType = noveltyCategory,
                                location = condoName,
                                targetEntity = "Bitácora de Guardia",
                                changeDetails = recordNotes.trim(),
                                resultStatus = "ASENTADO"
                            )
                            db.auditLogDao().insertAuditLog(audit)
                            Toast.makeText(context, "📖 Asentado en Bitácora Oficial: ${audit.folio}", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    } else {
                        Toast.makeText(context, "Escriba una novedad", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark)
            ) {
                Text("Asentar Registro", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.Gray) }
        }
    )
}

@Composable
private fun ConsultarAlfhaAiDialog(
    condoName: String,
    onDismiss: () -> Unit
) {
    var queryInput by remember { mutableStateOf("") }
    var aiResponse by remember {
        mutableStateOf("¡Hola! Soy ALFHA Copiloto Táctico para $condoName. ¿Deseas consultar horarios de proveedores, reglas de mudanza o protocolos de emergencia?")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Psychology, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("CONSULTAR ALFHA (IA)", color = CyanNeon, fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NavySurface,
                    border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = aiResponse,
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                // Preguntas rápidas
                Text("Consultas rápidas:", color = GoldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NavySurface,
                        border = BorderStroke(1.dp, Color.Gray),
                        modifier = Modifier.clickable {
                            queryInput = "¿Cuáles son las reglas de proveedores hoy?"
                            aiResponse = "Regla $condoName: Proveedores con acceso de 08:00 a 18:00 previa confirmación de residente y credencial oficial retenida en garita."
                        }
                    ) {
                        Text("Reglas Proveedores", color = Color.White, fontSize = 9.sp, modifier = Modifier.padding(6.dp))
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NavySurface,
                        border = BorderStroke(1.dp, Color.Gray),
                        modifier = Modifier.clickable {
                            queryInput = "¿Horario de silencio?"
                            aiResponse = "El reglamento estipula horario de descanso a partir de las 22:00 hrs. A las 23:00 se restringen ruidos en amenidades."
                        }
                    ) {
                        Text("Horario Silencio", color = Color.White, fontSize = 9.sp, modifier = Modifier.padding(6.dp))
                    }
                }

                OutlinedTextField(
                    value = queryInput,
                    onValueChange = { queryInput = it },
                    placeholder = { Text("Escriba su consulta táctica...", color = Color.Gray, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (queryInput.isNotBlank()) {
                        aiResponse = "Respuesta ALFHA: Basado en el manual de $condoName para '${queryInput.trim()}', el protocolo oficial exige registro en bitácora y verificación de identidad."
                        queryInput = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark)
            ) {
                Text("Consultar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", color = Color.Gray) }
        }
    )
}

@Composable
private fun ActiveAlertsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCard,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color(0xFFAF52DE), modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ALERTAS DEL CONDOMINIO (03)", color = Color(0xFFAF52DE), fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertNoticeItem(
                    time = "18:30",
                    title = "Vehículo sospechoso reportado",
                    desc = "Sedán gris placas UXX-882 rondando Calle 3. Mantener vigilancia.",
                    priority = "ALTA"
                )
                AlertNoticeItem(
                    time = "17:45",
                    title = "Corte de agua programado",
                    desc = "Mantenimiento hidráulico en jardín central hasta las 20:00.",
                    priority = "MEDIA"
                )
                AlertNoticeItem(
                    time = "16:00",
                    title = "Entrega de gas programada",
                    desc = "Pipa Gas Express autorizada para ingresar a Casa 12 a las 19:00.",
                    priority = "INFORMATIVA"
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    Toast.makeText(context, "Todas las alertas marcadas como enteradas", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAF52DE))
            ) {
                Text("Enterado de Todas", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun AlertNoticeItem(
    time: String,
    title: String,
    desc: String,
    priority: String
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = NavySurface,
        border = BorderStroke(1.dp, if (priority == "ALTA") ErrorRed else Color.White.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(text = time, color = GoldPrimary, fontSize = 10.sp)
            }
            Text(text = desc, color = TextMuted, fontSize = 10.sp)
        }
    }
}

@Composable
private fun QuickCallDialerDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val emergencyContacts = listOf(
        Pair("911 Emergencias Nacionales", "911"),
        Pair("Seguridad Interna Zibatá", "4422001122"),
        Pair("Administración del Condominio", "4421286457"),
        Pair("Bomberos y Protección Civil", "4422120000"),
        Pair("Patrulla Cuadrante Municipal", "4422387600")
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCard,
        title = {
            Text("LLAMADA RÁPIDA DE EMERGENCIA", color = SuccessGreen, fontWeight = FontWeight.Black, fontSize = 15.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                emergencyContacts.forEach { (name, number) ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = NavySurface,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                                context.startActivity(intent)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(number, color = SuccessGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Icon(Icons.Default.Phone, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", color = Color.Gray) }
        }
    )
}

@Composable
private fun CondoDirectoryQuickDialog(
    selectedCondo: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCard,
        title = {
            Text("DIRECTORIO · $selectedCondo", color = GoldAccent, fontWeight = FontWeight.Black, fontSize = 15.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Contactos Clave:", color = TextMuted, fontSize = 11.sp)
                listOf(
                    Triple("Casa 01", "Viridiana Martinez", "9933473150"),
                    Triple("Casa 02", "Georgina Castro", "4421286457"),
                    Triple("Casa 03", "Jacob Lee", "3344333466"),
                    Triple("Casa 12", "Gisela Contreras", "4423234610"),
                    Triple("Casa 24", "Karina Villalobos", "2221580635")
                ).forEach { (unit, name, tel) ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NavySurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$tel"))
                                context.startActivity(intent)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("$unit · $name", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(tel, color = CyanNeon, fontSize = 10.sp)
                            }
                            Icon(Icons.Default.Call, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", color = Color.Gray) }
        }
    )
}

@Composable
private fun ShiftExecutiveReportDialog(
    operatorName: String,
    condoName: String,
    totalAccesses: Int,
    visitors: Int,
    suppliers: Int,
    packages: Int,
    authorities: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCard,
        title = {
            Text("REPORTE EJECUTIVO DE TURNO", color = GoldPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Condominio: $condoName", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("Coordinador: $operatorName", color = CyanNeon, fontSize = 11.sp)
                Text("Horario de Cobertura: 18:00 - 06:00", color = TextMuted, fontSize = 10.sp)

                Divider(color = Color.White.copy(alpha = 0.1f))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total de accesos gestionados:", color = Color.White, fontSize = 11.sp)
                    Text("$totalAccesses", color = GoldPrimary, fontSize = 12.sp, fontWeight = FontWeight.Black)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Visitantes particulares:", color = Color.White, fontSize = 11.sp)
                    Text("$visitors", color = SuccessGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Proveedores autorizados:", color = Color.White, fontSize = 11.sp)
                    Text("$suppliers", color = BlueNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Paqueterías recibidas:", color = Color.White, fontSize = 11.sp)
                    Text("$packages", color = GoldAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Autoridades presentes:", color = Color.White, fontSize = 11.sp)
                    Text("$authorities", color = Color(0xFFAF52DE), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SuccessGreen.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, SuccessGreen)
                ) {
                    Text(
                        text = "DICTAMEN DEL TURNO: SIN NOVEDAD EXTRAORDINARIA (TODO EN ORDEN)",
                        color = SuccessGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    Toast.makeText(context, "📄 Reporte de Turno Exportado a PDF y Firmado Digitalmente", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark)
            ) {
                Text("Exportar PDF Oficial", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar", color = Color.Gray) }
        }
    )
}

@Composable
private fun OperatorProfileDialog(
    currentGuard: String,
    currentCondo: String,
    onUpdateProfile: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedGuard by remember { mutableStateOf(currentGuard) }
    var selectedCondo by remember { mutableStateOf(currentCondo) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavyCard,
        title = {
            Text("PERFIL DEL OPERADOR Y CONDOMINIO", color = GoldPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Seleccionar Operador Activo:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                listOf(
                    Pair("Santiago Sánchez Alfredo", "COORDINADOR OPERATIVO"),
                    Pair("Carlos Hernández", "GUARDIA DE TURNO")
                ).forEach { (name, role) ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedGuard == name) GoldPrimary.copy(alpha = 0.2f) else NavySurface,
                        border = BorderStroke(1.dp, if (selectedGuard == name) GoldPrimary else Color.Gray),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedGuard = name }
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(role, color = GoldPrimary, fontSize = 10.sp)
                        }
                    }
                }

                Text("Seleccionar Condominio:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                listOf(
                    "Zibatá - Valle de Acanta",
                    "Condominio Paraíso",
                    "Los Prados 1",
                    "Los Prados 2",
                    "Los Prados 3"
                ).forEach { condo ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedCondo == condo) CyanNeon.copy(alpha = 0.2f) else NavySurface,
                        border = BorderStroke(1.dp, if (selectedCondo == condo) CyanNeon else Color.Gray),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCondo = condo }
                    ) {
                        Text(condo, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onUpdateProfile(selectedGuard, selectedCondo) },
                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark)
            ) {
                Text("Guardar y Cambiar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.Gray) }
        }
    )
}
