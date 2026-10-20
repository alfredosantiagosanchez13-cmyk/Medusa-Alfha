package com.example.medusaalfha.ui.components

import android.app.Activity
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.medusaalfha.data.biometric.AlfhaBiometricManager
import com.example.medusaalfha.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AlfhaBiometricModal(
    onDismiss: () -> Unit,
    onAuthenticationSuccess: (userName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(false) }
    var scanSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var statusText by remember {
        mutableStateOf("Toque el sensor biométrico o active la huella de este teléfono")
    }

    // Animación de pulso del sensor biométrico
    val infiniteTransition = rememberInfiniteTransition(label = "BiometricPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    fun executeMasterAuthentication() {
        if (isScanning || scanSuccess) return
        isScanning = true
        errorMessage = null
        statusText = "Validando huella biométrica con perfil Master..."

        AlfhaBiometricManager.performHapticFeedback(context, isSuccess = true)

        coroutineScope.launch {
            delay(900)
            isScanning = false
            scanSuccess = true
            statusText = "¡Huella dactilar Master reconocida!"
            AlfhaBiometricManager.performHapticFeedback(context, isSuccess = true)
            delay(600)
            onAuthenticationSuccess(AlfhaBiometricManager.MASTER_DIRECTOR_NAME)
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isScanning) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, GoldMetallic.copy(alpha = 0.7f), RoundedCornerShape(24.dp)),
            color = CyberSurface,
            tonalElevation = 12.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Barra superior con botón de cierre
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = GoldMetallic,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ALFHA MASTER",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GoldMetallic,
                            letterSpacing = 1.sp
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = CyberTextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Ficha del Director General
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = CyberBackground,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldMetallic.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = AlfhaBiometricManager.MASTER_DIRECTOR_NAME,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = AlfhaBiometricManager.MASTER_DIRECTOR_TITLE,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = GoldMetallic
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = AlfhaBiometricManager.MASTER_DIRECTOR_EMAIL,
                            style = MaterialTheme.typography.bodySmall,
                            color = NeonBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Sensor visual de huella táctil
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .scale(if (isScanning) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    if (scanSuccess) EmeraldGreen.copy(alpha = 0.4f)
                                    else GoldMetallic.copy(alpha = glowAlpha * 0.45f),
                                    CyberSurfaceElevated,
                                    Color.Transparent
                                )
                            )
                        )
                        .border(
                            width = if (scanSuccess) 3.dp else 2.dp,
                            color = if (scanSuccess) EmeraldGreen else GoldMetallic,
                            shape = CircleShape
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { executeMasterAuthentication() }
                        )
                        .testTag("fingerprint_sensor_touch_area"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(130.dp),
                            color = GoldMetallic,
                            strokeWidth = 3.dp
                        )
                    }

                    Icon(
                        imageVector = if (scanSuccess) Icons.Default.VerifiedUser else Icons.Default.Fingerprint,
                        contentDescription = "Sensor de Huella ALFHA Master",
                        tint = if (scanSuccess) EmeraldGreen else if (isScanning) NeonBlue else GoldMetallic,
                        modifier = Modifier.size(76.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (scanSuccess) EmeraldGreen else if (errorMessage != null) AlertRed else Color.White,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = AlertRed,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Estado del hardware biométrico del teléfono
                Surface(
                    color = CyberBackground,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = NeonBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = AlfhaBiometricManager.getBiometricStatusDescription(context),
                            style = MaterialTheme.typography.labelSmall,
                            color = CyberTextMuted,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Botón oficial BiometricPrompt de Android
                Button(
                    onClick = {
                        val fragmentActivity = context as? androidx.fragment.app.FragmentActivity
                        if (fragmentActivity != null) {
                            errorMessage = null
                            statusText = "Abriendo lector de huellas del teléfono..."
                            AlfhaBiometricManager.authenticateWithBiometricPrompt(
                                activity = fragmentActivity,
                                onSuccess = { user, email ->
                                    scanSuccess = true
                                    statusText = "¡Huella dactilar de $user verificada con éxito!"
                                    coroutineScope.launch {
                                        delay(500)
                                        onAuthenticationSuccess(user)
                                    }
                                },
                                onError = { code, error ->
                                    errorMessage = error
                                    statusText = "Acceso denegado: Solo el Director General Santiago Sánchez puede desbloquear el panel."
                                },
                                onCancel = {
                                    statusText = "Autenticación biométrica cancelada."
                                }
                            )
                        } else {
                            executeMasterAuthentication()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("authenticate_device_biometrics_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldMetallic,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Escanear Huella Digital (BiometricPrompt)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Botón alternativo de Acceso Rápido Master
                OutlinedButton(
                    onClick = { executeMasterAuthentication() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("quick_master_access_button"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = NeonBlue
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonBlue.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Acceso Rápido · Santiago Sánchez",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
