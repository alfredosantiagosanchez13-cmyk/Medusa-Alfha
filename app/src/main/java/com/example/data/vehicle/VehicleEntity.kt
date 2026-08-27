package com.example.data.vehicle

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Entidad de Vehículo Registrado en Room SQLite.
 * Fuente Única de Verdad para el Padrón Vehicular de Residentes y Unidades.
 */
@Entity(
    tableName = "vehicles",
    indices = [
        Index(value = ["plate"], unique = true),
        Index(value = ["unitId"]),
        Index(value = ["residentId"]),
        Index(value = ["tagRfid"]),
        Index(value = ["qrAccessCode"]),
        Index(value = ["status"])
    ]
)
data class VehicleEntity(
    @PrimaryKey
    val plate: String, // Placa estandarizada (e.g. "ABC-1234")
    val brand: String, // e.g. "Toyota", "Mazda", "Volkswagen"
    val model: String, // e.g. "RAV4", "3 Sedán", "Jetta"
    val color: String, // e.g. "Gris Metálico", "Blanco", "Negro"
    val vehicleType: String = "SUV", // SEDAN, SUV, PICKUP, HATCHBACK, MOTOCICLETA, VAN, CARGA, OTRO
    val unitId: String, // e.g. "Casa 104", "Torre 1 - Depto 302"
    val residentId: String = "", // e.g. "RES-A104-01"
    val ownerName: String = "", // e.g. "Familia Arismendi"
    val relationship: String = "PROPIETARIO", // PROPIETARIO, ARRENDATARIO, FAMILIAR, VISITA_FRECUENTE, EMPLEADO, PROVEEDOR
    val tagRfid: String = "", // e.g. "TAG-A104-1"
    val qrAccessCode: String = "", // e.g. "QR-VEH-A104-01"
    val status: String = "ACTIVO", // ACTIVO, SUSPENDIDO, NO_AUTORIZADO, BAJA_LOGICA, EN_REVISION
    val isPrimary: Boolean = true,
    val notes: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val updatedBy: String = "ADMINISTRACION"
) {
    val formattedCreatedAt: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(createdAtMillis))

    val displaySummary: String
        get() = "$brand $model ($color) - $plate"

    val isAuthorizedForAutoEntry: Boolean
        get() = status.equals("ACTIVO", ignoreCase = true)

    companion object {
        fun normalizePlate(input: String): String {
            return input.trim().uppercase(Locale.getDefault())
                .replace(" ", "")
        }
    }
}
