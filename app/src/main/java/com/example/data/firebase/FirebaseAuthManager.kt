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
    private val credentialManager = CredentialManager.create(context)

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

        return try {
            val googleIdOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result: GetCredentialResponse = credentialManager.getCredential(
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

    /**
     * Cierra la sesión activa.
     */
    fun signOut() {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.w(tag, "Error al cerrar sesión en Firebase: ${e.message}")
        }
        _authState.value = AuthUiState.Unauthenticated()
    }
}
