package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.visitor.VisitorCheckInRepository
import com.example.ui.theme.*
import com.example.utils.AccessAuthorizationManager
import com.example.utils.AccessAuthorizationRequest
import com.example.utils.ImageCaptureHelper

/**
 * Banner Interactivo de Autorización Inmediata para el Residente.
 * Se despliega prominentemente cuando un guardia en garita notifica la llegada de una visita,
 * proveedor o paquetería con las 2 fotos tácticas (Identificación Oficial y Vehículo/Placas).
 * Permite autorizar o denegar en 1 solo toque.
 */
@Composable
fun ResidentAccessAuthorizationBanner(
    residentUnit: String?,
    visitorRepo: VisitorCheckInRepository? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allRequests by AccessAuthorizationManager.requests.collectAsState()

    // Filtrar solicitudes pendientes aplicables a la casa del residente (o todas si no está configurada la unidad específica)
    val pendingRequests = remember(allRequests, residentUnit) {
        allRequests.filter { req ->
            req.status == "PENDING" && (
                residentUnit.isNullOrBlank() ||
                req.destinationHouse.contains(residentUnit, ignoreCase = true) ||
                residentUnit.contains(req.destinationHouse, ignoreCase = true)
            )
        }
    }

    var selectedPreviewPhoto by remember { mutableStateOf<String?>(null) }

    AnimatedVisibility(
        visible = pendingRequests.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            pendingRequests.forEach { req ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("authorization_request_card_${req.id}"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    border = BorderStroke(2.dp, GoldPrimary)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Cabecera de Alerta
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "🚨 VISITA EN GARITA PRINCIPAL",
                                    color = GoldPrimary,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                )
                            }
                            Text(
                                text = req.formattedTime,
                                color = TextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Detalle del Visitante
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = req.visitorName,
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = CyanNeon.copy(alpha = 0.2f),
                                    border = BorderStroke(1.dp, CyanNeon)
                                ) {
                                    Text(
                                        text = req.visitorType,
                                        color = CyanNeon,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = "Destino: ${req.destinationHouse}",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    modifier = Modifier.align(Alignment.CenterVertically)
                                )
                                if (!req.vehiclePlate.isNullOrBlank()) {
                                    Text(
                                        text = "• Placas: ${req.vehiclePlate}",
                                        color = GoldPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    )
                                }
                            }
                        }

                        // Muestra de las 2 Fotografías de Garita (Identificación y Vehículo)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Foto 1: INE / Identificación
                            val idBmp = remember(req.idPhotoPath) {
                                req.idPhotoPath?.let { ImageCaptureHelper.loadBitmapFromPath(it, 300, 200) }
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = NavyDark,
                                border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(80.dp)
                                    .clickable { selectedPreviewPhoto = req.idPhotoPath }
                            ) {
                                if (idBmp != null) {
                                    Image(
                                        bitmap = idBmp.asImageBitmap(),
                                        contentDescription = "Foto INE",
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                    )
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(Icons.Default.Badge, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(24.dp))
                                        Text("Foto INE / ID", color = TextMuted, fontSize = 10.sp)
                                    }
                                }
                            }

                            // Foto 2: Vehículo y Placas
                            val vehBmp = remember(req.vehiclePhotoPath) {
                                req.vehiclePhotoPath?.let { ImageCaptureHelper.loadBitmapFromPath(it, 300, 200) }
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = NavyDark,
                                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(80.dp)
                                    .clickable { selectedPreviewPhoto = req.vehiclePhotoPath }
                            ) {
                                if (vehBmp != null) {
                                    Image(
                                        bitmap = vehBmp.asImageBitmap(),
                                        contentDescription = "Foto Vehículo",
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                    )
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(24.dp))
                                        Text("Foto Vehículo", color = TextMuted, fontSize = 10.sp)
                                    }
                                }
                            }
                        }

                        // Botones de Decisión Inmediata
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    AccessAuthorizationManager.authorizeRequest(
                                        context = context,
                                        requestId = req.id,
                                        visitorRepo = visitorRepo
                                    ) {
                                        Toast.makeText(context, "✅ Acceso AUTORIZADO. Notificando a Garita...", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(44.dp)
                                    .testTag("btn_authorize_visitor_${req.id}")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("AUTORIZAR ACCESO", fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    AccessAuthorizationManager.denyRequest(
                                        context = context,
                                        requestId = req.id
                                    ) {
                                        Toast.makeText(context, "❌ Acceso DENEGADO. Notificando a Garita...", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AlertRed, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(44.dp)
                                    .testTag("btn_deny_visitor_${req.id}")
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("DENEGAR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal de ampliación de fotografía
    if (selectedPreviewPhoto != null) {
        Dialog(onDismissRequest = { selectedPreviewPhoto = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavyDark),
                border = BorderStroke(1.dp, GoldPrimary),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val bmp = remember(selectedPreviewPhoto) {
                        ImageCaptureHelper.loadBitmapFromPath(selectedPreviewPhoto!!, 1024, 1024)
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
                        onClick = { selectedPreviewPhoto = null },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cerrar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
