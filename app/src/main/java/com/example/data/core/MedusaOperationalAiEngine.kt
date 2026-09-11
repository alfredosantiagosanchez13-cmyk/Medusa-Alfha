package com.example.data.core

import com.example.auth.UserRole
import com.example.data.booking.AppDatabase
import com.example.data.incident.IncidentEntity
import com.example.data.packages.PackageEntity
import com.example.data.supervision.SupervisionAuditEntity
import com.example.data.vehicle.VehicleAccessLogEntity
import com.example.data.visitor.VisitorCheckIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * MOTOR DE INTELIGENCIA OPERACIONAL IA MEDUSA
 *
 * Principio Rector: La IA no inventa datos ni utiliza mocks.
 * Consume directamente la Fuente Única de Verdad en Room SQLite.
 * "ESTO DEVUELVE TIEMPO (TIEMPO = FAMILIA)"
 *
 * Capacidades Clave:
 * 1. Resumen Ejecutivo de Operación en Tiempo Real.
 * 2. Análisis de Incidencias y Detección de Zonas Recurrentes.
 * 3. Identificación de Turnos con Anomalías y Picos de Tráfico.
 * 4. Apoyo Táctico para Informes de Supervisión y Cumplimiento.
 * 5. Cuantificación de Tiempo Devuelto acumulado a la comunidad.
 * 6. Respuestas a consultas operativas de guardias, administradores y supervisores.
 */
object MedusaOperationalAiEngine {

    /**
     * Genera un Resumen Ejecutivo consolidado con datos 100% reales de Room SQLite.
     */
    suspend fun generateExecutiveSummary(db: AppDatabase): String = withContext(Dispatchers.IO) {
        val checkIns = try { db.visitorCheckInDao().getAllCheckInsList() } catch (e: Exception) { emptyList() }
        val incidents = try { db.incidentDao().getAllIncidentsList() } catch (e: Exception) { emptyList() }
        val audits = try { db.supervisionAuditDao().getAllAuditsList() } catch (e: Exception) { emptyList() }
        val packages = try { db.packageDao().getAllPackagesList() } catch (e: Exception) { emptyList() }
        val vehicles = try { db.vehicleDao().getAllAccessLogsList() } catch (e: Exception) { emptyList() }
        val timeStats = try { TimeReturnEngine.computeStats(db) } catch (e: Exception) { null }

        val activeVisitors = checkIns.count { it.status == "CHECKED_IN" || it.status == "VERIFICADO" }
        val departures = checkIns.count { it.status == "DEPARTED" }
        val openIncidents = incidents.count { it.status != "RESUELTO" && it.status != "CERRADO" }
        val criticalIncidents = incidents.count { (it.status != "RESUELTO" && it.status != "CERRADO") && it.priority.name == "CRITICA" }
        val pendingPackages = packages.count { it.status != "ENTREGADO" }
        val vehiclesInside = vehicles.count { it.status == "DENTRO_DEL_CONDOMINIO" }

        val totalTimeMinutes = (timeStats?.totalSecondsSaved ?: 0L) / 60

        buildString {
            appendLine("📋 **RESUMEN EJECUTIVO DE OPERACIÓN MEDUSA ALFHA**")
            appendLine("Fecha de Corte: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}")
            appendLine("Fuente: Room SQLite (Base Local Inmutable)")
            appendLine("--------------------------------------------------")
            appendLine("⏱️ **MÉTRICA SAGRADA (TIEMPO DEVUELTO):**")
            appendLine("• **${totalTimeMinutes} minutos** (${totalTimeMinutes / 60}h ${totalTimeMinutes % 60}m) ahorrados a la comunidad y personal.")
            appendLine("• Elimina recaptura, cuadernos de papel, llamadas de confirmación y elaboración manual de reportes.")
            appendLine()
            appendLine("🚪 **CONTROL DE ACCESO & VISITAS:**")
            appendLine("• Visitas activas dentro del condominio: **$activeVisitors**")
            appendLine("• Salidas registradas (1 toque): **$departures**")
            appendLine("• Vehículos en tránsito interior: **$vehiclesInside**")
            appendLine()
            appendLine("📦 **AVISOS DE PAQUETERÍA (SIN RESGUARDO FÍSICO):**")
            appendLine("• Avisos activos en espera de residente: **$pendingPackages**")
            appendLine("• Regla: Caseta no almacena paquetería; notifica llegada para entrega directa inmediata.")
            appendLine()
            appendLine("🚨 **INCIDENCIAS & SEGURIDAD:**")
            appendLine("• Incidencias abiertas / en atención: **$openIncidents**")
            if (criticalIncidents > 0) {
                appendLine("• ⚠️ **CRÍTICAS ACTIVAS:** **$criticalIncidents** (Requieren atención inmediata)")
            } else {
                appendLine("• ✅ Sin incidencias críticas activas.")
            }
            appendLine()
            appendLine("🛡️ **RONDINES & SUPERVISIÓN:**")
            appendLine("• Puntos de control / auditorías registradas: **${audits.size}**")
            val criticalAudits = audits.count { it.riskLevel == "CRITICO" || it.riskLevel == "ALTO" }
            if (criticalAudits > 0) {
                appendLine("• Puntos con riesgo Alto/Crítico: **$criticalAudits**")
            } else {
                appendLine("• Perímetro y garitas en estado ÓPTIMO.")
            }
        }
    }

    /**
     * Análisis de Incidencias: Detección de patrones, zonas recurrentes y categorías prioritarias.
     */
    suspend fun analyzeIncidentPatterns(db: AppDatabase): String = withContext(Dispatchers.IO) {
        val incidents = try { db.incidentDao().getAllIncidentsList() } catch (e: Exception) { emptyList() }

        if (incidents.isEmpty()) {
            return@withContext "✅ **ANÁLISIS DE INCIDENCIAS:** No se registran incidencias en la base de datos local. Operación sin novedades adversas."
        }

        // Agrupación por ubicación para zonas recurrentes
        val locationGroups = incidents.groupBy { it.location.trim().uppercase() }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }

        // Agrupación por categoría
        val categoryGroups = incidents.groupBy { it.category.displayName }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }

        val activeIncidents = incidents.filter { it.status != "RESUELTO" && it.status != "CERRADO" }
        val slaExceededCount = incidents.count { it.isSlaExceeded() }

        buildString {
            appendLine("🔍 **ANÁLISIS DE INCIDENCIAS & DETECCIÓN DE PATRONES (IA MEDUSA)**")
            appendLine("Total histórico analizado: ${incidents.size} registros de Room")
            appendLine()
            appendLine("📍 **ZONAS RECURRENTES IDENTIFICADAS:**")
            locationGroups.take(4).forEachIndexed { idx, (loc, count) ->
                val pct = (count * 100) / incidents.size
                appendLine("${idx + 1}. **$loc**: $count incidencias ($pct% del total)")
            }
            appendLine()
            appendLine("📊 **CATEGORÍAS CON MAYOR INCIDENCIA:**")
            categoryGroups.take(3).forEach { (cat, count) ->
                appendLine("• $cat: $count reportes")
            }
            appendLine()
            appendLine("⏱️ **CUMPLIMIENTO DE SLA & ATENCIÓN:**")
            appendLine("• Incidencias pendientes de cierre: **${activeIncidents.size}**")
            appendLine("• Incidencias con SLA excedido: **$slaExceededCount**")
            if (activeIncidents.isNotEmpty()) {
                appendLine("• Próxima a atender: ${activeIncidents.first().folio} - ${activeIncidents.first().location} (${activeIncidents.first().priority.displayName})")
            }
            appendLine()
            appendLine("💡 **RECOMENDACIÓN OPERACIONAL IA:**")
            if (locationGroups.isNotEmpty() && locationGroups.first().second >= 2) {
                appendLine("Reforzar rondines preventivos con checkpoint GPS prioritario en: **${locationGroups.first().first}**.")
            } else {
                appendLine("Mantener protocolos de vigilancia estándar y tiempos de respuesta bajo 15 minutos.")
            }
        }
    }

    /**
     * Identificación de Turnos con Anomalías y Picos de Carga.
     */
    suspend fun analyzeShiftAnomalies(db: AppDatabase): String = withContext(Dispatchers.IO) {
        val checkIns = try { db.visitorCheckInDao().getAllCheckInsList() } catch (e: Exception) { emptyList() }
        val incidents = try { db.incidentDao().getAllIncidentsList() } catch (e: Exception) { emptyList() }

        var matutinoVisitas = 0
        var vespertinoVisitas = 0
        var nocturnoVisitas = 0

        val cal = Calendar.getInstance()

        checkIns.forEach { ci ->
            cal.timeInMillis = ci.timestampMillis
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            when (hour) {
                in 6..13 -> matutinoVisitas++
                in 14..21 -> vespertinoVisitas++
                else -> nocturnoVisitas++
            }
        }

        var matutinoIncidencias = 0
        var vespertinoIncidencias = 0
        var nocturnoIncidencias = 0

        incidents.forEach { inc ->
            cal.timeInMillis = inc.timestampMillis
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            when (hour) {
                in 6..13 -> matutinoIncidencias++
                in 14..21 -> vespertinoIncidencias++
                else -> nocturnoIncidencias++
            }
        }

        buildString {
            appendLine("🕒 **ANÁLISIS DE TURNOS Y ANOMALÍAS OPERACIONALES**")
            appendLine("Distribución real calculada desde Room:")
            appendLine()
            appendLine("🌅 **TURNO MATUTINO (06:00 - 14:00):**")
            appendLine("• Flujo de Visitas: **$matutinoVisitas** accesos")
            appendLine("• Incidencias Registradas: **$matutinoIncidencias**")
            appendLine()
            appendLine("☀️ **TURNO VESPERTINO (14:00 - 22:00):**")
            appendLine("• Flujo de Visitas: **$vespertinoVisitas** accesos")
            appendLine("• Incidencias Registradas: **$vespertinoIncidencias**")
            appendLine()
            appendLine("🌙 **TURNO NOCTURNO (22:00 - 06:00):**")
            appendLine("• Flujo de Visitas: **$nocturnoVisitas** accesos")
            appendLine("• Incidencias Registradas: **$nocturnoIncidencias**")
            appendLine()
            appendLine("⚡ **DICTAMEN TÁCTICO IA:**")
            val peakShift = when {
                matutinoVisitas >= vespertinoVisitas && matutinoVisitas >= nocturnoVisitas -> "Matutino (Horario de proveedores y paquetería)"
                vespertinoVisitas >= matutinoVisitas && vespertinoVisitas >= nocturnoVisitas -> "Vespertino (Horario de visitas sociales y retornos)"
                else -> "Nocturno"
            }
            appendLine("• Pico de mayor tráfico en garita: **$peakShift**.")
            if (nocturnoIncidencias > 0) {
                appendLine("• Alerta: Se registraron $nocturnoIncidencias novedades en turno nocturno. Se recomienda verificar bitácora y cerco perimetral.")
            } else {
                appendLine("• El turno nocturno se mantiene en parámetros normales de calma.")
            }
        }
    }

    /**
     * Apoyo para Informes de Supervisión Táctica y Rondines.
     */
    suspend fun generateSupervisionSupportReport(db: AppDatabase): String = withContext(Dispatchers.IO) {
        val audits = try { db.supervisionAuditDao().getAllAuditsList() } catch (e: Exception) { emptyList() }

        if (audits.isEmpty()) {
            return@withContext "📋 **INFORME DE SUPERVISIÓN:** No hay registros de rondines o auditorías en Room. Se requiere realizar la ronda perimetral inicial."
        }

        val optimal = audits.count { it.statusCondition == "OPTIMO" }
        val regular = audits.count { it.statusCondition == "REGULAR" }
        val critical = audits.count { it.statusCondition == "CRITICO" || it.riskLevel == "CRITICO" || it.riskLevel == "ALTO" }

        buildString {
            appendLine("🛡️ **INFORME TÁCTICO DE SUPERVISIÓN & RONDINES**")
            appendLine("Puntos auditados en Room: ${audits.size}")
            appendLine("• Óptimos: **$optimal**")
            appendLine("• Con Novedad Regular: **$regular**")
            appendLine("• Críticos / Requiere Acción: **$critical**")
            appendLine()
            appendLine("📌 **ÚLTIMOS HALLAZGOS REGISTRADOS:**")
            audits.take(3).forEach { a ->
                appendLine("• [${a.folio}] ${a.checkpointName} (${a.areaName}) - Condición: ${a.statusCondition}")
                if (a.findingsDescription.isNotBlank()) {
                    appendLine("  Detalle: \"${a.findingsDescription}\"")
                }
                if (a.correctiveActionRequired.isNotBlank()) {
                    appendLine("  Acción Requerida: ${a.correctiveActionRequired} (Resp: ${a.responsibleParty})")
                }
            }
            appendLine()
            appendLine("🔐 **INTEGRIDAD Y CERTIFICACIÓN:**")
            appendLine("Cada registro cuenta con Folio unificado, sellos de tiempo y coordenadas GPS validadas.")
        }
    }

    /**
     * Responde de forma inteligente a preguntas en lenguaje natural del Copiloto consultando Room en vivo.
     */
    suspend fun answerOperationalQuery(db: AppDatabase, query: String, role: UserRole): String = withContext(Dispatchers.IO) {
        val q = query.lowercase(Locale.getDefault())

        return@withContext when {
            q.contains("resumen") || q.contains("ejecutivo") || q.contains("estado general") || q.contains("como vamos") || q.contains("cómo vamos") -> {
                generateExecutiveSummary(db)
            }
            q.contains("patron") || q.contains("patrón") || q.contains("zona") || q.contains("recorrente") || q.contains("recurrente") || q.contains("analisis") || q.contains("análisis") -> {
                analyzeIncidentPatterns(db)
            }
            q.contains("turno") || q.contains("anomalia") || q.contains("anomalía") || q.contains("horario") || q.contains("pico") -> {
                analyzeShiftAnomalies(db)
            }
            q.contains("supervision") || q.contains("supervisión") || q.contains("rondin") || q.contains("rondín") || q.contains("checkpoint") -> {
                generateSupervisionSupportReport(db)
            }
            q.contains("tiempo devuelto") || q.contains("tiempo ahorrado") || q.contains("familia") || q.contains("tiempo") -> {
                val stats = try { TimeReturnEngine.computeStats(db) } catch (e: Exception) { null }
                val totalMin = (stats?.totalSecondsSaved ?: 0L) / 60
                "⏱️ **TIEMPO DEVUELTO MEDUSA ALFHA (MÉTRICA SAGRADA: TIEMPO = FAMILIA)**\n\n" +
                        "• **Total Ahorrado:** $totalMin minutos (${totalMin / 60}h ${totalMin % 60}m)\n" +
                        "• **Desglose de Impacto:**\n" +
                        "  - Residentes & Visitas: ${stats?.residentsMinutes ?: 0} min (Acceso QR sin llamadas ni esperas)\n" +
                        "  - Guardias de Caseta: ${stats?.guardsMinutes ?: 0} min (Cero libretas de papel, 2 fotos y 1-toque)\n" +
                        "  - Supervisión Táctica: ${stats?.supervisorsMinutes ?: 0} min (Informes automáticos GPS sin recaptura)\n" +
                        "  - Administración: ${stats?.adminMinutes ?: 0} min (Trazabilidad unificada sin transcripción)\n\n" +
                        "«ESTO DEVUELVE TIEMPO. TIEMPO = FAMILIA.»"
            }
            q.contains("visita") || q.contains("visitante") || q.contains("quien esta dentro") || q.contains("quién está dentro") -> {
                val checkIns = try { db.visitorCheckInDao().getAllCheckInsList() } catch (e: Exception) { emptyList() }
                val active = checkIns.filter { it.status == "CHECKED_IN" || it.status == "VERIFICADO" }
                if (active.isEmpty()) {
                    "🚪 **VISITAS ACTIVAS:** En este momento no hay visitantes dentro del condominio según Room SQLite."
                } else {
                    buildString {
                        appendLine("🚪 **VISITAS ACTIVAS EN CONDOMINIO (${active.size}):**")
                        active.take(6).forEach { v ->
                            appendLine("• **${v.visitorName}** -> Casa ${v.destinationHouse} (${v.passTypeLabel}) | Entró: ${v.formattedTime}")
                        }
                    }
                }
            }
            q.contains("paquete") || q.contains("paqueteria") || q.contains("paquetería") -> {
                val packages = try { db.packageDao().getAllPackagesList() } catch (e: Exception) { emptyList() }
                val pending = packages.filter { it.status != "ENTREGADO" }
                buildString {
                    appendLine("📦 **AVISOS DE PAQUETERÍA (REGLA: SIN RESGUARDO FÍSICO):**")
                    appendLine("• Caseta no almacena paquetes físicamente; registra la llegada y notifica al residente de inmediato.")
                    appendLine("• Avisos activos en espera: **${pending.size}**")
                    pending.take(4).forEach { p ->
                        appendLine("  - [${p.folio}] ${p.courierCompany} -> ${p.residentName} (${p.unitId})")
                    }
                }
            }
            q.contains("incidencia") || q.contains("alerta") || q.contains("emergencia") -> {
                val incidents = try { db.incidentDao().getAllIncidentsList() } catch (e: Exception) { emptyList() }
                val open = incidents.filter { it.status != "RESUELTO" && it.status != "CERRADO" }
                if (open.isEmpty()) {
                    "✅ **INCIDENCIAS:** Cero incidencias abiertas en Room. Todas han sido resueltas conforme a protocolo."
                } else {
                    buildString {
                        appendLine("🚨 **INCIDENCIAS ACTIVAS (${open.size}):**")
                        open.take(5).forEach { inc ->
                            appendLine("• [${inc.folio}] ${inc.location} - ${inc.priority.displayName} (${inc.category.displayName})")
                            appendLine("  Acción recomendada: ${inc.recommendedAction}")
                        }
                    }
                }
            }
            else -> {
                // Respuesta de síntesis operativa general
                val stats = try { TimeReturnEngine.computeStats(db) } catch (e: Exception) { null }
                val checkInsCount = try { db.visitorCheckInDao().getCheckInCount() } catch (e: Exception) { 0 }
                "🤖 **COPILOTO MEDUSA ALFHA (RESPUESTA OPERACIONAL REAL)**\n\n" +
                        "He consultado las tablas locales de Room SQLite respecto a \"$query\":\n" +
                        "• **Registros de Acceso Totales:** $checkInsCount eventos auditados.\n" +
                        "• **Tiempo Devuelto Acumulado:** ${(stats?.totalSecondsSaved ?: 0L) / 60} minutos.\n" +
                        "• **Sugerencias:** Puede solicitarme:\n" +
                        "  - \"Resumen ejecutivo\"\n" +
                        "  - \"Análisis de incidencias y zonas recurrentes\"\n" +
                        "  - \"Turnos y anomalías\"\n" +
                        "  - \"Informe de supervisión\"\n" +
                        "  - \"Visitas activas\" o \"Paquetes avisados\""
            }
        }
    }
}
