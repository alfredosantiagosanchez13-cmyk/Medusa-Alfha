package com.example

import com.example.data.firebase.AuthUiState
import com.example.data.firebase.FirebaseConfigHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias para la integración de Firebase y autenticación en MEDUSA ALFHA.
 */
class FirebaseIntegrationTest {

    @Test
    fun testAuthUiState_hierarchy() {
        val idle = AuthUiState.Idle
        val loading = AuthUiState.Loading
        val unauthenticated = AuthUiState.Unauthenticated("Sin sesión")
        val error = AuthUiState.Error("Error de credenciales")

        assertTrue(idle is AuthUiState)
        assertTrue(loading is AuthUiState)
        assertTrue(unauthenticated is AuthUiState.Unauthenticated)
        assertEquals("Sin sesión", unauthenticated.message)
        assertTrue(error is AuthUiState.Error)
        assertEquals("Error de credenciales", error.errorMessage)
    }

    @Test
    fun testFirebaseConfigHelper_defaults() {
        val statusFlow = FirebaseConfigHelper.initializationStatusMessage
        val availableFlow = FirebaseConfigHelper.isFirebaseAvailable

        assertNotNull(statusFlow.value)
        assertFalse(availableFlow.value)
    }
}
