package com.example.medusaalfha

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medusaalfha.data.repository.ResidentAlertRepository
import com.example.medusaalfha.ui.components.AmenityBookingCalendarHub
import com.example.medusaalfha.ui.components.RecentVisitorEntriesList
import com.example.medusaalfha.ui.components.ResidentAlertsNotificationSheet
import com.example.medusaalfha.ui.theme.CyanNeon
import com.example.medusaalfha.ui.theme.GoldLight
import com.example.medusaalfha.ui.theme.GoldPrimary
import com.example.medusaalfha.ui.theme.MEDUSAALFHATheme
import com.example.medusaalfha.ui.theme.NavyBorder
import com.example.medusaalfha.ui.theme.NavyDark
import com.example.medusaalfha.ui.theme.NavySurface
import com.example.medusaalfha.ui.theme.TextMuted
import com.example.medusaalfha.ui.theme.TextWhite
import com.example.medusaalfha.ui.viewmodel.AmenityBookingViewModel
import com.example.medusaalfha.ui.viewmodel.VisitorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MEDUSAALFHATheme {
                MainAppContainer()
            }
        }
    }
}

@Composable
fun MainAppContainer() {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var isAlertSheetOpen by remember { mutableStateOf(false) }

    val visitorViewModel: VisitorViewModel = viewModel()
    val visitorUiState by visitorViewModel.uiState.collectAsState()

    val bookingViewModel: AmenityBookingViewModel = viewModel()
    val bookingUiState by bookingViewModel.uiState.collectAsState()
    val formState by bookingViewModel.formState.collectAsState()

    val alertRepository = remember { ResidentAlertRepository() }
    val alertsList by alertRepository.getAlertsFlow().collectAsState(initial = emptyList())
    val unreadCount = alertsList.count { !it.isRead }

    // Solicitud de Permiso de Notificaciones en Android 13+ (Tiramisu)
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionStatus = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    Scaffold(
        containerColor = NavyDark,
        topBar = {
            Surface(
                color = NavySurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, NavyBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(GoldPrimary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "MEDUSA ALFHA",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = TextWhite
                            )
                            Text(
                                text = "Los Prados Residencial · Garita & Residentes",
                                fontSize = 9.sp,
                                color = TextMuted
                            )
                        }
                    }

                    // Botón con Insignia de Notificaciones FCM
                    IconButton(
                        onClick = { isAlertSheetOpen = true },
                        modifier = Modifier.testTag("notification_bell_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge(
                                        containerColor = GoldPrimary,
                                        contentColor = NavyDark
                                    ) {
                                        Text("$unreadCount", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Alertas en Tiempo Real",
                                tint = if (unreadCount > 0) GoldPrimary else TextMuted,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = NavySurface,
                tonalElevation = 8.dp,
                modifier = Modifier.testTag("main_bottom_nav")
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Bitácora"
                        )
                    },
                    label = {
                        Text(
                            text = "Bitácora Garita",
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NavyDark,
                        selectedTextColor = GoldPrimary,
                        indicatorColor = GoldPrimary,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_item_visitors")
                )

                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "Áreas Comunes"
                        )
                    },
                    label = {
                        Text(
                            text = "Áreas Comunes",
                            fontSize = 11.sp,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = NavyDark,
                        selectedTextColor = GoldPrimary,
                        indicatorColor = GoldPrimary,
                        unselectedIconColor = TextMuted,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_item_amenities")
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = NavyDark
        ) {
            when (selectedTab) {
                0 -> {
                    RecentVisitorEntriesList(
                        uiState = visitorUiState,
                        onSearchChange = visitorViewModel::onSearchQueryChanged,
                        onFilterSelect = visitorViewModel::onFilterTabSelected,
                        onSelectEntry = visitorViewModel::onSelectVisitorEntry,
                        onDismissDetail = visitorViewModel::onDismissDetailSheet,
                        onMarkCheckOut = visitorViewModel::markVisitorCheckOut,
                        onSeedData = visitorViewModel::seedSampleDataToFirestore,
                        onQuickAddVisitor = visitorViewModel::registerQuickVisitor,
                        onClearNotice = visitorViewModel::clearUserNotice
                    )
                }
                1 -> {
                    AmenityBookingCalendarHub(
                        uiState = bookingUiState,
                        formState = formState,
                        onSelectDate = bookingViewModel::onSelectDate,
                        onFilterAmenity = bookingViewModel::onFilterAmenity,
                        onOpenAddDialog = bookingViewModel::onOpenAddDialog,
                        onDismissAddDialog = bookingViewModel::onDismissAddDialog,
                        onOpenDetailBooking = bookingViewModel::onOpenDetailBooking,
                        onDismissDetailBooking = bookingViewModel::onDismissDetailBooking,
                        onFormAmenityChanged = bookingViewModel::onFormAmenityChanged,
                        onFormResidentNameChanged = bookingViewModel::onFormResidentNameChanged,
                        onFormResidentHouseChanged = bookingViewModel::onFormResidentHouseChanged,
                        onFormStartHourChanged = bookingViewModel::onFormStartHourChanged,
                        onFormEndHourChanged = bookingViewModel::onFormEndHourChanged,
                        onFormGuestCountChanged = bookingViewModel::onFormGuestCountChanged,
                        onFormNotesChanged = bookingViewModel::onFormNotesChanged,
                        onSubmitBooking = bookingViewModel::submitBooking,
                        onCancelBooking = bookingViewModel::cancelBooking,
                        onSeedData = bookingViewModel::seedSampleBookings,
                        onClearNotice = bookingViewModel::clearNotice
                    )
                }
            }
        }

        // Hoja de Alertas del Residente (FCM)
        if (isAlertSheetOpen) {
            ResidentAlertsNotificationSheet(
                alerts = alertsList,
                onDismiss = { isAlertSheetOpen = false }
            )
        }
    }
}
