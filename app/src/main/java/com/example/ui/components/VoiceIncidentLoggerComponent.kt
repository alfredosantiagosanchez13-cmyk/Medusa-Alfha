package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.incident.IncidentCategory
import com.example.data.incident.IncidentEngine
import com.example.data.incident.IncidentEntity
import com.example.data.incident.IncidentPriority
import com.example.data.incident.VoiceIncident
import com.example.data.incident.VoiceIncidentCategorizer
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun VoiceIncidentLoggerComponent(
    modifier: Modifier = Modifier,
    onIncidentLogged: (VoiceIncident) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val incidentDao = remember { db.incidentDao() }

    val roomIncidents by incidentDao.getAllIncidentsFlow().collectAsState(initial = emptyList())

    var isListening by remember { mutableStateOf(false) }
    var currentTranscript by remember { mutableStateOf("") }
    var categorizedIncident by remember { mutableStateOf<VoiceIncident?>(null) }

    // Seed initial incident if empty
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            if (incidentDao.getIncidentCount() == 0) {
                incidentDao.insertIncident(
                    IncidentEntity(
                        folio = "MED-20260821-2001",
                        rawTranscript = "Vehículo patente AB123CD estacionado en portón principal bloqueando acceso",
                        category = IncidentCategory.PARKING_VIALIDAD,
                        priority = IncidentPriority.MEDIA,
                        location = "Portón Principal",
                        aiSummary = "Vehículo obstaculizando acceso de emergencia en portón",
                        recommendedAction = "Contactar al propietario de la patente AB123CD o llamar grúa",
                        guardName = "Agente #402",
                        status = "REGISTRADO"
                    )
                )
            }
        }
    }

    // Speech Recognizer Intent Launcher
    val speechIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull() ?: ""

            if (spokenText.isNotBlank()) {
                currentTranscript = spokenText
                categorizedIncident = VoiceIncidentCategorizer.analyzeAndCategorize(spokenText)
            }
        }
    }

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchSpeechRecognizer(context, speechIntentLauncher) { isListening = true }
        } else {
            Toast.makeText(context, "Permiso de micrófono requerido para registro por voz", Toast.LENGTH_SHORT).show()
        }
    }

    // Pulse animation during voice listening
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("voice_incident_logger_card"),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(GoldPrimary.copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, GoldPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "REGISTRO DE INCIDENCIAS POR VOZ (HANDS-FREE)",
                            color = GoldPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Transcripción y Persistencia Room SQLite",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    color = (if (isListening) ErrorRed else GoldPrimary).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isListening) ErrorRed else GoldPrimary)
                ) {
                    Text(
                        text = if (isListening) "Escuchando..." else "Listo para Grabación",
                        color = if (isListening) ErrorRed else GoldPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Microphone Recording Trigger Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NavyDark, RoundedCornerShape(12.dp))
                    .border(
                        1.dp,
                        if (isListening) ErrorRed else GoldPrimary.copy(alpha = 0.3f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                launchSpeechRecognizer(context, speechIntentLauncher) { isListening = true }
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        shape = CircleShape,
                        color = if (isListening) ErrorRed else GoldPrimary,
                        modifier = Modifier
                            .size(64.dp)
                            .scale(if (isListening) pulseScale else 1f)
                            .testTag("mic_record_btn")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.GraphicEq else Icons.Default.Mic,
                                contentDescription = "Grabar Voz",
                                tint = NavyDark,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Text(
                        text = if (isListening) "🎙️ Hable ahora... La IA está transcribiendo en tiempo real" else "Presione el micrófono para iniciar registro por voz",
                        color = if (isListening) ErrorRed else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    // Hands-Free Quick Sample Presets (For rapid testing in emulator environments)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "💡 O seleccione un dictado de voz de prueba (Demostración Hands-Free):",
                            color = TextMuted,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        val voicePresets = listOf(
                            "Vehículo patente AB123CD estacionado en portón principal bloqueando acceso de emergencia",
                            "Ruidos molestos y música a alto volumen en departamento 304 Torre B a la 01:15 AM",
                            "Muelle de ascensor subterráneo 2 fuera de servicio con personas atrapadas aviso urgente"
                        )

                        voicePresets.forEach { preset ->
                            Surface(
                                onClick = {
                                    currentTranscript = preset
                                    categorizedIncident = VoiceIncidentCategorizer.analyzeAndCategorize(preset)
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = NavyCard,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF374151)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = "🗣️ \"$preset\"",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Transcribed Result and AI Categorization Display Card
            categorizedIncident?.let { incident ->
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanNeon)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = incident.category.iconName,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = incident.category.displayName,
                                    color = CyanNeon,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Surface(
                                color = Color(incident.priority.badgeColorHex).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    Color(incident.priority.badgeColorHex)
                                )
                            ) {
                                Text(
                                    text = "PRIORIDAD ${incident.priority.displayName}",
                                    color = Color(incident.priority.badgeColorHex),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "📝 Transcripción de Voz:",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "\"${incident.rawTranscript}\"",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "📍 Ubicación Extraída:",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = incident.location,
                                    color = GoldPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "👮 Operador Garita:",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = incident.guardName,
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            color = NavyDark,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "🤖 Acción Recomendada por IA:",
                                    color = CyanNeon,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = incident.recommendedAction,
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val registered = IncidentEngine.registerIncident(
                                            context = context,
                                            db = db,
                                            rawTranscript = incident.rawTranscript,
                                            category = incident.category,
                                            priority = incident.priority,
                                            location = incident.location,
                                            aiSummary = incident.aiSummary,
                                            recommendedAction = incident.recommendedAction,
                                            reportedBy = incident.guardName,
                                            reportedByRole = "GUARDIA"
                                        )
                                        onIncidentLogged(incident)
                                        Toast.makeText(context, "✅ Incidencia ${registered.folio} guardada y asignada a ${registered.assignedTo}", Toast.LENGTH_SHORT).show()
                                        categorizedIncident = null
                                        currentTranscript = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = NavyDark),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("save_voice_incident_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Guardar en Room DB", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    categorizedIncident = null
                                    currentTranscript = ""
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Descartar", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Room SQLite Stored Incidents Section
            if (roomIncidents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "INCIDENCIAS REGISTRADAS EN ROOM SQLITE (${roomIncidents.size})",
                    color = GoldPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                roomIncidents.take(3).forEach { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        colors = CardDefaults.cardColors(containerColor = NavyCard),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color(item.priority.badgeColorHex).copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.category.iconName,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${item.category.displayName} • ${item.folio}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "📍 ${item.location} • ${item.formattedDate}",
                                    color = TextMuted,
                                    fontSize = 9.sp
                                )
                            }

                            Surface(
                                color = Color(item.priority.badgeColorHex).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = item.priority.displayName,
                                    color = Color(item.priority.badgeColorHex),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun launchSpeechRecognizer(
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
    onStarted: () -> Unit
) {
    try {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-CL")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Reporte su novedad o incidencia de garita por voz...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        launcher.launch(intent)
        onStarted()
    } catch (e: Exception) {
        Toast.makeText(context, "Reconocimiento por voz no disponible en este dispositivo", Toast.LENGTH_SHORT).show()
    }
}
