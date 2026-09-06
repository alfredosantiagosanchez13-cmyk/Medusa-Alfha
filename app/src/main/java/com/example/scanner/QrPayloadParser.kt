package com.example.scanner

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
 * Implementado en Kotlin puro para alta velocidad, cero dependencias de framework
 * y total compatibilidad con pruebas unitarias JVM y entornos Android.
 */
object QrPayloadParser {

    private val jsonKeyRegexes = listOf(
        "passCode", "code", "entryCode", "folio", "id"
    )

    private fun extractJsonField(json: String, key: String): String? {
        val pattern = Regex("""\"$key\"\s*:\s*\"([^\"]+)\"""", RegexOption.IGNORE_CASE)
        val match = pattern.find(json)
        return match?.groupValues?.getOrNull(1)?.trim()
    }

    private fun extractQueryParam(url: String, key: String): String? {
        val pattern = Regex("""[?&]$key=([^&#\s]+)""", RegexOption.IGNORE_CASE)
        val match = pattern.find(url)
        return match?.groupValues?.getOrNull(1)?.trim()
    }

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
            for (key in jsonKeyRegexes) {
                val value = extractJsonField(trimmed, key)
                if (!value.isNullOrBlank()) {
                    return value.trim().trim('"', '\'')
                }
            }
        }

        // Formato URL / Deeplink con query param
        if (trimmed.contains("?") || trimmed.contains("&")) {
            for (param in listOf("code", "passCode", "entryCode", "folio", "id")) {
                val value = extractQueryParam(trimmed, param)
                if (!value.isNullOrBlank()) {
                    return value.trim()
                }
            }
        }

        // Si es una URL con path terminado en el código (ej: https://app.com/pass/MED-1234)
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            val lastSegment = trimmed.substringAfterLast("/").substringBefore("?").substringBefore("#")
            if (lastSegment.startsWith("MED-", ignoreCase = true) ||
                lastSegment.startsWith("VIS-", ignoreCase = true) ||
                lastSegment.startsWith("RES-", ignoreCase = true)
            ) {
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
            val guest = extractJsonField(trimmed, "guestName")
                ?: extractJsonField(trimmed, "visitorName")
            val destination = extractJsonField(trimmed, "destinationHouse")
                ?: extractJsonField(trimmed, "unitId")
                ?: extractJsonField(trimmed, "house")
            val host = extractJsonField(trimmed, "hostResidentName")
                ?: extractJsonField(trimmed, "residentName")
            val plate = extractJsonField(trimmed, "vehiclePlate")
                ?: extractJsonField(trimmed, "plate")
            val type = extractJsonField(trimmed, "passType")

            return ParsedQrPass(
                passCode = extractedCode,
                guestName = guest,
                destinationHouse = destination,
                hostResidentName = host,
                vehiclePlate = plate,
                passType = type,
                rawPayload = raw
            )
        }

        return ParsedQrPass(
            passCode = extractedCode,
            rawPayload = raw
        )
    }
}
