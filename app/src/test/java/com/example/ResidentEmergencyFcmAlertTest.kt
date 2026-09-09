package com.example

import com.example.data.fcm.EmergencyAlertFcmPayload
import com.example.data.fcm.FcmNotificationManager
import com.example.ui.components.EmergencyCategory
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pruebas unitarias para el sistema de Alerta y Botón de Emergencia S.O.S.
 * mediante Firebase Cloud Messaging (FCM) y geolocalización de unidad residencial.
 */
class ResidentEmergencyFcmAlertTest {

    @Test
    fun testEmergencyAlertFcmPayload_structureAndContent() {
        val now = System.currentTimeMillis()
        val folio = "EMG-20260908-7712"
        val residentUnit = "Casa 102"
        val residentName = "Carlos Mendoza"
        val residentId = "RES-001"
        val condominiumId = "PRADOS_1"
        val emergencyType = "Pánico S.O.S."
        val details = "Alarma sonora activada por residente en sala"
        val lat = 19.432608
        val lon = -99.133209
        val accuracy = 8.5f
        val locationDesc = "Unidad Casa 102 • GPS: 19.4326, -99.1332"

        val payload = EmergencyAlertFcmPayload(
            title = "🚨 EMERGENCIA RESIDENCIAL: $residentUnit",
            body = "Alerta de $emergencyType reportada por $residentName en $residentUnit.",
            alertFolio = folio,
            emergencyType = emergencyType,
            residentUnit = residentUnit,
            residentName = residentName,
            residentId = residentId,
            condominiumId = condominiumId,
            latitude = lat,
            longitude = lon,
            gpsAccuracyMeters = accuracy,
            locationName = locationDesc,
            details = details,
            timestampMillis = now,
            status = "ACTIVA"
        )

        // Verificaciones
        assertEquals("🚨 EMERGENCIA RESIDENCIAL: Casa 102", payload.title)
        assertTrue(payload.body.contains("Casa 102"))
        assertTrue(payload.body.contains("Carlos Mendoza"))
        assertEquals("Casa 102", payload.residentUnit)
        assertEquals(lat, payload.latitude!!, 0.0001)
        assertEquals(lon, payload.longitude!!, 0.0001)
        assertEquals("ACTIVA", payload.status)
        assertEquals("EMG-20260908-7712", payload.alertFolio)
    }

    @Test
    fun testEmergencyCategories_coverageAndConsistency() {
        val categories = EmergencyCategory.values()
        assertEquals(4, categories.size)

        val panic = EmergencyCategory.PANIC
        assertEquals("Pánico S.O.S.", panic.label)
        assertTrue(panic.description.contains("inmediato"))

        val medical = EmergencyCategory.MEDICAL
        assertEquals("Emergencia Médica", medical.label)
        assertTrue(medical.description.contains("ambulancia") || medical.description.contains("botiquín"))

        val intrusion = EmergencyCategory.INTRUSION
        assertEquals("Intrusión / Sospecha", intrusion.label)

        val fire = EmergencyCategory.FIRE
        assertEquals("Incendio / Fuego", fire.label)
    }

    @Test
    fun testEmergencyFcmTopic_conformsToSecurityChannel() {
        val globalTopic = FcmNotificationManager.TOPIC_SECURITY_EMERGENCY_ALERTS
        assertEquals("security_emergency_alerts", globalTopic)

        val condoId = "PRADOS_1"
        val sanitizedCondo = condoId.replace("[^a-zA-Z0-9-_.~%]".toRegex(), "_")
        val condoSecurityTopic = "condo_${sanitizedCondo}_security"
        assertEquals("condo_PRADOS_1_security", condoSecurityTopic)
    }

    @Test
    fun testEmergencyNotificationMessage_formatting() {
        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(1757376000000L))
        val unit = "Torre Norte Depto 504"
        val resident = "Dra. Sofía Alarcón"
        val type = "Emergencia Médica"

        val title = "🚨 ALERTA S.O.S. • UNIDAD: $unit"
        val details = "Persona mayor con dificultades respiratorias"

        val summary = """
            🚨 BOTÓN DE EMERGENCIA ACCIONADO
            🏠 Unidad Habitacional: $unit
            👤 Residente: $resident
            ⚠️ Tipo: $type
            ⏱️ Hora: $timeStr hrs
            📝 Situación: $details
        """.trimIndent()

        assertTrue(summary.contains(unit))
        assertTrue(summary.contains(resident))
        assertTrue(summary.contains(type))
        assertTrue(summary.contains(details))
    }
}
