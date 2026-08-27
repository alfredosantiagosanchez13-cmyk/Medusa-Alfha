package com.example

import com.example.data.validation.FieldValidationRepository
import com.example.data.validation.FieldValidationTestEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suite de Pruebas Unitarias para el Checklist de Validación de Campo (16 pruebas físicas).
 * Verifica:
 * 1. Definición canónica de exactamente 16 pruebas en estricto orden correlativo.
 * 2. Todas inician en estado PENDIENTE.
 * 3. Categorías y Criterios canónicos (Caseta, QR, Ubicación, Offline, Tiempo Devuelto).
 * 4. Actualización de resultados a APROBADO / FALLO con evidencia y notas sin afectar otras pruebas.
 * 5. Lógica de contadores automáticos y condición de "PENDIENTE DE VALIDACIÓN DE CAMPO".
 */
class FieldValidationChecklistTest {

    @Test
    fun testCanonical16Tests_structureAndOrder() {
        val tests = FieldValidationRepository.getCanonical16Tests()

        assertEquals("Debe contener exactamente 16 pruebas obligatorias", 16, tests.size)

        // Verificar orden correlativo estricto 1 a 16
        for (i in 0 until 16) {
            val item = tests[i]
            assertEquals("El orden correlativo debe ser ${i + 1}", i + 1, item.orderIndex)
            assertEquals("Todas las pruebas deben iniciar en PENDIENTE", "PENDIENTE", item.status)
            assertTrue("La referencia de evidencia inicial debe estar vacía", item.evidenceReference.isEmpty())
            assertTrue("Las observaciones iniciales deben estar vacías", item.observations.isEmpty())
            assertTrue("Debe tener un título no vacío", item.title.isNotBlank())
            assertTrue("Debe tener un procedimiento no vacío", item.procedure.isNotBlank())
            assertTrue("Debe tener un criterio de aceptación no vacío", item.acceptanceCriteria.isNotBlank())
            assertTrue("Debe tener un requerimiento de evidencia no vacío", item.evidenceRequired.isNotBlank())
        }

        // Verificar IDs canónicos
        val expectedIds = listOf(
            "CAS-01", "CAS-02", "CAS-03", "CAS-04",
            "QR-01", "QR-02", "QR-03",
            "GPS-01", "GPS-02", "GPS-03",
            "OFF-01", "OFF-02", "OFF-03", "OFF-04",
            "TME-01", "TME-02"
        )
        val actualIds = tests.map { it.testId }
        assertEquals(expectedIds, actualIds)
    }

    @Test
    fun testCategoriesDistribution() {
        val tests = FieldValidationRepository.getCanonical16Tests()

        val casetaTests = tests.filter { it.category == "CASETA" }
        val qrTests = tests.filter { it.category == "PASE QR" }
        val gpsTests = tests.filter { it.category == "UBICACIÓN GPS" }
        val offlineTests = tests.filter { it.category == "OFFLINE / RECONEXIÓN" }
        val tiempoTests = tests.filter { it.category == "TIEMPO DEVUELTO" }

        assertEquals(4, casetaTests.size)
        assertEquals(3, qrTests.size)
        assertEquals(3, gpsTests.size)
        assertEquals(4, offlineTests.size)
        assertEquals(2, tiempoTests.size)
    }

    @Test
    fun testSimulatedEvaluationCycle_andPendingCondition() {
        val testMap = FieldValidationRepository.getCanonical16Tests().associateBy { it.testId }.toMutableMap()

        // 1. Estado inicial
        var total = testMap.size
        var aprobadas = testMap.values.count { it.status == "APROBADO" }
        var fallidas = testMap.values.count { it.status == "FALLO" }
        var pendientes = testMap.values.count { it.status == "PENDIENTE" }

        assertEquals(16, total)
        assertEquals(0, aprobadas)
        assertEquals(0, fallidas)
        assertEquals(16, pendientes)
        assertTrue("Con pruebas pendientes el estado es PENDIENTE DE VALIDACIÓN DE CAMPO", pendientes > 0)

        // 2. Evaluar Caseta
        testMap["CAS-01"] = testMap["CAS-01"]!!.copy(
            status = "APROBADO",
            evidenceReference = "Folio MED-20260827-001",
            observations = "Check-in exitoso en garita física"
        )
        testMap["CAS-02"] = testMap["CAS-02"]!!.copy(
            status = "APROBADO",
            evidenceReference = "Folio MED-20260827-001 marcado DEPARTED",
            observations = "Salida confirmada en 1 toque"
        )
        testMap["CAS-03"] = testMap["CAS-03"]!!.copy(
            status = "APROBADO",
            evidenceReference = "Permanencia calculada: 18 minutos",
            observations = "Cero recaptura ni datos ficticios"
        )
        testMap["CAS-04"] = testMap["CAS-04"]!!.copy(
            status = "APROBADO",
            evidenceReference = "Visitante recuperado en Room SQLite tras reinicio",
            observations = "Persistencia confirmada"
        )

        // 3. Evaluar una con fallo
        testMap["QR-03"] = testMap["QR-03"]!!.copy(
            status = "FALLO",
            evidenceReference = "Rechazo de QR expirado tardó más de lo esperado",
            observations = "Requiere optimización de caché"
        )

        aprobadas = testMap.values.count { it.status == "APROBADO" }
        fallidas = testMap.values.count { it.status == "FALLO" }
        pendientes = testMap.values.count { it.status == "PENDIENTE" }

        assertEquals(16, total)
        assertEquals(4, aprobadas)
        assertEquals(1, fallidas)
        assertEquals(11, pendientes)
        assertTrue("Aún con 11 pendientes debe requerir validación de campo", pendientes > 0)

        // Verificar que CAS-01 mantiene su evidencia intacta y las no evaluadas siguen en PENDIENTE
        assertEquals("Folio MED-20260827-001", testMap["CAS-01"]?.evidenceReference)
        assertEquals("PENDIENTE", testMap["GPS-01"]?.status)
        assertEquals("PENDIENTE", testMap["OFF-01"]?.status)
    }
}
