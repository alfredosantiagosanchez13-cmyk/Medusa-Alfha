package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PieChart
import com.example.ui.components.AmenityBookingSection
import com.example.ui.components.PdfReportDialog
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarningOrange

data class DailyTrafficPoint(val dayLabel: String, val count: Int)
data class HourlyTrafficPoint(val hourLabel: String, val count: Int)
data class AmenityStat(val name: String, val visits: Int, val percentage: Float, val color: Color, val icon: ImageVector)

@Composable
fun AnalyticsSummaryScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTimeframe by remember { mutableStateOf("30 Días") }
    var showPdfReportDialog by remember { mutableStateOf(false) }

    if (showPdfReportDialog) {
        PdfReportDialog(
            timeframe = selectedTimeframe,
            onDismiss = { showPdfReportDialog = false }
        )
    }

    // Mock 30-day traffic data
    val trafficData = remember {
        listOf(
            DailyTrafficPoint("D1", 28),
            DailyTrafficPoint("D4", 42),
            DailyTrafficPoint("D7", 65),
            DailyTrafficPoint("D10", 38),
            DailyTrafficPoint("D13", 55),
            DailyTrafficPoint("D16", 78),
            DailyTrafficPoint("D19", 92),
            DailyTrafficPoint("D22", 60),
            DailyTrafficPoint("D25", 85),
            DailyTrafficPoint("D28", 74),
            DailyTrafficPoint("D30", 98)
        )
    }

    // Hourly peak traffic distribution
    val hourlyData = remember {
        listOf(
            HourlyTrafficPoint("06h", 12),
            HourlyTrafficPoint("09h", 45),
            HourlyTrafficPoint("12h", 68),
            HourlyTrafficPoint("15h", 52),
            HourlyTrafficPoint("18h", 94),
            HourlyTrafficPoint("21h", 76),
            HourlyTrafficPoint("00h", 18)
        )
    }

    // Amenity Usage Statistics over last 30 days
    val amenityStats = remember {
        listOf(
            AmenityStat("Quincho & BBQ Principal", 482, 0.38f, GoldPrimary, Icons.Default.LocationOn),
            AmenityStat("Gimnasio Residencial", 342, 0.27f, CyanNeon, Icons.Default.FitnessCenter),
            AmenityStat("Piscina & Solarium", 228, 0.18f, SuccessGreen, Icons.Default.Pool),
            AmenityStat("Cancha Pádel / Tenis", 152, 0.12f, WarningOrange, Icons.Default.SportsTennis),
            AmenityStat("Co-Work & Sala Eventos", 64, 0.05f, Color(0xFFAB47BC), Icons.Default.Group)
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("analytics_summary_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = NavySurface,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldPrimary.copy(alpha = 0.8f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(GoldPrimary.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Analytics,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "PANEL ADMINISTRATIVO • RECHARTS & ANALYTICS",
                                    color = GoldPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Resumen de Tráfico y Amenidades",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Button(
                            onClick = {
                                showPdfReportDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("export_analytics_btn")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Exportar PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Timeframe filter options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("7 Días", "15 Días", "30 Días", "Mes Actual").forEach { period ->
                            FilterChip(
                                selected = selectedTimeframe == period,
                                onClick = { selectedTimeframe = period },
                                label = { Text(period, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldPrimary,
                                    selectedLabelColor = NavyDark,
                                    containerColor = NavyCard,
                                    labelColor = TextMuted
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Executive KPI Metrics Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiMetricCard(
                    title = "Total Accesos",
                    value = "1,268",
                    subtitle = "+14.2% vs mes ant.",
                    color = GoldPrimary,
                    icon = Icons.Default.TrendingUp,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = "Hora Pico",
                    value = "18:00 - 20:00",
                    subtitle = "184 visitas registradas",
                    color = CyanNeon,
                    icon = Icons.Default.Schedule,
                    modifier = Modifier.weight(1f)
                )
                KpiMetricCard(
                    title = "Uso Amenidades",
                    value = "1,268",
                    subtitle = "38% Quinchos / BBQ",
                    color = SuccessGreen,
                    icon = Icons.Default.LocationOn,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Chart 1: 30-Day Visitor Traffic Line Chart
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("traffic_line_chart_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Patrón de Tráfico de Visitas (Últimos $selectedTimeframe)",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Prom: 42 visitas/día",
                            color = CyanNeon,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Custom Compose Canvas Line Chart
                    VisitorTrafficLineCanvas(
                        points = trafficData,
                        lineColor = GoldPrimary,
                        gradientColor = CyanNeon
                    )
                }
            }
        }

        // Chart 2: Hourly Peak Traffic Bar Chart
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hourly_bar_chart_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyanNeon.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                tint = CyanNeon,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Distribución de Flujo por Rango Horario",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Pico: 18:00 h",
                            color = GoldPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Custom Compose Canvas Bar Chart
                    HourlyTrafficBarCanvas(
                        points = hourlyData
                    )
                }
            }
        }

        // Chart 3: Amenity Usage Statistics (Donut & Progress Bars)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("amenity_usage_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PieChart,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Uso de Amenidades del Condominio",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Total: 1,268 Usos",
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Custom Donut Canvas Chart
                        AmenityDonutCanvas(
                            stats = amenityStats,
                            modifier = Modifier.size(130.dp)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // Amenity Legend & Breakdown List
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            amenityStats.forEach { stat ->
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(stat.color, CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stat.name,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Text(
                                            text = "${(stat.percentage * 100).toInt()}%",
                                            color = stat.color,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(3.dp))
                                    LinearProgressIndicator(
                                        progress = { stat.percentage },
                                        color = stat.color,
                                        trackColor = NavyDark,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Local Room Database Amenity Booking & 15-Min Reminder System Section
        item {
            AmenityBookingSection()
        }

        // Chart 4: Dedicated PDF Export Banner for Administrators
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pdf_export_banner_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldPrimary)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(Color(0xFFEF4444).copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "PDF Audit Report",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Exportar Reporte Mensual PDF",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Genera un documento PDF oficial con la auditoría de accesos y reservas de amenidades.",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            showPdfReportDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("generate_pdf_report_btn")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Generar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun KpiMetricCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = NavySurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
                Text(text = title, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Text(text = value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            Text(text = subtitle, color = color, fontSize = 9.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun VisitorTrafficLineCanvas(
    points: List<DailyTrafficPoint>,
    lineColor: Color,
    gradientColor: Color
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        val width = size.width
        val height = size.height
        val paddingLeft = 30f
        val paddingBottom = 40f
        val usableWidth = width - paddingLeft
        val usableHeight = height - paddingBottom

        val maxVal = (points.maxOfOrNull { it.count } ?: 100).toFloat()

        val coordinates = points.mapIndexed { index, point ->
            val x = paddingLeft + (index.toFloat() / (points.size - 1)) * usableWidth
            val y = usableHeight - (point.count.toFloat() / maxVal) * usableHeight + 10f
            Offset(x, y)
        }

        // Draw horizontal grid lines
        for (i in 0..3) {
            val y = usableHeight * (i / 3f) + 10f
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(paddingLeft, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }

        // Build filled area path below curve
        val areaPath = Path().apply {
            if (coordinates.isNotEmpty()) {
                moveTo(coordinates.first().x, usableHeight)
                coordinates.forEach { lineTo(it.x, it.y) }
                lineTo(coordinates.last().x, usableHeight)
                close()
            }
        }

        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    lineColor.copy(alpha = 0.35f),
                    Color.Transparent
                )
            )
        )

        // Build line path
        val linePath = Path().apply {
            if (coordinates.isNotEmpty()) {
                moveTo(coordinates.first().x, coordinates.first().y)
                for (i in 0 until coordinates.size - 1) {
                    val p1 = coordinates[i]
                    val p2 = coordinates[i + 1]
                    val control1 = Offset((p1.x + p2.x) / 2f, p1.y)
                    val control2 = Offset((p1.x + p2.x) / 2f, p2.y)
                    cubicTo(control1.x, control1.y, control2.x, control2.y, p2.x, p2.y)
                }
            }
        }

        drawPath(
            path = linePath,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // Draw data point circles
        coordinates.forEachIndexed { idx, point ->
            drawCircle(
                color = NavyDark,
                radius = 5.dp.toPx(),
                center = point
            )
            drawCircle(
                color = if (idx == points.size - 1) gradientColor else lineColor,
                radius = 3.5.dp.toPx(),
                center = point
            )
        }
    }
}

@Composable
private fun HourlyTrafficBarCanvas(
    points: List<HourlyTrafficPoint>
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
    ) {
        val width = size.width
        val height = size.height
        val maxVal = (points.maxOfOrNull { it.count } ?: 100).toFloat()

        val barWidth = width / (points.size * 2)

        points.forEachIndexed { index, point ->
            val x = (index * 2 + 0.5f) * barWidth
            val barHeight = (point.count / maxVal) * (height - 30f)
            val y = height - barHeight - 20f

            val isPeak = point.count == points.maxOf { it.count }

            drawRoundRect(
                color = if (isPeak) CyanNeon else GoldPrimary.copy(alpha = 0.65f),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
            )
        }
    }
}

@Composable
private fun AmenityDonutCanvas(
    stats: List<AmenityStat>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val canvasSize = size.minDimension
        val strokeWidth = 24.dp.toPx()
        val radius = (canvasSize - strokeWidth) / 2f
        val topLeft = Offset((size.width - canvasSize) / 2f + strokeWidth / 2f, (size.height - canvasSize) / 2f + strokeWidth / 2f)
        val arcSize = Size(radius * 2, radius * 2)

        var startAngle = -90f

        stats.forEach { stat ->
            val sweepAngle = stat.percentage * 360f
            drawArc(
                color = stat.color,
                startAngle = startAngle,
                sweepAngle = sweepAngle - 2f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            startAngle += sweepAngle
        }
    }
}
