package com.example.data.resident

import android.content.Context
import com.example.auth.AlfhaPermission
import com.example.auth.AlfhaRole
import com.example.auth.AlfhaSecurityContext
import com.example.data.audit.AuditLogEntity
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import com.example.data.notifications.NotificationCategory
import com.example.data.notifications.NotificationPriority
import com.example.data.notifications.SmartNotificationHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ResidentOperationResult {
    data class Success(val resident: ResidentEntity, val message: String, val minutesSaved: Int = 10) : ResidentOperationResult()
    data class Error(val message: String) : ResidentOperationResult()
    data class PermissionDenied(val reason: String) : ResidentOperationResult()
}

/**
 * MOTOR DE GESTIÓN DE RESIDENTES Y UNIDADES - FASE 12.
 * 
 * Reglas de Negocio:
 * 1. Room SQLite como Fuente Única de Verdad.
 * 2. Asociación automática e instantánea Residente ↔ Unidad.
 * 3. Cero recaptura: sincronización con usuarios del sistema y autocompletado universal.
 * 4. Alta y Baja lógica con auditoría inmutable.
 * 5. Registro auditable de Tiempo Devuelto por automatización.
 */
object ResidentDirectoryEngine {

    private const val DEFAULT_SAVED_MINUTES_REGISTER = 12 // Minutos ahorrados vs captura en papel/hojas de cálculo
    private const val DEFAULT_SAVED_MINUTES_UPDATE = 6
    private const val DEFAULT_SAVED_MINUTES_DELETE = 5

    /**
     * Inicializa unidades y residentes base si la base de datos está vacía.
     */
    suspend fun seedInitialResidentsIfEmpty(db: AppDatabase) = withContext(Dispatchers.IO) {
        val residentDao = db.residentDao()
        val unitDao = db.unitDao()

        if (unitDao.getUnitCount() == 0) {
            val initialUnits = listOf(
                UnitEntity(unitId = "Casa 101", blockOrTower = "Manzana A", unitNumber = "101", status = "HABITADA", intercomCode = "101", parkingSpots = "E-101A, E-101B"),
                UnitEntity(unitId = "Casa 102", blockOrTower = "Manzana A", unitNumber = "102", status = "HABITADA", intercomCode = "102", parkingSpots = "E-102"),
                UnitEntity(unitId = "Casa 103", blockOrTower = "Manzana A", unitNumber = "103", status = "DESOCUPADA", intercomCode = "103", parkingSpots = "E-103"),
                UnitEntity(unitId = "Casa 104", blockOrTower = "Manzana A", unitNumber = "104", status = "HABITADA", intercomCode = "104", parkingSpots = "E-104A, E-104B"),
                UnitEntity(unitId = "Casa 105", blockOrTower = "Manzana A", unitNumber = "105", status = "HABITADA", intercomCode = "105", parkingSpots = "E-105"),
                UnitEntity(unitId = "Torre 1 - Depto 101", blockOrTower = "Torre 1", unitNumber = "T1-101", status = "HABITADA", intercomCode = "201", parkingSpots = "S1-01"),
                UnitEntity(unitId = "Torre 1 - Depto 201", blockOrTower = "Torre 1", unitNumber = "T1-201", status = "HABITADA", intercomCode = "202", parkingSpots = "S1-02"),
                UnitEntity(unitId = "Torre 1 - Depto 302", blockOrTower = "Torre 1", unitNumber = "T1-302", status = "HABITADA", intercomCode = "203", parkingSpots = "S1-03"),
                UnitEntity(unitId = "Torre 2 - Depto 401", blockOrTower = "Torre 2", unitNumber = "T2-401", status = "HABITADA", intercomCode = "301", parkingSpots = "S2-01"),
                UnitEntity(unitId = "Torre 2 - Depto 502", blockOrTower = "Torre 2", unitNumber = "T2-502", status = "HABITADA", intercomCode = "302", parkingSpots = "S2-02")
            )
            unitDao.insertUnits(initialUnits)
        }

        if (residentDao.getTotalResidentCount() == 0) {
            val initialResidents = listOf(
                ResidentEntity(
                    id = "RES-A104-01",
                    unitId = "Casa 104",
                    fullName = "Familia Arismendi",
                    occupancyType = "PROPIETARIO",
                    phone = "555-432-1980",
                    email = "arismendi.residente@condominio.com",
                    status = "ACTIVO",
                    vehiclesJson = ResidentEntity.encodeVehicles(
                        listOf(
                            ResidentVehicle(plates = "ABC-1234", brand = "Toyota", model = "RAV4", color = "Gris Metálico", tagRfid = "TAG-A104-1", isPrimary = true),
                            ResidentVehicle(plates = "XYZ-9876", brand = "Mazda", model = "3 Sedán", color = "Rojo Soul", tagRfid = "TAG-A104-2", isPrimary = false)
                        )
                    ),
                    authorizedPersonsJson = ResidentEntity.encodeAuthorizedPersons(
                        listOf(
                            AuthorizedPerson(name = "Clara Valenzuela", relation = "Familiar", phone = "555-111-2233", canAuthorizeVisits = true),
                            AuthorizedPerson(name = "Martín Solís", relation = "Empleado Doméstico / Chofer", phone = "555-333-4455", canAuthorizeVisits = false)
                        )
                    ),
                    emergencyContactsJson = ResidentEntity.encodeEmergencyContacts(
                        listOf(
                            EmergencyContact(name = "Dra. Sofía Arismendi", relation = "Hija / Médico", phone = "555-999-0011", isPrimary = true),
                            EmergencyContact(name = "Lic. Fernando Ruiz", relation = "Abogado Familiar", phone = "555-888-7766", isPrimary = false)
                        )
                    ),
                    notes = "Residencia principal, acceso automatizado activo.",
                    linkedUserId = "USR-ALFHA-006",
                    isDeleted = false,
                    updatedBy = "SISTEMA_INICIAL"
                ),
                ResidentEntity(
                    id = "RES-A101-01",
                    unitId = "Casa 101",
                    fullName = "Ing. Rodrigo Morales",
                    occupancyType = "PROPIETARIO",
                    phone = "555-222-3344",
                    email = "rodrigo.morales@empresa.com",
                    status = "ACTIVO",
                    vehiclesJson = ResidentEntity.encodeVehicles(
                        listOf(
                            ResidentVehicle(plates = "VFR-5678", brand = "BMW", model = "X3", color = "Negro", tagRfid = "TAG-A101-1", isPrimary = true)
                        )
                    ),
                    authorizedPersonsJson = ResidentEntity.encodeAuthorizedPersons(
                        listOf(
                            AuthorizedPerson(name = "Ana Morales", relation = "Cónyuge", phone = "555-222-3345", canAuthorizeVisits = true)
                        )
                    ),
                    emergencyContactsJson = ResidentEntity.encodeEmergencyContacts(
                        listOf(
                            EmergencyContact(name = "Carlos Morales", relation = "Hermano", phone = "555-777-6655", isPrimary = true)
                        )
                    ),
                    notes = "Miembro activo de comité de vigilancia.",
                    isDeleted = false,
                    updatedBy = "SISTEMA_INICIAL"
                ),
                ResidentEntity(
                    id = "RES-T1-201-01",
                    unitId = "Torre 1 - Depto 201",
                    fullName = "Lic. Mariana Navarro",
                    occupancyType = "ARRENDATARIO",
                    phone = "555-666-7788",
                    email = "mariana.navarro@consultora.com",
                    status = "ACTIVO",
                    vehiclesJson = ResidentEntity.encodeVehicles(
                        listOf(
                            ResidentVehicle(plates = "JHG-4433", brand = "Honda", model = "Civic", color = "Blanco", tagRfid = "TAG-T1-201", isPrimary = true)
                        )
                    ),
                    authorizedPersonsJson = ResidentEntity.encodeAuthorizedPersons(
                        listOf(
                            AuthorizedPerson(name = "Esteban Lozano", relation = "Roomie / Co-arrendatario", phone = "555-666-7789", canAuthorizeVisits = true)
                        )
                    ),
                    emergencyContactsJson = ResidentEntity.encodeEmergencyContacts(
                        listOf(
                            EmergencyContact(name = "Laura Navarro", relation = "Madre", phone = "555-333-2211", isPrimary = true)
                        )
                    ),
                    notes = "Contrato de arrendamiento vigente 2026-2027.",
                    isDeleted = false,
                    updatedBy = "SISTEMA_INICIAL"
                ),
                ResidentEntity(
                    id = "RES-T2-401-01",
                    unitId = "Torre 2 - Depto 401",
                    fullName = "Arq. Diego Cárdenas",
                    occupancyType = "PROPIETARIO",
                    phone = "555-901-2345",
                    email = "diego.cardenas@arquitectura.com",
                    status = "ACTIVO",
                    vehiclesJson = ResidentEntity.encodeVehicles(
                        listOf(
                            ResidentVehicle(plates = "KLP-1122", brand = "Audi", model = "Q5", color = "Azul Marino", tagRfid = "TAG-T2-401", isPrimary = true)
                        )
                    ),
                    authorizedPersonsJson = ResidentEntity.encodeAuthorizedPersons(
                        listOf(
                            AuthorizedPerson(name = "Valeria Ríos", relation = "Esposa", phone = "555-901-2346", canAuthorizeVisits = true)
                        )
                    ),
                    emergencyContactsJson = ResidentEntity.encodeEmergencyContacts(
                        listOf(
                            EmergencyContact(name = "Manuel Cárdenas", relation = "Padre", phone = "555-444-1122", isPrimary = true)
                        )
                    ),
                    notes = "Recibe paquetería técnica con frecuencia.",
                    isDeleted = false,
                    updatedBy = "SISTEMA_INICIAL"
                )
            )
            residentDao.insertResidents(initialResidents)
        }
    }

    /**
     * Alta de Residente con Asociación Automática Residente ↔ Unidad.
     * Cero recaptura: Si la unidad no existe, la crea; si el usuario ya existe en RBAC, lo vincula.
     */
    suspend fun executeRegisterResident(
        context: Context,
        db: AppDatabase,
        fullName: String,
        unitId: String,
        occupancyType: String,
        phone: String,
        email: String,
        vehicles: List<ResidentVehicle>,
        authorizedPersons: List<AuthorizedPerson>,
        emergencyContacts: List<EmergencyContact>,
        notes: String,
        operatorName: String
    ): ResidentOperationResult = withContext(Dispatchers.IO) {
        val currentUser = AlfhaSecurityContext.currentUser.value
        if (!currentUser.hasPermission(AlfhaPermission.CREAR) && !currentUser.hasPermission(AlfhaPermission.ADMINISTRAR)) {
            return@withContext ResidentOperationResult.PermissionDenied(
                "El rol ${currentUser.alfhaRole.displayName} no tiene permisos para dar de alta residentes."
            )
        }

        val cleanName = fullName.trim()
        val cleanUnit = unitId.trim()
        if (cleanName.isBlank() || cleanUnit.isBlank()) {
            return@withContext ResidentOperationResult.Error("El nombre y la unidad son obligatorios.")
        }

        val residentDao = db.residentDao()
        val unitDao = db.unitDao()

        // 1. Asegurar existencia de la Unidad (Asociación automática)
        val existingUnit = unitDao.getUnitById(cleanUnit)
        if (existingUnit == null) {
            val newUnit = UnitEntity(
                unitId = cleanUnit,
                blockOrTower = if (cleanUnit.contains("Torre", ignoreCase = true)) "Torres" else "Manzanas",
                unitNumber = cleanUnit.filter { it.isDigit() }.ifBlank { cleanUnit },
                status = "HABITADA",
                createdAtMillis = System.currentTimeMillis(),
                updatedAtMillis = System.currentTimeMillis()
            )
            unitDao.insertUnit(newUnit)
        }

        // 2. Vínculo Cero Recaptura con AlfhaUserEntity si coincide email
        var linkedUserId = ""
        if (email.isNotBlank()) {
            val userMatch = db.alfhaUserDao().getUserByEmail(email.trim())
            if (userMatch != null) {
                linkedUserId = userMatch.id
            }
        }

        // 3. Crear Folio / ID único de Residente
        val resId = AlphaCoreEngine.generateUniqueFolio("RES")
        val newResident = ResidentEntity(
            id = resId,
            unitId = cleanUnit,
            fullName = cleanName,
            occupancyType = occupancyType,
            phone = phone.trim(),
            email = email.trim(),
            status = "ACTIVO",
            vehiclesJson = ResidentEntity.encodeVehicles(vehicles),
            authorizedPersonsJson = ResidentEntity.encodeAuthorizedPersons(authorizedPersons),
            emergencyContactsJson = ResidentEntity.encodeEmergencyContacts(emergencyContacts),
            notes = notes.trim(),
            linkedUserId = linkedUserId,
            isDeleted = false,
            createdAtMillis = System.currentTimeMillis(),
            updatedAtMillis = System.currentTimeMillis(),
            updatedBy = operatorName
        )

        residentDao.insertResident(newResident)

        // 4. Registro de Auditoría Inmutable Room
        val auditFolio = AlphaCoreEngine.generateUniqueFolio("AUD")
        db.auditLogDao().insertAuditLog(
            AuditLogEntity(
                folio = auditFolio,
                operatorName = operatorName,
                actionType = "ALTA_RESIDENTE",
                location = cleanUnit,
                targetEntity = "$cleanName ($cleanUnit)",
                changeDetails = "Alta de residente [ID: $resId] con tipo '$occupancyType', ${vehicles.size} vehículos y ${emergencyContacts.size} contactos de emergencia. Cero recaptura aplicada.",
                resultStatus = "CONFIRMADA"
            )
        )

        // 5. Notificación Inteligente
        SmartNotificationHub.notifyResidentRegistered(
            context = context,
            db = db,
            residentName = cleanName,
            unitId = cleanUnit,
            residentFolio = resId
        )

        ResidentOperationResult.Success(
            resident = newResident,
            message = "Residente registrado exitosamente con asociación a $cleanUnit.",
            minutesSaved = DEFAULT_SAVED_MINUTES_REGISTER
        )
    }

    /**
     * Baja Lógica de Residente con Auditoría Inmutable.
     */
    suspend fun executeSoftDeleteResident(
        context: Context,
        db: AppDatabase,
        residentId: String,
        reason: String,
        operatorName: String
    ): ResidentOperationResult = withContext(Dispatchers.IO) {
        val currentUser = AlfhaSecurityContext.currentUser.value
        if (!currentUser.hasPermission(AlfhaPermission.EDITAR) && !currentUser.hasPermission(AlfhaPermission.ADMINISTRAR)) {
            return@withContext ResidentOperationResult.PermissionDenied(
                "El rol ${currentUser.alfhaRole.displayName} no tiene permisos para dar de baja residentes."
            )
        }

        val residentDao = db.residentDao()
        val resident = residentDao.getResidentById(residentId)
            ?: return@withContext ResidentOperationResult.Error("Residente no encontrado.")

        residentDao.softDeleteResident(residentId, operatorName)

        // Registro de Auditoría Room
        val auditFolio = AlphaCoreEngine.generateUniqueFolio("AUD")
        db.auditLogDao().insertAuditLog(
            AuditLogEntity(
                folio = auditFolio,
                operatorName = operatorName,
                actionType = "BAJA_LOGICA_RESIDENTE",
                location = resident.unitId,
                targetEntity = "${resident.fullName} (${resident.unitId})",
                changeDetails = "Baja lógica aplicada al residente [ID: $residentId]. Motivo: $reason",
                resultStatus = "CONFIRMADA"
            )
        )

        // Notificación de seguridad a la administración
        SmartNotificationHub.notifyResidentSoftDeleted(
            context = context,
            db = db,
            residentName = resident.fullName,
            unitId = resident.unitId,
            operatorName = operatorName,
            reason = reason,
            residentFolio = residentId
        )

        ResidentOperationResult.Success(
            resident = resident.copy(isDeleted = true, status = "BAJA_LOGICA"),
            message = "Baja lógica aplicada correctamente a ${resident.fullName}.",
            minutesSaved = DEFAULT_SAVED_MINUTES_DELETE
        )
    }

    /**
     * Actualización Completa de Residente (Vehículos, Contactos, Personas Autorizadas).
     */
    suspend fun executeUpdateResident(
        context: Context,
        db: AppDatabase,
        resident: ResidentEntity,
        operatorName: String
    ): ResidentOperationResult = withContext(Dispatchers.IO) {
        val currentUser = AlfhaSecurityContext.currentUser.value
        
        // Si es residente, solo puede editar su propia unidad
        val isAllowed = when (currentUser.alfhaRole) {
            AlfhaRole.RESIDENTE -> {
                currentUser.unitOrDepartment.contains(resident.unitId, ignoreCase = true) ||
                        resident.unitId.contains(currentUser.unitOrDepartment, ignoreCase = true) ||
                        resident.email.equals(currentUser.email, ignoreCase = true)
            }
            AlfhaRole.ADMINISTRACION, AlfhaRole.MESA_DIRECTIVA, AlfhaRole.MAESTRO_ALFHA, AlfhaRole.SUPERVISOR -> true
            else -> false
        }

        if (!isAllowed) {
            return@withContext ResidentOperationResult.PermissionDenied(
                "No tienes autorización para modificar la información de esta unidad."
            )
        }

        val updated = resident.copy(
            updatedAtMillis = System.currentTimeMillis(),
            updatedBy = operatorName
        )
        db.residentDao().updateResident(updated)

        // Auditoría
        db.auditLogDao().insertAuditLog(
            AuditLogEntity(
                folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                operatorName = operatorName,
                actionType = "ACTUALIZACION_RESIDENTE",
                location = updated.unitId,
                targetEntity = "${updated.fullName} (${updated.unitId})",
                changeDetails = "Actualización de ficha residencial: datos de contacto, vehículos y personas autorizadas guardados.",
                resultStatus = "CONFIRMADA"
            )
        )

        ResidentOperationResult.Success(
            resident = updated,
            message = "Ficha residencial actualizada exitosamente.",
            minutesSaved = DEFAULT_SAVED_MINUTES_UPDATE
        )
    }
}
