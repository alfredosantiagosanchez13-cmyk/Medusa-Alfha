package com.example.medusaalfha.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.medusaalfha.data.model.PatrolCheckpoint
import com.example.medusaalfha.data.model.PatrolLogEntry
import com.example.medusaalfha.ui.theme.*
import com.example.medusaalfha.ui.viewmodel.GuardStationUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * BLOQUE 4: Control de Recorridos, Validación de Checkpoints en Tiempo Real
 * y Asistencia de Voz Inteligente (Speech-to-Text Integrado).
 *
 * Estética MEDUSA ALFHA:
 * - Tarjeta con RoundedCornerShape(16.dp), fondo gris grafito translúcido y borde azul neón (#00A3FF).
 * - Textos de jerarquía y títulos en tono dorado premium (#FFD700).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatrolLogVoiceAssistantSection(
    uiState: GuardStationUiState,
    onReportTextChanged: (String) -> Unit,
    onTriggerVoice: (String?) -> Unit,
    onValidateCheckpoint: (String) -> Unit,
    onSelectCheckpoint: (PatrolCheckpoint) -> Unit,
    onResetCircuit: () -> Unit,
    onSaveReport: () -> Unit,
    onTriggerSync: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Launcher nativo de reconocimiento de voz de Android (Speech-to-Text)
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenTextList = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognizedSpeech = spokenTextList?.firstOrNull()
            if (!recognizedSpeech.isNullOrBlank()) {
                onTriggerVoice(recognizedSpeech)
            } else {
                onTriggerVoice(null)
            }
        } else {
            // Cancelado o sin audio; ejecutamos el asistente con fallback inteligente
            onTriggerVoice(null)
        }
    }

    fun launchNativeSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "MEDUSA ALFHA: Diga 'Redactar el informe'...")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "Servicio de voz nativo no detectado. Activando síntesis con IA integrada...",
                Toast.LENGTH_SHORT
            ).show()
            // Fallback directo al motor de IA en el ViewModel
            onTriggerVoice(null)
        }
    }

    // Contenedor General Bloque 4: Tarjeta Cyber-Security Premium
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = CyberSurface.copy(alpha = 0.92f)
        ),
        border = BorderStroke(1.dp, NeonBlue),
        modifier = modifier
            .fillMaxWidth()
            .testTag("bloque_4_patrol_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Encabezado del Módulo (Títulos y Jerarquía en Oro Premium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "BLOQUE 4: CONTROL DE RECORRIDOS & ASISTENCIA DE VOZ",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        ),
                        color = GoldMetallic
                    )
                    Text(
                        text = "Mapeo georreferenciado en tiempo real · Tolerancia GPS · Dictado militarizado",
                        fontSize = 11.sp,
                        color = CyberTextMuted
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyberSurfaceElevated,
                    border = BorderStroke(1.dp, NeonBlue.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(StatusNeonGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "GPS RTK FIX",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = StatusNeonGreen
                        )
                    }
                }
            }

            CircuitDividerLine()

            // Capa de Sincronización Local Room SQLite <-> Cloud Firestore
            PatrolSyncStatusBanner(
                syncState = uiState.syncState,
                onTriggerSync = onTriggerSync
            )

            CircuitDividerLine()

            // -------------------------------------------------------------
            // 1. MAPA DIDÁCTICO GEORREFERENCIADO (MAPEO DE CHECKPOINTS)
            // -------------------------------------------------------------
            PatrolMapInteractiveModule(
                checkpoints = uiState.checkpoints,
                selectedCheckpoint = uiState.selectedCheckpoint,
                gpsAccuracyMeters = uiState.currentGpsAccuracyMeters,
                onSelectCheckpoint = onSelectCheckpoint,
                onValidateCheckpoint = onValidateCheckpoint,
                onResetCircuit = onResetCircuit
            )

            CircuitDividerLine()

            // -------------------------------------------------------------
            // 2. CAMPO DE INFORME Y REPORTE DE NOVEDADES
            // & 3. BOTÓN ASISTENTE DE IA POR VOZ (SPEECH-TO-TEXT)
            // -------------------------------------------------------------
            PatrolReportWithVoiceAssistantField(
                reportText = uiState.patrolReportText,
                isVoiceListening = uiState.isVoiceListening,
                voiceFeedbackMessage = uiState.voiceFeedbackMessage,
                onReportTextChanged = onReportTextChanged,
                onLaunchVoiceRecognition = { launchNativeSpeechRecognition() },
                onSaveReport = onSaveReport
            )

            // Historial de Rondines Anteriores
            if (uiState.patrolLogs.isNotEmpty()) {
                CircuitDividerLine()
                PatrolLogsHistoryView(logs = uiState.patrolLogs)
            }
        }
    }
}

// -------------------------------------------------------------
// Componente 1: Mapa Didáctico Georreferenciado
// -------------------------------------------------------------
@Composable
private fun PatrolMapInteractiveModule(
    checkpoints: List<PatrolCheckpoint>,
    selectedCheckpoint: PatrolCheckpoint?,
    gpsAccuracyMeters: Double,
    onSelectCheckpoint: (PatrolCheckpoint) -> Unit,
    onValidateCheckpoint: (String) -> Unit,
    onResetCircuit: () -> Unit
) {
    val validatedCount = checkpoints.count { it.isValidated }
    val totalCount = checkpoints.size
    val progressPercent = if (totalCount > 0) (validatedCount.toFloat() / totalCount) else 0f

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Barra Superior de Estado del Circuito
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = null,
                    tint = GoldMetallic,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Circuito de Vigilancia: $validatedCount/$totalCount Checkpoints Cubiertos",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = GoldMetallic
                )
            }

            TextButton(
                onClick = onResetCircuit,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(13.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reiniciar Rondín", fontSize = 11.sp, color = NeonBlue)
            }
        }

        // Barra de Progreso con Gradiente Neón
        LinearProgressIndicator(
            progress = { progressPercent },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (progressPercent >= 1.0f) StatusNeonGreen else NeonBlue,
            trackColor = CyberSurfaceElevated
        )

        // Contenedor Gráfico del Mapa Táctico (Canvas + Marcadores Interactivos)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF04060C))
                .border(BorderStroke(1.dp, CyberBorderCircuit), RoundedCornerShape(12.dp))
                .testTag("patrol_interactive_map")
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()

            // Pulso infinito para los halos de los marcadores activos
            val infiniteTransition = rememberInfiniteTransition(label = "map_radar_pulse")
            val pulseRadiusFactor by infiniteTransition.animateFloat(
                initialValue = 0.8f,
                targetValue = 1.35f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "pulse_radius"
            )

            // 1. Dibujo de Rejilla Táctica, Perímetro y Líneas de Circuito entre Checkpoints
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Rejilla de coordenadas de seguridad
                val stepX = size.width / 8f
                val stepY = size.height / 6f
                for (i in 1..7) {
                    drawLine(
                        color = NeonBlue.copy(alpha = 0.08f),
                        start = Offset(i * stepX, 0f),
                        end = Offset(i * stepX, size.height),
                        strokeWidth = 1f
                    )
                }
                for (j in 1..5) {
                    drawLine(
                        color = NeonBlue.copy(alpha = 0.08f),
                        start = Offset(0f, j * stepY),
                        end = Offset(size.width, j * stepY),
                        strokeWidth = 1f
                    )
                }

                // Polígonos de zonas residenciales (Plano Táctico Didáctico)
                // Garita / Acceso
                drawRect(
                    color = NeonBlue.copy(alpha = 0.12f),
                    topLeft = Offset(size.width * 0.10f, size.height * 0.14f),
                    size = Size(size.width * 0.18f, size.height * 0.22f)
                )
                // Casa Club & Alberca
                drawRect(
                    color = GoldMetallic.copy(alpha = 0.10f),
                    topLeft = Offset(size.width * 0.38f, size.height * 0.32f),
                    size = Size(size.width * 0.22f, size.height * 0.25f)
                )
                // Canchas Pádel / Perímetro Este
                drawRect(
                    color = NeonBlue.copy(alpha = 0.10f),
                    topLeft = Offset(size.width * 0.68f, size.height * 0.20f),
                    size = Size(size.width * 0.22f, size.height * 0.30f)
                )
                // Subestación Sur
                drawRect(
                    color = Color(0xFF6B21A8).copy(alpha = 0.20f),
                    topLeft = Offset(size.width * 0.45f, size.height * 0.68f),
                    size = Size(size.width * 0.24f, size.height * 0.22f)
                )

                // Línea de Circuito de Patrullaje conectando secuencialmente los checkpoints
                val pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                for (i in 0 until checkpoints.size - 1) {
                    val p1 = checkpoints[i]
                    val p2 = checkpoints[i + 1]
                    val isSegmentDone = p1.isValidated && p2.isValidated

                    drawLine(
                        color = if (isSegmentDone) StatusNeonGreen.copy(alpha = 0.85f) else NeonBlue.copy(alpha = 0.40f),
                        start = Offset(p1.mapXPercent * size.width, p1.mapYPercent * size.height),
                        end = Offset(p2.mapXPercent * size.width, p2.mapYPercent * size.height),
                        strokeWidth = if (isSegmentDone) 3f else 2f,
                        pathEffect = pathEffect
                    )
                }

                // Cierre de circuito de vuelta al punto inicial (CP-05 -> CP-01)
                if (checkpoints.size >= 2) {
                    val pLast = checkpoints.last()
                    val pFirst = checkpoints.first()
                    val isFullLoop = pLast.isValidated && pFirst.isValidated
                    drawLine(
                        color = if (isFullLoop) StatusNeonGreen.copy(alpha = 0.85f) else NeonBlue.copy(alpha = 0.40f),
                        start = Offset(pLast.mapXPercent * size.width, pLast.mapYPercent * size.height),
                        end = Offset(pFirst.mapXPercent * size.width, pFirst.mapYPercent * size.height),
                        strokeWidth = if (isFullLoop) 3f else 2f,
                        pathEffect = pathEffect
                    )
                }

                // Halo de posición satelital del elemento de seguridad
                drawCircle(
                    color = NeonBlue.copy(alpha = 0.15f * (1.5f - pulseRadiusFactor)),
                    radius = 32f * pulseRadiusFactor,
                    center = Offset(size.width * 0.35f, size.height * 0.48f)
                )
                drawCircle(
                    color = NeonBlue,
                    radius = 5f,
                    center = Offset(size.width * 0.35f, size.height * 0.48f)
                )
            }

            // 2. Marcadores Visuales de Checkpoints con Animación de Transición de Color
            // Rojo de advertencia (#FF3333) -> Verde neón (#00FF66) con animaciones suaves
            checkpoints.forEach { cp ->
                val markerX = (cp.mapXPercent * widthPx) / LocalContext.current.resources.displayMetrics.density
                val markerY = (cp.mapYPercent * heightPx) / LocalContext.current.resources.displayMetrics.density

                val animatedMarkerColor by animateColorAsState(
                    targetValue = if (cp.isValidated) StatusNeonGreen else AlertRedFatigue,
                    animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                    label = "marker_color_${cp.id}"
                )

                val isSelected = selectedCheckpoint?.id == cp.id

                Box(
                    modifier = Modifier
                        .offset(x = markerX.dp - 20.dp, y = markerY.dp - 20.dp)
                        .size(40.dp)
                        .clickable { onSelectCheckpoint(cp) },
                    contentAlignment = Alignment.Center
                ) {
                    // Anillo exterior de validación con animación
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 36.dp else 28.dp)
                            .clip(CircleShape)
                            .background(animatedMarkerColor.copy(alpha = 0.20f))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = animatedMarkerColor,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (cp.isValidated) Icons.Default.CheckCircle else Icons.Default.GpsFixed,
                            contentDescription = cp.name,
                            tint = animatedMarkerColor,
                            modifier = Modifier.size(if (isSelected) 18.dp else 14.dp)
                        )
                    }

                    // Etiqueta del Código del Checkpoint
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .offset(y = 12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.85f))
                            .border(0.5.dp, animatedMarkerColor.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = cp.code,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = animatedMarkerColor
                        )
                    }
                }
            }

            // Indicador de leyenda de mapa superpuesto
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .border(0.5.dp, CyberBorderCircuit, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(AlertRedFatigue))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pendiente", fontSize = 9.sp, color = CyberTextMuted)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(StatusNeonGreen))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Validado GPS", fontSize = 9.sp, color = StatusNeonGreen)
                }
            }
        }

        // Tarjeta de Detalle y Validación Espacial del Checkpoint Seleccionado
        val activeTarget = selectedCheckpoint ?: checkpoints.firstOrNull { !it.isValidated } ?: checkpoints.firstOrNull()

        if (activeTarget != null) {
            CheckpointActionCard(
                checkpoint = activeTarget,
                gpsAccuracyMeters = gpsAccuracyMeters,
                onValidate = { onValidateCheckpoint(activeTarget.id) }
            )
        }
    }
}

@Composable
private fun CheckpointActionCard(
    checkpoint: PatrolCheckpoint,
    gpsAccuracyMeters: Double,
    onValidate: () -> Unit
) {
    val animatedStatusColor by animateColorAsState(
        targetValue = if (checkpoint.isValidated) StatusNeonGreen else AlertRedFatigue,
        animationSpec = tween(500),
        label = "action_card_color"
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CyberSurfaceElevated,
        border = BorderStroke(1.dp, animatedStatusColor.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(animatedStatusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${checkpoint.code}: ${checkpoint.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = GoldMetallic
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val syncBadgeColor = if (checkpoint.isSynced) StatusNeonGreen else StatusAmber
                    val syncBadgeText = if (checkpoint.isSynced) "FIRESTORE" else "ROOM LOCAL"
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = syncBadgeColor.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, syncBadgeColor)
                    ) {
                        Text(
                            text = syncBadgeText,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = syncBadgeColor,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (checkpoint.isValidated) "VALIDADO" else "NO CUBIERTO",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = animatedStatusColor
                    )
                }
            }

            Text(
                text = checkpoint.description,
                fontSize = 11.sp,
                color = CyberTextPrimary
            )

            // Fila de Telemetría GPS Estricta
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("COORDENADAS GPS:", fontSize = 9.sp, color = CyberTextMuted)
                    Text(
                        text = "LAT ${checkpoint.latitude} | LON ${checkpoint.longitude}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = NeonBlue
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("TOLERANCIA / PRECISIÓN:", fontSize = 9.sp, color = CyberTextMuted)
                    Text(
                        text = "±${checkpoint.toleranceMeters}m (Fix: ±${gpsAccuracyMeters}m)",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = GoldMetallic
                    )
                }
            }

            // Botón de Validación Espacial / Simulación en Rango
            Button(
                onClick = onValidate,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (checkpoint.isValidated) CyberSurface else NeonBlue,
                    contentColor = if (checkpoint.isValidated) StatusNeonGreen else Color.Black
                ),
                border = BorderStroke(1.dp, if (checkpoint.isValidated) StatusNeonGreen else NeonBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .testTag("validate_checkpoint_${checkpoint.id}")
            ) {
                Icon(
                    imageVector = if (checkpoint.isValidated) Icons.Default.Check else Icons.Default.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (checkpoint.isValidated) {
                        val timeStr = checkpoint.validatedAtMillis?.let {
                            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it))
                        } ?: "--:--"
                        "Punto Verificado por GPS ($timeStr) · Revalidar"
                    } else {
                        "Validar Posición Espacial en Rango GPS"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Componente 2 & 3: Campo de Informe con Scroll Vertical
// y Botón Asistente de Voz Inteligente (Speech-to-Text)
// -------------------------------------------------------------
@Composable
private fun PatrolReportWithVoiceAssistantField(
    reportText: String,
    isVoiceListening: Boolean,
    voiceFeedbackMessage: String?,
    onReportTextChanged: (String) -> Unit,
    onLaunchVoiceRecognition: () -> Unit,
    onSaveReport: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Título del Campo de Novedades
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "INFORME TÁCTICO & DICTADO DE NOVEDADES",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = GoldMetallic
            )
            Text(
                text = "Scroll independiente habilitado",
                fontSize = 10.sp,
                color = CyberTextMuted
            )
        }

        // Contenedor del Campo con scroll vertical independiente
        // Enmarcado con borde de enfoque color azul neón y tipografía en color oro
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
        ) {
            OutlinedTextField(
                value = reportText,
                onValueChange = onReportTextChanged,
                label = { Text("Reporte Escrito de Supervisión", color = GoldMetallic) },
                placeholder = {
                    Text(
                        text = "Presiona el botón de micrófono dorado para dictar: 'Redactar el informe'...\nEl asistente procesará sintácticamente las novedades y cargará los puntos GPS validados.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                },
                textStyle = TextStyle(
                    color = GoldMetallic,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonBlue,
                    unfocusedBorderColor = CyberBorderCircuit,
                    focusedTextColor = GoldMetallic,
                    unfocusedTextColor = GoldMetallic,
                    focusedContainerColor = CyberSurfaceElevated,
                    unfocusedContainerColor = CyberSurfaceElevated,
                    cursorColor = NeonBlue
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .testTag("patrol_report_field")
            )

            // ---------------------------------------------------------
            // 3. BOTÓN ASISTENTE DE IA POR VOZ (Speech-to-Text Integrado)
            // FloatingActionButton circular estilizado con gradiente metálico dorado
            // y captura táctil (OnClick o OnLongPress)
            // ---------------------------------------------------------
            val goldMetallicGradient = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFEE88),
                    GoldMetallic,
                    GoldAccent,
                    Color(0xFF996515)
                )
            )

            // Animación de pulso cuando está escuchando la voz
            val infinitePulse = rememberInfiniteTransition(label = "mic_pulse")
            val micPulseScale by infinitePulse.animateFloat(
                initialValue = 1.0f,
                targetValue = if (isVoiceListening) 1.25f else 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "mic_scale"
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 10.dp)
                    .size(52.dp)
                    .testTag("voice_assistant_mic_fab")
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onLaunchVoiceRecognition() },
                            onLongPress = { onLaunchVoiceRecognition() }
                        )
                    },
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(2.dp, if (isVoiceListening) NeonBlue else Color.White.copy(alpha = 0.85f)),
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(goldMetallicGradient),
                    contentAlignment = Alignment.Center
                ) {
                    if (isVoiceListening) {
                        CircularProgressIndicator(
                            color = Color.Black,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(26.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Asistente de IA por Voz",
                            tint = Color.Black,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }

        // Banner informativo de procesamiento de IA y comandos de voz detectados
        AnimatedVisibility(visible = voiceFeedbackMessage != null) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = CyberSurfaceElevated,
                border = BorderStroke(1.dp, GoldMetallic.copy(alpha = 0.8f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = GoldMetallic,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = voiceFeedbackMessage ?: "",
                        fontSize = 11.sp,
                        color = GoldMetallic,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Botón para Archivar y Registrar el Reporte Oficial en la Bitácora
        Button(
            onClick = onSaveReport,
            enabled = reportText.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CyberSurfaceElevated,
                contentColor = NeonBlue
            ),
            border = BorderStroke(1.dp, if (reportText.isNotBlank()) NeonBlue else CyberBorderCircuit),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("save_patrol_log_button")
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Archivar Informe Oficial de Rondín",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

// -------------------------------------------------------------
// Componente de Historial de Rondines Guardados
// -------------------------------------------------------------
@Composable
private fun PatrolLogsHistoryView(logs: List<PatrolLogEntry>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Historial de Rondines Recientes:",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = GoldMetallic
        )

        logs.take(3).forEach { log ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CyberSurfaceElevated,
                border = BorderStroke(0.5.dp, CyberBorderCircuit),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (log.isAiAssisted) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = GoldMetallic,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = log.zone,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeonBlue
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val syncColor = if (log.isSynced) StatusNeonGreen else StatusAmber
                            val syncLabel = if (log.isSynced) "FIRESTORE" else "ROOM"
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = syncColor.copy(alpha = 0.15f),
                                border = BorderStroke(0.5.dp, syncColor)
                            ) {
                                Text(
                                    text = syncLabel,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = syncColor,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = SimpleDateFormat("HH:mm · dd/MM", Locale.getDefault()).format(Date(log.timestampMillis)),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = CyberTextMuted
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = log.reportText,
                        fontSize = 11.sp,
                        color = CyberTextPrimary,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CircuitDividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        NeonBlue.copy(alpha = 0.5f),
                        NeonBlue,
                        NeonBlue.copy(alpha = 0.5f),
                        Color.Transparent
                    )
                )
            )
    )
}

/**
 * Banner táctico de estado de sincronización local Room <-> Cloud Firestore.
 * Muestra si los datos están sincronizados en Firestore o almacenados en Room SQLite offline.
 */
@Composable
private fun PatrolSyncStatusBanner(
    syncState: com.example.medusaalfha.data.repository.PatrolSyncState,
    onTriggerSync: () -> Unit
) {
    val isOnline = syncState.isOnline
    val isSyncing = syncState.isSyncing
    val pendingCount = syncState.totalPendingCount

    val statusColor = when {
        isSyncing -> NeonBlue
        isOnline && pendingCount == 0 -> StatusNeonGreen
        isOnline -> StatusAmber
        else -> StatusOrangeAlert
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = CyberSurfaceElevated,
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("patrol_sync_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = NeonBlue
                    )
                } else {
                    Icon(
                        imageVector = if (isOnline) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isOnline) "EN LÍNEA · FIRESTORE CONECTADO" else "MODO FUERA DE LÍNEA · ROOM SQLite",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = statusColor
                        )
                        if (pendingCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = StatusAmber.copy(alpha = 0.2f),
                                border = BorderStroke(0.5.dp, StatusAmber)
                            ) {
                                Text(
                                    text = "$pendingCount pendientes",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusAmber,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = syncState.statusMessage,
                        fontSize = 10.sp,
                        color = CyberTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isOnline && pendingCount > 0 && !isSyncing) {
                OutlinedButton(
                    onClick = onTriggerSync,
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, NeonBlue),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sincronizar", fontSize = 10.sp, color = NeonBlue)
                }
            }
        }
    }
}
