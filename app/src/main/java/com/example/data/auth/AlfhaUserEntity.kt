package com.example.data.auth

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.auth.AlfhaPermission
import com.example.auth.AlfhaRole

/**
 * Entidad de Usuario y Asignación de Roles en Room SQLite.
 * Fuente Única de Verdad para Control de Roles y Permisos RBAC.
 */
@Entity(
    tableName = "alfha_users",
    indices = [
        Index(value = ["email"], unique = true),
        Index(value = ["role"]),
        Index(value = ["isActive"])
    ]
)
data class AlfhaUserEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val email: String,
    val role: String, // RESIDENTE, GUARDIA, SUPERVISOR, ADMINISTRACION, MESA_DIRECTIVA, MAESTRO_ALFHA
    val unitOrDepartment: String,
    val permissionsCsv: String = "", // Sobrescritura personalizada de permisos separada por coma
    val isActive: Boolean = true,
    val lastLoginMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val updatedBy: String = "SISTEMA_INICIAL"
) {
    val alfhaRole: AlfhaRole
        get() = AlfhaRole.fromCode(role)

    /**
     * Calcula los permisos efectivos del usuario:
     * Si permissionsCsv está vacío, retorna los permisos por defecto de su rol.
     * Si permissionsCsv tiene contenido, devuelve los permisos configurados explícitamente.
     */
    val effectivePermissions: Set<AlfhaPermission>
        get() {
            if (permissionsCsv.isBlank()) {
                return getDefaultPermissionsForRole(alfhaRole)
            }
            val parsed = permissionsCsv.split(",")
                .mapNotNull { AlfhaPermission.fromCode(it.trim()) }
                .toSet()
            return if (parsed.isEmpty()) getDefaultPermissionsForRole(alfhaRole) else parsed
        }

    fun hasPermission(permission: AlfhaPermission): Boolean {
        return effectivePermissions.contains(permission)
    }

    companion object {
        fun getDefaultPermissionsForRole(role: AlfhaRole): Set<AlfhaPermission> {
            return when (role) {
                AlfhaRole.RESIDENTE -> setOf(
                    AlfhaPermission.VER,
                    AlfhaPermission.CREAR
                )
                AlfhaRole.GUARDIA -> setOf(
                    AlfhaPermission.VER,
                    AlfhaPermission.CREAR,
                    AlfhaPermission.EDITAR
                )
                AlfhaRole.SUPERVISOR -> setOf(
                    AlfhaPermission.VER,
                    AlfhaPermission.CREAR,
                    AlfhaPermission.EDITAR,
                    AlfhaPermission.APROBAR
                )
                AlfhaRole.ADMINISTRACION -> setOf(
                    AlfhaPermission.VER,
                    AlfhaPermission.CREAR,
                    AlfhaPermission.EDITAR,
                    AlfhaPermission.RESOLVER,
                    AlfhaPermission.EXPORTAR
                )
                AlfhaRole.MESA_DIRECTIVA -> setOf(
                    AlfhaPermission.VER,
                    AlfhaPermission.RESOLVER,
                    AlfhaPermission.APROBAR,
                    AlfhaPermission.EXPORTAR
                )
                AlfhaRole.MAESTRO_ALFHA -> setOf(
                    AlfhaPermission.VER,
                    AlfhaPermission.CREAR,
                    AlfhaPermission.EDITAR,
                    AlfhaPermission.RESOLVER,
                    AlfhaPermission.APROBAR,
                    AlfhaPermission.EXPORTAR,
                    AlfhaPermission.ADMINISTRAR
                )
            }
        }
    }
}
