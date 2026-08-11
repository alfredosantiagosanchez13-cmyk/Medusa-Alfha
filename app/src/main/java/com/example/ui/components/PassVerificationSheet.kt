package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scanner.PassStatus
import com.example.scanner.VerificationResult
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarningOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassVerificationSheet(
    result: VerificationResult,
    onConfirmEntry: () -> Unit,
    onDenyEntry: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val (statusTitle, statusColor, statusIcon) = when (result.status) {
        PassStatus.VALID -> Triple("PASE VÁLIDO - ACCESO PERMITIDO", SuccessGreen, Icons.Default.CheckCircle)
        PassStatus.EXPIRED -> Triple("PASE EXPIRADO", WarningOrange, Icons.Default.Warning)
        PassStatus.ALREADY_USED -> Triple("PASE YA UTILIZADO", WarningOrange, Icons.Default.Info)
        PassStatus.INVALID -> Triple("PASE INVÁLIDO O INEXISTENTE", ErrorRed, Icons.Default.Cancel)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NavySurface,
        scrimColor = Color.Black.copy(alpha = 0.75f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .testTag("pass_verification_sheet_content"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Status Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = statusColor.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, statusColor)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(statusColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = NavyDark,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = statusTitle,
                            color = statusColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Código QR: ${result.passCode}",
                            color = TextMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val pass = result.qrPass
            if (pass != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "DETALLES DE LA VISITA",
                            color = GoldPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        DetailRow(
                            icon = Icons.Default.Person,
                            label = "Invitado/a",
                            value = pass.guestName,
                            subValue = "RUT/Doc: ${pass.guestDocument}"
                        )

                        DetailRow(
                            icon = Icons.Default.Home,
                            label = "Destino",
                            value = pass.destinationHouse,
                            subValue = "Anfitrión: ${pass.hostResidentName}"
                        )

                        DetailRow(
                            icon = Icons.Default.Badge,
                            label = "Tipo de Pase",
                            value = pass.passType.label,
                            subValue = "Usos: ${pass.currentEntriesCount} / ${pass.maxEntries}"
                        )

                        if (!pass.vehiclePlate.isNullOrEmpty()) {
                            DetailRow(
                                icon = Icons.Default.DirectionsCar,
                                label = "Vehículo / Patente",
                                value = pass.vehiclePlate ?: "",
                                highlight = true
                            )
                        }

                        DetailRow(
                            icon = Icons.Default.Schedule,
                            label = "Vigilancia / Expiración",
                            value = SimpleDateFormat("HH:mm 'hrs' - dd/MM/yyyy", Locale.getDefault())
                                .format(Date(pass.validUntilMillis))
                        )

                        if (!pass.note.isNullOrEmpty()) {
                            Text(
                                text = "Nota: ${pass.note}",
                                color = CyanNeon,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                }
            } else if (result.failureReason != null) {
                Text(
                    text = result.failureReason,
                    color = ErrorRed,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (result.status == PassStatus.VALID) {
                    OutlinedButton(
                        onClick = onDenyEntry,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("deny_access_button"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Rechazar", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onConfirmEntry,
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                            .testTag("confirm_entry_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = NavyDark),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Aprobar Ingreso", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("close_sheet_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cerrar y Volver al Escáner", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    label: String,
    value: String,
    subValue: String? = null,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (highlight) GoldPrimary else CyanNeon,
            modifier = Modifier.size(20.dp)
        )
        Column {
            Text(
                text = label,
                color = TextMuted,
                fontSize = 11.sp
            )
            Text(
                text = value,
                color = if (highlight) GoldPrimary else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (subValue != null) {
                Text(
                    text = subValue,
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
            }
        }
    }
}
