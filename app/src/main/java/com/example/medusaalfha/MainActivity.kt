package com.example.medusaalfha

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
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
import com.example.medusaalfha.ui.components.AmenityBookingCalendarHub
import com.example.medusaalfha.ui.components.RecentVisitorEntriesList
import com.example.medusaalfha.ui.components.ResidentAlertsNotificationSheet
import com.example.medusaalfha.ui.theme.*
import com.example.medusaalfha.ui.viewmodel.AmenityBookingViewModel
import com.example.medusaalfha.ui.viewmodel.VisitorViewModel

class MainActivity : ComponentActivity() {

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
    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    if (currentUser == null) {
        com.example.medusaalfha.ui.components.AuthScreen(
            onAuthSuccess = { currentUser = auth.currentUser }
        )
    } else {
        MainAppContainer(
            visitorViewModel = visitorViewModel,
            bookingViewModel = bookingViewModel,
            guardStationViewModel = guardStationViewModel,
            onSignOut = { currentUser = null }
        )
    }
}

enum class MedusaScreen(val title: String) {
    GUARD_PANEL("Panel de Caseta"),
    VISITORS("Bitácora de Visitas"),
    BOOKINGS("Áreas Comunes")
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
    var currentScreen by remember { mutableStateOf(MedusaScreen.GUARD_PANEL) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { androidx.credentials.CredentialManager.create(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "MEDUSA ALFHA",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = GoldMetallic
                        )
                        Text(
                            text = "Control de Accesos & Ciberseguridad · Caseta",
                            fontSize = 11.sp,
                            color = NeonBlue
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { visitorViewModel.setAlertsSheetOpen(true) }) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Alertas y Notificaciones",
                            tint = GoldMetallic
                        )
                    }
                    IconButton(onClick = {
                        com.example.medusaalfha.ui.components.performSignOut(
                            context = context,
                            credentialManager = credentialManager,
                            onSignOutComplete = onSignOut,
                            scope = coroutineScope
                        )
                    }) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Cerrar Sesión",
                            tint = AlertRed
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberSurface)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = CyberSurface) {
                NavigationBarItem(
                    selected = currentScreen == MedusaScreen.GUARD_PANEL,
                    onClick = { currentScreen = MedusaScreen.GUARD_PANEL },
                    icon = { Icon(Icons.Default.Security, contentDescription = "Panel de Caseta") },
                    label = { Text("Panel Caseta") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GoldMetallic,
                        selectedTextColor = GoldMetallic,
                        unselectedIconColor = CyberTextMuted,
                        unselectedTextColor = CyberTextMuted,
                        indicatorColor = NeonBlue.copy(alpha = 0.2f)
                    )
                )
                NavigationBarItem(
                    selected = currentScreen == MedusaScreen.VISITORS,
                    onClick = { currentScreen = MedusaScreen.VISITORS },
                    icon = { Icon(Icons.Default.Badge, contentDescription = "Bitácora") },
                    label = { Text("Bitácora") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonBlue,
                        selectedTextColor = NeonBlue,
                        unselectedIconColor = CyberTextMuted,
                        unselectedTextColor = CyberTextMuted,
                        indicatorColor = NeonBlue.copy(alpha = 0.2f)
                    )
                )
                NavigationBarItem(
                    selected = currentScreen == MedusaScreen.BOOKINGS,
                    onClick = { currentScreen = MedusaScreen.BOOKINGS },
                    icon = { Icon(Icons.Default.Event, contentDescription = "Áreas Comunes") },
                    label = { Text("Reservas") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NeonBlue,
                        selectedTextColor = NeonBlue,
                        unselectedIconColor = CyberTextMuted,
                        unselectedTextColor = CyberTextMuted,
                        indicatorColor = NeonBlue.copy(alpha = 0.2f)
                    )
                )
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

                    FloatingActionButton(
                        onClick = { visitorViewModel.setAddDialogOpen(true) },
                        containerColor = NavyPrimary,
                        contentColor = Color.White,
                        modifier = Modifier.testTag("manual_add_fab")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Registro Manual")
                    }
                }
            }
        }
    ) { innerPadding ->
        val context = androidx.compose.ui.platform.LocalContext.current
        val snackbarHostState = remember { SnackbarHostState() }

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
                        onDismissAddDialog = { visitorViewModel.setAddDialogOpen(false) }
                    )
                }
                MedusaScreen.BOOKINGS -> {
                    AmenityBookingCalendarHub(
                        bookingViewModel = bookingViewModel
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
                    onConfirmPass = { visitorViewModel.confirmScannedPass(context) },
                    onDismissPass = { visitorViewModel.dismissScannedPass() }
                )
            }
        }
    }
}
