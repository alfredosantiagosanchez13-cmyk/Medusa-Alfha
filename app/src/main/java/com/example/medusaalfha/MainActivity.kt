package com.example.medusaalfha

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.medusaalfha.ui.components.AmenityBookingCalendarHub
import com.example.medusaalfha.ui.components.RecentVisitorEntriesList
import com.example.medusaalfha.ui.components.ResidentAlertsNotificationSheet
import com.example.medusaalfha.ui.theme.*
import com.example.medusaalfha.ui.viewmodel.AmenityBookingViewModel
import com.example.medusaalfha.ui.viewmodel.VisitorViewModel

class MainActivity : androidx.fragment.app.FragmentActivity() {

    private val visitorViewModel: VisitorViewModel by viewModels()
    private val bookingViewModel: AmenityBookingViewModel by viewModels()
    private val guardStationViewModel: com.example.medusaalfha.ui.viewmodel.GuardStationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MEDUSAALFHATheme {
                AppRootContent(
                    visitorViewModel = visitorViewModel,
                    bookingViewModel = bookingViewModel,
                    guardStationViewModel = guardStationViewModel
                )
            }
        }
    }
}

@Composable
fun AppRootContent(
    visitorViewModel: VisitorViewModel,
    bookingViewModel: AmenityBookingViewModel,
    guardStationViewModel: com.example.medusaalfha.ui.viewmodel.GuardStationViewModel
) {
    // Ingreso directo e inmediato a la app real de Los Prados Residencial
    MainAppContainer(
        visitorViewModel = visitorViewModel,
        bookingViewModel = bookingViewModel,
        guardStationViewModel = guardStationViewModel
    )
}

enum class MedusaScreen(val title: String) {
    VISITORS("Bitácora Garita"),
    BOOKINGS("Áreas Comunes"),
    GUARD_PANEL("Control Caseta"),
    MASTER_PANEL("ALFHA Master")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(
    visitorViewModel: VisitorViewModel,
    bookingViewModel: AmenityBookingViewModel,
    guardStationViewModel: com.example.medusaalfha.ui.viewmodel.GuardStationViewModel,
    onSignOut: () -> Unit = {}
) {
    val visitorUiState by visitorViewModel.uiState.collectAsState()
    var currentScreen by remember { mutableStateOf(MedusaScreen.VISITORS) }
    var isMasterUnlocked by remember { mutableStateOf(false) }
    var showBiometricModal by remember { mutableStateOf(false) }

    val condominiumRepository = remember { com.example.medusaalfha.data.repository.CondominiumRepository() }
    val activeCondo by com.example.medusaalfha.data.repository.CondominiumRepository.activeCondominium.collectAsState()

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { androidx.credentials.CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(GoldMetallic.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "MEDUSA",
                                tint = GoldMetallic,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "MEDUSA ALFHA",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    ),
                                    color = Color.White
                                )
                                if (isMasterUnlocked) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = GoldMetallic.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "MASTER",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = GoldMetallic,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Los Prados Residencial · Garita & Residentes",
                                fontSize = 11.sp,
                                color = CyberTextMuted,
                                maxLines = 1
                            )
                        }
                    }
                },
                actions = {
                    // Botón de Lector de Huella ALFHA Master
                    FilledTonalButton(
                        onClick = {
                            if (isMasterUnlocked) {
                                currentScreen = MedusaScreen.MASTER_PANEL
                            } else {
                                showBiometricModal = true
                            }
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isMasterUnlocked) EmeraldGreen.copy(alpha = 0.25f) else GoldMetallic.copy(alpha = 0.2f),
                            contentColor = if (isMasterUnlocked) EmeraldGreen else GoldMetallic
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .testTag("topbar_master_biometric_button"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Lector de Huella ALFHA Master",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isMasterUnlocked) "MASTER" else "HUELLA",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }

                    // Campana de notificaciones con badge 1
                    IconButton(onClick = { visitorViewModel.setAlertsSheetOpen(true) }) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = GoldMetallic,
                                    contentColor = Color.Black
                                ) {
                                    Text("1", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Alertas y Notificaciones",
                                tint = GoldMetallic
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberSurface)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = CyberSurface) {
                NavigationBarItem(
                    selected = currentScreen == MedusaScreen.VISITORS,
                    onClick = { currentScreen = MedusaScreen.VISITORS },
                    icon = { Icon(Icons.Default.Security, contentDescription = "Bitácora Garita") },
                    label = { Text("Bitácora Garita") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GoldMetallic,
                        selectedTextColor = GoldMetallic,
                        unselectedIconColor = CyberTextMuted,
                        unselectedTextColor = CyberTextMuted,
                        indicatorColor = GoldMetallic.copy(alpha = 0.2f)
                    )
                )
                NavigationBarItem(
                    selected = currentScreen == MedusaScreen.BOOKINGS,
                    onClick = { currentScreen = MedusaScreen.BOOKINGS },
                    icon = { Icon(Icons.Default.Event, contentDescription = "Áreas Comunes") },
                    label = { Text("Áreas Comunes") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonBlue,
                        selectedTextColor = NeonBlue,
                        unselectedIconColor = CyberTextMuted,
                        unselectedTextColor = CyberTextMuted,
                        indicatorColor = NeonBlue.copy(alpha = 0.2f)
                    )
                )
                NavigationBarItem(
                    selected = currentScreen == MedusaScreen.GUARD_PANEL,
                    onClick = { currentScreen = MedusaScreen.GUARD_PANEL },
                    icon = { Icon(Icons.Default.LocalPolice, contentDescription = "Control Caseta") },
                    label = { Text("Control Caseta") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonBlue,
                        selectedTextColor = NeonBlue,
                        unselectedIconColor = CyberTextMuted,
                        unselectedTextColor = CyberTextMuted,
                        indicatorColor = NeonBlue.copy(alpha = 0.2f)
                    )
                )

                if (isMasterUnlocked) {
                    NavigationBarItem(
                        selected = currentScreen == MedusaScreen.MASTER_PANEL,
                        onClick = { currentScreen = MedusaScreen.MASTER_PANEL },
                        icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = "ALFHA Master") },
                        label = { Text("Master") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = GoldMetallic,
                            selectedTextColor = GoldMetallic,
                            unselectedIconColor = CyberTextMuted,
                            unselectedTextColor = CyberTextMuted,
                            indicatorColor = GoldMetallic.copy(alpha = 0.2f)
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentScreen == MedusaScreen.VISITORS) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExtendedFloatingActionButton(
                        onClick = { visitorViewModel.setScannerOpen(true) },
                        containerColor = TealAccent,
                        contentColor = Color.White,
                        icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear QR") },
                        text = { Text("Escanear QR", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("scan_qr_fab")
                    )

                    ExtendedFloatingActionButton(
                        onClick = { visitorViewModel.setAddDialogOpen(true) },
                        containerColor = GoldMetallic,
                        contentColor = Color.Black,
                        icon = { Icon(Icons.Default.PersonAdd, contentDescription = "Registrar Visita") },
                        text = { Text("REGISTRAR VISITA", fontWeight = FontWeight.ExtraBold) },
                        modifier = Modifier.testTag("manual_add_fab")
                    )
                }
            }
        }
    ) { innerPadding ->
        LaunchedEffect(visitorUiState.scanSuccessMessage) {
            visitorUiState.scanSuccessMessage?.let {
                snackbarHostState.showSnackbar(it)
                visitorViewModel.clearScanSuccessMessage()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                MedusaScreen.GUARD_PANEL -> {
                    com.example.medusaalfha.ui.components.GuardStationScreen(
                        viewModel = guardStationViewModel,
                        onOpenCameraScanner = { visitorViewModel.setScannerOpen(true) }
                    )
                }
                MedusaScreen.VISITORS -> {
                    RecentVisitorEntriesList(
                        uiState = visitorUiState,
                        onSearchChanged = { visitorViewModel.onSearchQueryChanged(it) },
                        onFilterSelected = { visitorViewModel.onFilterTabSelected(it) },
                        onSelectEntry = { visitorViewModel.onSelectEntryForDetail(it) },
                        onDismissDetail = { visitorViewModel.onSelectEntryForDetail(null) },
                        onCheckOut = { visitorViewModel.markVisitorCheckOut(it) },
                        onOpenAddDialog = { visitorViewModel.setAddDialogOpen(true) },
                        onOpenScanner = { visitorViewModel.setScannerOpen(true) },
                        onRegisterQuickVisitor = { name, house, plate, notes ->
                            visitorViewModel.registerQuickVisitor(name, house, plate, notes)
                        },
                        onDismissAddDialog = { visitorViewModel.setAddDialogOpen(false) },
                        onRefresh = { visitorViewModel.refreshVisitorEntries() }
                    )
                }
                MedusaScreen.BOOKINGS -> {
                    AmenityBookingCalendarHub(
                        bookingViewModel = bookingViewModel
                    )
                }
                MedusaScreen.MASTER_PANEL -> {
                    com.example.medusaalfha.ui.components.MasterDirectorGeneralScreen(
                        condominiumRepository = condominiumRepository,
                        onNavigateToGuardPanel = { currentScreen = MedusaScreen.GUARD_PANEL },
                        onNavigateToVisitors = { currentScreen = MedusaScreen.VISITORS },
                        onNavigateToBookings = { currentScreen = MedusaScreen.BOOKINGS },
                        onExitMasterMode = {
                            isMasterUnlocked = false
                            currentScreen = MedusaScreen.GUARD_PANEL
                        }
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 70.dp)
            )

            if (visitorUiState.isAlertsSheetOpen) {
                ResidentAlertsNotificationSheet(
                    onDismiss = { visitorViewModel.setAlertsSheetOpen(false) }
                )
            }

            if (visitorUiState.isScannerOpen) {
                com.example.medusaalfha.ui.components.QrScannerBottomSheet(
                    onDismiss = { visitorViewModel.setScannerOpen(false) },
                    onQrScanned = { qrResult -> visitorViewModel.onQrCodeScanned(qrResult) },
                    scannedPass = visitorUiState.scannedPass,
                    isPassAlreadyInside = visitorUiState.isPassAlreadyInside,
                    isConfirming = visitorUiState.isConfirmingPass,
                    onConfirmPass = { visitorViewModel.confirmScannedPass(context) },
                    onDismissPass = { visitorViewModel.dismissScannedPass() }
                )
            }

            if (showBiometricModal) {
                com.example.medusaalfha.ui.components.AlfhaBiometricModal(
                    onDismiss = { showBiometricModal = false },
                    onAuthenticationSuccess = { userName ->
                        showBiometricModal = false
                        isMasterUnlocked = true
                        currentScreen = MedusaScreen.MASTER_PANEL
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("¡Acceso Supremo Concedido: $userName (Director General)!")
                        }
                    }
                )
            }
        }
    }
}
