package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.supervision.SupervisionAuditEntity
import com.example.data.supervision.SupervisionCheckpoint
import com.example.data.supervision.SupervisionRoute
import com.example.data.supervision.SupervisionRoutesCatalog
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarningOrange

/**
 * FASE 17: MAPA TÁCTICO DE RONDINES Y PUNTOS DE CONTROL
 *
 * Muestra el trazado de la ruta en un lienzo interactivo, la secuencia de checkpoints,
 * los estados en tiempo real (Óptimo, Regular, Crítico, Omitido, Pendiente),
 * validación satelital y detalles operativos al interactuar con cada punto.
 */
@Composable
fun SupervisionTourMapView(
    route: SupervisionRoute = SupervisionRoutesCatalog.ROUTE_PERIMETER,
    tourFolio: String = "RON-ACTIVO",
    recordedAudits: List<SupervisionAuditEntity> = emptyList(),
    selectedCheckpointId: String? = null,
    onCheckpointSelected: (SupervisionCheckpoint) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var activeFilter by remember { mutableStateOf("TODOS") }
    var inspectCheckpoint by remember { mutableStateOf<SupervisionCheckpoint?>(null) }

    // Pulso animado para hallazgos críticos y posición del supervisor
    val infiniteTransition = rememberInfiniteTransition(label = "map_pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_radius"
    )

    // Mapa de auditorías por nombre de checkpoint
    val auditMap = remember(recordedAudits) {
        recordedAudits.associateBy { it.checkpointName }
    }

    val completedCount = route.checkpoints.count { cp ->
        val audit = auditMap[cp.name]
        audit != null && audit.statusCondition != "PENDIENTE"
    }
    val criticalCount = route.checkpoints.count { cp ->
        auditMap[cp.name]?.statusCondition == "CRITICO"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("supervision_tour_map_view"),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Info de Ruta y Progreso
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Route,
                        contentDescription = null,
                        tint = GoldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = route.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Folio: $tourFolio • ${route.checkpoints.size} Puntos de Control",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                }

                Surface(
                    color = if (criticalCount > 0) ErrorRed.copy(alpha = 0.2f) else SuccessGreen.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (criticalCount > 0) ErrorRed else SuccessGreen
                    )
                ) {
                    Text(
                        text = "$completedCount/${route.checkpoints.size} (${(completedCount * 100) / route.checkpoints.size}%)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = if (criticalCount > 0) ErrorRed else SuccessGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("TODOS", "CRÍTICOS", "PENDIENTES", "ÓPTIMOS").forEach { filter ->
                    val isSelected = activeFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { activeFilter = filter },
                        label = { Text(filter, fontSize = 9.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = GoldPrimary,
                            containerColor = NavySurface,
                            labelColor = TextMuted
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = TextMuted.copy(alpha = 0.3f),
                            selectedBorderColor = GoldPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Canvas Táctico de Navegación y Coordenadas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .background(NavyDark, RoundedCornerShape(12.dp))
                    .border(0.5.dp, GoldPrimary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            ) {
                // Cálculo de límites geográficos locales para proyección de pantalla
                val minLat = route.checkpoints.minOfOrNull { it.targetLat } ?: -33.4385
                val maxLat = route.checkpoints.maxOfOrNull { it.targetLat } ?: -33.4360
                val minLng = route.checkpoints.minOfOrNull { it.targetLng } ?: -33.6520
                val maxLng = route.checkpoints.maxOfOrNull { it.targetLng } ?: -33.6490

                val latSpan = (maxLat - minLat).coerceAtLeast(0.0001)
                val lngSpan = (maxLng - minLng).coerceAtLeast(0.0001)

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(route.checkpoints, auditMap) {
                            detectTapGestures { offset ->
                                val w = size.width
                                val h = size.height
                                val padding = 40f

                                // Encontrar checkpoint más cercano al tap
                                val clicked = route.checkpoints.minByOrNull { cp ->
                                    val nx = ((cp.targetLng - minLng) / lngSpan).toFloat()
                                    val ny = (1f - ((cp.targetLat - minLat) / latSpan).toFloat())
                                    val px = padding + nx * (w - 2 * padding)
                                    val py = padding + ny * (h - 2 * padding)
                                    val dx = offset.x - px
                                    val dy = offset.y - py
                                    dx * dx + dy * dy
                                }

                                if (clicked != null) {
                                    inspectCheckpoint = clicked
                                    onCheckpointSelected(clicked)
                                }
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val padding = 40f

                    // 1. Dibujar Cuadrícula Táctica de Fondo
                    val gridCols = 8
                    val gridRows = 6
                    val colWidth = w / gridCols
                    val rowHeight = h / gridRows

                    for (i in 0..gridCols) {
                        drawLine(
                            color = Color(0xFF1E293B).copy(alpha = 0.4f),
                            start = Offset(i * colWidth, 0f),
                            end = Offset(i * colWidth, h),
                            strokeWidth = 1f
                        )
                    }
                    for (j in 0..gridRows) {
                        drawLine(
                            color = Color(0xFF1E293B).copy(alpha = 0.4f),
                            start = Offset(0f, j * rowHeight),
                            end = Offset(w, j * rowHeight),
                            strokeWidth = 1f
                        )
                    }

                    // 2. Proyección de Puntos de la Ruta
                    val pointPositions = route.checkpoints.map { cp ->
                        val nx = ((cp.targetLng - minLng) / lngSpan).toFloat()
                        val ny = (1f - ((cp.targetLat - minLat) / latSpan).toFloat())
                        val px = padding + nx * (w - 2 * padding)
                        val py = padding + ny * (h - 2 * padding)
                        Pair(cp, Offset(px, py))
                    }

                    // 3. Dibujar Trazado de Ruta (Polyline conector)
                    val routePath = Path()
                    if (pointPositions.isNotEmpty()) {
                        routePath.moveTo(pointPositions.first().second.x, pointPositions.first().second.y)
                        for (k in 1 until pointPositions.size) {
                            routePath.lineTo(pointPositions[k].second.x, pointPositions[k].second.y)
                        }
                        // Cerrar circuito con el primer punto si es perimetral
                        routePath.lineTo(pointPositions.first().second.x, pointPositions.first().second.y)

                        drawPath(
                            path = routePath,
                            color = CyanNeon.copy(alpha = 0.45f),
                            style = Stroke(
                                width = 3f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                            )
                        )
                    }

                    // 4. Dibujar Nodos de Checkpoints
                    pointPositions.forEachIndexed { index, (cp, offset) ->
                        val audit = auditMap[cp.name]
                        val condition = audit?.statusCondition ?: "PENDIENTE"

                        val matchesFilter = when (activeFilter) {
                            "CRÍTICOS" -> condition == "CRITICO"
                            "PENDIENTES" -> condition == "PENDIENTE"
                            "ÓPTIMOS" -> condition == "OPTIMO"
                            else -> true
                        }

                        if (!matchesFilter) return@forEachIndexed

                        val nodeColor = when (condition) {
                            "OPTIMO" -> SuccessGreen
                            "REGULAR" -> WarningOrange
                            "CRITICO" -> ErrorRed
                            "OMITIDO" -> Color(0xFFB388FF)
                            "FUERA_UBICACION" -> WarningOrange
                            else -> TextMuted
                        }

                        // Pulso si es crítico
                        if (condition == "CRITICO") {
                            drawCircle(
                                color = ErrorRed.copy(alpha = 0.3f),
                                radius = pulseRadius + 6f,
                                center = offset
                            )
                        }

                        // Anillo exterior
                        drawCircle(
                            color = nodeColor.copy(alpha = 0.25f),
                            radius = 16f,
                            center = offset
                        )

                        // Nodo central
                        drawCircle(
                            color = nodeColor,
                            radius = 8f,
                            center = offset
                        )

                        // Punto de control actual seleccionado
                        if (cp.id == selectedCheckpointId || inspectCheckpoint?.id == cp.id) {
                            drawCircle(
                                color = GoldPrimary,
                                radius = 22f,
                                center = offset,
                                style = Stroke(width = 2.5f)
                            )
                        }
                    }
                }

                // Leyenda Flotante en Esquina
                Surface(
                    color = NavyDark.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, TextMuted.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(modifier = Modifier.size(6.dp)) { drawCircle(SuccessGreen) }
                        Text("Óptimo", fontSize = 8.sp, color = TextMuted)
                        Canvas(modifier = Modifier.size(6.dp)) { drawCircle(WarningOrange) }
                        Text("Regular", fontSize = 8.sp, color = TextMuted)
                        Canvas(modifier = Modifier.size(6.dp)) { drawCircle(ErrorRed) }
                        Text("Crítico", fontSize = 8.sp, color = TextMuted)
                        Canvas(modifier = Modifier.size(6.dp)) { drawCircle(TextMuted) }
                        Text("Pend.", fontSize = 8.sp, color = TextMuted)
                    }
                }
            }

            // Tarjeta de Detalle del Checkpoint Inspeccionado al Tocar
            inspectCheckpoint?.let { cp ->
                Spacer(modifier = Modifier.height(10.dp))
                val audit = auditMap[cp.name]
                val cond = audit?.statusCondition ?: "PENDIENTE"

                Surface(
                    color = NavySurface,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when (cond) {
                            "CRITICO" -> ErrorRed
                            "REGULAR" -> WarningOrange
                            "OPTIMO" -> SuccessGreen
                            else -> GoldPrimary.copy(alpha = 0.4f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = GoldPrimary,
                                    shape = CircleShape,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("${cp.sequence}", fontSize = 10.sp, fontWeight = FontWeight.Black, color = NavyDark)
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(cp.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(cp.area, fontSize = 10.sp, color = TextMuted)
                                }
                            }
                            IconButton(onClick = { inspectCheckpoint = null }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar detalle", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.MyLocation, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Objetivo: %.5f, %.5f".format(cp.targetLat, cp.targetLng), fontSize = 9.sp, color = CyanNeon, fontFamily = FontFamily.Monospace)
                            }
                            Text(
                                text = "Estado: $cond",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (cond) {
                                    "CRITICO" -> ErrorRed
                                    "REGULAR" -> WarningOrange
                                    "OPTIMO" -> SuccessGreen
                                    else -> TextMuted
                                }
                            )
                        }

                        if (audit != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Hallazgo: ${audit.findingsDescription}", fontSize = 10.sp, color = Color.White)
                            if (!audit.correctiveActionRequired.contains("Sin observaciones")) {
                                Text("Acción: ${audit.correctiveActionRequired} (${audit.responsibleParty})", fontSize = 9.sp, color = WarningOrange)
                            }
                            if (audit.photoEvidencePath != null) {
                                Row(modifier = Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(11.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Evidencia fotográfica adjunta [${audit.photoEvidencePath}]", fontSize = 9.sp, color = CyanNeon)
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Criterios clave: ${cp.checklistCriteria.joinToString(" • ")}", fontSize = 9.sp, color = TextMuted)
                        }
                    }
                }
            }
        }
    }
}
