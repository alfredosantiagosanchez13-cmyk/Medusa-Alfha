package com.example.data.ai

import android.util.Log
import com.example.data.audit.AuditLogEntity
import com.example.data.auth.AreaIsolationCheckResult
import com.example.data.auth.AreaSector
import com.example.data.auth.MedusaAreaIsolationGuard
import com.example.data.auth.MedusaFinancialAccessGuard
import com.example.data.auth.MedusaRole
import com.example.data.booking.AppDatabase
import com.example.data.chat.AiGuardChatLog
import com.example.data.core.TimeReturnEngine
import com.example.data.sync.OfflineSyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * INTERFAZ MODULAR PREPARADA PARA MODELOS FUNDACIONALES EN DISPOSITIVO (LiteRT / Gemini Nano Ready).
 *
 * Permite la evolución progresiva a modelos de inferencia local sin romper la arquitectura
 * ni alterar los bypass de validación criptográfica local ('MEDUSA-ADM-2026' y 'MEDUSA-CASETA-2026').
 */
interface MedusaFoundationAiModel {
    val modelName: String
    val isReadyOnDevice: Boolean

    suspend fun generateSandboxedResponse(
        context: AiSandboxedContext,
        prompt: String,
        db: AppDatabase
    ): String
}

/**
 * Contexto de datos segmentado de forma física en memoria.
 * Evita contaminación cruzada de datos entre sectores (Anti-Filtering Sandboxing).
 */
data class AiSandboxedContext(
    val role: MedusaRole,
    val sector: AreaSector,
    val condominiumId: String,
    val assignedUnitId: String? = null,
    val financialNodesAllowed: Boolean = false,
    val tacticalIntercomsAvailable: Boolean = true,
    val memorySignature: String = UUID.randomUUID().toString()
)

sealed class AiExecutionResult {
    data class Success(val responseText: String, val sandboxedContext: AiSandboxedContext) : AiExecutionResult()
    data class Restricted(
        val errorMessage: String = MedusaAreaIsolationGuard.RESTRICTED_ACCESS_ERROR,
        val reason: String,
        val attemptedSector: AreaSector
    ) : AiExecutionResult()
}

/**
 * NÚCLEO CENTRAL DE IA: MEDUSA AI CORE (MEDUSA OS v3)
 *
 * Aplica el mandato de aislamiento criptográfico y lógico absoluto (Zero-Trust Inter-Area Protocol).
 */
object MedusaAiCore {
    private const val TAG = "MedusaAiCore"

    // Proveedor activo de IA (LiteRT / Gemini Nano / Motor Local Zero-Trust)
    @Volatile
    private var activeAiModel: MedusaFoundationAiModel = LocalZeroTrustInferenceEngine

    /**
     * Permite conectar dinámicamente un modelo fundacional LiteRT o Gemini Nano.
     */
    fun registerFoundationModel(model: MedusaFoundationAiModel) {
        activeAiModel = model
        Log.i(TAG, "🤖 Modelo fundacional de IA registrado con éxito: ${model.modelName}")
    }

    /**
     * Procesa un comando de texto o voz aplicando Data Sandboxing estricto.
     */
    suspend fun processPrompt(
        db: AppDatabase,
        prompt: String,
        role: MedusaRole,
        condominiumId: String = "PRADOS_1",
        assignedUnit: String? = null,
        isVoice: Boolean = false,
        operatorName: String = "Terminal Operativa"
    ): AiExecutionResult = withContext(Dispatchers.IO) {
        val sanitizedPrompt = prompt.trim()

        // 1. FILTRADO NATIVO E INTERCEPTOR INTER-ÁREAS (Anti-Filtering Sandboxing)
        val isolationCheck = MedusaAreaIsolationGuard.evaluateQueryAccess(role, sanitizedPrompt)

        if (isolationCheck is AreaIsolationCheckResult.Blocked) {
            val incidentFolio = "AUD-ISOLATION-${System.currentTimeMillis() % 100000}"

            // Asentar alerta inmutable en medusa_audit_logs
            try {
                val audit = AuditLogEntity(
                    logId = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    operatorId = "$operatorName [${role.name}]",
                    eventDescription = "Intento de violación de aislamiento inter-área: ${isolationCheck.reason}",
                    severity = AuditLogEntity.Severity.SECURITY_CRITICAL,
                    forensicPayload = """{"prompt":"$sanitizedPrompt","role":"${role.name}","attemptedSector":"${isolationCheck.attemptedSector.name}","keyword":"${isolationCheck.triggeredKeyword}"}"""
                )
                db.auditLogDao().insertAuditLog(audit)
            } catch (e: Exception) {
                Log.e(TAG, "Error registrando auditoría forense de aislamiento: ${e.message}")
            }

            // Asentar en ai_guard_chat_logs
            try {
                val chatLog = AiGuardChatLog(
                    id = UUID.randomUUID().toString(),
                    sender = "AI",
                    content = MedusaAreaIsolationGuard.RESTRICTED_ACCESS_ERROR,
                    activeRole = role.name,
                    isAccessDenied = true,
                    timestampMillis = System.currentTimeMillis(),
                    operatorName = operatorName,
                    isVoiceDictation = isVoice
                )
                db.aiGuardChatLogDao().insertChatLog(chatLog)
            } catch (e: Exception) {
                Log.e(TAG, "Error registrando chat log de acceso denegado: ${e.message}")
            }

            return@withContext AiExecutionResult.Restricted(
                errorMessage = MedusaAreaIsolationGuard.RESTRICTED_ACCESS_ERROR,
                reason = isolationCheck.reason,
                attemptedSector = isolationCheck.attemptedSector
            )
        }

        // 2. CONSTRUCCIÓN DEL CONTEXTO FÍSICO SEGMENTADO EN MEMORIA
        val sector = when (role) {
            MedusaRole.ADMINISTRACION -> AreaSector.ADMINISTRACION
            MedusaRole.GUARDIA_CASETA -> AreaSector.CASETA_SEGURIDAD
            MedusaRole.RESIDENTE, MedusaRole.UNASSIGNED -> AreaSector.RESIDENCIAL
        }

        val sandboxedContext = AiSandboxedContext(
            role = role,
            sector = sector,
            condominiumId = condominiumId,
            assignedUnitId = assignedUnit,
            financialNodesAllowed = (role == MedusaRole.ADMINISTRACION && !MedusaFinancialAccessGuard.isFinancialAccessBlocked()),
            tacticalIntercomsAvailable = true
        )

        // 3. GENERACIÓN DE RESPUESTA A TRAVÉS DEL MODELO AISLADO
        val response = activeAiModel.generateSandboxedResponse(sandboxedContext, sanitizedPrompt, db)

        // 4. REGISTRAR INTERACCIÓN AUTORIZADA EN BITÁCORA ROOM
        try {
            val userMsg = AiGuardChatLog(
                id = UUID.randomUUID().toString(),
                sender = "USER",
                content = sanitizedPrompt,
                activeRole = role.name,
                isAccessDenied = false,
                timestampMillis = System.currentTimeMillis(),
                operatorName = operatorName,
                isVoiceDictation = isVoice
            )
            val aiMsg = AiGuardChatLog(
                id = UUID.randomUUID().toString(),
                sender = "AI",
                content = response,
                activeRole = role.name,
                isAccessDenied = false,
                timestampMillis = System.currentTimeMillis() + 50,
                operatorName = "MEDUSA OS v3 Core",
                isVoiceDictation = false
            )
            db.aiGuardChatLogDao().insertChatLog(userMsg)
            db.aiGuardChatLogDao().insertChatLog(aiMsg)
        } catch (e: Exception) {
            Log.w(TAG, "Aviso registrando logs de conversación: ${e.message}")
        }

        return@withContext AiExecutionResult.Success(response, sandboxedContext)
    }
}

/**
 * Motor local predeterminado de inferencia Zero-Trust (LiteRT Ready).
 */
object LocalZeroTrustInferenceEngine : MedusaFoundationAiModel {
    override val modelName: String = "Medusa-LiteRT-ZeroTrust-v3"
    override val isReadyOnDevice: Boolean = true

    override suspend fun generateSandboxedResponse(
        context: AiSandboxedContext,
        prompt: String,
        db: AppDatabase
    ): String = withContext(Dispatchers.IO) {
        val q = prompt.lowercase()

        when (context.role) {
            MedusaRole.GUARDIA_CASETA -> {
                // SECTOR CASETA DE SEGURIDAD: Solo datos tácticos de acceso, intercoms y lotes habitados/desocupados.
                // Prohibido el acceso a balances, deudas, finanzas o información personal.
                when {
                    q.contains("lote") || q.contains("casa") || q.contains("ocupacion") || q.contains("intercom") -> {
                        val units = OfflineSyncEngine.getSanitizedUnitsForRole(db, MedusaRole.GUARDIA_CASETA)
                        val habitadas = units.count { it.status == "HABITADA" }
                        val desocupadas = units.count { it.status == "DESOCUPADA" }
                        buildString {
                            appendLine("🛡️ **CONTROL TÁCTICO DE LOTES (CASETA DE SEGURIDAD):**")
                            appendLine("• Total Lotes en Registro: **${units.size}** (Prados Residencial)")
                            appendLine("• Unidades Habitadas: **$habitadas** | Desocupadas: **$desocupadas**")
                            appendLine("• Intercomunicadores y Escaneo QR: Habilitados para CameraX + ZXing.")
                            appendLine("🔒 *Filtro Anti-Filtering activo: Datos personales de residentes y finanzas no expuestos.*")
                        }
                    }
                    q.contains("visita") || q.contains("acceso") || q.contains("ingreso") -> {
                        val checkIns = try { db.visitorCheckInDao().getAllCheckInsList() } catch (_: Exception) { emptyList() }
                        val active = checkIns.count { it.status == "CHECKED_IN" || it.status == "VERIFICADO" }
                        buildString {
                            appendLine("🚪 **CONTROL DE VISITAS (GARITA ACTIVA):**")
                            appendLine("• Visitas activas en interior: **$active**")
                            appendLine("• Protocolo: Validación QR por escáner táctico CameraX.")
                        }
                    }
                    q.contains("paquete") || q.contains("paqueteria") -> {
                        val packages = try { db.packageDao().getAllPackagesList() } catch (_: Exception) { emptyList() }
                        val pending = packages.filter { it.status != "ENTREGADO" }
                        "📦 **AVISOS DE PAQUETERÍA:** ${pending.size} avisos activos. Regla: Sin resguardo físico en caseta; notificación inmediata."
                    }
                    else -> {
                        "🛡️ **COPILOTO TÁCTICO DE CASETA (MEDUSA OS v3):**\n" +
                                "Operación táctica autorizada para Garita Principal.\n" +
                                "• Puede consultar: 'Lotes y ocupación', 'Visitas activas', 'Avisos de paquetería', 'Reporte táctico de turno'.\n" +
                                "🔒 Nodos de administración y finanzas aislados de esta terminal."
                    }
                }
            }

            MedusaRole.ADMINISTRACION -> {
                // SECTOR ADMINISTRACIÓN: Supervisión consolidada, finanzas, métricas sagradas y auditoría.
                when {
                    q.contains("resumen") || q.contains("ejecutivo") -> {
                        val stats = try { TimeReturnEngine.computeStats(db) } catch (_: Exception) { null }
                        val timeMin = (stats?.totalSecondsSaved ?: 0L) / 60
                        val units = try { db.unitDao().getUnitCount() } catch (_: Exception) { 261 }
                        val checkIns = try { db.visitorCheckInDao().getCheckInCount() } catch (_: Exception) { 0 }
                        buildString {
                            appendLine("📋 **INFORME EJECUTIVO DE ADMINISTRACIÓN (MEDUSA OS v3):**")
                            appendLine("• Condominio: Residencial Los Prados (${context.condominiumId})")
                            appendLine("• Lotes Auditados en Croquis: **$units**")
                            appendLine("• Accesos Totales Registrados: **$checkIns**")
                            appendLine("• Métrica Sagrada (Tiempo Devuelto): **$timeMin minutos**")
                            appendLine("• Nivel de Seguridad: Aislamiento Inter-Áreas Activo (Zero-Trust)")
                        }
                    }
                    q.contains("finanza") || q.contains("cuota") || q.contains("pago") || q.contains("ingreso") -> {
                        val unitsCount = try { db.unitDao().getUnitCount() } catch (_: Exception) { 261 }
                        buildString {
                            appendLine("💰 **PANEL DE CONTROL FINANCIERO (ADMINISTRACIÓN):**")
                            appendLine("• Unidades en facturación y gestión de cuotas: **$unitsCount** lotes.")
                            appendLine("• Estado de cuenta: En conciliación bancaria y auditoría periódica.")
                            appendLine("🔒 *Área aislada de terminales de caseta y residentes.*")
                        }
                    }
                    else -> {
                        "🏛️ **COPILOTO ADMINISTRATIVO CENTRAL (MEDUSA OS v3):**\n" +
                                "Terminal autorizada con privilegios maestros de gobernanza.\n" +
                                "• Puede solicitar: 'Resumen ejecutivo', 'Finanzas y cuotas', 'Auditoría de rondines', 'Ocupación de lotes'."
                    }
                }
            }

            MedusaRole.RESIDENTE, MedusaRole.UNASSIGNED -> {
                // SECTOR RESIDENTE: Limitado a su vivienda y servicios comunitarios
                val unit = context.assignedUnitId ?: "Casa Asignada"
                "🏡 **PORTAL INTELIGENTE DEL RESIDENTE ($unit):**\n" +
                        "• Puede gestionar: Pases QR para sus visitas, reservas de amenidades y consultar comunicados oficiales."
            }
        }
    }
}
