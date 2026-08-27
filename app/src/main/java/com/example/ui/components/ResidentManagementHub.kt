package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AlfhaPermission
import com.example.auth.AlfhaRole
import com.example.auth.AlfhaSecurityContext
import com.example.data.booking.AppDatabase
import com.example.data.resident.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch

private val NavyCardBorder = Color(0xFF334155)
private val AmberGold = Color(0xFFF59E0B)

@Composable
private fun MetricMiniChip(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = NavyDark,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                Text(title, fontSize = 9.sp, color = TextMuted, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}

enum class ResidentFilterTab(val label: String) {
    TODOS("Todos"),
    PROPIETARIOS("Propietarios"),
    ARRENDATARIOS("Arrendatarios"),
    VEHICULOS("Con Vehículo"),
    BAJAS_LOGICAS("Bajas Lógicas")
}

/**
 * COMPONENTE PRINCIPAL: GESTIÓN DE RESIDENTES Y UNIDADES - FASE 12.
 * 
 * Reglas de Negocio:
 * - Room SQLite como Fuente Única de Verdad.
 * - Alta y baja lógica con trazabilidad e historial inmutable.
 * - Asociación automática residente ↔ unidad.
 * - Búsqueda universal por nombre, unidad, placas y tag de vehículo.
 * - Cero recaptura en pases QR, caseta, paquetería y reservas.
 * - Tiempo devuelto registrado por automatización.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResidentManagementHub(
    db: AppDatabase,
    modifier: Modifier = Modifier,
    filterUnitId: String? = null,
    onNavigateToQrGenerator: ((unitId: String, residentName: String) -> Unit)? = null,
    onNavigateToPackages: ((unitId: String, residentName: String) -> Unit)? = null,
    onNavigateToBookings: ((unitId: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by AlfhaSecurityContext.currentUser.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(ResidentFilterTab.TODOS) }

    // Diálogos de acción
    var showRegisterDialog by remember { mutableStateOf(false) }
    var residentToEdit by remember { mutableStateOf<ResidentEntity?>(null) }
    var residentToDelete by remember { mutableStateOf<ResidentEntity?>(null) }
    var expandedResidentId by remember { mutableStateOf<String?>(null) }

    // Observar base de datos Room
    val allResidentsWithDeleted by db.residentDao().getAllResidentsWithDeletedFlow().collectAsState(initial = emptyList())
    val allUnits by db.unitDao().getAllUnitsFlow().collectAsState(initial = emptyList())

    // Si hay un filtro de unidad preestablecido (ej. desde Panel Residente)
    val effectiveResidents = remember(allResidentsWithDeleted, filterUnitId) {
        if (filterUnitId.isNullOrBlank()) allResidentsWithDeleted
        else allResidentsWithDeleted.filter { it.unitId.contains(filterUnitId, ignoreCase = true) }
    }

    // Filtrar residentes según búsqueda y tab
    val filteredResidents = remember(effectiveResidents, searchQuery, selectedTab) {
        effectiveResidents.filter { res ->
            val matchesSearch = if (searchQuery.isBlank()) true else {
                val q = searchQuery.trim().lowercase()
                res.fullName.lowercase().contains(q) ||
                        res.unitId.lowercase().contains(q) ||
                        res.phone.contains(q) ||
                        res.email.lowercase().contains(q) ||
                        res.vehiclesJson.lowercase().contains(q) ||
                        res.authorizedPersonsJson.lowercase().contains(q) ||
                        res.emergencyContactsJson.lowercase().contains(q)
            }

            val matchesTab = when (selectedTab) {
                ResidentFilterTab.TODOS -> !res.isDeleted
                ResidentFilterTab.PROPIETARIOS -> !res.isDeleted && res.occupancyType.equals("PROPIETARIO", ignoreCase = true)
                ResidentFilterTab.ARRENDATARIOS -> !res.isDeleted && res.occupancyType.equals("ARRENDATARIO", ignoreCase = true)
                ResidentFilterTab.VEHICULOS -> !res.isDeleted && res.parseVehicles().isNotEmpty()
                ResidentFilterTab.BAJAS_LOGICAS -> res.isDeleted || res.status.equals("BAJA_LOGICA", ignoreCase = true)
            }

            matchesSearch && matchesTab
        }
    }

    // Métricas para la cabecera
    val totalActiveResidents = remember(effectiveResidents) { effectiveResidents.count { !it.isDeleted } }
    val totalOwners = remember(effectiveResidents) { effectiveResidents.count { !it.isDeleted && it.occupancyType == "PROPIETARIO" } }
    val totalRenters = remember(effectiveResidents) { effectiveResidents.count { !it.isDeleted && it.occupancyType == "ARRENDATARIO" } }
    val totalVehicles = remember(effectiveResidents) { effectiveResidents.filter { !it.isDeleted }.sumOf { it.parseVehicles().size } }
    val totalSoftDeleted = remember(effectiveResidents) { effectiveResidents.count { it.isDeleted } }

    val canManage = currentUser.hasPermission(AlfhaPermission.CREAR) ||
            currentUser.hasPermission(AlfhaPermission.ADMINISTRAR) ||
            currentUser.alfhaRole == AlfhaRole.ADMINISTRACION ||
            currentUser.alfhaRole == AlfhaRole.MAESTRO_ALFHA

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- CABECERA Y METRICAS ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NavyCard),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(NavyCardBorder))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.HomeWork, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(22.dp))
                            Text(
                                text = "DIRECTORIO DE RESIDENTES & UNIDADES",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "Expediente 360°, Vehículos, Contactos y Cero Recaptura",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }

                    if (canManage) {
                        Button(
                            onClick = { showRegisterDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Alta Residente", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // Metric Badges
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricMiniChip(
                        title = "Activos",
                        value = "$totalActiveResidents",
                        icon = Icons.Default.People,
                        color = SuccessGreen,
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniChip(
                        title = "Propietarios",
                        value = "$totalOwners",
                        icon = Icons.Default.VpnKey,
                        color = CyanNeon,
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniChip(
                        title = "Arrendatarios",
                        value = "$totalRenters",
                        icon = Icons.Default.Badge,
                        color = AmberGold,
                        modifier = Modifier.weight(1f)
                    )
                    MetricMiniChip(
                        title = "Vehículos",
                        value = "$totalVehicles",
                        icon = Icons.Default.DirectionsCar,
                        color = Color(0xFF9D4EDD),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // --- BARRA DE BÚSQUEDA UNIVERSAL ---
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Buscar por nombre, unidad, placas, modelo o teléfono...", fontSize = 12.sp, color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanNeon,
                unfocusedBorderColor = NavyCardBorder,
                focusedContainerColor = NavySurface,
                unfocusedContainerColor = NavySurface,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        // --- CHIPS DE FILTROS ---
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ResidentFilterTab.values()) { tab ->
                val isSelected = selectedTab == tab
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedTab = tab },
                    label = {
                        Text(
                            text = if (tab == ResidentFilterTab.BAJAS_LOGICAS && totalSoftDeleted > 0) "${tab.label} ($totalSoftDeleted)" else tab.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanNeon.copy(alpha = 0.2f),
                        selectedLabelColor = CyanNeon,
                        containerColor = NavySurface,
                        labelColor = TextMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) CyanNeon else NavyCardBorder
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        // --- LISTADO DE RESIDENTES Y EXPEDIENTES ---
        if (filteredResidents.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.PersonSearch, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
                    Text("No se encontraron registros de residentes", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                    Text("Prueba cambiando los términos de búsqueda o el filtro activo.", fontSize = 11.sp, color = TextMuted)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredResidents, key = { it.id }) { resident ->
                    val isExpanded = expandedResidentId == resident.id
                    ResidentRecordCard(
                        resident = resident,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            expandedResidentId = if (isExpanded) null else resident.id
                        },
                        canManage = canManage,
                        onEdit = { residentToEdit = resident },
                        onDelete = { residentToDelete = resident },
                        onRestore = {
                            scope.launch {
                                db.residentDao().restoreResident(resident.id, currentUser.name)
                                Toast.makeText(context, "Residente reactivado en Room", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onQuickCall = { phone ->
                            if (phone.isNotBlank()) {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Llamando a $phone", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onQuickQrPass = {
                            onNavigateToQrGenerator?.invoke(resident.unitId, resident.fullName)
                        },
                        onQuickPackage = {
                            onNavigateToPackages?.invoke(resident.unitId, resident.fullName)
                        },
                        onQuickBooking = {
                            onNavigateToBookings?.invoke(resident.unitId)
                        }
                    )
                }
            }
        }
    }

    // --- DIÁLOGO DE ALTA / EDICIÓN (CERO RECAPTURA) ---
    if (showRegisterDialog || residentToEdit != null) {
        val editingResident = residentToEdit
        ResidentFormDialog(
            initialResident = editingResident,
            availableUnits = allUnits.map { it.unitId },
            onDismiss = {
                showRegisterDialog = false
                residentToEdit = null
            },
            onSave = { fullName, unitId, occupancyType, phone, email, vehicles, authPersons, emergContacts, notes ->
                scope.launch {
                    if (editingResident == null) {
                        val result = ResidentDirectoryEngine.executeRegisterResident(
                            context = context,
                            db = db,
                            fullName = fullName,
                            unitId = unitId,
                            occupancyType = occupancyType,
                            phone = phone,
                            email = email,
                            vehicles = vehicles,
                            authorizedPersons = authPersons,
                            emergencyContacts = emergContacts,
                            notes = notes,
                            operatorName = currentUser.name
                        )
                        when (result) {
                            is ResidentOperationResult.Success -> {
                                Toast.makeText(context, "✅ ${result.message} (+${result.minutesSaved}m devueltos)", Toast.LENGTH_LONG).show()
                                showRegisterDialog = false
                            }
                            is ResidentOperationResult.Error -> {
                                Toast.makeText(context, "⚠️ ${result.message}", Toast.LENGTH_SHORT).show()
                            }
                            is ResidentOperationResult.PermissionDenied -> {
                                Toast.makeText(context, "❌ ${result.reason}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        val updated = editingResident.copy(
                            fullName = fullName,
                            unitId = unitId,
                            occupancyType = occupancyType,
                            phone = phone,
                            email = email,
                            vehiclesJson = ResidentEntity.encodeVehicles(vehicles),
                            authorizedPersonsJson = ResidentEntity.encodeAuthorizedPersons(authPersons),
                            emergencyContactsJson = ResidentEntity.encodeEmergencyContacts(emergContacts),
                            notes = notes
                        )
                        val result = ResidentDirectoryEngine.executeUpdateResident(
                            context = context,
                            db = db,
                            resident = updated,
                            operatorName = currentUser.name
                        )
                        when (result) {
                            is ResidentOperationResult.Success -> {
                                Toast.makeText(context, "✅ ${result.message}", Toast.LENGTH_SHORT).show()
                                residentToEdit = null
                            }
                            is ResidentOperationResult.Error -> {
                                Toast.makeText(context, "⚠️ ${result.message}", Toast.LENGTH_SHORT).show()
                            }
                            is ResidentOperationResult.PermissionDenied -> {
                                Toast.makeText(context, "❌ ${result.reason}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        )
    }

    // --- DIÁLOGO DE BAJA LÓGICA ---
    residentToDelete?.let { res ->
        var deleteReason by remember { mutableStateOf("Cambio de domicilio o desocupación") }
        AlertDialog(
            onDismissRequest = { residentToDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.PersonRemove, contentDescription = null, tint = ErrorRed)
                    Text("Baja Lógica de Residente", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "¿Deseas aplicar baja lógica a '${res.fullName}' en '${res.unitId}'?",
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Text(
                        text = "El registro se archivará de manera inmutable en Room SQLite y se conservará el historial para auditorías.",
                        fontSize = 11.sp,
                        color = TextMuted
                    )
                    OutlinedTextField(
                        value = deleteReason,
                        onValueChange = { deleteReason = it },
                        label = { Text("Motivo de la baja", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = NavyCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val result = ResidentDirectoryEngine.executeSoftDeleteResident(
                                context = context,
                                db = db,
                                residentId = res.id,
                                reason = deleteReason,
                                operatorName = currentUser.name
                            )
                            when (result) {
                                is ResidentOperationResult.Success -> {
                                    Toast.makeText(context, "✅ ${result.message}", Toast.LENGTH_SHORT).show()
                                    residentToDelete = null
                                }
                                is ResidentOperationResult.Error -> {
                                    Toast.makeText(context, "⚠️ ${result.message}", Toast.LENGTH_SHORT).show()
                                }
                                is ResidentOperationResult.PermissionDenied -> {
                                    Toast.makeText(context, "❌ ${result.reason}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Confirmar Baja", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { residentToDelete = null }) {
                    Text("Cancelar", color = TextMuted)
                }
            },
            containerColor = NavyCard,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

/**
 * Tarjeta de Residente con Datos Expandibles (Vehículos, Personas Autorizadas, Contactos de Emergencia).
 */
@Composable
fun ResidentRecordCard(
    resident: ResidentEntity,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    canManage: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onQuickCall: (String) -> Unit,
    onQuickQrPass: () -> Unit,
    onQuickPackage: () -> Unit,
    onQuickBooking: () -> Unit
) {
    val vehicles = remember(resident.vehiclesJson) { resident.parseVehicles() }
    val authorizedPersons = remember(resident.authorizedPersonsJson) { resident.parseAuthorizedPersons() }
    val emergencyContacts = remember(resident.emergencyContactsJson) { resident.parseEmergencyContacts() }

    val statusColor = when {
        resident.isDeleted || resident.status == "BAJA_LOGICA" -> ErrorRed
        resident.status == "SUSPENDIDO" -> AmberGold
        else -> SuccessGreen
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() },
        colors = CardDefaults.cardColors(containerColor = if (resident.isDeleted) NavySurface.copy(alpha = 0.6f) else NavyCard),
        shape = RoundedCornerShape(14.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(if (isExpanded) CyanNeon else NavyCardBorder)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Fila Principal: Avatar, Nombre, Unidad y Estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(CyanNeon.copy(alpha = 0.15f))
                            .border(1.dp, CyanNeon.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = resident.fullName.take(2).uppercase(),
                            color = CyanNeon,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = resident.fullName,
                            fontWeight = FontWeight.Bold,
                            color = if (resident.isDeleted) TextMuted else Color.White,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = resident.unitId,
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                color = CyanNeon
                            )
                            Text("•", color = TextMuted, fontSize = 10.sp)
                            Text(
                                text = resident.occupancyType,
                                fontSize = 10.sp,
                                color = if (resident.occupancyType == "PROPIETARIO") AmberGold else TextMuted
                            )
                        }
                    }
                }

                // Chip de Estado
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = if (resident.isDeleted) "BAJA LÓGICA" else resident.status,
                        color = statusColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            // Datos Rápidos: Teléfono, Correo, Conteo de Vehículos
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (resident.phone.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.clickable { onQuickCall(resident.phone) }
                        ) {
                            Icon(Icons.Default.Phone, contentDescription = "Llamar", tint = SuccessGreen, modifier = Modifier.size(14.dp))
                            Text(resident.phone, fontSize = 11.sp, color = Color.White)
                        }
                    }
                    if (vehicles.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = AmberGold, modifier = Modifier.size(14.dp))
                            Text("${vehicles.size} veh.", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Colapsar" else "Expandir",
                    tint = TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            // --- SECCIÓN EXPANDIDA: EXPEDIENTE 360° ---
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(color = NavyCardBorder)

                    // 1. VEHÍCULOS REGISTRADOS
                    Text("🚗 VEHÍCULOS REGISTRADOS (${vehicles.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyanNeon)
                    if (vehicles.isEmpty()) {
                        Text("Sin vehículos registrados para esta unidad.", fontSize = 11.sp, color = TextMuted)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            vehicles.forEach { v ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = NavySurface,
                                    border = BorderStroke(1.dp, NavyCardBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            // Placa estilo metálico
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color.White,
                                                border = BorderStroke(1.dp, Color.Black)
                                            ) {
                                                Text(
                                                    text = v.plates.uppercase(),
                                                    color = Color.Black,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Text("${v.brand} ${v.model} (${v.color})", fontSize = 11.sp, color = Color.White)
                                        }
                                        if (v.tagRfid.isNotBlank()) {
                                            Text(v.tagRfid, fontSize = 9.sp, color = CyanNeon, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. PERSONAS AUTORIZADAS
                    Text("👥 PERSONAS AUTORIZADAS (${authorizedPersons.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberGold)
                    if (authorizedPersons.isEmpty()) {
                        Text("Sin personas adicionales autorizadas.", fontSize = 11.sp, color = TextMuted)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            authorizedPersons.forEach { ap ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("• ${ap.name} (${ap.relation})", fontSize = 11.sp, color = Color.White)
                                    if (ap.canAuthorizeVisits) {
                                        Text("Autoriza Accesos", fontSize = 9.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // 3. CONTACTOS DE EMERGENCIA
                    Text("🚨 CONTACTOS DE EMERGENCIA (${emergencyContacts.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                    if (emergencyContacts.isEmpty()) {
                        Text("No se han registrado contactos de emergencia.", fontSize = 11.sp, color = TextMuted)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            emergencyContacts.forEach { ec ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onQuickCall(ec.phone) },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("• ${ec.name} (${ec.relation})", fontSize = 11.sp, color = Color.White)
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Default.Phone, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(12.dp))
                                        Text(ec.phone, fontSize = 11.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    if (resident.notes.isNotBlank()) {
                        Text("📝 Notas: ${resident.notes}", fontSize = 10.sp, color = TextMuted)
                    }

                    // 4. ACCIONES CERO RECAPTURA
                    HorizontalDivider(color = NavyCardBorder)
                    Text("⚡ ACCIONES DIRECTAS (CERO RECAPTURA)", fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextMuted)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = onQuickQrPass,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pase QR", fontSize = 10.sp, color = CyanNeon)
                        }

                        OutlinedButton(
                            onClick = onQuickPackage,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.Inventory2, contentDescription = null, tint = AmberGold, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Paquete", fontSize = 10.sp, color = AmberGold)
                        }

                        OutlinedButton(
                            onClick = onQuickBooking,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.EventAvailable, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reserva", fontSize = 10.sp, color = SuccessGreen)
                        }
                    }

                    // Acciones de Administración (Editar / Baja / Reactivar)
                    if (canManage) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onEdit) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = CyanNeon, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Editar Ficha", fontSize = 11.sp, color = CyanNeon)
                            }
                            if (resident.isDeleted) {
                                TextButton(onClick = onRestore) {
                                    Icon(Icons.Default.Restore, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reactivar", fontSize = 11.sp, color = SuccessGreen)
                                }
                            } else {
                                TextButton(onClick = onDelete) {
                                    Icon(Icons.Default.PersonRemove, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Baja Lógica", fontSize = 11.sp, color = ErrorRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Diálogo Completo de Alta / Edición de Residente con soporte para múltiples vehículos, personas autorizadas y emergencias.
 */
@Composable
fun ResidentFormDialog(
    initialResident: ResidentEntity?,
    availableUnits: List<String>,
    onDismiss: () -> Unit,
    onSave: (
        fullName: String,
        unitId: String,
        occupancyType: String,
        phone: String,
        email: String,
        vehicles: List<ResidentVehicle>,
        authorizedPersons: List<AuthorizedPerson>,
        emergencyContacts: List<EmergencyContact>,
        notes: String
    ) -> Unit
) {
    var fullName by remember { mutableStateOf(initialResident?.fullName ?: "") }
    var unitId by remember { mutableStateOf(initialResident?.unitId ?: "") }
    var occupancyType by remember { mutableStateOf(initialResident?.occupancyType ?: "PROPIETARIO") }
    var phone by remember { mutableStateOf(initialResident?.phone ?: "") }
    var email by remember { mutableStateOf(initialResident?.email ?: "") }
    var notes by remember { mutableStateOf(initialResident?.notes ?: "") }

    // Listas dinámicas
    var vehiclesList by remember { mutableStateOf(initialResident?.parseVehicles() ?: emptyList()) }
    var authPersonsList by remember { mutableStateOf(initialResident?.parseAuthorizedPersons() ?: emptyList()) }
    var emergContactsList by remember { mutableStateOf(initialResident?.parseEmergencyContacts() ?: emptyList()) }

    // Sub-campos temporales para agregar
    var tempPlate by remember { mutableStateOf("") }
    var tempBrandModel by remember { mutableStateOf("") }
    var tempColor by remember { mutableStateOf("") }

    var tempAuthName by remember { mutableStateOf("") }
    var tempAuthRelation by remember { mutableStateOf("Familiar") }

    var tempEmergName by remember { mutableStateOf("") }
    var tempEmergPhone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (initialResident == null) Icons.Default.PersonAdd else Icons.Default.Edit,
                    contentDescription = null,
                    tint = CyanNeon
                )
                Text(
                    text = if (initialResident == null) "Alta de Residente (Cero Recaptura)" else "Editar Expediente Residencial",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("DATOS PERSONALES Y ASIGNACIÓN DE UNIDAD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyanNeon)
                }

                item {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Nombre Completo *", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = NavyCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                item {
                    OutlinedTextField(
                        value = unitId,
                        onValueChange = { unitId = it },
                        label = { Text("Unidad / Domicilio * (ej: Casa 104, Torre 1 - 302)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = NavyCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                item {
                    // Selector Tipo de Ocupación
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("PROPIETARIO", "ARRENDATARIO", "FAMILIAR").forEach { type ->
                            val isSelected = occupancyType == type
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { occupancyType = type },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) CyanNeon.copy(alpha = 0.2f) else NavySurface,
                                border = BorderStroke(1.dp, if (isSelected) CyanNeon else NavyCardBorder)
                            ) {
                                Text(
                                    text = type,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) CyanNeon else TextMuted,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Teléfono Directo", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanNeon,
                                unfocusedBorderColor = NavyCardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Correo Electrónico", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanNeon,
                                unfocusedBorderColor = NavyCardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
                }

                // --- SUB-SECCIÓN VEHÍCULOS ---
                item {
                    HorizontalDivider(color = NavyCardBorder)
                    Text("🚗 VEHÍCULOS DE LA UNIDAD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyanNeon)
                }

                items(vehiclesList) { v ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NavySurface, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${v.plates} - ${v.brand} ${v.model} (${v.color})", fontSize = 11.sp, color = Color.White)
                        IconButton(
                            onClick = { vehiclesList = vehiclesList.filter { it.plates != v.plates } },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = ErrorRed, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = tempPlate,
                            onValueChange = { tempPlate = it.uppercase() },
                            placeholder = { Text("Placas", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp)
                        )
                        OutlinedTextField(
                            value = tempBrandModel,
                            onValueChange = { tempBrandModel = it },
                            placeholder = { Text("Marca/Modelo", fontSize = 10.sp) },
                            modifier = Modifier.weight(1.3f),
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp)
                        )
                        OutlinedTextField(
                            value = tempColor,
                            onValueChange = { tempColor = it },
                            placeholder = { Text("Color", fontSize = 10.sp) },
                            modifier = Modifier.weight(0.9f),
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp)
                        )
                        IconButton(
                            onClick = {
                                if (tempPlate.isNotBlank()) {
                                    vehiclesList = vehiclesList + ResidentVehicle(
                                        plates = tempPlate.trim(),
                                        brand = tempBrandModel.trim(),
                                        color = tempColor.trim()
                                    )
                                    tempPlate = ""
                                    tempBrandModel = ""
                                    tempColor = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Agregar Vehículo", tint = CyanNeon)
                        }
                    }
                }

                // --- SUB-SECCIÓN PERSONAS AUTORIZADAS ---
                item {
                    HorizontalDivider(color = NavyCardBorder)
                    Text("👥 PERSONAS AUTORIZADAS PERMANENTES", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberGold)
                }

                items(authPersonsList) { ap ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NavySurface, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${ap.name} (${ap.relation})", fontSize = 11.sp, color = Color.White)
                        IconButton(
                            onClick = { authPersonsList = authPersonsList.filter { it.name != ap.name } },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = ErrorRed, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = tempAuthName,
                            onValueChange = { tempAuthName = it },
                            placeholder = { Text("Nombre autorizado", fontSize = 10.sp) },
                            modifier = Modifier.weight(1.5f),
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp)
                        )
                        OutlinedTextField(
                            value = tempAuthRelation,
                            onValueChange = { tempAuthRelation = it },
                            placeholder = { Text("Parentesco", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp)
                        )
                        IconButton(
                            onClick = {
                                if (tempAuthName.isNotBlank()) {
                                    authPersonsList = authPersonsList + AuthorizedPerson(
                                        name = tempAuthName.trim(),
                                        relation = tempAuthRelation.trim(),
                                        canAuthorizeVisits = true
                                    )
                                    tempAuthName = ""
                                    tempAuthRelation = "Familiar"
                                }
                            }
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Agregar", tint = AmberGold)
                        }
                    }
                }

                // --- SUB-SECCIÓN CONTACTOS DE EMERGENCIA ---
                item {
                    HorizontalDivider(color = NavyCardBorder)
                    Text("🚨 CONTACTOS DE EMERGENCIA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ErrorRed)
                }

                items(emergContactsList) { ec ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(NavySurface, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${ec.name} - ${ec.phone}", fontSize = 11.sp, color = Color.White)
                        IconButton(
                            onClick = { emergContactsList = emergContactsList.filter { it.name != ec.name } },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = ErrorRed, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = tempEmergName,
                            onValueChange = { tempEmergName = it },
                            placeholder = { Text("Nombre contacto", fontSize = 10.sp) },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp)
                        )
                        OutlinedTextField(
                            value = tempEmergPhone,
                            onValueChange = { tempEmergPhone = it },
                            placeholder = { Text("Teléfono", fontSize = 10.sp) },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true,
                            shape = RoundedCornerShape(6.dp)
                        )
                        IconButton(
                            onClick = {
                                if (tempEmergName.isNotBlank() && tempEmergPhone.isNotBlank()) {
                                    emergContactsList = emergContactsList + EmergencyContact(
                                        name = tempEmergName.trim(),
                                        phone = tempEmergPhone.trim()
                                    )
                                    tempEmergName = ""
                                    tempEmergPhone = ""
                                }
                            }
                        ) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Agregar", tint = ErrorRed)
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notas internas / Observaciones de seguridad", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fullName.isNotBlank() && unitId.isNotBlank()) {
                        onSave(fullName, unitId, occupancyType, phone, email, vehiclesList, authPersonsList, emergContactsList, notes)
                    }
                },
                enabled = fullName.isNotBlank() && unitId.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Guardar Expediente", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = TextMuted)
            }
        },
        containerColor = NavyCard,
        shape = RoundedCornerShape(16.dp)
    )
}
