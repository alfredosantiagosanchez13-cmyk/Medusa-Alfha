package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import com.example.data.core.AlphaCoreEngine
import com.example.data.incident.IncidentCategory
import com.example.data.incident.IncidentEntity
import com.example.data.incident.IncidentPriority
import com.example.data.passes.QrPassRoomEntity
import com.example.data.vecinos.LosPradosCroquisData
import com.example.data.vecinos.LoteCroquis
import com.example.data.vecinos.PrototipoCasa
import com.example.scanner.PassType
import com.example.ui.theme.*
import com.example.utils.ResidentQrCodeUtility

// =============================================================================
// 1. TARJETA DE INCIDENCIA VINCULADA A CASA DE PRADOS RESIDENCIAL
// =============================================================================

@Composable
fun PradosIncidentCard(
    incident: IncidentEntity,
    onClick: () -> Unit,
    onAttend: () -> Unit,
    onResolve: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val priorityColor = Color(incident.priority.badgeColorHex)
    val statusColor = when (incident.status) {
        "REGISTRADO" -> GoldPrimary
        "EN_ATENCION" -> CyanNeon
        "RESUELTO" -> SuccessGreen
        "CERRADO" -> Color(0xFF9E9E9E)
        else -> GoldPrimary
    }

    // Identificar prototipo si la ubicación contiene número de casa
    val matchedLote = remember(incident.location) {
        val numMatch = Regex("""Casa\s+(\d+)""", RegexOption.IGNORE_CASE).find(incident.location)
        numMatch?.groupValues?.get(1)?.toIntOrNull()?.let { num ->
            LosPradosCroquisData.TODOS_LOS_LOTES.find { it.numero == num }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("incident_card_${incident.folio}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        border = BorderStroke(
            1.dp,
            if (incident.priority == IncidentPriority.CRITICA && incident.status != "RESUELTO") {
                ErrorRed.copy(alpha = 0.8f)
            } else {
                Color.White.copy(alpha = 0.10f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Fila Superior: Folio + Badge de Prioridad + SLA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NavyDark,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Text(
                            text = incident.folio,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = GoldPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = priorityColor.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, priorityColor.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = incident.priority.displayName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = priorityColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Estado de SLA
                val slaExceeded = incident.isSlaExceeded()
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (slaExceeded) ErrorRed.copy(alpha = 0.2f) else SuccessGreen.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (slaExceeded) ErrorRed.copy(alpha = 0.5f) else SuccessGreen.copy(alpha = 0.4f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (slaExceeded) Icons.Default.Warning else Icons.Default.Timer,
                            contentDescription = null,
                            tint = if (slaExceeded) ErrorRed else SuccessGreen,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (slaExceeded) "SLA Excedido" else "${incident.targetSlaMinutes}m Meta",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (slaExceeded) ErrorRed else SuccessGreen
                        )
                    }
                }
            }

            // Ubicación Real en Prados Residencial (con Prototipo de Casa)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = NavyDark.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = matchedLote?.prototipo?.color ?: GoldPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = incident.location,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    if (matchedLote != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = matchedLote.prototipo.color.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, matchedLote.prototipo.color.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = matchedLote.prototipo.codigo,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = matchedLote.prototipo.color,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // Categoría + Resumen de la Incidencia
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = incident.category.iconName,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = incident.category.displayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GoldPrimary
                    )
                }

                Text(
                    text = incident.aiSummary.ifBlank { incident.rawTranscript },
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Stepper Visual de Estado (4 Pasos)
            IncidentStatusStepper(currentStatus = incident.status)

            // Fila Inferior: Tiempo transcurrido y Botones de Acción
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = incident.getElapsedTimeFormatted(),
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (incident.status == "REGISTRADO") {
                        Button(
                            onClick = onAttend,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanNeon.copy(alpha = 0.18f),
                                contentColor = CyanNeon
                            ),
                            border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("btn_attend_${incident.folio}")
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Atender", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (incident.status == "EN_ATENCION") {
                        Button(
                            onClick = onResolve,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SuccessGreen.copy(alpha = 0.2f),
                                contentColor = SuccessGreen
                            ),
                            border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("btn_resolve_${incident.folio}")
                        ) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resolver", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = onClick,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("btn_detail_${incident.folio}")
                    ) {
                        Text("Ver Detalle", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/**
 * Stepper visual para el ciclo de vida de la incidencia
 */
@Composable
private fun IncidentStatusStepper(currentStatus: String) {
    val steps = listOf("REGISTRADO", "EN_ATENCION", "RESUELTO", "CERRADO")
    val currentIndex = when (currentStatus) {
        "REGISTRADO" -> 0
        "EN_ATENCION" -> 1
        "RESUELTO" -> 2
        "CERRADO" -> 3
        else -> 0
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, stepName ->
            val isPassedOrCurrent = index <= currentIndex
            val isCurrent = index == currentIndex
            val color = when {
                index < currentIndex -> SuccessGreen
                isCurrent -> GoldPrimary
                else -> Color.White.copy(alpha = 0.2f)
            }

            Box(
                modifier = Modifier
                    .size(if (isCurrent) 10.dp else 8.dp)
                    .background(color, CircleShape)
            )

            if (index < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(
                            if (index < currentIndex) SuccessGreen.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f)
                        )
                )
            }
        }
    }
}

// =============================================================================
// 2. TARJETA DE PASE QR VINCULADO A CASA DE PRADOS RESIDENCIAL
// =============================================================================

@Composable
fun PradosQrPassCard(
    pass: QrPassRoomEntity,
    onViewQr: () -> Unit,
    onSimulateEntry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isValid = pass.isValidForEntry

    // Extraer prototipo de casa si coincide
    val matchedLote = remember(pass.destinationHouse) {
        val numMatch = Regex("""Casa\s+(\d+)""", RegexOption.IGNORE_CASE).find(pass.destinationHouse)
        numMatch?.groupValues?.get(1)?.toIntOrNull()?.let { num ->
            LosPradosCroquisData.TODOS_LOS_LOTES.find { it.numero == num }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onViewQr() }
            .testTag("qr_pass_card_${pass.passCode}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        border = BorderStroke(
            1.dp,
            if (isValid) CyanNeon.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Cabecera: Folio QR + Estado (Válido / Expirado / Agotado)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = null,
                        tint = CyanNeon,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = pass.passCode,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        isValid -> SuccessGreen.copy(alpha = 0.15f)
                        pass.isExhausted -> Color(0xFFF97316).copy(alpha = 0.15f)
                        else -> ErrorRed.copy(alpha = 0.15f)
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            isValid -> SuccessGreen.copy(alpha = 0.4f)
                            pass.isExhausted -> Color(0xFFF97316).copy(alpha = 0.4f)
                            else -> ErrorRed.copy(alpha = 0.4f)
                        }
                    )
                ) {
                    Text(
                        text = when {
                            isValid -> "VÁLIDO"
                            pass.isExhausted -> "AGOTADO"
                            else -> "EXPIRADO"
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isValid -> SuccessGreen
                            pass.isExhausted -> Color(0xFFF97316)
                            else -> ErrorRed
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Destino en Prados Residencial
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = NavyDark.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = matchedLote?.prototipo?.color ?: GoldPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = pass.destinationHouse,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    if (matchedLote != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = matchedLote.prototipo.color.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, matchedLote.prototipo.color.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = matchedLote.prototipo.codigo,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = matchedLote.prototipo.color,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // Datos del Visitante y Residente Anfitrión
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Visitante", fontSize = 10.sp, color = TextMuted)
                        Text(
                            text = pass.guestName,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Doc: ${pass.guestDocument}",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "Anfitrión", fontSize = 10.sp, color = TextMuted)
                        Text(
                            text = pass.hostResidentName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = GoldPrimary
                        )
                        if (!pass.vehiclePlate.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = pass.vehiclePlate,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyanNeon
                                )
                            }
                        }
                    }
                }
            }

            // Fila de Vigencia e Interacciones
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Vence: ${pass.formattedValidUntil}",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isValid) {
                        Button(
                            onClick = onSimulateEntry,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanNeon.copy(alpha = 0.15f),
                                contentColor = CyanNeon
                            ),
                            border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("btn_simulate_entry_${pass.passCode}")
                        ) {
                            Icon(imageVector = Icons.Default.Login, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ingreso", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onViewQr,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            contentColor = NavyDark
                        ),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("btn_view_qr_${pass.passCode}")
                    ) {
                        Icon(imageVector = Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Ver QR", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// =============================================================================
// 3. SELECTOR DE CASAS DE PRADOS RESIDENCIAL (BARRA DE FILTRO)
// =============================================================================

@Composable
fun PradosHouseFilterBar(
    selectedHouse: LoteCroquis?,
    onSelectHouse: (LoteCroquis?) -> Unit,
    onOpenHousePicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = NavySurface,
        border = BorderStroke(
            1.dp,
            if (selectedHouse != null) GoldPrimary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.10f)
        )
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
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenHousePicker() }
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            (selectedHouse?.prototipo?.color ?: GoldPrimary).copy(alpha = 0.2f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = selectedHouse?.prototipo?.color ?: GoldPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = if (selectedHouse != null) "Filtrado por Casa:" else "Filtrar por Casa de Prados:",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                    Text(
                        text = selectedHouse?.labelCompleto ?: "Todas las Casas (200 Lotes Prados)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedHouse != null) GoldPrimary else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (selectedHouse != null) {
                IconButton(
                    onClick = { onSelectHouse(null) },
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Limpiar filtro de casa",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onOpenHousePicker,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Seleccionar", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =============================================================================
// 4. DIÁLOGO SELECTOR DE CASAS DE PRADOS RESIDENCIAL
// =============================================================================

@Composable
fun PradosHousePickerDialog(
    onDismiss: () -> Unit,
    onHouseSelected: (LoteCroquis) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCondoTab by remember { mutableStateOf("ALL") }

    val filteredLots = remember(searchQuery, selectedCondoTab) {
        val base = when (selectedCondoTab) {
            "PRADOS_1" -> LosPradosCroquisData.LOTES_PRADOS_1
            "PRADOS_2" -> LosPradosCroquisData.LOTES_PRADOS_2
            "PRADOS_3" -> LosPradosCroquisData.LOTES_PRADOS_3
            else -> LosPradosCroquisData.TODOS_LOS_LOTES
        }
        if (searchQuery.isBlank()) base else {
            val q = searchQuery.trim().lowercase()
            base.filter {
                it.numero.toString().contains(q) ||
                it.calle.lowercase().contains(q) ||
                it.prototipo.nombre.lowercase().contains(q) ||
                it.nombreCondominio.lowercase().contains(q)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(18.dp),
            color = NavyCard,
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Cabecera
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Casas Prados Residencial",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "2750 Av. de la Cantera · 3 Condominios",
                            fontSize = 10.sp,
                            color = CyanNeon
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                // Tabs de Condominio
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = listOf(
                        "ALL" to "Todos (200)",
                        "PRADOS_1" to "Prados 1",
                        "PRADOS_2" to "Prados 2",
                        "PRADOS_3" to "Prados 3"
                    )
                    tabs.forEach { (id, label) ->
                        val isSelected = selectedCondoTab == id
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) GoldPrimary else NavySurface,
                            border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedCondoTab = id }
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) NavyDark else Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        }
                    }
                }

                // Campo de Búsqueda
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar por número de casa o calle...", fontSize = 11.sp, color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NavySurface,
                        unfocusedContainerColor = NavySurface,
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Mostrando ${filteredLots.size} casas registradas",
                    fontSize = 10.sp,
                    color = TextMuted
                )

                // Lista de Casas
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredLots) { lote ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = NavySurface,
                            border = BorderStroke(1.dp, lote.prototipo.color.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onHouseSelected(lote)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(lote.prototipo.color.copy(alpha = 0.2f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${lote.numero}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = lote.prototipo.color
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = lote.labelCasa,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "${lote.calle} · ${lote.nombreCondominio}",
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = lote.prototipo.color.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, lote.prototipo.color.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = lote.prototipo.codigo,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = lote.prototipo.color,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// 5. MODAL DE DETALLE DE INCIDENCIA
// =============================================================================

@Composable
fun PradosIncidentDetailDialog(
    incident: IncidentEntity,
    onDismiss: () -> Unit,
    onAttend: () -> Unit,
    onResolve: () -> Unit
) {
    val context = LocalContext.current
    val priorityColor = Color(incident.priority.badgeColorHex)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(18.dp),
            color = NavyCard,
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Cabecera
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Expediente de Incidencia",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = incident.folio,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        // Estado y Prioridad
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = priorityColor.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, priorityColor.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Prioridad", fontSize = 9.sp, color = TextMuted)
                                    Text(incident.priority.displayName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = priorityColor)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CyanNeon.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.4f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Estado", fontSize = 9.sp, color = TextMuted)
                                    Text(incident.status, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CyanNeon)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = GoldPrimary.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("SLA Objetivo", fontSize = 9.sp, color = TextMuted)
                                    Text("${incident.targetSlaMinutes} min", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                                }
                            }
                        }
                    }

                    item {
                        // Ubicación en Prados Residencial
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = NavySurface,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Ubicación Oficial", fontSize = 10.sp, color = TextMuted)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Home, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(incident.location, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Text("2750 Avenida de la Cantera, Qro.", fontSize = 10.sp, color = CyanNeon)
                            }
                        }
                    }

                    item {
                        // Descripción y Resumen IA
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = NavySurface,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Diagnóstico y Resumen Operativo", fontSize = 10.sp, color = TextMuted)
                                Text(incident.aiSummary.ifBlank { incident.rawTranscript }, fontSize = 12.sp, color = Color.White)

                                if (incident.recommendedAction.isNotBlank()) {
                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                    Text("Acción Recomendada", fontSize = 10.sp, color = GoldPrimary, fontWeight = FontWeight.Bold)
                                    Text(incident.recommendedAction, fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f))
                                }
                            }
                        }
                    }

                    if (!incident.resolutionNotes.isNullOrBlank()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = SuccessGreen.copy(alpha = 0.1f),
                                border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Dictamen de Resolución", fontSize = 10.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                                    Text(incident.resolutionNotes ?: "", fontSize = 12.sp, color = Color.White)
                                    incident.resolvedBy?.let {
                                        Text("Resuelto por: $it", fontSize = 10.sp, color = TextMuted)
                                    }
                                }
                            }
                        }
                    }

                    item {
                        // Reportante y Asignación
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = NavySurface,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Trazabilidad y Responsables", fontSize = 10.sp, color = TextMuted)
                                Text("Reportado por: ${incident.reportedBy} (${incident.reportedByRole})", fontSize = 11.sp, color = Color.White)
                                Text("Asignado a: ${incident.assignedTo} (${incident.assignedRole})", fontSize = 11.sp, color = CyanNeon)
                                Text("Registrado: ${incident.formattedDate} (${incident.getElapsedTimeFormatted()})", fontSize = 10.sp, color = TextMuted)
                            }
                        }
                    }
                }

                // Acciones Inferiores
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (incident.status == "REGISTRADO") {
                        Button(
                            onClick = {
                                onAttend()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Atender Incidencia", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (incident.status == "EN_ATENCION") {
                        Button(
                            onClick = {
                                onResolve()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Resolver Incidencia", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cerrar", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// =============================================================================
// 6. MODAL PARA REPORTAR NUEVA INCIDENCIA EN PRADOS RESIDENCIAL
// =============================================================================

@Composable
fun PradosReportIncidentDialog(
    onDismiss: () -> Unit,
    onSaveIncident: (IncidentEntity) -> Unit
) {
    val context = LocalContext.current
    var selectedLote by remember { mutableStateOf<LoteCroquis?>(null) }
    var showHousePicker by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(IncidentCategory.PARKING_VIALIDAD) }
    var selectedPriority by remember { mutableStateOf(IncidentPriority.ALTA) }
    var descriptionText by remember { mutableStateOf("") }
    var reportedByName by remember { mutableStateOf("Guardia de Caseta Principal") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(18.dp),
            color = NavyCard,
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Reportar Incidencia",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Prados Residencial · Registro Táctico",
                            fontSize = 10.sp,
                            color = GoldPrimary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Selección de Casa
                    item {
                        Text("Casa Vinculada (Obligatorio)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = NavySurface,
                            border = BorderStroke(1.dp, if (selectedLote != null) GoldPrimary else Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showHousePicker = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = null,
                                        tint = selectedLote?.prototipo?.color ?: GoldPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = selectedLote?.labelCompleto ?: "Toca para seleccionar Casa...",
                                        fontSize = 12.sp,
                                        fontWeight = if (selectedLote != null) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedLote != null) Color.White else TextMuted
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = GoldPrimary)
                            }
                        }
                    }

                    // 2. Categoría
                    item {
                        Text("Categoría", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(IncidentCategory.values()) { cat ->
                                val isSelected = selectedCategory == cat
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) GoldPrimary else NavySurface,
                                    border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.15f)),
                                    modifier = Modifier.clickable { selectedCategory = cat }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(text = cat.iconName, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = cat.displayName,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) NavyDark else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Prioridad
                    item {
                        Text("Prioridad", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IncidentPriority.values().forEach { prio ->
                                val isSelected = selectedPriority == prio
                                val pColor = Color(prio.badgeColorHex)
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) pColor else NavySurface,
                                    border = BorderStroke(1.dp, if (isSelected) pColor else pColor.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedPriority = prio }
                                ) {
                                    Text(
                                        text = prio.displayName,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) NavyDark else pColor,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 4. Descripción
                    item {
                        Text("Descripción / Hechos", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        OutlinedTextField(
                            value = descriptionText,
                            onValueChange = { descriptionText = it },
                            placeholder = { Text("Detalla la incidencia en el lote...", fontSize = 11.sp, color = TextMuted) },
                            minLines = 3,
                            maxLines = 5,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = NavySurface,
                                unfocusedContainerColor = NavySurface,
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 5. Reportante
                    item {
                        Text("Reportante", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        OutlinedTextField(
                            value = reportedByName,
                            onValueChange = { reportedByName = it },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = NavySurface,
                                unfocusedContainerColor = NavySurface,
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Botón Guardar
                Button(
                    onClick = {
                        val lote = selectedLote
                        if (lote == null) {
                            Toast.makeText(context, "Por favor selecciona una casa de Prados", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (descriptionText.isBlank()) {
                            Toast.makeText(context, "Ingresa una descripción", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val folio = AlphaCoreEngine.generateUniqueFolio("INC")
                        val now = System.currentTimeMillis()
                        val targetSla = when (selectedPriority) {
                            IncidentPriority.CRITICA -> 15
                            IncidentPriority.ALTA -> 45
                            IncidentPriority.MEDIA -> 180
                            IncidentPriority.BAJA -> 1440
                        }

                        val newIncident = IncidentEntity(
                            folio = folio,
                            rawTranscript = descriptionText,
                            category = selectedCategory,
                            priority = selectedPriority,
                            location = lote.labelCompleto,
                            aiSummary = descriptionText.take(120),
                            recommendedAction = "Despachar personal operativo a ${lote.labelCasa} en ${lote.calle}",
                            timestampMillis = now,
                            guardName = reportedByName,
                            reportedBy = reportedByName,
                            reportedByRole = "GUARDIA",
                            status = "REGISTRADO",
                            assignedTo = "Oficial de Turno",
                            assignedRole = "GUARDIA",
                            targetSlaMinutes = targetSla,
                            locationStatus = "CONFIRMADO_EN_LOTE"
                        )

                        onSaveIncident(newIncident)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_submit_incident"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Registrar Incidencia", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showHousePicker) {
        PradosHousePickerDialog(
            onDismiss = { showHousePicker = false },
            onHouseSelected = { lote ->
                selectedLote = lote
                showHousePicker = false
            }
        )
    }
}

// =============================================================================
// 7. MODAL PARA RESOLVER INCIDENCIA
// =============================================================================

@Composable
fun PradosResolveIncidentDialog(
    incident: IncidentEntity,
    onDismiss: () -> Unit,
    onConfirmResolve: (String) -> Unit
) {
    var resolutionNotes by remember { mutableStateOf("") }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = NavyCard,
            border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Resolver Incidencia", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(incident.folio, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = GoldPrimary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                Text(
                    text = "Ubicación: ${incident.location}",
                    fontSize = 12.sp,
                    color = CyanNeon
                )

                Text("Dictamen y Acciones Realizadas", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                OutlinedTextField(
                    value = resolutionNotes,
                    onValueChange = { resolutionNotes = it },
                    placeholder = { Text("Escribe las notas de solución concluyente...", fontSize = 11.sp, color = TextMuted) },
                    minLines = 3,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = NavySurface,
                        unfocusedContainerColor = NavySurface,
                        focusedBorderColor = SuccessGreen,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancelar", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            if (resolutionNotes.isBlank()) {
                                Toast.makeText(context, "Ingresa las notas de dictamen", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            onConfirmResolve(resolutionNotes)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Finalizar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// =============================================================================
// 8. VISOR DE CÓDIGO QR EN ALTA RESOLUCIÓN
// =============================================================================

@Composable
fun PradosQrCodeViewerDialog(
    pass: QrPassRoomEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val qrBitmap = remember(pass.passCode) {
        ResidentQrCodeUtility.generateQrBitmap(pass.passCode, sizePx = 512)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = NavyCard,
            border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Pase QR de Acceso", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(pass.destinationHouse, fontSize = 11.sp, color = CyanNeon, fontWeight = FontWeight.Medium)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                // Imagen del Código QR en contenedor blanco de alto contraste
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    modifier = Modifier.size(230.dp),
                    shadowElevation = 8.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = "Código QR para ${pass.guestName}",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("Generando QR...", color = Color.Black)
                        }
                    }
                }

                // Folio y Datos
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NavySurface,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Folio Único:", fontSize = 10.sp, color = TextMuted)
                            Text(
                                text = pass.passCode,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = GoldPrimary
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Visitante:", fontSize = 10.sp, color = TextMuted)
                            Text(pass.guestName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Vigente Hasta:", fontSize = 10.sp, color = TextMuted)
                            Text(pass.formattedValidUntil, fontSize = 10.sp, color = SuccessGreen)
                        }
                    }
                }

                // Botones Compartir / Copiar Folio
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Folio QR", pass.passCode))
                            Toast.makeText(context, "Folio copiado: ${pass.passCode}", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NavySurface, contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copiar", fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Pase de Acceso Prados Residencial\nFolio: ${pass.passCode}\nDestino: ${pass.destinationHouse}\nVisitante: ${pass.guestName}\nVigencia: ${pass.formattedValidUntil}\nPresentar en Garita Principal (2750 Av. de la Cantera)"
                                )
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartir Pase QR"))
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Compartir", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// =============================================================================
// 9. DIÁLOGO PARA GENERAR PASE QR VINCULADO A CASA DE PRADOS RESIDENCIAL
// =============================================================================

@Composable
fun PradosCreateQrPassDialog(
    onDismiss: () -> Unit,
    onSavePass: (QrPassRoomEntity) -> Unit
) {
    val context = LocalContext.current
    var selectedLote by remember { mutableStateOf<LoteCroquis?>(null) }
    var showHousePicker by remember { mutableStateOf(false) }
    var guestName by remember { mutableStateOf("") }
    var guestDoc by remember { mutableStateOf("") }
    var hostResidentName by remember { mutableStateOf("Familia Residente") }
    var vehiclePlate by remember { mutableStateOf("") }
    var durationHours by remember { mutableStateOf(12) }
    var passType by remember { mutableStateOf(PassType.VISITOR_SINGLE) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(18.dp),
            color = NavyCard,
            border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Generar Pase QR", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Prados Residencial · Garita Digital", fontSize = 10.sp, color = CyanNeon)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Casa de Destino
                    item {
                        Text("Casa Destino en Prados Residencial", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = NavySurface,
                            border = BorderStroke(1.dp, if (selectedLote != null) GoldPrimary else Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showHousePicker = true }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = null,
                                        tint = selectedLote?.prototipo?.color ?: GoldPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = selectedLote?.labelCompleto ?: "Toca para elegir Casa (1 a 200)...",
                                        fontSize = 12.sp,
                                        fontWeight = if (selectedLote != null) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedLote != null) Color.White else TextMuted
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = GoldPrimary)
                            }
                        }
                    }

                    // Visitante
                    item {
                        Text("Nombre del Visitante", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        OutlinedTextField(
                            value = guestName,
                            onValueChange = { guestName = it },
                            placeholder = { Text("Ej. Lic. Mariana Solís", fontSize = 11.sp, color = TextMuted) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = NavySurface,
                                unfocusedContainerColor = NavySurface,
                                focusedBorderColor = CyanNeon,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Documento / Identificación
                    item {
                        Text("Identificación / INE (Opcional)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        OutlinedTextField(
                            value = guestDoc,
                            onValueChange = { guestDoc = it },
                            placeholder = { Text("Ej. INE-8291039", fontSize = 11.sp, color = TextMuted) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = NavySurface,
                                unfocusedContainerColor = NavySurface,
                                focusedBorderColor = CyanNeon,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Placa Vehicular
                    item {
                        Text("Placas Vehiculares (Opcional)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        OutlinedTextField(
                            value = vehiclePlate,
                            onValueChange = { vehiclePlate = it.uppercase() },
                            placeholder = { Text("Ej. ULM-123-A", fontSize = 11.sp, color = TextMuted) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = NavySurface,
                                unfocusedContainerColor = NavySurface,
                                focusedBorderColor = CyanNeon,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Duración
                    item {
                        Text("Vigencia del Pase", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(4 to "4h", 12 to "12h", 24 to "24h", 72 to "3 días").forEach { (hours, label) ->
                                val isSelected = durationHours == hours
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) CyanNeon else NavySurface,
                                    border = BorderStroke(1.dp, if (isSelected) CyanNeon else Color.White.copy(alpha = 0.15f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { durationHours = hours }
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) NavyDark else Color.White,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Botón Guardar
                Button(
                    onClick = {
                        val lote = selectedLote
                        if (lote == null) {
                            Toast.makeText(context, "Por favor selecciona una casa de Prados", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (guestName.isBlank()) {
                            Toast.makeText(context, "Ingresa el nombre del visitante", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val folio = AlphaCoreEngine.generateUniqueFolio("MED")
                        val now = System.currentTimeMillis()
                        val validUntil = now + (durationHours.toLong() * 3600 * 1000L)

                        val newPass = QrPassRoomEntity(
                            passCode = folio,
                            guestName = guestName.trim(),
                            guestDocument = guestDoc.trim().ifBlank { "Verificar en Garita" },
                            destinationHouse = lote.labelCompleto,
                            hostResidentName = hostResidentName,
                            vehiclePlate = vehiclePlate.trim().takeIf { it.isNotBlank() },
                            passType = passType,
                            validUntilMillis = validUntil,
                            maxEntries = if (durationHours > 24) 5 else 1,
                            currentEntriesCount = 0,
                            note = "Pase generado desde Dashboard Principal",
                            isActive = true
                        )

                        onSavePass(newPass)
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_confirm_create_qr"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark)
                ) {
                    Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Emitir Pase QR", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showHousePicker) {
        PradosHousePickerDialog(
            onDismiss = { showHousePicker = false },
            onHouseSelected = { lote ->
                selectedLote = lote
                showHousePicker = false
            }
        )
    }
}
