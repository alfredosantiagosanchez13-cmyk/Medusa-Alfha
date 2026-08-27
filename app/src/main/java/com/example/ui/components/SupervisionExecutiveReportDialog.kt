package com.example.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.supervision.SupervisionExecutiveReport
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
 * Diálogo de Informe Ejecutivo Oficial de Supervisión Táctica ALFHA.
 * Muestra el documento formal 100% certificado con hash SHA-256 e inmutable en Room SQLite.
 */
@Composable
fun SupervisionExecutiveReportDialog(
    report: SupervisionExecutiveReport,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag("supervision_executive_report_dialog"),
            colors = CardDefaults.cardColors(containerColor = NavyCard),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "INFORME EJECUTIVO DE SUPERVISIÓN",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = GoldPrimary
                            )
                            Text(
                                text = "SEGURIDAD INTEGRAL ALFHA • PRODUCCIÓN",
                                fontSize = 9.sp,
                                color = TextMuted
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = GoldPrimary.copy(alpha = 0.3f))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Meta Information Block
                    item {
                        Surface(
                            color = NavySurface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("FOLIO:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GoldPrimary)
                                    Text(report.folio, fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("FECHA Y HORA:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                    Text("${report.dateFormatted} ${report.timeFormatted}", fontSize = 10.sp, color = Color.White)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("SUPERVISOR:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                    Text(report.supervisorName, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyanNeon)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("UBICACIÓN:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                    Text(report.mainLocation, fontSize = 10.sp, color = Color.White)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("DURACIÓN RONDA:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                                    Text("${report.durationMinutes} min", fontSize = 10.sp, color = SuccessGreen)
                                }
                            }
                        }
                    }

                    // Status Breakdown Box
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            ReportScoreBadge("Puntos", report.totalCheckpointsCount.toString(), GoldPrimary, Modifier.weight(1f))
                            ReportScoreBadge("Óptimos", report.optimumCount.toString(), SuccessGreen, Modifier.weight(1f))
                            ReportScoreBadge("Regulares", report.regularCount.toString(), WarningOrange, Modifier.weight(1f))
                            ReportScoreBadge("Críticos", report.criticalCount.toString(), ErrorRed, Modifier.weight(1f))
                            if (report.omittedCount > 0) {
                                ReportScoreBadge("Omitidos", report.omittedCount.toString(), Color(0xFFB388FF), Modifier.weight(1f))
                            }
                        }
                    }

                    // Section: Puntos Revisados y Evidencias
                    item {
                        ReportSectionBlock(
                            title = "PUNTOS REVISADOS Y EVIDENCIAS GPS",
                            content = report.evidenceSummary,
                            accentColor = CyanNeon
                        )
                    }

                    // Section: Hallazgos
                    item {
                        ReportSectionBlock(
                            title = "HALLAZGOS Y NOVEDADES REGISTRADAS",
                            content = report.findingsSummary,
                            accentColor = GoldPrimary
                        )
                    }

                    // Section: Acciones Correctivas
                    item {
                        ReportSectionBlock(
                            title = "ACCIONES CORRECTIVAS REQUERIDAS",
                            content = report.correctiveActionsSummary,
                            accentColor = if (report.criticalCount > 0) ErrorRed else WarningOrange
                        )
                    }

                    // Section: Resultado Final
                    item {
                        Surface(
                            color = when {
                                report.criticalCount > 0 -> ErrorRed.copy(alpha = 0.2f)
                                report.regularCount > 0 -> WarningOrange.copy(alpha = 0.2f)
                                else -> SuccessGreen.copy(alpha = 0.2f)
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                when {
                                    report.criticalCount > 0 -> ErrorRed
                                    report.regularCount > 0 -> WarningOrange
                                    else -> SuccessGreen
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "RESULTADO FINAL DE CERTIFICACIÓN:",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextMuted
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = report.finalResult,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Section: SHA-256 Signature
                    item {
                        Surface(
                            color = NavyDark,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, TextMuted.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "SELLO DIGITAL DE INTEGRIDAD (SHA-256):",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextMuted
                                    )
                                    Text(
                                        text = report.integrityHashSha256,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = CyanNeon
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cerrar", fontSize = 11.sp, color = TextMuted)
                    }
                    Button(
                        onClick = onShare,
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Compartir / Exportar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportScoreBadge(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = NavySurface,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Black, color = color)
            Text(text = label, fontSize = 9.sp, color = TextMuted)
        }
    }
}

@Composable
private fun ReportSectionBlock(title: String, content: String, accentColor: Color) {
    Surface(
        color = NavySurface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = title, fontSize = 9.sp, fontWeight = FontWeight.Black, color = accentColor)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = content, fontSize = 10.sp, color = Color.White, lineHeight = 14.sp)
        }
    }
}
