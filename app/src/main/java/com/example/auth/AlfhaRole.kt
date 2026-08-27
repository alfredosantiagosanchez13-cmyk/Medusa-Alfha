package com.example.auth

/**
 * ROLES DEL ECOSISTEMA MEDUSA ALFHA
 * 1. RESIDENTE
 * 2. GUARDIA
 * 3. SUPERVISOR
 * 4. ADMINISTRACION
 * 5. MESA DIRECTIVA
 * 6. MAESTRO ALFHA
 */
enum class AlfhaRole(
    val roleCode: String,
    val displayName: String,
    val shortName: String,
    val description: String,
    val privilegeLevel: Int
) {
    RESIDENTE(
        roleCode = "RESIDENTE",
        displayName = "Residente",
        shortName = "Residente",
        description = "Autogestión de pases QR, reservas de amenidades y reporte de incidencias de su unidad.",
        privilegeLevel = 1
    ),
    GUARDIA(
        roleCode = "GUARDIA",
        displayName = "Guardia de Caseta",
        shortName = "Guardia",
        description = "Control de accesos con escáner CameraX, bitácora de novedades e incidencias inmediatas.",
        privilegeLevel = 2
    ),
    SUPERVISOR(
        roleCode = "SUPERVISOR",
        displayName = "Supervisor Operativo",
        shortName = "Supervisor",
        description = "Rondas de supervisión táctica GPS, dictámenes técnicos y auditoría de caseta.",
        privilegeLevel = 3
    ),
    ADMINISTRACION(
        roleCode = "ADMINISTRACION",
        displayName = "Administración",
        shortName = "Admin",
        description = "Gestión integral de incidencias, catálogos, estados de cuenta y reportes operativos.",
        privilegeLevel = 4
    ),
    MESA_DIRECTIVA(
        roleCode = "MESA_DIRECTIVA",
        displayName = "Mesa Directiva",
        shortName = "Directiva",
        description = "Gobierno vecinal, indicadores clave, aprobación de acuerdos y trazabilidad comunitaria.",
        privilegeLevel = 5
    ),
    MAESTRO_ALFHA(
        roleCode = "MAESTRO_ALFHA",
        displayName = "Maestro ALFHA",
        shortName = "Maestro",
        description = "Superintendencia total del sistema, inteligencia operativa, administración de roles y auditoría inmutable.",
        privilegeLevel = 6
    );

    companion object {
        fun fromCode(code: String): AlfhaRole {
            return values().firstOrNull {
                it.roleCode.equals(code, ignoreCase = true) ||
                it.name.equals(code, ignoreCase = true) ||
                it.displayName.equals(code, ignoreCase = true)
            } ?: RESIDENTE
        }
    }
}

/**
 * PERMISOS DEL SISTEMA
 * - VER
 * - CREAR
 * - EDITAR
 * - RESOLVER
 * - APROBAR
 * - EXPORTAR
 * - ADMINISTRAR
 */
enum class AlfhaPermission(
    val permCode: String,
    val label: String,
    val description: String,
    val tagColorHex: Long
) {
    VER(
        permCode = "VER",
        label = "Ver",
        description = "Visualización de módulos, bitácoras e información según alcance del rol.",
        tagColorHex = 0xFF38BDF8
    ),
    CREAR(
        permCode = "CREAR",
        label = "Crear",
        description = "Generación de pases QR, registros de ingreso, nuevas reservas e incidencias.",
        tagColorHex = 0xFF4ADE80
    ),
    EDITAR(
        permCode = "EDITAR",
        label = "Editar",
        description = "Modificación de datos operativos, notas de caseta y estados permitidos.",
        tagColorHex = 0xFFFACC15
    ),
    RESOLVER(
        permCode = "RESOLVER",
        label = "Resolver",
        description = "Cierre y resolución formal de incidencias y atención de alertas.",
        tagColorHex = 0xFFFB923C
    ),
    APROBAR(
        permCode = "APROBAR",
        label = "Aprobar",
        description = "Aprobación de dictámenes de supervisión, reservas especiales y acuerdos comunitarios.",
        tagColorHex = 0xFFA855F7
    ),
    EXPORTAR(
        permCode = "EXPORTAR",
        label = "Exportar",
        description = "Generación y descarga de bitácoras, reportes ejecutivos y sellos de integridad SHA-256.",
        tagColorHex = 0xFF06B6D4
    ),
    ADMINISTRAR(
        permCode = "ADMINISTRAR",
        label = "Administrar",
        description = "Asignación y modificación de roles, parámetros del sistema y políticas de seguridad RBAC.",
        tagColorHex = 0xFFD4AF37
    );

    companion object {
        fun fromCode(code: String): AlfhaPermission? {
            return values().firstOrNull {
                it.permCode.equals(code, ignoreCase = true) ||
                it.name.equals(code, ignoreCase = true) ||
                it.label.equals(code, ignoreCase = true)
            }
        }
    }
}
