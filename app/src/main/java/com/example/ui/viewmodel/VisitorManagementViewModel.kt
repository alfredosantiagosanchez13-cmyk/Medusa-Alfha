package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.audit.AuditLogEntity
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.firebase.FirebaseConfigHelper
import com.example.data.passes.QrPassRoomEntity
import com.example.data.visitor.VisitorCheckIn
import com.example.data.visitor.VisitorCheckInRepository
import com.example.scanner.PassType
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel reactivo y seguro para la Gestión de Visitantes en MEDUSA ALFHA.
 *
 * Características Críticas:
 * 1. Inicialización y transición segura de datos: Consulta primero datos reales en Firestore
 *    y solo recurre a datos demo si Room está vacío y no hay conexión con Firestore.
 * 2. Hilo de fondo garantizado (Dispatchers.IO): Todas las operaciones de sincronización Firestore,
 *    persistencia en Room SQLite y bitácora forense de auditoría se ejecutan explícitamente en
 *    `viewModelScope.launch(Dispatchers.IO)` para prevenir NetworkOnMainThreadException y bloqueos de UI.
 * 3. Manejo de excepciones y reglas de seguridad: Captura limpia de errores por falta de permisos
 *    (PERMISSION_DENIED) de Firestore, emitiendo alertas reactivas para la interfaz sin tumbar la aplicación.
 */
class VisitorManagementViewModel(
    val db: AppDatabase,
    val repository: VisitorCheckInRepository,
    val activeCondominiumId: String = "PRADOS_1"
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow("Verificando sincronización...")
    val syncMessage: StateFlow<String> = _syncMessage.asStateFlow()

    private val _securityRuleAlert = MutableStateFlow<String?>(null)
    val securityRuleAlert: StateFlow<String?> = _securityRuleAlert.asStateFlow()

    private val _isRealFirestoreConnected = MutableStateFlow(false)
    val isRealFirestoreConnected: StateFlow<Boolean> = _isRealFirestoreConnected.asStateFlow()

    val allCheckIns: StateFlow<List<VisitorCheckIn>> = repository.allCheckIns
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allQrPasses: StateFlow<List<QrPassRoomEntity>> = db.qrPassDao().getAllPassesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        initializeSafeDataTransition(activeCondominiumId)
    }

    /**
     * Inicializa la carga de datos sin bloquear el hilo principal.
     * Intenta sincronizar datos reales de Firestore; si falla o no hay datos,
     * permite continuar con la base de datos local y datos demo si corresponde.
     */
    fun initializeSafeDataTransition(condoId: String = activeCondominiumId) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            try {
                // 1. Intentar obtener datos reales de Firestore
                val fs = FirebaseConfigHelper.getFirestore()
                if (fs != null) {
                    val syncResult = repository.syncFromFirestore(condoId)
                    if (syncResult.isSuccess) {
                        val count = syncResult.getOrDefault(0)
                        _syncMessage.value = if (count > 0) "Firestore: $count registros reales" else "Firestore al día (Datos Reales)"
                        _isRealFirestoreConnected.value = true
                        _securityRuleAlert.value = null
                    } else {
                        val exception = syncResult.exceptionOrNull()
                        handleFirestoreException(exception, condoId)
                    }
                } else {
                    _syncMessage.value = "Modo Local Autónomo (Room SQLite)"
                }
            } catch (t: Throwable) {
                handleFirestoreException(t, condoId)
            } finally {
                _isSyncing.value = false
            }

            // 2. Transición segura: si Room no tiene datos registrados aún, poblar datos de prueba
            try {
                if (repository.getCheckInCount() == 0) {
                    repository.seedInitialCheckInsIfEmpty()
                    if (_syncMessage.value.startsWith("Verificando")) {
                        _syncMessage.value = "Modo Local (Datos iniciales cargados)"
                    }
                }
            } catch (t: Throwable) {
                Log.w("VisitorManagementVM", "Aviso al verificar datos demo en Room: ${t.message}")
            }
        }
    }

    /**
     * Sincronización manual explícita con Firestore bajo Dispatchers.IO.
     */
    fun syncWithFirestore(condoId: String = activeCondominiumId, onComplete: ((Boolean, String) -> Unit)? = null) {
        if (_isSyncing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _isSyncing.value = true
            try {
                val syncResult = repository.syncFromFirestore(condoId)
                if (syncResult.isSuccess) {
                    val count = syncResult.getOrDefault(0)
                    val msg = if (count > 0) "Firestore: $count nuevos logs sincronizados" else "Firestore al día"
                    _syncMessage.value = msg
                    _isRealFirestoreConnected.value = true
                    _securityRuleAlert.value = null
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(true, msg)
                    }
                } else {
                    val ex = syncResult.exceptionOrNull()
                    val warning = handleFirestoreException(ex, condoId)
                    withContext(Dispatchers.Main) {
                        onComplete?.invoke(false, warning)
                    }
                }
            } catch (t: Throwable) {
                val warning = handleFirestoreException(t, condoId)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke(false, warning)
                }
            } finally {
                _isSyncing.value = false
            }
        }
    }

    /**
     * Registra el ingreso de un visitante en Room, Firestore y Bitácora de Auditoría en Dispatchers.IO.
     */
    fun registerCheckIn(
        checkInId: Long,
        folio: String,
        visitorName: String,
        destinationHouse: String,
        guardName: String,
        passTypeLabel: String = "Visita",
        onDone: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.registerCheckInEntry(checkInId, notes = "Ingreso verificado en caseta por $guardName")

                // Bitácora inmutable de auditoría forense
                try {
                    val audit = AuditLogEntity(
                        logId = AlphaCoreEngine.generateUniqueFolio("AUD"),
                        timestamp = System.currentTimeMillis(),
                        operatorId = guardName,
                        eventDescription = "VISITOR CHECK-IN: Ingreso registrado para $visitorName -> $destinationHouse (Folio: $folio)",
                        severity = AuditLogEntity.Severity.INFO,
                        forensicPayload = """{"checkInId":$checkInId,"folio":"$folio","house":"$destinationHouse","passType":"$passTypeLabel"}"""
                    )
                    db.auditLogDao().insertAuditLog(audit)
                } catch (ae: Throwable) {
                    Log.w("VisitorManagementVM", "Audit log warning: ${ae.message}")
                }

                withContext(Dispatchers.Main) {
                    onDone(true, "✅ Ingreso registrado con éxito")
                }
            } catch (t: Throwable) {
                val err = handleFirestoreException(t, activeCondominiumId)
                withContext(Dispatchers.Main) {
                    onDone(false, "Aviso: $err")
                }
            }
        }
    }

    /**
     * Registra la salida de un visitante en Room, Firestore y Bitácora de Auditoría en Dispatchers.IO.
     */
    fun registerCheckOut(
        checkInId: Long,
        folio: String,
        visitorName: String,
        destinationHouse: String,
        guardName: String,
        notes: String? = null,
        onDone: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = repository.registerCheckOutByFolio(folio, notes = notes ?: "Salida confirmada por $guardName")
                if (res.isFailure) {
                    repository.registerCheckOut(checkInId, notes = notes ?: "Salida confirmada")
                }

                // Bitácora inmutable de auditoría
                try {
                    val audit = AuditLogEntity(
                        logId = AlphaCoreEngine.generateUniqueFolio("AUD"),
                        timestamp = System.currentTimeMillis(),
                        operatorId = guardName,
                        eventDescription = "VISITOR CHECK-OUT: Salida completada para $visitorName -> $destinationHouse (Folio: $folio)",
                        severity = AuditLogEntity.Severity.INFO,
                        forensicPayload = """{"checkInId":$checkInId,"folio":"$folio","operator":"$guardName"}"""
                    )
                    db.auditLogDao().insertAuditLog(audit)
                } catch (ae: Throwable) {
                    Log.w("VisitorManagementVM", "Audit log warning: ${ae.message}")
                }

                withContext(Dispatchers.Main) {
                    onDone(true, "👋 Salida registrada con éxito")
                }
            } catch (t: Throwable) {
                val err = handleFirestoreException(t, activeCondominiumId)
                withContext(Dispatchers.Main) {
                    onDone(false, "Aviso: $err")
                }
            }
        }
    }

    /**
     * Registra un nuevo invitado persistiendo en Room, sincronizando con Firestore
     * y creando la entrada en la bitácora de auditoría bajo Dispatchers.IO.
     */
    fun registerGuest(
        condominiumId: String,
        visitorName: String,
        authorizedUnitNumber: String,
        hostResidentName: String,
        visitorDocument: String,
        passTypeLabel: String,
        durationHours: Int,
        vehiclePlate: String?,
        residentNotes: String?,
        isImmediateCheckIn: Boolean,
        onResult: (Result<VisitorCheckIn>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.registerGuestAndLogToFirestore(
                    condominiumId = condominiumId,
                    visitorName = visitorName,
                    authorizedUnitNumber = authorizedUnitNumber,
                    hostResidentName = hostResidentName,
                    visitorDocument = visitorDocument.ifBlank { "Sin Documento" },
                    passTypeLabel = "$passTypeLabel (Token ${durationHours}h)",
                    vehiclePlate = vehiclePlate?.ifBlank { null },
                    residentNotes = residentNotes?.ifBlank { null },
                    isImmediateCheckIn = isImmediateCheckIn
                )

                if (result.isSuccess) {
                    val saved = result.getOrThrow()
                    val now = System.currentTimeMillis()
                    val validUntil = now + (durationHours.toLong() * 3600 * 1000L)
                    val hash = AlphaCoreEngine.computeIntegrityHash(saved.passCode, saved.visitorDocument, saved.destinationHouse)
                    val qrPass = QrPassRoomEntity(
                        passCode = saved.passCode,
                        guestName = saved.visitorName,
                        guestDocument = saved.visitorDocument,
                        destinationHouse = saved.destinationHouse,
                        hostResidentName = saved.hostResidentName,
                        vehiclePlate = saved.vehiclePlate,
                        passType = PassType.VISITOR_SINGLE,
                        validUntilMillis = validUntil,
                        maxEntries = 1,
                        currentEntriesCount = 0,
                        note = "Token temporal (${durationHours}h). ${saved.residentNotes ?: ""}".trim(),
                        createdAtMillis = now,
                        integrityHash = hash,
                        isActive = true
                    )
                    db.qrPassDao().insertPass(qrPass)

                    // Registro en Bitácora de Auditoría
                    try {
                        val audit = AuditLogEntity(
                            logId = AlphaCoreEngine.generateUniqueFolio("AUD"),
                            timestamp = now,
                            operatorId = hostResidentName,
                            eventDescription = "VISITOR PRE-REGISTRATION: Nuevo pase creado para ${saved.visitorName} -> ${saved.destinationHouse} (Folio: ${saved.folio})",
                            severity = AuditLogEntity.Severity.INFO,
                            forensicPayload = """{"folio":"${saved.folio}","passCode":"${saved.passCode}","host":"$hostResidentName"}"""
                        )
                        db.auditLogDao().insertAuditLog(audit)
                    } catch (ae: Throwable) {
                        Log.w("VisitorManagementVM", "Audit log warning: ${ae.message}")
                    }

                    withContext(Dispatchers.Main) {
                        onResult(Result.success(saved))
                    }
                } else {
                    val ex = result.exceptionOrNull()
                    handleFirestoreException(ex, condominiumId)
                    withContext(Dispatchers.Main) {
                        onResult(Result.failure(ex ?: Exception("Error al registrar invitado")))
                    }
                }
            } catch (t: Throwable) {
                handleFirestoreException(t, condominiumId)
                withContext(Dispatchers.Main) {
                    onResult(Result.failure(t))
                }
            }
        }
    }

    fun clearSecurityRuleAlert() {
        _securityRuleAlert.value = null
    }

    /**
     * Intercepta excepciones de Firestore clasificando errores de permisos (PERMISSION_DENIED)
     * de reglas de seguridad sin causar crashes en la aplicación.
     */
    private fun handleFirestoreException(t: Throwable?, condoId: String): String {
        if (t == null) return "Operación local completada"
        val message = t.message.orEmpty()
        val isPermissionDenied = t is SecurityException ||
                (t is FirebaseFirestoreException && t.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) ||
                message.contains("PERMISSION_DENIED", ignoreCase = true) ||
                message.contains("permission-denied", ignoreCase = true) ||
                message.contains("Missing or insufficient permissions", ignoreCase = true)

        return if (isPermissionDenied) {
            val alert = "Reglas de seguridad de Firestore deniegan acceso a '/condominiums/$condoId/visitor_logs'. Verifique las reglas en Firebase Console. La app continúa en modo local seguro (Room SQLite)."
            _securityRuleAlert.value = alert
            _syncMessage.value = "Modo Local (Reglas de Firestore restrictivas)"
            Log.w("VisitorManagementVM", "Firestore permission denied handled gracefully: $message")
            alert
        } else {
            Log.w("VisitorManagementVM", "Firestore exception handled: $message")
            _syncMessage.value = "Modo Local Autónomo (Room SQLite)"
            "Modo Local Autónomo (Room): $message"
        }
    }

    companion object {
        fun provideFactory(
            db: AppDatabase,
            condominiumId: String
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = VisitorCheckInRepository(db.visitorCheckInDao(), activeCondominiumId = condominiumId)
                return VisitorManagementViewModel(db, repository, condominiumId) as T
            }
        }
    }
}
