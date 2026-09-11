package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.auth.AlfhaRole
import com.example.auth.AlfhaSecurityContext
import com.example.data.auth.AlfhaUserEntity
import com.example.data.booking.AppDatabase
import com.example.data.firebase.AuthUiState
import com.example.data.firebase.FirebaseAuthManager
import com.example.data.firebase.FirebaseConfigHelper
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import kotlinx.coroutines.launch

/**
 * Modelo estructurado para presentar diagnósticos claros de errores en Google Sign-In.
 */
data class GoogleSignInErrorInfo(
    val title: String,
    val technicalMessage: String,
    val userFriendlyExplanation: String,
    val suggestedAction: String? = null
)

/**
 * Interpreta las excepciones arrojadas por Credential Manager y Firebase Auth para generar
 * mensajes descriptivos orientados al usuario y al diagnóstico del sistema.
 */
fun parseGoogleSignInError(exception: Throwable?, defaultWebClientId: String): GoogleSignInErrorInfo {
    val rawMsg = exception?.localizedMessage ?: exception?.message ?: "Error desconocido en el proveedor de credenciales."
    return when {
        exception is androidx.credentials.exceptions.GetCredentialCancellationException -> {
            GoogleSignInErrorInfo(
                title = "Inicio de Sesión Cancelado",
                technicalMessage = rawMsg,
                userFriendlyExplanation = "El selector de cuentas de Google fue cerrado antes de seleccionar una cuenta.",
                suggestedAction = "Presione nuevamente 'Sign in with Google' y elija la cuenta con la que desea ingresar."
            )
        }
        exception is androidx.credentials.exceptions.NoCredentialException || rawMsg.contains("No credential", ignoreCase = true) -> {
            GoogleSignInErrorInfo(
                title = "Sin Cuentas de Google",
                technicalMessage = rawMsg,
                userFriendlyExplanation = "No se detectaron cuentas de Google activas o sincronizadas en este dispositivo o emulador para Credential Manager.",
                suggestedAction = "Agregue o sincronice una cuenta de Google en los Ajustes del dispositivo, o utilice el acceso por correo / Modo Autónomo."
            )
        }
        rawMsg.contains("10:", ignoreCase = true) || rawMsg.contains("DEVELOPER_ERROR", ignoreCase = true) -> {
            GoogleSignInErrorInfo(
                title = "Error de Configuración (DEVELOPER_ERROR 10)",
                technicalMessage = rawMsg,
                userFriendlyExplanation = "La huella digital SHA-1 de la firma o el Web Client ID no coincide con los valores registrados en Firebase / Google Cloud Console.",
                suggestedAction = "Verifique que el Web Client ID ('$defaultWebClientId') y la huella SHA-1 de la app estén registrados en la consola de Firebase."
            )
        }
        rawMsg.contains("network", ignoreCase = true) || rawMsg.contains("timeout", ignoreCase = true) || rawMsg.contains("connect", ignoreCase = true) -> {
            GoogleSignInErrorInfo(
                title = "Falla de Conexión de Red",
                technicalMessage = rawMsg,
                userFriendlyExplanation = "No se pudo establecer comunicación con los servidores de autenticación de Google o Firebase.",
                suggestedAction = "Compruebe su conexión a Internet o utilice el Modo Autónomo Local para continuar operando sin interrupciones."
            )
        }
        rawMsg.contains("Firebase no está inicializado", ignoreCase = true) || rawMsg.contains("google-services.json", ignoreCase = true) -> {
            GoogleSignInErrorInfo(
                title = "Firebase No Configurado",
                technicalMessage = rawMsg,
                userFriendlyExplanation = "Firebase Auth no está activo porque no se ha colocado el archivo 'google-services.json' en /app.",
                suggestedAction = "Incorpore el archivo 'google-services.json' en el proyecto o continúe en Modo Local Autónomo."
            )
        }
        else -> {
            GoogleSignInErrorInfo(
                title = "Fallo en Google Sign-In",
                technicalMessage = rawMsg,
                userFriendlyExplanation = "Ocurrió un inconveniente al procesar la autenticación a través de Credential Manager.",
                suggestedAction = "Puede reintentar la operación o ingresar temporalmente mediante las opciones de rol local."
            )
        }
    }
}

/**
 * Pantalla Principal de Autenticación Firebase en MEDUSA ALFHA.
 * Soporta:
 * 1. Google Sign-In mediante Jetpack Credential Manager (Integración Oficial).
 * 2. Inicio de Sesión y Registro con Correo / Contraseña en Firebase Auth.
 * 3. Recuperación de Contraseña.
 * 4. Bypass táctico por Roles en Modo Local / Autónomo (Garantiza operación continua offline).
 */
@Composable
fun LoginScreen(
    onLoginSuccess: (AlfhaUserEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val db = remember { AppDatabase.getDatabase(context) }
    val authManager = remember { FirebaseAuthManager(context) }

    val authState by authManager.authState.collectAsState()
    val isFirebaseAvailable by FirebaseConfigHelper.isFirebaseAvailable.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var googleSignInErrorDialog by remember { mutableStateOf<GoogleSignInErrorInfo?>(null) }

    var selectedTabIndex by remember { mutableStateOf(0) } // 0: Iniciar Sesión, 1: Registro
    var emailInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var showQuickRoleAccess by remember { mutableStateOf(false) }
    var isGoogleLoading by remember { mutableStateOf(false) }

    val webClientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        "188729568141-medusa.apps.googleusercontent.com"
    }

    // Inicializar usuarios semilla de Room en caso de que esté vacía la base local
    LaunchedEffect(Unit) {
        AlfhaSecurityContext.seedInitialUsersIfEmpty(db)
    }

    // Manejo de respuesta tras autenticación exitosa en Firebase
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthUiState.Authenticated -> {
                scope.launch {
                    val localUser = authManager.syncFirebaseUserToLocalRoom(
                        db = db,
                        firebaseUser = state.user,
                        targetRole = AlfhaRole.RESIDENTE
                    )
                    Toast.makeText(context, "Bienvenido, ${localUser.name}", Toast.LENGTH_SHORT).show()
                    onLoginSuccess(localUser)
                }
            }
            is AuthUiState.Error -> {
                errorMessage = state.errorMessage
            }
            else -> Unit
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("login_screen_root"),
        color = NavyDark
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                item {
                    Spacer(modifier = Modifier.height(28.dp))

                    // Logotipo / Escudo Dorado Táctico
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = GoldPrimary.copy(alpha = 0.12f),
                        border = BorderStroke(2.dp, GoldPrimary)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "MEDUSA ALFHA Crest",
                                tint = GoldPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "MEDUSA ALFHA",
                        color = GoldPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )

                    Text(
                        text = "Control Táctico y Acceso Residencial",
                        color = TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Indicador de Estado de Firebase
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .background(NavySurface, RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isFirebaseAvailable) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (isFirebaseAvailable) SuccessGreen else CyanNeon,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isFirebaseAvailable) "Firebase Auth Activo" else "Modo Local + Nube Lista",
                            color = if (isFirebaseAvailable) SuccessGreen else CyanNeon,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Tarjeta Principal de Autenticación
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = NavyCard),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.25f))
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // BOTÓN PRINCIPAL: 'Sign in with Google' con Credential Manager
                            Button(
                                onClick = {
                                    errorMessage = null
                                    successMessage = null
                                    isGoogleLoading = true
                                    scope.launch {
                                        try {
                                            val result = authManager.signInWithGoogle(webClientId)
                                            if (result.isFailure) {
                                                val ex = result.exceptionOrNull()
                                                val errorInfo = parseGoogleSignInError(ex, webClientId)
                                                if (ex is androidx.credentials.exceptions.GetCredentialCancellationException) {
                                                    // Notificación sutil vía Snackbar por cancelación del usuario
                                                    snackbarHostState.showSnackbar(
                                                        message = "Inicio de sesión con Google cancelado",
                                                        duration = SnackbarDuration.Short
                                                    )
                                                } else {
                                                    // Desplegar Diálogo de Error Descriptivo con diagnóstico técnico y sugerencias
                                                    googleSignInErrorDialog = errorInfo
                                                    // También emitir Snackbar informativo con acción para reabrir diálogo
                                                    val snackbarRes = snackbarHostState.showSnackbar(
                                                        message = "${errorInfo.title}: ${errorInfo.userFriendlyExplanation.take(55)}...",
                                                        actionLabel = "Detalles",
                                                        duration = SnackbarDuration.Long
                                                    )
                                                    if (snackbarRes == SnackbarResult.ActionPerformed) {
                                                        googleSignInErrorDialog = errorInfo
                                                    }
                                                }
                                            }
                                        } finally {
                                            isGoogleLoading = false
                                        }
                                    }
                                },
                                enabled = !isGoogleLoading && authState !is AuthUiState.Loading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("sign_in_with_google_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = Color(0xFF1F1F1F),
                                    disabledContainerColor = Color.White.copy(alpha = 0.75f),
                                    disabledContentColor = Color(0xFF1F1F1F).copy(alpha = 0.75f)
                                ),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 2.dp,
                                    pressedElevation = 4.dp
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (isGoogleLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color(0xFF4285F4),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Conectando con Google...",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF1F1F1F)
                                        )
                                    } else {
                                        Image(
                                            painter = painterResource(id = R.drawable.ic_google_logo),
                                            contentDescription = "Google Logo",
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "Sign in with Google",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF1F1F1F)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Separador "o con correo"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Divider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.12f))
                                Text(
                                    text = "  O CON CREDENCIALES  ",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Divider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.12f))
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Pestañas Iniciar Sesión vs Registrarse
                            TabRow(
                                selectedTabIndex = selectedTabIndex,
                                containerColor = NavySurface,
                                contentColor = GoldPrimary,
                                indicator = { tabPositions ->
                                    TabRowDefaults.SecondaryIndicator(
                                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                        color = GoldPrimary,
                                        height = 2.dp
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(NavySurface, RoundedCornerShape(8.dp))
                            ) {
                                Tab(
                                    selected = selectedTabIndex == 0,
                                    onClick = {
                                        selectedTabIndex = 0
                                        errorMessage = null
                                    },
                                    text = {
                                        Text(
                                            "Iniciar Sesión",
                                            fontSize = 12.sp,
                                            fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                )
                                Tab(
                                    selected = selectedTabIndex == 1,
                                    onClick = {
                                        selectedTabIndex = 1
                                        errorMessage = null
                                    },
                                    text = {
                                        Text(
                                            "Registrarme",
                                            fontSize = 12.sp,
                                            fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Campo de Nombre (solo si es registro)
                            AnimatedVisibility(visible = selectedTabIndex == 1) {
                                Column {
                                    OutlinedTextField(
                                        value = nameInput,
                                        onValueChange = { nameInput = it },
                                        label = { Text("Nombre Completo") },
                                        leadingIcon = {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = GoldPrimary)
                                        },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("name_input"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GoldPrimary,
                                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }

                            // Campo de Correo Electrónico
                            OutlinedTextField(
                                value = emailInput,
                                onValueChange = { emailInput = it },
                                label = { Text("Correo Electrónico") },
                                leadingIcon = {
                                    Icon(Icons.Default.Email, contentDescription = null, tint = GoldPrimary)
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("email_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldPrimary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Campo de Contraseña
                            OutlinedTextField(
                                value = passwordInput,
                                onValueChange = { passwordInput = it },
                                label = { Text("Contraseña") },
                                leadingIcon = {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = GoldPrimary)
                                },
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (passwordVisible) "Ocultar contraseña" else "Mostrar contraseña",
                                            tint = TextMuted
                                        )
                                    }
                                },
                                singleLine = true,
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("password_input"),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldPrimary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            // Mensaje de Error
                            AnimatedVisibility(visible = errorMessage != null) {
                                errorMessage?.let { msg ->
                                    Text(
                                        text = "⚠️ $msg",
                                        color = ErrorRed,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 8.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            // Mensaje de Éxito
                            AnimatedVisibility(visible = successMessage != null) {
                                successMessage?.let { msg ->
                                    Text(
                                        text = "✅ $msg",
                                        color = SuccessGreen,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 8.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Botón de Enviar (Iniciar Sesión o Registro)
                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    errorMessage = null
                                    successMessage = null

                                    if (emailInput.isBlank() || passwordInput.isBlank()) {
                                        errorMessage = "Por favor completa todos los campos requeridos."
                                        return@Button
                                    }

                                    scope.launch {
                                        if (selectedTabIndex == 0) {
                                            // Iniciar Sesión con Firebase
                                            authManager.signInWithEmail(emailInput.trim(), passwordInput.trim())
                                        } else {
                                            // Registrar nuevo usuario
                                            val displayName = nameInput.ifBlank { emailInput.substringBefore("@") }
                                            authManager.signUpWithEmail(emailInput.trim(), passwordInput.trim(), displayName)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("login_submit_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = GoldPrimary,
                                    contentColor = NavyDark
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                if (authState is AuthUiState.Loading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = NavyDark,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text(
                                        text = if (selectedTabIndex == 0) "Entrar al Sistema" else "Crear Cuenta",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            // Recuperar contraseña
                            if (selectedTabIndex == 0) {
                                TextButton(
                                    onClick = {
                                        if (emailInput.isBlank()) {
                                            errorMessage = "Escribe tu correo arriba para enviarte el enlace de recuperación."
                                        } else {
                                            scope.launch {
                                                val res = authManager.sendPasswordReset(emailInput.trim())
                                                if (res.isSuccess) {
                                                    successMessage = "Enlace enviado a $emailInput. Revisa tu bandeja de entrada."
                                                } else {
                                                    errorMessage = res.exceptionOrNull()?.localizedMessage ?: "No se pudo enviar el correo"
                                                }
                                            }
                                        }
                                    }
                                ) {
                                    Text(
                                        "¿Olvidaste tu contraseña?",
                                        color = GoldAccent,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Sección 3: Acceso Directo por Rol Táctico / Modo Local Autónomo
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        OutlinedButton(
                            onClick = { showQuickRoleAccess = !showQuickRoleAccess },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = CyanNeon,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (showQuickRoleAccess) "Ocultar Acceso Táctico Rápido" else "Acceso Rápido por Rol (Demostración / Local)",
                                color = CyanNeon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        AnimatedVisibility(visible = showQuickRoleAccess) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Selecciona un perfil operativo registrado en Room SQLite:",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )

                                val quickRoles = listOf(
                                    Triple("USR-ALFHA-005", "Oficial Juan Pérez", "Garita Principal (Guardia)"),
                                    Triple("USR-ALFHA-006", "Familia Arismendi", "Residente (Casa 104)"),
                                    Triple("USR-ALFHA-001", "Ing. Carlos Mendoza", "Comando Central (Maestro ALFHA)"),
                                    Triple("USR-ALFHA-003", "Lic. Patricia Ruiz", "Administración General")
                                )

                                quickRoles.forEach { (userId, userName, userDesc) ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                scope.launch {
                                                    val success = AlfhaSecurityContext.switchActiveUser(db, userId)
                                                    if (success) {
                                                        val user = AlfhaSecurityContext.currentUser.value
                                                        Toast.makeText(context, "Ingresando como ${user.name}", Toast.LENGTH_SHORT).show()
                                                        onLoginSuccess(user)
                                                    }
                                                }
                                            },
                                        color = NavySurface,
                                        shape = RoundedCornerShape(8.dp),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccountCircle,
                                                contentDescription = null,
                                                tint = GoldPrimary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(userName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text(userDesc, color = TextMuted, fontSize = 10.sp)
                                            }
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = SuccessGreen,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }

            // SnackbarHost para notificaciones flotantes y avisos de autenticación
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .imePadding()
                    .testTag("login_snackbar_host"),
                snackbar = { snackbarData ->
                    Snackbar(
                        snackbarData = snackbarData,
                        containerColor = NavyCard,
                        contentColor = Color.White,
                        actionColor = GoldPrimary,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            )

            // Diálogo Modal Descriptivo de Error para Google Sign-In
            if (googleSignInErrorDialog != null) {
                val error = googleSignInErrorDialog!!
                AlertDialog(
                    onDismissRequest = { googleSignInErrorDialog = null },
                    icon = {
                        Surface(
                            modifier = Modifier.size(48.dp),
                            shape = CircleShape,
                            color = ErrorRed.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error de Autenticación",
                                    tint = ErrorRed,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    },
                    title = {
                        Text(
                            text = error.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = error.userFriendlyExplanation,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                lineHeight = 18.sp
                            )

                            if (error.suggestedAction != null) {
                                Surface(
                                    color = NavySurface,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Security,
                                                contentDescription = null,
                                                tint = GoldPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Acción Sugerida:",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = GoldPrimary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = error.suggestedAction,
                                            fontSize = 12.sp,
                                            color = TextMuted,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }

                            Surface(
                                color = NavyDark,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "Diagnóstico Técnico:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextMuted
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = error.technicalMessage,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White.copy(alpha = 0.7f),
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                googleSignInErrorDialog = null
                                isGoogleLoading = true
                                scope.launch {
                                    try {
                                        val retryResult = authManager.signInWithGoogle(webClientId)
                                        if (retryResult.isFailure) {
                                            val retryEx = retryResult.exceptionOrNull()
                                            val errorInfo = parseGoogleSignInError(retryEx, webClientId)
                                            if (retryEx is androidx.credentials.exceptions.GetCredentialCancellationException) {
                                                snackbarHostState.showSnackbar(
                                                    message = "Inicio de sesión con Google cancelado",
                                                    duration = SnackbarDuration.Short
                                                )
                                            } else {
                                                googleSignInErrorDialog = errorInfo
                                            }
                                        }
                                    } finally {
                                        isGoogleLoading = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldPrimary,
                                contentColor = NavyDark
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("google_error_dialog_retry_button")
                        ) {
                            Text("Reintentar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { googleSignInErrorDialog = null },
                            modifier = Modifier.testTag("google_error_dialog_dismiss_button")
                        ) {
                            Text("Cerrar", color = TextMuted, fontSize = 13.sp)
                        }
                    },
                    containerColor = NavyCard,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("google_sign_in_error_dialog")
                )
            }
        }
    }
}
