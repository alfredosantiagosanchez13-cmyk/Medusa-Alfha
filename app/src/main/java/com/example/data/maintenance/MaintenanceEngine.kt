package com.example.data.maintenance

import android.content.Context
import android.util.Log
import com.example.data.audit.AuditLogEntity
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.notifications.SmartNotificationHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FASE 13: MOTOR DE MANTENIMIENTO Y ÓRDENES DE TRABAJO ALFHA
 *
 * Principio Sagrado: ESTO DEVUELVE TIEMPO.
 * Centraliza la lógica de negocio, asignación automática, SLA, trazabilidad y Room como Fuente Única de Verdad.
 */
object MaintenanceEngine {
    private const val TAG = "MaintenanceEngine"

    sealed class MaintenanceOperationResult {
        data class Success(val order: MaintenanceOrderEntity, val message: String) : MaintenanceOperationResult()
        data class Error(val message: String) : MaintenanceOperationResult()
    }

    /**
     * Generador de folio único e inmutable para Órdenes de Trabajo (e.g. MNT-20260824-7F89)
     */
    fun generateMaintenanceFolio(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        val randomSuffix = (1000..9999).random()
        return "MNT-$dateFormat-$randomSuffix"
    }

    /**
     * Sugiere un responsable o cuadrilla técnica por defecto según la categoría de mantenimiento
     */
    fun getSuggestedTechnician(category: MaintenanceCategory): Pair<String, String> {
        return when (category) {
            MaintenanceCategory.PLOMERIA -> "Téc. Fernando Ruiz (Hidráulica)" to "+52 55 1234 5601"
            MaintenanceCategory.ELECTRICIDAD -> "Ing. Roberto Martínez (Eléctrico)" to "+52 55 1234 5602"
            MaintenanceCategory.ALBANILERIA -> "Cuadrilla Albañilería & Obra Civil" to "+52 55 1234 5603"
            MaintenanceCategory.PINTURA -> "Servicios Pintura & Acabados Pro" to "+52 55 1234 5604"
            MaintenanceCategory.JARDINERIA -> "Equipo de Jardinería & Paisajismo" to "+52 55 1234 5605"
            MaintenanceCategory.CERRAJERIA -> "Cerrajería & Control Acceso 24/7" to "+52 55 1234 5606"
            MaintenanceCategory.ELEVADORES -> "Schindler / Otis Mantenimiento Certificado" to "+52 55 1234 5607"
            MaintenanceCategory.PISCINA_AMENIDADES -> "Especialista Albercas & Químicos" to "+52 55 1234 5608"
            MaintenanceCategory.CLIMATIZACION -> "Técnico HVAC Clima & Ventilación" to "+52 55 1234 5609"
            MaintenanceCategory.PORTONES_AUTOMATIZACION -> "Automatismos & Motores Garita" to "+52 55 1234 5610"
            MaintenanceCategory.GENERAL -> "Cuadrilla Mantenimiento General ALFHA" to "+52 55 1234 5600"
        }
    }

    /**
     * 1. REGISTRAR SOLICITUD DE MANTENIMIENTO
     */
    suspend fun createMaintenanceOrder(
        context: Context,
        db: AppDatabase,
        title: String,
        description: String,
        category: MaintenanceCategory,
        priority: MaintenancePriority,
        locationType: MaintenanceLocationType,
        location: String,
        unitId: String? = null,
        requesterName: String,
        requesterRole: String = "RESIDENTE",
        requesterPhone: String = "",
        initialPhotoUri: String? = null,
        customTechnician: String? = null,
        customTechnicianPhone: String? = null,
        autoAssign: Boolean = true
    ): MaintenanceOperationResult = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = title.trim()
            val cleanDesc = description.trim()
            val cleanLocation = location.trim()

            if (cleanTitle.isBlank()) {
                return@withContext MaintenanceOperationResult.Error("El título de la orden es obligatorio")
            }
            if (cleanDesc.isBlank()) {
                return@withContext MaintenanceOperationResult.Error("La descripción del mantenimiento es obligatoria")
            }
            if (cleanLocation.isBlank()) {
                return@withContext MaintenanceOperationResult.Error("La ubicación es requerida")
            }

            val folio = generateMaintenanceFolio()
            val slaHours = priority.defaultSlaHours
            val now = System.currentTimeMillis()
            val deadline = now + (slaHours * 3600 * 1000L)

            val suggested = getSuggestedTechnician(category)
            val assignedTech = if (!customTechnician.isNullOrBlank()) {
                customTechnician.trim()
            } else if (autoAssign) {
                suggested.first
            } else {
                "Por Asignar"
            }

            val assignedPhone = if (!customTechnicianPhone.isNullOrBlank()) {
                customTechnicianPhone.trim()
            } else if (autoAssign) {
                suggested.second
            } else {
                ""
            }

            val initialStatus = if (assignedTech != "Por Asignar") "ASIGNADO" else "REGISTRADO"

            val order = MaintenanceOrderEntity(
                folio = folio,
                title = cleanTitle,
                description = cleanDesc,
                category = category,
                priority = priority,
                locationType = locationType,
                location = cleanLocation,
                unitId = unitId?.trim()?.takeIf { it.isNotBlank() },
                requesterName = requesterName.trim().ifBlank { "Residente" },
                requesterRole = requesterRole,
                requesterPhone = requesterPhone.trim(),
                initialPhotoUri = initialPhotoUri,
                status = initialStatus,
                assignedTechnician = assignedTech,
                assignedTechnicianPhone = assignedPhone,
                assignedRole = "MANTENIMIENTO",
                assignedAtMillis = if (initialStatus == "ASIGNADO") now else null,
                assignedBy = if (initialStatus == "ASIGNADO") "Sistema ALFHA (Auto-Asignación)" else null,
                slaTargetHours = slaHours,
                timestampMillis = now,
                deadlineMillis = deadline,
                timeSavedMinutes = 30 // Ahorro por eliminación de llamadas, papel y recaptura
            )

            // 1. Guardar en Room SQLite
            db.maintenanceDao().insertOrder(order)

            // 2. Registro inmutable en Cadena de Auditoría SHA-256
            db.auditLogDao().insertAuditLog(
                AuditLogEntity(
                    operatorName = requesterName,
                    actionType = "REGISTRO_ORDEN_MANTENIMIENTO",
                    location = cleanLocation,
                    targetEntity = folio,
                    changeDetails = "OT $folio creada ($cleanTitle) en $cleanLocation. Cat: ${category.label}, Pri: ${priority.label}, Asignado: $assignedTech, SLA: ${slaHours}h",
                    resultStatus = "EXITOSO"
                )
            )

            // 3. Notificación Inteligente Multirrol
            SmartNotificationHub.notifyMaintenanceRegistered(
                context = context,
                db = db,
                folio = folio,
                title = cleanTitle,
                location = cleanLocation,
                priorityStr = priority.name,
                requesterName = requesterName,
                targetUnitId = unitId
            )

            if (initialStatus == "ASIGNADO") {
                SmartNotificationHub.notifyMaintenanceAssigned(
                    context = context,
                    db = db,
                    folio = folio,
                    title = cleanTitle,
                    technicianName = assignedTech,
                    targetUnitId = unitId,
                    recipientName = requesterName
                )
            }

            MaintenanceOperationResult.Success(
                order = order,
                message = "Orden de trabajo $folio registrada exitosamente con SLA de ${slaHours}h"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error creando orden de mantenimiento: ${e.message}", e)
            MaintenanceOperationResult.Error("Error al registrar la orden: ${e.localizedMessage ?: "Error desconocido"}")
        }
    }

    /**
     * 2. ASIGNAR O REASIGNAR TÉCNICO RESPONSABLE
     */
    suspend fun assignTechnician(
        context: Context,
        db: AppDatabase,
        folio: String,
        technicianName: String,
        technicianPhone: String,
        assignedBy: String
    ): MaintenanceOperationResult = withContext(Dispatchers.IO) {
        try {
            val existing = db.maintenanceDao().getOrderByFolio(folio)
                ?: return@withContext MaintenanceOperationResult.Error("Orden $folio no encontrada")

            val now = System.currentTimeMillis()
            val updated = existing.copy(
                assignedTechnician = technicianName.trim(),
                assignedTechnicianPhone = technicianPhone.trim(),
                assignedAtMillis = now,
                assignedBy = assignedBy,
                status = if (existing.status == "REGISTRADO") "ASIGNADO" else existing.status
            )

            db.maintenanceDao().updateOrder(updated)

            db.auditLogDao().insertAuditLog(
                AuditLogEntity(
                    operatorName = assignedBy,
                    actionType = "ASIGNACION_TECNICO_MANTENIMIENTO",
                    location = updated.location,
                    targetEntity = folio,
                    changeDetails = "OT $folio asignada a $technicianName (Tel: $technicianPhone) por $assignedBy",
                    resultStatus = "EXITOSO"
                )
            )

            SmartNotificationHub.notifyMaintenanceAssigned(
                context = context,
                db = db,
                folio = folio,
                title = updated.title,
                technicianName = technicianName,
                targetUnitId = updated.unitId,
                recipientName = updated.requesterName
            )

            MaintenanceOperationResult.Success(
                order = updated,
                message = "Técnico $technicianName asignado a la orden $folio"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error asignando técnico: ${e.message}", e)
            MaintenanceOperationResult.Error("Error al asignar técnico: ${e.localizedMessage}")
        }
    }

    /**
     * 3. INICIAR ATENCIÓN (EN_ATENCION)
     */
    suspend fun startAttention(
        context: Context,
        db: AppDatabase,
        folio: String,
        attendedBy: String
    ): MaintenanceOperationResult = withContext(Dispatchers.IO) {
        try {
            val existing = db.maintenanceDao().getOrderByFolio(folio)
                ?: return@withContext MaintenanceOperationResult.Error("Orden $folio no encontrada")

            val now = System.currentTimeMillis()
            val updated = existing.copy(
                status = "EN_ATENCION",
                startedAttentionAtMillis = now,
                attendedBy = attendedBy
            )

            db.maintenanceDao().updateOrder(updated)

            db.auditLogDao().insertAuditLog(
                AuditLogEntity(
                    operatorName = attendedBy,
                    actionType = "INICIO_ATENCION_MANTENIMIENTO",
                    location = updated.location,
                    targetEntity = folio,
                    changeDetails = "Inicio de labores para OT $folio por $attendedBy",
                    resultStatus = "EXITOSO"
                )
            )

            MaintenanceOperationResult.Success(
                order = updated,
                message = "Orden $folio ahora está EN ATENCIÓN activa"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando atención: ${e.message}", e)
            MaintenanceOperationResult.Error("Error al actualizar estado: ${e.localizedMessage}")
        }
    }

    /**
     * 4. MARCAR COMO RESUELTO CON MATERIALES, COSTO Y EVIDENCIA
     */
    suspend fun resolveOrder(
        context: Context,
        db: AppDatabase,
        folio: String,
        solutionDescription: String,
        materialsUsed: String?,
        materialsCost: Double,
        solutionPhotoUri: String?,
        resolvedBy: String
    ): MaintenanceOperationResult = withContext(Dispatchers.IO) {
        try {
            val existing = db.maintenanceDao().getOrderByFolio(folio)
                ?: return@withContext MaintenanceOperationResult.Error("Orden $folio no encontrada")

            val cleanSolution = solutionDescription.trim()
            if (cleanSolution.isBlank()) {
                return@withContext MaintenanceOperationResult.Error("Debe incluir la descripción de la solución realizada")
            }

            val now = System.currentTimeMillis()
            val updated = existing.copy(
                status = "RESUELTO",
                solutionDescription = cleanSolution,
                materialsUsed = materialsUsed?.trim()?.takeIf { it.isNotBlank() },
                materialsCost = if (materialsCost >= 0) materialsCost else 0.0,
                solutionPhotoUri = solutionPhotoUri,
                resolvedAtMillis = now,
                resolvedBy = resolvedBy
            )

            db.maintenanceDao().updateOrder(updated)

            db.auditLogDao().insertAuditLog(
                AuditLogEntity(
                    operatorName = resolvedBy,
                    actionType = "RESOLUCION_ORDEN_MANTENIMIENTO",
                    location = updated.location,
                    targetEntity = folio,
                    changeDetails = "OT $folio marcada como RESUELTA por $resolvedBy. Costo Mat: $$materialsCost. Dictamen: $cleanSolution",
                    resultStatus = "EXITOSO"
                )
            )

            SmartNotificationHub.notifyMaintenanceResolved(
                context = context,
                db = db,
                folio = folio,
                title = updated.title,
                solutionNotes = cleanSolution,
                targetUnitId = updated.unitId,
                recipientName = updated.requesterName
            )

            MaintenanceOperationResult.Success(
                order = updated,
                message = "Orden $folio resuelta con éxito. Costo de materiales: $$materialsCost"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error resolviendo orden: ${e.message}", e)
            MaintenanceOperationResult.Error("Error al resolver orden: ${e.localizedMessage}")
        }
    }

    /**
     * 5. CIERRE DEFINITIVO CON RESPONSABLE, FECHA/HORA Y NOTAS
     */
    suspend fun closeOrder(
        context: Context,
        db: AppDatabase,
        folio: String,
        closedBy: String,
        closureNotes: String?,
        satisfactionRating: Int? = null
    ): MaintenanceOperationResult = withContext(Dispatchers.IO) {
        try {
            val existing = db.maintenanceDao().getOrderByFolio(folio)
                ?: return@withContext MaintenanceOperationResult.Error("Orden $folio no encontrada")

            val now = System.currentTimeMillis()
            val updated = existing.copy(
                status = "CERRADO",
                closedAtMillis = now,
                closedBy = closedBy,
                closureNotes = closureNotes?.trim()?.takeIf { it.isNotBlank() },
                residentSatisfactionRating = satisfactionRating?.coerceIn(1, 5)
            )

            db.maintenanceDao().updateOrder(updated)

            db.auditLogDao().insertAuditLog(
                AuditLogEntity(
                    operatorName = closedBy,
                    actionType = "CIERRE_DEFINITIVO_MANTENIMIENTO",
                    location = updated.location,
                    targetEntity = folio,
                    changeDetails = "OT $folio CERRADA formalmente por $closedBy. Calificación: ${satisfactionRating ?: "N/A"}/5. Notas: ${closureNotes ?: "Conforme"}",
                    resultStatus = "EXITOSO"
                )
            )

            MaintenanceOperationResult.Success(
                order = updated,
                message = "Orden $folio cerrada exitosamente con firma y fecha inmutable"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error cerrando orden: ${e.message}", e)
            MaintenanceOperationResult.Error("Error al cerrar orden: ${e.localizedMessage}")
        }
    }

    /**
     * 6. ESCÁNER PERIÓDICO DE SLA PARA ÓRDENES ACTIVAS
     */
    suspend fun checkSlaAndTriggerAlerts(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        try {
            val orders = db.maintenanceDao().getAllOrdersSnapshot()
            val now = System.currentTimeMillis()

            orders.filter { it.status != "CERRADO" && it.status != "RESUELTO" }.forEach { order ->
                val remainingMins = order.getRemainingSlaMinutes(now)
                if (remainingMins < 0) {
                    // SLA Vencido
                    SmartNotificationHub.notifyMaintenanceSlaAlert(
                        context = context,
                        db = db,
                        folio = order.folio,
                        title = order.title,
                        location = order.location,
                        technician = order.assignedTechnician,
                        isOverdue = true,
                        diffMinutes = Math.abs(remainingMins)
                    )
                } else if (remainingMins <= 120) {
                    // SLA en Riesgo (<2h)
                    SmartNotificationHub.notifyMaintenanceSlaAlert(
                        context = context,
                        db = db,
                        folio = order.folio,
                        title = order.title,
                        location = order.location,
                        technician = order.assignedTechnician,
                        isOverdue = false,
                        diffMinutes = remainingMins
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking maintenance SLA: ${e.message}", e)
        }
    }
}
