package com.example.data.fcm

/**
 * Modelo de datos para Notificación en Tiempo Real vía FCM cuando
 * el personal de seguridad escanea exitosamente el código QR de una visita.
 */
data class VisitorCheckInFcmPayload(
    val type: String = "VISITOR_QR_SCANNED",
    val event: String = "VISITOR_CHECK_IN",
    val passFolio: String = "",
    val passCode: String = "",
    val guestName: String = "",
    val guestDocument: String = "",
    val targetUnitId: String = "",
    val hostResidentName: String = "",
    val passTypeLabel: String = "Visita General",
    val vehiclePlate: String? = null,
    val guardName: String = "Guardia de Garita",
    val gateLocation: String = "Garita Principal",
    val guardNotes: String = "Acceso verificado por personal de seguridad",
    val scanTimestamp: Long = System.currentTimeMillis(),
    val condominiumId: String = "Los Prados Residencial",
    val title: String = "🔔 ¡Tu Visita ha Ingresado!",
    val body: String = "",
    val requiresAck: Boolean = false
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "type" to type,
            "event" to event,
            "passFolio" to passFolio,
            "passCode" to passCode,
            "guestName" to guestName,
            "guestDocument" to guestDocument,
            "targetUnitId" to targetUnitId,
            "hostResidentName" to hostResidentName,
            "passTypeLabel" to passTypeLabel,
            "vehiclePlate" to (vehiclePlate ?: ""),
            "guardName" to guardName,
            "gateLocation" to gateLocation,
            "guardNotes" to guardNotes,
            "scanTimestamp" to scanTimestamp,
            "condominiumId" to condominiumId,
            "title" to title,
            "body" to body,
            "requiresAck" to requiresAck
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): VisitorCheckInFcmPayload {
            return VisitorCheckInFcmPayload(
                type = map["type"] as? String ?: "VISITOR_QR_SCANNED",
                event = map["event"] as? String ?: "VISITOR_CHECK_IN",
                passFolio = map["passFolio"] as? String ?: "",
                passCode = map["passCode"] as? String ?: "",
                guestName = map["guestName"] as? String ?: "",
                guestDocument = map["guestDocument"] as? String ?: "",
                targetUnitId = map["targetUnitId"] as? String ?: "",
                hostResidentName = map["hostResidentName"] as? String ?: "",
                passTypeLabel = map["passTypeLabel"] as? String ?: "Visita General",
                vehiclePlate = (map["vehiclePlate"] as? String)?.takeIf { it.isNotBlank() },
                guardName = map["guardName"] as? String ?: "Guardia de Garita",
                gateLocation = map["gateLocation"] as? String ?: "Garita Principal",
                guardNotes = map["guardNotes"] as? String ?: "",
                scanTimestamp = (map["scanTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                condominiumId = map["condominiumId"] as? String ?: "Los Prados Residencial",
                title = map["title"] as? String ?: "🔔 ¡Tu Visita ha Ingresado!",
                body = map["body"] as? String ?: "",
                requiresAck = map["requiresAck"] as? Boolean ?: false
            )
        }
    }
}

/**
 * Registro de token FCM del dispositivo del residente
 */
data class FcmDeviceRegistration(
    val token: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val residentName: String = "",
    val unitId: String = "",
    val condominiumId: String = "",
    val role: String = "RESIDENTE",
    val deviceModel: String = "",
    val appVersion: String = "1.0",
    val subscribedTopics: List<String> = emptyList(),
    val registeredAtMillis: Long = System.currentTimeMillis(),
    val lastActiveMillis: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "token" to token,
            "userId" to userId,
            "userEmail" to userEmail,
            "residentName" to residentName,
            "unitId" to unitId,
            "condominiumId" to condominiumId,
            "role" to role,
            "deviceModel" to deviceModel,
            "appVersion" to appVersion,
            "subscribedTopics" to subscribedTopics,
            "registeredAtMillis" to registeredAtMillis,
            "lastActiveMillis" to lastActiveMillis
        )
    }
}
