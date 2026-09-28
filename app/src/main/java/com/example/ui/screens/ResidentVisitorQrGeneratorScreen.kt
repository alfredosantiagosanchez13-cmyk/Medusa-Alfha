package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.example.data.auth.MedusaAreaIsolationGuard
import com.example.data.auth.MedusaRole
import com.example.data.auth.MedusaSessionPreferences
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.core.AlphaSecurityAuthority
import com.example.data.passes.QrPassRoomEntity
import com.example.data.vecinos.LosPradosCroquisData
import com.example.scanner.PassType
import com.example.scanner.QrPassStatus
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.EnumMap
import java.util.Locale

/**
 * Generador de código QR ZXing sin bloquear el hilo principal.
 * Utiliza la biblioteca oficial de ZXing para renderizar una matriz binaria en Bitmap.
 */
fun generateZxingQrBitmap(content: String, sizePx: Int = 600): Bitmap? {
    return try {
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
            put(EncodeHintType.MARGIN, 1)
        }
        val bitMatrix = MultiFormatWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            hints
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
    } catch (_: Throwable) {
        null
    }
}

/**
 * PANTALLA ESPECIALIZADA: GENERADOR DE PASES QR PARA VISITANTES (ROL RESIDENTE)
 *
 * Permite a los residentes generar códigos QR temporales y únicos para sus visitantes:
 * 1. Selección de duración sensible al tiempo (1h, 2h, 4h, 8h, 12h, 24h, 48h).
 * 2. Emisión de folios criptográficos inmutables estándar MED-YYYYMMDD-XXXX.
 * 3. Renderizado de código QR en alta resolución utilizando ZXing Barcode Engine.
 * 4. Persistencia local en la base de datos de Room (`QrPassDao`).
 * 5. Aislamiento por vivienda: solo emite y consulta pases para la unidad asignada.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ResidentVisitorQrGeneratorScreen(
    assignedUnit: String,
    condominiumName: String = "Residencial Los Prados",
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val qrPassDao = remember { db.qrPassDao() }

    // Estado del formulario
    var guestName by remember { mutableStateOf("") }
    var guestDocument by remember { mutableStateOf("") }
    var vehiclePlate by remember { mutableStateOf("") }
    var visitReason by remember { mutableStateOf("Visita social") }
    var selectedPassType by remember { mutableStateOf(PassType.VISITOR_SINGLE) }
    var selectedDurationHours by remember { mutableIntStateOf(4) } // 4 horas por defecto

    // Opciones de duración sensible al tiempo
    val durationOptions = listOf(1, 2, 4, 8, 12, 24, 48)

    // Estado del pase generado
    var generatedPass by remember { mutableStateOf<QrPassRoomEntity?>(null) }
    var generatedQrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    // Modal para ver pase previo
    var inspectingPass by remember { mutableStateOf<QrPassRoomEntity?>(null) }

    // Historial de pases para esta residencia desde Room
    val unitPasses by qrPassDao.getPassesByHouse(assignedUnit).collectAsState(initial = emptyList())

    // Cálculo dinámico de la fecha de expiración esperada
    val nowMillis = remember(selectedDurationHours, generatedPass) { System.currentTimeMillis() }
    val expectedExpirationMillis = nowMillis + (selectedDurationHours * 3600 * 1000L)
    val expirationFormatter = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val fullDateFormatter = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("visitor_qr_screen_root"),
        containerColor = NavyDark,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Generar Pase QR",
                            color = TextWhite,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$assignedUnit · $condominiumName",
                            color = GoldPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("btn_back_from_qr_generator")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar al Portal",
                            tint = GoldPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavySurface,
                    navigationIconContentColor = GoldPrimary,
                    titleContentColor = TextWhite
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // TARJETA DE VIVIENDA ASIGNADA (AISLAMIENTO DE DATOS)
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = NavySurface,
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = GoldPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DESTINO AUTORIZADO",
                                color = GoldPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = assignedUnit,
                                color = TextWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Pases encriptados en Room vinculados a su unidad",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyanNeon.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "ZXing v3.5",
                                color = CyanNeon,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // FORMULARIO DE GENERACIÓN DE PASE
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_qr_form"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    border = BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Badge,
                                contentDescription = null,
                                tint = CyanNeon,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "DATOS DEL VISITANTE",
                                color = CyanNeon,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }

                        // Campo 1: Nombre del Visitante
                        OutlinedTextField(
                            value = guestName,
                            onValueChange = { guestName = it },
                            label = { Text("Nombre Completo del Visitante *") },
                            placeholder = { Text("Ej. Ing. Roberto Sánchez") },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = GoldPrimary)
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_visitor_name"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedLabelColor = GoldPrimary,
                                unfocusedLabelColor = TextMuted,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = NavyDark,
                                unfocusedContainerColor = NavyDark
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Campo 2: Identificación oficial (Opcional)
                        OutlinedTextField(
                            value = guestDocument,
                            onValueChange = { guestDocument = it },
                            label = { Text("Identificación / INE / DNI (Opcional)") },
                            placeholder = { Text("Ej. INE-82736412") },
                            leadingIcon = {
                                Icon(Icons.Default.Security, contentDescription = null, tint = TextMuted)
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_visitor_doc"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanNeon,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedLabelColor = CyanNeon,
                                unfocusedLabelColor = TextMuted,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = NavyDark,
                                unfocusedContainerColor = NavyDark
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Campo 3: Placas de Vehículo (Opcional)
                        OutlinedTextField(
                            value = vehiclePlate,
                            onValueChange = { vehiclePlate = it.uppercase() },
                            label = { Text("Placas de Vehículo (Si aplica)") },
                            placeholder = { Text("Ej. QRO-982-A") },
                            leadingIcon = {
                                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = TextMuted)
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_vehicle_plate"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanNeon,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedLabelColor = CyanNeon,
                                unfocusedLabelColor = TextMuted,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = NavyDark,
                                unfocusedContainerColor = NavyDark
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Selector de Tipo de Acceso
                        Text(
                            text = "TIPO DE ACCESO",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = selectedPassType == PassType.VISITOR_SINGLE,
                                onClick = { selectedPassType = PassType.VISITOR_SINGLE },
                                label = { Text("1 Entrada Única") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.EventAvailable,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldPrimary,
                                    selectedLabelColor = NavyDark,
                                    selectedLeadingIconColor = NavyDark,
                                    containerColor = NavyDark,
                                    labelColor = TextMuted
                                )
                            )
                            FilterChip(
                                selected = selectedPassType == PassType.DELIVERY_SERVICE,
                                onClick = { selectedPassType = PassType.DELIVERY_SERVICE },
                                label = { Text("Delivery / Servicio") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.LocalShipping,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyanNeon,
                                    selectedLabelColor = NavyDark,
                                    selectedLeadingIconColor = NavyDark,
                                    containerColor = NavyDark,
                                    labelColor = TextMuted
                                )
                            )
                        }

                        HorizontalDivider(color = Color(0xFF334155), thickness = 0.8.dp)

                        // SELECTOR DE VIGENCIA SENSIBLE AL TIEMPO (CHIPS)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = null,
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "VIGENCIA TEMPORAL",
                                        color = GoldPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Text(
                                    text = "Expira: ${expirationFormatter.format(Date(expectedExpirationMillis))}",
                                    color = TextWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                durationOptions.forEach { hours ->
                                    val isSelected = selectedDurationHours == hours
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedDurationHours = hours },
                                        label = {
                                            Text(
                                                text = if (hours == 1) "1 hora" else "$hours horas",
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        modifier = Modifier.testTag("chip_duration_${hours}h"),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GoldPrimary,
                                            selectedLabelColor = NavyDark,
                                            containerColor = NavyDark,
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

                        // Motivo / Nota para caseta
                        OutlinedTextField(
                            value = visitReason,
                            onValueChange = { visitReason = it },
                            label = { Text("Motivo de la Visita o Instrucción a Caseta") },
                            leadingIcon = {
                                Icon(Icons.Default.Note, contentDescription = null, tint = TextMuted)
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_visit_reason"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = NavyDark,
                                unfocusedContainerColor = NavyDark
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // BOTÓN DE ACCIÓN: GENERAR PASE CON ZXING Y ROOM
                        Button(
                            onClick = {
                                if (guestName.isBlank()) {
                                    Toast.makeText(context, "Por favor ingrese el nombre del visitante", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                isGenerating = true
                                scope.launch(Dispatchers.Default) {
                                    val session = MedusaSessionPreferences.getInstance(context).getUserSession()
                                    if (session == null || !session.isActive) {
                                        withContext(Dispatchers.Main) {
                                            isGenerating = false
                                            Toast.makeText(context, "Sesión no autenticada o inactiva.", Toast.LENGTH_SHORT).show()
                                        }
                                        return@launch
                                    }

                                    // Validación estricta contra sesión: El residente solo puede emitir pases para su propia vivienda
                                    try {
                                        MedusaAreaIsolationGuard.assertResidentLotAccess(session.assignedUnitId, assignedUnit)
                                    } catch (se: SecurityException) {
                                        withContext(Dispatchers.Main) {
                                            isGenerating = false
                                            Toast.makeText(context, se.message ?: "Acceso denegado a otra vivienda", Toast.LENGTH_LONG).show()
                                        }
                                        return@launch
                                    }

                                    // Validación contra Fuente Única de Verdad (catálogo oficial de lotes)
                                    val houseNum = assignedUnit.filter { it.isDigit() }.toIntOrNull()
                                    val existsInCatalog = houseNum != null && LosPradosCroquisData.TODOS_LOS_LOTES.any { it.numero == houseNum }
                                    if (!existsInCatalog && !assignedUnit.contains("Paraíso", ignoreCase = true) && !assignedUnit.contains("General", ignoreCase = true)) {
                                        withContext(Dispatchers.Main) {
                                            isGenerating = false
                                            Toast.makeText(context, "La vivienda $assignedUnit no existe en el catálogo oficial.", Toast.LENGTH_LONG).show()
                                        }
                                        return@launch
                                    }

                                    val now = System.currentTimeMillis()
                                    val validUntil = now + (selectedDurationHours * 3600 * 1000L)
                                    val passCode = AlphaCoreEngine.generateUniqueFolio("MED")
                                    val doc = guestDocument.trim().ifBlank { "N/A" }
                                    val residentId = session.activationKey.ifBlank { "RES-${assignedUnit.filter { it.isDigit() }.ifBlank { "000" }}" }

                                    // Carga útil canónica que vincula: folio, residentId, assignedUnit, visitante, emisión, expiración y tipo
                                    val canonicalPayload = AlphaCoreEngine.buildCanonicalPayload(
                                        folio = passCode,
                                        residentId = residentId,
                                        assignedUnit = assignedUnit,
                                        guestName = guestName.trim(),
                                        createdAtMillis = now,
                                        validUntilMillis = validUntil,
                                        passType = selectedPassType.name
                                    )

                                    // Firma criptográfica asimétrica ECDSA (NIST P-256)
                                    val digitalSignature = AlphaSecurityAuthority.signPassPayload(canonicalPayload, session)

                                    // Carga útil estructurada para escaneo táctico en Caseta
                                    val qrPayload = "MEDUSA-QR-PASS|folio=$passCode|residentId=$residentId|dest=$assignedUnit|guest=${guestName.trim()}|created=$now|exp=$validUntil|type=${selectedPassType.name}|sig=$digitalSignature"

                                    // Generación de Bitmap de alta resolución con ZXing
                                    val bitmap = generateZxingQrBitmap(qrPayload, 600)

                                    // Persistencia estricta en Room SQLite
                                    val entity = QrPassRoomEntity(
                                        passCode = passCode,
                                        guestName = guestName.trim(),
                                        guestDocument = doc,
                                        destinationHouse = assignedUnit,
                                        hostResidentName = "Residente Titular ($assignedUnit)",
                                        vehiclePlate = vehiclePlate.trim().ifBlank { "SIN_VEHICULO" },
                                        passType = selectedPassType,
                                        validUntilMillis = validUntil,
                                        maxEntries = if (selectedPassType == PassType.VISITOR_SINGLE) 1 else 5,
                                        currentEntriesCount = 0,
                                        note = visitReason.trim().ifBlank { "Pase generado por residente" },
                                        createdAtMillis = now,
                                        integrityHash = "",
                                        isActive = true,
                                        residentId = residentId,
                                        assignedUnit = assignedUnit,
                                        status = QrPassStatus.EMITIDO,
                                        digitalSignature = digitalSignature
                                    )
                                    db.qrPassDao().insertPass(entity)

                                    withContext(Dispatchers.Main) {
                                        generatedPass = entity
                                        generatedQrBitmap = bitmap
                                        isGenerating = false
                                        Toast.makeText(context, "¡Pase QR firmado y emitido con seguridad ECDSA! 🛡️", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = guestName.isNotBlank() && !isGenerating,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_generate_visitor_qr"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldPrimary,
                                contentColor = NavyDark,
                                disabledContainerColor = Color(0xFF334155),
                                disabledContentColor = TextMuted
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isGenerating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = NavyDark,
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generando QR con ZXing...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.QrCode2, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "EMITIR CÓDIGO QR SEGURO",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // VISTA DEL PASE QR RECIÉN GENERADO (RENDERIZADO ZXING)
            generatedPass?.let { pass ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("card_active_generated_qr"),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = NavySurface),
                        border = BorderStroke(2.dp, GoldPrimary)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SuccessGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "PASE ACTIVO Y AUTORIZADO",
                                        color = SuccessGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = NavyDark,
                                    border = BorderStroke(1.dp, Color(0xFF334155))
                                ) {
                                    Text(
                                        text = pass.passCode,
                                        color = CyanNeon,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // CONTENEDOR DEL BITMAP DE ZXING
                            Surface(
                                modifier = Modifier
                                    .size(240.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .testTag("qr_display_container"),
                                color = Color.White,
                                border = BorderStroke(3.dp, CyanNeon)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (generatedQrBitmap != null) {
                                        Image(
                                            bitmap = generatedQrBitmap!!.asImageBitmap(),
                                            contentDescription = "Código QR para ${pass.guestName}",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .testTag("qr_display_image")
                                        )
                                    } else {
                                        CircularProgressIndicator(color = NavyDark)
                                    }
                                }
                            }

                            // DETALLES DE VIGENCIA Y DESTINO
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = pass.guestName,
                                    color = TextWhite,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "Destino: ${pass.destinationHouse} · Placas: ${pass.vehiclePlate ?: "N/A"}",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                                        Text(
                                            text = "Válido hasta: ${fullDateFormatter.format(Date(pass.validUntilMillis))}",
                                            color = GoldPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                            // BOTONES DE COMPARTIR Y COPIAR
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Folio Pase QR", pass.passCode)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Folio copiado: ${pass.passCode}", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .testTag("btn_copy_qr_folio"),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon),
                                    border = BorderStroke(1.dp, CyanNeon),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copiar Folio", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_SUBJECT,
                                                "Pase de Acceso para $assignedUnit - Los Prados"
                                            )
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                """
                                                🏡 Pase de Acceso Residencial - Residencial Los Prados
                                                Visitante: ${pass.guestName}
                                                Destino: ${pass.destinationHouse}
                                                Folio: ${pass.passCode}
                                                Válido hasta: ${fullDateFormatter.format(Date(pass.validUntilMillis))}
                                                Presenta este código en la caseta principal para ingresar.
                                                """.trimIndent()
                                            )
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Compartir Pase QR"))
                                    },
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .height(44.dp)
                                        .testTag("btn_share_qr_pass"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = GoldPrimary,
                                        contentColor = NavyDark
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Compartir Pase", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // LISTA DE PASES EMITIDOS PARA ESTA VIVIENDA (PERSISTENCIA ROOM)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PASES DE ${assignedUnit.uppercase()}",
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${unitPasses.size} registrados en Room",
                        color = CyanNeon,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (unitPasses.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = NavySurface,
                        border = BorderStroke(1.dp, Color(0xFF1E293B))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Sin pases emitidos recientemente",
                                color = TextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                items(unitPasses) { passItem ->
                    val isPassExpired = passItem.isExpired
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { inspectingPass = passItem }
                            .testTag("item_pass_${passItem.passCode}"),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = NavySurface),
                        border = BorderStroke(
                            1.dp,
                            if (isPassExpired) Color(0xFF334155) else GoldPrimary.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isPassExpired) Color(0xFF334155) else GoldPrimary.copy(alpha = 0.15f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.QrCode,
                                        contentDescription = null,
                                        tint = if (isPassExpired) TextMuted else GoldPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = passItem.guestName,
                                    color = TextWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Folio: ${passItem.passCode}",
                                    color = CyanNeon,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "Válido hasta: ${passItem.formattedValidUntil}",
                                    color = if (isPassExpired) ErrorRed else TextMuted,
                                    fontSize = 11.sp
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isPassExpired) ErrorRed.copy(alpha = 0.15f) else SuccessGreen.copy(alpha = 0.15f),
                                border = BorderStroke(
                                    1.dp,
                                    if (isPassExpired) ErrorRed else SuccessGreen
                                )
                            ) {
                                Text(
                                    text = if (isPassExpired) "EXPIRADO" else "ACTIVO",
                                    color = if (isPassExpired) ErrorRed else SuccessGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // MODAL DE DETALLE Y CÓDIGO QR ZXING PARA PASES PREVIOS
    inspectingPass?.let { pass ->
        Dialog(
            onDismissRequest = { inspectingPass = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var cachedBitmap by remember { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(pass.passCode) {
                withContext(Dispatchers.Default) {
                    val payload = if (pass.digitalSignature.isNotBlank()) {
                        "MEDUSA-QR-PASS|folio=${pass.passCode}|residentId=${pass.residentId}|dest=${pass.destinationHouse}|guest=${pass.guestName}|created=${pass.createdAtMillis}|exp=${pass.validUntilMillis}|type=${pass.passType.name}|sig=${pass.digitalSignature}"
                    } else {
                        "MEDUSA-QR-PASS|folio=${pass.passCode}|dest=${pass.destinationHouse}|guest=${pass.guestName}|exp=${pass.validUntilMillis}|hash=${pass.integrityHash}"
                    }
                    cachedBitmap = generateZxingQrBitmap(payload, 600)
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .testTag("dialog_inspect_qr_pass"),
                shape = RoundedCornerShape(22.dp),
                color = NavySurface,
                border = BorderStroke(1.dp, GoldPrimary)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
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
                                text = "Folio: ${pass.passCode}",
                                color = CyanNeon,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = { inspectingPass = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                        }
                    }

                    // Renderizado del QR
                    Surface(
                        modifier = Modifier
                            .size(230.dp)
                            .clip(RoundedCornerShape(14.dp)),
                        color = Color.White,
                        border = BorderStroke(2.dp, GoldPrimary)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (cachedBitmap != null) {
                                Image(
                                    bitmap = cachedBitmap!!.asImageBitmap(),
                                    contentDescription = "Código QR",
                                    modifier = Modifier.fillMaxSize().padding(10.dp)
                                )
                            } else {
                                CircularProgressIndicator(color = NavyDark)
                            }
                        }
                    }

                    Text(
                        text = "Válido hasta: ${pass.formattedValidUntil}",
                        color = if (pass.isExpired) ErrorRed else GoldPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Pase de acceso para ${pass.guestName} (${pass.destinationHouse}). Folio: ${pass.passCode}. Válido hasta: ${pass.formattedValidUntil}"
                                    )
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Compartir Pase"))
                            },
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compartir", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { inspectingPass = null },
                            modifier = Modifier.weight(1f).height(44.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite),
                            border = BorderStroke(1.dp, Color(0xFF334155)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cerrar", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
