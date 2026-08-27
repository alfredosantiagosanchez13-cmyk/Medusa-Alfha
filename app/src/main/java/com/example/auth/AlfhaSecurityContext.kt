package com.example.auth

import android.content.Context
import com.example.data.audit.AuditLogEntity
import com.example.data.auth.AlfhaUserEntity
import com.example.data.booking.AppDatabase
import com.example.data.core.AlphaCoreEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MOTOR DE SEGURIDAD Y CONTROL DE ROLES Y PERMISOS (RBAC) - MEDUSA ALFHA.
 * 
 * Reglas de Arquitectura:
 * 1. Cada usuario solo puede acceder a las funciones de su rol.
 * 2. Los permisos se validan en la lógica de la aplicación, no únicamente en la interfaz.
 * 3. Se registra en auditoría inmutable de Room cualquier cambio de permisos o intento denegado.
 * 4. El Panel Maestro ALFHA puede administrar roles y permisos.
 * 5. Protección estricta contra auto-elevación de privilegios.
 * 6. Room SQLite como Fuente Única de Verdad.
 */
object AlfhaSecurityContext {

    private val _currentUser = MutableStateFlow<AlfhaUserEntity>(
        AlfhaUserEntity(
            id = "USR-ALFHA-001",
            name = "Ing. Carlos Mendoza",
            email = "carlos.mendoza@alfhaseguridad.com",
            role = AlfhaRole.MAESTRO_ALFHA.name,
            unitOrDepartment = "Comando Central ALFHA",
            permissionsCsv = "",
            isActive = true
        )
    )
    val currentUser: StateFlow<AlfhaUserEntity> = _currentUser.asStateFlow()

    /**
     * Inicializa el repositorio de usuarios en Room SQLite si está vacío.
     */
    suspend fun seedInitialUsersIfEmpty(db: AppDatabase) = withContext(Dispatchers.IO) {
        val userDao = db.alfhaUserDao()
        if (userDao.getUserCount() == 0) {
            val initialUsers = listOf(
                AlfhaUserEntity(
                    id = "USR-ALFHA-001",
                    name = "Ing. Carlos Mendoza",
                    email = "carlos.mendoza@alfhaseguridad.com",
                    role = AlfhaRole.MAESTRO_ALFHA.name,
                    unitOrDepartment = "Comando Central ALFHA",
                    permissionsCsv = "",
                    isActive = true,
                    updatedBy = "INICIALIZACION_SISTEMA"
                ),
                AlfhaUserEntity(
                    id = "USR-ALFHA-002",
                    name = "Mesa Directiva Central",
                    email = "mesa.directiva@condominio.com",
                    role = AlfhaRole.MESA_DIRECTIVA.name,
                    unitOrDepartment = "Presidencia y Consejo",
                    permissionsCsv = "",
                    isActive = true,
                    updatedBy = "INICIALIZACION_SISTEMA"
                ),
                AlfhaUserEntity(
                    id = "USR-ALFHA-003",
                    name = "Lic. Patricia Ruiz",
                    email = "administracion@condominio.com",
                    role = AlfhaRole.ADMINISTRACION.name,
                    unitOrDepartment = "Administración General",
                    permissionsCsv = "",
                    isActive = true,
                    updatedBy = "INICIALIZACION_SISTEMA"
                ),
                AlfhaUserEntity(
                    id = "USR-ALFHA-004",
                    name = "Sup. Roberto Gómez",
                    email = "roberto.gomez@alfhaseguridad.com",
                    role = AlfhaRole.SUPERVISOR.name,
                    unitOrDepartment = "Supervisión Operativa Táctica",
                    permissionsCsv = "",
                    isActive = true,
                    updatedBy = "INICIALIZACION_SISTEMA"
                ),
                AlfhaUserEntity(
                    id = "USR-ALFHA-005",
                    name = "Oficial Juan Pérez",
                    email = "caseta1@alfhaseguridad.com",
                    role = AlfhaRole.GUARDIA.name,
                    unitOrDepartment = "Garita Principal (Acceso Vehicular)",
                    permissionsCsv = "",
                    isActive = true,
                    updatedBy = "INICIALIZACION_SISTEMA"
                ),
                AlfhaUserEntity(
                    id = "USR-ALFHA-006",
                    name = "Familia Arismendi",
                    email = "arismendi.residente@condominio.com",
                    role = AlfhaRole.RESIDENTE.name,
                    unitOrDepartment = "Manzana A - Casa 104",
                    permissionsCsv = "",
                    isActive = true,
                    updatedBy = "INICIALIZACION_SISTEMA"
                )
            )
            userDao.insertUsers(initialUsers)
        }
        com.example.data.resident.ResidentDirectoryEngine.seedInitialResidentsIfEmpty(db)
    }

    /**
     * Cambia el usuario activo en la sesión (para pruebas y control operativo).
     */
    suspend fun switchActiveUser(db: AppDatabase, userId: String): Boolean = withContext(Dispatchers.IO) {
        val user = db.alfhaUserDao().getUserById(userId)
        if (user != null) {
            _currentUser.value = user
            db.alfhaUserDao().updateLastLogin(userId)
            true
        } else {
            false
        }
    }

    /**
     * Valida si el usuario actual tiene el permiso especificado en tiempo de ejecución.
     */
    fun hasPermission(permission: AlfhaPermission): Boolean {
        return _currentUser.value.hasPermission(permission)
    }

    /**
     * REGLA 2: Validación obligatoria en la LÓGICA de la aplicación.
     * Si no tiene permiso, genera un registro de auditoría en Room con resultado DENEGADO.
     */
    suspend fun enforcePermission(
        db: AppDatabase,
        permission: AlfhaPermission,
        actionName: String,
        targetResource: String,
        location: String = "App Móvil"
    ): RbacValidationOutcome = withContext(Dispatchers.IO) {
        val user = _currentUser.value
        if (user.hasPermission(permission)) {
            RbacValidationOutcome.Granted(user)
        } else {
            val reason = "Acceso denegado en lógica: El rol '${user.alfhaRole.displayName}' no posee el permiso '${permission.label}' requerido para '$actionName'."
            
            // REGLA 3: Registrar en auditoría intento no autorizado
            db.auditLogDao().insertAuditLog(
                AuditLogEntity(
                    folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                    operatorName = "${user.name} (${user.alfhaRole.shortName})",
                    actionType = "ACCESO_DENEGADO_LOGICA",
                    location = location,
                    targetEntity = targetResource,
                    changeDetails = reason,
                    resultStatus = "DENEGADO"
                )
            )

            RbacValidationOutcome.Denied(
                requiredPermission = permission,
                user = user,
                reason = reason
            )
        }
    }

    /**
     * REGLA 4 y 5: Modificación de Roles y Permisos con protección de Auto-Elevación.
     */
    suspend fun updateUserRoleAndPermissions(
        db: AppDatabase,
        targetUserId: String,
        newRole: AlfhaRole,
        newPermissions: Set<AlfhaPermission>,
        location: String = "Panel Maestro ALFHA"
    ): RbacOperationResult = withContext(Dispatchers.IO) {
        val operator = _currentUser.value
        val userDao = db.alfhaUserDao()
        val targetUser = userDao.getUserById(targetUserId)
            ?: return@withContext RbacOperationResult.Error("Usuario destino no encontrado en la base de datos.")

        // 1. Validar que el operador tenga permiso de ADMINISTRAR
        if (!operator.hasPermission(AlfhaPermission.ADMINISTRAR)) {
            val reason = "Operación denegada: '${operator.name}' no tiene permiso de ADMINISTRAR para gestionar roles."
            db.auditLogDao().insertAuditLog(
                AuditLogEntity(
                    folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                    operatorName = "${operator.name} (${operator.alfhaRole.shortName})",
                    actionType = "CAMBIO_PERMISO_DENEGADO",
                    location = location,
                    targetEntity = "Usuario: ${targetUser.name} ($targetUserId)",
                    changeDetails = reason,
                    resultStatus = "DENEGADO"
                )
            )
            return@withContext RbacOperationResult.Error(reason)
        }

        // 2. REGLA 5: PROTECCIÓN ESTRICTA CONTRA AUTO-ELEVACIÓN DE PRIVILEGIOS
        if (operator.id == targetUserId) {
            val isElevatingRole = newRole.privilegeLevel > operator.alfhaRole.privilegeLevel
            val isAddingAdminPerm = newPermissions.contains(AlfhaPermission.ADMINISTRAR) && !operator.hasPermission(AlfhaPermission.ADMINISTRAR)

            if (isElevatingRole || isAddingAdminPerm) {
                val reason = "VIOLACIÓN DE SEGURIDAD RBAC: No está permitido que un usuario auto-eleve sus propios privilegios (Intentó cambiar de ${operator.alfhaRole.displayName} a ${newRole.displayName})."
                db.auditLogDao().insertAuditLog(
                    AuditLogEntity(
                        folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                        operatorName = "${operator.name} (${operator.alfhaRole.shortName})",
                        actionType = "AUTO_ELEVACION_BLOQUEADA",
                        location = location,
                        targetEntity = "Auto-Usuario: $targetUserId",
                        changeDetails = reason,
                        resultStatus = "DENEGADO"
                    )
                )
                return@withContext RbacOperationResult.Error(reason)
            }
        }

        // 3. Solo MAESTRO_ALFHA puede asignar el rol MAESTRO_ALFHA
        if (newRole == AlfhaRole.MAESTRO_ALFHA && operator.alfhaRole != AlfhaRole.MAESTRO_ALFHA) {
            val reason = "Solo un 'Maestro ALFHA' puede asignar el rol supremo 'Maestro ALFHA'."
            db.auditLogDao().insertAuditLog(
                AuditLogEntity(
                    folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                    operatorName = "${operator.name} (${operator.alfhaRole.shortName})",
                    actionType = "ASIGNACION_SUPREMA_BLOQUEADA",
                    location = location,
                    targetEntity = "Usuario: ${targetUser.name}",
                    changeDetails = reason,
                    resultStatus = "DENEGADO"
                )
            )
            return@withContext RbacOperationResult.Error(reason)
        }

        // 4. Aplicar cambio en Room SQLite
        val defaultPerms = AlfhaUserEntity.getDefaultPermissionsForRole(newRole)
        val permissionsCsv = if (newPermissions == defaultPerms) "" else newPermissions.joinToString(",") { it.name }
        val now = System.currentTimeMillis()

        userDao.updateUserRoleAndPermissions(
            userId = targetUserId,
            newRole = newRole.name,
            newPermissionsCsv = permissionsCsv,
            updatedAt = now,
            updatedBy = operator.name
        )

        // 5. REGLA 3: REGISTRAR EN AUDITORÍA INMUTABLE EL CAMBIO DE PERMISOS
        val permsSummary = newPermissions.joinToString(", ") { it.label }
        val changeDetail = "Rol anterior: ${targetUser.role} -> Nuevo rol: ${newRole.name}. Permisos efectivos asignados: [$permsSummary]"
        
        db.auditLogDao().insertAuditLog(
            AuditLogEntity(
                folio = AlphaCoreEngine.generateUniqueFolio("AUD"),
                operatorName = "${operator.name} (${operator.alfhaRole.shortName})",
                actionType = "CAMBIO_ROL_Y_PERMISOS",
                location = location,
                targetEntity = "Usuario: ${targetUser.name} (${targetUser.email})",
                changeDetails = changeDetail,
                resultStatus = "EXITOSO"
            )
        )

        // Si el usuario modificado es el usuario activo actual, refrescar sesión local
        if (operator.id == targetUserId) {
            val updatedUser = userDao.getUserById(targetUserId)
            if (updatedUser != null) {
                _currentUser.value = updatedUser
            }
        }

        RbacOperationResult.Success("Permisos y rol actualizados exitosamente en Room.")
    }
}

sealed class RbacValidationOutcome {
    data class Granted(val user: AlfhaUserEntity) : RbacValidationOutcome()
    data class Denied(
        val requiredPermission: AlfhaPermission,
        val user: AlfhaUserEntity,
        val reason: String
    ) : RbacValidationOutcome()
}

sealed class RbacOperationResult {
    data class Success(val message: String) : RbacOperationResult()
    data class Error(val errorMessage: String) : RbacOperationResult()
}
