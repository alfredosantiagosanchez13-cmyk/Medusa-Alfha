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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

enum class VisitorFilterTab(val label: String) {
    TODOS("Todos"),
    DENTRO("En Sitio"),
    SALIDA("Salidas"),
    PAQUETERIA("Paquetería")
}

data class VisitorUiState(
    val isLoading: Boolean = true,
    val allEntries: List<VisitorEntry> = emptyList(),
    val filteredEntries: List<VisitorEntry> = emptyList(),
    val searchQuery: String = "",
    val activeFilter: VisitorFilterTab = VisitorFilterTab.TODOS,
    val selectedEntry: VisitorEntry? = null,
    val isSeeding: Boolean = false,
    val userNotice: String? = null,
    val errorMessage: String? = null
)

class VisitorViewModel(
    private val repository: VisitorRepository = VisitorRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(VisitorUiState())
    val uiState: StateFlow<VisitorUiState> = _uiState.asStateFlow()

    init {
        listenToRecentVisitorEntries()
    }

    private fun listenToRecentVisitorEntries() {
        _uiState.value = _uiState.value.copy(isLoading = true)

        repository.getRecentVisitorEntries()
            .onEach { list ->
                val entries = if (list.isEmpty()) repository.getFallbackSampleEntries() else list
                val filtered = applyFilters(entries, _uiState.value.searchQuery, _uiState.value.activeFilter)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    allEntries = entries,
                    filteredEntries = filtered,
                    errorMessage = null
                )
            }
            .catch { e ->
                val fallback = repository.getFallbackSampleEntries()
                val filtered = applyFilters(fallback, _uiState.value.searchQuery, _uiState.value.activeFilter)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    allEntries = fallback,
                    filteredEntries = filtered,
                    errorMessage = "Conexión a Firestore en modo contingencia: ${e.message}"
                )
            }
            .launchIn(viewModelScope)
    }

    fun onSearchQueryChanged(query: String) {
        val filtered = applyFilters(_uiState.value.allEntries, query, _uiState.value.activeFilter)
        _uiState.value = _uiState.value.copy(
            searchQuery = query,
            filteredEntries = filtered
        )
    }

    fun onFilterTabSelected(tab: VisitorFilterTab) {
        val filtered = applyFilters(_uiState.value.allEntries, _uiState.value.searchQuery, tab)
        _uiState.value = _uiState.value.copy(
            activeFilter = tab,
            filteredEntries = filtered
        )
    }

    fun onSelectVisitorEntry(entry: VisitorEntry) {
        _uiState.value = _uiState.value.copy(selectedEntry = entry)
    }

    fun onDismissDetailSheet() {
        _uiState.value = _uiState.value.copy(selectedEntry = null)
    }

    fun markVisitorCheckOut(folio: String) {
        viewModelScope.launch {
            val result = repository.registerVisitorCheckOut(folio)
            val updatedAll = _uiState.value.allEntries.map {
                if (it.folio == folio) it.copy(status = VisitorStatus.SALIDA, checkOutTimestamp = System.currentTimeMillis()) else it
            }
            val filtered = applyFilters(updatedAll, _uiState.value.searchQuery, _uiState.value.activeFilter)
            _uiState.value = _uiState.value.copy(
                allEntries = updatedAll,
                filteredEntries = filtered,
                selectedEntry = _uiState.value.selectedEntry?.let { if (it.folio == folio) it.copy(status = VisitorStatus.SALIDA, checkOutTimestamp = System.currentTimeMillis()) else it },
                userNotice = if (result.isSuccess) "Salida del folio $folio sincronizada en Firestore." else "Salida registrada en el dispositivo."
            )
        }
    }

    fun seedSampleDataToFirestore() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSeeding = true)
            val result = repository.seedInitialVisitorEntriesIfEmpty()
            _uiState.value = _uiState.value.copy(
                isSeeding = false,
                userNotice = if (result.isSuccess) "Registros sincronizados con Cloud Firestore." else "Catálogo activo localmente."
            )
        }
    }

    fun registerQuickVisitor(name: String, house: String, plate: String, type: String) {
        viewModelScope.launch {
            val generatedFolio = "VIS-${System.currentTimeMillis() % 100000}"
            val newEntry = VisitorEntry(
                folio = generatedFolio,
                visitorName = name.ifBlank { "Visitante Express" },
                destinationHouse = house.ifBlank { "Casa 54 · Circuito Los Álamos" },
                visitorType = type,
                checkInTimestamp = System.currentTimeMillis(),
                status = VisitorStatus.DENTRO,
                vehiclePlate = plate.ifBlank { null },
                authorizedBy = "Residente $house",
                guardName = "Oficial Juan Pérez · Garita 1",
                accessMethod = "REGISTRO MANUAL CASETA",
                notes = "Ingreso registrado desde terminal táctica"
            )
            val res = repository.registerVisitorCheckIn(newEntry)
            val updatedAll = listOf(newEntry) + _uiState.value.allEntries.filter { it.folio != newEntry.folio }
            val filtered = applyFilters(updatedAll, _uiState.value.searchQuery, _uiState.value.activeFilter)
            _uiState.value = _uiState.value.copy(
                allEntries = updatedAll,
                filteredEntries = filtered,
                userNotice = if (res.isSuccess) {
                    "Visitante ${newEntry.visitorName} registrado en Firestore (Folio $generatedFolio)."
                } else {
                    "Visitante ${newEntry.visitorName} registrado (Folio $generatedFolio)."
                }
            )
        }
    }

    fun clearUserNotice() {
        _uiState.value = _uiState.value.copy(userNotice = null)
    }

    private fun applyFilters(
        list: List<VisitorEntry>,
        query: String,
        filterTab: VisitorFilterTab
    ): List<VisitorEntry> {
        return list.filter { item ->
            val matchesTab = when (filterTab) {
                VisitorFilterTab.TODOS -> true
                VisitorFilterTab.DENTRO -> item.status == VisitorStatus.DENTRO
                VisitorFilterTab.SALIDA -> item.status == VisitorStatus.SALIDA
                VisitorFilterTab.PAQUETERIA -> item.visitorType.contains("PAQUETER", ignoreCase = true)
            }

            val matchesQuery = query.isBlank() ||
                item.visitorName.contains(query, ignoreCase = true) ||
                item.destinationHouse.contains(query, ignoreCase = true) ||
                item.folio.contains(query, ignoreCase = true) ||
                (item.vehiclePlate?.contains(query, ignoreCase = true) == true)

            matchesTab && matchesQuery
        }
    }
}
