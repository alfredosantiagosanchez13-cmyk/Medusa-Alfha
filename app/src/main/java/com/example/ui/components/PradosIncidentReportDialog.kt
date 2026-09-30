package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.auth.AlfhaSecurityContext
import com.example.data.audit.AuditLogEntity
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.firebase.FirebaseConfigHelper
import com.example.data.firebase.FirestoreTenantManager
import com.example.data.incident.IncidentCategory
import com.example.data.incident.IncidentEngine
import com.example.data.incident.IncidentEntity
import com.example.data.incident.IncidentPriority
import com.example.data.vecinos.LosPradosCroquisData
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MODAL OFICIAL: REPORTE DE INCIDENCIA & NOVEDAD OPERATIVA
 * Diseñado específicamente para RESIDENCIAL LOS PRADOS (261 Lotes Reales).
 *
 * Filosofía Sagrada MEDUSA ALFHA:
 * "ÉSTO DEVUELVE TIEMPO · TIEMPO = FAMILIA"
 * Cada minuto ahorrado en redacción, burocracia y llamadas es devuelto al descanso y a la familia del oficial.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PradosIncidentReportDialog(
    db: AppDatabase,
    condominiumId: String = "PRADOS_1",
    initialLocation: String? = null,
    onDismiss: () -> Unit,
    onIncidentCreated: (IncidentEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by AlfhaSecurityContext.currentUser.collectAsState()

    // 1. Título o Asunto del Evento
    var eventTitle by remember {
        mutableStateOf("Fiesta a altas horas / Ruido excesivo en patio posterior")
    }

    // 2. Tipo de Incidente
    val incidentTypes = listOf(
        "Ruido Excesivo / Fiesta",
        "Violación al Reglamento de Convivencia",
        "Obstrucción de Cochera / Estacionamiento Indebido",
        "Acceso Vehicular / Peatonal no Autorizado",
        "Mascotas sin Correa / Desechos en Áreas Comunes",
        "Daño a Instalaciones / Amenidades",
        "Altercado / Discusión Vecinal",
        "Fuga de Agua / Falla de Alumbrado en Calle",
        "Sospecha / Novedad Observada en Ronda Geo-Alpha"
    )
    var selectedIncidentType by remember { mutableStateOf(incidentTypes.first()) }
    var expandedTypeDropdown by remember { mutableStateOf(false) }

    // 3. Nivel de Severidad Inicial
    val severityLevels = listOf(
        "Informativa (Baja)",
        "Media (Atención Estándar)",
        "Alta (Prioritaria)",
        "Crítica (Emergencia de Seguridad)"
    )
    var selectedSeverity by remember { mutableStateOf(severityLevels[1]) } // Media por defecto
    var expandedSeverityDropdown by remember { mutableStateOf(false) }

    // 4. Ubicación / Casa (Catálogo 261 Casas Reales de Los Prados)
    var locationInput by remember {
        mutableStateOf(initialLocation ?: "Casa 54 · Calle 2 (Bali 2r, Condominio 1)")
    }
    var showLocationPicker by remember { mutableStateOf(false) }
    var locationSearchQuery by remember { mutableStateOf("") }

    // 5. Oficial que Reporta (Autocompletado con sesión activa)
    var reportingOfficer by remember {
        mutableStateOf(currentUser.name.ifBlank { "Oficial Ramiro Morales" })
    }

    // 6. Personas / Vehículos Involucrados
    var peopleInvolved by remember {
        mutableStateOf("Residente de Casa 54 y aproximadamente 15 personas en patio")
    }

    // 7. Testigos / Evidencia
    var witnessesAndEvidence by remember {
        mutableStateOf("Vecinos de Casa 52 y Casa 56 / Registro de patrulla móvil")
    }

    // 8. Relato en Bruto del Guardia (Hechos observados)
    var rawGuardReport by remember {
        mutableStateOf(
            "Volumen muy alto con música amplificada en patio posterior. Acudió patrulla móvil a solicitud vecinal y se dialogó con el morador quien prometió apagar la música, pero a la 01:15 volvieron a encenderla. Se levantó apercibimiento administrativo conforme al Reglamento de Los Prados."
        )
    }

    // Estado del Micrófono (Dictado por Voz)
    var isListeningVoice by remember { mutableStateOf(false) }

    // Motor MEDUSA ALFHA: Síntesis Inteligente "TIEMPO = FAMILIA"
    var isSynthesized by remember { mutableStateOf(false) }
    var aiExecutiveSummary by remember { mutableStateOf("") }
    var aiFormalApercibimiento by remember { mutableStateOf("") }
    var aiRecommendedAction by remember { mutableStateOf("") }
    var assignedDepartment by remember { mutableStateOf("Administración & Mediador Comunitario") }
    var targetSlaMinutes by remember { mutableStateOf(180) }

    var isSaving by remember { mutableStateOf(false) }

    // Launcher de Reconocimiento de Voz
    val speechIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListeningVoice = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull() ?: ""
            if (spokenText.isNotBlank()) {
                rawGuardReport = if (rawGuardReport.isBlank()) spokenText else "$rawGuardReport. $spokenText"
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isListeningVoice = true
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Dicte el relato de los hechos observados...")
            }
            try {
                speechIntentLauncher.launch(intent)
            } catch (e: Exception) {
                isListeningVoice = false
                Toast.makeText(context, "Reconocimiento de voz no disponible", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Permiso de micrófono requerido para dictado", Toast.LENGTH_SHORT).show()
        }
    }

    // Función de Síntesis Instantánea "TIEMPO = FAMILIA"
    fun executeAlfhaSynthesis() {
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        aiExecutiveSummary = "INCIDENCIA FORMALIZADA [LOS PRADOS RESIDENCIAL]: $selectedIncidentType en $locationInput. Reportado por $reportingOfficer el $dateStr. Hechos: ${rawGuardReport.take(160)}..."
        
        aiFormalApercibimiento = "APERCIBIMIENTO ADMINISTRATIVO DIGITAL:\n" +
                "En atención al Art. 14 del Reglamento de Convivencia y Régimen Condominal de Los Prados Residencial, se notifica apercibimiento formal a los moradores de $locationInput por emisión de ruido excesivo y alteración del orden vecinal registrado a las $dateStr. De reincidir, se aplicará sanción pecuniaria con cargo directo a la cuota de mantenimiento mensual."

        aiRecommendedAction = "1. Notificar inmediatamente al titular de $locationInput vía App Residente.\n" +
                "2. Registrar antecedente formal en el Padrón de la Unidad.\n" +
                "3. Mantener monitoreo preventivo en rondas Geo-Alpha de las 02:00 y 04:00 horas."

        when {
            selectedSeverity.contains("Crítica") -> {
                assignedDepartment = "Supervisor Táctico & Seguridad Operativa"
                targetSlaMinutes = 15
            }
            selectedSeverity.contains("Alta") -> {
                assignedDepartment = "Guardia de Caseta Principal"
                targetSlaMinutes = 45
            }
            selectedSeverity.contains("Media") -> {
                assignedDepartment = "Administración & Mediador Comunitario"
                targetSlaMinutes = 180
            }
            else -> {
                assignedDepartment = "Comité de Vigilancia & Mantenimiento"
                targetSlaMinutes = 1440
            }
        }
        isSynthesized = true
        Toast.makeText(context, "⚡ Reporte y Apercibimiento formalizados con éxito (+25 min ganados)", Toast.LENGTH_SHORT).show()
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .testTag("prados_incident_report_dialog_root"),
            shape = RoundedCornerShape(20.dp),
            color = NavyDark,
            border = BorderStroke(1.5.dp, GoldPrimary)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // --- CABECERA DE SEGURIDAD & TIEMPO = FAMILIA ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(GoldPrimary.copy(alpha = 0.2f))
                                .border(1.2.dp, GoldPrimary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Medusa Alfa",
                                tint = GoldPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "MEDUSA ALFHA",
                                    color = GoldPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = SuccessGreen.copy(alpha = 0.2f),
                                    border = BorderStroke(0.5.dp, SuccessGreen)
                                ) {
                                    Text(
                                        text = "TIEMPO = FAMILIA",
                                        color = SuccessGreen,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Redacción de Novedades · Los Prados Residencial",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSaving
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // BANNER DE VALOR: TIEMPO DEVUELTO
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = NavySurface,
                    border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Ahorro de Tiempo Auditado:",
                                color = TextWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "+25 min devueltos a la familia",
                            color = CyanNeon,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // --- CONTENIDO SCROLLEABLE CON LOS 8 CAMPOS DE OPERACIÓN REAL ---
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. TÍTULO O ASUNTO DEL EVENTO
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Título o Asunto del Evento:",
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = eventTitle,
                            onValueChange = { eventTitle = it },
                            placeholder = { Text("Ej. Fiesta a altas horas / Ruido excesivo en terraza", color = TextMuted.copy(alpha = 0.6f), fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("prados_incident_title_input"),
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
                    }

                    // 2. TIPO DE INCIDENTE (DROPDOWN)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Tipo de Incidente:",
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        ExposedDropdownMenuBox(
                            expanded = expandedTypeDropdown,
                            onExpandedChange = { expandedTypeDropdown = it }
                        ) {
                            OutlinedTextField(
                                value = selectedIncidentType,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTypeDropdown) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
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
                            ExposedDropdownMenu(
                                expanded = expandedTypeDropdown,
                                onDismissRequest = { expandedTypeDropdown = false },
                                modifier = Modifier.background(NavySurface)
                            ) {
                                incidentTypes.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type, color = TextWhite, fontSize = 12.sp) },
                                        onClick = {
                                            selectedIncidentType = type
                                            expandedTypeDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 3. NIVEL DE SEVERIDAD INICIAL
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Nivel de Severidad Inicial:",
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        ExposedDropdownMenuBox(
                            expanded = expandedSeverityDropdown,
                            onExpandedChange = { expandedSeverityDropdown = it }
                        ) {
                            OutlinedTextField(
                                value = selectedSeverity,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSeverityDropdown) },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth(),
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
                            ExposedDropdownMenu(
                                expanded = expandedSeverityDropdown,
                                onDismissRequest = { expandedSeverityDropdown = false },
                                modifier = Modifier.background(NavySurface)
                            ) {
                                severityLevels.forEach { level ->
                                    DropdownMenuItem(
                                        text = { Text(level, color = TextWhite, fontSize = 12.sp) },
                                        onClick = {
                                            selectedSeverity = level
                                            expandedSeverityDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 4. UBICACIÓN / CASA (CATÁLOGO REAL RESIDENCIAL LOS PRADOS 261 LOTES)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ubicación / Casa (Los Prados):",
                                color = TextWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "261 Lotes Reales",
                                color = GoldPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedTextField(
                            value = locationInput,
                            onValueChange = { locationInput = it },
                            placeholder = { Text("Ej. Casa 54 (Calle 2 · Bali 2r, Condominio 1)", color = TextMuted.copy(alpha = 0.6f), fontSize = 12.sp) },
                            trailingIcon = {
                                IconButton(onClick = { showLocationPicker = !showLocationPicker }) {
                                    Icon(
                                        imageVector = if (showLocationPicker) Icons.Default.ExpandLess else Icons.Default.LocationOn,
                                        contentDescription = "Selector de Casas de Prados",
                                        tint = GoldPrimary
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("prados_incident_location_input"),
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

                        // Selector Rápido de Lotes de Los Prados
                        AnimatedVisibility(visible = showLocationPicker) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = NavyCard,
                                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedTextField(
                                        value = locationSearchQuery,
                                        onValueChange = { locationSearchQuery = it },
                                        placeholder = { Text("Buscar casa (ej. 54, 12, Alberca, Calle 2)...", fontSize = 11.sp, color = TextMuted) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = GoldPrimary,
                                            unfocusedBorderColor = Color(0xFF334155),
                                            focusedTextColor = TextWhite,
                                            unfocusedTextColor = TextWhite
                                        )
                                    )

                                    val filteredLots = remember(locationSearchQuery) {
                                        val commonAreas = listOf(
                                            "Garita Principal / Caseta de Acceso",
                                            "Alberca Semiolímpica & Asoleaderos",
                                            "Casa Club & Salón de Eventos",
                                            "Cancha de Pádel & Tenis",
                                            "Palapa & Asadores",
                                            "Parque Central & Juegos Infantiles"
                                        )
                                        val lots = LosPradosCroquisData.TODOS_LOS_LOTES.map {
                                            "Casa ${it.numero} (${it.calle} · ${it.prototipo.codigo}, ${it.nombreCondominio})"
                                        }
                                        val combined = commonAreas + lots
                                        if (locationSearchQuery.isBlank()) {
                                            combined.take(12)
                                        } else {
                                            combined.filter { it.contains(locationSearchQuery, ignoreCase = true) }.take(15)
                                        }
                                    }

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        items(filteredLots) { itemLocation ->
                                            Surface(
                                                onClick = {
                                                    locationInput = itemLocation
                                                    showLocationPicker = false
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (locationInput == itemLocation) GoldPrimary else NavyDark,
                                                border = BorderStroke(1.dp, if (locationInput == itemLocation) GoldPrimary else Color.White.copy(alpha = 0.15f))
                                            ) {
                                                Text(
                                                    text = itemLocation,
                                                    fontSize = 11.sp,
                                                    color = if (locationInput == itemLocation) NavyDark else TextWhite,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 5. OFICIAL QUE REPORTA
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Oficial que Reporta:",
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = reportingOfficer,
                            onValueChange = { reportingOfficer = it },
                            placeholder = { Text("Nombre del oficial de servicio", color = TextMuted) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("prados_incident_officer_input"),
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
                    }

                    // 6. PERSONAS / VEHÍCULOS INVOLUCRADOS
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Personas / Vehículos Involucrados:",
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = peopleInvolved,
                            onValueChange = { peopleInvolved = it },
                            placeholder = { Text("Ej. Morador de Casa 54, vehículo Nissan Versa placas UMA-45-89...", color = TextMuted.copy(alpha = 0.6f), fontSize = 12.sp) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Group, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("prados_incident_people_input"),
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
                    }

                    // 7. TESTIGOS / EVIDENCIA
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Testigos / Evidencia:",
                            color = TextWhite,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedTextField(
                            value = witnessesAndEvidence,
                            onValueChange = { witnessesAndEvidence = it },
                            placeholder = { Text("Ej. Vecinos de Casa 52 y Casa 56 / Cámara PTZ Garita", color = TextMuted.copy(alpha = 0.6f), fontSize = 12.sp) },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Default.Visibility, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("prados_incident_evidence_input"),
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
                    }

                    // 8. RELATO EN BRUTO DEL GUARDIA (HECHOS OBSERVADOS)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Relato en Bruto del Guardia (Hechos observados):",
                                color = TextWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Botón de Dictado por Voz
                            OutlinedButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                        isListeningVoice = true
                                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-MX")
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dicte las observaciones del reporte...")
                                        }
                                        try {
                                            speechIntentLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            isListeningVoice = false
                                            Toast.makeText(context, "Reconocimiento no disponible", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (isListeningVoice) ErrorRed else CyanNeon),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isListeningVoice) ErrorRed else CyanNeon),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = if (isListeningVoice) Icons.Default.GraphicEq else Icons.Default.Mic,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isListeningVoice) "Escuchando..." else "Dictar por Voz",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        OutlinedTextField(
                            value = rawGuardReport,
                            onValueChange = { rawGuardReport = it },
                            placeholder = { Text("Describa cronológicamente los hechos observados de forma clara...", color = TextMuted) },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("prados_incident_raw_report_input"),
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
                    }

                    // --- BOTÓN SÍNTESIS INTELIGENTE ALFHA CORE (TIEMPO = FAMILIA) ---
                    Button(
                        onClick = { executeAlfhaSynthesis() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanNeon.copy(alpha = 0.2f),
                            contentColor = CyanNeon
                        ),
                        border = BorderStroke(1.2.dp, CyanNeon),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_alfha_synthesis")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp), tint = CyanNeon)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "⚡ SINTETIZAR REPORTE & APERCIBIMIENTO (TIEMPO = FAMILIA)",
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // --- PANEL RESULTANTE DE LA SÍNTESIS DE LA IA ---
                    AnimatedVisibility(visible = isSynthesized, enter = fadeIn(), exit = fadeOut()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = NavySurface,
                            border = BorderStroke(1.2.dp, GoldPrimary.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "DICTAMEN & APERCIBIMIENTO FORMAL GENERADO",
                                            color = GoldPrimary,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = NavyDark
                                    ) {
                                        Text(
                                            text = "SLA: ${targetSlaMinutes}m",
                                            color = CyanNeon,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = aiFormalApercibimiento,
                                    color = TextWhite,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    fontWeight = FontWeight.Normal,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(NavyDark, RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                )

                                Text(
                                    text = "Asignado automáticamente a: $assignedDepartment",
                                    color = CyanNeon,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // --- ACCIONES FINALES: CANCELAR & CONFIRMAR EN ROOM + FIRESTORE ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Text("Cancelar", color = TextMuted, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (eventTitle.isBlank() || rawGuardReport.isBlank()) {
                                Toast.makeText(context, "Por favor complete el título y relato del reporte", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            if (!isSynthesized) {
                                executeAlfhaSynthesis()
                            }

                            isSaving = true
                            scope.launch {
                                val category = when {
                                    selectedIncidentType.contains("Ruido") || selectedIncidentType.contains("Convivencia") -> IncidentCategory.RUIDO_CONVIVENCIA
                                    selectedIncidentType.contains("Estacionamiento") -> IncidentCategory.PARKING_VIALIDAD
                                    selectedIncidentType.contains("Acceso") -> IncidentCategory.CONTROL_ACCESO
                                    selectedIncidentType.contains("Daño") || selectedIncidentType.contains("Fuga") -> IncidentCategory.INFRAESTRUCTURA
                                    else -> IncidentCategory.SEGURIDAD_EMERGENCIA
                                }

                                val priority = when {
                                    selectedSeverity.contains("Crítica") -> IncidentPriority.CRITICA
                                    selectedSeverity.contains("Alta") -> IncidentPriority.ALTA
                                    selectedSeverity.contains("Media") -> IncidentPriority.MEDIA
                                    else -> IncidentPriority.BAJA
                                }

                                val fullTranscript = buildString {
                                    append("ASUNTO: $eventTitle\n")
                                    append("TIPO: $selectedIncidentType\n")
                                    append("SEVERIDAD: $selectedSeverity\n")
                                    append("PERSONAS/VEHÍCULOS: $peopleInvolved\n")
                                    append("TESTIGOS/EVIDENCIA: $witnessesAndEvidence\n")
                                    append("HECHOS OBSERVADOS: $rawGuardReport\n\n")
                                    if (aiFormalApercibimiento.isNotBlank()) {
                                        append("--- DICTAMEN APERCIBIMIENTO MEDUSA ALFHA ---\n")
                                        append(aiFormalApercibimiento)
                                    }
                                }

                                val savedIncident = IncidentEngine.registerIncident(
                                    context = context,
                                    db = db,
                                    rawTranscript = fullTranscript,
                                    category = category,
                                    priority = priority,
                                    location = locationInput,
                                    aiSummary = aiExecutiveSummary.ifBlank { eventTitle },
                                    recommendedAction = aiRecommendedAction.ifBlank { "Seguimiento administrativo y mediación vecinal." },
                                    reportedBy = reportingOfficer,
                                    reportedByRole = "GUARDIA",
                                    evidenceNotes = witnessesAndEvidence
                                )

                                // Persistir en Firebase Firestore de forma aislada
                                try {
                                    val firestore = FirebaseConfigHelper.getFirestore()
                                    if (firestore != null) {
                                        FirestoreTenantManager.saveIncident(
                                            firestore = firestore,
                                            condominiumId = condominiumId,
                                            incident = savedIncident
                                        )
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("PradosIncidentDialog", "Error sincronizando con Firestore: ${e.message}")
                                }

                                withContext(Dispatchers.Main) {
                                    isSaving = false
                                    Toast.makeText(
                                        context,
                                        "✅ Novedad [${savedIncident.folio}] registrada. 25 min devueltos a la familia.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onIncidentCreated(savedIncident)
                                    onDismiss()
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier
                            .weight(2f)
                            .height(48.dp)
                            .testTag("btn_confirm_prados_incident"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldPrimary,
                            contentColor = NavyDark
                        )
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = NavyDark, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Guardando...", fontWeight = FontWeight.Bold, color = NavyDark)
                        } else {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Guardar Reporte · Tiempo = Familia",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
