package com.example.data.visitor

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Entidad de Room para el Historial de Accesos y Pases de Visitantes de la Unidad.
 * Almacena el registro cronológico de entradas/salidas vinculadas a la casa o departamento.
 */
@Entity(
    tableName = "unit_visitor_history",
    indices = [
        Index(value = ["destinationHouse"]),
        Index(value = ["timestampMillis"]),
        Index(value = ["status"]),
        Index(value = ["folio"], unique = true)
    ]
)
data class VisitorPassEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String, // ej: "VIS-20260912-104-01"

    @ColumnInfo(name = "destinationHouse")
    val destinationHouse: String, // ej: "Casa 104", "Casa 01"

    @ColumnInfo(name = "visitorName")
    val visitorName: String,

    @ColumnInfo(name = "passType")
    val passType: String, // ej: "Visita Ocasional", "Delivery / Paquetería", "Servicio Técnico", "Invitado Evento", "Frecuente"

    @ColumnInfo(name = "status")
    val status: String, // "VERIFICADO", "EXPIRADO", "DENEGADO", "EN_CURSO"

    @ColumnInfo(name = "timestampMillis")
    val timestampMillis: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "exitTimestampMillis")
    val exitTimestampMillis: Long? = null,

    @ColumnInfo(name = "vehiclePlate")
    val vehiclePlate: String? = null,

    @ColumnInfo(name = "folio")
    val folio: String,

    @ColumnInfo(name = "accessPoint")
    val accessPoint: String = "Garita Principal",

    @ColumnInfo(name = "guardNotes")
    val guardNotes: String? = null
) {
    val formattedDate: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestampMillis))

    val formattedExitDate: String?
        get() = exitTimestampMillis?.let {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
        }

    val isVerified: Boolean
        get() = status.equals("VERIFICADO", ignoreCase = true) || status.equals("CHECKED_IN", ignoreCase = true)

    val isExpired: Boolean
        get() = status.equals("EXPIRADO", ignoreCase = true) || status.equals("DEPARTED", ignoreCase = true)

    val isDenied: Boolean
        get() = status.equals("DENEGADO", ignoreCase = true)
}
