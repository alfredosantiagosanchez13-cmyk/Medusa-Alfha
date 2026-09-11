package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.visitor.VisitorCheckIn
import com.example.data.visitor.VisitorCheckInRepository
import com.example.ui.theme.*
import com.example.utils.AccessAuthorizationManager
import com.example.utils.AccessAuthorizationRequest
import com.example.utils.ImageCaptureHelper
import kotlinx.coroutines.launch

/**
 * Tarjeta Táctica de Flujo Ultrarrápido para la Garita Principal.
 * Diseñada para alto flujo vehicular:
 * 1. Toma exactamente 2 fotografías (Identificación Oficial y Vehículo/Placas).
 * 2. Selección en 1 toque: Visita, Proveedor o Paquetería.
 * 3. Escribe únicamente el nombre y casa destino.
 * 4. Botón 'Notificar al Residente' para autorización remota sin demoras.
 */
@Composable
fun GaritaFastVehicleFlowCard(
    condoDisplayName: String,
    condoTag: String,
    defaultDestinationCasa: String,
    visitorRepo: VisitorCheckInRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Estados del formulario rápido
    var visitorType by remember { mutableStateOf("Visita") }
    var visitorName by remember { mutableStateOf("") }
    var destinationCasa by remember(defaultDestinationCasa) { mutableStateOf(defaultDestinationCasa) }
    var vehiclePlate by remember { mutableStateOf("") }

    // Estados de las 2 fotografías
    var idPhotoPath by remember { mutableStateOf<String?>(null) }
    var vehiclePhotoPath by remember { mutableStateOf<String?>(null) }
    var idPhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var vehiclePhotoBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Diálogo de foto ampliada
    var previewPhotoPath by remember { mutableStateOf<String?>(null) }

    // Observar solicitudes activas de autorización
    val allRequests by AccessAuthorizationManager.requests.collectAsState()
    val condoRequests = remember(allRequests, condoTag) {
        allRequests.filter { it.condoId == condoTag || it.condoId.contains(condoTag, ignoreCase = true) }
    }

    // Cámara 1: Identificación Oficial (INE / Licencia)
    val idCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) {
            idPhotoBitmap = bmp
            val savedPath = ImageCaptureHelper.saveBitmapToInternalStorage(context, bmp, "garita_id")
            idPhotoPath = savedPath
            Toast.makeText(context, "📸 Foto 1 (INE/Licencia) capturada", Toast.LENGTH_SHORT).show()
        }
    }

    // Galería de respaldo 1: Identificación
    val idGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = ImageCaptureHelper.copyUriToInternalStorage(context, uri, "garita_id")
            idPhotoPath = savedPath
            if (savedPath != null) {
                idPhotoBitmap = ImageCaptureHelper.loadBitmapFromPath(savedPath, 400, 300)
                Toast.makeText(context, "🪪 Documento cargado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Cámara 2: Vehículo y Placas
    val vehicleCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp: Bitmap? ->
        if (bmp != null) {
            vehiclePhotoBitmap = bmp
            val savedPath = ImageCaptureHelper.saveBitmapToInternalStorage(context, bmp, "garita_auto")
            vehiclePhotoPath = savedPath
            Toast.makeText(context, "📸 Foto 2 (Vehículo y Placas) capturada", Toast.LENGTH_SHORT).show()
        }
    }

    // Galería de respaldo 2: Vehículo
    val vehicleGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = ImageCaptureHelper.copyUriToInternalStorage(context, uri, "garita_auto")
            vehiclePhotoPath = savedPath
            if (savedPath != null) {
                vehiclePhotoBitmap = ImageCaptureHelper.loadBitmapFromPath(savedPath, 400, 300)
                Toast.makeText(context, "🚗 Foto de auto cargada", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        border = BorderStroke(1.5.dp, GoldPrimary.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Cabecera Garita Rápida
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = GoldPrimary,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.ElectricBolt, contentDescription = null, tint = NavyDark, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "FLUJO RÁPIDO · GARITA PRINCIPAL",
                            color = GoldPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "2 Fotos tácticas + Notificación inmediata al Residente",
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SuccessGreen.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, SuccessGreen)
                ) {
                    Text(
                        text = condoTag,
                        color = SuccessGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // =========================================================================
            // 1. SELECTOR DE TIPO: VISITA | PROVEEDOR | PAQUETERÍA
            // =========================================================================
            Text(
                text = "1. Tipo de Ingreso *",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val types = listOf(
                    Triple("Visita", Icons.Default.Person, GoldPrimary),
                    Triple("Proveedor", Icons.Default.Handyman, CyanNeon),
                    Triple("Paquetería", Icons.Default.LocalShipping, SuccessGreen)
                )
                types.forEach { (type, icon, color) ->
                    val isSelected = visitorType == type
                    Surface(
                        onClick = { visitorType = type },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) color.copy(alpha = 0.25f) else NavyCard,
                        border = BorderStroke(1.5.dp, if (isSelected) color else Color(0xFF334155)),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("btn_type_${type.lowercase()}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) color else TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = type,
                                color = if (isSelected) color else Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // =========================================================================
            // 2. DOS FOTOGRAFÍAS TÁCTICAS: IDENTIFICACIÓN Y VEHÍCULO/PLACAS
            // =========================================================================
            Text(
                text = "2. Dos Fotografías de Control *",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // FOTO 1: IDENTIFICACIÓN OFICIAL (INE / LICENCIA)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyDark),
                    border = BorderStroke(1.dp, if (idPhotoPath != null) SuccessGreen else CyanNeon.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🪪 Foto 1: INE / ID",
                                color = if (idPhotoPath != null) SuccessGreen else CyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (idPhotoPath != null) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                            }
                        }

                        if (idPhotoBitmap != null) {
                            Image(
                                bitmap = idPhotoBitmap!!.asImageBitmap(),
                                contentDescription = "Foto ID",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(70.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { previewPhotoPath = idPhotoPath }
                            )
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(70.dp)
                                    .background(NavyCard, RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Badge, contentDescription = null, tint = TextMuted, modifier = Modifier.size(24.dp))
                                    Text("Pendiente", color = TextMuted, fontSize = 9.sp)
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { idCameraLauncher.launch(null) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Cámara", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = {
                                    idGalleryLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = "Galería", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // FOTO 2: VEHÍCULO Y PLACAS
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyDark),
                    border = BorderStroke(1.dp, if (vehiclePhotoPath != null) SuccessGreen else GoldPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "🚗 Foto 2: Vehículo",
                                color = if (vehiclePhotoPath != null) SuccessGreen else GoldPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (vehiclePhotoPath != null) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                            }
                        }

                        if (vehiclePhotoBitmap != null) {
                            Image(
                                bitmap = vehiclePhotoBitmap!!.asImageBitmap(),
                                contentDescription = "Foto Vehículo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(70.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable { previewPhotoPath = vehiclePhotoPath }
                            )
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(70.dp)
                                    .background(NavyCard, RoundedCornerShape(6.dp))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = TextMuted, modifier = Modifier.size(24.dp))
                                    Text("Pendiente", color = TextMuted, fontSize = 9.sp)
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { vehicleCameraLauncher.launch(null) },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Cámara", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = {
                                    vehicleGalleryLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = "Galería", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // 3. DATOS MÍNIMOS: NOMBRE Y DESTINO
            // =========================================================================
            OutlinedTextField(
                value = visitorName,
                onValueChange = { visitorName = it },
                label = { Text("Escribe su nombre completo *", fontSize = 11.sp) },
                placeholder = { Text("Ej: Mario Delgado", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("fast_visitor_name"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = Color(0xFF334155)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = destinationCasa,
                    onValueChange = { destinationCasa = it },
                    label = { Text("Casa Destino *", fontSize = 11.sp) },
                    placeholder = { Text("Ej: Casa 14") },
                    leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1.1f)
                        .testTag("fast_destination_house"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = Color(0xFF334155)
                    )
                )

                OutlinedTextField(
                    value = vehiclePlate,
                    onValueChange = { vehiclePlate = it.uppercase() },
                    label = { Text("Placas (Opcional)", fontSize = 11.sp) },
                    placeholder = { Text("ABC-123") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(0.9f)
                        .testTag("fast_plate"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = Color(0xFF334155)
                    )
                )
            }

            // =========================================================================
            // 4. BOTONES TÁCTICOS: NOTIFICAR AL RESIDENTE O INGRESO DIRECTO
            // =========================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // BOTÓN 1: NOTIFICAR AL RESIDENTE
                Button(
                    onClick = {
                        if (visitorName.isNotBlank() && destinationCasa.isNotBlank()) {
                            val req = AccessAuthorizationManager.submitAccessRequest(
                                context = context,
                                condoId = condoTag,
                                visitorName = visitorName,
                                visitorType = visitorType,
                                destinationHouse = destinationCasa,
                                vehiclePlate = vehiclePlate,
                                idPhotoPath = idPhotoPath,
                                vehiclePhotoPath = vehiclePhotoPath
                            )
                            Toast.makeText(
                                context,
                                "📲 ¡Notificación enviada al residente de $destinationCasa! Esperando autorización...",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(context, "⚠️ Ingrese al menos el nombre y la casa destino", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp)
                        .testTag("btn_notify_resident")
                ) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("NOTIFICAR AL RESIDENTE", fontWeight = FontWeight.Black, fontSize = 11.sp)
                }

                // BOTÓN 2: INGRESO DIRECTO
                Button(
                    onClick = {
                        if (visitorName.isNotBlank() && destinationCasa.isNotBlank()) {
                            val folio = "DIR-${System.currentTimeMillis().toString().takeLast(6)}"
                            scope.launch {
                                visitorRepo.insertCheckIn(
                                    VisitorCheckIn(
                                        folio = folio,
                                        visitorName = visitorName.trim(),
                                        visitorDocument = if (idPhotoPath != null) "Foto ID Garita" else "Acceso Rápido Garita",
                                        destinationHouse = destinationCasa.trim(),
                                        passCode = folio,
                                        passTypeLabel = visitorType,
                                        vehiclePlate = vehiclePlate.trim(),
                                        photoPath = idPhotoPath,
                                        status = "CHECKED_IN",
                                        guardNotes = "Ingreso directo en garita principal $condoTag",
                                        hostResidentName = "Residente $destinationCasa"
                                    )
                                )
                                Toast.makeText(context, "✅ Ingreso directo registrado ($folio)", Toast.LENGTH_SHORT).show()
                                visitorName = ""
                                vehiclePlate = ""
                                idPhotoPath = null
                                vehiclePhotoPath = null
                                idPhotoBitmap = null
                                vehiclePhotoBitmap = null
                            }
                        } else {
                            Toast.makeText(context, "⚠️ Ingrese el nombre del visitante", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_direct_checkin")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ingreso Directo", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }

            // =========================================================================
            // 5. BANDEJA DE SOLICITUDES EN TIEMPO REAL CON ESTADO (APROBADO/PENDIENTE/DENEGADO)
            // =========================================================================
            if (condoRequests.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "📡 Solicitudes Recientes Garita (${condoRequests.size})",
                    color = CyanNeon,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(condoRequests) { req ->
                        val statusBg = when (req.status) {
                            "APPROVED" -> SuccessGreen
                            "DENIED" -> AlertRed
                            else -> GoldPrimary
                        }
                        val statusLabel = when (req.status) {
                            "APPROVED" -> "✅ AUTORIZADO"
                            "DENIED" -> "❌ DENEGADO"
                            else -> "⏳ ESPERANDO RESIDENTE"
                        }

                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = NavyCard),
                            border = BorderStroke(1.dp, statusBg.copy(alpha = 0.6f)),
                            modifier = Modifier.width(230.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = req.visitorName,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(req.formattedTime, color = TextMuted, fontSize = 9.sp)
                                }

                                Text(
                                    text = "${req.visitorType} hacia ${req.destinationHouse}",
                                    color = CyanNeon,
                                    fontSize = 11.sp
                                )

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = statusBg.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, statusBg),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = statusLabel,
                                        color = statusBg,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 3.dp)
                                    )
                                }

                                if (req.status == "APPROVED") {
                                    Button(
                                        onClick = {
                                            val folio = "AUT-${req.id.takeLast(6)}"
                                            scope.launch {
                                                visitorRepo.insertCheckIn(
                                                    VisitorCheckIn(
                                                        folio = folio,
                                                        visitorName = req.visitorName,
                                                        visitorDocument = "Autorizado por Residente",
                                                        destinationHouse = req.destinationHouse,
                                                        passCode = folio,
                                                        passTypeLabel = req.visitorType,
                                                        vehiclePlate = req.vehiclePlate ?: "",
                                                        photoPath = req.idPhotoPath,
                                                        status = "CHECKED_IN",
                                                        guardNotes = "Autorizado por residente de ${req.destinationHouse}",
                                                        hostResidentName = "Residente ${req.destinationHouse}"
                                                    )
                                                )
                                                Toast.makeText(context, "🚪 ¡Pluma abierta! Ingreso confirmado ($folio)", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(32.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("ABRIR PLUMA / DAR ENTRADA", fontSize = 10.sp, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal de previsualización de foto ampliada
    if (previewPhotoPath != null) {
        Dialog(onDismissRequest = { previewPhotoPath = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavyDark),
                border = BorderStroke(1.dp, GoldPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val bmp = remember(previewPhotoPath) {
                        ImageCaptureHelper.loadBitmapFromPath(previewPhotoPath!!, 1024, 1024)
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Foto Ampliada",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    }
                    Button(
                        onClick = { previewPhotoPath = null },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cerrar Vista Previa", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
