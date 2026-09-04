package com.example.data.visitor

import com.example.data.core.AlphaCoreEngine
import com.example.scanner.VisitorEntry
import com.example.scanner.VisitorStatus
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ESQUEMA OFICIAL DE FIRESTORE PARA REGISTRO DE VISITANTES (VISITOR LOGS)
 *
 * Ruta en Firestore (Aislamiento Multi-Inquilino):
 *   /condominiums/{condominiumId}/visitor_logs/{folio}
 *
 * Campos Requeridos:
 *   - timestamp: com.google.firebase.Timestamp (Timestamp nativo de Firestore con fecha y hora exacta del ingreso)
 *   - visitorName: String (Nombre completo del visitante autorizado)
 *   - authorizedUnitNumber: String (Número de unidad / departamento / casa autorizada para el acceso)
 *
 * Campos Complementarios de Seguridad y Auditoría:
 *   - destinationHouse: String (Sinónimo/alias para compatibilidad con authorizedUnitNumber)
 *   - unitNumber: String (Número limpio de la unidad residencial)
 *   - condominiumId: String (Identificador de partición de condominio, e.g. "PRADOS_1")
 *   - folio: String (Identificador único e irrepetible del registro de acceso)
 *   - visitorDocument: String (RUT, DNI o Cédula de Identidad)
 *   - passCode: String (Código de pase QR o invitación digital asociada)
 *   - passTypeLabel: String (Tipo de pase: "Visita Ocasional", "Delivery", "Servicio Técnico", etc.)
 *   - vehiclePlate: String? (Placa o patente del vehículo si ingresó motorizado)
 *   - status: String ("CHECKED_IN", "DEPARTED", "VERIFICADO", "DENEGADO")
 *   - guardName: String (Nombre y número de placa del guardia que autorizó el ingreso)
 *   - guardNotes: String? (Observaciones de garita)
 *   - residentNotes: String? (Instrucciones especiales dejadas por el residente)
 *   - hostResidentName: String (Nombre del residente anfitrión que otorgó el permiso)
 *   - checkOutTimestamp: Timestamp? (Timestamp nativo de salida del condominio)
 *   - checkOutMillis: Long? (Milisegundos de salida)
 *   - timestampMillis: Long (Milisegundos para ordenamiento numérico eficiente)
 *   - syncedAtMillis: Long (Hora de sincronización en la nube)
 */
data class FirestoreVisitorLog(
    val folio: String = AlphaCoreEngine.generateUniqueFolio("MED"),
    val visitorName: String,
    val authorizedUnitNumber: String,
    val timestamp: Timestamp = Timestamp.now(),
    val timestampMillis: Long = System.currentTimeMillis(),
    val condominiumId: String = "PRADOS_1",
    val visitorDocument: String = "Sin Documento",
    val passCode: String = "",
    val passTypeLabel: String = "Visita General",
    val vehiclePlate: String? = null,
    val status: String = "CHECKED_IN",
    val guardName: String = "Agente #402 - Garita 1",
    val guardNotes: String? = null,
    val residentNotes: String? = null,
    val hostResidentName: String = "Residente Anfitrión",
    val checkOutTimestamp: Timestamp? = null,
    val checkOutMillis: Long? = null,
    val photoPath: String? = null,
    val syncedAtMillis: Long = System.currentTimeMillis()
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.getDefault()).format(Date(timestampMillis))

    val destinationHouse: String
        get() = authorizedUnitNumber

    /**
     * Serializa la entidad al mapa estructurado de Firestore garantizando
     * el esquema exacto solicitado: timestamp, visitorName, authorizedUnitNumber.
     */
    fun toMap(): Map<String, Any?> = hashMapOf(
        "folio" to folio,
        "visitorName" to visitorName,
        "authorizedUnitNumber" to authorizedUnitNumber,
        "destinationHouse" to authorizedUnitNumber,
        "unitNumber" to authorizedUnitNumber,
        "timestamp" to timestamp,
        "timestampMillis" to timestampMillis,
        "condominiumId" to condominiumId,
        "visitorDocument" to visitorDocument,
        "passCode" to passCode,
        "passTypeLabel" to passTypeLabel,
        "vehiclePlate" to (vehiclePlate ?: ""),
        "status" to status,
        "guardName" to guardName,
        "guardNotes" to (guardNotes ?: ""),
        "residentNotes" to (residentNotes ?: ""),
        "hostResidentName" to hostResidentName,
        "checkOutTimestamp" to checkOutTimestamp,
        "checkOutMillis" to (checkOutMillis ?: 0L),
        "photoPath" to (photoPath ?: ""),
        "syncedAtMillis" to syncedAtMillis
    )

    fun toVisitorCheckIn(): VisitorCheckIn {
        return VisitorCheckIn(
            id = 0,
            folio = folio,
            visitorName = visitorName,
            visitorDocument = visitorDocument,
            destinationHouse = authorizedUnitNumber,
            passCode = passCode,
            passTypeLabel = passTypeLabel,
            vehiclePlate = vehiclePlate,
            status = status,
            timestampMillis = timestampMillis,
            checkOutMillis = checkOutMillis,
            guardNotes = guardNotes,
            guardName = guardName,
            photoPath = photoPath,
            residentNotes = residentNotes,
            hostResidentName = hostResidentName
        )
    }

    companion object {
        /**
         * Parsea un DocumentSnapshot de Firestore al modelo fuertemente tipado FirestoreVisitorLog,
         * extrayendo robustamente timestamp, visitorName y authorizedUnitNumber.
         */
        fun fromDocumentSnapshot(doc: DocumentSnapshot, defaultCondoId: String = "PRADOS_1"): FirestoreVisitorLog? {
            return try {
                val folio = doc.getString("folio") ?: doc.id
                val visitorName = doc.getString("visitorName") ?: doc.getString("name") ?: "Visitante Anónimo"
                val authorizedUnitNumber = doc.getString("authorizedUnitNumber")
                    ?: doc.getString("destinationHouse")
                    ?: doc.getString("unitNumber")
                    ?: "Unidad Desconocida"

                // Extraer timestamp nativo de Firestore o calcular desde timestampMillis
                val fsTimestamp = doc.getTimestamp("timestamp")
                val millis = doc.getLong("timestampMillis")
                    ?: fsTimestamp?.toDate()?.time
                    ?: System.currentTimeMillis()
                val resolvedTimestamp = fsTimestamp ?: Timestamp(Date(millis))

                val fsCheckOutTimestamp = doc.getTimestamp("checkOutTimestamp")
                val checkOutMillis = doc.getLong("checkOutMillis")
                    ?: fsCheckOutTimestamp?.toDate()?.time

                val condoId = doc.getString("condominiumId") ?: defaultCondoId
                val visitorDoc = doc.getString("visitorDocument") ?: "Sin documento"
                val passCode = doc.getString("passCode") ?: ""
                val passType = doc.getString("passTypeLabel") ?: "Visita"
                val plate = doc.getString("vehiclePlate")
                val status = doc.getString("status") ?: "CHECKED_IN"
                val guardName = doc.getString("guardName") ?: "Agente Caseta"
                val guardNotes = doc.getString("guardNotes")
                val residentNotes = doc.getString("residentNotes")
                val hostResidentName = doc.getString("hostResidentName") ?: "Residente"
                val photoPath = doc.getString("photoPath")
                val syncedAtMillis = doc.getLong("syncedAtMillis") ?: System.currentTimeMillis()

                FirestoreVisitorLog(
                    folio = folio,
                    visitorName = visitorName,
                    authorizedUnitNumber = authorizedUnitNumber,
                    timestamp = resolvedTimestamp,
                    timestampMillis = millis,
                    condominiumId = condoId,
                    visitorDocument = visitorDoc,
                    passCode = passCode,
                    passTypeLabel = passType,
                    vehiclePlate = plate,
                    status = status,
                    guardName = guardName,
                    guardNotes = guardNotes,
                    residentNotes = residentNotes,
                    hostResidentName = hostResidentName,
                    checkOutTimestamp = fsCheckOutTimestamp,
                    checkOutMillis = checkOutMillis,
                    photoPath = photoPath,
                    syncedAtMillis = syncedAtMillis
                )
            } catch (e: Exception) {
                null
            }
        }

        fun fromVisitorCheckIn(checkIn: VisitorCheckIn, condominiumId: String): FirestoreVisitorLog {
            val ts = Timestamp(Date(checkIn.timestampMillis))
            val coTs = checkIn.checkOutMillis?.let { Timestamp(Date(it)) }
            return FirestoreVisitorLog(
                folio = checkIn.folio,
                visitorName = checkIn.visitorName,
                authorizedUnitNumber = checkIn.destinationHouse,
                timestamp = ts,
                timestampMillis = checkIn.timestampMillis,
                condominiumId = condominiumId,
                visitorDocument = checkIn.visitorDocument,
                passCode = checkIn.passCode,
                passTypeLabel = checkIn.passTypeLabel,
                vehiclePlate = checkIn.vehiclePlate,
                status = checkIn.status,
                guardName = checkIn.guardName,
                guardNotes = checkIn.guardNotes,
                residentNotes = checkIn.residentNotes,
                hostResidentName = checkIn.hostResidentName,
                checkOutTimestamp = coTs,
                checkOutMillis = checkIn.checkOutMillis,
                photoPath = checkIn.photoPath,
                syncedAtMillis = System.currentTimeMillis()
            )
        }
    }
}
