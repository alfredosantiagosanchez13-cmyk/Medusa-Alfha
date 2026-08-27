package com.example.data.resident

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad de Unidad Residencial / Lote / Departamento en Room SQLite.
 */
@Entity(
    tableName = "residential_units",
    indices = [
        Index(value = ["blockOrTower"]),
        Index(value = ["status"])
    ]
)
data class UnitEntity(
    @PrimaryKey
    val unitId: String, // e.g. "Casa 104", "Torre 1 - Depto 302"
    val blockOrTower: String = "Principal", // Manzana A, Torre 1, Sección Norte
    val unitNumber: String = "", // 104, 302
    val status: String = "HABITADA", // HABITADA, DESOCUPADA, EN_MUDANZA, EN_OBRA, SUSPENDIDA
    val intercomCode: String = "", // Código de conmutador / interfón
    val parkingSpots: String = "", // e.g. "E-104A, E-104B"
    val notes: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)
