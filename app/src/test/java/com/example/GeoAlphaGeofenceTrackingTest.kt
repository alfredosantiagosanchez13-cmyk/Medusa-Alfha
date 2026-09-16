package com.example

import com.example.data.incident.IncidentCategory
import com.example.data.incident.IncidentEntity
import com.example.data.incident.IncidentPriority
import com.example.data.supervision.GeoAlphaPoint
import com.example.data.supervision.GeoAlphaTourEngine
import com.example.ui.components.CondoTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas unitarias para la lógica de geocercas satelitales Google Play Services
 * del sistema de rondas automáticas Geo-Alpha.
 */
class GeoAlphaGeofenceTrackingTest {

    @Test
    fun testPredefinedGeopointsCatalogCompleteness() {
        val allPoints = GeoAlphaTourEngine.getAllPredefinedPoints()
        assertTrue("Debe existir catálogo de geopuntos predefinidos", allPoints.isNotEmpty())

        val paraisoPoints = GeoAlphaTourEngine.getPointsForCondo(CondoTarget.PARAISO)
        assertEquals(6, paraisoPoints.size)

        val prados1Points = GeoAlphaTourEngine.getPointsForCondo(CondoTarget.PRADOS_1)
        assertEquals(6, prados1Points.size)

        val prados2Points = GeoAlphaTourEngine.getPointsForCondo(CondoTarget.PRADOS_2)
        assertEquals(6, prados2Points.size)

        val prados3Points = GeoAlphaTourEngine.getPointsForCondo(CondoTarget.PRADOS_3)
        assertEquals(6, prados3Points.size)

        // Verificar que cada geopunto predefinido tenga coordenadas válidas y radio >= 30m
        for (point in allPoints) {
            assertTrue("ID debe iniciar con GEO-: ${point.id}", point.id.startsWith("GEO-"))
            assertTrue("Latitud debe ser válida: ${point.latitude}", point.latitude in 20.0..21.0)
            assertTrue("Longitud debe ser válida: ${point.longitude}", point.longitude in -101.0..-100.0)
            assertTrue("Radio debe ser mayor o igual a 30m: ${point.radiusMeters}", point.radiusMeters >= 30f)
            assertTrue("Debe tener nombre descriptivo", point.name.isNotBlank())
            assertTrue("Debe tener área asignada", point.area.isNotBlank())
        }
    }

    @Test
    fun testFindPointById() {
        val p1 = GeoAlphaTourEngine.findPointById("GEO-PAR-01")
        assertNotNull("Debe encontrar GEO-PAR-01", p1)
        assertEquals("Garita de Acceso Principal Paraíso", p1?.name)

        val p2 = GeoAlphaTourEngine.findPointById("GEO-PR1-01")
        assertNotNull("Debe encontrar GEO-PR1-01", p2)
        assertEquals("Caseta Principal Prados 1", p2?.name)

        val nonExistent = GeoAlphaTourEngine.findPointById("GEO-NON-EXISTENT")
        assertNull("Punto no existente debe retornar null", nonExistent)
    }

    @Test
    fun testTourLifecycleAndOrderAgnosticDetection() {
        // 1. Iniciar ronda en Condominio Paraíso
        val initialTour = GeoAlphaTourEngine.startTour(CondoTarget.PARAISO, "Guardia Satelital Alpha")

        assertNotNull(initialTour)
        assertEquals(CondoTarget.PARAISO, initialTour.condo)
        assertEquals(6, initialTour.allAuthorizedPoints.size)
        assertEquals(0, initialTour.coveredDetections.size)
        assertEquals(6, initialTour.pendingPoints.size)
        assertFalse(initialTour.isComplete)
        assertEquals(0, initialTour.coveragePercentage)

        // 2. Principio Rector: Registro de paso en cualquier orden (p.ej. iniciando en Garita GEO-PAR-01)
        val p1 = initialTour.allAuthorizedPoints.first { it.id == "GEO-PAR-01" }
        val (tourStep1, det1) = GeoAlphaTourEngine.evaluateGpsPosition(
            currentState = initialTour,
            lat = p1.latitude,
            lng = p1.longitude
        )

        assertNotNull(det1)
        assertEquals("GEO-PAR-01", det1?.pointId)
        assertEquals(1, det1?.sequenceOrder)
        assertEquals(1, tourStep1.coveredDetections.size)
        assertEquals(5, tourStep1.pendingPoints.size)
        assertEquals(16, tourStep1.coveragePercentage) // 1/6 = 16%

        // 3. Evaluar paso duplicado por el mismo punto ya cubierto -> No debe duplicar detección
        val (tourStep1Dup, detDup) = GeoAlphaTourEngine.simulatePassByPoint(
            currentState = tourStep1,
            pointId = "GEO-PAR-01"
        )
        assertNull("No debe generar nueva detección si el punto ya fue cubierto", detDup)
        assertEquals(1, tourStep1Dup.coveredDetections.size)

        // 4. Guardia continúa hacia punto del extremo norte perimetral (GEO-PAR-05)
        val p5 = initialTour.allAuthorizedPoints.first { it.id == "GEO-PAR-05" }
        val (tourStep2, det2) = GeoAlphaTourEngine.evaluateGpsPosition(
            currentState = tourStep1,
            lat = p5.latitude,
            lng = p5.longitude
        )
        assertNotNull(det2)
        assertEquals("GEO-PAR-05", det2?.pointId)
        assertEquals(2, det2?.sequenceOrder)
        assertEquals(2, tourStep2.coveredDetections.size)
        assertEquals(4, tourStep2.pendingPoints.size)
        assertEquals(33, tourStep2.coveragePercentage)

        // 5. Cubrir los restantes puntos mediante simulación
        var current = tourStep2
        val remaining = listOf("GEO-PAR-02", "GEO-PAR-03", "GEO-PAR-04", "GEO-PAR-06")
        for (ptId in remaining) {
            val (updated, newDet) = GeoAlphaTourEngine.simulatePassByPoint(current, ptId)
            assertNotNull("Detección de $ptId debe ser exitosa", newDet)
            current = updated
        }

        assertEquals(6, current.coveredDetections.size)
        assertEquals(0, current.pendingPoints.size)
        assertEquals(100, current.coveragePercentage)
        assertTrue("Ronda debe estar completa al cubrir el 100% de los puntos", current.isComplete)
    }

    @Test
    fun testSupervisorOverrideAuthorization() {
        val initialTour = GeoAlphaTourEngine.startTour(CondoTarget.PRADOS_1, "Oficial de Ronda")

        // Cubrir solo 1 punto
        val (tourWithOne, _) = GeoAlphaTourEngine.simulatePassByPoint(initialTour, "GEO-PR1-01")
        assertFalse("Ronda parcial no debe estar completa sin supervisor", tourWithOne.isComplete)

        // Autorizar cierre con justificación de supervisión
        val overrideTour = GeoAlphaTourEngine.authorizeSupervisorOverride(
            currentState = tourWithOne,
            supervisorNotes = "Acceso a subestación eléctrica restringido por mantenimiento CFE"
        )

        assertTrue("Ronda debe quedar autorizada", overrideTour.isSupervisorAuthorizedOverride)
        assertEquals("Acceso a subestación eléctrica restringido por mantenimiento CFE", overrideTour.supervisorOverrideNotes)
        assertTrue("isComplete debe ser true tras la autorización", overrideTour.isComplete)
    }

    @Test
    fun testLinkIncidentToActiveTour() {
        val initialTour = GeoAlphaTourEngine.startTour(CondoTarget.PRADOS_2, "Oficial Alfa")
        val incident = IncidentEntity(
            folio = "INC-TEST-001",
            rawTranscript = "Lámpara perimetral fundida en barda norte",
            category = IncidentCategory.INFRAESTRUCTURA,
            priority = IncidentPriority.MEDIA,
            location = "Barda norte",
            reportedBy = "Oficial Alfa",
            reportedByRole = "GUARDIA_RONDA",
            aiSummary = "Lámpara no enciende durante la ronda",
            recommendedAction = "Reemplazar foco LED",
            status = "REGISTRADO"
        )

        val tourWithIncident = GeoAlphaTourEngine.linkIncident(initialTour, incident)
        assertEquals(1, tourWithIncident.linkedIncidents.size)
        assertEquals("INC-TEST-001", tourWithIncident.linkedIncidents.first().folio)
    }
}
