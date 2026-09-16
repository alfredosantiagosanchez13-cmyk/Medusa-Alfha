package com.example.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.data.firebase.AuthUiState
import com.example.data.firebase.FirebaseConfigHelper
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * Interfaz y especificación del proveedor de autenticación Firebase con soporte nativo para
 * Google Sign-In mediante Jetpack Credential Manager.
 */
interface FirebaseAuthProvider {

    /**
     * Instancia subyacente de FirebaseAuth si Firebase está disponible en el entorno actual.
     */
    val auth: FirebaseAuth?

    /**
     * Usuario actual de Firebase Auth si hay una sesión activa.
     */
    val currentUser: FirebaseUser?

    /**
     * Identificador único (UID) del usuario autenticado actual o null.
     */
    val currentUserId: String?

    /**
     * Correo electrónico del usuario autenticado actual o null.
     */
    val currentUserEmail: String?

    /**
     * Nombre de visualización del usuario actual o null.
     */
    val currentUserDisplayName: String?

    /**
     * Indica si existe una sesión de usuario activa en Firebase Auth.
     */
    val isAuthenticated: Boolean

    /**
     * Flujo de estado reactivo de la interfaz de autenticación.
     */
    val authState: StateFlow<AuthUiState>

    /**
     * Indica si el proveedor cuenta con Firebase inicializado y activo.
     */
    val isInitialized: Boolean

    /**
     * Inicia sesión con Google utilizando Jetpack CredentialManager y GoogleIdTokenCredential.
     * @param context Contexto de UI (preferentemente Activity) para invocar el selector de credenciales.
     * @param serverClientId ID de cliente web de Google Cloud Console / Firebase. Si es nulo o vacío,
     *                       se utiliza el valor predeterminado del recurso R.string.default_web_client_id.
     */
    suspend fun signInWithGoogle(context: Context, serverClientId: String? = null): Result<FirebaseUser>

    /**
     * Inicia sesión directamente en Firebase Auth usando un ID Token emitido por Google Sign-In.
     */
    suspend fun signInWithGoogleIdToken(idToken: String): Result<FirebaseUser>

    /**
     * Inicia sesión en Firebase con cualquier credencial compatible de Firebase Auth (AuthCredential).
     */
    suspend fun signInWithCredential(credential: AuthCredential): Result<FirebaseUser>

    /**
     * Inicia sesión con Correo Electrónico y Contraseña.
     */
    suspend fun signInWithEmail(email: String, pass: String): Result<FirebaseUser>

    /**
     * Registra un nuevo usuario en Firebase Auth con Correo, Contraseña y Nombre.
     */
    suspend fun signUpWithEmail(email: String, pass: String, displayName: String): Result<FirebaseUser>

    /**
     * Autenticación anónima para modo táctico de contingencia o acceso temporal.
     */
    suspend fun signInAnonymously(): Result<FirebaseUser>

    /**
     * Envía correo de recuperación / restablecimiento de contraseña.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit>

    /**
     * Cierra la sesión activa en Firebase Auth y limpia el estado local.
     */
    fun signOut()

    companion object {
        private const val TAG = "FirebaseAuthProvider"

        @Volatile
        private var instance: FirebaseAuthProvider? = null

        /**
         * Inicializa el singleton de FirebaseAuthProvider dentro de MainApplication.onCreate().
         */
        fun initialize(context: Context): FirebaseAuthProvider {
            return instance ?: synchronized(this) {
                instance ?: DefaultFirebaseAuthProvider(context.applicationContext).also {
                    instance = it
                    Log.i(TAG, "FirebaseAuthProvider successfully initialized.")
                }
            }
        }

        /**
         * Obtiene la instancia activa de FirebaseAuthProvider.
         * @throws IllegalStateException si no ha sido inicializado mediante initialize(context).
         */
        fun getInstance(): FirebaseAuthProvider {
            return instance ?: throw IllegalStateException(
                "FirebaseAuthProvider must be initialized before use. Call FirebaseAuthProvider.initialize(context) in MainApplication.onCreate()."
            )
        }

        /**
         * Retorna la instancia activa o null si no ha sido inicializada.
         */
        fun getOrNull(): FirebaseAuthProvider? = instance

        /**
         * Permite inyectar o sustituir la instancia activa (útil para pruebas unitarias y mocks).
         */
        fun setInstance(provider: FirebaseAuthProvider?) {
            instance = provider
        }
    }
}

/**
 * Función fábrica para permitir instanciar FirebaseAuthProvider(context) directamente.
 */
fun FirebaseAuthProvider(context: Context): FirebaseAuthProvider = DefaultFirebaseAuthProvider(context)

/**
 * Implementación por defecto de FirebaseAuthProvider.
 */
open class DefaultFirebaseAuthProvider(
    private val appContext: Context
) : FirebaseAuthProvider {

    private val tag = "DefaultFirebaseAuthProv"

    private val credentialManager: CredentialManager? by lazy {
        try {
            CredentialManager.create(appContext)
        } catch (e: Exception) {
            Log.w(tag, "CredentialManager initialization warning: ${e.message}")
            null
        }
    }

    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    override val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    override val auth: FirebaseAuth?
        get() = FirebaseConfigHelper.getAuth()

    override val currentUser: FirebaseUser?
        get() = auth?.currentUser

    override val currentUserId: String?
        get() = currentUser?.uid

    override val currentUserEmail: String?
        get() = currentUser?.email

    override val currentUserDisplayName: String?
        get() = currentUser?.displayName

    override val isAuthenticated: Boolean
        get() = currentUser != null

    override val isInitialized: Boolean
        get() = auth != null

    init {
        checkCurrentSession()
    }

    private fun checkCurrentSession() {
        val currentAuth = auth
        if (currentAuth == null) {
            _authState.value = AuthUiState.Unauthenticated("Firebase Auth no disponible en este entorno.")
            return
        }

        val user = currentAuth.currentUser
        if (user != null) {
            _authState.value = AuthUiState.Authenticated(user)
        } else {
            _authState.value = AuthUiState.Unauthenticated()
        }
    }

    override suspend fun signInWithGoogle(context: Context, serverClientId: String?): Result<FirebaseUser> {
        _authState.value = AuthUiState.Loading

        val currentAuth = auth
        if (currentAuth == null) {
            val error = "Firebase no está inicializado o google-services.json no está configurado."
            _authState.value = AuthUiState.Error(error)
            return Result.failure(IllegalStateException(error))
        }

        val targetServerClientId = when {
            !serverClientId.isNullOrBlank() -> serverClientId.trim()
            else -> try {
                context.getString(R.string.default_web_client_id)
            } catch (e: Exception) {
                null
            }
        }

        if (targetServerClientId.isNullOrBlank()) {
            val error = "Web Client ID de Google no configurado. Configure default_web_client_id en strings.xml o pase serverClientId."
            _authState.value = AuthUiState.Error(error)
            return Result.failure(IllegalArgumentException(error))
        }

        val credManager = try {
            CredentialManager.create(context)
        } catch (e: Exception) {
            credentialManager
        }

        if (credManager == null) {
            val error = "CredentialManager no está disponible en este dispositivo."
            _authState.value = AuthUiState.Error(error)
            return Result.failure(IllegalStateException(error))
        }

        return try {
            val googleIdOption = GetSignInWithGoogleOption.Builder(targetServerClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result: GetCredentialResponse = credManager.getCredential(
                request = request,
                context = context
            )

            val credential = result.credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
                signInWithGoogleIdToken(googleIdToken.idToken)
            } else {
                val error = "Tipo de credencial no soportado: ${credential::class.java.simpleName}"
                _authState.value = AuthUiState.Error(error)
                Result.failure(IllegalArgumentException(error))
            }
        } catch (e: GetCredentialCancellationException) {
            _authState.value = AuthUiState.Unauthenticated("Inicio de sesión con Google cancelado por el usuario.")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "Error en Google Sign-In: ${e.message}", e)
            val msg = e.localizedMessage ?: "Error al autenticar con Google"
            _authState.value = AuthUiState.Error(msg)
            Result.failure(e)
        }
    }

    override suspend fun signInWithGoogleIdToken(idToken: String): Result<FirebaseUser> {
        val authCredential = GoogleAuthProvider.getCredential(idToken, null)
        return signInWithCredential(authCredential)
    }

    override suspend fun signInWithCredential(credential: AuthCredential): Result<FirebaseUser> {
        _authState.value = AuthUiState.Loading
        val currentAuth = auth
        if (currentAuth == null) {
            val error = "Firebase Auth no disponible."
            _authState.value = AuthUiState.Error(error)
            return Result.failure(IllegalStateException(error))
        }

        return try {
            val authResult: AuthResult = currentAuth.signInWithCredential(credential).await()
            val user = authResult.user ?: throw IllegalStateException("FirebaseUser no retornado por Firebase Auth.")
            _authState.value = AuthUiState.Authenticated(user)
            Result.success(user)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Fallo de autenticación con credenciales"
            _authState.value = AuthUiState.Error(msg)
            Result.failure(e)
        }
    }

    override suspend fun signInWithEmail(email: String, pass: String): Result<FirebaseUser> {
        _authState.value = AuthUiState.Loading
        val currentAuth = auth
        if (currentAuth == null) {
            val error = "Firebase no está inicializado."
            _authState.value = AuthUiState.Error(error)
            return Result.failure(IllegalStateException(error))
        }

        return try {
            val result = currentAuth.signInWithEmailAndPassword(email.trim(), pass).await()
            val user = result.user ?: throw IllegalStateException("Usuario no encontrado.")
            _authState.value = AuthUiState.Authenticated(user)
            Result.success(user)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Error al autenticar con correo y contraseña"
            _authState.value = AuthUiState.Error(msg)
            Result.failure(e)
        }
    }

    override suspend fun signUpWithEmail(email: String, pass: String, displayName: String): Result<FirebaseUser> {
        _authState.value = AuthUiState.Loading
        val currentAuth = auth
        if (currentAuth == null) {
            val error = "Firebase no está inicializado."
            _authState.value = AuthUiState.Error(error)
            return Result.failure(IllegalStateException(error))
        }

        return try {
            val result = currentAuth.createUserWithEmailAndPassword(email.trim(), pass).await()
            val user = result.user ?: throw IllegalStateException("Usuario no creado.")
            if (displayName.isNotBlank()) {
                try {
                    val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                        .setDisplayName(displayName.trim())
                        .build()
                    user.updateProfile(profileUpdates).await()
                } catch (e: Exception) {
                    Log.w(tag, "No se pudo actualizar el nombre del perfil: ${e.message}")
                }
            }
            _authState.value = AuthUiState.Authenticated(user)
            Result.success(user)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Error al registrar usuario en Firebase"
            _authState.value = AuthUiState.Error(msg)
            Result.failure(e)
        }
    }

    override suspend fun signInAnonymously(): Result<FirebaseUser> {
        _authState.value = AuthUiState.Loading
        val currentAuth = auth
        if (currentAuth == null) {
            val error = "Firebase no disponible."
            _authState.value = AuthUiState.Error(error)
            return Result.failure(IllegalStateException(error))
        }

        return try {
            val result = currentAuth.signInAnonymously().await()
            val user = result.user ?: throw IllegalStateException("Sesión anónima fallida.")
            _authState.value = AuthUiState.Authenticated(user)
            Result.success(user)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Error en acceso de invitado anónimo"
            _authState.value = AuthUiState.Error(msg)
            Result.failure(e)
        }
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        val currentAuth = auth ?: return Result.failure(IllegalStateException("Firebase no disponible"))
        return try {
            currentAuth.sendPasswordResetEmail(email.trim()).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.w(tag, "Error durante signOut: ${e.message}")
        }
        _authState.value = AuthUiState.Unauthenticated()
    }
}
