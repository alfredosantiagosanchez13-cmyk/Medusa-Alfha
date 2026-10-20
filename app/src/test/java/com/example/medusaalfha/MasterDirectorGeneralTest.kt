package com.example.medusaalfha

import com.example.medusaalfha.data.biometric.AlfhaBiometricManager
import com.example.medusaalfha.data.model.Condominium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterDirectorGeneralTest {

    @Test
    fun testMasterCredentials() {
        assertEquals("Santiago Sánchez", AlfhaBiometricManager.MASTER_DIRECTOR_NAME)
        assertEquals("alfredosantiagosanchez13@gmail.com", AlfhaBiometricManager.MASTER_DIRECTOR_EMAIL)
        assertTrue(AlfhaBiometricManager.MASTER_AUTHORIZED_EMAILS.contains("alfredosantiagosanchez13@gmail.com"))
        assertTrue(AlfhaBiometricManager.MASTER_AUTHORIZED_EMAILS.contains("alfhaseguridad070@gmail.com"))
    }

    @Test
    fun testCondominiumSerialization() {
        val condo = Condominium(
            id = "PRADOS_2",
            name = "Prados Residencial Fase 2",
            address = "Av. Del Bosque #200",
            totalUnits = 140,
            adminContact = "Santiago Sánchez · Dir. Gral",
            gateCount = 2,
            activeStatus = "ACTIVO",
            createdAtMillis = 1700000000000L,
            createdBy = "alfredosantiagosanchez13@gmail.com"
        )

        val map = condo.toMap().filterValues { it != null } as Map<String, Any>
        assertEquals("PRADOS_2", map["id"])
        assertEquals("Prados Residencial Fase 2", map["name"])
        assertEquals(140, map["totalUnits"])
        assertEquals("alfredosantiagosanchez13@gmail.com", map["createdBy"])

        val deserialized = Condominium.fromMap(map)
        assertEquals(condo.id, deserialized.id)
        assertEquals(condo.name, deserialized.name)
        assertEquals(condo.address, deserialized.address)
        assertEquals(condo.totalUnits, deserialized.totalUnits)
        assertEquals(condo.adminContact, deserialized.adminContact)
        assertEquals(condo.createdBy, deserialized.createdBy)
    }

    @Test
    fun testDefaultPradosCondominium() {
        val defaultPrados = Condominium.DEFAULT_PRADOS
        assertNotNull(defaultPrados)
        assertEquals("PRADOS_1", defaultPrados.id)
        assertTrue(defaultPrados.name.contains("Prados"))
        assertEquals("ACTIVO", defaultPrados.activeStatus)
    }
}
