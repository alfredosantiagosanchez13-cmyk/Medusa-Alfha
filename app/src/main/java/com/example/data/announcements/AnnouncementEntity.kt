package com.example.data.announcements

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FASE 14: COMUNICADOS Y DOCUMENTOS INTELIGENTES ALFHA
 *
 * Fuente Única de Verdad en Room SQLite.
 * Trazabilidad inmutable con folios unificados COM-YYYYMMDD-XXXX y sellado criptográfico SHA-256.
 *
 * Cero recaptura y registro automático de Tiempo Devuelto (45 min por comunicado).
 */
@Entity(
    tableName = "announcements",
    indices = [
        Index(value = ["folio"], unique = true),
        Index(value = ["category"]),
        Index(value = ["priority"]),
        Index(value = ["targetScope"]),
        Index(value = ["status"]),
        Index(value = ["timestampMillis"])
    ]
)
data class AnnouncementEntity(
    @PrimaryKey
    val folio: String, // COM-YYYYMMDD-XXXX
    val title: String,
    val content: String,
    val category: AnnouncementCategory,
    val priority: AnnouncementPriority,
    val targetScope: AnnouncementTargetScope, // CONDOMINIO, POR_UNIDAD, POR_ROL
    val targetUnits: String? = null, // "Todas" o lista separada por comas: "Casa 101, Casa 102"
    val targetRole: String? = null, // "RESIDENTE", "MESA_DIRECTIVA", "TODOS", etc.
    val senderName: String, // e.g. "Lic. Sofía Alarcón (Administración)"
    val senderRole: String = "ADMINISTRACION", // ADMINISTRACION, MESA_DIRECTIVA, COMITE_VIGILANCIA
    val timestampMillis: Long = System.currentTimeMillis(),
    val effectiveDate: String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(timestampMillis)),
    val attachmentName: String? = null, // e.g. "Convocatoria_Asamblea_2026.pdf"
    val attachmentType: String? = null, // "PDF", "IMAGEN", "DOCUMENTO"
    val attachmentUri: String? = null,
    val attachmentSizeKb: Int = 0,
    val status: String = "PUBLICADO", // PUBLICADO, VIGENTE, ARCHIVADO
    val requiresAcknowledgement: Boolean = false, // Acuse de recibo / confirmación de lectura digital
    val acknowledgementsJson: String = "[]", // Registro JSON de acuses de recibo
    val readCount: Int = 0, // Cantidad total de lecturas registradas
    val sha256Signature: String = "", // Sello criptográfico SHA-256
    val savedTimeMinutes: Int = 45 // Tiempo devuelto por eliminación de impresión, pegado y firmas manuales
) {
    val isUrgent: Boolean
        get() = priority == AnnouncementPriority.URGENTE || category == AnnouncementCategory.AVISO_URGENTE

    val formattedDate: String
        get() = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestampMillis))

    val dateOnly: String
        get() = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestampMillis))
}

enum class AnnouncementCategory(val label: String, val colorHex: Long, val iconCode: String) {
    CIRCULAR("Circular Informativa", 0xFF00F0FF, "description"), // Cian
    CONVOCATORIA_ASAMBLEA("Asamblea General", 0xFFFFD700, "groups"), // Oro
    AVISO_URGENTE("Aviso Urgente", 0xFFFF0055, "warning"), // Rojo Neón
    MANTENIMIENTO_PROGRAMADO("Mantenimiento Programado", 0xFFFFB703, "build"), // Ámbar
    REGLAMENTO_INTERNO("Reglamento Oficial", 0xFF9D4EDD, "gavel"), // Púrpura
    ESTADO_CUENTA("Finanzas & Cuotas", 0xFF4ADE80, "account_balance"), // Verde
    COMUNICADO_OFICIAL("Comunicado Oficial", 0xFF38BDF8, "campaign"); // Celeste

    companion object {
        fun fromString(str: String): AnnouncementCategory {
            return values().firstOrNull {
                it.name.equals(str, ignoreCase = true) || it.label.equals(str, ignoreCase = true)
            } ?: COMUNICADO_OFICIAL
        }
    }
}

enum class AnnouncementPriority(val label: String, val colorHex: Long, val weight: Int) {
    URGENTE("URGENTE", 0xFFFF0055, 1),
    ALTA("ALTA", 0xFFFFB703, 2),
    NORMAL("NORMAL", 0xFF00F0FF, 3),
    INFORMATIVA("INFORMATIVA", 0xFF4ADE80, 4);

    companion object {
        fun fromString(str: String): AnnouncementPriority {
            return values().firstOrNull {
                it.name.equals(str, ignoreCase = true) || it.label.equals(str, ignoreCase = true)
            } ?: NORMAL
        }
    }
}

enum class AnnouncementTargetScope(val label: String, val shortDesc: String) {
    CONDOMINIO("Todo el Condominio", "Todos los residentes, propietarios y personal"),
    POR_UNIDAD("Unidades Específicas", "Destinatarios seleccionados por casa o departamento"),
    POR_ROL("Por Rol / Estamento", "Filtrado por estamento institucional");

    companion object {
        fun fromString(str: String): AnnouncementTargetScope {
            return values().firstOrNull {
                it.name.equals(str, ignoreCase = true) || it.label.equals(str, ignoreCase = true)
            } ?: CONDOMINIO
        }
    }
}

/**
 * Registro de Acuse de Recibo / Confirmación de Lectura
 */
data class ReadAcknowledgement(
    val unitId: String,
    val residentName: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val signatureSha256: String,
    val comments: String? = null
) {
    val formattedTimestamp: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestampMillis))
}
