package com.example.medusaalfha.data.model

import androidx.annotation.Keep

@Keep
data class Condominium(
    val id: String = "PRADOS_1",
    val name: String = "Prados Residencial · Caseta Principal",
    val address: String = "Av. De los Prados #100, Fracc. Prados",
    val totalUnits: Int = 180,
    val adminContact: String = "Santiago Sánchez · Dir. Gral",
    val gateCount: Int = 2,
    val activeStatus: String = "ACTIVO",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val createdBy: String = "alfredosantiagosanchez13@gmail.com"
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "address" to address,
        "totalUnits" to totalUnits,
        "adminContact" to adminContact,
        "gateCount" to gateCount,
        "activeStatus" to activeStatus,
        "createdAtMillis" to createdAtMillis,
        "createdBy" to createdBy
    )

    companion object {
        fun fromMap(map: Map<String, Any>): Condominium {
            return Condominium(
                id = map["id"] as? String ?: "PRADOS_1",
                name = map["name"] as? String ?: "Prados Residencial",
                address = map["address"] as? String ?: "",
                totalUnits = (map["totalUnits"] as? Number)?.toInt() ?: 180,
                adminContact = map["adminContact"] as? String ?: "Santiago Sánchez",
                gateCount = (map["gateCount"] as? Number)?.toInt() ?: 2,
                activeStatus = map["activeStatus"] as? String ?: "ACTIVO",
                createdAtMillis = (map["createdAtMillis"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                createdBy = map["createdBy"] as? String ?: "alfredosantiagosanchez13@gmail.com"
            )
        }

        val DEFAULT_PRADOS = Condominium(
            id = "PRADOS_1",
            name = "Prados Residencial · Caseta Principal",
            address = "Circuito Prados Norte #100",
            totalUnits = 180,
            adminContact = "Santiago Sánchez (Director General)",
            gateCount = 2,
            activeStatus = "ACTIVO",
            createdAtMillis = 1700000000000L,
            createdBy = "alfredosantiagosanchez13@gmail.com"
        )
    }
}
