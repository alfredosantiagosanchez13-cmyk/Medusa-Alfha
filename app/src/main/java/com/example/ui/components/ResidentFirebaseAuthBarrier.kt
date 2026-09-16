package com.example.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
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
import com.example.auth.ResidentAuthStatus
import com.example.auth.ResidentFirebaseAuthGuard
import com.example.data.booking.AppDatabase
import com.example.data.resident.ResidentEntity
import com.example.ui.theme.AlertRed
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.launch

enum class ResidentAuthTab(val title: String) {
    LOGIN("Iniciar Sesión"),
    REGISTER("Registrarse"),
    VERIFIED_RESIDENTS("Residentes Validados")
}

/**
 * Barrera defensiva de seguridad con Firebase Auth:
 * Restringe el acceso al Escáner QR y al Módulo de Reservas de Amenidades
 * exclusivamente a residentes registrados y verificados.
 */
@Composable
fun ResidentFirebaseAuthBarrier(
    featureName: String,
    featureDescription: String,
    db: AppDatabase,
    modifier: Modifier = Modifier,
    onDismissOrBack: (() -> Unit)? = null,
    content: @Composable (ResidentEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authStatus by ResidentFirebaseAuthGuard.currentStatus.collectAsState()

    // Verificar automáticamente al cargar el componente
    LaunchedEffect(Unit) {
        ResidentFirebaseAuthGuard.verifyResidentAccess(context, db)
    }

    when (val status = authStatus) {
        is ResidentAuthStatus.Checking -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(NavyDark)
                    .testTag("resident_auth_barrier_checking"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(
                        color = GoldPrimary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "Verificando identidad en Firebase Auth...",
                        color = TextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Validando acreditación de residencia para: $featureName",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        is ResidentAuthStatus.Authorized -> {
            // Usuario autorizado y verificado como residente registrado
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .testTag("resident_auth_barrier_authorized")
            ) {
                // Barra de Estado de Residente Acreditado
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = NavySurface,
                    border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(SuccessGreen)
                            )
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Residente Acreditado:",
                                        color = TextMuted,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = status.resident.fullName,
                                        color = GoldPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Unidad: ${status.resident.unitId} • Firebase Auth Activo",
                                    color = CyanNeon,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                ResidentFirebaseAuthGuard.signOut(context)
                                Toast.makeText(context, "Sesión de residente cerrada", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Cerrar sesión de residente",
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Contenido protegido
                Box(modifier = Modifier.weight(1f)) {
                    content(status.resident)
                }
            }
        }

        else -> {
            // No autenticado o cuenta no registrada como residente
            ResidentAuthGateForm(
                featureName = featureName,
                featureDescription = featureDescription,
                db = db,
                currentStatus = status,
                onDismissOrBack = onDismissOrBack,
                modifier = modifier
            )
        }
    }
}

/**
 * Formulario táctico para autenticarse o registrarse como residente.
 */
@Composable
private fun ResidentAuthGateForm(
    featureName: String,
    featureDescription: String,
    db: AppDatabase,
    currentStatus: ResidentAuthStatus,
    onDismissOrBack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var selectedTab by remember { mutableStateOf(ResidentAuthTab.LOGIN) }

    // Campos de Login
    var loginEmail by remember { mutableStateOf("arismendi.residente@condominio.com") }
    var loginPassword by remember { mutableStateOf("Medusa2026!") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Campos de Registro
    var regFullName by remember { mutableStateOf("") }
    var regUnitId by remember { mutableStateOf("Casa 104") }
    var regPhone by remember { mutableStateOf("") }
    var regEmail by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var localErrorMessage by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("resident_auth_gate_form"),
        color = NavyDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Cabecera con botón de retroceso opcional
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (onDismissOrBack != null) {
                    IconButton(
                        onClick = onDismissOrBack,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = GoldPrimary
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(36.dp))
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = GoldPrimary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "CONTROL DE ACCESO RESIDENCIAL",
                        color = GoldPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(36.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Escudo de Seguridad Central
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(NavySurface)
                    .border(1.5.dp, GoldPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Seguridad",
                    tint = GoldPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Acceso Restringido a Residentes",
                color = TextWhite,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "La función '$featureName' está reservada exclusivamente para residentes registrados mediante Firebase Auth. Identifícate para continuar.",
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Selector de Pestañas
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = NavySurface,
                contentColor = GoldPrimary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = GoldPrimary
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
            ) {
                ResidentAuthTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = {
                            selectedTab = tab
                            localErrorMessage = null
                        },
                        text = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == tab) GoldPrimary else TextMuted
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Mensajes de Error de Estado
            val displayError = localErrorMessage
                ?: (currentStatus as? ResidentAuthStatus.Error)?.errorMessage
                ?: (currentStatus as? ResidentAuthStatus.NonResident)?.message

            AnimatedVisibility(
                visible = !displayError.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (displayError != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                            .testTag("auth_barrier_error_banner"),
                        shape = RoundedCornerShape(8.dp),
                        color = AlertRed.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = AlertRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = displayError,
                                color = TextWhite,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            when (selectedTab) {
                ResidentAuthTab.LOGIN -> {
                    // Formulario de Inicio de Sesión
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = loginEmail,
                            onValueChange = { loginEmail = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("barrier_login_email"),
                            label = { Text("Correo Electrónico del Residente", color = TextMuted) },
                            leadingIcon = {
                                Icon(Icons.Default.Email, contentDescription = null, tint = CyanNeon)
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = NavySurface,
                                unfocusedContainerColor = NavySurface
                            ),
                            shape = RoundedCornerShape(10.dp)
                        )

                        OutlinedTextField(
                            value = loginPassword,
                            onValueChange = { loginPassword = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("barrier_login_password"),
                            label = { Text("Contraseña", color = TextMuted) },
                            leadingIcon = {
                                Icon(Icons.Default.VpnKey, contentDescription = null, tint = CyanNeon)
                            },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Ver contraseña",
                                        tint = TextMuted
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    focusManager.clearFocus()
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
                            shape = RoundedCornerShape(10.dp)
                        )

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                isLoading = true
                                localErrorMessage = null
                                scope.launch {
                                    val result = ResidentFirebaseAuthGuard.signInWithEmail(
                                        context = context,
                                        db = db,
                                        email = loginEmail,
                                        pass = loginPassword
                                    )
                                    isLoading = false
                                    if (result.isFailure) {
                                        localErrorMessage = result.exceptionOrNull()?.message ?: "Error al autenticar"
                                    } else {
                                        Toast.makeText(context, "Bienvenido Residente", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("barrier_btn_sign_in"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldPrimary,
                                contentColor = NavyDark
                            ),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isLoading && loginEmail.isNotBlank() && loginPassword.isNotBlank()
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = NavyDark,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("VALIDANDO RESIDENCIA...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("AUTENTICAR CON FIREBASE AUTH", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                ResidentAuthTab.REGISTER -> {
                    // Formulario de Registro de Residente
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = regFullName,
                            onValueChange = { regFullName = it },
                            modifier = Modifier.fillMaxWidth().testTag("barrier_reg_name"),
                            label = { Text("Nombre Completo del Residente", color = TextMuted) },
                            leadingIcon = { Icon(Icons.Default.Person, null, tint = CyanNeon) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = NavySurface,
                                unfocusedContainerColor = NavySurface
                            )
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = regUnitId,
                                onValueChange = { regUnitId = it },
                                modifier = Modifier.weight(1f).testTag("barrier_reg_unit"),
                                label = { Text("Unidad / Casa", color = TextMuted) },
                                leadingIcon = { Icon(Icons.Default.Home, null, tint = CyanNeon) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldPrimary,
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    focusedContainerColor = NavySurface,
                                    unfocusedContainerColor = NavySurface
                                )
                            )

                            OutlinedTextField(
                                value = regPhone,
                                onValueChange = { regPhone = it },
                                modifier = Modifier.weight(1f).testTag("barrier_reg_phone"),
                                label = { Text("Teléfono", color = TextMuted) },
                                leadingIcon = { Icon(Icons.Default.Phone, null, tint = CyanNeon) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = GoldPrimary,
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = TextWhite,
                                    unfocusedTextColor = TextWhite,
                                    focusedContainerColor = NavySurface,
                                    unfocusedContainerColor = NavySurface
                                )
                            )
                        }

                        OutlinedTextField(
                            value = regEmail,
                            onValueChange = { regEmail = it },
                            modifier = Modifier.fillMaxWidth().testTag("barrier_reg_email"),
                            label = { Text("Correo Electrónico", color = TextMuted) },
                            leadingIcon = { Icon(Icons.Default.Email, null, tint = CyanNeon) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = NavySurface,
                                unfocusedContainerColor = NavySurface
                            )
                        )

                        OutlinedTextField(
                            value = regPassword,
                            onValueChange = { regPassword = it },
                            modifier = Modifier.fillMaxWidth().testTag("barrier_reg_password"),
                            label = { Text("Contraseña (mínimo 6 caracteres)", color = TextMuted) },
                            leadingIcon = { Icon(Icons.Default.VpnKey, null, tint = CyanNeon) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite,
                                focusedContainerColor = NavySurface,
                                unfocusedContainerColor = NavySurface
                            )
                        )

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                isLoading = true
                                localErrorMessage = null
                                scope.launch {
                                    val result = ResidentFirebaseAuthGuard.registerResident(
                                        context = context,
                                        db = db,
                                        email = regEmail,
                                        pass = regPassword,
                                        fullName = regFullName,
                                        unitId = regUnitId,
                                        phone = regPhone
                                    )
                                    isLoading = false
                                    if (result.isFailure) {
                                        localErrorMessage = result.exceptionOrNull()?.message ?: "Error al registrar residente"
                                    } else {
                                        Toast.makeText(context, "Residente registrado exitosamente", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("barrier_btn_register"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanNeon,
                                contentColor = NavyDark
                            ),
                            shape = RoundedCornerShape(10.dp),
                            enabled = !isLoading && regFullName.isNotBlank() && regEmail.isNotBlank() && regPassword.length >= 6
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NavyDark)
                            } else {
                                Text("REGISTRAR RESIDENTE EN FIREBASE", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                ResidentAuthTab.VERIFIED_RESIDENTS -> {
                    // Acceso Rápido con Cuentas de Demostración Verificadas
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "SELECCIONA UN RESIDENTE VERIFICADO DE PRUEBA:",
                            color = GoldPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )

                        val sampleResidents = listOf(
                            Triple("Familia Arismendi", "arismendi.residente@condominio.com", "Manzana A - Casa 104"),
                            Triple("Ing. Rodrigo Morales", "rodrigo.morales@empresa.com", "Manzana A - Casa 101"),
                            Triple("Lic. Mariana Navarro", "mariana.navarro@consultora.com", "Torre 1 - Depto 201"),
                            Triple("Arq. Diego Cárdenas", "diego.cardenas@arquitectura.com", "Torre 2 - Depto 401")
                        )

                        sampleResidents.forEach { (name, email, unit) ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isLoading = true
                                        localErrorMessage = null
                                        scope.launch {
                                            val result = ResidentFirebaseAuthGuard.authenticateVerifiedResident(
                                                context = context,
                                                db = db,
                                                residentEmail = email
                                            )
                                            isLoading = false
                                            if (result.isFailure) {
                                                localErrorMessage = result.exceptionOrNull()?.message
                                            } else {
                                                Toast.makeText(context, "Sesión activa: $name", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    .testTag("barrier_demo_resident_${email.replace("@", "_").replace(".", "_")}"),
                                shape = RoundedCornerShape(8.dp),
                                color = NavySurface,
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(GoldPrimary.copy(alpha = 0.15f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.AccountCircle, null, tint = GoldPrimary, modifier = Modifier.size(20.dp))
                                        }
                                        Column {
                                            Text(text = name, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            Text(text = "$unit • $email", color = TextMuted, fontSize = 11.sp)
                                        }
                                    }
                                    Icon(Icons.Default.CheckCircle, null, tint = CyanNeon, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
