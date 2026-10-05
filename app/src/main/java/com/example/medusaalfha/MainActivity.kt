package com.example.medusaalfha

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.medusaalfha.ui.components.RecentVisitorEntriesList
import com.example.medusaalfha.ui.theme.MEDUSAALFHATheme
import com.example.medusaalfha.ui.theme.NavyDark
import com.example.medusaalfha.ui.viewmodel.VisitorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MEDUSAALFHATheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NavyDark
                ) {
                    val visitorViewModel: VisitorViewModel = viewModel()
                    val uiState by visitorViewModel.uiState.collectAsState()

                    RecentVisitorEntriesList(
                        uiState = uiState,
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
            }
        }
    }
}
