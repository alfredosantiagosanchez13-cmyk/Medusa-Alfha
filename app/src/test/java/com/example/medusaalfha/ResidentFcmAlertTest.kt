package com.example.medusaalfha

import com.example.medusaalfha.data.model.AlertType
import com.example.medusaalfha.data.model.ResidentAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResidentFcmAlertTest {

    @Test
    fun testVisitorArrivalAlertSerialization() {
        val alert = ResidentAlert(
            id = "ALT-VIS-100",
            type = AlertType.VISITOR_ARRIVAL,
            title = "🔔 Visita en Garita: Carlos Ruiz",
            message = "Ha ingresado Carlos Ruiz con destino a Casa 54.",
            targetHouse = "Casa 54 · Circuito Los Álamos",
            timestampMillis = 1700000000000L,
            metadata = mapOf("visitorName" to "Carlos Ruiz", "plate" to "NZA-99-10"),
            isRead = false
        )

        val map = alert.toMap()
        assertEquals("ALT-VIS-100", map["id"])
        assertEquals("VISITOR_ARRIVAL", map["type"])
        assertEquals("Casa 54 · Circuito Los Álamos", map["targetHouse"])

        val deserialized = ResidentAlert.fromMap(map)
        assertEquals(alert.id, deserialized.id)
        assertEquals(alert.type, deserialized.type)
        assertEquals(alert.title, deserialized.title)
        assertEquals(alert.message, deserialized.message)
        assertEquals(alert.targetHouse, deserialized.targetHouse)
        assertEquals("Carlos Ruiz", deserialized.metadata["visitorName"])
        assertFalse(deserialized.isRead)
    }

    @Test
    fun testBookingConfirmedAlertSerialization() {
        val alert = ResidentAlert(
            id = "ALT-BOK-200",
            type = AlertType.BOOKING_CONFIRMED,
            title = "📅 Reserva Confirmada: Palapa & Alberca",
            message = "Tu reserva para Palapa & Alberca quedó programada para el 2026-10-12 de 12:00 - 15:00.",
            targetHouse = "Casa 18 · Cto. Encinos",
            timestampMillis = 1700000005000L,
            metadata = mapOf(
                "amenityName" to "Palapa & Alberca",
                "dateString" to "2026-10-12",
                "timeRange" to "12:00 - 15:00"
            ),
            isRead = true
        )

        val map = alert.toMap()
        val deserialized = ResidentAlert.fromMap(map)
        assertEquals(AlertType.BOOKING_CONFIRMED, deserialized.type)
        assertEquals("Palapa & Alberca", deserialized.metadata["amenityName"])
        assertEquals("12:00 - 15:00", deserialized.metadata["timeRange"])
        assertTrue(deserialized.isRead)
    }

    @Test
    fun testTopicSanitization() {
        val rawHouse = "Casa 54 · Circuito Los Álamos"
        val sanitized = rawHouse.lowercase()
            .replace(Regex("[^a-z0-9_]"), "_")
            .trim('_')
        val topic = "topic_$sanitized"

        assertTrue(topic.startsWith("topic_"))
        assertFalse(topic.contains(" "))
        assertFalse(topic.contains("·"))
    }
}
