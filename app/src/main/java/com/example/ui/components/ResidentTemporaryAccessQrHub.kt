package com.example.ui.components

import android.content.Context
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
import com.example.data.booking.AppDatabase
import com.example.data.passes.QrPassRoomEntity
import com.example.scanner.PassType
import com.example.ui.theme.*
import com.example.utils.ResidentQrCodeUtility
import com.example.utils.TemporaryAccessPass
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * HUB DE GENERACIÓN DE CÓDIGOS QR Y ACCESOS TEMPORALES PARA RESIDENTES.
 *
 * Esta utilidad permite a los residentes crear códigos de acceso y pases QR temporales
 * para visitantes, los cuales son guardados directamente en Cloud Firestore
 * en la subcolección multi-inquilino /condominiums/{condominiumId}/qr_passes/
 * y en Room SQLite para redundancia local offline.
 */
@Composable
fun ResidentTemporaryAccessQrHubDialog(
    db: AppDatabase,
    condominiumId: String,
    condominiumName: String = "Residencial Los Prados",
    residentUnit: String,
    residentName: String,
    residentUid: String? = null,
    onDismiss: () -> Unit,
    onSimulateScanInCaseta: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Generar, 1: Historial / Pases Activos

    // Estado del formulario de creación
    var visitorName by remember { mutableStateOf("") }
    var visitorDocument by remember { mutableStateOf("") }
    var destinationUnit by remember { mutableStateOf(residentUnit) }
    var hostResidentName by remember { mutableStateOf(residentName) }
    var selectedCategory by remember { mutableStateOf("Visita Familiar") }
    var hasVehicle by remember { mutableStateOf(false) }
    var vehiclePlate by remember { mutableStateOf("") }
    var durationHours by remember { mutableIntStateOf(4) }
    var isSingleEntry by remember { mutableStateOf(true) }
    var residentNotes by remember { mutableStateOf("") }

    var isGenerating by remember { mutableStateOf(false) }
    var generatedPassResult by remember { mutableStateOf<TemporaryAccessPass?>(null) }
    var generatedQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var passToRevoke by remember { mutableStateOf<QrPassRoomEntity?>(null) }
    var selectedPassForQrModal by remember { mutableStateOf<QrPassRoomEntity?>(null) }

    // Pases del residente en tiempo real desde Room
    val passesList by db.qrPassDao().getAllPassesFlow().collectAsState(initial = emptyList())
    val residentPasses = remember(passesList, residentUnit) {
        passesList.filter {
            it.destinationHouse.contains(residentUnit, ignoreCase = true) ||
                    it.hostResidentName.contains(residentName, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .testTag("resident_temporary_access_qr_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = NavyDark,
            border = BorderStroke(1.5.dp, GoldPrimary)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Cabecera Principal
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = GoldPrimary.copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.QrCode2,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Pases QR de Acceso Temporal",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Casa: $residentUnit",
                                    color = CyanNeon,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SuccessGreen.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "Cloud Firestore Activo",
                                        color = SuccessGreen,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_qr_hub_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Selector de Pestañas (Generar vs Códigos Activos)
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = NavySurface,
                    contentColor = GoldPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AddCircleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Generar Pase", fontWeight = FontWeight.Bold)
                            }
                        },
                        modifier = Modifier.testTag("tab_generate_qr")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Mis Pases (${residentPasses.size})", fontWeight = FontWeight.Bold)
                            }
                        },
                        modifier = Modifier.testTag("tab_active_passes")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Vista de Generación de Pase o Historial
                if (generatedPassResult != null) {
                    // Pantalla de Pase QR Generado Exitosamente
                    PassGenerationSuccessView(
                        pass = generatedPassResult!!,
                        qrBitmap = generatedQrBitmap,
                        condominiumName = condominiumName,
                        onNewPass = {
                            generatedPassResult = null
                            generatedQrBitmap = null
                            visitorName = ""
                            visitorDocument = ""
                            vehiclePlate = ""
                            residentNotes = ""
                        },
                        onSimulateScan = { code ->
                            onSimulateScanInCaseta(code)
                            onDismiss()
                        }
                    )
                } else if (selectedTab == 0) {
                    // Formulario de Generación de Pase Temporal
                    CreateTemporaryPassForm(
                        visitorName = visitorName,
                        onVisitorNameChange = { visitorName = it },
                        visitorDocument = visitorDocument,
                        onVisitorDocumentChange = { visitorDocument = it },
                        destinationUnit = destinationUnit,
                        hostResidentName = hostResidentName,
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it },
                        hasVehicle = hasVehicle,
                        onHasVehicleChange = { hasVehicle = it },
                        vehiclePlate = vehiclePlate,
                        onVehiclePlateChange = { vehiclePlate = it },
                        durationHours = durationHours,
                        onDurationHoursChange = { durationHours = it },
                        isSingleEntry = isSingleEntry,
                        onIsSingleEntryChange = { isSingleEntry = it },
                        residentNotes = residentNotes,
                        onResidentNotesChange = { residentNotes = it },
                        isGenerating = isGenerating,
                        onGenerateClick = {
                            if (visitorName.isBlank()) {
                                Toast.makeText(context, "Ingresa el nombre del visitante.", Toast.LENGTH_SHORT).show()
                                return@CreateTemporaryPassForm
                            }
                            isGenerating = true
                            val passType = when (selectedCategory) {
                                "Delivery / Envíos" -> PassType.DELIVERY_SERVICE
                                "Técnico / Servicios" -> PassType.DELIVERY_SERVICE
                                "Evento / Fiesta" -> PassType.EVENT_GUEST
                                else -> if (isSingleEntry) PassType.VISITOR_SINGLE else PassType.RESIDENT_PERMANENT
                            }
                            val maxEntries = if (isSingleEntry) 1 else 99

                            scope.launch {
                                val result = ResidentQrCodeUtility.createTemporaryAccessPass(
                                    context = context,
                                    db = db,
                                    condominiumId = condominiumId,
                                    visitorName = visitorName,
                                    visitorDocument = visitorDocument,
                                    destinationUnit = destinationUnit,
                                    hostResidentName = hostResidentName,
                                    passType = passType,
                                    vehiclePlate = if (hasVehicle) vehiclePlate else null,
                                    durationHours = durationHours,
                                    maxEntries = maxEntries,
                                    notes = residentNotes,
                                    residentUid = residentUid
                                )

                                val bitmap = ResidentQrCodeUtility.generateQrBitmap(result.passCode, 512)
                                generatedQrBitmap = bitmap
                                generatedPassResult = result
                                isGenerating = false
                                Toast.makeText(
                                    context,
                                    "✅ Pase temporal generado y guardado en Firestore.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                } else {
                    // Lista de Pases Activos del Residente
                    ActivePassesListView(
                        passes = residentPasses,
                        onViewQr = { selectedPassForQrModal = it },
                        onSharePass = { pass ->
                            val tempPass = TemporaryAccessPass(
                                passCode = pass.passCode,
                                folio = pass.passCode,
                                visitorName = pass.guestName,
                                visitorDocument = pass.guestDocument,
                                destinationUnit = pass.destinationHouse,
                                hostResidentName = pass.hostResidentName,
                                passType = pass.passType,
                                vehiclePlate = pass.vehiclePlate,
                                durationHours = ((pass.validUntilMillis - pass.createdAtMillis) / (3600 * 1000L)).toInt().coerceAtLeast(1),
                                issuedAtMillis = pass.createdAtMillis,
                                validUntilMillis = pass.validUntilMillis,
                                maxEntries = pass.maxEntries,
                                currentEntriesCount = pass.currentEntriesCount,
                                notes = pass.note,
                                integrityHash = pass.integrityHash,
                                isActive = pass.isActive,
                                firestoreDocumentPath = "/condominiums/$condominiumId/qr_passes/${pass.passCode}",
                                savedToFirestore = true
                            )
                            ResidentQrCodeUtility.shareAccessPass(context, tempPass, condominiumName)
                        },
                        onRevokeClick = { passToRevoke = it }
                    )
                }
            }
        }
    }

    // Modal de Confirmación de Revocación
    passToRevoke?.let { pass ->
        AlertDialog(
            onDismissRequest = { passToRevoke = null },
            title = { Text("¿Revocar Acceso Temporal?", fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Text(
                    "El código ${pass.passCode} para ${pass.guestName} será desactivado de inmediato tanto en el dispositivo como en Firebase Firestore. La caseta de vigilancia denegará el ingreso.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            ResidentQrCodeUtility.revokeAccessPass(
                                context = context,
                                db = db,
                                condominiumId = condominiumId,
                                passCode = pass.passCode
                            )
                            passToRevoke = null
                            Toast.makeText(context, "Pase ${pass.passCode} revocado.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White)
                ) {
                    Text("Revocar Acceso")
                }
            },
            dismissButton = {
                TextButton(onClick = { passToRevoke = null }) {
                    Text("Cancelar", color = TextMuted)
                }
            },
            containerColor = NavyCard
        )
    }

    // Modal de Visualización de QR para Pases Existentes
    selectedPassForQrModal?.let { pass ->
        val bmp = remember(pass.passCode) { ResidentQrCodeUtility.generateQrBitmap(pass.passCode, 512) }
        Dialog(onDismissRequest = { selectedPassForQrModal = null }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                color = NavySurface,
                border = BorderStroke(1.5.dp, GoldPrimary)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pase QR: ${pass.guestName}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        IconButton(onClick = { selectedPassForQrModal = null }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = TextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.size(200.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(2.dp, CyanNeon)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            bmp?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "QR Code",
                                    modifier = Modifier.size(170.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = pass.passCode,
                        color = GoldPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Válido hasta: ${pass.formattedValidUntil}",
                        color = TextMuted,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                ResidentQrCodeUtility.copyToClipboard(context, pass.passCode)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon),
                            border = BorderStroke(1.dp, CyanNeon)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copiar", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                val tempPass = TemporaryAccessPass(
                                    passCode = pass.passCode,
                                    folio = pass.passCode,
                                    visitorName = pass.guestName,
                                    visitorDocument = pass.guestDocument,
                                    destinationUnit = pass.destinationHouse,
                                    hostResidentName = pass.hostResidentName,
                                    passType = pass.passType,
                                    vehiclePlate = pass.vehiclePlate,
                                    durationHours = 24,
                                    issuedAtMillis = pass.createdAtMillis,
                                    validUntilMillis = pass.validUntilMillis,
                                    maxEntries = pass.maxEntries,
                                    currentEntriesCount = pass.currentEntriesCount,
                                    notes = pass.note,
                                    integrityHash = pass.integrityHash,
                                    isActive = pass.isActive,
                                    firestoreDocumentPath = "/condominiums/$condominiumId/qr_passes/${pass.passCode}",
                                    savedToFirestore = true
                                )
                                ResidentQrCodeUtility.shareAccessPass(context, tempPass, condominiumName)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Compartir", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Formulario interactivo para que el residente configure y genere el pase temporal.
 */
@Composable
private fun CreateTemporaryPassForm(
    visitorName: String,
    onVisitorNameChange: (String) -> Unit,
    visitorDocument: String,
    onVisitorDocumentChange: (String) -> Unit,
    destinationUnit: String,
    hostResidentName: String,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    hasVehicle: Boolean,
    onHasVehicleChange: (Boolean) -> Unit,
    vehiclePlate: String,
    onVehiclePlateChange: (String) -> Unit,
    durationHours: Int,
    onDurationHoursChange: (Int) -> Unit,
    isSingleEntry: Boolean,
    onIsSingleEntryChange: (Boolean) -> Unit,
    residentNotes: String,
    onResidentNotesChange: (String) -> Unit,
    isGenerating: Boolean,
    onGenerateClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    val categories = listOf(
        "Visita Familiar",
        "Delivery / Envíos",
        "Técnico / Servicios",
        "Evento / Fiesta"
    )

    val durationPresets = listOf(1, 2, 4, 8, 12, 24, 48, 72)
    val calculatedExpiry = remember(durationHours) {
        val expiryMillis = System.currentTimeMillis() + (durationHours * 3600 * 1000L)
        SimpleDateFormat("EEE dd/MM HH:mm", Locale.getDefault()).format(Date(expiryMillis))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Campo: Nombre del Visitante
        OutlinedTextField(
            value = visitorName,
            onValueChange = onVisitorNameChange,
            label = { Text("Nombre Completo del Visitante *") },
            placeholder = { Text("Ej: Carlos Ramírez Morales") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = GoldPrimary) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("qr_util_visitor_name"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = GoldPrimary,
                unfocusedLabelColor = TextMuted
            )
        )

        // Categoría de Visita
        Text(
            text = "Tipo de Visita:",
            color = TextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { cat ->
                val isSelected = selectedCategory == cat
                Surface(
                    onClick = { onSelectCategory(cat) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) GoldPrimary.copy(alpha = 0.2f) else NavyCard,
                    border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF334155)),
                    modifier = Modifier.height(34.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) GoldPrimary else Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Fila: Documento y Placas
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = visitorDocument,
                onValueChange = onVisitorDocumentChange,
                label = { Text("INE / DNI (Opcional)") },
                placeholder = { Text("Ej: 12345678") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("qr_util_visitor_doc"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanNeon,
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = CyanNeon,
                    unfocusedLabelColor = TextMuted
                )
            )

            OutlinedTextField(
                value = vehiclePlate,
                onValueChange = {
                    onVehiclePlateChange(it.uppercase(Locale.getDefault()))
                    onHasVehicleChange(it.isNotBlank())
                },
                label = { Text("Placas (Opcional)") },
                placeholder = { Text("Ej: ABC-123") },
                leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = CyanNeon) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("qr_util_plate"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanNeon,
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedLabelColor = CyanNeon,
                    unfocusedLabelColor = TextMuted
                )
            )
        }

        // Duración y Expiración del Código
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Duración del Acceso:",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Expira: $calculatedExpiry",
                        color = GoldPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(durationPresets) { hours ->
                        val isSelected = durationHours == hours
                        Surface(
                            onClick = { onDurationHoursChange(hours) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) GoldPrimary else NavyDark,
                            border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color(0xFF334155)),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            ) {
                                Text(
                                    text = "${hours}h",
                                    color = if (isSelected) NavyDark else Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Límite de Entradas (Uso Único vs Múltiple)
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isSingleEntry) "Entrada de Uso Único" else "Entradas Múltiples",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isSingleEntry) "El código caduca tras el primer escaneo en caseta" else "Permite reingresos ilimitados dentro de la vigencia",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
                Switch(
                    checked = isSingleEntry,
                    onCheckedChange = onIsSingleEntryChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GoldPrimary,
                        checkedTrackColor = GoldPrimary.copy(alpha = 0.3f),
                        uncheckedThumbColor = CyanNeon,
                        uncheckedTrackColor = CyanNeon.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.testTag("switch_single_entry")
                )
            }
        }

        // Notas para el Oficial de Caseta
        OutlinedTextField(
            value = residentNotes,
            onValueChange = onResidentNotesChange,
            label = { Text("Instrucciones para Vigilancia (Opcional)") },
            placeholder = { Text("Ej: Dejar pasar hasta la puerta de la casa") },
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("qr_util_notes"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GoldPrimary,
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedLabelColor = GoldPrimary,
                unfocusedLabelColor = TextMuted
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Botón Principal: Generar y Guardar en Firestore
        Button(
            onClick = onGenerateClick,
            enabled = !isGenerating && visitorName.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GoldPrimary,
                contentColor = NavyDark,
                disabledContainerColor = Color.Gray.copy(alpha = 0.3f),
                disabledContentColor = Color.White.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("btn_generate_and_save_firestore")
        ) {
            if (isGenerating) {
                CircularProgressIndicator(color = NavyDark, modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Sincronizando con Firestore...", fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generar Pase QR y Guardar en Firestore", fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
        }
    }
}

/**
 * Pantalla mostrada una vez generado el pase, con el QR grande, opciones de compartir y guardar.
 */
@Composable
private fun PassGenerationSuccessView(
    pass: TemporaryAccessPass,
    qrBitmap: Bitmap?,
    condominiumName: String,
    onNewPass: () -> Unit,
    onSimulateScan: (String) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Badge de Éxito y Sincronización en la Nube
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = SuccessGreen.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.CloudDone, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Guardado en Firebase Firestore",
                    color = SuccessGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Contenedor del Código QR de Alta Visibilidad
        Card(
            modifier = Modifier
                .size(210.dp)
                .padding(4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(3.dp, GoldPrimary)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Pase QR Generado",
                        modifier = Modifier.size(185.dp)
                    )
                } else {
                    CircularProgressIndicator(color = GoldPrimary)
                }
            }
        }

        // Código en formato de texto Monoespacio
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = NavySurface,
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
            modifier = Modifier.clickable {
                ResidentQrCodeUtility.copyToClipboard(context, pass.passCode)
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = pass.passCode,
                    color = GoldPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
            }
        }

        // Ficha Informativa del Visitante
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Visitante:", color = TextMuted, fontSize = 11.sp)
                    Text(pass.visitorName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Destino:", color = TextMuted, fontSize = 11.sp)
                    Text(pass.destinationUnit, color = CyanNeon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Vigencia:", color = TextMuted, fontSize = 11.sp)
                    Text(pass.formattedValidUntil, color = GoldPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                if (!pass.vehiclePlate.isNullOrBlank()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Placas:", color = TextMuted, fontSize = 11.sp)
                        Text(pass.vehiclePlate, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Ruta Firestore:", color = TextMuted, fontSize = 10.sp)
                    Text(pass.firestoreDocumentPath, color = TextMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // Botones de Acción
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    ResidentQrCodeUtility.shareAccessPass(context, pass, condominiumName)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .testTag("btn_share_generated_pass"),
                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Compartir", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    onSimulateScan(pass.passCode)
                },
                modifier = Modifier
                    .weight(1.2f)
                    .height(46.dp)
                    .testTag("btn_simulate_caseta_scan"),
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Probar en Caseta", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        OutlinedButton(
            onClick = onNewPass,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .testTag("btn_create_another_pass"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Generar Otro Pase", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

/**
 * Pestaña con la lista y estado en tiempo real de los pases creados por el residente.
 */
@Composable
private fun ActivePassesListView(
    passes: List<QrPassRoomEntity>,
    onViewQr: (QrPassRoomEntity) -> Unit,
    onSharePass: (QrPassRoomEntity) -> Unit,
    onRevokeClick: (QrPassRoomEntity) -> Unit
) {
    if (passes.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.QrCode2,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Aún no has generado pases de acceso.",
                    color = TextMuted,
                    fontSize = 13.sp
                )
                Text(
                    text = "Ve a la pestaña 'Generar Pase' para crear tu primer código.",
                    color = TextMuted.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(passes, key = { it.passCode }) { pass ->
                ResidentPassItemCard(
                    pass = pass,
                    onViewQr = { onViewQr(pass) },
                    onShare = { onSharePass(pass) },
                    onRevoke = { onRevokeClick(pass) }
                )
            }
        }
    }
}

/**
 * Tarjeta individual de pase temporal con temporizador y acciones directas.
 */
@Composable
private fun ResidentPassItemCard(
    pass: QrPassRoomEntity,
    onViewQr: () -> Unit,
    onShare: () -> Unit,
    onRevoke: () -> Unit
) {
    val now = System.currentTimeMillis()
    val isExpired = now > pass.validUntilMillis
    val remainingMillis = (pass.validUntilMillis - now).coerceAtLeast(0L)
    val remainingHours = remainingMillis / (3600 * 1000)

    val (badgeText, badgeColor) = when {
        !pass.isActive -> Pair("REVOCADO", ErrorRed)
        isExpired -> Pair("EXPIRADO", Color(0xFFEF4444))
        remainingHours <= 2 -> Pair("< 2H RESTANTES", WarningOrange)
        else -> Pair("ACTIVO (${remainingHours}h)", SuccessGreen)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, if (pass.isActive && !isExpired) GoldPrimary.copy(alpha = 0.35f) else Color(0xFF334155)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pass.guestName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${pass.passType.label} • ${pass.destinationHouse}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Código: ${pass.passCode}",
                    color = GoldPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Vence: ${pass.formattedValidUntil}",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = onViewQr,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon),
                    border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ver QR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onShare,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Compartir", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                if (pass.isActive && !isExpired) {
                    IconButton(
                        onClick = onRevoke,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            Icons.Default.Cancel,
                            contentDescription = "Revocar",
                            tint = ErrorRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
