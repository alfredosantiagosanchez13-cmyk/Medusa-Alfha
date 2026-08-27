package com.example

import com.example.data.incident.GpsCoordinates
import com.example.data.supervision.SupervisionAuditEntity
import com.example.data.supervision.SupervisionCheckpoint
import com.example.data.supervision.SupervisionExecutiveReport
import com.example.data.supervision.SupervisionRoutesCatalog
import com.example.data.supervision.SupervisionTourEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FASE 17: PRUEBAS UNITARIAS DE SUPERVISIÓN TÁCTICA Y RONDINES INTELIGENTES
 */
class SupervisionTourEngineTest {

    @Test
    fun testGenerateTourFolioFormat() {
        val folio1 = SupervisionTourEngine.generateTourFolio()
        val folio2 = SupervisionTourEngine.generateTourFolio()

        assertTrue(folio1.startsWith("RON-"))
        assertTrue(folio2.startsWith("RON-"))
        assertTrue(folio1 != folio2)
    }

    @Test
    fun testGeodeticDistanceCalculation() {
        // Mismo punto -> Distancia 0m
        val d0 = SupervisionTourEngine.calculateDistanceMeters(-33.43720, -70.65060, -33.43720, -70.65060)
        assertTrue("Distance should be 0, got $d0", d0 < 0.01)

        // Punto cercano (~11 metros)
        val d1 = SupervisionTourEngine.calculateDistanceMeters(-33.43720, -70.65060, -33.43730, -70.65060)
        assertTrue("Distance should be approx 11.1 meters, got $d1", d1 in 10.0..13.0)
    }

    @Test
    fun testValidateCheckpointGpsWithinTolerance() {
        val checkpoint = SupervisionCheckpoint(
            id = "CP-TEST-1",
            sequence = 1,
            name = "Garita Central",
            area = "Acceso",
            targetLat = -33.43720,
            targetLng = -70.65060,
            checklistCriteria = listOf("Barrera", "Bitácora"),
            criticalRiskFactors = "Fallo de barrera"
        )

        // GPS dentro de tolerancia (35 metros de distancia)
        val nearGps = GpsCoordinates(
            latitude = -33.43750,
            longitude = -70.65060,
            accuracyMeters = 5.0f,
            provider = "GPS"
        )

        val resultNear = SupervisionTourEngine.validateCheckpointGps(nearGps, checkpoint)
        assertTrue(resultNear.isWithinTolerance)
        assertTrue(resultNear.distanceMeters < 80.0)

        // GPS fuera de tolerancia (500 metros)
        val farGps = GpsCoordinates(
            latitude = -33.44200,
            longitude = -70.65060,
            accuracyMeters = 5.0f,
            provider = "GPS"
        )

        val resultFar = SupervisionTourEngine.validateCheckpointGps(farGps, checkpoint)
        assertFalse(resultFar.isWithinTolerance)
        assertTrue(resultFar.distanceMeters > 80.0)
        assertTrue(resultFar.statusLabel.contains("FUERA DE UBICACIÓN"))
    }

    @Test
    fun testExecutiveReportSha256Integrity() {
        val audits = listOf(
            SupervisionAuditEntity(
                folio = "RON-20260825-1001-01",
                supervisorName = "Esteban Silva",
                checkpointName = "Garita Principal",
                areaName = "Acceso",
                statusCondition = "OPTIMO",
                findingsDescription = "Todo en orden",
                riskLevel = "BAJO",
                correctiveActionRequired = "Mantener estándar",
                responsibleParty = "Supervisor",
                commitmentDate = "2026-08-25",
                gpsCoordinates = "-33.43720, -70.65060",
                durationMinutes = 5,
                timestampMillis = 1000000L,
                isClosed = false
            ),
            SupervisionAuditEntity(
                folio = "RON-20260825-1001-02",
                supervisorName = "Esteban Silva",
                checkpointName = "Perímetro Norte",
                areaName = "Perímetro",
                statusCondition = "CRITICO",
                findingsDescription = "Luminaria dañada y corte en alambre",
                riskLevel = "ALTO",
                correctiveActionRequired = "Reparación urgente de cerco",
                responsibleParty = "Mantenimiento",
                commitmentDate = "2026-08-25",
                gpsCoordinates = "-33.43650, -70.65150",
                durationMinutes = 8,
                timestampMillis = 1000000L,
                isClosed = false
            ),
            SupervisionAuditEntity(
                folio = "RON-20260825-1001-03",
                supervisorName = "Esteban Silva",
                checkpointName = "Subestación",
                areaName = "Infraestructura",
                statusCondition = "OMITIDO",
                findingsDescription = "Punto omitido: Acceso bloqueado por trabajos",
                riskLevel = "MEDIO",
                correctiveActionRequired = "Revisar en siguiente turno",
                responsibleParty = "Supervisor",
                commitmentDate = "2026-08-25",
                gpsCoordinates = "OMITIDO",
                durationMinutes = 0,
                timestampMillis = 1000000L,
                isClosed = false
            )
        )

        val report = SupervisionExecutiveReport.buildFromAudits(
            tourFolio = "RON-20260825-1001",
            supervisorName = "Esteban Silva",
            mainLocation = "Ruta Perimetral",
            tourAudits = audits,
            durationMinutes = 25
        )

        assertEquals("RON-20260825-1001", report.folio)
        assertEquals(3, report.totalCheckpointsCount)
        assertEquals(1, report.optimumCount)
        assertEquals(0, report.regularCount)
        assertEquals(1, report.criticalCount)
        assertEquals(1, report.omittedCount)

        assertNotNull(report.integrityHashSha256)
        assertEquals(64, report.integrityHashSha256.length) // SHA-256 is 64 hex chars
        assertTrue(report.finalResult.contains("NO CONFORME") || report.finalResult.contains("CRÍTICA"))
    }
}
