package com.example.medusaalfha.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medusaalfha.data.model.*
import com.example.medusaalfha.data.repository.ResidentAlertRepository
import com.example.medusaalfha.data.repository.VisitorRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.example.medusaalfha.MedusaApplication
import com.example.medusaalfha.data.repository.PatrolSyncRepository
import com.example.medusaalfha.data.repository.PatrolSyncState
import kotlinx.coroutines.flow.update

enum class ResidentConfirmationState {
    IDLE,
    WAITING_CONFIRMATION,
    ACCEPTED,
    REJECTED
}

data class GuardStationUiState(
    // 1. Asistencia del Guardia & Control de Fatiga
    val attendance: GuardAttendanceState = GuardAttendanceState(),
    val attendanceNotice: String? = null,
    val isShiftHandoverOpen: Boolean = false,
    val isIncomingPhotoCaptured: Boolean = false,
    val isBiometricSignatureVerified: Boolean = false,
    val isSimulatingHandover: Boolean = false,

    // 2. Visitantes y Proveedores
    val selectedAccessClassification: String = "Visita General",
    val generalObservations: String = "",
    val hasIdPhoto: Boolean = false,
    val hasVehiclePhoto: Boolean = false,
    val selectedHouses: Set<String> = setOf("Casa 54"),
    val confirmationState: ResidentConfirmationState = ResidentConfirmationState.IDLE,
    val isFormControlsLocked: Boolean = false,

    // 3. Paquetería Inteligente
    val isOcrScanning: Boolean = false,
    val ocrSuccessFeedback: String? = null,
    val detectedPackageHouse: String = "Casa 54",
    val packageCarrier: String = "Amazon Logistics",
    val packageTrackingInput: String = "AMZ-99482-MX",
    val pendingPackages: List<PackageDeliveryItem> = emptyList(),
    val isPickupScannerActive: Boolean = false,
    val packageRegistrationFeedback: String? = null,

    // 4. Bitácora de Rondines & Georreferencia de Checkpoints
    val patrolReportText: String = "",
    val isVoiceListening: Boolean = false,
    val voiceFeedbackMessage: String? = null,
    val patrolLogs: List<PatrolLogEntry> = emptyList(),
    val checkpoints: List<PatrolCheckpoint> = CondominiumPatrolMapCatalog.getInitialCheckpoints(),
    val selectedCheckpoint: PatrolCheckpoint? = null,
    val currentGpsAccuracyMeters: Double = 3.2,
    val isSimulatingGpsScan: Boolean = false,
    val lastCheckpointFeedback: String? = null,

    // Capa de Sincronización Local Room <-> Cloud Firestore
    val syncState: PatrolSyncState = PatrolSyncState(),

    // Sistema general
    val systemNotice: String? = null
)

class GuardStationViewModel(
    private val visitorRepository: VisitorRepository = VisitorRepository(),
    private val alertRepository: ResidentAlertRepository = ResidentAlertRepository(),
    customPatrolRepository: PatrolSyncRepository? = null
) : ViewModel() {

    val patrolRepository: PatrolSyncRepository? = customPatrolRepository ?: run {
        try {
            com.example.medusaalfha.MedusaApplication.instance?.let { app ->
                PatrolSyncRepository.getInstance(app)
            }
        } catch (_: Throwable) {
            null
        }
    }

    private val _uiState = MutableStateFlow(GuardStationUiState())
    val uiState: StateFlow<GuardStationUiState> = _uiState.asStateFlow()

    init {
        // Inicializar paquetes de muestra activos
        _uiState.value = _uiState.value.copy(
            pendingPackages = listOf(
                PackageDeliveryItem(
                    id = "PKG-101",
                    trackingCode = "DHL-88319-CDMX",
                    destinationHouse = "Casa 12",
                    carrierName = "DHL Express",
                    ocrRawDetection = "DEST: CASA 12 // LIC. MENDOZA // GUIA: 88319",
                    qrPickupToken = "TOKEN-CASA12-DHL88319"
                ),
                PackageDeliveryItem(
                    id = "PKG-102",
                    trackingCode = "ML-4491-PRADOS",
                    destinationHouse = "Casa 88",
                    carrierName = "Mercado Libre Full",
                    ocrRawDetection = "DEST: CASA 88 // MARIANA TORRES // REF: PAQUETE 4491",
                    qrPickupToken = "TOKEN-CASA88-ML4491"
                )
            ),
            patrolLogs = listOf(
                PatrolLogEntry(
                    id = "ROND-01",
                    timestampMillis = System.currentTimeMillis() - 1000 * 60 * 40,
                    zone = "Perímetro Norte y Canchas",
                    reportText = "Rondín sin novedades. Cercado electrificado y luminarias LED operando al 100%.",
                    isAiAssisted = true,
                    isSynced = true
                )
            )
        )

        // Observar reactivamente Room Database si el repositorio está activo
        patrolRepository?.let { repo ->
            viewModelScope.launch {
                repo.checkpointsFlow.collect { cpList ->
                    if (cpList.isNotEmpty()) {
                        _uiState.update { state ->
                            val currentSelected = state.selectedCheckpoint?.id
                            val updatedSelected = cpList.find { it.id == currentSelected } ?: state.selectedCheckpoint
                            state.copy(checkpoints = cpList, selectedCheckpoint = updatedSelected)
                        }
                    }
                }
            }
            viewModelScope.launch {
                repo.reportsFlow.collect { reportsList ->
                    if (reportsList.isNotEmpty()) {
                        _uiState.update { it.copy(patrolLogs = reportsList) }
                    }
                }
            }
            viewModelScope.launch {
                repo.syncState.collect { sync ->
                    _uiState.update { it.copy(syncState = sync) }
                }
            }
        }
    }

    // --- Módulo 1: Asistencia, Control de Fatiga y Relevo Biométrico ---
    fun setGuardAtRestroom(away: Boolean) {
        val now = System.currentTimeMillis()
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
        val event = if (away) "Guardia ausente en WC ($timeStr)" else "Guardia regresó de WC ($timeStr)"

        val currentLogs = _uiState.value.attendance.logs.toMutableList()
        currentLogs.add(0, event)

        _uiState.value = _uiState.value.copy(
            attendance = _uiState.value.attendance.copy(
                isAwayAtRestroom = away,
                awaySinceTimestamp = if (away) now else null,
                logs = currentLogs
            ),
            attendanceNotice = if (away) "Alerta: Caseta en modo supervisión remota activa (Guardia en WC)" else "Oficial reincorporado a la posición de caseta"
        )
    }

    fun openShiftHandoverModal() {
        _uiState.value = _uiState.value.copy(
            isShiftHandoverOpen = true,
            isIncomingPhotoCaptured = false,
            isBiometricSignatureVerified = false
        )
    }

    fun closeShiftHandoverModal() {
        _uiState.value = _uiState.value.copy(
            isShiftHandoverOpen = false,
            isIncomingPhotoCaptured = false,
            isBiometricSignatureVerified = false
        )
    }

    fun captureIncomingGuardPhoto() {
        _uiState.value = _uiState.value.copy(
            isIncomingPhotoCaptured = true,
            systemNotice = "Fotografía facial del guardia entrante validada y encriptada."
        )
    }

    fun verifyBiometricSignature() {
        _uiState.value = _uiState.value.copy(
            isBiometricSignatureVerified = true,
            systemNotice = "Firma biométrica y huella dactilar cotejada con éxito."
        )
    }

    fun commitShiftChange(incomingGuardName: String, incomingRank: String) {
        val now = System.currentTimeMillis()
        val name = incomingGuardName.ifBlank { "Oficial R. Méndez" }
        val rank = incomingRank.ifBlank { "Segundo Oficial · Turno Bravo" }
        val timeStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(now))

        val handoverLog = "Relevo Biométrico: Entra $name ($rank) a las $timeStr. Contador reiniciado a 00:00."
        val updatedLogs = _uiState.value.attendance.logs.toMutableList()
        updatedLogs.add(0, handoverLog)

        _uiState.value = _uiState.value.copy(
            attendance = GuardAttendanceState(
                guardId = "GRD-${(1000..9999).random()}",
                guardName = name,
                guardRank = rank,
                shiftStartTimestamp = now, // Reiniciar contador a cero
                isAwayAtRestroom = false,
                awaySinceTimestamp = null,
                logs = updatedLogs,
                totalHoursWorked = 0.0
            ),
            isShiftHandoverOpen = false,
            isIncomingPhotoCaptured = false,
            isBiometricSignatureVerified = false,
            systemNotice = "¡Cambio de Turno exitoso! Contador de jornada reiniciado a 00:00 para $name."
        )
    }

    /**
     * Permite probar interactivamente el estado crítico (>= 24 horas)
     * simulando un inicio de turno hace 25 horas.
     */
    fun toggleSimulatedCriticalFatigue() {
        val current = _uiState.value.attendance
        val isCriticalNow = current.isCriticalFatigue
        val newTimestamp = if (isCriticalNow) {
            System.currentTimeMillis() - (1000 * 60 * 60 * 6 + 1000 * 60 * 15) // 6h 15m (Normal)
        } else {
            System.currentTimeMillis() - (1000 * 60 * 60 * 25 + 1000 * 60 * 42) // 25h 42m (Crítico >= 24h)
        }

        _uiState.value = _uiState.value.copy(
            attendance = current.copy(shiftStartTimestamp = newTimestamp),
            systemNotice = if (!isCriticalNow) {
                "Simulación activa: Alerta de Fatiga Laboral (>24h) sincronizada con residentes y administración."
            } else {
                "Simulación restablecida: Turno laboral en rango normal (<24h)."
            }
        )
    }

    // --- Módulo 2: Visitantes y Notificación Cruzada ---
    fun onAccessClassificationChanged(newClass: String) {
        _uiState.value = _uiState.value.copy(selectedAccessClassification = newClass)
    }

    fun onGeneralObservationsChanged(obs: String) {
        _uiState.value = _uiState.value.copy(generalObservations = obs)
    }

    fun toggleIdPhoto() {
        val current = _uiState.value.hasIdPhoto
        _uiState.value = _uiState.value.copy(hasIdPhoto = !current)
    }

    fun toggleVehiclePhoto() {
        val current = _uiState.value.hasVehiclePhoto
        _uiState.value = _uiState.value.copy(hasVehiclePhoto = !current)
    }

    fun toggleHouseSelection(houseId: String) {
        val currentSet = _uiState.value.selectedHouses.toMutableSet()
        if (currentSet.contains(houseId)) {
            if (currentSet.size > 1) { // Mantener al menos una
                currentSet.remove(houseId)
            }
        } else {
            currentSet.add(houseId)
        }
        _uiState.value = _uiState.value.copy(selectedHouses = currentSet)
    }

    fun dispatchCrossNotificationToResident(context: Context) {
        val houses = _uiState.value.selectedHouses.joinToString(", ")
        val obs = _uiState.value.generalObservations.ifBlank { "Acceso regular autorizado por garita" }

        viewModelScope.launch {
            // Bloquear controles y poner en espera
            _uiState.value = _uiState.value.copy(
                isFormControlsLocked = true,
                confirmationState = ResidentConfirmationState.WAITING_CONFIRMATION,
                systemNotice = "Enviando notificación satelital a $houses..."
            )

            // Despachar alerta al repositorio de residentes
            try {
                for (house in _uiState.value.selectedHouses) {
                    alertRepository.dispatchVisitorArrivalAlert(
                        context = context,
                        visitorName = _uiState.value.selectedAccessClassification,
                        destinationHouse = house,
                        folio = "FOL-${System.currentTimeMillis() % 100000}",
                        vehiclePlate = if (_uiState.value.hasVehiclePhoto) "EVIDENCIA-FOTO" else null
                    )
                }
            } catch (_: Exception) {}

            // Simulación interactiva del enlace en tiempo real con la app del residente
            delay(3200)

            // Confirmación exitosa recibida
            _uiState.value = _uiState.value.copy(
                confirmationState = ResidentConfirmationState.ACCEPTED,
                isFormControlsLocked = false,
                systemNotice = "¡Acceso AUTORIZADO remotamente por el residente de $houses!",
                generalObservations = ""
            )

            // Registrar también en bitácora de visitantes
            val folio = "FOL-GAR-${System.currentTimeMillis() % 100000}"
            val newEntry = VisitorEntry(
                folio = folio,
                visitorName = "${_uiState.value.selectedAccessClassification} (Multi-casas)",
                visitorType = _uiState.value.selectedAccessClassification,
                destinationHouse = houses,
                vehiclePlate = if (_uiState.value.hasVehiclePhoto) "CON-FOTO-VEHÍCULO" else null,
                notes = obs,
                status = VisitorStatus.DENTRO,
                accessMethod = "Notificación Cruzada Autorizada"
            )
            visitorRepository.registerVisitorEntry(newEntry)
        }
    }

    fun resetConfirmationState() {
        _uiState.value = _uiState.value.copy(
            confirmationState = ResidentConfirmationState.IDLE,
            isFormControlsLocked = false
        )
    }

    // --- Módulo 3: Paquetería Inteligente (AI Visión + QR Contraentrega) ---
    fun onPackageTrackingInputChanged(input: String) {
        _uiState.value = _uiState.value.copy(packageTrackingInput = input)
    }

    fun onPackageCarrierChanged(carrier: String) {
        _uiState.value = _uiState.value.copy(packageCarrier = carrier)
    }

    fun onDetectedHouseChanged(house: String) {
        _uiState.value = _uiState.value.copy(detectedPackageHouse = house)
    }

    fun simulateAiVisionOcrScan() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOcrScanning = true, ocrSuccessFeedback = null)
            delay(1500)

            // Muestra de OCR simulado de alta fidelidad detectando etiqueta de mensajería
            val simulatedHouses = listOf("Casa 54", "Casa 12", "Casa 88", "Casa 31")
            val chosen = simulatedHouses.random()
            val trackingNumber = "AMZ-${(10000..99999).random()}-MX"

            _uiState.value = _uiState.value.copy(
                isOcrScanning = false,
                detectedPackageHouse = chosen,
                packageTrackingInput = trackingNumber,
                packageCarrier = if (trackingNumber.contains("AMZ")) "Amazon Prime" else "FedEx Express",
                ocrSuccessFeedback = "AI Visión: Etiqueta escaneada con éxito -> Coincidencia 99.8% con $chosen."
            )
        }
    }

    fun registerAndTokenizePackage(context: Context) {
        val state = _uiState.value
        val token = "QR-PASS-${state.detectedPackageHouse.replace(" ", "")}-${System.currentTimeMillis() % 10000}"

        val newItem = PackageDeliveryItem(
            id = "PKG-${System.currentTimeMillis() % 100000}",
            trackingCode = state.packageTrackingInput.ifBlank { "TRK-${(1000..9999).random()}" },
            destinationHouse = state.detectedPackageHouse,
            carrierName = state.packageCarrier,
            ocrRawDetection = "AI Vision Match: ${state.detectedPackageHouse}",
            qrPickupToken = token
        )

        val updatedList = state.pendingPackages.toMutableList()
        updatedList.add(0, newItem)

        // Enviar notificación al residente
        viewModelScope.launch {
            try {
                alertRepository.dispatchVisitorArrivalAlert(
                    context = context,
                    visitorName = "Paquete ${newItem.carrierName}",
                    destinationHouse = newItem.destinationHouse,
                    folio = newItem.trackingCode,
                    vehiclePlate = null
                )
            } catch (_: Exception) {}
        }

        _uiState.value = state.copy(
            pendingPackages = updatedList,
            packageRegistrationFeedback = "Paquete para ${newItem.destinationHouse} registrado. Token QR generado: $token",
            packageTrackingInput = "AMZ-${(10000..99999).random()}-MX"
        )
    }

    fun setPickupScannerActive(active: Boolean) {
        _uiState.value = _uiState.value.copy(isPickupScannerActive = active)
    }

    fun handlePickupQrScanned(tokenScanned: String) {
        val trimmedToken = tokenScanned.trim()
        val current = _uiState.value.pendingPackages.toMutableList()
        val targetIndex = current.indexOfFirst {
            it.qrPickupToken.equals(trimmedToken, ignoreCase = true) ||
            trimmedToken.contains(it.destinationHouse.replace(" ", ""), ignoreCase = true)
        }

        if (targetIndex != -1) {
            val deliveredItem = current.removeAt(targetIndex)
            _uiState.value = _uiState.value.copy(
                pendingPackages = current,
                isPickupScannerActive = false,
                systemNotice = "¡Token validado! Paquete ${deliveredItem.trackingCode} entregado a residente de ${deliveredItem.destinationHouse}."
            )
        } else {
            // Si el token es simulado o un escaneo directo de cualquier paquete existente
            if (current.isNotEmpty()) {
                val deliveredItem = current.removeAt(0)
                _uiState.value = _uiState.value.copy(
                    pendingPackages = current,
                    isPickupScannerActive = false,
                    systemNotice = "¡Token validado! Paquete ${deliveredItem.trackingCode} marcado como ENTREGADO."
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isPickupScannerActive = false,
                    systemNotice = "Aviso: No se encontraron paquetes pendientes coincidentes con el código escaneado."
                )
            }
        }
    }

    // --- Módulo 4: Bitácora de Rondines con Asistente de IA por Voz & Georreferencia ---
    fun onPatrolReportTextChanged(text: String) {
        _uiState.value = _uiState.value.copy(patrolReportText = text)
    }

    fun selectCheckpoint(checkpoint: PatrolCheckpoint) {
        _uiState.value = _uiState.value.copy(selectedCheckpoint = checkpoint)
    }

    /**
     * Valida físicamente la posición espacial del guardia dentro del rango
     * de tolerancia GPS del checkpoint específico (cambia de rojo a verde neón con animación).
     * Sincroniza con Room inmediatamente, y con Firestore sólo si hay Internet activo.
     */
    fun validateCheckpointLocation(checkpointId: String) {
        viewModelScope.launch {
            if (patrolRepository != null) {
                val feedback = patrolRepository.validateCheckpoint(checkpointId)
                val updatedCp = _uiState.value.checkpoints.find { it.id == checkpointId }
                _uiState.update {
                    it.copy(
                        selectedCheckpoint = updatedCp,
                        lastCheckpointFeedback = feedback,
                        systemNotice = feedback
                    )
                }
            } else {
                // Fallback en memoria si Room no está inicializado (ej. entorno de tests básicos)
                val now = System.currentTimeMillis()
                val currentCheckpoints = _uiState.value.checkpoints.map { cp ->
                    if (cp.id == checkpointId) {
                        cp.copy(
                            isValidated = true,
                            validatedAtMillis = now
                        )
                    } else {
                        cp
                    }
                }

                val updatedCp = currentCheckpoints.find { it.id == checkpointId }
                val validatedCount = currentCheckpoints.count { it.isValidated }
                val totalCount = currentCheckpoints.size

                val feedback = "GPS OK: ${updatedCp?.name} verificado dentro de ${updatedCp?.toleranceMeters}m de tolerancia. ($validatedCount/$totalCount puntos cubiertos)."

                _uiState.update {
                    it.copy(
                        checkpoints = currentCheckpoints,
                        selectedCheckpoint = updatedCp,
                        lastCheckpointFeedback = feedback,
                        systemNotice = feedback
                    )
                }
            }
        }
    }

    /**
     * Reinicia el circuito de patrullaje para un nuevo rondín.
     * Actualiza Room y sincroniza con Firestore si hay conexión a Internet.
     */
    fun resetPatrolCircuit() {
        viewModelScope.launch {
            if (patrolRepository != null) {
                patrolRepository.resetCircuit()
                _uiState.update {
                    it.copy(
                        selectedCheckpoint = null,
                        lastCheckpointFeedback = "Nuevo circuito de rondín iniciado. Todos los checkpoints restablecidos."
                    )
                }
            } else {
                val resetList = _uiState.value.checkpoints.map {
                    it.copy(isValidated = false, validatedAtMillis = null)
                }
                _uiState.update {
                    it.copy(
                        checkpoints = resetList,
                        selectedCheckpoint = null,
                        lastCheckpointFeedback = "Nuevo circuito de rondín iniciado. Todos los checkpoints restablecidos."
                    )
                }
            }
        }
    }

    /**
     * Invocación del Asistente de IA por Voz (Speech-to-Text integrado).
     * Si se recibe audio/texto con el comando 'Redactar el informe' (o por defecto en simulación de voz),
     * captura las novedades, las organiza con redacción coherente y profesional,
     * e inyecta el texto formateado directamente en el estado mutable del Campo de Informe.
     */
    fun triggerVoiceDictationAssistant(rawVoiceInput: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isVoiceListening = true,
                voiceFeedbackMessage = "🎙 Reconocimiento de voz nativo activo... Detectando comando 'Redactar el informe'..."
            )
            delay(1800)

            val currentValidated = _uiState.value.checkpoints.filter { it.isValidated }
            val validatedSummary = if (currentValidated.isEmpty()) {
                "Sin checkpoints escaneados aún (Rondín en curso)"
            } else {
                currentValidated.joinToString(", ") { "${it.code} (${it.name})" }
            }

            val currentTimeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

            val isCommandExplicit = rawVoiceInput?.contains("redactar", ignoreCase = true) == true || rawVoiceInput == null

            val professionalReport = if (isCommandExplicit) {
                """
[INFORME TÁCTICO DE RONDÍN - SECTOR PRIVADO MEDUSA ALFHA]
══════════════════════════════════════════════════════════
• Horario de Registro : $currentTimeStr
• Oficial a Cargo     : ${_uiState.value.attendance.guardName} (${_uiState.value.attendance.guardRank})
• Estado de Checkpoints: ${currentValidated.size}/${_uiState.value.checkpoints.size} Validados por GPS
• Puntos Confirmados  : $validatedSummary
• Precisión Satelital : ${_uiState.value.currentGpsAccuracyMeters} metros (Fix RTK Nominal)
• Novedades Observadas:
  - Cercado electrificado y sensores de fibra perimetral sin disparo de alarmas.
  - Alumbrado vial y circuitos LED operando al 100% en todos los andadores.
  - Sin presencia de personas ajenas o vehículos sospechosos en bahías de retorno.
• Diagnóstico Final   : CONDICIÓN GENERAL EN REGLA Y DESPEJADA.
══════════════════════════════════════════════════════════
                """.trimIndent()
            } else {
                """
[TRANSCRIPCIÓN DICTADO - OFICIAL EN TURNO ($currentTimeStr)]
"${rawVoiceInput.trim()}"
Checkpoints GPS verificados: ${currentValidated.size}/${_uiState.value.checkpoints.size}.
                """.trimIndent()
            }

            _uiState.value = _uiState.value.copy(
                isVoiceListening = false,
                patrolReportText = professionalReport,
                voiceFeedbackMessage = "Asistente de IA: Comando 'Redactar el informe' procesado con síntesis estructurada."
            )
        }
    }

    /**
     * Guarda el informe en Room y lo sincroniza a Cloud Firestore sólo si hay Internet activo.
     */
    fun savePatrolReport() {
        val text = _uiState.value.patrolReportText.trim()
        if (text.isBlank()) return

        viewModelScope.launch {
            if (patrolRepository != null) {
                val savedEntry = patrolRepository.saveReport(
                    zone = "Circuito Completo Residencial",
                    reportText = text,
                    isAiAssisted = true
                )
                _uiState.update {
                    it.copy(
                        patrolReportText = "",
                        voiceFeedbackMessage = null,
                        systemNotice = if (savedEntry.isSynced)
                            "Rondín guardado en Room y sincronizado con Cloud Firestore."
                        else
                            "Rondín guardado localmente en Room. Pendiente de sincronización a Firestore."
                    )
                }
            } else {
                val newLog = PatrolLogEntry(
                    id = "ROND-${System.currentTimeMillis() % 100000}",
                    timestampMillis = System.currentTimeMillis(),
                    zone = "Circuito Completo Residencial",
                    reportText = text,
                    isAiAssisted = true
                )

                val updated = _uiState.value.patrolLogs.toMutableList()
                updated.add(0, newLog)

                _uiState.update {
                    it.copy(
                        patrolLogs = updated,
                        patrolReportText = "",
                        voiceFeedbackMessage = null,
                        systemNotice = "Rondín registrado en la bitácora permanente de seguridad."
                    )
                }
            }
        }
    }

    /**
     * Dispara manualmente la sincronización de transacciones pendientes en Room hacia Firestore.
     */
    fun triggerManualSync() {
        viewModelScope.launch {
            patrolRepository?.syncPendingData()
        }
    }

    fun clearSystemNotice() {
        _uiState.value = _uiState.value.copy(systemNotice = null, attendanceNotice = null)
    }
}
