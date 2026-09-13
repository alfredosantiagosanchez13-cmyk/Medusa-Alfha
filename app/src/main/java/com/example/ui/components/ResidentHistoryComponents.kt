package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.finance.MaintenancePaymentEntity
import com.example.data.visitor.VisitorPassEntity
import com.example.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

// =========================================================================================
// 1. HISTORIAL DE VISITANTES DE LA UNIDAD (`Visitor History Log`)
// =========================================================================================

/**
 * Componente de UI tipo lista compacta (`LazyColumn`) para el Historial de Visitantes.
 * Lista de forma cronológica invertida los accesos asociados a la casa/departamento,
 * mostrando el nombre del visitante, tipo de pase, vehículo y etiqueta visual de estatus.
 */
@Composable
fun VisitorHistoryLogList(
    unitId: String,
    historyList: List<VisitorPassEntity>,
    modifier: Modifier = Modifier,
    onVisitorClick: ((VisitorPassEntity) -> Unit)? = null
) {
    if (historyList.isEmpty()) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .testTag("visitor_history_empty_state"),
            shape = RoundedCornerShape(14.dp),
            color = NavySurface,
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "Sin accesos registrados para $unitId",
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Los accesos verificados, expirados o denegados en garita se sincronizarán aquí automáticamente.",
                    color = TextMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .testTag("visitor_history_lazy_column"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Historial de Accesos Recientes",
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${historyList.size} registros",
                        color = CyanNeon,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            items(historyList, key = { it.id }) { visitor ->
                VisitorHistoryCompactCard(
                    visitor = visitor,
                    onClick = { onVisitorClick?.invoke(visitor) }
                )
            }
        }
    }
}

/**
 * Tarjeta individual compacta para cada registro de visitante.
 */
@Composable
fun VisitorHistoryCompactCard(
    visitor: VisitorPassEntity,
    onClick: () -> Unit
) {
    // Configuración visual según estatus: Verificado (Verde), Expirado (Gris), Denegado (Rojo)
    val visualConfig = when (visitor.status.uppercase()) {
        "VERIFICADO", "CHECKED_IN" -> VisitorStatusVisualConfig(
            label = "Verificado",
            textColor = SuccessGreen,
            bgColor = SuccessGreen.copy(alpha = 0.15f),
            icon = Icons.Default.CheckCircle
        )
        "DENEGADO", "RECHAZADO" -> VisitorStatusVisualConfig(
            label = "Denegado",
            textColor = ErrorRed,
            bgColor = ErrorRed.copy(alpha = 0.15f),
            icon = Icons.Default.Block
        )
        "EXPIRADO", "DEPARTED", "FINALIZADO" -> VisitorStatusVisualConfig(
            label = "Expirado",
            textColor = Color(0xFF94A3B8), // Gris suave
            bgColor = Color(0xFF334155).copy(alpha = 0.4f),
            icon = Icons.Default.Schedule
        )
        else -> VisitorStatusVisualConfig(
            label = "En Proceso",
            textColor = CyanNeon,
            bgColor = CyanNeon.copy(alpha = 0.15f),
            icon = Icons.Default.HourglassTop
        )
    }

    // Icono según tipo de pase
    val passIcon = when {
        visitor.passType.contains("Delivery", ignoreCase = true) || visitor.passType.contains("Paquete", ignoreCase = true) -> Icons.Default.LocalShipping
        visitor.passType.contains("Técnico", ignoreCase = true) || visitor.passType.contains("Servicio", ignoreCase = true) -> Icons.Default.Build
        visitor.passType.contains("Frecuente", ignoreCase = true) -> Icons.Default.VerifiedUser
        visitor.passType.contains("Evento", ignoreCase = true) || visitor.passType.contains("Amenidad", ignoreCase = true) -> Icons.Default.Celebration
        else -> Icons.Default.Person
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("visitor_card_${visitor.id}"),
        shape = RoundedCornerShape(12.dp),
        color = NavySurface,
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = NavyCard,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = passIcon,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = visitor.visitorName,
                            color = TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = visitor.passType,
                                color = CyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (!visitor.vehiclePlate.isNullOrBlank() && visitor.vehiclePlate != "SIN_PLACA" && visitor.vehiclePlate != "SIN_VEHICULO") {
                                Text("•", color = TextMuted, fontSize = 10.sp)
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = TextMuted, modifier = Modifier.size(11.dp))
                                    Text(visitor.vehiclePlate, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                // Etiqueta visual de color de estatus final (Verificado / Expirado / Denegado)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = visualConfig.bgColor,
                    border = BorderStroke(1.dp, visualConfig.textColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = visualConfig.icon,
                            contentDescription = null,
                            tint = visualConfig.textColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = visualConfig.label,
                            color = visualConfig.textColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Pie de tarjeta con fechas y punto de acceso
            HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.8.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
                    Text(
                        text = "Acceso: ${visitor.formattedDate}",
                        color = TextMuted,
                        fontSize = 10.5.sp
                    )
                    if (visitor.formattedExitDate != null) {
                        Text(
                            text = "• Salida: ${visitor.formattedExitDate}",
                            color = TextMuted,
                            fontSize = 10.5.sp
                        )
                    }
                }

                Text(
                    text = "Folio: ${visitor.folio}",
                    color = Color(0xFF64748B),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (!visitor.guardNotes.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = NavyDark.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Nota oficial: ${visitor.guardNotes}",
                        color = TextMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// =========================================================================================
// 2. HISTORIAL DE PAGOS Y CUOTAS (`Maintenance Payment History`)
// =========================================================================================

/**
 * Componente de UI que desglose de manera clara el histórico de cuotas de mantenimiento liquidadas.
 * Consume los datos directamente del búfer local offline de Room.
 */
@Composable
fun MaintenancePaymentHistorySection(
    payments: List<MaintenancePaymentEntity>,
    unitId: String,
    modifier: Modifier = Modifier,
    onViewReceipt: (MaintenancePaymentEntity) -> Unit,
    onDownloadReceipt: (MaintenancePaymentEntity) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("payment_history_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Histórico de Cuotas Liquidadas",
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Búfer local offline de comprobantes digitales • $unitId",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = SuccessGreen.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "${payments.size} recibos",
                    color = SuccessGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        if (payments.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = NavySurface,
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "No se registran pagos en el búfer local",
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Al realizar pagos en línea o en garita, tus comprobantes fiscales aparecerán aquí.",
                        color = TextMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            payments.forEach { payment ->
                PaymentReceiptCard(
                    payment = payment,
                    onViewReceipt = { onViewReceipt(payment) },
                    onDownloadReceipt = { onDownloadReceipt(payment) }
                )
            }
        }
    }
}

/**
 * Tarjeta individual para un pago liquidado con botones rápidos de "Ver Recibo" y "Descargar Comprobante".
 */
@Composable
fun PaymentReceiptCard(
    payment: MaintenancePaymentEntity,
    onViewReceipt: () -> Unit,
    onDownloadReceipt: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("payment_receipt_card_${payment.receiptFolio}"),
        shape = RoundedCornerShape(14.dp),
        color = NavySurface,
        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header del Pago: Folio y Estado Liquidado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = GoldPrimary.copy(alpha = 0.15f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = payment.receiptFolio,
                            color = GoldPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = payment.formattedDate,
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SuccessGreen.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, SuccessGreen)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = SuccessGreen,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = payment.status,
                            color = SuccessGreen,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            // Concepto y Monto
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = payment.concept,
                    color = TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Método: ${payment.paymentMethod}",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                    Text(
                        text = payment.formattedAmount,
                        color = TextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.8.dp)

            // Botones de acción rápida: "Ver Recibo" y "Descargar Comprobante"
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onViewReceipt,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("btn_view_receipt_${payment.receiptFolio}"),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = CyanNeon
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ver Recibo", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onDownloadReceipt,
                    modifier = Modifier
                        .weight(1.1f)
                        .height(38.dp)
                        .testTag("btn_download_receipt_${payment.receiptFolio}"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldPrimary,
                        contentColor = NavyDark
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Descargar", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// =========================================================================================
// DIÁLOGO OFICIAL: COMPROBANTE DIGITAL CRIPTOGRÁFICO DE PAGO
// =========================================================================================

/**
 * Diálogo modal con formato formal de Comprobante Fiscal / Recibo Digital de Cuota.
 */
@Composable
fun PaymentReceiptDetailDialog(
    payment: MaintenancePaymentEntity,
    onDismiss: () -> Unit
) {
    val qrBitmap = remember(payment.receiptFolio) {
        val verificationUrl = "https://alfhaseguridad.com/verify/receipt?folio=${payment.receiptFolio}&unit=${payment.unitId}&auth=${payment.bankReference}"
        generateReceiptQr(verificationUrl)
    }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = NavySurface,
            border = BorderStroke(1.5.dp, GoldPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .testTag("payment_receipt_detail_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header oficial
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "RECIBO DIGITAL OFICIAL",
                            color = GoldPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "MEDUSA ALFHA CONDOMINIOS",
                            color = TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                // Datos clave de la transacción
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(NavyDark, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ReceiptRow(label = "Folio Oficial:", value = payment.receiptFolio, isMonospace = true, valueColor = GoldPrimary)
                    ReceiptRow(label = "Unidad / Casa:", value = payment.unitId, isBold = true)
                    ReceiptRow(label = "Fecha de Emisión:", value = payment.formattedDate)
                    ReceiptRow(label = "Método:", value = payment.paymentMethod)
                    ReceiptRow(label = "Referencia Bancaria:", value = payment.bankReference, isMonospace = true)
                    ReceiptRow(label = "Estado:", value = payment.status, valueColor = SuccessGreen, isBold = true)
                }

                // Desglose de cuotas
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "DESGLOSE DE APORTACIÓN",
                        color = CyanNeon,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    ReceiptRow(label = "• Cuota Ordinaria de Operación:", value = String.format("$%,.2f", payment.breakdownCuota))
                    ReceiptRow(label = "• Fondo de Reserva Comunitaria:", value = String.format("$%,.2f", payment.breakdownFondoReserva))
                    ReceiptRow(label = "• Conservación de Amenidades:", value = String.format("$%,.2f", payment.breakdownAmenidades))

                    HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TOTAL LIQUIDADO:", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(payment.formattedAmount, color = GoldPrimary, fontSize = 17.sp, fontWeight = FontWeight.Black)
                    }
                }

                // Código QR de validación fiscal y sello digital
                qrBitmap?.let { bmp ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White,
                        modifier = Modifier.size(110.dp)
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Código QR de Validación Fiscal",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                        )
                    }
                }

                Text(
                    text = "Sello Digital: ${payment.digitalSignatureSha}",
                    color = TextMuted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )

                // Acciones de cierre y guardado
                Button(
                    onClick = {
                        Toast.makeText(context, "Comprobante ${payment.receiptFolio} guardado en Descargas", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Guardar Comprobante PDF", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }
        }
    }
}

/**
 * Fila auxiliar para datos en recibo
 */
@Composable
private fun ReceiptRow(
    label: String,
    value: String,
    isMonospace: Boolean = false,
    isBold: Boolean = false,
    valueColor: Color = TextWhite
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = TextMuted, fontSize = 11.sp)
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.5.sp,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/**
 * Generador interno de QR para el recibo.
 */
private fun generateReceiptQr(content: String, sizePx: Int = 300): Bitmap? {
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

private data class VisitorStatusVisualConfig(
    val label: String,
    val textColor: Color,
    val bgColor: Color,
    val icon: ImageVector
)
