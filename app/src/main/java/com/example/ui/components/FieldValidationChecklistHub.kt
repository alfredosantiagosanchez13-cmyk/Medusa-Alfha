package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.booking.AppDatabase
import com.example.data.validation.FieldValidationRepository
import com.example.data.validation.FieldValidationTestEntity
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarningOrange
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CHECKLIST DE VALIDACIÓN DE CAMPO — MEDUSA ALFHA
 *
 * Interfaz interactiva de 16 pruebas en orden estricto para el Piloto Controlado en Garita.
 * - Todas las pruebas inician en PENDIENTE.
 * - Permite calificar APROBADO / FALLÓ / PENDIENTE con evidencia u observaciones.
 * - Persistencia garantizada en Room SQLite (sin pérdida de resultados previos).
 * - Contadores automáticos: Total (16), Aprobadas, Fallidas y Pendientes.
 * - Banner canónico: "PENDIENTE DE VALIDACIÓN DE CAMPO" mientras existan pendientes.
 */
@Composable
fun FieldValidationChecklistHub(
    db: AppDatabase,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val repository = remember { FieldValidationRepository(db.fieldValidationDao()) }
    val testList by repository.allTestsFlow.collectAsState(initial = emptyList())

    var selectedFilterCategory by remember { mutableStateOf("TODAS") }
    var selectedFilterStatus by remember { mutableStateOf("TODOS") }
    var activeEditingTest by remember { mutableStateOf<FieldValidationTestEntity?>(null) }
    var showResetConfirmationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repository.seedInitialTestsIfEmpty()
    }

    val totalCount = testList.size
    val aprobadasCount = testList.count { it.status == "APROBADO" }
    val fallidasCount = testList.count { it.status == "FALLO" }
    val pendientesCount = testList.count { it.status == "PENDIENTE" }

    val isPendingValidation = pendientesCount > 0 || totalCount == 0

    val filteredList = remember(testList, selectedFilterCategory, selectedFilterStatus) {
        testList.filter { test ->
            val matchesCategory = (selectedFilterCategory == "TODAS" || test.category == selectedFilterCategory)
            val matchesStatus = (selectedFilterStatus == "TODOS" || test.status == selectedFilterStatus)
            matchesCategory && matchesStatus
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(NavyDark)
            .padding(horizontal = 4.dp)
            .testTag("field_validation_checklist_hub"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Header Banner & Status
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("validation_header_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                border = BorderStroke(
                    1.dp,
                    if (isPendingValidation) WarningOrange.copy(alpha = 0.5f) else SuccessGreen.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FactCheck,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Column {
                                Text(
                                    text = "CHECKLIST DE VALIDACIÓN DE CAMPO",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    color = GoldPrimary
                                )
                                Text(
                                    text = "MEDUSA ALFHA • 16 Pruebas Físicas Obligatorias",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                            }
                        }

                        IconButton(
                            onClick = { showResetConfirmationDialog = true },
                            modifier = Modifier.testTag("reset_checklist_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reiniciar Checklist",
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Dictamen / Estado General
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isPendingValidation) WarningOrange.copy(alpha = 0.12f) else if (fallidasCount == 0) SuccessGreen.copy(alpha = 0.12f) else ErrorRed.copy(alpha = 0.12f),
                        border = BorderStroke(
                            1.dp,
                            if (isPendingValidation) WarningOrange.copy(alpha = 0.4f) else if (fallidasCount == 0) SuccessGreen.copy(alpha = 0.4f) else ErrorRed.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isPendingValidation) Icons.Default.HourglassEmpty else if (fallidasCount == 0) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isPendingValidation) WarningOrange else if (fallidasCount == 0) SuccessGreen else ErrorRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = if (isPendingValidation) "PENDIENTE DE VALIDACIÓN DE CAMPO" else if (fallidasCount == 0) "PILOTO APROBADO EN CAMPO" else "PILOTO FINALIZADO CON OBSERVACIONES",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isPendingValidation) WarningOrange else if (fallidasCount == 0) SuccessGreen else ErrorRed
                                )
                                Text(
                                    text = if (isPendingValidation) "Faltan $pendientesCount prueba(s) por evaluar físicamente en garita." else "100% de las pruebas evaluadas y persistidas en Room SQLite.",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // Progress indicator
                    val progress = if (totalCount > 0) (totalCount - pendientesCount).toFloat() / totalCount.toFloat() else 0f
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Progreso del Piloto: ${(progress * 100).toInt()}%",
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${totalCount - pendientesCount} de $totalCount Evaluadas",
                                fontSize = 11.sp,
                                color = CyanNeon,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = if (isPendingValidation) GoldPrimary else SuccessGreen,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                    }

                    // Automatic Counter Metrics Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCounterCard(
                            label = "TOTAL",
                            count = totalCount,
                            color = GoldPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCounterCard(
                            label = "APROBADAS",
                            count = aprobadasCount,
                            color = SuccessGreen,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCounterCard(
                            label = "FALLIDAS",
                            count = fallidasCount,
                            color = ErrorRed,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCounterCard(
                            label = "PENDIENTES",
                            count = pendientesCount,
                            color = WarningOrange,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 2. Filter Selector Row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Category filter
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val categories = listOf("TODAS", "CASETA", "PASE QR", "UBICACIÓN GPS", "OFFLINE / RECONEXIÓN", "TIEMPO DEVUELTO")
                    items(categories) { cat ->
                        val isSelected = selectedFilterCategory == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilterCategory = cat },
                            label = { Text(cat, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = GoldPrimary,
                                containerColor = NavySurface,
                                labelColor = TextMuted
                            ),
                            border = BorderStroke(1.dp, if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.1f))
                        )
                    }
                }

                // Status filter
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val statuses = listOf("TODOS", "PENDIENTE", "APROBADO", "FALLO")
                    items(statuses) { stat ->
                        val isSelected = selectedFilterStatus == stat
                        val chipColor = when (stat) {
                            "APROBADO" -> SuccessGreen
                            "FALLO" -> ErrorRed
                            "PENDIENTE" -> WarningOrange
                            else -> CyanNeon
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilterStatus = stat },
                            label = { Text(stat, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = chipColor.copy(alpha = 0.2f),
                                selectedLabelColor = chipColor,
                                containerColor = NavySurface,
                                labelColor = TextMuted
                            ),
                            border = BorderStroke(1.dp, if (isSelected) chipColor else Color.White.copy(alpha = 0.08f))
                        )
                    }
                }
            }
        }

        // 3. Tests List in Strict Order (1 to 16)
        if (filteredList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = NavySurface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No hay pruebas que coincidan con los filtros seleccionados.",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            items(filteredList, key = { it.testId }) { test ->
                FieldValidationTestCard(
                    test = test,
                    onStatusChange = { newStatus ->
                        scope.launch {
                            repository.updateResult(
                                testId = test.testId,
                                status = newStatus,
                                evidence = test.evidenceReference,
                                observations = test.observations
                            )
                            Toast.makeText(context, "${test.testId}: $newStatus guardado", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onOpenEditDialog = {
                        activeEditingTest = test
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }

    // Modal para capturar Evidencia u Observaciones detalladas
    activeEditingTest?.let { test ->
        EditValidationEvidenceDialog(
            test = test,
            onDismiss = { activeEditingTest = null },
            onSave = { updatedStatus, updatedEvidence, updatedObs ->
                scope.launch {
                    repository.updateResult(
                        testId = test.testId,
                        status = updatedStatus,
                        evidence = updatedEvidence,
                        observations = updatedObs
                    )
                    activeEditingTest = null
                    Toast.makeText(context, "Resultado de ${test.testId} actualizado", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Modal de confirmación para reiniciar checklist
    if (showResetConfirmationDialog) {
        Dialog(onDismissRequest = { showResetConfirmationDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = WarningOrange)
                        Text(
                            text = "Reiniciar Checklist de Campo",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Text(
                        text = "¿Deseas restablecer las 16 pruebas al estado inicial 'PENDIENTE'? Se limpiarán las observaciones de prueba.",
                        fontSize = 12.sp,
                        color = TextMuted
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showResetConfirmationDialog = false }) {
                            Text("Cancelar", color = TextMuted)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    repository.resetAll()
                                    showResetConfirmationDialog = false
                                    Toast.makeText(context, "Checklist restablecido a PENDIENTE", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WarningOrange, contentColor = NavyDark)
                        ) {
                            Text("Restablecer", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tarjeta individual de prueba del Checklist de Validación de Campo.
 */
@Composable
private fun FieldValidationTestCard(
    test: FieldValidationTestEntity,
    onStatusChange: (String) -> Unit,
    onOpenEditDialog: () -> Unit
) {
    val statusColor = when (test.status) {
        "APROBADO" -> SuccessGreen
        "FALLO" -> ErrorRed
        else -> WarningOrange
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("test_card_${test.testId.lowercase()}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header row: ID, Order, Category, Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = GoldPrimary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "#${test.orderIndex} • ${test.testId}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GoldPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White.copy(alpha = 0.08f)
                    ) {
                        Text(
                            text = test.category,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CyanNeon,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, statusColor)
                ) {
                    Text(
                        text = test.status,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Title
            Text(
                text = test.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Procedure
            Text(
                text = "Procedimiento: ${test.procedure}",
                fontSize = 11.sp,
                color = TextMuted,
                lineHeight = 15.sp
            )

            // Criteria
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = NavyDark.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "Criterio de Aceptación:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanNeon
                    )
                    Text(
                        text = test.acceptanceCriteria,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Evidencia Requerida: ${test.evidenceRequired}",
                        fontSize = 10.sp,
                        color = GoldPrimary
                    )
                }
            }

            // Observations & Evidence Section (if present)
            if (test.evidenceReference.isNotBlank() || test.observations.isNotBlank()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = NavySurface,
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (test.evidenceReference.isNotBlank()) {
                            Text(
                                text = "Evidencia: ${test.evidenceReference}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SuccessGreen
                            )
                        }
                        if (test.observations.isNotBlank()) {
                            Text(
                                text = "Notas: ${test.observations}",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            // Action Buttons: Quick Status Switchers & Edit Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Action Pills
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // APROBADO Button
                    OutlinedButton(
                        onClick = { onStatusChange("APROBADO") },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (test.status == "APROBADO") SuccessGreen.copy(alpha = 0.2f) else Color.Transparent,
                            contentColor = if (test.status == "APROBADO") SuccessGreen else Color.Gray
                        ),
                        border = BorderStroke(1.dp, if (test.status == "APROBADO") SuccessGreen else Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.testTag("btn_aprobado_${test.testId.lowercase()}")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Aprobado", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // FALLÓ Button
                    OutlinedButton(
                        onClick = { onStatusChange("FALLO") },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (test.status == "FALLO") ErrorRed.copy(alpha = 0.2f) else Color.Transparent,
                            contentColor = if (test.status == "FALLO") ErrorRed else Color.Gray
                        ),
                        border = BorderStroke(1.dp, if (test.status == "FALLO") ErrorRed else Color.White.copy(alpha = 0.15f)),
                        modifier = Modifier.testTag("btn_fallo_${test.testId.lowercase()}")
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Falló", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Edit / Add Evidence Details Button
                IconButton(
                    onClick = onOpenEditDialog,
                    modifier = Modifier.testTag("btn_edit_${test.testId.lowercase()}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar Evidencia / Notas",
                        tint = GoldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Diálogo modal para registrar evidencia u observaciones de cada prueba.
 */
@Composable
private fun EditValidationEvidenceDialog(
    test: FieldValidationTestEntity,
    onDismiss: () -> Unit,
    onSave: (status: String, evidence: String, observations: String) -> Unit
) {
    var statusState by remember { mutableStateOf(test.status) }
    var evidenceState by remember { mutableStateOf(test.evidenceReference) }
    var observationsState by remember { mutableStateOf(test.observations) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${test.testId} • ${test.title}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = GoldPrimary
                        )
                        Text(
                            text = "Registro de Evidencia y Observaciones",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }

                // Status Selector Row
                Text("Calificación del Test:", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("PENDIENTE", "APROBADO", "FALLO").forEach { st ->
                        val isSelected = statusState == st
                        val color = when (st) {
                            "APROBADO" -> SuccessGreen
                            "FALLO" -> ErrorRed
                            else -> WarningOrange
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { statusState = st },
                            label = { Text(st, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = color.copy(alpha = 0.25f),
                                selectedLabelColor = color,
                                containerColor = NavyDark,
                                labelColor = TextMuted
                            ),
                            border = BorderStroke(1.dp, if (isSelected) color else Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Evidence Reference Field
                OutlinedTextField(
                    value = evidenceState,
                    onValueChange = { evidenceState = it },
                    label = { Text("Evidencia Obtenida / Folio / ID Foto", fontSize = 11.sp) },
                    placeholder = { Text("Ej: Folio MED-20260827-001 / Foto IMG-01", color = Color.Gray, fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Observations Field
                OutlinedTextField(
                    value = observationsState,
                    onValueChange = { observationsState = it },
                    label = { Text("Observaciones del Guardia / Supervisor", fontSize = 11.sp) },
                    placeholder = { Text("Detalles del comportamiento en dispositivo físico...", color = Color.Gray, fontSize = 11.sp) },
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Actions: Cancel & Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(statusState, evidenceState.trim(), observationsState.trim())
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Mini tarjeta para métricas de contadores automáticos.
 */
@Composable
private fun MetricCounterCard(
    label: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = NavyDark,
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$count",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
            Text(
                text = label,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                maxLines = 1
            )
        }
    }
}
