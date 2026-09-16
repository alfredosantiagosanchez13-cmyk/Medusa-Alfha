package com.example

import com.example.auth.FirebaseAuthProvider
import com.example.data.firebase.AuthUiState
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pruebas unitarias para FirebaseAuthProvider y su inicialización en MainApplication.
 */
class FirebaseAuthProviderTest {

    @Before
    fun setUp() {
        FirebaseAuthProvider.setInstance(null)
    }

    @After
    fun tearDown() {
        FirebaseAuthProvider.setInstance(null)
    }

    @Test
    fun testFirebaseAuthProvider_initiallyNull() {
        assertNull("Instance should be null before initialization", FirebaseAuthProvider.getOrNull())
    }

    @Test(expected = IllegalStateException::class)
    fun testFirebaseAuthProvider_getInstanceThrowsIfNotInitialized() {
        FirebaseAuthProvider.getInstance()
    }

    @Test
    fun testFirebaseAuthProvider_setInstanceAndGetInstance() {
        val fakeProvider = FakeFirebaseAuthProvider()
        FirebaseAuthProvider.setInstance(fakeProvider)

        val retrieved = FirebaseAuthProvider.getInstance()
        assertNotNull(retrieved)
        assertEquals(fakeProvider, retrieved)
        assertEquals(fakeProvider, FirebaseAuthProvider.getOrNull())
    }

    @Test
    fun testFirebaseAuthProvider_googleSignInFlow() = runBlocking {
        val fakeProvider = FakeFirebaseAuthProvider()
        FirebaseAuthProvider.setInstance(fakeProvider)

        assertFalse("Initially unauthenticated", fakeProvider.isAuthenticated)
        assertEquals(AuthUiState.Idle, fakeProvider.authState.value)

        fakeProvider.signInWithGoogleIdToken("mock_google_id_token_xyz")
        assertTrue("Should be marked authenticated", fakeProvider.isAuthenticated)
        assertEquals("google_user_123", fakeProvider.currentUserId)
        assertEquals("usuario.google@condominio.com", fakeProvider.currentUserEmail)
        assertEquals("Usuario Google Residente", fakeProvider.currentUserDisplayName)

        fakeProvider.signOut()
        assertFalse("Should be unauthenticated after sign out", fakeProvider.isAuthenticated)
        assertTrue(fakeProvider.authState.value is AuthUiState.Unauthenticated)
    }

    @Test
    fun testMainApplication_defaults() {
        assertFalse(MainApplication.isDatabaseAvailable)
        assertFalse(MainApplication.isFirebaseAvailable)
    }

    /**
     * Fake implementation to verify FirebaseAuthProvider interface compliance without Android Context.
     */
    private class FakeFirebaseAuthProvider : FirebaseAuthProvider {
        private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
        override val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

        override val auth: FirebaseAuth? = null
        override val currentUser: FirebaseUser? = null

        private var _mockUserId: String? = null
        private var _mockUserEmail: String? = null
        private var _mockUserDisplayName: String? = null

        override val currentUserId: String?
            get() = _mockUserId

        override val currentUserEmail: String?
            get() = _mockUserEmail

        override val currentUserDisplayName: String?
            get() = _mockUserDisplayName

        override val isAuthenticated: Boolean
            get() = _mockUserId != null

        override val isInitialized: Boolean = true

        override suspend fun signInWithGoogle(context: android.content.Context, serverClientId: String?): Result<FirebaseUser> {
            return signInWithGoogleIdToken("mock_token")
        }

        override suspend fun signInWithGoogleIdToken(idToken: String): Result<FirebaseUser> {
            _mockUserId = "google_user_123"
            _mockUserEmail = "usuario.google@condominio.com"
            _mockUserDisplayName = "Usuario Google Residente"
            return Result.failure(UnsupportedOperationException("FirebaseUser mocked for unit test"))
        }

        override suspend fun signInWithCredential(credential: AuthCredential): Result<FirebaseUser> {
            return Result.failure(UnsupportedOperationException("Mock"))
        }

        override suspend fun signInWithEmail(email: String, pass: String): Result<FirebaseUser> {
            _mockUserId = "email_user_456"
            _mockUserEmail = email
            return Result.failure(UnsupportedOperationException("Mock"))
        }

        override suspend fun signUpWithEmail(email: String, pass: String, displayName: String): Result<FirebaseUser> {
            _mockUserId = "new_user_789"
            _mockUserEmail = email
            _mockUserDisplayName = displayName
            return Result.failure(UnsupportedOperationException("Mock"))
        }

        override suspend fun signInAnonymously(): Result<FirebaseUser> {
            _mockUserId = "anon_000"
            return Result.failure(UnsupportedOperationException("Mock"))
        }

        override suspend fun sendPasswordReset(email: String): Result<Unit> {
            return Result.success(Unit)
        }

        override fun signOut() {
            _mockUserId = null
            _mockUserEmail = null
            _mockUserDisplayName = null
            _authState.value = AuthUiState.Unauthenticated("Sesión cerrada")
        }
    }
}
