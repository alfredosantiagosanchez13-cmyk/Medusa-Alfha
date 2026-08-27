package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CorporateFare
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.auth.AlfhaPermission
import com.example.auth.AlfhaRole
import com.example.auth.AlfhaSecurityContext
import com.example.auth.RbacOperationResult
import com.example.auth.RbacValidationOutcome
import com.example.data.audit.AuditLogEntity
import com.example.data.auth.AlfhaUserEntity
import com.example.data.booking.AppDatabase
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyDark
import com.example.ui.theme.NavySurface
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.TextMuted
import com.example.ui.theme.WarningOrange
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class RbacSectionTab(val label: String) {
    ACTIVE_SESSION("Sesión & Simulador"),
    PERMISSIONS_MATRIX("Matriz de Permisos"),
    USERS_DIRECTORY("Directorio de Usuarios"),
    LOGIC_TESTER("Validador en Lógica"),
    SECURITY_AUDIT("Auditoría RBAC")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RbacControlView(
    db: AppDatabase,
    currentUser: AlfhaUserEntity,
    users: List<AlfhaUserEntity>,
    auditLogs: List<AuditLogEntity>,
    onRefreshRequested: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedSection by remember { mutableStateOf(RbacSectionTab.ACTIVE_SESSION) }

    var userToEdit by remember { mutableStateOf<AlfhaUserEntity?>(null) }
    var testResultBanner by remember { mutableStateOf<String?>(null) }
    var testResultIsSuccess by remember { mutableStateOf(true) }

    val rbacAuditLogs = remember(auditLogs) {
        auditLogs.filter {
            it.actionType.contains("ROL", ignoreCase = true) ||
            it.actionType.contains("PERMISO", ignoreCase = true) ||
            it.actionType.contains("ACCESO", ignoreCase = true) ||
            it.actionType.contains("ELEVACION", ignoreCase = true) ||
            it.actionType.contains("ASIGNACION", ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("rbac_control_view"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section Navigation Tabs
        item {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(RbacSectionTab.values()) { section ->
                    val isSelected = selectedSection == section
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedSection = section },
                        label = {
                            Text(
                                text = section.label,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldPrimary,
                            selectedLabelColor = NavyDark,
                            containerColor = NavySurface,
                            labelColor = TextMuted
                        ),
                        modifier = Modifier.testTag("rbac_tab_${section.name.lowercase()}")
                    )
                }
            }
        }

        // SECTION 1: ACTIVE SESSION & ROLE SIMULATOR
        if (selectedSection == RbacSectionTab.ACTIVE_SESSION) {
            item {
                ActiveUserSessionCard(
                    currentUser = currentUser,
                    onSwitchUser = { targetUser ->
                        scope.launch {
                            AlfhaSecurityContext.switchActiveUser(db, targetUser.id)
                            Toast.makeText(context, "Sesión activa cambiada a: ${targetUser.name} (${targetUser.alfhaRole.displayName})", Toast.LENGTH_SHORT).show()
                            onRefreshRequested()
                        }
                    },
                    availableUsers = users
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NavySurface),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanNeon.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "ESTRUCTURA DE PRIVILEGIOS MEDUSA ALFHA",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = CyanNeon
                        )
                        Text(
                            text = "El sistema valida los permisos en la LÓGICA antes de cada operación en Room SQLite. No se permite la auto-elevación de privilegios y todo intento denegado queda registrado con firma SHA-256 en la auditoría inmutable.",
                            fontSize = 10.sp,
                            color = TextMuted,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        // SECTION 2: PERMISSIONS MATRIX
        if (selectedSection == RbacSectionTab.PERMISSIONS_MATRIX) {
            item {
                Text(
                    text = "MATRIZ DE CONTROL DE ROLES Y PERMISOS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = GoldPrimary
                )
            }

            items(AlfhaRole.values()) { role ->
                val defaultPerms = AlfhaUserEntity.getDefaultPermissionsForRole(role)
                val isCurrentRole = currentUser.alfhaRole == role

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrentRole) NavyCard else NavySurface
                    ),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        if (isCurrentRole) 1.5.dp else 0.5.dp,
                        if (isCurrentRole) GoldPrimary else CyanNeon.copy(alpha = 0.2f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = getRoleColor(role).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, getRoleColor(role))
                                ) {
                                    Text(
                                        text = "NIVEL ${role.privilegeLevel}",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = getRoleColor(role),
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = role.displayName.uppercase(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                            if (isCurrentRole) {
                                Surface(
                                    color = GoldPrimary.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "ROL ACTIVO",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GoldPrimary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = role.description,
                            fontSize = 10.sp,
                            color = TextMuted
                        )

                        // Permissions chips
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            AlfhaPermission.values().forEach { permission ->
                                val isGranted = defaultPerms.contains(permission)
                                Surface(
                                    color = if (isGranted) Color(permission.tagColorHex).copy(alpha = 0.2f) else NavyDark,
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(
                                        0.5.dp,
                                        if (isGranted) Color(permission.tagColorHex) else TextMuted.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
                                            contentDescription = null,
                                            tint = if (isGranted) Color(permission.tagColorHex) else TextMuted.copy(alpha = 0.5f),
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = permission.label,
                                            fontSize = 9.sp,
                                            fontWeight = if (isGranted) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isGranted) Color.White else TextMuted.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION 3: USERS DIRECTORY IN ROOM
        if (selectedSection == RbacSectionTab.USERS_DIRECTORY) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DIRECTORIO DE USUARIOS EN ROOM (${users.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = GoldPrimary
                    )
                    Text(
                        text = "Fuente Única: SQLite",
                        fontSize = 9.sp,
                        color = CyanNeon
                    )
                }
            }

            items(users) { user ->
                val isMe = user.id == currentUser.id
                val canManageUsers = currentUser.hasPermission(AlfhaPermission.ADMINISTRAR)

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("user_card_${user.id}"),
                    colors = CardDefaults.cardColors(containerColor = NavySurface),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        if (isMe) 1.5.dp else 0.5.dp,
                        if (isMe) GoldPrimary else CyanNeon.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = user.name,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    if (isMe) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = GoldPrimary.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "TU SESIÓN",
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Black,
                                                color = GoldPrimary,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = "${user.email} • ${user.unitOrDepartment}",
                                    fontSize = 9.sp,
                                    color = TextMuted
                                )
                            }

                            Surface(
                                color = getRoleColor(user.alfhaRole).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, getRoleColor(user.alfhaRole))
                            ) {
                                Text(
                                    text = user.alfhaRole.displayName,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = getRoleColor(user.alfhaRole),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Granted permissions chips
                        Text(
                            text = "Permisos Efectivos:",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanNeon
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            user.effectivePermissions.forEach { perm ->
                                Surface(
                                    color = Color(perm.tagColorHex).copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(perm.tagColorHex))
                                ) {
                                    Text(
                                        text = perm.label,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(perm.tagColorHex),
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Action Button: Edit User Roles / Permissions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Button(
                                onClick = { userToEdit = user },
                                enabled = canManageUsers,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (canManageUsers) GoldPrimary else TextMuted.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.testTag("edit_permissions_btn_${user.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ManageAccounts,
                                    contentDescription = null,
                                    tint = if (canManageUsers) NavyDark else Color.Gray,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (canManageUsers) "Gestionar Rol & Permisos" else "Requiere ADMINISTRAR",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (canManageUsers) NavyDark else Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }

        // SECTION 4: REAL-TIME LOGIC VALIDATOR
        if (selectedSection == RbacSectionTab.LOGIC_TESTER) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = NavySurface),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CyanNeon.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "CONSOLA DE VALIDACIÓN EN LÓGICA (REGLA 2)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = GoldPrimary
                        )
                        Text(
                            text = "Ejecuta llamadas reales a AlfhaSecurityContext.enforcePermission() con el usuario activo '${currentUser.name}' (${currentUser.alfhaRole.displayName}) para verificar el bloqueo o autorización a nivel de arquitectura y el registro inmutable en Room.",
                            fontSize = 10.sp,
                            color = TextMuted
                        )

                        // Result banner if tested
                        testResultBanner?.let { bannerText ->
                            Surface(
                                color = if (testResultIsSuccess) SuccessGreen.copy(alpha = 0.2f) else ErrorRed.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (testResultIsSuccess) SuccessGreen else ErrorRed
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (testResultIsSuccess) Icons.Default.CheckCircle else Icons.Default.Block,
                                        contentDescription = null,
                                        tint = if (testResultIsSuccess) SuccessGreen else ErrorRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = bannerText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Probar Acciones por Permiso:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanNeon
                        )

                        // 7 Test Buttons
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AlfhaPermission.values().forEach { perm ->
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val outcome = AlfhaSecurityContext.enforcePermission(
                                                db = db,
                                                permission = perm,
                                                actionName = "Prueba de Lógica '${perm.label}'",
                                                targetResource = "Consola de Pruebas RBAC",
                                                location = "Panel Maestro ALFHA"
                                            )
                                            when (outcome) {
                                                is RbacValidationOutcome.Granted -> {
                                                    testResultIsSuccess = true
                                                    testResultBanner = "✅ ACCESO AUTORIZADO POR LÓGICA: El rol '${currentUser.alfhaRole.displayName}' tiene permiso '${perm.label}'."
                                                }
                                                is RbacValidationOutcome.Denied -> {
                                                    testResultIsSuccess = false
                                                    testResultBanner = "🚫 ACCESO DENEGADO POR LÓGICA: El rol '${currentUser.alfhaRole.displayName}' NO tiene permiso '${perm.label}'. Intento auditado en Room con resultado DENEGADO."
                                                }
                                            }
                                            onRefreshRequested()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(perm.tagColorHex).copy(alpha = 0.25f)
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(perm.tagColorHex)),
                                    modifier = Modifier.testTag("test_perm_btn_${perm.name.lowercase()}")
                                ) {
                                    Text(
                                        text = "Probar ${perm.label}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(perm.tagColorHex)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION 5: SECURITY AUDIT LOGS IN ROOM
        if (selectedSection == RbacSectionTab.SECURITY_AUDIT) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TRAZABILIDAD DE SEGURIDAD Y PERMISOS (${rbacAuditLogs.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = GoldPrimary
                    )
                    Text(
                        text = "Inmutable SHA-256",
                        fontSize = 9.sp,
                        color = CyanNeon
                    )
                }
            }

            if (rbacAuditLogs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = NavySurface),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Sin registros de auditoría de permisos aún. Realice cambios o pruebas de lógica para registrar eventos inmutables.",
                            fontSize = 10.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            } else {
                items(rbacAuditLogs) { log ->
                    val isSuccess = log.resultStatus.equals("EXITOSO", ignoreCase = true)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = NavySurface),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            if (isSuccess) CyanNeon.copy(alpha = 0.3f) else ErrorRed.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = log.folio,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = GoldPrimary
                                )
                                Surface(
                                    color = if (isSuccess) SuccessGreen.copy(alpha = 0.2f) else ErrorRed.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = log.resultStatus,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isSuccess) SuccessGreen else ErrorRed,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Acción: ${log.actionType} • Operador: ${log.operatorName}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Text(
                                text = "Destino: ${log.targetEntity}",
                                fontSize = 9.sp,
                                color = CyanNeon
                            )

                            Text(
                                text = log.changeDetails,
                                fontSize = 9.sp,
                                color = TextMuted
                            )

                            Text(
                                text = "Firma: ${log.sha256Signature.take(16)}... • ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(log.timestampMillis))}",
                                fontSize = 8.sp,
                                color = TextMuted.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }

    // MODAL DIALOG: EDIT ROLE AND PERMISSIONS
    userToEdit?.let { targetUser ->
        EditUserRoleAndPermissionsDialog(
            targetUser = targetUser,
            operatorUser = currentUser,
            onDismiss = { userToEdit = null },
            onSave = { newRole, newPermissions ->
                scope.launch {
                    val result = AlfhaSecurityContext.updateUserRoleAndPermissions(
                        db = db,
                        targetUserId = targetUser.id,
                        newRole = newRole,
                        newPermissions = newPermissions,
                        location = "Panel Maestro ALFHA - Módulo RBAC"
                    )

                    when (result) {
                        is RbacOperationResult.Success -> {
                            Toast.makeText(context, "✅ ${result.message}", Toast.LENGTH_LONG).show()
                            userToEdit = null
                            onRefreshRequested()
                        }
                        is RbacOperationResult.Error -> {
                            Toast.makeText(context, "❌ ${result.errorMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun ActiveUserSessionCard(
    currentUser: AlfhaUserEntity,
    onSwitchUser: (AlfhaUserEntity) -> Unit,
    availableUsers: List<AlfhaUserEntity>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_user_session_card"),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, GoldPrimary)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(GoldPrimary.copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, GoldPrimary, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = GoldPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = currentUser.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = "${currentUser.email} • ${currentUser.unitOrDepartment}",
                            fontSize = 9.sp,
                            color = TextMuted
                        )
                    }
                }

                Surface(
                    color = getRoleColor(currentUser.alfhaRole).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, getRoleColor(currentUser.alfhaRole))
                ) {
                    Text(
                        text = currentUser.alfhaRole.displayName.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = getRoleColor(currentUser.alfhaRole),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            // Permisos Activos del Usuario Actual
            Text(
                text = "PERMISOS EFECTIVOS OTORGADOS:",
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                color = CyanNeon
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                AlfhaPermission.values().forEach { perm ->
                    val has = currentUser.hasPermission(perm)
                    Surface(
                        color = if (has) Color(perm.tagColorHex).copy(alpha = 0.2f) else NavyDark,
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            if (has) Color(perm.tagColorHex) else TextMuted.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = if (has) Icons.Default.Check else Icons.Default.Close,
                                contentDescription = null,
                                tint = if (has) Color(perm.tagColorHex) else TextMuted.copy(alpha = 0.3f),
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = perm.label,
                                fontSize = 8.sp,
                                fontWeight = if (has) FontWeight.Bold else FontWeight.Normal,
                                color = if (has) Color.White else TextMuted.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }

            // Simulador de Rol / Conmutador Rápido de Sesión
            Text(
                text = "CONMUTAR SESIÓN A OTRO ROL (SIMULADOR OPERATIVO):",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = GoldPrimary
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(availableUsers) { user ->
                    val isSelected = user.id == currentUser.id
                    Surface(
                        color = if (isSelected) GoldPrimary else NavySurface,
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) GoldPrimary else CyanNeon.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .clickable { onSwitchUser(user) }
                            .testTag("switch_user_chip_${user.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = user.alfhaRole.shortName,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) NavyDark else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditUserRoleAndPermissionsDialog(
    targetUser: AlfhaUserEntity,
    operatorUser: AlfhaUserEntity,
    onDismiss: () -> Unit,
    onSave: (AlfhaRole, Set<AlfhaPermission>) -> Unit
) {
    var selectedRole by remember { mutableStateOf(targetUser.alfhaRole) }
    var selectedPermissions by remember {
        mutableStateOf(targetUser.effectivePermissions.toMutableSet())
    }

    val isSelf = targetUser.id == operatorUser.id
    val canAdmin = operatorUser.hasPermission(AlfhaPermission.ADMINISTRAR)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("edit_role_permissions_dialog"),
            colors = CardDefaults.cardColors(containerColor = NavyCard),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GESTIÓN DE ROL Y PERMISOS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = GoldPrimary
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextMuted)
                    }
                }

                Text(
                    text = "Usuario: ${targetUser.name} (${targetUser.email})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                if (isSelf) {
                    Surface(
                        color = WarningOrange.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, WarningOrange)
                    ) {
                        Text(
                            text = "⚠️ REGLA 5: Estás editando tu propio usuario. El motor de seguridad bloqueará cualquier intento de auto-elevar privilegios a un nivel superior.",
                            fontSize = 9.sp,
                            color = WarningOrange,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }

                // Role selector
                Text(
                    text = "Seleccionar Rol Asignado:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanNeon
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AlfhaRole.values().forEach { role ->
                        val isSelected = selectedRole == role
                        Surface(
                            color = if (isSelected) getRoleColor(role) else NavySurface,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) getRoleColor(role) else TextMuted.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier
                                .clickable {
                                    selectedRole = role
                                    // Auto populate default permissions for role
                                    selectedPermissions = AlfhaUserEntity.getDefaultPermissionsForRole(role).toMutableSet()
                                }
                                .testTag("select_role_chip_${role.name.lowercase()}")
                        ) {
                            Text(
                                text = role.displayName,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                                color = if (isSelected) NavyDark else Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Permissions checkboxes
                Text(
                    text = "Permisos Específicos para este Usuario:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanNeon
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AlfhaPermission.values().forEach { perm ->
                        val isChecked = selectedPermissions.contains(perm)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val newSet = selectedPermissions.toMutableSet()
                                    if (isChecked) newSet.remove(perm) else newSet.add(perm)
                                    selectedPermissions = newSet
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    val newSet = selectedPermissions.toMutableSet()
                                    if (checked) newSet.add(perm) else newSet.remove(perm)
                                    selectedPermissions = newSet
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color(perm.tagColorHex),
                                    uncheckedColor = TextMuted,
                                    checkmarkColor = NavyDark
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${perm.label}: ",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(perm.tagColorHex)
                            )
                            Text(
                                text = perm.description,
                                fontSize = 8.sp,
                                color = TextMuted
                            )
                        }
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancelar", fontSize = 10.sp, color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(selectedRole, selectedPermissions)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        modifier = Modifier.testTag("save_role_permissions_btn")
                    ) {
                        Text(
                            text = "Guardar en Room SQLite",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                    }
                }
            }
        }
    }
}

private fun getRoleColor(role: AlfhaRole): Color {
    return when (role) {
        AlfhaRole.RESIDENTE -> Color(0xFF38BDF8)
        AlfhaRole.GUARDIA -> Color(0xFFFACC15)
        AlfhaRole.SUPERVISOR -> Color(0xFF4ADE80)
        AlfhaRole.ADMINISTRACION -> Color(0xFFFB923C)
        AlfhaRole.MESA_DIRECTIVA -> Color(0xFFA855F7)
        AlfhaRole.MAESTRO_ALFHA -> GoldPrimary
    }
}
