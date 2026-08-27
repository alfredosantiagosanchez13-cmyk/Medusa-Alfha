package com.example.data.vehicle

import android.content.Context
import android.util.Log
import com.example.auth.AlfhaSecurityContext
import com.example.data.alerts.OperationalAlertEntity
import com.example.data.audit.AuditLogEntity
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.notifications.NotificationCategory
import com.example.data.notifications.NotificationPriority
import com.example.data.notifications.SmartNotificationHub
import com.example.data.passes.QrPassRoomEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

sealed class VehicleValidationResult {
    data class AuthorizedResident(
        val vehicle: VehicleEntity,
        val message: String = "Acceso Autorizado - Residente Activo"
    ) : VehicleValidationResult()

    data class AuthorizedVisitorPass(
        val qrPass: QrPassRoomEntity,
        val plate: String,
        val message: String = "Acceso Autorizado - Pase QR Vigente"
    ) : VehicleValidationResult()

    data class DeniedSuspended(
        val vehicle: VehicleEntity,
        val reason: String = "Vehículo o Residente con Estatus SUSPENDIDO / RESTRICCIÓN"
    ) : VehicleValidationResult()

    data class Unauthorized(
        val plate: String,
        val reason: String = "Vehículo NO Registrado y Sin Pase QR Vigente"
    ) : VehicleValidationResult()
}

/**
 * MOTOR INTELIGENTE DE CONTROL VEHICULAR Y ACCESOS - FASE 15.
 * 
 * Reglas de Negocio:
 * 1. Room SQLite como Fuente Única de Verdad.
 * 2. Validación instantánea contra padrón de residentes y pases QR vigentes.
 * 3. Detección inmediata de vehículos no autorizados y despacho de alertas críticas a Caseta y Panel Maestro ALFHA.
 * 4. Registro inmutable de entrada y salida con cálculo exacto de tiempo de permanencia.
 * 5. Una sola captura alimenta a Caseta, Residente, Administración, Supervisión, Mesa Directiva y Panel Maestro ALFHA.
 * 6. "ESTO DEVUELVE TIEMPO": Ahorro de 175s por acceso vehicular vs anotación manual en libreta.
 */
object VehicleAccessControlEngine {
    private const val TAG = "VehicleAccessEngine"

    const val TIME_SAVED_SEC_PER_VEHICLE_ACCESS = 175L // 180s manual vs 5s automatizado

    /**
     * Valida si un vehículo tiene autorización para ingresar.
     */
    suspend fun validateVehicle(
        db: AppDatabase,
        plate: String,
        tagRfid: String = "",
        qrCode: String = ""
    ): VehicleValidationResult = withContext(Dispatchers.IO) {
        val cleanPlate = VehicleEntity.normalizePlate(plate)

        // 1. Búsqueda en Padrón de Residentes
        var residentVehicle: VehicleEntity? = null
        if (cleanPlate.isNotBlank()) {
            residentVehicle = db.vehicleDao().getVehicleByPlate(cleanPlate)
        }
        if (residentVehicle == null && tagRfid.isNotBlank()) {
            residentVehicle = db.vehicleDao().getVehicleByTag(tagRfid.trim())
        }
        if (residentVehicle == null && qrCode.isNotBlank()) {
            residentVehicle = db.vehicleDao().getVehicleByQr(qrCode.trim())
        }

        if (residentVehicle != null) {
            return@withContext when {
                residentVehicle.status.equals("ACTIVO", ignoreCase = true) -> {
                    VehicleValidationResult.AuthorizedResident(residentVehicle)
                }
                else -> {
                    VehicleValidationResult.DeniedSuspended(
                        residentVehicle,
                        "El vehículo ${residentVehicle.plate} (${residentVehicle.unitId}) tiene estatus ${residentVehicle.status}"
                    )
                }
            }
        }

        // 2. Búsqueda en Pases QR de Visita Vigentes
        if (cleanPlate.isNotBlank()) {
            val passes = db.qrPassDao().getAllPassesList()
            val matchingPass = passes.firstOrNull { pass ->
                val passPlateClean = pass.vehiclePlate?.let { VehicleEntity.normalizePlate(it) } ?: ""
                passPlateClean.isNotBlank() && passPlateClean == cleanPlate && pass.isValidForEntry
            }
            if (matchingPass != null) {
                return@withContext VehicleValidationResult.AuthorizedVisitorPass(matchingPass, cleanPlate)
            }
        }

        if (qrCode.isNotBlank()) {
            val passByCode = db.qrPassDao().getPassByCode(qrCode.trim())
            if (passByCode != null && passByCode.isValidForEntry) {
                return@withContext VehicleValidationResult.AuthorizedVisitorPass(passByCode, cleanPlate.ifBlank { passByCode.vehiclePlate ?: "S/P" })
            }
        }

        // 3. No encontrado -> No Autorizado
        VehicleValidationResult.Unauthorized(
            plate = cleanPlate.ifBlank { "SIN_PLACA" },
            reason = "No se localizó en padrón de residentes ni en pases QR vigentes"
        )
    }

    /**
     * Registra la entrada vehicular en Room SQLite.
     * Si no está autorizado, genera alerta inmediata y notificación crítica.
     */
    suspend fun registerVehicleEntry(
        context: Context,
        db: AppDatabase,
        validationResult: VehicleValidationResult,
        gateLane: String = "CARRIL_RESIDENTES_1",
        operatorName: String = "Oficial en Caseta",
        operatorRole: String = "CASETA_VIGILANCIA",
        manualPlate: String = "",
        manualBrand: String = "",
        manualModel: String = "",
        manualColor: String = "",
        manualUnitId: String = "",
        manualDriverName: String = "",
        guardNotes: String = "",
        allowEmergencyEntry: Boolean = false
    ): VehicleAccessLogEntity = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val folio = AlphaCoreEngine.generateUniqueFolio("VEH")

        val logEntity = when (validationResult) {
            is VehicleValidationResult.AuthorizedResident -> {
                val v = validationResult.vehicle
                val hash = AlphaCoreEngine.computeIntegrityHash(folio, v.plate, v.unitId)
                VehicleAccessLogEntity(
                    folio = folio,
                    plate = v.plate,
                    brand = v.brand,
                    model = v.model,
                    color = v.color,
                    vehicleType = v.vehicleType,
                    unitId = v.unitId,
                    driverOrOwnerName = v.ownerName,
                    accessCategory = "RESIDENTE_AUTORIZADO",
                    identificationMethod = if (v.tagRfid.isNotBlank()) "TAG_RFID" else "QR_RESIDENTE",
                    gateLane = gateLane,
                    direction = "ENTRADA",
                    status = "DENTRO_DEL_CONDOMINIO",
                    entryTimestampMillis = now,
                    isAuthorized = true,
                    operatorName = operatorName,
                    operatorRole = operatorRole,
                    guardNotes = guardNotes.ifBlank { "Acceso vehicular automático autorizado por padrón" },
                    hashIntegrity = hash
                )
            }

            is VehicleValidationResult.AuthorizedVisitorPass -> {
                val p = validationResult.qrPass
                val plateUsed = validationResult.plate
                val hash = AlphaCoreEngine.computeIntegrityHash(folio, plateUsed, p.destinationHouse)

                // Actualizar contador de entradas del pase QR
                val updatedPass = p.copy(currentEntriesCount = p.currentEntriesCount + 1)
                db.qrPassDao().insertPass(updatedPass)

                VehicleAccessLogEntity(
                    folio = folio,
                    plate = plateUsed,
                    brand = manualBrand.ifBlank { "Vehículo Visitante" },
                    model = manualModel,
                    color = manualColor,
                    vehicleType = "SEDAN",
                    unitId = p.destinationHouse,
                    driverOrOwnerName = p.guestName,
                    accessCategory = "VISITANTE_PASE_QR",
                    identificationMethod = "PASE_QR_VISITA",
                    gateLane = gateLane,
                    direction = "ENTRADA",
                    status = "DENTRO_DEL_CONDOMINIO",
                    entryTimestampMillis = now,
                    isAuthorized = true,
                    operatorName = operatorName,
                    operatorRole = operatorRole,
                    guardNotes = guardNotes.ifBlank { "Acceso con pase QR ${p.passCode} hacia ${p.destinationHouse}" },
                    hashIntegrity = hash
                )
            }

            is VehicleValidationResult.DeniedSuspended -> {
                val v = validationResult.vehicle
                val alertFolio = AlphaCoreEngine.generateUniqueFolio("ALT")
                val hash = AlphaCoreEngine.computeIntegrityHash(folio, v.plate, v.unitId)

                // Crear Alerta Operativa Inmediata
                val alert = OperationalAlertEntity(
                    folio = alertFolio,
                    alertType = "VEHICULO_SUSPENDIDO_INTENTO_ACCESO",
                    priorityLevel = "ALTA",
                    whatHappened = "Intento de acceso de vehículo con estatus ${v.status}: ${v.plate} (${v.brand} ${v.model})",
                    whereLocation = "Garita Principal / $gateLane",
                    whenFormatted = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(now)),
                    whyItMatters = "El vehículo o unidad ${v.unitId} cuenta con restricción administrativa activa.",
                    whoMustAttend = "Caseta de Vigilancia y Administración General",
                    recommendedAction = if (allowEmergencyEntry) "Acceso concedido bajo criterio de emergencia. Verificar con administración." else "Denegar acceso y solicitar aclaración con administración.",
                    status = if (allowEmergencyEntry) "EN_ATENCION" else "ACTIVA",
                    timestampMillis = now,
                    hashIntegrity = AlphaCoreEngine.computeIntegrityHash(alertFolio, v.plate, v.unitId)
                )
                db.operationalAlertDao().insertAlert(alert)

                // Disparar Notificación Crítica
                SmartNotificationHub.notifyVehicleAlert(
                    context = context,
                    db = db,
                    alertFolio = alertFolio,
                    plate = v.plate,
                    unitId = v.unitId,
                    reason = "Vehículo con restricción (${v.status}) intentó ingresar",
                    gateLane = gateLane,
                    allowEmergencyEntry = allowEmergencyEntry
                )

                VehicleAccessLogEntity(
                    folio = folio,
                    plate = v.plate,
                    brand = v.brand,
                    model = v.model,
                    color = v.color,
                    vehicleType = v.vehicleType,
                    unitId = v.unitId,
                    driverOrOwnerName = v.ownerName,
                    accessCategory = "VEHICULO_NO_AUTORIZADO",
                    identificationMethod = "RECONOCIMIENTO_PLACA_OCR",
                    gateLane = gateLane,
                    direction = "ENTRADA",
                    status = if (allowEmergencyEntry) "DENTRO_DEL_CONDOMINIO" else "ACCESO_DENEGADO_BLOQUEADO",
                    entryTimestampMillis = now,
                    isAuthorized = allowEmergencyEntry,
                    operatorName = operatorName,
                    operatorRole = operatorRole,
                    guardNotes = guardNotes.ifBlank { validationResult.reason },
                    alertFolio = alertFolio,
                    hashIntegrity = hash
                )
            }

            is VehicleValidationResult.Unauthorized -> {
                val plate = manualPlate.ifBlank { validationResult.plate }
                val alertFolio = AlphaCoreEngine.generateUniqueFolio("ALT")
                val hash = AlphaCoreEngine.computeIntegrityHash(folio, plate, manualUnitId.ifBlank { "SIN_UNIDAD" })

                // Crear Alerta Operativa Inmediata
                val alert = OperationalAlertEntity(
                    folio = alertFolio,
                    alertType = "VEHICULO_NO_AUTORIZADO",
                    priorityLevel = "CRITICA",
                    whatHappened = "Vehículo NO AUTORIZADO detectado en caseta: Placas $plate",
                    whereLocation = "Garita Principal / $gateLane",
                    whenFormatted = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(now)),
                    whyItMatters = "Vehículo sin registro previo ni pase QR intentando ingresar al condominio.",
                    whoMustAttend = "Caseta de Vigilancia, Supervisión Táctica y Panel Maestro ALFHA",
                    recommendedAction = if (allowEmergencyEntry) "Ingreso registrado manualmente bajo autorización del oficial. Monitorear estancia." else "Acceso retenido en bahía de control. Solicitar identificación oficial.",
                    status = if (allowEmergencyEntry) "EN_ATENCION" else "ACTIVA",
                    timestampMillis = now,
                    hashIntegrity = AlphaCoreEngine.computeIntegrityHash(alertFolio, plate, "NO_AUTORIZADO")
                )
                db.operationalAlertDao().insertAlert(alert)

                // Disparar Notificación Crítica a Caseta y Supervisión
                SmartNotificationHub.notifyVehicleAlert(
                    context = context,
                    db = db,
                    alertFolio = alertFolio,
                    plate = plate,
                    unitId = manualUnitId.ifBlank { "Sin unidad" },
                    reason = "Vehículo no registrado detectado en garita",
                    gateLane = gateLane,
                    allowEmergencyEntry = allowEmergencyEntry
                )

                VehicleAccessLogEntity(
                    folio = folio,
                    plate = plate,
                    brand = manualBrand.ifBlank { "Desconocida" },
                    model = manualModel.ifBlank { "Sin especificar" },
                    color = manualColor.ifBlank { "Sin especificar" },
                    vehicleType = "SEDAN",
                    unitId = manualUnitId.ifBlank { "Visita no confirmada" },
                    driverOrOwnerName = manualDriverName.ifBlank { "Conductor no identificado" },
                    accessCategory = if (allowEmergencyEntry) "ACCESO_MANUAL_EMERGENCIA" else "VEHICULO_NO_AUTORIZADO",
                    identificationMethod = "MANUAL_CASETA",
                    gateLane = gateLane,
                    direction = "ENTRADA",
                    status = if (allowEmergencyEntry) "DENTRO_DEL_CONDOMINIO" else "ACCESO_DENEGADO_BLOQUEADO",
                    entryTimestampMillis = now,
                    isAuthorized = allowEmergencyEntry,
                    operatorName = operatorName,
                    operatorRole = operatorRole,
                    guardNotes = guardNotes.ifBlank { "Vehículo no registrado detectado en garita." },
                    alertFolio = alertFolio,
                    hashIntegrity = hash
                )
            }
        }

        db.vehicleDao().insertAccessLog(logEntity)

        // Encolar para Sincronización Persistente Offline (FASE 19)
        try {
            com.example.data.sync.OfflineSyncEngine.enqueueOperation(
                db = db,
                operationType = "VEHICLE_ACCESS",
                targetFolio = logEntity.folio,
                targetModule = "VEHICULOS",
                payloadJson = "{\"folio\":\"${logEntity.folio}\",\"plate\":\"${logEntity.plate}\",\"unit\":\"${logEntity.unitId}\",\"direction\":\"ENTRADA\",\"authorized\":${logEntity.isAuthorized},\"lane\":\"$gateLane\"}",
                operatorName = operatorName,
                operatorRole = operatorRole,
                locationName = gateLane,
                deviceGateId = "Terminal Caseta Vehicular"
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error enqueuing vehicle entry sync: ${e.message}")
        }

        // Si fue autorizado, notificar al residente y caseta
        if (logEntity.isAuthorized && logEntity.unitId.isNotBlank()) {
            SmartNotificationHub.notifyVehicleAccessGranted(
                context = context,
                db = db,
                plate = logEntity.plate,
                unitId = logEntity.unitId,
                driverName = logEntity.driverOrOwnerName,
                gateLane = gateLane,
                logFolio = logEntity.folio
            )
        }

        // Registrar en Cadena de Auditoría Inmutable SHA-256
        val audit = AuditLogEntity(
            folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
            operatorName = "$operatorName ($operatorRole)",
            actionType = "ACCESO_VEHICULAR_ENTRADA",
            location = gateLane,
            targetEntity = logEntity.folio,
            changeDetails = "Entrada de vehículo ${logEntity.plate} (${logEntity.accessCategory}) en $gateLane. Autorizado=${logEntity.isAuthorized}",
            resultStatus = if (logEntity.isAuthorized) "EXITOSO" else "DENEGADO",
            timestampMillis = now
        )
        db.auditLogDao().insertAuditLog(audit)

        logEntity
    }

    /**
     * Registra la salida vehicular en Room SQLite.
     */
    suspend fun registerVehicleExit(
        context: Context,
        db: AppDatabase,
        plateOrFolio: String,
        exitLane: String = "CARRIL_SALIDA_1",
        operatorName: String = "Oficial en Caseta",
        operatorRole: String = "CASETA_VIGILANCIA",
        notes: String = ""
    ): VehicleAccessLogEntity? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cleanPlate = VehicleEntity.normalizePlate(plateOrFolio)

        var log = db.vehicleDao().getAccessLogByFolio(plateOrFolio)
        if (log == null) {
            log = db.vehicleDao().getActiveInsideLogByPlate(cleanPlate)
        }

        if (log == null) {
            Log.w(TAG, "No se encontró registro de entrada activo dentro del condominio para: $plateOrFolio")
            return@withContext null
        }

        val updatedLog = log.copy(
            exitTimestampMillis = now,
            status = "SALIDA_REGISTRADA",
            direction = "SALIDA",
            guardNotes = if (notes.isNotBlank()) "${log.guardNotes} | Salida: $notes" else log.guardNotes
        )

        db.vehicleDao().updateAccessLog(updatedLog)

        // Encolar para Sincronización Persistente Offline (FASE 19)
        try {
            com.example.data.sync.OfflineSyncEngine.enqueueOperation(
                db = db,
                operationType = "VEHICLE_ACCESS",
                targetFolio = "${updatedLog.folio}-EXIT",
                targetModule = "VEHICULOS",
                payloadJson = "{\"folio\":\"${updatedLog.folio}\",\"plate\":\"${updatedLog.plate}\",\"unit\":\"${updatedLog.unitId}\",\"direction\":\"SALIDA\",\"lane\":\"$exitLane\"}",
                operatorName = operatorName,
                operatorRole = operatorRole,
                locationName = exitLane,
                deviceGateId = "Terminal Caseta Vehicular"
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error enqueuing vehicle exit sync: ${e.message}")
        }

        // Notificar al residente de la salida
        if (updatedLog.unitId.isNotBlank()) {
            SmartNotificationHub.notifyVehicleExitRecorded(
                context = context,
                db = db,
                plate = updatedLog.plate,
                unitId = updatedLog.unitId,
                stayDuration = updatedLog.stayDurationFormatted,
                logFolio = updatedLog.folio
            )
        }

        // Auditoría inmutable
        val audit = AuditLogEntity(
            folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
            operatorName = "$operatorName ($operatorRole)",
            actionType = "ACCESO_VEHICULAR_SALIDA",
            location = exitLane,
            targetEntity = updatedLog.folio,
            changeDetails = "Salida de vehículo ${updatedLog.plate} por $exitLane. Permanencia total: ${updatedLog.stayDurationFormatted}",
            resultStatus = "EXITOSO",
            timestampMillis = now
        )
        db.auditLogDao().insertAuditLog(audit)

        updatedLog
    }

    /**
     * Registra o actualiza un vehículo en el Padrón Vehicular de Room.
     */
    suspend fun saveVehicle(
        db: AppDatabase,
        vehicle: VehicleEntity,
        updatedBy: String = "ADMINISTRACION"
    ): VehicleEntity = withContext(Dispatchers.IO) {
        val normalized = vehicle.copy(
            plate = VehicleEntity.normalizePlate(vehicle.plate),
            updatedAtMillis = System.currentTimeMillis(),
            updatedBy = updatedBy
        )
        db.vehicleDao().insertVehicle(normalized)

        val audit = AuditLogEntity(
            folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
            operatorName = updatedBy,
            actionType = "PADRON_VEHICULAR_ACTUALIZACION",
            location = "Panel de Control Vehicular",
            targetEntity = normalized.plate,
            changeDetails = "Vehículo ${normalized.plate} (${normalized.brand} ${normalized.model}) asignado a ${normalized.unitId}. Estado: ${normalized.status}",
            resultStatus = "EXITOSO",
            timestampMillis = System.currentTimeMillis()
        )
        db.auditLogDao().insertAuditLog(audit)

        normalized
    }

    /**
     * Elimina un vehículo del padrón vehicular.
     */
    suspend fun deleteVehicle(
        db: AppDatabase,
        plate: String,
        deletedBy: String = "ADMINISTRACION"
    ): Boolean = withContext(Dispatchers.IO) {
        val cleanPlate = VehicleEntity.normalizePlate(plate)
        db.vehicleDao().deleteVehicle(cleanPlate)

        val audit = AuditLogEntity(
            folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
            operatorName = deletedBy,
            actionType = "PADRON_VEHICULAR_BAJA",
            location = "Panel de Control Vehicular",
            targetEntity = cleanPlate,
            changeDetails = "Baja de vehículo con placa $cleanPlate del padrón.",
            resultStatus = "EXITOSO",
            timestampMillis = System.currentTimeMillis()
        )
        db.auditLogDao().insertAuditLog(audit)
        true
    }

    /**
     * Sembrado inicial de vehículos base asociados a unidades y residentes existentes.
     */
    suspend fun seedInitialVehiclesIfEmpty(context: Context, db: AppDatabase) = withContext(Dispatchers.IO) {
        try {
            val totalVehicles = db.vehicleDao().countTotalVehicles()
            if (totalVehicles == 0) {
                val initialVehicles = listOf(
                    VehicleEntity(
                        plate = "ABC-1234",
                        brand = "Toyota",
                        model = "RAV4",
                        color = "Gris Metálico",
                        vehicleType = "SUV",
                        unitId = "Casa 104",
                        residentId = "RES-A104-01",
                        ownerName = "Familia Arismendi",
                        relationship = "PROPIETARIO",
                        tagRfid = "TAG-A104-1",
                        qrAccessCode = "QR-VEH-A104-1",
                        status = "ACTIVO",
                        isPrimary = true,
                        notes = "Cajón asignado E-104A"
                    ),
                    VehicleEntity(
                        plate = "XYZ-9876",
                        brand = "Mazda",
                        model = "3 Sedán",
                        color = "Rojo Soul",
                        vehicleType = "SEDAN",
                        unitId = "Casa 104",
                        residentId = "RES-A104-01",
                        ownerName = "Familia Arismendi",
                        relationship = "FAMILIAR",
                        tagRfid = "TAG-A104-2",
                        qrAccessCode = "QR-VEH-A104-2",
                        status = "ACTIVO",
                        isPrimary = false,
                        notes = "Cajón asignado E-104B"
                    ),
                    VehicleEntity(
                        plate = "HJK-5621",
                        brand = "Volkswagen",
                        model = "Tiguan",
                        color = "Blanco Puro",
                        vehicleType = "SUV",
                        unitId = "Casa 101",
                        residentId = "RES-A101-01",
                        ownerName = "Dr. Roberto Casas",
                        relationship = "PROPIETARIO",
                        tagRfid = "TAG-A101-1",
                        qrAccessCode = "QR-VEH-A101-1",
                        status = "ACTIVO",
                        isPrimary = true,
                        notes = "Cajón asignado E-101A"
                    ),
                    VehicleEntity(
                        plate = "MNO-3344",
                        brand = "Honda",
                        model = "CR-V",
                        color = "Azul Noche",
                        vehicleType = "SUV",
                        unitId = "Casa 102",
                        residentId = "RES-A102-01",
                        ownerName = "Lic. Patricia Estrada",
                        relationship = "PROPIETARIO",
                        tagRfid = "TAG-A102-1",
                        qrAccessCode = "QR-VEH-A102-1",
                        status = "ACTIVO",
                        isPrimary = true,
                        notes = "Cajón asignado E-102"
                    ),
                    VehicleEntity(
                        plate = "QWE-8812",
                        brand = "Tesla",
                        model = "Model 3",
                        color = "Negro Sólido",
                        vehicleType = "SEDAN",
                        unitId = "Torre 1 - Depto 302",
                        residentId = "RES-T1302-01",
                        ownerName = "Arq. Valeria Domínguez",
                        relationship = "PROPIETARIO",
                        tagRfid = "TAG-T1302-1",
                        qrAccessCode = "QR-VEH-T1302-1",
                        status = "ACTIVO",
                        isPrimary = true,
                        notes = "Cajón S1-03 con cargador EV"
                    ),
                    VehicleEntity(
                        plate = "RST-4455",
                        brand = "Nissan",
                        model = "Versa",
                        color = "Plata",
                        vehicleType = "SEDAN",
                        unitId = "Torre 1 - Depto 101",
                        residentId = "RES-T1101-01",
                        ownerName = "Ing. Manuel Cárdenas",
                        relationship = "ARRENDATARIO",
                        tagRfid = "TAG-T1101-1",
                        qrAccessCode = "QR-VEH-T1101-1",
                        status = "ACTIVO",
                        isPrimary = true,
                        notes = "Cajón S1-01"
                    ),
                    VehicleEntity(
                        plate = "SUS-9900",
                        brand = "BMW",
                        model = "Serie 3",
                        color = "Gris Oxford",
                        vehicleType = "SEDAN",
                        unitId = "Casa 105",
                        residentId = "RES-A105-01",
                        ownerName = "Esteban Navarro",
                        relationship = "PROPIETARIO",
                        tagRfid = "",
                        qrAccessCode = "",
                        status = "SUSPENDIDO",
                        isPrimary = true,
                        notes = "Adeudo administrativo pendiente. Restricción de portón automático."
                    )
                )
                db.vehicleDao().insertVehicles(initialVehicles)

                // Sembrar algunos accesos vehiculares de muestra para el día de hoy
                val cal = Calendar.getInstance()
                cal.add(Calendar.HOUR_OF_DAY, -3)
                val entry1 = cal.timeInMillis

                val access1 = VehicleAccessLogEntity(
                    folio = "FOL-VEH-20260824-0001",
                    plate = "ABC-1234",
                    brand = "Toyota",
                    model = "RAV4",
                    color = "Gris Metálico",
                    vehicleType = "SUV",
                    unitId = "Casa 104",
                    driverOrOwnerName = "Familia Arismendi",
                    accessCategory = "RESIDENTE_AUTORIZADO",
                    identificationMethod = "TAG_RFID",
                    gateLane = "CARRIL_RESIDENTES_1",
                    direction = "ENTRADA",
                    status = "DENTRO_DEL_CONDOMINIO",
                    entryTimestampMillis = entry1,
                    isAuthorized = true,
                    operatorName = "Oficial Ramírez (Garita 1)",
                    guardNotes = "Apertura automática por Tag RFID.",
                    hashIntegrity = AlphaCoreEngine.computeIntegrityHash("FOL-VEH-20260824-0001", "ABC-1234", "Casa 104")
                )

                cal.add(Calendar.HOUR_OF_DAY, 1)
                val entry2 = cal.timeInMillis
                cal.add(Calendar.MINUTE, 45)
                val exit2 = cal.timeInMillis

                val access2 = VehicleAccessLogEntity(
                    folio = "FOL-VEH-20260824-0002",
                    plate = "MNO-3344",
                    brand = "Honda",
                    model = "CR-V",
                    color = "Azul Noche",
                    vehicleType = "SUV",
                    unitId = "Casa 102",
                    driverOrOwnerName = "Lic. Patricia Estrada",
                    accessCategory = "RESIDENTE_AUTORIZADO",
                    identificationMethod = "TAG_RFID",
                    gateLane = "CARRIL_RESIDENTES_1",
                    direction = "SALIDA",
                    status = "SALIDA_REGISTRADA",
                    entryTimestampMillis = entry2,
                    exitTimestampMillis = exit2,
                    isAuthorized = true,
                    operatorName = "Oficial Ramírez (Garita 1)",
                    guardNotes = "Salida por carril 1.",
                    hashIntegrity = AlphaCoreEngine.computeIntegrityHash("FOL-VEH-20260824-0002", "MNO-3344", "Casa 102")
                )

                db.vehicleDao().insertAccessLog(access1)
                db.vehicleDao().insertAccessLog(access2)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sembrando vehículos iniciales: ${e.message}", e)
        }
    }

    /**
     * Genera el Reporte Ejecutivo Certificado de Tráfico y Control Vehicular.
     */
    suspend fun generateVehicularAuditReport(db: AppDatabase): String = withContext(Dispatchers.IO) {
        val totalVehicles = db.vehicleDao().countTotalVehicles()
        val activeVehicles = db.vehicleDao().countActiveVehicles()
        val insideVehicles = db.vehicleDao().countVehiclesInside()
        val logs = db.vehicleDao().getAllAccessLogsList()
        val unauthorizedCount = logs.count { !it.isAuthorized || it.accessCategory == "VEHICULO_NO_AUTORIZADO" }

        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val entriesToday = logs.count { it.entryTimestampMillis >= startOfDay }
        val exitsToday = logs.count { it.exitTimestampMillis != null && it.exitTimestampMillis >= startOfDay }
        val timeSavedSec = logs.size * TIME_SAVED_SEC_PER_VEHICLE_ACCESS
        val timeSavedMin = timeSavedSec / 60

        val timestamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val hash = AlphaCoreEngine.computeIntegrityHash("VEH_REPORT", "$totalVehicles-$insideVehicles", timestamp)

        buildString {
            appendLine("================================================================================")
            appendLine("      SISTEMA MEDUSA ALFHA - REPORTE CERTIFICADO DE CONTROL VEHICULAR")
            appendLine("================================================================================")
            appendLine("Fecha de Emisión: $timestamp")
            appendLine("Firma Criptográfica SHA-256: $hash")
            appendLine("--------------------------------------------------------------------------------")
            appendLine("1. PADRÓN VEHICULAR")
            appendLine("   - Vehículos Totales Registrados: $totalVehicles")
            appendLine("   - Vehículos Activos (Autorizados): $activeVehicles")
            appendLine("   - Vehículos con Restricción/Suspendidos: ${totalVehicles - activeVehicles}")
            appendLine("--------------------------------------------------------------------------------")
            appendLine("2. ESTADO ACTUAL DEL CONDOMINIO")
            appendLine("   - Vehículos Actualmente Dentro: $insideVehicles")
            appendLine("   - Movimientos Registrados Hoy: $entriesToday Entradas / $exitsToday Salidas")
            appendLine("   - Alertas de No Autorizados: $unauthorizedCount incidentes")
            appendLine("--------------------------------------------------------------------------------")
            appendLine("3. IMPACTO DE TIEMPO DEVUELTO (PROC-09)")
            appendLine("   - Operaciones Vehiculares Automatizadas: ${logs.size}")
            appendLine("   - Tiempo Devuelto Total: $timeSavedMin minutos (${timeSavedSec}s)")
            appendLine("   - Equivalencia: Eliminación de 100% de registros manuales y llamadas de caseta")
            appendLine("================================================================================")
        }
    }
}
