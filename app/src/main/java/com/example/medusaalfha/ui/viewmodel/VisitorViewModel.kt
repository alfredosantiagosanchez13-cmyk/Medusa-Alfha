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
    CHECKED_OUT("Salidas"),
    PACKAGES("Paquetería")
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
    val isPassAlreadyInside: Boolean = false,
    val isConfirmingPass: Boolean = false,
    val scanSuccessMessage: String? = null,
    val totalInsideCount: Int = 0,
    val totalTodayCount: Int = 0,
    val totalAuthorizedCount: Int = 0,
    val totalCheckedOutCount: Int = 0,
    val isRefreshing: Boolean = false,
    val refreshFeedbackMessage: String? = null
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
                    val salio = list.count { it.status == VisitorStatus.SALIDA }
                    _uiState.value = _uiState.value.copy(
                        entries = list,
                        isLoading = false,
                        totalInsideCount = inside,
                        totalTodayCount = list.size,
                        totalAuthorizedCount = auth,
                        totalCheckedOutCount = salio
                    )
                    applyFilters()
                }
        }
    }

    /**
     * Gatillo manual de actualización (Pull-To-Refresh) desde Cloud Firestore.
     */
    fun refreshVisitorEntries() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, refreshFeedbackMessage = null)
            val result = repository.refreshVisitorEntries()
            result.fold(
                onSuccess = { list ->
                    val inside = list.count { it.status == VisitorStatus.DENTRO }
                    val auth = list.count { it.status == VisitorStatus.AUTORIZADO }
                    val salio = list.count { it.status == VisitorStatus.SALIDA }
                    _uiState.value = _uiState.value.copy(
                        entries = list,
                        isRefreshing = false,
                        totalInsideCount = inside,
                        totalTodayCount = list.size,
                        totalAuthorizedCount = auth,
                        totalCheckedOutCount = salio,
                        refreshFeedbackMessage = "Bitácora actualizada en vivo desde Cloud Firestore"
                    )
                    applyFilters()
                },
                onFailure = { e ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        refreshFeedbackMessage = "Actualizado con caché local (${e.localizedMessage ?: "Sin cambios"})"
                    )
                }
            )
        }
    }

    fun clearRefreshFeedback() {
        _uiState.value = _uiState.value.copy(refreshFeedbackMessage = null)
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
        viewModelScope.launch {
            val status = repository.checkVisitorCurrentStatus(parsed.folio)
            val isInside = status == com.example.medusaalfha.data.model.VisitorStatus.DENTRO
            _uiState.value = _uiState.value.copy(
                scannedPass = parsed,
                isPassAlreadyInside = isInside
            )
        }
    }

    fun confirmScannedPass(context: android.content.Context) {
        val pass = _uiState.value.scannedPass ?: return
        val isInside = _uiState.value.isPassAlreadyInside

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isConfirmingPass = true)

            if (isInside) {
                // Registrar Salida del visitante en Firestore
                repository.markVisitorCheckOut(pass.folio)
                try {
                    alertRepository.dispatchVisitorArrivalAlert(
                        context = context,
                        visitorName = pass.visitorName,
                        destinationHouse = pass.destinationHouse,
                        folio = pass.folio,
                        vehiclePlate = pass.vehiclePlate
                    )
                } catch (_: Exception) {}

                _uiState.value = _uiState.value.copy(
                    isScannerOpen = false,
                    scannedPass = null,
                    isPassAlreadyInside = false,
                    isConfirmingPass = false,
                    scanSuccessMessage = "¡Salida registrada para ${pass.visitorName} (${pass.destinationHouse})!"
                )
            } else {
                // Registrar Ingreso del visitante en Firestore
                val entry = pass.toVisitorEntry(com.example.medusaalfha.data.model.VisitorStatus.DENTRO)
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
                    isPassAlreadyInside = false,
                    isConfirmingPass = false,
                    scanSuccessMessage = "¡Ingreso confirmado para ${entry.visitorName} (${entry.destinationHouse})!"
                )
            }
        }
    }

    fun dismissScannedPass() {
        _uiState.value = _uiState.value.copy(scannedPass = null, isPassAlreadyInside = false)
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
            VisitorFilterTab.CHECKED_OUT -> result.filter { it.status == VisitorStatus.SALIDA }
            VisitorFilterTab.PACKAGES -> result.filter {
                it.visitorType.contains("paquet", ignoreCase = true) ||
                it.visitorType.contains("entrega", ignoreCase = true) ||
                it.visitorName.contains("amazon", ignoreCase = true) ||
                it.visitorName.contains("mercado", ignoreCase = true) ||
                it.visitorName.contains("logistics", ignoreCase = true) ||
                it.visitorName.contains("dhl", ignoreCase = true) ||
                it.visitorName.contains("fedex", ignoreCase = true) ||
                (it.notes?.contains("paquet", ignoreCase = true) == true)
            }
        }

        _uiState.value = current.copy(filteredEntries = result)
    }
}
