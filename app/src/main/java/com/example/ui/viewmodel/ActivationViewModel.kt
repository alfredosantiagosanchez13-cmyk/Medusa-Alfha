package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.auth.ActivationKey
import com.example.data.auth.MedusaFinancialAccessGuard
import com.example.data.auth.MedusaRole
import com.example.data.auth.MedusaSessionData
import com.example.data.auth.MedusaSessionPreferences
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.UnknownHostException

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
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val sessionPreferences: MedusaSessionPreferences = MedusaSessionPreferences.getInstance(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<ActivationUiState>(ActivationUiState.Idle)
    val uiState: StateFlow<ActivationUiState> = _uiState.asStateFlow()

    private val _currentSession = MutableStateFlow<MedusaSessionData?>(null)
    val currentSession: StateFlow<MedusaSessionData?> = _currentSession.asStateFlow()

    init {
        checkExistingSession()
    }

    /**
     * Verifica si el dispositivo ya cuenta con una sesión activa en SharedPreferences.
     * Permite que la aplicación recuerde el perfil en los siguientes inicios de sesión.
     */
    fun checkExistingSession() {
        val existingSession = sessionPreferences.getSessionData()
        if (existingSession != null && existingSession.isActive) {
            _currentSession.value = existingSession
            // Restablece la política de seguridad nativa para el rol recordado
            MedusaFinancialAccessGuard.applyRoleSecurityPolicy(existingSession.role)
            _uiState.value = ActivationUiState.Success(
                activationKey = ActivationKey(
                    keyId = existingSession.keyId,
                    role = existingSession.role.name,
                    condominiumId = existingSession.condominiumId,
                    assignedUnit = existingSession.assignedUnit,
                    isActive = true
                ),
                role = existingSession.role,
                message = "Sesión activa restaurada: ${existingSession.role.displayName}",
                isFinancialBlocked = existingSession.isFinancialBlocked
            )
            Log.i(TAG, "🔄 Sesión previa restaurada con éxito para condominio: ${existingSession.condominiumId}")
        }
    }

    /**
     * Implementa la función principal requerida:
     * Realiza una consulta segura a la ruta de Firestore `/activation_keys/$inputKey`.
     */
    fun validateActivationKey(inputKey: String) {
        val sanitizedKey = inputKey.trim()

        if (sanitizedKey.isBlank()) {
            _uiState.value = ActivationUiState.Error(
                errorMessage = "Por favor, introduce una llave de activación válida.",
                errorType = ActivationErrorType.EMPTY_INPUT
            )
            return
        }

        _uiState.value = ActivationUiState.Loading

        viewModelScope.launch {
            try {
                // 1. Consulta segura a la ruta /activation_keys/$inputKey
                val snapshot = withContext(Dispatchers.IO) {
                    firestore.collection(COLLECTION_ACTIVATION_KEYS)
                        .document(sanitizedKey)
                        .get()
                        .await()
                }

                // 2. Verificar existencia del documento
                if (!snapshot.exists()) {
                    _uiState.value = ActivationUiState.Error(
                        errorMessage = "La llave de activación '$sanitizedKey' no existe en el sistema.",
                        errorType = ActivationErrorType.KEY_NOT_FOUND
                    )
                    Log.w(TAG, "❌ Intento de activación con llave inexistente: $sanitizedKey")
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
                    Log.w(TAG, "⛔ Llave inactiva rechazada: $sanitizedKey")
                    return@launch
                }

                // Verificación de expiración
                if (activationKey.isExpired()) {
                    _uiState.value = ActivationUiState.Error(
                        errorMessage = "Esta llave de activación ha expirado.",
                        errorType = ActivationErrorType.KEY_EXPIRED
                    )
                    Log.w(TAG, "⏳ Llave expirada: $sanitizedKey")
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
                val newSession = MedusaSessionData(
                    keyId = activationKey.keyId,
                    role = medusaRole,
                    condominiumId = activationKey.condominiumId,
                    assignedUnit = activationKey.assignedUnit,
                    isActive = true,
                    isFinancialBlocked = isFinancialBlocked,
                    activatedAtMillis = System.currentTimeMillis()
                )
                _currentSession.value = newSession

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
     * Cierra la sesión activa y purga las credenciales del almacenamiento seguro.
     */
    fun logout() {
        sessionPreferences.clearSession()
        _currentSession.value = null
        _uiState.value = ActivationUiState.Idle
        Log.i(TAG, "👋 Sesión purgada exitosamente.")
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
            firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
            sessionPreferences: MedusaSessionPreferences = MedusaSessionPreferences.getInstance(application)
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ActivationViewModel(application, firestore, sessionPreferences) as T
            }
        }
    }
}
