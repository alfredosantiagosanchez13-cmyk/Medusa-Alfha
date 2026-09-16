package com.example.data.visitor

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.core.AlphaCoreEngine
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Modelo de datos en Room (SQLite) para la bitácora de visitantes (Visitor Log).
 *
 * Contiene explícitamente los campos requeridos:
 * - visitorName: Nombre completo del visitante.
 * - arrivalTime: Fecha y hora exacta de llegada (milisegundos).
 * - accessStatus: Estado actual del acceso ("CHECKED_IN", "DEPARTED", "VERIFICADO", "DENEGADO", "PENDIENTE").
 */
@Entity(
    tableName = "visitor_logs",
    indices = [
        Index(value = ["folio"], unique = true),
        Index(value = ["condominiumId"]),
        Index(value = ["arrivalTime"]),
        Index(value = ["accessStatus"])
    ]
)
data class VisitorLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "folio")
    val folio: String = AlphaCoreEngine.generateUniqueFolio("VIS"),

    @ColumnInfo(name = "visitorName")
    val visitorName: String,

    @ColumnInfo(name = "arrivalTime")
    val arrivalTime: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "accessStatus")
    val accessStatus: String = "CHECKED_IN",

    @ColumnInfo(name = "authorizedUnitNumber")
    val authorizedUnitNumber: String = "",

    @ColumnInfo(name = "condominiumId")
    val condominiumId: String = "PRADOS_1",

    @ColumnInfo(name = "visitorDocument")
    val visitorDocument: String = "Sin Documento",

    @ColumnInfo(name = "passCode")
    val passCode: String = "",

    @ColumnInfo(name = "passTypeLabel")
    val passTypeLabel: String = "Visita General",

    @ColumnInfo(name = "vehiclePlate")
    val vehiclePlate: String? = null,

    @ColumnInfo(name = "guardName")
    val guardName: String = "Agente Caseta",

    @ColumnInfo(name = "guardNotes")
    val guardNotes: String? = null,

    @ColumnInfo(name = "departureTime")
    val departureTime: Long? = null,

    @ColumnInfo(name = "hostResidentName")
    val hostResidentName: String = "Residente",

    @ColumnInfo(name = "residentNotes")
    val residentNotes: String? = null,

    @ColumnInfo(name = "photoPath")
    val photoPath: String? = null,

    @ColumnInfo(name = "isSynced")
    val isSynced: Boolean = false,

    @ColumnInfo(name = "lastSyncedAt")
    val lastSyncedAt: Long? = null
) {
    val formattedArrivalTime: String
        get() = SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.getDefault()).format(Date(arrivalTime))

    val formattedDepartureTime: String?
        get() = departureTime?.let { SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.getDefault()).format(Date(it)) }

    fun toFirestoreModel(): FirestoreVisitorLog {
        return FirestoreVisitorLog(
            folio = folio,
            visitorName = visitorName,
            authorizedUnitNumber = authorizedUnitNumber,
            timestamp = Timestamp(Date(arrivalTime)),
            timestampMillis = arrivalTime,
            condominiumId = condominiumId,
            visitorDocument = visitorDocument,
            passCode = passCode,
            passTypeLabel = passTypeLabel,
            vehiclePlate = vehiclePlate,
            status = accessStatus,
            guardName = guardName,
            guardNotes = guardNotes,
            residentNotes = residentNotes,
            hostResidentName = hostResidentName,
            checkOutTimestamp = departureTime?.let { Timestamp(Date(it)) },
            checkOutMillis = departureTime,
            photoPath = photoPath,
            syncedAtMillis = lastSyncedAt ?: System.currentTimeMillis()
        )
    }

    fun toVisitorCheckIn(): VisitorCheckIn {
        return VisitorCheckIn(
            id = id,
            folio = folio,
            visitorName = visitorName,
            visitorDocument = visitorDocument,
            destinationHouse = authorizedUnitNumber,
            passCode = passCode,
            passTypeLabel = passTypeLabel,
            vehiclePlate = vehiclePlate,
            status = accessStatus,
            timestampMillis = arrivalTime,
            checkOutMillis = departureTime,
            guardNotes = guardNotes,
            guardName = guardName,
            photoPath = photoPath,
            residentNotes = residentNotes,
            hostResidentName = hostResidentName
        )
    }

    companion object {
        fun fromVisitorCheckIn(checkIn: VisitorCheckIn, condoId: String = "PRADOS_1"): VisitorLogEntity {
            return VisitorLogEntity(
                id = checkIn.id,
                folio = checkIn.folio,
                visitorName = checkIn.visitorName,
                arrivalTime = checkIn.timestampMillis,
                accessStatus = checkIn.status,
                authorizedUnitNumber = checkIn.destinationHouse,
                condominiumId = condoId,
                visitorDocument = checkIn.visitorDocument,
                passCode = checkIn.passCode,
                passTypeLabel = checkIn.passTypeLabel,
                vehiclePlate = checkIn.vehiclePlate,
                guardName = checkIn.guardName,
                guardNotes = checkIn.guardNotes,
                departureTime = checkIn.checkOutMillis,
                hostResidentName = checkIn.hostResidentName,
                residentNotes = checkIn.residentNotes,
                photoPath = checkIn.photoPath,
                isSynced = true,
                lastSyncedAt = System.currentTimeMillis()
            )
        }

        fun fromFirestoreModel(firestoreLog: FirestoreVisitorLog, localId: Long = 0): VisitorLogEntity {
            return VisitorLogEntity(
                id = localId,
                folio = firestoreLog.folio,
                visitorName = firestoreLog.visitorName,
                arrivalTime = firestoreLog.timestampMillis,
                accessStatus = firestoreLog.status,
                authorizedUnitNumber = firestoreLog.authorizedUnitNumber,
                condominiumId = firestoreLog.condominiumId,
                visitorDocument = firestoreLog.visitorDocument,
                passCode = firestoreLog.passCode,
                passTypeLabel = firestoreLog.passTypeLabel,
                vehiclePlate = firestoreLog.vehiclePlate,
                guardName = firestoreLog.guardName,
                guardNotes = firestoreLog.guardNotes,
                departureTime = firestoreLog.checkOutMillis,
                hostResidentName = firestoreLog.hostResidentName,
                residentNotes = firestoreLog.residentNotes,
                photoPath = firestoreLog.photoPath,
                isSynced = true,
                lastSyncedAt = firestoreLog.syncedAtMillis
            )
        }
    }
}
