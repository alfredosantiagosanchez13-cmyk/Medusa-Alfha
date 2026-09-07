package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.booking.AppDatabase
import com.example.ui.components.GenerateTimedTokenDialog
import com.example.ui.components.TimeLimitedDigitalPassModal
import com.example.ui.components.TimedTokenStatusBadge
import com.example.ui.components.VisitorAccessTokenInfo
import com.example.data.core.AlphaCoreEngine
import com.example.data.passes.QrPassRoomEntity
import com.example.scanner.PassType
import com.example.data.firebase.FirebaseConfigHelper
import com.example.data.visitor.VisitorCheckIn
import com.example.data.visitor.VisitorCheckInRepository
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarningOrange
import com.example.utils.ResidentNotificationManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PANTALLA OFICIAL DE GESTIÓN DE VISITANTES (VISITOR MANAGEMENT SCREEN)
 *
 * Características Clave:
 * - Registra invitados tanto para residentes como para personal de seguridad.
 * - Registra bitácoras de ingreso/salida en Firestore (/condominiums/{condoId}/visitor_logs)
 *   utilizando Timestamp nativo, visitorName y authorizedUnitNumber.
 * - Soporte offline-first con sincronización automática en Room Database.
 * - Historial interactivo con búsqueda en vivo, filtros por estado y trazabilidad completa.
 * - Check-in y check-out de un solo toque con cálculo de permanencia.
 * - Generador y visualizador de pase QR con opción para compartir.
 */
@Composable
fun VisitorManagementScreen(
    db: AppDatabase,
    condominiumId: String = "PRADOS_1",
    userUnit: String = "Casa #104",
    userName: String = "Carlos Mendoza",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember {
        VisitorCheckInRepository(db.visitorCheckInDao(), activeCondominiumId = condominiumId)
    }

    val checkIns by repository.allCheckIns.collectAsState(initial = emptyList())
    val qrPasses by db.qrPassDao().getAllPassesFlow().collectAsState(initial = emptyList())
    val qrPassMap = remember(qrPasses) { qrPasses.associateBy { it.passCode } }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("TODOS") }
    var showRegisterModal by remember { mutableStateOf(false) }
    var showGenerateTimedTokenDialog by remember { mutableStateOf(false) }
    var activeTimedTokenModal by remember { mutableStateOf<VisitorAccessTokenInfo?>(null) }
    var qrPassToShow by remember { mutableStateOf<VisitorCheckIn?>(null) }
    var detailEntryToShow by remember { mutableStateOf<VisitorCheckIn?>(null) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf("Sincronizado con Firestore") }

    // Al inicio, sembrar datos de prueba si Room está vacío e intentar sincronizar desde Firestore
    LaunchedEffect(Unit) {
        repository.seedInitialCheckInsIfEmpty()
        try {
            val syncRes = repository.syncFromFirestore(condominiumId)
            if (syncRes.isSuccess) {
                val count = syncRes.getOrDefault(0)
                syncMessage = if (count > 0) "Firestore: $count nuevos logs" else "Nube Firestore al día"
            }
        } catch (_: Exception) {}
    }

    // Métricas de KPIs
    val totalCount = checkIns.size
    val insideCount = checkIns.count { it.status == "CHECKED_IN" || it.status == "VERIFICADO" }
    val pendingCount = checkIns.count { it.status == "PRE_REGISTRADO" || it.status == "PENDIENTE" }
    val departedCount = checkIns.count { it.status == "DEPARTED" }

    // Filtrado y búsqueda
    val filteredList = remember(checkIns, searchQuery, selectedFilter) {
        checkIns.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                    item.visitorName.contains(searchQuery, ignoreCase = true) ||
                    item.destinationHouse.contains(searchQuery, ignoreCase = true) ||
                    item.visitorDocument.contains(searchQuery, ignoreCase = true) ||
                    item.passCode.contains(searchQuery, ignoreCase = true) ||
                    item.folio.contains(searchQuery, ignoreCase = true) ||
                    (item.vehiclePlate?.contains(searchQuery, ignoreCase = true) == true)

            val matchesFilter = when (selectedFilter) {
                "PASES TEMPORALES" -> qrPassMap.containsKey(item.passCode) || item.passTypeLabel.contains("Token", ignoreCase = true) || item.status == "PRE_REGISTRADO"
                "EN CONDOMINIO" -> item.status == "CHECKED_IN" || item.status == "VERIFICADO"
                "PRE-REGISTRADOS" -> item.status == "PRE_REGISTRADO" || item.status == "PENDIENTE"
                "SALIDAS" -> item.status == "DEPARTED"
                else -> true
            }

            matchesSearch && matchesFilter
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("visitor_management_screen")
    ) {
        // ENCABEZADO PRINCIPAL DE LA PANTALLA
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = NavySurface,
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(GoldPrimary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AssignmentInd,
                                contentDescription = "Gestión de Visitas",
                                tint = GoldPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "CONTROL DE ACCESOS Y VISITAS",
                                    color = GoldPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = CyanNeon.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "FIRESTORE CLOUD",
                                        color = CyanNeon,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Registro de Invitados & Bitácora",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Botón de sincronización con Firestore
                    IconButton(
                        onClick = {
                            if (isSyncing) return@IconButton
                            isSyncing = true
                            scope.launch {
                                val res = repository.syncFromFirestore(condominiumId)
                                isSyncing = false
                                if (res.isSuccess) {
                                    val count = res.getOrDefault(0)
                                    syncMessage = "Firestore: $count actualizados"
                                    Toast.makeText(context, "✅ Sincronizado con Firestore ($count logs)", Toast.LENGTH_SHORT).show()
                                } else {
                                    syncMessage = "Modo Local Autónomo (Room)"
                                    Toast.makeText(context, "Sincronización local completada", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.testTag("btn_sync_firestore")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = CyanNeon,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = "Sincronizar Firestore",
                                tint = CyanNeon
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Fila de contexto y estado de sincronización
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NavyDark,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Home, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$condominiumId • $userUnit",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = syncMessage,
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // KPI Counter Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    KpiStatChip(
                        title = "TOTAL",
                        value = totalCount.toString(),
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    KpiStatChip(
                        title = "DENTRO",
                        value = insideCount.toString(),
                        color = SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                    KpiStatChip(
                        title = "PRE-REG.",
                        value = pendingCount.toString(),
                        color = WarningOrange,
                        modifier = Modifier.weight(1f)
                    )
                    KpiStatChip(
                        title = "SALIDAS",
                        value = departedCount.toString(),
                        color = CyanNeon,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Acciones Principales para Residentes: Generar Token Temporal o Registrar Visita
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showGenerateTimedTokenDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("btn_generate_timed_qr_pass")
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generar Pase QR con Token Temporal",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = NavyDark.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "TOKEN ÚNICO",
                                color = NavyDark,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showRegisterModal = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("btn_open_register_guest")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Registrar Ingreso Manual en Garita",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // BUSCADOR EN VIVO
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar por nombre, casa, RUT, patente o folio...", fontSize = 12.sp, color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = TextMuted, modifier = Modifier.size(16.dp))
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_visitor_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // CHIPS DE FILTRO DE ESTADO
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                "TODOS" to "filter_chip_all",
                "PASES TEMPORALES" to "filter_chip_timed_tokens",
                "EN CONDOMINIO" to "filter_chip_inside",
                "PRE-REGISTRADOS" to "filter_chip_preregistered",
                "SALIDAS" to "filter_chip_departed"
            ).forEach { (filterText, tag) ->
                val selected = selectedFilter == filterText
                FilterChip(
                    selected = selected,
                    onClick = { selectedFilter = filterText },
                    label = { Text(filterText, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GoldPrimary,
                        selectedLabelColor = NavyDark,
                        containerColor = NavySurface,
                        labelColor = TextMuted
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (selected) GoldPrimary else Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.testTag(tag)
                )
            }
        }
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (selected) GoldPrimary else Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.testTag(tag)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // LISTA DEL HISTORIAL DE VISITAS
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No hay visitas que coincidan con la búsqueda" else "Sin visitas registradas",
                        color = TextMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredList, key = { it.folio }) { entry ->
                    val pass = qrPassMap[entry.passCode]
                    VisitorItemCard(
                        entry = entry,
                        qrPass = pass,
                        onCheckIn = {
                            scope.launch {
                                repository.registerCheckInEntry(entry.id, notes = "Ingreso registrado por residente/garita")
                                ResidentNotificationManager.notifyCustomVisitorEntry(
                                    context = context,
                                    guestName = entry.visitorName,
                                    destinationHouse = entry.destinationHouse,
                                    hostResidentName = entry.hostResidentName,
                                    passTypeLabel = entry.passTypeLabel,
                                    vehiclePlate = entry.vehiclePlate
                                )
                                Toast.makeText(context, "✅ Ingreso registrado y sincronizado en Firestore", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onCheckOut = {
                            scope.launch {
                                repository.registerCheckOut(entry.id, notes = "Salida confirmada en sistema")
                                Toast.makeText(context, "👋 Salida registrada en Firestore", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onShowQr = {
                            scope.launch {
                                val resolvedPass = pass ?: withContext(Dispatchers.IO) { db.qrPassDao().getPassByCode(entry.passCode) }
                                if (resolvedPass != null) {
                                    val durationH = ((resolvedPass.validUntilMillis - resolvedPass.createdAtMillis) / (3600 * 1000L)).toInt().coerceAtLeast(1)
                                    activeTimedTokenModal = VisitorAccessTokenInfo(
                                        tokenId = "TOK-${entry.folio.takeLast(6)}-${resolvedPass.passCode.takeLast(4)}",
                                        passCode = resolvedPass.passCode,
                                        folio = entry.folio,
                                        visitorName = resolvedPass.guestName,
                                        visitorDocument = resolvedPass.guestDocument,
                                        destinationHouse = resolvedPass.destinationHouse,
                                        hostResidentName = resolvedPass.hostResidentName,
                                        passTypeLabel = entry.passTypeLabel,
                                        vehiclePlate = resolvedPass.vehiclePlate,
                                        issuedAtMillis = resolvedPass.createdAtMillis,
                                        validUntilMillis = resolvedPass.validUntilMillis,
                                        durationHours = durationH,
                                        maxEntries = resolvedPass.maxEntries,
                                        currentEntriesCount = resolvedPass.currentEntriesCount,
                                        integrityHash = resolvedPass.integrityHash,
                                        residentNotes = resolvedPass.note,
                                        isActive = resolvedPass.isActive
                                    )
                                } else {
                                    val now = System.currentTimeMillis()
                                    val validUntil = now + (24 * 3600 * 1000L)
                                    val hash = AlphaCoreEngine.computeIntegrityHash(entry.passCode, entry.visitorDocument, entry.destinationHouse)
                                    val newPass = QrPassRoomEntity(
                                        passCode = entry.passCode,
                                        guestName = entry.visitorName,
                                        guestDocument = entry.visitorDocument,
                                        destinationHouse = entry.destinationHouse,
                                        hostResidentName = entry.hostResidentName,
                                        vehiclePlate = entry.vehiclePlate,
                                        passType = PassType.VISITOR_SINGLE,
                                        validUntilMillis = validUntil,
                                        maxEntries = 1,
                                        currentEntriesCount = 0,
                                        note = "Pase QR temporal generado para ${entry.visitorName}",
                                        createdAtMillis = now,
                                        integrityHash = hash,
                                        isActive = true
                                    )
                                    withContext(Dispatchers.IO) { db.qrPassDao().insertPass(newPass) }
                                    activeTimedTokenModal = VisitorAccessTokenInfo(
                                        tokenId = "TOK-${entry.folio.takeLast(6)}-${entry.passCode.takeLast(4)}",
                                        passCode = entry.passCode,
                                        folio = entry.folio,
                                        visitorName = entry.visitorName,
                                        visitorDocument = entry.visitorDocument,
                                        destinationHouse = entry.destinationHouse,
                                        hostResidentName = entry.hostResidentName,
                                        passTypeLabel = entry.passTypeLabel,
                                        vehiclePlate = entry.vehiclePlate,
                                        issuedAtMillis = now,
                                        validUntilMillis = validUntil,
                                        durationHours = 24,
                                        maxEntries = 1,
                                        currentEntriesCount = 0,
                                        integrityHash = hash,
                                        residentNotes = entry.residentNotes,
                                        isActive = true
                                    )
                                }
                            }
                        },
                        onShowDetail = { detailEntryToShow = entry }
                    )
                }
            }
        }
    }

    // MODAL DE GENERACIÓN DE PASE QR CON TOKEN TEMPORAL
    if (showGenerateTimedTokenDialog) {
        GenerateTimedTokenDialog(
            db = db,
            condominiumId = condominiumId,
            defaultUnit = userUnit,
            defaultHost = userName,
            onDismiss = { showGenerateTimedTokenDialog = false },
            onTokenCreated = { tokenInfo ->
                showGenerateTimedTokenDialog = false
                activeTimedTokenModal = tokenInfo
            }
        )
    }

    // MODAL DE REGISTRO MANUAL DE INVITADO EN CASETA
    if (showRegisterModal) {
        GuestRegistrationDialog(
            condominiumId = condominiumId,
            defaultUnit = userUnit,
            defaultHost = userName,
            onDismiss = { showRegisterModal = false },
            onGuestRegistered = { newEntry ->
                showRegisterModal = false
                qrPassToShow = newEntry
                Toast.makeText(context, "🎉 Invitado registrado exitosamente en Firestore", Toast.LENGTH_LONG).show()
            },
            repository = repository,
            db = db
        )
    }

    // MODAL DE PASE QR CON TOKEN TEMPORAL EN VIVO
    activeTimedTokenModal?.let { tokenInfo ->
        TimeLimitedDigitalPassModal(
            tokenInfo = tokenInfo,
            db = db,
            onDismiss = { activeTimedTokenModal = null }
        )
    }

    // MODAL DE PASE QR DIGITAL
    qrPassToShow?.let { entry ->
        DigitalPassModal(
            entry = entry,
            onDismiss = { qrPassToShow = null }
        )
    }

    // MODAL DE DETALLE COMPLETO Y AUDITORÍA FIRESTORE
    detailEntryToShow?.let { entry ->
        VisitorDetailModal(
            entry = entry,
            condominiumId = condominiumId,
            onDismiss = { detailEntryToShow = null }
        )
    }
}

/**
 * Tarjeta individual de visita en la bitácora histórica.
 */
@Composable
private fun VisitorItemCard(
    entry: VisitorCheckIn,
    qrPass: QrPassRoomEntity? = null,
    onCheckIn: () -> Unit,
    onCheckOut: () -> Unit,
    onShowQr: () -> Unit,
    onShowDetail: () -> Unit
) {
    val isInside = entry.status == "CHECKED_IN" || entry.status == "VERIFICADO"
    val isPending = entry.status == "PRE_REGISTRADO" || entry.status == "PENDIENTE"
    val isDeparted = entry.status == "DEPARTED"

    val statusColor = when {
        isInside -> SuccessGreen
        isPending -> WarningOrange
        isDeparted -> TextMuted
        else -> ErrorRed
    }

    val statusLabel = when {
        isInside -> "EN CONDOMINIO"
        isPending -> "PRE-REGISTRADO"
        isDeparted -> "SALIDA REGISTRADA"
        else -> entry.status
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("visitor_card_${entry.folio}"),
        shape = RoundedCornerShape(12.dp),
        color = NavyDark,
        border = BorderStroke(1.dp, if (isInside) SuccessGreen.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Cabecera de la tarjeta: Estado, Badge de Token Temporal y Folio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusColor.copy(alpha = 0.2f),
                        border = BorderStroke(0.5.dp, statusColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(statusColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = statusLabel,
                                color = statusColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (qrPass != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        TimedTokenStatusBadge(validUntilMillis = qrPass.validUntilMillis)
                    }
                }

                Text(
                    text = "Folio: ${entry.folio}",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Datos del Visitante y Destino
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.visitorName,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Doc: ${entry.visitorDocument} • Tipo: ${entry.passTypeLabel}",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                    if (!entry.vehiclePlate.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Patente: ${entry.vehiclePlate}",
                                color = CyanNeon,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NavySurface,
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Home, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = entry.destinationHouse,
                                color = GoldPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "Anfitrión: ${entry.hostResidentName}",
                        color = TextMuted,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tiempos y Permanencia
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = entry.formattedTime,
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }

                Text(
                    text = "Permanencia: ${entry.durationStayFormatted}",
                    color = if (isInside) SuccessGreen else TextMuted,
                    fontSize = 10.sp,
                    fontWeight = if (isInside) FontWeight.Bold else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Botones de acción contextuales
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Si está pre-registrado: botón Check-In
                if (isPending) {
                    Button(
                        onClick = onCheckIn,
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = NavyDark),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_checkin_entry_${entry.id}")
                    ) {
                        Icon(Icons.Default.HowToReg, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Check-In Ahora", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Si está dentro: botón Check-Out
                if (isInside) {
                    Button(
                        onClick = onCheckOut,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon.copy(alpha = 0.2f), contentColor = CyanNeon),
                        border = BorderStroke(1.dp, CyanNeon),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("btn_checkout_entry_${entry.id}")
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Registrar Salida", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Botón Ver Pase QR
                OutlinedButton(
                    onClick = onShowQr,
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("btn_view_qr_${entry.id}")
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pase QR", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Botón Ver Detalle
                IconButton(
                    onClick = onShowDetail,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Notes, contentDescription = "Ver Detalles", tint = TextMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * Diálogo interactivo para que los residentes registren visitas y las persistan en Firestore.
 */
@Composable
private fun GuestRegistrationDialog(
    condominiumId: String,
    defaultUnit: String,
    defaultHost: String,
    onDismiss: () -> Unit,
    onGuestRegistered: (VisitorCheckIn) -> Unit,
    repository: VisitorCheckInRepository,
    db: AppDatabase
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var visitorName by remember { mutableStateOf("") }
    var visitorDocument by remember { mutableStateOf("") }
    var authorizedUnit by remember { mutableStateOf(defaultUnit) }
    var hostResidentName by remember { mutableStateOf(defaultHost) }
    var selectedPassType by remember { mutableStateOf("Visita Familiar") }
    var durationHours by remember { mutableIntStateOf(4) }
    var isVehicular by remember { mutableStateOf(false) }
    var vehiclePlate by remember { mutableStateOf("") }
    var isImmediateCheckIn by remember { mutableStateOf(false) }
    var residentNotes by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp)
                .testTag("guest_registration_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = NavySurface,
            border = BorderStroke(1.5.dp, GoldPrimary)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header del Formulario
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(GoldPrimary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "REGISTRAR INVITADO",
                                color = GoldPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Guardar en Firestore & Generar Pase",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Formulario con Scroll
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Aviso de persistencia Firestore
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NavyDark,
                        border = BorderStroke(0.5.dp, CyanNeon.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Este registro se guardará en Firestore (/condominiums/$condominiumId/visitor_logs) con timestamp nativo.",
                                color = CyanNeon,
                                fontSize = 10.sp
                            )
                        }
                    }

                    // Nombre Completo del Invitado
                    OutlinedTextField(
                        value = visitorName,
                        onValueChange = { visitorName = it },
                        label = { Text("Nombre Completo del Invitado *") },
                        placeholder = { Text("Ej: María José Valencia") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = GoldPrimary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("guest_name_input")
                    )

                    // Cédula / RUT / Documento
                    OutlinedTextField(
                        value = visitorDocument,
                        onValueChange = { visitorDocument = it },
                        label = { Text("Documento de Identidad / RUT (Opcional)") },
                        placeholder = { Text("Ej: 19.345.678-9") },
                        leadingIcon = { Icon(Icons.Default.AssignmentInd, contentDescription = null, tint = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("guest_doc_input")
                    )

                    // Fila: Unidad Autorizada & Residente Anfitrión
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = authorizedUnit,
                            onValueChange = { authorizedUnit = it },
                            label = { Text("Unidad Autorizada *") },
                            leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = GoldPrimary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("guest_unit_input")
                        )

                        OutlinedTextField(
                            value = hostResidentName,
                            onValueChange = { hostResidentName = it },
                            label = { Text("Anfitrión *") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("guest_host_input")
                        )
                    }

                    // Tipo de Pase / Visita
                    Column {
                        Text(
                            text = "Tipo de Visita",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("Visita Familiar", "Amigos", "Delivery", "Servicio Técnico", "Contratista").forEach { type ->
                                val selected = selectedPassType == type
                                FilterChip(
                                    selected = selected,
                                    onClick = { selectedPassType = type },
                                    label = { Text(type, fontSize = 9.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = GoldPrimary,
                                        selectedLabelColor = NavyDark,
                                        containerColor = NavyDark,
                                        labelColor = TextMuted
                                    )
                                )
                            }
                        }
                    }

                    // Acceso Vehicular Toggle
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NavyDark,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = if (isVehicular) CyanNeon else TextMuted)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Ingreso en Vehículo",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (isVehicular) "Requiere registrar patente vehicular" else "Ingreso peatonal estándar",
                                        color = TextMuted,
                                        fontSize = 9.sp
                                    )
                                }
                            }

                            Switch(
                                checked = isVehicular,
                                onCheckedChange = { isVehicular = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CyanNeon,
                                    checkedTrackColor = CyanNeon.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }

                    if (isVehicular) {
                        OutlinedTextField(
                            value = vehiclePlate,
                            onValueChange = { vehiclePlate = it.uppercase() },
                            label = { Text("Patente / Placa del Vehículo *") },
                            placeholder = { Text("Ej: KXYZ-98") },
                            leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = CyanNeon) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanNeon,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("guest_plate_input")
                        )
                    }

                    // Selector de Modo: Check-In Inmediato vs Pre-registro
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = NavyDark,
                        border = BorderStroke(1.dp, if (isImmediateCheckIn) SuccessGreen.copy(alpha = 0.5f) else GoldPrimary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isImmediateCheckIn) "Check-In Inmediato (Ingreso Ahora)" else "Pre-Registro (Visita Futura)",
                                    color = if (isImmediateCheckIn) SuccessGreen else GoldPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isImmediateCheckIn) "Registra la entrada ahora mismo con timestamp de Firestore" else "Genera código QR para cuando el invitado llegue a garita",
                                    color = TextMuted,
                                    fontSize = 9.sp
                                )
                            }

                            Switch(
                                checked = isImmediateCheckIn,
                                onCheckedChange = { isImmediateCheckIn = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = SuccessGreen,
                                    checkedTrackColor = SuccessGreen.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }

                    // Notas para Seguridad / Vigilancia
                    OutlinedTextField(
                        value = residentNotes,
                        onValueChange = { residentNotes = it },
                        label = { Text("Instrucciones para Caseta / Guardia (Opcional)") },
                        placeholder = { Text("Ej: Dejar pasar hasta estacionamiento #14...") },
                        leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null, tint = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("guest_notes_input")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Botón Guardar en Firestore
                Button(
                    onClick = {
                        if (visitorName.isBlank() || authorizedUnit.isBlank()) return@Button
                        isSaving = true
                        scope.launch {
                            val result = repository.registerGuestAndLogToFirestore(
                                condominiumId = condominiumId,
                                visitorName = visitorName,
                                authorizedUnitNumber = authorizedUnit,
                                hostResidentName = hostResidentName,
                                visitorDocument = visitorDocument.ifBlank { "Sin Documento" },
                                passTypeLabel = "$selectedPassType (Token ${durationHours}h)",
                                vehiclePlate = if (isVehicular) vehiclePlate else null,
                                residentNotes = residentNotes.ifBlank { null },
                                isImmediateCheckIn = isImmediateCheckIn
                            )
                            isSaving = false
                            result.onSuccess { saved ->
                                val now = System.currentTimeMillis()
                                val validUntil = now + (durationHours.toLong() * 3600 * 1000L)
                                val hash = AlphaCoreEngine.computeIntegrityHash(saved.passCode, saved.visitorDocument, saved.destinationHouse)
                                val qrPass = QrPassRoomEntity(
                                    passCode = saved.passCode,
                                    guestName = saved.visitorName,
                                    guestDocument = saved.visitorDocument,
                                    destinationHouse = saved.destinationHouse,
                                    hostResidentName = saved.hostResidentName,
                                    vehiclePlate = saved.vehiclePlate,
                                    passType = PassType.VISITOR_SINGLE,
                                    validUntilMillis = validUntil,
                                    maxEntries = 1,
                                    currentEntriesCount = 0,
                                    note = "Token temporal (${durationHours}h). ${saved.residentNotes ?: ""}".trim(),
                                    createdAtMillis = now,
                                    integrityHash = hash,
                                    isActive = true
                                )
                                withContext(Dispatchers.IO) {
                                    db.qrPassDao().insertPass(qrPass)
                                }
                                onGuestRegistered(saved)
                            }.onFailure { err ->
                                Toast.makeText(context, "Error: ${err.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = visitorName.isNotBlank() && authorizedUnit.isNotBlank() && (!isVehicular || vehiclePlate.isNotBlank()) && !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_submit_guest_registration")
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NavyDark, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Guardando en Firestore...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isImmediateCheckIn) "Registrar e Ingresar Inmediatamente" else "Guardar y Generar Pase QR",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modal visual con el Pase QR generado y opciones para compartir con el invitado.
 */
@Composable
private fun DigitalPassModal(
    entry: VisitorCheckIn,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val qrBitmap = remember(entry.passCode) {
        generateQrBitmap(entry.passCode, 480)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("digital_pass_modal"),
            shape = RoundedCornerShape(20.dp),
            color = NavySurface,
            border = BorderStroke(1.5.dp, GoldPrimary)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCode, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PASE DIGITAL DE ACCESO", color = GoldPrimary, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Contenedor QR con fondo blanco de alto contraste
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    modifier = Modifier.size(220.dp),
                    border = BorderStroke(2.dp, GoldPrimary)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Código QR de Acceso",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )
                        } else {
                            CircularProgressIndicator(color = NavyDark)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = entry.visitorName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Autorizado para: ${entry.destinationHouse}",
                    color = GoldPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
                Text(
                    text = "Código: ${entry.passCode} • Folio: ${entry.folio}",
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Botón Compartir con el invitado
                Button(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Hola ${entry.visitorName}, aquí tienes tu pase de acceso para ${entry.destinationHouse}.\nCódigo: ${entry.passCode}\nFolio: ${entry.folio}\nPresenta este código al ingresar a la caseta de seguridad."
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Compartir Pase de Acceso"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Compartir Invitación con el Visitante", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Modal detallado de auditoría que muestra la ruta y datos del documento en Firestore.
 */
@Composable
private fun VisitorDetailModal(
    entry: VisitorCheckIn,
    condominiumId: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(18.dp),
            color = NavySurface,
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("DETALLES DE BITÁCORA FIRESTORE", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = NavyDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Ruta Firestore: /condominiums/$condominiumId/visitor_logs/${entry.folio}",
                            color = CyanNeon,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(text = "Visitante: ${entry.visitorName}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Documento: ${entry.visitorDocument}", color = TextMuted, fontSize = 11.sp)
                        Text(text = "Unidad Autorizada: ${entry.destinationHouse}", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "Anfitrión: ${entry.hostResidentName}", color = TextMuted, fontSize = 11.sp)
                        Text(text = "Tipo: ${entry.passTypeLabel}", color = TextMuted, fontSize = 11.sp)
                        if (!entry.vehiclePlate.isNullOrBlank()) {
                            Text(text = "Patente: ${entry.vehiclePlate}", color = CyanNeon, fontSize = 11.sp)
                        }
                        Text(text = "Ingreso: ${entry.formattedTime}", color = TextMuted, fontSize = 10.sp)
                        entry.formattedCheckOutTime?.let {
                            Text(text = "Salida: $it", color = TextMuted, fontSize = 10.sp)
                        }
                        Text(text = "Permanencia: ${entry.durationStayFormatted}", color = SuccessGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (!entry.residentNotes.isNullOrBlank()) {
                            Text(text = "Instrucciones de Residente: ${entry.residentNotes}", color = TextMuted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Chip de KPI estadístico.
 */
@Composable
private fun KpiStatChip(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = NavyDark,
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}

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
