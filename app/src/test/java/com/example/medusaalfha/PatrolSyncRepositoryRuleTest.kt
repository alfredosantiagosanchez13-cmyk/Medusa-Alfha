package com.example.medusaalfha

import com.example.medusaalfha.base.FirestoreEmulatorTestBase
import com.example.medusaalfha.data.local.entity.CheckpointEntity
import com.example.medusaalfha.data.local.entity.PatrolReportEntity
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test

class PatrolSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun testAuthenticatedGuardCanWriteCheckpoint() = runBlocking {
        val uid = signInTestUser("guard1@medusaalfha.com")

        val entity = CheckpointEntity(
            id = "CP_TEST_01",
            code = "CP-01",
            name = "Acceso Perimetral Norte",
            description = "Caseta y pluma automatizada",
            latitude = 19.432608,
            longitude = -99.133209,
            toleranceMeters = 15.0,
            isValidated = true,
            validatedAtMillis = System.currentTimeMillis()
        )

        val payload = entity.toFirestoreMap().toMutableMap().apply {
            put("userId", uid)
        }

        val docRef = firestore.collection("condominiums")
            .document("PRADOS_1")
            .collection("patrol_checkpoints")
            .document(entity.id)

        docRef.set(payload).await()

        val snapshot = docRef.get().await()
        assertTrue(snapshot.exists())
        assertEquals("Acceso Perimetral Norte", snapshot.getString("name"))
        assertEquals(true, snapshot.getBoolean("isValidated"))
    }

    @Test
    fun testAuthenticatedGuardCanWriteReport() = runBlocking {
        val uid = signInTestUser("guard2@medusaalfha.com")

        val report = PatrolReportEntity(
            id = "REP_TEST_01",
            timestampMillis = System.currentTimeMillis(),
            zone = "Sector Caseta Central",
            reportText = "Sin novedades relevantes durante el turno.",
            isAiAssisted = false
        )

        val payload = report.toFirestoreMap().toMutableMap().apply {
            put("userId", uid)
        }

        val docRef = firestore.collection("condominiums")
            .document("PRADOS_1")
            .collection("patrol_reports")
            .document(report.id)

        docRef.set(payload).await()

        val snapshot = docRef.get().await()
        assertTrue(snapshot.exists())
        assertEquals("Sector Caseta Central", snapshot.getString("zone"))
    }

    @Test
    fun testUnauthenticatedUserCannotWriteCheckpoint() = runBlocking {
        auth.signOut()

        val entity = CheckpointEntity(
            id = "CP_UNAUTH",
            code = "CP-UNAUTH",
            name = "Punto no autorizado",
            description = "Intruso",
            latitude = 19.43,
            longitude = -99.13,
            toleranceMeters = 15.0,
            isValidated = false
        )

        val docRef = firestore.collection("condominiums")
            .document("PRADOS_1")
            .collection("patrol_checkpoints")
            .document(entity.id)

        try {
            docRef.set(entity.toFirestoreMap()).await()
            fail("Expected PERMISSION_DENIED exception")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }
}
