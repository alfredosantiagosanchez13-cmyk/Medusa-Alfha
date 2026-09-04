package com.example.scanner

import android.net.Uri
import org.json.JSONObject

data class ParsedQrPass(
    val passCode: String,
    val guestName: String? = null,
    val destinationHouse: String? = null,
    val hostResidentName: String? = null,
    val vehiclePlate: String? = null,
    val passType: String? = null,
    val rawPayload: String
)

/**
 * Parser resiliente para códigos QR capturados en caseta de seguridad.
 * Decodifica códigos directos, formatos JSON estructurados, URLs o Deeplinks.
 */
object QrPayloadParser {

    /**
     * Extrae el código de pase limpio de cualquier formato admitido:
     * - Código directo: "MED-20260904-1001", "MEDUSA-VISITA-PARAISO-01-99", "VIS-...", "RES-..."
     * - JSON: {"passCode": "...", "guestName": "...", "house": "..."}
     * - URI/Deeplink: "https://medusa.app/verify?code=MED-..." o "medusa://access?code=MED-..."
     * - Cadenas entre comillas: "\"MED-2026...\""
     */
    fun extractEntryCode(raw: String): String {
        val trimmed = raw.trim().trim('"', '\'')
        if (trimmed.isBlank()) return ""

        // Formato JSON
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val json = JSONObject(trimmed)
                val code = json.optString("passCode").takeIf { it.isNotBlank() }
                    ?: json.optString("code").takeIf { it.isNotBlank() }
                    ?: json.optString("entryCode").takeIf { it.isNotBlank() }
                    ?: json.optString("folio").takeIf { it.isNotBlank() }
                    ?: json.optString("id").takeIf { it.isNotBlank() }
                if (!code.isNullOrBlank()) return code.trim().trim('"', '\'')
            } catch (_: Exception) {}
        }

        // Formato URL / Deeplink con query param
        if (trimmed.contains("?code=") || trimmed.contains("&code=") ||
            trimmed.contains("?passCode=") || trimmed.contains("&passCode=") ||
            trimmed.contains("?entryCode=") || trimmed.contains("&entryCode=")
        ) {
            try {
                val uri = Uri.parse(trimmed)
                val code = uri.getQueryParameter("code")
                    ?: uri.getQueryParameter("passCode")
                    ?: uri.getQueryParameter("entryCode")
                    ?: uri.getQueryParameter("folio")
                if (!code.isNullOrBlank()) return code.trim()
            } catch (_: Exception) {}
        }

        // Si es una URL con path terminado en el código (ej: https://app.com/pass/MED-1234)
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            val lastSegment = trimmed.substringAfterLast("/").substringBefore("?")
            if (lastSegment.startsWith("MED-") || lastSegment.startsWith("VIS-") || lastSegment.startsWith("RES-")) {
                return lastSegment.trim()
            }
        }

        return trimmed
    }

    /**
     * Parsea un payload QR completo si contiene metadatos estructurados.
     */
    fun parse(raw: String): ParsedQrPass {
        val trimmed = raw.trim()
        val extractedCode = extractEntryCode(trimmed)

        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                val json = JSONObject(trimmed)
                return ParsedQrPass(
                    passCode = extractedCode,
                    guestName = json.optString("guestName").takeIf { it.isNotBlank() }
                        ?: json.optString("visitorName").takeIf { it.isNotBlank() },
                    destinationHouse = json.optString("destinationHouse").takeIf { it.isNotBlank() }
                        ?: json.optString("unitId").takeIf { it.isNotBlank() }
                        ?: json.optString("house").takeIf { it.isNotBlank() },
                    hostResidentName = json.optString("hostResidentName").takeIf { it.isNotBlank() }
                        ?: json.optString("residentName").takeIf { it.isNotBlank() },
                    vehiclePlate = json.optString("vehiclePlate").takeIf { it.isNotBlank() }
                        ?: json.optString("plate").takeIf { it.isNotBlank() },
                    passType = json.optString("passType").takeIf { it.isNotBlank() },
                    rawPayload = raw
                )
            } catch (_: Exception) {}
        }

        return ParsedQrPass(
            passCode = extractedCode,
            rawPayload = raw
        )
    }
}
