package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OutdoorGrill
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.booking.AmenityBooking
import com.example.data.passes.QrPassRoomEntity
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.utils.AmenityReminderManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper para asociar un pase QR a una reserva y programar la alarma local
 * de 15 minutos antes conectada al AmenityReminderReceiver.
 */
fun scheduleSmartBookingPassAlert(
    context: Context,
    booking: AmenityBooking,
    pass: QrPassRoomEntity
) {
    val folio = if (booking.folio.isNotBlank()) booking.folio else "RSV-${System.currentTimeMillis() % 1000000}"
    AmenityReminderManager.schedule15MinReminder(
        context = context,
        booking = booking,
        folio = folio,
        guestName = pass.guestName
    )
}

/**
 * COMPONENTE DE ALERTAS INTELIGENTES DE RESERVA (SmartBookingAlerts)
 * 
 * Escucha y supervisa reactivamente el estado de solicitudes de amenidades (Alberca, Quincho, etc.),
 * mostrando el folio de confirmación y permitiendo programar el trigger local con AlarmManager
 * que despachará la notificación de alta prioridad 15 minutos antes del turno.
 */
@Composable
fun SmartBookingAlerts(
    condominiumId: String,
    assignedUnit: String,
    allBookings: List<AmenityBooking>,
    onGenerateGuestPassForBooking: (AmenityBooking) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val now = System.currentTimeMillis()

    // Filtrar solicitudes de amenidades activas o próximas para la unidad o condominio
    val relevantBookings = remember(allBookings, assignedUnit, condominiumId) {
        allBookings.filter { booking ->
            val matchesUnit = booking.unitId.equals(assignedUnit, ignoreCase = true) || booking.unitId == "GENERAL"
            val isUpcomingOrActive = booking.status != "CANCELADA" && (booking.bookingTimeMillis + (booking.durationMinutes * 60000L) > now)
            matchesUnit && isUpcomingOrActive
        }.sortedBy { it.bookingTimeMillis }
    }

    if (relevantBookings.isEmpty()) {
        // Estado vacío informativo
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .testTag("smart_booking_alerts_empty"),
            shape = RoundedCornerShape(12.dp),
            color = NavyCard.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = CyanNeon.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = CyanNeon,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "Alertas Inteligentes de Reserva",
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "No hay turnos activos próximos para Alberca o Quincho en $assignedUnit.",
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("smart_booking_alerts_container"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = CircleShape,
                    color = GoldPrimary.copy(alpha = 0.15f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = "Alertas Inteligentes de Reserva (15 Min)",
                    color = TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CyanNeon.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "${relevantBookings.size} activa(s)",
                    color = CyanNeon,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        relevantBookings.forEach { booking ->
            SmartBookingItemCard(
                booking = booking,
                onGeneratePass = { onGenerateGuestPassForBooking(booking) },
                onTriggerTestAlarm = {
                    val folio = if (booking.folio.isNotBlank()) booking.folio else "RSV-20260905-201"
                    AmenityReminderManager.schedule15MinReminder(
                        context = context,
                        booking = booking,
                        folio = folio,
                        guestName = "Invitado Reserva"
                    )
                    Toast.makeText(
                        context,
                        "⏰ Alarma inteligente vinculada a $folio. Notificación local programada 15 min antes.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }
}

@Composable
private fun SmartBookingItemCard(
    booking: AmenityBooking,
    onGeneratePass: () -> Unit,
    onTriggerTestAlarm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPool = booking.amenityName.contains("Piscina", ignoreCase = true) || booking.amenityName.contains("Alberca", ignoreCase = true)
    val amenityIcon: ImageVector = if (isPool) Icons.Default.Pool else Icons.Default.OutdoorGrill
    val accentColor = if (isPool) CyanNeon else GoldPrimary
    val folio = if (booking.folio.isNotBlank()) booking.folio else "RSV-${(booking.id.hashCode() and 0xFFFF) + 1000}"

    val formattedDate = try {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(booking.bookingTimeMillis))
    } catch (_: Exception) {
        booking.bookingDate
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("smart_booking_card_${booking.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Cabecera: Icono, Nombre de Amenidad y Folio
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accentColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = amenityIcon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = booking.amenityName,
                            color = TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF475569))
                            ) {
                                Text(
                                    text = "Folio: $folio",
                                    color = GoldPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = "• ${booking.unitId}",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (booking.status == "CONFIRMADA") Color(0xFF10B981).copy(alpha = 0.15f) else GoldPrimary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, if (booking.status == "CONFIRMADA") Color(0xFF10B981) else GoldPrimary)
                ) {
                    Text(
                        text = booking.status,
                        color = if (booking.status == "CONFIRMADA") Color(0xFF34D399) else GoldPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Horario y fecha
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = CyanNeon,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Turno: ${booking.timeSlot.ifBlank { "18:00 - 20:00" }}",
                        color = TextWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = formattedDate,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            // Banner explicativo del receptor local de alarmas
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.7f),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Notificación de alta prioridad programada 15 min antes con folio en pantalla.",
                        color = TextWhite,
                        fontSize = 10.5.sp,
                        lineHeight = 13.sp
                    )
                }
            }

            // Botones de acción: Generar Pase de Invitado y Activar/Probar Alarma
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onGeneratePass,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("btn_guest_pass_${booking.id}"),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Pase de Invitado",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onTriggerTestAlarm,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .testTag("btn_trigger_alarm_${booking.id}"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon),
                    border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Vincular Alarma",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
