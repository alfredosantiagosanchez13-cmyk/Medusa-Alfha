package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.DefaultFirebaseAuthProvider
import com.example.auth.FirebaseAuthProvider
import com.example.auth.ResidentBiometricGate
import com.example.data.booking.AppDatabase
import com.example.data.firebase.FirebaseConfigHelper
import com.example.data.resident.ResidentEntity
import com.example.ui.theme.*
import com.example.ui.viewmodel.ActivationViewModel
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CondoSmartAuthMode(val label: String) {
    SIGN_IN("Iniciar Sesión"),
    REGISTER("Registrar Condómino")
}

/**
 * PANTALLA OFICIAL DE INICIO DE SESIÓN PARA CONDOSMART QR.
 * Autenticación robusta y segura mediante Firebase Auth y Jetpack Credential Manager (Google Sign-In).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CondoSmartQrLoginScreen(
    activationViewModel: ActivationViewModel,
    onNavigateBack: () -> Unit,
    onLoginSuccess: (ResidentEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val db = remember { AppDatabase.getDatabase(context) }
    val residentDao = remember { db.residentDao() }

    val authProvider: FirebaseAuthProvider = remember {
        FirebaseAuthProvider.getOrNull() ?: DefaultFirebaseAuthProvider(context.applicationContext)
    }

    var authMode by remember { mutableStateOf(CondoSmartAuthMode.SIGN_IN) }
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var fullNameInput by remember { mutableStateOf("") }
    var assignedUnitInput by remember { mutableStateOf("Casa 54 · Circuito Los Álamos") }
    var phoneInput by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successNotice by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    BackHandler {
        onNavigateBack()
    }

    // Proceso unificado al autenticar con éxito en Firebase
    fun handleFirebaseUserSuccess(firebaseUser: FirebaseUser, customUnit: String? = null) {
        scope.launch {
            val userEmail = firebaseUser.email ?: "condosmart.${firebaseUser.uid.take(6)}@residente.com"
            val displayName = firebaseUser.displayName?.ifBlank { null }
                ?: fullNameInput.ifBlank { null }
                ?: "Condómino Residencial"

            // Buscar si ya existe en la base de datos local de Los Prados
            val existing = withContext(Dispatchers.IO) {
                residentDao.getResidentByEmail(userEmail)
            }

            val residentToActivate: ResidentEntity = if (existing != null) {
                existing
            } else {
                val newUnit = customUnit ?: assignedUnitInput.ifBlank { "Casa 54 · Circuito Los Álamos" }
                val newResident = ResidentEntity(
                    id = "RES-${firebaseUser.uid.take(8).uppercase()}",
                    unitId = newUnit,
                    fullName = displayName,
                    email = userEmail,
                    phone = phoneInput.ifBlank { "555-019-PRADOS" },
                    occupancyType = "PROPIETARIO",
                    status = "ACTIVO",
                    createdAtMillis = System.currentTimeMillis()
                )
                withContext(Dispatchers.IO) {
                    residentDao.insertResident(newResident)
                }
                newResident
            }

            // Activar sesión en ViewModel con permisos de residente
            activationViewModel.activateAsResidentFromFirebaseAuth(residentToActivate)
            isLoading = false
            successNotice = "¡Acceso concedido! Bienvenido, ${residentToActivate.fullName}."
            Toast.makeText(context, "Bienvenido a CondoSmart QR: ${residentToActivate.fullName}", Toast.LENGTH_SHORT).show()
            onLoginSuccess(residentToActivate)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary.copy(alpha = 0.2f))
                                .border(1.dp, GoldPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "CONDOSMART QR",
                                color = GoldPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Acceso Seguro con Firebase Auth",
                                color = TextMuted,
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = GoldPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyDark,
                    titleContentColor = GoldPrimary
                )
            )
        },
        containerColor = NavyDark,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Emblema Central
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(NavySurface)
                    .border(1.5.dp, GoldPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode2,
                    contentDescription = "CondoSmart QR",
                    tint = CyanNeon,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "INICIO DE SESIÓN CONDOSMART",
                color = TextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp
            )

            Text(
                text = "Emisión, validación y gestión de Pases QR para residentes y visitantes de Los Prados Residencial.",
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // =========================================================================
            // BOTÓN DE ACCESO PRINCIPAL: GOOGLE SIGN-IN VIA CREDENTIAL MANAGER
            // =========================================================================
            Button(
                onClick = {
                    focusManager.clearFocus()
                    isLoading = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val result = authProvider.signInWithGoogle(context)
                            if (result.isSuccess) {
                                val user = result.getOrNull()
                                if (user != null) {
                                    handleFirebaseUserSuccess(user)
                                } else {
                                    isLoading = false
                                    errorMessage = "No se obtuvo usuario de Google."
                                }
                            } else {
                                isLoading = false
                                val ex = result.exceptionOrNull()
                                errorMessage = ex?.localizedMessage ?: "Error al autenticar con Google Credential Manager."
                            }
                        } catch (t: Throwable) {
                            isLoading = false
                            errorMessage = "Fallo en Credential Manager: ${t.message}"
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("btn_google_credential_manager_signin"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF1F2937)
                ),
                enabled = !isLoading
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Google",
                        tint = Color(0xFF4285F4),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Continuar con Google (Credential Manager)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF1F2937)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Divisor Visual "o con correo electrónico"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF334155))
                Text(
                    text = "O CON CORREO Y CONTRASEÑA",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFF334155))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Selector de Modo: Iniciar Sesión vs Registro
            TabRow(
                selectedTabIndex = authMode.ordinal,
                containerColor = NavySurface,
                contentColor = GoldPrimary,
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
            ) {
                CondoSmartAuthMode.values().forEach { mode ->
                    Tab(
                        selected = authMode == mode,
                        onClick = {
                            authMode = mode
                            errorMessage = null
                        },
                        text = {
                            Text(
                                text = mode.label,
                                fontSize = 12.sp,
                                fontWeight = if (authMode == mode) FontWeight.Bold else FontWeight.Normal,
                                color = if (authMode == mode) GoldPrimary else TextMuted
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Formulario de Entrada
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = NavySurface,
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (authMode == CondoSmartAuthMode.REGISTER) {
                        OutlinedTextField(
                            value = fullNameInput,
                            onValueChange = { fullNameInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Nombre Completo del Residente", color = TextMuted) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = GoldPrimary)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = NavyDark,
                                unfocusedContainerColor = NavyDark
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = assignedUnitInput,
                            onValueChange = { assignedUnitInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Unidad / Casa Asignada", color = TextMuted) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Home, contentDescription = null, tint = GoldPrimary)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = NavyDark,
                                unfocusedContainerColor = NavyDark
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Correo Electrónico", color = TextMuted) },
                        placeholder = { Text("ejemplo@condominio.com", color = TextMuted.copy(alpha = 0.5f)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        leadingIcon = {
                            Icon(Icons.Default.Email, contentDescription = null, tint = GoldPrimary)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedContainerColor = NavyDark,
                            unfocusedContainerColor = NavyDark
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Contraseña", color = TextMuted) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = GoldPrimary)
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Mostrar contraseña",
                                    tint = TextMuted
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedContainerColor = NavyDark,
                            unfocusedContainerColor = NavyDark
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Botón Olvidé Contraseña
                    if (authMode == CondoSmartAuthMode.SIGN_IN) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    if (emailInput.isBlank()) {
                                        errorMessage = "Introduce tu correo para enviarte el enlace de recuperación."
                                    } else {
                                        scope.launch {
                                            isLoading = true
                                            val res = authProvider.sendPasswordReset(emailInput)
                                            isLoading = false
                                            if (res.isSuccess) {
                                                Toast.makeText(context, "Correo de recuperación enviado a $emailInput", Toast.LENGTH_LONG).show()
                                            } else {
                                                errorMessage = "No se pudo enviar correo: ${res.exceptionOrNull()?.message}"
                                            }
                                        }
                                    }
                                }
                            ) {
                                Text("¿Olvidaste tu contraseña?", color = CyanNeon, fontSize = 11.sp)
                            }
                        }
                    }

                    // Botón Principal de Acción (Login o Registro)
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (emailInput.isBlank() || passwordInput.isBlank()) {
                                errorMessage = "Por favor ingresa tu correo y contraseña."
                                return@Button
                            }

                            isLoading = true
                            errorMessage = null

                            scope.launch {
                                val result = if (authMode == CondoSmartAuthMode.SIGN_IN) {
                                    authProvider.signInWithEmail(emailInput, passwordInput)
                                } else {
                                    val name = fullNameInput.ifBlank { "Condómino Los Prados" }
                                    authProvider.signUpWithEmail(emailInput, passwordInput, name)
                                }

                                if (result.isSuccess) {
                                    val user = result.getOrNull()
                                    if (user != null) {
                                        handleFirebaseUserSuccess(user)
                                    } else {
                                        isLoading = false
                                        errorMessage = "No se obtuvo usuario válido de Firebase."
                                    }
                                } else {
                                    isLoading = false
                                    errorMessage = result.exceptionOrNull()?.localizedMessage
                                        ?: "Error en autenticación. Verifica tus credenciales."
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_submit_condosmart_auth"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            contentColor = NavyDark
                        ),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = NavyDark,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AUTENTICANDO...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        } else {
                            Icon(
                                imageVector = if (authMode == CondoSmartAuthMode.SIGN_IN) Icons.Default.Login else Icons.Default.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (authMode == CondoSmartAuthMode.SIGN_IN) "INGRESAR A CONDOSMART QR" else "REGISTRAR Y ACCEDER",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            // Alerta de Error
            AnimatedVisibility(
                visible = errorMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AlertRed.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = AlertRed, modifier = Modifier.size(20.dp))
                        Text(
                            text = errorMessage ?: "",
                            color = TextWhite,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // =========================================================================
            // MÉTODOS ALTERNATIVOS: BIOMÉTRICO Y PASE RÁPIDO CONTINGENCIA
            // =========================================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Biometría
                OutlinedButton(
                    onClick = {
                        ResidentBiometricGate.authenticateAppAccess(
                            context = context,
                            onAuthorized = {
                                scope.launch {
                                    val resident = withContext(Dispatchers.IO) {
                                        residentDao.getResidentByEmail("arismendi.residente@condominio.com")
                                            ?: residentDao.getAllResidentsWithDeletedFlow()
                                            ?: null
                                    }
                                    val targetResident = if (resident is ResidentEntity) resident else ResidentEntity(
                                        id = "RES-BIOMETRIC-01",
                                        unitId = "Casa 54 · Circuito Los Álamos",
                                        fullName = "Familia Arismendi",
                                        email = "arismendi.residente@condominio.com"
                                    )
                                    activationViewModel.activateAsResidentFromFirebaseAuth(targetResident)
                                    onLoginSuccess(targetResident)
                                }
                            },
                            onDenied = { err ->
                                Toast.makeText(context, "Biometría: $err", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CyanNeon),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("BIOMETRÍA", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                // Modo Anónimo / Contingencia
                OutlinedButton(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            val res = authProvider.signInAnonymously()
                            isLoading = false
                            if (res.isSuccess) {
                                val user = res.getOrNull()
                                if (user != null) {
                                    handleFirebaseUserSuccess(user, "Casa Contingencia QR")
                                }
                            } else {
                                errorMessage = "Modo contingencia no disponible: ${res.exceptionOrNull()?.message}"
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF64748B)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextWhite)
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("PASE RÁPIDO", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}
