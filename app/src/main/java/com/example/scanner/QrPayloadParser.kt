package com.example.scanner

data class ParsedQrPass(
    val passCode: String,
    val guestName: String? = null,
    val destinationHouse: String? = null,
    val hostResidentName: String? = null,
    val vehiclePlate: String? = null,
    val passType: String? = null,
    val residentId: String? = null,
    val assignedUnit: String? = null,
    val createdAtMillis: Long? = null,
    val validUntilMillis: Long? = null,
    val digitalSignature: String? = null,
    val rawPayload: String
)

/**
 * Parser resiliente para códigos QR capturados en caseta de seguridad.
 * Implementado en Kotlin puro para alta velocidad, cero dependencias de framework
 * y total compatibilidad con pruebas unitarias JVM y entornos Android.
 */
object QrPayloadParser {

    private val jsonKeyRegexes = listOf(
        "passCode", "code", "entryCode", "folio", "id", "residentId", "token"
    )

    private fun extractJsonField(json: String, key: String): String? {
        val pattern = Regex("""\"$key\"\s*:\s*\"([^\"]+)\"""", RegexOption.IGNORE_CASE)
        val match = pattern.find(json)
        return match?.groupValues?.getOrNull(1)?.trim()
    }

    private fun extractJsonLongField(json: String, key: String): Long? {
        val pattern = Regex("""\"$key\"\s*:\s*([0-9]+)""", RegexOption.IGNORE_CASE)
        val match = pattern.find(json)
        return match?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    private fun extractQueryParam(url: String, key: String): String? {
        val pattern = Regex("""[?&]$key=([^&#\s]+)""", RegexOption.IGNORE_CASE)
        val match = pattern.find(url)
        return match?.groupValues?.getOrNull(1)?.trim()
    }

    private fun extractPipeParam(raw: String, key: String): String? {
        val parts = raw.split("|")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.startsWith("$key=", ignoreCase = true)) {
                return trimmed.substringAfter("=").trim()
            }
        }
        return null
    }

    /**
     * Extrae el código de pase limpio de cualquier formato admitido:
     * - Pipe-Delimited: "MEDUSA-QR-PASS|folio=MED-20260928-1001|..."
     * - Código directo: "MED-20260904-1001", "MEDUSA-VISITA-PARAISO-01-99", "VIS-...", "RES-..."
     * - JSON: {"passCode": "...", "guestName": "...", "house": "..."}
     * - URI/Deeplink: "https://medusa.app/verify?code=MED-..." o "medusa://access?code=MED-..."
     * - Cadenas entre comillas: "\"MED-2026...\""
     */
    fun extractEntryCode(raw: String): String {
        val trimmed = raw.trim().trim('"', '\'')
        if (trimmed.isBlank()) return ""

        // Formato Pipe-Delimited oficial
        if (trimmed.contains("|") && (trimmed.contains("folio=", ignoreCase = true) || trimmed.contains("passCode=", ignoreCase = true))) {
            val folioVal = extractPipeParam(trimmed, "folio") ?: extractPipeParam(trimmed, "passCode") ?: extractPipeParam(trimmed, "code")
            if (!folioVal.isNullOrBlank()) {
                return folioVal
            }
        }

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

        // Formato Pipe-Delimited oficial de MEDUSA
        if (trimmed.contains("|")) {
            val folio = extractPipeParam(trimmed, "folio") ?: extractedCode
            val guest = extractPipeParam(trimmed, "guest") ?: extractPipeParam(trimmed, "visitor")
            val dest = extractPipeParam(trimmed, "dest") ?: extractPipeParam(trimmed, "house") ?: extractPipeParam(trimmed, "unit")
            val residentId = extractPipeParam(trimmed, "residentId")
            val host = extractPipeParam(trimmed, "host")
            val plate = extractPipeParam(trimmed, "plate")
            val type = extractPipeParam(trimmed, "type") ?: extractPipeParam(trimmed, "passType")
            val created = extractPipeParam(trimmed, "created")?.toLongOrNull()
            val exp = extractPipeParam(trimmed, "exp")?.toLongOrNull()
            val sig = extractPipeParam(trimmed, "sig") ?: extractPipeParam(trimmed, "signature") ?: extractPipeParam(trimmed, "hash")

            return ParsedQrPass(
                passCode = folio,
                guestName = guest,
                destinationHouse = dest,
                hostResidentName = host,
                vehiclePlate = plate,
                passType = type,
                residentId = residentId,
                assignedUnit = dest,
                createdAtMillis = created,
                validUntilMillis = exp,
                digitalSignature = sig,
                rawPayload = raw
            )
        }

        // Formato JSON
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            val typeVal = extractJsonField(trimmed, "type")
            val isResVal = extractJsonField(trimmed, "isResident")
            val roleVal = extractJsonField(trimmed, "role")
            val touchlessVal = extractJsonField(trimmed, "touchless")

            val isResident = typeVal.equals("RESIDENT", ignoreCase = true) ||
                    typeVal.equals("RESIDENTE", ignoreCase = true) ||
                    isResVal.equals("true", ignoreCase = true) ||
                    roleVal.equals("RESIDENT", ignoreCase = true) ||
                    roleVal.equals("RESIDENTE", ignoreCase = true) ||
                    touchlessVal.equals("true", ignoreCase = true) ||
                    extractedCode.startsWith("RES-", ignoreCase = true) ||
                    extractedCode.startsWith("TOUCHLESS-", ignoreCase = true) ||
                    extractedCode.startsWith("MEDUSA-RESIDENT-", ignoreCase = true)

            val guest = if (isResident) {
                extractJsonField(trimmed, "residentName")
                    ?: extractJsonField(trimmed, "guestName")
                    ?: extractJsonField(trimmed, "visitorName")
                    ?: extractJsonField(trimmed, "name")
            } else {
                extractJsonField(trimmed, "guestName")
                    ?: extractJsonField(trimmed, "visitorName")
                    ?: extractJsonField(trimmed, "name")
                    ?: extractJsonField(trimmed, "residentName")
            }
            val destination = extractJsonField(trimmed, "destinationHouse")
                ?: extractJsonField(trimmed, "unitId")
                ?: extractJsonField(trimmed, "unit")
                ?: extractJsonField(trimmed, "house")
                ?: extractJsonField(trimmed, "assignedUnit")
            val host = extractJsonField(trimmed, "hostResidentName")
                ?: if (!isResident) extractJsonField(trimmed, "residentName") else null
                ?: guest
            val plate = extractJsonField(trimmed, "vehiclePlate")
                ?: extractJsonField(trimmed, "plates")
                ?: extractJsonField(trimmed, "plate")
            val type = if (isResident) {
                "RESIDENT_PERMANENT"
            } else {
                extractJsonField(trimmed, "passType") ?: extractJsonField(trimmed, "type")
            }
            val resId = extractJsonField(trimmed, "residentId")
            val created = extractJsonLongField(trimmed, "createdAt") ?: extractJsonLongField(trimmed, "createdAtMillis")
            val exp = extractJsonLongField(trimmed, "validUntil") ?: extractJsonLongField(trimmed, "validUntilMillis")
            val sig = extractJsonField(trimmed, "digitalSignature") ?: extractJsonField(trimmed, "signature") ?: extractJsonField(trimmed, "sig")

            return ParsedQrPass(
                passCode = extractedCode,
                guestName = guest,
                destinationHouse = destination,
                hostResidentName = host,
                vehiclePlate = plate,
                passType = type,
                residentId = resId,
                assignedUnit = destination,
                createdAtMillis = created,
                validUntilMillis = exp,
                digitalSignature = sig,
                rawPayload = raw
            )
        }

        return ParsedQrPass(
            passCode = extractedCode,
            rawPayload = raw
        )
    }
}
