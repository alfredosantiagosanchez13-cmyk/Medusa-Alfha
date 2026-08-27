package com.example.data.packages

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
import java.util.UUID

/**
 * MOTOR OPERATIVO DE PAQUETERÍA ALFHA (FASE 10)
 *
 * Automatiza:
 * 1. Recepción en Caseta con asociación automática de residente/unidad.
 * 2. Asignación de Folio Único (PKG-YYYYMMDD-XXXX).
 * 3. Notificación Instantánea al Residente vía SmartNotificationHub.
 * 4. Control de Ciclo de Vida: RECIBIDO -> NOTIFICADO -> ENTREGADO.
 * 5. Confirmación formal de entrega con registro de receptor y guardia.
 * 6. Auditoría inmutable y cuantificación de Tiempo Devuelto.
 * 7. Alertas automáticas para paquetes no recolectados (> 24 hrs).
 */
object PackageEngine {

    private const val TAG = "PackageEngine"

    /**
     * Registra la recepción de un paquete en garita y despacha notificación automática al residente.
     */
    suspend fun receivePackage(
        context: Context,
        db: AppDatabase,
        unitId: String,
        residentName: String,
        courierCompany: String,
        trackingNumber: String = "",
        packageSize: String = "MEDIANO",
        locationInGuardhouse: String = "Estante Principal",
        guardName: String = "Guardia en Turno",
        notes: String = ""
    ): PackageEntity = withContext(Dispatchers.IO) {
        val folio = AlphaCoreEngine.generateUniqueFolio("PKG")
        val now = System.currentTimeMillis()
        val tracking = if (trackingNumber.isBlank()) folio else trackingNumber.trim()

        // 1. Crear entidad en Room
        val pkg = PackageEntity(
            id = UUID.randomUUID().toString(),
            folio = folio,
            unitId = unitId.trim(),
            residentName = residentName.trim(),
            courierCompany = courierCompany.trim(),
            trackingNumber = tracking,
            packageSize = packageSize,
            locationInGuardhouse = locationInGuardhouse,
            receivedTimestamp = now,
            receivedByGuard = guardName,
            status = "NOTIFICADO", // Pasa directamente a notificado al emitir la alerta
            notifiedTimestamp = now,
            notes = notes,
            timeSavedMinutes = 12 // 12 min: Cero libreta física + notificación instantánea + búsqueda inmediata
        )

        db.packageDao().insertPackage(pkg)

        // 2. Notificación Automática al Residente
        try {
            SmartNotificationHub.notifyPackageReceived(
                context = context,
                db = db,
                unitId = unitId,
                hostResidentName = residentName,
                courierName = courierCompany,
                packageGuide = tracking,
                guardName = guardName
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error notificando recepción de paquete: ${e.message}", e)
        }

        // 3. Registro de Auditoría Inmutable
        try {
            db.auditLogDao().insertAuditLog(
                AuditLogEntity(
                    folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                    operatorName = guardName,
                    actionType = "PACKAGE_RECEIVED",
                    location = "CASETA_PRINCIPAL",
                    targetEntity = folio,
                    changeDetails = "Paquete $folio de $courierCompany (Guía: $tracking) recibido para $residentName ($unitId). Resguardo en: $locationInGuardhouse",
                    resultStatus = "EXITOSO",
                    timestampMillis = now
                )
            )

            // 4. Registro de Tiempo Devuelto
            db.auditLogDao().insertAuditLog(
                AuditLogEntity(
                    folio = AlphaCoreEngine.generateUniqueFolio("TME"),
                    operatorName = "MEDUSA_PACKAGE_ENGINE",
                    actionType = "TIEMPO_DEVUELTO",
                    location = "CASETA_PRINCIPAL",
                    targetEntity = folio,
                    changeDetails = "Ahorro de 12 minutos generado por recepción digital y notificación automática al residente para $folio",
                    resultStatus = "EXITOSO",
                    timestampMillis = now
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando auditoría de paquete: ${e.message}", e)
        }

        pkg
    }

    /**
     * Confirma la entrega formal del paquete al residente o persona autorizada.
     */
    suspend fun deliverPackage(
        context: Context,
        db: AppDatabase,
        folio: String,
        deliveredByGuard: String,
        receivedByRecipientName: String,
        notes: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        val pkg = db.packageDao().getPackageByFolio(folio) ?: return@withContext false
        val now = System.currentTimeMillis()

        val updated = pkg.copy(
            status = "ENTREGADO",
            deliveredTimestamp = now,
            deliveredByGuard = deliveredByGuard,
            receivedByRecipientName = receivedByRecipientName.ifBlank { pkg.residentName },
            notes = if (notes.isNotBlank()) "${pkg.notes} | Entrega: $notes".trimStart('|', ' ') else pkg.notes
        )

        db.packageDao().updatePackage(updated)

        // Registrar Auditoría Inmutable de Entrega
        try {
            db.auditLogDao().insertAuditLog(
                AuditLogEntity(
                    folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                    operatorName = deliveredByGuard,
                    actionType = "PACKAGE_DELIVERED",
                    location = "CASETA_PRINCIPAL",
                    targetEntity = folio,
                    changeDetails = "Paquete $folio de ${pkg.courierCompany} ENTREGADO a $receivedByRecipientName (${pkg.unitId}) por $deliveredByGuard",
                    resultStatus = "EXITOSO",
                    timestampMillis = now
                )
            )

            // Registro de Tiempo Devuelto por entrega digital rápida (5 minutos)
            db.auditLogDao().insertAuditLog(
                AuditLogEntity(
                    folio = AlphaCoreEngine.generateUniqueFolio("TME"),
                    operatorName = "MEDUSA_PACKAGE_ENGINE",
                    actionType = "TIEMPO_DEVUELTO",
                    location = "CASETA_PRINCIPAL",
                    targetEntity = folio,
                    changeDetails = "Ahorro de 5 minutos en confirmación ágil y cierre digital de paquetería para $folio",
                    resultStatus = "EXITOSO",
                    timestampMillis = now
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error registrando auditoría de entrega: ${e.message}", e)
        }

        true
    }

    /**
     * Reenvía un recordatorio de recolección al residente.
     */
    suspend fun sendReminderToResident(
        context: Context,
        db: AppDatabase,
        pkg: PackageEntity,
        guardName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            SmartNotificationHub.notifyPackageReceived(
                context = context,
                db = db,
                unitId = pkg.unitId,
                hostResidentName = pkg.residentName,
                courierName = "${pkg.courierCompany} (RECORDATORIO)",
                packageGuide = pkg.trackingNumber,
                guardName = guardName
            )

            val now = System.currentTimeMillis()
            db.auditLogDao().insertAuditLog(
                AuditLogEntity(
                    folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                    operatorName = guardName,
                    actionType = "PACKAGE_REMINDER",
                    location = "CASETA_PRINCIPAL",
                    targetEntity = pkg.folio,
                    changeDetails = "Recordatorio de paquete ${pkg.folio} reenviado a ${pkg.residentName} (${pkg.unitId})",
                    resultStatus = "EXITOSO",
                    timestampMillis = now
                )
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error reenviando recordatorio: ${e.message}", e)
            false
        }
    }

    /**
     * Chequeo de paquetes pendientes con más de 24 horas para generar alertas visuales.
     */
    suspend fun getPendingPackagesWithAging(db: AppDatabase): List<Pair<PackageEntity, Long>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val pending = db.packageDao().getPendingPackagesList()
        pending.map { pkg ->
            val hours = (now - pkg.receivedTimestamp) / (1000 * 60 * 60)
            pkg to hours
        }
    }
}
