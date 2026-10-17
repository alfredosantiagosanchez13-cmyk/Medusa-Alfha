package com.example.medusaalfha.base

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
abstract class FirestoreEmulatorTestBase {

    protected lateinit var firestore: FirebaseFirestore
    protected lateinit var auth: FirebaseAuth
    protected lateinit var databaseId: String

    @Before
    open fun setUpFirebase() {
        val emulatorAvailable = try {
            val firestoreSocket = java.net.Socket()
            firestoreSocket.connect(java.net.InetSocketAddress(EMULATOR_HOST, FIRESTORE_PORT), 300)
            firestoreSocket.close()
            val authSocket = java.net.Socket()
            authSocket.connect(java.net.InetSocketAddress(EMULATOR_HOST, AUTH_PORT), 300)
            authSocket.close()
            true
        } catch (_: Exception) {
            false
        }
        org.junit.Assume.assumeTrue(
            "Firebase emulators must be running on $EMULATOR_HOST:$FIRESTORE_PORT and $AUTH_PORT for emulator rule tests",
            emulatorAvailable
        )

        val context = ApplicationProvider.getApplicationContext<Context>()
        val resId = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
        databaseId = if (resId != 0) context.getString(resId) else "demo-database"

        val app = if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApplicationId("com.aistudio.medusaalpha.qxvtkm")
                    .setProjectId(PROJECT_ID)
                    .setApiKey("fake-api-key-for-emulator")
                    .build()
            )
        } else {
            FirebaseApp.getInstance()
        }

        firestore = FirebaseFirestore.getInstance(app, databaseId).apply {
            try {
                useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
                firestoreSettings = FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                    .build()
            } catch (_: Exception) {}
        }

        auth = FirebaseAuth.getInstance(app).apply {
            try {
                useEmulator(EMULATOR_HOST, AUTH_PORT)
            } catch (_: Exception) {}
        }
    }

    @After
    open fun tearDownFirebase() {
        if (::auth.isInitialized) {
            auth.signOut()
        }
    }

    protected suspend fun signInTestUser(email: String): String = withContext(Dispatchers.IO) {
        withTimeout(AUTH_TIMEOUT_MS) {
            val result = try {
                auth.signInWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
            } catch (unused: FirebaseAuthInvalidUserException) {
                auth.createUserWithEmailAndPassword(email, DEFAULT_PASSWORD).await()
            }
            checkNotNull(result.user?.uid) { "User auth failed" }
        }
    }

    companion object {
        const val EMULATOR_HOST = "127.0.0.1"
        const val FIRESTORE_PORT = 8085
        const val AUTH_PORT = 9099
        const val PROJECT_ID = "demo-no-project"
        const val DEFAULT_PASSWORD = "password123"
        const val AUTH_TIMEOUT_MS = 5000L
    }
}
