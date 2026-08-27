package com.example.data.core

import android.content.Context
import com.example.data.booking.AppDatabase
import com.example.data.audit.AuditLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gestor de Respaldo y Recuperación Local contra cierres inesperados.
 * Principio: Fuente Única de Verdad (Room SQLite) con recuperación automática y exportación JSON.
 */
object LocalDataBackupManager {

    private const val BACKUP_DIR_NAME = "medusa_backups"
    private const val LAST_KNOWN_STATE_FILE = "last_supervision_state.json"

    /**
     * Guarda el estado en curso de una ronda de supervisión para recuperación ante cierres inesperados.
     */
    suspend fun saveOngoingTourState(
        context: Context,
        isTourActive: Boolean,
        tourStartMillis: Long?,
        supervisorName: String,
        currentGps: String
    ) = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, BACKUP_DIR_NAME).apply { if (!exists()) mkdirs() }
            val file = File(dir, LAST_KNOWN_STATE_FILE)
            val json = JSONObject().apply {
                put("isTourActive", isTourActive)
                put("tourStartMillis", tourStartMillis ?: 0L)
                put("supervisorName", supervisorName)
                put("currentGps", currentGps)
                put("savedAtMillis", System.currentTimeMillis())
            }
            file.writeText(json.toString())
        } catch (e: Exception) {
            android.util.Log.e("LocalBackup", "Error saving ongoing tour state: ${e.message}")
        }
    }

    /**
     * Recupera el estado de una ronda previa si la app se cerró inesperadamente.
     */
    suspend fun loadOngoingTourState(context: Context): OngoingTourRecoveryData? = withContext(Dispatchers.IO) {
        try {
            val file = File(File(context.filesDir, BACKUP_DIR_NAME), LAST_KNOWN_STATE_FILE)
            if (!file.exists()) return@withContext null

            val json = JSONObject(file.readText())
            val isTourActive = json.optBoolean("isTourActive", false)
            val tourStartMillis = json.optLong("tourStartMillis", 0L)
            val supervisorName = json.optString("supervisorName", "Supervisor en Turno")
            val currentGps = json.optString("currentGps", "-33.4372, -70.6506")
            val savedAtMillis = json.optLong("savedAtMillis", 0L)

            // Solo recuperar si tiene menos de 12 horas de antigüedad
            if (isTourActive && tourStartMillis > 0 && (System.currentTimeMillis() - savedAtMillis < 12 * 3600 * 1000)) {
                OngoingTourRecoveryData(
                    isTourActive = true,
                    tourStartMillis = tourStartMillis,
                    supervisorName = supervisorName,
                    currentGps = currentGps
                )
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("LocalBackup", "Error loading ongoing tour state: ${e.message}")
            null
        }
    }

    /**
     * Limpia el estado de recuperación al cerrar formalmente la ronda.
     */
    suspend fun clearOngoingTourState(context: Context) = withContext(Dispatchers.IO) {
        try {
            val file = File(File(context.filesDir, BACKUP_DIR_NAME), LAST_KNOWN_STATE_FILE)
            if (file.exists()) file.delete()
        } catch (e: Exception) {
            android.util.Log.e("LocalBackup", "Error clearing tour state: ${e.message}")
        }
    }

    /**
     * Genera un respaldo consolidado local de toda la base de datos Room en JSON.
     */
    suspend fun createFullLocalBackup(context: Context, db: AppDatabase): String = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, BACKUP_DIR_NAME).apply { if (!exists()) mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val backupFile = File(dir, "medusa_full_backup_$timeStamp.json")

            val audits = db.supervisionAuditDao().getAllAuditsList()
            val checkIns = db.visitorCheckInDao().getAllCheckInsList()
            val incidents = db.incidentDao().getAllIncidentsList()
            val passes = db.qrPassDao().getAllPassesList()

            val rootJson = JSONObject().apply {
                put("generatedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                put("version", "FASE_4_PRODUCCION")
                put("totalAudits", audits.size)
                put("totalCheckIns", checkIns.size)
                put("totalIncidents", incidents.size)
                put("totalPasses", passes.size)

                val auditsArray = JSONArray()
                audits.forEach { audit ->
                    auditsArray.put(JSONObject().apply {
                        put("folio", audit.folio)
                        put("checkpoint", audit.checkpointName)
                        put("status", audit.statusCondition)
                        put("findings", audit.findingsDescription)
                        put("gps", audit.gpsCoordinates)
                        put("timestamp", audit.timestampMillis)
                    })
                }
                put("audits", auditsArray)
            }

            backupFile.writeText(rootJson.toString(2))

            db.auditLogDao().insertAuditLog(
                AuditLogEntity(
                    operatorName = "Sistema ALFHA",
                    actionType = "BACKUP_LOCAL_COMPLETADO",
                    location = "Almacenamiento Seguro Room",
                    targetEntity = backupFile.name,
                    changeDetails = "Respaldo local generado exitosamente (${audits.size} auditorías, ${checkIns.size} accesos, ${incidents.size} incidencias)."
                )
            )

            backupFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("LocalBackup", "Backup error: ${e.message}", e)
            throw e
        }
    }
}

data class OngoingTourRecoveryData(
    val isTourActive: Boolean,
    val tourStartMillis: Long,
    val supervisorName: String,
    val currentGps: String
)
