package com.example.data.notifications

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad inmutable de Notificación Inteligente ALFHA en Room.
 * Almacena el ciclo de vida completo: Generación -> Entrega -> Lectura -> Resolución.
 */
@Entity(
    tableName = "smart_notifications",
    indices = [
        Index(value = ["deduplicationKey"], unique = false),
        Index(value = ["targetRole"]),
        Index(value = ["priority"]),
        Index(value = ["isResolved"]),
        Index(value = ["timestampMillis"])
    ]
)
data class SmartNotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val notificationId: String, // e.g. NOTIF-20260822-104500-A1B2
    val deduplicationKey: String, // Clave única anti-duplicados por evento
    val targetRole: String, // RESIDENTE, GUARDIA, SUPERVISOR, ADMINISTRACION, MESA_DIRECTIVA, MAESTRO_ALFHA, ALL
    val targetRecipient: String, // e.g. "Casa 102 - Juan Pérez", "Garita Principal", "Mesa Directiva"
    val targetUnitId: String? = null, // e.g. "Casa 102"
    val priority: String, // CRITICA, ALTA, MEDIA, PREVENTIVA
    val category: String, // VISITANTE_ENTRADA, VISITANTE_SALIDA, PAQUETERIA, RESERVA_CONFIRMADA, INCIDENCIA, ALERTA_CRITICA, HALLAZGO_CRITICO, RONDA_PENDIENTE, ESCALAMIENTO, RESUMEN_EJECUTIVO
    val title: String,
    val body: String,
    val relatedFolio: String, // Folio unificado MED-..., INC-..., AUD-..., etc.
    val timestampMillis: Long = System.currentTimeMillis(),
    val isDelivered: Boolean = true,
    val deliveredAtMillis: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val readAtMillis: Long? = null,
    val requiresHumanAction: Boolean = false, // Indica si requiere resolución/atención humana
    val isResolved: Boolean = false,
    val resolvedAtMillis: Long? = null,
    val resolvedBy: String? = null,
    val actionLabel: String? = null, // e.g. "Resolver Alerta", "Ver Incidencia", "Confirmar Entrega"
    val actionTarget: String? = null // e.g. "INCIDENT", "SCANNER", "SUPERVISION", "BOOKING"
)

enum class NotificationPriority(val label: String, val level: Int, val colorHex: Long) {
    CRITICA("CRÍTICA", 4, 0xFFFF0055), // Rojo Neón
    ALTA("ALTA", 3, 0xFFFFB703),      // Ámbar Dorado
    MEDIA("MEDIA", 2, 0xFF00F0FF),     // Cian Neón
    PREVENTIVA("PREVENTIVA", 1, 0xFF4ADE80); // Verde Esmeralda

    companion object {
        fun fromString(str: String): NotificationPriority {
            return values().firstOrNull {
                it.name.equals(str, ignoreCase = true) || it.label.equals(str, ignoreCase = true)
            } ?: MEDIA
        }
    }
}

enum class NotificationCategory(val label: String, val iconCode: String) {
    VISITANTE_ENTRADA("Entrada de Visitante", "door_open"),
    VISITANTE_SALIDA("Salida de Visitante", "door_closed"),
    PAQUETERIA("Paquetería Recibida", "inventory_2"),
    RESERVA_CONFIRMADA("Reserva Confirmada", "event_available"),
    INCIDENCIA("Incidencia Asignada", "warning"),
    ALERTA_CRITICA("Alerta Crítica", "report_problem"),
    HALLAZGO_CRITICO("Hallazgo Crítico", "policy"),
    RONDA_PENDIENTE("Ronda Pendiente", "schedule"),
    ESCALAMIENTO("Escalamiento Pendiente", "trending_up"),
    CONFLICTO_RESERVA("Conflicto de Reserva", "rule"),
    DIRECTORIO_RESIDENCIAL("Directorio Residencial", "people"),
    MANTENIMIENTO_ORDEN("Mantenimiento y OT", "build"),
    MANTENIMIENTO_SLA("Alerta SLA Mantenimiento", "alarm"),
    TAREA_PENDIENTE("Tarea Pendiente", "pending_actions"),
    ACCESO_VEHICULAR("Acceso Vehicular", "directions_car"),
    CONTROL_VEHICULAR("Control Vehicular", "directions_car"),
    COMUNICADO_OFICIAL("Comunicado Oficial", "campaign"),
    DOCUMENTO_INTELIGENTE("Documento Inteligente", "description"),
    RESUMEN_EJECUTIVO("Resumen Ejecutivo", "analytics")
}
