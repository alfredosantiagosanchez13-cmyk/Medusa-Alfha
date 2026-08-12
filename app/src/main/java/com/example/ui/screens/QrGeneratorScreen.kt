package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scanner.PassType
import com.example.scanner.QrPassEntity
import com.example.scanner.SamplePassRepository
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QrGeneratorScreen(
    onSimulateScan: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var selectedPassType by remember { mutableStateOf(PassType.VISITOR_SINGLE) }
    var guestName by remember { mutableStateOf("Carlos Valenzuela") }
    var guestDocument by remember { mutableStateOf("17.890.123-4") }
    var destinationHouse by remember { mutableStateOf("Casa #108") }
    var hostResidentName by remember { mutableStateOf("Familia Valenzuela") }
    var vehiclePlate by remember { mutableStateOf("KXYZ-45") }
    var note by remember { mutableStateOf("Visita de fin de semana") }

    // Validity options in hours
    var validityHours by remember { mutableStateOf(12) } // default 12h
    var maxEntries by remember { mutableStateOf(1) } // default 1

    var passCode by remember { mutableStateOf(generatePassCode(selectedPassType)) }
    var generatedPassList by remember {
        mutableStateOf(SamplePassRepository.getAllKnownPasses().reversed())
    }

    var qrBitmap by remember(passCode) {
        mutableStateOf(createQrCodeBitmap(passCode))
    }

    var showSuccessBanner by remember { mutableStateOf(false) }

    fun refreshPassCode() {
        passCode = generatePassCode(selectedPassType)
        qrBitmap = createQrCodeBitmap(passCode)
    }

    fun handleRegisterPass() {
        val calculatedValidUntil = System.currentTimeMillis() + (validityHours * 3600 * 1000L)
        val newPass = QrPassEntity(
            passCode = passCode,
            guestName = guestName.ifBlank { "Invitado / Residente" },
            guestDocument = guestDocument.ifBlank { "12.345.678-9" },
            destinationHouse = destinationHouse.ifBlank { "Casa Principal" },
            hostResidentName = hostResidentName.ifBlank { "Residente Anfitrión" },
            vehiclePlate = vehiclePlate.ifBlank { null },
            passType = selectedPassType,
            validUntilMillis = calculatedValidUntil,
            maxEntries = maxEntries,
            currentEntriesCount = 0,
            note = note.ifBlank { null }
        )

        SamplePassRepository.addCustomPass(newPass)
        generatedPassList = SamplePassRepository.getAllKnownPasses().reversed()

        Toast.makeText(context, "¡Pase $passCode creado con éxito!", Toast.LENGTH_SHORT).show()
        showSuccessBanner = true
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("qr_generator_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Screen Header Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = NavySurface,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldPrimary.copy(alpha = 0.8f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(GoldPrimary.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "MEDUSA ALFHA • EMISIÓN DE PASES",
                                    color = GoldPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Generador de Código QR Acceso",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        IconButton(
                            onClick = { refreshPassCode() },
                            modifier = Modifier.testTag("refresh_code_icon_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerar Código",
                                tint = CyanNeon
                            )
                        }
                    }
                }
            }
        }

        // Live Generated QR Preview Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("qr_preview_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyanNeon.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "VISTA PREVIA DEL CÓDIGO QR ÚNICO",
                        color = GoldPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Rendered QR Code
                    Card(
                        modifier = Modifier
                            .size(200.dp)
                            .border(3.dp, GoldPrimary, RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            qrBitmap?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Pase QR Generado",
                                    modifier = Modifier.size(170.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = passCode,
                        color = CyanNeon,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )

                    val expTimeStr = SimpleDateFormat("dd/MM/yyyy HH:mm hrs", Locale.getDefault())
                        .format(Date(System.currentTimeMillis() + (validityHours * 3600 * 1000L)))

                    Text(
                        text = "Válido por $validityHours h (Expira: $expTimeStr)",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // QR Action Bar: Copy, Share, Simulate
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Pase QR Medusa", passCode)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Código copiado: $passCode", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("copy_qr_code_btn")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copiar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                handleRegisterPass()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("save_and_register_qr_btn")
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Guardar Pase", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }

                        Button(
                            onClick = {
                                handleRegisterPass()
                                onSimulateScan(passCode)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("test_scan_now_btn")
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Probar Escaneo", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }

        // Pass Type Selection Chips
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "TIPO DE PASE Y CATEGORÍA",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(PassType.values()) { type ->
                        FilterChip(
                            selected = selectedPassType == type,
                            onClick = {
                                selectedPassType = type
                                refreshPassCode()
                            },
                            label = {
                                Text(
                                    text = type.label,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = {
                                val icon = when (type) {
                                    PassType.VISITOR_SINGLE -> Icons.Default.Person
                                    PassType.RESIDENT_PERMANENT -> Icons.Default.Home
                                    PassType.DELIVERY_SERVICE -> Icons.Default.LocalShipping
                                    PassType.EVENT_GUEST -> Icons.Default.Event
                                }
                                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldPrimary,
                                selectedLabelColor = NavyDark,
                                selectedLeadingIconColor = NavyDark,
                                containerColor = NavyCard,
                                labelColor = TextMuted,
                                iconColor = TextMuted
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("pass_type_chip_${type.name}")
                        )
                    }
                }
            }
        }

        // Form Fields Container
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "DATOS DEL TITULAR / INVITADO",
                        color = GoldPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    // Guest / Resident Name
                    OutlinedTextField(
                        value = guestName,
                        onValueChange = { guestName = it },
                        label = { Text("Nombre Completo del Titular/Invitado", color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = GoldPrimary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = NavyDark,
                            unfocusedContainerColor = NavyDark
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_guest_name")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Document / RUT
                        OutlinedTextField(
                            value = guestDocument,
                            onValueChange = { guestDocument = it },
                            label = { Text("Documento / RUT", color = TextMuted) },
                            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = CyanNeon) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = NavyDark,
                                unfocusedContainerColor = NavyDark
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_guest_doc")
                        )

                        // House / Destination
                        OutlinedTextField(
                            value = destinationHouse,
                            onValueChange = { destinationHouse = it },
                            label = { Text("Casa / Depto", color = TextMuted) },
                            leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = GoldPrimary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = NavyDark,
                                unfocusedContainerColor = NavyDark
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_destination_house")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Host Resident Name
                        OutlinedTextField(
                            value = hostResidentName,
                            onValueChange = { hostResidentName = it },
                            label = { Text("Residente Anfitrión", color = TextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = NavyDark,
                                unfocusedContainerColor = NavyDark
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_host_resident")
                        )

                        // Vehicle Plate
                        OutlinedTextField(
                            value = vehiclePlate,
                            onValueChange = { vehiclePlate = it },
                            label = { Text("Patente (Opcional)", color = TextMuted) },
                            leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = CyanNeon) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = NavyDark,
                                unfocusedContainerColor = NavyDark
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_vehicle_plate")
                        )
                    }

                    // Duration Chips
                    Text(
                        text = "DURACIÓN DE VALIDEZ DEL PASE",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(2 to "2 Horas", 6 to "6 Horas", 12 to "12 Horas", 24 to "24 Horas", 168 to "7 Días").forEach { (hours, label) ->
                            FilterChip(
                                selected = validityHours == hours,
                                onClick = { validityHours = hours },
                                label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldPrimary,
                                    selectedLabelColor = NavyDark,
                                    containerColor = NavyDark,
                                    labelColor = TextMuted
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Max Entries Chips
                    Text(
                        text = "LÍMITE DE ACCESOS PERMITIDOS",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1 to "1 Uso (Único)", 2 to "2 Usos", 5 to "5 Usos", 999 to "Ilimitado").forEach { (max, label) ->
                            FilterChip(
                                selected = maxEntries == max,
                                onClick = { maxEntries = max },
                                label = { Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyanNeon,
                                    selectedLabelColor = NavyDark,
                                    containerColor = NavyDark,
                                    labelColor = TextMuted
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Observations
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Notas / Observaciones de Garita", color = TextMuted) },
                        leadingIcon = { Icon(Icons.Default.NoteAdd, contentDescription = null, tint = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = NavyDark,
                            unfocusedContainerColor = NavyDark
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_pass_note")
                    )
                }
            }
        }

        // List of Active Generated Passes in System
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PASES GENERADOS EN EL SISTEMA (${generatedPassList.size})",
                    color = GoldPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )

                generatedPassList.forEach { pass ->
                    GeneratedPassItemCard(
                        pass = pass,
                        onTestScan = {
                            onSimulateScan(pass.passCode)
                        }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GeneratedPassItemCard(
    pass: QrPassEntity,
    onTestScan: () -> Unit
) {
    val context = LocalContext.current
    val isExpired = System.currentTimeMillis() > pass.validUntilMillis

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("generated_pass_card_${pass.passCode}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isExpired) Color.Red.copy(alpha = 0.5f) else GoldPrimary.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = if (isExpired) Color.Red.copy(alpha = 0.2f) else SuccessGreen.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (isExpired) "EXPIRADO" else pass.passType.label,
                            color = if (isExpired) Color.Red else SuccessGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = pass.passCode,
                        color = CyanNeon,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${pass.guestName} (${pass.destinationHouse})",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "RUT: ${pass.guestDocument} • Anfitrión: ${pass.hostResidentName}",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            IconButton(
                onClick = onTestScan,
                modifier = Modifier.testTag("test_scan_item_${pass.passCode}")
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Probar Escaneo",
                    tint = GoldPrimary
                )
            }
        }
    }
}

private fun generatePassCode(passType: PassType): String {
    val prefix = when (passType) {
        PassType.VISITOR_SINGLE -> "GST"
        PassType.RESIDENT_PERMANENT -> "RES"
        PassType.DELIVERY_SERVICE -> "DLV"
        PassType.EVENT_GUEST -> "VIP"
    }
    val randomNum = (1000..9999).random()
    return "MEDUSA-PASS-$prefix-$randomNum"
}

private fun createQrCodeBitmap(text: String, sizePx: Int = 512): Bitmap? {
    return try {
        val writer = MultiFormatWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
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
        e.printStackTrace()
        null
    }
}
