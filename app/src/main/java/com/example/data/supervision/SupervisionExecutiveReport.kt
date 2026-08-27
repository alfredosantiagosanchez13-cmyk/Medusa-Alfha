package com.example.data.supervision

import com.example.data.core.AlphaCoreEngine
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modelo inmutable de Informe Ejecutivo de Supervisión Táctica ALFHA.
 * Generado 100% a partir de datos reales de la ronda en Room SQLite, sin recaptura.
 */
data class SupervisionExecutiveReport(
    val folio: String,
    val dateFormatted: String,
    val timeFormatted: String,
    val supervisorName: String,
    val mainLocation: String,
    val totalCheckpointsCount: Int,
    val checkpointsList: List<SupervisionAuditEntity>,
    val optimumCount: Int,
    val regularCount: Int,
    val criticalCount: Int,
    val omittedCount: Int = 0,
    val findingsSummary: String,
    val evidenceSummary: String,
    val correctiveActionsSummary: String,
    val finalResult: String, // CONFORME CON PROTOCOLO, CON OBSERVACIONES MENORES, NO CONFORME / ATENCIÓN CRÍTICA
    val durationMinutes: Int,
    val integrityHashSha256: String
) {
    companion object {
        fun buildFromAudits(
            tourFolio: String,
            supervisorName: String,
            mainLocation: String,
            tourAudits: List<SupervisionAuditEntity>,
            durationMinutes: Int
        ): SupervisionExecutiveReport {
            val now = Date()
            val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now)
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)

            val optimum = tourAudits.count { it.statusCondition.equals("OPTIMO", ignoreCase = true) }
            val regular = tourAudits.count { it.statusCondition.equals("REGULAR", ignoreCase = true) }
            val critical = tourAudits.count { it.statusCondition.equals("CRITICO", ignoreCase = true) }
            val omitted = tourAudits.count { it.statusCondition.equals("OMITIDO", ignoreCase = true) }

            val findings = if (tourAudits.isEmpty()) {
                "Sin observaciones registradas durante la ronda."
            } else {
                tourAudits.joinToString("\n") { audit ->
                    val statusEmoji = when (audit.statusCondition.uppercase()) {
                        "OPTIMO" -> "🟢"
                        "REGULAR" -> "🟡"
                        "CRITICO" -> "🔴"
                        "OMITIDO" -> "🟣"
                        else -> "⚪"
                    }
                    "$statusEmoji [${audit.checkpointName}] (${audit.statusCondition}): ${audit.findingsDescription}"
                }
            }

            val evidences = if (tourAudits.isEmpty()) {
                "Geo-posicionamiento GPS registrado en cada punto auditado."
            } else {
                tourAudits.mapNotNull { audit ->
                    val gps = audit.gpsCoordinates ?: "GPS Garita"
                    val photo = audit.photoEvidencePath?.let { " [📷 Evidencia: $it]" } ?: ""
                    "• ${audit.checkpointName}: Coord $gps$photo (${audit.formattedTime})"
                }.joinToString("\n")
            }

            val actions = if (tourAudits.isEmpty() || (critical + regular + omitted == 0)) {
                "Mantener estándar operacional. No se requieren acciones correctivas inmediatas."
            } else {
                tourAudits.filter { it.statusCondition != "OPTIMO" }.joinToString("\n") { audit ->
                    "• [${audit.checkpointName}] (${audit.statusCondition}) Responsable: ${audit.responsibleParty} | Acción: ${audit.correctiveActionRequired} (Compromiso: ${audit.commitmentDate})"
                }
            }

            val result = when {
                critical > 0 -> "NO CONFORME - REQUIERE ATENCIÓN CRÍTICA INMEDIATA"
                omitted > 0 -> "CON OBSERVACIONES - PUNTOS DE CONTROL OMITIDOS EN RUTA"
                regular > 0 -> "CONFORME CON OBSERVACIONES OPERACIONALES MENORES"
                else -> "CONFORME - ESTÁNDAR DE SEGURIDAD ALFHA 100% CUMPLIDO"
            }

            val rawDataForHash = "$tourFolio|$dateStr|$timeStr|$supervisorName|$mainLocation|${tourAudits.size}|$optimum|$regular|$critical|$omitted|$result|ALFHA_INTEGRAL_2026"
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(rawDataForHash.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .lowercase(Locale.ROOT)

            return SupervisionExecutiveReport(
                folio = tourFolio,
                dateFormatted = dateStr,
                timeFormatted = timeStr,
                supervisorName = supervisorName,
                mainLocation = mainLocation,
                totalCheckpointsCount = tourAudits.size,
                checkpointsList = tourAudits,
                optimumCount = optimum,
                regularCount = regular,
                criticalCount = critical,
                omittedCount = omitted,
                findingsSummary = findings,
                evidenceSummary = evidences,
                correctiveActionsSummary = actions,
                finalResult = result,
                durationMinutes = durationMinutes,
                integrityHashSha256 = hash
            )
        }
    }
}
