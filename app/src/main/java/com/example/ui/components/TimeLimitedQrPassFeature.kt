package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentInd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.firebase.FirebaseConfigHelper
import com.example.data.firebase.FirestoreTenantManager
import com.example.data.visitor.FirestoreVisitorLog
import com.example.data.passes.QrPassRepository
import com.example.data.passes.QrPassRoomEntity
import com.example.data.visitor.VisitorCheckIn
import com.example.scanner.PassStatus
import com.example.scanner.PassType
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarningOrange
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Modelo representativo de un Token Temporal Único de Acceso para Visitantes.
 */
data class VisitorAccessTokenInfo(
    val tokenId: String,
    val passCode: String,
    val folio: String,
    val visitorName: String,
    val visitorDocument: String,
    val destinationHouse: String,
    val hostResidentName: String,
    val passTypeLabel: String,
    val vehiclePlate: String? = null,
    val issuedAtMillis: Long = System.currentTimeMillis(),
    val validUntilMillis: Long,
    val durationHours: Int,
    val maxEntries: Int = 1,
    val currentEntriesCount: Int = 0,
    val integrityHash: String,
    val residentNotes: String? = null,
    val isActive: Boolean = true
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > validUntilMillis

    val isExhausted: Boolean
        get() = currentEntriesCount >= maxEntries

    val isValidForAccess: Boolean
        get() = isActive && !isExpired && !isExhausted

    val remainingMillis: Long
        get() = (validUntilMillis - System.currentTimeMillis()).coerceAtLeast(0L)

    val remainingFormatted: String
        get() {
            if (isExpired) return "Expirado"
            val totalSecs = remainingMillis / 1000
            val hours = totalSecs / 3600
            val mins = (totalSecs % 3600) / 60
            return when {
                hours > 0 && mins > 0 -> "${hours}h ${mins}m restantes"
                hours > 0 -> "${hours}h restantes"
                else -> "${mins}m restantes"
            }
        }

    val formattedValidUntil: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(validUntilMillis))

    val formattedIssuedAt: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(issuedAtMillis))

    /**
     * Payload JSON estructurado que encapsula el token para máxima compatibilidad con escáneres.
     */
    val qrPayloadJson: String
        get() = """{"passCode":"$passCode","tokenId":"$tokenId","folio":"$folio","guest":"$visitorName","house":"$destinationHouse","expires":$validUntilMillis,"max":$maxEntries,"hash":"$integrityHash"}"""
}

/**
 * Generador de código QR como Bitmap.
 */
fun generateTokenQrBitmap(contents: String, sizePx: Int = 512): Bitmap? {
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

/**
 * Crea y persiste de manera transaccional un Token de Acceso Temporal único para visitantes.
 */
suspend fun createAndStoreTimeLimitedToken(
    db: AppDatabase,
    condominiumId: String,
    visitorName: String,
    visitorDocument: String,
    destinationHouse: String,
    hostResidentName: String,
    passTypeLabel: String,
    vehiclePlate: String?,
    durationHours: Int,
    maxEntries: Int,
    residentNotes: String?
): VisitorAccessTokenInfo = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    val folio = AlphaCoreEngine.generateUniqueFolio("MED")
    val passCode = folio
    val uniqueTokenId = "TOK-${folio.takeLast(6)}-${UUID.randomUUID().toString().take(6).uppercase(Locale.US)}"
    val validUntilMillis = now + (durationHours.toLong() * 3600 * 1000L)
    val docToUse = visitorDocument.ifBlank { "Verificar en Caseta" }
    val integrityHash = AlphaCoreEngine.computeIntegrityHash(passCode, docToUse, destinationHouse)

    val tokenInfo = VisitorAccessTokenInfo(
        tokenId = uniqueTokenId,
        passCode = passCode,
        folio = folio,
        visitorName = visitorName.trim(),
        visitorDocument = docToUse,
        destinationHouse = destinationHouse.trim(),
        hostResidentName = hostResidentName.trim(),
        passTypeLabel = passTypeLabel,
        vehiclePlate = vehiclePlate?.trim()?.ifBlank { null },
        issuedAtMillis = now,
        validUntilMillis = validUntilMillis,
        durationHours = durationHours,
        maxEntries = maxEntries,
        currentEntriesCount = 0,
        integrityHash = integrityHash,
        residentNotes = residentNotes?.trim()?.ifBlank { null },
        isActive = true
    )

    // 1. Guardar en QrPassDao (para validación del escáner en garita)
    val qrPassEntity = QrPassRoomEntity(
        passCode = passCode,
        guestName = tokenInfo.visitorName,
        guestDocument = tokenInfo.visitorDocument,
        destinationHouse = tokenInfo.destinationHouse,
        hostResidentName = tokenInfo.hostResidentName,
        vehiclePlate = tokenInfo.vehiclePlate,
        passType = if (maxEntries == 1) PassType.VISITOR_SINGLE else PassType.EVENT_GUEST,
        validUntilMillis = validUntilMillis,
        maxEntries = maxEntries,
        currentEntriesCount = 0,
        note = "Token temporal (${durationHours}h) ID: $uniqueTokenId. ${tokenInfo.residentNotes ?: ""}".trim(),
        createdAtMillis = now,
        integrityHash = integrityHash,
        isActive = true
    )
    db.qrPassDao().insertPass(qrPassEntity)

    // 2. Guardar en VisitorCheckInDao (para la bitácora de visitantes)
    val expiryStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(validUntilMillis))
    val checkIn = VisitorCheckIn(
        id = 0,
        folio = folio,
        visitorName = tokenInfo.visitorName,
        visitorDocument = tokenInfo.visitorDocument,
        destinationHouse = tokenInfo.destinationHouse,
        passCode = passCode,
        passTypeLabel = "$passTypeLabel (Token ${durationHours}h)",
        vehiclePlate = tokenInfo.vehiclePlate,
        status = "PRE_REGISTRADO",
        timestampMillis = now,
        guardNotes = "Token temporal emitido. Vence: $expiryStr • Máx usos: $maxEntries",
        residentNotes = tokenInfo.residentNotes,
        hostResidentName = tokenInfo.hostResidentName
    )
    val insertedId = db.visitorCheckInDao().insertCheckIn(checkIn)
    val finalCheckIn = checkIn.copy(id = insertedId)

    // 3. Sincronizar en Firestore si está disponible
    val fs = FirebaseConfigHelper.getFirestore()
    if (fs != null) {
        try {
            val firestoreLog = FirestoreVisitorLog.fromVisitorCheckIn(finalCheckIn, condominiumId)
            FirestoreTenantManager.saveVisitorLog(fs, condominiumId, firestoreLog)
            FirestoreTenantManager.saveVisitorCheckIn(fs, condominiumId, finalCheckIn)
        } catch (_: Exception) {}
    }

    tokenInfo
}

/**
 * Diálogo interactivo para que los residentes generen un Pase QR con Token Temporal.
 */
@Composable
fun GenerateTimedTokenDialog(
    db: AppDatabase,
    condominiumId: String,
    defaultUnit: String,
    defaultHost: String,
    onDismiss: () -> Unit,
    onTokenCreated: (VisitorAccessTokenInfo) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var visitorName by remember { mutableStateOf("") }
    var visitorDocument by remember { mutableStateOf("") }
    var authorizedUnit by remember { mutableStateOf(defaultUnit) }
    var hostResidentName by remember { mutableStateOf(defaultHost) }
    var selectedCategory by remember { mutableStateOf("Visita Familiar") }
    var isVehicular by remember { mutableStateOf(false) }
    var vehiclePlate by remember { mutableStateOf("") }
    var durationHours by remember { mutableIntStateOf(4) } // Default 4 hours
    var maxEntries by remember { mutableIntStateOf(1) } // Default single-use
    var residentNotes by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }

    // Cálculo dinámico de fecha de vencimiento estimada
    val now = remember { System.currentTimeMillis() }
    val estimatedExpiry = remember(durationHours) {
        val expiryMillis = System.currentTimeMillis() + (durationHours.toLong() * 3600 * 1000L)
        SimpleDateFormat("EEEE d 'de' MMMM, HH:mm 'hrs'", Locale("es", "ES")).format(Date(expiryMillis))
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("dialog_generate_timed_token"),
            shape = RoundedCornerShape(20.dp),
            color = NavySurface,
            border = BorderStroke(1.5.dp, GoldPrimary)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(GoldPrimary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "NUEVO PASE QR",
                                    color = GoldPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = CyanNeon.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "TOKEN TEMPORAL",
                                        color = CyanNeon,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Acceso con expiración automática",
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

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Nombre del Visitante
                    OutlinedTextField(
                        value = visitorName,
                        onValueChange = { visitorName = it },
                        label = { Text("Nombre Completo del Visitante *") },
                        placeholder = { Text("Ej: Andrea Morales Silva") },
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
                            .testTag("input_token_visitor_name")
                    )

                    // Cédula / RUT
                    OutlinedTextField(
                        value = visitorDocument,
                        onValueChange = { visitorDocument = it },
                        label = { Text("Documento / RUT (Opcional)") },
                        placeholder = { Text("Ej: 18.234.567-8") },
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
                            .testTag("input_token_visitor_doc")
                    )

                    // Fila: Unidad y Residente
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = authorizedUnit,
                            onValueChange = { authorizedUnit = it },
                            label = { Text("Unidad Destino *") },
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
                                .testTag("input_token_unit")
                        )

                        OutlinedTextField(
                            value = hostResidentName,
                            onValueChange = { hostResidentName = it },
                            label = { Text("Residente Anfitrión *") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.1f)
                                .testTag("input_token_host")
                        )
                    }

                    // SECCIÓN CRÍTICA: DURACIÓN Y TIEMPO LÍMITE DEL TOKEN
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = NavyDark,
                        border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = null,
                                        tint = CyanNeon,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "VIGENCIA TEMPORAL DEL TOKEN",
                                        color = CyanNeon,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = CyanNeon.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "$durationHours HORAS",
                                        color = CyanNeon,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Chips de Duraciones Rápidas
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    1 to "1h Exprés",
                                    2 to "2h",
                                    4 to "4h Estándar",
                                    8 to "8h",
                                    24 to "24h (1 Día)",
                                    48 to "48h"
                                ).forEach { (hours, label) ->
                                    val isSelected = durationHours == hours
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { durationHours = hours },
                                        label = { Text(label, fontSize = 9.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CyanNeon,
                                            selectedLabelColor = NavyDark,
                                            containerColor = NavySurface,
                                            labelColor = TextMuted
                                        ),
                                        border = BorderStroke(0.5.dp, if (isSelected) CyanNeon else Color.White.copy(alpha = 0.1f)),
                                        modifier = Modifier.testTag("chip_duration_${hours}h")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Banner Explicativo con Fecha de Expiración
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = NavySurface,
                                border = BorderStroke(0.5.dp, GoldPrimary.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            text = "El token expira automáticamente el:",
                                            color = TextMuted,
                                            fontSize = 9.sp
                                        )
                                        Text(
                                            text = estimatedExpiry,
                                            color = GoldPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Categoría de Visita
                    Column {
                        Text("Categoría / Propósito", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf("Visita Familiar", "Amigos", "Delivery", "Servicio Técnico", "Contratista").forEach { cat ->
                                val isSelected = selectedCategory == cat
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat, fontSize = 9.sp) },
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

                    // Tipo de Uso del Token: Entrada Única vs Múltiple
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (maxEntries == 1) "Pase de Entrada Única" else "Accesos Múltiples durante la vigencia",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (maxEntries == 1) "El código QR se consume y queda invalidado tras el primer ingreso" else "Permite reingresos hasta que el token expire",
                                    color = TextMuted,
                                    fontSize = 9.sp
                                )
                            }

                            Switch(
                                checked = maxEntries > 1,
                                onCheckedChange = { isMulti -> maxEntries = if (isMulti) 5 else 1 },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CyanNeon,
                                    checkedTrackColor = CyanNeon.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }

                    // Acceso Vehicular
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
                                    Text("Ingreso con Vehículo", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(if (isVehicular) "Se registrará la patente vehicular" else "Ingreso peatonal", color = TextMuted, fontSize = 9.sp)
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
                            label = { Text("Patente del Vehículo *") },
                            placeholder = { Text("Ej: ABCD-12") },
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
                                .testTag("input_token_plate")
                        )
                    }

                    // Notas para la Caseta
                    OutlinedTextField(
                        value = residentNotes,
                        onValueChange = { residentNotes = it },
                        label = { Text("Instrucciones para Caseta (Opcional)") },
                        placeholder = { Text("Ej: Estacionar en espacio de visitas #4...") },
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
                            .testTag("input_token_notes")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Botón Generar Token QR
                Button(
                    onClick = {
                        if (visitorName.isBlank() || authorizedUnit.isBlank()) return@Button
                        isGenerating = true
                        scope.launch {
                            val token = createAndStoreTimeLimitedToken(
                                db = db,
                                condominiumId = condominiumId,
                                visitorName = visitorName,
                                visitorDocument = visitorDocument,
                                destinationHouse = authorizedUnit,
                                hostResidentName = hostResidentName,
                                passTypeLabel = selectedCategory,
                                vehiclePlate = if (isVehicular) vehiclePlate else null,
                                durationHours = durationHours,
                                maxEntries = maxEntries,
                                residentNotes = residentNotes
                            )
                            isGenerating = false
                            Toast.makeText(context, "✨ Token temporal generado por $durationHours horas", Toast.LENGTH_SHORT).show()
                            onTokenCreated(token)
                        }
                    },
                    enabled = visitorName.isNotBlank() && authorizedUnit.isNotBlank() && (!isVehicular || vehiclePlate.isNotBlank()) && !isGenerating,
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_submit_generate_timed_token")
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NavyDark, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generando Token Criptográfico...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Generar Pase QR con Token Temporal",
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
 * Modal visual completo que muestra el Pase QR generado con su Token Temporal,
 * tiempo restante dinámico, credenciales criptográficas y opciones de compartir.
 */
@Composable
fun TimeLimitedDigitalPassModal(
    tokenInfo: VisitorAccessTokenInfo,
    db: AppDatabase,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // Temporizador de refresco en vivo para el tiempo restante
    var currentRemainingTime by remember { mutableStateOf(tokenInfo.remainingFormatted) }
    var isTokenExpired by remember { mutableStateOf(tokenInfo.isExpired) }

    LaunchedEffect(tokenInfo) {
        while (true) {
            currentRemainingTime = tokenInfo.remainingFormatted
            isTokenExpired = tokenInfo.isExpired
            delay(1000L)
        }
    }

    // QR Bitmap generado a partir del código único del pase
    val qrBitmap = remember(tokenInfo.passCode) {
        generateTokenQrBitmap(tokenInfo.passCode, 480)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .testTag("timed_digital_pass_modal"),
            shape = RoundedCornerShape(20.dp),
            color = NavySurface,
            border = BorderStroke(1.5.dp, if (isTokenExpired) ErrorRed else GoldPrimary)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            tint = if (isTokenExpired) ErrorRed else GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "PASE QR CON TOKEN TEMPORAL",
                            color = if (isTokenExpired) ErrorRed else GoldPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // BADGE DE ESTADO EN VIVO Y CUENTA REGRESIVA
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isTokenExpired) ErrorRed.copy(alpha = 0.15f) else CyanNeon.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, if (isTokenExpired) ErrorRed else CyanNeon),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = if (isTokenExpired) ErrorRed else CyanNeon,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = if (isTokenExpired) "TOKEN EXPIRADO" else "TOKEN ACTIVO",
                                    color = if (isTokenExpired) ErrorRed else CyanNeon,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "Vence: ${tokenInfo.formattedValidUntil}",
                                    color = TextMuted,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isTokenExpired) ErrorRed else CyanNeon
                        ) {
                            Text(
                                text = currentRemainingTime,
                                color = NavyDark,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // CONTENEDOR QR DE ALTO CONTRASTE
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    modifier = Modifier
                        .size(210.dp)
                        .testTag("qr_code_image_container"),
                    border = BorderStroke(2.5.dp, if (isTokenExpired) ErrorRed else GoldPrimary)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Código QR de Token de Acceso",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )
                        } else {
                            CircularProgressIndicator(color = NavyDark)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // DATOS DEL VISITANTE Y TOKEN
                Text(
                    text = tokenInfo.visitorName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Doc: ${tokenInfo.visitorDocument} • Tipo: ${tokenInfo.passTypeLabel}",
                    color = TextMuted,
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // TARJETA DE DETALLES DEL TOKEN Y SEGURIDAD
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Código Pase:", color = TextMuted, fontSize = 10.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = tokenInfo.passCode,
                                    color = GoldPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(tokenInfo.passCode))
                                        Toast.makeText(context, "Código copiado al portapapeles", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar", tint = GoldPrimary, modifier = Modifier.size(13.dp))
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Destino Autorizado:", color = TextMuted, fontSize = 10.sp)
                            Text(
                                text = tokenInfo.destinationHouse,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Anfitrión:", color = TextMuted, fontSize = 10.sp)
                            Text(
                                text = tokenInfo.hostResidentName,
                                color = Color.White,
                                fontSize = 10.sp
                            )
                        }

                        if (!tokenInfo.vehiclePlate.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Vehículo Autorizado:", color = TextMuted, fontSize = 10.sp)
                                Text(
                                    text = tokenInfo.vehiclePlate,
                                    color = CyanNeon,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Límite de Usos:", color = TextMuted, fontSize = 10.sp)
                            Text(
                                text = if (tokenInfo.maxEntries == 1) "1 Entrada Única" else "${tokenInfo.maxEntries} Entradas permitidas",
                                color = SuccessGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Firma de Integridad Criptográfica
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Firma Criptográfica:", color = TextMuted, fontSize = 9.sp)
                            Text(
                                text = "SHA-256: ${tokenInfo.integrityHash.take(8)}...",
                                color = CyanNeon,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // BOTONES DE ACCIÓN: COMPARTIR Y SIMULAR ESCANEO EN CASETA
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Botón Compartir Invitación con el Visitante
                    Button(
                        onClick = {
                            val shareMessage = buildString {
                                appendLine("🛡️ *PASE DE ACCESO - MEDUSA ALFHA*")
                                appendLine("Hola ${tokenInfo.visitorName},")
                                appendLine("Se ha generado tu pase de acceso para ingresar a ${tokenInfo.destinationHouse}.")
                                appendLine("")
                                appendLine("📋 *Detalles del Pase:*")
                                appendLine("• *Código de Entrada:* ${tokenInfo.passCode}")
                                appendLine("• *Folio:* ${tokenInfo.folio}")
                                appendLine("• *Válido hasta:* ${tokenInfo.formattedValidUntil} (${tokenInfo.durationHours} horas)")
                                appendLine("• *Anfitrión:* ${tokenInfo.hostResidentName}")
                                if (!tokenInfo.vehiclePlate.isNullOrBlank()) {
                                    appendLine("• *Patente:* ${tokenInfo.vehiclePlate}")
                                }
                                appendLine("• *Tipo:* ${if (tokenInfo.maxEntries == 1) "Entrada Única" else "Pase Múltiple"}")
                                appendLine("")
                                appendLine("Muestra este código o QR en la caseta de seguridad al llegar.")
                            }

                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareMessage)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Compartir Pase con Visitante"))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_share_token_pass")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Compartir Pase con el Visitante",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    // Botón Simular Lectura de Caseta (Verifica que el escáner de garita reconozca el token)
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val repo = QrPassRepository(db.qrPassDao())
                                val verification = repo.verifyPassCode(tokenInfo.passCode)
                                when (verification.status) {
                                    PassStatus.VALID -> {
                                        Toast.makeText(
                                            context,
                                            "✅ CASETA: Token Válido para ${tokenInfo.visitorName} (${tokenInfo.destinationHouse}). Vence en $currentRemainingTime.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    PassStatus.EXPIRED -> {
                                        Toast.makeText(
                                            context,
                                            "❌ CASETA: Token Expirado (${verification.failureReason})",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    else -> {
                                        Toast.makeText(
                                            context,
                                            "ℹ️ Estado en Caseta: ${verification.status} - ${verification.failureReason ?: "Sin observaciones"}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GoldPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("btn_test_token_verification")
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Probar Validación en Caseta",
                            color = GoldPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Insignia visual de estado para pases temporales mostrada en tarjetas individuales.
 */
@Composable
fun TimedTokenStatusBadge(
    validUntilMillis: Long,
    modifier: Modifier = Modifier
) {
    val isExpired = System.currentTimeMillis() > validUntilMillis
    val remainingMillis = (validUntilMillis - System.currentTimeMillis()).coerceAtLeast(0L)
    val remainingHours = remainingMillis / (3600 * 1000)
    val remainingMins = (remainingMillis % (3600 * 1000)) / (60 * 1000)

    val label = when {
        isExpired -> "EXPIRADO"
        remainingHours > 0 -> "⏱️ $remainingHours h ${remainingMins}m"
        else -> "⏱️ $remainingMins min"
    }

    val badgeColor = when {
        isExpired -> ErrorRed
        remainingHours < 1 -> WarningOrange
        else -> CyanNeon
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = badgeColor.copy(alpha = 0.15f),
        border = BorderStroke(0.5.dp, badgeColor),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(badgeColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = badgeColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
