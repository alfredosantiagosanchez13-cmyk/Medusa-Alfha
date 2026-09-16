package com.example

import com.example.auth.ResidentAuthStatus
import com.example.auth.ResidentFirebaseAuthGuard
import com.example.data.resident.ResidentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias para ResidentFirebaseAuthGuard y la restricción de acceso en MEDUSA ALFHA.
 */
class ResidentFirebaseAuthGuardTest {

    @Test
    fun testResidentAuthStatus_hierarchy() {
        val checking = ResidentAuthStatus.Checking
        val unauth = ResidentAuthStatus.Unauthenticated("Se requiere autenticación")
        val nonRes = ResidentAuthStatus.NonResident("user@email.com", "uid123", "No es residente")
        val error = ResidentAuthStatus.Error("Fallo de red")

        val dummyResident = ResidentEntity(
            id = "RES-001",
            unitId = "Casa 104",
            fullName = "Familia Arismendi",
            email = "arismendi.residente@condominio.com"
        )
        val authorized = ResidentAuthStatus.Authorized(
            resident = dummyResident,
            firebaseUser = null,
            email = dummyResident.email,
            unitId = dummyResident.unitId
        )

        assertTrue(checking is ResidentAuthStatus.Checking)
        assertTrue(unauth is ResidentAuthStatus.Unauthenticated)
        assertEquals("Se requiere autenticación", unauth.message)

        assertTrue(nonRes is ResidentAuthStatus.NonResident)
        assertEquals("user@email.com", nonRes.email)
        assertEquals("uid123", nonRes.uid)

        assertTrue(error is ResidentAuthStatus.Error)
        assertEquals("Fallo de red", error.errorMessage)

        assertTrue(authorized is ResidentAuthStatus.Authorized)
        assertEquals("Familia Arismendi", authorized.resident.fullName)
        assertEquals("Casa 104", authorized.unitId)
    }

    @Test
    fun testInitialCachedResident_isNullByDefault() {
        assertNull(ResidentFirebaseAuthGuard.getAuthenticatedResident())
    }
}
