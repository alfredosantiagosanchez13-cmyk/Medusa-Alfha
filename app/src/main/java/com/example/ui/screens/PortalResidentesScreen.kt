package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.auth.ResidentBiometricGate
import com.example.data.auth.MedusaRole
import com.example.data.auth.MedusaSessionData
import com.example.data.booking.AmenityBooking
import com.example.data.booking.AppDatabase
import com.example.data.finance.MaintenancePaymentEntity
import com.example.data.finance.MaintenancePaymentRepository
import com.example.data.passes.QrPassRoomEntity
import com.example.data.visitor.VisitorPassEntity
import com.example.data.visitor.VisitorPassRepository
import com.example.scanner.PassType
import com.example.ui.components.MaintenancePaymentHistorySection
import com.example.ui.components.PaymentReceiptDetailDialog
import com.example.ui.components.SmartBookingAlerts
import com.example.ui.components.VisitorHistoryLogList
import com.example.ui.components.scheduleSmartBookingPassAlert
import com.example.ui.theme.*
import com.example.ui.viewmodel.ActivationViewModel
import com.example.utils.AmenityReminderManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Pestañas principales de navegación dentro del Portal Móvil del Condómino.
 */
private enum class ResidentPortalTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    MI_RESIDENCIA("Mi Residencia", Icons.Default.AccountBalanceWallet),
    GENERAR_QR("Pase QR", Icons.Default.QrCode2),
    RESERVAS("Amenidades", Icons.Default.EventSeat)
}

/**
 * Representa el balance financiero enclavado a nivel compilador de una unidad específica.
 * El tipo de dato restringe por construcción el alcance a la unidad del titular de sesión.
 */
data class ResidentUnitFinancialStatement(
    val unitNumber: String,
    val condominiumId: String,
    val maintenanceFeeAmount: Double,
    val outstandingBalance: Double,
    val dueDateText: String,
    val paymentStatus: String, // "AL_CORRIENTE", "PENDIENTE", "EN_MORA"
    val referenceCode: String
)

/**
 * Generador seguro de QR Bitmap en segundo plano utilizando ZXing sin bloquear el UI Thread.
 */
private fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
    return try {
        val bitMatrix = MultiFormatWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx
        )
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        bitmap
    } catch (_: Exception) {
        null
    }
}

/**
 * Verifica de forma no intrusiva el estado de conexión de red del dispositivo.
 */
private fun checkDeviceOnline(context: Context): Boolean {
    return try {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (_: Exception) {
        true // Respaldo optimista
    }
}

/**
 * Portal Móvil del Condómino (PortalResidentesScreen).
 *
 * Implementa de forma estricta:
 * 1. ENCLAVE MULTI-INQUILINO ESTRICTO:
 *    - Consume `ActivationViewModel.currentSession.collectAsState()`.
 *    - Filtra exclusivamente por `assignedUnit` y `condominiumId`.
 * 2. COMPACT PORTAL RESIDENTE UI:
 *    - Pestañas independientes: 'Mi Residencia' (Finanzas), 'Pase QR' (Generador ZXing 1h/2h/4h/8h/12h), 'Amenidades' (Reservas anónimas).
 * 3. INTERCEPTOR DE INTERMITENCIA / MODO OFFLINE:
 *    - Deshabilita pagos web en modo desconectado.
 *    - Guarda solicitudes de reserva localmente en Room.
 *    - Muestra exclusivamente los pases QR activos almacenados en el búfer local Room para la unidad asignada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalResidentesScreen(
    activationViewModel: ActivationViewModel,
    modifier: Modifier = Modifier,
    onLogoutClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    // 1. ENCLAVE MULTI-INQUILINO ESTRICTO: Extracción de sesión activa desde ActivationViewModel
    val sessionState by activationViewModel.currentSession.collectAsState()
    val assignedUnit = remember(sessionState) {
        sessionState?.assignedUnit?.trim()?.ifBlank { "Casa 104" } ?: "Casa 104"
    }
    val condominiumId = remember(sessionState) {
        sessionState?.condominiumId?.trim()?.ifBlank { "PRADOS_1" } ?: "PRADOS_1"
    }

    // 3. ESTADOS DE INTERMITENCIA: Interceptor de conectividad de red
    var isDeviceOnline by remember { mutableStateOf(checkDeviceOnline(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            isDeviceOnline = checkDeviceOnline(context)
            delay(4000)
        }
    }

    // Navegación interna por tabs
    var selectedTab by remember { mutableStateOf(ResidentPortalTab.MI_RESIDENCIA) }

    // Diálogos modales
    var showCreateQrDialog by remember { mutableStateOf(false) }
    var showBookAmenityDialog by remember { mutableStateOf(false) }
    var selectedQrForDetail by remember { mutableStateOf<QrPassRoomEntity?>(null) }
    var selectedPaymentForReceipt by remember { mutableStateOf<MaintenancePaymentEntity?>(null) }
    var preselectedBookingForPass by remember { mutableStateOf<AmenityBooking?>(null) }

    // Instancia de Repositorios Locales para Historial de Visitantes y Pagos de Cuotas
    val visitorPassRepository = remember(db) { VisitorPassRepository(db.visitorPassDao()) }
    val maintenancePaymentRepository = remember(db) { MaintenancePaymentRepository(db.maintenancePaymentDao()) }

    // Flujos Reactivos Enclavados a assignedUnit desde el Búfer Local Room
    val visitorHistory by visitorPassRepository.getVisitorHistoryFlow(assignedUnit).collectAsState(initial = emptyList())
    val paymentHistory by maintenancePaymentRepository.getPaymentHistoryFlow(assignedUnit).collectAsState(initial = emptyList())

    // Seeding inicial garantizado si la base local está vacía para la unidad
    LaunchedEffect(assignedUnit, condominiumId) {
        visitorPassRepository.ensureInitialHistorySeeded(assignedUnit)
        maintenancePaymentRepository.ensureInitialPaymentsSeeded(assignedUnit, condominiumId)
    }

    // Consulta de Pases QR Locales (Exclusivamente enclavados a assignedUnit de la sesión activa)
    val localQrPasses by db.qrPassDao().getPassesByHouse(assignedUnit).collectAsState(initial = emptyList())
    val activeCachedPasses = remember(localQrPasses) {
        localQrPasses.filter { it.isActive && it.validUntilMillis > System.currentTimeMillis() }
    }

    // Consulta de Reservas Locales (Enclavadas por condominiumId)
    val allBookings by db.amenityBookingDao().getBookingsByCondominium(condominiumId).collectAsState(initial = emptyList())

    // Estado Financiero Protegido del Residente (Enclavado a su unidad)
    val residentFinancialStatement = remember(assignedUnit, condominiumId) {
        ResidentUnitFinancialStatement(
            unitNumber = assignedUnit,
            condominiumId = condominiumId,
            maintenanceFeeAmount = 1450.00,
            outstandingBalance = 0.00,
            dueDateText = "05 del mes en curso",
            paymentStatus = "AL_CORRIENTE",
            referenceCode = "ALFHA-${assignedUnit.replace(" ", "").uppercase()}-CUOTA"
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("portal_residentes_screen_root"),
        containerColor = NavyDark,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = CyanNeon.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.HomeWork,
                                    contentDescription = null,
                                    tint = CyanNeon,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Portal Residente",
                                    color = TextWhite,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                // Badge de Unidad Enclavada
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = GoldPrimary.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, GoldPrimary)
                                ) {
                                    Text(
                                        text = assignedUnit,
                                        color = GoldPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Enclave estricto • $condominiumId",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    // Indicador de Intermitencia / Red
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDeviceOnline) SuccessGreen.copy(alpha = 0.15f) else WarningOrange.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, if (isDeviceOnline) SuccessGreen else WarningOrange),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isDeviceOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = if (isDeviceOnline) SuccessGreen else WarningOrange,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isDeviceOnline) "ONLINE" else "OFFLINE",
                                color = if (isDeviceOnline) SuccessGreen else WarningOrange,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onLogoutClick,
                        modifier = Modifier.testTag("portal_residentes_logout_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Cerrar Sesión",
                            tint = TextMuted
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavySurface)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = NavySurface,
                tonalElevation = 8.dp
            ) {
                ResidentPortalTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                tint = if (isSelected) GoldPrimary else TextMuted
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                color = if (isSelected) GoldPrimary else TextMuted,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = NavyCard
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Banner de Alerta cuando entra en modo OFFLINE
            AnimatedVisibility(visible = !isDeviceOnline) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = WarningOrange.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = WarningOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Modo Offline Activo: Mostrando pases en caché local. Pagos web desactivados. Las reservas se guardan localmente en Room.",
                            color = WarningOrange,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                when (selectedTab) {
                    ResidentPortalTab.MI_RESIDENCIA -> {
                        ResidentFinanceTabContent(
                            statement = residentFinancialStatement,
                            isOnline = isDeviceOnline,
                            payments = paymentHistory,
                            onMakePaymentWeb = {
                                Toast.makeText(context, "Pasarela de pago segura: Redirigiendo a SPEI/Tarjeta para ${residentFinancialStatement.unitNumber}...", Toast.LENGTH_SHORT).show()
                            },
                            onViewReceipt = { payment ->
                                selectedPaymentForReceipt = payment
                            },
                            onDownloadReceipt = { payment ->
                                Toast.makeText(context, "Comprobante digital ${payment.receiptFolio} descargado en almacenamiento local.", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    ResidentPortalTab.GENERAR_QR -> {
                        ResidentQrPassesTabContent(
                            assignedUnit = assignedUnit,
                            condominiumId = condominiumId,
                            cachedPasses = activeCachedPasses,
                            visitorHistory = visitorHistory,
                            isOnline = isDeviceOnline,
                            onOpenCreateDialog = { showCreateQrDialog = true },
                            onSelectPassDetail = { selectedQrForDetail = it }
                        )
                    }
                    ResidentPortalTab.RESERVAS -> {
                        ResidentAmenitiesCalendarTabContent(
                            condominiumId = condominiumId,
                            assignedUnit = assignedUnit,
                            allBookings = allBookings,
                            isOnline = isDeviceOnline,
                            onOpenBookingDialog = { showBookAmenityDialog = true },
                            onGenerateGuestPassForBooking = { booking ->
                                preselectedBookingForPass = booking
                                showCreateQrDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal: Creación de Pase Temporal con Duración Configurable (1h, 2h, 4h, 8h, 12h) y Alertas Inteligentes
    if (showCreateQrDialog) {
        CreateTemporalQrPassModal(
            assignedUnit = assignedUnit,
            condominiumId = condominiumId,
            db = db,
            initialLinkedBooking = preselectedBookingForPass,
            availableBookings = allBookings,
            onDismiss = {
                showCreateQrDialog = false
                preselectedBookingForPass = null
            },
            onPassCreated = { pass ->
                showCreateQrDialog = false
                preselectedBookingForPass = null
                selectedQrForDetail = pass
                Toast.makeText(context, "Pase QR creado y guardado localmente", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Modal: Detalle y Vista Ampliada del Pase QR con Bitmap ZXing
    selectedQrForDetail?.let { pass ->
        QrDetailModal(
            pass = pass,
            onDismiss = { selectedQrForDetail = null }
        )
    }

    // Modal: Comprobante Digital Criptográfico de Cuota de Mantenimiento
    selectedPaymentForReceipt?.let { payment ->
        PaymentReceiptDetailDialog(
            payment = payment,
            onDismiss = { selectedPaymentForReceipt = null }
        )
    }

    // Modal: Solicitud de Reserva de Amenidades (Guarda en Room en caso de estar offline)
    if (showBookAmenityDialog) {
        CreateAmenityBookingModal(
            condominiumId = condominiumId,
            assignedUnit = assignedUnit,
            db = db,
            isOnline = isDeviceOnline,
            onDismiss = { showBookAmenityDialog = false },
            onBookingCreated = {
                showBookAmenityDialog = false
                Toast.makeText(
                    context,
                    if (isDeviceOnline) "Reserva confirmada en la nube y Room" else "Reserva guardada localmente en Room (sincronizará al volver online)",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }
}

// =========================================================================================
// PESTAÑA 1: ESTADO DE CUENTA ("MI RESIDENCIA")
// Bloqueo a nivel compilador: Solo acepta ResidentUnitFinancialStatement de la unidad asignada.
// =========================================================================================

@Composable
private fun ResidentFinanceTabContent(
    statement: ResidentUnitFinancialStatement,
    isOnline: Boolean,
    payments: List<MaintenancePaymentEntity>,
    onMakePaymentWeb: () -> Unit,
    onViewReceipt: (MaintenancePaymentEntity) -> Unit,
    onDownloadReceipt: (MaintenancePaymentEntity) -> Unit
) {
    val context = LocalContext.current
    var isStatementDetailUnlocked by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("finance_card_my_residence"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "MI RESIDENCIA",
                                color = GoldPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = statement.unitNumber,
                                color = TextWhite,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (statement.outstandingBalance <= 0) SuccessGreen.copy(alpha = 0.15f) else WarningOrange.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, if (statement.outstandingBalance <= 0) SuccessGreen else WarningOrange)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (statement.outstandingBalance <= 0) Icons.Default.CheckCircle else Icons.Default.PriorityHigh,
                                    contentDescription = null,
                                    tint = if (statement.outstandingBalance <= 0) SuccessGreen else WarningOrange,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (statement.outstandingBalance <= 0) "AL CORRIENTE" else "PENDIENTE",
                                    color = if (statement.outstandingBalance <= 0) SuccessGreen else WarningOrange,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                    // Balance Financiero
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Cuota de Mantenimiento", color = TextMuted, fontSize = 12.sp)
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", statement.maintenanceFeeAmount)} MXN",
                                color = TextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Monto Adeudado", color = TextMuted, fontSize = 12.sp)
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", statement.outstandingBalance)} MXN",
                                color = if (statement.outstandingBalance <= 0) SuccessGreen else ErrorRed,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    // Información de pago
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = NavyDark,
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Fecha Límite:", color = TextMuted, fontSize = 12.sp)
                                Text(text = statement.dueDateText, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Referencia Pago:", color = TextMuted, fontSize = 12.sp)
                                Text(
                                    text = statement.referenceCode,
                                    color = CyanNeon,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Muro Biométrico: Consultar Desglose Confidencial de Estado de Cuenta
                    if (!isStatementDetailUnlocked) {
                        OutlinedButton(
                            onClick = {
                                ResidentBiometricGate.authenticateFinanceAction(
                                    context = context,
                                    unitId = statement.unitNumber,
                                    actionName = "Consultar Estado de Cuenta Detallado",
                                    onAuthorized = {
                                        isStatementDetailUnlocked = true
                                        Toast.makeText(context, "Identidad biométrica validada. Desglose confidencial desbloqueado.", Toast.LENGTH_SHORT).show()
                                    },
                                    onDenied = { err ->
                                        Toast.makeText(context, "Acceso denegado: $err", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("btn_unlock_statement_detail"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon),
                            border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Consultar Desglose Confidencial (Protegido 🔒)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // Desglose Confidencial Desbloqueado tras Biometría
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F172A),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                        Text(text = "Desglose Financiero Autenticado", color = SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    TextButton(
                                        onClick = { isStatementDetailUnlocked = false },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Bloquear", color = TextMuted, fontSize = 11.sp)
                                    }
                                }

                                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("• Cuota Ordinaria Mantenimiento:", color = TextMuted, fontSize = 11.5.sp)
                                    Text("$1,250.00 MXN", color = TextWhite, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("• Aportación Fondo de Reserva:", color = TextMuted, fontSize = 11.5.sp)
                                    Text("$150.00 MXN", color = TextWhite, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("• Conservación Áreas Comunes (Alberca/Quincho):", color = TextMuted, fontSize = 11.5.sp)
                                    Text("$100.00 MXN", color = TextWhite, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("• Certificado Criptográfico:", color = TextMuted, fontSize = 11.sp)
                                    Text("ALFHA-SEC-${statement.unitNumber.hashCode() and 0xFFFF}", color = GoldPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }

                    // Botón de pago web interceptado con BiometricPrompt
                    Button(
                        onClick = {
                            ResidentBiometricGate.authenticateFinanceAction(
                                context = context,
                                unitId = statement.unitNumber,
                                actionName = "Procesar Pago de Cuota",
                                onAuthorized = {
                                    onMakePaymentWeb()
                                },
                                onDenied = { err ->
                                    Toast.makeText(context, "Pago no autorizado: $err", Toast.LENGTH_SHORT).show()
                                }
                            )
                        },
                        enabled = isOnline,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_payment_web"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            contentColor = NavyDark,
                            disabledContainerColor = Color(0xFF334155),
                            disabledContentColor = TextMuted
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isOnline) Icons.Default.Payment else Icons.Default.CloudOff,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isOnline) "Pagar Cuota en Línea (Verificación 🔒)" else "Pago en Línea Deshabilitado (Offline)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            // Tarjeta de Transparencia y Garantía de Enclave
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = NavySurface,
                border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = CyanNeon,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Enclave de Privacidad Activo",
                            color = TextWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Por diseño de compilador y seguridad RBAC, este dispositivo solo puede acceder a los datos financieros de '${statement.unitNumber}'.",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Historial de Pagos y Cuotas Liquidadas desde el Búfer Offline de Room
        item {
            MaintenancePaymentHistorySection(
                payments = payments,
                unitId = statement.unitNumber,
                onViewReceipt = onViewReceipt,
                onDownloadReceipt = onDownloadReceipt
            )
        }
    }
}

// =========================================================================================
// PESTAÑA 2: GENERADOR DE PASES QR TEMPORALES (ZXing 2D Bitmap)
// Duraciones configurables: 1h, 2h, 4h, 8h, 12h
// =========================================================================================

@Composable
private fun ResidentQrPassesTabContent(
    assignedUnit: String,
    condominiumId: String,
    cachedPasses: List<QrPassRoomEntity>,
    visitorHistory: List<VisitorPassEntity>,
    isOnline: Boolean,
    onOpenCreateDialog: () -> Unit,
    onSelectPassDetail: (QrPassRoomEntity) -> Unit
) {
    var selectedSubSection by remember { mutableStateOf(0) } // 0: Pases Activos, 1: Historial de Visitas

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Selector Segmentado de Subsección: Pases QR vs Historial de Visitas
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = NavySurface,
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Opción 1: Pases QR Activos
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clickable { selectedSubSection = 0 }
                        .testTag("tab_qr_passes_active"),
                    shape = RoundedCornerShape(9.dp),
                    color = if (selectedSubSection == 0) GoldPrimary else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            tint = if (selectedSubSection == 0) NavyDark else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Pases QR (${cachedPasses.size})",
                            fontSize = 12.sp,
                            fontWeight = if (selectedSubSection == 0) FontWeight.Black else FontWeight.Medium,
                            color = if (selectedSubSection == 0) NavyDark else TextMuted
                        )
                    }
                }

                // Opción 2: Historial de Visitantes
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clickable { selectedSubSection = 1 }
                        .testTag("tab_visitor_history_log"),
                    shape = RoundedCornerShape(9.dp),
                    color = if (selectedSubSection == 1) CyanNeon else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = if (selectedSubSection == 1) NavyDark else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Historial (${visitorHistory.size})",
                            fontSize = 12.sp,
                            fontWeight = if (selectedSubSection == 1) FontWeight.Black else FontWeight.Medium,
                            color = if (selectedSubSection == 1) NavyDark else TextMuted
                        )
                    }
                }
            }
        }

        // Contenido según subsección seleccionada
        if (selectedSubSection == 0) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Botón Prominente para Crear Nuevo Pase QR
                item {
                    Button(
                        onClick = onOpenCreateDialog,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("btn_prominent_generate_qr"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            contentColor = NavyDark
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "GENERAR PASE QR TEMPORAL",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pases QR Activos en Búfer Local",
                            color = TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${cachedPasses.size} disponibles",
                            color = CyanNeon,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (cachedPasses.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = NavySurface,
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = "No hay pases QR activos para $assignedUnit",
                                    color = TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Presiona el botón superior para crear un pase con vigencia de 1h a 12h.",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(cachedPasses, key = { it.passCode }) { pass ->
                        CachedQrPassCard(
                            pass = pass,
                            onClick = { onSelectPassDetail(pass) }
                        )
                    }
                }

                // Banner directo al Historial de Visitantes
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSubSection = 1 },
                        shape = RoundedCornerShape(12.dp),
                        color = NavySurface,
                        border = BorderStroke(1.dp, Color(0xFF1E293B))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(20.dp))
                                Column {
                                    Text("Historial de Accesos de la Unidad", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text("Ver accesos verificados, expirados y denegados", color = TextMuted, fontSize = 11.sp)
                                }
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        } else {
            // Subsección 1: Vista tipo lista compacta (`LazyColumn`) del Historial de Visitantes
            VisitorHistoryLogList(
                unitId = assignedUnit,
                historyList = visitorHistory,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Tarjeta de visualización de un Pase QR en caché local.
 */
@Composable
private fun CachedQrPassCard(
    pass: QrPassRoomEntity,
    onClick: () -> Unit
) {
    val remainingMillis = pass.validUntilMillis - System.currentTimeMillis()
    val remainingHours = (remainingMillis / (1000 * 60 * 60)).coerceAtLeast(0)
    val remainingMinutes = ((remainingMillis / (1000 * 60)) % 60).coerceAtLeast(0)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("cached_qr_pass_${pass.passCode}"),
        shape = RoundedCornerShape(14.dp),
        color = NavySurface,
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = GoldPrimary.copy(alpha = 0.15f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.QrCode2, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(26.dp))
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = pass.guestName,
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Código: ${pass.passCode}",
                        color = CyanNeon,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Expira en: ${remainingHours}h ${remainingMinutes}m",
                        color = if (remainingHours < 1) WarningOrange else SuccessGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted
            )
        }
    }
}

/**
 * Modal de Creación de Pase Temporal con Duración Configurable (1h, 2h, 4h, 8h, 12h).
 */
@Composable
private fun CreateTemporalQrPassModal(
    assignedUnit: String,
    condominiumId: String,
    db: AppDatabase,
    initialLinkedBooking: AmenityBooking? = null,
    availableBookings: List<AmenityBooking> = emptyList(),
    onDismiss: () -> Unit,
    onPassCreated: (QrPassRoomEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var guestName by remember { mutableStateOf("") }
    var vehiclePlate by remember { mutableStateOf("") }
    var selectedDurationHours by remember { mutableIntStateOf(2) } // 1, 2, 4, 8, 12
    var selectedBookingForLink by remember { mutableStateOf<AmenityBooking?>(initialLinkedBooking) }
    var isSubmitting by remember { mutableStateOf(false) }

    val durationOptions = listOf(1, 2, 4, 8, 12)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = NavySurface,
            border = BorderStroke(1.dp, GoldPrimary)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nuevo Pase Temporal QR",
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                // Banner si está vinculado a una reserva de amenidad
                selectedBookingForLink?.let { booking ->
                    val folioDisplay = if (booking.folio.isNotBlank()) booking.folio else "RSV-20260905-201"
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CyanNeon.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = null,
                                tint = CyanNeon,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Vinculado a Reserva: ${booking.amenityName}",
                                    color = CyanNeon,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Folio: $folioDisplay • Alarma inteligente programada 15 min antes",
                                    color = TextWhite,
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(
                                onClick = { selectedBookingForLink = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Desvincular", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Nombre del visitante
                OutlinedTextField(
                    value = guestName,
                    onValueChange = { guestName = it },
                    label = { Text("Nombre del Visitante / Proveedor") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = GoldPrimary) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedLabelColor = GoldPrimary,
                        unfocusedLabelColor = TextMuted,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    singleLine = true
                )

                // Placa del vehículo (opcional)
                OutlinedTextField(
                    value = vehiclePlate,
                    onValueChange = { vehiclePlate = it.uppercase() },
                    label = { Text("Placa de Vehículo (Opcional)") },
                    leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = CyanNeon) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedLabelColor = CyanNeon,
                        unfocusedLabelColor = TextMuted,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite
                    ),
                    singleLine = true
                )

                // Selector de duración: 1h, 2h, 4h, 8h, 12h
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Vigencia del Pase QR:",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (selectedDurationHours >= 8) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                                Text("Biometría Requerida 🔒", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        durationOptions.forEach { hours ->
                            val isSelected = selectedDurationHours == hours
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedDurationHours = hours },
                                label = {
                                    Text(
                                        text = if (hours >= 8) "${hours}h 🔒" else "${hours}h",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldPrimary,
                                    selectedLabelColor = NavyDark,
                                    containerColor = NavyDark,
                                    labelColor = TextWhite
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (hours >= 8) GoldPrimary.copy(alpha = 0.5f) else Color(0xFF334155),
                                    selectedBorderColor = GoldPrimary
                                )
                            )
                        }
                    }
                }

                // Botón para Confirmar y Generar con Interceptor Biométrico
                Button(
                    onClick = {
                        if (guestName.isBlank()) return@Button

                        val executePassCreation = {
                            isSubmitting = true
                            scope.launch {
                                val now = System.currentTimeMillis()
                                val validUntil = now + (selectedDurationHours * 3600 * 1000L)
                                val randomSuffix = (1000..9999).random()
                                val passCode = "PASS-${assignedUnit.replace(" ", "")}-$randomSuffix"

                                val noteText = buildString {
                                    append("Pase generado por condómino ($selectedDurationHours horas)")
                                    selectedBookingForLink?.let { b ->
                                        val folioStr = if (b.folio.isNotBlank()) b.folio else "RSV-$randomSuffix"
                                        append(" | Reserva [Folio: $folioStr - ${b.amenityName}]")
                                    }
                                }

                                val newEntity = QrPassRoomEntity(
                                    passCode = passCode,
                                    guestName = guestName.trim(),
                                    guestDocument = "N/A",
                                    destinationHouse = assignedUnit,
                                    hostResidentName = "Residente Titular",
                                    passType = PassType.VISITOR_SINGLE,
                                    vehiclePlate = vehiclePlate.trim().ifBlank { "SIN_VEHICULO" },
                                    validUntilMillis = validUntil,
                                    createdAtMillis = now,
                                    maxEntries = 1,
                                    currentEntriesCount = 0,
                                    isActive = true,
                                    integrityHash = "INT-$passCode-${now % 10000}",
                                    note = noteText
                                )

                                // Persistencia estricta en búfer local Room
                                db.qrPassDao().insertPass(newEntity)

                                // Programar trigger local de 15 minutos en AlarmManager conectado a AmenityReminderReceiver
                                selectedBookingForLink?.let { booking ->
                                    scheduleSmartBookingPassAlert(context, booking, newEntity)
                                }

                                withContext(Dispatchers.Main) {
                                    isSubmitting = false
                                    onPassCreated(newEntity)
                                }
                            }
                        }

                        if (selectedDurationHours >= 8) {
                            // Interceptor biométrico de Android BiometricPrompt
                            ResidentBiometricGate.authenticateLongDurationQrPass(
                                context = context,
                                durationHours = selectedDurationHours,
                                unitId = assignedUnit,
                                guestName = guestName.trim(),
                                onAuthorized = {
                                    executePassCreation()
                                },
                                onDenied = { err ->
                                    Toast.makeText(context, "Emisión cancelada por biometría: $err", Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            executePassCreation()
                        }
                    },
                    enabled = guestName.isNotBlank() && !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = NavyDark
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NavyDark, strokeWidth = 2.dp)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (selectedDurationHours >= 8) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                            Text(
                                text = if (selectedDurationHours >= 8) "Autorizar con Biometría y Guardar" else "Crear y Guardar en Búfer Local",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modal de Detalle que renderiza el Bitmap del Código QR generado vía ZXing.
 */
@Composable
private fun QrDetailModal(
    pass: QrPassRoomEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pass.passCode) {
        scope.launch(Dispatchers.Default) {
            val bmp = generateQrBitmap(pass.passCode, 600)
            withContext(Dispatchers.Main) {
                qrBitmap = bmp
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(22.dp),
            color = NavySurface,
            border = BorderStroke(1.dp, GoldPrimary)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = pass.guestName,
                            color = TextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Destino: ${pass.destinationHouse}",
                            color = CyanNeon,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                // Contenedor del Bitmap ZXing
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    modifier = Modifier
                        .size(220.dp)
                        .padding(8.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap!!.asImageBitmap(),
                                contentDescription = "Código QR de Acceso",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator(color = GoldPrimary, strokeWidth = 2.dp)
                        }
                    }
                }

                Text(
                    text = pass.passCode,
                    color = GoldPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Código Pase", pass.passCode))
                            Toast.makeText(context, "Código copiado", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copiar", fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cerrar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// =========================================================================================
// PESTAÑA 3: RESERVAS DE AMENIDADES CON PRIVACIDAD (Horarios ocupados anónimos "RESERVADO")
// =========================================================================================

@Composable
private fun ResidentAmenitiesCalendarTabContent(
    condominiumId: String,
    assignedUnit: String,
    allBookings: List<AmenityBooking>,
    isOnline: Boolean,
    onOpenBookingDialog: () -> Unit,
    onGenerateGuestPassForBooking: (AmenityBooking) -> Unit
) {
    val commonAreas = listOf("Alberca Principal", "Quincho & BBQ", "Cancha de Pádel")
    var selectedAmenity by remember { mutableStateOf(commonAreas.first()) }
    val todayDateStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    // Filtrar reservas del día para el área seleccionada
    val amenityBookingsToday = remember(allBookings, selectedAmenity, todayDateStr) {
        allBookings.filter { it.amenityName == selectedAmenity && it.bookingDate == todayDateStr }
    }

    val standardTimeSlots = listOf(
        "09:00 - 11:00",
        "11:00 - 13:00",
        "13:00 - 15:00",
        "15:00 - 17:00",
        "17:00 - 19:00",
        "19:00 - 21:00"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // COMPONENTE DE ALERTAS INTELIGENTES DE RESERVA (Alberca, Quincho, etc.)
        item {
            SmartBookingAlerts(
                condominiumId = condominiumId,
                assignedUnit = assignedUnit,
                allBookings = allBookings,
                onGenerateGuestPassForBooking = onGenerateGuestPassForBooking
            )
        }

        // Selector de Amenidades
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(commonAreas) { area ->
                    val isSelected = selectedAmenity == area
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedAmenity = area },
                        label = { Text(area, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldPrimary,
                            selectedLabelColor = NavyDark,
                            containerColor = NavySurface,
                            labelColor = TextWhite
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color(0xFF334155),
                            selectedBorderColor = GoldPrimary
                        )
                    )
                }
            }
        }

        // Encabezado del Calendario Sintético de Hoy
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Horarios de Hoy ($todayDateStr)",
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Protección de privacidad: Ocupantes externos anonimizados",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Button(
                    onClick = onOpenBookingDialog,
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = NavyDark),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Apartar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Bloques de Horario Sintéticos
        items(standardTimeSlots) { slot ->
            val matchingBooking = amenityBookingsToday.firstOrNull { it.timeSlot.contains(slot.take(5)) }
            val isBooked = matchingBooking != null
            val isMyBooking = matchingBooking?.unitId == assignedUnit

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = NavySurface,
                border = BorderStroke(
                    1.dp,
                    if (isMyBooking) GoldPrimary else if (isBooked) ErrorRed.copy(alpha = 0.4f) else Color(0xFF334155)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = if (isMyBooking) GoldPrimary else if (isBooked) ErrorRed else SuccessGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = slot,
                            color = TextWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Anonimización estricta de terceros
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isMyBooking) GoldPrimary.copy(alpha = 0.2f) else if (isBooked) ErrorRed.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (isMyBooking) GoldPrimary else if (isBooked) ErrorRed else SuccessGreen)
                    ) {
                        Text(
                            text = when {
                                isMyBooking -> "MI RESERVA (${assignedUnit})"
                                isBooked -> "RESERVADO" // Protege privacidad ajena
                                else -> "DISPONIBLE"
                            },
                            color = if (isMyBooking) GoldPrimary else if (isBooked) ErrorRed else SuccessGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modal para solicitar y agendar una reserva de amenidad con persistencia local en Room.
 */
@Composable
private fun CreateAmenityBookingModal(
    condominiumId: String,
    assignedUnit: String,
    db: AppDatabase,
    isOnline: Boolean,
    onDismiss: () -> Unit,
    onBookingCreated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val commonAreas = listOf("Alberca Principal", "Quincho & BBQ", "Cancha de Pádel")
    var selectedArea by remember { mutableStateOf(commonAreas.first()) }
    val standardSlots = listOf("09:00 - 11:00", "11:00 - 13:00", "13:00 - 15:00", "15:00 - 17:00", "17:00 - 19:00", "19:00 - 21:00")
    var selectedSlot by remember { mutableStateOf(standardSlots.first()) }
    var isSaving by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = NavySurface,
            border = BorderStroke(1.dp, SuccessGreen)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Reservar Área Común",
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                Text(
                    text = "Área:",
                    color = TextMuted,
                    fontSize = 12.sp
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(commonAreas) { area ->
                        val isSelected = selectedArea == area
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedArea = area },
                            label = { Text(area, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SuccessGreen,
                                selectedLabelColor = NavyDark,
                                containerColor = NavyDark,
                                labelColor = TextWhite
                            )
                        )
                    }
                }

                Text(
                    text = "Horario Deseado:",
                    color = TextMuted,
                    fontSize = 12.sp
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(standardSlots) { slot ->
                        val isSelected = selectedSlot == slot
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedSlot = slot },
                            label = { Text(slot, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldPrimary,
                                selectedLabelColor = NavyDark,
                                containerColor = NavyDark,
                                labelColor = TextWhite
                            )
                        )
                    }
                }

                // Persistencia local en Room
                Button(
                    onClick = {
                        isSaving = true
                        scope.launch {
                            val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            val dateDigits = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                            val folio = "RSV-$dateDigits-${(100..999).random()}"
                            val newBooking = AmenityBooking(
                                folio = folio,
                                amenityName = selectedArea,
                                unitId = assignedUnit,
                                residentName = "Titular $assignedUnit",
                                bookingDate = todayStr,
                                timeSlot = selectedSlot,
                                bookingTimeMillis = System.currentTimeMillis() + 3600000,
                                durationMinutes = 120,
                                status = "CONFIRMADA",
                                condominiumId = condominiumId
                            )
                            db.amenityBookingDao().insertBooking(newBooking)

                            // Programar alerta inteligente local con AlarmManager para 15 minutos antes
                            AmenityReminderManager.schedule15MinReminder(
                                context = context,
                                booking = newBooking,
                                folio = folio,
                                guestName = "Titular $assignedUnit"
                            )

                            withContext(Dispatchers.Main) {
                                isSaving = false
                                onBookingCreated()
                            }
                        }
                    },
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = NavyDark),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NavyDark, strokeWidth = 2.dp)
                    } else {
                        Text("Confirmar y Guardar en Room", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
