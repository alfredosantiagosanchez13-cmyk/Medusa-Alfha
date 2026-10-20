package com.example.medusaalfha.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.medusaalfha.data.biometric.AlfhaBiometricManager
import com.example.medusaalfha.data.model.Condominium
import com.example.medusaalfha.data.repository.CondominiumRepository
import com.example.medusaalfha.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun MasterDirectorGeneralScreen(
    condominiumRepository: CondominiumRepository,
    onNavigateToGuardPanel: () -> Unit,
    onNavigateToVisitors: () -> Unit,
    onNavigateToBookings: () -> Unit,
    onExitMasterMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val registeredCondos by condominiumRepository.getCondominiumsFlow().collectAsState(initial = listOf(Condominium.DEFAULT_PRADOS))
    val activeCondo by CondominiumRepository.activeCondominium.collectAsState()

    var showAddCondoDialog by remember { mutableStateOf(false) }
    var actionFeedbackMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CyberBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ENCABEZADO DIRECTOR GENERAL MASTER
        item {
            DirectorGeneralMasterHeader(
                activeCondo = activeCondo,
                onExitMasterMode = onExitMasterMode
            )
        }

        // FEEDBACK TEMPORAL DE ACCIONES
        if (actionFeedbackMessage != null) {
            item {
                Surface(
                    color = EmeraldGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreen)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = actionFeedbackMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { actionFeedbackMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = CyberTextMuted)
                        }
                    }
                }
            }
        }

        // MÉTRICAS GLOBALES DEL CORPORATIVO
        item {
            CorporateMetricsRow(
                totalCondos = registeredCondos.size,
                activeCondoName = activeCondo.name,
                totalUnits = registeredCondos.sumOf { it.totalUnits }
            )
        }

        // SECCIÓN: ALTAS DE CONDOMINIO Y GESTIÓN MULTI-RESIDENCIAL
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Apartment,
                                contentDescription = null,
                                tint = NeonBlue,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Condominios Administrados",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Button(
                            onClick = { showAddCondoDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GoldMetallic,
                                contentColor = Color.Black
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("btn_add_condominium_open")
                        ) {
                            Icon(Icons.Default.AddHomeWork, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Alta Condominio", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Seleccione el condominio que desea supervisar o dar de alta nuevos desarrollos para Prados y otros complejos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberTextMuted
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    registeredCondos.forEach { condo ->
                        val isSelected = condo.id == activeCondo.id
                        CondominiumItemCard(
                            condominium = condo,
                            isSelected = isSelected,
                            onSelect = {
                                CondominiumRepository.setActiveCondominium(condo)
                                actionFeedbackMessage = "Condominio activo cambiado a: ${condo.name}"
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // SECCIÓN: ACCESO A TODO (PANELES A TODO DIRECTOR GENERAL)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = CyberSurface),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldMetallic.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            tint = GoldMetallic,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Acceso a Todo · Paneles de Control",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = GoldMetallic
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Privilegio Supremo Master: Acceso irrestricto de supervisión y control a todas las funciones sin restricciones de guardia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyberTextMuted
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Grid de Accesos Directos
                    MasterActionRow(
                        title = "Panel de Caseta & Fatiga",
                        subtitle = "Supervisión de guardia, relevo biométrico y control de acceso",
                        icon = Icons.Default.Security,
                        iconTint = GoldMetallic,
                        onClick = onNavigateToGuardPanel,
                        tag = "master_goto_guard"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    MasterActionRow(
                        title = "Bitácora Global de Visitantes",
                        subtitle = "Ingresos, salidas, escaneo QR y control de proveedores",
                        icon = Icons.Default.Badge,
                        iconTint = NeonBlue,
                        onClick = onNavigateToVisitors,
                        tag = "master_goto_visitors"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    MasterActionRow(
                        title = "Reservas de Áreas Comunes",
                        subtitle = "Control de amenidades, albercas, canchas y salones",
                        icon = Icons.Default.Event,
                        iconTint = TealAccent,
                        onClick = onNavigateToBookings,
                        tag = "master_goto_bookings"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    MasterActionRow(
                        title = "Auditoría de Ciberseguridad & Logs",
                        subtitle = "Cripto-tokens de paquetería, sincronización offline y Firebase",
                        icon = Icons.Default.CloudDone,
                        iconTint = EmeraldGreen,
                        onClick = {
                            actionFeedbackMessage = "Auditoría de Ciberseguridad en línea. Todas las firmas de Firebase están sincronizadas."
                        },
                        tag = "master_goto_audit"
                    )
                }
            }
        }
    }

    // DIÁLOGO DE ALTA DE NUEVO CONDOMINIO
    if (showAddCondoDialog) {
        AddNewCondominiumDialog(
            onDismiss = { showAddCondoDialog = false },
            onConfirmAdd = { newCondo ->
                coroutineScope.launch {
                    val result = condominiumRepository.registerCondominium(newCondo)
                    result.fold(
                        onSuccess = {
                            CondominiumRepository.setActiveCondominium(it)
                            actionFeedbackMessage = "Condominio \"${it.name}\" dado de alta exitosamente en la nube."
                            showAddCondoDialog = false
                        },
                        onFailure = { err ->
                            actionFeedbackMessage = "Error al dar de alta condominio: ${err.message}"
                        }
                    )
                }
            }
        )
    }
}

@Composable
fun DirectorGeneralMasterHeader(
    activeCondo: Condominium,
    onExitMasterMode: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CyberSurfaceElevated,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, GoldMetallic)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(GoldMetallic, CyberSurfaceElevated)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(34.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = AlfhaBiometricManager.MASTER_DIRECTOR_NAME,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = AlfhaBiometricManager.MASTER_DIRECTOR_TITLE,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = GoldMetallic
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onExitMasterMode,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AlertRed.copy(alpha = 0.2f),
                        contentColor = AlertRed
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cerrar Master", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                color = CyberBackground,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldMetallic.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Huella Digital Verificada · ${AlfhaBiometricManager.MASTER_DIRECTOR_EMAIL}",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun CorporateMetricsRow(
    totalCondos: Int,
    activeCondoName: String,
    totalUnits: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            color = CyberSurface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Condominios", style = MaterialTheme.typography.labelSmall, color = CyberTextMuted)
                Text(
                    text = "$totalCondos",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeonBlue
                )
                Text("En Red ALFHA", style = MaterialTheme.typography.labelSmall, color = NeonBlue)
            }
        }

        Surface(
            modifier = Modifier.weight(1.3f),
            color = CyberSurface,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GoldMetallic.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Condominio Activo", style = MaterialTheme.typography.labelSmall, color = CyberTextMuted)
                Text(
                    text = activeCondoName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = GoldMetallic,
                    maxLines = 1
                )
                Text("Total $totalUnits Casas", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
    }
}

@Composable
fun CondominiumItemCard(
    condominium: Condominium,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        color = if (isSelected) NeonBlue.copy(alpha = 0.12f) else CyberSurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) NeonBlue else BorderGray
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = condominium.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = GoldMetallic.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = condominium.id,
                            fontSize = 10.sp,
                            color = GoldMetallic,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${condominium.address} · ${condominium.totalUnits} Casas · ${condominium.gateCount} Garitas",
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberTextMuted
                )
            }

            if (isSelected) {
                Surface(
                    color = NeonBlue,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "ACTIVO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onSelect,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonBlue),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonBlue.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("Activar", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun MasterActionRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(tag),
        color = CyberSurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = CyberTextMuted
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun AddNewCondominiumDialog(
    onDismiss: () -> Unit,
    onConfirmAdd: (Condominium) -> Unit
) {
    var condoId by remember { mutableStateOf("") }
    var condoName by remember { mutableStateOf("") }
    var condoAddress by remember { mutableStateOf("") }
    var condoUnits by remember { mutableStateOf("120") }
    var condoGates by remember { mutableStateOf("2") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AddHomeWork, contentDescription = null, tint = GoldMetallic)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Alta de Nuevo Condominio", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Director General: Santiago Sánchez · Registro en Cloud Firestore",
                    style = MaterialTheme.typography.bodySmall,
                    color = GoldMetallic
                )

                OutlinedTextField(
                    value = condoId,
                    onValueChange = { condoId = it.uppercase().replace(" ", "_") },
                    label = { Text("Clave / ID (ej. PRADOS_2)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_condo_id")
                )

                OutlinedTextField(
                    value = condoName,
                    onValueChange = { condoName = it },
                    label = { Text("Nombre del Condominio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_condo_name")
                )

                OutlinedTextField(
                    value = condoAddress,
                    onValueChange = { condoAddress = it },
                    label = { Text("Dirección / Ubicación") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_condo_address")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = condoUnits,
                        onValueChange = { condoUnits = it },
                        label = { Text("No. Casas") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("input_condo_units")
                    )

                    OutlinedTextField(
                        value = condoGates,
                        onValueChange = { condoGates = it },
                        label = { Text("Garitas") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("input_condo_gates")
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = AlertRed
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (condoId.isBlank() || condoName.isBlank()) {
                        errorMessage = "Por favor ingrese la Clave ID y el Nombre."
                        return@Button
                    }
                    val newCondo = Condominium(
                        id = condoId.trim(),
                        name = condoName.trim(),
                        address = condoAddress.trim(),
                        totalUnits = condoUnits.toIntOrNull() ?: 100,
                        gateCount = condoGates.toIntOrNull() ?: 1,
                        adminContact = "Santiago Sánchez · Dir. Gral",
                        activeStatus = "ACTIVO",
                        createdAtMillis = System.currentTimeMillis(),
                        createdBy = AlfhaBiometricManager.MASTER_DIRECTOR_EMAIL
                    )
                    onConfirmAdd(newCondo)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldMetallic,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_confirm_add_condo")
            ) {
                Text("Dar de Alta", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = CyberTextMuted)
            }
        }
    )
}
