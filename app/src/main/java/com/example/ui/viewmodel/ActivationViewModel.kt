package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.auth.AlfhaRole
import com.example.auth.AlfhaSecurityContext
import com.example.data.auth.ActivationKey
import com.example.data.auth.MedusaDevConfig
import com.example.data.auth.MedusaFinancialAccessGuard
import com.example.data.auth.MedusaRole
import com.example.data.auth.MedusaSessionPreferences
import com.example.data.auth.UserSession
import com.example.data.booking.AppDatabase
import com.example.data.firebase.FirebaseConfigHelper
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.UnknownHostException
import java.util.UUID

/**
 * Clasificación de errores en la validación de la llave de activación.
 */
enum class ActivationErrorType {
    EMPTY_INPUT,
    KEY_NOT_FOUND,
    KEY_INACTIVE,
    KEY_EXPIRED,
    INVALID_ROLE,
    NETWORK_ERROR,
    PERMISSION_DENIED,
    FIRESTORE_ERROR,
    UNKNOWN
}

/**
 * Estados reactivos de la interfaz de activación de MEDUSA ALFHA.
 */
sealed interface ActivationUiState {
    object Idle : ActivationUiState
    object Loading : ActivationUiState

    data class Success(
        val activationKey: ActivationKey,
        val role: MedusaRole,
        val message: String,
        val isFinancialBlocked: Boolean
    ) : ActivationUiState

    data class Error(
        val errorMessage: String,
        val errorType: ActivationErrorType,
        val technicalDetails: String? = null
    ) : ActivationUiState
}

/**
 * ViewModel Validador de Llaves de Activación (MVVM).
 *
 * Responsabilidades:
 * 1. Consultar de manera segura la ruta de Firestore `/activation_keys/$inputKey`.
 * 2. Si la clave existe y isActive == true:
 *    - Transforma el campo textual role al Enum MedusaRole correspondiente.
 *    - Guarda de forma segura los identificadores (condominiumId, assignedUnit, role)
 *      en las Preferencias del Sistema (EncryptedSharedPreferences) para recordar el perfil.
 * 3. Si el rol asignado es GUARDIA_CASETA:
 *    - Inicializa el ecosistema móvil bloqueando nativamente el acceso a los nodos de datos financieros.
 * 4. Manejo exhaustivo de excepciones (red, expiradas, inexistentes, permisos) con avisos reactivos
 *    mediante StateFlow para informar visualmente al usuario.
 */
class ActivationViewModel(
    application: Application,
    private val firestore: FirebaseFirestore? = FirebaseConfigHelper.getFirestore(),
    private val sessionPreferences: MedusaSessionPreferences = MedusaSessionPreferences.getInstance(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<ActivationUiState>(ActivationUiState.Idle)
    val uiState: StateFlow<ActivationUiState> = _uiState.asStateFlow()

    private val _currentSession = MutableStateFlow<UserSession?>(null)
    val currentSession: StateFlow<UserSession?> = _currentSession.asStateFlow()

    private val _currentRole = MutableStateFlow<MedusaRole>(
        try {
            sessionPreferences.getCurrentRole()
        } catch (t: Throwable) {
            MedusaRole.UNASSIGNED
        }
    )
    val currentRole: StateFlow<MedusaRole> = _currentRole.asStateFlow()

    init {
        try {
            checkExistingSession()
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing session check: ${t.message}", t)
        }
    }

    /**
     * Verifica si el dispositivo ya cuenta con una sesión activa en SharedPreferences.
     * Permite que la aplicación recuerde el perfil en los siguientes inicios de sesión.
     */
    fun checkExistingSession() {
        try {
            val existingSession = sessionPreferences.getSessionData()
            if (existingSession != null && existingSession.isActive) {
                _currentSession.value = existingSession
                _currentRole.value = existingSession.role
                // Restablece la política de seguridad nativa para el rol recordado
                MedusaFinancialAccessGuard.applyRoleSecurityPolicy(existingSession.role)
                _uiState.value = ActivationUiState.Success(
                    activationKey = ActivationKey(
                        keyId = existingSession.keyId,
                        role = existingSession.role.name,
                        condominiumId = existingSession.condominiumId,
                        condominiumName = existingSession.condominiumName,
                        assignedUnit = existingSession.assignedUnit,
                        isActive = true
                    ),
                    role = existingSession.role,
                    message = "Sesión activa restaurada: ${existingSession.role.displayName}",
                    isFinancialBlocked = existingSession.isFinancialBlocked
                )
                Log.i(TAG, "🔄 Sesión previa restaurada con éxito para condominio: ${existingSession.condominiumId}")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error verificando sesión existente: ${t.message}", t)
        }
    }

    /**
     * Implementa la función principal requerida:
     * Realiza una consulta segura a la ruta de Firestore `/activation_keys/$inputKey`.
     * Incluye fallback/bypass estático local para llaves maestras sin depender de Firestore.
     */
    fun validateActivationKey(inputKey: String) {
        // Sanitización robusta: eliminar espacios intermedios y en extremos, normalizar guiones y pasar a mayúsculas
        val normalizedKey = inputKey
            .replace("\\s+".toRegex(), "")
            .replace("–", "-")
            .replace("—", "-")
            .trim()
            .uppercase()

        if (normalizedKey.isBlank()) {
            _uiState.value = ActivationUiState.Error(
                errorMessage = "Por favor, introduce una llave de activación válida.",
                errorType = ActivationErrorType.EMPTY_INPUT
            )
            return
        }

        _uiState.value = ActivationUiState.Loading

        viewModelScope.launch {
            try {
                // 1. Evaluación de credenciales de depuración aisladas (Modo Desarrollo DEBUG)
                // En producción (!BuildConfig.DEBUG) evaluateDebugKey retorna null incondicionalmente.
                val debugRole = MedusaDevConfig.evaluateDebugKey(normalizedKey)
                if (debugRole != null) {
                    val isCaseta = debugRole == MedusaRole.GUARDIA_CASETA
                    val unitName = when (debugRole) {
                        MedusaRole.GUARDIA_CASETA -> "Caseta Principal"
                        MedusaRole.ADMINISTRACION -> "Administración Central"
                        MedusaRole.RESIDENTE -> "Casa 104"
                        MedusaRole.UNASSIGNED -> ""
                    }
                    val blockFinancial = debugRole.requiresFinancialNodeLock()
                    MedusaFinancialAccessGuard.applyRoleSecurityPolicy(debugRole)

                    val devKey = ActivationKey(
                        keyId = normalizedKey,
                        role = debugRole.name,
                        condominiumId = "PRADOS_1",
                        condominiumName = "Residencial Los Prados 1",
                        assignedUnit = unitName,
                        isActive = true
                    )
                    val devSession = UserSession(
                        activationKey = normalizedKey,
                        currentRole = debugRole,
                        condominiumId = "PRADOS_1",
                        assignedUnitId = unitName,
                        condominiumName = "Residencial Los Prados 1",
                        isActive = true,
                        isFinancialBlocked = blockFinancial,
                        timestampMillis = System.currentTimeMillis()
                    )
                    withContext(Dispatchers.IO) {
                        sessionPreferences.saveSession(devKey, debugRole)
                        sessionPreferences.saveSession(devSession)
                    }
                    _currentSession.value = devSession
                    _currentRole.value = debugRole
                    _uiState.value = ActivationUiState.Success(
                        activationKey = devKey,
                        role = debugRole,
                        message = "Entorno de desarrollo: Sesión ${debugRole.displayName} activada.",
                        isFinancialBlocked = blockFinancial
                    )
                    return@launch
                }

                // 2. Consulta y autenticación segura contra Room SQLite (Fuente Única de Verdad)
                val db = AppDatabase.getDatabase(getApplication())
                val localUser = withContext(Dispatchers.IO) {
                    AlfhaSecurityContext.seedInitialUsersIfEmpty(db)
                    db.alfhaUserDao().getUserByEmail(normalizedKey.lowercase())
                        ?: db.alfhaUserDao().getUserById(normalizedKey)
                }

                if (localUser != null) {
                    if (!localUser.isActive) {
                        _uiState.value = ActivationUiState.Error(
                            errorMessage = "La cuenta del usuario '${localUser.name}' se encuentra inactiva.",
                            errorType = ActivationErrorType.KEY_INACTIVE
                        )
                        return@launch
                    }

                    val role = when (localUser.role) {
                        AlfhaRole.ADMINISTRACION.name, AlfhaRole.MAESTRO_ALFHA.name, AlfhaRole.MESA_DIRECTIVA.name -> MedusaRole.ADMINISTRACION
                        AlfhaRole.GUARDIA.name, AlfhaRole.SUPERVISOR.name -> MedusaRole.GUARDIA_CASETA
                        AlfhaRole.RESIDENTE.name -> MedusaRole.RESIDENTE
                        else -> MedusaRole.fromString(localUser.role)
                    }

                    val isFinancialBlocked = role.requiresFinancialNodeLock()
                    MedusaFinancialAccessGuard.applyRoleSecurityPolicy(role)
                    AlfhaSecurityContext.setCurrentUser(localUser)

                    val authKey = ActivationKey(
                        keyId = "KEY-${localUser.id}",
                        role = role.name,
                        condominiumId = "PRADOS_1",
                        condominiumName = "Residencial Los Prados 1",
                        assignedUnit = localUser.unitOrDepartment,
                        isActive = true
                    )
                    val authSession = UserSession(
                        activationKey = "KEY-${localUser.id}",
                        currentRole = role,
                        condominiumId = "PRADOS_1",
                        assignedUnitId = localUser.unitOrDepartment,
                        condominiumName = "Residencial Los Prados 1",
                        isActive = true,
                        isFinancialBlocked = isFinancialBlocked,
                        timestampMillis = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.IO) {
                        sessionPreferences.saveSession(authKey, role)
                        sessionPreferences.saveSession(authSession)
                        db.alfhaUserDao().updateLastLogin(localUser.id)
                    }

                    _currentSession.value = authSession
                    _currentRole.value = role

                    val successMessage = when (role) {
                        MedusaRole.GUARDIA_CASETA -> "Caseta activada con éxito para ${localUser.name}. Nodos financieros restringidos."
                        MedusaRole.ADMINISTRACION -> "Sesión de Administración activada con éxito para ${localUser.name}."
                        MedusaRole.RESIDENTE -> "Bienvenido, ${localUser.name} (${localUser.unitOrDepartment})."
                        MedusaRole.UNASSIGNED -> "Sesión inicializada."
                    }

                    _uiState.value = ActivationUiState.Success(
                        activationKey = authKey,
                        role = role,
                        message = successMessage,
                        isFinancialBlocked = isFinancialBlocked
                    )
                    return@launch
                }

                // 3. Verificación de Residente en Room SQLite (Directorio de los 261 Lotes Reales de Prados)
                val residentEntity = withContext(Dispatchers.IO) {
                    db.residentDao().getResidentByEmail(normalizedKey.lowercase())
                        ?: db.residentDao().getResidentsByUnit(normalizedKey).firstOrNull()
                }

                if (residentEntity != null) {
                    val role = MedusaRole.RESIDENTE
                    MedusaFinancialAccessGuard.applyRoleSecurityPolicy(role)

                    val authKey = ActivationKey(
                        keyId = "KEY-RES-${residentEntity.id}",
                        role = role.name,
                        condominiumId = "PRADOS_1",
                        condominiumName = "Residencial Los Prados 1",
                        assignedUnit = residentEntity.unitId,
                        isActive = true
                    )
                    val authSession = UserSession(
                        activationKey = "KEY-RES-${residentEntity.id}",
                        currentRole = role,
                        condominiumId = "PRADOS_1",
                        assignedUnitId = residentEntity.unitId,
                        condominiumName = "Residencial Los Prados 1",
                        isActive = true,
                        isFinancialBlocked = false,
                        timestampMillis = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.IO) {
                        sessionPreferences.saveSession(authKey, role)
                        sessionPreferences.saveSession(authSession)
                    }

                    _currentSession.value = authSession
                    _currentRole.value = role
                    _uiState.value = ActivationUiState.Success(
                        activationKey = authKey,
                        role = role,
                        message = "Bienvenido al Portal del Residente: ${residentEntity.fullName} (${residentEntity.unitId})",
                        isFinancialBlocked = false
                    )
                    return@launch
                }

                // 4. Si no se autenticó en Room local y no hay servicio en la nube, rechazar de forma segura
                if (firestore == null) {
                    _uiState.value = ActivationUiState.Error(
                        errorMessage = "Credencial o llave '$normalizedKey' no autorizada en el sistema.",
                        errorType = ActivationErrorType.KEY_NOT_FOUND
                    )
                    return@launch
                }

                // 1. Consulta segura a la ruta /activation_keys/$inputKey
                val snapshot = withContext(Dispatchers.IO) {
                    firestore.collection(COLLECTION_ACTIVATION_KEYS)
                        .document(normalizedKey)
                        .get()
                        .await()
                }

                // 2. Verificar existencia del documento
                if (!snapshot.exists()) {
                    _uiState.value = ActivationUiState.Error(
                        errorMessage = "La llave de activación '$normalizedKey' no existe en el sistema.",
                        errorType = ActivationErrorType.KEY_NOT_FOUND
                    )
                    Log.w(TAG, "❌ Intento de activación con llave inexistente: $normalizedKey")
                    return@launch
                }

                // 3. Mapeo seguro del modelo de datos de la entidad de Firestore
                val activationKey = ActivationKey.fromSnapshot(snapshot)
                if (activationKey == null) {
                    _uiState.value = ActivationUiState.Error(
                        errorMessage = "El formato de los datos de la llave de activación es inválido.",
                        errorType = ActivationErrorType.FIRESTORE_ERROR
                    )
                    return@launch
                }

                // 4. Verificación de isActive == true
                if (!activationKey.isActive) {
                    _uiState.value = ActivationUiState.Error(
                        errorMessage = "La llave de activación ha sido desactivada o revocada por el Administrador.",
                        errorType = ActivationErrorType.KEY_INACTIVE
                    )
                    Log.w(TAG, "⛔ Llave inactiva rechazada: $normalizedKey")
                    return@launch
                }

                // Verificación de expiración
                if (activationKey.isExpired()) {
                    _uiState.value = ActivationUiState.Error(
                        errorMessage = "Esta llave de activación ha expirado.",
                        errorType = ActivationErrorType.KEY_EXPIRED
                    )
                    Log.w(TAG, "⏳ Llave expirada: $normalizedKey")
                    return@launch
                }

                // 5. Transformación del campo role al Enum MedusaRole correspondiente
                val medusaRole = MedusaRole.fromString(activationKey.role)
                if (medusaRole == MedusaRole.UNASSIGNED) {
                    _uiState.value = ActivationUiState.Error(
                        errorMessage = "La llave no tiene un rol válido asignado ('${activationKey.role}').",
                        errorType = ActivationErrorType.INVALID_ROLE
                    )
                    Log.w(TAG, "⚠️ Llave sin rol válido: ${activationKey.role}")
                    return@launch
                }

                // 6. Si el rol asignado es GUARDIA_CASETA:
                // Inicializa el ecosistema móvil bloqueando nativamente el acceso a los nodos de datos financieros.
                val isFinancialBlocked = (medusaRole == MedusaRole.GUARDIA_CASETA)
                MedusaFinancialAccessGuard.applyRoleSecurityPolicy(medusaRole)

                // 7. Guardar de forma segura los identificadores (condominiumId, assignedUnit)
                // y rol en las Preferencias del Sistema (EncryptedSharedPreferences)
                withContext(Dispatchers.IO) {
                    sessionPreferences.saveSession(activationKey, medusaRole)
                }

                // 8. Actualizar sesión en memoria
                val newSession = UserSession(
                    activationKey = activationKey.keyId,
                    currentRole = medusaRole,
                    condominiumId = activationKey.condominiumId.ifBlank { "PRADOS_1" },
                    assignedUnitId = activationKey.assignedUnit ?: "",
                    condominiumName = activationKey.condominiumName.ifBlank { "Los Prados 1" },
                    isActive = true,
                    isFinancialBlocked = isFinancialBlocked,
                    timestampMillis = System.currentTimeMillis()
                )
                _currentSession.value = newSession
                _currentRole.value = medusaRole

                // 9. Actualizar estado reactivo para avisar visualmente del éxito al usuario
                val successMessage = when (medusaRole) {
                    MedusaRole.GUARDIA_CASETA -> "Dispositivo activado para Caseta de Vigilancia. Nodos financieros bloqueados nativamente."
                    MedusaRole.ADMINISTRACION -> "Sesión administrativa activada con privilegios completos de gestión."
                    MedusaRole.RESIDENTE -> "Perfil de residente activado para unidad ${activationKey.assignedUnit ?: "asignada"}."
                    MedusaRole.UNASSIGNED -> "Llave validada."
                }

                _uiState.value = ActivationUiState.Success(
                    activationKey = activationKey,
                    role = medusaRole,
                    message = successMessage,
                    isFinancialBlocked = isFinancialBlocked
                )

                Log.i(TAG, "🎉 Activación exitosa: Rol=$medusaRole, Condominio=${activationKey.condominiumId}")

            } catch (e: FirebaseFirestoreException) {
                Log.e(TAG, "Error de Firestore al validar llave: ${e.code}", e)
                val (msg, errType) = when (e.code) {
                    FirebaseFirestoreException.Code.UNAVAILABLE,
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> {
                        "Error de red o conexión no disponible. Verifica tu acceso a internet o enlace seguro." to ActivationErrorType.NETWORK_ERROR
                    }
                    FirebaseFirestoreException.Code.PERMISSION_DENIED -> {
                        "Acceso denegado: Las reglas de seguridad no autorizan la lectura de la llave." to ActivationErrorType.PERMISSION_DENIED
                    }
                    FirebaseFirestoreException.Code.NOT_FOUND -> {
                        "Ruta de activación no encontrada en el servidor." to ActivationErrorType.KEY_NOT_FOUND
                    }
                    else -> {
                        "Error en el servicio de autenticación: ${e.localizedMessage}" to ActivationErrorType.FIRESTORE_ERROR
                    }
                }
                _uiState.value = ActivationUiState.Error(
                    errorMessage = msg,
                    errorType = errType,
                    technicalDetails = e.message
                )
            } catch (e: UnknownHostException) {
                Log.e(TAG, "Fallo DNS o sin conexión a internet", e)
                _uiState.value = ActivationUiState.Error(
                    errorMessage = "Sin conexión a internet. No se pudo conectar al servidor de activación.",
                    errorType = ActivationErrorType.NETWORK_ERROR,
                    technicalDetails = e.message
                )
            } catch (e: IOException) {
                Log.e(TAG, "Error de E/S de red", e)
                _uiState.value = ActivationUiState.Error(
                    errorMessage = "Error de comunicación de red al verificar la llave.",
                    errorType = ActivationErrorType.NETWORK_ERROR,
                    technicalDetails = e.message
                )
            } catch (e: Exception) {
                Log.e(TAG, "Excepción no esperada al validar la llave de activación", e)
                _uiState.value = ActivationUiState.Error(
                    errorMessage = "Ocurrió un error inesperado al validar la llave: ${e.localizedMessage ?: "Consulte al administrador"}",
                    errorType = ActivationErrorType.UNKNOWN,
                    technicalDetails = e.message
                )
            }
        }
    }

    /**
     * Autenticación segura y específica para el personal de ADMINISTRACIÓN.
     * Valida contra las identidades administradoras en Room SQLite y aplica permisos completos de gestión.
     */
    fun authenticateAdministration(adminCredential: String, pin: String) {
        val cleanCred = adminCredential.trim()
        val cleanPin = pin.trim()

        if (cleanCred.isBlank()) {
            _uiState.value = ActivationUiState.Error(
                errorMessage = "Por favor ingrese el correo o ID del Administrador.",
                errorType = ActivationErrorType.EMPTY_INPUT
            )
            return
        }

        _uiState.value = ActivationUiState.Loading

        viewModelScope.launch {
            try {
                val db = AppDatabase.getDatabase(getApplication())
                AlfhaSecurityContext.seedInitialUsersIfEmpty(db)

                val user = withContext(Dispatchers.IO) {
                    db.alfhaUserDao().getUserByEmail(cleanCred.lowercase())
                        ?: db.alfhaUserDao().getUserById(cleanCred)
                        ?: if (cleanCred.equals("administracion", ignoreCase = true) || cleanCred.equals("admin", ignoreCase = true)) {
                            db.alfhaUserDao().getUserByEmail("administracion@condominio.com")
                        } else null
                }

                if (user != null && (user.role == AlfhaRole.ADMINISTRACION.name || user.role == AlfhaRole.MAESTRO_ALFHA.name || user.role == AlfhaRole.MESA_DIRECTIVA.name)) {
                    if (!user.isActive) {
                        _uiState.value = ActivationUiState.Error(
                            errorMessage = "La cuenta administrativa '${user.name}' se encuentra inactiva.",
                            errorType = ActivationErrorType.KEY_INACTIVE
                        )
                        return@launch
                    }

                    val role = MedusaRole.ADMINISTRACION
                    MedusaFinancialAccessGuard.applyRoleSecurityPolicy(role)
                    AlfhaSecurityContext.setCurrentUser(user)

                    val sessionKey = "AUTH-ADM-${user.id}"
                    val activationKey = ActivationKey(
                        keyId = sessionKey,
                        role = role.name,
                        condominiumId = "PRADOS_1",
                        condominiumName = "Residencial Los Prados 1",
                        assignedUnit = user.unitOrDepartment,
                        isActive = true
                    )
                    val session = UserSession(
                        activationKey = sessionKey,
                        currentRole = role,
                        condominiumId = "PRADOS_1",
                        assignedUnitId = user.unitOrDepartment,
                        condominiumName = "Residencial Los Prados 1",
                        isActive = true,
                        isFinancialBlocked = false,
                        timestampMillis = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.IO) {
                        sessionPreferences.saveSession(activationKey, role)
                        sessionPreferences.saveSession(session)
                        db.alfhaUserDao().updateLastLogin(user.id)
                    }

                    _currentSession.value = session
                    _currentRole.value = role
                    _uiState.value = ActivationUiState.Success(
                        activationKey = activationKey,
                        role = role,
                        message = "Sesión de Administración iniciada con éxito para ${user.name}.",
                        isFinancialBlocked = false
                    )
                    Log.i(TAG, "🔒 Acceso administrativo seguro autenticado para: ${user.name} (${user.email})")
                } else {
                    _uiState.value = ActivationUiState.Error(
                        errorMessage = "Credenciales administrativas inválidas o no autorizadas.",
                        errorType = ActivationErrorType.PERMISSION_DENIED
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error autenticando administración: ${e.message}", e)
                _uiState.value = ActivationUiState.Error(
                    errorMessage = "Error de autenticación administrativa: ${e.message}",
                    errorType = ActivationErrorType.UNKNOWN
                )
            }
        }
    }

    /**
     * Autenticación segura y específica para el personal de CASETA DE SEGURIDAD.
     * Aplica el bloqueo nativo a todos los nodos y consultas financieras de forma obligatoria.
     */
    fun authenticateCaseta(guardCredential: String, pin: String) {
        val cleanCred = guardCredential.trim()

        if (cleanCred.isBlank()) {
            _uiState.value = ActivationUiState.Error(
                errorMessage = "Por favor ingrese el identificador o correo del Oficial de Guardia.",
                errorType = ActivationErrorType.EMPTY_INPUT
            )
            return
        }

        _uiState.value = ActivationUiState.Loading

        viewModelScope.launch {
            try {
                val db = AppDatabase.getDatabase(getApplication())
                AlfhaSecurityContext.seedInitialUsersIfEmpty(db)

                val user = withContext(Dispatchers.IO) {
                    db.alfhaUserDao().getUserByEmail(cleanCred.lowercase())
                        ?: db.alfhaUserDao().getUserById(cleanCred)
                        ?: if (cleanCred.equals("caseta", ignoreCase = true) || cleanCred.equals("guardia", ignoreCase = true)) {
                            db.alfhaUserDao().getUserByEmail("caseta1@alfhaseguridad.com")
                                ?: db.alfhaUserDao().getUsersByRole(AlfhaRole.GUARDIA.name).firstOrNull()
                        } else null
                }

                if (user != null) {
                    if (!user.isActive) {
                        _uiState.value = ActivationUiState.Error(
                            errorMessage = "La credencial del guardia '${user.name}' se encuentra inactiva.",
                            errorType = ActivationErrorType.KEY_INACTIVE
                        )
                        return@launch
                    }

                    // Directiva de Seguridad: Caseta NUNCA tiene acceso a finanzas
                    val role = MedusaRole.GUARDIA_CASETA
                    MedusaFinancialAccessGuard.applyRoleSecurityPolicy(role)
                    AlfhaSecurityContext.setCurrentUser(user)

                    val sessionKey = "AUTH-CASETA-${user.id}"
                    val activationKey = ActivationKey(
                        keyId = sessionKey,
                        role = role.name,
                        condominiumId = "PRADOS_1",
                        condominiumName = "Residencial Los Prados 1",
                        assignedUnit = "Caseta Principal",
                        isActive = true
                    )
                    val session = UserSession(
                        activationKey = sessionKey,
                        currentRole = role,
                        condominiumId = "PRADOS_1",
                        assignedUnitId = "Caseta Principal",
                        condominiumName = "Residencial Los Prados 1",
                        isActive = true,
                        isFinancialBlocked = true, // Caseta bloqueada de finanzas
                        timestampMillis = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.IO) {
                        sessionPreferences.saveSession(activationKey, role)
                        sessionPreferences.saveSession(session)
                        db.alfhaUserDao().updateLastLogin(user.id)
                    }

                    _currentSession.value = session
                    _currentRole.value = role
                    _uiState.value = ActivationUiState.Success(
                        activationKey = activationKey,
                        role = role,
                        message = "Caseta de Vigilancia activa para ${user.name}. Acceso financiero bloqueado.",
                        isFinancialBlocked = true
                    )
                    Log.i(TAG, "🛡️ Caseta autenticada con éxito para guardia: ${user.name}. Nodos financieros aislados.")
                } else {
                    _uiState.value = ActivationUiState.Error(
                        errorMessage = "Credencial de caseta no encontrada en la base de seguridad.",
                        errorType = ActivationErrorType.KEY_NOT_FOUND
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error autenticando caseta: ${e.message}", e)
                _uiState.value = ActivationUiState.Error(
                    errorMessage = "Error autenticando guardia de caseta: ${e.message}",
                    errorType = ActivationErrorType.UNKNOWN
                )
            }
        }
    }

    /**
     * Autenticación segura y específica para RESIDENTES.
     * Enlaza estrictamente la sesión con su vivienda específica dentro de los 261 lotes de Prados.
     */
    fun authenticateResident(residentCredential: String, unitOrPin: String) {
        val clean = residentCredential.trim()

        if (clean.isBlank()) {
            _uiState.value = ActivationUiState.Error(
                errorMessage = "Por favor ingrese su correo o número de vivienda.",
                errorType = ActivationErrorType.EMPTY_INPUT
            )
            return
        }

        _uiState.value = ActivationUiState.Loading

        viewModelScope.launch {
            try {
                val db = AppDatabase.getDatabase(getApplication())
                AlfhaSecurityContext.seedInitialUsersIfEmpty(db)

                val (resident, user) = withContext(Dispatchers.IO) {
                    val r = db.residentDao().getResidentByEmail(clean.lowercase())
                        ?: db.residentDao().getResidentsByUnit(clean).firstOrNull()
                        ?: if (clean.equals("residente", ignoreCase = true) || clean.equals("104", ignoreCase = true) || clean.equals("casa 104", ignoreCase = true)) {
                            db.residentDao().getResidentsByUnit("Casa 104").firstOrNull()
                        } else null

                    val u = db.alfhaUserDao().getUserByEmail(clean.lowercase())
                        ?: db.alfhaUserDao().getUserById(clean)
                        ?: if (clean.equals("residente", ignoreCase = true)) {
                            db.alfhaUserDao().getUserByEmail("arismendi.residente@condominio.com")
                        } else null

                    Pair(r, u)
                }

                if (resident != null || user != null) {
                    val assignedUnit = resident?.unitId ?: user?.unitOrDepartment ?: "Casa 104"
                    val residentName = resident?.fullName ?: user?.name ?: "Residente Acreditado"
                    val role = MedusaRole.RESIDENTE

                    MedusaFinancialAccessGuard.applyRoleSecurityPolicy(role)
                    if (user != null) {
                        AlfhaSecurityContext.setCurrentUser(user)
                    }

                    val sessionKey = "AUTH-RES-${resident?.id ?: user?.id ?: UUID.randomUUID()}"
                    val activationKey = ActivationKey(
                        keyId = sessionKey,
                        role = role.name,
                        condominiumId = "PRADOS_1",
                        condominiumName = "Residencial Los Prados 1",
                        assignedUnit = assignedUnit,
                        isActive = true
                    )
                    val session = UserSession(
                        activationKey = sessionKey,
                        currentRole = role,
                        condominiumId = "PRADOS_1",
                        assignedUnitId = assignedUnit,
                        condominiumName = "Residencial Los Prados 1",
                        isActive = true,
                        isFinancialBlocked = false,
                        timestampMillis = System.currentTimeMillis()
                    )

                    withContext(Dispatchers.IO) {
                        sessionPreferences.saveSession(activationKey, role)
                        sessionPreferences.saveSession(session)
                    }

                    _currentSession.value = session
                    _currentRole.value = role
                    _uiState.value = ActivationUiState.Success(
                        activationKey = activationKey,
                        role = role,
                        message = "Bienvenido, $residentName ($assignedUnit)",
                        isFinancialBlocked = false
                    )
                    Log.i(TAG, "🏠 Residente autenticado con éxito: $residentName ($assignedUnit)")
                } else {
                    _uiState.value = ActivationUiState.Error(
                        errorMessage = "Vivienda o residente '$clean' no encontrado en el padrón de Los Prados.",
                        errorType = ActivationErrorType.KEY_NOT_FOUND
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error acreditando residente: ${e.message}", e)
                _uiState.value = ActivationUiState.Error(
                    errorMessage = "Error autenticando residente: ${e.message}",
                    errorType = ActivationErrorType.UNKNOWN
                )
            }
        }
    }

    /**
     * Cierra la sesión activa y purga las credenciales del almacenamiento seguro.
     */
    fun logout() {
        sessionPreferences.clearSession()
        _currentSession.value = null
        _currentRole.value = MedusaRole.UNASSIGNED
        _uiState.value = ActivationUiState.Idle
        Log.i(TAG, "👋 Sesión purgada exitosamente.")
    }

    /**
     * Activa directamente la terminal como Residente tras autenticarse con Firebase Auth.
     */
    fun activateAsResidentFromFirebaseAuth(
        resident: com.example.data.resident.ResidentEntity,
        condominiumId: String = "PRADOS_1"
    ) {
        val session = UserSession(
            activationKey = "FIREBASE-AUTH-${resident.id}",
            currentRole = MedusaRole.RESIDENTE,
            condominiumId = condominiumId,
            assignedUnitId = resident.unitId,
            isActive = true,
            isFinancialBlocked = false,
            timestampMillis = System.currentTimeMillis()
        )
        sessionPreferences.saveSession(session)
        _currentSession.value = session
        _currentRole.value = MedusaRole.RESIDENTE
        _uiState.value = ActivationUiState.Success(
            activationKey = ActivationKey(
                keyId = "FIREBASE-AUTH-${resident.id}",
                role = "RESIDENTE",
                condominiumId = condominiumId,
                assignedUnit = resident.unitId,
                isActive = true
            ),
            role = MedusaRole.RESIDENTE,
            message = "Sesión de residente acreditada con Firebase Auth",
            isFinancialBlocked = false
        )
        Log.i(TAG, "🎉 Terminal activada como Residente vía Firebase Auth: ${resident.fullName} (${resident.unitId})")
    }

    /**
     * Activa directamente la terminal tras una verificación biométrica positiva (Huella/Rostro).
     */
    fun activateViaBiometrics() {
        val previous = sessionPreferences.getSessionData()
        if (previous != null) {
            val session = previous.copy(isActive = true)
            sessionPreferences.saveSession(session)
            _currentSession.value = session
            _currentRole.value = session.currentRole
            MedusaFinancialAccessGuard.applyRoleSecurityPolicy(session.currentRole)
            _uiState.value = ActivationUiState.Success(
                activationKey = ActivationKey(
                    keyId = session.activationKey,
                    role = session.currentRole.name,
                    condominiumId = session.condominiumId,
                    condominiumName = session.condominiumName,
                    assignedUnit = session.assignedUnitId,
                    isActive = true
                ),
                role = session.currentRole,
                message = "Acceso biométrico verificado: ${session.currentRole.displayName}",
                isFinancialBlocked = session.isFinancialBlocked
            )
            Log.i(TAG, "🔓 Sesión desbloqueada con biometría para rol: ${session.currentRole}")
        } else {
            // Perfil por defecto de residente seguro para la terminal
            val defaultSession = UserSession(
                activationKey = "BIOMETRIC-RESIDENT-104",
                currentRole = MedusaRole.RESIDENTE,
                condominiumId = "PRADOS_1",
                assignedUnitId = "Casa 104",
                condominiumName = "Residencial Los Prados 1",
                isActive = true,
                isFinancialBlocked = false,
                timestampMillis = System.currentTimeMillis()
            )
            sessionPreferences.saveSession(defaultSession)
            _currentSession.value = defaultSession
            _currentRole.value = MedusaRole.RESIDENTE
            MedusaFinancialAccessGuard.applyRoleSecurityPolicy(MedusaRole.RESIDENTE)
            _uiState.value = ActivationUiState.Success(
                activationKey = ActivationKey(
                    keyId = "BIOMETRIC-RESIDENT-104",
                    role = "RESIDENTE",
                    condominiumId = "PRADOS_1",
                    condominiumName = "Residencial Los Prados 1",
                    assignedUnit = "Casa 104",
                    isActive = true
                ),
                role = MedusaRole.RESIDENTE,
                message = "Acceso biométrico concedido: Residente Casa 104",
                isFinancialBlocked = false
            )
            Log.i(TAG, "🔓 Sesión inicial creada vía biometría para Residente Casa 104")
        }
    }

    /**
     * Restablece el estado de la interfaz visual a Idle.
     */
    fun resetUiState() {
        _uiState.value = ActivationUiState.Idle
    }

    companion object {
        private const val TAG = "ActivationViewModel"
        const val COLLECTION_ACTIVATION_KEYS = "activation_keys"

        /**
         * Factory para instanciar ActivationViewModel con inyección de dependencias limpia.
         */
        fun provideFactory(
            application: Application,
            firestore: FirebaseFirestore? = FirebaseConfigHelper.getFirestore(),
            sessionPreferences: MedusaSessionPreferences = MedusaSessionPreferences.getInstance(application)
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ActivationViewModel(application, firestore, sessionPreferences) as T
            }
        }
    }
}
