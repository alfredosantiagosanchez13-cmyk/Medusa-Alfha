package com.example.medusaalfha.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medusaalfha.data.model.VisitorEntry
import com.example.medusaalfha.data.model.VisitorStatus
import com.example.medusaalfha.data.repository.VisitorRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

enum class VisitorFilterTab(val title: String) {
    ALL("Todos"),
    INSIDE("En Sitio"),
    AUTHORIZED("Autorizados"),
    CHECKED_OUT("Salidas")
}

data class VisitorUiState(
    val entries: List<VisitorEntry> = emptyList(),
    val filteredEntries: List<VisitorEntry> = emptyList(),
    val currentFilter: VisitorFilterTab = VisitorFilterTab.ALL,
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val selectedEntry: VisitorEntry? = null,
    val isAddDialogOpen: Boolean = false,
    val isAlertsSheetOpen: Boolean = false,
    val isScannerOpen: Boolean = false,
    val scannedPass: com.example.medusaalfha.data.scanner.ParsedVisitorPass? = null,
    val scanSuccessMessage: String? = null,
    val totalInsideCount: Int = 0,
    val totalTodayCount: Int = 0,
    val totalAuthorizedCount: Int = 0
)

class VisitorViewModel(
    private val repository: VisitorRepository = VisitorRepository(),
    private val alertRepository: com.example.medusaalfha.data.repository.ResidentAlertRepository = com.example.medusaalfha.data.repository.ResidentAlertRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(VisitorUiState())
    val uiState: StateFlow<VisitorUiState> = _uiState.asStateFlow()

    init {
        listenToRecentVisitorEntries()
    }

    private fun listenToRecentVisitorEntries() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getVisitorEntriesFlow()
                .catch { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
                .collect { list ->
                    val inside = list.count { it.status == VisitorStatus.DENTRO }
                    val auth = list.count { it.status == VisitorStatus.AUTORIZADO }
                    _uiState.value = _uiState.value.copy(
                        entries = list,
                        isLoading = false,
                        totalInsideCount = inside,
                        totalTodayCount = list.size,
                        totalAuthorizedCount = auth
                    )
                    applyFilters()
                }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilters()
    }

    fun onFilterTabSelected(tab: VisitorFilterTab) {
        _uiState.value = _uiState.value.copy(currentFilter = tab)
        applyFilters()
    }

    fun onSelectEntryForDetail(entry: VisitorEntry?) {
        _uiState.value = _uiState.value.copy(selectedEntry = entry)
    }

    fun setAddDialogOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(isAddDialogOpen = open)
    }

    fun setAlertsSheetOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(isAlertsSheetOpen = open)
    }

    fun registerQuickVisitor(
        visitorName: String,
        destinationHouse: String,
        vehiclePlate: String,
        notes: String
    ) {
        val newEntry = VisitorEntry(
            folio = "FOL-${System.currentTimeMillis() % 100000}",
            visitorName = visitorName.trim(),
            destinationHouse = destinationHouse.trim(),
            vehiclePlate = vehiclePlate.trim().ifEmpty { null },
            notes = notes.trim().ifEmpty { null },
            status = VisitorStatus.DENTRO,
            checkInTimestamp = System.currentTimeMillis()
        )
        viewModelScope.launch {
            repository.registerVisitorEntry(newEntry)
            setAddDialogOpen(false)
        }
    }

    fun markVisitorCheckOut(folio: String) {
        viewModelScope.launch {
            repository.markVisitorCheckOut(folio)
            if (_uiState.value.selectedEntry?.folio == folio) {
                _uiState.value = _uiState.value.copy(selectedEntry = null)
            }
        }
    }

    fun setScannerOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(
            isScannerOpen = open,
            scannedPass = if (!open) null else _uiState.value.scannedPass
        )
    }

    fun onQrCodeScanned(qrRawText: String) {
        val parsed = com.example.medusaalfha.data.scanner.ParsedVisitorPass.parse(qrRawText)
        _uiState.value = _uiState.value.copy(
            scannedPass = parsed
        )
    }

    fun confirmScannedPass(context: android.content.Context) {
        val pass = _uiState.value.scannedPass ?: return
        val entry = pass.toVisitorEntry()
        viewModelScope.launch {
            repository.registerVisitorEntry(entry)
            try {
                alertRepository.dispatchVisitorArrivalAlert(
                    context = context,
                    visitorName = entry.visitorName,
                    destinationHouse = entry.destinationHouse,
                    folio = entry.folio,
                    vehiclePlate = entry.vehiclePlate
                )
            } catch (_: Exception) {}

            _uiState.value = _uiState.value.copy(
                isScannerOpen = false,
                scannedPass = null,
                scanSuccessMessage = "¡Acceso concedido para ${entry.visitorName} (${entry.destinationHouse})!"
            )
        }
    }

    fun dismissScannedPass() {
        _uiState.value = _uiState.value.copy(scannedPass = null)
    }

    fun clearScanSuccessMessage() {
        _uiState.value = _uiState.value.copy(scanSuccessMessage = null)
    }

    fun seedSampleDataToFirestore() {
        viewModelScope.launch {
            repository.seedInitialDataIfEmpty()
        }
    }

    private fun applyFilters() {
        val current = _uiState.value
        val query = current.searchQuery.trim().lowercase()

        var result = current.entries

        if (query.isNotEmpty()) {
            result = result.filter {
                it.visitorName.lowercase().contains(query) ||
                it.destinationHouse.lowercase().contains(query) ||
                (it.vehiclePlate?.lowercase()?.contains(query) == true) ||
                it.folio.lowercase().contains(query)
            }
        }

        result = when (current.currentFilter) {
            VisitorFilterTab.ALL -> result
            VisitorFilterTab.INSIDE -> result.filter { it.status == VisitorStatus.DENTRO }
            VisitorFilterTab.AUTHORIZED -> result.filter { it.status == VisitorStatus.AUTORIZADO }
            VisitorFilterTab.CHECKED_OUT -> result.filter { it.status == VisitorStatus.SALIDA }
        }

        _uiState.value = current.copy(filteredEntries = result)
    }
}
