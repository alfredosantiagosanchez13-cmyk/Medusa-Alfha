package com.example.medusaalfha.data.model

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modelo de datos oficial para los registros de ingreso de visitantes en Cloud Firestore.
 * Colección canónica: `/condominiums/{condominiumId}/visitor_logs/{folio}`
 */
data class VisitorEntry(
    val folio: String = "",
    val visitorName: String = "",
    val destinationHouse: String = "",
    val visitorType: String = "VISITANTE", // VISITANTE, PROVEEDOR, PAQUETERÍA, FAMILIAR, SERVICIOS
    val checkInTimestamp: Long = System.currentTimeMillis(),
    val checkOutTimestamp: Long? = null,
    val status: VisitorStatus = VisitorStatus.DENTRO,
    val vehiclePlate: String? = null,
    val authorizedBy: String = "",
    val guardName: String = "Oficial Juan Pérez · Garita 1",
    val accessMethod: String = "QR PASS DIGITAL",
    val notes: String = "",
    val condominiumId: String = "PRADOS_1"
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            return sdf.format(Date(checkInTimestamp))
        }

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("dd MMM yyyy", Locale("es", "MX"))
            return sdf.format(Date(checkInTimestamp))
        }

    val relativeTimeAgo: String
        get() {
            val diffMs = System.currentTimeMillis() - checkInTimestamp
            if (diffMs < 0) return "Justo ahora"
            val diffSec = diffMs / 1000
            val diffMin = diffSec / 60
            val diffHours = diffMin / 60
            val diffDays = diffHours / 24

            return when {
                diffMin < 1 -> "Justo ahora"
                diffMin < 60 -> "Hace $diffMin min"
                diffHours < 24 -> "Hace $diffHours h"
                diffDays == 1L -> "Ayer"
                else -> "Hace $diffDays días"
            }
        }

    fun toMap(): Map<String, Any?> = hashMapOf(
        "folio" to folio,
        "visitorName" to visitorName,
        "destinationHouse" to destinationHouse,
        "visitorType" to visitorType,
        "checkInTimestamp" to Timestamp(Date(checkInTimestamp)),
        "checkOutTimestamp" to checkOutTimestamp?.let { Timestamp(Date(it)) },
        "status" to status.name,
        "vehiclePlate" to (vehiclePlate ?: ""),
        "authorizedBy" to authorizedBy,
        "guardName" to guardName,
        "accessMethod" to accessMethod,
        "notes" to notes,
        "condominiumId" to condominiumId
    )

    companion object {
        fun fromMap(data: Map<String, Any?>, docId: String = ""): VisitorEntry {
            val folioVal = data["folio"] as? String ?: docId
            val nameVal = data["visitorName"] as? String ?: data["name"] as? String ?: "Visitante"
            val houseVal = data["destinationHouse"] as? String ?: data["houseNumber"] as? String ?: "Casa 54"
            val typeVal = data["visitorType"] as? String ?: data["type"] as? String ?: "VISITANTE"

            val checkInTs: Long = when (val raw = data["checkInTimestamp"]) {
                is Timestamp -> raw.toDate().time
                is Long -> raw
                is Double -> raw.toLong()
                else -> System.currentTimeMillis()
            }

            val checkOutTs: Long? = when (val raw = data["checkOutTimestamp"]) {
                is Timestamp -> raw.toDate().time
                is Long -> raw
                is Double -> raw.toLong()
                else -> null
            }

            val statusStr = data["status"] as? String ?: "DENTRO"
            val statusEnum = VisitorStatus.fromString(statusStr)

            val plateVal = data["vehiclePlate"] as? String ?: data["plate"] as? String
            val authByVal = data["authorizedBy"] as? String ?: "Residente"
            val guardVal = data["guardName"] as? String ?: "Caseta Principal"
            val methodVal = data["accessMethod"] as? String ?: "QR PASS"
            val notesVal = data["notes"] as? String ?: ""
            val condoVal = data["condominiumId"] as? String ?: "PRADOS_1"

            return VisitorEntry(
                folio = folioVal,
                visitorName = nameVal,
                destinationHouse = houseVal,
                visitorType = typeVal,
                checkInTimestamp = checkInTs,
                checkOutTimestamp = checkOutTs,
                status = statusEnum,
                vehiclePlate = plateVal?.ifBlank { null },
                authorizedBy = authByVal,
                guardName = guardVal,
                accessMethod = methodVal,
                notes = notesVal,
                condominiumId = condoVal
            )
        }
    }
}

enum class VisitorStatus(val label: String) {
    DENTRO("En Condominio"),
    SALIDA("Salida Registrada"),
    AUTORIZADO("Pase Aprobado"),
    DENEGADO("Acceso Denegado");

    companion object {
        fun fromString(value: String): VisitorStatus {
            return when (value.uppercase()) {
                "DENTRO", "ACTIVO", "INGRESADO", "EN_SITIO" -> DENTRO
                "SALIDA", "COMPLETADO", "FINALIZADO" -> SALIDA
                "AUTORIZADO", "VALIDO", "APROBADO" -> AUTORIZADO
                "DENEGADO", "RECHAZADO", "BLOQUEADO" -> DENEGADO
                else -> DENTRO
            }
        }
    }
}
