package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.auth.AlfhaUserEntity
import com.example.data.booking.AppDatabase
import com.example.data.firebase.FirebaseConfigHelper
import com.example.ui.theme.*
import kotlinx.coroutines.launch

enum class FirebaseHubTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    GUIDE("1. Guía Paso a Paso", Icons.Default.MenuBook),
    CONNECT("2. Vincular Firebase", Icons.Default.CloudSync),
    ACCOUNTS("3. Alta de Cuentas", Icons.Default.GroupAdd)
}

/**
 * CENTRO OFICIAL DE ALTA Y CONFIGURACIÓN FIREBASE (FIRESTONE) & CUENTAS OPERATIVAS.
 * MEDUSA ALFHA · TIEMPO = FAMILIA.
 */
@Composable
fun FirebaseSetupCenterHub(
    db: AppDatabase,
    onDismiss: () -> Unit,
    onSelectAccount: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(FirebaseHubTab.GUIDE) }
    val isFirebaseOnline by FirebaseConfigHelper.isFirebaseAvailable.collectAsState()
    val statusMessage by FirebaseConfigHelper.initializationStatusMessage.collectAsState()

    var testStatusResult by remember { mutableStateOf<String?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }

    var jsonInput by remember { mutableStateOf("") }
    var manualApiKey by remember { mutableStateOf("") }
    var manualAppId by remember { mutableStateOf("") }
    var manualProjectId by remember { mutableStateOf("") }
    var useManualFields by remember { mutableStateOf(false) }

    var provisionResult by remember { mutableStateOf<List<AlfhaUserEntity>?>(null) }
    var isProvisioning by remember { mutableStateOf(false) }

    // Pre-cargar valores existentes si los hay
    LaunchedEffect(Unit) {
        val saved = FirebaseConfigHelper.getSavedConfig(context)
        manualApiKey = saved["apiKey"] ?: ""
        manualAppId = saved["appId"] ?: ""
        manualProjectId = saved["projectId"] ?: ""
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .testTag("firebase_setup_dialog_root"),
            shape = RoundedCornerShape(16.dp),
            color = NavyDark,
            border = BorderStroke(1.5.dp, GoldPrimary.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(NavySurface)
                                .border(1.2.dp, GoldPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "ALTA FIREBASE & CUENTAS OPERATIVAS",
                                color = GoldPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = "MEDUSA ALFHA · Conexión Firestore y Padrón Oficial",
                                color = TextWhite.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Estado de Conexión en Vivo
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isFirebaseOnline) SuccessGreen.copy(alpha = 0.12f) else WarningOrange.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, if (isFirebaseOnline) SuccessGreen.copy(alpha = 0.4f) else WarningOrange.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                    .background(if (isFirebaseOnline) SuccessGreen else WarningOrange)
                            )
                            Text(
                                text = if (isFirebaseOnline) "NUBE FIRESTORE: CONECTADA Y ACTIVA" else "MODO LOCAL AUTÓNOMO (ROOM SQLITE)",
                                color = if (isFirebaseOnline) SuccessGreen else WarningOrange,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }

                        Text(
                            text = if (isFirebaseOnline) "Sincronización On" else "Sin Nube",
                            color = TextWhite,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Selector de Pestañas
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = NavySurface,
                    contentColor = GoldPrimary,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                ) {
                    FirebaseHubTab.values().forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = {
                                Text(
                                    text = tab.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == tab) GoldPrimary else TextMuted
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    tint = if (selectedTab == tab) GoldPrimary else TextMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Contenido de la Pestaña
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (selectedTab) {
                        FirebaseHubTab.GUIDE -> FirebaseGuideStepContent(
                            onSwitchToConnect = { selectedTab = FirebaseHubTab.CONNECT },
                            onSwitchToAccounts = { selectedTab = FirebaseHubTab.ACCOUNTS }
                        )
                        FirebaseHubTab.CONNECT -> FirebaseConnectionTabContent(
                            jsonInput = jsonInput,
                            onJsonInputChange = { jsonInput = it },
                            manualApiKey = manualApiKey,
                            onManualApiKeyChange = { manualApiKey = it },
                            manualAppId = manualAppId,
                            onManualAppIdChange = { manualAppId = it },
                            manualProjectId = manualProjectId,
                            onManualProjectIdChange = { manualProjectId = it },
                            useManualFields = useManualFields,
                            onToggleManualFields = { useManualFields = !useManualFields },
                            isTestingConnection = isTestingConnection,
                            testStatusResult = testStatusResult,
                            onSaveAndConnect = {
                                scope.launch {
                                    val result = if (!useManualFields && jsonInput.isNotBlank()) {
                                        FirebaseConfigHelper.configureFromGoogleServicesJson(context, jsonInput)
                                    } else {
                                        FirebaseConfigHelper.configureCustomFirebase(
                                            context = context,
                                            apiKey = manualApiKey,
                                            appId = manualAppId,
                                            projectId = manualProjectId
                                        )
                                    }

                                    if (result.isSuccess) {
                                        Toast.makeText(context, "¡Firebase vinculado con éxito!", Toast.LENGTH_SHORT).show()
                                        // Ejecutar prueba de ping
                                        isTestingConnection = true
                                        val pingRes = FirebaseConfigHelper.testFirestoreConnection()
                                        isTestingConnection = false
                                        testStatusResult = if (pingRes.isSuccess) pingRes.getOrNull() else "Error de enlace: ${pingRes.exceptionOrNull()?.message}"
                                    } else {
                                        testStatusResult = "Error: ${result.exceptionOrNull()?.message}"
                                        Toast.makeText(context, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onTestPing = {
                                scope.launch {
                                    isTestingConnection = true
                                    val pingRes = FirebaseConfigHelper.testFirestoreConnection()
                                    isTestingConnection = false
                                    testStatusResult = if (pingRes.isSuccess) {
                                        pingRes.getOrNull()
                                    } else {
                                        "Error: ${pingRes.exceptionOrNull()?.message}"
                                    }
                                }
                            }
                        )
                        FirebaseHubTab.ACCOUNTS -> FirebaseAccountsProvisioningTabContent(
                            db = db,
                            isProvisioning = isProvisioning,
                            provisionResult = provisionResult,
                            onProvisionAccounts = {
                                scope.launch {
                                    isProvisioning = true
                                    val res = FirebaseConfigHelper.provisionOfficialAccounts(context, db)
                                    isProvisioning = false
                                    if (res.isSuccess) {
                                        provisionResult = res.getOrNull()
                                        Toast.makeText(context, "¡7 Cuentas Operativas dadas de alta con éxito!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Error al dar de alta cuentas: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onSelectAccount = { key ->
                                onSelectAccount?.invoke(key)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FirebaseGuideStepContent(
    onSwitchToConnect: () -> Unit,
    onSwitchToAccounts: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Copiado al portapapeles: $label", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = NavySurface,
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "DATOS TÉCNICOS OBLIGATORIOS PARA TU PROYECTO FIREBASE",
                    color = GoldPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                TechnicalDataCopyRow(
                    label = "ID de Paquete Android (Package Name)",
                    value = FirebaseConfigHelper.OFFICIAL_PACKAGE_NAME,
                    onCopy = { copyToClipboard("Package Name", FirebaseConfigHelper.OFFICIAL_PACKAGE_NAME) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                TechnicalDataCopyRow(
                    label = "Cuenta Google del Proyecto",
                    value = "alfhaseguridad070@gmail.com",
                    onCopy = { copyToClipboard("Cuenta Google", "alfhaseguridad070@gmail.com") }
                )

                Spacer(modifier = Modifier.height(8.dp))

                TechnicalDataCopyRow(
                    label = "Nombre Sugerido de Proyecto",
                    value = "MEDUSA ALFHA",
                    onCopy = { copyToClipboard("Nombre Proyecto", "MEDUSA ALFHA") }
                )

                Spacer(modifier = Modifier.height(8.dp))

                TechnicalDataCopyRow(
                    label = "Condominio Predeterminado",
                    value = FirebaseConfigHelper.DEFAULT_CONDOMINIUM_ID,
                    onCopy = { copyToClipboard("Condominio", FirebaseConfigHelper.DEFAULT_CONDOMINIUM_ID) }
                )
            }
        }

        // Pasos detallados
        GuideStepCard(
            stepNumber = "1",
            title = "Crear el Proyecto en Firebase Console",
            description = "Abre en tu navegador https://console.firebase.google.com con tu cuenta alfhaseguridad070@gmail.com. Haz clic en 'Crear proyecto' y nómbralo 'MEDUSA ALFHA'."
        )

        GuideStepCard(
            stepNumber = "2",
            title = "Registrar la Aplicación Android",
            description = "Dentro del proyecto en Firebase, haz clic en el ícono de Android para agregar una app. En 'Nombre del paquete de Android' pega exactamente:\ncom.aistudio.medusaalpha.qxvtkm"
        )

        GuideStepCard(
            stepNumber = "3",
            title = "Crear Base de Datos Cloud Firestore",
            description = "En el menú izquierdo ve a Compilación > Firestore Database. Haz clic en 'Crear base de datos', selecciona Modo de Producción y ubicación (ej. us-central1 o us-east1). Las reglas de seguridad ya aíslan cada condominio."
        )

        GuideStepCard(
            stepNumber = "4",
            title = "Habilitar Firebase Authentication",
            description = "En el menú ve a Compilación > Authentication. Haz clic en 'Comenzar' y activa los métodos de acceso: Correo electrónico/contraseña, Google y Anónimo."
        )

        GuideStepCard(
            stepNumber = "5",
            title = "Descargar google-services.json y Vincular",
            description = "Descarga el archivo google-services.json que te da Firebase. Copia su texto y pégalo en la pestaña '2. Vincular Firebase' de esta ventana, o ingresa su Project ID y API Key."
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onSwitchToConnect,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark)
            ) {
                Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("PASO 2: VINCULAR CREDENCIALES", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = onSwitchToAccounts,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, GoldPrimary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldPrimary)
            ) {
                Icon(imageVector = Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("PASO 3: ALTA CUENTAS", fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun FirebaseConnectionTabContent(
    jsonInput: String,
    onJsonInputChange: (String) -> Unit,
    manualApiKey: String,
    onManualApiKeyChange: (String) -> Unit,
    manualAppId: String,
    onManualAppIdChange: (String) -> Unit,
    manualProjectId: String,
    onManualProjectIdChange: (String) -> Unit,
    useManualFields: Boolean,
    onToggleManualFields: () -> Unit,
    isTestingConnection: Boolean,
    testStatusResult: String?,
    onSaveAndConnect: () -> Unit,
    onTestPing: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = NavySurface,
            border = BorderStroke(1.dp, Color(0xFF1E293B)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (useManualFields) "ENTRADA MANUAL DE CREDENCIALES" else "PEGAR CONTENIDO DE google-services.json",
                        color = GoldPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )

                    TextButton(onClick = onToggleManualFields) {
                        Text(
                            text = if (useManualFields) "Cambiar a Modo JSON" else "Ingreso Manual (3 campos)",
                            color = CyanNeon,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!useManualFields) {
                    OutlinedTextField(
                        value = jsonInput,
                        onValueChange = onJsonInputChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        label = { Text("Contenido completo de google-services.json", color = TextMuted) },
                        placeholder = { Text("Pega aquí el texto completo del archivo JSON descargado de Firebase...", color = TextMuted.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedContainerColor = NavyDark,
                            unfocusedContainerColor = NavyDark
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = manualProjectId,
                        onValueChange = onManualProjectIdChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Project ID (ej. medusa-alfha)", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedContainerColor = NavyDark,
                            unfocusedContainerColor = NavyDark
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = manualApiKey,
                        onValueChange = onManualApiKeyChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Key (Web API Key de Firebase)", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedContainerColor = NavyDark,
                            unfocusedContainerColor = NavyDark
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = manualAppId,
                        onValueChange = onManualAppIdChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Application ID (ej. 1:123456789:android:abcdef)", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedContainerColor = NavyDark,
                            unfocusedContainerColor = NavyDark
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSaveAndConnect,
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark)
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("VINCULAR FIREBASE", fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onTestPing,
                        modifier = Modifier
                            .weight(0.8f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.2.dp, CyanNeon),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon)
                    ) {
                        if (isTestingConnection) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = CyanNeon, strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PROBAR PING", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Banner de resultado del test
        AnimatedVisibility(
            visible = testStatusResult != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val isSuccess = testStatusResult?.startsWith("¡") == true
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isSuccess) SuccessGreen.copy(alpha = 0.15f) else AlertRed.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, if (isSuccess) SuccessGreen else AlertRed),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (isSuccess) SuccessGreen else AlertRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = testStatusResult ?: "",
                        color = TextWhite,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FirebaseAccountsProvisioningTabContent(
    db: AppDatabase,
    isProvisioning: Boolean,
    provisionResult: List<AlfhaUserEntity>?,
    onProvisionAccounts: () -> Unit,
    onSelectAccount: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    val accountsList = listOf(
        OfficialAccountInfo(
            id = "USR-ALFHA-000",
            title = "MAESTRO ALFA (SUPERADMIN)",
            name = "Ing. Carlos Mendoza (Comando Central ALFHA)",
            email = "alfhaseguridad070@gmail.com",
            role = "MAESTRO_ALFHA",
            unit = "Comando Central ALFHA",
            status = "ACTIVO · NUBE",
            badgeColor = GoldPrimary
        ),
        OfficialAccountInfo(
            id = "USR-ALFHA-001",
            title = "MAESTRO ALFA (COMANDO)",
            name = "Ing. Carlos Mendoza (Maestro Alfa)",
            email = "carlos.mendoza@alfhaseguridad.com",
            role = "MAESTRO_ALFHA",
            unit = "Comando Central ALFHA",
            status = "ACTIVO · NUBE",
            badgeColor = GoldPrimary
        ),
        OfficialAccountInfo(
            id = "USR-ALFHA-002",
            title = "MESA DIRECTIVA",
            name = "Lic. Roberto Garza (Presidente)",
            email = "mesa.directiva@condominio.com",
            role = "MESA_DIRECTIVA",
            unit = "Presidencia y Consejo Directivo",
            status = "ACTIVO · NUBE",
            badgeColor = CyanNeon
        ),
        OfficialAccountInfo(
            id = "USR-ALFHA-003",
            title = "ADMINISTRACIÓN GENERAL",
            name = "Lic. Patricia Ruiz",
            email = "administracion@condominio.com",
            role = "ADMINISTRACION",
            unit = "Oficina de Administración Los Prados",
            status = "ACTIVO · NUBE",
            badgeColor = Color(0xFF60A5FA)
        ),
        OfficialAccountInfo(
            id = "USR-ALFHA-004",
            title = "SUPERVISIÓN TÁCTICA",
            name = "Comandante Roberto Gómez",
            email = "roberto.gomez@alfhaseguridad.com",
            role = "SUPERVISOR",
            unit = "Supervisión Operativa Táctica y Rondas",
            status = "ACTIVO · NUBE",
            badgeColor = Color(0xFFA78BFA)
        ),
        OfficialAccountInfo(
            id = "USR-ALFHA-005",
            title = "GUARDIA DE CASETA",
            name = "Oficial Juan Pérez",
            email = "caseta1@alfhaseguridad.com",
            role = "GUARDIA",
            unit = "Garita de Acceso Vehicular Los Prados",
            status = "ACTIVO · NUBE",
            badgeColor = SuccessGreen
        ),
        OfficialAccountInfo(
            id = "USR-ALFHA-006",
            title = "RESIDENTE TITULAR",
            name = "Familia Arismendi",
            email = "arismendi.residente@condominio.com",
            role = "RESIDENTE",
            unit = "Casa 54 · Circuito Los Álamos (Prados)",
            status = "ACTIVO · NUBE",
            badgeColor = WarningOrange
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Botón Maestro de Alta
        Button(
            onClick = onProvisionAccounts,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark)
        ) {
            if (isProvisioning) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NavyDark, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("SINCRONIZANDO 7 CUENTAS (ROOM + NUBE)...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            } else {
                Icon(imageVector = Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("SINCRONIZAR 7 CUENTAS OFICIALES EN FIRESTORE", fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }

        Text(
            text = "Padrón auditado de 7 Cuentas Oficiales. Selecciona una cuenta para autenticar de forma segura:",
            color = TextMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp
        )

        // Tarjetas de Cuentas
        accountsList.forEach { account ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = NavySurface,
                border = BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = account.badgeColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.8.dp, account.badgeColor)
                        ) {
                            Text(
                                text = "${account.id} · ${account.title}",
                                color = account.badgeColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Button(
                            onClick = { onSelectAccount(account.email) },
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon.copy(alpha = 0.2f), contentColor = CyanNeon),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                        ) {
                            Text("SELECCIONAR", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(text = account.name, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(text = "Correo: ${account.email}", color = CyanNeon, fontSize = 11.sp)
                    Text(text = "Adscripción: ${account.unit}", color = TextMuted, fontSize = 10.sp)

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(text = "Estado:", color = TextMuted, fontSize = 10.sp)
                        Surface(
                            color = NavyDark,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = account.status,
                                color = SuccessGreen,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class OfficialAccountInfo(
    val id: String,
    val title: String,
    val name: String,
    val email: String,
    val role: String,
    val unit: String,
    val status: String,
    val badgeColor: Color
)

@Composable
private fun TechnicalDataCopyRow(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, color = TextMuted, fontSize = 10.sp)
            SelectionContainer {
                Text(text = value, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copiar", tint = GoldPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun GuideStepCard(
    stepNumber: String,
    title: String,
    description: String
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = NavySurface,
        border = BorderStroke(1.dp, Color(0xFF1E293B)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(GoldPrimary.copy(alpha = 0.2f))
                    .border(1.dp, GoldPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = stepNumber, color = GoldPrimary, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(2.dp))
                SelectionContainer {
                    Text(text = description, color = TextMuted, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        }
    }
}
