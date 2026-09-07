package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.auth.AlfhaRole
import com.example.auth.AlfhaSecurityContext
import com.example.data.auth.AlfhaUserEntity
import com.example.data.booking.AmenityBooking
import com.example.data.booking.AmenityBookingEngine
import com.example.data.booking.AmenityCatalogItem
import com.example.data.booking.AppDatabase
import com.example.data.booking.TimeSlotAvailability
import com.example.data.passes.QrPassRoomEntity
import com.example.data.resident.ResidentDashboardRepository
import com.example.data.resident.ResidentDashboardState
import com.example.data.fcm.FcmNotificationManager
import com.example.data.fcm.VisitorCheckInFcmPayload
import com.example.utils.AmenityReminderManager
import com.example.scanner.PassType
import com.example.ui.components.AmenityCalendarView
import com.example.ui.components.GenerateTimedTokenDialog
import com.example.ui.components.TimeLimitedDigitalPassModal
import com.example.ui.components.TimedTokenStatusBadge
import com.example.ui.components.VisitorAccessTokenInfo
import com.example.ui.components.getAmenityIcon
import com.example.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private enum class DashboardFilterTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    ALL("Todo", Icons.Default.Dashboard),
    QR_PASSES("Pases QR Activos", Icons.Default.QrCode),
    AMENITIES("Reservas Amenidades", Icons.Default.EventAvailable)
}

/**
 * Pantalla Segura del Panel del Residente ('Resident Dashboard').
 * Consulta y despliega datos desde Firestore restringidos estrictamente al ID del usuario autenticado,
 * garantizando aislamiento absoluto respecto a otros condóminos de Los Prados Residencial.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResidentDashboardScreen(
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    // Usuario autenticado en el contexto de seguridad ALFHA
    val currentUser by AlfhaSecurityContext.currentUser.collectAsState()

    // Estado del Dashboard
    var state by remember { mutableStateOf(ResidentDashboardState(isLoading = true)) }
    var currentFilter by remember { mutableStateOf(DashboardFilterTab.ALL) }
    var amenityViewMode by remember { mutableStateOf(0) } // 0: Calendario Visual, 1: Mis Reservas
    var showNewQrDialog by remember { mutableStateOf(false) }
    var showNewBookingDialog by remember { mutableStateOf(false) }
    var selectedQrPassForDetail by remember { mutableStateOf<QrPassRoomEntity?>(null) }
    var bookingToCancel by remember { mutableStateOf<AmenityBooking?>(null) }
    var showUserSwitcherDialog by remember { mutableStateOf(false) }
    var showAuditDetailsDialog by remember { mutableStateOf(false) }
    var customFirebaseUid by remember { mutableStateOf<String?>(null) }
    var customFirebaseEmail by remember { mutableStateOf<String?>(null) }

    // Condominio activo para la consulta aislada
    val condominiumId = "PRADOS_1"

    // Estado reactivo de Firebase Cloud Messaging (FCM)
    val fcmToken by FcmNotificationManager.fcmToken.collectAsState()
    val fcmStatus by FcmNotificationManager.fcmStatusMessage.collectAsState()
    val isSubscribedToUnit by FcmNotificationManager.isSubscribedToUnit.collectAsState()
    val lastFcmPayload by FcmNotificationManager.lastReceivedNotification.collectAsState()
    var dismissedBannerFolio by remember { mutableStateOf<String?>(null) }

    // Carga de datos aislados
    fun refreshData(overrideUid: String? = customFirebaseUid, overrideEmail: String? = customFirebaseEmail) {
        scope.launch {
            state = state.copy(isLoading = true)
            // Asegurar datos de muestra para el residente si no tiene
            ResidentDashboardRepository.seedResidentSampleDataIfEmpty(db, condominiumId, currentUser)
            val updated = ResidentDashboardRepository.loadResidentData(
                context = context,
                db = db,
                condominiumId = condominiumId,
                user = currentUser,
                overrideFirebaseUid = overrideUid,
                overrideEmail = overrideEmail
            )
            state = updated
        }
    }

    LaunchedEffect(currentUser.id, currentUser.unitOrDepartment, customFirebaseUid) {
        refreshData(customFirebaseUid, customFirebaseEmail)
        FcmNotificationManager.initialize(
            context = context,
            currentUser = currentUser,
            condominiumId = condominiumId
        )
    }

    // Actualización automática instantánea cuando ingresa una visita vía FCM
    LaunchedEffect(lastFcmPayload) {
        if (lastFcmPayload != null && (lastFcmPayload?.targetUnitId == currentUser.unitOrDepartment || currentUser.unitOrDepartment.isNullOrBlank())) {
            refreshData(customFirebaseUid, customFirebaseEmail)
        }
    }

    Scaffold(
        containerColor = NavyDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Panel del Residente",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = SuccessGreen.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, SuccessGreen)
                            ) {
                                Text(
                                    text = "100% AISLADO",
                                    color = SuccessGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Restringido al ID de usuario autenticado • Firestore",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavySurface),
                actions = {
                    IconButton(
                        onClick = { refreshData() },
                        modifier = Modifier.testTag("resident_dashboard_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Actualizar datos",
                            tint = GoldPrimary
                        )
                    }
                    IconButton(
                        onClick = { showAuditDetailsDialog = true },
                        modifier = Modifier.testTag("resident_dashboard_audit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Ver Garantía de Privacidad",
                            tint = CyanNeon
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewQrDialog = true },
                containerColor = GoldPrimary,
                contentColor = NavyDark,
                icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                text = { Text("Registrar Visitante", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("fab_register_visitor")
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
        ) {
            // 0. Banner de Notificación Push FCM en Tiempo Real si hay evento reciente
            if (lastFcmPayload != null && dismissedBannerFolio != lastFcmPayload?.passFolio) {
                item {
                    RealtimeFcmAlertBanner(
                        payload = lastFcmPayload!!,
                        onDismiss = { dismissedBannerFolio = lastFcmPayload?.passFolio }
                    )
                }
            }

            // 1. Tarjeta de Identidad y Aislamiento del Residente
            item {
                ResidentIdentityCard(
                    user = currentUser,
                    condominiumId = condominiumId,
                    state = state,
                    fcmStatus = fcmStatus,
                    isSubscribedToUnit = isSubscribedToUnit,
                    fcmToken = fcmToken,
                    onSwitchUserClick = { showUserSwitcherDialog = true },
                    onAuditClick = { showAuditDetailsDialog = true },
                    onTestFcmClick = {
                        scope.launch {
                            FcmNotificationManager.sendVisitorQrScannedNotification(
                                context = context,
                                db = db,
                                condominiumId = condominiumId,
                                unitId = currentUser.unitOrDepartment.ifBlank { "Casa 102" },
                                hostResidentName = currentUser.name,
                                guestName = "Carlos Mendoza (Prueba Push)",
                                guestDocument = "18.345.987-2",
                                passFolio = "FCM-TEST-${(1000..9999).random()}",
                                passCode = "QR-${(1000..9999).random()}",
                                passTypeLabel = "Visita General",
                                vehiclePlate = "HZ-WP-99",
                                guardName = "Oficial Soto (Garita)",
                                gateLocation = "Garita Principal de Seguridad",
                                guardNotes = "Prueba de escaneo exitoso QR y notificación Push FCM al residente"
                            )
                        }
                    }
                )
            }

            // 2. Resumen Métrico Rápido (KPIs)
            item {
                ResidentKpiRow(
                    activePassesCount = state.activeQrPasses.size,
                    upcomingBookingsCount = state.upcomingBookings.size,
                    onQrTabClick = { currentFilter = DashboardFilterTab.QR_PASSES },
                    onBookingsTabClick = { currentFilter = DashboardFilterTab.AMENITIES }
                )
            }

            // Banner de Registro de Visitantes con Código Único en Firestore
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showNewQrDialog = true }
                        .testTag("banner_register_visitor"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = NavySurface),
                    border = BorderStroke(1.dp, GoldPrimary)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = GoldPrimary.copy(alpha = 0.15f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Registrar Nuevo Visitante",
                                    color = TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Genera un código único en Firestore vinculado a tu cuenta",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Button(
                            onClick = { showNewQrDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("btn_banner_register_visitor")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Registrar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. Barra de Filtro de Secciones
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DashboardFilterTab.values().forEach { tab ->
                        val selected = currentFilter == tab
                        FilterChip(
                            selected = selected,
                            onClick = { currentFilter = tab },
                            label = {
                                Text(
                                    text = when (tab) {
                                        DashboardFilterTab.ALL -> "Todo (${state.activeQrPasses.size + state.upcomingBookings.size})"
                                        DashboardFilterTab.QR_PASSES -> "Pases QR (${state.activeQrPasses.size})"
                                        DashboardFilterTab.AMENITIES -> "Amenidades (${state.upcomingBookings.size})"
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selected) NavyDark else GoldPrimary
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldPrimary,
                                selectedLabelColor = NavyDark,
                                containerColor = NavySurface,
                                labelColor = TextWhite
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = GoldPrimary.copy(alpha = 0.5f),
                                selectedBorderColor = GoldPrimary
                            ),
                            modifier = Modifier.testTag("filter_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }

            // 4. Sección de Pases QR Activos
            if (currentFilter == DashboardFilterTab.ALL || currentFilter == DashboardFilterTab.QR_PASSES) {
                item {
                    SectionHeader(
                        title = "Visitantes y Códigos QR Registrados",
                        subtitle = "Códigos únicos almacenados en Firestore y vinculados a tu cuenta",
                        count = state.activeQrPasses.size,
                        badgeColor = GoldPrimary,
                        actionLabel = "+ Registrar Visita",
                        onActionClick = { showNewQrDialog = true }
                    )
                }

                if (state.activeQrPasses.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.PersonAdd,
                            title = "No tienes visitantes registrados",
                            description = "Registra a tus visitantes para generar su código de acceso único en Firestore vinculado a tu cuenta.",
                            buttonText = "Registrar mi primer visitante",
                            onClick = { showNewQrDialog = true }
                        )
                    }
                } else {
                    items(state.activeQrPasses, key = { it.passCode }) { pass ->
                        ActiveQrPassCard(
                            pass = pass,
                            onViewQrCode = { selectedQrPassForDetail = pass },
                            onShareCode = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Folio Pase QR", pass.passCode)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Folio ${pass.passCode} copiado al portapapeles", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // 5. Sección de Reservas de Amenidades
            if (currentFilter == DashboardFilterTab.ALL || currentFilter == DashboardFilterTab.AMENITIES) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    SectionHeader(
                        title = if (currentFilter == DashboardFilterTab.AMENITIES) "Disponibilidad y Reservas de Amenidades" else "Próximas Reservas de Amenidades",
                        subtitle = "Áreas comunes en Los Prados • Aislamiento por unidad",
                        count = state.upcomingBookings.size,
                        badgeColor = SuccessGreen,
                        actionLabel = "+ Reservar",
                        onActionClick = { showNewBookingDialog = true }
                    )
                }

                if (currentFilter == DashboardFilterTab.AMENITIES) {
                    item {
                        // Selector de Modo de Vista para Amenidades
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = amenityViewMode == 0,
                                onClick = { amenityViewMode = 0 },
                                leadingIcon = {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text("Calendario de Disponibilidad", fontSize = 12.sp, fontWeight = if (amenityViewMode == 0) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldPrimary,
                                    selectedLabelColor = NavyDark,
                                    selectedLeadingIconColor = NavyDark,
                                    containerColor = NavySurface,
                                    labelColor = TextWhite
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = amenityViewMode == 1,
                                onClick = { amenityViewMode = 1 },
                                leadingIcon = {
                                    Icon(Icons.Default.EventAvailable, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text("Mis Reservas (${state.upcomingBookings.size})", fontSize = 12.sp, fontWeight = if (amenityViewMode == 1) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = SuccessGreen,
                                    selectedLabelColor = NavyDark,
                                    selectedLeadingIconColor = NavyDark,
                                    containerColor = NavySurface,
                                    labelColor = TextWhite
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (amenityViewMode == 0) {
                        item {
                            AmenityCalendarView(
                                db = db,
                                initialCondominiumId = condominiumId,
                                filterUnitId = currentUser.unitOrDepartment,
                                onShowQrPass = { booking ->
                                    Toast.makeText(context, "Reserva: ${booking.folio} • ${booking.amenityName}", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    } else {
                        if (state.upcomingBookings.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    icon = Icons.Default.EventAvailable,
                                    title = "Sin reservas próximas de amenidades",
                                    description = "Puedes apartar la alberca, canchas de pádel o el quincho para eventos familiares.",
                                    buttonText = "Explorar Calendario y Reservar",
                                    onClick = { showNewBookingDialog = true }
                                )
                            }
                        } else {
                            items(state.upcomingBookings, key = { it.folio }) { booking ->
                                UpcomingBookingCard(
                                    booking = booking,
                                    onCancelClick = { bookingToCancel = booking }
                                )
                            }
                        }
                    }
                } else {
                    // Vista consolidada (DashboardFilterTab.ALL)
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentFilter = DashboardFilterTab.AMENITIES
                                    amenityViewMode = 0
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = NavyCard,
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(GoldPrimary.copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "CALENDARIO VISUAL DE ÁREAS COMUNES",
                                            color = GoldPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Text(
                                            text = "Consulta fechas y horarios libres para Quincho, Pádel, Piscina y Gym",
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                            maxLines = 2
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        showNewBookingDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = NavyDark),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("+ Reservar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (state.upcomingBookings.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.EventAvailable,
                                title = "Sin reservas próximas de amenidades",
                                description = "Puedes apartar la alberca, canchas de pádel o el quincho para eventos familiares.",
                                buttonText = "Explorar y Reservar Amenidad",
                                onClick = { showNewBookingDialog = true }
                            )
                        }
                    } else {
                        items(state.upcomingBookings, key = { it.folio }) { booking ->
                            UpcomingBookingCard(
                                booking = booking,
                                onCancelClick = { bookingToCancel = booking }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal de Detalle de Código QR con Token Temporal
    selectedQrPassForDetail?.let { pass ->
        val durationH = ((pass.validUntilMillis - pass.createdAtMillis) / (3600 * 1000L)).toInt().coerceAtLeast(1)
        val tokenInfo = VisitorAccessTokenInfo(
            tokenId = "TOK-${pass.passCode.takeLast(6)}",
            passCode = pass.passCode,
            folio = "FOL-${pass.passCode.take(8)}",
            visitorName = pass.guestName,
            visitorDocument = pass.guestDocument,
            destinationHouse = pass.destinationHouse,
            hostResidentName = pass.hostResidentName,
            passTypeLabel = pass.passType.label,
            vehiclePlate = pass.vehiclePlate,
            issuedAtMillis = pass.createdAtMillis,
            validUntilMillis = pass.validUntilMillis,
            durationHours = durationH,
            maxEntries = pass.maxEntries,
            currentEntriesCount = pass.currentEntriesCount,
            integrityHash = pass.integrityHash,
            residentNotes = pass.note,
            isActive = pass.isActive
        )
        TimeLimitedDigitalPassModal(
            tokenInfo = tokenInfo,
            db = db,
            onDismiss = { selectedQrPassForDetail = null }
        )
    }

    // Diálogo interactivo para generar Token Temporal Único con QR
    if (showNewQrDialog) {
        GenerateTimedTokenDialog(
            db = db,
            condominiumId = condominiumId,
            defaultUnit = currentUser.unitOrDepartment,
            defaultHost = currentUser.name,
            onDismiss = { showNewQrDialog = false },
            onTokenCreated = { token ->
                showNewQrDialog = false
                refreshData()
                scope.launch {
                    val pass = db.qrPassDao().getPassByCode(token.passCode)
                    selectedQrPassForDetail = pass
                }
            }
        )
    }

    // Diálogo para reservar amenidad
    if (showNewBookingDialog) {
        CreateAmenityBookingDialog(
            user = currentUser,
            condominiumId = condominiumId,
            firebaseUid = state.firebaseAuthUid,
            db = db,
            onDismiss = { showNewBookingDialog = false },
            onBookingCreated = {
                showNewBookingDialog = false
                refreshData()
                Toast.makeText(context, "Reserva de amenidad confirmada y sincronizada", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Diálogo de confirmación para cancelar reserva
    bookingToCancel?.let { booking ->
        AlertDialog(
            onDismissRequest = { bookingToCancel = null },
            title = { Text("¿Cancelar esta reserva?", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "¿Estás seguro de cancelar tu reserva de '${booking.amenityName}' para el día ${booking.bookingDate} (${booking.timeSlot})? El horario quedará disponible para otros vecinos.",
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            ResidentDashboardRepository.cancelResidentAmenityBooking(
                                db = db,
                                condominiumId = condominiumId,
                                user = currentUser,
                                booking = booking,
                                context = context
                            )
                            bookingToCancel = null
                            refreshData()
                            Toast.makeText(context, "Reserva cancelada correctamente", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Confirmar Cancelación", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookingToCancel = null }) {
                    Text("Volver", color = TextWhite)
                }
            },
            containerColor = NavySurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Diálogo de Verificación de Privacidad y Auditoría
    if (showAuditDetailsDialog) {
        SecurityAuditGuaranteeDialog(
            user = currentUser,
            condominiumId = condominiumId,
            state = state,
            onDismiss = { showAuditDetailsDialog = false }
        )
    }

    // Diálogo de Selección de Usuario Residente para Demostración de Aislamiento
    if (showUserSwitcherDialog) {
        UserSwitcherModal(
            db = db,
            activeUserId = currentUser.id,
            activeFirebaseUid = state.firebaseAuthUid,
            onUserSelected = { selectedUserId, selectedFbUid, selectedEmail ->
                scope.launch {
                    AlfhaSecurityContext.switchActiveUser(db, selectedUserId)
                    customFirebaseUid = selectedFbUid
                    customFirebaseEmail = selectedEmail
                    showUserSwitcherDialog = false
                    refreshData(selectedFbUid, selectedEmail)
                }
            },
            onDismiss = { showUserSwitcherDialog = false }
        )
    }
}

/**
 * Tarjeta destacada de Identidad del Residente y Estado de Seguridad.
 */
@Composable
private fun ResidentIdentityCard(
    user: AlfhaUserEntity,
    condominiumId: String,
    state: ResidentDashboardState,
    fcmStatus: String = "FCM Conectado",
    isSubscribedToUnit: Boolean = true,
    fcmToken: String? = null,
    onSwitchUserClick: () -> Unit,
    onAuditClick: () -> Unit,
    onTestFcmClick: () -> Unit = {}
) {
    var showQueryInspector by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("resident_identity_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(GoldPrimary.copy(alpha = 0.15f))
                            .border(1.5.dp, GoldPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = user.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextWhite,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${user.unitOrDepartment} • $condominiumId",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyanNeon,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Botón para alternar usuario y probar aislamiento
                OutlinedButton(
                    onClick = onSwitchUserClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("switch_resident_user_button")
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cambiar", fontSize = 11.sp)
                }
            }

            // Credenciales y Sesión Firebase Authentication
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = NavyDark,
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = "Firebase Auth",
                            tint = if (state.isFirebaseAuthActive) GoldPrimary else CyanNeon,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (state.isFirebaseAuthActive) "Firebase Auth: Conectado" else "Firebase Auth UID Asignado",
                                    color = if (state.isFirebaseAuthActive) SuccessGreen else GoldPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "UID: ${state.firebaseAuthUid}",
                                color = TextWhite,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NavySurface,
                        border = BorderStroke(0.5.dp, NavyCard)
                    ) {
                        Text(
                            text = state.firebaseAuthEmail,
                            color = CyanNeon,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            // Firebase Cloud Messaging (FCM) - Notificaciones Push en Tiempo Real
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = NavyDark,
                border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("resident_fcm_status_surface")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "FCM Status",
                                tint = CyanNeon,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "FCM Push • Tiempo Real",
                                        color = CyanNeon,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = SuccessGreen.copy(alpha = 0.2f),
                                        border = BorderStroke(0.5.dp, SuccessGreen)
                                    ) {
                                        Text(
                                            text = if (isSubscribedToUnit) "ENLAZADO" else "ACTIVO",
                                            color = SuccessGreen,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Tópico: unit_${condominiumId.lowercase()}_${user.unitOrDepartment.lowercase().replace(" ", "_")}",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Botón para probar alerta push
                        OutlinedButton(
                            onClick = onTestFcmClick,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                            border = BorderStroke(0.8.dp, GoldPrimary.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("btn_test_fcm_notification")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = GoldPrimary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Probar Push",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (!fcmToken.isNullOrBlank()) {
                        Text(
                            text = "Token: ${fcmToken.take(28)}...${fcmToken.takeLast(8)}",
                            color = TextMuted.copy(alpha = 0.7f),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Panel de Inspección de Query Firestore (.whereEqualTo)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = NavyDark.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showQueryInspector = !showQueryInspector }
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterAlt,
                                contentDescription = null,
                                tint = CyanNeon,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Filtro Firestore Activo (.whereEqualTo)",
                                color = CyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (showQueryInspector) "Ocultar" else "Ver Query",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                            Icon(
                                imageVector = if (showQueryInspector) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = showQueryInspector) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Filtro Pases QR:",
                                color = GoldPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = NavySurface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.firestoreQrQueryFilter,
                                    color = TextWhite,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }

                            Text(
                                text = "Filtro Amenidades:",
                                color = SuccessGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = NavySurface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.firestoreBookingQueryFilter,
                                    color = TextWhite,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                        }
                    }

                    // Aislamiento verificado
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Aislamiento: ${state.excludedOtherResidentPassesCount} pases y ${state.excludedOtherResidentBookingsCount} reservas de otros residentes omitidos",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Divider(color = NavyCard, thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable { onAuditClick() }
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (state.isFirestoreOnline) SuccessGreen else CyanNeon)
                    )
                    Text(
                        text = if (state.isFirestoreOnline) "Firestore Activo" else "Caché Local SQLite",
                        color = TextWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Detalles",
                        tint = TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = NavyDark,
                    border = BorderStroke(1.dp, NavyCard)
                ) {
                    Text(
                        text = "ID: ${user.id}",
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

/**
 * Banner de Alerta en Tiempo Real cuando personal de seguridad en caseta
 * escanea el código QR de una visita con éxito.
 */
@Composable
fun RealtimeFcmAlertBanner(
    payload: VisitorCheckInFcmPayload,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("realtime_fcm_alert_banner"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        border = BorderStroke(1.5.dp, SuccessGreen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            SuccessGreen.copy(alpha = 0.22f),
                            NavySurface
                        )
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen.copy(alpha = 0.25f))
                            .border(1.dp, SuccessGreen, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Push Notification",
                            tint = SuccessGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "🔔 ¡VISITA INGRESADA! (FCM)",
                            style = MaterialTheme.typography.labelMedium,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Bold
                        )
                        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(payload.scanTimestamp))
                        Text(
                            text = "Notificación Push en Tiempo Real • $timeStr hrs",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("dismiss_fcm_banner")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar",
                        tint = TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = NavyDark,
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Visita: ${payload.guestName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = CyanNeon.copy(alpha = 0.15f),
                            border = BorderStroke(0.5.dp, CyanNeon)
                        ) {
                            Text(
                                text = payload.passTypeLabel,
                                color = CyanNeon,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Destino: ${payload.targetUnitId}",
                            color = GoldPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!payload.vehiclePlate.isNullOrBlank()) {
                            Text(
                                text = "Patente: ${payload.vehiclePlate}",
                                color = TextWhite,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Text(
                        text = "👮 ${payload.gateLocation} • ${payload.guardName}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )

                    if (payload.guardNotes.isNotBlank()) {
                        Text(
                            text = "💬 ${payload.guardNotes}",
                            color = TextMuted,
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Fila de Tarjetas Resumen Métrico (KPIs).
 */
@Composable
private fun ResidentKpiRow(
    activePassesCount: Int,
    upcomingBookingsCount: Int,
    onQrTabClick: () -> Unit,
    onBookingsTabClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier
                .weight(1f)
                .clickable { onQrTabClick() },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pases QR", color = TextMuted, fontSize = 11.sp)
                    Icon(Icons.Default.QrCode, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$activePassesCount",
                    color = TextWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("Accesos vigentes", color = CyanNeon, fontSize = 10.sp)
            }
        }

        Card(
            modifier = Modifier
                .weight(1f)
                .clickable { onBookingsTabClick() },
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Amenidades", color = TextMuted, fontSize = 11.sp)
                    Icon(Icons.Default.EventAvailable, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$upcomingBookingsCount",
                    color = TextWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("Reservas activas", color = SuccessGreen, fontSize = 10.sp)
            }
        }
    }
}

/**
 * Encabezado de Sección con botón de acción y contador.
 */
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    count: Int,
    badgeColor: Color,
    actionLabel: String,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = badgeColor.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, badgeColor)
                ) {
                    Text(
                        text = "$count",
                        color = badgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                fontSize = 11.sp
            )
        }

        TextButton(
            onClick = onActionClick,
            colors = ButtonDefaults.textButtonColors(contentColor = badgeColor)
        ) {
            Text(actionLabel, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

/**
 * Tarjeta de Pase QR Activo.
 */
@Composable
private fun ActiveQrPassCard(
    pass: QrPassRoomEntity,
    onViewQrCode: () -> Unit,
    onShareCode: () -> Unit
) {
    val expiryFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(pass.validUntilMillis))
    val isNearExpiry = pass.validUntilMillis - System.currentTimeMillis() < 3600000L

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("qr_pass_card_${pass.passCode}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        border = BorderStroke(1.dp, if (isNearExpiry) WarningOrange.copy(alpha = 0.6f) else NavyCard)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = pass.guestName,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Doc: ${pass.guestDocument} • Destino: ${pass.destinationHouse}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TimedTokenStatusBadge(validUntilMillis = pass.validUntilMillis)
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = GoldPrimary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = pass.passType.label,
                            color = GoldPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Información de Código Único y Enlace a Firestore
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = NavyDark,
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CÓDIGO ÚNICO",
                            color = GoldPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = pass.passCode,
                            color = TextWhite,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = CyanNeon.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("En Firestore", color = CyanNeon, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Información técnica, vehículo y vencimiento
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NavyDark, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = if (isNearExpiry) WarningOrange else CyanNeon,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Vence: $expiryFmt",
                        color = if (isNearExpiry) WarningOrange else TextWhite,
                        fontSize = 11.sp
                    )
                }

                if (!pass.vehiclePlate.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = pass.vehiclePlate,
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Text(
                        text = "${pass.maxEntries} ingreso(s)",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Botones de acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onViewQrCode,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("btn_view_qr_${pass.passCode}")
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ver Código / QR", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onShareCode,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                    border = BorderStroke(1.dp, NavyCard),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copiar", fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Tarjeta de Reserva Próxima de Amenidad.
 */
@Composable
private fun UpcomingBookingCard(
    booking: AmenityBooking,
    onCancelClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("booking_card_${booking.folio}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SuccessGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                booking.amenityName.contains("Pádel", true) -> Icons.Default.SportsTennis
                                booking.amenityName.contains("BBQ", true) || booking.amenityName.contains("Quincho", true) -> Icons.Default.OutdoorGrill
                                booking.amenityName.contains("Gym", true) -> Icons.Default.FitnessCenter
                                else -> Icons.Default.Pool
                            },
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = booking.amenityName,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextWhite,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Unidad: ${booking.unitId} • Folio: ${booking.folio}",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SuccessGreen.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, SuccessGreen)
                ) {
                    Text(
                        text = booking.status,
                        color = SuccessGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Horario y detalles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NavyDark, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = CyanNeon,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${booking.bookingDate} • ${booking.timeSlot}",
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Text(
                    text = "${booking.durationMinutes} min",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            if (booking.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Nota: ${booking.notes}",
                    color = TextMuted,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Indicador de Recordatorio Programado (1 hora antes)
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = CyanNeon.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = CyanNeon,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Recordatorio local: 1 hora antes activo",
                            color = CyanNeon,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = if (booking.reminderSent) "Notificado" else "Programado",
                        color = if (booking.reminderSent) SuccessGreen else TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val cardContext = LocalContext.current
                OutlinedButton(
                    onClick = {
                        AmenityReminderManager.sendOneHourReminderNotification(
                            context = cardContext,
                            bookingId = booking.id,
                            amenityName = booking.amenityName,
                            residentName = booking.residentName,
                            unitId = booking.unitId,
                            bookingTimeMillis = booking.bookingTimeMillis
                        )
                        Toast.makeText(cardContext, "⏰ Notificación de 1h antes enviada para ${booking.amenityName}", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon),
                    border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("btn_test_1h_reminder_${booking.folio}")
                ) {
                    Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Probar Alerta 1h", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onCancelClick,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("cancel_booking_${booking.folio}")
                ) {
                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancelar Reserva", fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * Tarjeta de Estado Vacío.
 */
@Composable
private fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        border = BorderStroke(1.dp, NavyCard)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(NavyCard),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(24.dp))
            }
            Text(
                text = title,
                color = TextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(
                onClick = onClick,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                border = BorderStroke(1.dp, GoldPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

/**
 * Diálogo Modal de Detalle de Código QR renderizado en alta definición con ZXing.
 */
@Composable
private fun QrDetailModal(
    pass: QrPassRoomEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val qrBitmap = remember(pass.passCode) { generateQrBitmap(pass.passCode, 512) }
    val expiryFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(pass.validUntilMillis))
    var copiedCode by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("qr_detail_modal"),
            shape = RoundedCornerShape(20.dp),
            color = NavySurface,
            border = BorderStroke(1.5.dp, GoldPrimary)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Pase QR Autorizado",
                            style = MaterialTheme.typography.titleMedium,
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                // Código Único de Entrada con botón de copiado rápido
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NavyDark,
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "CÓDIGO ÚNICO DE ENTRADA",
                            color = GoldPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = pass.passCode,
                            color = TextWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("detail_pass_code")
                        )
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Código Único", pass.passCode)
                                clipboard.setPrimaryClip(clip)
                                copiedCode = true
                                Toast.makeText(context, "Código ${pass.passCode} copiado", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (copiedCode) SuccessGreen.copy(alpha = 0.2f) else GoldPrimary.copy(alpha = 0.15f),
                                contentColor = if (copiedCode) SuccessGreen else GoldPrimary
                            ),
                            border = BorderStroke(1.dp, if (copiedCode) SuccessGreen else GoldPrimary),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(if (copiedCode) Icons.Default.Check else Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (copiedCode) "¡Copiado!" else "Copiar Código", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Matriz del Código QR
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    modifier = Modifier
                        .size(200.dp)
                        .padding(6.dp)
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Código QR del Pase",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Text("Generando QR...", color = Color.Black)
                        }
                    }
                }

                // Datos de la visita
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NavyDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Visitante:", color = TextMuted, fontSize = 11.sp)
                            Text(pass.guestName, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Destino Autorizado:", color = TextMuted, fontSize = 11.sp)
                            Text(pass.destinationHouse, color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Válido hasta:", color = TextMuted, fontSize = 11.sp)
                            Text(expiryFmt, color = SuccessGreen, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                        if (!pass.vehiclePlate.isNullOrBlank()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Vehículo:", color = TextMuted, fontSize = 11.sp)
                                Text("Placa ${pass.vehiclePlate}", color = TextWhite, fontSize = 11.sp)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Firestore:", color = TextMuted, fontSize = 10.sp)
                            Text("Vinculado a ${pass.hostResidentName}", color = CyanNeon, fontSize = 10.sp)
                        }
                    }
                }

                // Acciones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Hola ${pass.guestName}, tu código de entrada único para ${pass.destinationHouse} es: ${pass.passCode}. Válido hasta $expiryFmt. Preséntalo en caseta de vigilancia."
                                    )
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Compartir código con visitante")
                                context.startActivity(shareIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error al compartir: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                        border = BorderStroke(1.dp, GoldPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Compartir", fontSize = 12.sp, color = GoldPrimary)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Text("Listo", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Formulario Oficial de Registro de Visitantes para Residentes.
 * Genera un código de entrada único almacenado en Firestore bajo la partición del condominio
 * y vinculado estrictamente a la cuenta del residente autenticado.
 */
@Composable
private fun RegisterVisitorFormDialog(
    user: AlfhaUserEntity,
    condominiumId: String,
    firebaseUid: String,
    db: AppDatabase,
    onDismiss: () -> Unit,
    onPassCreated: (QrPassRoomEntity) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var guestName by remember { mutableStateOf("") }
    var guestDoc by remember { mutableStateOf("") }
    var passType by remember { mutableStateOf(PassType.VISITOR_SINGLE) }
    var durationHours by remember { mutableIntStateOf(12) }
    var isVehicular by remember { mutableStateOf(false) }
    var vehiclePlate by remember { mutableStateOf("") }
    var maxEntries by remember { mutableIntStateOf(1) }
    var note by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var createdPass by remember { mutableStateOf<QrPassRoomEntity?>(null) }
    var copiedCode by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("visitor_registration_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = NavySurface,
            border = BorderStroke(1.5.dp, GoldPrimary)
        ) {
            val scrollState = rememberScrollState()

            if (createdPass == null) {
                // ==========================================
                // FASE 1: FORMULARIO DE REGISTRO
                // ==========================================
                Column(
                    modifier = Modifier
                        .padding(18.dp)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Encabezado
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = CircleShape,
                                color = GoldPrimary.copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Registro de Visitante",
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp
                                )
                                Text(
                                    text = "Genera un código único de acceso",
                                    color = TextMuted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                        }
                    }

                    // Ficha de Aislamiento y Vinculación a Cuenta del Residente
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = NavyDark,
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "VINCULACIÓN A CUENTA DE RESIDENTE",
                                    color = GoldPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Text(
                                text = "Titular: ${user.name} • Unidad: ${user.unitOrDepartment}",
                                color = TextWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Auth UID: $firebaseUid • Partición: $condominiumId",
                                color = CyanNeon,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Nombre Completo del Visitante
                    OutlinedTextField(
                        value = guestName,
                        onValueChange = { guestName = it },
                        label = { Text("Nombre Completo del Visitante *") },
                        placeholder = { Text("Ej. Carlos Méndez Soto") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = GoldPrimary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = NavyCard
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_guest_name")
                    )

                    // Documento / Identificación
                    OutlinedTextField(
                        value = guestDoc,
                        onValueChange = { guestDoc = it },
                        label = { Text("Documento / INE / DNI / Cédula (Opcional)") },
                        placeholder = { Text("Ej. INE 84920489") },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = CyanNeon) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = NavyCard
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_guest_doc")
                    )

                    // Tipo de Visita
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Tipo de Visita",
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                PassType.VISITOR_SINGLE to "Visita",
                                PassType.DELIVERY_SERVICE to "Delivery",
                                PassType.EVENT_GUEST to "Evento",
                                PassType.RESIDENT_PERMANENT to "Frecuente"
                            ).forEach { (type, label) ->
                                val isSelected = passType == type
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { passType = type },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GoldPrimary,
                                        selectedLabelColor = NavyDark,
                                        containerColor = NavyDark,
                                        labelColor = TextWhite
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = NavyCard,
                                        selectedBorderColor = GoldPrimary
                                    )
                                )
                            }
                        }
                    }

                    // Modalidad de Ingreso: Peatonal o Vehicular
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Modalidad de Llegada",
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !isVehicular,
                                onClick = { isVehicular = false },
                                label = { Text("Peatonal", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.DirectionsWalk, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldPrimary,
                                    selectedLabelColor = NavyDark,
                                    containerColor = NavyDark,
                                    labelColor = TextWhite
                                )
                            )
                            FilterChip(
                                selected = isVehicular,
                                onClick = { isVehicular = true },
                                label = { Text("Vehicular", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(14.dp))
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldPrimary,
                                    selectedLabelColor = NavyDark,
                                    containerColor = NavyDark,
                                    labelColor = TextWhite
                                )
                            )
                        }

                        if (isVehicular) {
                            OutlinedTextField(
                                value = vehiclePlate,
                                onValueChange = { vehiclePlate = it.uppercase() },
                                label = { Text("Placa del Vehículo *") },
                                placeholder = { Text("Ej. ABC-789-Z") },
                                leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = GoldPrimary) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    focusedBorderColor = GoldPrimary,
                                    unfocusedBorderColor = NavyCard
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_vehicle_plate")
                            )
                        }
                    }

                    // Vigencia del Pase
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val expiryTime = remember(durationHours) {
                            val cal = Calendar.getInstance()
                            cal.add(Calendar.HOUR_OF_DAY, durationHours)
                            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(cal.time)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Vigencia del Pase", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Expira: $expiryTime", color = CyanNeon, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(4 to "4h", 12 to "12h", 24 to "24h", 48 to "48h", 168 to "7d").forEach { (hrs, label) ->
                                val selected = durationHours == hrs
                                FilterChip(
                                    selected = selected,
                                    onClick = { durationHours = hrs },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GoldPrimary,
                                        selectedLabelColor = NavyDark,
                                        containerColor = NavyDark,
                                        labelColor = TextWhite
                                    )
                                )
                            }
                        }
                    }

                    // Límite de Entradas
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Límite de Accesos", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(1 to "1 Ingreso (Único)", 2 to "2 Ingresos", 10 to "Ilimitado").forEach { (entries, label) ->
                                val selected = maxEntries == entries
                                FilterChip(
                                    selected = selected,
                                    onClick = { maxEntries = entries },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GoldPrimary,
                                        selectedLabelColor = NavyDark,
                                        containerColor = NavyDark,
                                        labelColor = TextWhite
                                    )
                                )
                            }
                        }
                    }

                    // Notas para Caseta
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Instrucciones para Vigilancia (Opcional)") },
                        placeholder = { Text("Ej. Viene a entregar un paquete, autorizar acceso rápido...") },
                        leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = TextMuted) },
                        maxLines = 2,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = NavyCard
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_visitor_note")
                    )

                    // Aviso de Almacenamiento en Firestore
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NavyDark,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "El código único se registrará en Firestore (/condominiums/$condominiumId/qr_passes) y quedará indexado a tu cuenta.",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Botón de Envío
                    Button(
                        onClick = {
                            if (guestName.isBlank()) return@Button
                            isSubmitting = true
                            scope.launch {
                                val result = ResidentDashboardRepository.createResidentQrPass(
                                    db = db,
                                    condominiumId = condominiumId,
                                    user = user,
                                    guestName = guestName,
                                    guestDocument = guestDoc,
                                    passType = passType,
                                    validDurationHours = durationHours,
                                    vehiclePlate = if (isVehicular) vehiclePlate else null,
                                    note = note,
                                    maxEntries = maxEntries,
                                    codePrefix = "VIS",
                                    firebaseUid = firebaseUid
                                )
                                isSubmitting = false
                                result.onSuccess { pass ->
                                    createdPass = pass
                                    onPassCreated(pass)
                                }
                            }
                        },
                        enabled = guestName.isNotBlank() && (!isVehicular || vehiclePlate.isNotBlank()) && !isSubmitting,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_submit_create_qr")
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NavyDark, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Guardando en Firestore...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Registrar y Generar Código Único", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                // ==========================================
                // FASE 2: CONFIRMACIÓN Y CÓDIGO ÚNICO GENERADO
                // ==========================================
                val pass = createdPass!!
                val expiryFmt = remember(pass.validUntilMillis) {
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(pass.validUntilMillis))
                }
                val qrBitmap = remember(pass.passCode) {
                    generateQrBitmap(pass.passCode, 400)
                }

                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Checkmark de Éxito
                    Surface(
                        shape = CircleShape,
                        color = SuccessGreen.copy(alpha = 0.15f),
                        border = BorderStroke(2.dp, SuccessGreen),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(32.dp))
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "¡Visitante Registrado!",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Código único almacenado en Firestore y vinculado a tu cuenta",
                            color = CyanNeon,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    // Caja Hero del Código Único
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = NavyDark,
                        border = BorderStroke(1.5.dp, GoldPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "CÓDIGO ÚNICO DE ENTRADA",
                                color = GoldPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = pass.passCode,
                                color = TextWhite,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.testTag("text_generated_entry_code")
                            )
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Código de Entrada Único", pass.passCode)
                                    clipboard.setPrimaryClip(clip)
                                    copiedCode = true
                                    Toast.makeText(context, "Código ${pass.passCode} copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (copiedCode) SuccessGreen.copy(alpha = 0.25f) else GoldPrimary.copy(alpha = 0.2f),
                                    contentColor = if (copiedCode) SuccessGreen else GoldPrimary
                                ),
                                border = BorderStroke(1.dp, if (copiedCode) SuccessGreen else GoldPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("btn_copy_unique_code")
                            ) {
                                Icon(
                                    if (copiedCode) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (copiedCode) "¡Copiado al Portapapeles!" else "Copiar Código",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Renderizado del Código QR
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White,
                        modifier = Modifier
                            .size(180.dp)
                            .padding(4.dp)
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Código QR de Entrada",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Renderizando QR...", color = Color.Black, fontSize = 12.sp)
                            }
                        }
                    }

                    Text(
                        text = "Válido para escanear en Garita o teclear en control de accesos",
                        color = TextMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )

                    // Ficha de Resumen del Registro
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NavyDark,
                        border = BorderStroke(1.dp, NavyCard),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Visitante:", color = TextMuted, fontSize = 11.sp)
                                Text(pass.guestName, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Destino:", color = TextMuted, fontSize = 11.sp)
                                Text(pass.destinationHouse, color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Vigencia:", color = TextMuted, fontSize = 11.sp)
                                Text(expiryFmt, color = TextWhite, fontSize = 11.sp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Accesos autorizados:", color = TextMuted, fontSize = 11.sp)
                                Text("${pass.maxEntries} entrada(s)", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            if (!pass.vehiclePlate.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Vehículo:", color = TextMuted, fontSize = 11.sp)
                                    Text("Placa: ${pass.vehiclePlate}", color = TextWhite, fontSize = 11.sp)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Cuenta Vinculada:", color = TextMuted, fontSize = 11.sp)
                                Text("${user.name} (${user.id})", color = TextWhite, fontSize = 11.sp)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Firestore Ref:", color = TextMuted, fontSize = 10.sp)
                                Text("/qr_passes/${pass.passCode}", color = CyanNeon, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                            }
                        }
                    }

                    // Botones de Compartir y Listo
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Hola ${pass.guestName}, tu código de entrada único para visitar ${pass.destinationHouse} en Los Prados Residencial es: ${pass.passCode}. Válido hasta $expiryFmt. Preséntalo o dicta el código en caseta de vigilancia para acceder."
                                        )
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Compartir código con visitante")
                                    context.startActivity(shareIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error al compartir: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                            border = BorderStroke(1.dp, GoldPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("btn_share_unique_code")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compartir Pase", fontSize = 12.sp, color = GoldPrimary, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("btn_done_registration")
                        ) {
                            Icon(Icons.Default.Done, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Listo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Diálogo compatible para crear un nuevo Pase QR o registrar visitantes.
 */
@Composable
private fun CreateQrPassDialog(
    user: AlfhaUserEntity,
    condominiumId: String,
    firebaseUid: String,
    db: AppDatabase,
    onDismiss: () -> Unit,
    onPassCreated: () -> Unit
) {
    RegisterVisitorFormDialog(
        user = user,
        condominiumId = condominiumId,
        firebaseUid = firebaseUid,
        db = db,
        onDismiss = onDismiss,
        onPassCreated = { onPassCreated() }
    )
}

/**
 * Diálogo Interactivo con Calendario Visual para apartar áreas comunes en Los Prados.
 * Muestra fechas y horarios disponibles en tiempo real con aislamiento Firestore por unidad.
 */
@Composable
private fun CreateAmenityBookingDialog(
    user: AlfhaUserEntity,
    condominiumId: String,
    firebaseUid: String,
    db: AppDatabase,
    onDismiss: () -> Unit,
    onBookingCreated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val catalog = remember { AmenityBookingEngine.CATALOG }
    var selectedAmenity by remember { mutableStateOf(catalog.first()) }

    // Calendario de navegación del mes
    var displayMonthCalendar by remember {
        mutableStateOf(Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        })
    }

    // Fecha seleccionada (por defecto hoy, o mañana si ya es tarde)
    var selectedDateCalendar by remember {
        mutableStateOf(Calendar.getInstance().apply {
            if (get(Calendar.HOUR_OF_DAY) >= 20) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        })
    }

    val allCondoBookings by db.amenityBookingDao().getBookingsByCondominium(condominiumId).collectAsState(initial = emptyList())

    var calculatedSlots by remember { mutableStateOf<List<TimeSlotAvailability>>(emptyList()) }
    var selectedSlot by remember { mutableStateOf<TimeSlotAvailability?>(null) }
    var isLoadingSlots by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    val monthTitleFormatter = remember { SimpleDateFormat("MMMM yyyy", Locale("es", "ES")) }
    val selectedDateHeaderFormatter = remember { SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "ES")) }
    val summaryDateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES")) }

    // Calcular disponibilidad en vivo cuando cambia el área común o la fecha seleccionada
    LaunchedEffect(selectedAmenity.name, selectedDateCalendar.timeInMillis, allCondoBookings.size) {
        isLoadingSlots = true
        val slots = AmenityBookingEngine.getDailyAvailability(
            db = db,
            amenityName = selectedAmenity.name,
            targetDateCalendar = selectedDateCalendar,
            condominiumId = condominiumId
        )
        calculatedSlots = slots
        // Auto-seleccionar primer horario disponible si el actual no existe o no está libre
        if (selectedSlot == null || slots.none { it.slotLabel == selectedSlot?.slotLabel && it.isAvailable }) {
            selectedSlot = slots.firstOrNull { it.isAvailable }
        }
        isLoadingSlots = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp)
                .testTag("create_booking_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = NavySurface,
            border = BorderStroke(1.5.dp, SuccessGreen)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Barra superior de encabezado
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(SuccessGreen.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "CALENDARIO DE RESERVAS",
                                color = SuccessGreen,
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Disponibilidad de áreas comunes en tiempo real",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                // Insignia de aislamiento de condominio y unidad
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NavyDark,
                    border = BorderStroke(1.dp, NavyCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Unidad: ${user.unitOrDepartment} • Los Prados ($condominiumId)",
                                color = CyanNeon,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                        Surface(
                            color = SuccessGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "FIRESTORE SYNC",
                                color = SuccessGreen,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // 1. Selector de Amenidad
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "1. SELECCIONA EL ÁREA COMÚN:",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(catalog) { item ->
                            val isSelected = selectedAmenity.name == item.name
                            Surface(
                                onClick = { selectedAmenity = item },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) SuccessGreen else NavyCard,
                                border = BorderStroke(1.dp, if (isSelected) SuccessGreen else Color.White.copy(alpha = 0.1f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = getAmenityIcon(item.name),
                                        contentDescription = null,
                                        tint = if (isSelected) NavyDark else GoldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Column {
                                        Text(
                                            text = item.name,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) NavyDark else Color.White
                                        )
                                        Text(
                                            text = "Aforo ${item.capacity}p • Máx ${item.maxHoursPerBooking}h",
                                            fontSize = 9.sp,
                                            color = if (isSelected) NavyDark.copy(alpha = 0.8f) else TextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Calendario Visual Mensual
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = NavyCard,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Navegación de Mes
                        val nowCal = remember { Calendar.getInstance() }
                        val isAtOrBeforeCurrentMonth = displayMonthCalendar.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
                                displayMonthCalendar.get(Calendar.MONTH) <= nowCal.get(Calendar.MONTH)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (!isAtOrBeforeCurrentMonth) {
                                        val prev = displayMonthCalendar.clone() as Calendar
                                        prev.add(Calendar.MONTH, -1)
                                        displayMonthCalendar = prev
                                    }
                                },
                                enabled = !isAtOrBeforeCurrentMonth
                            ) {
                                Icon(
                                    Icons.Default.ChevronLeft,
                                    contentDescription = "Mes anterior",
                                    tint = if (!isAtOrBeforeCurrentMonth) Color.White else TextMuted.copy(alpha = 0.3f)
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = monthTitleFormatter.format(displayMonthCalendar.time).uppercase(),
                                    color = GoldPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Toca un día para ver disponibilidad",
                                    color = TextMuted,
                                    fontSize = 9.sp
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = {
                                        displayMonthCalendar = Calendar.getInstance().apply {
                                            set(Calendar.DAY_OF_MONTH, 1)
                                        }
                                        selectedDateCalendar = Calendar.getInstance()
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Hoy", color = CyanNeon, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(
                                    onClick = {
                                        val next = displayMonthCalendar.clone() as Calendar
                                        next.add(Calendar.MONTH, 1)
                                        displayMonthCalendar = next
                                    }
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Mes siguiente", tint = Color.White)
                                }
                            }
                        }

                        // Encabezado de Días de la Semana
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf("LUN", "MAR", "MIÉ", "JUE", "VIE", "SÁB", "DOM").forEach { dayName ->
                                Text(
                                    text = dayName,
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Cuadrícula de 7 columnas
                        val daysInMonth = displayMonthCalendar.getActualMaximum(Calendar.DAY_OF_MONTH)
                        val tempFirstDayCal = (displayMonthCalendar.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
                        val startDayOfWeek = (tempFirstDayCal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7

                        val isCurrentMonth = nowCal.get(Calendar.YEAR) == displayMonthCalendar.get(Calendar.YEAR) &&
                                nowCal.get(Calendar.MONTH) == displayMonthCalendar.get(Calendar.MONTH)
                        val todayDay = nowCal.get(Calendar.DAY_OF_MONTH)

                        val isSelectedMonth = selectedDateCalendar.get(Calendar.YEAR) == displayMonthCalendar.get(Calendar.YEAR) &&
                                selectedDateCalendar.get(Calendar.MONTH) == displayMonthCalendar.get(Calendar.MONTH)
                        val selectedDay = selectedDateCalendar.get(Calendar.DAY_OF_MONTH)

                        val totalCells = startDayOfWeek + daysInMonth
                        val numRows = (totalCells + 6) / 7

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (row in 0 until numRows) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    for (col in 0 until 7) {
                                        val cellIndex = row * 7 + col
                                        val dayNumber = cellIndex - startDayOfWeek + 1

                                        if (dayNumber in 1..daysInMonth) {
                                            val isPast = (isCurrentMonth && dayNumber < todayDay) ||
                                                    (displayMonthCalendar.get(Calendar.YEAR) < nowCal.get(Calendar.YEAR) ||
                                                            (displayMonthCalendar.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) && displayMonthCalendar.get(Calendar.MONTH) < nowCal.get(Calendar.MONTH)))
                                            val isToday = isCurrentMonth && dayNumber == todayDay
                                            val isSelected = isSelectedMonth && dayNumber == selectedDay

                                            // Comprobar si hay reservas para esta amenidad en este día
                                            val dayCal = (displayMonthCalendar.clone() as Calendar).apply {
                                                set(Calendar.DAY_OF_MONTH, dayNumber)
                                            }
                                            val dayBookingsCount = allCondoBookings.count { b ->
                                                val bCal = Calendar.getInstance().apply { timeInMillis = b.bookingTimeMillis }
                                                b.amenityName == selectedAmenity.name &&
                                                        bCal.get(Calendar.YEAR) == dayCal.get(Calendar.YEAR) &&
                                                        bCal.get(Calendar.DAY_OF_YEAR) == dayCal.get(Calendar.DAY_OF_YEAR)
                                            }

                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .padding(2.dp)
                                                    .clickable(enabled = !isPast) {
                                                        selectedDateCalendar = (dayCal.clone() as Calendar)
                                                    },
                                                shape = RoundedCornerShape(8.dp),
                                                color = when {
                                                    isSelected -> SuccessGreen
                                                    isPast -> Color.Transparent
                                                    else -> NavyDark.copy(alpha = 0.8f)
                                                },
                                                border = when {
                                                    isSelected -> BorderStroke(1.5.dp, SuccessGreen)
                                                    isToday -> BorderStroke(1.dp, GoldPrimary)
                                                    else -> BorderStroke(0.5.dp, Color.White.copy(alpha = 0.05f))
                                                }
                                            ) {
                                                Column(
                                                    modifier = Modifier.fillMaxSize(),
                                                    verticalArrangement = Arrangement.Center,
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        text = "$dayNumber",
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected || isToday) FontWeight.Black else FontWeight.Medium,
                                                        color = when {
                                                            isSelected -> NavyDark
                                                            isPast -> TextMuted.copy(alpha = 0.35f)
                                                            isToday -> GoldPrimary
                                                            else -> Color.White
                                                        }
                                                    )
                                                    if (!isPast) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(4.dp)
                                                                .background(
                                                                    if (isSelected) NavyDark else if (dayBookingsCount > 0) GoldPrimary else SuccessGreen.copy(alpha = 0.5f),
                                                                    CircleShape
                                                                )
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // Accesos rápidos de fechas
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                0 to "Hoy",
                                1 to "Mañana",
                                2 to "+2 días",
                                3 to "+3 días"
                            ).forEach { (dayOffset, label) ->
                                val targetCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
                                val isSame = selectedDateCalendar.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR) &&
                                        selectedDateCalendar.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR)
                                Surface(
                                    onClick = {
                                        selectedDateCalendar = targetCal
                                        displayMonthCalendar = (targetCal.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
                                    },
                                    color = if (isSame) CyanNeon.copy(alpha = 0.2f) else NavyDark,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, if (isSame) CyanNeon else Color.White.copy(alpha = 0.08f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = label,
                                        color = if (isSame) CyanNeon else TextWhite,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSame) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.padding(vertical = 5.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Selector de Horarios Disponibles para la fecha seleccionada
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "3. HORARIOS DISPONIBLES:",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = selectedDateHeaderFormatter.format(selectedDateCalendar.time).replaceFirstChar { it.uppercase() },
                                color = CyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Surface(
                            color = SuccessGreen.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${calculatedSlots.count { it.isAvailable }} de ${calculatedSlots.size} libres",
                                color = SuccessGreen,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    if (isLoadingSlots) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = SuccessGreen, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Calculando disponibilidad en Firestore...", color = TextMuted, fontSize = 11.sp)
                        }
                    } else if (calculatedSlots.isEmpty()) {
                        Surface(
                            color = NavyDark,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No hay horarios configurados para esta fecha.",
                                color = TextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Lista de turnos en cuadrícula de 2 columnas
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            calculatedSlots.chunked(2).forEach { rowSlots ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowSlots.forEach { slot ->
                                        val isSelected = selectedSlot?.slotLabel == slot.slotLabel
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable(enabled = slot.isAvailable) {
                                                    selectedSlot = slot
                                                },
                                            shape = RoundedCornerShape(10.dp),
                                            color = when {
                                                isSelected -> SuccessGreen.copy(alpha = 0.2f)
                                                slot.isAvailable -> NavyCard
                                                else -> NavyDark.copy(alpha = 0.5f)
                                            },
                                            border = BorderStroke(
                                                1.dp,
                                                when {
                                                    isSelected -> SuccessGreen
                                                    slot.isAvailable -> Color.White.copy(alpha = 0.1f)
                                                    else -> ErrorRed.copy(alpha = 0.3f)
                                                }
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = slot.slotLabel,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSelected) SuccessGreen else if (slot.isAvailable) Color.White else TextMuted
                                                    )
                                                    Text(
                                                        text = if (slot.isAvailable) "2 hrs duración" else "Ocupado por ${slot.bookedByUnit}",
                                                        fontSize = 9.sp,
                                                        color = if (slot.isAvailable) TextMuted else ErrorRed
                                                    )
                                                }

                                                if (slot.isAvailable) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .background(
                                                                if (isSelected) SuccessGreen else Color.Transparent,
                                                                CircleShape
                                                            )
                                                            .border(1.dp, if (isSelected) SuccessGreen else Color.White.copy(alpha = 0.3f), CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        if (isSelected) {
                                                            Icon(Icons.Default.Check, contentDescription = null, tint = NavyDark, modifier = Modifier.size(12.dp))
                                                        }
                                                    }
                                                } else {
                                                    Surface(
                                                        color = ErrorRed.copy(alpha = 0.15f),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text(
                                                            text = "OCUPADO",
                                                            color = ErrorRed,
                                                            fontSize = 8.sp,
                                                            fontWeight = FontWeight.Black,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (rowSlots.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Motivo / Notas opcionales
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Nota / Motivo del evento (opcional, ej. Cumpleaños familiar)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = SuccessGreen,
                        unfocusedBorderColor = NavyCard
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // 5. Resumen de la reserva
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NavyDark,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("RESUMEN DE RESERVA:", color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Área:", color = TextMuted, fontSize = 11.sp)
                            Text("${selectedAmenity.name} (${selectedAmenity.capacity} pers)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Fecha:", color = TextMuted, fontSize = 11.sp)
                            Text(summaryDateFormatter.format(selectedDateCalendar.time), color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Horario:", color = TextMuted, fontSize = 11.sp)
                            Text(selectedSlot?.slotLabel ?: "Ninguno seleccionado", color = SuccessGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Titular / Unidad:", color = TextMuted, fontSize = 11.sp)
                            Text("${user.name} • ${user.unitOrDepartment}", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Botón de Confirmación
                Button(
                    onClick = {
                        val slot = selectedSlot ?: return@Button
                        isSubmitting = true

                        val bookingCal = (selectedDateCalendar.clone() as Calendar).apply {
                            val slotCal = Calendar.getInstance().apply { timeInMillis = slot.startMillis }
                            set(Calendar.HOUR_OF_DAY, slotCal.get(Calendar.HOUR_OF_DAY))
                            set(Calendar.MINUTE, slotCal.get(Calendar.MINUTE))
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }

                        scope.launch {
                            ResidentDashboardRepository.createResidentAmenityBooking(
                                db = db,
                                condominiumId = condominiumId,
                                user = user,
                                amenityName = selectedAmenity.name,
                                bookingDateCalendar = bookingCal,
                                timeSlot = slot.slotLabel,
                                notes = notes.ifBlank { "Reserva desde Calendario Visual" },
                                firebaseUid = firebaseUid,
                                context = context
                            )
                            isSubmitting = false
                            Toast.makeText(context, "✅ Reserva confirmada: ${selectedAmenity.name} (${slot.slotLabel}) • Recordatorio 1h antes activado", Toast.LENGTH_LONG).show()
                            onBookingCreated()
                            onDismiss()
                        }
                    },
                    enabled = !isSubmitting && selectedSlot != null,
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = NavyDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_submit_create_booking")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NavyDark, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sincronizando con Firestore...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.EventAvailable, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (selectedSlot != null) "Confirmar Reserva [${selectedSlot?.slotLabel}]" else "Selecciona un Horario Libre",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Diálogo de Verificación de Privacidad y Auditoría Criptográfica.
 */
@Composable
private fun SecurityAuditGuaranteeDialog(
    user: AlfhaUserEntity,
    condominiumId: String,
    state: ResidentDashboardState,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("security_guarantee_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = NavySurface,
            border = BorderStroke(1.dp, CyanNeon)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Garantía de Aislamiento",
                        color = CyanNeon,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NavyDark,
                    border = BorderStroke(1.dp, NavyCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Regla de Seguridad Firestore (.whereEqualTo):", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = state.firestoreQrQueryFilter,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Filtro de Amenidades:", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = state.firestoreBookingQueryFilter,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = TextWhite
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Partición Multi-Tenant:", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = "Condominio: $condominiumId | Unidad: ${user.unitOrDepartment} | Auth UID: ${state.firebaseAuthUid}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = CyanNeon
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SuccessGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Aislamiento Criptográfico Verificado: ${state.excludedOtherResidentPassesCount} pases y ${state.excludedOtherResidentBookingsCount} reservas de otros residentes han sido excluidos de la consulta.",
                            color = TextWhite,
                            fontSize = 11.sp
                        )
                    }
                }

                Text(
                    text = "MEDUSA ALFHA garantiza que ningún residente puede ver, consultar o listar pases ni reservas de otros condóminos. Todas las consultas viajan aisladas por token criptográfico de Firebase Authentication y regla de coincidencia estricta de ID.",
                    color = TextMuted,
                    fontSize = 11.sp
                )

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Entendido", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Diálogo para alternar entre diferentes usuarios residentes y comprobar que no hay filtraciones.
 */
@Composable
private fun UserSwitcherModal(
    db: AppDatabase,
    activeUserId: String,
    activeFirebaseUid: String,
    onUserSelected: (String, String?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val users by produceState<List<AlfhaUserEntity>>(initialValue = emptyList()) {
        value = db.alfhaUserDao().getActiveUsers()
    }
    var customUidInput by remember { mutableStateOf("") }
    var customEmailInput by remember { mutableStateOf("") }
    var showCustomInput by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(20.dp),
            color = NavySurface,
            border = BorderStroke(1.dp, GoldPrimary)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Alternar Residente / Firebase Auth",
                        color = GoldPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                Text(
                    text = "Selecciona un usuario residente para verificar cómo las consultas Firestore se filtran estrictamente por su Firebase Auth UID y unidad:",
                    color = TextMuted,
                    fontSize = 11.sp
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(users) { u ->
                        val isSelected = u.id == activeUserId
                        val fbUid = "usr-fb-${u.id.lowercase().replace("_", "-")}"
                        val email = "${u.id.lowercase().replace("_", ".")}@losprados.com"

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) GoldPrimary.copy(alpha = 0.15f) else NavyDark,
                            border = BorderStroke(1.dp, if (isSelected) GoldPrimary else NavyCard),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUserSelected(u.id, fbUid, email)
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(u.name, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("${u.unitOrDepartment} • ${u.role}", color = CyanNeon, fontSize = 11.sp)
                                    Text("Firebase UID: $fbUid", color = TextMuted, fontFamily = FontFamily.Monospace, fontSize = 9.sp)
                                }
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                Divider(color = NavyCard, thickness = 1.dp)

                // Sección para ingresar credenciales Firebase personalizadas
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showCustomInput = !showCustomInput },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Probar UID Firebase Personalizado",
                        color = CyanNeon,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = if (showCustomInput) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = CyanNeon,
                        modifier = Modifier.size(16.dp)
                    )
                }

                AnimatedVisibility(visible = showCustomInput) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customUidInput,
                            onValueChange = { customUidInput = it },
                            label = { Text("Firebase Auth UID (ej: alfha-uid-070)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = NavyCard
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = customEmailInput,
                            onValueChange = { customEmailInput = it },
                            label = { Text("Email (ej: alfhaseguridad070@gmail.com)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = NavyCard
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (customUidInput.isNotBlank()) {
                                    onUserSelected(activeUserId, customUidInput.trim(), customEmailInput.trim().ifBlank { null })
                                }
                            },
                            enabled = customUidInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Aplicar UID a Firestore", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Generador de Bitmap de Código QR usando ZXing.
 */
private fun generateQrBitmap(contents: String, sizePx: Int = 512): Bitmap? {
    return try {
        val writer = MultiFormatWriter()
        val bitMatrix = writer.encode(contents, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp
    } catch (e: Exception) {
        null
    }
}
