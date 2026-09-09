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

/**
 * Modelo de datos para Notificación de Emergencia / Pánico enviada vía Firebase Cloud Messaging (FCM)
 * desde el dispositivo del residente hacia el personal de seguridad (Guardias de Caseta, Supervisores Tácticos).
 * Incluye número de unidad del residente, ubicación GPS, tipo de emergencia y sellado de tiempo.
 */
data class EmergencyAlertFcmPayload(
    val type: String = "RESIDENT_EMERGENCY_ALERT",
    val event: String = "PANIC_SOS",
    val alertFolio: String = "",
    val residentId: String = "",
    val residentName: String = "",
    val residentUnit: String = "",
    val condominiumId: String = "Los Prados Residencial",
    val emergencyType: String = "PÁNICO S.O.S.",
    val details: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val gpsAccuracyMeters: Float? = null,
    val locationStatus: String = "GPS_CAPTURADO",
    val locationName: String = "",
    val mapsUrl: String = "",
    val timestampMillis: Long = System.currentTimeMillis(),
    val priority: String = "CRITICA",
    val status: String = "ACTIVA",
    val title: String = "🚨 ¡ALERTA DE EMERGENCIA - UNIDAD %s! 🚨",
    val body: String = "",
    val requiresAck: Boolean = true
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "type" to type,
            "event" to event,
            "alertFolio" to alertFolio,
            "residentId" to residentId,
            "residentName" to residentName,
            "residentUnit" to residentUnit,
            "condominiumId" to condominiumId,
            "emergencyType" to emergencyType,
            "details" to details,
            "latitude" to (latitude ?: 0.0),
            "longitude" to (longitude ?: 0.0),
            "gpsAccuracyMeters" to (gpsAccuracyMeters ?: 0f),
            "locationStatus" to locationStatus,
            "locationName" to locationName,
            "mapsUrl" to mapsUrl,
            "timestampMillis" to timestampMillis,
            "priority" to priority,
            "status" to status,
            "title" to title,
            "body" to body,
            "requiresAck" to requiresAck
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any?>): EmergencyAlertFcmPayload {
            val latNum = map["latitude"] as? Number
            val lonNum = map["longitude"] as? Number
            val accNum = map["gpsAccuracyMeters"] as? Number
            val timeNum = map["timestampMillis"] as? Number
            val unit = map["residentUnit"] as? String ?: ""

            return EmergencyAlertFcmPayload(
                type = map["type"] as? String ?: "RESIDENT_EMERGENCY_ALERT",
                event = map["event"] as? String ?: "PANIC_SOS",
                alertFolio = map["alertFolio"] as? String ?: "",
                residentId = map["residentId"] as? String ?: "",
                residentName = map["residentName"] as? String ?: "Residente",
                residentUnit = unit,
                condominiumId = map["condominiumId"] as? String ?: "Los Prados Residencial",
                emergencyType = map["emergencyType"] as? String ?: "PÁNICO S.O.S.",
                details = map["details"] as? String ?: "",
                latitude = latNum?.toDouble()?.takeIf { it != 0.0 },
                longitude = lonNum?.toDouble()?.takeIf { it != 0.0 },
                gpsAccuracyMeters = accNum?.toFloat()?.takeIf { it != 0f },
                locationStatus = map["locationStatus"] as? String ?: "GPS_CAPTURADO",
                locationName = map["locationName"] as? String ?: unit,
                mapsUrl = map["mapsUrl"] as? String ?: "",
                timestampMillis = timeNum?.toLong() ?: System.currentTimeMillis(),
                priority = map["priority"] as? String ?: "CRITICA",
                status = map["status"] as? String ?: "ACTIVA",
                title = map["title"] as? String ?: "🚨 ¡ALERTA DE EMERGENCIA - UNIDAD $unit! 🚨",
                body = map["body"] as? String ?: "",
                requiresAck = map["requiresAck"] as? Boolean ?: true
            )
        }
    }
}

