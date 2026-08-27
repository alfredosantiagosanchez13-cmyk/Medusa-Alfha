package com.example.data.vehicle

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.core.AlphaCoreEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro Inmutable de Movimientos de Entrada y Salida Vehicular en Room SQLite.
 */
@Entity(
    tableName = "vehicle_access_logs",
    indices = [
        Index(value = ["folio"], unique = true),
        Index(value = ["plate"]),
        Index(value = ["unitId"]),
        Index(value = ["entryTimestampMillis"]),
        Index(value = ["status"]),
        Index(value = ["accessCategory"]),
        Index(value = ["isAuthorized"])
    ]
)
data class VehicleAccessLogEntity(
    @PrimaryKey
    val folio: String, // e.g. "FOL-VEH-YYYYMMDD-XXXX"
    val plate: String,
    val brand: String = "",
    val model: String = "",
    val color: String = "",
    val vehicleType: String = "SEDAN",
    val unitId: String = "", // e.g. "Casa 104"
    val driverOrOwnerName: String = "",
    val accessCategory: String = "RESIDENTE_AUTORIZADO", // RESIDENTE_AUTORIZADO, VISITANTE_PASE_QR, VISITA_FRECUENTE, PROVEEDOR_AUTORIZADO, VEHICULO_NO_AUTORIZADO, ACCESO_MANUAL_EMERGENCIA
    val identificationMethod: String = "TAG_RFID", // TAG_RFID, QR_RESIDENTE, PASE_QR_VISITA, RECONOCIMIENTO_PLACA_OCR, MANUAL_CASETA
    val gateLane: String = "CARRIL_RESIDENTES_1", // CARRIL_RESIDENTES_1, CARRIL_VISITAS_2, CARRIL_SALIDA_1, ACCESO_PRINCIPAL
    val direction: String = "ENTRADA", // ENTRADA, SALIDA
    val status: String = "DENTRO_DEL_CONDOMINIO", // DENTRO_DEL_CONDOMINIO, SALIDA_REGISTRADA, ACCESO_DENEGADO_BLOQUEADO
    val entryTimestampMillis: Long = System.currentTimeMillis(),
    val exitTimestampMillis: Long? = null,
    val isAuthorized: Boolean = true,
    val operatorName: String = "Oficial de Guardia",
    val operatorRole: String = "CASETA_VIGILANCIA",
    val guardNotes: String = "",
    val alertFolio: String? = null,
    val hashIntegrity: String = ""
) {
    val formattedEntryTime: String
        get() = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(entryTimestampMillis))

    val formattedExitTime: String?
        get() = exitTimestampMillis?.let {
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(it))
        }

    val stayDurationFormatted: String
        get() = if (exitTimestampMillis != null) {
            AlphaCoreEngine.calculateDurationFormatted(entryTimestampMillis, exitTimestampMillis)
        } else {
            val elapsed = System.currentTimeMillis() - entryTimestampMillis
            val mins = elapsed / (60 * 1000)
            if (mins >= 60) {
                "${mins / 60}h ${mins % 60}m (En sitio)"
            } else {
                "$mins min (En sitio)"
            }
        }

    val isCurrentlyInside: Boolean
        get() = status == "DENTRO_DEL_CONDOMINIO"
}
