package com.example.data.maintenance

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FASE 13: MANTENIMIENTO Y ÓRDENES DE TRABAJO ALFHA
 *
 * Estados del Ciclo de Vida:
 * REGISTRADO ➔ ASIGNADO ➔ EN_ATENCION ➔ RESUELTO ➔ CERRADO
 *
 * Fuente Única de Verdad en Room SQLite.
 */
@Entity(
    tableName = "maintenance_orders",
    indices = [
        Index(value = ["folio"], unique = true),
        Index(value = ["category"]),
        Index(value = ["priority"]),
        Index(value = ["status"]),
        Index(value = ["unitId"]),
        Index(value = ["assignedTechnician"]),
        Index(value = ["timestampMillis"]),
        Index(value = ["deadlineMillis"])
    ]
)
data class MaintenanceOrderEntity(
    @PrimaryKey
    val folio: String, // MNT-YYYYMMDD-XXXX
    val title: String,
    val description: String,
    val category: MaintenanceCategory,
    val priority: MaintenancePriority,
    val locationType: MaintenanceLocationType = MaintenanceLocationType.UNIDAD_PRIVADA,
    val location: String, // e.g. "Casa 102" o "Área de Alberca"
    val unitId: String? = null,
    val requesterName: String,
    val requesterRole: String = "RESIDENTE", // RESIDENTE, ADMINISTRACION, SUPERVISOR, GUARDIA
    val requesterPhone: String = "",
    val initialPhotoUri: String? = null, // Evidencia fotográfica inicial
    val status: String = "REGISTRADO", // REGISTRADO, ASIGNADO, EN_ATENCION, RESUELTO, CERRADO
    val assignedTechnician: String = "Por Asignar",
    val assignedTechnicianPhone: String = "",
    val assignedRole: String = "MANTENIMIENTO",
    val assignedAtMillis: Long? = null,
    val assignedBy: String? = null,
    val slaTargetHours: Int = 24, // Horas de SLA (4h Urgente, 12h Alta, 24h Media, 72h Baja)
    val timestampMillis: Long = System.currentTimeMillis(),
    val deadlineMillis: Long = timestampMillis + (slaTargetHours * 3600 * 1000L),
    val startedAttentionAtMillis: Long? = null,
    val attendedBy: String? = null,
    val materialsUsed: String? = null, // Materiales utilizados (ej. "2 válvulas de paso 1/2, pegamento PVC")
    val materialsCost: Double = 0.0, // Costo de materiales en MXN
    val solutionDescription: String? = null, // Evidencia descriptiva de la solución
    val solutionPhotoUri: String? = null, // Evidencia fotográfica del trabajo terminado
    val resolvedAtMillis: Long? = null,
    val resolvedBy: String? = null,
    val closedAtMillis: Long? = null,
    val closedBy: String? = null,
    val closureNotes: String? = null,
    val residentSatisfactionRating: Int? = null, // 1..5 estrellas
    val timeSavedMinutes: Int = 30 // Minutos devueltos por eliminación de llamadas, papel y recaptura
) {
    val formattedCreatedDate: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestampMillis))

    val formattedDeadline: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(deadlineMillis))

    val formattedResolvedDate: String?
        get() = resolvedAtMillis?.let { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it)) }

    val formattedClosedDate: String?
        get() = closedAtMillis?.let { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it)) }

    /**
     * Calcula el tiempo transcurrido desde el registro en formato legible
     */
    fun getElapsedTimeFormatted(currentMillis: Long = System.currentTimeMillis()): String {
        val endMillis = if (status == "RESUELTO" || status == "CERRADO") {
            resolvedAtMillis ?: closedAtMillis ?: currentMillis
        } else {
            currentMillis
        }
        val diffMillis = (endMillis - timestampMillis).coerceAtLeast(0)
        val minutes = diffMillis / (60 * 1000)
        val hours = minutes / 60
        val days = hours / 24

        return when {
            minutes < 1 -> "Hace un momento"
            minutes < 60 -> "$minutes min"
            hours < 24 -> "${hours}h ${minutes % 60}m"
            else -> "${days}d ${hours % 24}h"
        }
    }

    /**
     * Determina si la orden de trabajo excedió su tiempo de SLA
     */
    fun isSlaExceeded(currentMillis: Long = System.currentTimeMillis()): Boolean {
        val endMillis = if (status == "RESUELTO" || status == "CERRADO") {
            resolvedAtMillis ?: closedAtMillis ?: currentMillis
        } else {
            currentMillis
        }
        return endMillis > deadlineMillis
    }

    /**
     * Minutos restantes o de retraso frente al SLA
     */
    fun getRemainingSlaMinutes(currentMillis: Long = System.currentTimeMillis()): Long {
        val endMillis = if (status == "RESUELTO" || status == "CERRADO") {
            resolvedAtMillis ?: closedAtMillis ?: currentMillis
        } else {
            currentMillis
        }
        return (deadlineMillis - endMillis) / (60 * 1000)
    }

    /**
     * Texto formateado de estado de SLA
     */
    fun getSlaStatusFormatted(currentMillis: Long = System.currentTimeMillis()): String {
        if (status == "RESUELTO" || status == "CERRADO") {
            val endMillis = resolvedAtMillis ?: closedAtMillis ?: currentMillis
            val isExceeded = endMillis > deadlineMillis
            val diffMins = Math.abs((deadlineMillis - endMillis) / (60 * 1000))
            return if (isExceeded) {
                "🚨 SLA Excedido (+${diffMins / 60}h ${diffMins % 60}m)"
            } else {
                "✅ Resuelto en Tiempo (Margen: ${diffMins / 60}h ${diffMins % 60}m)"
            }
        }

        val remainingMins = getRemainingSlaMinutes(currentMillis)
        return when {
            remainingMins < 0 -> {
                val overdue = Math.abs(remainingMins)
                "🚨 SLA VENCIDO (+${overdue / 60}h ${overdue % 60}m)"
            }
            remainingMins < 120 -> {
                "⚠️ SLA EN RIESGO (${remainingMins} min restantes)"
            }
            else -> {
                val hours = remainingMins / 60
                val mins = remainingMins % 60
                "⏱️ SLA: ${hours}h ${mins}m restantes"
            }
        }
    }

    /**
     * Índice de paso para el stepper visual
     */
    val statusStepIndex: Int
        get() = when (status) {
            "REGISTRADO" -> 0
            "ASIGNADO" -> 1
            "EN_ATENCION" -> 2
            "RESUELTO" -> 3
            "CERRADO" -> 4
            else -> 0
        }
}

enum class MaintenanceCategory(val label: String, val iconName: String) {
    PLOMERIA("Plomería e Hidráulica", "water_drop"),
    ELECTRICIDAD("Electricidad e Iluminación", "bolt"),
    ALBANILERIA("Albañilería y Estructura", "foundation"),
    PINTURA("Pintura y Acabados", "format_paint"),
    JARDINERIA("Jardinería y Áreas Verdes", "yard"),
    CERRAJERIA("Cerrajería y Accesos", "key"),
    ELEVADORES("Elevadores y Elevación", "elevator"),
    PISCINA_AMENIDADES("Alberca y Amenidades", "pool"),
    CLIMATIZACION("Climatización / HVAC", "ac_unit"),
    PORTONES_AUTOMATIZACION("Portones y Automatización", "garage"),
    GENERAL("Mantenimiento General", "handyman")
}

enum class MaintenancePriority(val label: String, val defaultSlaHours: Int, val colorHex: Long) {
    URGENTE("URGENTE (4h)", 4, 0xFFFF0055),    // Rojo Neón
    ALTA("ALTA (12h)", 12, 0xFFFFB703),        // Ámbar Dorado
    MEDIA("MEDIA (24h)", 24, 0xFF00F0FF),      // Cian Neón
    BAJA("BAJA (72h)", 72, 0xFF4ADE80);        // Verde Esmeralda

    companion object {
        fun fromString(str: String): MaintenancePriority {
            return values().firstOrNull {
                it.name.equals(str, ignoreCase = true) || it.label.startsWith(str, ignoreCase = true)
            } ?: MEDIA
        }
    }
}

enum class MaintenanceLocationType(val label: String) {
    UNIDAD_PRIVADA("Unidad Privada"),
    AREA_COMUN("Área Común")
}
