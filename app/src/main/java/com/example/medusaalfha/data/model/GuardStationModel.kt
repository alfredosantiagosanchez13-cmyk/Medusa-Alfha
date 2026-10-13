package com.example.medusaalfha.data.model

data class GuardAttendanceState(
    val guardId: String = "GRD-8801",
    val guardName: String = "J. Vargas",
    val guardRank: String = "Primer Oficial · Comandante de Garita",
    val avatarUrl: String = "",
    val shiftStartTimestamp: Long = System.currentTimeMillis() - (1000 * 60 * 60 * 7 + 1000 * 60 * 24), // Ej: 7h 24m transcurridos
    val isAwayAtRestroom: Boolean = false,
    val awaySinceTimestamp: Long? = null,
    val logs: List<String> = emptyList(),
    val totalHoursWorked: Double = 7.4
) {
    val isCriticalFatigue: Boolean
        get() {
            val elapsedMillis = System.currentTimeMillis() - shiftStartTimestamp
            return elapsedMillis >= 24 * 60 * 60 * 1000L
        }

    val elapsedFormatted: String
        get() {
            val elapsedSeconds = ((System.currentTimeMillis() - shiftStartTimestamp) / 1000).coerceAtLeast(0)
            val hours = elapsedSeconds / 3600
            val minutes = (elapsedSeconds % 3600) / 60
            return String.format("%02d:%02d", hours, minutes)
        }
}

data class CondominiumHouseInfo(
    val houseId: String,
    val residentName: String,
    val specialConsignments: String? = null,
    val alertLevel: HouseAlertLevel = HouseAlertLevel.NORMAL
)

enum class HouseAlertLevel {
    NORMAL,
    ATTENTION,
    RESTRICTED
}

data class PackageDeliveryItem(
    val id: String,
    val trackingCode: String,
    val destinationHouse: String,
    val carrierName: String,
    val ocrRawDetection: String = "",
    val qrPickupToken: String,
    val registeredAtMillis: Long = System.currentTimeMillis(),
    val isPickedUp: Boolean = false,
    val pickedUpAtMillis: Long? = null
)

data class PatrolLogEntry(
    val id: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val zone: String,
    val reportText: String,
    val isAiAssisted: Boolean = false,
    val isSynced: Boolean = false
)

data class PatrolCheckpoint(
    val id: String,
    val code: String,
    val name: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val toleranceMeters: Double = 15.0,
    val isValidated: Boolean = false,
    val validatedAtMillis: Long? = null,
    val mapXPercent: Float = 0.5f, // Coordenada didáctica en plano normalizado (0f..1f)
    val mapYPercent: Float = 0.5f,
    val isSynced: Boolean = false
)

object CondominiumPatrolMapCatalog {
    fun getInitialCheckpoints(): List<PatrolCheckpoint> = listOf(
        PatrolCheckpoint(
            id = "CP-01",
            code = "CP-NORTE",
            name = "Portón Principal & Garita Alfa",
            description = "Acceso vehicular, esclusa y lector biométrico",
            latitude = 20.6736,
            longitude = -103.3440,
            mapXPercent = 0.18f,
            mapYPercent = 0.22f
        ),
        PatrolCheckpoint(
            id = "CP-02",
            code = "CP-ALBERCA",
            name = "Casa Club & Alberca Central",
            description = "Palapa, bombas de calor y cuarto de máquinas",
            latitude = 20.6741,
            longitude = -103.3432,
            mapXPercent = 0.48f,
            mapYPercent = 0.42f
        ),
        PatrolCheckpoint(
            id = "CP-03",
            code = "CP-PADEL",
            name = "Canchas de Pádel & Perímetro Este",
            description = "Cercado electrificado y luminarias perimetrales",
            latitude = 20.6748,
            longitude = -103.3425,
            mapXPercent = 0.78f,
            mapYPercent = 0.32f
        ),
        PatrolCheckpoint(
            id = "CP-04",
            code = "CP-BOMBAS",
            name = "Cisterna & Subestación Eléctrica Sur",
            description = "Transformador principal y sensores de presión",
            latitude = 20.6729,
            longitude = -103.3435,
            mapXPercent = 0.55f,
            mapYPercent = 0.78f
        ),
        PatrolCheckpoint(
            id = "CP-05",
            code = "CP-OESTE",
            name = "Salida de Emergencia & Barda Poniente",
            description = "Portón de evacuación y cámaras térmicas PTZ",
            latitude = 20.6732,
            longitude = -103.3452,
            mapXPercent = 0.22f,
            mapYPercent = 0.68f
        )
    )
}

object CondominiumDirectory {
    val activeHouses = listOf(
        CondominiumHouseInfo(
            houseId = "Casa 54",
            residentName = "Familia González",
            specialConsignments = "Autorización previa telefónica requerida para técnicos y visitas no recurrentes.",
            alertLevel = HouseAlertLevel.ATTENTION
        ),
        CondominiumHouseInfo(
            houseId = "Casa 12",
            residentName = "Lic. Carlos Mendoza",
            specialConsignments = "Recibir paquetería en caseta y resguardar en casillero blindado.",
            alertLevel = HouseAlertLevel.NORMAL
        ),
        CondominiumHouseInfo(
            houseId = "Casa 88",
            residentName = "Ing. Mariana Torres",
            specialConsignments = "Vehículos de contratistas deben estacionarse exclusivamente en bahía exterior.",
            alertLevel = HouseAlertLevel.NORMAL
        ),
        CondominiumHouseInfo(
            houseId = "Casa 31",
            residentName = "Dr. Alejandro Ramos",
            specialConsignments = "Alerta especial: Reportar cualquier intento de entrega fuera de horario 08:00 - 20:00.",
            alertLevel = HouseAlertLevel.ATTENTION
        ),
        CondominiumHouseInfo(
            houseId = "Casa 102",
            residentName = "Sra. Patricia Vega",
            specialConsignments = "Pase exprés autorizado para repartidores registrados de agua y alimentos.",
            alertLevel = HouseAlertLevel.NORMAL
        ),
        CondominiumHouseInfo(
            houseId = "Casa 07",
            residentName = "Arq. Fernando Morales",
            specialConsignments = null,
            alertLevel = HouseAlertLevel.NORMAL
        )
    )

    fun findHouse(houseId: String): CondominiumHouseInfo? {
        val clean = houseId.lowercase().replace("casa", "").trim()
        return activeHouses.find {
            it.houseId.equals(houseId, ignoreCase = true) ||
            it.houseId.lowercase().replace("casa", "").trim() == clean
        }
    }
}
