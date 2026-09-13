package com.example.data.visitor

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entidad de persistencia local en Room para los registros de acceso en caseta (MEDUSA ALFHA).
 * Diseñada para el almacenamiento reactivo y trazabilidad offline de entradas y salidas.
 */
@Entity(
    tableName = "visitor_check_ins",
    indices = [
        Index(value = ["condominiumId"]),
        Index(value = ["timestamp"]),
        Index(value = ["folio"], unique = true)
    ]
)
data class VisitorCheckInEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "status")
    val status: String, // ej: "checked_in", "denied", "departed"

    @ColumnInfo(name = "unitNumber")
    val unitNumber: String, // ej: "Casa 01", "Casa 104"

    @ColumnInfo(name = "visitorName")
    val visitorName: String,

    @ColumnInfo(name = "folio")
    val folio: String,

    @ColumnInfo(name = "condominiumId")
    val condominiumId: String
)
