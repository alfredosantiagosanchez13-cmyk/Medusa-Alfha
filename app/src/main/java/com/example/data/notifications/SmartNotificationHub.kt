package com.example.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.auth.AlfhaPermission
import com.example.auth.AlfhaRole
import com.example.auth.AlfhaSecurityContext
import com.example.data.audit.AuditLogEntity
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HUB CENTRAL DE NOTIFICACIONES INTELIGENTES ALFHA (FASE 8)
 *
 * Automatiza únicamente notificaciones generadas por EVENTOS REALES.
 * Cumple con:
 * - Anti-duplicados estricto (Deduplication Window & Key).
 * - Enrutamiento específico por Rol (Residente, Guardia, Supervisor, Administración, Mesa Directiva, Maestro).
 * - Persistencia inmutable en Room DB (`SmartNotificationEntity`).
 * - Trazabilidad de Ciclo de Vida: Generada -> Entregada -> Leída -> Resuelta.
 * - Priorización Estricta: CRÍTICA (P1), ALTA (P2), MEDIA (P3), PREVENTIVA (P4).
 * - Cero Ruido: La información llega a quien debe actuar en el momento exacto.
 */
object SmartNotificationHub {

    private const val TAG = "SmartNotificationHub"

    // Canales de Notificación del Sistema Android
    private const val CHANNEL_CRITICAL_ID = "alfha_critical_alerts"
    private const val CHANNEL_HIGH_ID = "alfha_high_priority"
    private const val CHANNEL_RESIDENT_ID = "alfha_resident_updates"
    private const val CHANNEL_OPERATIONAL_ID = "alfha_operational_info"

    fun initializeChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            // Canal 1: CRÍTICAS (Sonido alto, vibración táctica, badge, visibilidad en pantalla de bloqueo)
            val critChannel = NotificationChannel(
                CHANNEL_CRITICAL_ID,
                "ALFHA - Alertas Críticas de Seguridad",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas de emergencia inmediata, accesos no autorizados y anomalías graves"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300, 150, 450)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            // Canal 2: ALTAS (Incidencias asignadas, rondas sin cierre, escalamientos)
            val highChannel = NotificationChannel(
                CHANNEL_HIGH_ID,
                "ALFHA - Eventos Operativos de Alta Prioridad",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Escalamientos, incidencias pendientes y hallazgos tácticos"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200)
                setShowBadge(true)
            }

            // Canal 3: RESIDENTES (Entrada/salida de visitas, paquetería, reservas)
            val residentChannel = NotificationChannel(
                CHANNEL_RESIDENT_ID,
                "ALFHA - Notificaciones para Residentes",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Entrada/salida de invitados, recepción de paquetería y reservas"
                enableVibration(true)
                setShowBadge(true)
            }

            // Canal 4: PREVENTIVAS E INFORMATIVAS (Resúmenes ejecutivos, estados)
            val opChannel = NotificationChannel(
                CHANNEL_OPERATIONAL_ID,
                "ALFHA - Informes Operativos y Resúmenes",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Resúmenes ejecutivos e informes de gestión"
                setShowBadge(false)
            }

            notificationManager.createNotificationChannels(listOf(critChannel, highChannel, residentChannel, opChannel))
        }
    }

    /**
     * Motor base de emisión con validación anti-duplicados y persistencia en Room.
     */
    private suspend fun dispatchSmartNotification(
        context: Context,
        db: AppDatabase,
        targetRole: AlfhaRole,
        targetRecipient: String,
        targetUnitId: String? = null,
        priority: NotificationPriority,
        category: NotificationCategory,
        title: String,
        body: String,
        relatedFolio: String,
        deduplicationKey: String,
        requiresHumanAction: Boolean = false,
        actionLabel: String? = null,
        actionTarget: String? = null,
        suppressSystemNotification: Boolean = false
    ): SmartNotificationEntity? = withContext(Dispatchers.IO) {
        try {
            // 1. Anti-Duplication Check (Ventana de 2 minutos para evitar flapping o doble click)
            val twoMinutesAgo = System.currentTimeMillis() - (120 * 1000L)
            val recentCount = db.smartNotificationDao().countRecentWithKey(deduplicationKey, twoMinutesAgo)
            if (recentCount > 0) {
                Log.d(TAG, "Notificación omitida por regla anti-duplicados: $deduplicationKey")
                return@withContext null
            }

            val notifFolio = AlphaCoreEngine.generateUniqueFolio("NOTIF")
            val entity = SmartNotificationEntity(
                notificationId = notifFolio,
                deduplicationKey = deduplicationKey,
                targetRole = targetRole.roleCode,
                targetRecipient = targetRecipient,
                targetUnitId = targetUnitId,
                priority = priority.name,
                category = category.name,
                title = title,
                body = body,
                relatedFolio = relatedFolio,
                timestampMillis = System.currentTimeMillis(),
                isDelivered = true,
                deliveredAtMillis = System.currentTimeMillis(),
                isRead = false,
                requiresHumanAction = requiresHumanAction,
                isResolved = false,
                actionLabel = actionLabel,
                actionTarget = actionTarget
            )

            // 2. Persistir en Room DB
            val rowId = db.smartNotificationDao().insertNotification(entity)
            val savedEntity = entity.copy(id = rowId)

            // 3. Emitir Notificación del Sistema Android si corresponde
            if (!suppressSystemNotification) {
                sendSystemNotification(context, savedEntity, priority)
            }

            Log.i(TAG, "Notificación Inteligente Emitida [$notifFolio] -> ${targetRole.roleCode} (${priority.name})")
            return@withContext savedEntity
        } catch (e: Exception) {
            Log.e(TAG, "Error emitiendo notificación inteligente: ${e.message}", e)
            return@withContext null
        }
    }

    private fun sendSystemNotification(
        context: Context,
        notification: SmartNotificationEntity,
        priority: NotificationPriority
    ) {
        try {
            initializeChannels(context)

            val channelId = when (priority) {
                NotificationPriority.CRITICA -> CHANNEL_CRITICAL_ID
                NotificationPriority.ALTA -> CHANNEL_HIGH_ID
                NotificationPriority.MEDIA -> CHANNEL_RESIDENT_ID
                NotificationPriority.PREVENTIVA -> CHANNEL_OPERATIONAL_ID
            }

            val notificationId = (notification.id.toInt().let { if (it > 0) it else (System.currentTimeMillis() % 100000).toInt() })

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("NAVIGATE_TO", "NOTIFICATIONS")
                putExtra("TARGET_ROLE", notification.targetRole)
                putExtra("FOLIO", notification.relatedFolio)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val iconRes = when (priority) {
                NotificationPriority.CRITICA -> android.R.drawable.stat_notify_error
                NotificationPriority.ALTA -> android.R.drawable.stat_sys_warning
                else -> android.R.drawable.ic_dialog_info
            }

            val systemNotif = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(iconRes)
                .setContentTitle(notification.title)
                .setContentText(notification.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
                .setPriority(
                    when (priority) {
                        NotificationPriority.CRITICA -> NotificationCompat.PRIORITY_MAX
                        NotificationPriority.ALTA -> NotificationCompat.PRIORITY_HIGH
                        NotificationPriority.MEDIA -> NotificationCompat.PRIORITY_DEFAULT
                        NotificationPriority.PREVENTIVA -> NotificationCompat.PRIORITY_LOW
                    }
                )
                .setCategory(
                    if (priority == NotificationPriority.CRITICA) NotificationCompat.CATEGORY_ALARM
                    else NotificationCompat.CATEGORY_EVENT
                )
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.notify(notificationId, systemNotif)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo disparar notificación de sistema: ${e.message}")
        }
    }

    // =========================================================================
    // 1. EVENTOS PARA RESIDENTE
    // =========================================================================

    /**
     * Entrada de visitante confirmada en garita
     */
    suspend fun notifyVisitorEntry(
        context: Context,
        db: AppDatabase,
        guestName: String,
        unitId: String,
        hostResidentName: String,
        passTypeLabel: String,
        vehiclePlate: String? = null,
        passFolio: String
    ) {
        val plateText = if (!vehiclePlate.isNullOrBlank()) " • Patente: $vehiclePlate" else ""
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val title = "🔔 ¡Tu Visita ha Ingresado! ($unitId)"
        val body = "Estimado/a $hostResidentName: Su visita $guestName ($passTypeLabel) ingresó a las $timeStr hrs hacia $unitId por Garita Principal.$plateText"
        val dedupKey = "VISITOR_ENTRY_${passFolio}_$unitId"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.RESIDENTE,
            targetRecipient = "$unitId - $hostResidentName",
            targetUnitId = unitId,
            priority = NotificationPriority.MEDIA,
            category = NotificationCategory.VISITANTE_ENTRADA,
            title = title,
            body = body,
            relatedFolio = passFolio,
            deduplicationKey = dedupKey,
            requiresHumanAction = false,
            actionLabel = "Ver Historial",
            actionTarget = "SCANNER"
        )
    }

    /**
     * Salida de visitante confirmada en garita
     */
    suspend fun notifyVisitorExit(
        context: Context,
        db: AppDatabase,
        guestName: String,
        unitId: String,
        hostResidentName: String,
        durationStay: String,
        checkInFolio: String
    ) {
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val title = "🚗 Salida Registrada: $unitId"
        val body = "Estimado/a $hostResidentName: Se registró la salida de $guestName a las $timeStr hrs. Tiempo total de permanencia: $durationStay."
        val dedupKey = "VISITOR_EXIT_${checkInFolio}_$unitId"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.RESIDENTE,
            targetRecipient = "$unitId - $hostResidentName",
            targetUnitId = unitId,
            priority = NotificationPriority.MEDIA,
            category = NotificationCategory.VISITANTE_SALIDA,
            title = title,
            body = body,
            relatedFolio = checkInFolio,
            deduplicationKey = dedupKey,
            requiresHumanAction = false,
            actionLabel = "Ver Registro",
            actionTarget = "SCANNER"
        )
    }

    /**
     * Paquetería recibida en caseta de seguridad
     */
    suspend fun notifyPackageReceived(
        context: Context,
        db: AppDatabase,
        unitId: String,
        hostResidentName: String,
        courierName: String,
        packageGuide: String,
        guardName: String
    ) {
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val title = "📦 Paquetería en Garita: $unitId"
        val body = "Hola $hostResidentName: Se ha recibido un paquete de $courierName (Guía: $packageGuide) en Garita Principal a las $timeStr hrs por $guardName. Listo para recolección."
        val dedupKey = "PACKAGE_${unitId}_$packageGuide"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.RESIDENTE,
            targetRecipient = "$unitId - $hostResidentName",
            targetUnitId = unitId,
            priority = NotificationPriority.MEDIA,
            category = NotificationCategory.PAQUETERIA,
            title = title,
            body = body,
            relatedFolio = packageGuide,
            deduplicationKey = dedupKey,
            requiresHumanAction = true,
            actionLabel = "Confirmar Recolección",
            actionTarget = "RESIDENT"
        )
    }

    /**
     * Reserva confirmada de amenidad
     */
    suspend fun notifyBookingConfirmed(
        context: Context,
        db: AppDatabase,
        amenityName: String,
        residentName: String,
        unitId: String,
        scheduleFormatted: String,
        bookingFolio: String
    ) {
        val title = "📅 Reserva Confirmada: $amenityName"
        val body = "Estimado/a $residentName ($unitId): Su reserva de $amenityName para el horario $scheduleFormatted ha sido validada y confirmada con éxito."
        val dedupKey = "BOOKING_CONFIRMED_$bookingFolio"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.RESIDENTE,
            targetRecipient = "$unitId - $residentName",
            targetUnitId = unitId,
            priority = NotificationPriority.PREVENTIVA,
            category = NotificationCategory.RESERVA_CONFIRMADA,
            title = title,
            body = body,
            relatedFolio = bookingFolio,
            deduplicationKey = dedupKey,
            requiresHumanAction = false,
            actionLabel = "Ver Amenidades",
            actionTarget = "BOOKING"
        )
    }

    /**
     * Reserva cancelada de amenidad
     */
    suspend fun notifyBookingCancelled(
        context: Context,
        db: AppDatabase,
        amenityName: String,
        residentName: String,
        unitId: String,
        bookingFolio: String,
        reason: String
    ) {
        val title = "🚫 Reserva Cancelada: $amenityName"
        val body = "Estimado/a $residentName ($unitId): Su reserva [$bookingFolio] para $amenityName ha sido cancelada. Motivo: $reason. El horario ha quedado liberado."
        val dedupKey = "BOOKING_CANCELLED_$bookingFolio"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.RESIDENTE,
            targetRecipient = "$unitId - $residentName",
            targetUnitId = unitId,
            priority = NotificationPriority.PREVENTIVA,
            category = NotificationCategory.RESERVA_CONFIRMADA,
            title = title,
            body = body,
            relatedFolio = bookingFolio,
            deduplicationKey = dedupKey,
            requiresHumanAction = false,
            actionLabel = "Ver Disponibilidad",
            actionTarget = "BOOKING"
        )
    }

    /**
     * Incidencia registrada que le corresponde al residente
     */
    suspend fun notifyResidentIncidentUpdate(
        context: Context,
        db: AppDatabase,
        folio: String,
        unitId: String,
        residentName: String,
        category: String,
        status: String,
        summary: String
    ) {
        val title = "🛠️ Actualización de Incidencia [$folio]"
        val body = "Estimado/a $residentName ($unitId): Su ticket de $category se encuentra en estado: $status. $summary"
        val dedupKey = "INCIDENT_RESIDENT_${folio}_$status"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.RESIDENTE,
            targetRecipient = "$unitId - $residentName",
            targetUnitId = unitId,
            priority = NotificationPriority.ALTA,
            category = NotificationCategory.INCIDENCIA,
            title = title,
            body = body,
            relatedFolio = folio,
            deduplicationKey = dedupKey,
            requiresHumanAction = false,
            actionLabel = "Ver Estado",
            actionTarget = "INCIDENT"
        )
    }

    /**
     * Alta de residente completada
     */
    suspend fun notifyResidentRegistered(
        context: Context,
        db: AppDatabase,
        residentName: String,
        unitId: String,
        residentFolio: String
    ) {
        val title = "🏠 Registro de Residente Completado"
        val body = "Bienvenido/a $residentName ($unitId). Su expediente y credenciales han sido dadas de alta en el sistema."
        val dedupKey = "RES_REGISTERED_${residentFolio}"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.RESIDENTE,
            targetRecipient = "$unitId - $residentName",
            targetUnitId = unitId,
            priority = NotificationPriority.PREVENTIVA,
            category = NotificationCategory.DIRECTORIO_RESIDENCIAL,
            title = title,
            body = body,
            relatedFolio = residentFolio,
            deduplicationKey = dedupKey,
            requiresHumanAction = false,
            actionLabel = "Ver Directorio",
            actionTarget = "RESIDENTS"
        )
    }

    /**
     * Notificación de Baja Lógica de Residente a Administración
     */
    suspend fun notifyResidentSoftDeleted(
        context: Context,
        db: AppDatabase,
        residentName: String,
        unitId: String,
        operatorName: String,
        reason: String,
        residentFolio: String
    ) {
        val title = "⚠️ Baja Lógica de Residente [$residentFolio]"
        val body = "Se procesó la baja lógica de $residentName ($unitId) por $operatorName. Motivo: $reason"
        val dedupKey = "RES_DELETED_${residentFolio}"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.ADMINISTRACION,
            targetRecipient = "Administración General",
            targetUnitId = unitId,
            priority = NotificationPriority.PREVENTIVA,
            category = NotificationCategory.DIRECTORIO_RESIDENCIAL,
            title = title,
            body = body,
            relatedFolio = residentFolio,
            deduplicationKey = dedupKey,
            requiresHumanAction = false,
            actionLabel = "Ver Directorio",
            actionTarget = "RESIDENTS"
        )
    }

    /**
     * FASE 13: Notificación de Registro de Solicitud de Mantenimiento / OT
     */
    suspend fun notifyMaintenanceRegistered(
        context: Context,
        db: AppDatabase,
        folio: String,
        title: String,
        location: String,
        priorityStr: String,
        requesterName: String,
        targetUnitId: String?
    ) {
        val priority = if (priorityStr == "URGENTE") NotificationPriority.CRITICA else if (priorityStr == "ALTA") NotificationPriority.ALTA else NotificationPriority.MEDIA
        val notifTitle = "🛠️ Nueva Orden de Mantenimiento [$folio]"
        val body = "$title en $location ($priorityStr). Solicitada por $requesterName. SLA activado."
        val dedupKey = "MNT_REG_${folio}"

        // Notificar a Administración y Supervisión
        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.ADMINISTRACION,
            targetRecipient = "Mantenimiento / Administración",
            targetUnitId = targetUnitId,
            priority = priority,
            category = NotificationCategory.MANTENIMIENTO_ORDEN,
            title = notifTitle,
            body = body,
            relatedFolio = folio,
            deduplicationKey = dedupKey,
            requiresHumanAction = true,
            actionLabel = "Asignar Técnico",
            actionTarget = "MAINTENANCE"
        )
    }

    /**
     * FASE 13: Notificación de Asignación de Técnico
     */
    suspend fun notifyMaintenanceAssigned(
        context: Context,
        db: AppDatabase,
        folio: String,
        title: String,
        technicianName: String,
        targetUnitId: String?,
        recipientName: String
    ) {
        val notifTitle = "👷 Técnico Asignado a OT [$folio]"
        val body = "La orden '$title' ha sido asignada a $technicianName. En proceso de atención."
        val dedupKey = "MNT_ASSIGN_${folio}_${System.currentTimeMillis() / 60000}"

        // Notificar al Residente / Reportante
        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.RESIDENTE,
            targetRecipient = recipientName,
            targetUnitId = targetUnitId,
            priority = NotificationPriority.PREVENTIVA,
            category = NotificationCategory.MANTENIMIENTO_ORDEN,
            title = notifTitle,
            body = body,
            relatedFolio = folio,
            deduplicationKey = dedupKey,
            requiresHumanAction = false,
            actionLabel = "Ver Orden",
            actionTarget = "MAINTENANCE"
        )
    }

    /**
     * FASE 13: Notificación de Trabajo Resuelto
     */
    suspend fun notifyMaintenanceResolved(
        context: Context,
        db: AppDatabase,
        folio: String,
        title: String,
        solutionNotes: String?,
        targetUnitId: String?,
        recipientName: String
    ) {
        val notifTitle = "✅ Mantenimiento Resuelto [$folio]"
        val body = "Se ha completado la orden '$title'. Solución: ${solutionNotes ?: "Trabajo finalizado con éxito"}. Pendiente de cierre."
        val dedupKey = "MNT_RESOLVED_${folio}"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.RESIDENTE,
            targetRecipient = recipientName,
            targetUnitId = targetUnitId,
            priority = NotificationPriority.PREVENTIVA,
            category = NotificationCategory.MANTENIMIENTO_ORDEN,
            title = notifTitle,
            body = body,
            relatedFolio = folio,
            deduplicationKey = dedupKey,
            requiresHumanAction = true,
            actionLabel = "Confirmar Cierre",
            actionTarget = "MAINTENANCE"
        )
    }

    /**
     * FASE 13: Alerta de SLA Vencido o en Riesgo
     */
    suspend fun notifyMaintenanceSlaAlert(
        context: Context,
        db: AppDatabase,
        folio: String,
        title: String,
        location: String,
        technician: String,
        isOverdue: Boolean,
        diffMinutes: Long
    ) {
        val notifTitle = if (isOverdue) "🚨 SLA VENCIDO: Mantenimiento [$folio]" else "⚠️ SLA En Riesgo: Mantenimiento [$folio]"
        val body = if (isOverdue) {
            "La OT '$title' en $location asignada a $technician excedió el SLA por ${diffMinutes / 60}h ${diffMinutes % 60}m."
        } else {
            "La OT '$title' en $location tiene menos de 2h de vigencia SLA ($diffMinutes min restantes)."
        }
        val dedupKey = "MNT_SLA_${folio}_${if (isOverdue) "OVERDUE" else "RISK"}"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.ADMINISTRACION,
            targetRecipient = "Mesa Directiva & Admin",
            targetUnitId = null,
            priority = if (isOverdue) NotificationPriority.CRITICA else NotificationPriority.ALTA,
            category = NotificationCategory.MANTENIMIENTO_SLA,
            title = notifTitle,
            body = body,
            relatedFolio = folio,
            deduplicationKey = dedupKey,
            requiresHumanAction = true,
            actionLabel = "Ver Alerta OT",
            actionTarget = "MAINTENANCE"
        )
    }

    // =========================================================================
    // 2. EVENTOS PARA GUARDIA
    // =========================================================================

    /**
     * Alerta crítica de seguridad para guardia
     */
    suspend fun notifyGuardCriticalAlert(
        context: Context,
        db: AppDatabase,
        alertFolio: String,
        location: String,
        description: String,
        actionRequired: String
    ) {
        val title = "🚨 ALERTA CRÍTICA EN CASETA [$alertFolio]"
        val body = "Ubicación: $location. $description. ACCIÓN REQUERIDA: $actionRequired"
        val dedupKey = "GUARD_CRIT_ALERT_$alertFolio"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.GUARDIA,
            targetRecipient = "Personal de Garita",
            priority = NotificationPriority.CRITICA,
            category = NotificationCategory.ALERTA_CRITICA,
            title = title,
            body = body,
            relatedFolio = alertFolio,
            deduplicationKey = dedupKey,
            requiresHumanAction = true,
            actionLabel = "Atender Inmediatamente",
            actionTarget = "ALERT"
        )
    }

    /**
     * Visitante pre-autorizado por residente listo para llegada
     */
    suspend fun notifyGuardVisitorAuthorized(
        context: Context,
        db: AppDatabase,
        guestName: String,
        destinationHouse: String,
        hostResidentName: String,
        passFolio: String
    ) {
        val title = "🎟️ Pase QR Autorizado: $guestName"
        val body = "Nuevo pase emitido por $hostResidentName para $guestName destino $destinationHouse. Código: $passFolio"
        val dedupKey = "GUARD_PASS_AUTH_$passFolio"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.GUARDIA,
            targetRecipient = "Garita Principal",
            priority = NotificationPriority.MEDIA,
            category = NotificationCategory.VISITANTE_ENTRADA,
            title = title,
            body = body,
            relatedFolio = passFolio,
            deduplicationKey = dedupKey,
            requiresHumanAction = false,
            actionLabel = "Escanear QR",
            actionTarget = "SCANNER"
        )
    }

    /**
     * Incidencia asignada para verificación en sitio por guardia
     */
    suspend fun notifyGuardIncidentAssigned(
        context: Context,
        db: AppDatabase,
        folio: String,
        location: String,
        category: String,
        instructions: String
    ) {
        val title = "👮 Incidencia Asignada a Garita [$folio]"
        val body = "Reporte de $category en $location. Instrucción: $instructions"
        val dedupKey = "GUARD_INCIDENT_ASSIGNED_$folio"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.GUARDIA,
            targetRecipient = "Guardia de Turno",
            priority = NotificationPriority.ALTA,
            category = NotificationCategory.INCIDENCIA,
            title = title,
            body = body,
            relatedFolio = folio,
            deduplicationKey = dedupKey,
            requiresHumanAction = true,
            actionLabel = "Verificar en Sitio",
            actionTarget = "INCIDENT"
        )
    }

    /**
     * Escalamiento pendiente que requiere acción en caseta
     */
    suspend fun notifyGuardPendingEscalation(
        context: Context,
        db: AppDatabase,
        folio: String,
        reason: String,
        contactProtocol: String
    ) {
        val title = "⚠️ Escalamiento Pendiente [$folio]"
        val body = "Alerta escalada por falta de confirmación: $reason. Protocolo: $contactProtocol"
        val dedupKey = "GUARD_ESCALATION_$folio"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.GUARDIA,
            targetRecipient = "Garita Principal",
            priority = NotificationPriority.ALTA,
            category = NotificationCategory.ESCALAMIENTO,
            title = title,
            body = body,
            relatedFolio = folio,
            deduplicationKey = dedupKey,
            requiresHumanAction = true,
            actionLabel = "Confirmar Protocolo",
            actionTarget = "ALERT"
        )
    }

    // =========================================================================
    // 3. EVENTOS PARA SUPERVISOR
    // =========================================================================

    /**
     * Alerta crítica para supervisor operativo
     */
    suspend fun notifySupervisorCriticalAlert(
        context: Context,
        db: AppDatabase,
        alertFolio: String,
        location: String,
        finding: String
    ) {
        val title = "🚨 ALERTA CRÍTICA OPERATIVA [$alertFolio]"
        val body = "Supervisor: Se detectó anomalía crítica en $location: $finding. Requiere dictamen pericial en campo."
        val dedupKey = "SUPERVISOR_CRIT_ALERT_$alertFolio"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.SUPERVISOR,
            targetRecipient = "Supervisor Operativo",
            priority = NotificationPriority.CRITICA,
            category = NotificationCategory.ALERTA_CRITICA,
            title = title,
            body = body,
            relatedFolio = alertFolio,
            deduplicationKey = dedupKey,
            requiresHumanAction = true,
            actionLabel = "Inspeccionar Campo",
            actionTarget = "SUPERVISION"
        )
    }

    /**
     * Hallazgo crítico durante supervisión
     */
    suspend fun notifySupervisorCriticalFinding(
        context: Context,
        db: AppDatabase,
        supervisionFolio: String,
        checkpointName: String,
        findingDetail: String
    ) {
        val title = "🛡️ Hallazgo Crítico en Ronda [$supervisionFolio]"
        val body = "Punto de control $checkpointName: $findingDetail. Se requiere emisión de dictamen correctivo."
        val dedupKey = "SUPERVISOR_FINDING_${supervisionFolio}_$checkpointName"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.SUPERVISOR,
            targetRecipient = "Supervisor Operativo",
            priority = NotificationPriority.CRITICA,
            category = NotificationCategory.HALLAZGO_CRITICO,
            title = title,
            body = body,
            relatedFolio = supervisionFolio,
            deduplicationKey = dedupKey,
            requiresHumanAction = true,
            actionLabel = "Generar Dictamen",
            actionTarget = "SUPERVISION"
        )
    }

    /**
     * Incidencia sin atención mayor a tiempo de tolerancia
     */
    suspend fun notifySupervisorUnattendedIncident(
        context: Context,
        db: AppDatabase,
        folio: String,
        location: String,
        elapsedMins: Int
    ) {
        val title = "⏱️ Incidencia sin Atención [$folio]"
        val body = "La incidencia en $location lleva $elapsedMins minutos sin primera respuesta. Requiere asignación de supervisor."
        val dedupKey = "SUPERVISOR_UNATTENDED_$folio"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.SUPERVISOR,
            targetRecipient = "Supervisor de Turno",
            priority = NotificationPriority.ALTA,
            category = NotificationCategory.INCIDENCIA,
            title = title,
            body = body,
            relatedFolio = folio,
            deduplicationKey = dedupKey,
            requiresHumanAction = true,
            actionLabel = "Tomar Control",
            actionTarget = "INCIDENT"
        )
    }

    /**
     * Ronda pendiente de cierre o checkpoint rezagado
     */
    suspend fun notifySupervisorPatrolPendingClose(
        context: Context,
        db: AppDatabase,
        patrolFolio: String,
        pendingCheckpointsCount: Int,
        elapsedMins: Int
    ) {
        val title = "📍 Ronda Pendiente de Cierre [$patrolFolio]"
        val body = "Ronda en curso iniciada hace $elapsedMins min tiene $pendingCheckpointsCount checkpoints pendientes de certificar."
        val dedupKey = "SUPERVISOR_PATROL_PENDING_$patrolFolio"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.SUPERVISOR,
            targetRecipient = "Supervisor Operativo",
            priority = NotificationPriority.ALTA,
            category = NotificationCategory.RONDA_PENDIENTE,
            title = title,
            body = body,
            relatedFolio = patrolFolio,
            deduplicationKey = dedupKey,
            requiresHumanAction = true,
            actionLabel = "Completar Ronda",
            actionTarget = "SUPERVISION"
        )
    }

    // =========================================================================
    // 4. EVENTOS PARA ADMINISTRACIÓN
    // =========================================================================

    /**
     * Incidencia escalada a nivel administrativo
     */
    suspend fun notifyAdminEscalatedIncident(
        context: Context,
        db: AppDatabase,
        folio: String,
        location: String,
        escalationReason: String
    ) {
        val title = "📈 Incidencia Escalada a Administración [$folio]"
        val body = "Ubicación: $location. Motivo de escalamiento: $escalationReason. Requiere autorización y resolución formal."
        val dedupKey = "ADMIN_ESCALATION_$folio"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.ADMINISTRACION,
            targetRecipient = "Administración General",
            priority = NotificationPriority.ALTA,
            category = NotificationCategory.ESCALAMIENTO,
            title = title,
            body = body,
            relatedFolio = folio,
            deduplicationKey = dedupKey,
            requiresHumanAction = true,
            actionLabel = "Gestionar Ticket",
            actionTarget = "ADMIN"
        )
    }

    /**
     * Conflicto de reserva de amenidad
     */
    suspend fun notifyAdminBookingConflict(
        context: Context,
        db: AppDatabase,
        amenityName: String,
        unit1: String,
        unit2: String,
        timeSlot: String,
        conflictFolio: String
    ) {
        val title = "⚖️ Conflicto de Reserva: $amenityName"
        val body = "Solapamiento de solicitudes para $amenityName ($timeSlot) entre $unit1 y $unit2. Requiere mediación administrativa."
        val dedupKey = "ADMIN_BOOKING_CONFLICT_$conflictFolio"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.ADMINISTRACION,
            targetRecipient = "Administración",
            priority = NotificationPriority.ALTA,
            category = NotificationCategory.CONFLICTO_RESERVA,
            title = title,
            body = body,
            relatedFolio = conflictFolio,
            deduplicationKey = dedupKey,
            requiresHumanAction = true,
            actionLabel = "Resolver Conflicto",
            actionTarget = "BOOKING"
        )
    }

    /**
     * Evento crítico para administración
     */
    suspend fun notifyAdminCriticalEvent(
        context: Context,
        db: AppDatabase,
        eventFolio: String,
        eventType: String,
        details: String
    ) {
        val title = "🚨 Evento Crítico de Infraestructura [$eventFolio]"
        val body = "Tipo: $eventType. $details. Intervención administrativa y protocolo de contingencia activado."
        val dedupKey = "ADMIN_CRITICAL_EVENT_$eventFolio"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.ADMINISTRACION,
            targetRecipient = "Administración General",
            priority = NotificationPriority.CRITICA,
            category = NotificationCategory.ALERTA_CRITICA,
            title = title,
            body = body,
            relatedFolio = eventFolio,
            deduplicationKey = dedupKey,
            requiresHumanAction = true,
            actionLabel = "Atender Evento",
            actionTarget = "ADMIN"
        )
    }

    /**
     * Tarea pendiente de resolución con vencimiento
     */
    suspend fun notifyAdminPendingTaskResolution(
        context: Context,
        db: AppDatabase,
        taskFolio: String,
        taskName: String,
        dueNotice: String
    ) {
        val title = "📋 Tarea Pendiente de Resolución [$taskFolio]"
        val body = "$taskName ($dueNotice). Requiere cierre administrativo para mantener indicadores de cumplimiento."
        val dedupKey = "ADMIN_TASK_PENDING_$taskFolio"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.ADMINISTRACION,
            targetRecipient = "Administración",
            priority = NotificationPriority.ALTA,
            category = NotificationCategory.TAREA_PENDIENTE,
            title = title,
            body = body,
            relatedFolio = taskFolio,
            deduplicationKey = dedupKey,
            requiresHumanAction = true,
            actionLabel = "Completar Tarea",
            actionTarget = "ADMIN"
        )
    }

    // =========================================================================
    // 5. EVENTOS PARA MESA DIRECTIVA (CERO RUIDO: Solo Alto/Crítico y Resumen)
    // =========================================================================

    /**
     * Evento relevante de nivel Alto o Crítico para Mesa Directiva
     */
    suspend fun notifyBoardHighPriorityEvent(
        context: Context,
        db: AppDatabase,
        folio: String,
        titleSummary: String,
        executiveDetail: String,
        isCritical: Boolean = false
    ) {
        val priority = if (isCritical) NotificationPriority.CRITICA else NotificationPriority.ALTA
        val title = if (isCritical) "🚨 Alerta Crítica para Mesa Directiva [$folio]" else "🏛️ Evento Relevante de Gobierno [$folio]"
        val dedupKey = "BOARD_EVENT_$folio"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.MESA_DIRECTIVA,
            targetRecipient = "Mesa Directiva",
            priority = priority,
            category = NotificationCategory.ALERTA_CRITICA,
            title = title,
            body = "$titleSummary. $executiveDetail",
            relatedFolio = folio,
            deduplicationKey = dedupKey,
            requiresHumanAction = false,
            actionLabel = "Ver Detalle",
            actionTarget = "BOARD"
        )
    }

    /**
     * Resumen ejecutivo periódico para Mesa Directiva (Métrica Tiempo Devuelto)
     */
    suspend fun notifyBoardExecutiveSummary(
        context: Context,
        db: AppDatabase,
        periodLabel: String,
        hoursReturnedFormatted: String,
        incidentsResolvedCount: Int,
        compliancePct: Int
    ) {
        val timeStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val title = "📊 Resumen Ejecutivo ALFHA: $periodLabel"
        val body = "Tiempo Devuelto a Familias: $hoursReturnedFormatted hrs | Tickets Resueltos: $incidentsResolvedCount | Cumplimiento SLA: $compliancePct%. Generado: $timeStr."
        val dedupKey = "BOARD_SUMMARY_${periodLabel.replace(" ", "_")}_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.MESA_DIRECTIVA,
            targetRecipient = "Mesa Directiva",
            priority = NotificationPriority.MEDIA,
            category = NotificationCategory.RESUMEN_EJECUTIVO,
            title = title,
            body = body,
            relatedFolio = "SUM-${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}",
            deduplicationKey = dedupKey,
            requiresHumanAction = false,
            actionLabel = "Ver Indicadores",
            actionTarget = "BOARD"
        )
    }

    // =========================================================================
    // GESTIÓN DE ESTADOS (Lectura, Resolución con RBAC y Auditoría Inmutable)
    // =========================================================================

    /**
     * Marca una notificación como leída
     */
    suspend fun markAsRead(db: AppDatabase, notificationId: Long) = withContext(Dispatchers.IO) {
        db.smartNotificationDao().markAsRead(notificationId, System.currentTimeMillis())
    }

    /**
     * Marca todas las notificaciones de un rol como leídas
     */
    suspend fun markAllAsReadForRole(db: AppDatabase, role: AlfhaRole) = withContext(Dispatchers.IO) {
        db.smartNotificationDao().markAllAsReadForRole(role.roleCode, System.currentTimeMillis())
    }

    /**
     * Resuelve una notificación inteligente con validación RBAC y registro en bitácora de auditoría.
     */
    suspend fun resolveNotificationWithSecurity(
        db: AppDatabase,
        notificationId: Long,
        operatorName: String,
        resolutionNotes: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val notif = db.smartNotificationDao().getById(notificationId) ?: return@withContext false
            val currentUser = AlfhaSecurityContext.currentUser.value

            // Validar permiso RESOLVER
            val hasPerm = currentUser.hasPermission(AlfhaPermission.RESOLVER)
            if (!hasPerm && currentUser.alfhaRole != AlfhaRole.MAESTRO_ALFHA) {
                Log.w(TAG, "Permiso denegado para resolver notificación: ${currentUser.name}")
                return@withContext false
            }

            // Resolver en Room
            db.smartNotificationDao().resolveNotification(
                id = notificationId,
                resolvedBy = "${currentUser.name} (${currentUser.alfhaRole.shortName})",
                resolvedAt = System.currentTimeMillis()
            )

            // Registrar en Auditoría Inmutable
            db.auditLogDao().insertAuditLog(
                AuditLogEntity(
                    folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                    operatorName = "${currentUser.name} (${currentUser.alfhaRole.shortName})",
                    actionType = "SMART_NOTIF_RESOLVED",
                    location = notif.targetRecipient,
                    targetEntity = notif.notificationId,
                    changeDetails = "Notificación [${notif.title}] resuelta formalmente: $resolutionNotes",
                    resultStatus = "RESUELTO"
                )
            )

            Log.i(TAG, "Notificación ${notif.notificationId} resuelta por ${currentUser.name}")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error resolviendo notificación: ${e.message}", e)
            return@withContext false
        }
    }

    /**
     * Cierra automáticamente notificaciones asociadas cuando un folio (ej. Incidencia o Alerta) se resuelve
     */
    suspend fun autoResolveNotificationsForFolio(
        db: AppDatabase,
        folio: String,
        resolvedBy: String
    ) = withContext(Dispatchers.IO) {
        try {
            db.smartNotificationDao().resolveNotificationsByFolio(
                relatedFolio = folio,
                resolvedBy = resolvedBy,
                resolvedAt = System.currentTimeMillis()
            )
            Log.i(TAG, "Notificaciones vinculadas al folio $folio marcadas automáticamente como resueltas.")
        } catch (e: Exception) {
            Log.e(TAG, "Error en auto-resolución de notificaciones para folio $folio: ${e.message}")
        }
    }

    /**
     * Notificación Inteligente para Comunicados Oficiales y Documentos (FASE 14)
     */
    suspend fun notifyAnnouncementBroadcast(
        context: Context,
        db: AppDatabase,
        folio: String,
        title: String,
        body: String,
        priority: NotificationPriority,
        targetRole: AlfhaRole,
        targetRecipient: String,
        targetUnitId: String? = null,
        requiresAcknowledgement: Boolean = false
    ) {
        val dedupKey = "ANNOUNCEMENT_${folio}_${targetRole.roleCode}_${targetUnitId ?: "ALL"}"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = targetRole,
            targetRecipient = targetRecipient,
            targetUnitId = targetUnitId,
            priority = priority,
            category = NotificationCategory.COMUNICADO_OFICIAL,
            title = title,
            body = body,
            relatedFolio = folio,
            deduplicationKey = dedupKey,
            requiresHumanAction = requiresAcknowledgement,
            actionLabel = if (requiresAcknowledgement) "Firmar Acuse" else "Ver Comunicado",
            actionTarget = "ANNOUNCEMENTS"
        )
    }

    /**
     * Notificación Inteligente para Acceso Vehicular Autorizado (FASE 15)
     */
    suspend fun notifyVehicleAccessGranted(
        context: Context,
        db: AppDatabase,
        plate: String,
        unitId: String,
        driverName: String,
        gateLane: String,
        logFolio: String
    ) {
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val title = "🚗 Acceso Vehicular: $plate"
        val body = "Ingreso vehicular registrado a las $timeStr hrs en $gateLane ($driverName) con destino $unitId."
        val dedupKey = "VEH_ENTRY_${plate}_${logFolio}"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.RESIDENTE,
            targetRecipient = "$unitId - $driverName",
            targetUnitId = unitId,
            priority = NotificationPriority.MEDIA,
            category = NotificationCategory.ACCESO_VEHICULAR,
            title = title,
            body = body,
            relatedFolio = logFolio,
            deduplicationKey = dedupKey,
            requiresHumanAction = false,
            actionLabel = "Ver Acceso",
            actionTarget = "VEHICLES"
        )
    }

    /**
     * Notificación Inteligente para Salida Vehicular (FASE 15)
     */
    suspend fun notifyVehicleExitRecorded(
        context: Context,
        db: AppDatabase,
        plate: String,
        unitId: String,
        stayDuration: String,
        logFolio: String
    ) {
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val title = "🚗 Salida Vehicular: $plate"
        val body = "Salida vehicular registrada a las $timeStr hrs. Permanencia en condominio: $stayDuration."
        val dedupKey = "VEH_EXIT_${plate}_${logFolio}"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.RESIDENTE,
            targetRecipient = unitId,
            targetUnitId = unitId,
            priority = NotificationPriority.PREVENTIVA,
            category = NotificationCategory.ACCESO_VEHICULAR,
            title = title,
            body = body,
            relatedFolio = logFolio,
            deduplicationKey = dedupKey,
            requiresHumanAction = false,
            actionLabel = "Ver Salida",
            actionTarget = "VEHICLES"
        )
    }

    /**
     * Alerta Crítica Vehicular (No autorizado / Suspendido)
     */
    suspend fun notifyVehicleAlert(
        context: Context,
        db: AppDatabase,
        alertFolio: String,
        plate: String,
        unitId: String,
        reason: String,
        gateLane: String,
        allowEmergencyEntry: Boolean
    ) {
        val title = if (allowEmergencyEntry) "⚠️ ACCESO VEHICULAR EXCEPCIONAL: $plate" else "🚨 ALERTA VEHICULAR: $plate"
        val body = "$reason en $gateLane ($unitId). ${if (allowEmergencyEntry) "Permitido bajo criterio de excepción en caseta." else "Acceso retenido y restringido."}"
        val dedupKey = "VEH_ALERT_${alertFolio}"

        dispatchSmartNotification(
            context = context,
            db = db,
            targetRole = AlfhaRole.GUARDIA,
            targetRecipient = "Garita de Vigilancia",
            targetUnitId = if (unitId.isNotBlank()) unitId else null,
            priority = NotificationPriority.CRITICA,
            category = NotificationCategory.ALERTA_CRITICA,
            title = title,
            body = body,
            relatedFolio = alertFolio,
            deduplicationKey = dedupKey,
            requiresHumanAction = true,
            actionLabel = "Gestionar Alerta",
            actionTarget = "VEHICLES"
        )
    }

    /**
     * Sembrado inicial de eventos de prueba representativos si la tabla está vacía
     */
    suspend fun seedInitialNotificationsIfEmpty(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        try {
            val all = db.smartNotificationDao().getById(1)
            if (all == null) {
                // Generar eventos muestra reales para los 5 roles
                notifyVisitorEntry(
                    context = context,
                    db = db,
                    guestName = "Carlos Mendoza",
                    unitId = "Casa 102",
                    hostResidentName = "Familia González",
                    passTypeLabel = "Familiar",
                    vehiclePlate = "CDMX-789-A",
                    passFolio = "MED-00921"
                )
                notifyPackageReceived(
                    context = context,
                    db = db,
                    unitId = "Casa 102",
                    hostResidentName = "Familia González",
                    courierName = "Amazon Prime",
                    packageGuide = "PKG-AMZ-9921",
                    guardName = "Oficial Ramírez"
                )
                notifyGuardCriticalAlert(
                    context = context,
                    db = db,
                    alertFolio = "ALT-8820",
                    location = "Perímetro Norte - Reja 4",
                    description = "Sensor infrarrojo reporta interrupción repetitiva",
                    actionRequired = "Inspección visual inmediata en punto de control 3"
                )
                notifySupervisorCriticalFinding(
                    context = context,
                    db = db,
                    supervisionFolio = "SUP-2026-088",
                    checkpointName = "Subestación Eléctrica 2",
                    findingDetail = "Cerradura secundaria forzada sin sello de seguridad"
                )
                notifyAdminEscalatedIncident(
                    context = context,
                    db = db,
                    folio = "INC-2026-004",
                    location = "Alberca Principal",
                    escalationReason = "Bomba de filtrado apagada por 48 horas continuas"
                )
                notifyBoardExecutiveSummary(
                    context = context,
                    db = db,
                    periodLabel = "Semana 34",
                    hoursReturnedFormatted = "18.5",
                    incidentsResolvedCount = 14,
                    compliancePct = 98
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sembrando notificaciones iniciales: ${e.message}")
        }
    }
}
