package com.example.data.incident

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FASE 9: CENTRO DE INCIDENCIAS Y SEGUIMIENTO ALFHA
 *
 * Entidad unificada para el ciclo de vida completo de una incidencia:
 * REGISTRADO ➔ EN_ATENCION ➔ RESUELTO ➔ CERRADO
 *
 * Trazabilidad inmutable que vincula:
 * - Folio, Reportante (Usuario/Rol), Ubicación, Fecha/Hora.
 * - Categoría, Prioridad, Responsable Asignado y SLA Objetivo.
 * - Evidencias, Transiciones de Atención, Dictamen de Resolución y Cierre.
 * - Tiempo Transcurrido y Tiempo Devuelto por Automatización.
 */
@Entity(
    tableName = "incidents",
    indices = [
        Index(value = ["folio"], unique = true),
        Index(value = ["category"]),
        Index(value = ["priority"]),
        Index(value = ["status"]),
        Index(value = ["assignedRole"]),
        Index(value = ["timestampMillis"])
    ]
)
data class IncidentEntity(
    @PrimaryKey
    val folio: String, // INC-YYYYMMDD-XXXX o MED-YYYYMMDD-XXXX
    val rawTranscript: String,
    val category: IncidentCategory,
    val priority: IncidentPriority,
    val location: String,
    val aiSummary: String,
    val recommendedAction: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val guardName: String = "Guardia de turno (Garita 1)",
    val reportedBy: String = guardName,
    val reportedByRole: String = "GUARDIA", // RESIDENTE, GUARDIA, SUPERVISOR, ADMINISTRACION, SISTEMA
    val status: String = "REGISTRADO", // REGISTRADO, EN_ATENCION, RESUELTO, CERRADO
    val assignedTo: String = "Oficial de Guardia en Turno",
    val assignedRole: String = "GUARDIA", // GUARDIA, SUPERVISOR, ADMINISTRACION, MESA_DIRECTIVA
    val targetSlaMinutes: Int = 45, // Minutos objetivo de SLA (15 para Crítica, 45 Alta, 180 Media, 1440 Baja)
    val isEscalated: Boolean = false,
    val escalatedAtMillis: Long? = null,
    val escalationReason: String? = null,
    val attendedAtMillis: Long? = null,
    val attendedBy: String? = null,
    val evidenceNotes: String? = null,
    val resolutionNotes: String? = null,
    val resolvedAtMillis: Long? = null,
    val resolvedBy: String? = null,
    val closedAtMillis: Long? = null,
    val closedBy: String? = null,
    val closureNotes: String? = null,
    val timeSavedMinutes: Int = 20, // Minutos de tiempo devuelto a la comunidad por automatización
    val latitude: Double? = null, // Coordenada Latitud GPS
    val longitude: Double? = null, // Coordenada Longitud GPS
    val gpsAccuracyMeters: Float? = null, // Precisión en metros de la captura satelital
    val locationStatus: String = "UBICACIÓN NO DISPONIBLE", // "GPS_CAPTURADO", "UBICACIÓN NO DISPONIBLE", "ACTUALIZADO"
    val gpsTimestampMillis: Long? = null,
    val isEmergency: Boolean = false // Indica si corresponde a una emergencia / pánico activo
) {
    val formattedDate: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestampMillis))

    /**
     * Calcula el tiempo transcurrido desde el registro en formato legible
     */
    fun getElapsedTimeFormatted(currentMillis: Long = System.currentTimeMillis()): String {
        val diffMillis = (currentMillis - timestampMillis).coerceAtLeast(0)
        val minutes = diffMillis / (60 * 1000)
        val hours = minutes / 60
        val days = hours / 24

        return when {
            minutes < 1 -> "Hace un momento"
            minutes < 60 -> "Hace $minutes min"
            hours < 24 -> "Hace ${hours}h ${minutes % 60}m"
            else -> "Hace ${days}d ${hours % 24}h"
        }
    }

    /**
     * Determina si la incidencia excedió su tiempo objetivo de SLA
     */
    fun isSlaExceeded(currentMillis: Long = System.currentTimeMillis()): Boolean {
        if (status == "RESUELTO" || status == "CERRADO") {
            val endMillis = resolvedAtMillis ?: closedAtMillis ?: currentMillis
            val durationMinutes = (endMillis - timestampMillis) / (60 * 1000)
            return durationMinutes > targetSlaMinutes
        }
        val elapsedMinutes = (currentMillis - timestampMillis) / (60 * 1000)
        return elapsedMinutes > targetSlaMinutes
    }

    /**
     * Retorna el texto del estado de SLA (restante o excedido)
     */
    fun getSlaStatusFormatted(currentMillis: Long = System.currentTimeMillis()): String {
        val elapsedMinutes = ((if (status == "RESUELTO" || status == "CERRADO") (resolvedAtMillis ?: closedAtMillis ?: currentMillis) else currentMillis) - timestampMillis) / (60 * 1000)
        val diff = targetSlaMinutes - elapsedMinutes

        return when {
            diff >= 0 -> "SLA: $diff min restantes (Meta: ${targetSlaMinutes}m)"
            else -> "🚨 SLA Excedido por ${Math.abs(diff)} min (Meta: ${targetSlaMinutes}m)"
        }
    }

    /**
     * Índice de paso para el stepper visual
     */
    val statusStepIndex: Int
        get() = when (status) {
            "REGISTRADO" -> 0
            "EN_ATENCION" -> 1
            "RESUELTO" -> 2
            "CERRADO" -> 3
            else -> 0
        }
}
