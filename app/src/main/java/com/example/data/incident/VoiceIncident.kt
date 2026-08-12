package com.example.data.incident

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.SuccessGreen

enum class IncidentCategory(val displayName: String, val iconName: String) {
    PARKING_VIALIDAD("Estacionamiento y Vialidad", "🚗"),
    RUIDO_CONVIVENCIA("Ruidos Molestos / Convivencia", "🔊"),
    INFRAESTRUCTURA("Infraestructura y Servicios", "🛠️"),
    CONTROL_ACCESO("Control de Accesos / Garita", "🛂"),
    SEGURIDAD_EMERGENCIA("Seguridad y Emergencia", "🚨"),
    GENERAL("General / Sin Clasificar", "📋")
}

enum class IncidentPriority(val displayName: String, val badgeColorHex: Long) {
    CRITICA("CRÍTICA", 0xFFEF4444),
    ALTA("ALTA", 0xFFF97316),
    MEDIA("MEDIA", 0xFFEAB308),
    BAJA("BAJA", 0xFF10B981)
}

data class VoiceIncident(
    val id: String = java.util.UUID.randomUUID().toString(),
    val rawTranscript: String,
    val category: IncidentCategory,
    val priority: IncidentPriority,
    val location: String,
    val aiSummary: String,
    val recommendedAction: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val guardName: String = "Guardia de turno (Garita 1)",
    val status: String = "REGISTRADO"
)

object VoiceIncidentCategorizer {

    /**
     * Analyzes raw voice transcription text and automatically categorizes the incident,
     * assigning priority level, location, summary, and AI recommended action.
     */
    fun analyzeAndCategorize(transcript: String): VoiceIncident {
        val text = transcript.lowercase()

        val category = when {
            text.contains("vehiculo") || text.contains("vehículo") || text.contains("auto") ||
                    text.contains("patente") || text.contains("estacion") || text.contains("porton") ||
                    text.contains("portón") || text.contains("estacionado") || text.contains("bloquea") ->
                IncidentCategory.PARKING_VIALIDAD

            text.contains("ruido") || text.contains("musica") || text.contains("música") ||
                    text.contains("fiesta") || text.contains("gritos") || text.contains("departamento") ||
                    text.contains("depto") || text.contains("volumen") ->
                IncidentCategory.RUIDO_CONVIVENCIA

            text.contains("fuga") || text.contains("agua") || text.contains("luz") ||
                    text.contains("ascensor") || text.contains("porton roto") || text.contains("foco") ||
                    text.contains("iluminacion") || text.contains("iluminación") || text.contains("subterraneo") ||
                    text.contains("subterráneo") ->
                IncidentCategory.INFRAESTRUCTURA

            text.contains("sospechoso") || text.contains("pánico") || text.contains("panico") ||
                    text.contains("robo") || text.contains("humo") || text.contains("fuego") ||
                    text.contains("emergencia") || text.contains("intruso") ->
                IncidentCategory.SEGURIDAD_EMERGENCIA

            text.contains("visita") || text.contains("rut") || text.contains("pase") ||
                    text.contains("qr") || text.contains("ingreso") ->
                IncidentCategory.CONTROL_ACCESO

            else -> IncidentCategory.GENERAL
        }

        val priority = when {
            text.contains("urgente") || text.contains("emergencia") || text.contains("fuego") ||
                    text.contains("humo") || text.contains("panico") || text.contains("pánico") ||
                    text.contains("intruso") || text.contains("bloquea emergencia") ->
                IncidentPriority.CRITICA

            text.contains("sospechoso") || text.contains("bloqueado") || text.contains("ruido") ||
                    text.contains("fiesta") || text.contains("ascensor detenido") ->
                IncidentPriority.ALTA

            text.contains("foco") || text.contains("porton lento") || text.contains("visita") ->
                IncidentPriority.MEDIA

            else -> IncidentPriority.BAJA
        }

        val location = extractLocation(transcript)
        val aiSummary = generateAiSummary(transcript, category)
        val recommendedAction = generateRecommendedAction(category, priority, location)

        return VoiceIncident(
            rawTranscript = transcript,
            category = category,
            priority = priority,
            location = location,
            aiSummary = aiSummary,
            recommendedAction = recommendedAction
        )
    }

    private fun extractLocation(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("porton principal") || lower.contains("portón principal") -> "Portón Principal"
            lower.contains("subterraneo") || lower.contains("subterráneo") -> "Estacionamiento Subterráneo"
            lower.contains("torre a") -> "Torre A"
            lower.contains("torre b") -> "Torre B"
            lower.contains("depto") || lower.contains("departamento") -> {
                val match = Regex("(depto|departamento)\\s*(\\d+)").find(lower)
                if (match != null) "Depto ${match.groupValues[2]}" else "Sector Departamentos"
            }
            lower.contains("piscina") -> "Área Piscina / Quincho"
            lower.contains("garita") -> "Garita de Seguridad"
            else -> "Copropiedad / Perímetro"
        }
    }

    private fun generateAiSummary(text: String, category: IncidentCategory): String {
        return "Incidencia registrada vía voz hands-free (${category.displayName}). Transcripción procesada y categorizada por IA."
    }

    private fun generateRecommendedAction(category: IncidentCategory, priority: IncidentPriority, location: String): String {
        return when (category) {
            IncidentCategory.PARKING_VIALIDAD -> "Notificar a propietario de vehículo vía app o solicitar despeje en $location."
            IncidentCategory.RUIDO_CONVIVENCIA -> "Efectuar llamada reglamentaria a $location. Registrar en bitácora de citaciones si persiste."
            IncidentCategory.INFRAESTRUCTURA -> "Generar ticket de servicio de mantenimiento urgente para $location."
            IncidentCategory.SEGURIDAD_EMERGENCIA -> "🚨 Despachar patrulla de ronda inmediata a $location y verificar cámaras CCTV."
            IncidentCategory.CONTROL_ACCESO -> "Verificar credencial en consola de garita y registrar RUT manualmente."
            IncidentCategory.GENERAL -> "Mantener en seguimiento durante el turno de garita."
        }
    }
}
