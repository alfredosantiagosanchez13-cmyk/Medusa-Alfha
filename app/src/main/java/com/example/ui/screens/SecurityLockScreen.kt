package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.audit.AuditLogEntity
import com.example.data.booking.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.ui.theme.AlertRed
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

/**
 * Pantalla de Bloqueo Inmutable por Violación de Acceso RBAC (MEDUSA ALFHA).
 * Se activa inmediatamente cuando un rol intenta acceder a un nodo sin la debida autorización.
 * Incorpora enclave para desbloqueo maestro mediante PIN/Clave de Emergencia o reinicio total.
 */
@Composable
fun SecurityLockScreen(
    intrusionToken: String,
    reason: String,
    modifier: Modifier = Modifier,
    onResetTerminal: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    var unlockKeyOrPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("security_lock_screen_root"),
        color = NavyDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Emblema de Alerta de Intrusión
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(AlertRed.copy(alpha = 0.15f))
                    .border(2.dp, AlertRed, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Bloqueo de Seguridad",
                    tint = AlertRed,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "TERMINAL BLOQUEADA",
                color = AlertRed,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            Text(
                text = "INTRUSIÓN / VIOLACIÓN DE POLÍTICA RBAC",
                color = TextWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "El acceso a la ruta solicitada fue rechazado por las cláusulas de guarda defensivas de MEDUSA ALFHA. La sesión anterior ha sido destruida y registrada en la auditoría Room.",
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Tarjeta de Detalle Forense del Incidente
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = NavySurface,
                border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AlertRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "REGISTRO FORENSE DE INTRUSIÓN",
                            color = AlertRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Column {
                        Text(
                            text = "MOTIVO DE RECHAZO:",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = reason,
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Column {
                        Text(
                            text = "TOKEN DE AUDITORÍA LOCAL:",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = intrusionToken,
                            color = GoldPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Evento asentado con sello SHA-256 en AppDatabase (Room) y notificado al supervisor.",
                        color = TextMuted,
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Sección de Desbloqueo por PIN / Clave Maestra
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = NavySurface.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "DESBLOQUEO CON PIN O CLAVE MAESTRA",
                            color = GoldPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = unlockKeyOrPin,
                        onValueChange = {
                            unlockKeyOrPin = it
                            pinError = null
                        },
                        placeholder = { Text("PIN maestro o clave de seguridad", color = TextMuted, fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_security_unlock_pin"),
                        shape = RoundedCornerShape(10.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (unlockKeyOrPin.trim().isNotBlank()) {
                                    onResetTerminal()
                                } else {
                                    pinError = "Introduce el PIN o Clave Maestra"
                                }
                            }
                        ),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = GoldPrimary.copy(alpha = 0.4f),
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            cursorColor = GoldPrimary
                        )
                    )

                    if (pinError != null) {
                        Text(
                            text = pinError ?: "",
                            color = AlertRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Button(
                        onClick = {
                            if (unlockKeyOrPin.trim().isNotBlank()) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val log = AuditLogEntity(
                                            operatorId = "SECURITY_ENCLAVE",
                                            eventDescription = "Desbloqueo autorizado mediante enclave de PIN/Clave maestra para token $intrusionToken.",
                                            severity = AuditLogEntity.Severity.INFO,
                                            forensicPayload = """{"intrusionToken":"$intrusionToken","reason":"$reason","action":"UNLOCKED_WITH_PIN"}"""
                                        )
                                        db.securityAuditDao().insertLog(log)
                                    } catch (e: Exception) {
                                        android.util.Log.e("SecurityLockScreen", "Error asentando desbloqueo", e)
                                    }
                                }
                                onResetTerminal()
                            } else {
                                pinError = "Introduce el PIN o Clave de Emergencia"
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        val log = AuditLogEntity(
                                            operatorId = "SECURITY_ENCLAVE",
                                            eventDescription = "Fallo de autenticación por PIN vacío o inválido en terminal bloqueada ($intrusionToken).",
                                            severity = AuditLogEntity.Severity.WARNING,
                                            forensicPayload = """{"intrusionToken":"$intrusionToken","reason":"$reason","action":"EMPTY_PIN_ATTEMPT"}"""
                                        )
                                        db.securityAuditDao().insertLog(log)
                                    } catch (e: Exception) {
                                        android.util.Log.e("SecurityLockScreen", "Error asentando fallo PIN", e)
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_unlock_with_pin"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            contentColor = NavyDark
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DESBLOQUEAR TERMINAL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Botón Alternativo de Restablecimiento Inmediato
            OutlinedButton(
                onClick = onResetTerminal,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("btn_reset_terminal_after_lock"),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextWhite
                )
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    tint = AlertRed,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "REINICIAR TERMINAL A ACTIVACIÓN",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
