package com.example

import com.example.data.core.AlphaCoreEngine
import com.example.scanner.PassType
import com.example.utils.ResidentQrCodeUtility
import com.example.utils.TemporaryAccessPass
import org.junit.Assert.*
import org.junit.Test

/**
 * Pruebas unitarias para la utilidad de generación de Códigos QR y pases temporales para visitantes
 * guardados en Firestore (ResidentQrCodeUtility).
 */
class ResidentQrCodeUtilityTest {

    @Test
    fun testTemporaryAccessPass_propertiesAndValidity() {
        val now = System.currentTimeMillis()
        val durationHours = 4
        val validUntil = now + (durationHours * 3600 * 1000L)
        val passCode = "MED-20260908-4821"
        val doc = "INE-992831"
        val unit = "Casa 102"
        val hash = AlphaCoreEngine.computeIntegrityHash(passCode, doc, unit)

        val pass = TemporaryAccessPass(
            passCode = passCode,
            folio = passCode,
            visitorName = "María Fernanda Gómez",
            visitorDocument = doc,
            destinationUnit = unit,
            hostResidentName = "Ing. Roberto Martínez",
            passType = PassType.VISITOR_SINGLE,
            durationHours = durationHours,
            issuedAtMillis = now,
            validUntilMillis = validUntil,
            maxEntries = 1,
            currentEntriesCount = 0,
            notes = "Acceso a reunión vecinal",
            integrityHash = hash,
            isActive = true,
            firestoreDocumentPath = "/condominiums/PRADOS_1/qr_passes/$passCode",
            savedToFirestore = true,
            syncMessage = "Guardado exitosamente en Firebase Firestore"
        )

        // Verificaciones de estado y validez
        assertTrue("El pase debe ser válido para acceso", pass.isValidForAccess)
        assertFalse("El pase no debe estar expirado", pass.isExpired)
        assertFalse("El pase no debe estar agotado", pass.isExhausted)
        assertTrue("El tiempo restante debe ser positivo", pass.remainingMillis > 0)
        assertTrue("El formato restante debe contener horas o minutos", pass.remainingFormatted.contains("restantes"))

        // Verificación de partición y ruta multi-inquilino de Firestore
        assertEquals("/condominiums/PRADOS_1/qr_passes/MED-20260908-4821", pass.firestoreDocumentPath)
        assertTrue(pass.savedToFirestore)

        // Verificación de Payload JSON para escaneo
        val json = pass.qrPayloadJson
        assertTrue("Debe contener el passCode", json.contains(passCode))
        assertTrue("Debe contener el visitante", json.contains("María Fernanda Gómez"))
        assertTrue("Debe contener la unidad", json.contains(unit))
        assertTrue("Debe contener el hash criptográfico", json.contains(hash))
    }

    @Test
    fun testTemporaryAccessPass_expirationBehavior() {
        val past = System.currentTimeMillis() - 10000L // 10 segundos atrás
        val pass = TemporaryAccessPass(
            passCode = "MED-EXP-001",
            folio = "MED-EXP-001",
            visitorName = "Pedro Infante",
            visitorDocument = "DOC-123",
            destinationUnit = "Casa 14",
            hostResidentName = "Carlos Mendoza",
            passType = PassType.VISITOR_SINGLE,
            durationHours = 1,
            issuedAtMillis = past - 3600000L,
            validUntilMillis = past,
            maxEntries = 1,
            currentEntriesCount = 0,
            integrityHash = "hash123",
            isActive = true,
            firestoreDocumentPath = "/condominiums/PRADOS_1/qr_passes/MED-EXP-001"
        )

        assertTrue("El pase debe estar marcado como expirado", pass.isExpired)
        assertFalse("Un pase expirado no debe ser válido para acceso", pass.isValidForAccess)
        assertEquals("Expirado", pass.remainingFormatted)
    }

    @Test
    fun testTemporaryAccessPass_exhaustedBehavior() {
        val future = System.currentTimeMillis() + 3600000L
        val pass = TemporaryAccessPass(
            passCode = "MED-EXHAUST-002",
            folio = "MED-EXHAUST-002",
            visitorName = "Laura Pausini",
            visitorDocument = "DOC-456",
            destinationUnit = "Casa 25",
            hostResidentName = "Ana Lucía",
            passType = PassType.VISITOR_SINGLE,
            durationHours = 2,
            validUntilMillis = future,
            maxEntries = 1,
            currentEntriesCount = 1, // Ya ingresó
            integrityHash = "hash456",
            isActive = true,
            firestoreDocumentPath = "/condominiums/PRADOS_1/qr_passes/MED-EXHAUST-002"
        )

        assertTrue("El pase debe estar agotado", pass.isExhausted)
        assertFalse("Un pase agotado no debe ser válido para acceso", pass.isValidForAccess)
    }

    @Test
    fun testQrEncoding_bitMatrixValid() {
        val payload = "MED-20260908-TEST-PAYLOAD"
        val writer = com.google.zxing.MultiFormatWriter()
        val bitMatrix = writer.encode(payload, com.google.zxing.BarcodeFormat.QR_CODE, 256, 256)
        assertNotNull("La matriz de bits QR debe generarse correctamente", bitMatrix)
        assertEquals(256, bitMatrix.width)
        assertEquals(256, bitMatrix.height)
    }

    @Test
    fun testTemporaryAccessPass_revokedState() {
        val pass = TemporaryAccessPass(
            passCode = "MED-REVOKED-003",
            folio = "MED-REVOKED-003",
            visitorName = "Juan Gabriel",
            visitorDocument = "DOC-789",
            destinationUnit = "Casa 10",
            hostResidentName = "Mario Moreno",
            passType = PassType.VISITOR_SINGLE,
            durationHours = 4,
            validUntilMillis = System.currentTimeMillis() + 3600000L,
            integrityHash = "hash789",
            isActive = false, // Revocado
            firestoreDocumentPath = "/condominiums/PRADOS_1/qr_passes/MED-REVOKED-003"
        )

        assertFalse("Un pase revocado no debe ser válido para acceso", pass.isValidForAccess)
        assertFalse(pass.isActive)
    }
}
