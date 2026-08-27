package com.example.ui.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.auth.AlfhaRole
import com.example.auth.AlfhaSecurityContext
import com.example.data.auth.AlfhaUserEntity
import com.example.data.booking.AppDatabase
import com.example.data.packages.PackageEngine
import com.example.data.packages.PackageEntity
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class PackageTabFilter(val label: String) {
    PENDING("En Caseta (Pendientes)"),
    DELIVERED("Historial de Entregados"),
    ALL("Todos")
}

/**
 * CENTRO INTEGRAL DE PAQUETERÍA ALFHA (FASE 10)
 *
 * Módulo unificado para Caseta, Residente, Administración y Panel Maestro.
 * Fuente Única de Verdad: Room SQLite (PackageEntity).
 * Flujo: Recepción en Caseta -> Notificación Inmediata -> Resguardo -> Confirmación de Entrega -> Tiempo Devuelto.
 */
@Composable
fun PackageCenterHub(
    db: AppDatabase,
    filterUnitId: String? = null, // Si es un residente, filtra solo sus paquetes
    canRegister: Boolean = true, // Guardias y Admin pueden recibir
    canDeliver: Boolean = true // Guardias y Admin pueden entregar
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentUser by AlfhaSecurityContext.currentUser.collectAsState()

    val allPackages by db.packageDao().getAllPackagesFlow().collectAsState(initial = emptyList())
    val allUsers by db.alfhaUserDao().getAllUsersFlow().collectAsState(initial = emptyList())

    val residentUsers = remember(allUsers) {
        allUsers.filter { it.role == AlfhaRole.RESIDENTE.roleCode }
    }

    var selectedTab by remember { mutableStateOf(PackageTabFilter.PENDING) }
    var searchQuery by remember { mutableStateOf("") }
    var showReceiveDialog by remember { mutableStateOf(false) }
    var packageToDeliver by remember { mutableStateOf<PackageEntity?>(null) }
    var packageForDetails by remember { mutableStateOf<PackageEntity?>(null) }

    val filteredPackages = remember(allPackages, selectedTab, searchQuery, filterUnitId) {
        allPackages.filter { pkg ->
            val matchesUnit = if (filterUnitId.isNullOrBlank()) true else pkg.unitId.equals(filterUnitId, ignoreCase = true)
            val matchesTab = when (selectedTab) {
                PackageTabFilter.PENDING -> pkg.status != "ENTREGADO"
                PackageTabFilter.DELIVERED -> pkg.status == "ENTREGADO"
                PackageTabFilter.ALL -> true
            }
            val query = searchQuery.trim().lowercase()
            val matchesQuery = if (query.isEmpty()) true else {
                pkg.folio.lowercase().contains(query) ||
                pkg.residentName.lowercase().contains(query) ||
                pkg.unitId.lowercase().contains(query) ||
                pkg.courierCompany.lowercase().contains(query) ||
                pkg.trackingNumber.lowercase().contains(query)
            }
            matchesUnit && matchesTab && matchesQuery
        }
    }

    val pendingCount = remember(allPackages, filterUnitId) {
        allPackages.count { pkg ->
            val matchesUnit = if (filterUnitId.isNullOrBlank()) true else pkg.unitId.equals(filterUnitId, ignoreCase = true)
            matchesUnit && pkg.status != "ENTREGADO"
        }
    }

    val deliveredCount = remember(allPackages, filterUnitId) {
        allPackages.count { pkg ->
            val matchesUnit = if (filterUnitId.isNullOrBlank()) true else pkg.unitId.equals(filterUnitId, ignoreCase = true)
            matchesUnit && pkg.status == "ENTREGADO"
        }
    }

    val totalTimeSavedMinutes = remember(allPackages) {
        allPackages.sumOf { it.timeSavedMinutes } + (deliveredCount * 5)
    }

    val now = System.currentTimeMillis()
    val delayedPackagesCount = remember(allPackages) {
        allPackages.count { it.status != "ENTREGADO" && (now - it.receivedTimestamp) > (24 * 3600 * 1000) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("package_center_hub")
    ) {
        // Encabezado Principal y KPIs de Paquetería
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = NavySurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = GoldPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Inventory2,
                                    contentDescription = null,
                                    tint = GoldPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "CENTRO DE PAQUETERÍA ALFHA",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                color = GoldPrimary
                            )
                            Text(
                                if (filterUnitId != null) "Domicilio: $filterUnitId" else "Recepción, Notificación y Entrega Digital",
                                fontSize = 11.sp,
                                color = CyanNeon
                            )
                        }
                    }

                    if (canRegister) {
                        Button(
                            onClick = { showReceiveDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("btn_receive_package")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = NavyDark, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Recibir", color = NavyDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Métricas Rápidas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = NavyCard,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("En Caseta", fontSize = 10.sp, color = TextMuted)
                            Text("$pendingCount", fontSize = 16.sp, fontWeight = FontWeight.Black, color = WarningOrange)
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = NavyCard,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Entregados", fontSize = 10.sp, color = TextMuted)
                            Text("$deliveredCount", fontSize = 16.sp, fontWeight = FontWeight.Black, color = SuccessGreen)
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1.3f),
                        color = NavyCard,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Tiempo Devuelto", fontSize = 10.sp, color = TextMuted)
                            Text("${totalTimeSavedMinutes} min", fontSize = 15.sp, fontWeight = FontWeight.Black, color = CyanNeon)
                        }
                    }
                }
            }
        }

        // Alerta de Paquetes con más de 24 horas sin recolectar
        if (delayedPackagesCount > 0 && filterUnitId == null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = WarningOrange.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.HourglassBottom, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Hay $delayedPackagesCount paquete(s) con más de 24 hrs en caseta sin recoger. Se sugiere reenviar recordatorio al residente.",
                        fontSize = 11.sp,
                        color = WarningOrange,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Barra de Búsqueda y Filtros
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar folio, guía, residente...", fontSize = 12.sp, color = TextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("package_search_field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GoldPrimary,
                    unfocusedBorderColor = NavyCard,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Chips de Filtro
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(PackageTabFilter.values()) { tab ->
                val isSelected = selectedTab == tab
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedTab = tab },
                    label = {
                        val countLabel = when (tab) {
                            PackageTabFilter.PENDING -> " ($pendingCount)"
                            PackageTabFilter.DELIVERED -> " ($deliveredCount)"
                            PackageTabFilter.ALL -> " (${allPackages.size})"
                        }
                        Text("${tab.label}$countLabel", fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GoldPrimary,
                        selectedLabelColor = NavyDark,
                        containerColor = NavyCard,
                        labelColor = TextMuted
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = TextMuted.copy(alpha = 0.3f),
                        selectedBorderColor = GoldPrimary
                    ),
                    modifier = Modifier.testTag("package_tab_${tab.name}")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Listado de Paquetes
        if (filteredPackages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = TextMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (searchQuery.isNotEmpty()) "No se encontraron paquetes con ese criterio"
                        else if (selectedTab == PackageTabFilter.PENDING) "No hay paquetes pendientes en caseta"
                        else "No hay registros de paquetería",
                        color = TextMuted,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredPackages, key = { it.id }) { pkg ->
                    PackageCardItem(
                        pkg = pkg,
                        canDeliver = canDeliver,
                        onDeliverClick = { packageToDeliver = pkg },
                        onDetailsClick = { packageForDetails = pkg },
                        onRemindClick = {
                            scope.launch {
                                val ok = PackageEngine.sendReminderToResident(
                                    context = context,
                                    db = db,
                                    pkg = pkg,
                                    guardName = currentUser.name
                                )
                                if (ok) {
                                    Toast.makeText(context, "🔔 Recordatorio enviado a ${pkg.residentName}", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Error al enviar recordatorio", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // Diálogo de Recepción de Paquetes en Caseta
    if (showReceiveDialog) {
        ReceivePackageDialog(
            residentUsers = residentUsers,
            currentGuardName = currentUser.name,
            onDismiss = { showReceiveDialog = false },
            onConfirm = { unitId, resName, courier, tracking, size, location, notes ->
                scope.launch {
                    val entity = PackageEngine.receivePackage(
                        context = context,
                        db = db,
                        unitId = unitId,
                        residentName = resName,
                        courierCompany = courier,
                        trackingNumber = tracking,
                        packageSize = size,
                        locationInGuardhouse = location,
                        guardName = currentUser.name,
                        notes = notes
                    )
                    Toast.makeText(context, "📦 Paquete ${entity.folio} registrado y residente notificado", Toast.LENGTH_LONG).show()
                    showReceiveDialog = false
                }
            }
        )
    }

    // Diálogo de Entrega de Paquete
    packageToDeliver?.let { pkg ->
        DeliverPackageDialog(
            pkg = pkg,
            currentGuardName = currentUser.name,
            onDismiss = { packageToDeliver = null },
            onConfirm = { receiverName, notes ->
                scope.launch {
                    val ok = PackageEngine.deliverPackage(
                        context = context,
                        db = db,
                        folio = pkg.folio,
                        deliveredByGuard = currentUser.name,
                        receivedByRecipientName = receiverName,
                        notes = notes
                    )
                    if (ok) {
                        Toast.makeText(context, "✅ Paquete ${pkg.folio} entregado formalmente", Toast.LENGTH_SHORT).show()
                    }
                    packageToDeliver = null
                }
            }
        )
    }

    // Diálogo de Detalle y Trazabilidad de Paquete
    packageForDetails?.let { pkg ->
        PackageDetailsDialog(
            pkg = pkg,
            onDismiss = { packageForDetails = null }
        )
    }
}

/**
 * Tarjeta individual de Paquete
 */
@Composable
private fun PackageCardItem(
    pkg: PackageEntity,
    canDeliver: Boolean,
    onDeliverClick: () -> Unit,
    onDetailsClick: () -> Unit,
    onRemindClick: () -> Unit
) {
    val isPending = pkg.status != "ENTREGADO"
    val timeFormatter = SimpleDateFormat("dd/MMM HH:mm", Locale.getDefault())
    val receivedDateStr = timeFormatter.format(Date(pkg.receivedTimestamp))
    val elapsedHours = (System.currentTimeMillis() - pkg.receivedTimestamp) / (1000 * 3600)
    val isDelayed = isPending && elapsedHours >= 24

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDetailsClick() }
            .testTag("package_card_${pkg.folio}"),
        colors = CardDefaults.cardColors(containerColor = NavySurface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (isDelayed) WarningOrange else if (isPending) GoldPrimary.copy(alpha = 0.5f) else SuccessGreen.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Fila Superior: Folio, Courier y Estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = NavyCard,
                        border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = pkg.folio,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = CyanNeon,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🚚 ${pkg.courierCompany}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Badge de Estado
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (pkg.status) {
                        "ENTREGADO" -> SuccessGreen.copy(alpha = 0.2f)
                        "NOTIFICADO" -> CyanNeon.copy(alpha = 0.2f)
                        else -> WarningOrange.copy(alpha = 0.2f)
                    },
                    border = BorderStroke(
                        1.dp,
                        when (pkg.status) {
                            "ENTREGADO" -> SuccessGreen
                            "NOTIFICADO" -> CyanNeon
                            else -> WarningOrange
                        }
                    )
                ) {
                    Text(
                        text = when (pkg.status) {
                            "ENTREGADO" -> "✓ ENTREGADO"
                            "NOTIFICADO" -> "🔔 NOTIFICADO"
                            else -> "📦 RECIBIDO"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = when (pkg.status) {
                            "ENTREGADO" -> SuccessGreen
                            "NOTIFICADO" -> CyanNeon
                            else -> WarningOrange
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Información de Residente y Domicilio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Home, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = pkg.unitId,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = GoldPrimary
                        )
                    }
                    Text(
                        text = "Destinatario: ${pkg.residentName}",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Guía / Rastreo:", fontSize = 9.sp, color = TextMuted)
                    Text(
                        text = pkg.trackingNumber.ifBlank { "Sin Guía" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Detalles de Recepción, Ubicación en Caseta y Horas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Recibido: $receivedDateStr (${pkg.receivedByGuard})", fontSize = 10.sp, color = TextMuted)
                }

                Surface(
                    color = NavyCard,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "📍 ${pkg.locationInGuardhouse} • ${pkg.packageSize}",
                        fontSize = 10.sp,
                        color = CyanNeon,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Alerta si lleva más de 24 horas
            if (isDelayed) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Pendiente desde hace $elapsedHours hrs (> 24 hrs)",
                        fontSize = 10.sp,
                        color = WarningOrange,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Información de Entrega si ya fue entregado
            if (pkg.status == "ENTREGADO" && pkg.deliveredTimestamp != null) {
                Spacer(modifier = Modifier.height(6.dp))
                val deliveredDateStr = timeFormatter.format(Date(pkg.deliveredTimestamp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SuccessGreen.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Entregado a: ${pkg.receivedByRecipientName ?: pkg.residentName}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                        Text(
                            "$deliveredDateStr por ${pkg.deliveredByGuard ?: "Guardia"}",
                            fontSize = 9.sp,
                            color = TextMuted
                        )
                    }
                }
            }

            // Botones de Acción para Guardias/Admin
            if (isPending && canDeliver) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRemindClick,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanNeon),
                        border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Recordatorio", fontSize = 11.sp)
                    }

                    Button(
                        onClick = onDeliverClick,
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).testTag("btn_deliver_${pkg.folio}")
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = NavyDark, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Entregar", color = NavyDark, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

/**
 * Diálogo de Recepción de Paquete en Garita
 */
@Composable
private fun ReceivePackageDialog(
    residentUsers: List<AlfhaUserEntity>,
    currentGuardName: String,
    onDismiss: () -> Unit,
    onConfirm: (unitId: String, resName: String, courier: String, tracking: String, size: String, location: String, notes: String) -> Unit
) {
    val presetCouriers = listOf("Amazon", "Mercado Libre", "DHL", "FedEx", "Estafeta", "Rappi / Uber Eats", "Otro Courier")
    val presetSizes = listOf("SOBRE", "CHICO", "MEDIANO", "GRANDE")
    val presetLocations = listOf("Estante A1", "Estante A2", "Caseta Principal", "Bodega de Paquetes", "Mostrador")

    var selectedUnit by remember { mutableStateOf(residentUsers.firstOrNull()?.unitOrDepartment ?: "Torre A - Depto 101") }
    var residentName by remember { mutableStateOf(residentUsers.firstOrNull()?.name ?: "Residente Titular") }
    var courierCompany by remember { mutableStateOf("Amazon") }
    var trackingNumber by remember { mutableStateOf("") }
    var selectedSize by remember { mutableStateOf("MEDIANO") }
    var locationInGuardhouse by remember { mutableStateOf("Estante A1") }
    var notes by remember { mutableStateOf("") }

    // Actualiza el nombre del residente cuando se cambia la unidad
    fun onUnitSelected(unit: String) {
        selectedUnit = unit
        val matchedUser = residentUsers.find { it.unitOrDepartment.equals(unit, ignoreCase = true) }
        if (matchedUser != null) {
            residentName = matchedUser.name
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = NavySurface,
            border = BorderStroke(1.5.dp, GoldPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("receive_package_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Inventory2, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("RECEPCIÓN DE PAQUETE", fontWeight = FontWeight.Black, fontSize = 14.sp, color = GoldPrimary)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                // 1. Domicilio / Residente Destinatario
                Text("1. Seleccionar Domicilio y Residente:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)

                if (residentUsers.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(residentUsers) { user ->
                            val isSel = selectedUnit == user.unitOrDepartment
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    selectedUnit = user.unitOrDepartment
                                    residentName = user.name
                                },
                                label = { Text("${user.unitOrDepartment} (${user.name.take(12)})", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldPrimary,
                                    selectedLabelColor = NavyDark,
                                    containerColor = NavyCard,
                                    labelColor = TextMuted
                                )
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = selectedUnit,
                        onValueChange = { selectedUnit = it },
                        label = { Text("Domicilio / Unidad", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f).testTag("package_unit_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = residentName,
                        onValueChange = { residentName = it },
                        label = { Text("Nombre Residente", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f).testTag("package_resident_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                }

                // 2. Courier / Empresa de Envío
                Text("2. Empresa de Paquetería:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presetCouriers) { courier ->
                        val isSel = courierCompany == courier
                        FilterChip(
                            selected = isSel,
                            onClick = { courierCompany = courier },
                            label = { Text(courier, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanNeon,
                                selectedLabelColor = NavyDark,
                                containerColor = NavyCard,
                                labelColor = TextMuted
                            )
                        )
                    }
                }

                // 3. Guía o Código de Rastreo (Opcional)
                OutlinedTextField(
                    value = trackingNumber,
                    onValueChange = { trackingNumber = it },
                    label = { Text("Número de Guía / Código (Opcional)", fontSize = 11.sp) },
                    placeholder = { Text("Se auto-generará si se deja vacío", color = TextMuted, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("package_tracking_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                // 4. Tamaño y Ubicación en Caseta
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tamaño:", fontSize = 10.sp, color = TextMuted)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(presetSizes) { size ->
                                val isSel = selectedSize == size
                                Surface(
                                    onClick = { selectedSize = size },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSel) GoldPrimary else NavyCard,
                                    border = BorderStroke(1.dp, if (isSel) GoldPrimary else Color.Gray.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = size,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) NavyDark else Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ubicación Caseta:", fontSize = 10.sp, color = TextMuted)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(presetLocations.take(3)) { loc ->
                                val isSel = locationInGuardhouse == loc
                                Surface(
                                    onClick = { locationInGuardhouse = loc },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSel) CyanNeon else NavyCard,
                                    border = BorderStroke(1.dp, if (isSel) CyanNeon else Color.Gray.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = loc,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSel) NavyDark else Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. Observaciones
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = { Text("Observaciones (Ej: caja frágil, paquete refrigerado...)", color = TextMuted, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldPrimary,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                // Botón Guardar y Notificar
                Button(
                    onClick = {
                        if (selectedUnit.isBlank() || residentName.isBlank()) return@Button
                        onConfirm(selectedUnit, residentName, courierCompany, trackingNumber, selectedSize, locationInGuardhouse, notes)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("btn_confirm_receive_package")
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, tint = NavyDark, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "📦 Recibir y Notificar Inmediatamente",
                        color = NavyDark,
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/**
 * Diálogo para Confirmar Entrega del Paquete al Residente
 */
@Composable
private fun DeliverPackageDialog(
    pkg: PackageEntity,
    currentGuardName: String,
    onDismiss: () -> Unit,
    onConfirm: (receiverName: String, notes: String) -> Unit
) {
    var receiverName by remember { mutableStateOf(pkg.residentName) }
    var deliveryNotes by remember { mutableStateOf("") }
    var recipientRelationship by remember { mutableStateOf("Titular") }

    val relationships = listOf("Titular", "Familiar", "Empleado/a", "Otro")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = NavySurface,
            border = BorderStroke(1.5.dp, SuccessGreen),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("deliver_package_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ENTREGA DE PAQUETE", fontWeight = FontWeight.Black, fontSize = 14.sp, color = SuccessGreen)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                // Resumen del Paquete
                Surface(
                    color = NavyCard,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Folio: ${pkg.folio} • ${pkg.courierCompany}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyanNeon)
                        Text("Domicilio: ${pkg.unitId} (${pkg.residentName})", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White)
                        Text("Ubicación: ${pkg.locationInGuardhouse} • Guía: ${pkg.trackingNumber}", fontSize = 10.sp, color = TextMuted)
                    }
                }

                Text("¿Quién retira el paquete?", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(relationships) { rel ->
                        val isSel = recipientRelationship == rel
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                recipientRelationship = rel
                                if (rel == "Titular") receiverName = pkg.residentName
                            },
                            label = { Text(rel, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SuccessGreen,
                                selectedLabelColor = NavyDark,
                                containerColor = NavyCard,
                                labelColor = TextMuted
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = receiverName,
                    onValueChange = { receiverName = it },
                    label = { Text("Nombre Completo de quien Recibe", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("receiver_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SuccessGreen,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                OutlinedTextField(
                    value = deliveryNotes,
                    onValueChange = { deliveryNotes = it },
                    placeholder = { Text("Notas de entrega (Opcional, ej: INE verificado)", color = TextMuted, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SuccessGreen,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                )

                Text("Entrega atendida por guardia: $currentGuardName", fontSize = 10.sp, color = TextMuted)

                Button(
                    onClick = {
                        if (receiverName.isBlank()) return@Button
                        val fullNotes = if (recipientRelationship != "Titular") "Retiró: $recipientRelationship - $deliveryNotes" else deliveryNotes
                        onConfirm(receiverName, fullNotes)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().height(46.dp).testTag("btn_confirm_deliver")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = NavyDark, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Confirmar Entrega y Registrar Cierre", color = NavyDark, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Diálogo de Detalle y Trazabilidad Completa del Paquete
 */
@Composable
private fun PackageDetailsDialog(
    pkg: PackageEntity,
    onDismiss: () -> Unit
) {
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    val receivedDate = formatter.format(Date(pkg.receivedTimestamp))
    val deliveredDate = pkg.deliveredTimestamp?.let { formatter.format(Date(it)) } ?: "Pendiente de Recolección"

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = NavySurface,
            border = BorderStroke(1.5.dp, GoldPrimary),
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("DETALLE DE PAQUETERÍA", fontWeight = FontWeight.Black, fontSize = 14.sp, color = GoldPrimary)
                        Text(pkg.folio, fontSize = 12.sp, color = CyanNeon, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                Surface(
                    color = NavyCard,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Estado:", fontSize = 11.sp, color = TextMuted)
                            Text(pkg.status, fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (pkg.status == "ENTREGADO") SuccessGreen else WarningOrange)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Domicilio:", fontSize = 11.sp, color = TextMuted)
                            Text(pkg.unitId, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Destinatario:", fontSize = 11.sp, color = TextMuted)
                            Text(pkg.residentName, fontSize = 11.sp, color = Color.White)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Empresa:", fontSize = 11.sp, color = TextMuted)
                            Text(pkg.courierCompany, fontSize = 11.sp, color = Color.White)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("No. Guía:", fontSize = 11.sp, color = TextMuted)
                            Text(pkg.trackingNumber, fontSize = 11.sp, color = CyanNeon)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ubicación Caseta:", fontSize = 11.sp, color = TextMuted)
                            Text("${pkg.locationInGuardhouse} (${pkg.packageSize})", fontSize = 11.sp, color = Color.White)
                        }
                        Divider(color = Color.White.copy(alpha = 0.1f))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Fecha Recepción:", fontSize = 11.sp, color = TextMuted)
                            Text(receivedDate, fontSize = 10.sp, color = Color.White)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Guardia Recepción:", fontSize = 11.sp, color = TextMuted)
                            Text(pkg.receivedByGuard, fontSize = 10.sp, color = Color.White)
                        }
                        if (pkg.status == "ENTREGADO") {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Fecha Entrega:", fontSize = 11.sp, color = TextMuted)
                                Text(deliveredDate, fontSize = 10.sp, color = SuccessGreen)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Entregado a:", fontSize = 11.sp, color = TextMuted)
                                Text(pkg.receivedByRecipientName ?: pkg.residentName, fontSize = 10.sp, color = Color.White)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Guardia Entrega:", fontSize = 11.sp, color = TextMuted)
                                Text(pkg.deliveredByGuard ?: "-", fontSize = 10.sp, color = Color.White)
                            }
                        }
                        if (pkg.notes.isNotBlank()) {
                            Divider(color = Color.White.copy(alpha = 0.1f))
                            Text("Notas: ${pkg.notes}", fontSize = 11.sp, color = TextMuted)
                        }
                        Divider(color = Color.White.copy(alpha = 0.1f))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Tiempo Devuelto:", fontSize = 11.sp, color = CyanNeon)
                            Text("${pkg.timeSavedMinutes + if (pkg.status == "ENTREGADO") 5 else 0} min", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyanNeon)
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = NavyCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cerrar", color = Color.White)
                }
            }
        }
    }
}
