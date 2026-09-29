package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.auth.ResidentBiometricGate
import com.example.data.auth.MedusaDevConfig
import com.example.data.auth.MedusaRole
import com.example.data.booking.AppDatabase
import com.example.ui.components.ResidentFirebaseAuthBarrier
import com.example.ui.theme.AlertRed
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import com.example.ui.viewmodel.ActivationUiState
import com.example.ui.viewmodel.ActivationViewModel

/**
 * Pantalla de Activación y Enrolamiento de Terminal (MEDUSA ALFHA).
 * Punto de entrada inicial por defecto para dispositivos en estado UNASSIGNED.
 */
@Composable
fun ActivationScreen(
    activationViewModel: ActivationViewModel,
    modifier: Modifier = Modifier,
    onActivationSuccess: (MedusaRole) -> Unit = {}
) {
    val uiState by activationViewModel.uiState.collectAsState()
    val currentSession by activationViewModel.currentSession.collectAsState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val db: AppDatabase = remember(context) { AppDatabase.getDatabase(context) }

    var inputKey by remember { mutableStateOf("") }
    var showResidentAuthBarrier by remember { mutableStateOf(false) }
    var showAdminAuthModal by remember { mutableStateOf(false) }
    var showCasetaAuthModal by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Escucha de éxito en la activación para propagar navegación
    LaunchedEffect(uiState) {
        if (uiState is ActivationUiState.Success) {
            val success = uiState as ActivationUiState.Success
            onActivationSuccess(success.role)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("activation_screen_root"),
        color = NavyDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Emblema Central de Seguridad
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(NavySurface)
                    .border(1.5.dp, GoldPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "Escudo Medusa",
                    tint = GoldPrimary,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "MEDUSA ALFHA",
                color = GoldPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp
            )

            Text(
                text = "ENROLAMIENTO Y CONTROL DE ACCESO",
                color = TextWhite,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Ingresa la llave criptográfica asignada para habilitar el perfil correspondiente en esta terminal (Administración, Caseta o Residente).",
                color = TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Campo de Entrada de Llave de Activación
            OutlinedTextField(
                value = inputKey,
                onValueChange = { 
                    // Limpieza automática de espacios accidentales del teclado móvil
                    inputKey = it.replace(" ", "").uppercase() 
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("input_activation_key"),
                label = { Text("Credencial, ID o Llave de Acceso", color = TextMuted) },
                placeholder = { Text("Ej: Correo, ID o Llave Oficial", color = TextMuted.copy(alpha = 0.6f)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = null,
                        tint = GoldPrimary
                    )
                },
                trailingIcon = {
                    if (inputKey.isNotEmpty()) {
                        IconButton(onClick = { inputKey = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Limpiar campo",
                                tint = TextMuted
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (inputKey.isNotBlank()) {
                            activationViewModel.validateActivationKey(inputKey)
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedContainerColor = NavySurface,
                    unfocusedContainerColor = NavySurface
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Botón de Validación Oficial
            Button(
                onClick = {
                    focusManager.clearFocus()
                    activationViewModel.validateActivationKey(inputKey)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_validate_key"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldPrimary,
                    contentColor = NavyDark
                ),
                enabled = uiState !is ActivationUiState.Loading && inputKey.isNotBlank()
            ) {
                if (uiState is ActivationUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = NavyDark,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "AUTENTICANDO TERMINAL...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = NavyDark
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ACTIVAR Y VINCULAR DISPOSITIVO",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Alerta de Error de Activación
            AnimatedVisibility(
                visible = uiState is ActivationUiState.Error,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val error = uiState as? ActivationUiState.Error
                if (error != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .testTag("activation_error_banner"),
                        shape = RoundedCornerShape(10.dp),
                        color = AlertRed.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = AlertRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Error de Enrolamiento",
                                    color = AlertRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = error.errorMessage,
                                    color = TextWhite,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Botón de Autenticación Biométrica (Huella / Rostro)
            Button(
                onClick = {
                    focusManager.clearFocus()
                    ResidentBiometricGate.authenticateAppAccess(
                        context = context,
                        onAuthorized = {
                            activationViewModel.activateViaBiometrics()
                        },
                        onDenied = { errorMsg ->
                            Toast.makeText(context, "Biometría: $errorMsg", Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_biometric_app_access"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanNeon.copy(alpha = 0.15f),
                    contentColor = CyanNeon
                ),
                border = BorderStroke(1.2.dp, CyanNeon)
            ) {
                Icon(
                    imageVector = Icons.Default.Fingerprint,
                    contentDescription = "Autenticación Biométrica",
                    modifier = Modifier.size(22.dp),
                    tint = CyanNeon
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "ACCESO BIOMÉTRICO (HUELLA / ROSTRO)",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 0.6.sp,
                        color = CyanNeon
                    )
                    Text(
                        text = "Desbloqueo seguro de terminal",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Botón de Autenticación de Residente vía Firebase Auth
            OutlinedButton(
                onClick = { showResidentAuthBarrier = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("btn_resident_firebase_auth_portal"),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.2.dp, CyanNeon.copy(alpha = 0.8f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = NavySurface.copy(alpha = 0.6f),
                    contentColor = CyanNeon
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ACCESO RESIDENTES (FIREBASE AUTH)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 0.6.sp
                )
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Indicadores de Referencia de Perfiles RBAC
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = NavySurface,
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "PERFILES DE ACCESO DISPONIBLES",
                        color = GoldPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ProfileAccessRow(
                        icon = Icons.Default.AdminPanelSettings,
                        role = "ADMINISTRACIÓN",
                        desc = "Auditoría, finanzas y control maestro (Acreditación requerida)",
                        onClick = {
                            showAdminAuthModal = true
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ProfileAccessRow(
                        icon = Icons.Default.Security,
                        role = "CASETA DE SEGURIDAD",
                        desc = "Escaneo QR, barreras y bitácora táctica (Sin acceso financiero)",
                        onClick = {
                            showCasetaAuthModal = true
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ProfileAccessRow(
                        icon = Icons.Default.Home,
                        role = "PORTAL RESIDENTE",
                        desc = "Pases de visita y reservas privadas (Acreditación)",
                        onClick = {
                            showResidentAuthBarrier = true
                        }
                    )
                }
            }
        }
    }

    // Modal de Autenticación de Administración
    if (showAdminAuthModal) {
        AdminAuthenticationDialog(
            onDismiss = { showAdminAuthModal = false },
            onAuthenticate = { adminCredential, pin ->
                showAdminAuthModal = false
                activationViewModel.authenticateAdministration(adminCredential, pin)
            }
        )
    }

    // Modal de Autenticación de Caseta de Seguridad
    if (showCasetaAuthModal) {
        CasetaAuthenticationDialog(
            onDismiss = { showCasetaAuthModal = false },
            onAuthenticate = { guardCredential, pin ->
                showCasetaAuthModal = false
                activationViewModel.authenticateCaseta(guardCredential, pin)
            }
        )
    }

    // Modal de Autenticación de Residente con Firebase Auth
    if (showResidentAuthBarrier) {
        Dialog(
            onDismissRequest = { showResidentAuthBarrier = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            ResidentFirebaseAuthBarrier(
                featureName = "Portal Residente",
                featureDescription = "Acreditación de identidad para acceso al portal de residentes, escáner y reservas.",
                db = db,
                onDismissOrBack = { showResidentAuthBarrier = false }
            ) { resident ->
                LaunchedEffect(resident) {
                    showResidentAuthBarrier = false
                    activationViewModel.activateAsResidentFromFirebaseAuth(resident)
                    onActivationSuccess(MedusaRole.RESIDENTE)
                }
            }
        }
    }
}

@Composable
private fun AdminAuthenticationDialog(
    onDismiss: () -> Unit,
    onAuthenticate: (String, String) -> Unit
) {
    var adminEmail by remember { mutableStateOf("administracion@condominio.com") }
    var adminPin by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = NavyDark,
            border = BorderStroke(1.5.dp, GoldPrimary)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(NavySurface)
                        .border(1.dp, GoldPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AdminPanelSettings,
                        contentDescription = "Admin",
                        tint = GoldPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "ACREDITACIÓN ADMINISTRATIVA",
                    color = GoldPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp
                )

                Text(
                    text = "Control Maestro y Auditoría General",
                    color = TextWhite,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = adminEmail,
                    onValueChange = { adminEmail = it },
                    label = { Text("Correo o ID Administrador", color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = GoldPrimary)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = NavySurface,
                        unfocusedContainerColor = NavySurface
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = adminPin,
                    onValueChange = { adminPin = it },
                    label = { Text("PIN o Clave de Seguridad", color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = GoldPrimary)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = TextMuted
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = NavySurface,
                        unfocusedContainerColor = NavySurface
                    )
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF475569))
                    ) {
                        Text("Cancelar", color = TextMuted, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { onAuthenticate(adminEmail, adminPin) },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            contentColor = NavyDark
                        ),
                        enabled = adminEmail.isNotBlank()
                    ) {
                        Text("Ingresar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CasetaAuthenticationDialog(
    onDismiss: () -> Unit,
    onAuthenticate: (String, String) -> Unit
) {
    var guardId by remember { mutableStateOf("caseta1@alfhaseguridad.com") }
    var guardPin by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = NavyDark,
            border = BorderStroke(1.5.dp, CyanNeon)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(NavySurface)
                        .border(1.dp, CyanNeon, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Caseta",
                        tint = CyanNeon,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "ACREDITACIÓN DE GUARDIA",
                    color = CyanNeon,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp
                )

                Text(
                    text = "Control Táctico y Garita (Nodos Financieros Bloqueados)",
                    color = TextWhite,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = CyanNeon,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Aislamiento Zero-Trust activo: Sin acceso a balances, nómina ni cuentas bancarias.",
                            color = TextMuted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = guardId,
                    onValueChange = { guardId = it },
                    label = { Text("Oficial / Correo de Caseta", color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Badge, contentDescription = null, tint = CyanNeon)
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = NavySurface,
                        unfocusedContainerColor = NavySurface
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = guardPin,
                    onValueChange = { guardPin = it },
                    label = { Text("PIN de Guardia en Turno", color = TextMuted) },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = CyanNeon)
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = TextMuted
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanNeon,
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedContainerColor = NavySurface,
                        unfocusedContainerColor = NavySurface
                    )
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF475569))
                    ) {
                        Text("Cancelar", color = TextMuted, fontSize = 12.sp)
                    }

                    Button(
                        onClick = { onAuthenticate(guardId, guardPin) },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanNeon,
                            contentColor = NavyDark
                        ),
                        enabled = guardId.isNotBlank()
                    ) {
                        Text("Habilitar Caseta", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileAccessRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    role: String,
    desc: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = CyanNeon,
            modifier = Modifier.size(16.dp)
        )
        Column {
            Text(text = role, color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(text = desc, color = TextMuted, fontSize = 10.sp)
        }
    }
}
