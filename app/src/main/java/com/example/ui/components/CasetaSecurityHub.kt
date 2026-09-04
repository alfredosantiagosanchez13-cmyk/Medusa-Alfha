package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.audit.AuditLogEntity
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.firebase.FirebaseFirestoreSyncService
import com.example.data.firebase.FirestoreTenantManager
import com.example.data.incident.IncidentCategory
import com.example.data.incident.IncidentEntity
import com.example.data.incident.IncidentPriority
import com.example.data.notifications.SmartNotificationHub
import com.example.data.packages.PackageEntity
import com.example.data.passes.QrPassRepository
import com.example.data.passes.toQrPassEntity
import com.example.data.resident.ResidentDirectoryEngine
import com.example.data.resident.ResidentEntity
import com.example.data.supervision.SupervisionAuditEntity
import com.example.data.vehicle.VehicleEntity
import com.example.data.visitor.VisitorCheckIn
import com.example.data.visitor.VisitorCheckInRepository
import com.example.scanner.PassType
import com.example.scanner.QrPassEntity
import com.example.scanner.VerificationResult
import com.example.scanner.VisitorEntry
import com.example.scanner.VisitorStatus
import com.example.ui.screens.ActiveScreenTab
import com.example.ui.theme.*
import com.example.utils.ResidentNotificationManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Catálogo Oficial de Condominios con Aislamiento Estricto de Seguridad en MEDUSA ALFHA.
 * Cada condominio gestiona sus propios accesos, vehículos, visitantes, paquetería y consignas
 * sin mezclar información entre ellos.
 */
enum class CondoTarget(val displayName: String, val shortTag: String, val locationInfo: String, val totalCasas: Int) {
    PARAISO("Condominio Paraíso", "PARAÍSO", "32 Casas · Fracción F4-133", 32),
    PRADOS_1("Los Prados 1", "PRADOS 1", "Calle 1 (Bali 2R) y Calle 2 (Bali 2R/3R) · 94 Casas", 94),
    PRADOS_2("Los Prados 2", "PRADOS 2", "Calle 3 (Bali 2R) y Calle 4 (Bali 2R/3R) · 91 Casas", 91),
    PRADOS_3("Los Prados 3", "PRADOS 3", "Calle 5 y 6 (Topacio 3R) · 76 Casas", 76)
}

/**
 * Sub-herramientas oficiales del Módulo de Seguridad Caseta de MEDUSA ALFHA.
 */
enum class CasetaSubTool(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val badge: String? = null) {
    DASHBOARD_MOVIL("01 Dashboard", Icons.Default.Dashboard, "01"),
    ACCESOS("02 Accesos Garita", Icons.Default.QrCodeScanner, "02"),
    PLACAS("Placas", Icons.Default.DirectionsCar, "AUTO"),
    VISITANTES("Visitantes", Icons.Default.People, "VISITA"),
    PAQUETERIA("Paquetería", Icons.Default.Inventory2, "WHATS"),
    FICHA_RESIDENTE("Ficha 360", Icons.Default.Badge, "CASA"),
    INCIDENTES("Incidentes", Icons.Default.AssignmentLate, "S.O.S."),
    RONDINES("Rondines", Icons.Default.DirectionsWalk, "GPS"),
    ENTREGA_TURNO("Relevo", Icons.Default.ChangeCircle, "FIRMA"),
    CONSIGNAS("Consignas", Icons.Default.Assignment, "REGLAS"),
    ASISTENCIA("Asistencia", Icons.Default.Schedule, "CHECK")
}

// Estructuras de datos de directorio
data class CondoCalle(val nombre: String, val tipo: String, val casasCount: Int)
data class ContactoCondo(val nombre: String, val tel: String, val casa: String)
data class PersonalAutorizado(val casa: String, val tipo: String, val nombre: String)
data class ProveedorCondo(val nombre: String, val servicio: String, val dias: String)
data class ConsignaItem(val hora: String, val texto: String)

object MultiCondoDirectoryHub {

    val callesPrados = mapOf(
        CondoTarget.PRADOS_1 to listOf(
            CondoCalle("Calle 1", "Bali 2R", 49),
            CondoCalle("Calle 2", "Bali 2R + Bali 3R", 45)
        ),
        CondoTarget.PRADOS_2 to listOf(
            CondoCalle("Calle 3", "Bali 2R", 45),
            CondoCalle("Calle 4", "Bali 2R + Bali 3R", 46)
        ),
        CondoTarget.PRADOS_3 to listOf(
            CondoCalle("Calle 5", "Topacio 3R", 45),
            CondoCalle("Calle 6", "Topacio 3R", 31)
        )
    )

    // Directorio Paraíso (32 casas)
    val contactosParaiso = mapOf(
        1 to listOf(ContactoCondo("Viridiana Martinez Bolivar", "9933473150", "Casa 01")),
        2 to listOf(ContactoCondo("Georgina Castro", "4421286457", "Casa 02"), ContactoCondo("Fermin Ruiz Rangel", "4422075912", "Casa 02")),
        3 to listOf(ContactoCondo("Jacob Lee", "3344333466", "Casa 03"), ContactoCondo("Sara Kim", "3344627369", "Casa 03")),
        4 to listOf(ContactoCondo("Michael Mould Urías", "4421732234", "Casa 04"), ContactoCondo("Patricia Palacios Sámano", "4421731488", "Casa 04")),
        5 to listOf(ContactoCondo("Thelma Flores", "4423225204", "Casa 05")),
        6 to listOf(ContactoCondo("Martha Elena Padrón", "4422199249", "Casa 06"), ContactoCondo("Francisco Mendivil", "4421212144", "Casa 06")),
        7 to listOf(ContactoCondo("Rene Roman", "4421942195", "Casa 07"), ContactoCondo("Margarita Pacheco Romero", "4421544127", "Casa 07")),
        8 to listOf(ContactoCondo("Andrea Corona", "4421228785", "Casa 08"), ContactoCondo("Daniela Quiroz", "4422087044", "Casa 08")),
        9 to listOf(ContactoCondo("Marcela Garcia Balderas", "4421281685", "Casa 09"), ContactoCondo("Pablo Arriaga", "4422638380", "Casa 09")),
        10 to listOf(ContactoCondo("Sofía Muñoz Osorio", "4423233507", "Casa 10")),
        11 to listOf(ContactoCondo("Jesús Alejandro Ramos", "4422650393", "Casa 11"), ContactoCondo("Sofia Ramos", "4424798318", "Casa 11")),
        12 to listOf(ContactoCondo("Gisela Contreras Cervantes", "4423234610", "Casa 12"), ContactoCondo("Rodolfo Tarango Juarez", "4426153883", "Casa 12")),
        13 to listOf(ContactoCondo("Isabella", "5516288674", "Casa 13"), ContactoCondo("Raul Cortes", "4424438263", "Casa 13")),
        14 to listOf(ContactoCondo("Juana (Chuy)", "7531022801", "Casa 14"), ContactoCondo("Liliana", "7531049707", "Casa 14")),
        15 to listOf(ContactoCondo("Alejandra Zentella", "5533338897", "Casa 15"), ContactoCondo("Andrés Cervantes", "5535007591", "Casa 15")),
        16 to listOf(ContactoCondo("Enrique A. Cantoral", "4421526695", "Casa 16"), ContactoCondo("Alma A. Angeles", "4424370898", "Casa 16"), ContactoCondo("Ramiro Cantoral", "4428233133", "Casa 16")),
        17 to listOf(ContactoCondo("Cecilia Espinosa Villareal", "4422390848", "Casa 17")),
        18 to listOf(ContactoCondo("Oscar Enrique Ramírez", "5544798924", "Casa 18"), ContactoCondo("Claudia Nuñez Real", "4422007447", "Casa 18")),
        19 to listOf(ContactoCondo("Gabriela Mondragon", "4424468794", "Casa 19")),
        20 to listOf(ContactoCondo("Raúl Hernandez Saguero", "4425046156", "Casa 20")),
        21 to listOf(ContactoCondo("Claudia Mireles Viveros", "4421867108", "Casa 21")),
        22 to listOf(ContactoCondo("Rodolfo Anaya", "5554556583", "Casa 22")),
        23 to listOf(ContactoCondo("Carlos Sánchez", "4422745293", "Casa 23"), ContactoCondo("Vanessa Cuevas", "4423175138", "Casa 23")),
        24 to listOf(ContactoCondo("Karina Villalobos", "2221580635", "Casa 24")),
        25 to listOf(ContactoCondo("Vianey Desachi Cortes", "4423861833", "Casa 25"), ContactoCondo("Ariel Cuevas", "4421525792", "Casa 25")),
        26 to listOf(ContactoCondo("José Domingo Vargas", "4423443088", "Casa 26"), ContactoCondo("Sara Dorantes Hernández", "4421226911", "Casa 26")),
        27 to listOf(ContactoCondo("Patricia Ochoa", "4421210131", "Casa 27")),
        28 to listOf(ContactoCondo("Renata Hernandez", "4721486638", "Casa 28"), ContactoCondo("Perla Rubi Acosta", "4462895265", "Casa 28")),
        29 to listOf(ContactoCondo("María Elena Silva", "5521429256", "Casa 29")),
        30 to listOf(ContactoCondo("Jorge", "3310478087", "Casa 30")),
        31 to listOf(ContactoCondo("Regina Ojeda", "4623048037", "Casa 31"), ContactoCondo("Luis Gerardo Ojeda Rodriguez", "4423827111", "Casa 31")),
        32 to listOf(ContactoCondo("Beatriz Eugenia Velázquez", "4421860918", "Casa 32"))
    )

    val personalParaiso = listOf(
        PersonalAutorizado("Casa 21", "Doméstica", "Teresa Bautista"),
        PersonalAutorizado("Casa 12", "Doméstica", "María Palacio"),
        PersonalAutorizado("Casa 17", "Doméstica", "Claudia Guevara"),
        PersonalAutorizado("Casa 02", "Visita recurrente", "Emanuel Vazquez"),
        PersonalAutorizado("Casa 29", "Doméstica", "Teresa Campos"),
        PersonalAutorizado("Casa 14", "Visita recurrente", "Arturo Salmon")
    )

    val proveedoresParaiso = listOf(
        ProveedorCondo("Ismael Becerra", "Jardinería general", "Viernes"),
        ProveedorCondo("María Claudia", "Limpieza en general", "Sábados"),
        ProveedorCondo("Agua Junghanns", "Agua purificada", "Solo Jueves")
    )

    val consignasPrados = listOf(
        ConsignaItem("07:00", "Encender luces y apertura de portón peatonal"),
        ConsignaItem("14:00", "Revisión perimetral y supervisión de accesos"),
        ConsignaItem("22:00", "Cerrar portón vehicular y activar cerco perimetral"),
        ConsignaItem("03:00–05:00", "Recorrido de vigilancia obligatorio en calles"),
        ConsignaItem("06:00", "Abrir portón y apagar luminarias")
    )

    val consignasParaiso = listOf(
        ConsignaItem("06:30", "Revisión de luminarias y alberca"),
        ConsignaItem("08:00–18:00", "Acceso a personal doméstico empadronado"),
        ConsignaItem("14:00", "Recepción y aviso de paquetería"),
        ConsignaItem("22:00", "Cierre de áreas comunes (alberca y jardín)"),
        ConsignaItem("02:00–04:00", "Rondín perimetral y cerco eléctrico")
    )

    val checkpointsParaiso = listOf(
        "Caseta Principal (Entrada/Salida)",
        "Área de Alberca y Camastros",
        "Jardín Central y Juegos",
        "Bodega de Herramientas y Mantenimiento",
        "Perímetro Norte - Cerco Eléctrico",
        "Estacionamiento de Visitas"
    )

    val checkpointsPrados1 = listOf(
        "Caseta Principal Prados 1",
        "Calle 1 (Inicio - Casas 1 a 25)",
        "Calle 1 (Fondo - Casas 26 a 49)",
        "Calle 2 (Tramo Bali 2R)",
        "Calle 2 (Tramo Bali 3R - Fondo)",
        "Transformador y Perímetro Trasero"
    )

    val checkpointsPrados2 = listOf(
        "Caseta Principal Prados 2",
        "Calle 3 (Sector Norte)",
        "Calle 4 (Sector Sur)",
        "Área de Amenidades y Palapa",
        "Malla Ciclónica Perimetral",
        "Portón de Servicios"
    )

    val checkpointsPrados3 = listOf(
        "Caseta Principal Prados 3",
        "Calle 5 (Topacio 3R - Sector Poniente)",
        "Calle 6 (Topacio 3R - Sector Oriente)",
        "Área Verde Central",
        "Contenedores de Basura",
        "Límite de Barda Perimetral"
    )
}

@Composable
fun CasetaSecurityHub(
    db: AppDatabase,
    onNavigateToTab: (ActiveScreenTab) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Estado del Condominio Activo (SSOT de Aislamiento de Información)
    var selectedCondo by remember { mutableStateOf(CondoTarget.PARAISO) }
    var isServicioActivo by remember { mutableStateOf(true) }
    var currentSubTool by remember { mutableStateOf(CasetaSubTool.ACCESOS) }

    // Repositorios Room
    val visitorRepo = remember { VisitorCheckInRepository(db.visitorCheckInDao()) }
    val qrPassRepo = remember { QrPassRepository(db.qrPassDao()) }
    val packageDao = remember { db.packageDao() }
    val vehicleDao = remember { db.vehicleDao() }
    val incidentDao = remember { db.incidentDao() }
    val supervisionDao = remember { db.supervisionAuditDao() }
    val firestoreSyncService = remember { FirebaseFirestoreSyncService(db, scope) }

    // Sincronización en tiempo real con aislamiento estricto por condominio
    LaunchedEffect(selectedCondo) {
        firestoreSyncService.startRealtimeListeners(selectedCondo.name)
    }

    DisposableEffect(Unit) {
        onDispose {
            firestoreSyncService.stopRealtimeListeners()
        }
    }

    val allCheckIns by visitorRepo.allCheckIns.collectAsState(initial = emptyList())
    val allRoomPasses by qrPassRepo.allPassesFlow.collectAsState(initial = emptyList())
    val allPackages by packageDao.getAllPackagesFlow().collectAsState(initial = emptyList())
    val allVehicles by vehicleDao.getAllVehicles().collectAsState(initial = emptyList())
    val allIncidents by incidentDao.getAllIncidentsFlow().collectAsState(initial = emptyList())
    val allHandoverLogs by supervisionDao.getAllAuditsFlow().collectAsState(initial = emptyList())

    // 🔒 AISLAMIENTO ESTRICTO: Filtrado de datos según el condominio activo
    val condoCheckIns = remember(allCheckIns, selectedCondo) {
        allCheckIns.filter { checkIn ->
            when (selectedCondo) {
                CondoTarget.PARAISO -> checkIn.destinationHouse.contains("Casa", ignoreCase = true) &&
                        (checkIn.guardNotes?.contains("Prados", ignoreCase = true) != true)
                CondoTarget.PRADOS_1 -> checkIn.destinationHouse.contains("Calle 1", ignoreCase = true) ||
                        checkIn.destinationHouse.contains("Calle 2", ignoreCase = true) ||
                        (checkIn.guardNotes?.contains("Prados 1", ignoreCase = true) == true)
                CondoTarget.PRADOS_2 -> checkIn.destinationHouse.contains("Calle 3", ignoreCase = true) ||
                        checkIn.destinationHouse.contains("Calle 4", ignoreCase = true) ||
                        (checkIn.guardNotes?.contains("Prados 2", ignoreCase = true) == true)
                CondoTarget.PRADOS_3 -> checkIn.destinationHouse.contains("Calle 5", ignoreCase = true) ||
                        checkIn.destinationHouse.contains("Calle 6", ignoreCase = true) ||
                        (checkIn.guardNotes?.contains("Prados 3", ignoreCase = true) == true)
            }
        }
    }

    val condoRoomPasses = remember(allRoomPasses, selectedCondo) {
        allRoomPasses.filter { pass ->
            when (selectedCondo) {
                CondoTarget.PARAISO -> !pass.destinationHouse.contains("Calle", ignoreCase = true)
                CondoTarget.PRADOS_1 -> pass.destinationHouse.contains("Calle 1", ignoreCase = true) || pass.destinationHouse.contains("Calle 2", ignoreCase = true)
                CondoTarget.PRADOS_2 -> pass.destinationHouse.contains("Calle 3", ignoreCase = true) || pass.destinationHouse.contains("Calle 4", ignoreCase = true)
                CondoTarget.PRADOS_3 -> pass.destinationHouse.contains("Calle 5", ignoreCase = true) || pass.destinationHouse.contains("Calle 6", ignoreCase = true)
            }
        }
    }

    val condoPackages = remember(allPackages, selectedCondo) {
        allPackages.filter { pkg ->
            when (selectedCondo) {
                CondoTarget.PARAISO -> !pkg.unitId.contains("Calle", ignoreCase = true)
                CondoTarget.PRADOS_1 -> pkg.unitId.contains("Calle 1", ignoreCase = true) || pkg.unitId.contains("Calle 2", ignoreCase = true)
                CondoTarget.PRADOS_2 -> pkg.unitId.contains("Calle 3", ignoreCase = true) || pkg.unitId.contains("Calle 4", ignoreCase = true)
                CondoTarget.PRADOS_3 -> pkg.unitId.contains("Calle 5", ignoreCase = true) || pkg.unitId.contains("Calle 6", ignoreCase = true)
            }
        }
    }

    val condoVehicles = remember(allVehicles, selectedCondo) {
        allVehicles.filter { veh ->
            when (selectedCondo) {
                CondoTarget.PARAISO -> !veh.unitId.contains("Calle", ignoreCase = true)
                CondoTarget.PRADOS_1 -> veh.unitId.contains("Calle 1", ignoreCase = true) || veh.unitId.contains("Calle 2", ignoreCase = true)
                CondoTarget.PRADOS_2 -> veh.unitId.contains("Calle 3", ignoreCase = true) || veh.unitId.contains("Calle 4", ignoreCase = true)
                CondoTarget.PRADOS_3 -> veh.unitId.contains("Calle 5", ignoreCase = true) || veh.unitId.contains("Calle 6", ignoreCase = true)
            }
        }
    }

    val condoIncidents = remember(allIncidents, selectedCondo) {
        allIncidents.filter { inc ->
            when (selectedCondo) {
                CondoTarget.PARAISO -> !inc.location.contains("Calle", ignoreCase = true) && !inc.location.contains("Prados", ignoreCase = true)
                CondoTarget.PRADOS_1 -> inc.location.contains("Calle 1", ignoreCase = true) || inc.location.contains("Calle 2", ignoreCase = true) || inc.location.contains("Prados 1", ignoreCase = true)
                CondoTarget.PRADOS_2 -> inc.location.contains("Calle 3", ignoreCase = true) || inc.location.contains("Calle 4", ignoreCase = true) || inc.location.contains("Prados 2", ignoreCase = true)
                CondoTarget.PRADOS_3 -> inc.location.contains("Calle 5", ignoreCase = true) || inc.location.contains("Calle 6", ignoreCase = true) || inc.location.contains("Prados 3", ignoreCase = true)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("caseta_security_hub")
    ) {
        // =========================================================
        // 1. CONDO SWITCHER MAESTRO (AISLAMIENTO TOTAL DE CONDOMINIOS)
        // =========================================================
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = NavyCard,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            shape = CircleShape,
                            color = if (isServicioActivo) SuccessGreen else ErrorRed,
                            modifier = Modifier.size(8.dp)
                        ) {}
                        Text(
                            text = "CONDOMINIO ACTIVO",
                            color = GoldPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                    }

                    Surface(
                        color = NavyDark,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "🔒 AISLAMIENTO ESTRICTO",
                            color = CyanNeon,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Switcher de los 4 Condominios
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(CondoTarget.values()) { condo ->
                        val isSelected = selectedCondo == condo
                        Surface(
                            onClick = {
                                selectedCondo = condo
                                Toast.makeText(context, "Cambiando a ${condo.displayName} (Datos aislados)", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) GoldPrimary else NavySurface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) GoldPrimary else Color.White.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier.testTag("condo_switch_${condo.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (condo == CondoTarget.PARAISO) "🌴" else "🏡",
                                    fontSize = 12.sp
                                )
                                Column {
                                    Text(
                                        text = condo.displayName,
                                        color = if (isSelected) NavyDark else Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${condo.totalCasas} Casas",
                                        color = if (isSelected) NavyDark.copy(alpha = 0.8f) else TextMuted,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Sub-info bar del condominio activo
                Surface(
                    color = NavyDark.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📍 ${selectedCondo.locationInfo}",
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            onClick = { onNavigateToTab(ActiveScreenTab.VECINOS_PORTAL) },
                            color = GoldPrimary.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "🪼 Portal Vecinos",
                                color = GoldPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // =========================================================
        // 2. SELECTOR DE LAS 10 HERRAMIENTAS TÁCTICAS DE CASETA
        // =========================================================
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(CasetaSubTool.values()) { tool ->
                val isSelected = currentSubTool == tool
                Surface(
                    onClick = { currentSubTool = tool },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) CyanNeon else NavyCard,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) CyanNeon else Color.White.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("caseta_subtool_${tool.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = tool.icon,
                            contentDescription = tool.label,
                            tint = if (isSelected) NavyDark else Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = tool.label,
                            color = if (isSelected) NavyDark else Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        if (tool.badge != null) {
                            Surface(
                                color = if (isSelected) NavyDark.copy(alpha = 0.25f) else GoldPrimary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = tool.badge,
                                    color = if (isSelected) NavyDark else GoldPrimary,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // =========================================================
        // 3. RENDERIZADO DEL SUB-MÓDULO CON INFORMACIÓN AISLADA
        // =========================================================
        when (currentSubTool) {
            CasetaSubTool.DASHBOARD_MOVIL -> {
                MedusaTacticalDashboardHub(
                    db = db,
                    onNavigateToTab = onNavigateToTab,
                    onTriggerScan = { onNavigateToTab(ActiveScreenTab.SCANNER) }
                )
            }
            CasetaSubTool.ACCESOS -> {
                CasetaAccesosIsolatedSection(
                    condo = selectedCondo,
                    db = db,
                    visitorRepo = visitorRepo,
                    qrPassRepo = qrPassRepo,
                    condoPasses = condoRoomPasses,
                    condoCheckIns = condoCheckIns
                )
            }
            CasetaSubTool.PLACAS -> {
                CasetaPlacasIsolatedSection(
                    condo = selectedCondo,
                    db = db,
                    vehicles = condoVehicles,
                    onRegistrarVisita = { currentSubTool = CasetaSubTool.VISITANTES }
                )
            }
            CasetaSubTool.VISITANTES -> {
                CasetaVisitantesIsolatedSection(
                    condo = selectedCondo,
                    db = db,
                    visitorRepo = visitorRepo,
                    condoCheckIns = condoCheckIns
                )
            }
            CasetaSubTool.PAQUETERIA -> {
                CasetaPaqueteriaIsolatedSection(
                    condo = selectedCondo,
                    db = db,
                    packageDao = packageDao,
                    condoPackages = condoPackages
                )
            }
            CasetaSubTool.FICHA_RESIDENTE -> {
                CasetaFichaResidenteIsolatedSection(
                    condo = selectedCondo,
                    db = db,
                    vehicles = condoVehicles,
                    packages = condoPackages,
                    checkIns = condoCheckIns,
                    onIrAVisita = { currentSubTool = CasetaSubTool.VISITANTES },
                    onIrAPaqueteria = { currentSubTool = CasetaSubTool.PAQUETERIA }
                )
            }
            CasetaSubTool.INCIDENTES -> {
                CasetaIncidentesIsolatedSection(
                    condo = selectedCondo,
                    db = db,
                    incidentDao = incidentDao,
                    incidents = condoIncidents
                )
            }
            CasetaSubTool.RONDINES -> {
                CasetaRondinesIsolatedSection(
                    condo = selectedCondo,
                    db = db,
                    auditDao = supervisionDao
                )
            }
            CasetaSubTool.ENTREGA_TURNO -> {
                CasetaEntregaTurnoIsolatedSection(
                    condo = selectedCondo,
                    db = db,
                    supervisionDao = supervisionDao,
                    handoverLogs = allHandoverLogs
                )
            }
            CasetaSubTool.CONSIGNAS -> {
                CasetaConsignasIsolatedSection(condo = selectedCondo)
            }
            CasetaSubTool.ASISTENCIA -> {
                CasetaAsistenciaIsolatedSection(condo = selectedCondo, db = db)
            }
        }
    }
}

// =========================================================================
// 1. ACCESOS / ESCÁNER QR AISLADO POR CONDOMINIO
// =========================================================================
@Composable
fun CasetaAccesosIsolatedSection(
    condo: CondoTarget,
    db: AppDatabase,
    visitorRepo: VisitorCheckInRepository,
    qrPassRepo: QrPassRepository,
    condoPasses: List<com.example.data.passes.QrPassRoomEntity>,
    condoCheckIns: List<VisitorCheckIn>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var manualCode by remember { mutableStateOf("") }
    var activeVerificationResult by remember { mutableStateOf<VerificationResult?>(null) }
    var showExpressModal by remember { mutableStateOf(false) }

    // Express Access Form state
    var expressName by remember { mutableStateOf("") }
    var expressUnit by remember(condo) { mutableStateOf(if (condo == CondoTarget.PARAISO) "Casa 01" else "Calle 1 #01") }
    var expressType by remember { mutableStateOf("Visita") }
    var expressPlate by remember { mutableStateOf("") }
    var expressHost by remember { mutableStateOf("") }

    // Filter for recent entries
    var selectedFilter by remember { mutableStateOf("TODOS") }

    val activeCount = remember(condoCheckIns) {
        condoCheckIns.count { it.status == "CHECKED_IN" || it.status == "VERIFIED" }
    }
    val departedCount = remember(condoCheckIns) {
        condoCheckIns.count { it.status == "DEPARTED" }
    }

    val filteredCheckIns = remember(condoCheckIns, selectedFilter) {
        when (selectedFilter) {
            "EN_PREDIO" -> condoCheckIns.filter { it.status == "CHECKED_IN" || it.status == "VERIFIED" }
            "SALIDAS" -> condoCheckIns.filter { it.status == "DEPARTED" }
            else -> condoCheckIns
        }
    }

    fun verifyCode(code: String) {
        scope.launch {
            val firestore = com.example.data.firebase.FirebaseConfigHelper.getFirestoreInstance()
            val res = qrPassRepo.verifyPassCode(
                code = code.trim(),
                currentCondominiumId = condo.name,
                firestore = firestore
            )
            activeVerificationResult = res
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // KPI Metrics Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    color = NavyCard,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("${condoPasses.size}", color = CyanNeon, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        Text("PASES VIGENTES", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    color = NavyCard,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("$activeCount", color = SuccessGreen, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        Text("EN CONDOMINIO", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    color = NavyCard,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("$departedCount", color = GoldPrimary, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        Text("SALIDAS HOY", color = TextMuted, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Express Registration Action Button
        item {
            Button(
                onClick = { showExpressModal = !showExpressModal },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showExpressModal) ErrorRed else GoldPrimary,
                    contentColor = NavyDark
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("btn_express_access")
            ) {
                Icon(
                    imageVector = if (showExpressModal) Icons.Default.Close else Icons.Default.AddCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (showExpressModal) "CANCELAR REGISTRO EXPRESS" else "+ REGISTRAR ACCESO EXPRESS (SIN QR)",
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp
                )
            }
        }

        // Express Modal Card if active
        if (showExpressModal) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GoldPrimary)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "⚡ REGISTRO EXPRESS DE ACCESO EN ${condo.displayName.uppercase()}",
                            color = GoldPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Visita", "Proveedor", "Paquetería", "Servicio").forEach { type ->
                                FilterChip(
                                    selected = expressType == type,
                                    onClick = { expressType = type },
                                    label = { Text(type, fontSize = 11.sp) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = expressName,
                            onValueChange = { expressName = it },
                            label = { Text("Nombre Completo *", fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = GoldPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = expressUnit,
                                onValueChange = { expressUnit = it },
                                label = { Text("Destino (${condo.shortTag})", fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = GoldPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = expressPlate,
                                onValueChange = { expressPlate = it },
                                label = { Text("Placa (Opcional)", fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = GoldPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = expressHost,
                            onValueChange = { expressHost = it },
                            label = { Text("Residente Anfitrión (Opcional)", fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = GoldPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                if (expressName.isNotBlank()) {
                                    val folio = AlphaCoreEngine.generateUniqueFolio("ACC")
                                    scope.launch {
                                        visitorRepo.insertCheckIn(
                                            VisitorCheckIn(
                                                folio = folio,
                                                visitorName = expressName.trim(),
                                                visitorDocument = "ID Express en Caseta",
                                                destinationHouse = expressUnit.trim(),
                                                passCode = folio,
                                                passTypeLabel = expressType,
                                                vehiclePlate = expressPlate.trim().uppercase(),
                                                status = "CHECKED_IN",
                                                guardNotes = "Registro express en caseta ${condo.displayName}",
                                                hostResidentName = expressHost.ifBlank { "Anfitrión ${condo.shortTag}" }
                                            )
                                        )
                                        ResidentNotificationManager.notifyCustomVisitorEntry(
                                            context = context,
                                            guestName = expressName.trim(),
                                            destinationHouse = expressUnit.trim(),
                                            hostResidentName = expressHost.ifBlank { "Residente" },
                                            passTypeLabel = expressType,
                                            vehiclePlate = expressPlate.trim().uppercase()
                                        )
                                        showExpressModal = false
                                        expressName = ""
                                        expressPlate = ""
                                        Toast.makeText(context, "✅ INGRESO EXPRESS AUTORIZADO ($folio)", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    Toast.makeText(context, "⚠️ Ingrese el nombre del visitante", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("AUTORIZAR INGRESO Y NOTIFICAR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Camera Scanner and Manual Input
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🔍 VALIDACIÓN QR · ${condo.displayName.uppercase()}",
                            color = GoldPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Exclusivo ${condo.shortTag}",
                            color = CyanNeon,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    CameraScannerView(onQrScanned = { verifyCode(it) })

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = manualCode,
                            onValueChange = { manualCode = it },
                            placeholder = { Text("Código: MED-2026...", fontSize = 12.sp, color = Color.Gray) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = GoldPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { if (manualCode.isNotBlank()) verifyCode(manualCode) },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Validar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Registered passes in condo
        item {
            Text(
                "⚡ PASES REGISTRADOS EN ${condo.displayName.uppercase()} (${condoPasses.size})",
                color = TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            if (condoPasses.isEmpty()) {
                Surface(
                    color = NavySurface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "No hay pases activos asignados a ${condo.displayName}.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(condoPasses) { passRoom ->
                        Surface(
                            onClick = { verifyCode(passRoom.passCode) },
                            color = NavySurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(passRoom.guestName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Destino: ${passRoom.destinationHouse}", color = GoldPrimary, fontSize = 10.sp)
                                Text(passRoom.passCode, color = CyanNeon, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }

        // Recent entries with filter chips
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🟢 INGRESOS RECIENTES (${filteredCheckIns.size})",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = selectedFilter == "TODOS",
                        onClick = { selectedFilter = "TODOS" },
                        label = { Text("Todos", fontSize = 10.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                    FilterChip(
                        selected = selectedFilter == "EN_PREDIO",
                        onClick = { selectedFilter = "EN_PREDIO" },
                        label = { Text("En Predio", fontSize = 10.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                    FilterChip(
                        selected = selectedFilter == "SALIDAS",
                        onClick = { selectedFilter = "SALIDAS" },
                        label = { Text("Salidas", fontSize = 10.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            val entries = filteredCheckIns.map { it.toVisitorEntry() }
            RecentVisitorEntriesList(
                entries = entries,
                onStatusChange = { entry, newStatus ->
                    val idLong = entry.id.toLongOrNull()
                    if (idLong != null) {
                        scope.launch {
                            if (newStatus == VisitorStatus.DEPARTED) {
                                visitorRepo.registerCheckOut(idLong, notes = "Check-out 1 toque en ${condo.displayName}")
                                Toast.makeText(context, "Salida registrada en ${condo.displayName}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        }
    }

    activeVerificationResult?.let { res ->
        PassVerificationSheet(
            result = res,
            onConfirmEntry = {
                val pass = res.qrPass
                if (pass != null) {
                    val unifiedFolio = if (pass.passCode.startsWith("MED-")) pass.passCode else AlphaCoreEngine.generateUniqueFolio("MED")
                    scope.launch {
                        qrPassRepo.markPassAsUsed(pass.passCode)
                        visitorRepo.insertCheckIn(
                            VisitorCheckIn(
                                folio = unifiedFolio,
                                visitorName = pass.guestName,
                                visitorDocument = pass.guestDocument,
                                destinationHouse = pass.destinationHouse,
                                passCode = pass.passCode,
                                passTypeLabel = pass.passType.label,
                                vehiclePlate = pass.vehiclePlate,
                                status = "CHECKED_IN",
                                guardNotes = "Ingreso autorizado en ${condo.displayName}",
                                hostResidentName = pass.hostResidentName
                            )
                        )
                        ResidentNotificationManager.notifyCustomVisitorEntry(
                            context = context,
                            guestName = pass.guestName,
                            destinationHouse = pass.destinationHouse,
                            hostResidentName = pass.hostResidentName,
                            passTypeLabel = pass.passType.label,
                            vehiclePlate = pass.vehiclePlate
                        )
                        activeVerificationResult = null
                        Toast.makeText(context, "✅ INGRESO AUTORIZADO EN ${condo.displayName}", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDenyEntry = {
                activeVerificationResult = null
                Toast.makeText(context, "Ingreso rechazado", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { activeVerificationResult = null }
        )
    }
}

// =========================================================================
// 2. PLACAS VEHICULARES AISLADAS POR CONDOMINIO
// =========================================================================
@Composable
fun CasetaPlacasIsolatedSection(
    condo: CondoTarget,
    db: AppDatabase,
    vehicles: List<VehicleEntity>,
    onRegistrarVisita: () -> Unit
) {
    var searchPlate by remember { mutableStateOf("") }
    val normalizedSearch = searchPlate.trim().uppercase().replace("-", "").replace(" ", "")

    val match = remember(normalizedSearch, vehicles) {
        if (normalizedSearch.length < 3) null
        else vehicles.find { it.plate.uppercase().replace("-", "").replace(" ", "").contains(normalizedSearch) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🚗 CONSULTA DE PLACAS · ${condo.displayName.uppercase()}", color = GoldPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Consulta exclusiva en el padrón vehicular de ${condo.displayName}.", color = TextMuted, fontSize = 11.sp)

                    OutlinedTextField(
                        value = searchPlate,
                        onValueChange = { searchPlate = it },
                        placeholder = { Text("Ej: ABC1234, QRO-789", color = Color.Gray, fontSize = 13.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CyanNeon
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("plate_search_input")
                    )

                    if (searchPlate.isNotBlank()) {
                        if (match != null) {
                            Surface(
                                color = SuccessGreen.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, SuccessGreen)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("✅ ES RESIDENTE DE ${condo.displayName.uppercase()}", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Placa: ${match.plate}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Ubicación: ${match.unitId}", color = GoldPrimary, fontSize = 12.sp)
                                    Text("Propietario: ${match.ownerName}", color = Color.LightGray, fontSize = 12.sp)
                                    Text("Vehículo: ${match.brand} ${match.model} (${match.color})", color = Color.LightGray, fontSize = 11.sp)
                                }
                            }
                        } else if (normalizedSearch.length >= 3) {
                            Surface(
                                color = ErrorRed.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, ErrorRed)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("⚠️ PLACA NO REGISTRADA EN ${condo.displayName.uppercase()}", color = ErrorRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("No pertenece a este condominio. Registrar como visita o proveedor externo.", color = Color.White, fontSize = 12.sp)
                                    Button(
                                        onClick = onRegistrarVisita,
                                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Registrar como Visita en ${condo.displayName}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text("📋 PADRÓN VEHICULAR DE ${condo.displayName.uppercase()} (${vehicles.size})", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        if (vehicles.isEmpty()) {
            item {
                Surface(color = NavySurface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("No hay vehículos empadronados para ${condo.displayName}.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(12.dp))
                }
            }
        }

        items(vehicles) { veh ->
            Card(
                colors = CardDefaults.cardColors(containerColor = NavySurface),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(veh.plate, color = CyanNeon, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${veh.brand} ${veh.model} • ${veh.color}", color = Color.White, fontSize = 11.sp)
                        Text("${veh.unitId} • ${veh.ownerName}", color = TextMuted, fontSize = 10.sp)
                    }
                    Surface(
                        color = GoldPrimary.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(veh.vehicleType, color = GoldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
}

// =========================================================================
// 3. VISITANTES / PROVEEDORES AISLADOS POR CONDOMINIO
// =========================================================================
@Composable
fun CasetaVisitantesIsolatedSection(
    condo: CondoTarget,
    db: AppDatabase,
    visitorRepo: VisitorCheckInRepository,
    condoCheckIns: List<VisitorCheckIn>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showForm by remember { mutableStateOf(false) }
    var visitorType by remember { mutableStateOf("Visita") }
    var destinationCasa by remember(condo) {
        mutableStateOf(if (condo == CondoTarget.PARAISO) "Casa 01" else "Calle 1 #01")
    }
    var visitorName by remember { mutableStateOf("") }
    var visitorDoc by remember { mutableStateOf("") }
    var vehiclePlate by remember { mutableStateOf("") }
    var hostName by remember { mutableStateOf("") }

    val activeVisitors = remember(condoCheckIns) {
        condoCheckIns.filter { it.status == "CHECKED_IN" || it.status == "VERIFIED" }
    }
    val departedVisitors = remember(condoCheckIns) {
        condoCheckIns.filter { it.status == "DEPARTED" }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Button(
                onClick = { showForm = !showForm },
                colors = ButtonDefaults.buttonColors(containerColor = if (showForm) ErrorRed else CyanNeon, contentColor = NavyDark),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(if (showForm) Icons.Default.Close else Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (showForm) "Cancelar Registro" else "+ Registrar Entrada en ${condo.displayName}", fontWeight = FontWeight.Bold)
            }
        }

        if (showForm) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GoldPrimary)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("📝 REGISTRO DE ENTRADA · ${condo.displayName.uppercase()}", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = visitorType == "Visita",
                                onClick = { visitorType = "Visita" },
                                label = { Text("Visita General") }
                            )
                            FilterChip(
                                selected = visitorType == "Proveedor",
                                onClick = { visitorType = "Proveedor" },
                                label = { Text("Proveedor / Servicio") }
                            )
                        }

                        OutlinedTextField(
                            value = visitorName,
                            onValueChange = { visitorName = it },
                            label = { Text("Nombre Completo Visitante") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = destinationCasa,
                                onValueChange = { destinationCasa = it },
                                label = { Text("Destino (${condo.shortTag})") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = vehiclePlate,
                                onValueChange = { vehiclePlate = it },
                                label = { Text("Placa Vehículo") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = hostName,
                            onValueChange = { hostName = it },
                            label = { Text("Residente Anfitrión") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                if (visitorName.isNotBlank()) {
                                    val folio = AlphaCoreEngine.generateUniqueFolio("MED")
                                    scope.launch {
                                        visitorRepo.insertCheckIn(
                                            VisitorCheckIn(
                                                folio = folio,
                                                visitorName = visitorName.trim(),
                                                visitorDocument = visitorDoc.trim(),
                                                destinationHouse = destinationCasa.trim(),
                                                passCode = folio,
                                                passTypeLabel = visitorType,
                                                vehiclePlate = vehiclePlate.trim(),
                                                status = "CHECKED_IN",
                                                guardNotes = "Entrada en ${condo.displayName}",
                                                hostResidentName = hostName.trim().ifBlank { "Residente de $destinationCasa" }
                                            )
                                        )
                                        ResidentNotificationManager.notifyCustomVisitorEntry(
                                            context = context,
                                            guestName = visitorName.trim(),
                                            destinationHouse = destinationCasa.trim(),
                                            hostResidentName = hostName.trim().ifBlank { "Residente" },
                                            passTypeLabel = visitorType,
                                            vehiclePlate = vehiclePlate.trim()
                                        )
                                        visitorName = ""
                                        vehiclePlate = ""
                                        showForm = false
                                        Toast.makeText(context, "✅ Entrada registrada en ${condo.displayName} (Folio $folio)", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Ingrese el nombre del visitante", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = NavyDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Guardar Entrada en ${condo.displayName}", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Text("🟢 ACTIVOS DENTRO DE ${condo.displayName.uppercase()} (${activeVisitors.size})", color = SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        if (activeVisitors.isEmpty()) {
            item {
                Surface(color = NavySurface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("No hay visitas activas dentro de ${condo.displayName}.", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.padding(10.dp))
                }
            }
        }

        items(activeVisitors) { v ->
            Card(
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(v.visitorName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(v.folio, color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("Destino: ${v.destinationHouse} • Anfitrión: ${v.hostResidentName}", color = TextMuted, fontSize = 11.sp)
                    Text("Hora Entrada: ${v.formattedTime} • Tipo: ${v.passTypeLabel}", color = Color.LightGray, fontSize = 10.sp)

                    Button(
                        onClick = {
                            scope.launch {
                                visitorRepo.registerCheckOut(v.id, notes = "Salida confirmada en ${condo.displayName}")
                                ResidentNotificationManager.notifyVisitorDeparted(
                                    context = context,
                                    guestName = v.visitorName,
                                    destinationHouse = v.destinationHouse,
                                    hostResidentName = v.hostResidentName,
                                    durationStay = v.durationStayFormatted
                                )
                                Toast.makeText(context, "🚪 Salida registrada para ${v.visitorName}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Registrar Salida (Check-Out 1 Toque)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(10.dp))
            Text("📜 HISTORIAL DE SALIDAS · ${condo.displayName.uppercase()} (${departedVisitors.size})", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        items(departedVisitors.take(8)) { v ->
            Surface(
                color = NavySurface,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(v.visitorName, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                        Text("${v.destinationHouse} • Salida: ${v.formattedCheckOutTime ?: v.formattedTime}", color = TextMuted, fontSize = 10.sp)
                    }
                    Surface(color = GoldPrimary.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                        Text("⏱️ ${v.durationStayFormatted}", color = GoldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
}

// =========================================================================
// 4. PAQUETERÍA TÁCTICA AISLADA (PARAÍSO VS LOS PRADOS)
// =========================================================================
@Composable
fun CasetaPaqueteriaIsolatedSection(
    condo: CondoTarget,
    db: AppDatabase,
    packageDao: com.example.data.packages.PackageDao,
    condoPackages: List<PackageEntity>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (condo == CondoTarget.PARAISO) {
        // Interfaz específica para Condominio Paraíso (32 casas con WhatsApp / Teléfono)
        var selectedCasaNum by remember { mutableStateOf(1) }
        var packageDesc by remember { mutableStateOf("") }
        val contactos = MultiCondoDirectoryHub.contactosParaiso[selectedCasaNum] ?: emptyList()
        var selectedContactoIdx by remember { mutableStateOf(0) }

        val isSinWhatsapp = selectedCasaNum == 22
        val isPaqueteDirecto = selectedCasaNum == 14

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("📦 RECEPCIÓN DE PAQUETES · CONDOMINIO PARAÍSO (32 CASAS)", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        Text("Selecciona la Casa (01 a 32):", color = TextMuted, fontSize = 11.sp)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items((1..32).toList()) { casa ->
                                FilterChip(
                                    selected = selectedCasaNum == casa,
                                    onClick = {
                                        selectedCasaNum = casa
                                        selectedContactoIdx = 0
                                    },
                                    label = { Text("Casa ${String.format("%02d", casa)}") }
                                )
                            }
                        }

                        if (isSinWhatsapp) {
                            Surface(color = WarningOrange.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                                Text("⚠️ Casa 22: solo llamada por teléfono fijo (sin WhatsApp).", color = WarningOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                            }
                        }
                        if (isPaqueteDirecto) {
                            Surface(color = CyanNeon.copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp)) {
                                Text("ℹ️ Casa 14: las paqueterías pasan directo a la casa.", color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                            }
                        }

                        Text("Destinatario registrado en directorio:", color = TextMuted, fontSize = 11.sp)
                        contactos.forEachIndexed { idx, c ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedContactoIdx = idx }
                            ) {
                                RadioButton(
                                    selected = selectedContactoIdx == idx,
                                    onClick = { selectedContactoIdx = idx },
                                    colors = RadioButtonDefaults.colors(selectedColor = GoldPrimary)
                                )
                                Text("${c.nombre} (${c.tel})", color = Color.White, fontSize = 12.sp)
                            }
                        }

                        OutlinedTextField(
                            value = packageDesc,
                            onValueChange = { packageDesc = it },
                            label = { Text("Descripción del paquete (Ej: 1 caja Amazon, sobre)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                val contacto = contactos.getOrNull(selectedContactoIdx)
                                if (contacto != null) {
                                    val folio = AlphaCoreEngine.generateUniqueFolio("PKG")
                                    scope.launch {
                                        packageDao.insertPackage(
                                            PackageEntity(
                                                id = "PKG-${System.currentTimeMillis()}",
                                                folio = folio,
                                                unitId = "Casa ${String.format("%02d", selectedCasaNum)}",
                                                residentName = contacto.nombre,
                                                courierCompany = "Paquetería Caseta",
                                                packageSize = packageDesc.ifBlank { "MEDIANO" },
                                                status = "RECIBIDO",
                                                receivedTimestamp = System.currentTimeMillis()
                                            )
                                        )
                                        val cleanTel = contacto.tel.replace(Regex("[^0-9]"), "")
                                        if (isSinWhatsapp) {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanTel"))
                                            context.startActivity(intent)
                                        } else {
                                            val directoNota = if (isPaqueteDirecto) "" else " Puede recogerlo en la caseta de Condominio Paraíso."
                                            val msg = "Le informamos que llegó un paquete para Casa ${String.format("%02d", selectedCasaNum)}: ${packageDesc.ifBlank { "1 paquete" }}.$directoNota — Seguridad"
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/52$cleanTel?text=${Uri.encode(msg)}"))
                                            try {
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanTel"))
                                                context.startActivity(callIntent)
                                            }
                                        }
                                        Toast.makeText(context, "✅ Paquete registrado para Casa ${String.format("%02d", selectedCasaNum)}", Toast.LENGTH_SHORT).show()
                                        packageDesc = ""
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = NavyDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isSinWhatsapp) "📞 Registrar y Llamar (Casa 22)" else "📲 Registrar y Avisar por WhatsApp", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Text("📋 PAQUETES PENDIENTES EN CASETA PARAÍSO (${condoPackages.count { it.status == "RECIBIDO" }})", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            items(condoPackages) { pkg ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = NavySurface),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(pkg.unitId, color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(pkg.residentName, color = Color.White, fontSize = 11.sp)
                            Text("${pkg.packageSize} • Folio: ${pkg.folio}", color = TextMuted, fontSize = 10.sp)
                        }
                        if (pkg.status == "RECIBIDO") {
                            Button(
                                onClick = {
                                    scope.launch {
                                        packageDao.updatePackage(pkg.copy(status = "ENTREGADO", deliveredTimestamp = System.currentTimeMillis()))
                                        Toast.makeText(context, "Entregado a ${pkg.residentName}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Entregar", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        } else {
                            Surface(color = SuccessGreen.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                                Text("ENTREGADO", color = SuccessGreen, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Interfaz específica para Los Prados 1, 2 y 3 (Calles, selección múltiple y fotos ID/Placas)
        val calles = MultiCondoDirectoryHub.callesPrados[condo] ?: emptyList()
        var selectedCallesCasas by remember { mutableStateOf(setOf<String>()) }
        var courierCompany by remember { mutableStateOf("") }
        var repartidorFotoTaken by remember { mutableStateOf(false) }
        var placasFotoTaken by remember { mutableStateOf(false) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("📦 PAQUETERÍA · ${condo.displayName.uppercase()}", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("El repartidor entra directo a las casas. Selecciona a quién(es) le llegó:", color = TextMuted, fontSize = 11.sp)

                        // Selector de Casas por Calle
                        calles.forEach { calle ->
                            Text("${calle.nombre} (${calle.tipo}) — ${calle.casasCount} Casas:", color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items((1..calle.casasCount).toList()) { num ->
                                    val casaTag = "${calle.nombre} #${num}"
                                    val isChecked = selectedCallesCasas.contains(casaTag)
                                    FilterChip(
                                        selected = isChecked,
                                        onClick = {
                                            selectedCallesCasas = if (isChecked) selectedCallesCasas - casaTag else selectedCallesCasas + casaTag
                                        },
                                        label = { Text("#$num", fontSize = 11.sp) }
                                    )
                                }
                            }
                        }

                        if (selectedCallesCasas.isNotEmpty()) {
                            Surface(color = NavyDark, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Seleccionadas: ${selectedCallesCasas.size} casas", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    TextButton(onClick = { selectedCallesCasas = emptySet() }) {
                                        Text("Limpiar", color = ErrorRed, fontSize = 10.sp)
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = courierCompany,
                            onValueChange = { courierCompany = it },
                            label = { Text("Empresa / Repartidor (Ej: Amazon, DHL, Estafeta)") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Botones de fotos duales (Identificación y Placas)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    repartidorFotoTaken = !repartidorFotoTaken
                                    Toast.makeText(context, if (repartidorFotoTaken) "📸 Foto de ID capturada" else "Foto retirada", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (repartidorFotoTaken) SuccessGreen else NavySurface,
                                    contentColor = if (repartidorFotoTaken) NavyDark else Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (repartidorFotoTaken) "✅ ID Capturada" else "📷 Foto ID Repartidor", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    placasFotoTaken = !placasFotoTaken
                                    Toast.makeText(context, if (placasFotoTaken) "📸 Foto de Placas capturada" else "Foto retirada", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (placasFotoTaken) SuccessGreen else NavySurface,
                                    contentColor = if (placasFotoTaken) NavyDark else Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (placasFotoTaken) "✅ Placas Capturada" else "🚗 Foto Placas", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = {
                                if (selectedCallesCasas.isEmpty()) {
                                    Toast.makeText(context, "Selecciona al menos una casa", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                scope.launch {
                                    selectedCallesCasas.forEach { casa ->
                                        val folio = AlphaCoreEngine.generateUniqueFolio("PKG")
                                        packageDao.insertPackage(
                                            PackageEntity(
                                                id = "PKG-${System.currentTimeMillis()}-${casa.hashCode()}",
                                                folio = folio,
                                                unitId = casa,
                                                residentName = "Residente $casa",
                                                courierCompany = courierCompany.ifBlank { "Paquetería Express" },
                                                packageSize = "Directo a Casa",
                                                status = "NOTIFICADO",
                                                receivedTimestamp = System.currentTimeMillis()
                                            )
                                        )
                                    }
                                    Toast.makeText(context, "📢 Avisado a ${selectedCallesCasas.size} casas en ${condo.displayName}", Toast.LENGTH_LONG).show()
                                    selectedCallesCasas = emptySet()
                                    courierCompany = ""
                                    repartidorFotoTaken = false
                                    placasFotoTaken = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📢 Avisar a Residentes — Llegó Paquetería", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            item {
                Text("📋 AVISOS RECIENTES DE PAQUETERÍA EN ${condo.displayName.uppercase()} (${condoPackages.size})", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            items(condoPackages.take(15)) { pkg ->
                Surface(
                    color = NavySurface,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(pkg.unitId, color = CyanNeon, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("${pkg.courierCompany} • Folio: ${pkg.folio}", color = Color.White, fontSize = 11.sp)
                        }
                        Surface(color = GoldPrimary.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                            Text("AVISADO", color = GoldPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 5. FICHA 360 DEL RESIDENTE AISLADA POR CONDOMINIO
// =========================================================================
@Composable
fun CasetaFichaResidenteIsolatedSection(
    condo: CondoTarget,
    db: AppDatabase,
    vehicles: List<VehicleEntity>,
    packages: List<PackageEntity>,
    checkIns: List<VisitorCheckIn>,
    onIrAVisita: () -> Unit,
    onIrAPaqueteria: () -> Unit
) {
    val context = LocalContext.current

    if (condo == CondoTarget.PARAISO) {
        var selectedCasaNum by remember { mutableStateOf(1) }
        val contactos = MultiCondoDirectoryHub.contactosParaiso[selectedCasaNum] ?: emptyList()
        val personal = MultiCondoDirectoryHub.personalParaiso.filter { it.casa == "Casa ${String.format("%02d", selectedCasaNum)}" }
        val vehiculosCasa = vehicles.filter { it.unitId.contains(String.format("%02d", selectedCasaNum)) }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("🏠 SELECCIONA LA CASA A CONSULTAR (COND. PARAÍSO):", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items((1..32).toList()) { casa ->
                        FilterChip(
                            selected = selectedCasaNum == casa,
                            onClick = { selectedCasaNum = casa },
                            label = { Text("Casa ${String.format("%02d", casa)}") }
                        )
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("🪪 FICHA 360 · CASA ${String.format("%02d", selectedCasaNum)}", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Cond. Paraíso", color = CyanNeon, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Text("👥 RESIDENTES REGISTRADOS:", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        contactos.forEach { c ->
                            Surface(color = NavySurface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(c.nombre, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("📞 ${c.tel}", color = CyanNeon, fontSize = 11.sp)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        IconButton(onClick = {
                                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.tel}"))
                                            context.startActivity(intent)
                                        }) {
                                            Icon(Icons.Default.Phone, contentDescription = "Llamar", tint = SuccessGreen)
                                        }
                                        if (selectedCasaNum != 22) {
                                            IconButton(onClick = {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/52${c.tel}"))
                                                context.startActivity(intent)
                                            }) {
                                                Icon(Icons.Default.Chat, contentDescription = "WhatsApp", tint = GoldPrimary)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Text("🧹 PERSONAL DOMÉSTICO / RECURRENTE AUTORIZADO:", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (personal.isEmpty()) {
                            Text("Sin personal doméstico registrado para esta casa.", color = Color.Gray, fontSize = 11.sp)
                        } else {
                            personal.forEach { p ->
                                Surface(color = NavySurface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(p.nombre, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Text(p.tipo, color = GoldPrimary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        Text("🚗 VEHÍCULOS EMPADRONADOS:", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (vehiculosCasa.isEmpty()) {
                            Text("No hay vehículos asignados en la base de datos.", color = Color.Gray, fontSize = 11.sp)
                        } else {
                            vehiculosCasa.forEach { v ->
                                Surface(color = NavySurface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${v.plate} • ${v.brand} ${v.model}", color = CyanNeon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(v.color, color = Color.White, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // Vista para Los Prados
        val calles = MultiCondoDirectoryHub.callesPrados[condo] ?: emptyList()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🪪 PADRÓN Y CALLES · ${condo.displayName.uppercase()}", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        calles.forEach { calle ->
                            Surface(color = NavySurface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(calle.nombre, color = CyanNeon, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Modelo arquitectónico: ${calle.tipo}", color = Color.White, fontSize = 11.sp)
                                    Text("Total de viviendas: ${calle.casasCount} casas numeradas", color = TextMuted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("🚗 VEHÍCULOS ASIGNADOS A ${condo.displayName.uppercase()} (${vehicles.size})", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            items(vehicles) { veh ->
                Surface(color = NavySurface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${veh.unitId} • ${veh.plate}", color = CyanNeon, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("${veh.brand} (${veh.color})", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 6. INCIDENTES AISLADOS POR CONDOMINIO
// =========================================================================
@Composable
fun CasetaIncidentesIsolatedSection(
    condo: CondoTarget,
    db: AppDatabase,
    incidentDao: com.example.data.incident.IncidentDao,
    incidents: List<IncidentEntity>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showReportDialog by remember { mutableStateOf(false) }
    var locationInput by remember(condo) { mutableStateOf(if (condo == CondoTarget.PARAISO) "Caseta Cond. Paraíso" else "${condo.displayName} - Entrada") }
    var detailsInput by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf(IncidentPriority.ALTA) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Button(
                onClick = { showReportDialog = !showReportDialog },
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("🚨 Levantar Incidencia en ${condo.displayName}", fontWeight = FontWeight.Bold)
            }
        }

        if (showReportDialog) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ErrorRed)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🚨 NUEVO REPORTE · ${condo.displayName.uppercase()}", color = ErrorRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                        OutlinedTextField(
                            value = locationInput,
                            onValueChange = { locationInput = it },
                            label = { Text("Ubicación exacta en ${condo.displayName}") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = detailsInput,
                            onValueChange = { detailsInput = it },
                            label = { Text("Descripción de los hechos y novedades") },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Button(
                            onClick = {
                                if (detailsInput.isNotBlank()) {
                                    val folio = AlphaCoreEngine.generateUniqueFolio("INC")
                                    scope.launch {
                                        incidentDao.insertIncident(
                                            IncidentEntity(
                                                folio = folio,
                                                rawTranscript = detailsInput.trim(),
                                                category = IncidentCategory.SEGURIDAD_EMERGENCIA,
                                                priority = selectedPriority,
                                                location = locationInput.trim(),
                                                aiSummary = detailsInput.trim(),
                                                recommendedAction = "Verificación inmediata de seguridad en ${condo.displayName}",
                                                reportedBy = "Guardia ${condo.displayName}",
                                                reportedByRole = "GUARDIA",
                                                status = "REGISTRADO"
                                            )
                                        )
                                        showReportDialog = false
                                        detailsInput = ""
                                        Toast.makeText(context, "🚨 Incidencia registrada en ${condo.displayName} (Folio $folio)", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Confirmar y Transmitir Alerta", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        item {
            Text("🚨 INCIDENCIAS ACTIVAS EN ${condo.displayName.uppercase()} (${incidents.size})", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        if (incidents.isEmpty()) {
            item {
                Surface(color = NavySurface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("Sin incidencias abiertas en ${condo.displayName}.", color = SuccessGreen, fontSize = 11.sp, modifier = Modifier.padding(10.dp))
                }
            }
        }

        items(incidents) { inc ->
            Card(
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, if (inc.status == "REGISTRADO" || inc.status == "EN_ATENCION") ErrorRed else SuccessGreen)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(inc.location, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(inc.folio, color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(inc.rawTranscript, color = Color.LightGray, fontSize = 11.sp)
                    Text("Estatus: ${inc.status} • Prioridad: ${inc.priority}", color = TextMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

// =========================================================================
// 7. RONDINES GPS AISLADOS POR CONDOMINIO
// =========================================================================
@Composable
fun CasetaRondinesIsolatedSection(
    condo: CondoTarget,
    db: AppDatabase,
    auditDao: com.example.data.supervision.SupervisionAuditDao
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val checkpoints = remember(condo) {
        when (condo) {
            CondoTarget.PARAISO -> MultiCondoDirectoryHub.checkpointsParaiso
            CondoTarget.PRADOS_1 -> MultiCondoDirectoryHub.checkpointsPrados1
            CondoTarget.PRADOS_2 -> MultiCondoDirectoryHub.checkpointsPrados2
            CondoTarget.PRADOS_3 -> MultiCondoDirectoryHub.checkpointsPrados3
        }
    }

    var checkedPoints by remember(condo) { mutableStateOf(setOf<String>()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.35f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🚶 CHECKPOINTS DE RONDÍN · ${condo.displayName.uppercase()}", color = CyanNeon, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Progreso: ${checkedPoints.size} de ${checkpoints.size} puntos verificados", color = GoldPrimary, fontSize = 11.sp)

                    LinearProgressIndicator(
                        progress = { if (checkpoints.isNotEmpty()) checkedPoints.size.toFloat() / checkpoints.size.toFloat() else 0f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = CyanNeon,
                        trackColor = NavyDark
                    )

                    checkpoints.forEach { cp ->
                        val isDone = checkedPoints.contains(cp)
                        Surface(
                            onClick = {
                                checkedPoints = if (isDone) checkedPoints - cp else checkedPoints + cp
                            },
                            color = if (isDone) SuccessGreen.copy(alpha = 0.15f) else NavySurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isDone) SuccessGreen else Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cp, color = if (isDone) SuccessGreen else Color.White, fontSize = 12.sp, fontWeight = if (isDone) FontWeight.Bold else FontWeight.Normal)
                                Icon(
                                    imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isDone) SuccessGreen else Color.Gray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (checkedPoints.size == checkpoints.size) {
                                val folio = AlphaCoreEngine.generateUniqueFolio("RND")
                                scope.launch {
                                    auditDao.insertAudit(
                                        SupervisionAuditEntity(
                                            folio = folio,
                                            supervisorName = "Guardia ${condo.shortTag}",
                                            checkpointName = "Todos los puntos (${checkpoints.size})",
                                            areaName = condo.displayName,
                                            statusCondition = "OPTIMO",
                                            findingsDescription = "Rondín 100% completado en ${condo.displayName}",
                                            riskLevel = "BAJO",
                                            correctiveActionRequired = "Ninguna",
                                            responsibleParty = "Seguridad ${condo.shortTag}",
                                            commitmentDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
                                            isClosed = true
                                        )
                                    )
                                    checkedPoints = emptySet()
                                    Toast.makeText(context, "✅ Rondín completado y registrado para ${condo.displayName}", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "Faltan checkpoints por marcar", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon, contentColor = NavyDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Finalizar y Registrar Rondín GPS", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// =========================================================================
// 8. ENTREGA DE TURNO / RELEVO AISLADO POR CONDOMINIO
// =========================================================================
@Composable
fun CasetaEntregaTurnoIsolatedSection(
    condo: CondoTarget,
    db: AppDatabase,
    supervisionDao: com.example.data.supervision.SupervisionAuditDao,
    handoverLogs: List<SupervisionAuditEntity>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var guardiaSaliente by remember { mutableStateOf("") }
    var guardiaEntrante by remember { mutableStateOf("") }
    var novedadesCaseta by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🔄 RELEVO DE CASETA · ${condo.displayName.uppercase()}", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    OutlinedTextField(
                        value = guardiaSaliente,
                        onValueChange = { guardiaSaliente = it },
                        label = { Text("Guardia Saliente") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = guardiaEntrante,
                        onValueChange = { guardiaEntrante = it },
                        label = { Text("Guardia Entrante") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = novedadesCaseta,
                        onValueChange = { novedadesCaseta = it },
                        label = { Text("Novedades y equipo de caseta (radios, llaves, bitácora)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (guardiaSaliente.isNotBlank() && guardiaEntrante.isNotBlank()) {
                                val folio = AlphaCoreEngine.generateUniqueFolio("REL")
                                scope.launch {
                                    supervisionDao.insertAudit(
                                        SupervisionAuditEntity(
                                            folio = folio,
                                            supervisorName = "$guardiaSaliente -> $guardiaEntrante",
                                            checkpointName = "Caseta de Vigilancia",
                                            areaName = condo.displayName,
                                            statusCondition = "NOVEDAD",
                                            findingsDescription = novedadesCaseta.ifBlank { "Relevo sin novedades" },
                                            riskLevel = "BAJO",
                                            correctiveActionRequired = "Entrega formal de turno",
                                            responsibleParty = guardiaEntrante,
                                            commitmentDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()),
                                            isClosed = true
                                        )
                                    )
                                    guardiaSaliente = ""
                                    guardiaEntrante = ""
                                    novedadesCaseta = ""
                                    Toast.makeText(context, "✅ Relevo firmado en ${condo.displayName}", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Ingrese los nombres de ambos guardias", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary, contentColor = NavyDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Firmar Entrega de Turno en ${condo.displayName}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Text("📜 HISTORIAL DE RELEVOS EN ${condo.displayName.uppercase()}", color = TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        items(handoverLogs.filter { it.areaName.contains(condo.shortTag, ignoreCase = true) || it.areaName.contains(condo.displayName, ignoreCase = true) }) { log ->
            Surface(color = NavySurface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(log.supervisorName, color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(log.findingsDescription, color = Color.White, fontSize = 11.sp)
                    Text("Folio: ${log.folio} • ${log.formattedTime}", color = TextMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

// =========================================================================
// 9. CONSIGNAS Y REGLAS AISLADAS POR CONDOMINIO
// =========================================================================
@Composable
fun CasetaConsignasIsolatedSection(condo: CondoTarget) {
    val consignas = remember(condo) {
        if (condo == CondoTarget.PARAISO) MultiCondoDirectoryHub.consignasParaiso
        else MultiCondoDirectoryHub.consignasPrados
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📋 CONSIGNAS OFICIALES · ${condo.displayName.uppercase()}", color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    consignas.forEach { c ->
                        Surface(color = NavySurface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(c.hora, color = WarningOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("—", color = Color.Gray, fontSize = 12.sp)
                                Text(c.texto, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        if (condo == CondoTarget.PARAISO) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = NavyCard),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🚛 PROVEEDORES FIJOS · COND. PARAÍSO", color = CyanNeon, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        MultiCondoDirectoryHub.proveedoresParaiso.forEach { p ->
                            Surface(color = NavySurface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text(p.nombre, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(p.servicio, color = TextMuted, fontSize = 11.sp)
                                    }
                                    Surface(color = GoldPrimary.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                                        Text(p.dias, color = GoldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// 10. ASISTENCIA DEL PERSONAL AISLADA POR CONDOMINIO
// =========================================================================
@Composable
fun CasetaAsistenciaIsolatedSection(condo: CondoTarget, db: AppDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var nombreElemento by remember { mutableStateOf("Seguridad ${condo.shortTag}") }
    var tipoMarcaje by remember { mutableStateOf("ENTRADA") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = NavyCard),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CyanNeon.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("📝 ASISTENCIA Y CONTROL GPS · ${condo.displayName.uppercase()}", color = CyanNeon, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    OutlinedTextField(
                        value = nombreElemento,
                        onValueChange = { nombreElemento = it },
                        label = { Text("Nombre del Elemento") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = tipoMarcaje == "ENTRADA",
                            onClick = { tipoMarcaje = "ENTRADA" },
                            label = { Text("🟢 Entrada") }
                        )
                        FilterChip(
                            selected = tipoMarcaje == "COMIDA_OUT",
                            onClick = { tipoMarcaje = "COMIDA_OUT" },
                            label = { Text("🍽️ Salida Comer") }
                        )
                        FilterChip(
                            selected = tipoMarcaje == "COMIDA_IN",
                            onClick = { tipoMarcaje = "COMIDA_IN" },
                            label = { Text("🍽️ Regreso Comer") }
                        )
                        FilterChip(
                            selected = tipoMarcaje == "SALIDA",
                            onClick = { tipoMarcaje = "SALIDA" },
                            label = { Text("🔴 Salida") }
                        )
                    }

                    Button(
                        onClick = {
                            val folio = AlphaCoreEngine.generateUniqueFolio("AST")
                            scope.launch {
                                db.auditLogDao().insertAuditLog(
                                    AuditLogEntity(
                                        folio = folio,
                                        operatorName = nombreElemento,
                                        actionType = "ASISTENCIA_$tipoMarcaje",
                                        location = "Caseta ${condo.displayName}",
                                        targetEntity = nombreElemento,
                                        changeDetails = "Marcaje de $tipoMarcaje registrado con éxito",
                                        resultStatus = "EXITOSO"
                                    )
                                )
                                Toast.makeText(context, "✅ Asistencia ($tipoMarcaje) registrada en ${condo.displayName}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = NavyDark),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Registrar Marcaje en ${condo.displayName}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
