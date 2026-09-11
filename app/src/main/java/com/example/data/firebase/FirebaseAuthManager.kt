package com.example.data.firebase

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import com.example.auth.AlfhaRole
import com.example.auth.AlfhaSecurityContext
import com.example.data.auth.AlfhaUserEntity
import com.example.data.booking.AppDatabase

/**
 * Estado de autenticación del usuario en Firebase.
 */
sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Authenticated(val user: FirebaseUser) : AuthUiState()
    data class Unauthenticated(val message: String? = null) : AuthUiState()
    data class Error(val errorMessage: String) : AuthUiState()
}

/**
 * Administrador de autenticación Firebase con soporte para:
 * 1. Google Sign-In mediante Jetpack CredentialManager (MANDATARIO para Firestore)
 * 2. Autenticación por Correo / Contraseña
 * 3. Gestión de sesión y cierre seguro
 */
class FirebaseAuthManager(
    private val context: Context
) {
    private val tag = "FirebaseAuthManager"
    private val credentialManager: CredentialManager? by lazy {
        try {
            CredentialManager.create(context)
        } catch (e: Exception) {
            Log.w(tag, "CredentialManager no pudo ser inicializado: ${e.message}")
            null
        }
    }

    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    private val auth: FirebaseAuth?
        get() = FirebaseConfigHelper.getAuth()

    init {
        checkCurrentSession()
    }

    fun checkCurrentSession() {
        val currentAuth = auth
        if (currentAuth == null) {
            _authState.value = AuthUiState.Unauthenticated("Firebase Auth no disponible en este entorno.")
            return
        }

        val currentUser = currentAuth.currentUser
        if (currentUser != null) {
            _authState.value = AuthUiState.Authenticated(currentUser)
        } else {
            _authState.value = AuthUiState.Unauthenticated()
        }
    }

    /**
     * Inicia sesión con Google usando Jetpack CredentialManager y GoogleIdTokenCredential.
     * @param serverClientId Web Client ID de Google Cloud Console / Firebase.
     */
    suspend fun signInWithGoogle(serverClientId: String): Result<FirebaseUser> {
        _authState.value = AuthUiState.Loading
        val currentAuth = auth
        if (currentAuth == null) {
            val error = "Firebase no está inicializado. Configure google-services.json."
            _authState.value = AuthUiState.Error(error)
            return Result.failure(IllegalStateException(error))
        }

        val credManager = credentialManager
        if (credManager == null) {
            val errorMsg = "Credential Manager no está disponible en este dispositivo."
            _authState.value = AuthUiState.Error(errorMsg)
            return Result.failure(IllegalStateException(errorMsg))
        }

        return try {
            val googleIdOption = GetSignInWithGoogleOption.Builder(serverClientId)
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
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken.idToken, null)
                val authResult: AuthResult = currentAuth.signInWithCredential(authCredential).await()
                val user = authResult.user ?: throw IllegalStateException("FirebaseUser no retornado.")
                _authState.value = AuthUiState.Authenticated(user)
                Result.success(user)
            } else {
                val error = "Tipo de credencial no soportado."
                _authState.value = AuthUiState.Error(error)
                Result.failure(IllegalArgumentException(error))
            }
        } catch (e: GetCredentialCancellationException) {
            _authState.value = AuthUiState.Unauthenticated("Inicio de sesión cancelado por el usuario.")
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "Error en Google Sign-In: ${e.message}", e)
            val msg = e.localizedMessage ?: "Error al autenticar con Google"
            _authState.value = AuthUiState.Error(msg)
            Result.failure(e)
        }
    }

    /**
     * Inicia sesión con Email y Contraseña.
     */
    suspend fun signInWithEmail(email: String, pass: String): Result<FirebaseUser> {
        _authState.value = AuthUiState.Loading
        val currentAuth = auth
        if (currentAuth == null) {
            val error = "Firebase no está inicializado."
            _authState.value = AuthUiState.Error(error)
            return Result.failure(IllegalStateException(error))
        }

        return try {
            val result = currentAuth.signInWithEmailAndPassword(email, pass).await()
            val user = result.user ?: throw IllegalStateException("Usuario no encontrado.")
            _authState.value = AuthUiState.Authenticated(user)
            Result.success(user)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Error al autenticar credenciales"
            _authState.value = AuthUiState.Error(msg)
            Result.failure(e)
        }
    }

    val currentUser: FirebaseUser?
        get() = auth?.currentUser

    /**
     * Registra un nuevo usuario con Email, Contraseña y Nombre.
     */
    suspend fun signUpWithEmail(email: String, pass: String, displayName: String): Result<FirebaseUser> {
        _authState.value = AuthUiState.Loading
        val currentAuth = auth
        if (currentAuth == null) {
            val error = "Firebase no está inicializado."
            _authState.value = AuthUiState.Error(error)
            return Result.failure(IllegalStateException(error))
        }

        return try {
            val result = currentAuth.createUserWithEmailAndPassword(email, pass).await()
            val user = result.user ?: throw IllegalStateException("Usuario no creado.")
            try {
                val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()
                user.updateProfile(profileUpdates).await()
            } catch (e: Exception) {
                Log.w(tag, "No se pudo actualizar el nombre del perfil: ${e.message}")
            }
            _authState.value = AuthUiState.Authenticated(user)
            Result.success(user)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Error al registrar usuario en Firebase"
            _authState.value = AuthUiState.Error(msg)
            Result.failure(e)
        }
    }

    /**
     * Envía correo para restablecer contraseña.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> {
        val currentAuth = auth ?: return Result.failure(IllegalStateException("Firebase no disponible"))
        return try {
            currentAuth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Autenticación anónima para modo operativo táctico rápido.
     */
    suspend fun signInAnonymously(): Result<FirebaseUser> {
        _authState.value = AuthUiState.Loading
        val currentAuth = auth
        if (currentAuth == null) {
            val error = "Firebase no disponible."
            _authState.value = AuthUiState.Error(error)
            return Result.failure(IllegalStateException(error))
        }

        return try {
            val result = currentAuth.signInAnonymously().await()
            val user = result.user ?: throw IllegalStateException("Sesión anónima fallida")
            _authState.value = AuthUiState.Authenticated(user)
            Result.success(user)
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Error en acceso de invitado"
            _authState.value = AuthUiState.Error(msg)
            Result.failure(e)
        }
    }

    /**
     * Sincroniza el usuario autenticado de Firebase con la tabla Room `alfha_users`
     * y el contexto de seguridad RBAC `AlfhaSecurityContext`.
     */
    suspend fun syncFirebaseUserToLocalRoom(
        db: AppDatabase,
        firebaseUser: FirebaseUser,
        targetRole: com.example.auth.AlfhaRole = com.example.auth.AlfhaRole.RESIDENTE
    ): com.example.data.auth.AlfhaUserEntity = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val userDao = db.alfhaUserDao()
        val userEmail = firebaseUser.email ?: "${firebaseUser.uid}@alfhaseguridad.com"
        val existingByEmail = userDao.getUserByEmail(userEmail)
        val existingById = userDao.getUserById(firebaseUser.uid)

        val localUser = when {
            existingByEmail != null -> {
                userDao.updateLastLogin(existingByEmail.id)
                existingByEmail.copy(lastLoginMillis = System.currentTimeMillis())
            }
            existingById != null -> {
                userDao.updateLastLogin(existingById.id)
                existingById.copy(lastLoginMillis = System.currentTimeMillis())
            }
            else -> {
                val displayName = firebaseUser.displayName.takeIf { !it.isNullOrBlank() }
                    ?: userEmail.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() }
                val newUser = com.example.data.auth.AlfhaUserEntity(
                    id = firebaseUser.uid,
                    name = displayName,
                    email = userEmail,
                    role = targetRole.name,
                    unitOrDepartment = if (targetRole == com.example.auth.AlfhaRole.GUARDIA) "Garita Táctica" else "Acceso Firebase",
                    permissionsCsv = "",
                    isActive = true,
                    lastLoginMillis = System.currentTimeMillis(),
                    updatedAtMillis = System.currentTimeMillis(),
                    updatedBy = "FIREBASE_AUTH_SYNC"
                )
                userDao.insertUser(newUser)
                newUser
            }
        }

        com.example.auth.AlfhaSecurityContext.setCurrentUser(localUser)
        localUser
    }

    /**
     * Cierra la sesión activa.
     */
    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.w(tag, "Error al cerrar sesión en Firebase: ${e.message}")
        }
        com.example.auth.AlfhaSecurityContext.clearSession()
        _authState.value = AuthUiState.Unauthenticated()
    }
}
