package com.example.data.ai

import android.util.Log
import com.example.data.audit.AuditLogEntity
import com.example.data.auth.AreaIsolationCheckResult
import com.example.data.auth.MedusaAreaIsolationGuard
import com.example.data.auth.MedusaFinancialAccessGuard
import com.example.data.auth.MedusaRole
import com.example.data.booking.AppDatabase
import com.example.data.chat.AiGuardChatLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Categorías de Intención del Comando de Voz evaluadas antes de la ejecución.
 */
enum class VoiceCommandIntent(val description: String) {
    TACTICAL_INCIDENT_REPORT("Reporte táctico de novedades o incidencias en caseta"),
    VISITOR_ACCESS_QUERY("Consulta de visitas activas o verificación QR"),
    UNIT_OCCUPANCY_INTERCOM("Consulta de ocupación de lotes y códigos de interfón"),
    ADMIN_EXECUTIVE_SUMMARY("Consulta ejecutiva y de métricas sagradas para administración"),
    ADMIN_FINANCIAL_QUERY("Consulta de balances, cuotas y finanzas (Solo Administración)"),
    RESTRICTED_ATTEMPT("Intento de violación de seguridad o acceso cruzado entre sectores"),
    GENERAL_VOICE_QUERY("Consulta operativa general")
}

data class VoiceIntentAnalysis(
    val intent: VoiceCommandIntent,
    val rawSpokenText: String,
    val isAuthorized: Boolean,
    val restrictedReason: String? = null,
    val errorCode: String? = null
)

sealed class VoiceExecutionResult {
    data class Success(val responseText: String, val intent: VoiceCommandIntent) : VoiceExecutionResult()
    data class Blocked(val errorCode: String, val reason: String, val intent: VoiceCommandIntent) : VoiceExecutionResult()
}

/**
 * DICTADOR DE VOZ SEGURO Y FILTRADO NATIVO (RBAC SPEECH ENGINE)
 * SISTEMA DE SEGURIDAD MEDUSA OS v3
 *
 * Intercepta y analiza la intención de cada comando de voz ANTES de que se ejecute o procese.
 * Aplica de forma nativa MedusaFinancialAccessGuard y MedusaAreaIsolationGuard.
 */
object MedusaVoiceEngine {
    private const val TAG = "MedusaVoiceEngine"

    /**
     * Analiza e intercepta la intención del comando de voz.
     * Si detecta palabras sensibles en una terminal no autorizada (ej. Caseta pidiendo finanzas o nóminas),
     * congela la ejecución de inmediato, registra el evento forense en Room y oculta los datos.
     */
    suspend fun interceptAndExecuteVoiceCommand(
        spokenText: String,
        role: MedusaRole,
        db: AppDatabase,
        condominiumId: String = "PRADOS_1",
        operatorName: String = "Guardia de Caseta"
    ): VoiceExecutionResult = withContext(Dispatchers.IO) {
        val cleanSpeech = spokenText.trim()
        val lowerSpeech = cleanSpeech.lowercase()

        // 1. ANÁLISIS DE INTENCIÓN PREVIO A LA EJECUCIÓN
        val intent = categorizeVoiceIntent(lowerSpeech, role)

        // 2. VALIDACIÓN NATIVA CON POLÍTICAS MEDUSA
        val isolationCheck = MedusaAreaIsolationGuard.evaluateQueryAccess(role, lowerSpeech)

        val isRestricted = (isolationCheck is AreaIsolationCheckResult.Blocked) ||
                (role.requiresFinancialNodeLock() && intent == VoiceCommandIntent.ADMIN_FINANCIAL_QUERY) ||
                (intent == VoiceCommandIntent.RESTRICTED_ATTEMPT)

        if (isRestricted) {
            val reason = if (isolationCheck is AreaIsolationCheckResult.Blocked) {
                isolationCheck.reason
            } else {
                "Intento de comando de voz hacia información financiera/administrativa confidencial."
            }

            Log.e(
                TAG,
                "🚨 [ALERTA DE SEGURIDAD MEDUSA] Interceptor de voz bloqueó comando: '$cleanSpeech' en terminal $role. Motivo: $reason"
            )

            // Asentar log inmutable de alerta en 'medusa_audit_logs'
            try {
                val audit = AuditLogEntity(
                    logId = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    operatorId = "$operatorName [${role.name}]",
                    eventDescription = "Intento no autorizado por dictador de voz: $reason",
                    severity = AuditLogEntity.Severity.SECURITY_CRITICAL,
                    forensicPayload = """{"voice_command":"$cleanSpeech","role":"${role.name}","intent":"${intent.name}"}"""
                )
                db.auditLogDao().insertAuditLog(audit)
            } catch (e: Exception) {
                Log.e(TAG, "Error persistiendo auditoría inmutable de voz: ${e.message}")
            }

            // Asentar en 'ai_guard_chat_logs' (registro forense de IA)
            try {
                val chatLog = AiGuardChatLog(
                    id = UUID.randomUUID().toString(),
                    sender = "VOICE_INTERCEPTOR",
                    content = MedusaAreaIsolationGuard.RESTRICTED_ACCESS_ERROR,
                    activeRole = role.name,
                    isAccessDenied = true,
                    timestampMillis = System.currentTimeMillis(),
                    operatorName = operatorName,
                    isVoiceDictation = true
                )
                db.aiGuardChatLogDao().insertChatLog(chatLog)
            } catch (e: Exception) {
                Log.e(TAG, "Error registrando chat log de acceso denegado por voz: ${e.message}")
            }

            // Retorno seguro: Congelar hilo y mantener información completamente oculta
            return@withContext VoiceExecutionResult.Blocked(
                errorCode = MedusaAreaIsolationGuard.RESTRICTED_ACCESS_ERROR,
                reason = reason,
                intent = intent
            )
        }

        // 3. EJECUCIÓN AUTORIZADA DENTRO DEL DATA SANDBOX
        val aiResult = MedusaAiCore.processPrompt(
            db = db,
            prompt = cleanSpeech,
            role = role,
            condominiumId = condominiumId,
            isVoice = true,
            operatorName = operatorName
        )

        return@withContext when (aiResult) {
            is AiExecutionResult.Success -> {
                VoiceExecutionResult.Success(aiResult.responseText, intent)
            }
            is AiExecutionResult.Restricted -> {
                VoiceExecutionResult.Blocked(
                    errorCode = aiResult.errorMessage,
                    reason = aiResult.reason,
                    intent = intent
                )
            }
        }
    }

    /**
     * Categoriza la intención semántica del dictado.
     */
    private fun categorizeVoiceIntent(lower: String, role: MedusaRole): VoiceCommandIntent {
        return when {
            lower.contains("finanza") || lower.contains("nómina") || lower.contains("nomina") ||
                    lower.contains("ingreso") || lower.contains("egreso") || lower.contains("balance") ||
                    lower.contains("datos de administrador") || lower.contains("cuenta bancaria") ||
                    lower.contains("moroso") || lower.contains("morosos") || lower.contains("cuota") -> {
                if (role == MedusaRole.ADMINISTRACION) VoiceCommandIntent.ADMIN_FINANCIAL_QUERY
                else VoiceCommandIntent.RESTRICTED_ATTEMPT
            }
            lower.contains("incidencia") || lower.contains("reporte") || lower.contains("novedad") ||
                    lower.contains("alerta") || lower.contains("relevo") -> {
                VoiceCommandIntent.TACTICAL_INCIDENT_REPORT
            }
            lower.contains("visita") || lower.contains("qr") || lower.contains("pase") ||
                    lower.contains("acceso") || lower.contains("garita") -> {
                VoiceCommandIntent.VISITOR_ACCESS_QUERY
            }
            lower.contains("lote") || lower.contains("casa") || lower.contains("intercom") ||
                    lower.contains("ocupacion") || lower.contains("habitada") -> {
                VoiceCommandIntent.UNIT_OCCUPANCY_INTERCOM
            }
            lower.contains("resumen") || lower.contains("ejecutivo") || lower.contains("auditoría") -> {
                VoiceCommandIntent.ADMIN_EXECUTIVE_SUMMARY
            }
            else -> VoiceCommandIntent.GENERAL_VOICE_QUERY
        }
    }
}
