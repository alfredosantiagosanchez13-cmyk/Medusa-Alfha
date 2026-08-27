package com.example.data.announcements

import android.content.Context
import android.util.Log
import com.example.auth.AlfhaRole
import com.example.data.audit.AuditLogEntity
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.notifications.NotificationCategory
import com.example.data.notifications.NotificationPriority
import com.example.data.notifications.SmartNotificationHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MOTOR DE COMUNICADOS Y DOCUMENTOS INTELIGENTES ALFHA (FASE 14)
 *
 * Automatización integral:
 * - Creación desde Administración con selección dinámica de destinatarios.
 * - Generación automática de Folio inmutable COM-YYYYMMDD-XXXX y sellado criptográfico.
 * - Despacho de notificaciones automáticas y en-app multirrol.
 * - Registro de acuse de lectura con firma SHA-256 por unidad/residente.
 * - Medición del Tiempo Devuelto (45 min por comunicado).
 * - Cero recaptura y Room SQLite como Fuente Única de Verdad.
 */
object AnnouncementEngine {

    private const val TAG = "AnnouncementEngine"
    const val TIME_SAVED_MINUTES_PER_ANNOUNCEMENT = 45 // 15 min redacción/formato + 15 min impresión/fotocopiado + 15 min distribución/firmas

    sealed class AnnouncementResult {
        data class Success(val announcement: AnnouncementEntity, val message: String) : AnnouncementResult()
        data class Error(val error: String) : AnnouncementResult()
    }

    /**
     * Crea y publica un nuevo Comunicado Oficial o Documento Inteligente.
     */
    suspend fun createAnnouncement(
        context: Context,
        db: AppDatabase,
        title: String,
        content: String,
        category: AnnouncementCategory,
        priority: AnnouncementPriority,
        targetScope: AnnouncementTargetScope,
        targetUnits: String? = null,
        targetRole: String? = null,
        senderName: String,
        senderRole: String = "ADMINISTRACION",
        attachmentName: String? = null,
        attachmentType: String? = null,
        attachmentUri: String? = null,
        attachmentSizeKb: Int = 0,
        requiresAcknowledgement: Boolean = false
    ): AnnouncementResult = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = title.trim()
            if (cleanTitle.isBlank()) {
                return@withContext AnnouncementResult.Error("El título del comunicado no puede estar vacío.")
            }
            val cleanContent = content.trim()
            if (cleanContent.isBlank()) {
                return@withContext AnnouncementResult.Error("El contenido o cuerpo del comunicado es obligatorio.")
            }

            val folio = AlphaCoreEngine.generateUniqueFolio("COM")
            val now = System.currentTimeMillis()
            val effectiveDateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(now))

            // Firma de integridad SHA-256
            val signaturePayload = "$folio|$cleanTitle|$cleanContent|$senderName|$effectiveDateStr|MEDUSA_ALFHA_DOC_2026"
            val sha256 = MessageDigest.getInstance("SHA-256")
                .digest(signaturePayload.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

            val entity = AnnouncementEntity(
                folio = folio,
                title = cleanTitle,
                content = cleanContent,
                category = category,
                priority = priority,
                targetScope = targetScope,
                targetUnits = if (targetScope == AnnouncementTargetScope.POR_UNIDAD) targetUnits?.trim() else "Todas",
                targetRole = if (targetScope == AnnouncementTargetScope.POR_ROL) targetRole else "TODOS",
                senderName = senderName.trim().ifBlank { "Administración General" },
                senderRole = senderRole,
                timestampMillis = now,
                effectiveDate = effectiveDateStr,
                attachmentName = attachmentName?.trim()?.ifBlank { null },
                attachmentType = attachmentType?.trim()?.ifBlank { null },
                attachmentUri = attachmentUri?.trim()?.ifBlank { null },
                attachmentSizeKb = attachmentSizeKb,
                status = "PUBLICADO",
                requiresAcknowledgement = requiresAcknowledgement,
                acknowledgementsJson = "[]",
                readCount = 0,
                sha256Signature = sha256,
                savedTimeMinutes = TIME_SAVED_MINUTES_PER_ANNOUNCEMENT
            )

            // 1. Guardar en Room SQLite
            db.announcementDao().insertAnnouncement(entity)

            // 2. Registro inmutable en Cadena de Auditoría
            db.auditLogDao().insertAuditLog(
                AuditLogEntity(
                    operatorName = senderName,
                    actionType = "EMISION_COMUNICADO_OFICIAL",
                    location = if (targetScope == AnnouncementTargetScope.POR_UNIDAD) (targetUnits ?: "Condominio") else "Condominio",
                    targetEntity = folio,
                    changeDetails = "Comunicado $folio emitido ($cleanTitle). Categoría: ${category.label}, Prioridad: ${priority.label}, Alcance: ${targetScope.label}. Adjunto: ${attachmentName ?: "Ninguno"}. Tiempo Devuelto: 45 min",
                    resultStatus = "EXITOSO"
                )
            )

            // 3. Despachar Notificaciones Inteligentes automáticas
            try {
                val notifPriority = when (priority) {
                    AnnouncementPriority.URGENTE -> NotificationPriority.CRITICA
                    AnnouncementPriority.ALTA -> NotificationPriority.ALTA
                    AnnouncementPriority.NORMAL -> NotificationPriority.MEDIA
                    AnnouncementPriority.INFORMATIVA -> NotificationPriority.PREVENTIVA
                }

                val notifRole = when (targetScope) {
                    AnnouncementTargetScope.CONDOMINIO -> AlfhaRole.RESIDENTE
                    AnnouncementTargetScope.POR_UNIDAD -> AlfhaRole.RESIDENTE
                    AnnouncementTargetScope.POR_ROL -> {
                        when (targetRole) {
                            "MESA_DIRECTIVA" -> AlfhaRole.MESA_DIRECTIVA
                            "ADMINISTRACION" -> AlfhaRole.ADMINISTRACION
                            "SEGURIDAD" -> AlfhaRole.GUARDIA
                            else -> AlfhaRole.RESIDENTE
                        }
                    }
                }

                val recipientDesc = when (targetScope) {
                    AnnouncementTargetScope.CONDOMINIO -> "Todo el Condominio"
                    AnnouncementTargetScope.POR_UNIDAD -> "Unidades: ${targetUnits ?: "Asignadas"}"
                    AnnouncementTargetScope.POR_ROL -> "Rol: ${targetRole ?: "Todos"}"
                }

                // Notificación para Residentes / Destinatarios
                SmartNotificationHub.notifyAnnouncementBroadcast(
                    context = context,
                    db = db,
                    folio = folio,
                    title = "📢 [${category.label.uppercase()}] $cleanTitle",
                    body = if (cleanContent.length > 120) cleanContent.take(120) + "..." else cleanContent,
                    priority = notifPriority,
                    targetRole = notifRole,
                    targetRecipient = recipientDesc,
                    targetUnitId = if (targetScope == AnnouncementTargetScope.POR_UNIDAD) targetUnits else null,
                    requiresAcknowledgement = requiresAcknowledgement
                )
            } catch (notifEx: Exception) {
                Log.w(TAG, "No se pudo disparar notificación de comunicado: ${notifEx.message}")
            }

            AnnouncementResult.Success(entity, "Comunicado $folio publicado exitosamente. Tiempo Devuelto: 45 min.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al crear comunicado: ${e.message}", e)
            AnnouncementResult.Error("Error al guardar comunicado: ${e.message}")
        }
    }

    /**
     * Registra un Acuse de Recibo / Confirmación de Lectura Digital.
     */
    suspend fun registerReadAcknowledgement(
        db: AppDatabase,
        folio: String,
        unitId: String,
        residentName: String,
        comments: String? = null
    ): AnnouncementResult = withContext(Dispatchers.IO) {
        try {
            val announcement = db.announcementDao().getAnnouncementByFolio(folio)
                ?: return@withContext AnnouncementResult.Error("Comunicado $folio no encontrado.")

            val existingAcks = parseAcknowledgements(announcement.acknowledgementsJson).toMutableList()

            // Verificar si ya confirmó
            val alreadyRead = existingAcks.any { it.unitId.equals(unitId.trim(), ignoreCase = true) }
            if (alreadyRead) {
                return@withContext AnnouncementResult.Success(announcement, "La unidad $unitId ya había registrado su acuse previamente.")
            }

            val now = System.currentTimeMillis()
            val ackSignature = MessageDigest.getInstance("SHA-256")
                .digest("$folio|$unitId|$residentName|$now|ALFHA_ACK".toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

            val newAck = ReadAcknowledgement(
                unitId = unitId.trim(),
                residentName = residentName.trim(),
                timestampMillis = now,
                signatureSha256 = ackSignature,
                comments = comments?.trim()?.ifBlank { null }
            )

            existingAcks.add(newAck)
            val updatedJson = serializeAcknowledgements(existingAcks)
            val updatedCount = existingAcks.size

            val updatedAnnouncement = announcement.copy(
                acknowledgementsJson = updatedJson,
                readCount = updatedCount
            )

            db.announcementDao().updateAnnouncement(updatedAnnouncement)

            // Registro inmutable en Cadena de Auditoría
            db.auditLogDao().insertAuditLog(
                AuditLogEntity(
                    operatorName = residentName,
                    actionType = "ACUSE_LECTURA_COMUNICADO",
                    location = unitId,
                    targetEntity = folio,
                    changeDetails = "Acuse de recibo confirmado para $folio por $residentName ($unitId). Firma: ${ackSignature.take(12)}...",
                    resultStatus = "EXITOSO"
                )
            )

            AnnouncementResult.Success(updatedAnnouncement, "Acuse de lectura registrado correctamente.")
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando acuse de recibo: ${e.message}", e)
            AnnouncementResult.Error("Error al registrar acuse: ${e.message}")
        }
    }

    /**
     * Archiva un comunicado.
     */
    suspend fun archiveAnnouncement(
        db: AppDatabase,
        folio: String,
        operatorName: String
    ): AnnouncementResult = withContext(Dispatchers.IO) {
        try {
            val announcement = db.announcementDao().getAnnouncementByFolio(folio)
                ?: return@withContext AnnouncementResult.Error("Comunicado $folio no encontrado.")

            val updated = announcement.copy(status = "ARCHIVADO")
            db.announcementDao().updateAnnouncement(updated)

            db.auditLogDao().insertAuditLog(
                AuditLogEntity(
                    operatorName = operatorName,
                    actionType = "ARCHIVAR_COMUNICADO",
                    location = "Administración",
                    targetEntity = folio,
                    changeDetails = "Comunicado $folio archivado por $operatorName",
                    resultStatus = "EXITOSO"
                )
            )

            AnnouncementResult.Success(updated, "Comunicado $folio archivado.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al archivar comunicado: ${e.message}", e)
            AnnouncementResult.Error("Error al archivar: ${e.message}")
        }
    }

    // =========================================================================
    // UTILIDADES DE SERIALIZACIÓN JSON Y ACUSES
    // =========================================================================

    fun parseAcknowledgements(jsonStr: String): List<ReadAcknowledgement> {
        if (jsonStr.isBlank() || jsonStr == "[]") return emptyList()
        val list = mutableListOf<ReadAcknowledgement>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ReadAcknowledgement(
                        unitId = obj.optString("unitId", "General"),
                        residentName = obj.optString("residentName", "Residente"),
                        timestampMillis = obj.optLong("timestampMillis", System.currentTimeMillis()),
                        signatureSha256 = obj.optString("signatureSha256", ""),
                        comments = if (obj.has("comments") && !obj.isNull("comments")) obj.optString("comments") else null
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing acknowledgements JSON: ${e.message}")
        }
        return list
    }

    fun serializeAcknowledgements(list: List<ReadAcknowledgement>): String {
        val array = JSONArray()
        for (ack in list) {
            val obj = JSONObject().apply {
                put("unitId", ack.unitId)
                put("residentName", ack.residentName)
                put("timestampMillis", ack.timestampMillis)
                put("signatureSha256", ack.signatureSha256)
                if (ack.comments != null) {
                    put("comments", ack.comments)
                }
            }
            array.put(obj)
        }
        return array.toString()
    }

    // =========================================================================
    // MÉTRICAS Y REPORTES EJECUTIVOS
    // =========================================================================

    data class CommunicationStats(
        val totalAnnouncements: Int,
        val activeAnnouncements: Int,
        val urgentAnnouncements: Int,
        val totalAcknowledgements: Int,
        val totalTimeSavedMinutes: Int,
        val totalTimeSavedFormatted: String,
        val averageOpeningRatePercent: Double,
        val circularsCount: Int,
        val assembliesCount: Int,
        val maintenanceNoticesCount: Int,
        val financialCount: Int
    )

    fun calculateStats(
        announcements: List<AnnouncementEntity>,
        totalCommunityUnits: Int = 60
    ): CommunicationStats {
        val total = announcements.size
        val active = announcements.count { it.status == "PUBLICADO" }
        val urgent = announcements.count { it.isUrgent && it.status == "PUBLICADO" }
        val totalAcks = announcements.sumOf { it.readCount }
        val timeSavedMins = announcements.sumOf { it.savedTimeMinutes }

        val hours = timeSavedMins / 60
        val mins = timeSavedMins % 60
        val formattedTime = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

        val effectiveUnits = if (totalCommunityUnits <= 0) 1 else totalCommunityUnits
        val openingRate = if (total > 0) {
            val totalPossibleAcks = total * effectiveUnits
            val rate = (totalAcks.toDouble() / totalPossibleAcks.toDouble()) * 100.0
            minOf(100.0, rate)
        } else 0.0

        val circulars = announcements.count { it.category == AnnouncementCategory.CIRCULAR }
        val assemblies = announcements.count { it.category == AnnouncementCategory.CONVOCATORIA_ASAMBLEA }
        val maintenance = announcements.count { it.category == AnnouncementCategory.MANTENIMIENTO_PROGRAMADO }
        val financial = announcements.count { it.category == AnnouncementCategory.ESTADO_CUENTA }

        return CommunicationStats(
            totalAnnouncements = total,
            activeAnnouncements = active,
            urgentAnnouncements = urgent,
            totalAcknowledgements = totalAcks,
            totalTimeSavedMinutes = timeSavedMins,
            totalTimeSavedFormatted = formattedTime,
            averageOpeningRatePercent = openingRate,
            circularsCount = circulars,
            assembliesCount = assemblies,
            maintenanceNoticesCount = maintenance,
            financialCount = financial
        )
    }

    /**
     * Genera el Reporte Ejecutivo Oficial de Comunicación y Documentos Inteligentes
     */
    fun generateExecutiveCommunicationReport(
        announcements: List<AnnouncementEntity>,
        communityName: String = "Condominio Residencial ALFHA",
        administratorName: String = "Administración General"
    ): String {
        val stats = calculateStats(announcements)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)
        val dateStr = dateFormat.format(Date())

        val sb = StringBuilder()
        sb.appendLine("================================================================================")
        sb.appendLine("        MEDUSA ALFHA SECURITY — REPORTE EJECUTIVO DE COMUNICACIÓN Y CIRCULARES")
        sb.appendLine("================================================================================")
        sb.appendLine("Condominio: $communityName")
        sb.appendLine("Emisor: $administratorName")
        sb.appendLine("Fecha de Emisión: $dateStr")
        sb.appendLine("Fuente Única de Verdad: SQLite Room (Cero Recaptura)")
        sb.appendLine("Sello Criptográfico: SHA-256 Inmutable")
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("MÉTRICA SAGRADA — TIEMPO DEVUELTO:")
        sb.appendLine("  • Total Tiempo Ahorrado por Automatización: ${stats.totalTimeSavedFormatted}")
        sb.appendLine("  • Minutos Ahorrados: ${stats.totalTimeSavedMinutes} min")
        sb.appendLine("  • Ahorro Promedio por Comunicado: 45 min (Eliminación de papel, fotocopiado y firmas)")
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("RESUMEN DE COMUNICACIÓN Y GOBERNANZA:")
        sb.appendLine("  • Total Comunicados Emitidos: ${stats.totalAnnouncements}")
        sb.appendLine("  • Circulares Vigentes: ${stats.activeAnnouncements}")
        sb.appendLine("  • Avisos Urgentes Activos: ${stats.urgentAnnouncements}")
        sb.appendLine("  • Acuses de Recibo Registrados: ${stats.totalAcknowledgements}")
        sb.appendLine("  • Tasa de Apertura Global: ${String.format(Locale.US, "%.1f", stats.averageOpeningRatePercent)}%")
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("DESGLOSE POR CATEGORÍA:")
        sb.appendLine("  • Circulares Informativas: ${stats.circularsCount}")
        sb.appendLine("  • Convocatorias a Asamblea: ${stats.assembliesCount}")
        sb.appendLine("  • Mantenimientos Programados: ${stats.maintenanceNoticesCount}")
        sb.appendLine("  • Estados de Cuenta y Finanzas: ${stats.financialCount}")
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("HISTORIAL RECIENTE DE COMUNICADOS:")

        if (announcements.isEmpty()) {
            sb.appendLine("  (No hay comunicados registrados en el sistema)")
        } else {
            announcements.take(10).forEachIndexed { idx, item ->
                sb.appendLine("  [${idx + 1}] Folio: ${item.folio} | Cat: ${item.category.label} | Pri: ${item.priority.label}")
                sb.appendLine("      Título: ${item.title}")
                sb.appendLine("      Fecha: ${item.effectiveDate} | Emisor: ${item.senderName}")
                sb.appendLine("      Alcance: ${item.targetScope.label} (${item.targetUnits ?: "Todas"})")
                sb.appendLine("      Adjunto: ${item.attachmentName ?: "N/A"} | Acuses: ${item.readCount}")
                sb.appendLine("      SHA-256: ${item.sha256Signature.take(16)}...")
                sb.appendLine("      ---")
            }
        }

        sb.appendLine("================================================================================")
        sb.appendLine("Certificado por el Motor de Integridad ALFHA. 'ESTO DEVUELVE TIEMPO.'")
        sb.appendLine("================================================================================")
        return sb.toString()
    }

    /**
     * Sembrado inicial de comunicados oficiales y circulares reales si la tabla está vacía
     */
    suspend fun seedInitialAnnouncementsIfEmpty(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        try {
            val count = db.announcementDao().countTotal()
            if (count == 0) {
                // 1. Convocatoria a Asamblea General Ordinaria 2026
                createAnnouncement(
                    context = context,
                    db = db,
                    title = "Convocatoria Oficial: Asamblea General Ordinaria 2026",
                    content = "Por medio de la presente, la Mesa Directiva y la Administración convocan a todos los condóminos y propietarios a la Asamblea General Ordinaria que se celebrará el sábado 5 de Septiembre a las 10:00 hrs en la Casa Club. Orden del día: 1. Aprobación de Estados Financieros, 2. Presentación del Proyecto de Automatización MEDUSA ALFHA, 3. Renovación de Comité de Vigilancia. Se requiere confirmación de lectura mediante acuse digital.",
                    category = AnnouncementCategory.CONVOCATORIA_ASAMBLEA,
                    priority = AnnouncementPriority.ALTA,
                    targetScope = AnnouncementTargetScope.CONDOMINIO,
                    senderName = "Mesa Directiva & Lic. Sofía Alarcón",
                    senderRole = "MESA_DIRECTIVA",
                    attachmentName = "Convocatoria_Asamblea_Ordinaria_2026.pdf",
                    attachmentType = "PDF",
                    attachmentSizeKb = 420,
                    requiresAcknowledgement = true
                )

                // 2. Mantenimiento Preventivo del Sistema Hidroneumático
                createAnnouncement(
                    context = context,
                    db = db,
                    title = "Aviso Operativo: Mantenimiento Preventivo del Sistema Hidroneumático",
                    content = "Se informa a la comunidad que el próximo martes de 09:00 a 13:00 hrs se llevará a cabo el lavado y desinfección de cisternas y mantenimiento a las bombas de presión. El suministro continuará por gravedad con presión moderada. Favor de tomar previsiones necesarias.",
                    category = AnnouncementCategory.MANTENIMIENTO_PROGRAMADO,
                    priority = AnnouncementPriority.NORMAL,
                    targetScope = AnnouncementTargetScope.CONDOMINIO,
                    senderName = "Ing. Roberto Mendieta (Jefe de Mantenimiento)",
                    senderRole = "ADMINISTRACION",
                    attachmentName = "Calendario_Mantenimiento_Hidraulico_2026.pdf",
                    attachmentType = "PDF",
                    attachmentSizeKb = 210,
                    requiresAcknowledgement = false
                )

                // 3. Circular Oficial de Convivencia y Reglamento Interno
                createAnnouncement(
                    context = context,
                    db = db,
                    title = "Actualización Oficial del Reglamento Interno y Normas de Convivencia",
                    content = "Estimados residentes: Se adjunta el compendio digital actualizado del Reglamento Interno aprobado en la última asamblea extraordinaria. Recordamos el uso obligatorio de correa para mascotas en áreas verdes comunes y el horario de respeto auditivo a partir de las 22:00 hrs.",
                    category = AnnouncementCategory.REGLAMENTO_INTERNO,
                    priority = AnnouncementPriority.INFORMATIVA,
                    targetScope = AnnouncementTargetScope.CONDOMINIO,
                    senderName = "Lic. Sofía Alarcón (Administración General)",
                    senderRole = "ADMINISTRACION",
                    attachmentName = "Reglamento_Interno_ALFHA_2026.pdf",
                    attachmentType = "PDF",
                    attachmentSizeKb = 580,
                    requiresAcknowledgement = true
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sembrando comunicados iniciales: ${e.message}", e)
        }
    }
}

