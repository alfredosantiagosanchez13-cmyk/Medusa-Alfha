package com.example.medusaalfha.data.scanner

import com.example.medusaalfha.data.model.VisitorEntry
import com.example.medusaalfha.data.model.VisitorStatus

data class ParsedVisitorPass(
    val folio: String,
    val visitorName: String,
    val destinationHouse: String,
    val visitorType: String = "Visita QR",
    val vehiclePlate: String? = null,
    val notes: String? = null,
    val authorizedBy: String = "Pase QR Digital",
    val rawContent: String = ""
) {
    fun toVisitorEntry(status: VisitorStatus = VisitorStatus.DENTRO): VisitorEntry {
        return VisitorEntry(
            folio = folio.ifEmpty { "QR-${System.currentTimeMillis() % 100000}" },
            visitorName = visitorName.ifEmpty { "Visitante QR" },
            visitorType = visitorType,
            destinationHouse = destinationHouse.ifEmpty { "Casa Prados" },
            vehiclePlate = vehiclePlate,
            accessMethod = "Código QR Escaneado",
            status = status,
            checkInTimestamp = System.currentTimeMillis(),
            checkOutTimestamp = if (status == VisitorStatus.SALIDA) System.currentTimeMillis() else null,
            notes = notes ?: "Acceso validado mediante lector QR de caseta",
            authorizedBy = authorizedBy
        )
    }

    companion object {
        /**
         * Parsea contenido de QR en múltiples formatos:
         * 1) URL o Parámetros clave-valor: https://medusa.app/pass?folio=VIS-101&name=Carlos&house=Casa54&plate=ABC12
         * 2) Formato Delimitado por tuberías o comas: FOL-901|Carlos Ruiz|Casa 54|NZA-98-12
         * 3) Formato JSON simple: {"folio":"FOL-101","name":"Carlos","house":"Casa 54"}
         * 4) Texto simple o número de folio: FOL-2026-901
         */
        fun parse(raw: String): ParsedVisitorPass {
            val trimmed = raw.trim()

            // 1. Detección de formato JSON básico
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val folio = extractJsonField(trimmed, "folio") ?: extractJsonField(trimmed, "id") ?: ""
                val name = extractJsonField(trimmed, "visitorName") ?: extractJsonField(trimmed, "name") ?: "Visitante QR"
                val house = extractJsonField(trimmed, "destinationHouse") ?: extractJsonField(trimmed, "house") ?: "Casa Prados"
                val plate = extractJsonField(trimmed, "vehiclePlate") ?: extractJsonField(trimmed, "plate")
                val notes = extractJsonField(trimmed, "notes")
                return ParsedVisitorPass(
                    folio = folio.ifEmpty { "QR-${System.currentTimeMillis() % 100000}" },
                    visitorName = name,
                    destinationHouse = house,
                    vehiclePlate = plate,
                    notes = notes,
                    rawContent = trimmed
                )
            }

            // 2. Detección de URL con query params (ej. ?folio=...&name=...&house=...)
            if (trimmed.contains("?") && trimmed.contains("=")) {
                val queryString = trimmed.substringAfter("?")
                val params = queryString.split("&").mapNotNull {
                    val pair = it.split("=")
                    if (pair.size == 2) pair[0].trim().lowercase() to java.net.URLDecoder.decode(pair[1].trim(), "UTF-8") else null
                }.toMap()

                val folio = params["folio"] ?: params["id"] ?: params["pass"] ?: ""
                val name = params["name"] ?: params["visitor"] ?: "Visitante Registrado"
                val house = params["house"] ?: params["destino"] ?: params["casa"] ?: "Casa Prados"
                val plate = params["plate"] ?: params["placa"]
                val type = params["type"] ?: "Visita QR"

                return ParsedVisitorPass(
                    folio = folio.ifEmpty { "QR-${System.currentTimeMillis() % 100000}" },
                    visitorName = name,
                    destinationHouse = house,
                    visitorType = type,
                    vehiclePlate = plate,
                    rawContent = trimmed
                )
            }

            // 3. Delimitado por '|' o ';' (ej. FOL-202|Roberto Gómez|Casa 54|NZA-88-21|Cita)
            val parts = if (trimmed.contains("|")) trimmed.split("|") else trimmed.split(";")
            if (parts.size >= 2) {
                val folio = parts[0].trim()
                val name = parts.getOrNull(1)?.trim() ?: "Visitante"
                val house = parts.getOrNull(2)?.trim() ?: "Casa Prados"
                val plate = parts.getOrNull(3)?.trim()?.ifEmpty { null }
                val notes = parts.getOrNull(4)?.trim()?.ifEmpty { null }
                return ParsedVisitorPass(
                    folio = folio.ifEmpty { "QR-${System.currentTimeMillis() % 100000}" },
                    visitorName = name,
                    destinationHouse = house,
                    vehiclePlate = plate,
                    notes = notes,
                    rawContent = trimmed
                )
            }

            // 4. Folio plano o identificador único
            return ParsedVisitorPass(
                folio = trimmed,
                visitorName = "Pase QR #$trimmed",
                destinationHouse = "Pendiente de Confirmar",
                rawContent = trimmed
            )
        }

        private fun extractJsonField(json: String, key: String): String? {
            val pattern = Regex("\"$key\"\\s*:\\s*\"([^\"]+)\"")
            return pattern.find(json)?.groupValues?.get(1)
        }
    }
}
